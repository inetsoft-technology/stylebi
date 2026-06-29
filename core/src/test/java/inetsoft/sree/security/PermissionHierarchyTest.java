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
 * Covers permission-test-architecture-design.md scenarios 19A-20C.
 *
 * The test uses real FileAuthenticationProvider / FileAuthorizationProvider instances through
 * SecurityTestDataBuilder and verifies decisions through SecurityEngine.checkPermission().
 */

import inetsoft.sree.ClientInfo;
import inetsoft.sree.security.support.*;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PermissionHierarchyTest {
   private static final String ORG_A = "hier_alpha_id";
   private static final String ORG_B = "hier_beta_id";

   private static final String PARENT = "/assets/parent";
   private static final String CHILD = "/assets/parent/child";
   private static final String BLOCKED_CHILD = "/assets/parent/blocked-child";
   private static final String GRANDPARENT = "/assets/grand";
   private static final String GRANDCHILD = "/assets/grand/middle/child";
   private static final String SHARED_PARENT = "/shared/assets";
   private static final String SHARED_CHILD = "/shared/assets/child";
   private static final String HIGH_ONLY = "/login-as/high-only";
   private static final String LOW_ALLOWED = "/login-as/low-allowed";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal viewerA;
   private static SRPrincipal outsiderA;
   private static SRPrincipal viewerB;
   private static SRPrincipal adminA;
   private static SRPrincipal lowA;
   private static SRPrincipal adminAsLowA;

   @BeforeAll
   static void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("hier-alpha", ORG_A)
         .addOrg("hier-beta", ORG_B)

         .addRole("asset_viewer", ORG_A)
         .addRole("blocked_viewer", ORG_A)
         .addRole("asset_admin", ORG_A)
         .addRole("low_reader", ORG_A)
         .addRole("asset_viewer", ORG_B)

         .addUser("viewerA", ORG_A, "password")
         .addUserToRole("viewerA", "asset_viewer", ORG_A)
         .addUser("outsiderA", ORG_A, "password")
         .addUser("adminA", ORG_A, "password")
         .addUserToRole("adminA", "asset_admin", ORG_A)
         .addUser("lowA", ORG_A, "password")
         .addUserToRole("lowA", "low_reader", ORG_A)
         .addUser("viewerB", ORG_B, "password")
         .addUserToRole("viewerB", "asset_viewer", ORG_B)

         .grantPermission(ResourceType.ASSET, HIGH_ONLY, ResourceAction.READ,
                          "asset_admin", Identity.ROLE, ORG_A)
         .grantPermission(ResourceType.ASSET, LOW_ALLOWED, ResourceAction.READ,
                          "low_reader", Identity.ROLE, ORG_A)
         .setup();

      grantRolePermission(ResourceType.ASSET, PARENT, ResourceAction.READ, "asset_viewer", ORG_A);
      grantRolePermission(ResourceType.ASSET, GRANDPARENT, ResourceAction.READ,
                          "asset_viewer", ORG_A);
      grantRolePermission(ResourceType.ASSET, SHARED_PARENT, ResourceAction.READ,
                          "asset_viewer", ORG_A);
      grantRolePermission(ResourceType.ASSET, BLOCKED_CHILD, ResourceAction.READ,
                          "blocked_viewer", ORG_A);

      viewerA = builder.principalOf("viewerA", ORG_A);
      outsiderA = builder.principalOf("outsiderA", ORG_A);
      viewerB = builder.principalOf("viewerB", ORG_B);
      adminA = builder.principalOf("adminA", ORG_A);
      lowA = builder.principalOf("lowA", ORG_A);
      adminAsLowA = loginAsPrincipal("adminA", "lowA", ORG_A);
   }

   @AfterAll
   static void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void scenario19A_roleInheritsParentFolderRead_allowed() {
      PermissionMatrixVerifier.of(engine())
         .resource(ResourceType.ASSET, CHILD)
            .expectAllow(viewerA, ResourceAction.READ)
         .verify();
   }

   @Test
   void scenario19B_userWithoutParentRole_denied() {
      PermissionMatrixVerifier.of(engine())
         .resource(ResourceType.ASSET, CHILD)
            .expectDeny(outsiderA, ResourceAction.READ)
         .verify();
   }

   @Test
   void scenario19C_explicitChildPermissionBlocksParentInheritance_denied() {
      PermissionMatrixVerifier.of(engine())
         .resource(ResourceType.ASSET, BLOCKED_CHILD)
            .expectDeny(viewerA, ResourceAction.READ)
         .verify();
   }

   @Test
   void scenario19D_grandparentFolderPermissionInherited_allowed() {
      PermissionMatrixVerifier.of(engine())
         .resource(ResourceType.ASSET, GRANDCHILD)
            .expectAllow(viewerA, ResourceAction.READ)
         .verify();
   }

   @Test
   void scenario19E_parentInheritanceDoesNotCrossOrg_denied() {
      PermissionMatrixVerifier.of(engine())
         .resource(ResourceType.ASSET, SHARED_CHILD)
            .expectDeny(viewerB, ResourceAction.READ)
         .verify();
   }

   @Test
   void scenario20A_loginAsLowUserDoesNotInheritAdminPermission() throws Exception {
      assertTrue(engine().checkPermission(adminA, ResourceType.ASSET, HIGH_ONLY,
                                          ResourceAction.READ),
                 "adminA must read the admin-only asset");
      assertFalse(engine().checkPermission(lowA, ResourceType.ASSET, HIGH_ONLY,
                                           ResourceAction.READ),
                  "lowA must not read the admin-only asset");
      assertFalse(engine().checkPermission(adminAsLowA, ResourceType.ASSET, HIGH_ONLY,
                                           ResourceAction.READ),
                  "adminA login-as lowA must evaluate as lowA");
   }

   @Test
   void scenario20B_loginAsLowUserKeepsLowUserAllowedPermission() throws Exception {
      assertTrue(engine().checkPermission(lowA, ResourceType.ASSET, LOW_ALLOWED,
                                          ResourceAction.READ),
                 "lowA must read the low-user asset");
      assertTrue(engine().checkPermission(adminAsLowA, ResourceType.ASSET, LOW_ALLOWED,
                                          ResourceAction.READ),
                 "adminA login-as lowA must keep lowA's allowed permission");
   }

   private static void grantRolePermission(ResourceType type, String resource,
                                           ResourceAction action, String roleName, String orgId)
   {
      SecurityProvider sp = engine().getSecurityProvider();
      Permission permission = sp.getPermission(type, resource, orgId);
      if(permission == null) {
         permission = new Permission();
      }
      permission.setRoleGrantsForOrg(action, Set.of(roleName), orgId);
      permission.updateGrantAllByOrg(orgId, true);
      sp.setPermission(type, resource, permission, orgId);
   }

   private static SRPrincipal loginAsPrincipal(String loginUserName, String effectiveUserName,
                                               String orgId)
   {
      IdentityID effectiveUserId = new IdentityID(effectiveUserName, orgId);
      User effectiveUser = engine().getSecurityProvider().getUser(effectiveUserId);
      IdentityID[] roles = effectiveUser == null ? new IdentityID[0] : effectiveUser.getRoles();
      String[] groups = effectiveUser == null ? new String[0] : effectiveUser.getGroups();

      ClientInfo clientInfo = new ClientInfo(effectiveUserId, null);
      clientInfo.setLoginUserName(new IdentityID(loginUserName, orgId));

      SRPrincipal principal = new SRPrincipal(clientInfo, roles, groups, orgId, 2L);
      registerPrincipal(principal);
      return principal;
   }

   private static void registerPrincipal(SRPrincipal principal) {
      @SuppressWarnings("unchecked")
      Map<ClientInfo, SRPrincipal> sessionUsers =
         (Map<ClientInfo, SRPrincipal>) ReflectionTestUtils.getField(engine(), "users");

      if(sessionUsers != null) {
         sessionUsers.put(principal.getUser().getCacheKey(), principal);
      }
   }

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
