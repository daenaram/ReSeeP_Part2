package com.mycompany.reseep;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

/**
 * Relays LiveUpdate events fired by the REST resources to WebSocket clients.
 *
 * AFTER_SUCCESS means the broadcast only happens once the transaction has
 * committed: clients that re-fetch on receipt are guaranteed to see the new
 * data, and a rolled-back write never produces a notification.
 */
@ApplicationScoped
public class LiveUpdateBroadcaster {

    public void onUpdate(@Observes(during = TransactionPhase.AFTER_SUCCESS) LiveUpdate update) {
        LiveUpdateEndpoint.broadcast(update.toJson());
    }
}
