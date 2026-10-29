package com.mycompany.reseep;

import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Every rule about recipes lives here. RecipeResource (the REST layer)
 * only reads the HTTP request, calls one of these methods, and turns the
 * result or thrown exception into a Response.
 */
@Stateless
public class RecipeService {

    @PersistenceContext(unitName = "reseepPU")
    private EntityManager em;

    @Inject
    private Event<LiveUpdate> liveUpdates;

    public List<RecipeDto> findAll() {
        return em.createQuery("SELECT r FROM Recipe r", Recipe.class)
                .getResultList()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public RecipeDto findById(Long id) {
        Recipe recipe = em.find(Recipe.class, id);
        if (recipe == null) {
            throw new RecipeNotFoundException(id);
        }
        return toDto(recipe);
    }

    @Transactional
    public RecipeDto create(RecipeDto dto) {
        validate(dto);

        Recipe recipe = new Recipe();
        applyFields(recipe, dto);
        applyItems(recipe, dto);

        em.persist(recipe);
        em.flush();

        liveUpdates.fire(LiveUpdate.recipe("created", recipe.getId()));
        return toDto(recipe);
    }

    @Transactional
    public RecipeDto update(Long id, RecipeDto dto) {
        Recipe recipe = em.find(Recipe.class, id);
        if (recipe == null) {
            throw new RecipeNotFoundException(id);
        }
        validate(dto);

        applyFields(recipe, dto);
        recipe.getItems().clear();
        applyItems(recipe, dto);

        liveUpdates.fire(LiveUpdate.recipe("updated", id));
        return toDto(recipe);
    }

    @Transactional
    public void delete(Long id) {
        Recipe recipe = em.find(Recipe.class, id);
        if (recipe == null) {
            throw new RecipeNotFoundException(id);
        }
        em.remove(recipe);
        liveUpdates.fire(LiveUpdate.recipe("deleted", id));
    }

    private void validate(RecipeDto dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new ValidationException("Recipe name is required.");
        }
        if (dto.getServings() < 1) {
            throw new ValidationException("Servings must be at least 1.");
        }
    }

    private void applyFields(Recipe recipe, RecipeDto dto) {
        recipe.setName(dto.getName().trim());
        recipe.setServings(dto.getServings());
        recipe.setNotes(dto.getNotes());
    }

    private void applyItems(Recipe recipe, RecipeDto dto) {
        if (dto.getItems() == null) return;
        for (RecipeItemDto itemDto : dto.getItems()) {
            Ingredient ingredient = em.find(Ingredient.class, itemDto.getIngredientId());
            if (ingredient == null) {
                throw new ValidationException("Unknown ingredient id: " + itemDto.getIngredientId());
            }
            if (itemDto.getQty() < 0) {
                throw new ValidationException("Ingredient quantity cannot be negative.");
            }
            RecipeIngredient ri = new RecipeIngredient();
            ri.setIngredient(ingredient);
            ri.setQty(itemDto.getQty());
            recipe.addItem(ri);
        }
    }

    private RecipeDto toDto(Recipe recipe) {
        RecipeDto dto = new RecipeDto();
        dto.setId(recipe.getId());
        dto.setName(recipe.getName());
        dto.setServings(recipe.getServings());
        dto.setNotes(recipe.getNotes());
        dto.setItems(recipe.getItems().stream()
                .map(ri -> new RecipeItemDto(ri.getIngredient().getId(), ri.getQty()))
                .collect(Collectors.toList()));
        return dto;
    }
}
