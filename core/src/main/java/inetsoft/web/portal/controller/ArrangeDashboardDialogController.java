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
package inetsoft.web.portal.controller;

import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.*;
import inetsoft.util.Catalog;
import inetsoft.web.portal.model.ArrangeDashboardDialogModel;
import inetsoft.web.portal.model.DashboardModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Controller that provides a REST endpoint for the arrange dashboard dialog
 *
 * @since 12.3
 */
@RestController
public class ArrangeDashboardDialogController {
   @Autowired
   public ArrangeDashboardDialogController(SecurityEngine securityEngine,
                                           DashboardRegistryManager dashboardRegistryManager,
                                           DashboardManager dashboardManager)
   {
      this.securityEngine = securityEngine;
      this.dashboardRegistryManager = dashboardRegistryManager;
      this.dashboardManager = dashboardManager;
   }

   @GetMapping(value = "/api/portal/arrange-dashboard-dialog-model")
   public ArrangeDashboardDialogModel getArrangeDashboardDialogModel(Principal principal) {
      ArrangeDashboardDialogModel model = new ArrangeDashboardDialogModel();
      model.setDashboards(getDashboards(principal));
      return model;
   }

   @PostMapping(value = "/api/portal/arrange-dashboard-dialog-model")
   public ArrangeDashboardDialogModel setArrangeDashboardDialogModel(
      @RequestBody ArrangeDashboardDialogModel model, Principal principal) throws Exception
   {
      IdentityID name = principal != null ? IdentityID.getIdentityIDFromKey(principal.getName()) :
         new IdentityID(XPrincipal.ANONYMOUS, OrganizationManager.getInstance().getCurrentOrgID());
      Identity identity = getIdentity((XPrincipal) principal);

      if(model.getDashboards() == null || model.getDashboards().size() == 0) {
         dashboardManager.setDashboards(identity, null);
         dashboardManager.setDeselectedDashboards(identity, dashboardManager.getUserDashboards(name));
      }
      else {
         Set<String> selected =
            new LinkedHashSet<>(Arrays.asList(getSelectedDashboardNames(model.getDashboards())));
         Set<String> available =
            new LinkedHashSet<>(Arrays.asList(dashboardManager.getUserDashboards(name)));
         List<String> deselected = new ArrayList<>();

         if(!selected.equals(available)) {
            dashboardManager.setDashboards(identity, null, true);
         }

         available.addAll(Arrays.asList(dashboardManager.getDeselectedDashboards(identity)));

         for(String availableName : available) {
            if(!selected.contains(availableName)) {
               deselected.add(availableName);
            }
         }

         dashboardManager.setDeselectedDashboards(identity, deselected.toArray(new String[0]));
         dashboardManager.setDashboards(identity, selected.toArray(new String[0]));
      }

      return model;
   }

   /**
    * Get dashboards.
    */
   private List<DashboardModel> getDashboards(Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      Catalog catalog2 = Catalog.getCatalog(principal, Catalog.REPORT);

      Identity identity = getIdentity((XPrincipal) principal);

      if(!securityEngine.isSecurityEnabled()) {
         identity = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);
      }

      DashboardRegistry uregistry = dashboardRegistryManager.getRegistry(identity.getIdentityID());

      // use linked hash sets to maintain original order
      Set<String> selected = new LinkedHashSet<>(Arrays.asList(dashboardManager.getDashboards(identity)));
      Set<String> all = new LinkedHashSet<>(selected);
      all.addAll(Arrays.asList(uregistry.getDashboardNames()));
      all.addAll(Arrays.asList(dashboardManager.getUserDashboards(identity.getIdentityID())));
      all.addAll(Arrays.asList(dashboardManager.getDeselectedDashboards(identity)));

      List<DashboardModel> dashboardModels = new ArrayList<>();

      for(String name : all) {
         String label = name;
         boolean global = false;

         if(label.endsWith("__GLOBAL")) {
            if(uregistry.getDashboard(label) == null) {
               global = true;
            }

            label = label.substring(0, label.length() - 8);
         }

         label = catalog2.getString(label);

         if(global) {
            label += catalog.getString("dashboard.globalLabel");
         }
         else {
            label += catalog.getString("dashboard.globalCopyLabel");
         }

         DashboardModel.Builder model = DashboardModel.builder();
         model.name(name);
         model.label(label);
         model.enabled(selected.contains(name));
         dashboardModels.add(model.build());
      }

      return dashboardModels;
   }

   private String[] getSelectedDashboardNames(List<DashboardModel> dashboardModels) {
      return dashboardModels.stream()
         .filter(DashboardModel::enabled)
         .map(DashboardModel::name)
         .toArray(String[]::new);
   }

   /**
    * Get the user identity for dashboard.
    */
   private Identity getIdentity(XPrincipal principal) {
      boolean securityEnabled = securityEngine.isSecurityEnabled();
      SecurityProvider provider = securityEngine.getSecurityProvider();
      IdentityID user = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      Identity identity;

      // @by billh, fix customer bug1303944306880
      // handle SSO problem
      if(securityEnabled && !hasUser(Objects.requireNonNull(provider), user)) {

         identity = new User(user, new String[0], principal.getGroups(),
                             principal.getRoles(), null, null);
      }
      else if(securityEnabled && user != null) {
         identity = new DefaultIdentity(user, Identity.USER);
      }
      else {
         identity = new DefaultIdentity(XPrincipal.ANONYMOUS, Identity.USER);
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

   private final SecurityEngine securityEngine;
   private final DashboardRegistryManager dashboardRegistryManager;
   private final DashboardManager dashboardManager;
}
