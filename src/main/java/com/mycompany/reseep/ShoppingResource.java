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
            return notFound(e.getMessage());
        } catch (ItemAlreadyBoughtException e) {
            return conflict(e.getMessage());
        }
    }

    private Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"" + message.replace("\"", "'") + "\"}")
                .build();
    }

    private Response conflict(String message) {
        return Response.status(Response.Status.CONFLICT)
                .entity("{\"error\":\"" + message.replace("\"", "'") + "\"}")
                .build();
    }
}
