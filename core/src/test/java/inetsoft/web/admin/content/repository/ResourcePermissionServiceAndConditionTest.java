/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.content.repository;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Catalog;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.EnumSet;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77351 reproducer: permission.andCondition ("requires both user and role") is an org-wide
 * setting that changes how every permission in the org is evaluated. A user who merely holds
 * ADMIN on a single resource (not an org admin, not a site admin) saving that one resource's
 * permissions must not be able to flip it. An org admin still can.
 */
@Tag("core")
class ResourcePermissionServiceAndConditionTest {
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<OrganizationManager> orgStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<Catalog> catalogStatic;
   private OrganizationManager orgManager;
   private Principal resourceAdmin;
   private ResourcePermissionService service;

   @BeforeEach
   void setUp() {
      Catalog catalog = mock(Catalog.class);
      when(catalog.getString(anyString())).thenAnswer(inv -> inv.getArgument(0));

      sreeEnv = mockStatic(SreeEnv.class);
      // the org currently evaluates permissions with OR semantics
      sreeEnv.when(() -> SreeEnv.getProperty(eq("permission.andCondition"), anyBoolean(), anyBoolean()))
         .thenReturn("false");
      sutil = mockStatic(SUtil.class);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      orgStatic = mockStatic(OrganizationManager.class);
      securityEngineStatic = mockStatic(SecurityEngine.class);
      catalogStatic = mockStatic(Catalog.class);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);

      resourceAdmin = mock(Principal.class);
      when(resourceAdmin.getName()).thenReturn(new IdentityID("carol", "orga").convertToKey());

      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orga");
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(orgManager.isOrgAdmin(any(Principal.class))).thenReturn(false);
      when(orgManager.isSiteAdmin(any(IdentityID.class))).thenReturn(false);
      when(orgManager.isOrgAdmin(any(IdentityID.class))).thenReturn(false);
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      AuthorizationProvider authz = mock(AuthorizationProvider.class);
      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(authz);

      service = new ResourcePermissionService(
         securityProvider, mock(SecurityEngine.class), mock(LibManagerProvider.class),
         mock(DataSourceRegistry.class));
   }

   @AfterEach
   void tearDown() {
      catalogStatic.close();
      securityEngineStatic.close();
      orgStatic.close();
      sutil.close();
      sreeEnv.close();
   }

   private static ResourcePermissionModel flippedModel() {
      return ResourcePermissionModel.builder()
         .displayActions(EnumSet.of(ResourceAction.READ))
         .securityEnabled(true)
         .derivePermissionLabel("Use Parent Permissions")
         .requiresBoth(true) // flipped from the org's current "false"
         .grantReadToAllVisible(false)
         .grantReadToAll(false)
         .build();
   }

   @Test
   void setResourcePermissions_singleResourceAdmin_cannotFlipOrgWideAndCondition() throws Exception {
      // carol has ADMIN on this one dashboard only (the controller's sole requirement)
      ResourcePermissionModel model = flippedModel();

      service.setResourcePermissions("carolDash__GLOBAL", ResourceType.DASHBOARD, model,
                                     resourceAdmin);

      sreeEnv.verify(() -> SreeEnv.setProperty(eq("permission.andCondition"), eq("true"),
                                               anyBoolean()), never());
      sreeEnv.verify(() -> SreeEnv.setProperty(eq("permission.andCondition"), eq("true")),
                     never());
   }

   @Test
   void setResourcePermissions_orgAdmin_canFlipOrgWideAndCondition() throws Exception {
      when(orgManager.isOrgAdmin(resourceAdmin)).thenReturn(true);

      service.setResourcePermissions("carolDash__GLOBAL", ResourceType.DASHBOARD, flippedModel(),
                                     resourceAdmin);

      sreeEnv.verify(() -> SreeEnv.setProperty("permission.andCondition", "true", true));
      sreeEnv.verify(SreeEnv::save);
   }
}
