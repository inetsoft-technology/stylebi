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
package inetsoft.web.admin.presentation;

import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.portal.PortalWelcomePage;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.PresentationLoginBannerSettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

@Service
public class PresentationLoginBannerSettingsService {

   public PresentationLoginBannerSettingsModel getModel() {
      PortalThemesManager manager = PortalThemesManager.getManager();
      manager.loadThemes();
      PortalWelcomePage welcomePage = manager.getWelcomePage();

      if(welcomePage == null) {
         return null;
      }

      return PresentationLoginBannerSettingsModel.builder()
         .bannerType(welcomePage.getBannerType() + "")
         .loginBanner(welcomePage.getBanner())
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Login Banner",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationLoginBannerSettingsModel model) {
      PortalThemesManager manager = PortalThemesManager.getManager();

      if(manager.getWelcomePage() == null) {
         manager.setWelcomePage(new PortalWelcomePage());
      }

      PortalWelcomePage welcomePage = manager.getWelcomePage();
      String loginBanner = model.loginBanner() == null ? "" : model.loginBanner();

      welcomePage.setBannerType(Integer.parseInt(model.bannerType()));
      welcomePage.setBanner(loginBanner);

      manager.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Login Banner",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings() {
      PortalThemesManager manager = PortalThemesManager.getManager();

      manager.setWelcomePage(new PortalWelcomePage());

      PortalWelcomePage welcomePage = manager.getWelcomePage();
      String loginBanner = "";

      welcomePage.setBannerType(0);
      welcomePage.setBanner(loginBanner);

      manager.save();
   }
}
