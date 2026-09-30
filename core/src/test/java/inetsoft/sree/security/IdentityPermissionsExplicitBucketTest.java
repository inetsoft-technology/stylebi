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

import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77271: IdentityService.setIdentityPermissions() with an explicit bucket org, which the
 * enterprise REST org endpoints use because they do not switch into the target org. The grant
 * must be read, written and removed in the bucket org, and the existing grantees the caller
 * cannot administer must be kept from the bucket org, not from the caller's (ambient) org.
 *
 * The authorization provider is an in-memory map that records which bucket each call used, with
 * the ambient (2-arg) calls kept apart from the explicit (4-arg) ones.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityPermissionsExplicitBucketTest {
   private static final String HOST_ORG = "host77271";
   private static final String TARGET_ORG = "target77271";
   private static final String OTHER_ORG = "other77271";
   private static final String AMBIENT = "<ambient>";
   private static final ResourceType ORG = ResourceType.SECURITY_ORGANIZATION;
   private static final ResourceAction ADMIN = ResourceAction.ADMIN;

   private final Map<String, Permission> store = new HashMap<>();
   private final Set<String> hidden = new HashSet<>();
   private AuthorizationProvider authz;
   private IdentityService identityService;
   private SRPrincipal caller;

   @BeforeEach
   void setUp() {
      authz = mock(AuthorizationProvider.class);
      when(authz.getPermission(any(ResourceType.class), any(IdentityID.class), anyString()))
         .thenAnswer(inv -> store.get(key(inv.getArgument(1), inv.getArgument(2))));
      when(authz.getPermission(any(ResourceType.class), any(IdentityID.class)))
         .thenAnswer(inv -> store.get(key(inv.getArgument(1), AMBIENT)));
      doAnswer(inv -> store.put(key(inv.getArgument(1), inv.getArgument(3)), inv.getArgument(2)))
         .when(authz).setPermission(any(ResourceType.class), any(IdentityID.class),
                                    any(Permission.class), anyString());
      doAnswer(inv -> store.put(key(inv.getArgument(1), AMBIENT), inv.getArgument(2)))
         .when(authz).setPermission(any(ResourceType.class), any(IdentityID.class),
                                    any(Permission.class));
      doAnswer(inv -> store.remove(key(inv.getArgument(1), inv.getArgument(2))))
         .when(authz).removePermission(any(ResourceType.class), any(IdentityID.class), anyString());
      doAnswer(inv -> store.remove(key(inv.getArgument(1), AMBIENT)))
         .when(authz).removePermission(any(ResourceType.class), any(IdentityID.class));

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(authz);
      // the caller can administer every grantee except the hidden ones
      when(securityProvider.checkAnyPermission(
         any(Principal.class), any(ResourceType.class), anyString(), any()))
         .thenAnswer(inv -> !hidden.contains(inv.getArgument(1) + "|" + inv.getArgument(2)));

      identityService = new IdentityService(
         null, securityProvider,
         null, null, null, null, null, null, null, null, null, null, null, null, // positions 3-14
         Optional.empty(),                                                    // position 15
         null, null, null, null, null, null, null, null, null, null, null, null, null, // 16-28
         Optional.empty());                                                   // position 29

      // the REST org endpoints run in the caller's org, not in the edited org
      caller = new SRPrincipal(new IdentityID("caller", HOST_ORG), new IdentityID[0],
                               new String[0], HOST_ORG, 0L);
      ThreadContext.setContextPrincipal(caller);
      assertEquals(HOST_ORG, OrganizationManager.getInstance().getCurrentOrgID(),
                   "precondition: the ambient org must be the caller's org");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   @Test
   void explicitBucket_keepsHiddenGranteesOfEveryTypeInBucketOrg() {
      IdentityID orgKey = new IdentityID("Target", TARGET_ORG);
      Permission existing = new Permission();
      existing.setUserGrantsForOrg(ADMIN, Set.of("hiddenUser", "visibleUser"), TARGET_ORG);
      existing.setGroupGrantsForOrg(ADMIN, Set.of("hiddenGroup"), TARGET_ORG);
      existing.setRoleGrantsForOrg(ADMIN, Set.of("hiddenRole"), TARGET_ORG);
      existing.setRoleGrantsForOrg(ADMIN, Set.of("hiddenGlobalRole"), null);
      existing.setOrganizationGrantsForOrg(ADMIN, Set.of("hiddenOrg"), TARGET_ORG);
      store.put(key(orgKey, TARGET_ORG), existing);
      hide(ResourceType.SECURITY_USER, new IdentityID("hiddenUser", TARGET_ORG));
      hide(ResourceType.SECURITY_GROUP, new IdentityID("hiddenGroup", TARGET_ORG));
      hide(ResourceType.SECURITY_ROLE, new IdentityID("hiddenRole", TARGET_ORG));
      hide(ResourceType.SECURITY_ROLE, new IdentityID("hiddenGlobalRole", null));
      hide(ResourceType.SECURITY_ORGANIZATION, new IdentityID("hiddenOrg", TARGET_ORG));

      // a grant under the same key in the caller's bucket must not be merged or touched
      Permission ambient = new Permission();
      ambient.setUserGrantsForOrg(ADMIN, Set.of("hostHidden"), HOST_ORG);
      store.put(key(orgKey, AMBIENT), ambient);
      hide(ResourceType.SECURITY_USER, new IdentityID("hostHidden", HOST_ORG));

      identityService.setIdentityPermissions(
         orgKey, orgKey, ORG, caller, List.of(user("newAdmin")), TARGET_ORG, TARGET_ORG);

      Permission saved = store.get(key(orgKey, TARGET_ORG));
      assertEquals(Set.of("hiddenUser", "newAdmin"), names(saved.getUserGrants(ADMIN, TARGET_ORG)),
                   "hidden users must be kept and the visible user the request dropped revoked");
      assertEquals(Set.of("hiddenGroup"), names(saved.getGroupGrants(ADMIN, TARGET_ORG)));
      assertEquals(Set.of("hiddenRole", "hiddenGlobalRole"),
                   names(saved.getRoleGrants(ADMIN, TARGET_ORG)));
      assertEquals(Set.of("hiddenGlobalRole"),
                   saved.getRoleGrants(ADMIN, TARGET_ORG).stream()
                      .filter(r -> r.getOrganizationID() == null)
                      .map(Permission.PermissionIdentity::getName).collect(Collectors.toSet()),
                   "a hidden global role must stay global");
      assertEquals(Set.of("hiddenOrg"), names(saved.getOrganizationGrants(ADMIN, TARGET_ORG)));
      assertTrue(names(saved.getAllUserGrants(ADMIN)).stream().noneMatch("hostHidden"::equals),
                 "grantees of the caller's bucket must not be merged into the target bucket");
      assertTrue(saved.hasOrgEditedGrantAll(TARGET_ORG), "a non-empty list sets the edited flag");
      assertSame(ambient, store.get(key(orgKey, AMBIENT)), "the caller's bucket is untouched");
      verifyNoAmbientCalls();
   }

   @Test
   void explicitBucket_leavesGrantsOfOtherOrgsUnscoped() {
      IdentityID orgKey = new IdentityID("Target", TARGET_ORG);
      Permission existing = new Permission();
      existing.setUserGrantsForOrg(ADMIN, Set.of("otherUser"), OTHER_ORG);
      store.put(key(orgKey, TARGET_ORG), existing);
      hide(ResourceType.SECURITY_USER, new IdentityID("otherUser", OTHER_ORG));

      identityService.setIdentityPermissions(
         orgKey, orgKey, ORG, caller, List.of(), TARGET_ORG, TARGET_ORG);

      Permission saved = store.get(key(orgKey, TARGET_ORG));
      assertEquals(Set.of("otherUser"), names(saved.getUserGrants(ADMIN, OTHER_ORG)),
                   "a grant scoped to another org must stay in that org");
      assertTrue(saved.getUserGrants(ADMIN, TARGET_ORG).isEmpty(),
                 "a grant scoped to another org must not be re-scoped to the bucket org");
      assertFalse(saved.hasOrgEditedGrantAll(TARGET_ORG), "an empty list sets no edited flag");
      verifyNoAmbientCalls();
   }

   @Test
   void explicitBucket_emptyGranteeScopeDefaultsToBucketOrg() {
      IdentityID orgKey = new IdentityID("Target", TARGET_ORG);

      identityService.setIdentityPermissions(
         orgKey, orgKey, ORG, caller, List.of(user("newAdmin")), "", TARGET_ORG);

      Permission saved = store.get(key(orgKey, TARGET_ORG));
      assertEquals(Set.of("newAdmin"), names(saved.getUserGrants(ADMIN, TARGET_ORG)),
                   "the grantees must be scoped to the bucket org, not to the ambient org");
      assertTrue(saved.getUserGrants(ADMIN, HOST_ORG).isEmpty());
      verifyNoAmbientCalls();
   }

   @Test
   void explicitBucket_rekeyRemovesOldAndLegacyKeysFromBucketOrg() {
      IdentityID legacyKey = new IdentityID(TARGET_ORG, TARGET_ORG);
      IdentityID renamedKey = new IdentityID("Renamed", TARGET_ORG);
      Permission legacy = new Permission();
      legacy.setUserGrantsForOrg(ADMIN, Set.of("hiddenUser"), TARGET_ORG);
      store.put(key(legacyKey, TARGET_ORG), legacy);
      hide(ResourceType.SECURITY_USER, new IdentityID("hiddenUser", TARGET_ORG));

      identityService.setIdentityPermissions(
         legacyKey, renamedKey, ORG, caller, List.of(user("newAdmin")), TARGET_ORG, TARGET_ORG);

      assertNull(store.get(key(legacyKey, TARGET_ORG)), "the old (id, id) key must be removed");
      assertEquals(Set.of("hiddenUser", "newAdmin"),
                   names(store.get(key(renamedKey, TARGET_ORG)).getUserGrants(ADMIN, TARGET_ORG)),
                   "the re-keyed grant must keep the old grant's hidden grantees");
      verifyNoAmbientCalls();
   }

   @Test
   void ambientBucket_keepsTodaysAmbientProviderCalls() {
      IdentityID orgKey = new IdentityID("Host", HOST_ORG);
      Permission existing = new Permission();
      existing.setUserGrantsForOrg(ADMIN, Set.of("hiddenUser"), HOST_ORG);
      store.put(key(orgKey, AMBIENT), existing);
      hide(ResourceType.SECURITY_USER, new IdentityID("hiddenUser", HOST_ORG));

      identityService.setIdentityPermissions(
         orgKey, orgKey, ORG, caller, List.of(user("newAdmin", HOST_ORG)), HOST_ORG);

      // the grant is read and removed in the ambient org, but written under the resolved org
      // (here the ambient org too), never the ambient 3-arg setPermission (Bug #76866)
      assertEquals(Set.of("hiddenUser", "newAdmin"),
                   names(store.get(key(orgKey, HOST_ORG)).getUserGrants(ADMIN, HOST_ORG)),
                   "the ambient path must keep the ambient org's hidden grantees");
      verify(authz).getPermission(ORG, orgKey);
      verify(authz).setPermission(eq(ORG), eq(orgKey), any(Permission.class), eq(HOST_ORG));
      verify(authz).removePermission(ORG, new IdentityID(HOST_ORG, HOST_ORG));
      verify(authz, never()).getPermission(any(ResourceType.class), any(IdentityID.class), any());
      verify(authz, never()).setPermission(any(ResourceType.class), any(IdentityID.class),
                                           any(Permission.class));
      verify(authz, never()).removePermission(any(ResourceType.class), any(IdentityID.class), any());
   }

   private void verifyNoAmbientCalls() {
      verify(authz, never()).getPermission(any(ResourceType.class), any(IdentityID.class));
      verify(authz, never()).setPermission(any(ResourceType.class), any(IdentityID.class),
                                           any(Permission.class));
      verify(authz, never()).removePermission(any(ResourceType.class), any(IdentityID.class));
      verify(authz, never()).getPermission(any(ResourceType.class), any(IdentityID.class),
                                           not(eq(TARGET_ORG)));
      verify(authz, never()).setPermission(any(ResourceType.class), any(IdentityID.class),
                                           any(Permission.class), not(eq(TARGET_ORG)));
      verify(authz, never()).removePermission(any(ResourceType.class), any(IdentityID.class),
                                              not(eq(TARGET_ORG)));
   }

   private void hide(ResourceType type, IdentityID identity) {
      hidden.add(type + "|" + identity.convertToKey());
   }

   private static String key(IdentityID identity, String bucket) {
      return bucket + "|" + identity.convertToKey();
   }

   private static Set<String> names(Set<Permission.PermissionIdentity> grants) {
      return grants.stream().map(Permission.PermissionIdentity::getName)
         .collect(Collectors.toSet());
   }

   private static IdentityModel user(String name) {
      return user(name, TARGET_ORG);
   }

   private static IdentityModel user(String name, String orgId) {
      return IdentityModel.builder()
         .identityID(new IdentityID(name, orgId))
         .type(Identity.USER)
         .build();
   }
}
