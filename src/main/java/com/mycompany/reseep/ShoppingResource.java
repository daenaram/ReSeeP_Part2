package com.mycompany.reseep;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/shopping")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ShoppingResource {

    @Inject
    private ShoppingListService shoppingListService;

    @GET
    public List<ShoppingItemDto> list() {
        return shoppingListService.list();
    }

    @POST
    @Path("/{id}/bought")
    public Response markBought(@PathParam("id") Long id, ActingUserRequest request) {
        try {
            Long userId = request == null ? null : request.getUserId();
            ShoppingItemDto dto = shoppingListService.markBought(id, userId);
            return Response.ok(dto).build();
        } catch (ShoppingItemNotFoundException | UserNotFoundException e) {
            return ApiErrors.notFound(e.getMessage());
        } catch (ItemAlreadyBoughtException e) {
            return ApiErrors.conflict(e.getMessage());
        }
    }
}
