package com.mycompany.reseep;

public class RecipeNotFoundException extends RuntimeException {
    public RecipeNotFoundException(Long id) {
        super("Recipe " + id + " does not exist.");
    }
}
