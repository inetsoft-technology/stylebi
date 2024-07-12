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
package inetsoft.web.viewsheet.model;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Class that maintains a reference to the runtime viewsheet associated with a WebSocket
 * session.
 *
 * @since 12.3
 */
@Component
@Scope(scopeName = "message", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RuntimeViewsheetRef {
   @Autowired
   public RuntimeViewsheetRef(ViewsheetService viewsheetService) {
      final MessageAttributes messageAttributes = MessageContextHolder.getMessageAttributes();
      final StompHeaderAccessor headerAccessor = messageAttributes.getHeaderAccessor();

      // get runtime id from headers
      final String sheetRuntimeId = headerAccessor.getFirstNativeHeader("sheetRuntimeId");
      final Message message = messageAttributes.getMessage();
      final MessageHeaders messageHeaders = message != null ? message.getHeaders() : null;

      if(sheetRuntimeId != null && messageHeaders != null) {
         messageAttributes.setAttribute("sheetRuntimeId", sheetRuntimeId);

         if(!"/events/composer/touch-asset".equals(messageHeaders.get("simpDestination"))) {
            try {
               RuntimeSheet rvs = viewsheetService.getSheet(sheetRuntimeId, null);

               if(rvs != null) {
                  rvs.access(true);
               }
            }
            catch(ExpiredSheetException e) {
               // ignored
            }
            catch(Exception e) {
               LOG.debug("Unable to update viewsheet access time", e);
            }
         }
      }

      // get last modified time from headers
      final String sheetLastModified = headerAccessor.getFirstNativeHeader("sheetLastModified");

      if(sheetLastModified != null) {
         messageAttributes.setAttribute("sheetLastModified", sheetLastModified);
      }

      final String focusedLayoutName = headerAccessor.getFirstNativeHeader("focusedLayoutName");

      if(focusedLayoutName != null) {
         messageAttributes.setAttribute("focusedLayoutName", focusedLayoutName);
      }
   }

   /**
    * Gets the runtime identifier of the opened viewsheet.
    *
    * @return the runtime identifier.
    */
   public String getRuntimeId() {
      return (String) MessageContextHolder.currentMessageAttributes()
         .getAttribute("sheetRuntimeId");
   }

   /**
    * Sets the runtime identifier of the opened viewsheet.
    *
    * @param runtimeId the runtime identifier.
    */
   public void setRuntimeId(String runtimeId) {
      MessageContextHolder.currentMessageAttributes()
         .setAttribute("sheetRuntimeId", runtimeId);
   }

   /**
    * Get the last modified time for the runtime viewsheet.
    */
   public Long getLastModified() {
      return (Long) MessageContextHolder.currentMessageAttributes()
         .getAttribute("sheetLastModified");
   }

   /**
    * Set the last modified time for the runtime viewsheet
    */
   public void setLastModified(long lastModified) {
      MessageContextHolder.currentMessageAttributes()
         .setAttribute("sheetLastModified", lastModified);
   }

   public String getFocusedLayoutName() {
      return (String) MessageContextHolder.currentMessageAttributes()
         .getAttribute("focusedLayoutName");
   }

   public void setFocusedLayoutName(String focusedLayoutName) {
      MessageContextHolder.currentMessageAttributes()
         .setAttribute("focusedLayoutName", focusedLayoutName);
   }

   private static final Logger LOG = LoggerFactory.getLogger(RuntimeViewsheetRef.class);
}
