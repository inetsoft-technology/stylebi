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

package inetsoft.web.wiz.controller;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.wiz.WizPermissions;
import inetsoft.web.wiz.controller.WizEntitlementGrantController.GrantEntry;
import inetsoft.web.wiz.controller.WizEntitlementGrantController.GrantRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Wiz grant endpoints against a real security engine, including the wiz bypass in
 * {@code CompositeSecurityProvider}: callers are wiz-tagged principals, as wiz-services' calls are.
 * Org ids are lower case on purpose (the strategy reads permissions with the lower-cased org id).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizEntitlementGrantControllerTest {
   private static final String ORG_A = "wizgrantorga";
   private static final String ORG_B = "wizgrantorgb";

   private SecurityTestDataBuilder builder;
   private WizEntitlementGrantController controller;
   private WizEntitlementController meController;
   private Principal savedContextPrincipal;
   private Principal savedPrincipal;

   @BeforeEach
   void setUp() throws Exception {
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      savedPrincipal = ThreadContext.getPrincipal();

      builder = SecurityTestDataBuilder.create()
         .addOrg("wizGrantOrgAName", ORG_A)
         .addOrg("wizGrantOrgBName", ORG_B)
         .addUser("plain", ORG_A, "password")
         .addUser("reader", ORG_A, "password")
         .addUser("writer", ORG_A, "password")
         .addUser("carol", ORG_A, "password")
         .addGroup("g1", ORG_A)
         .addRole("r1", ORG_A)
         .addRole("orgAdminRole", ORG_A)
         .addOrgAdminRole("orgAdminRole", ORG_A)
         .addUser("orgadmin", ORG_A, "password")
         .addUserToRole("orgadmin", "orgAdminRole", ORG_A)
         .addUser("readerb", ORG_B, "password")
         .addUser("dave", ORG_B, "password")
         .grantPermission(ResourceType.WIZ_PERMISSION, "entitlement.read", ResourceAction.ACCESS,
                          "reader", Identity.USER, ORG_A)
         .grantPermission(ResourceType.WIZ_PERMISSION, "entitlement.read", ResourceAction.ACCESS,
                          "writer", Identity.USER, ORG_A)
         .grantPermission(ResourceType.WIZ_PERMISSION, "entitlement.write", ResourceAction.ACCESS,
                          "writer", Identity.USER, ORG_A)
         .grantPermission(ResourceType.WIZ_PERMISSION, "entitlement.read", ResourceAction.ACCESS,
                          "readerb", Identity.USER, ORG_B)
         .grantPermission(ResourceType.WIZ_PERMISSION, "config.read", ResourceAction.ACCESS,
                          "dave", Identity.USER, ORG_B)
         .setup();

      SecurityEngine engine = SecurityEngine.getSecurity();
      controller = new WizEntitlementGrantController(engine);
      meController = new WizEntitlementController(engine);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);
      ThreadContext.setPrincipal(savedPrincipal);
      RequestContextHolder.resetRequestAttributes();

      // the permission storage outlives the fixture, so leave no grant behind for the next test
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      for(String permission : WizPermissions.ALL) {
         provider.removePermission(ResourceType.WIZ_PERMISSION, permission, ORG_A);
         provider.removePermission(ResourceType.WIZ_PERMISSION, permission, ORG_B);
      }

      if(builder != null) {
         builder.teardown();
      }
   }

   private SRPrincipal wiz(String user, String org) {
      SRPrincipal principal = builder.principalOf(user, org);
      principal.setProperty("wiz", "true");
      ThreadContext.setContextPrincipal(principal);
      return principal;
   }

   private static GrantRequest request(String permission, String type, String principal,
                                       Boolean granted)
   {
      return new GrantRequest(permission, type, principal, granted);
   }

   private static GrantEntry entry(List<GrantEntry> all, String permission) {
      return all.stream().filter(e -> e.permission().equals(permission)).findFirst().orElseThrow();
   }

   private static int status(Runnable action) {
      return assertThrows(ResponseStatusException.class, action::run).getStatusCode().value();
   }

   private boolean holds(String user, String permission, boolean multiTenant) throws Exception {
      SRPrincipal p = builder.principalOf(user, ORG_A);
      ThreadContext.setContextPrincipal(p);

      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(multiTenant);
         return SecurityEngine.getSecurity().checkPermission(
            p, ResourceType.WIZ_PERMISSION, permission, ResourceAction.ACCESS);
      }
   }

   // ---- read ----------------------------------------------------------------------------------

   @Test
   void getWithoutEntitlementReadIsForbidden() {
      SRPrincipal plain = wiz("plain", ORG_A);
      assertEquals(403, status(() -> {
         try {
            controller.getGrants(plain);
         }
         catch(inetsoft.sree.security.SecurityException e) {
            throw new RuntimeException(e);
         }
      }));
   }

   @Test
   void getWithEntitlementReadListsEveryPermissionAndTheCallersGrants() throws Exception {
      List<GrantEntry> all = controller.getGrants(wiz("reader", ORG_A));

      assertEquals(WizPermissions.ALL, all.stream().map(GrantEntry::permission).toList());
      assertEquals(List.of("reader", "writer"), entry(all, "entitlement.read").users());
      assertEquals(List.of("writer"), entry(all, "entitlement.write").users());

      GrantEntry ungranted = entry(all, "config.write");
      assertTrue(ungranted.users().isEmpty());
      assertTrue(ungranted.groups().isEmpty());
      assertTrue(ungranted.roles().isEmpty());
   }

   @Test
   void getOnlyShowsTheCallersOrganization() throws Exception {
      // dave holds config.read in org B; a caller of org A must not see it, and org B's caller does
      assertTrue(entry(controller.getGrants(wiz("reader", ORG_A)), "config.read").users().isEmpty());
      assertEquals(List.of("dave"),
                   entry(controller.getGrants(wiz("readerb", ORG_B)), "config.read").users());
   }

   // ---- write: authorization ------------------------------------------------------------------

   @Test
   void postWithoutEntitlementWriteIsForbiddenAndStoresNothing() throws Exception {
      for(String user : List.of("plain", "reader")) {
         SRPrincipal caller = wiz(user, ORG_A);
         assertEquals(403, status(() -> post(request("config.read", "user", "carol", true), caller)));
      }

      assertTrue(entry(controller.getGrants(wiz("reader", ORG_A)), "config.read").users().isEmpty());
   }

   // ---- write: validation ---------------------------------------------------------------------

   @Test
   void invalidRequestsAreRejectedWith400AndStoreNothing() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);
      List<GrantRequest> bad = List.of(
         request("nope.read", "user", "carol", true),
         request(null, "user", "carol", true),
         request("config.read", "team", "carol", true),
         request("config.read", null, "carol", true),
         request("config.read", "user", "", true),
         request("config.read", "user", null, true),
         request("config.read", "user", "nobody", true),
         request("config.read", "group", "carol", true),
         request("config.read", "user", "carol:" + ORG_B, true),
         request("config.read", "user", "dave", true),
         request("config.read", "user", "carol", null));

      for(GrantRequest r : bad) {
         assertEquals(400, status(() -> post(r, writer)), r.toString());
      }

      assertEquals(400, status(() -> post(null, writer)));
      assertTrue(entry(controller.getGrants(writer), "config.read").users().isEmpty());
   }

   // ---- write: behaviour ----------------------------------------------------------------------

   @Test
   void grantIsStoredReadBackReflectedInMeAndIdempotent() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);

      assertEquals(List.of("carol"),
                   post(request("config.read", "user", "carol", true), writer).users());
      assertEquals(List.of("carol"),
                   post(request("config.read", "user", "carol", true), writer).users());
      post(request("config.read", "group", "g1", true), writer);
      post(request("config.read", "role", "r1", true), writer);

      GrantEntry stored = entry(controller.getGrants(writer), "config.read");
      assertEquals(List.of("carol"), stored.users());
      assertEquals(List.of("g1"), stored.groups());
      assertEquals(List.of("r1"), stored.roles());

      // the grant shows up in carol's own effective permissions (single tenant)
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(false);
         assertTrue(meController.getMyEntitlements(wiz("carol", ORG_A)).permissions()
                       .contains("config.read"));
      }

      assertEquals(List.of(),
                   post(request("config.read", "user", "carol", false), writer).users());
      assertEquals(List.of(),
                   post(request("config.read", "user", "carol", false), writer).users());
   }

   @Test
   void grantIsInvisibleFromAnotherOrganization() throws Exception {
      post(request("config.write", "user", "carol", true), wiz("writer", ORG_A));

      assertTrue(entry(controller.getGrants(wiz("readerb", ORG_B)), "config.write").users().isEmpty());
      assertEquals(List.of("carol"),
                   entry(controller.getGrants(wiz("reader", ORG_A)), "config.write").users());
   }

   @Test
   void multiTenantOrgAdminGrantOfGlobalConfigHasNoEffect() throws Exception {
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);
         SRPrincipal orgAdmin = wiz("orgadmin", ORG_A);
         assertEquals(List.of("carol"),
                      post(request("config.write", "user", "carol", true), orgAdmin).users());
      }

      // stored, but the multi-tenant check returns false before any grant is read
      assertFalse(holds("carol", "config.write", true));
      // the same stored grant is effective in single-tenant mode
      assertTrue(holds("carol", "config.write", false));
   }

   @Test
   void singleTenantEntitlementWriteHolderCanGrantItselfConfigWrite() throws Exception {
      assertFalse(holds("writer", "config.write", false));
      post(request("config.write", "user", "writer", true), wiz("writer", ORG_A));
      assertTrue(holds("writer", "config.write", false));
   }

   private GrantEntry post(GrantRequest request, SRPrincipal caller) {
      try {
         return controller.setGrant(request, caller);
      }
      catch(inetsoft.sree.security.SecurityException e) {
         throw new RuntimeException(e);
      }
   }
}
