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
package inetsoft.sree.security;

/*
 * Bug #77251: DefaultCheckPermissionStrategy's direct-USER-ADMIN early return used to run before
 * the multi-tenant orgAdminActionExclusions guard, so a direct ADMIN grant on an excluded resource
 * (e.g. one planted through the actions page before the fix) passed checkPermission() for any
 * action, while an ACCESS grant on the same resource was denied (PermissionMatrixActionsS6Test).
 *
 * Every exclusion entry gets a direct USER ADMIN grant for both an org admin and a plain org user.
 * Controls: single-tenant mode keeps honoring the grant, and a direct ADMIN grant on a
 * non-excluded resource still passes in multi-tenant mode.
 *
 * Fixture pattern and the SUtil.isMultiTenant() mocking are those of PermissionMatrixActionsS6Test.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.support.*;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.security.action.ActionPermissionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.stream.Stream;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OrgAdminExclusionAdminGrantTest {
   private static final String ORG_NAME = "exclusionAdminGrantOrg";
   private static final String ORG_ID = "exclusion_admin_grant_org_id";
   private static final IdentityID ORG_ADMIN_ROLE = new IdentityID("Organization Administrator", null);
   private static final String SETTINGS_GENERAL = "settings/general";
   // real EM_COMPONENT key that is not on orgAdminActionExclusions
   private static final String SETTINGS_USERS = "settings/security/users";
   private static final IdentityID SYS_ADMIN_ROLE = new IdentityID("Administrator", null);
   // non-excluded, non-EM resources: a repository folder and a data source
   private static final String REPO_FOLDER = "gateControlFolder";
   private static final String DATA_SOURCE = "gateControlDataSource";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal orgAdmin;
   private static SRPrincipal delegate;
   private static SRPrincipal siteAdmin;

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)
         .addUser("orgAdminUser", ORG_ID, "password")
         .addUser("delegateUser", ORG_ID, "password")
         .addUser("siteAdminUser", ORG_ID, "password")
         .grantPermission(ResourceType.EM_COMPONENT, SETTINGS_USERS, ResourceAction.ADMIN,
                          "delegateUser", Identity.USER, ORG_ID);

      for(Resource excluded : ActionPermissionService.orgAdminActionExclusions) {
         builder
            .grantPermission(excluded.getType(), excluded.getPath(), ResourceAction.ADMIN,
                             "orgAdminUser", Identity.USER, ORG_ID)
            .grantPermission(excluded.getType(), excluded.getPath(), ResourceAction.ADMIN,
                             "delegateUser", Identity.USER, ORG_ID);
      }

      for(String user : new String[]{ "orgAdminUser", "delegateUser" }) {
         builder
            .grantPermission(ResourceType.REPORT, REPO_FOLDER, ResourceAction.ADMIN,
                             user, Identity.USER, ORG_ID)
            .grantPermission(ResourceType.DATA_SOURCE, DATA_SOURCE, ResourceAction.ADMIN,
                             user, Identity.USER, ORG_ID);
      }

      builder.setup();

      orgAdmin = builder.principalOf("orgAdminUser", ORG_ID);
      orgAdmin.setRoles(new IdentityID[]{ ORG_ADMIN_ROLE });
      delegate = builder.principalOf("delegateUser", ORG_ID);
      siteAdmin = builder.principalOf("siteAdminUser", ORG_ID);
      siteAdmin.setRoles(new IdentityID[]{ SYS_ADMIN_ROLE });
   }

   @AfterAll
   static void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @ParameterizedTest(name = "{0}:{1}")
   @MethodSource("orgAdminActionExclusionCases")
   void orgAdmin_directAdminGrantOnExclusion_denied_whenMultiTenant(ResourceType type,
                                                                     String resource)
      throws Exception
   {
      withMultiTenant(true, () ->
         withContextPrincipal(orgAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(type, resource)
                  .expectDeny(orgAdmin, ResourceAction.ACCESS, ResourceAction.ADMIN)
               .verify()));
   }

   @ParameterizedTest(name = "{0}:{1}")
   @MethodSource("orgAdminActionExclusionCases")
   void delegate_directAdminGrantOnExclusion_denied_whenMultiTenant(ResourceType type,
                                                                     String resource)
      throws Exception
   {
      withMultiTenant(true, () ->
         withContextPrincipal(delegate, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(type, resource)
                  .expectDeny(delegate, ResourceAction.ACCESS, ResourceAction.ADMIN)
               .verify()));
   }

   private static Stream<Arguments> orgAdminActionExclusionCases() {
      return Arrays.stream(ActionPermissionService.orgAdminActionExclusions)
         .map(excluded -> Arguments.of(excluded.getType(), excluded.getPath()));
   }

   // control: single-tenant installs have no exclusions, the direct ADMIN grant still applies
   @Test
   void delegate_directAdminGrantOnSettingsGeneral_allowed_whenNotMultiTenant() throws Exception {
      withMultiTenant(false, () ->
         withContextPrincipal(delegate, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.EM_COMPONENT, SETTINGS_GENERAL)
                  .expectAllow(delegate, ResourceAction.ACCESS, ResourceAction.ADMIN)
               .verify()));
   }

   // control: a direct ADMIN grant on a non-excluded resource still applies in multi-tenant mode
   @Test
   void delegate_directAdminGrantOnNonExcludedResource_allowed_whenMultiTenant() throws Exception {
      withMultiTenant(true, () ->
         withContextPrincipal(delegate, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.EM_COMPONENT, SETTINGS_USERS)
                  .expectAllow(delegate, ResourceAction.ACCESS, ResourceAction.ADMIN)
               .verify()));
   }

   // control: a site admin still passes on every excluded resource in multi-tenant mode (it
   // returns before the direct-grant gate)
   @ParameterizedTest(name = "{0}:{1}")
   @MethodSource("orgAdminActionExclusionCases")
   void siteAdmin_exclusion_allowed_whenMultiTenant(ResourceType type, String resource)
      throws Exception
   {
      withMultiTenant(true, () ->
         withContextPrincipal(siteAdmin, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(type, resource)
                  .expectAllow(siteAdmin, ResourceAction.ACCESS, ResourceAction.ADMIN)
               .verify()));
   }

   // control: direct ADMIN grants on non-excluded, non-EM resources (a repository folder and a
   // data source) keep applying in multi-tenant mode for an org admin and a plain org user
   @Test
   void directAdminGrantOnRepositoryFolderAndDataSource_allowed_whenMultiTenant() throws Exception {
      for(SRPrincipal user : new SRPrincipal[]{ orgAdmin, delegate }) {
         withMultiTenant(true, () ->
            withContextPrincipal(user, () ->
               PermissionMatrixVerifier.of(engine())
                  .resource(ResourceType.REPORT, REPO_FOLDER)
                     .expectAllow(user, ResourceAction.READ, ResourceAction.ADMIN)
                  .resource(ResourceType.DATA_SOURCE, DATA_SOURCE)
                     .expectAllow(user, ResourceAction.READ, ResourceAction.ADMIN)
                  .verify()));
      }
   }

   // the gate evaluates the cheap isOrgAdminAction() first, so a direct ADMIN grant on a
   // non-excluded resource is honored without the uncached SUtil.isMultiTenant() storage read
   @Test
   void directAdminGrantOnNonExcludedResource_doesNotReadMultiTenant() throws Exception {
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);
         withContextPrincipal(delegate, () -> Assertions.assertTrue(engine().checkPermission(
            delegate, ResourceType.EM_COMPONENT, SETTINGS_USERS, ResourceAction.ACCESS)));
         mocked.verify(SUtil::isMultiTenant, Mockito.never());
      }
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   private static void withMultiTenant(boolean multiTenant, ThrowingRunnable action) throws Exception {
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(multiTenant);
         action.run();
      }
   }

   private static void withContextPrincipal(SRPrincipal principal, ThrowingRunnable action) {
      ThreadContext.setContextPrincipal(principal);

      try {
         action.run();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }
   }

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
