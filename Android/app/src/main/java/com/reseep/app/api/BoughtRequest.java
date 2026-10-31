package com.reseep.app.api;

public class BoughtRequest {

    private int userId;

    public BoughtRequest(int userId) {
        this.userId = userId;
    }

    public int getUserId() {
        return userId;
    }
}
