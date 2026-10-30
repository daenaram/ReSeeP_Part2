package com.mycompany.reseep;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runs once when the app deploys. Creates the three tables if they don't
 * exist yet, and seeds sample data if the ingredient table is empty.
 * Safe to redeploy repeatedly — CREATE TABLE IF NOT EXISTS and the empty
 * check mean it won't duplicate anything.
 */
@Singleton
@Startup
public class DbInitializer {

    @Resource(lookup = "jdbc/ReSeePPool")
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ingredient (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(100) NOT NULL," +
                    "unit VARCHAR(20)," +
                    "qty_on_hand DOUBLE NOT NULL DEFAULT 0)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS recipe (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(150) NOT NULL," +
                    "servings INT NOT NULL DEFAULT 1," +
                    "notes VARCHAR(255))");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS recipe_ingredient (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "recipe_id BIGINT NOT NULL," +
                    "ingredient_id BIGINT NOT NULL," +
                    "qty DOUBLE NOT NULL," +
                    "CONSTRAINT fk_ri_recipe FOREIGN KEY (recipe_id) REFERENCES recipe(id) ON DELETE CASCADE," +
                    "CONSTRAINT fk_ri_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id) ON DELETE RESTRICT)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS app_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "username VARCHAR(50) NOT NULL UNIQUE," +
                    "password VARCHAR(100) NOT NULL," +
                    "role VARCHAR(20) NOT NULL)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS shopping_item (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "ingredient_id BIGINT NOT NULL," +
                    "qty DOUBLE NOT NULL," +
                    "status VARCHAR(20) NOT NULL," +
                    "added_by_id BIGINT NOT NULL," +
                    "bought_by_id BIGINT," +
                    "bought_at DATETIME," +
                    "CONSTRAINT fk_si_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id) ON DELETE RESTRICT," +
                    "CONSTRAINT fk_si_added_by FOREIGN KEY (added_by_id) REFERENCES app_user(id) ON DELETE RESTRICT," +
                    "CONSTRAINT fk_si_bought_by FOREIGN KEY (bought_by_id) REFERENCES app_user(id) ON DELETE RESTRICT)");

            // Idempotency guard for the broker consumer: one row per
            // ItemPurchased event it has successfully finished processing.
            // If the same event is delivered twice (a retry, or a manually
            // replayed message), the consumer checks this table first and
            // skips reprocessing.
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS processed_events (" +
                    "event_id VARCHAR(64) PRIMARY KEY," +
                    "processed_at DATETIME NOT NULL)");

            // Audit trail: one row per purchase the consumer has applied to
            // the pantry, separate from shopping_item so the history survives
            // even if the shopping list item itself is later changed.
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purchase_log (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "shopping_item_id BIGINT NOT NULL," +
                    "ingredient_id BIGINT NOT NULL," +
                    "qty DOUBLE NOT NULL," +
                    "bought_by_id BIGINT NOT NULL," +
                    "bought_at DATETIME NOT NULL," +
                    "event_id VARCHAR(64) NOT NULL," +
                    "CONSTRAINT fk_pl_shopping_item FOREIGN KEY (shopping_item_id) REFERENCES shopping_item(id) ON DELETE RESTRICT," +
                    "CONSTRAINT fk_pl_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient(id) ON DELETE RESTRICT," +
                    "CONSTRAINT fk_pl_bought_by FOREIGN KEY (bought_by_id) REFERENCES app_user(id) ON DELETE RESTRICT)");

            seedIfEmpty(conn);
            seedUsersIfEmpty(conn);

            System.out.println("[DbInitializer] Schema check complete.");
        } catch (SQLException e) {
            System.err.println("[DbInitializer] Failed to initialize schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void seedIfEmpty(Connection conn) throws SQLException {
        try (Statement check = conn.createStatement();
             ResultSet rs = check.executeQuery("SELECT COUNT(*) FROM ingredient")) {
            rs.next();
            if (rs.getInt(1) > 0) {
                return; // already seeded, don't duplicate
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO ingredient (name, unit, qty_on_hand) VALUES " +
                    "('Spaghetti','g',400)," +
                    "('Tomato','pc',2)," +
                    "('Basil','g',0)," +
                    "('Garlic','clove',6)," +
                    "('Rice','g',500)," +
                    "('Egg','pc',3)," +
                    "('Soy sauce','tbsp',0)," +
                    "('Ground beef','g',0)," +
                    "('Kidney beans','can',2)," +
                    "('Chili powder','tsp',4)");

            stmt.executeUpdate("INSERT INTO recipe (name, servings, notes) VALUES " +
                    "('Tomato basil pasta',2,'Quick midweek dinner')," +
                    "('Pantry fried rice',3,'Good for leftovers')," +
                    "('Weeknight chili',4,'')");

            stmt.executeUpdate("INSERT INTO recipe_ingredient (recipe_id, ingredient_id, qty) VALUES " +
                    "(1,1,200),(1,2,3),(1,3,10),(1,4,2)," +
                    "(2,5,300),(2,6,2),(2,7,2)," +
                    "(3,8,400),(3,9,2),(3,10,3)");
        }
    }

    /**
     * Two test accounts so the group can demo both roles straight away
     * without a signup screen. Plaintext passwords only, matching the
     * "simple login" scope — see README Known limitations.
     */
    private void seedUsersIfEmpty(Connection conn) throws SQLException {
        try (Statement check = conn.createStatement();
             ResultSet rs = check.executeQuery("SELECT COUNT(*) FROM app_user")) {
            rs.next();
            if (rs.getInt(1) > 0) {
                return;
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO app_user (username, password, role) VALUES " +
                    "('cook','cook123','COOK')," +
                    "('shopper','shopper123','SHOPPER')");
        }
        try (Statement fix = conn.createStatement()) {
    fix.executeUpdate("UPDATE app_user SET password='shopper123' WHERE username='shopper'");
}
    }
}