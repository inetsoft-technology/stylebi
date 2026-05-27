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

package inetsoft.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

public class StompLoggingInterceptor implements ExecutorChannelInterceptor {

   @Override
   public Message<?> beforeHandle(Message<?> message, MessageChannel channel,
                                  MessageHandler handler)
   {
      if(LOG.isDebugEnabled()) {
         StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
         LOG.debug("Received message for destination {}", accessor.getDestination());
      }

      return message;
   }

   @Override
   public void afterMessageHandled(Message<?> message, MessageChannel channel,
                                   MessageHandler handler, Exception ex)
   {
      if(LOG.isDebugEnabled()) {
         StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
         LOG.debug("Message handled for destination {}", accessor.getDestination());
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(StompLoggingInterceptor.class);
}
