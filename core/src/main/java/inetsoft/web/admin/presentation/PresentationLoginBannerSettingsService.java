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
import inetsoft.web.admin.presentation.model.PresentationLoginBannerSettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PresentationLoginBannerSettingsService {
   @Autowired
   private PortalThemesManager portalThemesManager;

   public PresentationLoginBannerSettingsModel getModel(boolean global) {
      PortalThemesManager manager = portalThemesManager;
      manager.loadThemes();
      PortalWelcomePage welcomePage = manager.getWelcomePage();
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();

      if(!global && orgId != null && manager.getWelcomePage(orgId) != null) {
         welcomePage = manager.getWelcomePage(orgId);
      }

      if(welcomePage == null) {
         return null;
      }

      return PresentationLoginBannerSettingsModel.builder()
         .bannerType(welcomePage.getBannerType() + "")
         .loginBanner(welcomePage.getBanner() == null ? "" : welcomePage.getBanner())
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Login Banner",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationLoginBannerSettingsModel model, boolean global) {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      String loginBanner = model.loginBanner() == null ? "" : model.loginBanner();
      int bannerType = Integer.parseInt(model.bannerType());

      // read, modify and save atomically; the welcome page must be fetched inside the
      // update so that a reload cannot replace it between the modify and the save
      portalThemesManager.update(manager -> {
         PortalWelcomePage welcomePage;

         if(global) {
            welcomePage = manager.getWelcomePage();

            if(welcomePage == null) {
               welcomePage = new PortalWelcomePage();
               manager.setWelcomePage(welcomePage);
            }
         }
         else {
            // an org's first write starts from the global page (Bug #79439)
            welcomePage = manager.getOrCreateOrgWelcomePage(orgId);
         }

         welcomePage.setBannerType(bannerType);
         welcomePage.setBanner(loginBanner);
      });
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Login Banner",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean global) {
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();

      portalThemesManager.update(manager -> {
         if(global) {
            manager.setWelcomePage(new PortalWelcomePage());

            PortalWelcomePage welcomePage = manager.getWelcomePage();
            welcomePage.setBannerType(0);
            welcomePage.setBanner("");
         }
         else {
            manager.removeWelcomePage(orgId);
         }
      });
   }
}
