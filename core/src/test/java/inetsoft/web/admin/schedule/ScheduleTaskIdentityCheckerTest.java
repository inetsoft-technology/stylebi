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
package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77281, the owner and execute-as identity a caller that isn't a site admin may store in a
 * schedule task. The SECURITY_USER ADMIN check is granted on every user name of the caller's
 * org here, including one that doesn't exist, like the real org admin check (Bug #66393).
 */
@Tag("core")
class ScheduleTaskIdentityCheckerTest {
   private static final String ORG_A = "orga";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private static final IdentityID CALLER = new IdentityID("oa", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_A);
   private static final IdentityID STAFF = new IdentityID("staff", ORG_A);

   private SecurityProvider provider;
   private SecurityEngine securityEngine;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgStatic;
   private Principal principal;
   private ScheduleTaskIdentityChecker checker;

   @BeforeEach
   void setUp() {
      provider = mock(SecurityProvider.class);
      Set<IdentityID> users = Set.of(CALLER, BOB, new IdentityID("admin", HOST_ORG));
      when(provider.getUser(any(IdentityID.class))).thenAnswer(
         inv -> users.contains(inv.<IdentityID>getArgument(0)) ?
            new User(inv.<IdentityID>getArgument(0)) : null);
      when(provider.getUsers()).thenReturn(users.toArray(new IdentityID[0]));
      when(provider.getGroups()).thenReturn(new IdentityID[] { STAFF });
      // ADMIN on any identity of the caller's org, whether it exists or not
      when(provider.checkPermission(any(), any(ResourceType.class), anyString(),
                                    eq(ResourceAction.ADMIN)))
         .thenAnswer(inv -> IdentityID.getIdentityIDFromKey(inv.<String>getArgument(2))
            .getOrgID().equals(ORG_A));
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);

      orgManager = mock(OrganizationManager.class);
      orgStatic = mockStatic(OrganizationManager.class);
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn(ORG_A);

      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(CALLER.convertToKey());
      checker = new ScheduleTaskIdentityChecker(securityEngine);
   }

   @AfterEach
   void tearDown() {
      orgStatic.close();
   }

   // ── run as the owner ─────────────────────────────────────────────────────

   private static final IdentityID MISSING = new IdentityID("admin", ORG_A);
   private static final IdentityID UA = new IdentityID("ua", ORG_A);

   @Test
   void runAsOwner_missingOwnerClearedOrSetToOwner_isRefused() {
      assertFalse(checker.isRunAsOwnerAllowed(MISSING, new User(UA), MISSING, null, principal));
      assertFalse(checker.isRunAsOwnerAllowed(MISSING, new User(UA), MISSING,
                                              new User(MISSING), principal));
   }

   @Test
   void runAsOwner_placeholderNamingMissingOwnerCleared_isRefused() {
      // an unresolved User(owner) placeholder can't run, no identity runs with the site admin's
      // roles, so the two are not the same
      assertFalse(checker.isRunAsOwnerAllowed(MISSING, new User(MISSING), MISSING, null,
                                              principal));
   }

   @Test
   void runAsOwner_placeholderUnchanged_isAllowed() {
      assertTrue(checker.isRunAsOwnerAllowed(MISSING, new User(MISSING), MISSING,
                                             new User(MISSING), principal));
   }

   @Test
   void runAsOwner_storedTaskAlreadyWithoutIdentity_isAllowed() {
      assertTrue(checker.isRunAsOwnerAllowed(MISSING, null, MISSING, null, principal));
      assertTrue(checker.isRunAsOwnerAllowed(MISSING, null, MISSING, new User(MISSING),
                                             principal));
   }

   @Test
   void runAsOwner_existingAdministeredOwnerOrCaller_isAllowed() {
      assertTrue(checker.isRunAsOwnerAllowed(BOB, new User(UA), BOB, null, principal));
      assertTrue(checker.isRunAsOwnerAllowed(BOB, new User(UA), BOB, new User(BOB), principal));
      assertTrue(checker.isRunAsOwnerAllowed(MISSING, new User(UA), CALLER, null, principal));
   }

   @Test
   void runAsOwner_otherIdentity_isNotCheckedHere() {
      assertTrue(checker.isRunAsOwnerAllowed(MISSING, new User(UA), MISSING, new User(CALLER),
                                             principal));
      assertTrue(checker.isRunAsOwnerAllowed(MISSING, new User(UA), MISSING, new Group(MISSING),
                                             principal));
   }

   @Test
   void runAsOwner_siteAdmin_isAllowed() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      assertTrue(checker.isRunAsOwnerAllowed(MISSING, new User(UA), MISSING, null, principal));
   }

   @Test
   void owner_missingUserNamedLikeSiteAdmin_isRefused() {
      assertFalse(checker.isOwnerAllowed(new IdentityID("admin", ORG_A), principal));
   }

   @Test
   void owner_callerOrExistingAdministeredUser_isAllowed() {
      assertTrue(checker.isOwnerAllowed(CALLER, principal));
      assertTrue(checker.isOwnerAllowed(BOB, principal));
   }

   @Test
   void owner_otherOrgSystemOrAnonymous_isRefused() {
      assertFalse(checker.isOwnerAllowed(new IdentityID("admin", HOST_ORG), principal));
      assertFalse(checker.isOwnerAllowed(new IdentityID("bob", "orgb"), principal));
      assertFalse(checker.isOwnerAllowed(new IdentityID(XPrincipal.SYSTEM, ORG_A), principal));
      assertFalse(checker.isOwnerAllowed(new IdentityID(XPrincipal.ANONYMOUS, ORG_A), principal));
      assertFalse(checker.isOwnerAllowed(null, principal));
   }

   @Test
   void owner_orgIdIsComparedExactly() {
      assertFalse(checker.isOwnerAllowed(new IdentityID("bob", "OrgA"), principal));
   }

   @Test
   void executeAs_callerOwnerExistingUserOrGroup_isAllowed() {
      assertTrue(checker.isExecuteAsAllowed(CALLER, Identity.USER, BOB, principal));
      assertTrue(checker.isExecuteAsAllowed(BOB, Identity.USER, BOB, principal));
      assertTrue(checker.isExecuteAsAllowed(BOB, Identity.USER, CALLER, principal));
      assertTrue(checker.isExecuteAsAllowed(STAFF, Identity.GROUP, CALLER, principal));
   }

   @Test
   void executeAs_missingUserOrGroup_isRefused() {
      assertFalse(checker.isExecuteAsAllowed(new IdentityID("admin", ORG_A), Identity.USER,
                                             CALLER, principal));
      assertFalse(checker.isExecuteAsAllowed(new IdentityID("nosuch", ORG_A), Identity.GROUP,
                                             CALLER, principal));
   }

   @Test
   void executeAs_roleGlobalOrOtherOrg_isRefused() {
      assertFalse(checker.isExecuteAsAllowed(new IdentityID("Administrator", null),
                                             Identity.ROLE, CALLER, principal));
      assertFalse(checker.isExecuteAsAllowed(new IdentityID("Everyone", ORG_A), Identity.ROLE,
                                             CALLER, principal));
      assertFalse(checker.isExecuteAsAllowed(new IdentityID("admin", HOST_ORG), Identity.USER,
                                             CALLER, principal));
      assertFalse(checker.isExecuteAsAllowed(null, Identity.USER, CALLER, principal));
   }

   @Test
   void executeAs_ownerNotAdministered_onlyTheCaller() {
      IdentityID foreignOwner = new IdentityID("carol", "orgb");

      assertFalse(checker.isExecuteAsAllowed(BOB, Identity.USER, foreignOwner, principal));
      assertTrue(checker.isExecuteAsAllowed(CALLER, Identity.USER, foreignOwner, principal));
   }

   @Test
   void isAllowed_checksOwnerAndExecuteAs() {
      ScheduleTask task = new ScheduleTask("t");
      task.setOwner(BOB);
      assertTrue(checker.isAllowed(task, "t", principal));

      task.setIdentity(new Role(new IdentityID("Administrator", null)));
      assertFalse(checker.isAllowed(task, "t", principal));

      task.setIdentity(new Group(STAFF));
      assertTrue(checker.isAllowed(task, "t", principal));

      task.setOwner(new IdentityID("admin", ORG_A));
      assertFalse(checker.isAllowed(task, "t", principal));
   }

   @Test
   void siteAdmin_isUnrestricted() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      assertTrue(checker.isUnrestricted(principal));
      assertTrue(checker.isOwnerAllowed(new IdentityID("admin", ORG_A), principal));
      assertTrue(checker.isExecuteAsAllowed(new IdentityID("Administrator", null),
                                            Identity.ROLE, CALLER, principal));
   }

   @Test
   void securityDisabled_isUnrestricted() {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      assertTrue(checker.isUnrestricted(principal));
      assertTrue(checker.isOwnerAllowed(new IdentityID("admin", ORG_A), principal));
   }

   @Test
   void providerConstructor_usesVirtualProviderAsSecurityDisabled() {
      ScheduleTaskIdentityChecker providerChecker = new ScheduleTaskIdentityChecker(provider);
      assertFalse(providerChecker.isOwnerAllowed(new IdentityID("admin", ORG_A), principal));

      when(provider.isVirtual()).thenReturn(true);
      assertTrue(providerChecker.isOwnerAllowed(new IdentityID("admin", ORG_A), principal));
   }
}
