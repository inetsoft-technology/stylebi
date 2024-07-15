/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.presentation;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalTab;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.presentation.model.PortalIntegrationSettingsModel;
import inetsoft.web.admin.presentation.model.PortalTabModel;
import inetsoft.web.viewsheet.Audited;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
public class PortalIntegrationViewSettingsService {
   public PortalIntegrationSettingsModel getModel(Principal principal, boolean globalProperty) {
      List<PortalTabModel> tabs = new ArrayList<>();
      boolean help = false;
      boolean preference = false;
      boolean logout = false;
      boolean search = false;
      boolean home = false;
      String customLoadingText = SreeEnv.getProperty("portal.customLoadingText", false, !globalProperty);
      String homeLink = SreeEnv.getProperty("portal.home.link", false, !globalProperty);

      if(manager.isButtonVisible(PortalThemesManager.HELP_BUTTON)) {
         help = true;
      }

      if(manager.isButtonVisible(PortalThemesManager.PREFERENCES_BUTTON)) {
         preference = true;
      }

      if(manager.isButtonVisible(PortalThemesManager.LOGOUT_BUTTON)) {
         logout = true;
      }

      if(manager.isButtonVisible(PortalThemesManager.HOME_BUTTON)) {
         home = true;
      }

      if(manager.isButtonVisible(PortalThemesManager.SEARCH_BUTTON)) {
         search = true;
      }

      int tabsCount = manager.getPortalTabsCount();
      Catalog catalog = Catalog.getCatalog(principal);

      for(int i = 0; i < tabsCount; i++) {
         PortalTab tab = manager.getPortalTab(i);
         String label;
         boolean editable;

         if(tab.isEditable()) {
            if("Report".equals(tab.getName())) {
               label = "Repository";
            }
            else {
               label = tab.getName();
            }
         }
         else {
            if("Report".equals(tab.getName())) {
               label = catalog.getString("Repository");
            }
            else {
               label = catalog.getString(tab.getName());
            }
         }

         editable = tab.isEditable();

         tabs.add(PortalTabModel.builder()
                     .name(tab.getName())
                     .label(label)
                     .uri(tab.getURI())
                     .visible(tab.isVisible())
                     .editable(editable)
                     .originalIndex(i)
                     .build());
      }

      return PortalIntegrationSettingsModel.builder()
         .tabs(tabs)
         .help(help)
         .preference(preference)
         .dashboardAvailable(true)
         .logout(logout)
         .search(search)
         .home(home)
         .customLoadingText(customLoadingText)
         .homeLink(homeLink)
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Portal Integration",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(PortalIntegrationSettingsModel model, Principal principal, boolean globalSettings)
      throws Exception
   {
      manager.loadThemes();
      manager.setButtonVisible(PortalThemesManager.HELP_BUTTON, model.help());
      manager.setButtonVisible(PortalThemesManager.PREFERENCES_BUTTON,
                               model.preference());
      manager.setButtonVisible(PortalThemesManager.LOGOUT_BUTTON, model.logout());
      manager.setButtonVisible(PortalThemesManager.SEARCH_BUTTON, model.search());
      manager.setButtonVisible(PortalThemesManager.HOME_BUTTON, model.home());

      SreeEnv.setProperty("portal.customLoadingText", model.customLoadingText(), !globalSettings);
      SreeEnv.setProperty("portal.home.link", model.homeLink(), !globalSettings);

      List<PortalTab> newPortalTabs = new ArrayList<>();
      List<PortalTab> portalTabs = manager.getPortalTabs();
      List<PortalTabModel> tabModels = model.tabs();
      int insertIndex = 0;

      for(int i = 0; i < tabModels.size(); i++) {
         PortalTabModel tabModel = tabModels.get(i);
         PortalTab tab;

         if(tabModel.originalIndex() != null) {
            tab = portalTabs.get(tabModel.originalIndex());

            if(tab.isEditable()) {
               tab.setName(tabModel.name());
               tab.setLabel(tabModel.name());
               tab.setURI(tabModel.uri());
               tab.setVisible(tabModel.visible());
            }
            else {
               insertIndex = i + 1;
            }
         }
         else {
            tab = new PortalTab(tabModel.name(), tabModel.uri(), tabModel.visible());
         }

         newPortalTabs.add(tab);
      }

      manager.setPortalTabs(newPortalTabs);
      manager.save();
      SreeEnv.save();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Presentation-Portal Integration",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void resetSettings(boolean globalSettings)
      throws Exception
   {
      if(globalSettings) {
         List<PortalTab> portalTabs = manager.getPortalTabs();
         List<PortalTab> newPortalTabs = new ArrayList<>();

         for(PortalTab tab : portalTabs) {
            if(!tab.isEditable()) {
               int index = 0;

               switch(tab.getName()) {
               case "Dashboard":
                  index = 0;
                  break;
               case "Repository":
                  index = 1;
                  break;
               case "Schedule":
                  index = 2;
                  break;
               case "Data":
                  index = 3;
                  break;
               }

               index = newPortalTabs.size() > index ? index : newPortalTabs.size();
               newPortalTabs.add(index, tab);
            }
         }

         manager.loadThemes();
         manager.setButtonVisible(PortalThemesManager.HELP_BUTTON, true);
         manager.setButtonVisible(PortalThemesManager.PREFERENCES_BUTTON, true);
         manager.setButtonVisible(PortalThemesManager.LOGOUT_BUTTON, true);
         manager.setButtonVisible(PortalThemesManager.SEARCH_BUTTON, true);
         manager.setButtonVisible(PortalThemesManager.HOME_BUTTON, true);
         manager.setPortalTabs(newPortalTabs);
         manager.save();
      }

      SreeEnv.remove("portal.customLoadingText", !globalSettings);
      SreeEnv.remove("portal.home.link", !globalSettings);
      SreeEnv.save();
   }

   private PortalThemesManager manager = PortalThemesManager.getManager();
}
