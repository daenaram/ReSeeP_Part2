package com.mycompany.reseep;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.stream.Collectors;

@Path("/pantry")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PantryResource {

    @PersistenceContext(unitName = "reseepPU")
    private EntityManager em;

    @Inject
    private Event<LiveUpdate> liveUpdates;

    @GET
    public List<IngredientDto> list() {
        List<Ingredient> ingredients = em.createQuery("SELECT i FROM Ingredient i ORDER BY i.name", Ingredient.class)
                .getResultList();
        return ingredients.stream().map(this::toDto).collect(Collectors.toList());
    }

    @POST
    @Transactional
    public Response create(IngredientDto dto) {
        Response validation = validate(dto);
        if (validation != null) return validation;

        Ingredient ingredient = new Ingredient();
        applyFields(ingredient, dto);
        em.persist(ingredient);
        em.flush();

        liveUpdates.fire(LiveUpdate.pantry("created", ingredient.getId()));
        return Response.status(Response.Status.CREATED).entity(toDto(ingredient)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, IngredientDto dto) {
        Ingredient ingredient = em.find(Ingredient.class, id);
        if (ingredient == null) {
            return notFound("ingredient " + id);
        }
        Response validation = validate(dto);
        if (validation != null) return validation;

        applyFields(ingredient, dto);
        liveUpdates.fire(LiveUpdate.pantry("updated", id));
        return Response.ok(toDto(ingredient)).build();
    }

    private Response validate(IngredientDto dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            return badRequest("Ingredient name is required.");
        }
        if (dto.getQtyOnHand() < 0) {
            return badRequest("Quantity on hand cannot be negative.");
        }
        return null;
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

    private Response notFound(String what) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"Not found: " + what + "\"}")
                .build();
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"" + message.replace("\"", "'") + "\"}")
                .build();
    }
}