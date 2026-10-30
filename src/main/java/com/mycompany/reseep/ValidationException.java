package com.mycompany.reseep;

import jakarta.ejb.ApplicationException;

/**
 * A service-layer rule was violated (blank name, negative quantity, unknown
 * referenced id, etc). Maps to 400 Bad Request. Using one exception type
 * for all of these keeps the services free of any HTTP-specific code —
 * they just throw a plain Java exception and the Resource decides what
 * status code that becomes.
 */
// Without this, the @Stateless services would wrap it in EJBException and the
// resource's catch block (which maps it to 4xx) would never see it -> 500.
@ApplicationException(rollback = true)
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
