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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.*;
import inetsoft.web.admin.schedule.model.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77192: a schedule task must not accept a cloud secret id that the caller does not already
 * manage. An id is accepted only if the stored task already uses it in the same kind of field
 * (for the same server), if it is the id of a configured server location and the path is inside
 * that location, or if the caller is an administrator. Rejected ids are never resolved and
 * nothing is saved.
 */
@Tag("core")
class ScheduleTaskServiceSecretIdTest {
   @BeforeEach
   void setUp() {
      tool = mockStatic(Tool.class, CALLS_REAL_METHODS);
      tool.when(Tool::isCloudSecrets).thenReturn(true);
      sutil = mockStatic(SUtil.class);
      sutil.when(SUtil::isMultiTenant).thenReturn(false);
      sutil.when(SUtil::getServerLocations).thenReturn(List.of());
      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      scheduleService = mock(ScheduleService.class);
      when(scheduleService.checkPermission(any(), eq(ResourceType.SCHEDULE_OPTION), anyString()))
         .thenReturn(true);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      scheduleManager = mock(ScheduleManager.class);
      // the save checks the owner with the security provider (Bug #77405), the task owner is
      // not a site admin
      service = new ScheduleTaskService(mock(AnalyticRepository.class), scheduleManager,
                                        scheduleService, null, mock(SecurityProvider.class),
                                        null, securityEngine);
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      sutil.close();
      tool.close();
   }

   // ── save-to-server paths ─────────────────────────────────────────────────

   @Test
   void rejectsForeignSecretIdOnServerPath() {
      ViewsheetAction action = saveAction("ftp://collector.invalid:2121/out", FOREIGN_ID);

      assertRejected(() -> service.sanitizeAction(action, null, principal, List.of()));
   }

   @Test
   void rejectsForeignSecretIdOnBackupPath() {
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();
      action.setServerPaths(credentialPath("ftp://collector.invalid/backup", FOREIGN_ID));

      assertRejected(() -> service.sanitizeAction(action, null, principal, List.of()));
   }

   @Test
   void rejectsForeignSecretIdThroughSingleActionOverload() {
      // the enterprise public API passes only the replaced action, or none
      ViewsheetAction action = saveAction("ftp://collector.invalid/out", FOREIGN_ID);

      assertRejected(() -> service.sanitizeAction(action, null, principal));
   }

   @Test
   void acceptsUnchangedSecretIdForSameServer() {
      ViewsheetAction original = saveAction("ftp://files.corp.example/out/a", OWN_ID);
      // another action of the stored task, the ids are matched across the whole task
      ViewsheetAction action = saveAction("ftp://FILES.corp.example:21/other/b", OWN_ID);

      service.sanitizeAction(action, null, principal,
                             List.of(new IndividualAssetBackupAction(), original));

      assertEquals(OWN_ID, action.getFilePathInfo(PDF).getSecretId());
      verifyNotResolved(OWN_ID);
   }

   @Test
   void rejectsUnchangedSecretIdWithSubPathThatMovesToAnotherHost() {
      ViewsheetAction original = saveAction("ftp://files.corp.example/out/a", OWN_ID);
      ViewsheetAction action =
         saveAction("ftp://files.corp.example/x@collector.invalid/f", OWN_ID);

      assertRejected(() -> service.sanitizeAction(action, original, principal, List.of(original)));
   }

   @Test
   void rejectsUnchangedSecretIdForAnotherPortOrProtocol() {
      ViewsheetAction original = saveAction("ftp://files.corp.example/out/a", OWN_ID);

      assertRejected(() -> service.sanitizeAction(
         saveAction("ftp://files.corp.example:2121/out/a", OWN_ID), original, principal,
         List.of(original)));
      assertRejected(() -> service.sanitizeAction(
         saveAction("sftp://files.corp.example/out/a", OWN_ID), original, principal,
         List.of(original)));
   }

   // ── configured server locations ─────────────────────────────────────────

   @Test
   void acceptsConfiguredLocationSecretIdInsideTheLocation() {
      configureLocation();
      ViewsheetAction action = saveAction("ftp://files.corp.example/reports/q1/r", LOCATION_ID);

      service.sanitizeAction(action, null, principal, List.of());

      verifyNotResolved(LOCATION_ID);
   }

   @Test
   void rejectsConfiguredLocationSecretIdForHostWithLocationHostAsPrefix() {
      configureLocation();

      assertRejected(() -> service.sanitizeAction(
         saveAction("ftp://files.corp.example.collector.invalid/reports/r", LOCATION_ID), null,
         principal, List.of()));
   }

   @Test
   void rejectsConfiguredLocationSecretIdWithSubPathThatMovesToAnotherHost() {
      configureLocation();

      assertRejected(() -> service.sanitizeAction(
         saveAction("ftp://files.corp.example/reports/x@collector.invalid/f", LOCATION_ID), null,
         principal, List.of()));
   }

   @Test
   void rejectsConfiguredLocationSecretIdOutsideTheLocation() {
      configureLocation();

      for(String path : new String[] {
         "ftp://files.corp.example/reportsX/r", "ftp://files.corp.example/reports/../etc/r",
         "ftp://files.corp.example:2121/reports/r", "collector.invalid/reports/r" })
      {
         assertRejected(() -> service.sanitizeAction(
            saveAction(path, LOCATION_ID), null, principal, List.of()));
      }
   }

   // ── administrators ──────────────────────────────────────────────────────

   @Test
   void siteAdminMayUseAnySecretId() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      ViewsheetAction action = saveAction("ftp://collector.invalid/out", FOREIGN_ID);
      action.setUseCredential(true);
      action.setSecretId(FOREIGN_ID);

      service.sanitizeAction(action, null, principal, List.of());

      verifyNotResolved(FOREIGN_ID);
   }

   @Test
   void orgAdminMayUseAnySecretIdOnlyWithoutMultiTenancy() {
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);

      assertRejected(() -> service.sanitizeAction(
         saveAction("ftp://collector.invalid/out", FOREIGN_ID), null, principal, List.of()));

      sutil.when(SUtil::isMultiTenant).thenReturn(false);
      service.sanitizeAction(
         saveAction("ftp://collector.invalid/out", FOREIGN_ID), null, principal, List.of());
   }

   @Test
   void dataSourceWriteDoesNotAllowSecretId() throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);

      assertRejected(() -> service.sanitizeAction(
         saveAction("ftp://collector.invalid/out", FOREIGN_ID), null, principal, List.of()));
   }

   // ── email attachment password ───────────────────────────────────────────

   @Test
   void rejectsForeignSecretIdOnZipPassword() {
      ViewsheetAction action = zipAction(FOREIGN_ID);

      assertRejected(() -> service.sanitizeAction(action, null, principal, List.of()));
   }

   @Test
   void acceptsUnchangedZipPasswordSecretId() {
      ViewsheetAction original = zipAction(OWN_ID);
      ViewsheetAction action = zipAction(OWN_ID);

      service.sanitizeAction(action, original, principal, List.of(original));

      assertEquals(OWN_ID, action.getSecretId());
   }

   @Test
   void secretIdsAreNotMatchedAcrossFieldKinds() {
      ViewsheetAction ftpOriginal = saveAction("ftp://files.corp.example/out/a", OWN_ID);
      ViewsheetAction zipOriginal = zipAction(OWN_ID);

      assertRejected(() -> service.sanitizeAction(
         zipAction(OWN_ID), ftpOriginal, principal, List.of(ftpOriginal)));
      assertRejected(() -> service.sanitizeAction(
         saveAction("ftp://files.corp.example/out/a", OWN_ID), zipOriginal, principal,
         List.of(zipOriginal)));
   }

   // ── local secrets and whole-task paths ──────────────────────────────────

   @Test
   void localSecretsModeIsUnaffected() {
      tool.when(Tool::isCloudSecrets).thenReturn(false);
      ViewsheetAction action = saveAction("ftp://collector.invalid/out", FOREIGN_ID);

      service.sanitizeAction(action, null, principal, List.of());

      assertEquals(FOREIGN_ID, action.getFilePathInfo(PDF).getSecretId());
      verify(securityEngine, never()).isSecurityEnabled();
   }

   @Test
   void saveTaskWithForeignSecretIdSavesNothing() throws Exception {
      ScheduleTask stored = new ScheduleTask("task1");
      // the caller owns the task, so it may edit it
      stored.setOwner(new IdentityID("alice", "orga"));
      stored.addAction(saveAction("ftp://files.corp.example/out/a", OWN_ID));
      when(scheduleManager.getScheduleTask("task1")).thenReturn(stored);
      when(scheduleService.updateTaskName(any(), any(), any(), any())).thenReturn("task1");
      when(scheduleService.getActionFromModel(any(), any(), any(), any(), any()))
         .thenReturn(saveAction("ftp://collector.invalid/out", FOREIGN_ID));
      ScheduleTaskEditorModel model = ScheduleTaskEditorModel.builder()
         .taskName("task1")
         .oldTaskName("task1")
         .options(mock(TaskOptionsPaneModel.class))
         .addActions(mock(ScheduleActionModel.class))
         .build();

      assertRejected(() -> service.saveTask(model, "http://host/", principal, false));

      verify(scheduleService, never()).saveTask(any(), any(), any());
      assertEquals(OWN_ID,
                   ((ViewsheetAction) stored.getAction(0)).getFilePathInfo(PDF)
                      .getSecretId());
   }

   @Test
   void importOfTaskWithForeignSecretIdIsRejected() throws Exception {
      ScheduleTask imported = new ScheduleTask("imported");
      imported.addAction(saveAction("ftp://collector.invalid/out", FOREIGN_ID));
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(request.getSession(true)).thenReturn(session);
      when(session.getAttribute(ImportTaskController.INFO_ATTR))
         .thenReturn(new ArrayList<>(List.of(imported)));
      // allow the scheduler permission so the secret id check is what refuses the task
      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER), anyString(),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);
      ImportTaskController controller = new ImportTaskController(
         scheduleManager, mock(ScheduleTaskFolderService.class), repository, securityEngine);

      ImportTaskResponse response = controller.importScheduleTask(
         List.of(imported.getTaskId()), request, true, "http://host/", principal);

      assertEquals(List.of(imported.getTaskId()), response.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any(Principal.class));
      verifyNotResolved(FOREIGN_ID);
   }

   @Test
   void reimportOfTaskWithUnchangedSecretIdIsAccepted() throws Exception {
      ScheduleTask stored = new ScheduleTask("imported");
      stored.addAction(saveAction("ftp://files.corp.example/out/a", OWN_ID));
      ScheduleTask imported = new ScheduleTask("imported");
      imported.addAction(saveAction("ftp://files.corp.example/out/a", OWN_ID));
      // the caller's own task, the import only allows an owner the caller may act as
      imported.setOwner(new IdentityID("alice", "orga"));
      when(orgManager.getCurrentOrgID(principal)).thenReturn("orga");
      when(scheduleManager.getScheduleTask(imported.getTaskId())).thenReturn(stored);
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(request.getSession(true)).thenReturn(session);
      when(session.getAttribute(ImportTaskController.INFO_ATTR))
         .thenReturn(new ArrayList<>(List.of(imported)));
      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER), anyString(),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);
      ImportTaskController controller = new ImportTaskController(
         scheduleManager, mock(ScheduleTaskFolderService.class), repository, securityEngine);

      ImportTaskResponse response = controller.importScheduleTask(
         List.of(imported.getTaskId()), request, true, "http://host/", principal);

      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      verify(scheduleManager).setScheduleTask(imported.getTaskId(), imported, principal);
   }

   private void assertRejected(org.junit.jupiter.api.function.Executable executable) {
      MessageException ex = assertThrows(MessageException.class, executable);
      assertEquals(Catalog.getCatalog().getString("em.schedule.secretIdNotAllowed"),
                   ex.getMessage());
      assertTrue(ex.getMessage().contains("Secret ID"), ex.getMessage());
      verifyNotResolved(FOREIGN_ID);
      verifyNotResolved(OWN_ID);
      verifyNotResolved(LOCATION_ID);
   }

   private void verifyNotResolved(String secretId) {
      tool.verify(() -> Tool.loadCredentials(secretId), never());
      tool.verify(() -> Tool.decryptPassword(eq(secretId), anyBoolean()), never());
   }

   private void configureLocation() {
      ServerPathInfoModel model = ServerPathInfoModel.builder()
         .path("ftp://files.corp.example/reports")
         .secretId(LOCATION_ID)
         .useCredential(true)
         .ftp(true)
         .build();
      sutil.when(SUtil::getServerLocations).thenReturn(List.of(
         ServerLocation.builder().path(model.path()).label("Reports").pathInfoModel(model)
            .build()));
   }

   private static ViewsheetAction saveAction(String path, String secretId) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("vs1");
      action.setFilePath(PDF, credentialPath(path, secretId));
      return action;
   }

   private static ViewsheetAction zipAction(String secretId) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("vs1");
      action.setEmails("someone@example.invalid");
      action.setCompressFile(true);
      action.setUseCredential(true);
      action.setSecretId(secretId);
      return action;
   }

   private static ServerPathInfo credentialPath(String path, String secretId) {
      return new ServerPathInfo(ServerPathInfoModel.builder()
                                   .path(path)
                                   .ftp(true)
                                   .useCredential(true)
                                   .secretId(secretId)
                                   .build());
   }

   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
   private static final String FOREIGN_ID = "org-b-secret";
   private static final String OWN_ID = "own-secret";
   private static final String LOCATION_ID = "location-secret";

   private MockedStatic<Tool> tool;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private OrganizationManager orgManager;
   private ScheduleService scheduleService;
   private ScheduleManager scheduleManager;
   private SecurityEngine securityEngine;
   private ScheduleTaskService service;
   private XPrincipal principal;
}
