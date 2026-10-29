package com.mycompany.reseep;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Publishes ItemPurchased events to RabbitMQ. One connection is opened when
 * the app starts and reused for every publish, rather than opening a new
 * one per request -- the same reasoning as a JDBC connection pool, just for
 * a message broker instead of a database.
 *
 * Deliberately tolerant of RabbitMQ being unreachable: if Docker isn't
 * running or the broker is down, connect() logs a warning instead of
 * throwing, so the rest of the app (login, recipes, pantry, marking items
 * bought) keeps working even without the broker piece running.
 */
@ApplicationScoped
public class EventPublisher {

    private static final Logger LOG = Logger.getLogger(EventPublisher.class.getName());
    public static final String QUEUE_NAME = "item_purchased_queue";

    private Connection connection;
    private Channel channel;

    @PostConstruct
    public void connect() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(envOrDefault("RABBITMQ_HOST", "localhost"));
            factory.setPort(Integer.parseInt(envOrDefault("RABBITMQ_PORT", "5672")));
            factory.setUsername(envOrDefault("RABBITMQ_USER", "reseep"));
            factory.setPassword(envOrDefault("RABBITMQ_PASSWORD", "reseep"));

            connection = factory.newConnection();
            channel = connection.createChannel();
            // durable=true: the queue itself survives a RabbitMQ restart.
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            LOG.info("[EventPublisher] Connected to RabbitMQ, queue '" + QUEUE_NAME + "' ready.");
        } catch (Exception e) {
            LOG.warning("[EventPublisher] Could not connect to RabbitMQ (" + e.getMessage() +
                    "). The app will keep running, but marking items bought won't publish events " +
                    "until RabbitMQ is reachable. Run 'docker compose up -d' and redeploy.");
        }
    }

    public void publishItemPurchased(String json) {
        if (channel == null) {
            LOG.warning("[EventPublisher] No RabbitMQ channel available; dropping event: " + json);
            return;
        }
        try {
            // PERSISTENT_TEXT_PLAIN marks the message itself as persistent
            // (written to disk, not just memory) so it survives a RabbitMQ
            // restart too, matching the durable queue above.
            channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN,
                    json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warning("[EventPublisher] Failed to publish event: " + e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (channel != null) channel.close();
            if (connection != null) connection.close();
        } catch (IOException | TimeoutException e) {
            LOG.warning("[EventPublisher] Error closing RabbitMQ connection: " + e.getMessage());
        }
    }

    private String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
