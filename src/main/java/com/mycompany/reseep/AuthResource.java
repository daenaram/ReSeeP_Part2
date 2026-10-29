package com.mycompany.reseep;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * POST /api/login. This is intentionally the only thing in this class:
 * read the request, call the service, translate the result or exception
 * into an HTTP response. All the actual rules live in UserService.
 */
@Path("/login")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    private UserService userService;

    @POST
    public Response login(LoginRequest request) {
        if (request == null) {
            return badRequest("Request body is required.");
        }
        try {
            UserDto user = userService.login(request.getUsername(), request.getPassword());
            return Response.ok(user).build();
        } catch (InvalidCredentialsException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"" + message.replace("\"", "'") + "\"}")
                .build();
    }
}
