package com.mycompany.reseep;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class IngredientInUseException extends RuntimeException {
    public IngredientInUseException(String message) {
        super(message);
    }
}