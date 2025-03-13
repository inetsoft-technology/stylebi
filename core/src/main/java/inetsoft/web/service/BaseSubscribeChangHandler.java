/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class BaseSubscribeChangHandler {
   public BaseSubscribeChangHandler(SimpMessagingTemplate messageTemplate) {
      this.messageTemplate = messageTemplate;
   }

   /**
    * On unsubscribe remove the subscriber that matches the event's subscription ID.
    */
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
    * @param subscriber the subscription.
    *
    * @return the most recent value posted to this topic if not-null, otherwise the result
    * of executing the supplier function.
    */
   public Object addSubscriber(BaseSubscriber subscriber) {
      lock.lock();

      try {
         if(subscriber != null && subscriber.getSessionId() != null &&
            subscriber.getLookupDestination() != null && subscriber.getSubscriptionId()!= null)
         {
            assert !subscribers.contains(subscriber) :
               "destination already subscribed on this socket, overwriting";
            subscribers.add(subscriber);
         }

         return getData(subscriber);
      }
      finally {
         lock.unlock();
      }
   }

   public abstract Object getData(BaseSubscriber subscriber);

   public void lock() {
      lock.lock();
   }

   public void unlock() {
      lock.unlock();
   }

   public Set<BaseSubscriber> getSubscribers() {
      return subscribers;
   }

   /**
    * Send monitoring data to a subscriber. Loads and returns data from the data cache.
    */
   protected void sendToSubscriber(BaseSubscriber subscriber) {
      final Object monitoringData = getData(subscriber);

      if(monitoringData == null) {
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

   public static class BaseSubscriber {
      public BaseSubscriber(String sessionId,
                                  String subscriptionId,
                                  String lookupDestination,
                                  String destination,
                                  Principal principal)
      {
         this.sessionId = sessionId;
         this.subscriptionId = subscriptionId;
         this.lookupDestination = lookupDestination;
         this.destination = destination;
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

         BaseSubscriber that = (BaseSubscriber) o;

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

         return true;
      }

      @Override
      public int hashCode() {
         int result = sessionId.hashCode();
         result = 31 * result + subscriptionId.hashCode();
         result = 31 * result + destination.hashCode();
         result = 31 * result + lookupDestination.hashCode();
         return result;
      }

      private final String sessionId;
      private final String subscriptionId;
      private final String destination;
      private final String lookupDestination;
      private final Principal principal;
   }

   private final SimpMessagingTemplate messageTemplate;
   private final Set<BaseSubscriber> subscribers = new HashSet<>();
   private final Lock lock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(BaseSubscribeChangHandler.class);
}
