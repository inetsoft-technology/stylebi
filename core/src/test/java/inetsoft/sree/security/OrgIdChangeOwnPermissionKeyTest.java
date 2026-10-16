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

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.util.Tuple4;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78497, an organization id change moves the kept members first
 * ({@code IdentityService.updateOrganizationMembers()}) and the organization second
 * ({@code updateIdentityPermissions(ORGANIZATION, ...)}, as
 * {@code AbstractEditableAuthenticationProvider.copyOrganizationInternal()} does). The member
 * step must leave the organization's own SECURITY_ORGANIZATION key alone, so the organization
 * step moves it from {@code oldName~;~oldId} to {@code newName~;~newId} and re-scopes its
 * grantees to the new id, with nothing left in either organization.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgIdChangeOwnPermissionKeyTest {
   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider fileProvider;
   private IdentityService identityService;
   private Principal savedPrincipal;
   private final Set<String> touchedOrgs = new HashSet<>();

   @BeforeEach
   void saveContext() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() {
      try {
         if(builder != null) {
            AuthorizationChain chain = chain();

            // the moved members and the migrated keys are not tracked by the builder
            for(Tuple4<ResourceType, String, String, Permission> entry : chain.getPermissions()) {
               if(touchedOrgs.contains(entry.getSecond())) {
                  chain.removePermission(entry.getFirst(), entry.getThird(), entry.getSecond());
               }
            }

            for(IdentityID user : fileProvider.getUsers()) {
               if(touchedOrgs.contains(user.getOrgID())) {
                  fileProvider.removeUser(user);
               }
            }

            for(IdentityID group : fileProvider.getGroups()) {
               if(touchedOrgs.contains(group.getOrgID())) {
                  fileProvider.removeGroup(group);
               }
            }

            for(IdentityID role : fileProvider.getRoles()) {
               if(touchedOrgs.contains(role.getOrgID())) {
                  fileProvider.removeRole(role);
               }
            }
         }
      }
      finally {
         if(builder != null) {
            builder.teardown();
            builder = null;
         }

         touchedOrgs.clear();
         ThreadContext.setContextPrincipal(savedPrincipal);
      }
   }

   // the reported case, a name and id change with a kept member and no explicit admin list
   @Test
   void nameAndIdChangeWithKeptUserMovesOwnKeyToNewNameAndId() throws Exception {
      setUp("B78497From", "b78497_from", "B78497To", "b78497_to",
            List.of("u1"), List.of(), List.of());

      changeOrg("B78497From", "b78497_from", "B78497To", "b78497_to", "u1");

      assertOnlyOwnKey("B78497To", "b78497_to", "b78497_from", Set.of("u1"), Set.of(), Set.of());
   }

   // a kept user, group and role all move, and a member has the organization's name
   @Test
   void memberNamedLikeOrgDoesNotReKeyOwnKey() throws Exception {
      setUp("b78497same", "b78497_same_from", "B78497SameTo", "b78497_same_to",
            List.of("b78497same", "u2"), List.of("g1"), List.of("r1"));

      changeOrg("b78497same", "b78497_same_from", "B78497SameTo", "b78497_same_to",
                "b78497same", "u2", "g1", "r1");

      assertOnlyOwnKey("B78497SameTo", "b78497_same_to", "b78497_same_from",
                       Set.of("b78497same", "u2"), Set.of("g1"), Set.of("r1"));
   }

   @Test
   void idOnlyChangeKeepsName() throws Exception {
      setUp("B78497Keep", "b78497_idonly_from", "B78497Keep", "b78497_idonly_to",
            List.of("u1"), List.of(), List.of());

      changeOrg("B78497Keep", "b78497_idonly_from", "B78497Keep", "b78497_idonly_to", "u1");

      assertOnlyOwnKey("B78497Keep", "b78497_idonly_to", "b78497_idonly_from",
                       Set.of("u1"), Set.of(), Set.of());
   }

   // a name change keeps the id, so the member step must not touch the key at all
   @Test
   void nameOnlyChangeLeavesOwnKeyToMemberStep() throws Exception {
      String orgId = "b78497_nameonly";
      setUp("B78497NameFrom", orgId, null, null, List.of("u1"), List.of(), List.of());

      FSOrganization renamed = new FSOrganization(orgId);
      renamed.setName("B78497NameTo");
      renamed.setMembers(new String[] { "u1" });
      moveMembers(renamed, orgId);

      assertEquals(Set.of(key("B78497NameFrom", orgId)), ownKeys(orgId),
                   "a same-id member save must not re-key the organization's own key");
   }

   // no member is kept, so only the organization step moves the key
   @Test
   void nameAndIdChangeWithoutMembers() throws Exception {
      String fromId = "b78497_empty_from";
      String toId = "b78497_empty_to";
      touchedOrgs.add(fromId);
      touchedOrgs.add(toId);
      // the grantee is not a user of the organization, so there is no member to move
      builder = SecurityTestDataBuilder.create()
         .addOrg("B78497Empty", fromId)
         .addOrg("B78497EmptyTo", toId)
         .grantPermission(ResourceType.SECURITY_ORGANIZATION, key("B78497Empty", fromId),
                          ResourceAction.ADMIN, "u1", Identity.USER, fromId)
         .setup();
      initService();

      changeOrg("B78497Empty", fromId, "B78497EmptyTo", toId);

      assertOnlyOwnKey("B78497EmptyTo", toId, fromId, Set.of("u1"), Set.of(), Set.of());
   }

   @Test
   void renameBackRestoresOriginalKey() throws Exception {
      setUp("B78497A", "b78497_rt_a", "B78497B", "b78497_rt_b",
            List.of("u1"), List.of("g1"), List.of());

      changeOrg("B78497A", "b78497_rt_a", "B78497B", "b78497_rt_b", "u1", "g1");
      assertOnlyOwnKey("B78497B", "b78497_rt_b", "b78497_rt_a", Set.of("u1"), Set.of("g1"),
                       Set.of());

      changeOrg("B78497B", "b78497_rt_b", "B78497A", "b78497_rt_a", "u1", "g1");
      assertOnlyOwnKey("B78497A", "b78497_rt_a", "b78497_rt_b", Set.of("u1"), Set.of("g1"),
                       Set.of());
   }

   private void setUp(String fromName, String fromId, String toName, String toId,
                      List<String> users, List<String> groups, List<String> roles)
      throws Exception
   {
      builder = SecurityTestDataBuilder.create()
         .addOrg(fromName, fromId);

      touchedOrgs.add(fromId);

      // both organizations exist when the organization step runs, as in copyOrganizationInternal()
      if(toId != null) {
         builder.addOrg(toName, toId);
         touchedOrgs.add(toId);
      }

      String orgKey = key(fromName, fromId);

      for(String user : users) {
         builder.addUser(user, fromId, "password")
            .grantPermission(ResourceType.SECURITY_ORGANIZATION, orgKey, ResourceAction.ADMIN,
                             user, Identity.USER, fromId);
      }

      for(String group : groups) {
         builder.addGroup(group, fromId)
            .grantPermission(ResourceType.SECURITY_ORGANIZATION, orgKey, ResourceAction.ADMIN,
                             group, Identity.GROUP, fromId);
      }

      for(String role : roles) {
         builder.addRole(role, fromId)
            .grantPermission(ResourceType.SECURITY_ORGANIZATION, orgKey, ResourceAction.ADMIN,
                             role, Identity.ROLE, fromId);
      }

      builder.setup();
      initService();
   }

   private void initService() {
      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      identityService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, mock(FavoritesService.class), null, null, null, null,
         null, null, null, null, Optional.empty(), null, null, null,
         mock(DashboardRegistryManager.class), null, null, null, null, null, null, null, null,
         mock(RepletRegistryManager.class), Optional.empty());
   }

   // the permission steps of IdentityService.setOrganizationInfo() for an id change
   private void changeOrg(String fromName, String fromId, String toName, String toId,
                          String... members)
   {
      touchedOrgs.add(fromId);
      touchedOrgs.add(toId);

      FSOrganization renamed = new FSOrganization(toId);
      renamed.setName(toName);
      renamed.setMembers(members);
      moveMembers(renamed, fromId);

      IdentityID oldOrg = new IdentityID(fromName, fromId);
      IdentityID newOrg = new IdentityID(toName, toId);
      identityService.addCopiedIdentityPermission(oldOrg, newOrg, toId, Identity.ORGANIZATION,
                                                  true);
      identityService.updateIdentityPermissions(Identity.ORGANIZATION, oldOrg, newOrg, fromId,
                                                toId, true);
   }

   private void moveMembers(FSOrganization renamed, String fromId) {
      ReflectionTestUtils.invokeMethod(identityService, "updateOrganizationMembers",
         renamed, new ArrayList<IdentityModel>(), fromId, fileProvider, null);
   }

   private void assertOnlyOwnKey(String name, String orgId, String oldOrgId, Set<String> users,
                                 Set<String> groups, Set<String> roles)
   {
      String newKey = key(name, orgId);
      assertEquals(Set.of(newKey), ownKeys(orgId),
                   "the organization's own key must be moved to its new name and id only");
      assertEquals(Set.of(), ownKeys(oldOrgId),
                   "no organization key may be left under the old organization id");

      Permission permission = chain().getPermission(ResourceType.SECURITY_ORGANIZATION, newKey,
                                                    orgId);
      assertNotNull(permission);
      assertEquals(grantees(users, orgId), grants(permission, Identity.USER));
      assertEquals(grantees(groups, orgId), grants(permission, Identity.GROUP));
      assertEquals(grantees(roles, orgId), grants(permission, Identity.ROLE));
   }

   private Set<String> ownKeys(String orgId) {
      return chain().getPermissions().stream()
         .filter(entry -> entry.getFirst() == ResourceType.SECURITY_ORGANIZATION)
         .filter(entry -> Tool.equals(entry.getSecond(), orgId))
         .map(Tuple4::getThird)
         .collect(Collectors.toSet());
   }

   private static Set<String> grants(Permission permission, int type) {
      return permission.getGrants(ResourceAction.ADMIN, type, null).stream()
         .map(grant -> grant.getName() + "^" + grant.getOrganizationID())
         .collect(Collectors.toSet());
   }

   private static Set<String> grantees(Set<String> names, String orgId) {
      return names.stream().map(name -> name + "^" + orgId).collect(Collectors.toSet());
   }

   private static String key(String name, String orgId) {
      return new IdentityID(name, orgId).convertToKey();
   }

   private static AuthorizationChain chain() {
      return SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
   }
}
