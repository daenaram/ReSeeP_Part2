package com.mycompany.reseep;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/login")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    private UserService userService;

    @POST
    public Response login(LoginRequest request) {
        if (request == null) {
            return ApiErrors.badRequest("Request body is required.");
        }
        try {
            UserDto user = userService.login(request.getUsername(), request.getPassword());
            return Response.ok(user).build();
        } catch (InvalidCredentialsException e) {
            return ApiErrors.unauthorized(e.getMessage());
        }
    }
}
