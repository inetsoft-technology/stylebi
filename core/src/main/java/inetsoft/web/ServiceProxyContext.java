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

import inetsoft.util.*;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.*;

public class ServiceProxyContext {
   private final Principal contextPrincipal;
   private final Set<String> threadContextRecords;
   private final UUID messageId;
   private final Long messageTimestamp;
   private final Map<String, Object> messageHeaders;
   private final Map<String, Object> messageAttributes;
   private final List<UserMessage> userMessages;

   public ServiceProxyContext() {
      this.contextPrincipal = ThreadContext.getPrincipal();
      this.userMessages = new ArrayList<>();
      this.threadContextRecords = new HashSet<>();

      if(Thread.currentThread() instanceof GroupedThread gt) {
         for(Object record : gt.getRecords()) {
            if(record instanceof String str) {
               threadContextRecords.add(str);
            }
         }
      }

      MessageAttributes messageAttrs = MessageContextHolder.getMessageAttributes();

      if(messageAttrs != null) {
         this.messageAttributes = new HashMap<>(messageAttrs.getAttributes());
         Message<?> message = messageAttrs.getMessage();

         if(message != null) {
            this.messageId = message.getHeaders().getId();
            this.messageTimestamp = message.getHeaders().getTimestamp();
            this.messageHeaders = new HashMap<>(message.getHeaders());
         }
         else {
            this.messageId = null;
            this.messageTimestamp = null;
            this.messageHeaders = null;
         }
      }
      else {
         this.messageAttributes = null;
         this.messageId = null;
         this.messageTimestamp = null;
         this.messageHeaders = null;
      }
   }

   public void apply() {
      if(Thread.currentThread() instanceof GroupedThread gt) {
         for(Object record : threadContextRecords) {
            gt.addRecord(record);
         }
      }

      MessageAttributes messageAttrs = MessageContextHolder.getMessageAttributes();

      if(messageAttrs != null) {
         if(messageAttributes != null) {
            for(Map.Entry<String, Object> e : messageAttributes.entrySet()) {
               messageAttrs.setAttribute(e.getKey(), e.getValue());
            }
         }

         if(messageHeaders != null) {
            for(Map.Entry<String, Object> e : messageHeaders.entrySet()) {
               messageAttrs.getMessage().getHeaders().put(e.getKey(), e.getValue());
            }
         }
      }

      for(UserMessage message : userMessages) {
         Tool.addUserMessage(message);
      }
   }

   @SuppressWarnings("JavaReflectionMemberAccess")
   public void preprocess() {
      if(contextPrincipal != null) {
         ThreadContext.setContextPrincipal(contextPrincipal);
      }

      if(Thread.currentThread() instanceof GroupedThread gt) {
         for(Object record : threadContextRecords) {
            gt.addRecord(record);
         }
      }

      if(messageId != null) {
         MessageHeaders headers;

         try {
            Constructor<MessageHeaders> cstr =
               MessageHeaders.class.getConstructor(Map.class, UUID.class, Long.class);
            cstr.setAccessible(true);
            headers = cstr.newInstance(messageHeaders, messageId, messageTimestamp);
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to create message headers", e);
         }

         Message<?> message = MessageBuilder.createMessage(new byte[0], headers);
         MessageAttributes messageAttrs = new MessageAttributes(message);

         if(messageAttributes != null) {
            for(Map.Entry<String, Object> entry : messageAttributes.entrySet()) {
               messageAttrs.setAttribute(entry.getKey(), entry.getValue());
            }
         }

         MessageContextHolder.setMessageAttributes(messageAttrs);
      }
   }

   public void postprocess() {
      for(MessageCommand.Type type : MessageCommand.Type.values()) {
         userMessages.addAll(Tool.getUserMessages(type));
      }

      if(messageId != null) {
         if(messageHeaders != null) {
            messageHeaders.clear();
            MessageAttributes messageAttrs = MessageContextHolder.getMessageAttributes();
            messageHeaders.putAll(messageAttrs.getMessage().getHeaders());
         }

         if(messageAttributes != null) {
            messageAttributes.clear();
            MessageAttributes messageAttrs = MessageContextHolder.getMessageAttributes();
            messageAttributes.putAll(messageAttrs.getAttributes());
         }
      }

      threadContextRecords.clear();

      if(Thread.currentThread() instanceof GroupedThread gt) {
         for(Object record : gt.getRecords()) {
            if(record instanceof String str) {
               threadContextRecords.add(str);
            }
         }
      }

      Tool.clearUserMessage();
      ThreadContext.setContextPrincipal(null);
      MessageContextHolder.setMessageAttributes(null);
   }
}
