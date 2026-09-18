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
package inetsoft.web.admin.ai.shapes;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.RollbackFailure;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.dataspace.DataSpaceContentSettingsService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-design.md section 2.3 (per-verb apply/rollback), the unconditional Tier-2 backup, and the
 * asymmetric cache-clear (upload clears explicitly; delete relies on {@code deleteDataSpaceNode}'s
 * own internal clear). {@link DataSpace}/{@link DataSpaceContentSettingsService} are backed by a
 * small in-memory fake, matching {@code StoredAssetChangesetApplyServiceTest}'s own approach.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ShapeChangesetApplyServiceTest {
   private static final String GLOBAL_DIR = "portal/shapes";
   private static final String ORG_DIR = "portal/myorg/shapes";

   @Mock private DataSpace dataSpace;
   @Mock private SecurityEngine securityEngine;
   @Mock private DataSpaceContentSettingsService contentSettingsService;
   @Mock private AdminBackupService backupService;
   @Mock private OrganizationManager orgManager;
   @Mock private Principal user;

   private final Map<String, byte[]> files = new HashMap<>();
   private ShapeChangePlanService planService;
   private ShapeChangesetApplyService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ImageShapes> imageShapes;
   private MockedStatic<Tool> tool;

   @BeforeEach
   void setUp() throws Exception {
      planService = new ShapeChangePlanService(dataSpace, securityEngine);
      service = new ShapeChangesetApplyService(planService, contentSettingsService, dataSpace,
         backupService);

      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      imageShapes = mockStatic(ImageShapes.class, withSettings().lenient());
      imageShapes.when(ImageShapes::getGlobalShapesDirectory).thenReturn(GLOBAL_DIR);
      imageShapes.when(ImageShapes::getShapesDirectory).thenReturn(ORG_DIR);

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

      lenient().when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.EM_COMPONENT), eq("settings/presentation/settings"),
         eq(ResourceAction.ACCESS))).thenReturn(true);
      lenient().when(securityEngine.checkPermission(
         eq(user), eq(ResourceType.EM_COMPONENT), eq("settings/presentation/org-settings"),
         eq(ResourceAction.ACCESS))).thenReturn(true);

      wireDataSpaceFake();
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      imageShapes.close();
      tool.close();
   }

   private void wireDataSpaceFake() throws Exception {
      lenient().when(dataSpace.exists(isNull(), anyString()))
         .thenAnswer(inv -> files.containsKey((String) inv.getArgument(1)));
      lenient().when(dataSpace.getFileLength(isNull(), anyString()))
         .thenAnswer(inv -> (long) files.get((String) inv.getArgument(1)).length);
      lenient().when(dataSpace.getInputStream(isNull(), anyString()))
         .thenAnswer(inv -> new ByteArrayInputStream(files.get((String) inv.getArgument(1))));
      lenient().doAnswer(inv -> {
         String dir = inv.getArgument(0);
         String name = inv.getArgument(1);
         DataSpace.OutputStreamOperation op = inv.getArgument(2);
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         op.accept(out);
         files.put(dir + "/" + name, out.toByteArray());
         return null;
      }).when(dataSpace).withOutputStream(anyString(), anyString(),
         any(DataSpace.OutputStreamOperation.class));
      lenient().when(dataSpace.delete(isNull(), anyString()))
         .thenAnswer(inv -> files.remove((String) inv.getArgument(1)) != null);
      lenient().doAnswer(inv -> {
         String path = inv.getArgument(0);
         files.remove(path);
         return null;
      }).when(contentSettingsService).deleteDataSpaceNode(anyString(), eq(false));
   }

   private static ShapeChangeRequest change(String verb, String scope, String name) {
      ShapeChangeRequest c = new ShapeChangeRequest();
      c.setVerb(verb);
      c.setScope(scope);
      c.setName(name);
      return c;
   }

   private static ShapeChangePlanRequest request(ShapeChangeRequest... changes) {
      ShapeChangePlanRequest req = new ShapeChangePlanRequest();
      req.setTask("test task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static ShapeApplyRequest applyRequest(String task, String hash, String reviewOutcome,
                                                 ShapeChangeRequest... changes)
   {
      ShapeApplyRequest req = new ShapeApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(hash);
      req.setTaskToken(TaskAuditToken.issue(hash, task));
      req.setReviewOutcome(reviewOutcome);
      return req;
   }

   private static String base64(String text) {
      return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
   }

   private MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      Audit instance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(instance);
      return audit;
   }

   // -------------------------------------------------------------------------
   // hash / reviewOutcome gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsPlanHashMismatchOnStaleHash() {
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      c.setContent(base64("<svg/>"));
      ShapeApplyRequest req = applyRequest("task", "not-the-real-hash", "looks good", c);

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyThrowsOnMissingReviewOutcome() throws Exception {
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      c.setContent(base64("<svg/>"));
      String hash = planService.resolve(request(c), user).planHash();
      ShapeApplyRequest req = applyRequest("task", hash, "  ", c);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"), ex.getMessage());
   }

   // -------------------------------------------------------------------------
   // success, per verb
   // -------------------------------------------------------------------------

   @Test void appliesAnUploadOfANewShapeAndClearsTheGlobalCache() throws Exception {
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "global", "new.svg");
      c.setContent(base64("<svg/>"));
      String hash = planService.resolve(request(c), user).planHash();
      ShapeApplyRequest req = applyRequest("task", hash, "looks good", c);

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("admin-snapshot/ref", result.backupRef());
      assertArrayEquals("<svg/>".getBytes(StandardCharsets.UTF_8), files.get(GLOBAL_DIR + "/new.svg"));
      // A global upload must invalidate every organization's cache (each falls back to global).
      imageShapes.verify(ImageShapes::clearAllShapes);
      imageShapes.verify(ImageShapes::clearShapes, never());
      verify(backupService).backup(anyString());
   }

   @Test void appliesAnOverwriteUploadInOrganizationScopeAndClearsOnlyThatOrgsCache()
      throws Exception
   {
      files.put(ORG_DIR + "/existing.svg", "<old/>".getBytes(StandardCharsets.UTF_8));
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "organization", "existing.svg");
      c.setContent(base64("<new/>"));
      String hash = planService.resolve(request(c), user).planHash();
      ShapeApplyRequest req = applyRequest("task", hash, "looks good", c);

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertArrayEquals("<new/>".getBytes(StandardCharsets.UTF_8), files.get(ORG_DIR + "/existing.svg"));
      imageShapes.verify(ImageShapes::clearShapes);
      imageShapes.verify(ImageShapes::clearAllShapes, never());
   }

   @Test void appliesADeleteAndReportsAppliedWithoutAnExtraCacheClearCall() throws Exception {
      files.put(GLOBAL_DIR + "/old.svg", "<svg/>".getBytes(StandardCharsets.UTF_8));
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_DELETE, "global", "old.svg");
      String hash = planService.resolve(request(c), user).planHash();
      ShapeApplyRequest req = applyRequest("task", hash, "looks good", c);

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(files.containsKey(GLOBAL_DIR + "/old.svg"));
      verify(contentSettingsService).deleteDataSpaceNode(GLOBAL_DIR + "/old.svg", false);
      // deleteDataSpaceNode already clears the cache internally -- applyDelete must not also call
      // ImageShapes.clear*() itself (the asymmetry vs. applyUpload is deliberate).
      imageShapes.verify(ImageShapes::clearAllShapes, never());
      imageShapes.verify(ImageShapes::clearShapes, never());
   }

   // -------------------------------------------------------------------------
   // rollback
   // -------------------------------------------------------------------------

   @Test void rollsBackAnUploadWhenALaterDeleteFailsVerification() throws Exception {
      ShapeChangeRequest upload = change(ShapeChangeRequest.VERB_UPLOAD, "global", "new.svg");
      upload.setContent(base64("<svg/>"));
      files.put(GLOBAL_DIR + "/gone.svg", "<svg/>".getBytes(StandardCharsets.UTF_8));
      ShapeChangeRequest delete = change(ShapeChangeRequest.VERB_DELETE, "global", "gone.svg");
      String hash = planService.resolve(request(upload, delete), user).planHash();
      ShapeApplyRequest req = applyRequest("task", hash, "looks good", upload, delete);

      // The delete's own deleteDataSpaceNode call reports success but the file is still present --
      // forces a STATUS_FAILED verification on the second entry without throwing.
      doAnswer(inv -> null).when(contentSettingsService)
         .deleteDataSpaceNode(GLOBAL_DIR + "/gone.svg", false);

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      // The first (upload) change was verified, then undone: the new file must not remain.
      assertFalse(files.containsKey(GLOBAL_DIR + "/new.svg"));
      // The second (delete) change never actually removed the file (fake left it in place).
      assertTrue(files.containsKey(GLOBAL_DIR + "/gone.svg"));
   }

   @Test void rollsBackAnOverwriteByRestoringThePriorBytes() throws Exception {
      files.put(GLOBAL_DIR + "/a.svg", "<old/>".getBytes(StandardCharsets.UTF_8));
      ShapeChangeRequest upload = change(ShapeChangeRequest.VERB_UPLOAD, "global", "a.svg");
      upload.setContent(base64("<new/>"));
      ShapeChangeRequest badDelete = change(ShapeChangeRequest.VERB_DELETE, "global", "b.svg");
      files.put(GLOBAL_DIR + "/b.svg", "<b/>".getBytes(StandardCharsets.UTF_8));
      String hash = planService.resolve(request(upload, badDelete), user).planHash();
      ShapeApplyRequest req = applyRequest("task", hash, "looks good", upload, badDelete);

      // The delete's own deleteDataSpaceNode call reports success without throwing but the file is
      // still present -- a verification failure (not a thrown exception), so this entry is never
      // added to `undoable` and the earlier upload's own rollback can complete cleanly.
      doAnswer(inv -> null).when(contentSettingsService)
         .deleteDataSpaceNode(GLOBAL_DIR + "/b.svg", false);

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertArrayEquals("<old/>".getBytes(StandardCharsets.UTF_8), files.get(GLOBAL_DIR + "/a.svg"));
   }

   @Test void reportsRollbackFailedWhenAnUndoItselfFails() throws Exception {
      ShapeChangeRequest upload = change(ShapeChangeRequest.VERB_UPLOAD, "global", "new.svg");
      upload.setContent(base64("<svg/>"));
      ShapeChangeRequest badUpload = change(ShapeChangeRequest.VERB_UPLOAD, "global", "bad.svg");
      badUpload.setContent(base64("x"));
      String hash = planService.resolve(request(upload, badUpload), user).planHash();
      ShapeApplyRequest req = applyRequest("task", hash, "looks good", upload, badUpload);

      doThrow(new IllegalStateException("write boom"))
         .when(dataSpace).withOutputStream(eq(GLOBAL_DIR), eq("bad.svg"),
                                           any(DataSpace.OutputStreamOperation.class));
      // The undo of the first upload (a plain delete, since it had no prior bytes) itself fails.
      doThrow(new IllegalStateException("undo boom"))
         .when(dataSpace).delete(isNull(), eq(GLOBAL_DIR + "/new.svg"));

      ApplyResult result;

      try(MockedStatic<Audit> audit = mockAudit()) {
         result = service.apply(req, user);
      }

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      List<String> failedProperties = result.rollbackFailures().stream()
         .map(RollbackFailure::property).toList();
      assertEquals(List.of(GLOBAL_DIR + "/bad.svg", GLOBAL_DIR + "/new.svg"), failedProperties);
   }

   // -------------------------------------------------------------------------
   // audit / task-token pinning
   // -------------------------------------------------------------------------

   @Test void auditsThePreviewedTaskEvenWhenApplyTaskDiffers() throws Exception {
      String previewTask = "add a custom triangle shape";
      ShapeChangeRequest c = change(ShapeChangeRequest.VERB_UPLOAD, "global", "new.svg");
      c.setContent(base64("<svg/>"));
      String hash = planService.resolve(request(c), user).planHash();
      String taskToken = TaskAuditToken.issue(hash, previewTask);
      ShapeApplyRequest req = applyRequest("unrelated substituted text", hash, "looks good", c);
      req.setTaskToken(taskToken);

      tool.when(Tool::getHost).thenReturn("test-host");
      Audit auditInstance = mock(Audit.class);
      ApplyResult result;

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
