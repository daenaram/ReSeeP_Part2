package com.mycompany.reseep;

import jakarta.ejb.Stateless;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

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
}
