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
package inetsoft.web.admin.ai.file;

import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-design.md section 6.3 (per-verb apply/rollback), 01-design.md section 6.3's Tier-2 backup
 * (unconditional, synchronous, before any mutation), 6.3's {@code reviewOutcome}/
 * {@code acknowledgeIrreversibleDelete} gates. {@link DataSpace}/{@link
 * DataSpaceContentSettingsService} are backed by a small in-memory fake (a file/folder map mutated
 * by the mocked calls) so verification/rollback assertions reflect real state transitions, matching
 * {@code ProviderChangesetApplyServiceTest}'s/{@code LicenseChangesetApplyServiceTest}'s own
 * in-memory fake chain.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class StoredAssetChangesetApplyServiceTest {
   @Mock private DataSpace dataSpace;
   @Mock private DataSpaceContentSettingsService contentSettingsService;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;

   private final Map<String, byte[]> files = new HashMap<>();
   private final Set<String> folders = new HashSet<>();
   private StoredAssetChangePlanService planService;
   private StoredAssetChangesetApplyService service;
   private MockedStatic<Tool> tool;

   @BeforeEach
   void setUp() throws Exception {
      planService = new StoredAssetChangePlanService(dataSpace);
      service = new StoredAssetChangesetApplyService(
         planService, contentSettingsService, dataSpace, backupService);

      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");
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

      wireDataSpaceFake();
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   private void wireDataSpaceFake() throws Exception {
      lenient().when(dataSpace.exists(isNull(), anyString()))
         .thenAnswer(inv -> {
            String path = inv.getArgument(1);
            return files.containsKey(path) || folders.contains(path);
         });
      lenient().when(dataSpace.isDirectory(anyString()))
         .thenAnswer(inv -> folders.contains((String) inv.getArgument(0)));
      lenient().when(dataSpace.getFileLength(isNull(), anyString()))
         .thenAnswer(inv -> (long) files.get((String) inv.getArgument(1)).length);
      lenient().when(dataSpace.getInputStream(isNull(), anyString()))
         .thenAnswer(inv -> new java.io.ByteArrayInputStream(files.get((String) inv.getArgument(1))));
      lenient().when(dataSpace.makeDirectory(anyString()))
         .thenAnswer(inv -> folders.add((String) inv.getArgument(0)));
      lenient().doAnswer(inv -> {
         String path = inv.getArgument(1);
         DataSpace.OutputStreamOperation op = inv.getArgument(2);
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         op.accept(out);
         files.put(path, out.toByteArray());
         return null;
      }).when(dataSpace).withOutputStream(isNull(), anyString(), any(DataSpace.OutputStreamOperation.class));
      lenient().when(dataSpace.delete(isNull(), anyString()))
         .thenAnswer(inv -> {
            String path = inv.getArgument(1);
            boolean removedFile = files.remove(path) != null;
            boolean removedFolder = folders.remove(path);
            return removedFile || removedFolder;
         });
      lenient().when(dataSpace.rename(anyString(), anyString()))
         .thenAnswer(inv -> {
            String from = inv.getArgument(0);
            String to = inv.getArgument(1);

            if(folders.remove(from)) {
               folders.add(to);
               return true;
            }

            byte[] content = files.remove(from);

            if(content == null) {
               return false;
            }

            files.put(to, content);
            return true;
         });
      lenient().doAnswer(inv -> {
         String path = inv.getArgument(0);
         boolean folder = inv.getArgument(1);

         if(folder) {
            folders.remove(path);
         }
         else {
            files.remove(path);
         }

         return null;
      }).when(contentSettingsService).deleteDataSpaceNode(anyString(), anyBoolean());
   }

   private static StoredAssetChangePlanRequest request(StoredAssetChangeRequest... changes) {
      StoredAssetChangePlanRequest req = new StoredAssetChangePlanRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static StoredAssetChangeRequest change(String unitType, String verb, String path) {
      StoredAssetChangeRequest c = new StoredAssetChangeRequest();
      c.setUnitType(unitType);
      c.setVerb(verb);
      c.setPath(path);
      return c;
   }

   private static StoredAssetApplyRequest applyRequest(String task, String hash,
                                                        String reviewOutcome,
                                                        Boolean acknowledgeIrreversibleDelete,
                                                        StoredAssetChangeRequest... changes)
   {
      StoredAssetApplyRequest req = new StoredAssetApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(hash);
      req.setTaskToken(TaskAuditToken.issue(hash, task));
      req.setReviewOutcome(reviewOutcome);
      req.setAcknowledgeIrreversibleDelete(acknowledgeIrreversibleDelete);
      return req;
   }

   private MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      Audit instance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(instance);
      return audit;
   }

   // -------------------------------------------------------------------------
   // hash / reviewOutcome / acknowledgeIrreversibleDelete gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsPlanHashMismatchOnStaleHash() {
      StoredAssetApplyRequest req = applyRequest("task", "not-the-real-hash", "looks good", null,
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "a"));

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyThrowsOnMissingReviewOutcome() {
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "a");
      String hash = planService.resolve(request(c), user).planHash();
      StoredAssetApplyRequest req = applyRequest("task", hash, "  ", null, c);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"), ex.getMessage());
   }

   @Test void applyThrowsWhenIrreversibleDeleteIsNotAcknowledged() throws Exception {
      folders.add("old-folder");
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_DELETE, "old-folder");
      String hash = planService.resolve(request(c), user).planHash();
      StoredAssetApplyRequest req = applyRequest("task", hash, "looks good", null, c);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("acknowledgeIrreversibleDelete"), ex.getMessage());
      verify(backupService, never()).backup(anyString());
   }

   // -------------------------------------------------------------------------
   // success, per verb
   // -------------------------------------------------------------------------

   @Test void appliesACreateFolderAndReportsApplied() throws Exception {
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "new-folder");
      String hash = planService.resolve(request(c), user).planHash();
      StoredAssetApplyRequest req = applyRequest("task", hash, "looks good", null, c);

      StoredAssetApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("admin-snapshot/ref", result.backupRef());
      assertNull(result.rollbackFailures());
      assertTrue(folders.contains("new-folder"));
      verify(backupService).backup(anyString());
   }

   @Test void appliesAWriteToANewFileAndReportsApplied() throws Exception {
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_WRITE, "new.js");
      c.setContent("console.log(1);");
      String hash = planService.resolve(request(c), user).planHash();
      StoredAssetApplyRequest req = applyRequest("task", hash, "looks good", null, c);

      StoredAssetApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertArrayEquals("console.log(1);".getBytes(StandardCharsets.UTF_8), files.get("new.js"));
      verify(contentSettingsService).updateFolder("new.js");
   }

   @Test void appliesARenameAndReportsApplied() throws Exception {
      files.put("old.js", "x".getBytes(StandardCharsets.UTF_8));
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_RENAME, "old.js");
      c.setNewName("new.js");
      String hash = planService.resolve(request(c), user).planHash();
      StoredAssetApplyRequest req = applyRequest("task", hash, "looks good", null, c);

      StoredAssetApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(files.containsKey("old.js"));
      assertTrue(files.containsKey("new.js"));
      verify(contentSettingsService).onFileRenamed("old.js", "new.js");
   }

   @Test void appliesADeleteOfACompensableFileWithoutRequiringAcknowledgement() throws Exception {
      files.put("a.js", "console.log(1);".getBytes(StandardCharsets.UTF_8));
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_DELETE, "a.js");
      String hash = planService.resolve(request(c), user).planHash();
      // acknowledgeIrreversibleDelete deliberately omitted: a small text file's delete is
      // compensable (a live rollback exists), so the advisory gate must not fire.
      StoredAssetApplyRequest req = applyRequest("task", hash, "looks good", null, c);

      StoredAssetApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(files.containsKey("a.js"));
   }

   @Test void appliesADeleteOfAFolderOnlyWithAcknowledgement() throws Exception {
      folders.add("old-folder");
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_DELETE, "old-folder");
      String hash = planService.resolve(request(c), user).planHash();
      StoredAssetApplyRequest req = applyRequest("task", hash, "looks good", true, c);

      StoredAssetApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(folders.contains("old-folder"));
   }

   // -------------------------------------------------------------------------
   // rollback
   // -------------------------------------------------------------------------

   /**
    * The mid-batch failure here is a returned {@code STATUS_FAILED} verification (rename reports
    * unsuccessful), not a thrown exception -- deliberately, since a thrown failure is ALWAYS
    * unknown-state for the throwing entry itself (see {@code reportsRollbackFailedWhenAnUndo
    * ItselfFails} below), which would never let this path reach a clean {@code STATUS_ROLLED_BACK}.
    */
   @Test void rollsBackAnEarlierVerifiedChangeWhenALaterOneFailsVerification() throws Exception {
      files.put("existing.js", "x".getBytes(StandardCharsets.UTF_8));
      StoredAssetChangeRequest write =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_WRITE, "new.js");
      write.setContent("console.log(1);");
      StoredAssetChangeRequest rename =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_RENAME, "existing.js");
      rename.setNewName("renamed.js");
      String hash = planService.resolve(request(write, rename), user).planHash();
      StoredAssetApplyRequest req = applyRequest("task", hash, "looks good", null, write, rename);

      // Override the fake: the rename call itself reports failure without throwing. `doReturn`,
      // not `when(dataSpace.rename(...))`, because the latter would actually INVOKE the already-
      // stubbed generic `rename` answer as part of setting up the override (mutating the fake's
      // state as a side effect of stubbing it).
      doReturn(false).when(dataSpace).rename("existing.js", "renamed.js");

      StoredAssetApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      // The first (write) change was verified, then undone: the new file must not remain.
      assertFalse(files.containsKey("new.js"));
      // The second (rename) change never took effect.
      assertTrue(files.containsKey("existing.js"));
      assertFalse(files.containsKey("renamed.js"));
   }

   @Test void reportsRollbackFailedWhenAnUndoItselfFails() throws Exception {
      StoredAssetChangeRequest create =
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "new-folder");
      StoredAssetChangeRequest badWrite =
         change(StoredAssetChangeRequest.UNIT_FILE, StoredAssetChangeRequest.VERB_WRITE, "bad.js");
      badWrite.setContent("x");
      String hash = planService.resolve(request(create, badWrite), user).planHash();
      StoredAssetApplyRequest req = applyRequest("task", hash, "looks good", null, create, badWrite);

      doThrow(new IllegalStateException("write boom"))
         .when(dataSpace).withOutputStream(isNull(), eq("bad.js"),
                                           any(DataSpace.OutputStreamOperation.class));
      // The undo of the folder create (a plain delete) itself fails.
      doThrow(new IllegalStateException("undo boom"))
         .when(dataSpace).delete(isNull(), eq("new-folder"));

      StoredAssetApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      // Two failures: "bad.js" is unknown-state (its own apply threw, so it can never be treated
      // as rolled back), and "new-folder" is a genuine rollback failure (its undo itself threw).
      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      List<String> failedProperties = result.rollbackFailures().stream()
         .map(inetsoft.web.admin.ai.RollbackFailure::property).toList();
      assertEquals(List.of("bad.js", "new-folder"), failedProperties);
   }

   // -------------------------------------------------------------------------
   // audit / task-token pinning
   // -------------------------------------------------------------------------

   @Test void auditsThePreviewedTaskEvenWhenApplyTaskDiffers() throws Exception {
      String previewTask = "add a helper script";
      StoredAssetChangeRequest c =
         change(StoredAssetChangeRequest.UNIT_FOLDER, StoredAssetChangeRequest.VERB_CREATE, "new-folder");
      String hash = planService.resolve(request(c), user).planHash();
      String taskToken = TaskAuditToken.issue(hash, previewTask);
      StoredAssetApplyRequest req = applyRequest("unrelated substituted text", hash, "looks good",
                                                 null, c);
      req.setTaskToken(taskToken);

      tool.when(Tool::getHost).thenReturn("test-host");
      Audit auditInstance = mock(Audit.class);
      StoredAssetApplyResult result;

      try(MockedStatic<Audit> audit = mockStatic(Audit.class)) {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      org.mockito.ArgumentCaptor<inetsoft.util.audit.AdminChangeRecord> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.util.audit.AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(user));
      assertEquals(previewTask, captor.getValue().getTaskDescription());
   }
}
