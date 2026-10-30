package com.mycompany.reseep;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

/**
 * Standalone consumer for the ItemPurchased event published by the web app.
 * Run this as its own separate process (mvn exec:java from this folder) --
 * it is NOT part of the reseep.war and is never deployed to Payara. That
 * separation is the point: the web app publishes an event and returns
 * immediately; this independent program picks the event up whenever it
 * next runs and does the actual pantry/purchase-log work.
 *
 * See the project README for setup (RabbitMQ via docker-compose, required
 * environment variables) and demo instructions (forcing a failure/retry,
 * and replaying a duplicate event).
 */
public class PurchaseConsumer {

    private static final String QUEUE_NAME = "item_purchased_queue";
    private static final AtomicBoolean FAILED_ONCE = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        requireDbPassword();

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(envOrDefault("RABBITMQ_HOST", "localhost"));
        factory.setPort(Integer.parseInt(envOrDefault("RABBITMQ_PORT", "5672")));
        factory.setUsername(envOrDefault("RABBITMQ_USER", "reseep"));
        factory.setPassword(envOrDefault("RABBITMQ_PASSWORD", "reseep"));

        com.rabbitmq.client.Connection rabbitConnection = factory.newConnection();
        Channel channel = rabbitConnection.createChannel();
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);

        // Handle one message at a time, and don't let RabbitMQ consider a
        // message delivered until we explicitly ack it ourselves below.
        channel.basicQos(1);

        System.out.println("[Consumer] Waiting for ItemPurchased events on '" + QUEUE_NAME + "'. Ctrl+C to stop.");

        DeliverCallback onDelivery = (consumerTag, delivery) -> {
            String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
            long deliveryTag = delivery.getEnvelope().getDeliveryTag();
            System.out.println("[Consumer] Received: " + json);

            try {
                handleEvent(json);
                channel.basicAck(deliveryTag, false);
                System.out.println("[Consumer] Acked.");
            } catch (SimulatedFailureException e) {
                System.err.println("[Consumer] Simulated failure (" + e.getMessage() + ") -- nacking, will be requeued.");
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception e) {
                System.err.println("[Consumer] Unexpected error (" + e.getMessage() + ") -- nacking, will be requeued.");
                e.printStackTrace();
                channel.basicNack(deliveryTag, false, true);
            }
        };

        channel.basicConsume(QUEUE_NAME, false, onDelivery, consumerTag -> { });
    }

    private static void handleEvent(String json) throws SQLException {
        JSONObject event = new JSONObject(json);
        String eventId = event.getString("eventId");

        // Demo hook (see README "Demonstrating failure and retry"): set
        // SIMULATE_FAILURE_ONCE=true to make the very next event fail
        // before it's acked, so you can show RabbitMQ redelivering it.
        if ("true".equalsIgnoreCase(System.getenv("SIMULATE_FAILURE_ONCE")) && FAILED_ONCE.compareAndSet(false, true)) {
            throw new SimulatedFailureException("forced failure for event " + eventId);
        }

        try (Connection db = openDbConnection()) {
            db.setAutoCommit(false);
            try {
                if (alreadyProcessed(db, eventId)) {
                    System.out.println("[Consumer] Event " + eventId + " was already processed -- skipping (idempotent).");
                    db.commit();
                    return;
                }

                long ingredientId = event.getLong("ingredientId");
                double qty = event.getDouble("qty");
                long shoppingItemId = event.getLong("shoppingItemId");
                long boughtByUserId = event.getLong("boughtByUserId");
                LocalDateTime boughtAt = LocalDateTime.parse(event.getString("boughtAt"));

                if (!addToPantry(db, ingredientId, qty)) {
                    // The ingredient was removed from the pantry after this was
                    // bought. Retrying can never succeed, so record it as done
                    // instead of nacking it back onto the queue forever.
                    System.out.println("[Consumer] Ingredient " + ingredientId + " no longer exists -- skipping event " + eventId + ".");
                    markProcessed(db, eventId);
                    db.commit();
                    return;
                }
                insertPurchaseLog(db, shoppingItemId, ingredientId, qty, boughtByUserId, boughtAt, eventId);
                markProcessed(db, eventId);

                db.commit();
                System.out.println("[Consumer] Pantry updated and purchase logged for event " + eventId + ".");
                notifyWebAppPantryChanged(ingredientId);
            } catch (SQLException e) {
                db.rollback();
                throw e;
            }
        }
    }

    private static boolean alreadyProcessed(Connection db, String eventId) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT 1 FROM processed_events WHERE event_id = ?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Returns false if the ingredient no longer exists. */
    private static boolean addToPantry(Connection db, long ingredientId, double qty) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE ingredient SET qty_on_hand = qty_on_hand + ? WHERE id = ?")) {
            ps.setDouble(1, qty);
            ps.setLong(2, ingredientId);
            return ps.executeUpdate() > 0;
        }
    }

    private static void insertPurchaseLog(Connection db, long shoppingItemId, long ingredientId, double qty,
                                           long boughtByUserId, LocalDateTime boughtAt, String eventId) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO purchase_log (shopping_item_id, ingredient_id, qty, bought_by_id, bought_at, event_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, shoppingItemId);
            ps.setLong(2, ingredientId);
            ps.setDouble(3, qty);
            ps.setLong(4, boughtByUserId);
            ps.setTimestamp(5, Timestamp.valueOf(boughtAt));
            ps.setString(6, eventId);
            ps.executeUpdate();
        }
    }

    private static void markProcessed(Connection db, String eventId) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO processed_events (event_id, processed_at) VALUES (?, NOW())")) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        }
    }

    /**
     * Tells the running web app a pantry quantity changed, so its existing
     * LiveUpdate/WebSocket broadcast can push the change to connected
     * browsers. Best-effort: if the web app isn't reachable, the database
     * work this consumer did is already committed and correct regardless --
     * a browser will just show the old number until it next reloads.
     */
    private static void notifyWebAppPantryChanged(long ingredientId) {
        try {
            String baseUrl = envOrDefault("WEBAPP_BASE_URL", "http://localhost:8080/reseep/api");
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/internal/notify-pantry-change"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"ingredientId\":" + ingredientId + "}"))
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            System.out.println("[Consumer] Notified web app of pantry change (HTTP " + response.statusCode() + ").");
        } catch (Exception e) {
            System.err.println("[Consumer] Could not notify web app, continuing anyway (non-fatal): " + e.getMessage());
        }
    }

    private static Connection openDbConnection() throws SQLException {
        String host = envOrDefault("DB_HOST", "comp713-s2-2026.c5m2mi6uava2.ap-southeast-2.rds.amazonaws.com");
        String port = envOrDefault("DB_PORT", "3306");
        String database = envOrDefault("DB_NAME", "comp713_23208043");
        String user = envOrDefault("DB_USER", "c713_23208043");
        String password = System.getenv("DB_PASSWORD");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?sslMode=REQUIRED&fallbackToSystemTrustStore=false&fallbackToSystemKeyStore=false";
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * The database password is never hardcoded or committed to git -- it
     * must be supplied as an environment variable each time you run this.
     */
    private static void requireDbPassword() {
        if (System.getenv("DB_PASSWORD") == null || System.getenv("DB_PASSWORD").isBlank()) {
            System.err.println("[Consumer] DB_PASSWORD environment variable is not set. " +
                    "Set it to the AWS MySQL password before running this (see README).");
            System.exit(1);
        }
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static class SimulatedFailureException extends RuntimeException {
        SimulatedFailureException(String message) {
            super(message);
        }
    }
}
