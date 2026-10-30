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
        if (isInUse(ingredient.getId())) {
            blocked.add(ingredient.getName());
        }
    }
    if (!blocked.isEmpty()) {
        throw new IngredientInUseException("Can't remove " + String.join(", ", blocked)
                + " - still used by a recipe, the shopping list or the purchase history.");
    }

    for (Ingredient ingredient : found) {
        em.remove(ingredient);
    }
    em.flush();
    for (Ingredient ingredient : found) {
        liveUpdates.fire(LiveUpdate.pantry("deleted", ingredient.getId()));
    }
}

private boolean isInUse(Long id) {
    long inRecipes = em.createQuery(
            "SELECT COUNT(r) FROM RecipeIngredient r WHERE r.ingredient.id = :id", Long.class)
            .setParameter("id", id).getSingleResult();
    long onList = em.createQuery(
            "SELECT COUNT(s) FROM ShoppingItem s WHERE s.ingredient.id = :id", Long.class)
            .setParameter("id", id).getSingleResult();
    // purchase_log has no entity (the consumer writes it), so use a native query
    Number logged = (Number) em.createNativeQuery(
            "SELECT COUNT(*) FROM purchase_log WHERE ingredient_id = ?1")
            .setParameter(1, id).getSingleResult();
    return inRecipes > 0 || onList > 0 || logged.longValue() > 0;
}
}
