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
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.internal.cluster.AffinityCallable;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.uql.asset.AssetEntry;
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

      if(Thread.currentThread() instanceof GroupedThread groupedThread) {
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

      if(Thread.currentThread() instanceof GroupedThread groupedThread) {
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

      if(id != null && !id.isEmpty()) {
         Principal principal =
            MessageContextHolder.getMessageAttributes().getHeaderAccessor().getUser();

         try {
            String runtimeId = id.getFirst();

            if(runtimeId != null) {
               GetViewsheetEntryTask task = new GetViewsheetEntryTask(runtimeId, principal);
               AssetEntry entry = Cluster.getInstance().affinityCall(
                  WorksheetEngine.CACHE_NAME, runtimeId, task);
               thread.addRecord(LogContext.DASHBOARD, entry.getPath());
            }
         }
         catch(ExpiredSheetException | InvalidUserException e) {
            // ignore
         }
         catch(Exception e) {
            LOG.warn("Failed to get runtime viewsheet {}", id, e);
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
      String runtimeId = headerAccessor.getFirstNativeHeader("sheetRuntimeId");

      if(runtimeId != null) {
         boolean shouldSwitch = Cluster.getInstance().affinityCall(
            WorksheetEngine.CACHE_NAME, runtimeId, new SwitchToHostOrgTask(runtimeId, principal));

         if(shouldSwitch) {
            OrganizationContextHolder.setCurrentOrgId(Organization.getDefaultOrganizationID());
         }
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(MessageScopeInterceptor.class);

   private static final class SwitchToHostOrgTask implements AffinityCallable<Boolean> {
      public SwitchToHostOrgTask(String id, Principal principal) {
         this.id = id;
         this.principal = principal;
      }

      @Override
      public Boolean call() {
         return VSUtil.switchToHostOrgForGlobalShareAsset(id, principal);
      }

      private final String id;
      private final Principal principal;
   }

   private static final class GetViewsheetEntryTask implements AffinityCallable<AssetEntry> {
      public GetViewsheetEntryTask(String id, Principal principal) {
         this.id = id;
         this.principal = principal;
      }

      @Override
      public AssetEntry call() {
         return ViewsheetEngine.getViewsheetEngine().getSheet(id, principal).getEntry();
      }

      private final String id;
      private final Principal principal;
   }
}
