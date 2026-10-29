package com.mycompany.reseep;

public class ShoppingItemNotFoundException extends RuntimeException {
    public ShoppingItemNotFoundException(Long id) {
        super("Shopping item " + id + " does not exist.");
    }
}
