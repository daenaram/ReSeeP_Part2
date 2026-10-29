package com.mycompany.reseep;

/**
 * What the client receives after a successful login. Deliberately leaves
 * the password field out entirely — never send it back, even the hash of
 * it, there's simply no reason the client needs it again.
 */
public class UserDto {
    private Long id;
    private String username;
    private UserRole role;

    public UserDto() {
    }

    public UserDto(Long id, String username, UserRole role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
}
