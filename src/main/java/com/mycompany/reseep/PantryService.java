package com.mycompany.reseep;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@Stateless
public class PantryService {

    @PersistenceContext(unitName = "reseepPU")
    private EntityManager em;

    @Inject
    private Event<LiveUpdate> liveUpdates;

    public List<IngredientDto> findAll() {
        return em.createQuery("SELECT i FROM Ingredient i ORDER BY i.name", Ingredient.class)
                .getResultList()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public IngredientDto create(IngredientDto dto) {
        validate(dto);

        Ingredient ingredient = new Ingredient();
        applyFields(ingredient, dto);
        em.persist(ingredient);
        em.flush();

        liveUpdates.fire(LiveUpdate.pantry("created", ingredient.getId()));
        return toDto(ingredient);
    }

    @Transactional
    public IngredientDto update(Long id, IngredientDto dto) {
        Ingredient ingredient = em.find(Ingredient.class, id);
        if (ingredient == null) {
            throw new IngredientNotFoundException(id);
        }
        validate(dto);

        applyFields(ingredient, dto);
        liveUpdates.fire(LiveUpdate.pantry("updated", id));
        return toDto(ingredient);
    }

    private void validate(IngredientDto dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new ValidationException("Ingredient name is required.");
        }
        if (dto.getQtyOnHand() < 0) {
            throw new ValidationException("Quantity on hand cannot be negative.");
        }
    }

    private void applyFields(Ingredient ingredient, IngredientDto dto) {
        ingredient.setName(dto.getName().trim());
        ingredient.setUnit(dto.getUnit());
        ingredient.setQtyOnHand(dto.getQtyOnHand());
    }

    private IngredientDto toDto(Ingredient ingredient) {
        IngredientDto dto = new IngredientDto();
        dto.setId(ingredient.getId());
        dto.setName(ingredient.getName());
        dto.setUnit(ingredient.getUnit());
        dto.setQtyOnHand(ingredient.getQtyOnHand());
        return dto;
    }

    /**
     * Removes ingredients, all or nothing. Only live uses block a removal:
     * a recipe that still lists it, or a NEEDED shopping item. Its history
     * (BOUGHT shopping items and their purchase_log rows) goes with it,
     * otherwise anything ever bought could never be removed.
     */
    @Transactional
    public void deleteAll(List<Long> ids) {
        List<Long> distinct = ids == null ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            throw new ValidationException("Pick at least one ingredient to remove.");
        }

        List<Ingredient> found = new ArrayList<>();
        for (Long id : distinct) {
            Ingredient ingredient = em.find(Ingredient.class, id);
            if (ingredient == null) {
                throw new IngredientNotFoundException(id);
            }
            found.add(ingredient);
        }

        List<String> blocked = new ArrayList<>();
        for (Ingredient ingredient : found) {
            String reason = blockingReason(ingredient.getId());
            if (reason != null) {
                blocked.add(ingredient.getName() + " (" + reason + ")");
            }
        }
        if (!blocked.isEmpty()) {
            throw new IngredientInUseException("Can't remove " + String.join(", ", blocked) + ".");
        }

        // purchase_log references shopping_item, so it goes first. It has no
        // entity (the consumer writes it), hence the native query.
        for (Ingredient ingredient : found) {
            em.createNativeQuery("DELETE FROM purchase_log WHERE ingredient_id = ?1")
                    .setParameter(1, ingredient.getId()).executeUpdate();
            em.createQuery("DELETE FROM ShoppingItem s WHERE s.ingredient.id = :id")
                    .setParameter("id", ingredient.getId()).executeUpdate(); // only BOUGHT ones are left by now
            em.remove(ingredient);
        }
        em.flush();
        for (Ingredient ingredient : found) {
            liveUpdates.fire(LiveUpdate.pantry("deleted", ingredient.getId()));
        }
    }

    /** Why this ingredient can't be removed yet, or null if it can. */
    private String blockingReason(Long id) {
        List<String> recipes = em.createQuery(
                        "SELECT DISTINCT r.recipe.name FROM RecipeIngredient r WHERE r.ingredient.id = :id", String.class)
                .setParameter("id", id).getResultList();
        if (!recipes.isEmpty()) {
            return "used by " + String.join(", ", recipes);
        }
        long needed = em.createQuery(
                        "SELECT COUNT(s) FROM ShoppingItem s WHERE s.ingredient.id = :id AND s.status = :status", Long.class)
                .setParameter("id", id).setParameter("status", ShoppingStatus.NEEDED).getSingleResult();
        if (needed > 0) {
            return "still needed on the shopping list";
        }
        return null;
    }
}
