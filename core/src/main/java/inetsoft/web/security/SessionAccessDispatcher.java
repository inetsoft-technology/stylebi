/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.security;

import inetsoft.util.GroupedThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * {@code SessionAccessDispatcher} handles notifying subscribers when an HTTP request or websocket
 * message is received for a user session.
 */
public final class SessionAccessDispatcher {
   /**
    * Notify subscribers that a user session has been accessed.
    *
    * @param source            the source of the event.
    * @param principal         a supplier for the principal that identifies the remote user.
    * @param sessionId         a supplier for the user session identifier.
    * @param sessionAttributes a supplier for the session attribute map.
    */
   public static void access(Object source, Supplier<Principal> principal,
                             Supplier<String> sessionId,
                             Supplier<Map<String, Object>> sessionAttributes)
   {
      if(!listeners.isEmpty()) {
         synchronized(lastAccess) {
            String id = sessionId.get();
            long now = System.currentTimeMillis();
            Long last = lastAccess.get(id);

            if(last == null || now - last > 30000L) {
               lastAccess.put(id, now);
               SessionAccessEvent event = new SessionAccessEvent(
                  source, principal.get(), id, sessionAttributes.get());
               queue.offer(event); // NOSONAR queue is unbounded
            }
         }
      }
   }

   /**
    * Adds a listener that is notified when a session is accessed.
    *
    * @param listener the listener to add.
    */
   public static void addListener(SessionAccessListener listener) {
      listeners.add(listener);
   }

   /**
    * Removes a listener from the notification list.
    *
    * @param listener the listener to remove.
    */
   public static void removeListener(SessionAccessListener listener) {
      listeners.remove(listener);
   }

   private static void dispatch() {
      while(!dispatchThread.isCancelled()) {
         try {
            dispatch(queue.take());
         }
         catch(InterruptedException e) { // NOSONAR only break the loop if thread is cancelled
            if(!dispatchThread.isCancelled()) {
               LOG.error("Session access dispatcher thread was interrupted", e);
            }
         }
      }
   }

   private static void dispatch(SessionAccessEvent event) {
      for(SessionAccessListener listener : listeners) {
         listener.sessionAccessed(event);
      }
   }

   private static final BlockingQueue<SessionAccessEvent> queue = new LinkedBlockingQueue<>();
   private static final Map<String, Long> lastAccess = new LinkedHashMap<String, Long>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
         return System.currentTimeMillis() - eldest.getValue() > 30000L;
      }
   };
   private static final Set<SessionAccessListener> listeners = new CopyOnWriteArraySet<>();
   private static final GroupedThread dispatchThread =
      new GroupedThread(SessionAccessDispatcher::dispatch, "SessionAccessDispatcher");
   private static final Logger LOG = LoggerFactory.getLogger(SessionAccessDispatcher.class);

   static {
      dispatchThread.start();
   }

   /**
    * Listener that is notified when an HTTP request or websocket message was received for a user
    * session.
    */
   @FunctionalInterface
   public interface SessionAccessListener extends EventListener {
      /**
       * Called when an HTTP request or websocket message was received for a user session.
       *
       * @param event the event object.
       */
      void sessionAccessed(SessionAccessEvent event);
   }

   /**
    * Event that signals that an HTTP request or websocket message was received for a user session.
    */
   public static final class SessionAccessEvent extends EventObject {
      /**
       * Creates a new instance of {@code SessionAccessEvent}.
       *
       * @param source            the event source.
       * @param principal         a principal that identifies the remote user.
       * @param sessionId         the user session identifier.
       * @param sessionAttributes the user session attributes.
       */
      public SessionAccessEvent(Object source, Principal principal, String sessionId,
                                Map<String, Object> sessionAttributes)
      {
         super(source);
         this.principal = principal;
         this.sessionId = sessionId;
         this.sessionAttributes = sessionAttributes;
      }

      /**
       * Gets the principal that identifies the remote user that sent the HTTP request or websocket
       * message.
       *
       * @return the remote user principal.
       */
      public Principal getPrincipal() {
         return principal;
      }

      /**
       * Gets the identifier of the user session.
       *
       * @return the session identifier.
       */
      public String getSessionId() {
         return sessionId;
      }

      public Map<String, Object> getSessionAttributes() {
         return sessionAttributes;
      }

      private final transient Principal principal;
      private final transient String sessionId;
      private final transient Map<String, Object> sessionAttributes;
   }
}
