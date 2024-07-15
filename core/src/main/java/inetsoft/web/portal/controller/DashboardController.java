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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.WebService;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.portal.model.DashboardModel;
import inetsoft.web.portal.model.DashboardTabModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller that provides a REST endpoint used to get the dashboards.
 *
 * @since 12.3
 */
@RestController
public class DashboardController {
   /**
    * Creates a new instance of <tt>DashboardController</tt>.
    *
    * @param analyticRepository the analytic repository.
    */
   @Autowired
   public DashboardController(AnalyticRepository analyticRepository,
                              ViewsheetService viewsheetService)
   {
      this.analyticRepository = analyticRepository;
      this.viewsheetService = viewsheetService;
   }

   @GetMapping(value = "/api/portal/dashboard-tab-model")
   @Secured({
      @RequiredPermission(resourceType = ResourceType.DASHBOARD, resource = "*")
   })
   public DashboardTabModel getDashboardTabModel(Principal principal) throws Exception {
      boolean editable = analyticRepository.checkPermission(
         principal, ResourceType.DASHBOARD, "*", ResourceAction.WRITE);
      boolean composerEnable = WebService.isWebExtEnabled(
         analyticRepository, principal, "DataWorksheet");

      DashboardTabModel model = new DashboardTabModel();
      model.setDashboards(getDashboards(principal));
      model.setDashboardTabsTop(isDashboardTabsTop());
      model.setComposerEnabled(composerEnable);
      model.setEditable(editable);
      return model;
   }

   @GetMapping(value = "/api/portal/dashboard-tab-model/{name}")
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Dashboard"),
      @RequiredPermission(resourceType = ResourceType.DASHBOARD, resource = "*")
   })
   public DashboardModel getDashboard(@PathVariable("name") String name, Principal principal)
         throws FileNotFoundException
   {
      return getDashboardModel(name, principal);
   }

   private List<DashboardModel> getDashboards(Principal principal) {
      DashboardManager manager = DashboardManager.getManager();
      Identity identity = getIdentity((XPrincipal) principal);
      return Arrays.stream(manager.getDashboards(identity))
         .map(d -> {
            try {
               return getDashboardModel(d, principal);
            }
            catch(FileNotFoundException ex) {
               LOG.error("Missing dasbhoard: " + d, ex);
               manager.removeDashboard(d);
               return null;
            }
            catch(Exception ex) {
               LOG.error("Failed to get dashboard: " + d, ex);
               return null;
            }
         })
         .filter(Objects::nonNull)
         .collect(Collectors.toList());
   }

   private DashboardModel getDashboardModel(String name, Principal principal)
      throws FileNotFoundException
   {
      try {
         Catalog catalog = Catalog.getCatalog(principal, Catalog.REPORT);
         IdentityID user = SecurityEngine.getSecurity().isSecurityEnabled() ? IdentityID.getIdentityIDFromKey(principal.getName()) :
            new IdentityID(XPrincipal.ANONYMOUS, Organization.getDefaultOrganizationName());
         DashboardRegistry uregistry = DashboardRegistry.getRegistry(user);
         DashboardRegistry registry = DashboardRegistry.getRegistry();
         Dashboard dashboard;
         String type;
         String desc;
         String path = null;
         String identifier = null;

         if((dashboard = uregistry.getDashboard(name)) != null) {
            type = "u";
            desc = dashboard.getDescription();

            if(dashboard instanceof VSDashboard) {
               ViewsheetEntry vsEntry = ((VSDashboard) dashboard).getViewsheet();

               if(vsEntry != null) {
                  path = vsEntry.isMyReport() ? Tool.MY_DASHBOARD + "/" + vsEntry.getPath() :
                     vsEntry.getPath();
                  identifier = vsEntry.getIdentifier();
               }
            }
         }
         else if((dashboard = registry.getDashboard(name)) != null) {
            type = "g";
            desc = dashboard.getDescription();

            if(dashboard instanceof VSDashboard) {
               ViewsheetEntry vs = ((VSDashboard) dashboard).getViewsheet();

               if(vs != null) {
                  path = vs.getPath();
                  identifier = vs.getIdentifier();
               }
            }
         }
         else {
            throw new FileNotFoundException("Dashboard not found: " + name);
         }

         String label;

         if(name.endsWith("__GLOBAL")) {
            label = name.substring(0, name.length() - 8);
            label = catalog.getString(label);
         }
         else {
            label = catalog.getString(name);
         }

         boolean composedDashboard = false;
         boolean scaleToScreen = false;
         boolean fitToWidth = false;

         if(identifier != null) {
            AssetEntry entry = AssetEntry.createAssetEntry(identifier);
            Viewsheet vs = (Viewsheet) viewsheetService.getAssetRepository().getSheet(
               entry, principal, false, AssetContent.CONTEXT);

            if(vs != null) {
               ViewsheetInfo info = vs.getViewsheetInfo();
               composedDashboard = info.isComposedDashboard();
               scaleToScreen = info.isScaleToScreen();
               fitToWidth = info.isFitToWidth();
            }
         }

         return DashboardModel.builder()
            .name(name)
            .label(label)
            .type(type)
            .description(desc)
            .path(path)
            .identifier(identifier)
            .enabled(true)
            .composedDashboard(composedDashboard)
            .scaleToScreen(scaleToScreen)
            .fitToWidth(fitToWidth)
            .build();
      }
      catch(FileNotFoundException ex) {
         throw ex;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to get dashboard model: " + name, e);
      }
   }

   private boolean isDashboardTabsTop() {
      return Boolean.parseBoolean(SreeEnv.getProperty("dashboard.tabs.top"));
   }

   @PostMapping(value = "/api/portal/dashboard/new")
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Dashboard"),
      @RequiredPermission(
         resourceType = ResourceType.DASHBOARD,
         resource = "*",
         actions = ResourceAction.WRITE)
   })
   public DashboardModel newDashboard(@RequestBody DashboardModel dashboardModel,
                                      Principal principal) throws Exception
   {
      String identifier;
      String path;
      String type;
      boolean composedDashboard = false;

      IdentityID user = SecurityEngine.getSecurity().isSecurityEnabled() ? IdentityID.getIdentityIDFromKey(principal.getName()) :
         new IdentityID(XPrincipal.ANONYMOUS, Organization.getDefaultOrganizationName());
      DashboardRegistry registry = DashboardRegistry.getRegistry(user);
      // log create dashboard action
      String actionName = ActionRecord.ACTION_NAME_CREATE;
      String objectName = null;
      String objectType = ActionRecord.OBJECT_TYPE_DASHBOARD;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal),
                                                   actionName, objectName, objectType, actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_FAILURE, null);
      Identity identity = getIdentity((XPrincipal) principal);

      try {
         actionRecord.setObjectName(dashboardModel.name());

         VSDashboard dashboard = new VSDashboard();
         dashboard.setDescription(dashboardModel.description());
         identifier = dashboardModel.identifier();
         IdentityID owner;
         AssetEntry entry;

         if(identifier == null) {
            owner = identity.getIdentityID();
            AssetRepository engine = AssetUtil.getAssetRepository(false);
            entry = new AssetEntry(AssetRepository.USER_SCOPE,
               AssetEntry.Type.VIEWSHEET, dashboardModel.name(), owner);

            // make sure the name is unique
            for(int n = 2; SUtil.isDuplicatedEntry(engine, entry); n++) {
               entry = new AssetEntry(AssetRepository.USER_SCOPE,
                  AssetEntry.Type.VIEWSHEET, dashboardModel.name() + n,
                  owner);
            }

            Viewsheet vs = new Viewsheet();

            vs.getViewsheetInfo().setOnReport(true);
            vs.getViewsheetInfo().setComposedDashboard(true);
            composedDashboard = true;
            // create the viewsheet
            viewsheetService.setViewsheet(vs, entry, principal, true, true);
            identifier = entry.toIdentifier();
         }
         else {
            entry = AssetEntry.createAssetEntry(identifier);
            owner = Objects.requireNonNull(entry).getUser();
         }

         ViewsheetEntry viewsheet = owner != null ?
            new ViewsheetEntry(entry.getPath(), owner) :
            new ViewsheetEntry(entry.getPath());

         viewsheet.setIdentifier(identifier);
         dashboard.setViewsheet(viewsheet);
         registry.addDashboard(dashboardModel.name(), dashboard);
         dashboard.setCreated(System.currentTimeMillis());
         dashboard.setLastModified(System.currentTimeMillis());
         dashboard.setCreatedBy(principal.getName());
         dashboard.setLastModifiedBy(principal.getName());
         registry.save();

         // if this dashboard is created by a user on the viewer, then the
         // dashboard should be automatically selected.
         if(user != null) {
            Identity id = getIdentity((XPrincipal) principal);
            DashboardManager manager = DashboardManager.getManager();

            manager.addDashboard(id, dashboardModel.name());
         }

         DependencyHandler.getInstance().updateDashboardDependencies(user, dashboardModel.name(),
            true);
         path = viewsheet.getPath();
         type = dashboard.getType();

         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(e.getMessage());
         throw e;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);
      }

      return DashboardModel.builder()
         .from(dashboardModel)
         .identifier(identifier)
         .path(path)
         .type(type)
         .composedDashboard(composedDashboard)
         .build();
   }

   @PostMapping(value = "/api/portal/dashboard/edit/{oldName}")
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Dashboard"),
      @RequiredPermission(
         resourceType = ResourceType.DASHBOARD,
         resource = "*",
         actions = ResourceAction.WRITE)
   })
   public DashboardModel editDashboard(
      @PathVariable("oldName") String oldName,
      @RequestBody DashboardModel dashboardModel,
      Principal principal) throws Exception
   {
      String identifier;
      String path;
      boolean composedDashboard = false;

      ActionRecord actionRecord = null;
      IdentityID user = SecurityEngine.getSecurity().isSecurityEnabled() ? IdentityID.getIdentityIDFromKey(principal.getName()) :
         new IdentityID(XPrincipal.ANONYMOUS, Organization.getDefaultOrganizationName());
      DashboardRegistry registry = DashboardRegistry.getRegistry(user);
      Catalog catalog = Catalog.getCatalog();

      try {
         Dashboard odashboard = registry.getDashboard(oldName);

         if(odashboard == null && oldName != null && oldName.endsWith("__GLOBAL")) {
            registry = DashboardRegistry.getRegistry();
            odashboard = registry.getDashboard(oldName);
         }

         DependencyHandler.getInstance().updateDashboardDependencies(user, oldName, false);

         String actionName = null;
         String objectName = null;
         String objectType = ActionRecord.OBJECT_TYPE_DASHBOARD;
         Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
         actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName,
            objectType, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, null);
         String historyName = Objects.requireNonNull(oldName).replaceAll(
            "__GLOBAL", catalog.getString("dashboard.globalCopyLabel"));
         actionRecord.setObjectName(historyName);

         if(!oldName.equals(dashboardModel.name())) {
            actionRecord.setActionName(ActionRecord.ACTION_NAME_RENAME);
         }
         else {
            actionRecord.setActionName(ActionRecord.ACTION_NAME_EDIT);
         }

         VSDashboard dashboard = new VSDashboard();
         identifier = dashboardModel.identifier();
         IdentityID owner;
         AssetEntry entry;

         if(identifier == null) {
            owner = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
            AssetRepository engine = viewsheetService.getAssetRepository();
            entry = new AssetEntry(AssetRepository.USER_SCOPE,
               AssetEntry.Type.VIEWSHEET, dashboardModel.name(), owner);

            // make sure the name is unique
            for(int n = 2; SUtil.isDuplicatedEntry(engine, entry); n++) {
               entry = new AssetEntry(AssetRepository.USER_SCOPE,
                  AssetEntry.Type.VIEWSHEET, dashboardModel.name() + n,
                  owner);
            }

            Viewsheet vs = new Viewsheet();

            vs.getViewsheetInfo().setOnReport(false);
            vs.getViewsheetInfo().setComposedDashboard(true);
            composedDashboard = true;
            // create the viewsheet
            viewsheetService.setViewsheet(vs, entry, principal, true, true);
            identifier = entry.toIdentifier();
         }
         else {
            entry = AssetEntry.createAssetEntry(identifier);
            owner = Objects.requireNonNull(entry).getUser();
         }

         ViewsheetEntry viewsheet = owner != null ?
            new ViewsheetEntry(entry.getPath(), owner) :
            new ViewsheetEntry(entry.getPath());

         viewsheet.setIdentifier(identifier);
         dashboard.setViewsheet(viewsheet);
         dashboard.setDescription(dashboardModel.description());
         dashboard.setLastModified(System.currentTimeMillis());
         dashboard.setLastModifiedBy(principal.getName());

         if(!oldName.equals(dashboardModel.name())) {
            registry.renameDashboard(oldName, dashboardModel.name());
         }

         // remove the base vs is a new vs replaces it
         if(odashboard instanceof VSDashboard) {
            ViewsheetEntry v1 = dashboard.getViewsheet();
            ViewsheetEntry v2 = ((VSDashboard) odashboard).getViewsheet();

            if(!Tool.equals(v1, v2)) {
               removeDashboardViewsheet((VSDashboard) odashboard);
            }
         }

         registry.addDashboard(dashboardModel.name(), dashboard);
         registry.save();
         DependencyHandler.getInstance().updateDashboardDependencies(user, dashboardModel.name(),
            true);

         // if this dashboard is created by a user on the viewer, then the
         // dashboard should be automatically selected.
         if(user != null) {
            Identity id = getIdentity((XPrincipal) principal);
            DashboardManager manager = DashboardManager.getManager();
            manager.addDashboard(id, dashboardModel.name());
         }

         path = viewsheet.getPath();

         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);

         if(!oldName.equals(dashboardModel.name())) {
            actionRecord.setActionError("new name: " + dashboardModel.name());
         }
      }
      catch(Exception e) {
         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            String error = Objects.equals(oldName, dashboardModel.name()) ?
               e.getMessage() :
               e.getMessage() + "new name: " + dashboardModel.name();
            actionRecord.setActionError(error);
         }

         throw e;
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }

      return DashboardModel.builder()
         .from(dashboardModel)
         .identifier(identifier)
         .path(path)
         .composedDashboard(composedDashboard)
         .build();
   }

   @GetMapping(value = "/api/portal/dashboard/duplicate/{name}")
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Dashboard"),
      @RequiredPermission(
         resourceType = ResourceType.DASHBOARD,
         resource = "*",
         actions = ResourceAction.WRITE)
   })
   public boolean isDashboardNameDuplicate(
      @PathVariable("name") String name,
      Principal principal)
   {
      Identity identity = getIdentity((XPrincipal) principal);
      DashboardRegistry registry = DashboardRegistry.getRegistry(identity.getIdentityID());
      Dashboard dashboard = registry.getDashboard(name);

      if(dashboard == null && name != null && name.endsWith("__GLOBAL")) {
         registry = DashboardRegistry.getRegistry();
         dashboard = registry.getDashboard(name);
      }

      return dashboard != null;
   }

   @DeleteMapping(value = "/api/portal/dashboard/deleteDashboard/{dashboardName}")
   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Dashboard"),
      @RequiredPermission(
         resourceType = ResourceType.DASHBOARD,
         resource = "*",
         actions = ResourceAction.WRITE)
   })
   public boolean deleteDashboard(@PathVariable("dashboardName") String dashboardName,
                                  Principal principal)
   {
      String actionName = ActionRecord.ACTION_NAME_DELETE;
      String objectName = null;
      String objectType = ActionRecord.OBJECT_TYPE_DASHBOARD;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName,
         objectName, objectType, actionTimestamp,
         ActionRecord.ACTION_STATUS_FAILURE, null);

      try {
         String historyName = dashboardName.replaceAll("__GLOBAL",
            Catalog.getCatalog().getString("dashboard.globalCopyLabel"));
         actionRecord.setObjectName(historyName);

         IdentityID user = SecurityEngine.getSecurity().isSecurityEnabled() ? IdentityID.getIdentityIDFromKey(principal.getName()) :
            new IdentityID(XPrincipal.ANONYMOUS, Organization.getDefaultOrganizationName());
         DashboardRegistry registry;

         if(dashboardName.endsWith("__GLOBAL")) {
            registry = DashboardRegistry.getRegistry();
         }
         else {
            registry = DashboardRegistry.getRegistry(user);
         }

         DashboardManager manager = DashboardManager.getManager();
         Dashboard dashboard = registry.getDashboard(dashboardName);
         DependencyHandler.getInstance().updateDashboardDependencies(user, dashboardName, false);
         registry.removeDashboard(dashboardName);
         registry.save();
         manager.removeDashboard(dashboardName);

         // remove the underlying vs if it's created for this dashboard
         if(dashboard instanceof VSDashboard) {
            removeDashboardViewsheet((VSDashboard) dashboard);
         }

         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         return true;
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(e.getMessage());
         return false;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);
      }
   }

   /**
    * Get security provider.
    */
   private SecurityProvider getSecurityProvider() {
      try {
         return SecurityEngine.getSecurity().getSecurityProvider();
      }
      catch(Exception ex) {
         LOG.error("Failed to get security provider", ex);
         return null;
      }
   }

   /**
    * Get the user identity for dashboard.
    */
   private Identity getIdentity(XPrincipal principal) {
      boolean securityEnabled = SecurityEngine.getSecurity().isSecurityEnabled();
      SecurityProvider provider = getSecurityProvider();
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      Identity identity;

      // @by billh, fix customer bug1303944306880
      // handle SSO problem
      if(securityEnabled && !hasUser(Objects.requireNonNull(provider), user)) {

         identity = new User(user, new String[0], principal.getGroups(),
            principal.getRoles(), null, null);
      }
      else {
         identity = securityEnabled ? new DefaultIdentity(user, Identity.USER) :
            new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.ROLE);
      }

      return identity;
   }

   /**
    * Check if the specified user is contained in the security provider.
    */
   private boolean hasUser(SecurityProvider provider, IdentityID user) {
      IdentityID[] users = provider.getUsers();
      return users != null && Arrays.asList(users).contains(user);
   }

   /**
    * Remove the viewsheet of a vs dashboard.
    */
   private void removeDashboardViewsheet(VSDashboard dashboard) {
      ViewsheetEntry ve = dashboard.getViewsheet();
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      AssetEntry entry = (ve == null) ? null
         : AssetEntry.createAssetEntry(ve.getIdentifier());

      if(entry != null && entry.getScope() == AssetRepository.USER_SCOPE) {
         try {
            Principal user = new XPrincipal(entry.getUser());
            Viewsheet vs = (Viewsheet) engine.getSheet(entry, user, false,
               AssetContent.ALL);

            if(vs.getViewsheetInfo().isComposedDashboard()) {
               engine.removeSheet(entry, user, false);
            }
         }
         catch(Exception ex) {
            // ignore exception in case the vs is already removed
         }
      }
   }

   private final AnalyticRepository analyticRepository;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG =
      LoggerFactory.getLogger(DashboardController.class);
}
