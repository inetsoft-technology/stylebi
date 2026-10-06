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
package inetsoft.web.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;

/**
 * Channel interceptor that hands the events one HTTP session sends for one runtime sheet to the
 * client inbound channel's thread pool one at a time, in the order they were received. The next
 * event of the sheet is released only after every subscriber has finished handling the previous
 * one. Events for other sheets, frames without a sheet runtime id (subscriptions, heartbeats),
 * the cancel events and the flyover events are not held, so a cancel can still reach the query
 * it is meant to stop and a newer flyover can still cancel an older one (Bug #77155).
 * <p>
 * Without this, the events run concurrently on the pool and a later event (e.g. a selection delta
 * or a range slider range) can be applied before an earlier one (Bug #77887). Spring's
 * {@code setPreserveReceiveOrder} would order the whole session, which is shared by every sheet
 * of a browser window and would also queue the cancel events. The events are ordered by HTTP
 * session rather than STOMP session, so the events a client sends after it reconnects are still
 * applied after the ones it sent before. The held events of a disconnected STOMP session are not
 * dropped, the client reconnects and keeps using its sheets.
 * <p>
 * The periodic touch-asset event is not queued again while the same event is held for the sheet
 * with only touch-asset events after it, so a refresh that takes longer than the refresh interval
 * cannot build a backlog, and a touch-asset (e.g. an auto save) is never moved before a later
 * event. A close is not held behind the running event unless other events are held, so closing a
 * busy sheet still cancels its query without overtaking a held event (e.g. a save) sent before the
 * close.
 * <p>
 * The tickets are kept out of the message headers, which are copied into the
 * {@code ServiceProxyContext} of cluster calls. Only the ticket id is put in a header.
 * <p>
 * This must be the first interceptor of the channel, so a held event has not been through the
 * other interceptors yet when it is released.
 */
public class SheetEventOrderInterceptor implements ExecutorChannelInterceptor {
   @Override
   public Message<?> preSend(Message<?> message, MessageChannel channel) {
      MessageHeaders headers = message.getHeaders();

      if(headers.get(TICKET_HEADER) instanceof Long id) {
         // released by sendNext(), count the subscribers it is now dispatched to
         Ticket ticket = tickets.get(id);

         if(ticket != null) {
            ticket.subscriberCount = getSubscriberCount(channel);
         }

         return message;
      }

      Key key = getOrderKey(message);
      SimpMessageHeaderAccessor accessor =
         MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
      int subscriberCount = getSubscriberCount(channel);

      if(key == null || accessor == null || !accessor.isMutable() || subscriberCount == 0) {
         return message;
      }

      String destination = SimpMessageHeaderAccessor.getDestination(headers);
      Ticket ticket = new Ticket(nextId.incrementAndGet(), key, message);
      ticket.subscriberCount = subscriberCount;
      // set before the ticket is queued, sendNext() may send the message as soon as it is
      accessor.setHeader(TICKET_HEADER, ticket.id);
      AtomicReference<Action> action = new AtomicReference<>(Action.HOLD);

      queues.compute(key, (k, queue) -> {
         if(queue == null) {
            queue = new ArrayDeque<>();
            action.set(Action.SEND);
         }
         else if(CLOSE_DESTINATIONS.contains(destination) && queue.size() == 1) {
            // only the running event is queued, let the close cancel it
            action.set(Action.SEND_UNORDERED);
            return queue;
         }
         else if(COALESCED_DESTINATIONS.contains(destination) && isHeld(queue, message)) {
            action.set(Action.DROP);
            return queue;
         }

         queue.add(ticket);
         tickets.put(ticket.id, ticket);
         return queue;
      });

      switch(action.get()) {
      case SEND:
         return message;
      case SEND_UNORDERED:
         accessor.removeHeader(TICKET_HEADER);
         return message;
      default:
         // returning null holds the event (it is sent when the previous one is handled) or
         // drops a duplicate of a held event
         return null;
      }
   }

   @Override
   public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent,
                                   Exception ex)
   {
      // the event will never be handled (rejected by another interceptor or failed to send)
      if(!sent && getTicket(message) instanceof Ticket ticket) {
         sendNext(ticket, channel);
      }
   }

   @Override
   public void afterMessageHandled(Message<?> message, MessageChannel channel,
                                   MessageHandler handler, Exception ex)
   {
      if(getTicket(message) instanceof Ticket ticket &&
         ticket.handled.incrementAndGet() == ticket.subscriberCount)
      {
         sendNext(ticket, channel);
      }
   }

   private void sendNext(Ticket done, MessageChannel channel) {
      AtomicReference<Ticket> next = new AtomicReference<>();

      queues.computeIfPresent(done.key, (k, queue) -> {
         if(queue.peek() == done) {
            queue.poll();
            tickets.remove(done.id);
            next.set(queue.peek());
         }

         return queue.isEmpty() ? null : queue;
      });

      if(next.get() != null) {
         try {
            channel.send(next.get().message);
         }
         catch(Exception e) {
            // afterSendCompletion() has released the event after it
            LOG.error("Failed to send held event {}", next.get().message, e);
         }
      }
   }

   private Ticket getTicket(Message<?> message) {
      return message.getHeaders().get(TICKET_HEADER) instanceof Long id ? tickets.get(id) : null;
   }

   /**
    * Check if an event with the same destination and payload is held (not running) in the queue
    * and only coalesced events are held after it, so dropping the new event moves nothing before
    * an event that changes the sheet.
    */
   private static boolean isHeld(Deque<Ticket> queue, Message<?> message) {
      String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
      Iterator<Ticket> iterator = queue.descendingIterator();

      // stop before the running event
      for(int i = queue.size() - 1; i > 0; i--) {
         Message<?> held = iterator.next().message;
         String heldDestination = SimpMessageHeaderAccessor.getDestination(held.getHeaders());

         if(Objects.equals(destination, heldDestination) &&
            Objects.deepEquals(message.getPayload(), held.getPayload()))
         {
            return true;
         }

         if(!COALESCED_DESTINATIONS.contains(heldDestination)) {
            return false;
         }
      }

      return false;
   }

   private static int getSubscriberCount(MessageChannel channel) {
      return channel instanceof ExecutorSubscribableChannel execChannel ?
         execChannel.getSubscribers().size() : 0;
   }

   private static Key getOrderKey(Message<?> message) {
      MessageHeaders headers = message.getHeaders();

      if(SimpMessageHeaderAccessor.getMessageType(headers) != SimpMessageType.MESSAGE) {
         return null;
      }

      String sessionId = getSessionId(headers);
      String destination = SimpMessageHeaderAccessor.getDestination(headers);
      String runtimeId = SimpMessageHeaderAccessor.getFirstNativeHeader("sheetRuntimeId", headers);

      if(sessionId == null || runtimeId == null || UNORDERED_DESTINATIONS.contains(destination)) {
         return null;
      }

      return new Key(sessionId, runtimeId);
   }

   /**
    * Get the HTTP session of the event, which a reconnected STOMP session keeps, or the STOMP
    * session if there is none.
    */
   private static String getSessionId(MessageHeaders headers) {
      Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(headers);
      Object httpSessionId = attributes == null ? null :
         attributes.get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);

      return httpSessionId != null ?
         "http:" + httpSessionId : SimpMessageHeaderAccessor.getSessionId(headers);
   }

   // for testing
   int getPendingSheetCount() {
      return queues.size();
   }

   // for testing
   int getQueuedEventCount() {
      return tickets.size();
   }

   private record Key(String sessionId, String runtimeId) {
   }

   private enum Action { SEND, SEND_UNORDERED, HOLD, DROP }

   private static final class Ticket {
      Ticket(long id, Key key, Message<?> message) {
         this.id = id;
         this.key = key;
         this.message = message;
      }

      @Override
      public String toString() {
         return "Ticket[" + key + "]";
      }

      private final long id;
      private final Key key;
      private final Message<?> message;
      // the subscribers the event was dispatched to, set when it is sent
      private volatile int subscriberCount;
      private final AtomicInteger handled = new AtomicInteger(0);
   }

   private final Map<Key, Deque<Ticket>> queues = new ConcurrentHashMap<>();
   private final Map<Long, Ticket> tickets = new ConcurrentHashMap<>();
   private final AtomicLong nextId = new AtomicLong();

   private static final String TICKET_HEADER = SheetEventOrderInterceptor.class.getName() + ".ticket";
   // events that cancel the running query of the sheet, they must not wait for it, and the
   // flyover events, a newer flyover cancels the query of an older one (Bug #77155). Each
   // endpoint has a comment pointing here, add one to a new endpoint of this kind.
   private static final Set<String> UNORDERED_DESTINATIONS = Set.of(
      "/events/composer/viewsheet/cancelViewsheet",
      "/events/vschart/cancel-query",
      "/events/composer/worksheet/cancel-loading",
      "/events/composer/worksheet/query/stop",
      "/events/composer/ws/join/cancel-ws-join/",
      "/events/vs/wizard/use-meta",
      "/events/vschart/flyover",
      "/events/table/flyover");
   // events that close the sheet and cancel its running query
   private static final Set<String> CLOSE_DESTINATIONS = Set.of(
      "/events/composer/viewsheet/close",
      "/events/ws/close",
      "/events/close");
   // periodic events, a copy of one that is already held is dropped
   private static final Set<String> COALESCED_DESTINATIONS = Set.of(
      "/events/composer/touch-asset");
   private static final Logger LOG = LoggerFactory.getLogger(SheetEventOrderInterceptor.class);
}
