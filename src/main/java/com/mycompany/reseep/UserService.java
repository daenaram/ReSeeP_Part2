package com.mycompany.reseep;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

/**
 * All the login business logic lives here, not in a Resource class. The
 * Resource's only job (see AuthResource) is to translate HTTP <-> this.
 */
@Stateless
public class UserService {

    @PersistenceContext(unitName = "reseepPU")
    private EntityManager em;

    public UserDto login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new InvalidCredentialsException();
        }

        AppUser user;
        try {
            user = em.createQuery(
                            "SELECT u FROM AppUser u WHERE u.username = :username",
                            AppUser.class)
                    .setParameter("username", username.trim())
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new InvalidCredentialsException();
        }

        if (!user.getPassword().equals(password)) {
            throw new InvalidCredentialsException();
        }

        return new UserDto(user.getId(), user.getUsername(), user.getRole());
    }
}
