package com.mycompany.reseep;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
    @DELETE
@Path("/{id}")
public Response delete(@PathParam("id") Long id) {
    return deleteIds(List.of(id));
}

@POST
@Path("/batch-delete")
public Response deleteBatch(BatchDeleteRequest request) {
    return deleteIds(request == null ? null : request.getIds());
}

private Response deleteIds(List<Long> ids) {
    try {
        pantryService.deleteAll(ids);
        return Response.noContent().build();
    } catch (ValidationException e) {
        return ApiErrors.badRequest(e.getMessage());
    } catch (IngredientNotFoundException e) {
        return ApiErrors.notFound(e.getMessage());
    } catch (IngredientInUseException e) {
        return ApiErrors.conflict(e.getMessage());
    }
}
}
