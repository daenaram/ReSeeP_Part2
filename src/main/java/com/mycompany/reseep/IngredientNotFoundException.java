package com.mycompany.reseep;

public class IngredientNotFoundException extends RuntimeException {
    public IngredientNotFoundException(Long id) {
        super("Ingredient " + id + " does not exist.");
    }
}
