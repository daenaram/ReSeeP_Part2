package com.mycompany.reseep;

/**
 * Shared request body shape for "do this action as user X" endpoints —
 * planning a recipe and marking an item bought both just need to know who's
 * doing it, since there's no session to read that from.
 */
public class ActingUserRequest {
    private Long userId;

    public ActingUserRequest() {
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
