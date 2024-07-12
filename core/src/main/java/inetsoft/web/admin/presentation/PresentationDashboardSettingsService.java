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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.PresentationDashboardSettingsModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

@Service
public class PresentationDashboardSettingsService {
   public PresentationDashboardSettingsService(SecurityProvider securityProvider) {
      this.securityProvider = securityProvider;
   }

   public PresentationDashboardSettingsModel getModel(boolean globalProperty) {
      boolean enabled = SreeEnv.getProperty("dashboard.enabled", false, !globalProperty) != null &&
         "true".equals(SreeEnv.getProperty("dashboard.enabled", false, !globalProperty));
      boolean tabsTop = SreeEnv.getProperty("dashboard.tabs.top", false, !globalProperty) != null &&
         "true".equals(SreeEnv.getProperty("dashboard.tabs.top", false, !globalProperty));

      return PresentationDashboardSettingsModel.builder()
         .enabled(enabled)
         .tabsTop(tabsTop)
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Dashboard Settings",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PresentationDashboardSettingsModel model, boolean globalSettings) throws Exception {

      SreeEnv.setProperty("dashboard.enabled", model.enabled() ? "true" : "false", !globalSettings);
      SreeEnv.setProperty("dashboard.tabs.top", model.tabsTop() ? "true" : "false", !globalSettings);

      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Dashboard Settings",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings) throws Exception {
      SreeEnv.resetProperty("dashboard.enabled", !globalSettings);
      SreeEnv.resetProperty("dashboard.tabs.top",  !globalSettings);

      SreeEnv.save();
   }

   private final SecurityProvider securityProvider;
   private final DashboardManager manager = DashboardManager.getManager();
}
