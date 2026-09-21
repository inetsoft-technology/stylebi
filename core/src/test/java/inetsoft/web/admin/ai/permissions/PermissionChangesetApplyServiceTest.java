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
package inetsoft.web.admin.ai.permissions;

import inetsoft.web.admin.security.PermissionGrant;
import inetsoft.web.admin.security.SecurityService;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Two things this area's apply/rollback loop does that neither properties nor schedule tasks do
 * (spec section 6): single-grant (not whole-resource) verification, and a null-vs-empty rollback
 * branch for {@code create} (restore "no entry" exactly, spec section 4) -- both exercised below.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class PermissionChangesetApplyServiceTest {
   @Mock private SecurityService securityService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   private PermissionChangePlanService planService;
   private PermissionChangesetApplyService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;
   private Audit auditMock;

   @BeforeEach void setUp() {
      planService = new PermissionChangePlanService(securityService, securityEngine);
      service = new PermissionChangesetApplyService(planService, securityService, securityEngine);
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      lenient().when(user.getName()).thenReturn(new IdentityID("caller", "host-org").convertToKey());

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

   @Test void appliesACreateAndReportsApplied() throws Exception {
      Permission withGrant = new Permission();
      withGrant.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");
      // resolve(probe), resolve(req) inside apply, applyCreate's before-capture: no prior entry.
      // applyCreate's after-verify: the grant is now present.
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null, null, null, withGrant);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);

      PermissionApplyRequest req = applyRequest("create a grant",
         grantChange("bob", List.of("READ")));

      var result = service.apply(req, user);

      assertEquals(inetsoft.web.admin.ai.AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(result.backupRef());
      assertNull(result.rollbackFailures());
      verify(securityService).createPermissionGrant(eq("Examples/Census"), eq("ASSET"),
         any(PermissionGrant.class), eq(user));
   }

   @Test void appliesAnUpdateAndReportsApplied() throws Exception {
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("READ"));
      Permission withOldActions = new Permission();
      withOldActions.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");
      Permission withNewActions = new Permission();
      withNewActions.setUserGrantsForOrg(ResourceAction.WRITE, Set.of("bob"), "host-org");

      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(withOldActions, withOldActions, withOldActions, withNewActions);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(existing);

      PermissionChangeRequest change = grantChange("bob", List.of("WRITE"));
      change.setVerb(PermissionChangeRequest.VERB_UPDATE);
      PermissionApplyRequest req = applyRequest("update a grant", change);

      var result = service.apply(req, user);

      assertEquals(inetsoft.web.admin.ai.AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).updatePermissionGrant(eq("Examples/Census"), eq("ASSET"),
         eq("bob~;~host-org"), eq("USER"), any(PermissionGrant.class), eq(user));
   }

   @Test void appliesADeleteAndReportsApplied() throws Exception {
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("READ"));
      Permission withGrant = new Permission();
      withGrant.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");
      Permission withoutGrant = new Permission();

      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(withGrant, withGrant, withGrant, withoutGrant);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(existing);

      PermissionApplyRequest req = applyRequest("delete a grant", deleteChange("bob"));

      var result = service.apply(req, user);

      assertEquals(inetsoft.web.admin.ai.AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(securityService).deletePermissionGrant(eq("Examples/Census"), eq("ASSET"),
         eq("bob~;~host-org"), eq("USER"), eq(user));
   }

   // -------------------------------------------------------------------------
   // gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsOnStalePlanHash() throws Exception {
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);

      PermissionApplyRequest req = applyRequest("create a grant", grantChange("bob", List.of("READ")));
      req.setPlanHash("stale-hash");

      assertThrows(inetsoft.web.admin.ai.AdminChangesetApplyService.PlanHashMismatchException.class,
                  () -> service.apply(req, user));
      verify(securityService, never()).createPermissionGrant(any(), any(), any(), any());
   }

   @Test void applyThrowsWhenReviewOutcomeMissing() throws Exception {
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);

      PermissionApplyRequest req = applyRequest("create a grant", grantChange("bob", List.of("READ")));
      req.setReviewOutcome(null);

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   @Test void rejectsAMissingTaskToken() throws Exception {
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      PermissionApplyRequest req = applyRequest("create a grant", grantChange("bob", List.of("READ")));
      req.setTaskToken(null);

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
      verify(securityService, never()).createPermissionGrant(any(), any(), any(), any());
   }

   @Test void rejectsATaskTokenIssuedForADifferentPlan() throws Exception {
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "carol", "USER", user))
         .thenReturn(null);
      PermissionApplyRequest req = applyRequest("create a grant", grantChange("bob", List.of("READ")));
      PermissionApplyRequest other =
         applyRequest("a different task", grantChange("carol", List.of("READ")));
      req.setTaskToken(other.getTaskToken());

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
      verify(securityService, never()).createPermissionGrant(any(), any(), any(), any());
   }

   @Test void auditsThePreviewedTaskEvenWhenApplyTaskDiffers() throws Exception {
      Permission withGrant = new Permission();
      withGrant.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null, null, null, withGrant);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);

      PermissionApplyRequest req = applyRequestWithDivergentApplyTask(
         "reviewed: grant bob READ", "totally different apply-time text",
         grantChange("bob", List.of("READ")));

      service.apply(req, user);

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditMock, atLeastOnce()).auditAdminChange(captor.capture(), eq(user));
      assertTrue(captor.getAllValues().stream()
         .allMatch(r -> "reviewed: grant bob READ".equals(r.getTaskDescription())));
   }

   // -------------------------------------------------------------------------
   // failure / rollback
   // -------------------------------------------------------------------------

   @Test void throwMidApplyIsReportedAsUnknownStateAndRollbackFailed() throws Exception {
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      doThrow(new IllegalStateException("boom")).when(securityService)
         .createPermissionGrant(eq("Examples/Census"), eq("ASSET"), any(PermissionGrant.class),
                                eq(user));

      PermissionApplyRequest req = applyRequest("create a grant", grantChange("bob", List.of("READ")));

      var result = service.apply(req, user);

      assertEquals(inetsoft.web.admin.ai.AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                  result.status());
      assertNotNull(result.rollbackFailures());
      assertEquals(1, result.rollbackFailures().size());
      assertEquals("ASSET|Examples/Census|USER|bob~;~host-org",
                  result.rollbackFailures().get(0).property());
   }

   // The null-vs-empty finding (spec section 4/6): when the resource had NO permission entry
   // before this create, rollback must delete the storage entry outright, not leave an explicit
   // empty Permission where none existed.
   // Closes the read-then-write race 06-review.md flagged (item 2): rollback always removes just
   // the caller's own grant first (deletePermissionGrant), then re-checks LIVE state -- not the
   // stale resourceHadNoPriorEntry flag alone -- before deciding whether to also delete the whole
   // storage entry.
   @Test void rollbackOfCreateWithNoPriorEntryCallsDeletePermissionGrantThenRemovePermissionWhenEmpty()
      throws Exception
   {
      Permission withGrant = new Permission();
      withGrant.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");
      Permission empty = new Permission();

      // First change (create bob) succeeds; second (create carol) fails verification, forcing
      // rollback of the first.
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null,        // resolve(probe): bob's before (no prior entry)
                    null,         // resolve(probe): carol's before
                    null,         // resolve(req) inside apply: bob's before
                    null,         // resolve(req) inside apply: carol's before
                    null,         // applyCreate(bob): before-capture -- no prior entry
                    withGrant,    // applyCreate(bob): after-verify -- bob now present
                    withGrant,    // applyCreate(carol): before-capture -- bob still the only grant
                    withGrant,    // applyCreate(carol): after-verify -- carol did NOT get added (forces failure)
                    empty,        // rollback of bob's create: fresh post-delete read -- genuinely empty
                    null);        // rollback of bob's create: final verify read -- entry gone
      when(securityService.getPermissionGrant(eq("Examples/Census"), eq("ASSET"), anyString(),
         eq("USER"), eq(user))).thenReturn(null);

      PermissionApplyRequest req = applyRequest("two grants",
         grantChange("bob", List.of("READ")), grantChange("carol", List.of("READ")));

      var result = service.apply(req, user);

      assertEquals(inetsoft.web.admin.ai.AdminChangesetApplyService.STATUS_ROLLED_BACK,
                  result.status());
      verify(securityService).deletePermissionGrant(eq("Examples/Census"), eq("ASSET"),
         eq("bob~;~host-org"), eq("USER"), eq(user));
      verify(securityProvider).removePermission(ResourceType.ASSET, "Examples/Census", null);
   }

   // The race-closure itself: when a concurrent, unrelated grant exists at rollback time (despite
   // resourceHadNoPriorEntry having been captured true during apply), rollback must NOT delete the
   // whole entry -- only the caller's own grant.
   @Test void rollbackOfCreateDoesNotDeleteAConcurrentGrantEvenWhenResourceHadNoPriorEntry()
      throws Exception
   {
      Permission withBob = new Permission();
      withBob.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");
      Permission withCarolOnly = new Permission();
      withCarolOnly.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("carol"), "host-org");

      // Single-change plan (create bob) that itself throws on the mechanical delete call it needs
      // for its own verify step to fail is unnecessary here -- simulate the failure via a second,
      // always-failing change so rollback of bob's create is triggered, then assert the
      // post-delete state (a concurrent carol grant, simulating an EM-UI write that landed between
      // the before-capture and now) survives.
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null, null, null, null, null, withBob, withBob, withBob,
                    withCarolOnly, // rollback of bob's create: fresh post-delete read -- carol survived
                    withCarolOnly); // rollback of bob's create: final verify read
      when(securityService.getPermissionGrant(eq("Examples/Census"), eq("ASSET"), anyString(),
         eq("USER"), eq(user))).thenReturn(null);

      PermissionApplyRequest req = applyRequest("two grants",
         grantChange("bob", List.of("READ")), grantChange("dave", List.of("READ")));

      service.apply(req, user);

      verify(securityService).deletePermissionGrant(eq("Examples/Census"), eq("ASSET"),
         eq("bob~;~host-org"), eq("USER"), eq(user));
      verify(securityProvider, never()).removePermission(any(), anyString(), any());
   }

   // Every undo is attempted regardless, and a failed undo is named, not silently dropped.
   @Test void rollbackItselfFailingIsReportedNamingTheKey() throws Exception {
      Permission withGrant = new Permission();
      withGrant.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");

      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null, null, null, null, null, withGrant, withGrant, withGrant, withGrant);
      when(securityService.getPermissionGrant(eq("Examples/Census"), eq("ASSET"), anyString(),
         eq("USER"), eq(user))).thenReturn(null);
      // Rollback of bob's create now always attempts deletePermissionGrant first (06-review.md's
      // race-closure fix) -- that is the call whose failure this test exercises.
      doThrow(new IllegalStateException("cannot delete")).when(securityService)
         .deletePermissionGrant(eq("Examples/Census"), eq("ASSET"), eq("bob~;~host-org"),
                                eq("USER"), eq(user));

      PermissionApplyRequest req = applyRequest("two grants",
         grantChange("bob", List.of("READ")), grantChange("carol", List.of("READ")));

      var result = service.apply(req, user);

      assertEquals(inetsoft.web.admin.ai.AdminChangesetApplyService.STATUS_ROLLBACK_FAILED,
                  result.status());
      assertEquals(1, result.rollbackFailures().size());
      assertEquals("ASSET|Examples/Census|USER|bob~;~host-org",
                  result.rollbackFailures().get(0).property());
   }

   // A throw that fires strictly BEFORE any mutating call (here: applyCreate's own before-capture
   // read for the second item) must not by itself force rollback-failed when the rest of the batch
   // was applied, verified, and then cleanly rolled back.
   @Test void preMutationThrowOnSecondItemStillReportsRolledBackWhenFirstItemRollsBackCleanly()
      throws Exception
   {
      Permission withBob = new Permission();
      withBob.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), "host-org");
      Permission empty = new Permission();

      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(null,        // resolve(probe): bob's before
                    null,         // resolve(req) inside apply: bob's before
                    null,         // applyCreate(bob): before-capture -- no prior entry
                    withBob,      // applyCreate(bob): after-verify -- bob now present
                    empty,        // rollback of bob's create: fresh post-delete read -- empty
                    null);        // rollback of bob's create: final verify read -- entry gone
      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Beta", null))
         .thenReturn(null)        // resolve(probe): carol's before
         .thenReturn(null)        // resolve(req) inside apply: carol's before
         .thenThrow(new IllegalStateException("provider unavailable")); // applyCreate(carol):
                                                                        // before-capture throws
      when(securityService.getPermissionGrant(anyString(), eq("ASSET"), anyString(), eq("USER"),
         eq(user))).thenReturn(null);

      PermissionChangeRequest bobChange = grantChange("bob", List.of("READ"));
      PermissionChangeRequest carolChange = grantChange("carol", List.of("READ"));
      carolChange.setResourcePath("Examples/Beta");
      PermissionApplyRequest req = applyRequest("two grants", bobChange, carolChange);

      var result = service.apply(req, user);

      assertEquals(inetsoft.web.admin.ai.AdminChangesetApplyService.STATUS_ROLLED_BACK,
                  result.status());
      assertNull(result.rollbackFailures());
      verify(securityService, times(1)).createPermissionGrant(eq("Examples/Census"), eq("ASSET"),
         any(PermissionGrant.class), eq(user));
      verify(securityService, never()).createPermissionGrant(eq("Examples/Beta"), anyString(),
         any(PermissionGrant.class), any());
      verify(securityService).deletePermissionGrant(eq("Examples/Census"), eq("ASSET"),
         eq("bob~;~host-org"), eq("USER"), eq(user));
      verify(securityProvider).removePermission(ResourceType.ASSET, "Examples/Census", null);
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private PermissionApplyRequest applyRequest(String task, PermissionChangeRequest... changes)
      throws Exception
   {
      PermissionChangePlanRequest probe = new PermissionChangePlanRequest();
      probe.setTask(task);
      probe.setChanges(List.of(changes));
      ResolvedPlan resolved = planService.resolve(probe, user);

      PermissionApplyRequest req = new PermissionApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");
      return req;
   }

   private PermissionApplyRequest applyRequestWithDivergentApplyTask(
      String previewTask, String applyTask, PermissionChangeRequest... changes) throws Exception
   {
      PermissionChangePlanRequest probe = new PermissionChangePlanRequest();
      probe.setTask(previewTask);
      probe.setChanges(List.of(changes));
      ResolvedPlan resolved = planService.resolve(probe, user);

      PermissionApplyRequest req = new PermissionApplyRequest();
      req.setTask(applyTask);
      req.setChanges(List.of(changes));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");
      return req;
   }

   private static PermissionChangeRequest grantChange(String identityId, List<String> actions) {
      PermissionChangeRequest change = new PermissionChangeRequest();
      change.setVerb(PermissionChangeRequest.VERB_CREATE);
      change.setResourceType("ASSET");
      change.setResourcePath("Examples/Census");
      change.setIdentityType("USER");
      change.setIdentityId(identityId);
      change.setActions(actions);
      return change;
   }

   private static PermissionChangeRequest deleteChange(String identityId) {
      PermissionChangeRequest change = new PermissionChangeRequest();
      change.setVerb(PermissionChangeRequest.VERB_DELETE);
      change.setResourceType("ASSET");
      change.setResourcePath("Examples/Census");
      change.setIdentityType("USER");
      change.setIdentityId(identityId);
      return change;
   }
}
