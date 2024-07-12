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
package inetsoft.web.admin.security.action;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalTab;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.schedule.InternalScheduledTaskService;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.authz.ViewComponent;

import java.security.Principal;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActionPermissionService {
   @Autowired
   public ActionPermissionService(ComponentAuthorizationService componentService) {
      this.componentService = componentService;
   }

   public ActionTreeNode getActionTree(Principal principal) {
      boolean isOrgAdmin = false;

      if(SUtil.isMultiTenant()) {
         if(principal != null) {
            SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
            IdentityID[] roles = provider.getRoles(IdentityID.getIdentityIDFromKey(principal.getName()));
            isOrgAdmin = Arrays
               .stream(provider.getAllRoles(roles))
               .noneMatch(provider::isSystemAdministratorRole);
         }
         else {
            isOrgAdmin = false;
         }
      }

      IdentityID principalID = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      Catalog catalog = Catalog.getCatalog(principal);
      ActionTreeNode.Builder root = ActionTreeNode.builder()
         .label("")
         .folder(true)
         .actions(EnumSet.noneOf(ResourceAction.class))
         .orgAdmin(isOrgAdmin);

      root.addFilteredChildren(getViewsheetToolbarNode(catalog));

      if(principal != null) {
         if(!SUtil.isMultiTenant() || OrganizationManager.getInstance().isSiteAdmin(principal)) {
            root.addFilteredChildren(ActionTreeNode.builder()
                                        .label(catalog.getString("Edit Mobile Devices"))
                                        .resource("*")
                                        .folder(false)
                                        .grant(false)
                                        .type(ResourceType.DEVICE)
                                        .actions(EnumSet.of(ResourceAction.ACCESS))
                                        .build());
         }
      }

      ActionTreeNode.Builder composer = ActionTreeNode.builder()
         .label(catalog.getString("Visual Composer"))
         .folder(true)
         .actions(EnumSet.noneOf(ResourceAction.class));

      composer.addFilteredChildren(ActionTreeNode.builder()
                                      .label(catalog.getString("Viewsheet"))
                                      .resource("*")
                                      .folder(false)
                                      .grant(false)
                                      .type(ResourceType.VIEWSHEET)
                                      .actions(EnumSet.of(ResourceAction.ACCESS))
                                      .build());

      composer.addFilteredChildren(ActionTreeNode.builder()
                                      .label(catalog.getString("Worksheet"))
                                      .resource("*")
                                      .folder(false)
                                      .grant(false)
                                      .type(ResourceType.WORKSHEET)
                                      .actions(EnumSet.of(ResourceAction.ACCESS))
                                      .build());
      root.addFilteredChildren(composer.build());

      root.addFilteredChildren(ActionTreeNode.builder()
                                  .label(catalog.getString("Physical Table"))
                                  .resource("*")
                                  .folder(false)
                                  .grant(false)
                                  .type(ResourceType.PHYSICAL_TABLE)
                                  .actions(EnumSet.of(ResourceAction.ACCESS))
                                  .build());
      root.addFilteredChildren(ActionTreeNode.builder()
                                  .label(catalog.getString("Free Form SQL"))
                                  .resource("*")
                                  .folder(false)
                                  .grant(false)
                                  .type(ResourceType.FREE_FORM_SQL)
                                  .actions(EnumSet.of(ResourceAction.ACCESS))
                                  .build());

      root.addChildren(ActionTreeNode.builder()
                          .label(catalog.getString(Tool.MY_DASHBOARD))
                          .resource("*")
                          .folder(false)
                          .type(ResourceType.MY_DASHBOARDS)
                          .grant(true)
                          .actions(EnumSet.of(ResourceAction.READ))
                          .build());

      root.addFilteredChildren(ActionTreeNode.builder()
                          .label(catalog.getString("Portal Repository Tree - Drag and Drop"))
                          .resource("*")
                          .folder(false)
                          .type(ResourceType.PORTAL_REPOSITORY_TREE_DRAG_AND_DROP)
                          .grant(true)
                          .actions(EnumSet.of(ResourceAction.ACCESS))
                          .build());

      root.addFilteredChildren(ActionTreeNode.builder()
                                  .label(catalog.getString("Materialize Assets"))
                                  .resource("*")
                                  .folder(false)
                                  .type(ResourceType.MATERIALIZATION)
                                  .grant(true)
                                  .actions(EnumSet.of(ResourceAction.ACCESS))
                                  .build());

      root.addFilteredChildren(getPortalTabsNode(catalog));
      root.addFilteredChildren(getScheduleOptionsNode(principal, catalog));
      root.addFilteredChildren(getInternalScheduleTasksNode(principal, catalog));
      root.addFilteredChildren(getEnterpriseManagerNode(catalog, isOrgAdmin));
      root.addFilteredChildren(getChartTypesNode(catalog));
      root.addFilteredChildren(getBookmarkNode(catalog));

      if("on".equals(SreeEnv.getProperty("login.loginAs"))) {
         root.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Log in As"))
                             .resource("*")
                             .folder(false)
                             .type(ResourceType.LOGIN_AS)
                             .grant(false)
                             .actions(EnumSet.of(ResourceAction.ACCESS))
                             .build());
      }

      root.addChildren(getSharingNode(catalog));

      if(principal != null) {
         if(OrganizationManager.getInstance().isSiteAdmin(principal) ||
            !LicenseManager.getInstance().isEnterprise())
         {
            root.addFilteredChildren(ActionTreeNode.builder()
                                        .label(catalog.getString("Upload Drivers"))
                                        .resource("*")
                                        .folder(false)
                                        .type(ResourceType.UPLOAD_DRIVERS)
                                        .grant(false)
                                        .actions(EnumSet.of(ResourceAction.ACCESS))
                                        .build());
         }
      }

      root.addFilteredChildren(ActionTreeNode.builder()
              .label(catalog.getString("Profile"))
              .resource("*")
              .folder(false)
              .type(ResourceType.PROFILE)
              .grant(false)
              .actions(EnumSet.of(ResourceAction.ACCESS))
              .build());

      root.addFilteredChildren(ActionTreeNode.builder()
                          .label(catalog.getString("Cross Join"))
                          .resource("*")
                          .folder(false)
                          .type(ResourceType.CROSS_JOIN)
                          .grant(false)
                          .actions(EnumSet.of(ResourceAction.ACCESS))
                          .build());

      root.addFilteredChildren(ActionTreeNode.builder()
         .label(catalog.getString("Create New DataSource"))
         .resource("*")
         .folder(false)
         .type(ResourceType.CREATE_DATA_SOURCE)
         .grant(false)
         .actions(EnumSet.of(ResourceAction.ACCESS))
         .build());

      return root.build();
   }

   private ActionTreeNode getViewsheetToolbarNode(Catalog catalog) {
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString("Viewsheet Toolbar"))
         .folder(true)
         .actions(EnumSet.noneOf(ResourceAction.class));

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Edit"))
                             .resource("Edit")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Email"))
                             .resource("Email")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Export"))
                             .resource("Export")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Export - Expand Components"))
                             .resource("ExportExpandComponents")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Import"))
                             .resource("Import")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("PageNavigation"))
                             .resource("PageNavigation")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Print"))
                             .resource("Print")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Refresh"))
                             .resource("Refresh")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Schedule"))
                             .resource("Schedule")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Schedule - Expand Components"))
                             .resource("ScheduleExpandComponents")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Social Sharing"))
                             .resource("Social Sharing")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      return builder.build();
   }

   private ActionTreeNode getPortalTabsNode(Catalog catalog) {
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString("Portal Tabs"))
         .folder(true)
         .actions(EnumSet.noneOf(ResourceAction.class));
      PortalThemesManager manager = PortalThemesManager.getManager();

      for(int i = 0; i < manager.getPortalTabsCount(); i++) {
         PortalTab tab = manager.getPortalTab(i);
         String name = catalog.getString(tab.getName());

         if(catalog.getString("Dashboard").equals(name)) {
            builder.addFilteredChildren(ActionTreeNode.builder()
                                           .label(name)
                                           .resource("*")
                                           .folder(false)
                                           .grant(false)
                                           .type(ResourceType.DASHBOARD)
                                           .actions(EnumSet.of(ResourceAction.READ, ResourceAction.WRITE))
                                           .build());
         }
         else if(catalog.getString("Schedule").equals(name)) {
            builder.addFilteredChildren(ActionTreeNode.builder()
                                   .label(name)
                                   .resource("*")
                                   .folder(false)
                                   .grant(false)
                                   .type(ResourceType.SCHEDULER)
                                   .actions(EnumSet.of(ResourceAction.ACCESS))
                                   .build());
         }
         else if(catalog.getString("Design").equals(name)) {
            builder.addFilteredChildren(ActionTreeNode.builder()
                                           .label(name)
                                           .resource("Design")
                                           .folder(false)
                                           .grant(false)
                                           .type(ResourceType.PORTAL_TAB)
                                           .actions(EnumSet.of(ResourceAction.ACCESS))
                                           .build());
         }
         else if(catalog.getString("Report").equals(name)) {
            String label = catalog.getString("Repository");
            builder.addFilteredChildren(ActionTreeNode.builder()
                                   .label(label)
                                   .resource("Report")
                                   .folder(false)
                                   .grant(true)
                                   .type(ResourceType.PORTAL_TAB)
                                   .actions(EnumSet.of(ResourceAction.READ))
                                   .build());
         }
         else {
            builder.addFilteredChildren(ActionTreeNode.builder()
                                   .label(name)
                                   .resource(tab.getName())
                                   .folder(false)
                                   .grant(false)
                                   .type(ResourceType.PORTAL_TAB)
                                   .actions(EnumSet.of(ResourceAction.ACCESS))
                                   .build());
         }
      }

      return builder.build();
   }

   private ActionTreeNode getScheduleOptionsNode(Principal principal, Catalog catalog) {
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString("Schedule Options"))
         .folder(true)
         .actions(EnumSet.noneOf(ResourceAction.class));

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Notification Email"))
                             .resource("notificationEmail")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.SCHEDULE_OPTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Save to Disk"))
                             .resource("saveToDisk")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.SCHEDULE_OPTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Email Delivery"))
                             .resource("emailDelivery")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.SCHEDULE_OPTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Start Time"))
                             .resource("startTime")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.SCHEDULE_OPTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      if(principal != null) {
         IdentityID userID = IdentityID.getIdentityIDFromKey(principal.getName());

         if(!SUtil.isMultiTenant() || OrganizationManager.getInstance().isSiteAdmin(principal)) {
            builder.addFilteredChildren(ActionTreeNode.builder()
                                           .label(catalog.getString("Time Range"))
                                           .resource("timeRange")
                                           .folder(false)
                                           .grant(true)
                                           .type(ResourceType.SCHEDULE_OPTION)
                                           .actions(EnumSet.of(ResourceAction.READ))
                                           .build());
         }
      }

      return builder.build();
   }

   private ActionTreeNode getInternalScheduleTasksNode(Principal principal, Catalog catalog) {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString("Internal Schedule Tasks"))
         .folder(true)
         .actions(EnumSet.noneOf(ResourceAction.class));

      if(principal != null) {
         if(!SUtil.isMultiTenant() || OrganizationManager.getInstance().isSiteAdmin(principal)) {
            ScheduleManager.getWriteableInternalTaskNames().stream()
               .map(name -> ActionTreeNode.builder()
                  .label(catalog.getString(name))
                  .resource(name)
                  .folder(false)
                  .grant(false)
                  .type(ResourceType.SCHEDULE_TASK)
                  .actions(EnumSet.of(ResourceAction.READ, ResourceAction.WRITE))
                  .build())
               .forEach(builder::addFilteredChildren);

            builder.addFilteredChildren(ActionTreeNode.builder()
                                           .label(catalog.getString(InternalScheduledTaskService.BALANCE_TASKS))
                                           .resource(InternalScheduledTaskService.BALANCE_TASKS)
                                           .folder(false)
                                           .grant(false)
                                           .type(ResourceType.SCHEDULE_TASK)
                                           .actions(EnumSet.of(ResourceAction.READ))
                                           .build());
         }
      }

      builder.addFilteredChildren(ActionTreeNode.builder()
         .label(catalog.getString(InternalScheduledTaskService.UPDATE_ASSETS_DEPENDENCIES))
         .resource(InternalScheduledTaskService.UPDATE_ASSETS_DEPENDENCIES)
         .folder(false)
         .grant(false)
         .type(ResourceType.SCHEDULE_TASK)
         .actions(EnumSet.of(ResourceAction.READ))
         .build());

      return builder.build();
   }

   private ActionTreeNode getEnterpriseManagerNode(Catalog catalog, boolean isOrgAdmin) {
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString("Enterprise Manager"))
         .resource("*")
         .folder(true)
         .orgAdmin(isOrgAdmin)
         .type(ResourceType.EM)
         .actions(EnumSet.of(ResourceAction.ACCESS));

      addEnterpriseManagerNodes(catalog, builder, componentService.getComponentTree(), "", isOrgAdmin);

      return builder.build();
   }

   private void addEnterpriseManagerNodes(Catalog catalog, ActionTreeNode.Builder parent,
                                          ViewComponent component, String path, boolean isOrgAdmin)
   {
      boolean enterprise = LicenseManager.getInstance().isEnterprise();
      component.children().values().stream()
         .filter(ViewComponent::available)
         .filter(c -> !Tool.equals("auditing", c.name()) &&
            !Tool.equals("org-settings", c.name()) && !Tool.equals("themes", c.name()) || enterprise)
         .sorted(Comparator.comparing(ViewComponent::name))
         .map(c -> getEnterpriseManagerNode(catalog, path, c, isOrgAdmin))
         .filter(Objects::nonNull)
         .forEach(parent::addFilteredChildren);
   }

   private ActionTreeNode getEnterpriseManagerNode(Catalog catalog, String path,
                                                   ViewComponent component, boolean isOrgAdmin)
   {
      String resource = path + component.name();
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString(component.label()))
         .resource(resource)
         .folder(!component.children().isEmpty())
         .grant(false)
         .orgAdmin(isOrgAdmin)
         .type(ResourceType.EM_COMPONENT)
         .actions(EnumSet.of(ResourceAction.ACCESS));
      addEnterpriseManagerNodes(catalog, builder, component, resource + "/", isOrgAdmin);
      return builder.build();
   }

   private ActionTreeNode getBookmarkNode(Catalog catalog) {
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString("Bookmark"))
         .folder(true)
         .actions(EnumSet.noneOf(ResourceAction.class));

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Open and Create Bookmark"))
                             .resource("Bookmark")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Open Bookmark"))
                             .resource("OpenBookmark")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Share Bookmark"))
                             .resource("ShareBookmark")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("Share to All"))
                             .resource("ShareToAll")
                             .folder(false)
                             .grant(true)
                             .type(ResourceType.VIEWSHEET_ACTION)
                             .actions(EnumSet.of(ResourceAction.READ))
                             .build());

      return builder.build();
   }

   private ActionTreeNode getSharingNode(Catalog catalog) {
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString("Social Sharing"))
         .folder(true)
         .actions(EnumSet.noneOf(ResourceAction.class));

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("em.settings.share.email"))
                             .resource("email")
                             .grant(true)
                             .folder(false)
                             .type(ResourceType.SHARE)
                             .actions(EnumSet.of(ResourceAction.ACCESS))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("em.settings.share.facebook"))
                             .resource("facebook")
                             .grant(true)
                             .folder(false)
                             .type(ResourceType.SHARE)
                             .actions(EnumSet.of(ResourceAction.ACCESS))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("em.settings.share.googleChat"))
                             .resource("googlechat")
                             .grant(true)
                             .folder(false)
                             .type(ResourceType.SHARE)
                             .actions(EnumSet.of(ResourceAction.ACCESS))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("em.settings.share.linkedin"))
                             .resource("linkedin")
                             .grant(true)
                             .folder(false)
                             .type(ResourceType.SHARE)
                             .actions(EnumSet.of(ResourceAction.ACCESS))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("em.settings.share.slack"))
                             .resource("slack")
                             .grant(true)
                             .folder(false)
                             .type(ResourceType.SHARE)
                             .actions(EnumSet.of(ResourceAction.ACCESS))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("em.settings.share.twitter"))
                             .resource("twitter")
                             .grant(true)
                             .folder(false)
                             .type(ResourceType.SHARE)
                             .actions(EnumSet.of(ResourceAction.ACCESS))
                             .build());

      builder.addFilteredChildren(ActionTreeNode.builder()
                             .label(catalog.getString("em.settings.share.link"))
                             .resource("link")
                             .grant(true)
                             .folder(false)
                             .type(ResourceType.SHARE)
                             .actions(EnumSet.of(ResourceAction.ACCESS))
                             .build());

      return builder.build();
   }

   private ActionTreeNode getChartTypesNode(Catalog catalog) {
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString("Chart Types"))
         .folder(true)
         .actions(EnumSet.noneOf(ResourceAction.class));

      builder.addFilteredChildren(getChartNode(catalog, null,
         "Bar", "Bar", "3D Bar", "Interval"));
      builder.addFilteredChildren(getChartNode(catalog, null,
         "Line", "Line", "Step Line", "Jump Line"));
      builder.addFilteredChildren(getChartNode(catalog, null,
         "Area", "Area", "Step Area"));
      builder.addFilteredChildren(getChartNode(catalog, null,
         "Pie", "Pie", "3D Pie", "Donut"));
      builder.addFilteredChildren(getChartNode(catalog, null,
         "Radar", "Radar", "Filled Radar"));
      builder.addFilteredChildren(getChartNode(catalog, null,
         "Schema", "Stock", "Candle", "Box Plot"));
      builder.addFilteredChildren(getChartNode(catalog, null,
         "Map", "Map", "Contour Map"));
      builder.addFilteredChildren(getChartNode(catalog, null,
         "Treemap", "Treemap", "Sunburst", "Circle Packing", "Icicle"));
      builder.addFilteredChildren(getChartNode(catalog, null,
         "Relation", "Tree", "Network", "Circular Network"));
      builder.addFilteredChildren(getChartNode(catalog, null,
         "Others", "Funnel", "Gantt", "Marimekko", "Pareto", "Scatter Contour", "Waterfall"));
      builder.addFilteredChildren(getChartNode(catalog, null, "Point"));

      return builder.build();
   }

   private ActionTreeNode getChartNode(Catalog catalog, String parentPath, String... paths) {
      String currPath = parentPath == null ? paths[0] : parentPath + "/" + paths[0];
      boolean leaf = parentPath != null || paths.length == 1;
      ActionTreeNode.Builder builder = ActionTreeNode.builder()
         .label(catalog.getString(paths[0]))
         .resource(currPath)
         .folder(!leaf)
         .grant(parentPath == null)
         .type(parentPath == null ? ResourceType.CHART_TYPE_FOLDER : ResourceType.CHART_TYPE)
         .actions(EnumSet.of(ResourceAction.READ));

      if(paths.length > 1) {
         for(int i = 1; i < paths.length; i++) {
            builder.addFilteredChildren(getChartNode(catalog, currPath, paths[i]));
         }
      }

      return builder.build();
   }

   public static boolean isOrgAdminAction(ResourceType type, String resource) {
      boolean isFluentDLogging = "fluentd".equals(SreeEnv.getProperty("log.provider"));
      boolean canOrgAdminAccess = Boolean.parseBoolean(SreeEnv.getProperty("log.fluentd.orgAdminAccess"));

      if(isFluentDLogging && canOrgAdminAccess) {
         return Arrays.stream(orgAdminActionExclusions)
            .filter(ex -> !("monitoring/log".equals(ex.getPath())))
            .noneMatch(res -> res.getType() == type &&
               res.getPath().equals(resource));
      }
      else {
         return Arrays.stream(orgAdminActionExclusions)
            .noneMatch(res -> res.getType() == type &&
               res.getPath().equals(resource));
      }
   }

   public static final Resource[] orgAdminActionExclusions = new Resource[] {
      new Resource(ResourceType.EM_COMPONENT, "monitoring/summary"),
      new Resource(ResourceType.EM_COMPONENT, "monitoring/cluster"),
      new Resource(ResourceType.EM_COMPONENT, "monitoring/cache"),
      new Resource(ResourceType.EM_COMPONENT, "monitoring/log"),
      new Resource(ResourceType.EM_COMPONENT, "settings/general"),
      new Resource(ResourceType.EM_COMPONENT, "settings/presentation/org-settings"),
      new Resource(ResourceType.EM_COMPONENT, "settings/properties"),
      new Resource(ResourceType.EM_COMPONENT, "settings/security/provider"),
      new Resource(ResourceType.EM_COMPONENT, "settings/security/sso"),
      new Resource(ResourceType.EM_COMPONENT, "settings/content/data-space"),
      new Resource(ResourceType.EM_COMPONENT, "settings/content/drivers-and-plugins"),
      new Resource(ResourceType.EM_COMPONENT, "settings/logging"),
      new Resource(ResourceType.EM_COMPONENT, "settings/schedule/settings"),
      new Resource(ResourceType.EM_COMPONENT, "settings/schedule/status"),
      new Resource(ResourceType.EM_COMPONENT, "notification"),
      new Resource(ResourceType.SCHEDULE_TASK, "__asset file backup__"),
      new Resource(ResourceType.SCHEDULE_TASK, "__balance tasks__"),
      new Resource(ResourceType.UPLOAD_DRIVERS, "*")
   };

   public static final Map<String, String[]> orgAdminExclusionFragments = Map.of(
      "settings/presentation/settings",
               new String[]{"look-and-feel", "font-mapping", "welcome-page", "login-banner"}
      );

   private final ComponentAuthorizationService componentService;
}
