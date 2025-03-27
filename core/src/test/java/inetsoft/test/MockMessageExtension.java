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
package inetsoft.test;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;

import java.security.Principal;
import java.util.Map;
import java.util.function.*;

public abstract class MockMessageExtension implements BeforeEachCallback, AfterEachCallback {
   public String getRuntimeId() {
      return null;
   }

   public Principal getUser() {
      return headerAccessor == null ? null : headerAccessor.getUser();
   }

   public SimpMessagingTemplate getMessagingTemplate() {
      return messagingTemplate;
   }

   public StompHeaderAccessor getHeaderAccessor() {
      return headerAccessor;
   }

   public CommandDispatcher getCommandDispatcher() {
      return commandDispatcher;
   }

   protected <T, R> R mockMessage(T t, Function<T, R> action) {
      Principal principal = SUtil.getPrincipal(new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()), null, false);
      GenericMessage<String> message = new GenericMessage<>("test");
      MessageAttributes messageAttributes = new MessageAttributes(message);

      if(getRuntimeId() != null) {
         messageAttributes.setAttribute("sheetRuntimeId", getRuntimeId());
      }

      headerAccessor = messageAttributes.getHeaderAccessor();
      headerAccessor.setUser(principal);
      messagingTemplate = new SimpMessagingTemplate(new MessageChannel() {
         @Override
         public boolean send(Message<?> message) {
            return true;
         }

         @Override
         public boolean send(Message<?> message, long timeout) {
            return true;
         }
      });
      CommandDispatcherService dispatcherService = new CommandDispatcherService(messagingTemplate) {
         @Override
         public void convertAndSendToUser(String user, String destination, Object payload,
                                          Map<String, Object> headers) throws MessagingException
         {
            // NO-OP
         }
      };
      commandDispatcher = new CommandDispatcher(headerAccessor, dispatcherService, null);
      MessageContextHolder.setMessageAttributes(messageAttributes);

      try {
         return action.apply(t);
      }
      finally {
         commandDispatcher = null;
         headerAccessor = null;
         messagingTemplate = null;
         MessageContextHolder.setMessageAttributes(null);
      }
   }

   protected <T> T mockMessage(Supplier<T> action) {
      return mockMessage("", (Function<String, T>) ignore -> action.get());
   }

   protected <T> void mockMessage(T t, Consumer<T> action) {
      mockMessage(t, param -> {
         action.accept(param);
         return null;
      });
   }

   protected void mockMessage(Runnable action) {
      mockMessage("", (Function<String, Void>) ignore -> {
         action.run();
         return null;
      });
   }

   private SimpMessagingTemplate messagingTemplate;
   private StompHeaderAccessor headerAccessor;
   private CommandDispatcher commandDispatcher;
}
