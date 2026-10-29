package com.mycompany.reseep;

/**
 * Thrown by UserService when a username doesn't exist or the password
 * doesn't match. Deliberately the same message either way — never tell a
 * caller "that username doesn't exist" vs "wrong password" separately,
 * since that lets someone enumerate valid usernames.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password.");
    }
}
