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
package inetsoft.web.admin.security;

import inetsoft.sree.security.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #76487 / Bug #76486: the default roles seeded onto a new user are not an explicit
 * assignment, so they were never checked, and a role flagged both Default and system or
 * organization administrator reached the new user whoever created it. The helper drops the
 * administrator defaults the caller could never assign, keeps the rest, and never throws.
 * <p>
 * Roles are resolved through the chain provider the service holds (the editable provider that
 * supplies the defaults is a different object in production), as the last test pins.
 */
@Tag("core")
class IdentityServiceDefaultRolesTest {
   private static final String ORG = "orga";
   private static final IdentityID PLAIN = new IdentityID("Viewer", ORG);
   private static final IdentityID SYS = new IdentityID("SysDef", null);
   private static final IdentityID GLOBAL_OA = new IdentityID("Organization Administrator", null);
   private static final IdentityID ORG_BOSS = new IdentityID("OrgBoss", ORG);
   private static final IdentityID SUB_SYS = new IdentityID("SubSys", ORG);   // inherits SysDef
   private static final IdentityID SUB_OA = new IdentityID("SubBoss", ORG);    // inherits OrgBoss
   private static final IdentityID SPOOF = new IdentityID("SysDef", ORG);      // not in the provider
   private static final IdentityID UNKNOWN = new IdentityID("Ghost", ORG);     // not in the provider

   private final Map<IdentityID, FSRole> roles = new HashMap<>();
   private final Principal principal = mock(Principal.class);
   private AuthenticationProvider provider;
   private SecurityEngine securityEngine;
   private IdentityService service;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach
   void setUp() {
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      add(new FSRole(PLAIN));
      FSRole sys = new FSRole(SYS);
      sys.setSysAdmin(true);
      add(sys);
      FSRole oa = new FSRole(GLOBAL_OA);
      oa.setOrgAdmin(true);
      add(oa);
      FSRole boss = new FSRole(ORG_BOSS);
      boss.setOrgAdmin(true);
      add(boss);
      FSRole subSys = new FSRole(SUB_SYS);
      subSys.setRoles(new IdentityID[] { SYS });
      add(subSys);
      FSRole subOa = new FSRole(SUB_OA);
      subOa.setRoles(new IdentityID[] { ORG_BOSS });
      add(subOa);

      provider = mock(AuthenticationProvider.class);
      when(provider.getRole(any())).thenAnswer(inv -> roles.get(inv.<IdentityID>getArgument(0)));
      when(provider.isSystemAdministratorRole(any())).thenAnswer(inv -> {
         FSRole role = roles.get(inv.<IdentityID>getArgument(0));
         return role != null && role.isSysAdmin();
      });
      when(provider.isOrgAdministratorRole(any())).thenAnswer(inv -> {
         FSRole role = roles.get(inv.<IdentityID>getArgument(0));
         return role != null && role.isOrgAdmin();
      });
      doCallRealMethod().when(provider).getAllRoles(any());
      doCallRealMethod().when(provider).getAllGroups(any());

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthenticationProvider()).thenReturn(provider);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      // a mock skips the field initializer
      ReflectionTestUtils.setField(service, "LOG", org.slf4j.LoggerFactory.getLogger(IdentityService.class));

      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      caller(false, false);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   private void add(FSRole role) {
      roles.put(role.getIdentityID(), role);
   }

   private void caller(boolean siteAdmin, boolean orgAdmin) {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(siteAdmin);
      when(orgManager.isOrgAdmin(any(Principal.class))).thenReturn(orgAdmin);
   }

   private List<IdentityID> filter(Principal who, IdentityID... defaults) {
      return service.filterAssignableDefaultRoles(Arrays.asList(defaults), who);
   }

   private static final IdentityID[] ALL = { PLAIN, SYS, GLOBAL_OA, ORG_BOSS, SUB_SYS, SUB_OA };

   @Test
   void delegate_keepsOnlyOrdinaryDefaults() {
      assertEquals(List.of(PLAIN), filter(principal, ALL));
   }

   @Test
   void delegate_losesInheritedAndSpoofedAdminDefaults() {
      assertEquals(List.of(PLAIN), filter(principal, SUB_SYS, SUB_OA, SPOOF, PLAIN));
   }

   @Test
   void orgAdmin_keepsOrgAdminDefaultsButLosesSystemAdminOnes() {
      caller(false, true);

      assertEquals(List.of(PLAIN, GLOBAL_OA, ORG_BOSS, SUB_OA), filter(principal, ALL));
   }

   @Test
   void siteAdmin_keepsEveryDefault() {
      caller(true, false);

      assertEquals(List.of(ALL), filter(principal, ALL));
   }

   @Test
   void securityDisabled_keepsEveryDefault() {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      assertEquals(List.of(ALL), filter(principal, ALL));
   }

   @Test
   void nullPrincipal_isUnprivileged() {
      assertEquals(List.of(PLAIN), filter(null, ALL));
   }

   @Test
   void unresolvableRole_isKeptAsNotAnAdminRole() {
      assertEquals(List.of(UNKNOWN, PLAIN), filter(principal, UNKNOWN, PLAIN));
   }

   @Test
   void nullAndEmptyInput_giveAnEmptyList() {
      assertEquals(List.of(), service.filterAssignableDefaultRoles(null, principal));
      assertEquals(List.of(), service.filterAssignableDefaultRoles(List.of(), principal));
      assertEquals(List.of(PLAIN), filter(principal, null, PLAIN));
   }

   @Test
   void providerFailure_neverThrows() {
      when(provider.getAllRoles(any())).thenThrow(new IllegalStateException("provider down"));

      List<IdentityID> result = assertDoesNotThrow(() -> filter(principal, PLAIN, SYS));

      // fail closed: no role could be assessed, so none is given, and creation goes on
      assertEquals(List.of(), result);
   }

   @Test
   void callerLookupFailure_neverThrowsAndGivesNoRoles() {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenThrow(new IllegalStateException("x"));

      assertEquals(List.of(), assertDoesNotThrow(() -> filter(principal, PLAIN, SYS)));
   }

   /**
    * The defaults come from the editable provider but the roles are judged by the chain provider
    * the service holds: a role the chain does not know (getRole is null) is not an admin role.
    */
   @Test
   void roleUnknownToTheChainProvider_isKeptAsNotAnAdminRole() {
      roles.remove(SYS);

      assertEquals(List.of(SYS, PLAIN), filter(principal, SYS, PLAIN));
   }
}
