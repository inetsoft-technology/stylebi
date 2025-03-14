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
package inetsoft.web.admin.monitoring;

import inetsoft.util.*;
import inetsoft.web.service.BaseSubscribeChangHandler;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.DestinationPatternsMessageCondition;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.*;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Encapsulates logic for keeping track of subscribers and dispatching messages to them.
 */
@Service
@Lazy(false)
public class MonitoringDataService extends BaseSubscribeChangHandler {
   @Autowired
   public MonitoringDataService(SimpUserRegistry userRegistry,
                                SimpMessagingTemplate messageTemplate)
   {
      super(messageTemplate);
      this.userRegistry = userRegistry;
      this.debouncer = new DefaultDebouncer<>();
   }

   @PreDestroy
   public void stopDebouncer() throws Exception {
      debouncer.close();
   }

   /**
    * On unsubscribe remove the subscriber that matches the event's subscription ID.
    */
   @EventListener
   public void handleUnsubscribe(SessionUnsubscribeEvent event) {
      super.handleUnsubscribe(event);
   }

   /**
    * On disconnect remove all subscribers for the given session.
    */
   @EventListener
   public void handleDisconnect(SessionDisconnectEvent event) {
      super.handleDisconnect(event);
   }

   /**
    * Subscribe to a monitoring endpoint
    *
    * @param headerAccessor STOMP header accessor for getting the STOMP subscription
    *                       info.
    * @param supplier       supplier function that returns the data for the subscriber.
    *
    * @return the most recent value posted to this topic if not-null, otherwise the result
    * of executing the supplier function.
    */
   public <T> T addSubscriber(StompHeaderAccessor headerAccessor, Supplier<T> supplier) {
      final String sessionId = headerAccessor.getSessionId();
      final MessageHeaders messageHeaders = headerAccessor.getMessageHeaders();
      final String destination = (String) messageHeaders
         .get(SimpMessageHeaderAccessor.DESTINATION_HEADER);
      final String lookupDestination = (String) messageHeaders
         .get(DestinationPatternsMessageCondition.LOOKUP_DESTINATION_HEADER);
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      final MonitoringSubscriber subscriber =
         new MonitoringSubscriber(sessionId, subscriptionId,
                                  lookupDestination, destination, supplier, headerAccessor.getUser());

      return (T) addSubscriber(subscriber);
   }

   @Override
   public Object getData(BaseSubscriber subscriber) {
      MonitoringSubscriber monitoringSubscriber = ((MonitoringSubscriber) subscriber);
      final Object monitoringData = dataCache.get(subscriber, monitoringSubscriber::get);

      if(monitoringData == null) {
         LOG.warn("Monitoring data is missing: " + monitoringSubscriber.supplier);
         return null;
      }

      return monitoringData;
   }

   /**
    * Remove inactive sessions then sends messages to the active sessions
    */
   public void update() {
      debouncer.debounce("UPDATE_ALL", 1L, TimeUnit.SECONDS, this::doUpdate);
   }

   private void doUpdate() {
      lock();

      try {
         removeDisconnectedSubscribers();
         dataCache.clear();
         getSubscribers().forEach(this::sendToSubscriber);
      }
      finally {
         unlock();
      }
   }

   /**
    * Update all subscribers of a websocket session
    */
   public void updateSession(StompHeaderAccessor headerAccessor) {
      updateSession(headerAccessor, 1L, TimeUnit.SECONDS);
   }

   /**
    * Update all subscribers of a websocket session
    */
   public void updateSession(StompHeaderAccessor headerAccessor,  long interval,
                             TimeUnit intervalUnit)
   {
      final MessageHeaders messageHeaders = headerAccessor.getMessageHeaders();
      final String sessionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
      debouncer.debounce(sessionId, interval, intervalUnit, () -> updateSession(sessionId));
   }

   private void updateSession(String sessionId) {
      lock();

      try {
         getSubscribers().stream()
            .filter(subscriber -> subscriber.getSessionId().equals(sessionId))
            .forEach(subscriber -> {
               dataCache.evict(subscriber);
               sendToSubscriber(subscriber);
            });
      }
      finally {
         unlock();
      }
   }

   /**
    * Check subscribers against user registry and remove those without active
    * destination, session ID, or subscription ID
    */
   private void removeDisconnectedSubscribers() {
      final Set<String> sessionIds =
         getSessions().map(SimpSession::getId).collect(Collectors.toSet());
      final Set<String> subscriptionIds =
         getSubscriptions().map(SimpSubscription::getId)
                           .collect(Collectors.toSet());
      final Set<String> destinations =
         getSubscriptions().map(SimpSubscription::getDestination)
                           .collect(Collectors.toSet());

      getSubscribers().removeIf(subscriber -> {
         final String destination = subscriber.getDestination();
         final String sessionId = subscriber.getSessionId();
         final String subscriptionId = subscriber.getSubscriptionId();

         return !destinations.contains(destination) ||
            !sessionIds.contains(sessionId) ||
            !subscriptionIds.contains(subscriptionId);
      });
   }

   /**
    * Get the websocket sessions.
    */
   private Stream<SimpSession> getSessions() {
      return userRegistry.getUsers()
         .stream()
         .flatMap((user) -> user.getSessions().stream());
   }

   /**
    * Get the websocket subscriptions.
    */
   private Stream<SimpSubscription> getSubscriptions() {
      return getSessions()
         .flatMap((session) -> session.getSubscriptions().stream());
   }

   private static class MonitoringSubscriber extends BaseSubscriber {
      public MonitoringSubscriber(String sessionId,
                                  String subscriptionId,
                                  String lookupDestination,
                                  String destination,
                                  Supplier<?> supplier,
                                  Principal principal)
      {
         super(sessionId, subscriptionId, lookupDestination, destination, principal);
         this.supplier = supplier;
      }

      public Object get() {
         ThreadContext.setContextPrincipal(getUser());

         if(Thread.currentThread() instanceof GroupedThread) {
            GroupedThread groupedThread = (GroupedThread) Thread.currentThread();
            groupedThread.setPrincipal(getUser());
         }

         Object result = supplier.get();

         if(Thread.currentThread() instanceof GroupedThread) {
            GroupedThread groupedThread = (GroupedThread) Thread.currentThread();
            groupedThread.setPrincipal(null);
            groupedThread.removeRecords();
         }
         else {
            ThreadContext.setContextPrincipal(null);
         }

         // clear the ThreadContext thread local variables
         ThreadContext.setPrincipal(null);
         ThreadContext.setLocale(null);

         return result;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }
         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         MonitoringSubscriber that = (MonitoringSubscriber) o;

         if(!getSessionId().equals(that.getSessionId())) {
            return false;
         }
         if(!getSubscriptionId().equals(that.getSubscriptionId())) {
            return false;
         }
         if(!getDestination().equals(that.getDestination())) {
            return false;
         }
         if(!getLookupDestination().equals(that.getLookupDestination())) {
            return false;
         }

         return supplier.equals(that.supplier);
      }

      @Override
      public int hashCode() {
         int result = super.hashCode();
         result = result + java.util.Objects.hash(supplier);
         return result;
      }

      private final Supplier<?> supplier;
   }

   private final ConcurrentMapCache dataCache = new ConcurrentMapCache("MonitoringCache");
   private final SimpUserRegistry userRegistry;
   private final Debouncer<String> debouncer;
   private static final Logger LOG = LoggerFactory.getLogger(MonitoringDataService.class);
}
