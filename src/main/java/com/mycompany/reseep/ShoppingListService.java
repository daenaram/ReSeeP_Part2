package com.mycompany.reseep;

import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * All the shopping-list business rules live here — the Resource (see
 * ShoppingResource) only translates HTTP in and out of these methods.
 */
@Stateless
public class ShoppingListService {

    @PersistenceContext(unitName = "reseepPU")
    private EntityManager em;

    @Inject
    private Event<LiveUpdate> liveUpdates;

    @Inject
    private EventPublisher eventPublisher;

    public List<ShoppingItemDto> list() {
        List<ShoppingItem> items = em.createQuery(
                        "SELECT s FROM ShoppingItem s ORDER BY s.status, s.id",
                        ShoppingItem.class)
                .getResultList();
        return items.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Workflow 1: work out what's missing for a recipe (needed qty minus
     * what's already in the pantry) and add shortfalls to the shared list.
     * If an item for that ingredient is already on the list as NEEDED, its
     * quantity is topped up instead of creating a duplicate row.
     */
    @Transactional
    public List<ShoppingItemDto> planForRecipe(Long recipeId, Long userId) {
        Recipe recipe = em.find(Recipe.class, recipeId);
        if (recipe == null) {
            throw new RecipeNotFoundException(recipeId);
        }
        AppUser user = findUser(userId);

        List<ShoppingItem> affected = new ArrayList<>();

        for (RecipeIngredient line : recipe.getItems()) {
            Ingredient ingredient = line.getIngredient();
            double shortfall = line.getQty() - ingredient.getQtyOnHand();
            if (shortfall <= 0) {
                continue; // pantry already has enough of this one
            }

            ShoppingItem existing = findExistingNeededItem(ingredient.getId());
            if (existing != null) {
                existing.setQty(existing.getQty() + shortfall);
                affected.add(existing);
            } else {
                ShoppingItem item = new ShoppingItem();
                item.setIngredient(ingredient);
                item.setQty(shortfall);
                item.setStatus(ShoppingStatus.NEEDED);
                item.setAddedBy(user);
                em.persist(item);
                affected.add(item);
            }
        }

        em.flush();

        if (!affected.isEmpty()) {
            liveUpdates.fire(LiveUpdate.shopping("planned", recipeId));
        }

        return affected.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Workflow 2: mark one item bought. This only updates the shopping_item
     * row and announces the change — it deliberately does NOT touch pantry
     * quantities here. That happens asynchronously once the message broker
     * delivers the ItemPurchased event to the separate consumer (see the
     * broker piece of this project), which is what makes this a genuine
     * distributed-systems workflow rather than one method doing everything.
     */
    @Transactional
    public ShoppingItemDto markBought(Long itemId, Long userId) {
        ShoppingItem item = em.find(ShoppingItem.class, itemId);
        if (item == null) {
            throw new ShoppingItemNotFoundException(itemId);
        }
        if (item.getStatus() == ShoppingStatus.BOUGHT) {
            throw new ItemAlreadyBoughtException(itemId);
        }
        AppUser user = findUser(userId);

        item.setStatus(ShoppingStatus.BOUGHT);
        item.setBoughtBy(user);
        item.setBoughtAt(LocalDateTime.now());
        em.flush();

        liveUpdates.fire(LiveUpdate.shopping("bought", itemId));
        eventPublisher.publishItemPurchased(toItemPurchasedJson(item, user));

        return toDto(item);
    }

    /**
     * eventId is a fresh UUID per publish, not the shopping item's own id --
     * the consumer uses it to tell "a brand new purchase" apart from "the
     * same RabbitMQ message delivered again" (see the consumer's idempotency
     * check against the processed_events table).
     */
    private String toItemPurchasedJson(ShoppingItem item, AppUser user) {
        String eventId = java.util.UUID.randomUUID().toString();
        return "{\"eventId\":\"" + eventId + "\"," +
                "\"shoppingItemId\":" + item.getId() + "," +
                "\"ingredientId\":" + item.getIngredient().getId() + "," +
                "\"qty\":" + item.getQty() + "," +
                "\"boughtByUserId\":" + user.getId() + "," +
                "\"boughtAt\":\"" + item.getBoughtAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\"}";
    }

    private ShoppingItem findExistingNeededItem(Long ingredientId) {
        try {
            return em.createQuery(
                            "SELECT s FROM ShoppingItem s " +
                                    "WHERE s.ingredient.id = :ingredientId AND s.status = :status",
                            ShoppingItem.class)
                    .setParameter("ingredientId", ingredientId)
                    .setParameter("status", ShoppingStatus.NEEDED)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private AppUser findUser(Long userId) {
        if (userId == null) {
            throw new UserNotFoundException(null);
        }
        AppUser user = em.find(AppUser.class, userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }
        return user;
    }

    private ShoppingItemDto toDto(ShoppingItem item) {
        ShoppingItemDto dto = new ShoppingItemDto();
        dto.setId(item.getId());
        dto.setIngredientId(item.getIngredient().getId());
        dto.setIngredientName(item.getIngredient().getName());
        dto.setUnit(item.getIngredient().getUnit());
        dto.setQty(item.getQty());
        dto.setStatus(item.getStatus().name());
        dto.setAddedByUsername(item.getAddedBy().getUsername());
        if (item.getBoughtBy() != null) {
            dto.setBoughtByUsername(item.getBoughtBy().getUsername());
        }
        if (item.getBoughtAt() != null) {
            dto.setBoughtAt(item.getBoughtAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        return dto;
    }
}
