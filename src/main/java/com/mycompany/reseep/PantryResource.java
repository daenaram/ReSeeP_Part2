package com.mycompany.reseep;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/pantry")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PantryResource {

    @Inject
    private PantryService pantryService;

    @GET
    public List<IngredientDto> list() {
        return pantryService.findAll();
    }

    @POST
    public Response create(IngredientDto dto) {
        try {
            IngredientDto created = pantryService.create(dto);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (ValidationException e) {
            return ApiErrors.badRequest(e.getMessage());
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") Long id, IngredientDto dto) {
        try {
            return Response.ok(pantryService.update(id, dto)).build();
        } catch (IngredientNotFoundException e) {
            return ApiErrors.notFound(e.getMessage());
        } catch (ValidationException e) {
            return ApiErrors.badRequest(e.getMessage());
        }
    }
}
