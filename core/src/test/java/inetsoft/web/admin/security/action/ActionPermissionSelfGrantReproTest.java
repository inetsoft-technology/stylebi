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
package inetsoft.web.admin.security.action;

/*
 * F-20 reproducer (product decision): a "security actions delegate" - a plain org user whose
 * only grant is ACCESS on EM_COMPONENT:settings/security/actions - can grant THEMSELVES ACCESS on
 * the EM root (EM:*) and on any EM_COMPONENT of the org action tree (e.g.
 * settings/security/users), because ActionPermissionService.getActionTree(principal) is not
 * filtered by the caller's own grants and ActionPermissionController.setPermissions only checks
 * that the node is in that tree and that displayActions is a subset of the node's actions.
 *
 * Bug #77362: the tests assert the restrictive behavior (the self grant is refused and not
 * stored), per the product decision that a caller who is neither a site admin nor an org admin
 * may only grant actions they hold. See ActionPermissionHoldToGrantTest for the other variants.
 *
 * Fixture is that of ActionPermissionControllerRealTreeTest (real tree, real
 * ResourcePermissionService, real security provider, multi-tenant).
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.DataSpace;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.authz.ViewComponent;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ActionPermissionSelfGrantReproTest {
   private static final String ORG_NAME = "selfGrantActionsOrg";
   private static final String ORG_ID = "self_grant_actions_org_id";
   private static final String SETTINGS_ACTIONS = "settings/security/actions";
   private static final String SETTINGS_USERS = "settings/security/users";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal delegate;
   private static ActionPermissionController controller;

   private MockedStatic<SUtil> sutil;
   private MockedStatic<LicenseManager> license;

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)
         .addUser("delegateUser", ORG_ID, "password")
         .grantPermission(ResourceType.EM_COMPONENT, SETTINGS_ACTIONS, ResourceAction.ACCESS,
                          "delegateUser", Identity.USER, ORG_ID);
      builder.setup();
      delegate = builder.principalOf("delegateUser", ORG_ID);

      ViewComponent components;

      try(InputStream in = ActionPermissionControllerRealTreeTest.class
         .getResourceAsStream("view-components-snapshot.json"))
      {
         components = new ObjectMapper().readValue(in, ViewComponent.class);
      }

      ComponentAuthorizationService componentService =
         Mockito.mock(ComponentAuthorizationService.class);
      Mockito.when(componentService.getComponentTree()).thenReturn(components);
      PortalThemesManager themes =
         new PortalThemesManager(Cluster.getInstance(), DataSpace.getDataSpace());
      themes.loadThemes();

      SecurityEngine engine = SecurityEngine.getSecurity();
      ActionPermissionService actionService =
         new ActionPermissionService(componentService, engine, themes);
      ResourcePermissionService permissionService =
         new ResourcePermissionService(engine.getSecurityProvider(), engine, null, null);
      controller = new ActionPermissionController(actionService, permissionService, engine);
   }

   @AfterAll
   static void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      sutil = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      license = Mockito.mockStatic(LicenseManager.class, Mockito.CALLS_REAL_METHODS);
      license.when(LicenseManager::isEnterprise).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      // a self grant stored by one test (e.g. EM:*) must not leak into the other through
      // inheritance
      engine().getSecurityProvider().removePermission(ResourceType.EM, "*", ORG_ID);
      engine().getSecurityProvider().removePermission(ResourceType.EM_COMPONENT, SETTINGS_USERS, ORG_ID);
      ThreadContext.setContextPrincipal(null);
      license.close();
      sutil.close();
   }

   // delegate -> ACCESS for self on EM_COMPONENT settings/security/users (not held before)
   @Test
   void delegate_selfAccessOnEmComponentNotHeld_refused() throws Exception {
      ThreadContext.setContextPrincipal(delegate);
      assertFalse(engine().checkPermission(delegate, ResourceType.EM_COMPONENT, SETTINGS_USERS,
                                           ResourceAction.ACCESS),
                  "precondition: the delegate has no ACCESS on " + SETTINGS_USERS);

      Throwable thrown = catchThrowable(() -> controller.setPermissions(
         ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true, selfAccess(), delegate));

      boolean stored = hasUserGrant(ResourceType.EM_COMPONENT, SETTINGS_USERS);
      boolean nowAllowed = engine().checkPermission(delegate, ResourceType.EM_COMPONENT,
                                                    SETTINGS_USERS, ResourceAction.ACCESS);
      assertAll(
         () -> assertInstanceOf(java.lang.SecurityException.class, thrown,
                                "self grant of ACCESS on EM_COMPONENT:" + SETTINGS_USERS +
                                " must be refused (403)"),
         () -> assertFalse(stored, "self ACCESS grant on EM_COMPONENT:" + SETTINGS_USERS +
                                   " was stored"),
         () -> assertFalse(nowAllowed, "delegate escalated to ACCESS on EM_COMPONENT:" +
                                       SETTINGS_USERS));
   }

   // delegate -> ACCESS for self on the EM root (EM:*)
   @Test
   void delegate_selfAccessOnEmRoot_refused() throws Exception {
      ThreadContext.setContextPrincipal(delegate);

      Throwable thrown = catchThrowable(() -> controller.setPermissions(
         ResourceType.EM.name(), "*", true, selfAccess(), delegate));

      boolean stored = hasUserGrant(ResourceType.EM, "*");
      assertAll(
         () -> assertInstanceOf(java.lang.SecurityException.class, thrown,
                                "self grant of ACCESS on EM:* must be refused (403)"),
         () -> assertFalse(stored, "self ACCESS grant on EM:* was stored"));
   }

   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   private static Throwable catchThrowable(ThrowingRunnable r) {
      try {
         r.run();
         return null;
      }
      catch(Throwable t) {
         return t;
      }
   }

   private static ResourcePermissionModel selfAccess() {
      return ResourcePermissionModel.builder()
         .permissions(List.of(inetsoft.web.admin.security.ResourcePermissionTableModel.builder()
                                 .identityID(new IdentityID("delegateUser", ORG_ID))
                                 .type(Identity.Type.USER)
                                 .actions(EnumSet.of(ResourceAction.ACCESS))
                                 .build()))
         .displayActions(EnumSet.of(ResourceAction.ACCESS))
         .hasOrgEdited(true)
         .securityEnabled(true)
         .requiresBoth(false)
         .derivePermissionLabel("")
         .grantReadToAllVisible(false)
         .build();
   }

   private static boolean hasUserGrant(ResourceType type, String path) {
      Permission perm = engine().getSecurityProvider().getPermission(type, path, ORG_ID);

      if(perm == null) {
         return false;
      }

      Set<IdentityID> grants = perm.getOrgScopedUserGrants(ResourceAction.ACCESS, ORG_ID);
      return grants != null && grants.contains(new IdentityID("delegateUser", ORG_ID));
   }

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
