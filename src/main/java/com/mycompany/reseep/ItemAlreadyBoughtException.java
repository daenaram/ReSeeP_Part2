package com.mycompany.reseep;

/**
 * Thrown when someone tries to mark an already-bought item bought again.
 * Maps to 409 Conflict — the request isn't malformed, it just conflicts
 * with the item's current state.
 */
public class ItemAlreadyBoughtException extends RuntimeException {
    public ItemAlreadyBoughtException(Long id) {
        super("Shopping item " + id + " was already marked bought.");
    }
}
