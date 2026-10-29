package com.mycompany.reseep;

/**
 * What the client sends to POST /api/login. JAX-RS will deserialise the
 * incoming JSON body straight into this, the same way RecipeDto works for
 * /api/recipes.
 */
public class LoginRequest {
    private String username;
    private String password;

    public LoginRequest() {
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
