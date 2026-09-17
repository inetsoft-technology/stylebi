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
import java.util.List;

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

   private static IdentityChangeRequest createRole(String name) {
      IdentityChangeRequest change = new IdentityChangeRequest();
      change.setVerb("create");
      change.setUnitType("role");
      IdentitySpec spec = new IdentitySpec();
      spec.setName(name);
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

   /** Small local enum so assertions read clearly without importing the raw string constants
    * everywhere -- avoids a typo silently comparing two different literal strings. */
   private enum AdminChangeRecordStatus { VERIFIED, FAILED }

   private static AdminChangeRecordStatus statusOf(String raw) {
      return "verified".equals(raw) ? AdminChangeRecordStatus.VERIFIED : AdminChangeRecordStatus.FAILED;
   }
}
