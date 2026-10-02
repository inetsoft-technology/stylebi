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

/*
 * ResourcePermissionService grant-read-to-all scope (Bug #77253)
 *
 * security.{datasource,tablestyle,script,scheduletask}.everyone are global properties shared by
 * every organization. Only a site admin (or any admin on a single-tenant server) may change them
 * or see the "grant read to all" checkbox.
 *
 *   [write guard]   setResourcePermissions() on the four grant-read-to-all roots writes the
 *                   global property and refreshes the SecurityEngine cache only when
 *                   !multiTenant || siteAdmin.
 *   [visibility]    getTableModel() sets grantReadToAllVisible only when !multiTenant || siteAdmin.
 *
 * ResourcePermissionService andCondition scope (Bug #77351)
 *
 * permission.andCondition decides how every permission check in the organization is evaluated.
 *
 *   [write guard]   setResourcePermissions() writes it (org-scoped) only when the caller is a site
 *                   admin or an org admin; a user who is only an admin of the saved resource keeps
 *                   the org's current value. SreeEnv.save() runs either way because it also
 *                   persists the security.*.everyone writes.
 *
 * SreeEnv, SUtil, OrganizationManager, SecurityEngine and Catalog are intercepted with
 * MockedStatic; the authorization provider returns no permission so the rest of the save is a
 * no-op.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Catalog;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.verification.VerificationMode;

import java.security.Principal;
import java.util.EnumSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ResourcePermissionServiceTest {
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<OrganizationManager> orgStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<Catalog> catalogStatic;
   private OrganizationManager orgManager;
   private Principal principal;
   private ResourcePermissionService service;

   @BeforeEach
   void setUp() {
      Catalog catalog = mock(Catalog.class);
      when(catalog.getString(anyString())).thenAnswer(inv -> inv.getArgument(0));

      sreeEnv = mockStatic(SreeEnv.class);
      sutil = mockStatic(SUtil.class);
      orgStatic = mockStatic(OrganizationManager.class);
      securityEngineStatic = mockStatic(SecurityEngine.class);
      catalogStatic = mockStatic(Catalog.class);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);

      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orga");
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      principal = mock(Principal.class);

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

   private static Stream<Arguments> tenancyMatrix() {
      return Stream.of(
         // multiTenant, siteAdmin, allowed
         Arguments.of(true, false, false),  // org admin on a multi-tenant server
         Arguments.of(true, true, true),    // site admin on a multi-tenant server
         Arguments.of(false, false, true),  // admin on a single-tenant server
         Arguments.of(false, true, true)
      );
   }

   private static Stream<Arguments> writeMatrix() {
      return tenancyMatrix().flatMap(args -> Stream.of(
         Arguments.of(ResourceType.DATA_SOURCE_FOLDER, "/", "security.datasource.everyone",
                      args.get()[0], args.get()[1], args.get()[2]),
         Arguments.of(ResourceType.TABLE_STYLE_LIBRARY, "*", "security.tablestyle.everyone",
                      args.get()[0], args.get()[1], args.get()[2]),
         Arguments.of(ResourceType.SCRIPT_LIBRARY, "*", "security.script.everyone",
                      args.get()[0], args.get()[1], args.get()[2]),
         Arguments.of(ResourceType.SCHEDULE_TASK_FOLDER, "/", "security.scheduletask.everyone",
                      args.get()[0], args.get()[1], args.get()[2])
      ));
   }

   @ParameterizedTest(name = "{0} {1} multiTenant={3} siteAdmin={4} -> write={5}")
   @MethodSource("writeMatrix")
   void setResourcePermissions_grantReadToAllWriteGuardedBySiteAdmin(
      ResourceType type, String path, String property,
      boolean multiTenant, boolean siteAdmin, boolean allowed) throws Exception
   {
      sutil.when(SUtil::isMultiTenant).thenReturn(multiTenant);
      when(orgManager.isSiteAdmin(principal)).thenReturn(siteAdmin);

      ResourcePermissionModel model = ResourcePermissionModel.builder()
         .displayActions(EnumSet.of(ResourceAction.READ))
         .securityEnabled(true)
         .derivePermissionLabel("Use Parent Permissions")
         .requiresBoth(true)
         .grantReadToAllVisible(true)
         .grantReadToAll(false)
         .build();

      service.setResourcePermissions(path, type, model, principal);

      VerificationMode mode = allowed ? times(1) : never();
      sreeEnv.verify(() -> SreeEnv.setProperty(property, "false"), mode);
      // no other global (2-arg) property write on any path
      sreeEnv.verify(() -> SreeEnv.setProperty(not(eq(property)), anyString()), never());

      switch(type) {
      case DATA_SOURCE_FOLDER:
         securityEngineStatic.verify(SecurityEngine::updateSecurityDatasourceEveryoneValue, mode);
         break;
      case TABLE_STYLE_LIBRARY:
         securityEngineStatic.verify(SecurityEngine::updateSecurityTablestyleEveryoneValue, mode);
         break;
      case SCRIPT_LIBRARY:
         securityEngineStatic.verify(SecurityEngine::updateSecurityScriptEveryoneValue, mode);
         break;
      case SCHEDULE_TASK_FOLDER:
         securityEngineStatic.verify(SecurityEngine::updateSecuritySchduletaskEveryoneValue, mode);
         break;
      default:
         fail("unexpected type " + type);
      }

      // andCondition is org-scoped and only written for a site or org admin (isOrgAdmin is not
      // stubbed here, so only the site admin rows write it)
      sreeEnv.verify(() -> SreeEnv.setProperty("permission.andCondition", "true", true),
                     siteAdmin ? times(1) : never());
      // save() also persists the security.*.everyone write for the single-tenant non-site-admin row
      sreeEnv.verify(SreeEnv::save);
   }

   private static Stream<Arguments> visibilityMatrix() {
      return tenancyMatrix().flatMap(args -> Stream.of(
         Arguments.of(ResourceType.DATA_SOURCE_FOLDER, "/", args.get()[0], args.get()[1], args.get()[2]),
         Arguments.of(ResourceType.TABLE_STYLE_LIBRARY, "*", args.get()[0], args.get()[1], args.get()[2]),
         Arguments.of(ResourceType.SCRIPT_LIBRARY, "*", args.get()[0], args.get()[1], args.get()[2]),
         Arguments.of(ResourceType.SCHEDULE_TASK_FOLDER, "/", args.get()[0], args.get()[1], args.get()[2])
      ));
   }

   @ParameterizedTest(name = "{0} {1} multiTenant={2} siteAdmin={3} -> visible={4}")
   @MethodSource("visibilityMatrix")
   void getTableModel_grantReadToAllVisibleOnlyForSiteWideAdmins(
      ResourceType type, String path, boolean multiTenant, boolean siteAdmin, boolean allowed)
   {
      sutil.when(SUtil::isMultiTenant).thenReturn(multiTenant);
      when(orgManager.isSiteAdmin(principal)).thenReturn(siteAdmin);
      sreeEnv.when(() -> SreeEnv.getProperty(anyString(), anyString())).thenReturn("false");

      ResourcePermissionModel model = service.getTableModel(
         path, type, EnumSet.of(ResourceAction.READ), principal);

      assertEquals(allowed, model.grantReadToAllVisible());

      if(allowed) {
         assertNotNull(model.grantReadToAllLabel());
         assertFalse(model.grantReadToAll());
      }
      else {
         assertNull(model.grantReadToAllLabel());
         sreeEnv.verify(() -> SreeEnv.getProperty(startsWith("security."), anyString()), never());
      }
   }

   @Test
   void setResourcePermissions_nonRootDataSourceFolderNeverWritesEveryone() throws Exception {
      sutil.when(SUtil::isMultiTenant).thenReturn(false);

      ResourcePermissionModel model = ResourcePermissionModel.builder()
         .displayActions(EnumSet.of(ResourceAction.READ))
         .securityEnabled(true)
         .derivePermissionLabel("Use Parent Permissions")
         .requiresBoth(false)
         .grantReadToAllVisible(true)
         .grantReadToAll(true)
         .build();

      service.setResourcePermissions("folder1", ResourceType.DATA_SOURCE_FOLDER, model, principal);

      sreeEnv.verify(() -> SreeEnv.setProperty(anyString(), anyString()), never());
      securityEngineStatic.verify(SecurityEngine::updateSecurityDatasourceEveryoneValue, never());
   }

   private static Stream<Arguments> andConditionMatrix() {
      return Stream.of(
         // multiTenant, siteAdmin, orgAdmin, allowed
         Arguments.of(true, false, false, false),  // resource admin on a multi-tenant server
         Arguments.of(true, false, true, true),    // org admin on a multi-tenant server
         Arguments.of(true, true, false, true),    // site admin on a multi-tenant server
         Arguments.of(false, false, false, false), // resource admin on a single-tenant server
         Arguments.of(false, false, true, true),
         Arguments.of(false, true, false, true)
      );
   }

   @ParameterizedTest(name = "multiTenant={0} siteAdmin={1} orgAdmin={2} -> write={3}")
   @MethodSource("andConditionMatrix")
   void setResourcePermissions_andConditionWriteGuardedBySiteOrOrgAdmin(
      boolean multiTenant, boolean siteAdmin, boolean orgAdmin, boolean allowed) throws Exception
   {
      sutil.when(SUtil::isMultiTenant).thenReturn(multiTenant);
      when(orgManager.isSiteAdmin(principal)).thenReturn(siteAdmin);
      when(orgManager.isOrgAdmin(principal)).thenReturn(orgAdmin);

      ResourcePermissionModel model = ResourcePermissionModel.builder()
         .displayActions(EnumSet.of(ResourceAction.READ))
         .securityEnabled(true)
         .derivePermissionLabel("Use Parent Permissions")
         .requiresBoth(true)
         .grantReadToAllVisible(false)
         .grantReadToAll(false)
         .build();

      service.setResourcePermissions("dash1__GLOBAL", ResourceType.DASHBOARD, model, principal);

      sreeEnv.verify(() -> SreeEnv.setProperty("permission.andCondition", "true", true),
                     allowed ? times(1) : never());
      sreeEnv.verify(() -> SreeEnv.setProperty(eq("permission.andCondition"), anyString()),
                     never());
      sreeEnv.verify(SreeEnv::save);
   }

   @Test
   void setResourcePermissions_nullPrincipalNeverWritesAndCondition() throws Exception {
      sutil.when(SUtil::isMultiTenant).thenReturn(true);

      ResourcePermissionModel model = ResourcePermissionModel.builder()
         .displayActions(EnumSet.of(ResourceAction.READ))
         .securityEnabled(true)
         .derivePermissionLabel("Use Parent Permissions")
         .requiresBoth(true)
         .grantReadToAllVisible(false)
         .grantReadToAll(false)
         .build();

      service.setResourcePermissions("dash1__GLOBAL", ResourceType.DASHBOARD, model, null);

      sreeEnv.verify(() -> SreeEnv.setProperty(eq("permission.andCondition"), anyString(),
                                               anyBoolean()), never());
      verify(orgManager, never()).isOrgAdmin(any(Principal.class));
   }

   @Test
   void setResourcePermissions_nullPrincipalDoesNotReachEnterpriseIsOrgAdmin() throws Exception {
      // the enterprise OrganizationManager.isOrgAdmin(Principal) reads principal.getName()
      // before its null check, so the save must not call it with a null principal
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      when(orgManager.isOrgAdmin((Principal) isNull())).thenThrow(new NullPointerException());

      ResourcePermissionModel model = ResourcePermissionModel.builder()
         .displayActions(EnumSet.of(ResourceAction.READ))
         .securityEnabled(true)
         .derivePermissionLabel("Use Parent Permissions")
         .requiresBoth(true)
         .grantReadToAllVisible(false)
         .grantReadToAll(false)
         .build();

      assertDoesNotThrow(() -> service.setResourcePermissions(
         "dash1__GLOBAL", ResourceType.DASHBOARD, model, null));
      sreeEnv.verify(() -> SreeEnv.setProperty(eq("permission.andCondition"), anyString(),
                                               anyBoolean()), never());
   }
}
