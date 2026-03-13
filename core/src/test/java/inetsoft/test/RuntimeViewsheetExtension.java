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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.controller.OpenViewsheetController;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Map;

public class RuntimeViewsheetExtension implements BeforeEachCallback, AfterEachCallback {
   public RuntimeViewsheetExtension(OpenViewsheetEvent openViewsheetEvent) {
      this.openViewsheetEvent = openViewsheetEvent;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public RuntimeViewsheet getRuntimeViewsheet() {
      try {
         return runtimeId == null ?
            null : viewsheetService.getViewsheet(runtimeId, null);
      }
      catch(RuntimeException e) {
         throw e;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to get runtime viewsheet", e);
      }
   }

   @Override
   public void beforeEach(ExtensionContext context) {
      ApplicationContext ctx = SpringExtension.getApplicationContext(context);
      OpenViewsheetController openViewsheetController = ctx.getBean(OpenViewsheetController.class);
      RuntimeViewsheetRef runtimeViewsheetRef = ctx.getBean(RuntimeViewsheetRef.class);
      viewsheetService = ctx.getBean(ViewsheetService.class);

      Principal principal = SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false);
      GenericMessage<String> message = new GenericMessage<>("test");
      MessageAttributes messageAttributes = new MessageAttributes(message);
      StompHeaderAccessor headerAccessor = messageAttributes.getHeaderAccessor();
      headerAccessor.setUser(principal);
      SimpMessagingTemplate messagingTemplate = new SimpMessagingTemplate(new MessageChannel() {
         @Override
         public boolean send(Message<?> message) {
            return true;
         }

         @Override
         public boolean send(Message<?> message, long timeout) {
            return true;
         }
      });
      CommandDispatcherService dispatcherService = new CommandDispatcherService(messagingTemplate, null) {
         @Override
         public void convertAndSendToUser(String user, String destination, Object payload,
                                          Map<String, Object> headers) throws MessagingException
         {
            // NO-OP
         }
      };
      CommandDispatcher commandDispatcher = new CommandDispatcher(headerAccessor, dispatcherService, null);
      MessageContextHolder.setMessageAttributes(messageAttributes);

      try {
         openViewsheetController.openViewsheet(
            openViewsheetEvent, principal, commandDispatcher, "http://localhost:8080/sree");
         runtimeId = runtimeViewsheetRef.getRuntimeId();
      }
      catch(RuntimeException e) {
         throw e;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to open viewsheet", e);
      }
      finally {
         MessageContextHolder.setMessageAttributes(null);
      }
   }

   @Override
   public void afterEach(ExtensionContext context) {
      if(runtimeId != null && viewsheetService != null) {
         try {
            viewsheetService.closeViewsheet(runtimeId, null);
         }
         catch(Exception e) {
            e.printStackTrace();
         }

         runtimeId = null;
      }
   }

   private final OpenViewsheetEvent openViewsheetEvent;
   private ViewsheetService viewsheetService;
   private String runtimeId;
}
