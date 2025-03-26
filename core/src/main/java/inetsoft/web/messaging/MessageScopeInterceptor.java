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
package inetsoft.web.messaging;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

import java.security.Principal;
import java.util.List;

/**
 * Channel interceptor that registers the beginning and end of the message scope.
 */
public class MessageScopeInterceptor implements ExecutorChannelInterceptor {
   @Override
   public Message<?> beforeHandle(Message<?> message, MessageChannel messageChannel,
                                  MessageHandler messageHandler)
   {
      MessageAttributes attributes = new MessageAttributes(message);
      MessageContextHolder.setMessageAttributes(attributes);
      Principal principal = attributes.getHeaderAccessor().getUser();
      switchToHostOrgForGlobalShareAsset(attributes, principal);
      ThreadContext.setContextPrincipal(principal);

      if(Thread.currentThread() instanceof GroupedThread) {
         GroupedThread groupedThread = (GroupedThread) Thread.currentThread();
         groupedThread.setPrincipal(principal);
         addViewsheetRecord(groupedThread);
      }

      return message;
   }

   @Override
   public void afterMessageHandled(Message<?> message, MessageChannel messageChannel,
                                   MessageHandler messageHandler, Exception e)
   {
      MessageAttributes attributes = MessageContextHolder.getMessageAttributes();

      if(attributes != null) {
         attributes.messageHandled();
      }

      MessageContextHolder.setMessageAttributes(null);
      OrganizationContextHolder.clear();

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
   }

   private void addViewsheetRecord(GroupedThread thread) {
      List<String> id = MessageContextHolder.currentMessageAttributes()
         .getHeaderAccessor().getNativeHeader("sheetRuntimeId");

      if(id != null && id.size() > 0) {
         Principal principal =
            MessageContextHolder.getMessageAttributes().getHeaderAccessor().getUser();

         try {
            thread.addRecord(LogContext.DASHBOARD,
               ViewsheetEngine.getViewsheetEngine()
                  .getSheet(id.get(0), principal).getEntry().getPath());
         }
         catch(ExpiredSheetException | InvalidUserException e) {
            // ignore
         }
         catch(Exception e) {
            LOG.warn("Failed to get runtime viewsheet " + id, e);
         }
      }
   }

   /**
    * Template switch current org to host-org for the share global assets.
    *
    * @param attributes MessageAttributes.
    * @param principal current org.
    */
   private void switchToHostOrgForGlobalShareAsset(MessageAttributes attributes,
                                                   Principal principal)
   {
      final StompHeaderAccessor headerAccessor = attributes.getHeaderAccessor();
      boolean shouldSwitch = VSUtil.switchToHostOrgForGlobalShareAsset(
         headerAccessor.getFirstNativeHeader("sheetRuntimeId"), principal);

      if(shouldSwitch) {
         OrganizationContextHolder.setCurrentOrgId(Organization.getDefaultOrganizationID());
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(MessageScopeInterceptor.class);
}
