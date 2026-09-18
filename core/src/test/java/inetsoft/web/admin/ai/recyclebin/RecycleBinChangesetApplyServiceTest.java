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
package inetsoft.web.admin.ai.recyclebin;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76672: {@code apply()}'s per-item {@code catch(Exception e)} used to unconditionally add
 * every throwing item to {@code unknownStateFailures}, forcing {@code STATUS_ROLLBACK_FAILED} even
 * when the throw came from a pure, apply-time re-check (mirroring {@code RecycleBinChangePlanService
 * .resolveOne}'s own plan-time checks, per this class's own doc comment) strictly before any
 * mutating call. The fix threads a per-iteration {@code AtomicBoolean mutationEntered} through
 * {@code applyOne} -> {@code applyRestore}/{@code applyPurge}, gating {@code
 * unknownStateFailures.add(...)} on it.
 *
 * <p>Follow-up: that first fix set {@code mutationEntered} in {@code applyRestore} before
 * dispatching into {@code RecycleUtils.restoreSheet}/{@code restoreWSFolder}/{@code
 * restoreRepositoryFolder}, each of which opens with its OWN source lookup that throws strictly
 * before that method's first mutating statement ({@code validatePath}/{@code
 * checkParentFolderExist}, both of which already call {@code addFolder}). {@code applyRestore} now
 * runs the same lookups first, via {@code RecycleBinService.requireRestorableSource}, and only then
 * sets the flag.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class RecycleBinChangesetApplyServiceTest {
   @Mock private RecycleBinService recycleBinService;
   @Mock private RecycleBin recycleBin;
   @Mock private AssetRepository assetRepository;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;

   private RecycleBinChangePlanService planService;
   private RecycleBinChangesetApplyService service;
   private MockedStatic<Tool> tool;

   @BeforeEach
   void setUp() throws Exception {
      planService = new RecycleBinChangePlanService(recycleBinService);
      service = new RecycleBinChangesetApplyService(planService, recycleBinService, recycleBin,
                                                    assetRepository, backupService);

      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   private static RecycleBin.Entry sheetEntry(String path, String originalPath, String name) {
      RecycleBin.Entry entry = new RecycleBin.Entry();
      entry.setPath(path);
      entry.setOriginalPath(originalPath);
      entry.setName(name);
      entry.setType(RepositoryEntry.WORKSHEET);
      entry.setOriginalScope(AssetRepository.GLOBAL_SCOPE);
      entry.setOriginalUser(new IdentityID("admin", "host-org"));
      entry.setTimestamp(new Date());
      return entry;
   }

   private static RecycleBin.Entry repositoryFolderEntry(String path, String originalPath,
                                                          String name)
   {
      RecycleBin.Entry entry = new RecycleBin.Entry();
      entry.setPath(path);
      entry.setOriginalPath(originalPath);
      entry.setName(name);
      entry.setType(RepositoryEntry.FOLDER);
      entry.setOriginalScope(AssetRepository.GLOBAL_SCOPE);
      entry.setOriginalUser(new IdentityID("admin", "host-org"));
      entry.setTimestamp(new Date());
      return entry;
   }

   private static RecycleBinChangeRequest restore(String path) {
      RecycleBinChangeRequest r = new RecycleBinChangeRequest();
      r.setVerb(RecycleBinChangeRequest.VERB_RESTORE);
      r.setPath(path);
      return r;
   }

   private static RecycleBinChangeRequest purge(String path) {
      RecycleBinChangeRequest r = new RecycleBinChangeRequest();
      r.setVerb(RecycleBinChangeRequest.VERB_PURGE);
      r.setPath(path);
      return r;
   }

   private static RecycleBinChangePlanRequest planRequest(String task,
                                                           List<RecycleBinChangeRequest> changes)
   {
      RecycleBinChangePlanRequest req = new RecycleBinChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static RecycleBinApplyRequest applyRequest(String task, String hash,
                                                       RecycleBinChangeRequest... changes)
   {
      RecycleBinApplyRequest req = new RecycleBinApplyRequest();
      req.setTask(task);
      req.setChanges(Arrays.asList(changes));
      req.setPlanHash(hash);
      return req;
   }

   private MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      Audit instance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(instance);
      return audit;
   }

   /** Precomputes the plan hash off a separate, never-throwing {@code RecycleBinService} mock, so
    * doing so does not consume a call from the throwing sequence stubbed on the real {@code
    * recycleBinService} used by {@code service} under test -- mirrors the diagnosis/refuter's own
    * executed-probe technique (01-diagnosis.md section 6 / 02-refute.md "attack surface 3"). */
   private String hashFor(String task, RecycleBin.Entry entry, boolean collides,
                          RecycleBinChangeRequest... changes) throws Exception
   {
      RecycleBinService hashRecycleBinService = mock(RecycleBinService.class);
      lenient().when(hashRecycleBinService.requireEntry(entry.getPath(), user)).thenReturn(entry);
      lenient().when(hashRecycleBinService.wouldCollide(entry)).thenReturn(collides);
      RecycleBinChangePlanService hashPlanService = new RecycleBinChangePlanService(
         hashRecycleBinService);
      return hashPlanService.resolve(planRequest(task, Arrays.asList(changes)), user).planHash();
   }

   // -------------------------------------------------------------------------
   // applyRestore: requireEntry throws at apply time, strictly before any mutation
   // -------------------------------------------------------------------------

   @Test void applyRestoreRollsBackCleanlyWhenApplyTimeRequireEntryThrowsBeforeAnyMutation()
      throws Exception
   {
      RecycleBin.Entry entry = sheetEntry("Recycle Bin/uuid1", "folder1/ws1", "ws1");
      String hash = hashFor("restore ws1", entry, false, restore("Recycle Bin/uuid1"));

      // apply()'s own fresh top-of-method resolve() re-checks requireEntry/wouldCollide once more
      // (call #1) before the loop is even reached; applyRestore's OWN re-check (call #2) is the one
      // that throws -- a concurrent purge/restore of the same entry between preview and apply, the
      // scenario this class's own doc comment names.
      when(recycleBinService.requireEntry("Recycle Bin/uuid1", user))
         .thenReturn(entry)
         .thenThrow(new MissingResourceException(
            "path: no recycle bin entry found at \"Recycle Bin/uuid1\""));
      when(recycleBinService.wouldCollide(entry)).thenReturn(false);

      RecycleBinApplyRequest req = applyRequest("restore ws1", hash, restore("Recycle Bin/uuid1"));
      RecycleBinApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(recycleBin, never()).removeEntry(anyString());
      verifyNoInteractions(assetRepository);
   }

   // -------------------------------------------------------------------------
   // applyPurge: repository-folder branch, RecycleUtils.getRegistry throws before any mutation
   // -------------------------------------------------------------------------

   @Test void applyPurgeRepositoryFolderRollsBackCleanlyWhenGetRegistryThrowsBeforeAnyMutation()
      throws Exception
   {
      RecycleBin.Entry entry = repositoryFolderEntry("Recycle Bin/uuid2", "dashboards/folder1",
         "folder1");
      String hash = hashFor("purge folder1", entry, false, purge("Recycle Bin/uuid2"));

      when(recycleBinService.requireEntry("Recycle Bin/uuid2", user)).thenReturn(entry);

      RecycleBinApplyRequest req = applyRequest("purge folder1", hash, purge("Recycle Bin/uuid2"));
      req.setAcknowledgeIrreversibleDelete(true);
      req.setReviewOutcome("looks good");
      RecycleBinApplyResult result;

      try(MockedStatic<RecycleUtils> recycleUtils = mockStatic(RecycleUtils.class,
         Answers.CALLS_REAL_METHODS))
      {
         recycleUtils.when(() -> RecycleUtils.getRegistry(entry.getPath(), entry.getOriginalUser()))
            .thenThrow(new Exception("simulated registry load failure"));

         try(MockedStatic<Audit> audit = mockAudit()) {
            result = service.apply(req, user);
         }
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(recycleBin, never()).removeEntry(anyString());
   }

   // -------------------------------------------------------------------------
   // correct-behavior guard: a genuine failure AFTER the mutating call has started still forces
   // STATUS_ROLLBACK_FAILED
   // -------------------------------------------------------------------------

   @Test void applyPurgeStillReportsRollbackFailedWhenTheMutatingCallItselfThrows() throws Exception {
      RecycleBin.Entry entry = sheetEntry("Recycle Bin/uuid3", "folder1/ws2", "ws2");
      String hash = hashFor("purge ws2", entry, false, purge("Recycle Bin/uuid3"));

      when(recycleBinService.requireEntry("Recycle Bin/uuid3", user)).thenReturn(entry);
      doThrow(new RuntimeException("simulated storage failure"))
         .when(assetRepository).removeSheet(any(), eq(user), eq(true));

      RecycleBinApplyRequest req = applyRequest("purge ws2", hash, purge("Recycle Bin/uuid3"));
      req.setAcknowledgeIrreversibleDelete(true);
      req.setReviewOutcome("looks good");
      RecycleBinApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream()
         .anyMatch(f -> f.property().endsWith("Recycle Bin/uuid3") &&
                        f.error().contains("state unknown")));
      verify(recycleBin, never()).removeEntry(anyString());
   }

   // -------------------------------------------------------------------------
   // applyRestore: RecycleUtils' OWN pre-mutation lookups, now gated on via
   // RecycleBinService.requireRestorableSource (bug #76672 follow-up)
   // -------------------------------------------------------------------------

   @Test void applyRestoreRollsBackCleanlyWhenTheSourceAssetVanishedBeforeAnyMutation()
      throws Exception
   {
      RecycleBin.Entry entry = sheetEntry("Recycle Bin/uuid4", "folder1/ws3", "ws3");
      String hash = hashFor("restore ws3", entry, false, restore("Recycle Bin/uuid4"));

      when(recycleBinService.requireEntry("Recycle Bin/uuid4", user)).thenReturn(entry);
      when(recycleBinService.wouldCollide(entry)).thenReturn(false);
      // The concurrent-purge race: the trashed asset itself is gone by the time
      // RecycleUtils.restoreSheet's own getSheetEntry lookup would have run -- strictly before
      // validatePath, that method's first mutating statement.
      doThrow(new MissingResourceException(
         "path: the recycled asset at \"folder1/ws3\" could not be found"))
         .when(recycleBinService).requireRestorableSource(entry, user);

      RecycleBinApplyRequest req = applyRequest("restore ws3", hash, restore("Recycle Bin/uuid4"));
      RecycleBinApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(recycleBin, never()).removeEntry(anyString());
      verifyNoInteractions(assetRepository);
   }

   @Test void applyRestoreRepositoryFolderRollsBackCleanlyWhenThePrecheckThrowsBeforeAnyMutation()
      throws Exception
   {
      RecycleBin.Entry entry = repositoryFolderEntry("Recycle Bin/uuid5", "dashboards/folder2",
         "folder2");
      String hash = hashFor("restore folder2", entry, false, restore("Recycle Bin/uuid5"));

      when(recycleBinService.requireEntry("Recycle Bin/uuid5", user)).thenReturn(entry);
      when(recycleBinService.wouldCollide(entry)).thenReturn(false);
      // Mirrors RecycleUtils.restoreRepositoryFolder's opening getRegistry load, which precedes
      // checkParentFolderExist -- the first statement there that mutates.
      doThrow(new Exception("simulated registry load failure"))
         .when(recycleBinService).requireRestorableSource(entry, user);

      RecycleBinApplyRequest req = applyRequest("restore folder2", hash,
                                                restore("Recycle Bin/uuid5"));
      RecycleBinApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(recycleBin, never()).removeEntry(anyString());
   }

   // -------------------------------------------------------------------------
   // correct-behavior guard: once the precheck passes, a throw out of the restore itself still
   // forces STATUS_ROLLBACK_FAILED -- the gate was narrowed, not removed
   // -------------------------------------------------------------------------

   @Test void applyRestoreStillReportsRollbackFailedWhenRestoreSheetItselfThrows() throws Exception {
      RecycleBin.Entry entry = sheetEntry("Recycle Bin/uuid6", "folder1/ws4", "ws4");
      String hash = hashFor("restore ws4", entry, false, restore("Recycle Bin/uuid6"));

      when(recycleBinService.requireEntry("Recycle Bin/uuid6", user)).thenReturn(entry);
      when(recycleBinService.wouldCollide(entry)).thenReturn(false);
      // requireRestorableSource passes (mock default no-op), so mutationEntered is set before the
      // restore call below throws from somewhere at or past validatePath.
      RecycleBinApplyRequest req = applyRequest("restore ws4", hash, restore("Recycle Bin/uuid6"));
      RecycleBinApplyResult result;

      try(MockedStatic<RecycleUtils> recycleUtils = mockStatic(RecycleUtils.class,
         Answers.CALLS_REAL_METHODS))
      {
         recycleUtils.when(() -> RecycleUtils.restoreSheet(entry, false, user, recycleBin))
            .thenThrow(new RuntimeException("simulated storage failure"));

         try(MockedStatic<Audit> audit = mockAudit()) {
            result = service.apply(req, user);
         }
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream()
         .anyMatch(f -> f.property().endsWith("Recycle Bin/uuid6") &&
                        f.error().contains("state unknown")));
      verify(recycleBin, never()).removeEntry(anyString());
   }
}
