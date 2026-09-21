/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.identities;

import inetsoft.web.admin.security.*;
import inetsoft.sree.security.*;
import inetsoft.web.admin.InvalidResourceException;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.RollbackFailure;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.DeleteIdentitiesTaskImpactResponse;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.AdminChangeRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Spec section 6 (apply/rollback per verb) and section 7 (the Tier-2 backup, unconditional for
 * this whole area, taken synchronously before any mutation). Every prior area's own compensating-
 * transaction shape (apply loop, undo newest-first, {@code RollbackFailure} on "state unknown")
 * is exercised the same way here; the tests below focus on what is NEW: the partial user-delete
 * inverse (a generated password disclosed once, spec section 4/9), and organization-delete's
 * deliberate non-compensability (spec section 6 item 5).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class IdentityChangesetApplyServiceTest {
   @Mock private SecurityService securityService;
   @Mock private IdentityService identityService;
   @Mock private SecurityEngine securityEngine;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   private IdentityChangePlanService planService;
   private IdentityChangesetApplyService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;
   private Audit auditMock;

   @BeforeEach void setUp() throws Exception {
      planService = new IdentityChangePlanService(securityService);
      service = new IdentityChangesetApplyService(planService, securityService, identityService,
                                                  securityEngine, backupService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      lenient().when(user.getName()).thenReturn(new IdentityID("caller", "host-org").convertToKey());
      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");
      lenient().when(identityService.getDeleteTaskImpacts(any(), any(), eq(user)))
         .thenReturn(DeleteIdentitiesTaskImpactResponse.builder()
            .ownedTasks(List.of()).executeAsTasks(List.of()).build());

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> {
            String s = inv.getArgument(0);

            if(!s.startsWith("TKN:")) {
               throw new IllegalArgumentException("not a token");
            }

            return s.substring(4);
         });
      tool.when(Tool::getHost).thenReturn("test-host");

      auditMock = mock(Audit.class);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(auditMock);
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      tool.close();
      auditStatic.close();
   }

   // -------------------------------------------------------------------------
   // success
   // -------------------------------------------------------------------------

   @Test void appliesACreateUserAndReportsApplied() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      // applyRequest()'s own resolve() call (for the hash) and apply()'s internal re-resolve each
      // make one existence-check call before applyCreateUser's own after-create verify -- three
      // calls total, not two (a fixture mistake this comment exists to prevent repeating).
      when(securityService.getUser(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such user"))
         .thenThrow(new MissingResourceException("no such user"))
         .thenReturn(existingUser(id));

      var result = service.apply(applyRequest("create bob", createUser("bob")), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("admin-snapshot/ref", result.backupRef());
      assertNull(result.rollbackFailures());
      verify(securityService).createUser(any(SecurityUser.class), eq("host-org"), eq(user));
      verify(backupService).backup(anyString());
   }

   @Test void appliesADeleteUserAndReportsAppliedWithNoAdvisoryWhenNoTaskImpact() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      // Three calls return the existing user (helper resolve, apply's internal re-resolve,
      // applyDeleteUser's own before-capture), then the after-delete verify throws (confirms gone).
      when(securityService.getUser(eq(id), eq(user)))
         .thenReturn(existingUser(id), existingUser(id), existingUser(id))
         .thenThrow(new MissingResourceException("gone"));

      var result = service.apply(applyRequest("delete bob", deleteUser("bob")), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(result.results().get(0).advisory());
      verify(securityService).deleteUser(eq(id), eq(user));
   }

   @Test void appliesADeleteUserAndDisclosesScheduleTaskImpact() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      when(securityService.getUser(eq(id), eq(user)))
         .thenReturn(existingUser(id), existingUser(id), existingUser(id))
         .thenThrow(new MissingResourceException("gone"));
      when(identityService.getDeleteTaskImpacts(any(), any(), eq(user)))
         .thenReturn(DeleteIdentitiesTaskImpactResponse.builder()
            .ownedTasks(List.of("nightly-refresh")).executeAsTasks(List.of()).build());

      var result = service.apply(applyRequest("delete bob", deleteUser("bob")), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNotNull(result.results().get(0).advisory());
      assertTrue(result.results().get(0).advisory().contains("nightly-refresh"));
   }

   // bug-76715: defaultRole/sysAdmin (role) and properties (organization) must actually reach
   // SecurityService.createRole/createOrganization, not just be accepted by the schema.

   @Test void appliesACreateRoleAndPassesDefaultRoleAndSysAdminThrough() throws Exception {
      IdentityID id = new IdentityID("Analyst", "host-org");
      when(securityService.getRole(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such role"))
         .thenThrow(new MissingResourceException("no such role"))
         .thenReturn(existingRole(id));

      IdentityChangeRequest change = createRole("Analyst");
      change.getSpec().setDefaultRole(true);
      change.getSpec().setSysAdmin(true);

      var result = service.apply(applyRequest("create Analyst", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).createRole(argThat(req ->
         Boolean.TRUE.equals(req.getDefaultRole()) && Boolean.TRUE.equals(req.getSysAdmin())
      ), eq("host-org"), eq(user));
   }

   // bug-76824: orgAdmin must also actually reach SecurityService.createRole, not just be
   // accepted by the schema.

   @Test void appliesACreateRoleAndPassesOrgAdminThrough() throws Exception {
      IdentityID id = new IdentityID("Org Admin Role", "host-org");
      when(securityService.getRole(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such role"))
         .thenThrow(new MissingResourceException("no such role"))
         .thenReturn(existingRole(id));

      IdentityChangeRequest change = createRole("Org Admin Role");
      change.getSpec().setOrgAdmin(true);

      var result = service.apply(applyRequest("create Org Admin Role", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).createRole(argThat(req ->
         Boolean.TRUE.equals(req.getOrgAdmin())
      ), eq("host-org"), eq(user));
   }

   @Test void appliesACreateOrganizationAndPassesPropertiesThrough() throws Exception {
      String orgId = "neworg";
      when(securityService.getOrganization(eq(orgId), eq(user)))
         .thenThrow(new MissingResourceException("no such organization"))
         .thenThrow(new MissingResourceException("no such organization"))
         .thenReturn(existingOrganization(orgId));

      IdentityChangeRequest change = createOrganization(orgId, "New Org");
      change.getSpec().setProperties(List.of(PropertyModel.builder().name("k").value("v").build()));

      var result = service.apply(applyRequest("create org", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).createOrganization(argThat(req ->
         req.getProperties() != null && req.getProperties().size() == 1 &&
         "k".equals(req.getProperties().get(0).name()) && "v".equals(req.getProperties().get(0).value())
      ), isNull(), eq(user));
   }

   // -------------------------------------------------------------------------
   // rollback
   // -------------------------------------------------------------------------

   @Test void trivialRollbackWhenTheOnlyEntryFailsVerification() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      // resolve's existence check: free. apply's after-check: still not found -> verification fails.
      when(securityService.getUser(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such user"))
         .thenThrow(new MissingResourceException("still not found"));

      var result = service.apply(applyRequest("create bob", createUser("bob")), user);

      // Spec section 6 item 5's corrected framing (01-spec.md, precision fix): a single failing
      // entry with nothing else to undo reports rolled-back trivially -- established plugin-wide
      // precedent, not unique to this area. What must never happen is a rollback ATTEMPT on this
      // failed create (there was nothing verified to undo).
      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      verify(securityService, never()).deleteUser(any(), any());
   }

   @Test void rollsBackAnEarlierCreateWhenALaterEntryFails() throws Exception {
      IdentityID alice = new IdentityID("alice", "host-org");
      IdentityID carol = new IdentityID("carol", "host-org");

      when(securityService.getUser(eq(alice), eq(user)))
         // helper resolve() + apply()'s internal re-resolve: both existence-check "id free".
         .thenThrow(new MissingResourceException("free"))
         .thenThrow(new MissingResourceException("free"))
         .thenReturn(existingUser(alice))                    // apply: alice after-create verify
         .thenThrow(new MissingResourceException("gone"));   // rollback: alice after-delete verify
      // carol: deleting this role "succeeds" (deleteRole is called) but a stale read still finds
      // her present afterward -- a verification failure, not a throw, so it does not add an
      // "unknown state" RollbackFailure (spec section 6).
      when(securityService.getRole(eq(carol), eq(user))).thenReturn(existingRole(carol),
         existingRole(carol), existingRole(carol));

      IdentityChangeRequest createAlice = createUser("alice");
      IdentityChangeRequest deleteCarolRole = deleteRole("carol");

      var result = service.apply(applyRequest("two changes", createAlice, deleteCarolRole), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      verify(securityService).createUser(any(SecurityUser.class), eq("host-org"), eq(user));
      // alice's create was rolled back: deleteUser called once for the undo.
      verify(securityService, atLeastOnce()).deleteUser(eq(alice), eq(user));
   }

   @Test void deleteUserRollbackGeneratesFreshPasswordAndDisclosesItOnce() throws Exception {
      IdentityID alice = new IdentityID("alice", "host-org");
      IdentityID carol = new IdentityID("carol", "host-org");
      SecurityUser aliceBefore = existingUser(alice);
      aliceBefore.setGroups(List.of("Analysts"));

      when(securityService.getUser(eq(alice), eq(user)))
         // helper resolve() + apply()'s internal re-resolve: both currentValue-capture reads.
         .thenReturn(aliceBefore, aliceBefore)
         .thenReturn(aliceBefore)                             // apply: before-capture at delete time
         .thenThrow(new MissingResourceException("deleted")) // apply: after-delete verify (gone)
         .thenReturn(aliceBefore);                            // rollback: after-recreate verify (back)
      when(securityService.getRole(eq(carol), eq(user)))
         .thenThrow(new MissingResourceException("free"));    // carol create: id free
      // carol's create "succeeds" but after-verify still can't find her -- a plain failure.

      IdentityChangeRequest deleteAlice = deleteUser("alice");
      IdentityChangeRequest createCarolRole = createRole("carol");

      var result = service.apply(applyRequest("delete then failing create", deleteAlice,
                                               createCarolRole), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      verify(securityService).createUser(argThat(u -> u.getIdentityID().equals(alice) &&
         u.getPassword() != null && !u.getPassword().isBlank()), eq("host-org"), eq(user));
      String advisory = result.results().get(0).advisory();
      assertNotNull(advisory);
      assertTrue(advisory.contains("freshly generated password"));
   }

   // -------------------------------------------------------------------------
   // update verb (design section 3.2/8/11)
   // -------------------------------------------------------------------------

   @Test void appliesAnUpdateUserAndCallsUpdateUserWithTheFullyMergedRequest() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      SecurityUser before = existingUser(id);
      before.setAlias("Old Alias");
      before.setLocale("en_US");
      before.setTheme("dark");
      before.setEmails(List.of("old@x.com"));
      before.setGroups(List.of("Analysts"));
      before.setRoles(List.of(new IdentityID("Viewer", "host-org")));

      when(securityService.getUser(eq(id), eq(user))).thenReturn(before);

      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("New Alias");
      IdentityChangeRequest change = updateUser("bob", spec);

      var result = service.apply(applyRequest("update bob", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).updateUser(eq(id), argThat(req ->
         "New Alias".equals(req.getAlias()) &&
         before.getLocale().equals(req.getLocale()) &&
         before.getTheme().equals(req.getTheme()) &&
         before.getEmails().equals(req.getEmails()) &&
         before.getGroups().equals(req.getGroups()) &&
         before.getRoles().equals(req.getRoles()) &&
         id.equals(req.getIdentityID())
      ), eq(user));
   }

   @Test void appliesAnUpdateGroupAndCallsUpdateGroupWithTheFullyMergedRequest() throws Exception {
      IdentityID id = new IdentityID("Analysts", "host-org");
      SecurityGroup before = existingGroup(id);
      before.setParentGroups(List.of("Employees"));
      before.setMemberUsers(List.of("bob"));
      before.setMemberGroups(List.of("SubGroup"));
      before.setRoles(List.of(new IdentityID("Viewer", "host-org")));

      when(securityService.getGroup(eq(id), eq(user))).thenReturn(before);

      IdentitySpec spec = new IdentitySpec();
      spec.setParentGroups(List.of("Contractors"));
      IdentityChangeRequest change = updateGroup("Analysts", spec);

      var result = service.apply(applyRequest("update Analysts", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).updateGroup(eq(id), argThat(req ->
         List.of("Contractors").equals(req.getParentGroups()) &&
         before.getMemberUsers().equals(req.getMemberUsers()) &&
         before.getMemberGroups().equals(req.getMemberGroups()) &&
         before.getRoles().equals(req.getRoles()) &&
         id.equals(req.getIdentityID())
      ), eq(user));
   }

   @Test void appliesAnUpdateRoleAndCallsUpdateRoleWithTheFullyMergedRequest() throws Exception {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole before = existingRole(id);
      before.setDescription("Old description");
      before.setAssignedUsers(List.of("bob"));
      before.setAssignedGroups(List.of("Analysts"));
      before.setInheritedRoles(List.of(new IdentityID("BaseRole", "host-org")));

      when(securityService.getRole(eq(id), eq(user))).thenReturn(before);

      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description");
      IdentityChangeRequest change = updateRole("Viewer", spec);

      var result = service.apply(applyRequest("update Viewer", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).updateRole(eq(id), argThat(req ->
         "New description".equals(req.getDescription()) &&
         before.getAssignedUsers().equals(req.getAssignedUsers()) &&
         before.getAssignedGroups().equals(req.getAssignedGroups()) &&
         before.getInheritedRoles().equals(req.getInheritedRoles()) &&
         id.equals(req.getIdentityID())
      ), eq(user));
   }

   @Test void appliesAnUpdateOrganizationAndCallsUpdateOrganizationWithTheFullyMergedRequest()
      throws Exception
   {
      SecurityOrganization before = existingOrganization("org1");
      before.setLocale("en_US");
      before.setMemberUsers(List.of("alice"));
      before.setMemberGroups(List.of("Analysts"));
      before.setRoles(List.of("Viewer"));

      when(securityService.getOrganization(eq("org1"), eq(user))).thenReturn(before);

      IdentitySpec spec = new IdentitySpec();
      spec.setOrgName("New Org Name");
      IdentityChangeRequest change = updateOrganization("org1", spec);

      var result = service.apply(applyRequest("update org1", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).updateOrganization(eq("org1"), argThat(req ->
         "New Org Name".equals(req.getName()) &&
         "org1".equals(req.getId()) &&
         before.getLocale().equals(req.getLocale()) &&
         before.getMemberUsers().equals(req.getMemberUsers()) &&
         before.getMemberGroups().equals(req.getMemberGroups()) &&
         before.getRoles().equals(req.getRoles())
      ), eq(user));
   }

   // bug-76715: the single most important regression test in this whole build (design/reconcile's
   // own sequencing warning) -- updating a field UNRELATED to defaultRole/sysAdmin on an existing
   // defaultRole:true/sysAdmin:true role must not silently un-default/un-sysAdmin it. This exercises
   // the full apply path (SecurityService.getRole -> IdentityMerge.mergeRole ->
   // SecurityService.updateRole), not just IdentityMergeTest's unit-level version.
   @Test void appliesAnUpdateRolePreservesDefaultRoleAndSysAdminWhenUpdatingAnUnrelatedField()
      throws Exception
   {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole before = existingRole(id);
      before.setDefaultRole(true);
      before.setSysAdmin(true);
      when(securityService.getRole(eq(id), eq(user))).thenReturn(before);

      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description"); // unrelated; defaultRole/sysAdmin left absent
      IdentityChangeRequest change = updateRole("Viewer", spec);

      var result = service.apply(applyRequest("update Viewer", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).updateRole(eq(id), argThat(req ->
         Boolean.TRUE.equals(req.getDefaultRole()) && Boolean.TRUE.equals(req.getSysAdmin())
      ), eq(user));
   }

   // bug-76824: orgAdmin needs the same preserve-on-unrelated-update guarantee as
   // defaultRole/sysAdmin above, exercised through the full apply path.
   @Test void appliesAnUpdateRolePreservesOrgAdminWhenUpdatingAnUnrelatedField() throws Exception {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole before = existingRole(id);
      before.setOrgAdmin(true);
      when(securityService.getRole(eq(id), eq(user))).thenReturn(before);

      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description"); // unrelated; orgAdmin left absent
      IdentityChangeRequest change = updateRole("Viewer", spec);

      var result = service.apply(applyRequest("update Viewer", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).updateRole(eq(id), argThat(req ->
         Boolean.TRUE.equals(req.getOrgAdmin())
      ), eq(user));
   }

   @Test void appliesAnUpdateOrganizationPreservesQuotaKeyWhenUpdatingAnUnrelatedProperty()
      throws Exception
   {
      SecurityOrganization before = existingOrganization("org1");
      before.setProperties(List.of(PropertyModel.builder().name("max.row.count").value("1000").build()));
      when(securityService.getOrganization(eq("org1"), eq(user))).thenReturn(before);

      IdentitySpec spec = new IdentitySpec();
      // Caller updates an unrelated property, never mentions max.row.count.
      spec.setProperties(List.of(PropertyModel.builder().name("other").value("y").build()));
      IdentityChangeRequest change = updateOrganization("org1", spec);

      var result = service.apply(applyRequest("update org1 properties", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).updateOrganization(eq("org1"), argThat(req ->
         req.getProperties().stream().anyMatch(p -> "max.row.count".equals(p.name()) && "1000".equals(p.value())) &&
         req.getProperties().stream().anyMatch(p -> "other".equals(p.name()) && "y".equals(p.value()))
      ), eq(user));
   }

   @Test void rollsBackAnEarlierUpdateRenameWhenALaterEntryFails() throws Exception {
      IdentityID aliceOld = new IdentityID("alice", "host-org");
      IdentityID aliceNew = new IdentityID("alice2", "host-org");
      IdentityID carol = new IdentityID("carol", "host-org");

      SecurityUser aliceBefore = existingUser(aliceOld);
      aliceBefore.setAlias("Alice Original");
      SecurityUser aliceAfter = existingUser(aliceNew);
      aliceAfter.setAlias("Alice Original");

      when(securityService.getUser(eq(aliceOld), eq(user))).thenReturn(aliceBefore);
      when(securityService.getUser(eq(aliceNew), eq(user))).thenReturn(aliceAfter);
      when(securityService.getRole(eq(carol), eq(user)))
         .thenThrow(new MissingResourceException("free"));

      IdentitySpec renameSpec = new IdentitySpec();
      renameSpec.setName("alice2");
      IdentityChangeRequest updateAlice = updateUser("alice", renameSpec);
      IdentityChangeRequest createCarolRole = createRole("carol");

      var result = service.apply(applyRequest("rename then failing create", updateAlice,
                                               createCarolRole), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      // Forward: renames alice -> alice2 (updateUser called with the ORIGINAL id, request carrying
      // the NEW name).
      verify(securityService).updateUser(eq(aliceOld),
         argThat(req -> "alice2".equals(req.getIdentityID().name)), eq(user));
      // Rollback: restores the original name (updateUser called with the NEW id -- the identity now
      // lives under alice2 -- and `before` passed unmodified, whose OWN identityID is the original
      // "alice", triggering updateUser's rename-back behavior for free, design section 8).
      verify(securityService).updateUser(eq(aliceNew),
         argThat(req -> "alice".equals(req.getIdentityID().name)), eq(user));
   }

   // Design section 4/11's flagged subtle bug: an implementer could re-verify a rename via the
   // ORIGINAL `id` variable instead of merged.getIdentityID() (the new id). This stubs the new id to
   // never resolve, so a correct implementation (which checks the new id) must report the update as
   // failed even though updateUser itself "succeeded" and the old id is still stubbed to resolve.
   @Test void renamingUpdateUserVerifiesViaTheNewIdNotTheOriginalId() throws Exception {
      IdentityID oldId = new IdentityID("dave", "host-org");
      IdentityID newId = new IdentityID("dave2", "host-org");
      SecurityUser before = existingUser(oldId);

      when(securityService.getUser(eq(oldId), eq(user))).thenReturn(before);
      when(securityService.getUser(eq(newId), eq(user)))
         .thenThrow(new MissingResourceException("not found under new name"));

      IdentitySpec spec = new IdentitySpec();
      spec.setName("dave2");
      IdentityChangeRequest change = updateUser("dave", spec);

      var result = service.apply(applyRequest("rename dave", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      verify(securityService).getUser(eq(newId), eq(user));
   }

   @Test void rollsBackAnEarlierUpdateOrganizationWhenALaterEntryFails() throws Exception {
      SecurityOrganization before = existingOrganization("org1");
      before.setLocale("en_US");
      IdentityID carol = new IdentityID("carol", "host-org");

      when(securityService.getOrganization(eq("org1"), eq(user))).thenReturn(before);
      when(securityService.getRole(eq(carol), eq(user)))
         .thenThrow(new MissingResourceException("free"));

      IdentitySpec spec = new IdentitySpec();
      spec.setOrgName("New Org Name");
      IdentityChangeRequest updateOrg = updateOrganization("org1", spec);
      IdentityChangeRequest createCarolRole = createRole("carol");

      var result = service.apply(applyRequest("update then failing create", updateOrg,
                                               createCarolRole), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(securityService).updateOrganization(eq("org1"),
         argThat(req -> "New Org Name".equals(req.getName())), eq(user));
      // Rollback restores the original name -- organization-update's rollback is compensable
      // (unlike organization-delete, declared permanently non-compensable), design section 8.
      verify(securityService).updateOrganization(eq("org1"),
         argThat(req -> "Org One".equals(req.getName())), eq(user));
   }

   // bug-76834: organization id rename is a real, supported capability -- SecurityService
   // .updateOrganization(id, request, principal) already relocates the organization to
   // request.getId() when it differs from the target `id` (confirmed by reading its full call
   // chain down through IdentityService.setOrganizationInfo/copyOrganizationInternal); this test
   // exercises the merge+apply+verify wiring that lets spec.id reach it.
   @Test void appliesAnUpdateOrganizationIdRenameAndVerifiesAtTheNewId() throws Exception {
      SecurityOrganization before = existingOrganization("org1");
      SecurityOrganization after = existingOrganization("org2");

      when(securityService.getOrganization(eq("org1"), eq(user))).thenReturn(before);
      when(securityService.getOrganization(eq("org2"), eq(user))).thenReturn(after);
      when(securityService.getOrganizations(eq(user))).thenReturn(organizationList("org1"));

      IdentitySpec spec = new IdentitySpec();
      spec.setId("org2");
      IdentityChangeRequest change = updateOrganization("org1", spec);

      var result = service.apply(applyRequest("rename org1 to org2", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      // Forward: updateOrganization called with the ORIGINAL id as the target, request carrying
      // the NEW id (SecurityService.updateOrganization's own contract -- confirmed by reading it).
      verify(securityService).updateOrganization(eq("org1"),
         argThat(req -> "org2".equals(req.getId())), eq(user));
      // Verified at the NEW id, not the original -- verifying at "org1" after a genuine rename
      // would misreport a successful rename as "organization not found after update" (the bug
      // this fix closes in applyUpdateOrganization).
      verify(securityService).getOrganization(eq("org2"), eq(user));
   }

   // The one claim in the diagnosis that was traced through the full deterministic call chain but
   // never executed before this test: rollback of an id-changing update must target the
   // organization's CURRENT (post-rename) id and restore the ORIGINAL id, not the reverse.
   @Test void rollsBackAnOrganizationIdRenameToTheOriginalIdWhenALaterEntryFails() throws Exception {
      SecurityOrganization before = existingOrganization("org1");
      SecurityOrganization after = existingOrganization("org2");
      IdentityID carol = new IdentityID("carol", "host-org");

      when(securityService.getOrganization(eq("org1"), eq(user))).thenReturn(before);
      when(securityService.getOrganization(eq("org2"), eq(user))).thenReturn(after);
      when(securityService.getOrganizations(eq(user))).thenReturn(organizationList("org1"));
      when(securityService.getRole(eq(carol), eq(user)))
         .thenThrow(new MissingResourceException("free"));

      IdentitySpec spec = new IdentitySpec();
      spec.setId("org2");
      IdentityChangeRequest renameOrg = updateOrganization("org1", spec);
      IdentityChangeRequest createCarolRole = createRole("carol");

      var result = service.apply(applyRequest("rename then failing create", renameOrg,
                                               createCarolRole), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      // Forward: renames org1 -> org2 (updateOrganization called with the ORIGINAL id, request
      // carrying the NEW id).
      verify(securityService).updateOrganization(eq("org1"),
         argThat(req -> "org2".equals(req.getId())), eq(user));
      // Rollback: targets the CURRENT/live id ("org2", where the organization actually lives
      // after the forward rename) with `before` passed unmodified -- `before`'s own captured id
      // ("org1") is what drives updateOrganization's rename-back, mirroring
      // rollbackUpdatedUser's updateUser(undo.afterId, undo.beforeUser, user) shape.
      verify(securityService).updateOrganization(eq("org2"),
         argThat(req -> "org1".equals(req.getId())), eq(user));
   }

   // bug-76715: confirms defaultRole/sysAdmin round-trip through Undo.updatedRole's replay-
   // captured-`before`-object rollback machinery "for free" (design's own claim) -- not assumed,
   // verified with a test per the design's own instruction.
   @Test void rollsBackAnEarlierUpdateRoleAndRestoresDefaultRoleAndSysAdmin() throws Exception {
      IdentityID id = new IdentityID("Viewer", "host-org");
      SecurityRole before = existingRole(id);
      before.setDefaultRole(true);
      before.setSysAdmin(true);
      IdentityID carol = new IdentityID("carol", "host-org");

      when(securityService.getRole(eq(id), eq(user))).thenReturn(before);
      when(securityService.getRole(eq(carol), eq(user)))
         .thenThrow(new MissingResourceException("free"));

      IdentitySpec spec = new IdentitySpec();
      spec.setDefaultRole(false);
      spec.setSysAdmin(false);
      IdentityChangeRequest updateViewer = updateRole("Viewer", spec);
      IdentityChangeRequest createCarolRole = createRole("carol");

      var result = service.apply(applyRequest("update then failing create", updateViewer,
                                               createCarolRole), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      // Forward: sets defaultRole/sysAdmin to false.
      verify(securityService).updateRole(eq(id), argThat(req ->
         Boolean.FALSE.equals(req.getDefaultRole()) && Boolean.FALSE.equals(req.getSysAdmin())
      ), eq(user));
      // Rollback: replays the captured `before` (defaultRole/sysAdmin still true) back through
      // updateRole.
      verify(securityService).updateRole(eq(id), argThat(req ->
         Boolean.TRUE.equals(req.getDefaultRole()) && Boolean.TRUE.equals(req.getSysAdmin())
      ), eq(user));
   }

   // -------------------------------------------------------------------------
   // pre-mutation validation refusals must not be misreported as rollback-failed (bug 76444)
   // -------------------------------------------------------------------------

   @Test void groupDeleteRefusedForMembersReportsPlainFailedNotRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("analysts", "host-org");
      SecurityGroup group = existingGroup(id);
      // resolve() helper call, apply()'s internal re-resolve, applyDeleteGroup's own before-
      // capture, then this fix's post-throw re-check -- all four see the identical, untouched
      // group, since deleteIdentities' "group has members" refusal never reaches syncIdentity.
      when(securityService.getGroup(eq(id), eq(user)))
         .thenReturn(group, group, group, group);
      doThrow(new SecurityService.PreMutationRefusalException(
         "Cannot remove a group containing user(s)."))
         .when(securityService).deleteGroup(eq(id), eq(user));

      var result = service.apply(applyRequest("delete analysts", deleteGroup("analysts")), user);

      // Trivially rolled back (nothing was ever mutated, so nothing needs undoing) -- same shape
      // as organizationDeleteFailureNeverAttemptsARollbackCreate, not rollback-failed.
      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
      verify(securityService, never()).createGroup(any(), any(), any());
   }

   @Test void userDeleteRefusedByValidationReportsPlainFailedNotRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      SecurityUser existing = existingUser(id);
      when(securityService.getUser(eq(id), eq(user)))
         .thenReturn(existing, existing, existing, existing);
      doThrow(new SecurityService.PreMutationRefusalException("em.security.noSystemAdmin"))
         .when(securityService).deleteUser(eq(id), eq(user));

      var result = service.apply(applyRequest("delete bob", deleteUser("bob")), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
   }

   @Test void roleDeleteRefusedByValidationReportsPlainFailedNotRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("viewer", "host-org");
      SecurityRole existing = existingRole(id);
      when(securityService.getRole(eq(id), eq(user)))
         .thenReturn(existing, existing, existing, existing);
      doThrow(new SecurityService.PreMutationRefusalException("em.security.noSystemAdmin"))
         .when(securityService).deleteRole(eq(id), eq(user));

      var result = service.apply(applyRequest("delete viewer", deleteRole("viewer")), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
   }

   @Test void groupDeleteAmbiguousMidMutationFailureStillReportsRollbackFailedEvenIfFieldsUnchanged()
      throws Exception
   {
      IdentityID id = new IdentityID("analysts", "host-org");
      SecurityGroup group = existingGroup(id);
      // Every getGroup call -- resolve() helper, apply()'s internal re-resolve, applyDeleteGroup's
      // own before-capture, AND (if it were ever attempted) a post-throw re-check -- sees the
      // identical, byte-for-byte unchanged group. This reproduces the review-round-1 counter-
      // example: IdentityService.syncIdentity runs dashboard/schedule-task cleanup
      // UNCONDITIONALLY, before its entity-specific eprovider.removeGroup call, so a failure in
      // that later call can leave real side effects applied even though the group's own fields
      // (all IdentityProjection looks at) are still unchanged -- a naive "is the entity's
      // projection unchanged" check would wrongly call this safe. deleteGroup throws a plain
      // Exception here (the shape syncIdentity's own catch-and-report-as-warning fallback
      // produces, NOT SecurityService.PreMutationRefusalException), so the fix must not even
      // attempt the projection re-check for it -- verified below by asserting getGroup is called
      // exactly 3 times, not 4.
      when(securityService.getGroup(eq(id), eq(user))).thenReturn(group, group, group, group);
      doThrow(new Exception("Failed to delete identity " + id + "."))
         .when(securityService).deleteGroup(eq(id), eq(user));

      var result = service.apply(applyRequest("delete analysts", deleteGroup("analysts")), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
      verify(securityService, times(3)).getGroup(eq(id), eq(user));
   }

   // -------------------------------------------------------------------------
   // create-side pre-mutation validation refusals must not be misreported as rollback-failed
   // (bug 76790 -- same family as bug 76444 above, but for create* instead of delete*).
   // -------------------------------------------------------------------------

   @Test void userCreateRefusedByValidationReportsPlainFailedNotRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      // Every getUser call -- resolve() helper, apply()'s internal re-resolve, and this fix's
      // post-throw re-check -- confirms the user never existed, since createUser's precondition
      // checks (e.g. weak-password validation) run before its first write.
      when(securityService.getUser(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such user"));
      doThrow(new Exception("Password does not meet strength requirements"))
         .when(securityService).createUser(any(SecurityUser.class), eq("host-org"), eq(user));

      var result = service.apply(applyRequest("create bob", createUser("bob")), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
      verify(securityService, times(3)).getUser(eq(id), eq(user));
   }

   @Test void groupCreateRefusedByValidationReportsPlainFailedNotRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("analysts", "host-org");
      when(securityService.getGroup(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such group"));
      doThrow(new Exception("Invalid parent group"))
         .when(securityService).createGroup(any(SecurityGroup.class), eq("host-org"), eq(user));

      var result = service.apply(applyRequest("create analysts", createGroup("analysts")), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
      verify(securityService, times(3)).getGroup(eq(id), eq(user));
   }

   @Test void roleCreateRefusedByValidationReportsPlainFailedNotRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("viewer", "host-org");
      when(securityService.getRole(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such role"));
      doThrow(new Exception("Invalid inherited role"))
         .when(securityService).createRole(any(SecurityRole.class), eq("host-org"), eq(user));

      var result = service.apply(applyRequest("create viewer", createRole("viewer")), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
      verify(securityService, times(3)).getRole(eq(id), eq(user));
   }

   @Test void organizationCreateRefusedByLocaleValidationReportsPlainFailedNotRollbackFailed()
      throws Exception
   {
      String orgId = "qa-org";
      // This is the bug's exact live repro: validateLocale throws InvalidResourceException
      // strictly before SecurityService.createOrganization's first write.
      when(securityService.getOrganization(eq(orgId), eq(user)))
         .thenThrow(new MissingResourceException("no such organization"));
      doThrow(new InvalidResourceException("Invalid locale: not-a-real-locale-code"))
         .when(securityService).createOrganization(any(SecurityOrganization.class), isNull(), eq(user));

      var result = service.apply(applyRequest("create qa-org", createOrganization(orgId, "QA Org")), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
      verify(securityService, times(3)).getOrganization(eq(orgId), eq(user));
   }

   @Test void organizationCreateAmbiguousMidMutationFailureStillReportsRollbackFailed()
      throws Exception
   {
      String orgId = "qa-org";
      // Unlike the clean-refusal case above, the org DOES exist after the throw (e.g. a partial
      // write) -- the fix must not swallow this into a false no-op; it must rethrow and let the
      // outer loop's conservative "unknown state" handling apply, same as before this fix.
      when(securityService.getOrganization(eq(orgId), eq(user)))
         .thenThrow(new MissingResourceException("no such organization"))
         .thenThrow(new MissingResourceException("no such organization"))
         .thenReturn(existingOrganization(orgId));
      doThrow(new Exception("Failed to create identity " + orgId + "."))
         .when(securityService).createOrganization(any(SecurityOrganization.class), isNull(), eq(user));

      var result = service.apply(applyRequest("create qa-org", createOrganization(orgId, "QA Org")), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
      verify(securityService, times(3)).getOrganization(eq(orgId), eq(user));
   }

   // -------------------------------------------------------------------------
   // update-side pre-mutation validation refusals must not be misreported as rollback-failed
   // (bug 76855 -- same family as bug 76444/76790 above, but for update* instead of delete*/
   // create*. Unlike those two, update*'s own SecurityService call has REAL cross-entity side
   // effects (addOrganizationMember for user/group, checkInheritRoles for role,
   // updateOrganizationMembers + dashboard/replet-registry/DataSpace migration for organization)
   // that run inside IdentityService.setIdentity, before the primary entity's own record write --
   // so the fix does NOT wrap the whole updateX() call the way create/delete do. Instead,
   // SecurityService.updateUser/Group/Role/Organization now wrap only their OWN precondition
   // segment (permission check, existence check, request-model building) -- everything strictly
   // before identityService.setIdentity(...) is called, which has no provider-mutating call of
   // any kind -- and rethrow as PreMutationRefusalException. Anything past that boundary (inside
   // setIdentity itself) is deliberately left as "unknown state"/rollback-failed, unchanged.)
   // -------------------------------------------------------------------------

   @Test void userUpdateRefusedByValidationReportsPlainFailedNotRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      SecurityUser existing = existingUser(id);
      // Every getUser call -- resolve() helper, apply()'s internal re-resolve, applyUpdateUser's
      // own before-capture, then this fix's post-throw re-check -- sees the identical, untouched
      // user, since updateUser's own precondition segment never reaches setIdentity.
      when(securityService.getUser(eq(id), eq(user))).thenReturn(existing);
      doThrow(new SecurityService.PreMutationRefusalException("Permission denied to update user"))
         .when(securityService).updateUser(eq(id), any(SecurityUser.class), eq(user));

      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("New Alias");
      var result = service.apply(applyRequest("update bob", updateUser("bob", spec)), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
   }

   @Test void groupUpdateRefusedByValidationReportsPlainFailedNotRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("analysts", "host-org");
      SecurityGroup existing = existingGroup(id);
      when(securityService.getGroup(eq(id), eq(user))).thenReturn(existing);
      doThrow(new SecurityService.PreMutationRefusalException("Permission denied to update group"))
         .when(securityService).updateGroup(eq(id), any(SecurityGroup.class), eq(user));

      IdentitySpec spec = new IdentitySpec();
      spec.setParentGroups(List.of("Contractors"));
      var result = service.apply(applyRequest("update analysts", updateGroup("analysts", spec)), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
   }

   @Test void roleUpdateRefusedByValidationReportsPlainFailedNotRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("viewer", "host-org");
      SecurityRole existing = existingRole(id);
      when(securityService.getRole(eq(id), eq(user))).thenReturn(existing);
      doThrow(new SecurityService.PreMutationRefusalException("Permission denied to update role"))
         .when(securityService).updateRole(eq(id), any(SecurityRole.class), eq(user));

      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description");
      var result = service.apply(applyRequest("update viewer", updateRole("viewer", spec)), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
   }

   @Test void organizationUpdateRefusedByValidationReportsPlainFailedNotRollbackFailed()
      throws Exception
   {
      String orgId = "qa-org";
      SecurityOrganization existing = existingOrganization(orgId);
      when(securityService.getOrganization(eq(orgId), eq(user))).thenReturn(existing);
      doThrow(new SecurityService.PreMutationRefusalException(
         "Permission denied to update organization"))
         .when(securityService).updateOrganization(eq(orgId), any(SecurityOrganization.class), eq(user));

      IdentitySpec spec = new IdentitySpec();
      spec.setOrgName("New Org Name");
      var result =
         service.apply(applyRequest("update qa-org", updateOrganization(orgId, spec)), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      assertNull(result.rollbackFailures());
   }

   @Test void userUpdateAmbiguousMidMutationFailureStillReportsRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      SecurityUser existing = existingUser(id);
      // Plain Exception (NOT PreMutationRefusalException) -- the shape a failure INSIDE
      // IdentityService.setUserInfo's own cross-entity mutation (e.g. addOrganizationMember,
      // which runs before the user's own record write) would produce. Even with the user's own
      // projection provably unchanged, the fix must not attempt (or trust) the safe re-check for
      // any exception type other than PreMutationRefusalException -- must still report
      // rollback-failed, proving the fix does not over-widen the safe classification.
      when(securityService.getUser(eq(id), eq(user))).thenReturn(existing);
      doThrow(new Exception("Failed to update identity " + id + "."))
         .when(securityService).updateUser(eq(id), any(SecurityUser.class), eq(user));

      IdentitySpec spec = new IdentitySpec();
      spec.setAlias("New Alias");
      var result = service.apply(applyRequest("update bob", updateUser("bob", spec)), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
   }

   @Test void groupUpdateAmbiguousMidMutationFailureStillReportsRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("analysts", "host-org");
      SecurityGroup existing = existingGroup(id);
      when(securityService.getGroup(eq(id), eq(user))).thenReturn(existing);
      doThrow(new Exception("Failed to update identity " + id + "."))
         .when(securityService).updateGroup(eq(id), any(SecurityGroup.class), eq(user));

      IdentitySpec spec = new IdentitySpec();
      spec.setParentGroups(List.of("Contractors"));
      var result = service.apply(applyRequest("update analysts", updateGroup("analysts", spec)), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
   }

   @Test void roleUpdateAmbiguousMidMutationFailureStillReportsRollbackFailed() throws Exception {
      IdentityID id = new IdentityID("viewer", "host-org");
      SecurityRole existing = existingRole(id);
      when(securityService.getRole(eq(id), eq(user))).thenReturn(existing);
      doThrow(new Exception("Failed to update identity " + id + "."))
         .when(securityService).updateRole(eq(id), any(SecurityRole.class), eq(user));

      IdentitySpec spec = new IdentitySpec();
      spec.setDescription("New description");
      var result = service.apply(applyRequest("update viewer", updateRole("viewer", spec)), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
   }

   @Test void organizationUpdateAmbiguousMidMutationFailureStillReportsRollbackFailed()
      throws Exception
   {
      String orgId = "qa-org";
      SecurityOrganization existing = existingOrganization(orgId);
      when(securityService.getOrganization(eq(orgId), eq(user))).thenReturn(existing);
      doThrow(new Exception("Failed to update identity " + orgId + "."))
         .when(securityService).updateOrganization(eq(orgId), any(SecurityOrganization.class), eq(user));

      IdentitySpec spec = new IdentitySpec();
      spec.setOrgName("New Org Name");
      var result =
         service.apply(applyRequest("update qa-org", updateOrganization(orgId, spec)), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
   }

   // -------------------------------------------------------------------------
   // organization delete -- non-compensable by design (spec section 6 item 5)
   // -------------------------------------------------------------------------

   @Test void organizationDeleteFailureNeverAttemptsARollbackCreate() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(existingOrganization("org1"));
      doThrow(new RuntimeException("boom")).when(securityService)
         .deleteOrganization(eq("org1"), eq(user));

      var result = service.apply(applyRequest("delete org1", deleteOrganization("org1")), user);

      assertEquals(AdminChangeRecordStatus.FAILED, statusOf(result.results().get(0).status()));
      verify(securityService, never()).createOrganization(any(), any(), any());
      // Trivial rolled-back at the plan level -- nothing else in the plan needed undoing, matching
      // the corrected precision note in 01-spec.md section 6 item 5.
      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
   }

   // -------------------------------------------------------------------------
   // gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsOnStaleHash() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("free"));
      IdentityApplyRequest req = applyRequest("create bob", createUser("bob"));
      req.setPlanHash("not-the-real-hash");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
      verify(securityService, never()).createUser(any(), any(), any());
   }

   @Test void applyThrowsWhenReviewOutcomeMissing() throws Exception {
      when(securityService.getUser(any(), eq(user)))
         .thenThrow(new MissingResourceException("free"));
      IdentityChangePlanRequest planReq = new IdentityChangePlanRequest();
      planReq.setTask("create bob");
      planReq.setChanges(List.of(createUser("bob")));
      ResolvedPlan resolved = planService.resolve(planReq, user);

      IdentityApplyRequest req = new IdentityApplyRequest();
      req.setTask("create bob");
      req.setChanges(List.of(createUser("bob")));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      // reviewOutcome left blank.

      assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
   }

   // -------------------------------------------------------------------------
   // taskToken audit pinning (bug #76588)
   // -------------------------------------------------------------------------

   @Test void rejectsAMissingTaskToken() throws Exception {
      when(securityService.getUser(any(), eq(user))).thenThrow(new MissingResourceException("free"));
      IdentityApplyRequest req = applyRequest("create bob", createUser("bob"));
      req.setTaskToken(null);

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
      verify(securityService, never()).createUser(any(), any(), any());
   }

   @Test void rejectsATaskTokenIssuedForADifferentPlan() throws Exception {
      when(securityService.getUser(any(), eq(user))).thenThrow(new MissingResourceException("free"));
      IdentityApplyRequest req = applyRequest("create bob", createUser("bob"));
      IdentityApplyRequest other = applyRequest("create carol", createUser("carol"));
      req.setTaskToken(other.getTaskToken());

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
      verify(securityService, never()).createUser(any(), any(), any());
   }

   @Test void auditsThePreviewedTaskEvenWhenApplyTaskDiffersForUserCreate() throws Exception {
      IdentityID id = new IdentityID("bob", "host-org");
      when(securityService.getUser(eq(id), eq(user)))
         .thenThrow(new MissingResourceException("no such user"))
         .thenThrow(new MissingResourceException("no such user"))
         .thenReturn(existingUser(id));

      IdentityApplyRequest req = applyRequestWithDivergentApplyTask(
         "reviewed: create bob", "totally different apply-time text", createUser("bob"));

      service.apply(req, user);

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditMock, atLeastOnce()).auditAdminChange(captor.capture(), eq(user));
      assertTrue(captor.getAllValues().stream()
         .allMatch(r -> "reviewed: create bob".equals(r.getTaskDescription())));
   }

   @Test void auditsThePreviewedTaskEvenWhenApplyTaskDiffersForOrganizationDelete() throws Exception {
      when(securityService.getOrganization(eq("org1"), eq(user)))
         .thenReturn(existingOrganization("org1"));

      IdentityApplyRequest req = applyRequestWithDivergentApplyTask(
         "reviewed: delete org1", "totally different apply-time text", deleteOrganization("org1"));

      service.apply(req, user);

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditMock, atLeastOnce()).auditAdminChange(captor.capture(), eq(user));
      assertTrue(captor.getAllValues().stream()
         .allMatch(r -> "reviewed: delete org1".equals(r.getTaskDescription())));
   }

   @Test void rollbackAuditsThePreviewedTaskEvenWhenApplyTaskDiffers() throws Exception {
      IdentityID alice = new IdentityID("alice", "host-org");
      IdentityID carol = new IdentityID("carol", "host-org");

      when(securityService.getUser(eq(alice), eq(user)))
         .thenThrow(new MissingResourceException("free"))
         .thenThrow(new MissingResourceException("free"))
         .thenReturn(existingUser(alice))
         .thenThrow(new MissingResourceException("gone"));
      when(securityService.getRole(eq(carol), eq(user))).thenReturn(existingRole(carol),
         existingRole(carol), existingRole(carol));

      IdentityChangeRequest createAlice = createUser("alice");
      IdentityChangeRequest deleteCarolRole = deleteRole("carol");

      IdentityApplyRequest req = applyRequestWithDivergentApplyTask(
         "reviewed: two changes", "totally different apply-time text", createAlice, deleteCarolRole);

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      verify(securityService, atLeastOnce()).deleteUser(eq(alice), eq(user));

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditMock, atLeastOnce()).auditAdminChange(captor.capture(), eq(user));
      assertTrue(captor.getAllValues().stream()
         .allMatch(r -> "reviewed: two changes".equals(r.getTaskDescription())));
   }

   // -------------------------------------------------------------------------
   // fixtures
   // -------------------------------------------------------------------------

   private IdentityApplyRequest applyRequest(String task, IdentityChangeRequest... changes)
      throws Exception
   {
      IdentityChangePlanRequest planReq = new IdentityChangePlanRequest();
      planReq.setTask(task);
      planReq.setChanges(List.of(changes));
      ResolvedPlan resolved = planService.resolve(planReq, user);

      IdentityApplyRequest req = new IdentityApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved by test");
      return req;
   }

   /** Previews with {@code previewTask}, then builds an apply request for the SAME plan hash/
    * taskToken but a deliberately DIFFERENT {@code applyTask} text -- the exact divergence this
    * area's audit-pinning bug consists of. */
   private IdentityApplyRequest applyRequestWithDivergentApplyTask(
      String previewTask, String applyTask, IdentityChangeRequest... changes) throws Exception
   {
      IdentityChangePlanRequest planReq = new IdentityChangePlanRequest();
      planReq.setTask(previewTask);
      planReq.setChanges(List.of(changes));
      ResolvedPlan resolved = planService.resolve(planReq, user);

      IdentityApplyRequest req = new IdentityApplyRequest();
      req.setTask(applyTask);
      req.setChanges(List.of(changes));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved by test");
      return req;
   }

   private static IdentityChangeRequest createUser(String name) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("create");
      change.setUnitType("user");
      IdentitySpec spec = new IdentitySpec();
      spec.setName(name);
      spec.setPassword("Password1!AAA");
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest createGroup(String name) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("create");
      change.setUnitType("group");
      IdentitySpec spec = new IdentitySpec();
      spec.setName(name);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest createRole(String name) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("create");
      change.setUnitType("role");
      IdentitySpec spec = new IdentitySpec();
      spec.setName(name);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest createOrganization(String id, String name) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("create");
      change.setUnitType("organization");
      IdentitySpec spec = new IdentitySpec();
      spec.setId(id);
      spec.setOrgName(name);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest deleteUser(String id) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("delete");
      change.setUnitType("user");
      change.setId(id);
      return change;
   }

   private static IdentityChangeRequest deleteGroup(String id) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("delete");
      change.setUnitType("group");
      change.setId(id);
      return change;
   }

   private static IdentityChangeRequest deleteRole(String id) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("delete");
      change.setUnitType("role");
      change.setId(id);
      return change;
   }

   private static IdentityChangeRequest deleteOrganization(String id) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("delete");
      change.setUnitType("organization");
      change.setId(id);
      return change;
   }

   private static IdentityChangeRequest updateUser(String id, IdentitySpec spec) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("user");
      change.setId(id);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest updateGroup(String id, IdentitySpec spec) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("group");
      change.setId(id);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest updateRole(String id, IdentitySpec spec) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("role");
      change.setId(id);
      change.setSpec(spec);
      return change;
   }

   private static IdentityChangeRequest updateOrganization(String id, IdentitySpec spec) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("update");
      change.setUnitType("organization");
      change.setId(id);
      change.setSpec(spec);
      return change;
   }

   private static SecurityUser existingUser(IdentityID id) {
      SecurityUser u = new SecurityUser();
      u.setIdentityID(id);
      u.setActive(true);
      u.setEmails(List.of());
      u.setGroups(List.of());
      u.setRoles(List.of());
      return u;
   }

   private static SecurityGroup existingGroup(IdentityID id) {
      SecurityGroup g = new SecurityGroup();
      g.setIdentityID(id);
      g.setParentGroups(List.of());
      g.setMemberUsers(List.of());
      g.setMemberGroups(List.of());
      g.setRoles(List.of());
      return g;
   }

   private static SecurityRole existingRole(IdentityID id) {
      SecurityRole r = new SecurityRole();
      r.setIdentityID(id);
      r.setAssignedUsers(List.of());
      r.setAssignedGroups(List.of());
      r.setInheritedRoles(List.of());
      return r;
   }

   private static SecurityOrganization existingOrganization(String id) {
      SecurityOrganization o = new SecurityOrganization();
      o.setId(id);
      o.setName("Org One");
      return o;
   }

   private static SecurityOrganizationList organizationList(String... ids) {
      SecurityOrganizationList list = new SecurityOrganizationList();
      list.setOrganizations(Arrays.stream(ids).map(IdentityChangesetApplyServiceTest::existingOrganization)
                                .collect(Collectors.toList()));
      return list;
   }

   /** Small local enum so assertions read clearly without importing the raw string constants
    * everywhere -- avoids a typo silently comparing two different literal strings. */
   private enum AdminChangeRecordStatus { VERIFIED, FAILED }

   private static AdminChangeRecordStatus statusOf(String raw) {
      return "verified".equals(raw) ? AdminChangeRecordStatus.VERIFIED : AdminChangeRecordStatus.FAILED;
   }
}
