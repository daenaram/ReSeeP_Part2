package com.mycompany.reseep;

import jakarta.ejb.ApplicationException;

/**
 * Thrown when someone tries to mark an already-bought item bought again.
 * Maps to 409 Conflict — the request isn't malformed, it just conflicts
 * with the item's current state.
 */
// Without this, the @Stateless services would wrap it in EJBException and the
// resource's catch block (which maps it to 4xx) would never see it -> 500.
@ApplicationException(rollback = true)
public class ItemAlreadyBoughtException extends RuntimeException {
    public ItemAlreadyBoughtException(Long id) {
        super("Shopping item " + id + " was already marked bought.");
    }
}
