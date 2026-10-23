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
package inetsoft.web.admin.ai.permissions;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalTab;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.authz.ViewComponent;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79216: {@code preview_permission_changes} refuses a made-up resourcePath only for the
 * resource types in {@link PermissionChangePlanService#CAPABILITY_RESOURCE_TYPES}. This guards
 * that classification against the real action tree: a type added to the allowlist or to the
 * tree without being classified fails here instead of silently going unchecked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PermissionResourceTypeClassificationTest {
   @Test void everyAllowedTypeIsClassifiedExactlyOnce() {
      Set<String> classified = new HashSet<>(PermissionChangePlanService.CAPABILITY_RESOURCE_TYPES);
      int expected = classified.size() + PermissionChangePlanService.OPEN_RESOURCE_TYPES.size();
      classified.addAll(PermissionChangePlanService.OPEN_RESOURCE_TYPES);

      assertEquals(expected, classified.size(), "a type is in both sets");
      assertEquals(PermissionChangePlanService.ALLOWED_RESOURCE_TYPES, classified);
   }

   @Test void everyAllowedTypeInTheActionTreeIsClassifiedAsCapabilityOrDashboard() {
      Set<String> inTree = allowedTypesInTree();

      assertFalse(inTree.isEmpty());

      for(String type : inTree) {
         assertTrue(PermissionChangePlanService.CAPABILITY_RESOURCE_TYPES.contains(type) ||
                    "DASHBOARD".equals(type),
                    type + " appears in the action tree, so it must be a capability type " +
                    "(or DASHBOARD, keyed by display name)");
      }
   }

   @Test void everyCapabilityTypeAppearsInTheActionTree() {
      Set<String> missing = new TreeSet<>(PermissionChangePlanService.CAPABILITY_RESOURCE_TYPES);
      missing.removeAll(allowedTypesInTree());

      assertTrue(missing.isEmpty(), "capability types with no action-tree node: " + missing);
   }

   private static Set<String> allowedTypesInTree() {
      ViewComponent components = Mockito.mock(ViewComponent.class);
      Mockito.when(components.children()).thenReturn(Map.of());
      ComponentAuthorizationService componentService =
         Mockito.mock(ComponentAuthorizationService.class);
      Mockito.when(componentService.getComponentTree()).thenReturn(components);

      PortalThemesManager themes = Mockito.mock(PortalThemesManager.class);
      Mockito.when(themes.getPortalTabsCount()).thenReturn(4);
      Mockito.when(themes.getPortalTab(0)).thenReturn(new PortalTab("Dashboard", ""));
      Mockito.when(themes.getPortalTab(1)).thenReturn(new PortalTab("Schedule", ""));
      Mockito.when(themes.getPortalTab(2)).thenReturn(new PortalTab("Design", ""));
      Mockito.when(themes.getPortalTab(3)).thenReturn(new PortalTab("My Custom Tab", ""));

      Principal principal = Mockito.mock(Principal.class);
      OrganizationManager orgManager = Mockito.mock(OrganizationManager.class);
      Mockito.when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      try(MockedStatic<SUtil> sutil = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> org = Mockito.mockStatic(OrganizationManager.class);
          MockedStatic<LicenseManager> license =
             Mockito.mockStatic(LicenseManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutil.when(SUtil::isMultiTenant).thenReturn(false);
         org.when(OrganizationManager::getInstance).thenReturn(orgManager);
         license.when(LicenseManager::isEnterprise).thenReturn(true);

         ActionTreeNode root = new ActionPermissionService(
            componentService, Mockito.mock(SecurityEngine.class), themes).getActionTree(principal);
         Set<String> types = new TreeSet<>();
         Deque<ActionTreeNode> queue = new ArrayDeque<>(root.children());

         while(!queue.isEmpty()) {
            ActionTreeNode node = queue.removeFirst();

            if(node.type() != null && node.resource() != null &&
               PermissionChangePlanService.ALLOWED_RESOURCE_TYPES.contains(node.type().name()))
            {
               types.add(node.type().name());
            }

            queue.addAll(node.children());
         }

         return types;
      }
   }
}
