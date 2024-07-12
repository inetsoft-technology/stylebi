/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.monitoring;

import inetsoft.util.*;
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
public class MonitoringDataService {
   @Autowired
   public MonitoringDataService(SimpUserRegistry userRegistry,
                                SimpMessagingTemplate messageTemplate)
   {
      this.userRegistry = userRegistry;
      this.messageTemplate = messageTemplate;
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
      final Message<byte[]> message = event.getMessage();
      final MessageHeaders headers = message.getHeaders();
      final String subscriptionId =
         (String) headers.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);

      if(subscriptionId != null) {
         lock.lock();

         try {
            subscribers.removeIf(sub -> subscriptionId.equals(sub.getSubscriptionId()));
         }
         finally {
            lock.unlock();
         }
      }
   }

   /**
    * On disconnect remove all subscribers for the given session.
    */
   @EventListener
   public void handleDisconnect(SessionDisconnectEvent event) {
      final String sessionId = event.getSessionId();

      if(sessionId != null) {
         lock.lock();

         try {
            subscribers.removeIf(sub -> sessionId.equals(sub.getSessionId()));
         }
         finally {
            lock.unlock();
         }
      }
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

      lock.lock();

      try {
         if(sessionId != null && lookupDestination != null && subscriptionId != null) {
            assert !subscribers.contains(subscriber) :
               "monitoring destination already subscribed on this socket, overwriting";
            subscribers.add(subscriber);
         }

         return dataCache.get(subscriber, supplier::get);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Remove inactive sessions then sends messages to the active sessions
    */
   public void update() {
      debouncer.debounce("UPDATE_ALL", 1L, TimeUnit.SECONDS, this::doUpdate);
   }

   private void doUpdate() {
      lock.lock();

      try {
         removeDisconnectedSubscribers();
         dataCache.clear();
         subscribers.forEach(this::sendToSubscriber);
      }
      finally {
         lock.unlock();
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
      lock.lock();

      try {
         subscribers.stream()
            .filter(subscriber -> subscriber.getSessionId().equals(sessionId))
            .forEach(subscriber -> {
               dataCache.evict(subscriber);
               sendToSubscriber(subscriber);
            });
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Send monitoring data to a subscriber. Loads and returns data from the data cache.
    */
   private void sendToSubscriber(MonitoringSubscriber subscriber) {
      final Object monitoringData = dataCache.get(subscriber, subscriber::get);

      if(monitoringData == null) {
         LOG.warn("Monitoring data is missing: " + subscriber.supplier);
         return;
      }

      final String subscriptionId = subscriber.getSubscriptionId();
      final String destination = subscriber.getLookupDestination();
      final String sessionId = subscriber.getSessionId();

      // create headers to send to session
      SimpMessageHeaderAccessor headerAccessor = StompHeaderAccessor.create();
      headerAccessor.setSubscriptionId(subscriptionId);
      headerAccessor.setDestination(destination);
      headerAccessor.setSessionId(sessionId);
      headerAccessor.setLeaveMutable(true);
      headerAccessor.setUser(subscriber.getUser());
      final MessageHeaders headers = headerAccessor.getMessageHeaders();
      messageTemplate.convertAndSendToUser(sessionId, destination, monitoringData, headers);
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

      subscribers.removeIf(subscriber -> {
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

   private static class MonitoringSubscriber {
      public MonitoringSubscriber(String sessionId,
                                  String subscriptionId,
                                  String lookupDestination,
                                  String destination,
                                  Supplier<?> supplier,
                                  Principal principal)
      {
         this.sessionId = sessionId;
         this.subscriptionId = subscriptionId;
         this.lookupDestination = lookupDestination;
         this.destination = destination;
         this.supplier = supplier;
         this.principal = principal;
      }

      public String getSessionId() {
         return sessionId;
      }

      public String getSubscriptionId() {
         return subscriptionId;
      }

      public String getLookupDestination() {
         return lookupDestination;
      }

      public String getDestination() {
         return destination;
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

      public Principal getUser() {
         return principal;
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

         if(!sessionId.equals(that.sessionId)) {
            return false;
         }
         if(!subscriptionId.equals(that.subscriptionId)) {
            return false;
         }
         if(!destination.equals(that.destination)) {
            return false;
         }
         if(!lookupDestination.equals(that.lookupDestination)) {
            return false;
         }
         return supplier.equals(that.supplier);
      }

      @Override
      public int hashCode() {
         int result = sessionId.hashCode();
         result = 31 * result + subscriptionId.hashCode();
         result = 31 * result + destination.hashCode();
         result = 31 * result + lookupDestination.hashCode();
         result = 31 * result + supplier.hashCode();
         return result;
      }

      private final String sessionId;
      private final String subscriptionId;
      private final String destination;
      private final String lookupDestination;
      private final Supplier<?> supplier;
      private final Principal principal;
   }

   private final SimpUserRegistry userRegistry;
   private final SimpMessagingTemplate messageTemplate;
   private final Set<MonitoringSubscriber> subscribers = new HashSet<>();
   private final ConcurrentMapCache dataCache = new ConcurrentMapCache("MonitoringCache");
   private final Lock lock = new ReentrantLock();
   private final Debouncer<String> debouncer;
   private static final Logger LOG = LoggerFactory.getLogger(MonitoringDataService.class);
}
