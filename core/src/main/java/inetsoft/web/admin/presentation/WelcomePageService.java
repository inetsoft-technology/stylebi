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
package inetsoft.web.admin.presentation;

import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.portal.PortalWelcomePage;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.WelcomePageSettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

@Service
public class WelcomePageService {
   public WelcomePageSettingsModel getModel(boolean global) {
      int type;
      String source;

      PortalThemesManager manager = PortalThemesManager.getManager();
      manager.loadThemes();
      PortalWelcomePage welcomePage = manager.getWelcomePage() ;
      String currentOrgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(!global && currentOrgID != null && manager.getWelcomePage(currentOrgID) != null) {
         welcomePage = manager.getWelcomePage(currentOrgID);
      }

      source = welcomePage == null ? "" : welcomePage.getData();
      type = welcomePage == null ? PortalWelcomePage.NONE : welcomePage.getType();

      return WelcomePageSettingsModel.builder()
         .type(type)
         .source(source)
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Welcome Page",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(WelcomePageSettingsModel model, boolean globalSettings) {
      PortalThemesManager manager = PortalThemesManager.getManager();

      if(globalSettings) {
         if(manager.getWelcomePage() == null) {
            manager.setWelcomePage(new PortalWelcomePage(model.type(), model.source()));
         }
         else {
            manager.getWelcomePage().setType(model.type());
            manager.getWelcomePage().setData(model.source());
         }
      }
      else {
         String orgId = OrganizationManager.getInstance().getCurrentOrgID();

         if(manager.getWelcomePage(orgId) == null) {
            manager.setWelcomePage(orgId, new PortalWelcomePage(model.type(), model.source()));
         }
         else {
            manager.getWelcomePage(orgId).setType(model.type());
            manager.getWelcomePage(orgId).setData(model.source());
         }
      }

      manager.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Welcome Page",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings) {
      PortalThemesManager manager = PortalThemesManager.getManager();

      if(globalSettings) {
         manager.setWelcomePage(new PortalWelcomePage(0, ""));
      }
      else {
         manager.removeWelcomePage(OrganizationManager.getInstance().getCurrentOrgID());
      }

      manager.save();
   }
}
