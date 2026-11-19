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
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.wiz.WizPermissions;
import inetsoft.web.wiz.controller.WizEntitlementGrantController.GrantEntry;
import inetsoft.web.wiz.controller.WizEntitlementGrantController.GrantRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
         .addGlobalRole("globalr")
         .addUserToRole("carol", "globalr", null)
         .addUser("member", ORG_A, "password")
         .addUserToRole("member", "r1", ORG_A)
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
   void multiTenantRefusesToStoreGlobalConfigGrantsButNotOrgLevelOnes() throws Exception {
      SRPrincipal orgAdmin = wiz("orgadmin", ORG_A);

      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         for(String name : List.of("config.read", "config.write", "config.write.critical")) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
               () -> post(request(name, "user", "carol", true), orgAdmin));
            assertEquals(400, ex.getStatusCode().value());
            assertEquals("permission '" + name + "' is not grantable in multi-tenant mode",
                         ex.getReason());
         }

         assertEquals(List.of("carol"),
                      post(request("orgconfig.read", "user", "carol", true), orgAdmin).users());
      }

      GrantEntry config = entry(controller.getGrants(wiz("reader", ORG_A)), "config.write");
      assertTrue(config.users().isEmpty(), "nothing may be stored");
   }

   @Test
   void singleTenantStillStoresGlobalConfigGrants() throws Exception {
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(false);
         assertEquals(List.of("carol"),
                      post(request("config.write", "user", "carol", true), wiz("writer", ORG_A))
                         .users());
      }
   }

   @Test
   void singleTenantEntitlementWriteHolderCanGrantItselfConfigWrite() throws Exception {
      assertFalse(holds("writer", "config.write", false));
      post(request("config.write", "user", "writer", true), wiz("writer", ORG_A));
      assertTrue(holds("writer", "config.write", false));
   }

   // ---- end-user permissions and the restricted flag -------------------------------------------

   @Test
   void endUserPermissionLifecycleKeepsTheDefaultAllowConsistent() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);
      assertTrue(holds("carol", "plugin.use", false));
      assertTrue(holds("plain", "plugin.use", false));
      assertFalse(entry(controller.getGrants(writer), "plugin.use").restricted());

      GrantEntry first = post(request("plugin.use", "user", "carol", true), writer);
      assertEquals(List.of("carol"), first.users());
      assertTrue(first.restricted());
      assertTrue(holds("carol", "plugin.use", false));
      assertFalse(holds("plain", "plugin.use", false), "granting restricts everybody else");

      assertTrue(post(request("plugin.use", "user", "carol", true), writer).restricted());
      assertFalse(holds("plain", "plugin.use", false));

      post(request("plugin.use", "user", "reader", true), writer);
      GrantEntry afterRevoke = post(request("plugin.use", "user", "carol", false), writer);
      assertEquals(List.of("reader"), afterRevoke.users());
      assertTrue(afterRevoke.restricted());
      assertFalse(holds("carol", "plugin.use", false));
      assertTrue(holds("reader", "plugin.use", false));
      assertTrue(entry(controller.getGrants(writer), "plugin.use").restricted());

      GrantEntry last = post(request("plugin.use", "user", "reader", false), writer);
      assertTrue(last.users().isEmpty());
      assertFalse(last.restricted());
      assertTrue(holds("plain", "plugin.use", false), "revoking the last grant restores default-allow");
      assertTrue(holds("carol", "plugin.use", false));

      assertFalse(post(request("plugin.use", "user", "reader", false), writer).restricted());
      assertTrue(holds("plain", "plugin.use", false));
   }

   @Test
   void anAdminPermissionWithGrantsIsNeverReportedAsRestricted() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);
      assertFalse(post(request("config.read", "user", "carol", false), writer).restricted());

      GrantEntry granted = post(request("config.read", "user", "carol", true), writer);
      assertFalse(granted.restricted());
      assertFalse(granted.defaultAllow());
   }

   // ---- failure atomicity and concurrency ---------------------------------------------------------

   /** An engine and provider that delegate to the real ones, so single methods can be stubbed. */
   private SecurityProvider[] delegating() {
      SecurityEngine real = SecurityEngine.getSecurity();
      SecurityProvider provider = mock(SecurityProvider.class,
                                       AdditionalAnswers.delegatesTo(real.getSecurityProvider()));
      SecurityEngine engine = mock(SecurityEngine.class, AdditionalAnswers.delegatesTo(real));
      doReturn(provider).when(engine).getSecurityProvider();
      controller = new WizEntitlementGrantController(engine);
      return new SecurityProvider[] { provider };
   }

   private void failingProvider(String permission) {
      SecurityProvider provider = delegating()[0];
      doThrow(new RuntimeException("storage down")).when(provider).setPermission(
         eq(ResourceType.WIZ_PERMISSION), eq(permission), any(Permission.class), anyString());
   }

   @Test
   void failedFirstGrantLeavesTheStoredPermissionAndTheDefaultAllowUntouched() throws Exception {
      SecurityProvider real = SecurityEngine.getSecurity().getSecurityProvider();
      real.setPermission(ResourceType.WIZ_PERMISSION, "plugin.use", new Permission(), ORG_A);
      Permission stored = real.getPermission(ResourceType.WIZ_PERMISSION, "plugin.use", ORG_A);
      assertFalse(stored.hasOrgEditedGrantAll(ORG_A));
      failingProvider("plugin.use");
      SRPrincipal writer = wiz("writer", ORG_A);

      assertEquals(500, status(() -> post(request("plugin.use", "user", "carol", true), writer)));

      Permission after = real.getPermission(ResourceType.WIZ_PERMISSION, "plugin.use", ORG_A);
      assertFalse(after.hasOrgEditedGrantAll(ORG_A), "the live edited flag must not flip");
      assertTrue(after.getOrgScopedUserGrants(ResourceAction.ACCESS, ORG_A).isEmpty());
      assertTrue(holds("plain", "plugin.use", false), "everybody keeps the default-allow");
   }

   @Test
   void failedRevokeOfTheLastGrantKeepsTheRestriction() throws Exception {
      post(request("plugin.use", "user", "carol", true), wiz("writer", ORG_A));
      SecurityProvider real = SecurityEngine.getSecurity().getSecurityProvider();
      assertTrue(real.getPermission(ResourceType.WIZ_PERMISSION, "plugin.use", ORG_A)
                    .hasOrgEditedGrantAll(ORG_A));
      failingProvider("plugin.use");
      SRPrincipal writer = wiz("writer", ORG_A);

      assertEquals(500, status(() -> post(request("plugin.use", "user", "carol", false), writer)));

      Permission after = real.getPermission(ResourceType.WIZ_PERMISSION, "plugin.use", ORG_A);
      assertTrue(after.hasOrgEditedGrantAll(ORG_A), "the live edited flag must not be removed");
      assertEquals(1, after.getOrgScopedUserGrants(ResourceAction.ACCESS, ORG_A).size());
      assertFalse(holds("plain", "plugin.use", false));
      assertTrue(holds("carol", "plugin.use", false));
   }

   @Test
   void concurrentGrantsOfTheSamePermissionAreNotLost() throws Exception {
      SecurityProvider realProvider = SecurityEngine.getSecurity().getSecurityProvider();
      SecurityProvider provider = delegating()[0];
      WizEntitlementGrantController racing = controller;
      CountDownLatch bothRead = new CountDownLatch(2);
      doAnswer(inv -> {
         Object result = realProvider.getPermission(
            ResourceType.WIZ_PERMISSION, "config.read", inv.<String>getArgument(2));
         bothRead.countDown();
         bothRead.await(1500, TimeUnit.MILLISECONDS);
         return result;
      }).when(provider).getPermission(
         eq(ResourceType.WIZ_PERMISSION), eq("config.read"), anyString());
      SRPrincipal writer = wiz("writer", ORG_A);
      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         List<Future<GrantEntry>> results = new ArrayList<>();

         for(String user : List.of("carol", "reader")) {
            results.add(pool.submit(() -> {
               MockHttpServletRequest request = new MockHttpServletRequest();
               request.addHeader("Authorization", "Bearer test-jwt");
               RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
               ThreadContext.setContextPrincipal(writer);
               return racing.setGrant(request("config.read", "user", user, true), writer);
            }));
         }

         for(Future<GrantEntry> f : results) {
            f.get(30, TimeUnit.SECONDS);
         }
      }
      finally {
         pool.shutdownNow();
      }

      assertEquals(List.of("carol", "reader"),
                   entry(controller.getGrants(writer), "config.read").users());
   }

   // ---- validation details -------------------------------------------------------------------------

   @Test
   void principalResolutionFailuresAllGiveTheSameMessage() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);
      Set<String> messages = new HashSet<>();

      for(String bad : List.of("carol^" + ORG_B, "carol^nosuchorg", "carol:" + ORG_B,
                               "carol:nosuchorg", "nobody", "nobody^" + ORG_B, "a~;~b"))
      {
         ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> post(request("config.read", "user", bad, true), writer), bad);
         assertEquals(400, ex.getStatusCode().value());
         messages.add(ex.getReason());
      }

      assertEquals(1, messages.size(), messages.toString());
      assertFalse(messages.iterator().next().contains(ORG_B));
   }

   @Test
   void grantedMustBeARealBooleanAndPrincipalIsLengthCapped() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);

      for(Object notBoolean : new Object[] { 1, "true", "false", 0 }) {
         assertEquals(400, status(() ->
            post(new GrantRequest("config.read", "user", "carol", notBoolean), writer)));
      }

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> post(request("config.read", "user", "x".repeat(257), true), writer));
      assertEquals(400, ex.getStatusCode().value());
      assertTrue(ex.getReason().startsWith("principal:"));
      assertTrue(entry(controller.getGrants(writer), "config.read").users().isEmpty());
   }

   @Test
   void aNonXPrincipalIsUnauthorizedNotAServerError() {
      Principal plain = mock(Principal.class);
      assertEquals(401, status(() -> {
         try {
            controller.getGrants(plain);
         }
         catch(inetsoft.sree.security.SecurityException e) {
            throw new RuntimeException(e);
         }
      }));
      assertEquals(401, status(() ->
         post(request("config.read", "user", "carol", true), plain)));
   }

   // ---- audit -------------------------------------------------------------------------------------------

   @Test
   void theAuditRecordCarriesTheCallersOrganizationAndNoErrorText() throws Exception {
      Audit audit = mock(Audit.class);

      try(MockedStatic<Audit> mocked = Mockito.mockStatic(Audit.class)) {
         mocked.when(Audit::getInstance).thenReturn(audit);
         post(request("config.read", "user", "carol", true), wiz("writer", ORG_A));
      }

      ArgumentCaptor<ActionRecord> captor = ArgumentCaptor.forClass(ActionRecord.class);
      verify(audit).auditAction(captor.capture(), any());
      ActionRecord record = captor.getValue();
      assertEquals(ORG_A, record.getResourceOrganization());
      assertEquals("wizGrantOrgAName", record.getResourceOrganizationName());
      assertEquals(ActionRecord.ACTION_STATUS_SUCCESS, record.getActionStatus());
      assertTrue(record.getActionError() == null || record.getActionError().isEmpty());
      assertTrue(record.getObjectName().contains("config.read"));
   }

   // ---- facts about global roles (pinned) ---------------------------------------------------------

   @Test
   void aGlobalRoleCannotBeGranted() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> post(request("config.read", "role", "globalr", true), writer));
      assertEquals(400, ex.getStatusCode().value());
      assertEquals("principal: global roles cannot be granted Wiz permissions; " +
                   "use an organization role", ex.getReason());

      assertTrue(entry(controller.getGrants(writer), "config.read").roles().isEmpty());
      assertFalse(holds("carol", "config.read", false));
   }

   @Test
   void anOrganizationRoleGrantIsEffectiveForItsMembers() throws Exception {
      for(boolean multiTenant : new boolean[] { false, true }) {
         try(MockedStatic<SUtil> mocked =
                Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS))
         {
            mocked.when(SUtil::isMultiTenant).thenReturn(multiTenant);
            assertEquals(List.of("r1"),
                         post(request("orgconfig.read", "role", "r1", true), wiz("writer", ORG_A))
                            .roles());
         }

         assertTrue(holds("member", "orgconfig.read", multiTenant));
         assertFalse(holds("plain", "orgconfig.read", multiTenant));
         post(request("orgconfig.read", "role", "r1", false), wiz("writer", ORG_A));
      }
   }

   // ---- revoke of stale grants ---------------------------------------------------------------------

   /** A record as an older write could have left it: a grant of a user that no longer exists. */
   private void storeStaleUserGrant(String permission, String ghost) {
      Permission stale = new Permission();
      stale.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of(ghost), ORG_A);
      stale.updateGrantAllByOrg(ORG_A, true);
      SecurityEngine.getSecurity().getSecurityProvider()
         .setPermission(ResourceType.WIZ_PERMISSION, permission, stale, ORG_A);
   }

   @Test
   void aStaleGrantOfAVanishedUserCanBeRevokedAndRestoresDefaultAllow() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);
      storeStaleUserGrant("plugin.use", "ghost");
      assertFalse(holds("plain", "plugin.use", false));
      // a grant of that vanished user cannot be made, only removed
      assertEquals(400, status(() -> post(request("plugin.use", "user", "ghost", true), writer)));

      GrantEntry result = post(request("plugin.use", "user", "ghost", false), writer);

      assertTrue(result.users().isEmpty(), result.toString());
      assertFalse(result.restricted());
      assertTrue(holds("plain", "plugin.use", false));
   }

   @Test
   void deletingAGrantedUserRemovesItsGrantButLeavesTheRecordEdited() throws Exception {
      // characterization: FileAuthorizationProvider.authenticationChanged rewrites the stored
      // permissions when an identity is removed, but the org's edited flag is not touched, so an
      // end-user permission whose only grantee was deleted is left denied for everybody
      SRPrincipal writer = wiz("writer", ORG_A);
      post(request("plugin.use", "user", "carol", true), writer);
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      ((AuthenticationChain) provider.getAuthenticationProvider()).getProviders().stream()
         .filter(EditableAuthenticationProvider.class::isInstance)
         .map(EditableAuthenticationProvider.class::cast)
         .forEach(p -> p.removeUser(new IdentityID("carol", ORG_A)));

      Permission stored = provider.getPermission(ResourceType.WIZ_PERMISSION, "plugin.use", ORG_A);
      assertTrue(stored.getOrgScopedUserGrants(ResourceAction.ACCESS, ORG_A).isEmpty());
      assertTrue(stored.hasOrgEditedGrantAll(ORG_A));
      assertFalse(holds("plain", "plugin.use", false));
   }

   @Test
   void aRevokeNamingAnotherOrganizationIsRejectedAndChangesNothing() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);
      post(request("config.read", "user", "carol", true), writer);

      for(String other : List.of("carol:" + ORG_B, "carol^" + ORG_B, "dave:" + ORG_B)) {
         assertEquals(400, status(() -> post(request("config.read", "user", other, false), writer)),
                      other);
      }

      assertEquals(List.of("carol"), entry(controller.getGrants(writer), "config.read").users());
   }

   @Test
   void aRevokeOfAGlobalRoleNameIsTheIdempotentNoOp() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);
      post(request("config.read", "role", "r1", true), writer);

      GrantEntry result = post(request("config.read", "role", "globalr", false), writer);

      assertEquals(List.of("r1"), result.roles());
   }

   // ---- audit outside the lock --------------------------------------------------------------------------

   @Test
   void theAuditCallRunsAfterTheWriteLockIsReleased() throws Exception {
      Audit audit = mock(Audit.class);
      SRPrincipal writer = wiz("writer", ORG_A);
      ExecutorService pool = Executors.newSingleThreadExecutor();
      boolean[] otherWriteFinishedDuringAudit = { false };
      doAnswer(inv -> {
         if(otherWriteFinishedDuringAudit[0]) {
            return null;
         }

         Future<?> other = pool.submit(() -> {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer test-jwt");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            ThreadContext.setContextPrincipal(writer);
            return controller.setGrant(request("config.write", "user", "reader", true), writer);
         });

         try {
            other.get(5, TimeUnit.SECONDS);
            otherWriteFinishedDuringAudit[0] = true;
         }
         catch(TimeoutException e) {
            other.cancel(true);
         }

         return null;
      }).when(audit).auditAction(any(ActionRecord.class), any());

      try(MockedStatic<Audit> mocked = Mockito.mockStatic(Audit.class)) {
         mocked.when(Audit::getInstance).thenReturn(audit);
         post(request("config.read", "user", "carol", true), writer);
      }
      finally {
         pool.shutdownNow();
      }

      assertTrue(otherWriteFinishedDuringAudit[0],
                 "another write must be able to run while the audit call is in progress");
   }

   // ---- restricted-to-nobody and reset ---------------------------------------------------------------

   private GrantEntry reset(String permission, Principal caller) {
      try {
         return controller.resetGrants(new WizEntitlementGrantController.ResetRequest(permission),
                                       caller);
      }
      catch(inetsoft.sree.security.SecurityException e) {
         throw new RuntimeException(e);
      }
   }

   private void deleteUser(String name) {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      ((AuthenticationChain) provider.getAuthenticationProvider()).getProviders().stream()
         .filter(EditableAuthenticationProvider.class::isInstance)
         .map(EditableAuthenticationProvider.class::cast)
         .forEach(p -> p.removeUser(new IdentityID(name, ORG_A)));
   }

   @Test
   void resetClearsTheRestrictedToNobodyStateLeftByADeletedGrantee() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);
      post(request("plugin.use", "user", "carol", true), writer);
      deleteUser("carol");

      assertFalse(holds("plain", "plugin.use", false));
      GrantEntry seen = entry(controller.getGrants(writer), "plugin.use");
      assertTrue(seen.restricted());
      assertTrue(seen.defaultAllow());
      assertTrue(seen.users().isEmpty());

      GrantEntry result = reset("plugin.use", writer);

      assertFalse(result.restricted());
      assertTrue(result.defaultAllow());
      assertTrue(holds("plain", "plugin.use", false));
   }

   @Test
   void defaultAllowTellsEndUserFromAdminPermissions() throws Exception {
      List<GrantEntry> all = controller.getGrants(wiz("reader", ORG_A));
      assertTrue(entry(all, "plugin.use").defaultAllow());
      assertFalse(entry(all, "config.read").defaultAllow());
      assertFalse(entry(all, "plugin.use").restricted());
   }

   @Test
   void resetNeedsEntitlementWrite() throws Exception {
      post(request("config.read", "user", "carol", true), wiz("writer", ORG_A));

      for(String user : List.of("plain", "reader")) {
         SRPrincipal caller = wiz(user, ORG_A);
         assertEquals(403, status(() -> reset("config.read", caller)));
      }

      assertEquals(List.of("carol"),
                   entry(controller.getGrants(wiz("reader", ORG_A)), "config.read").users());
   }

   @Test
   void resetOnlyAffectsTheCallersOrganization() throws Exception {
      // dave holds config.read in org B
      post(request("config.read", "user", "carol", true), wiz("writer", ORG_A));

      GrantEntry result = reset("config.read", wiz("writer", ORG_A));

      assertTrue(result.users().isEmpty());
      assertEquals(List.of("dave"),
                   entry(controller.getGrants(wiz("readerb", ORG_B)), "config.read").users());
   }

   @Test
   void aFailedStoreDuringResetLeavesTheStoredPermissionUnchanged() throws Exception {
      post(request("plugin.use", "user", "carol", true), wiz("writer", ORG_A));
      SecurityProvider real = SecurityEngine.getSecurity().getSecurityProvider();
      failingProvider("plugin.use");
      SRPrincipal writer = wiz("writer", ORG_A);

      assertEquals(500, status(() -> reset("plugin.use", writer)));

      Permission after = real.getPermission(ResourceType.WIZ_PERMISSION, "plugin.use", ORG_A);
      assertTrue(after.hasOrgEditedGrantAll(ORG_A));
      assertEquals(1, after.getOrgScopedUserGrants(ResourceAction.ACCESS, ORG_A).size());
      assertFalse(holds("plain", "plugin.use", false));
   }

   @Test
   void resetOfAnUntouchedPermissionIsANoOpWithoutAWrite() throws Exception {
      SecurityProvider provider = delegating()[0];

      GrantEntry result = reset("plugin.use", wiz("writer", ORG_A));

      assertFalse(result.restricted());
      verify(provider, never()).setPermission(any(ResourceType.class), anyString(),
                                              any(Permission.class), anyString());
   }

   @Test
   void resetRejectsAnUnknownPermission() {
      SRPrincipal writer = wiz("writer", ORG_A);
      assertEquals(400, status(() -> reset("nope.read", writer)));
      assertEquals(400, status(() -> reset(null, writer)));
   }

   @Test
   void resettingAnAdminPermissionRemovesItsGrantsAndDeniesItAgain() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);
      post(request("entitlement.read", "user", "plain", true), writer);
      assertTrue(holds("plain", "entitlement.read", false));

      GrantEntry result = reset("entitlement.read", writer);

      assertTrue(result.users().isEmpty());
      assertFalse(result.defaultAllow());
      assertFalse(holds("plain", "entitlement.read", false));
   }

   @Test
   void resetIsAllowedForGlobalConfigInMultiTenantMode() throws Exception {
      post(request("config.read", "user", "carol", true), wiz("writer", ORG_A));

      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);
         assertTrue(reset("config.read", wiz("orgadmin", ORG_A)).users().isEmpty());
      }
   }

   @Test
   void restrictedIsOnlyTrueForAnEndUserPermissionWhoseDefaultAllowIsSwitchedOff() throws Exception {
      SRPrincipal writer = wiz("writer", ORG_A);

      GrantEntry admin = post(request("config.read", "user", "carol", true), writer);
      assertFalse(admin.restricted());
      assertFalse(admin.defaultAllow());
      assertEquals(List.of("carol"), admin.users());
      assertFalse(entry(controller.getGrants(writer), "config.read").restricted());

      // an edited admin permission with no grants is not "restricted" either
      post(request("config.read", "user", "carol", false), writer);
      storeEditedWithoutGrants("config.write");
      assertFalse(entry(controller.getGrants(writer), "config.write").restricted());

      assertFalse(entry(controller.getGrants(writer), "plugin.use").restricted());
      assertTrue(post(request("plugin.use", "user", "carol", true), writer).restricted());
      assertTrue(post(request("plugin.use", "user", "carol", false), writer).users().isEmpty());
      assertFalse(entry(controller.getGrants(writer), "plugin.use").restricted());

      storeEditedWithoutGrants("plugin.use");
      GrantEntry nobody = entry(controller.getGrants(writer), "plugin.use");
      assertTrue(nobody.restricted());
      assertTrue(nobody.users().isEmpty());
      assertFalse(reset("plugin.use", writer).restricted());
   }

   private void storeEditedWithoutGrants(String permission) {
      Permission edited = new Permission();
      edited.updateGrantAllByOrg(ORG_A, true);
      SecurityEngine.getSecurity().getSecurityProvider()
         .setPermission(ResourceType.WIZ_PERMISSION, permission, edited, ORG_A);
   }

   private GrantEntry post(GrantRequest request, Principal caller) {
      try {
         return controller.setGrant(request, caller);
      }
      catch(inetsoft.sree.security.SecurityException e) {
         throw new RuntimeException(e);
      }
   }
}
