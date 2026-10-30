package com.mycompany.reseep;

import jakarta.ejb.ApplicationException;

// Without this, the @Stateless services would wrap it in EJBException and the
// resource's catch block (which maps it to 4xx) would never see it -> 500.
@ApplicationException(rollback = true)
public class ShoppingItemNotFoundException extends RuntimeException {
    public ShoppingItemNotFoundException(Long id) {
        super("Shopping item " + id + " does not exist.");
    }
}
