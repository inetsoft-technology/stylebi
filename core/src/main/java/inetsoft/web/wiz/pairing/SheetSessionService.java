/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.pairing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Service
public class SheetSessionService {
   public static final long TTL_MILLIS = 30 * 60_000L;
   private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

   private final ConcurrentHashMap<String, JoinSession> sessions;
   private final SecureRandom random = new SecureRandom();
   private final LongSupplier clock;

   public SheetSessionService() { this(System::currentTimeMillis); }

   SheetSessionService(LongSupplier clock) {
      this.clock = clock;
      this.sessions = new ConcurrentHashMap<>();
   }

   /** Test constructor: shares the sessions map of an existing service with a different clock. */
   SheetSessionService(LongSupplier clock, SheetSessionService source) {
      this.clock = clock;
      this.sessions = source.sessions;
   }

   public JoinSession open(String runtimeId, String ownerIdentity, SheetType sheetType,
                           String socketSessionId, String socketUserName,
                           EditorContext editorContext)
   {
      String token = newToken();
      JoinSession s = new JoinSession(token, runtimeId, ownerIdentity, sheetType,
                                      clock.getAsLong(), TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
                                      socketSessionId, socketUserName, editorContext);
      sessions.put(token, s);
      return s;
   }

   /** Returns the session (TTL refreshed) iff present, unexpired, and owned by agentIdentity; else null. */
   public JoinSession resolve(String token, String agentIdentity) {
      if (token == null) return null;
      JoinSession s = sessions.get(token);
      if (s == null || s.isExpired(clock.getAsLong()) || !s.ownerIdentity().equals(agentIdentity)) return null;
      JoinSession refreshed = new JoinSession(s.sessionToken(), s.runtimeId(), s.ownerIdentity(),
                                              s.sheetType(), clock.getAsLong(), s.ttlMillis(),
                                              s.connectionMode(), s.socketSessionId(),
                                              s.socketUserName(), s.editorContext());
      sessions.put(token, refreshed);
      return refreshed;
   }

   public void close(String token) { if (token != null) sessions.remove(token); }

   /**
    * Ends every pane-scoped session bound to {@code socketSessionId} -- called when that
    * WebSocket session disconnects (crash, network drop, or a killed tab; see
    * {@code SheetSessionSocketCleanup}). A pane-scoped session (nullable
    * {@link JoinSession#editorContext()} non-null) is meant to live only "while a script pane
    * is active"; once its socket is gone, that is no longer knowable to be true, so it is
    * expired immediately rather than given a reconnect grace period. A user who legitimately
    * blips and reconnects just re-pairs -- cheap, one click -- versus leaving a live write
    * handle open for an unknown gap, which is the exact hole this task exists to close.
    *
    * <p>Whole-sheet (editorContext == null) sessions are deliberately left untouched here --
    * they keep today's TTL-only lifetime so a toolbar-paired user does not lose their session
    * to a transient socket blip. This is the regression this method must never cause.
    */
   public void socketClosed(String socketSessionId) {
      if(socketSessionId == null) return;
      sessions.values().removeIf(
         s -> s.editorContext() != null && socketSessionId.equals(s.socketSessionId()));
   }

   /**
    * Ends the one pane-scoped session bound to both {@code socketSessionId} and
    * {@code editorContext}, if any -- called explicitly when the script pane or formula editor
    * that produced it is destroyed (its dialog closed or cancelled). Scoped to the exact
    * {@code editorContext} match, not just the socket, so closing one pane does not end a
    * session paired from a different pane/dialog still open on the same browser socket.
    *
    * <p>Distinct from {@link #socketClosed}, which reacts to the transport itself going away
    * (crash/network drop) rather than a deliberate, in-app close.
    */
   public void detach(String socketSessionId, EditorContext editorContext) {
      if(socketSessionId == null || editorContext == null) return;
      sessions.values().removeIf(
         s -> editorContext.equals(s.editorContext()) && socketSessionId.equals(s.socketSessionId()));
   }

   /**
    * Returns an unexpired session of {@code sheetType} owned by {@code ownerIdentity}, or null if
    * none is held.
    *
    * <p>Used to refuse silently replacing a session the user may be editing -- e.g.
    * {@code open_base_worksheet} refusing to open a second worksheet runtime out from under one
    * already paired, naming its runtime id instead of ending it with a success response.
    */
   public JoinSession findOpen(String ownerIdentity, SheetType sheetType) {
      if(ownerIdentity == null) {
         return null;
      }

      long now = clock.getAsLong();

      for(JoinSession s : sessions.values()) {
         if(!s.isExpired(now) && s.sheetType() == sheetType &&
            ownerIdentity.equals(s.ownerIdentity()))
         {
            return s;
         }
      }

      return null;
   }

   @Scheduled(fixedDelay = 10 * 60_000)
   void evictExpired() {
      long now = clock.getAsLong();
      sessions.values().removeIf(s -> s.isExpired(now));
   }

   private String newToken() {
      StringBuilder sb = new StringBuilder(24);
      for (int i = 0; i < 24; i++) {
         sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      }
      return sb.toString();
   }
}
