package com.mycompany.reseep;

import jakarta.ws.rs.core.Response;

/**
 * One place that builds the JSON error body every Resource returns, so
 * "a 404 looks like X" is decided once instead of four times. Every
 * Resource (Auth, Recipe, Pantry, Shopping) uses these instead of writing
 * its own Response.status(...).entity("{\"error\":...}") by hand.
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static Response badRequest(String message) {
        return build(Response.Status.BAD_REQUEST, message);
    }

    public static Response notFound(String message) {
        return build(Response.Status.NOT_FOUND, message);
    }

    public static Response conflict(String message) {
        return build(Response.Status.CONFLICT, message);
    }

    public static Response unauthorized(String message) {
        return build(Response.Status.UNAUTHORIZED, message);
    }

    private static Response build(Response.Status status, String message) {
        String safeMessage = message == null ? "Unexpected error." : message.replace("\"", "'");
        return Response.status(status)
                .entity("{\"error\":\"" + safeMessage + "\"}")
                .build();
    }
}
