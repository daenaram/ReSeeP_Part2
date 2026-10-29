package com.mycompany.reseep;

/**
 * The two roles the brief asks for. Kept as a plain enum (not a free-text
 * column) so an invalid role is impossible at the database level — MySQL
 * will store it as a VARCHAR, but JPA only ever writes one of these two
 * names into it.
 */
public enum UserRole {
    COOK,
    SHOPPER
}
