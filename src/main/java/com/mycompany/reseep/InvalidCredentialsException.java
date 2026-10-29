package com.mycompany.reseep;

import jakarta.ejb.ApplicationException;

/**
 * Thrown by UserService when a username doesn't exist or the password
 * doesn't match. Deliberately the same message either way — never tell a
 * caller "that username doesn't exist" vs "wrong password" separately,
 * since that lets someone enumerate valid usernames.
 */
// Without this, the @Stateless services would wrap it in EJBException and the
// resource's catch block (which maps it to 4xx) would never see it -> 500.
@ApplicationException(rollback = true)
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password.");
    }
}
