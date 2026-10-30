package com.mycompany.reseep;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Internal bridge endpoint. The standalone PurchaseConsumer (a separate JVM,
 * entirely outside this web app, with no access to its CDI event bus or
 * open WebSocket sessions) calls this after it finishes updating the
 * pantry, so the existing LiveUpdate/WebSocket mechanism can push the
 * change to connected browsers -- the same mechanism PantryService already
 * uses for direct pantry edits.
 *
 * Not authenticated and not meant for external use, same dev-grade status
 * as /reset -- see README Known limitations.
 */
@Path("/internal/notify-pantry-change")
@Consumes(MediaType.APPLICATION_JSON)
public class InternalNotifyResource {

    @Inject
    private Event<LiveUpdate> liveUpdates;

    @POST
    public Response notify(NotifyRequest request) {
        if (request != null && request.getIngredientId() != null) {
            liveUpdates.fire(LiveUpdate.pantry("updated", request.getIngredientId()));
        }
        return Response.ok().build();
    }
}
