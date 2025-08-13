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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVSession;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.util.*;
import inetsoft.web.composer.ClipboardService;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRefServiceProxy;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
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
   public static ThreadLocal<JoinPoint> joinPointThreadLocal = new ThreadLocal<>();
   public static ThreadLocal<String> eventVsIdThreadLocal = new ThreadLocal<>();
   private final JoinPoint eventJoinPoint;
   private final String eventVsId;

   public ServiceProxyContext() {
      this.contextPrincipal = ThreadContext.getPrincipal();
      this.userMessages = new ArrayList<>();
      this.threadContextRecords = new HashSet<>();
      this.eventJoinPoint = this.joinPointThreadLocal.get();
      this.eventVsId = this.eventVsIdThreadLocal.get();

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

   @SuppressWarnings("unchecked")
   private static void filterAttributes(Map<String, Object> source, Map<String, Object> target) {
      for(Map.Entry<String, Object> e : source.entrySet()) {
         String key = e.getKey();

         if(!key.startsWith(SimpAttributes.DESTRUCTION_CALLBACK_NAME_PREFIX) &&
            !key.startsWith("scopedTarget.") && !key.equals(ClipboardService.CLIPBOARD))
         {
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
            LoggerFactory.getLogger(getClass()).warn(
               "APPLY MESSAGE HEADERS: old={}, new={}",
               messageAttrs.getMessage().getHeaders(), messageHeaders);
         }
      }

      for(UserMessage message : userMessages) {
         Tool.addUserMessage(message);
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

      if(eventJoinPoint != null) {
         Object[] args = eventJoinPoint.getArgs();
         Principal principal = null;

         for(Object arg : args) {
            if(arg instanceof Principal) {
               principal = (Principal) arg;
            }
         }

         MethodSignature signature = (MethodSignature) eventJoinPoint.getSignature();
         boolean undoable = signature.getMethod().isAnnotationPresent(Undoable.class);

         try {
            ViewsheetService viewsheetService = ConfigurationContext.getContext()
               .getSpringBean(ViewsheetService.class);
            RuntimeWorksheet rws = viewsheetService.getWorksheet(eventVsId, principal);
            MVSession session = rws.getAssetQuerySandbox().getMVSession();
            WSExecution.setAssetQuerySandbox(rws.getAssetQuerySandbox());

            // if worksheet changed, re-init sql context so change in table
            // is reflected in spark sql
            if(undoable && session != null) {
               session.clearInitialized();
            }

            WSExecution.setAssetQuerySandbox(rws.getAssetQuerySandbox());
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to set current worksheet", e);
         }
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

      if(eventJoinPoint != null) {
         WSExecution.setAssetQuerySandbox(null);
      }

      Tool.clearUserMessage();
      ThreadContext.setContextPrincipal(null);
      MessageContextHolder.setMessageAttributes(null);
   }

   public RuntimeViewsheetRef createRuntimeViewsheetRef() {
      return new RuntimeViewsheetRef(ConfigurationContext.getContext()
                                        .getSpringBean(RuntimeViewsheetRefServiceProxy.class));
   }

   @SuppressWarnings("unchecked")
   public CommandDispatcher createCommandDispatcher() {
      ConfigurationContext config = ConfigurationContext.getContext();
      StompHeaderAccessor headerAccessor =
         MessageContextHolder.getMessageAttributes().getHeaderAccessor();
      CommandDispatcherService dispatcherService =
         config.getSpringBean(CommandDispatcherService.class);
      FindByIndexNameSessionRepository<? extends Session> sessionRepository =
         config.getSpringBean(FindByIndexNameSessionRepository.class);
      return new CommandDispatcher(headerAccessor, dispatcherService, sessionRepository);
   }
}
