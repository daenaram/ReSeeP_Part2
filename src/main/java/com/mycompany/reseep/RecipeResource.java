package com.mycompany.reseep;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Pure HTTP adapter: read the request, call RecipeService, translate the
 * result (or a thrown exception) into a Response. No business rules and
 * no direct database access live in this class any more — see
 * RecipeService for that.
 */
@Path("/recipes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecipeResource {

    @Inject
    private RecipeService recipeService;

    @Inject
    private ShoppingListService shoppingListService;

    @GET
    public List<RecipeDto> list() {
        return recipeService.findAll();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        try {
            return Response.ok(recipeService.findById(id)).build();
        } catch (RecipeNotFoundException e) {
            return ApiErrors.notFound(e.getMessage());
        }
    }

    @POST
    public Response create(RecipeDto dto) {
        try {
            RecipeDto created = recipeService.create(dto);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (ValidationException e) {
            return ApiErrors.badRequest(e.getMessage());
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, RecipeDto dto) {
        try {
            return Response.ok(recipeService.update(id, dto)).build();
        } catch (RecipeNotFoundException e) {
            return ApiErrors.notFound(e.getMessage());
        } catch (ValidationException e) {
            return ApiErrors.badRequest(e.getMessage());
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        try {
            recipeService.delete(id);
            return Response.noContent().build();
        } catch (RecipeNotFoundException e) {
            return ApiErrors.notFound(e.getMessage());
        }
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
            return ApiErrors.notFound(e.getMessage());
        }
    }
}
