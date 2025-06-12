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

package inetsoft.web.admin.security.user;

import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.util.Catalog;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.EventListener;

public class EditOrganizationListener implements EventListener {
   public EditOrganizationListener(SimpMessagingTemplate messagingTemplate) {
      super();
      this.messagingTemplate = messagingTemplate;
   }

   /**
    * Called when an asset entry is modified.
    *
    * @param event the event object that describes the change.
    */
   public void statusChanged(String destination, EditOrganizationEvent event) {
      if(messagingTemplate != null && event != null) {
         messagingTemplate.convertAndSendToUser(destination, "/create-org-status-changed", getMessage(event));
      }
   }

   private String getMessage(EditOrganizationEvent event) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      String fromOrgName = provider.getOrgNameFromID(event.getFromOrgID());

      if(event.getStatus() == EditOrganizationEvent.STARTED) {
         return catalog.getString("em.cloneOrg.started", event.getToOrgID(), fromOrgName);
      }
      else if(event.getStatus() == EditOrganizationEvent.FINSHED) {
         return catalog.getString("em.cloneOrg.finished", event.getToOrgID(), fromOrgName);
      }

      return null;
   }

   private SimpMessagingTemplate messagingTemplate;
   private Catalog catalog = Catalog.getCatalog();
}
