package com.mycompany.reseep;

/**
 * A change notification pushed to connected web clients over WebSocket.
 * Deliberately tiny: clients re-fetch the affected data over REST, so the
 * API stays the single source of truth.
 *
 * type   - "pantry-updated" or "recipe-updated"
 * action - "created", "updated" or "deleted"
 * id     - id of the affected ingredient or recipe
 */
public record LiveUpdate(String type, String action, Long id) {

    public static LiveUpdate pantry(String action, Long id) {
        return new LiveUpdate("pantry-updated", action, id);
    }

    public static LiveUpdate recipe(String action, Long id) {
        return new LiveUpdate("recipe-updated", action, id);
    }

    public static LiveUpdate shopping(String action, Long id) {
        return new LiveUpdate("shopping-updated", action, id);
    }

    public String toJson() {
        return "{\"type\":\"" + type + "\",\"action\":\"" + action + "\",\"id\":" + id + "}";
    }
}
