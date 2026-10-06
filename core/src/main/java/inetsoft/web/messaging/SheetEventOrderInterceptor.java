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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;

/**
 * Channel interceptor that hands the events one STOMP session sends for one runtime sheet to the
 * client inbound channel's thread pool one at a time, in the order they were received. The next
 * event of the sheet is released only after every subscriber has finished handling the previous
 * one. Events for other sheets, frames without a sheet runtime id (subscriptions, heartbeats) and
 * the cancel events are not held, so a cancel can still reach the query it is meant to stop.
 * <p>
 * Without this, the events run concurrently on the pool and a later event (e.g. a selection delta
 * or a range slider range) can be applied before an earlier one (Bug #77887). Spring's
 * {@code setPreserveReceiveOrder} would order the whole session, which is shared by every sheet
 * of a browser window and would also queue the cancel events.
 * <p>
 * This must be the first interceptor of the channel, so a held event has not been through the
 * other interceptors yet when it is released.
 */
public class SheetEventOrderInterceptor implements ExecutorChannelInterceptor {
   @Override
   public Message<?> preSend(Message<?> message, MessageChannel channel) {
      if(message.getHeaders().containsKey(TICKET_HEADER)) {
         // released by sendNext()
         return message;
      }

      String key = getOrderKey(message);
      SimpMessageHeaderAccessor accessor =
         MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
      int subscriberCount = channel instanceof ExecutorSubscribableChannel execChannel ?
         execChannel.getSubscribers().size() : 0;

      if(key == null || accessor == null || !accessor.isMutable() || subscriberCount == 0) {
         return message;
      }

      Ticket ticket = new Ticket(key, message, subscriberCount);
      accessor.setHeader(TICKET_HEADER, ticket);
      AtomicBoolean first = new AtomicBoolean(false);

      queues.compute(key, (k, queue) -> {
         if(queue == null) {
            queue = new ArrayDeque<>();
            first.set(true);
         }

         queue.add(ticket);
         return queue;
      });

      // returning null holds the event, it is sent when the previous one is handled
      return first.get() ? message : null;
   }

   @Override
   public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent,
                                   Exception ex)
   {
      // the event will never be handled (rejected by another interceptor or failed to send)
      if(!sent && message.getHeaders().get(TICKET_HEADER) instanceof Ticket ticket) {
         sendNext(ticket, channel);
      }
   }

   @Override
   public void afterMessageHandled(Message<?> message, MessageChannel channel,
                                   MessageHandler handler, Exception ex)
   {
      if(message.getHeaders().get(TICKET_HEADER) instanceof Ticket ticket &&
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

   private static String getOrderKey(Message<?> message) {
      MessageHeaders headers = message.getHeaders();

      if(SimpMessageHeaderAccessor.getMessageType(headers) != SimpMessageType.MESSAGE) {
         return null;
      }

      String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
      String destination = SimpMessageHeaderAccessor.getDestination(headers);
      String runtimeId = SimpMessageHeaderAccessor.getFirstNativeHeader("sheetRuntimeId", headers);

      if(sessionId == null || runtimeId == null || UNORDERED_DESTINATIONS.contains(destination)) {
         return null;
      }

      return sessionId + "|" + runtimeId;
   }

   // for testing
   int getPendingSheetCount() {
      return queues.size();
   }

   private static final class Ticket {
      Ticket(String key, Message<?> message, int subscriberCount) {
         this.key = key;
         this.message = message;
         this.subscriberCount = subscriberCount;
      }

      @Override
      public String toString() {
         return "Ticket[" + key + "]";
      }

      private final String key;
      private final Message<?> message;
      private final int subscriberCount;
      private final AtomicInteger handled = new AtomicInteger(0);
   }

   private final Map<String, Deque<Ticket>> queues = new ConcurrentHashMap<>();

   private static final String TICKET_HEADER = SheetEventOrderInterceptor.class.getName() + ".ticket";
   // events that cancel the running query of the sheet, they must not wait for it
   private static final Set<String> UNORDERED_DESTINATIONS = Set.of(
      "/events/composer/viewsheet/cancelViewsheet",
      "/events/vschart/cancel-query",
      "/events/composer/worksheet/cancel-loading",
      "/events/composer/worksheet/query/stop",
      "/events/composer/ws/join/cancel-ws-join/",
      "/events/vs/wizard/use-meta");
   private static final Logger LOG = LoggerFactory.getLogger(SheetEventOrderInterceptor.class);
}
