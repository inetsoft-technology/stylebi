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
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRefServiceProxy;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpAttributes;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

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
   private final List<AspectTask> tasks;
   private final boolean async;
   private CommandDispatcher dispatcher;
   private MessageAttributes previousMessageAttributes;
   public static final ThreadLocal<List<AspectTask>> aspectTasks =
      ThreadLocal.withInitial(ArrayList::new);

   public ServiceProxyContext(boolean async) {
      this.contextPrincipal = ThreadContext.getPrincipal();
      this.userMessages = new ArrayList<>();
      this.threadContextRecords = new HashSet<>();
      this.tasks = new ArrayList<>(aspectTasks.get());
      this.async = async;

      if(Thread.currentThread() instanceof GroupedThread gt) {
         for(Object record : gt.getRecords()) {
            if(record instanceof String str) {
               threadContextRecords.add(str);
            }
         }
      }

      MessageAttributes messageAttrs = MessageContextHolder.getMessageAttributes();

      if(messageAttrs != null) {
         this.messageAttributes = new HashMap<>();
         filterAttributes(messageAttrs.getAttributes(), this.messageAttributes);
         Message<?> message = messageAttrs.getMessage();

         if(message != null) {
            UUID messageId = message.getHeaders().getId();

            if(messageId == null) {
               messageId = MessageHeaders.ID_VALUE_NONE;
            }

            this.messageId = messageId;
            this.messageTimestamp = message.getHeaders().getTimestamp();
            this.messageHeaders = new HashMap<>();
            filterAttributes(message.getHeaders(), this.messageHeaders);
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

   public boolean isAsync() {
      return async;
   }

   @SuppressWarnings("unchecked")
   private static void filterAttributes(Map<String, Object> source, Map<String, Object> target) {
      for(Map.Entry<String, Object> e : source.entrySet()) {
         String key = e.getKey();

         if(!key.startsWith(SimpAttributes.DESTRUCTION_CALLBACK_NAME_PREFIX)) {
            Object value = e.getValue();

            if(value instanceof Map) {
               Map<String, Object> valueMap = new HashMap<>();
               filterAttributes((Map<String, Object>) value, valueMap);
               value = valueMap;
            }

            target.put(key, value);
         }
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
            LoggerFactory.getLogger(getClass()).debug(
               "APPLY MESSAGE HEADERS: old={}, new={}",
               messageAttrs.getMessage().getHeaders(), messageHeaders);
         }
      }

      for(UserMessage message : userMessages) {
         Tool.addUserMessage(message);
      }

      for(AspectTask task : tasks) {
         task.apply();
      }
   }

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
         // Save the previous message attributes so we can restore them in postprocess()
         previousMessageAttributes = MessageContextHolder.getMessageAttributes();

         MessageHeaders headers;

         try {
            Constructor<MessageHeaders> cstr =
               MessageHeaders.class.getDeclaredConstructor(Map.class, UUID.class, Long.class);
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

      for(AspectTask task : tasks) {
         task.preprocess(createCommandDispatcher(), contextPrincipal);
      }
   }

   public void postprocess() {
      for(AspectTask task : tasks) {
         task.postprocess(createCommandDispatcher(), contextPrincipal);
      }

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

      // Restore the previous message attributes instead of clearing
      // This allows nested ServiceProxyContext calls to work correctly
      if(messageId != null) {
         MessageContextHolder.setMessageAttributes(previousMessageAttributes);
      }
   }

   public RuntimeViewsheetRef createRuntimeViewsheetRef() {
      return new RuntimeViewsheetRef(ConfigurationContext.getContext()
                                        .getSpringBean(RuntimeViewsheetRefServiceProxy.class));
   }

   @SuppressWarnings("unchecked")
   public synchronized CommandDispatcher createCommandDispatcher() {
      if(dispatcher == null) {
         ConfigurationContext config = ConfigurationContext.getContext();
         StompHeaderAccessor headerAccessor =
            MessageContextHolder.getMessageAttributes().getHeaderAccessor();
         CommandDispatcherService dispatcherService =
            config.getSpringBean(CommandDispatcherService.class);
         FindByIndexNameSessionRepository<? extends Session> sessionRepository =
            config.getSpringBean(FindByIndexNameSessionRepository.class);
         dispatcher = new CommandDispatcher(headerAccessor, dispatcherService, sessionRepository);
      }

      return dispatcher;
   }
}
