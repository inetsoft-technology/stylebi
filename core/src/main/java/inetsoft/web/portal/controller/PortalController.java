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
package inetsoft.web.portal.controller;

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.PortalTab;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.sree.web.WebService;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.GlobalParameterProvider;
import inetsoft.web.portal.model.*;
import inetsoft.web.reportviewer.model.ParameterPageModel;
import inetsoft.web.viewsheet.controller.ComposerClientController;
import inetsoft.web.viewsheet.service.LinkUri;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Controller that provides a REST endpoint for the portal
 *
 * @since 12.3
 */
@RestController
public class PortalController {
   @Autowired
   public PortalController(SecurityEngine securityEngine,
      AnalyticRepository analyticRepository)
   {
      this.securityEngine = securityEngine;
      this.analyticRepository = analyticRepository;
   }

   @GetMapping("/api/portal/get-portal-model")
   public PortalModel getUserPortal(Principal principal, @LinkUri String linkUri) throws Exception {
      PortalThemesManager manager = PortalThemesManager.getManager();
      DashboardManager dashboards = DashboardManager.getManager();

      boolean composerEnabled = WebService
         .isWebExtEnabled(analyticRepository, principal, "DataWorksheet");
      boolean dashboardEnabled = WebService
         .isWebExtEnabled(analyticRepository, principal, "Dashboard");
      boolean accessible = "true".equalsIgnoreCase(SreeEnv.getProperty("accessibility.enabled"));
      String logoutUrl = SreeEnv.getProperty("sso.logout.url");
      String homeLink = SreeEnv.getProperty("portal.home.link", "..");
      boolean newDatasourceEnabled = false;
      boolean newWorksheetEnabled = false;
      boolean newViewsheetEnabled = false;
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      try {
         newDatasourceEnabled = securityEngine.checkPermission(
            principal, ResourceType.PORTAL_TAB, "Data", ResourceAction.ACCESS);

         if(newDatasourceEnabled) {
            newDatasourceEnabled = securityEngine.checkPermission(
               principal, ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.WRITE) ||
               securityEngine.checkPermission(
                  principal, ResourceType.CREATE_DATA_SOURCE, "*", ResourceAction.ACCESS);
         }

         newWorksheetEnabled = securityEngine.checkPermission(
            principal, ResourceType.WORKSHEET, "*", ResourceAction.ACCESS);
         newViewsheetEnabled = securityEngine.checkPermission(
            principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS);
      }
      catch(SecurityException ex) {
         // ignore
      }

      if(logoutUrl == null || logoutUrl.isEmpty()) {
         logoutUrl = linkUri + "logout";

         if(!securityEngine.isSecurityEnabled() &&
            (principal == null || XPrincipal.ANONYMOUS.equals(pId.name)))
         {
            logoutUrl = logoutUrl + "?showLogin=true";
         }
      }

      boolean porfile =
         SecurityEngine
            .getSecurity().checkPermission(principal, ResourceType.PROFILE, "*", ResourceAction.ACCESS);

      boolean profiling = porfile && ((XPrincipal) principal).isProfiling();

      return PortalModel.builder()
         .currentUser(getCurrentUser(principal))
         .helpVisible(manager.isButtonVisible(PortalThemesManager.HELP_BUTTON))
         .preferencesVisible(manager.isButtonVisible(PortalThemesManager.PREFERENCES_BUTTON))
         .logoutVisible(manager.isButtonVisible(PortalThemesManager.LOGOUT_BUTTON))
         .homeLink(homeLink)
         .homeVisible(manager.isButtonVisible(PortalThemesManager.HOME_BUTTON))
         .reportEnabled(true)
         .composerEnabled(composerEnabled)
         .dashboardEnabled(dashboardEnabled)
         .customLogo(PortalThemesManager.CUSTOM_LOGO.equals(manager.getLogoStyle()))
         .helpURL(Tool.getHelpURL(true))
         .logoutUrl(logoutUrl)
         .accessible(accessible)
         .hasDashboards(dashboards.getDashboards(new User(pId), false).length > 0)
         .title(getPageTitle(principal))
         .newDatasourceEnabled(newDatasourceEnabled)
         .newWorksheetEnabled(newWorksheetEnabled)
         .newViewsheetEnabled(newViewsheetEnabled)
         .profile(porfile)
         .profiling(profiling)
         .build();
   }

   @GetMapping("/api/portal/is-composer-open")
   public boolean isComposerOpened(HttpServletRequest request) {
      HttpSession session = request.getSession();
      String sessionId = session.getId();
      String simpSessionId =
         ComposerClientController.getFirstSimpSessionId(sessionId);

      return simpSessionId != null;
   }

   @GetMapping("/api/portal/get-current-user")
   public CurrentUserModel getCurrentUser(Principal principal) {
      if(securityEngine == null) {
         return null;
      }

      String localeLanguage = null;
      String localeCountry = null;

      if(principal instanceof XPrincipal) {
         String localeName = ((XPrincipal) principal).getProperty(XPrincipal.LOCALE);
         Locale locale = Catalog.parseLocale(localeName);

         if(locale != null) {
            localeLanguage = locale.getLanguage();
            localeCountry = locale.getCountry();
         }
      }

      IdentityID pId = principal == null ? null :IdentityID.getIdentityIDFromKey(principal.getName());

      return CurrentUserModel.builder()
         .anonymous(principal == null || pId.name.equals(XPrincipal.ANONYMOUS))
         .name(principal == null ? new IdentityID(XPrincipal.ANONYMOUS, OrganizationManager.getCurrentOrgName()) : pId)
         .alias(principal instanceof XPrincipal ? ((XPrincipal) principal).getAlias() : null)
         .isSysAdmin(principal != null && OrganizationManager.getInstance().isSiteAdmin(principal))
         .localeLanguage(localeLanguage)
         .localeCountry(localeCountry)
         .build();
   }

   @GetMapping("/api/portal/get-portal-tabs")
   public List<PortalTabModel> getPortalTabs(Principal principal) throws Exception {
      PortalThemesManager manager = PortalThemesManager.getManager();
      List<PortalTabModel> portalTabModels = new ArrayList<>();

      for(int i = 0; i < manager.getPortalTabsCount(); i++) {
         PortalTab tab = (PortalTab) manager.getPortalTab(i).clone();
         String name = tab.getName();

         if("Dashboard".equals(name)) {
            if(!isDashboardAvailable(principal)) {
               continue;
            }
         }
         else if("Report".equals(name)) {
            boolean viewReport = analyticRepository.checkPermission(
               principal, ResourceType.PORTAL_TAB, "Report", ResourceAction.READ);

            if(!viewReport) {
               continue;
            }
         }
         else if("Schedule".equals(name)) {
            boolean viewSchedule = analyticRepository.checkPermission(
                  principal, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS);

            if(!viewSchedule) {
               continue;
            }
         }
         else if("Design".equals(name)) {
         }
         else if("Data".equals(name)) {
            boolean viewDataTab = securityEngine.checkPermission(
               principal, ResourceType.PORTAL_TAB, "Data", ResourceAction.ACCESS);

            if(!viewDataTab) {
               continue;
            }
         }
         else if(!analyticRepository.checkPermission(
            principal, ResourceType.PORTAL_TAB, name, ResourceAction.ACCESS))
         {
            continue;
         }

         PortalTabModel portalTabModel = new PortalTabModel();
         portalTabModel.setName(tab.getName());
         portalTabModel.setLabel(tab.getLabel());
         portalTabModel.setUri(tab.getURI());
         portalTabModel.setCustom(tab.isEditable());
         portalTabModel.setVisible(tab.isVisible());
         portalTabModels.add(portalTabModel);
      }

      return portalTabModels;
   }

   @GetMapping("/api/portal/set-profiling/{profiling}")
   public void setProfiling(@PathVariable("profiling") Boolean profiling, Principal principal) {
      if(principal instanceof XPrincipal) {
         ((XPrincipal) principal).setProfiling(profiling);
      }
   }

   @GetMapping("/api/portal/global-parameters/{type}/**")
   public GlobalParameterModel getGlobalParameters(@PathVariable("type") String type,
                                                   @RemainingPath String path, Principal principal)
   {
      String providerClassName = SreeEnv.getProperty("global.parameter.provider", (String) null);
      ParameterPageModel model = null;

      if(providerClassName != null) {
         try {
            Class<?> providerClass = Class.forName(providerClassName);
            GlobalParameterProvider provider =
               (GlobalParameterProvider) providerClass.getConstructor().newInstance();
            model = provider.getParameters(path, false, principal);
         }
         catch(Exception e) {
            LOG.error("Failed to instantiate global parameter provider {}", providerClassName, e);
         }
      }

      return GlobalParameterModel.builder()
         .required(model != null)
         .model(model)
         .build();
   }

   private String getPageTitle(Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      return catalog.getString("InetSoft");
   }

   /**
    * Return if the dashboard tab is available.
    */
   private boolean isDashboardAvailable(Principal principal) throws Exception {
      return WebService.isWebExtEnabled(analyticRepository, principal, "Dashboard") &&
         !"false".equals(SreeEnv.getProperty("dashboard.enabled")) &&
         analyticRepository.checkPermission(
            principal, ResourceType.DASHBOARD, "*", ResourceAction.READ);
   }

   private final SecurityEngine securityEngine;
   private final AnalyticRepository analyticRepository;
   private static final Logger LOG = LoggerFactory.getLogger(PortalController.class);
}
