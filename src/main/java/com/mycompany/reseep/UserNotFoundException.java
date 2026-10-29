package com.mycompany.reseep;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("User " + id + " does not exist.");
    }
}
