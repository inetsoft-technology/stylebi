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
package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.RollbackFailure;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link ScriptLibraryChangesetApplyService#apply}: the plan-hash/reviewOutcome/
 * acknowledgeIrreversibleDelete gates, a successful apply for each verb, the dependency-check
 * refuse/force-allow gate carried through from preview to apply, and rollback of a verified
 * {@code create} when a later change in the same changeset fails verification -- exercising the
 * class javadoc's "delete IS fully compensable" claim indirectly (an undone create is the same
 * "capture, mutate, replay" shape a rolled-back delete uses).
 *
 * <p>{@link LibManager} is backed by a small in-memory fake (two {@code Map}s keyed by script
 * name), the same approach {@code ShapeChangesetApplyServiceTest} uses for its own {@code
 * DataSpace} fake -- {@code LibManager} is stateful across {@code setScript}/{@code
 * renameScript}/{@code removeScript}/{@code save}, and asserting real before/after state is more
 * robust here than chaining argument captors. Audit writes are not mocked: {@code writeAudit}
 * swallows every exception internally (logs and continues), so an unmocked {@code Audit.getInstance()}
 * failing in this unit test environment does not affect any assertion below.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScriptLibraryChangesetApplyServiceTest {
   @Mock private LibManagerProvider libManagerProvider;
   @Mock private LibManager lib;
   @Mock private SecurityEngine securityEngine;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;

   private final Map<String, String> scripts = new HashMap<>();
   private final Map<String, String> comments = new HashMap<>();
   private ScriptLibraryService scriptLibraryService;
   private ScriptLibraryChangePlanService planService;
   private ScriptLibraryChangesetApplyService service;
   private MockedStatic<Tool> tool;

   @BeforeEach
   void setUp() throws Exception {
      lenient().when(libManagerProvider.getManager(any(Principal.class))).thenReturn(lib);
      scriptLibraryService = new ScriptLibraryService(libManagerProvider, securityEngine);
      planService = new ScriptLibraryChangePlanService(scriptLibraryService);
      service = new ScriptLibraryChangesetApplyService(planService, scriptLibraryService,
                                                       libManagerProvider, backupService);

      lenient().when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.SCRIPT), anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(Tool::getHost).thenReturn("test-host");

      wireLibManagerFake();
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   private void wireLibManagerFake() {
      lenient().when(lib.getScript(anyString())).thenAnswer(inv -> scripts.get(inv.getArgument(0)));
      lenient().when(lib.getScriptComment(anyString()))
         .thenAnswer(inv -> comments.get(inv.getArgument(0)));
      lenient().doAnswer(inv -> {
         scripts.put(inv.getArgument(0), inv.getArgument(1));
         return null;
      }).when(lib).setScript(anyString(), anyString());
      lenient().doAnswer(inv -> {
         comments.put(inv.getArgument(0), inv.getArgument(1));
         return null;
      }).when(lib).setScriptComment(anyString(), anyString());
      lenient().doAnswer(inv -> {
         String oldName = inv.getArgument(0);
         String newName = inv.getArgument(1);
         scripts.put(newName, scripts.remove(oldName));

         if(comments.containsKey(oldName)) {
            comments.put(newName, comments.remove(oldName));
         }

         return null;
      }).when(lib).renameScript(anyString(), anyString());
      lenient().doAnswer(inv -> {
         String name = inv.getArgument(0);
         scripts.remove(name);
         comments.remove(name);
         return null;
      }).when(lib).removeScript(anyString());
   }

   private static ScriptLibraryChangeRequest change(String verb, String name) {
      ScriptLibraryChangeRequest c = new ScriptLibraryChangeRequest();
      c.setVerb(verb);
      c.setName(name);
      return c;
   }

   private static ScriptLibraryChangePlanRequest request(ScriptLibraryChangeRequest... changes) {
      ScriptLibraryChangePlanRequest req = new ScriptLibraryChangePlanRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static ScriptLibraryApplyRequest applyRequest(String hash, String reviewOutcome,
                                                         Boolean acknowledgeIrreversibleDelete,
                                                         ScriptLibraryChangeRequest... changes)
   {
      ScriptLibraryApplyRequest req = new ScriptLibraryApplyRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      req.setPlanHash(hash);
      req.setReviewOutcome(reviewOutcome);
      req.setAcknowledgeIrreversibleDelete(acknowledgeIrreversibleDelete);
      return req;
   }

   private String hashOf(ScriptLibraryChangeRequest... changes) throws Exception {
      return planService.resolve(request(changes), user).planHash();
   }

   // -------------------------------------------------------------------------
   // hash / reviewOutcome / acknowledgeIrreversibleDelete gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsPlanHashMismatchOnStaleHash() {
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_CREATE, "a");
      ScriptLibraryApplyRequest req = applyRequest("not-the-real-hash", null, null, c);

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyOfALowRiskChangeNeverRequiresReviewOutcome() throws Exception {
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_CREATE, "a");
      String hash = hashOf(c);
      ScriptLibraryApplyRequest req = applyRequest(hash, null, null, c);

      ScriptLibraryApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
   }

   @Test void applyOfADeleteRequiresReviewOutcomeBecauseItIsHighRisk() throws Exception {
      scripts.put("a", "return 1;");
      comments.put("a", "desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_DELETE, "a");

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         dep.when(() -> DependencyTransformer.getDependencies(anyString())).thenReturn(List.of());
         String hash = hashOf(c);
         ScriptLibraryApplyRequest req = applyRequest(hash, "  ", true, c);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.apply(req, user));
         assertTrue(ex.getMessage().contains("reviewOutcome"), ex.getMessage());
      }
   }

   @Test void applyOfADeleteRequiresAcknowledgeIrreversibleDelete() throws Exception {
      scripts.put("a", "return 1;");
      comments.put("a", "desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_DELETE, "a");

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         dep.when(() -> DependencyTransformer.getDependencies(anyString())).thenReturn(List.of());
         String hash = hashOf(c);
         ScriptLibraryApplyRequest req = applyRequest(hash, "looks good", null, c);

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.apply(req, user));
         assertTrue(ex.getMessage().contains("acknowledgeIrreversibleDelete"), ex.getMessage());
      }
   }

   // -------------------------------------------------------------------------
   // success, per verb
   // -------------------------------------------------------------------------

   @Test void appliesACreateAndStoresTheScriptAndComment() throws Exception {
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_CREATE, "newScript");
      c.setDescription("does a thing");
      c.setText("return 1;");
      String hash = hashOf(c);
      ScriptLibraryApplyRequest req = applyRequest(hash, null, null, c);

      ScriptLibraryApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("return 1;", scripts.get("newScript"));
      assertEquals("does a thing", comments.get("newScript"));
   }

   @Test void appliesARenameAndMovesAnExistingPermissionGrant() throws Exception {
      scripts.put("old", "return 1;");
      comments.put("old", "desc");
      inetsoft.sree.security.Permission grant = new inetsoft.sree.security.Permission();
      when(securityEngine.getPermission(ResourceType.SCRIPT, "old")).thenReturn(grant);
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_RENAME, "old");
      c.setNewName("newName");
      String hash = hashOf(c);
      ScriptLibraryApplyRequest req = applyRequest(hash, null, null, c);

      ScriptLibraryApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(scripts.containsKey("old"));
      assertEquals("return 1;", scripts.get("newName"));
      verify(securityEngine).removePermission(ResourceType.SCRIPT, "old");
      verify(securityEngine).setPermission(ResourceType.SCRIPT, "newName", grant);
   }

   @Test void appliesAnUpdateChangingOnlyTheDescription() throws Exception {
      scripts.put("a", "return 1;");
      comments.put("a", "old desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_UPDATE, "a");
      c.setDescription("new desc");
      String hash = hashOf(c);
      ScriptLibraryApplyRequest req = applyRequest(hash, null, null, c);

      ScriptLibraryApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("return 1;", scripts.get("a"), "update must never touch the script body");
      assertEquals("new desc", comments.get("a"));
   }

   @Test void appliesADeleteRemovingTheScript() throws Exception {
      scripts.put("a", "return 1;");
      comments.put("a", "desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_DELETE, "a");

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         dep.when(() -> DependencyTransformer.getDependencies(anyString())).thenReturn(List.of());
         String hash = hashOf(c);
         ScriptLibraryApplyRequest req = applyRequest(hash, "looks good", true, c);

         ScriptLibraryApplyResult result = service.apply(req, user);

         assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
         assertFalse(scripts.containsKey("a"));
      }
   }

   // -------------------------------------------------------------------------
   // dependency-check refuse/force-allow, carried from preview through to apply
   // -------------------------------------------------------------------------

   @Test void applyRefusesADeleteWithDependentsWhenForceIsNotSet() throws Exception {
      scripts.put("a", "return 1;");
      comments.put("a", "desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_DELETE, "a");

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         AssetObject dependent = mock(AssetObject.class);
         dep.when(() -> DependencyTransformer.getDependencies(anyString()))
            .thenReturn(List.of(dependent));

         // Preview-time resolve() also refuses (no hash to obtain), matching production: a caller
         // can never even reach apply() with a valid hash for a forceless dependent delete.
         assertThrows(IllegalArgumentException.class, () -> hashOf(c));
      }
   }

   @Test void applyAllowsADeleteWithDependentsWhenForceIsSet() throws Exception {
      scripts.put("a", "return 1;");
      comments.put("a", "desc");
      ScriptLibraryChangeRequest c = change(ScriptLibraryChangeRequest.VERB_DELETE, "a");
      c.setForce(true);

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         AssetObject dependent = mock(AssetObject.class);
         dep.when(() -> DependencyTransformer.getDependencies(anyString()))
            .thenReturn(List.of(dependent));
         String hash = hashOf(c);
         ScriptLibraryApplyRequest req = applyRequest(hash, "looks good", true, c);

         ScriptLibraryApplyResult result = service.apply(req, user);

         assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
         assertFalse(scripts.containsKey("a"));
         String advisory = result.results().get(0).advisory();
         assertNotNull(advisory);
         assertTrue(advisory.contains("still used by"), advisory);
      }
   }

   // -------------------------------------------------------------------------
   // rollback
   // -------------------------------------------------------------------------

   @Test void rollsBackAnEarlierVerifiedCreateWhenALaterDeleteFailsVerification() throws Exception {
      scripts.put("keep", "return 1;");
      comments.put("keep", "desc");
      ScriptLibraryChangeRequest create = change(ScriptLibraryChangeRequest.VERB_CREATE, "newScript");
      create.setText("return 2;");
      ScriptLibraryChangeRequest delete = change(ScriptLibraryChangeRequest.VERB_DELETE, "keep");

      try(MockedStatic<DependencyTransformer> dep = mockStatic(DependencyTransformer.class)) {
         dep.when(() -> DependencyTransformer.getDependencies(anyString())).thenReturn(List.of());
         String hash = hashOf(create, delete);
         ScriptLibraryApplyRequest req = applyRequest(hash, "looks good", true, create, delete);

         // The delete's own removeScript call reports success but never actually removes the
         // script (fake left in place) -- forces a STATUS_FAILED verification on the second entry
         // without throwing, the same "verification failure, not an exception" shape
         // ShapeChangesetApplyServiceTest exercises for its own compensable delete.
         doAnswer(inv -> null).when(lib).removeScript("keep");

         ScriptLibraryApplyResult result = service.apply(req, user);

         assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
         assertNull(result.rollbackFailures());
         // The first (create) change was verified, then undone: the new script must not remain.
         assertFalse(scripts.containsKey("newScript"));
         // The second (delete) change never actually removed "keep" (fake left it in place).
         assertTrue(scripts.containsKey("keep"));
      }
   }

   @Test void reportsRollbackFailedWhenAnUndoItselfFails() throws Exception {
      ScriptLibraryChangeRequest create = change(ScriptLibraryChangeRequest.VERB_CREATE, "newScript");
      create.setText("return 1;");
      ScriptLibraryChangeRequest badCreate = change(ScriptLibraryChangeRequest.VERB_CREATE, "bad");
      badCreate.setText("x");
      String hash = hashOf(create, badCreate);
      ScriptLibraryApplyRequest req = applyRequest(hash, "looks good", null, create, badCreate);

      // The second create throws while writing (state unknown, never added to `undoable`).
      doThrow(new IllegalStateException("write boom")).when(lib).setScript(eq("bad"), anyString());
      // The undo of the first change (create, a plain removeScript) itself fails too.
      doThrow(new IllegalStateException("undo boom")).when(lib).removeScript("newScript");

      ScriptLibraryApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      List<String> failedProperties = result.rollbackFailures().stream()
         .map(RollbackFailure::property).toList();
      assertEquals(List.of("scriptlibrary:bad", "scriptlibrary:newScript"), failedProperties);
   }
}
