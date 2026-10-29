package com.mycompany.reseep;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * WebSocket endpoint at ws://host:8080/reseep/ws/updates.
 *
 * Clients only listen; they never send anything. Every successful write to
 * the pantry or recipes is broadcast here (see LiveUpdateBroadcaster) so any
 * open web page can refresh without polling.
 */
@ServerEndpoint("/ws/updates")
public class LiveUpdateEndpoint {

    private static final Logger LOG = Logger.getLogger(LiveUpdateEndpoint.class.getName());
    private static final Set<Session> SESSIONS = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
        LOG.info("[LiveUpdate] client connected (" + SESSIONS.size() + " open)");
    }

    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(session);
        LOG.info("[LiveUpdate] client disconnected (" + SESSIONS.size() + " open)");
    }

    @OnError
    public void onError(Session session, Throwable error) {
        SESSIONS.remove(session);
        LOG.warning("[LiveUpdate] session error: " + error.getMessage());
    }

    static void broadcast(String json) {
        for (Session session : SESSIONS) {
            if (!session.isOpen()) {
                SESSIONS.remove(session);
                continue;
            }
            // A session's basic remote isn't safe for concurrent sends, and two
            // requests can finish at the same moment, so serialise per session.
            synchronized (session) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    SESSIONS.remove(session);
                    LOG.warning("[LiveUpdate] dropping session after send failure: " + e.getMessage());
                }
            }
        }
    }
}
