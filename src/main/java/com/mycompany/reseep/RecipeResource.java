package com.mycompany.reseep;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;
import java.util.stream.Collectors;

@Path("/recipes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecipeResource {

    @PersistenceContext(unitName = "reseepPU")
    private EntityManager em;

    @Inject
    private Event<LiveUpdate> liveUpdates;

    @Inject
    private ShoppingListService shoppingListService;

    @GET
    public List<RecipeDto> list() {
        List<Recipe> recipes = em.createQuery("SELECT r FROM Recipe r", Recipe.class).getResultList();
        return recipes.stream().map(this::toDto).collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        Recipe recipe = em.find(Recipe.class, id);
        if (recipe == null) {
            return notFound("recipe " + id);
        }
        return Response.ok(toDto(recipe)).build();
    }

    @POST
    @Transactional
    public Response create(RecipeDto dto) {
        Response validation = validate(dto);
        if (validation != null) return validation;

        Recipe recipe = new Recipe();
        applyFields(recipe, dto);

        Response itemsError = applyItems(recipe, dto);
        if (itemsError != null) return itemsError;

        em.persist(recipe);
        em.flush();

        liveUpdates.fire(LiveUpdate.recipe("created", recipe.getId()));
        return Response.status(Response.Status.CREATED)
                .entity(toDto(recipe))
                .build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, RecipeDto dto) {
        Recipe recipe = em.find(Recipe.class, id);
        if (recipe == null) {
            return notFound("recipe " + id);
        }

        Response validation = validate(dto);
        if (validation != null) return validation;

        applyFields(recipe, dto);

        recipe.getItems().clear();
        Response itemsError = applyItems(recipe, dto);
        if (itemsError != null) return itemsError;

        liveUpdates.fire(LiveUpdate.recipe("updated", id));
        return Response.ok(toDto(recipe)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        Recipe recipe = em.find(Recipe.class, id);
        if (recipe == null) {
            return notFound("recipe " + id);
        }
        em.remove(recipe);
        liveUpdates.fire(LiveUpdate.recipe("deleted", id));
        return Response.noContent().build();
    }

    /**
     * Workflow 1 from the brief: work out what's missing to cook this
     * recipe and add the shortfall to the shared shopping list.
     */
    @POST
    @Path("/{id}/plan")
    public Response plan(@PathParam("id") Long id, ActingUserRequest request) {
        try {
            Long userId = request == null ? null : request.getUserId();
            List<ShoppingItemDto> added = shoppingListService.planForRecipe(id, userId);
            return Response.ok(added).build();
        } catch (RecipeNotFoundException | UserNotFoundException e) {
            return notFound(e.getMessage());
        }
    }

    private Response validate(RecipeDto dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            return badRequest("Recipe name is required.");
        }
        if (dto.getServings() < 1) {
            return badRequest("Servings must be at least 1.");
        }
        return null;
    }

    private void applyFields(Recipe recipe, RecipeDto dto) {
        recipe.setName(dto.getName().trim());
        recipe.setServings(dto.getServings());
        recipe.setNotes(dto.getNotes());
    }

    private Response applyItems(Recipe recipe, RecipeDto dto) {
        if (dto.getItems() == null) return null;
        for (RecipeItemDto itemDto : dto.getItems()) {
            Ingredient ingredient = em.find(Ingredient.class, itemDto.getIngredientId());
            if (ingredient == null) {
                return badRequest("Unknown ingredient id: " + itemDto.getIngredientId());
            }
            if (itemDto.getQty() < 0) {
                return badRequest("Ingredient quantity cannot be negative.");
            }
            RecipeIngredient ri = new RecipeIngredient();
            ri.setIngredient(ingredient);
            ri.setQty(itemDto.getQty());
            recipe.addItem(ri);
        }
        return null;
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