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

import inetsoft.sree.internal.SUtil;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77353, a stored (e.g. planted before #77251) grant held by a non-site-admin never gives
 * permission over a user that grants system administrator, through the exact-key role grant
 * (cumulative merge) or a grant on a plain group of the target (group traversal), nor through
 * the direct user grant, whatever the shape of the caller principal. The same grants keep
 * working on an ordinary user.
 *
 * Guards the placement of the Bug #77347 {@code adminGrantingUser} return in
 * {@link DefaultCheckPermissionStrategy}, ahead of the direct grant, the cumulative merge and the
 * group traversal. Runs multi-tenant and single-tenant, for SSO, provider SRPrincipal and plain
 * XPrincipal callers.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class DefaultCheckPermissionStrategyPlantedGrantTest {
   static final String DELEGATE = "testUser";
   static final String DELEGATE_ROLE = "testRole";

   enum Mode {
      MULTI_TENANT("testOrg", true, new IdentityID("sysRole", "testOrg")),
      SINGLE_TENANT(Organization.getDefaultOrganizationID(), false,
                    new IdentityID("Administrator", null));

      Mode(String org, boolean multiTenant, IdentityID sysRole) {
         this.org = org;
         this.multiTenant = multiTenant;
         this.sysRole = sysRole;
      }

      final String org;
      final boolean multiTenant;
      final IdentityID sysRole;
   }

   enum Grant {
      NONE,              // p0 control
      DIRECT_USER,       // p1 USER delegate ADMIN on SECURITY_USER/<target>
      EXACT_KEY_ROLE,    // p2 ROLE delegateRole ADMIN on SECURITY_USER/<target>
      PLAIN_GROUP_USER,  // p3 USER delegate ADMIN on SECURITY_GROUP/grpA (target is a member)
      ORG_NODE,          // p4 USER delegate ADMIN on SECURITY_ORGANIZATION/(org, org)
      ORG_ADMIN_ROLE     // p5 delegate role is an org administrator role, no grant
   }

   /** Shapes of the caller principal, to cover every identity resolution branch. */
   enum Caller {
      SSO,              // SRPrincipal carrying roles, not in the provider (ssoIdentity path)
      SR_PROVIDER_USER, // SRPrincipal without roles/groups, user in the provider
      XPRINCIPAL        // plain XPrincipal, user in the provider
   }

   static Stream<Arguments> cases() {
      List<Arguments> list = new ArrayList<>();

      for(Mode m : Mode.values()) {
         for(Caller c : Caller.values()) {
            for(Grant g : Grant.values()) {
               list.add(Arguments.of(m, c, g));
            }
         }
      }

      return list.stream();
   }

   SecurityProvider mockProvider;
   DefaultCheckPermissionStrategy strategy;
   Mode mode;
   IdentityID siteAdmin;
   IdentityID plainUser;

   void init(Mode mode, Caller caller) {
      this.mode = mode;
      String org = mode.org;
      siteAdmin = new IdentityID("rootA", org);
      plainUser = new IdentityID("bobA", org);

      mockProvider = Mockito.mock(SecurityProvider.class);
      strategy = new DefaultCheckPermissionStrategy(mockProvider);

      lenient().when(mockProvider.getUser(any())).thenReturn(null);
      lenient().when(mockProvider.getGroup(any())).thenReturn(null);
      lenient().when(mockProvider.getRole(any())).thenReturn(null);
      lenient().when(mockProvider.getRoles(any())).thenReturn(new IdentityID[0]);
      lenient().when(mockProvider.getUserGroups(any())).thenReturn(new String[0]);
      lenient().when(mockProvider.getAllGroups(any(IdentityID[].class))).thenAnswer(inv -> inv.getArgument(0));
      lenient().when(mockProvider.isSystemAdministratorRole(any())).thenReturn(false);
      lenient().when(mockProvider.isOrgAdministratorRole(any())).thenReturn(false);
      lenient().when(mockProvider.getAllRoles(any(IdentityID[].class))).thenAnswer(inv -> inv.getArgument(0));
      lenient().when(mockProvider.getOrgNameFromID(anyString())).thenReturn(org);
      AuthenticationProvider mockAuthProvider = Mockito.mock(AuthenticationProvider.class);
      lenient().when(mockProvider.getAuthenticationProvider()).thenReturn(mockAuthProvider);
      Organization mockOrg = Mockito.mock(Organization.class);
      lenient().when(mockOrg.getOrganizationID()).thenReturn(org);
      lenient().when(mockOrg.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(mockProvider.getOrganization(anyString())).thenReturn(mockOrg);

      lenient().when(mockProvider.isSystemAdministratorRole(eq(mode.sysRole))).thenReturn(true);
      stubGroup("grpA");
      stubUser(siteAdmin, new IdentityID[]{ mode.sysRole }, "grpA");
      stubUser(plainUser, new IdentityID[0], "grpA");

      if(caller != Caller.SSO) {
         stubUser(new IdentityID(DELEGATE, org),
                  new IdentityID[]{ new IdentityID(DELEGATE_ROLE, org) }, null);
      }
   }

   @ParameterizedTest(name = "{0} {1} {2}")
   @MethodSource("cases")
   void storedGrantNeverReachesSiteAdminButStillReachesPlainUser(Mode mode, Caller caller,
                                                                 Grant grant)
   {
      init(mode, caller);
      stubGrant(grant);
      Principal p = caller(caller);

      boolean overSiteAdmin = check(p, siteAdmin);
      boolean overPlain = check(p, plainUser);

      assertFalse(overSiteAdmin, grant + " gave " + caller + " ADMIN over a site admin user");
      assertEquals(grant != Grant.NONE, overPlain,
                   grant + " for " + caller + " over a plain user");
   }

   private Principal caller(Caller caller) {
      IdentityID id = new IdentityID(DELEGATE, mode.org);

      switch(caller) {
      case SSO:
         return new SRPrincipal(id, new IdentityID[]{ new IdentityID(DELEGATE_ROLE, mode.org) },
                                new String[0], mode.org, Tool.getSecureRandom().nextLong());
      case SR_PROVIDER_USER:
         return new SRPrincipal(id, new IdentityID[0], new String[0], mode.org,
                                Tool.getSecureRandom().nextLong());
      default:
         return new XPrincipal(id, new IdentityID[0], new String[0], mode.org);
      }
   }

   private boolean check(Principal caller, IdentityID target) {
      try(MockedStatic<SUtil> sutilMock = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<OrganizationManager> omMock =
             Mockito.mockStatic(OrganizationManager.class, Mockito.CALLS_REAL_METHODS))
      {
         sutilMock.when(SUtil::isMultiTenant).thenReturn(mode.multiTenant);
         sutilMock.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         OrganizationManager mockOM = mock(OrganizationManager.class);
         omMock.when(OrganizationManager::getInstance).thenReturn(mockOM);
         omMock.when(OrganizationManager::getCurrentOrgName).thenReturn(mode.org);
         when(mockOM.getCurrentOrgID()).thenReturn(mode.org);
         when(mockOM.getCurrentOrgID(any())).thenReturn(mode.org);
         when(mockOM.isSiteAdmin(any(Principal.class))).thenReturn(false);

         return strategy.checkPermission(caller, ResourceType.SECURITY_USER,
                                         target.convertToKey(), ResourceAction.ADMIN);
      }
   }

   private void stubUser(IdentityID id, IdentityID[] roles, String group) {
      String[] groups = group == null ? new String[0] : new String[]{ group };
      User user = mock(User.class);
      lenient().when(user.getIdentityID()).thenReturn(id);
      lenient().when(user.getName()).thenReturn(id.getName());
      lenient().when(user.getOrganizationID()).thenReturn(id.getOrgID());
      lenient().when(user.getGroups()).thenReturn(groups);
      lenient().when(user.getRoles()).thenReturn(roles);
      lenient().when(mockProvider.getUser(eq(id))).thenReturn(user);
      lenient().when(mockProvider.getUserGroups(eq(id))).thenReturn(groups);
      lenient().when(mockProvider.getRoles(eq(id))).thenReturn(roles);
   }

   private void stubGroup(String name) {
      IdentityID id = new IdentityID(name, mode.org);
      Group group = mock(Group.class);
      lenient().when(group.getIdentityID()).thenReturn(id);
      lenient().when(group.getName()).thenReturn(name);
      lenient().when(group.getOrganizationID()).thenReturn(mode.org);
      lenient().when(group.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(group.getGroups()).thenReturn(new String[0]);
      lenient().when(mockProvider.getGroup(eq(id))).thenReturn(group);
      lenient().when(mockProvider.getGroupParentGroups(eq(id))).thenReturn(new String[0]);
   }

   private void stubGrant(Grant grant) {
      String org = mode.org;
      Permission userAdmin = new Permission();
      userAdmin.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of(DELEGATE), org);
      Permission roleAdmin = new Permission();
      roleAdmin.setRoleGrantsForOrg(ResourceAction.ADMIN, Set.of(DELEGATE_ROLE), org);

      switch(grant) {
      case DIRECT_USER:
         stubUserKeys(userAdmin);
         break;
      case EXACT_KEY_ROLE:
         stubUserKeys(roleAdmin);
         break;
      case PLAIN_GROUP_USER:
         String grpKey = new IdentityID("grpA", org).convertToKey();
         lenient().when(mockProvider.getPermission(eq(ResourceType.SECURITY_GROUP), eq(grpKey)))
            .thenReturn(userAdmin);
         lenient().when(mockProvider.getPermission(eq(ResourceType.SECURITY_GROUP), eq(grpKey), anyString()))
            .thenReturn(userAdmin);
         break;
      case ORG_NODE:
         lenient().when(mockProvider.getPermission(eq(ResourceType.SECURITY_ORGANIZATION),
                                                   eq(new IdentityID(org, org))))
            .thenReturn(userAdmin);
         break;
      case ORG_ADMIN_ROLE:
         lenient().when(mockProvider.isOrgAdministratorRole(eq(new IdentityID(DELEGATE_ROLE, org))))
            .thenReturn(true);
         break;
      default:
         break;
      }
   }

   // the direct early return reads the 3-arg overload, the cumulative merge the 2-arg one
   private void stubUserKeys(Permission perm) {
      for(IdentityID target : new IdentityID[]{ siteAdmin, plainUser }) {
         lenient().when(mockProvider.getPermission(eq(ResourceType.SECURITY_USER), eq(target.convertToKey())))
            .thenReturn(perm);
         lenient().when(mockProvider.getPermission(eq(ResourceType.SECURITY_USER), eq(target.convertToKey()),
                                                   anyString()))
            .thenReturn(perm);
      }
   }
}
