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
 * Scope: concrete methods whose logic lives solely in this abstract class.
 * Tier: [unit] — pure JUnit + Mockito, no static factory mocking.
 * Static-dependency methods (copyThemes, copyScopedProperties, clearScopedProperties,
 * copyDataSpace, copyFileSystemFileAndBlockSystemFile) are in the companion
 * AbstractEditableAuthenticationProviderStaticDepTest.
 *
 * No-op stub methods (addUser, removeUser, setRole, changePassword, etc.) are tested
 * in the concrete subclass that overrides them — see FileAuthenticationProviderTest.
 *
 * Cases deferred to subclass tests — do NOT add here:
 *
 * [FileAuthenticationProvider] addUser/setUser/removeUser, addGroup/setGroup/removeGroup,
 *                              addRole/setRole/removeRole, addOrganization/setOrganization/removeOrganization,
 *                              changePassword, authenticate
 *                              → covered by FileAuthenticationProviderTest
 *
 * Cases deferred — require integration context or companion file:
 *
 * [AbstractEditableAuthenticationProviderStaticDepTest]
 *                              clearScopedProperties, copyScopedProperties,
 *                              copyThemes, copyDataSpace, copyFileSystemFileAndBlockSystemFile
 *                              → require Mockito.mockStatic(DataSpace/SreeEnv/CustomThemesManager)
 *                              → see AbstractEditableAuthenticationProviderStaticDepTest
 * [Integration] copyOrganization full flow (copyOrganizationInternal)
 *                              → requires @SreeHome + DataSpace + PortalThemesManager + ScheduleManager
 *                              → NOT yet covered; needs integration test
 */

/*
 * Intent vs implementation suspects
 *
 * Issue #74695
 * [Suspect 1] copyPermittedIDs(null, fromOrgId, newOrgId)
 *             intent: return a filtered/updated list
 *             actual: for-each on null list → NullPointerException
 *
 * [Suspect 2] copyPermittedIDs — Identity.ORGANIZATION (type == 4) not handled by switch
 *             intent: copy all permitted identity types including ORGANIZATION
 *             actual: switch covers only USER/GROUP/ROLE — ORGANIZATION entries silently dropped
 *
 * [Suspect 3] updatePermittedIdentities — unrecognized type (e.g. type == 99)
 *             intent: throw or skip on unrecognized type
 *             actual: falls through to default → silently treated as SECURITY_USER
 *
 * [Suspect 4] fireAuthenticationChanged — throwing listener aborts remaining dispatch
 *             intent: all registered listeners should be notified
 *             actual: no try/catch in dispatch loop; a RuntimeException from one listener
 *             prevents subsequent listeners from being called
 *
 * [Suspect 5] copyUserToOrganization(fromUser=null) — placeholder user stored with old orgID
 *             intent: add a placeholder user in the new org when the source user is missing
 *             actual: addUser(new FSUser(memberID)) where memberID.orgID == fromOrgID (not newOrgID)
 *             — AbstractEditableAuthenticationProvider:637
 *             Fix: new FSUser(new IdentityID(memberID.name, orgID))
 */

/*
 * fireAuthenticationChanged decision tree
 *  ├─ [A] no listeners registered        → no event created, no call
 *  ├─ [B] one listener                   → event created once, listener called with correct fields
 *  ├─ [C] multiple listeners             → single event instance shared across all (lazy creation)
 *  └─ [D] listener throws               → subsequent listeners NOT notified [Suspect 4]
 *
 * copyPermittedIDs decision tree (per entry)
 *  ├─ [E] fromIDs is null               → NullPointerException  [Suspect 1]
 *  ├─ [F] fromIDs is empty              → returns empty list
 *  ├─ [G] type == USER, getUser != null, orgID matches  → included with new orgID
 *  ├─ [H] type == USER, getUser != null, orgID mismatch → excluded
 *  ├─ [I] type == USER, getUser == null                 → excluded
 *  ├─ [J] type == GROUP (same logic as G/H/I)
 *  ├─ [K] type == ROLE  (same logic as G/H/I)
 *  └─ [L] type == ORGANIZATION (4)      → silently dropped  [Suspect 2]
 *
 * updatePermittedIdentities decision tree
 *  ├─ [A] type == USER         → ResourceType.SECURITY_USER
 *  ├─ [B] type == GROUP        → ResourceType.SECURITY_GROUP
 *  ├─ [C] type == ROLE         → ResourceType.SECURITY_ROLE
 *  ├─ [D] type == ORGANIZATION → ResourceType.SECURITY_ORGANIZATION
 *  ├─ [E] unknown type         → ResourceType.SECURITY_USER (default)  [Suspect 3]
 *  ├─ [F] copyPermittedIDs result is empty     → setIdentityPermissions NOT called
 *  └─ [G] copyPermittedIDs result is non-empty → setIdentityPermissions called
 *
 * copyIdentityRoles decision tree (per role in fromID.getRoles())
 *  ├─ [A] roles array empty             → []
 *  ├─ [B] role not found (getRole null) → excluded
 *  ├─ [C] role found, local (orgID non-null) → IdentityID(name, newOrg)
 *  ├─ [D] role found, global (orgID null), name != "Administrator" → kept as-is (global)
 *  └─ [E] role found, global, name == "Administrator" (sysAdmin default) → stripped
 *
 * copyRoleToOrganization decision tree
 *  ├─ [A] getRole returns null          → returns null
 *  └─ [B] role found                   → FSRole built, addRole called, updatePermittedIdentities called,
 *                                          returns IdentityID(name, newOrgID)
 *
 * copyUserToOrganization decision tree
 *  ├─ [A] fromUser found, defaultPassword null   → password hash copied from old user
 *  ├─ [B] fromUser found, defaultPassword given  → password hashed from defaultPassword
 *  └─ [C] fromUser null                          → addUser(FSUser(memberID)) with old orgID  [Suspect 5]
 *
 * copyGroupToOrganization decision tree
 *  ├─ [A] fromGroup null  → returns null, addGroup NOT called
 *  └─ [B] fromGroup found → addGroup called, returns IdentityID(name, newOrgID)
 *
 * copyRootPermittedIdentities decision tree
 *  ├─ [A] replace=false → 4 × getPermission + 4 × setIdentityPermissions (new org only)
 *  └─ [B] replace=true  → 4 × getPermission + 4 × setIdentityPermissions (new org)
 *                          + 4 × setIdentityPermissions(null, null) (clear old org)
 */

import inetsoft.uql.util.AbstractIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AbstractEditableAuthenticationProviderTest {

   private StubProvider provider;

   @BeforeEach
   void setUp() {
      provider = new StubProvider();
   }

   // -------------------------------------------------------------------------
   // fireAuthenticationChanged
   // -------------------------------------------------------------------------

   // [Path A] no listeners registered → no event created, no exception
   @Test
   void fireAuthenticationChanged_noListeners_noException() {
      assertDoesNotThrow(() ->
         provider.fireAuthenticationChanged(
            new IdentityID("alice", "orgA"), new IdentityID("alice2", "orgA"),
            "orgA", "orgA", Identity.USER, false));
   }

   // [Path B] one listener → receives event with all fields correctly populated
   @Test
   void fireAuthenticationChanged_oneListener_eventDeliveredWithCorrectProperties() {
      List<AuthenticationChangeEvent> received = new ArrayList<>();
      provider.addAuthenticationChangeListener(received::add);

      IdentityID oldID = new IdentityID("alice", "orgA");
      IdentityID newID = new IdentityID("alice2", "orgA");
      provider.fireAuthenticationChanged(oldID, newID, "orgA", "orgB", Identity.USER, false);

      assertEquals(1, received.size());
      AuthenticationChangeEvent evt = received.get(0);
      assertEquals(oldID, evt.getOldID());
      assertEquals(newID, evt.getNewID());
      assertEquals("orgA", evt.getOldOrgID());
      assertEquals("orgB", evt.getNewOrgID());
      assertEquals(Identity.USER, evt.getType());
      assertFalse(evt.isRemoved());
   }

   // [Path B] removed=true is propagated correctly
   @Test
   void fireAuthenticationChanged_removedTrue_flagPropagatedInEvent() {
      List<AuthenticationChangeEvent> received = new ArrayList<>();
      provider.addAuthenticationChangeListener(received::add);

      provider.fireAuthenticationChanged(
         new IdentityID("bob", "orgA"), null, "orgA", null, Identity.GROUP, true);

      assertTrue(received.get(0).isRemoved());
   }

   // [Path C] multiple listeners → all receive the same event instance (lazy creation)
   @Test
   void fireAuthenticationChanged_multipleListeners_allReceiveSameEventInstance() {
      List<AuthenticationChangeEvent> first = new ArrayList<>();
      List<AuthenticationChangeEvent> second = new ArrayList<>();

      provider.addAuthenticationChangeListener(first::add);
      provider.addAuthenticationChangeListener(second::add);

      provider.fireAuthenticationChanged(
         new IdentityID("alice", "orgA"), new IdentityID("alice2", "orgA"),
         "orgA", "orgA", Identity.USER, false);

      assertEquals(1, first.size());
      assertEquals(1, second.size());
      assertSame(first.get(0), second.get(0), "All listeners must share the same event instance");
   }

   // Issue #74695
   // [Path D] throwing listener aborts dispatch — subsequent listeners not notified  [Suspect 4]
   @Disabled("Suspect 4: throwing listener aborts dispatch — AbstractEditableAuthenticationProvider:834; Fix: wrap listener.authenticationChanged(evt) in try/catch(Exception)")
   @Test
   void fireAuthenticationChanged_throwingListener_subsequentListenersStillNotified() {
      List<AuthenticationChangeEvent> received = new ArrayList<>();

      provider.addAuthenticationChangeListener(evt -> { throw new RuntimeException("boom"); });
      provider.addAuthenticationChangeListener(received::add);

      assertDoesNotThrow(() ->
         provider.fireAuthenticationChanged(
            new IdentityID("x", "orgA"), new IdentityID("y", "orgA"),
            "orgA", "orgA", Identity.USER, false));
      assertEquals(1, received.size(), "Second listener must be notified despite first listener throwing");
   }

   // -------------------------------------------------------------------------
   // addAuthenticationChangeListener / removeAuthenticationChangeListener
   // -------------------------------------------------------------------------

   // [Scenario: dedup] HashSet deduplicates identical listener references
   @Test
   void addListener_sameInstanceTwice_listenerCalledOnlyOnce() {
      List<AuthenticationChangeEvent> received = new ArrayList<>();
      AuthenticationChangeListener listener = received::add;

      provider.addAuthenticationChangeListener(listener);
      provider.addAuthenticationChangeListener(listener);

      provider.fireAuthenticationChanged(
         new IdentityID("alice", "orgA"), new IdentityID("alice", "orgA"),
         "orgA", "orgA", Identity.USER, false);

      assertEquals(1, received.size(), "Duplicate listener registration must be deduplicated by HashSet");
   }

   // [Scenario: safe remove] removing a never-registered listener does not throw
   @Test
   void removeListener_notRegistered_noException() {
      assertDoesNotThrow(() -> provider.removeAuthenticationChangeListener(evt -> {}));
   }

   // [Scenario: remove then fire] removed listener no longer receives events
   @Test
   void removeListener_registered_nolongerReceivesEvents() {
      List<AuthenticationChangeEvent> received = new ArrayList<>();
      AuthenticationChangeListener listener = received::add;

      provider.addAuthenticationChangeListener(listener);
      provider.removeAuthenticationChangeListener(listener);

      provider.fireAuthenticationChanged(
         new IdentityID("alice", "orgA"), new IdentityID("alice2", "orgA"),
         "orgA", "orgA", Identity.USER, false);

      assertTrue(received.isEmpty());
   }

   // -------------------------------------------------------------------------
   // copyPermittedIDs
   // -------------------------------------------------------------------------

   // [Path F] empty input list → empty output
   @Test
   void copyPermittedIDs_emptyList_returnsEmptyList() {
      assertTrue(provider.copyPermittedIDs(List.of(), "orgA", "orgB").isEmpty());
   }

   // Issue #74695
   // [Path E] null input → NullPointerException  [Suspect 1]
   @Disabled("Suspect 1: for-each on null list throws NPE — AbstractEditableAuthenticationProvider:551; Fix: add null-guard before for-each, e.g. if (fromIDs == null) return List.of()")
   @Test
   void copyPermittedIDs_nullList_throwsNullPointerException() {
      assertThrows(NullPointerException.class,
         () -> provider.copyPermittedIDs(null, "orgA", "orgB"),
         "Suspect 1: for-each on null list throws NPE — AbstractEditableAuthenticationProvider:551");
   }

   // [Path G] USER in fromOrg → included with newOrgId
   @Test
   void copyPermittedIDs_userBelongsToFromOrg_includedWithNewOrgId() {
      IdentityID uid = new IdentityID("alice", "orgA");
      FSUser user = new FSUser(uid);
      user.setOrganization("orgA");
      provider.stubUser(uid, user);

      List<IdentityModel> result = provider.copyPermittedIDs(
         List.of(IdentityModel.builder().identityID(uid).type(Identity.USER).build()),
         "orgA", "orgB");

      assertEquals(1, result.size());
      assertEquals("orgB", result.get(0).identityID().orgID);
      assertEquals("alice", result.get(0).identityID().name);
      assertEquals(Identity.USER, result.get(0).type());
   }

   // [Path H] USER exists but belongs to a different org → excluded
   @Test
   void copyPermittedIDs_userBelongsToDifferentOrg_excluded() {
      IdentityID uid = new IdentityID("alice", "orgC");
      FSUser user = new FSUser(uid);
      user.setOrganization("orgC");
      provider.stubUser(uid, user);

      List<IdentityModel> result = provider.copyPermittedIDs(
         List.of(IdentityModel.builder().identityID(uid).type(Identity.USER).build()),
         "orgA", "orgB");

      assertTrue(result.isEmpty());
   }

   // [Path I] USER not found (getUser returns null) → excluded
   @Test
   void copyPermittedIDs_userNotFound_excluded() {
      IdentityID uid = new IdentityID("ghost", "orgA");

      List<IdentityModel> result = provider.copyPermittedIDs(
         List.of(IdentityModel.builder().identityID(uid).type(Identity.USER).build()),
         "orgA", "orgB");

      assertTrue(result.isEmpty());
   }

   // [Path J/K] GROUP and ROLE in fromOrg → both included with newOrgId (same switch logic as G)
   private static Stream<Arguments> memberTypeCases() {
      return Stream.of(
         // ✓ GROUP in fromOrg → included
         Arguments.of(Identity.GROUP),
         // ✓ ROLE in fromOrg → included
         Arguments.of(Identity.ROLE)
      );
   }

   @ParameterizedTest(name = "type={0} → included with newOrgId")
   @MethodSource("memberTypeCases")
   void copyPermittedIDs_groupOrRoleInFromOrg_includedWithNewOrgId(int type) {
      IdentityID id = new IdentityID("member", "orgA");

      if(type == Identity.GROUP) {
         FSGroup group = new FSGroup(id);
         group.setOrganization("orgA");
         provider.stubGroup(id, group);
      }
      else {
         FSRole role = new FSRole(id);
         role.setOrganization("orgA");
         provider.stubRole(id, role);
      }

      List<IdentityModel> result = provider.copyPermittedIDs(
         List.of(IdentityModel.builder().identityID(id).type(type).build()),
         "orgA", "orgB");

      assertEquals(1, result.size());
      assertEquals("orgB", result.get(0).identityID().orgID);
      assertEquals(type, result.get(0).type());
   }

   // Issue #74695
   // [Path L] ORGANIZATION type (Identity.ORGANIZATION == 4) → silently dropped  [Suspect 2]
   @Disabled("Suspect 2: ORGANIZATION type (4) has no switch case in copyPermittedIDs — AbstractEditableAuthenticationProvider:552; Fix: add case Identity.ORGANIZATION analogous to USER/GROUP/ROLE")
   @Test
   void copyPermittedIDs_organizationTypeEntry_silentlyDropped() {
      IdentityID oid = new IdentityID("acme", "orgA");

      List<IdentityModel> result = provider.copyPermittedIDs(
         List.of(IdentityModel.builder().identityID(oid).type(Identity.ORGANIZATION).build()),
         "orgA", "orgB");

      assertTrue(result.isEmpty(),
         "Suspect 2: ORGANIZATION type (4) has no switch case — AbstractEditableAuthenticationProvider:552");
   }

   // [Path G+H] mixed entries — only fromOrg entries survive
   @Test
   void copyPermittedIDs_mixedEntries_onlyFromOrgEntriesIncluded() {
      IdentityID uid = new IdentityID("alice", "orgA");
      FSUser user = new FSUser(uid);
      user.setOrganization("orgA");
      provider.stubUser(uid, user);

      IdentityID gid = new IdentityID("devs", "orgC");
      FSGroup group = new FSGroup(gid);
      group.setOrganization("orgC");
      provider.stubGroup(gid, group);

      List<IdentityModel> result = provider.copyPermittedIDs(
         List.of(
            IdentityModel.builder().identityID(uid).type(Identity.USER).build(),
            IdentityModel.builder().identityID(gid).type(Identity.GROUP).build()),
         "orgA", "orgB");

      assertEquals(1, result.size());
      assertEquals("alice", result.get(0).identityID().name);
   }

   // -------------------------------------------------------------------------
   // updatePermittedIdentities — ResourceType mapping
   // -------------------------------------------------------------------------

   private static Stream<Arguments> typeToResourceType() {
      return Stream.of(
         // ✓ [Path A] USER → SECURITY_USER
         Arguments.of(Identity.USER,         ResourceType.SECURITY_USER),
         // ✓ [Path B] GROUP → SECURITY_GROUP
         Arguments.of(Identity.GROUP,        ResourceType.SECURITY_GROUP),
         // ✓ [Path C] ROLE → SECURITY_ROLE
         Arguments.of(Identity.ROLE,         ResourceType.SECURITY_ROLE),
         // ✓ [Path D] ORGANIZATION → SECURITY_ORGANIZATION
         Arguments.of(Identity.ORGANIZATION, ResourceType.SECURITY_ORGANIZATION)
      );
   }

   // [Path A-D] each recognized type maps to its correct ResourceType
   @ParameterizedTest(name = "type={0} → {1}")
   @MethodSource("typeToResourceType")
   void updatePermittedIdentities_recognizedType_correctResourceTypeUsed(int type, ResourceType expectedResourceType) {
      IdentityService identityService = mock(IdentityService.class);
      when(identityService.getPermission(any(), any(), any(), any())).thenReturn(List.of());

      IdentityID from = new IdentityID("alice", "orgA");
      IdentityID to   = new IdentityID("alice", "orgB");
      Principal principal = mock(Principal.class);

      provider.updatePermittedIdentities(type, identityService, principal, from, to, "orgB", "orgA");

      ArgumentCaptor<ResourceType> captor = ArgumentCaptor.forClass(ResourceType.class);
      verify(identityService).getPermission(eq(from), captor.capture(), eq("orgA"), eq(principal));
      assertEquals(expectedResourceType, captor.getValue());
   }

   // Issue #74695
   // [Path E] unknown type → silently falls back to SECURITY_USER  [Suspect 3]
   @Disabled("Suspect 3: unrecognized type still silently defaults to SECURITY_USER via default: branch — AbstractEditableAuthenticationProvider:678; Fix: throw IllegalArgumentException for unrecognized types")
   @Test
   void updatePermittedIdentities_unknownType_silentlyFallsBackToSecurityUser() {
      IdentityService identityService = mock(IdentityService.class);
      when(identityService.getPermission(any(), any(), any(), any())).thenReturn(List.of());

      provider.updatePermittedIdentities(99, identityService, mock(Principal.class),
         new IdentityID("alice", "orgA"), new IdentityID("alice", "orgB"), "orgB", "orgA");

      ArgumentCaptor<ResourceType> captor = ArgumentCaptor.forClass(ResourceType.class);
      verify(identityService).getPermission(any(), captor.capture(), any(), any());
      assertEquals(ResourceType.SECURITY_USER, captor.getValue(),
         "Suspect 3: unrecognized type defaults to SECURITY_USER — AbstractEditableAuthenticationProvider:679");
   }

   // [Path F] empty result from copyPermittedIDs → setIdentityPermissions NOT called
   @Test
   void updatePermittedIdentities_noMatchingPermIds_doesNotCallSetIdentityPermissions() {
      IdentityService identityService = mock(IdentityService.class);
      IdentityID unknown = new IdentityID("ghost", "orgA");
      when(identityService.getPermission(any(), any(), any(), any()))
         .thenReturn(List.of(IdentityModel.builder().identityID(unknown).type(Identity.USER).build()));

      provider.updatePermittedIdentities(Identity.USER, identityService, mock(Principal.class),
         new IdentityID("alice", "orgA"), new IdentityID("alice", "orgB"), "orgB", "orgA");

      verify(identityService, never()).setIdentityPermissions(any(), any(), any(), any(), any(), any());
   }

   // [Path G] non-empty result → setIdentityPermissions IS called with correct arguments
   @Test
   void updatePermittedIdentities_matchingPermIds_callsSetIdentityPermissions() {
      IdentityService identityService = mock(IdentityService.class);

      IdentityID uid = new IdentityID("alice", "orgA");
      FSUser user = new FSUser(uid);
      user.setOrganization("orgA");
      provider.stubUser(uid, user);

      when(identityService.getPermission(any(), any(), any(), any()))
         .thenReturn(List.of(IdentityModel.builder().identityID(uid).type(Identity.USER).build()));

      IdentityID from = new IdentityID("alice", "orgA");
      IdentityID to   = new IdentityID("alice", "orgB");
      Principal principal = mock(Principal.class);

      provider.updatePermittedIdentities(Identity.USER, identityService, principal, from, to, "orgB", "orgA");

      verify(identityService).setIdentityPermissions(
         eq(to), eq(to), eq(ResourceType.SECURITY_USER), eq(principal), any(), eq("orgB"));
   }

   // -------------------------------------------------------------------------
   // copyIdentityRoles — via: copyRoleToOrganization/copyUserToOrganization/copyGroupToOrganization
   // -------------------------------------------------------------------------

   // [Path A] empty roles array → returns empty array
   @Test
   void copyIdentityRoles_emptyRoles_returnsEmptyArray() {
      FSRole source = new FSRole(new IdentityID("source", "fromOrg"));
      source.setRoles(new IdentityID[0]);

      IdentityID[] result = provider.callCopyIdentityRoles(source, "newOrg");

      assertEquals(0, result.length);
   }

   // [Path B] role not found in provider (getRole null) → excluded
   @Test
   void copyIdentityRoles_roleNotFound_excluded() {
      FSRole source = new FSRole(new IdentityID("source", "fromOrg"));
      source.setRoles(new IdentityID[]{new IdentityID("missing", "fromOrg")});
      // no stub → getRole("missing") returns null

      IdentityID[] result = provider.callCopyIdentityRoles(source, "newOrg");

      assertEquals(0, result.length);
   }

   // [Path C] local role (orgID non-null) found → IdentityID(name, newOrg)
   @Test
   void copyIdentityRoles_localRoleFound_renamedToNewOrg() {
      IdentityID localRoleID = new IdentityID("editor", "fromOrg");
      FSRole localRole = new FSRole(localRoleID);
      provider.stubRole(localRoleID, localRole);

      FSRole source = new FSRole(new IdentityID("source", "fromOrg"));
      source.setRoles(new IdentityID[]{localRoleID});

      IdentityID[] result = provider.callCopyIdentityRoles(source, "newOrg");

      assertEquals(1, result.length);
      assertEquals("editor", result[0].name);
      assertEquals("newOrg", result[0].orgID);
   }

   // [Path D] global non-sysAdmin role (orgID null, name != "Administrator") → kept as-is
   @Test
   void copyIdentityRoles_globalNonAdminRole_preservedAsGlobal() {
      IdentityID globalRoleID = new IdentityID("GlobalViewer", null);
      FSRole globalRole = new FSRole(globalRoleID);
      provider.stubRole(globalRoleID, globalRole);

      FSRole source = new FSRole(new IdentityID("source", "fromOrg"));
      source.setRoles(new IdentityID[]{globalRoleID});

      IdentityID[] result = provider.callCopyIdentityRoles(source, "newOrg");

      assertEquals(1, result.length);
      assertEquals("GlobalViewer", result[0].name);
      assertNull(result[0].orgID, "Global role must keep null orgID");
   }

   // [Path E] global sysAdmin role (name == "Administrator" by default impl) → stripped
   @Test
   void copyIdentityRoles_globalAdministratorRole_stripped() {
      IdentityID adminRoleID = new IdentityID("Administrator", null);
      FSRole adminRole = new FSRole(adminRoleID);
      provider.stubRole(adminRoleID, adminRole);

      FSRole source = new FSRole(new IdentityID("source", "fromOrg"));
      source.setRoles(new IdentityID[]{adminRoleID});

      IdentityID[] result = provider.callCopyIdentityRoles(source, "newOrg");

      assertEquals(0, result.length,
         "Global 'Administrator' role must be stripped by isSystemAdministratorRole()");
   }

   // -------------------------------------------------------------------------
   // copyRoleToOrganization — via: copyOrganizationInternal (private; tested via reflection)
   // -------------------------------------------------------------------------

   // [Path A] role not found → returns null
   @Test
   void copyRoleToOrganization_roleNotFound_returnsNull() {
      IdentityService svc = mock(IdentityService.class);
      IdentityID roleID = new IdentityID("editor", "fromOrg");
      // no stub → getRole returns null

      IdentityID result = provider.callCopyRoleToOrganization(roleID, "newOrg", "fromOrg", svc, mock(Principal.class));

      assertNull(result);
      assertTrue(provider.getAddedRoles().isEmpty());
   }

   // [Path B] role found → FSRole built with newOrgID, addRole called, returns new IdentityID
   @Test
   void copyRoleToOrganization_roleFound_createsNewRoleAndCallsAddRole() {
      IdentityService svc = mock(IdentityService.class);
      when(svc.getPermission(any(), any(), any(), any())).thenReturn(List.of());

      IdentityID roleID = new IdentityID("editor", "fromOrg");
      FSRole fromRole = new FSRole(roleID);
      fromRole.setOrganization("fromOrg");
      fromRole.setRoles(new IdentityID[0]);
      fromRole.setDefaultRole(true);
      fromRole.setDesc("an editor role");
      provider.stubRole(roleID, fromRole);

      IdentityID result = provider.callCopyRoleToOrganization(roleID, "newOrg", "fromOrg", svc, mock(Principal.class));

      assertEquals("editor", result.name);
      assertEquals("newOrg", result.orgID);

      assertEquals(1, provider.getAddedRoles().size());
      Role added = provider.getAddedRoles().get(0);
      assertEquals("newOrg", added.getOrganizationID());
      assertEquals("an editor role", added.getDescription());
      assertTrue(added.isDefaultRole());
   }

   // -------------------------------------------------------------------------
   // copyUserToOrganization — via: copyOrganizationInternal (private; tested via reflection)
   // -------------------------------------------------------------------------

   // [Path A] fromUser found, no defaultPassword → password hash copied from old user
   @Test
   void copyUserToOrganization_userFound_noDefaultPassword_copiesPasswordHash() {
      IdentityService svc = mock(IdentityService.class);
      when(svc.getPermission(any(), any(), any(), any())).thenReturn(List.of());

      IdentityID memberID = new IdentityID("alice", "fromOrg");
      FSUser fromUser = new FSUser(memberID);
      fromUser.setPassword("existingHash");
      fromUser.setPasswordAlgorithm("bcrypt");
      fromUser.setOrganization("fromOrg");
      provider.stubUser(memberID, fromUser);

      IdentityID result = provider.callCopyUserToOrganization(memberID, "newOrg", "fromOrg", svc, mock(Principal.class), null);

      assertEquals("alice", result.name);
      assertEquals("newOrg", result.orgID);

      assertEquals(1, provider.getAddedUsers().size());
      User added = provider.getAddedUsers().get(0);
      assertEquals("existingHash", added.getPassword());
      assertEquals("bcrypt", added.getPasswordAlgorithm());
      assertEquals("newOrg", added.getOrganizationID());
   }

   // [Path B] fromUser found, defaultPassword given → new hash applied
   // Tool.hash() requires PasswordEncryption.newInstance() → Spring context → [mockStatic] tier
   @Disabled("Requires mockStatic(Tool.class) or @SreeHome — move to AbstractEditableAuthenticationProviderStaticDepTest")
   @Test
   void copyUserToOrganization_userFound_withDefaultPassword_hashesNewPassword() {
      IdentityService svc = mock(IdentityService.class);
      when(svc.getPermission(any(), any(), any(), any())).thenReturn(List.of());

      IdentityID memberID = new IdentityID("alice", "fromOrg");
      FSUser fromUser = new FSUser(memberID);
      fromUser.setPassword("oldHash");
      fromUser.setPasswordAlgorithm("SHA-256");
      fromUser.setOrganization("fromOrg");
      provider.stubUser(memberID, fromUser);

      provider.callCopyUserToOrganization(memberID, "newOrg", "fromOrg", svc, mock(Principal.class), "newP@ssw0rd");

      User added = provider.getAddedUsers().get(0);
      assertNotEquals("oldHash", added.getPassword(), "Password must be re-hashed, not copied");
      assertNotNull(added.getPassword());
      assertEquals("bcrypt", added.getPasswordAlgorithm());
   }

   // Issue #74695
   // [Path C] fromUser null → addUser called with old memberID (wrong orgID)  [Suspect 5]
   @Disabled("Suspect 5: null-user branch uses memberID.orgID==fromOrgID instead of newOrgID — AbstractEditableAuthenticationProvider:637; Fix: new FSUser(new IdentityID(memberID.name, orgID))")
   @Test
   void copyUserToOrganization_userNotFound_placeholderShouldUseNewOrgId() {
      IdentityService svc = mock(IdentityService.class);
      when(svc.getPermission(any(), any(), any(), any())).thenReturn(List.of());

      IdentityID memberID = new IdentityID("ghost", "fromOrg");
      // no user stubbed → getUser returns null

      IdentityID result = provider.callCopyUserToOrganization(memberID, "newOrg", "fromOrg", svc, mock(Principal.class), null);

      assertEquals(1, provider.getAddedUsers().size());
      assertEquals("newOrg", provider.getAddedUsers().get(0).getIdentityID().orgID,
         "Suspect 5: placeholder user should be created in newOrg, not fromOrg");
      assertEquals("newOrg", result.orgID,
         "Suspect 5: returned IdentityID should have newOrgID");
   }

   // -------------------------------------------------------------------------
   // copyGroupToOrganization — via: copyOrganizationInternal (private; tested via reflection)
   // -------------------------------------------------------------------------

   // [Path A] group not found → returns null, addGroup NOT called
   @Test
   void copyGroupToOrganization_groupNotFound_returnsNull() {
      IdentityService svc = mock(IdentityService.class);
      IdentityID groupID = new IdentityID("devs", "fromOrg");
      // no stub → getGroup returns null

      IdentityID result = provider.callCopyGroupToOrganization(groupID, "newOrg", "fromOrg", svc, mock(Principal.class));

      assertNull(result);
      assertTrue(provider.getAddedGroups().isEmpty());
   }

   // [Path B] group found → FSGroup built with newOrgID, addGroup called, returns new IdentityID
   @Test
   void copyGroupToOrganization_groupFound_createsNewGroupAndCallsAddGroup() {
      IdentityService svc = mock(IdentityService.class);
      when(svc.getPermission(any(), any(), any(), any())).thenReturn(List.of());

      IdentityID groupID = new IdentityID("devs", "fromOrg");
      FSGroup fromGroup = new FSGroup(groupID);
      fromGroup.setOrganization("fromOrg");
      fromGroup.setLocale("en_US");
      fromGroup.setRoles(new IdentityID[0]);
      provider.stubGroup(groupID, fromGroup);

      IdentityID result = provider.callCopyGroupToOrganization(groupID, "newOrg", "fromOrg", svc, mock(Principal.class));

      assertEquals("devs", result.name);
      assertEquals("newOrg", result.orgID);

      assertEquals(1, provider.getAddedGroups().size());
      Group added = provider.getAddedGroups().get(0);
      assertEquals("newOrg", added.getOrganizationID());
   }

   // -------------------------------------------------------------------------
   // copyRootPermittedIdentities — via: copyOrganizationInternal (private; tested via reflection)
   // -------------------------------------------------------------------------

   // [Path A] replace=false → 4 getPermission calls + 4 setIdentityPermissions for new org; no clear calls
   @Test
   void copyRootPermittedIdentities_replaceFalse_setsNewOrgPermissionsOnly() {
      IdentityService svc = mock(IdentityService.class);
      when(svc.getPermission(any(), any(), any(), any())).thenReturn(List.of());
      Principal principal = mock(Principal.class);

      FSOrganization fromOrg = new FSOrganization("fromOrg");
      fromOrg.setName("fromOrg");

      provider.callCopyRootPermittedIdentities(fromOrg, "newOrg", "newOrg", svc, principal, false);

      verify(svc, times(4)).getPermission(any(), any(), eq("fromOrg"), eq(principal));
      verify(svc, times(4)).setIdentityPermissions(any(), any(), any(), eq(principal), any(), eq("newOrg"));
      verify(svc, never()).setIdentityPermissions(any(), any(), any(), any(), isNull(), isNull());
   }

   // [Path B] replace=true → same 4 getPermission calls + 4 new-org sets + 4 null-clear sets
   @Test
   void copyRootPermittedIdentities_replaceTrue_setsNewOrgPermissionsAndClearsOldOrg() {
      IdentityService svc = mock(IdentityService.class);
      when(svc.getPermission(any(), any(), any(), any())).thenReturn(List.of());
      Principal principal = mock(Principal.class);

      FSOrganization fromOrg = new FSOrganization("fromOrg");
      fromOrg.setName("fromOrg");

      provider.callCopyRootPermittedIdentities(fromOrg, "newOrg", "newOrg", svc, principal, true);

      verify(svc, times(4)).getPermission(any(), any(), eq("fromOrg"), eq(principal));
      verify(svc, times(4)).setIdentityPermissions(any(), any(), any(), eq(principal), any(), eq("newOrg"));
      verify(svc, times(4)).setIdentityPermissions(any(), any(), any(), eq(principal), isNull(), isNull());
   }

   // -------------------------------------------------------------------------
   // StubProvider — minimal instantiation per Section 2D step 2
   // Overrides only: (a) compiler-required abstract methods, (b) query methods
   // called by the concrete methods under test (getUser/getGroup/getRole),
   // (c) addUser/addRole/addGroup for call tracking.
   // Does NOT override the concrete methods being tested.
   //
   // Reflection helpers expose private methods directly without going through
   // copyOrganizationInternal (which requires static factory mocking).
   // -------------------------------------------------------------------------

   static class StubProvider extends AbstractEditableAuthenticationProvider {

      private final Map<IdentityID, User>  users  = new HashMap<>();
      private final Map<IdentityID, Group> groups = new HashMap<>();
      private final Map<IdentityID, Role>  roles  = new HashMap<>();

      private final List<User>  addedUsers  = new ArrayList<>();
      private final List<Role>  addedRoles  = new ArrayList<>();
      private final List<Group> addedGroups = new ArrayList<>();

      void stubUser(IdentityID id, User user)    { users.put(id, user); }
      void stubGroup(IdentityID id, Group group) { groups.put(id, group); }
      void stubRole(IdentityID id, Role role)    { roles.put(id, role); }

      List<User>  getAddedUsers()  { return addedUsers; }
      List<Role>  getAddedRoles()  { return addedRoles; }
      List<Group> getAddedGroups() { return addedGroups; }

      @Override public User  getUser(IdentityID id)  { return users.get(id); }
      @Override public Group getGroup(IdentityID id) { return groups.get(id); }
      @Override public Role  getRole(IdentityID id)  { return roles.get(id); }

      @Override public void addUser(User user)    { addedUsers.add(user); }
      @Override public void addRole(Role role)    { addedRoles.add(role); }
      @Override public void addGroup(Group group) { addedGroups.add(group); }

      @Override public boolean authenticate(IdentityID userIdentity, Object credential) { return false; }
      @Override public Organization getOrganization(String id)  { return null; }
      @Override public String getOrgIdFromName(String name)     { return null; }
      @Override public String getOrgNameFromID(String id)       { return null; }
      @Override public String[] getOrganizationIDs()            { return new String[0]; }
      @Override public String[] getOrganizationNames()          { return new String[0]; }
      @Override public void tearDown() {}

      // -- Reflection helpers (wrap private methods without going through copyOrganizationInternal) --

      @SuppressWarnings("unchecked")
      IdentityID[] callCopyIdentityRoles(AbstractIdentity fromID, String orgName) {
         try {
            var m = AbstractEditableAuthenticationProvider.class
               .getDeclaredMethod("copyIdentityRoles", AbstractIdentity.class, String.class);
            m.setAccessible(true);
            return (IdentityID[]) m.invoke(this, fromID, orgName);
         }
         catch(ReflectiveOperationException e) {
            throw new AssertionError("reflection failed: copyIdentityRoles", e);
         }
      }

      IdentityID callCopyRoleToOrganization(IdentityID roleIdentity, String orgID, String fromOrgID,
                                             IdentityService identityService, Principal principal) {
         try {
            var m = AbstractEditableAuthenticationProvider.class.getDeclaredMethod(
               "copyRoleToOrganization", IdentityID.class, String.class, String.class,
               IdentityService.class, Principal.class);
            m.setAccessible(true);
            return (IdentityID) m.invoke(this, roleIdentity, orgID, fromOrgID, identityService, principal);
         }
         catch(ReflectiveOperationException e) {
            throw new AssertionError("reflection failed: copyRoleToOrganization", e);
         }
      }

      IdentityID callCopyUserToOrganization(IdentityID memberID, String orgID, String fromOrgID,
                                             IdentityService identityService, Principal principal,
                                             String defaultPassword) {
         try {
            var m = AbstractEditableAuthenticationProvider.class.getDeclaredMethod(
               "copyUserToOrganization", IdentityID.class, String.class, String.class,
               IdentityService.class, Principal.class, String.class);
            m.setAccessible(true);
            return (IdentityID) m.invoke(this, memberID, orgID, fromOrgID, identityService, principal, defaultPassword);
         }
         catch(ReflectiveOperationException e) {
            throw new AssertionError("reflection failed: copyUserToOrganization", e);
         }
      }

      IdentityID callCopyGroupToOrganization(IdentityID memberID, String orgID, String fromOrgID,
                                              IdentityService identityService, Principal principal) {
         try {
            var m = AbstractEditableAuthenticationProvider.class.getDeclaredMethod(
               "copyGroupToOrganization", IdentityID.class, String.class, String.class,
               IdentityService.class, Principal.class);
            m.setAccessible(true);
            return (IdentityID) m.invoke(this, memberID, orgID, fromOrgID, identityService, principal);
         }
         catch(ReflectiveOperationException e) {
            throw new AssertionError("reflection failed: copyGroupToOrganization", e);
         }
      }

      void callCopyRootPermittedIdentities(Organization fromOrganization, String newOrgName,
                                            String newOrgID, IdentityService identityService,
                                            Principal principal, boolean replace) {
         try {
            var m = AbstractEditableAuthenticationProvider.class.getDeclaredMethod(
               "copyRootPermittedIdentities", Organization.class, String.class, String.class,
               IdentityService.class, Principal.class, boolean.class);
            m.setAccessible(true);
            m.invoke(this, fromOrganization, newOrgName, newOrgID, identityService, principal, replace);
         }
         catch(ReflectiveOperationException e) {
            throw new AssertionError("reflection failed: copyRootPermittedIdentities", e);
         }
      }
   }
}
