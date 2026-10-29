package com.mycompany.reseep;

/**
 * A service-layer rule was violated (blank name, negative quantity, unknown
 * referenced id, etc). Maps to 400 Bad Request. Using one exception type
 * for all of these keeps the services free of any HTTP-specific code —
 * they just throw a plain Java exception and the Resource decides what
 * status code that becomes.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
