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
package inetsoft.web.messaging;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.*;
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

   private static final Logger LOG = LoggerFactory.getLogger(MessageScopeInterceptor.class);
}
