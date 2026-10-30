package com.reseep.app.api;

public class LoginRequest {
    private String username;

    public LoginRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}