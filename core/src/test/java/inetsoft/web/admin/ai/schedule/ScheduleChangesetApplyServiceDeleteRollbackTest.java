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
package inetsoft.web.admin.ai.schedule;

import inetsoft.report.internal.Util;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.ScheduleConditionService;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78420, the rollback of a delete re-creates the task as it was stored: every stored
 * password, the email secret id, the linkURI, the folder and the permission of the task, none of
 * which the public-API model of the task carries unmasked. Runs the real apply, plan and gateway
 * services and the real core task, only the storage and the security engine are mocked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleChangesetApplyServiceDeleteRollbackTest {
   @BeforeEach
   void setUp() throws Exception {
      repository = mock(AnalyticRepository.class, withSettings().lenient());
      scheduleManager = mock(ScheduleManager.class, withSettings().lenient());
      securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      AdminBackupService backupService = mock(AdminBackupService.class);
      when(backupService.backup(anyString())).thenReturn("snap");
      when(repository.checkPermission(any(), any(ResourceType.class), anyString(), any()))
         .thenReturn(true);
      when(repository.checkPermission(any(), any(ResourceType.class), any(IdentityID.class), any()))
         .thenReturn(true);

      user = mock(Principal.class, withSettings().lenient());
      when(user.getName()).thenReturn(OWNER.convertToKey());

      AdminScheduleGateway gateway = new AdminScheduleGateway(
         repository, scheduleManager, mock(ScheduleService.class, withSettings().lenient()),
         mock(ScheduleConditionService.class), mock(ScheduleTaskService.class), securityEngine);
      planService = new ScheduleChangePlanService(gateway, scheduleManager);
      service = new ScheduleChangesetApplyService(planService, gateway, scheduleManager,
                                                  backupService);

      orgStatic = mockStatic(OrganizationManager.class);
      sutil = mockStatic(SUtil.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
      audit = mockStatic(Audit.class);
      OrganizationManager org = mock(OrganizationManager.class, withSettings().lenient());
      when(org.isSiteAdmin(any(Principal.class))).thenReturn(true);
      when(org.getCurrentOrgID(any(Principal.class))).thenReturn(ORG);
      when(org.getCurrentOrgID()).thenReturn(ORG);
      orgStatic.when(OrganizationManager::getInstance).thenReturn(org);
      sutil.when(() -> SUtil.getIdentity(any(IdentityID.class), anyInt())).thenReturn(null);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
   }

   @AfterEach
   void tearDown() {
      orgStatic.close();
      sutil.close();
      audit.close();
   }

   @Test
   void rollbackOfDeleteRestoresTheStoredTask() throws Exception {
      ScheduleTask stored = new ScheduleTask();
      stored.setName("t1");
      stored.setOwner(OWNER);
      stored.setEnabled(true);
      stored.setPath("/Reports");
      stored.addCondition(TimeCondition.atDayOfMonth(1, 9, 0, 0));

      // save to an FTP server, and email the file in a zip with a password
      ViewsheetAction zipped = viewsheetAction();
      zipped.setFilePath(PDF, new ServerPathInfo("ftp://ftp.example.com/out", "ftpuser",
                                                 "ftpSecret"));
      zipped.setPassword("zipSecret");
      zipped.setLinkURI("http://stylebi.example.com/");
      stored.addAction(zipped);

      // email the file in a zip whose password is a secret
      ViewsheetAction secret = viewsheetAction();
      secret.setUseCredential(true);
      secret.setSecretId("zip-secret-id");
      stored.addAction(secret);

      IndividualAssetBackupAction backup = new IndividualAssetBackupAction();
      backup.setServerPaths(new ServerPathInfo("ftp://ftp.example.com/bk", "bkuser", "bkSecret"));
      stored.addAction(backup);

      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.READ,
                               Set.of(new Permission.PermissionIdentity(OWNER.name, ORG)));
      permission.setGroupGrants(ResourceAction.READ,
                                Set.of(new Permission.PermissionIdentity("Sales", ORG)));

      Recreated recreated = applyDeleteThenFailingDelete(stored, permission);

      assertNotNull(recreated.task.get(), "t1 was re-created");
      ScheduleTask task = recreated.task.get();
      ViewsheetAction rzipped = (ViewsheetAction) task.getAction(0);
      ViewsheetAction rsecret = (ViewsheetAction) task.getAction(1);
      IndividualAssetBackupAction rbackup = (IndividualAssetBackupAction) task.getAction(2);
      AssetEntry parent = recreated.parent.get();

      // the stored values, never Util.PLACEHOLDER_PASSWORD or null
      assertAll(
         () -> assertEquals("ftpSecret", rzipped.getFilePathInfo(PDF).getPassword(),
                            "save to server password"),
         () -> assertEquals("bkSecret", rbackup.getServerPath().getPassword(),
                            "backup server path password"),
         () -> assertEquals("zipSecret", rzipped.getPassword(), "email zip password"),
         () -> assertTrue(rsecret.isUseCredential(), "email zip useCredential"),
         () -> assertEquals("zip-secret-id", rsecret.getSecretId(), "email zip secret id"),
         () -> assertEquals("http://stylebi.example.com/", rzipped.getLinkURI(), "linkURI"),
         () -> assertNotNull(parent, "folder entry"),
         () -> assertEquals("/Reports", parent == null ? null : parent.getPath(), "folder"),
         () -> assertNotEquals(Util.PLACEHOLDER_PASSWORD, rzipped.getFilePathInfo(PDF).getPassword()),
         () -> assertNotEquals(Util.PLACEHOLDER_PASSWORD, rbackup.getServerPath().getPassword()));

      // the permission of the task is written back after the re-create granted the owner
      InOrder order = inOrder(scheduleManager, securityEngine);
      order.verify(scheduleManager).setScheduleTask(eq(T1), any(ScheduleTask.class),
         nullable(AssetEntry.class), nullable(Principal.class), nullable(ScheduleTask.class));
      ArgumentCaptor<Permission> restored = ArgumentCaptor.forClass(Permission.class);
      order.verify(securityEngine).setPermission(eq(ResourceType.SCHEDULE_TASK), eq(T1),
                                                 restored.capture());
      assertEquals(Set.of(new Permission.PermissionIdentity("Sales", ORG)),
                   restored.getValue().getGroupGrants(ResourceAction.READ), "group grant");
      assertEquals(Set.of(new Permission.PermissionIdentity(OWNER.name, ORG)),
                   restored.getValue().getUserGrants(ResourceAction.READ), "owner grant");
   }

   @Test
   void rollbackOfDeleteKeepsARootTaskWithoutPermissionAsIs() throws Exception {
      ScheduleTask stored = new ScheduleTask();
      stored.setName("t1");
      stored.setOwner(OWNER);
      stored.setEnabled(true);
      stored.setPath("/");
      stored.addCondition(TimeCondition.atDayOfMonth(1, 9, 0, 0));
      stored.addAction(viewsheetAction());

      Recreated recreated = applyDeleteThenFailingDelete(stored, null);

      assertNotNull(recreated.task.get(), "t1 was re-created");
      assertNull(recreated.parent.get(), "a task in the root folder is re-created in it");
      // nothing to write back, the owner grant of the re-create stays
      verify(securityEngine, never()).setPermission(eq(ResourceType.SCHEDULE_TASK), eq(T1),
                                                    any(Permission.class));
   }

   @Test
   void rollbackOfDeleteRestoresEverySaveToServerFormatInANestedFolder() throws Exception {
      ScheduleTask stored = new ScheduleTask();
      stored.setName("t1");
      stored.setOwner(OWNER);
      stored.setEnabled(true);
      stored.setPath("/Reports/Monthly");
      stored.addCondition(TimeCondition.atDayOfMonth(1, 9, 0, 0));

      // save each format to a different server: SFTP, FTP and an FTP login from a secret
      ViewsheetAction formats = viewsheetAction();
      formats.setFilePath(PDF, new ServerPathInfo("sftp://sftp.example.com/pdf", "sftpuser",
                                                  "sftpSecret"));
      formats.setFilePath(EXCEL, new ServerPathInfo("ftp://ftp.example.com/xls", "ftpuser",
                                                    "ftpSecret"));
      ServerPathInfo fromSecret = new ServerPathInfo("ftp://ftp.example.com/ppt");
      fromSecret.setUseCredential(true);
      fromSecret.setSecretId("ppt-secret-id");
      formats.setFilePath(POWERPOINT, fromSecret);
      stored.addAction(formats);

      IndividualAssetBackupAction backup = new IndividualAssetBackupAction();
      backup.setServerPaths(new ServerPathInfo("sftp://sftp.example.com/bk", "bkuser",
                                               "bkSecret"));
      stored.addAction(backup);

      Permission permission = new Permission();
      permission.setUserGrants(ResourceAction.WRITE,
                               Set.of(new Permission.PermissionIdentity("editor", ORG)));
      permission.setRoleGrants(ResourceAction.READ,
                               Set.of(new Permission.PermissionIdentity("Viewer", ORG)));

      Recreated recreated = applyDeleteThenFailingDelete(stored, permission);

      ScheduleTask task = recreated.task.get();
      assertNotNull(task, "t1 was re-created");
      ViewsheetAction rformats = (ViewsheetAction) task.getAction(0);
      IndividualAssetBackupAction rbackup = (IndividualAssetBackupAction) task.getAction(1);
      ServerPathInfo pdf = rformats.getFilePathInfo(PDF);
      ServerPathInfo excel = rformats.getFilePathInfo(EXCEL);
      ServerPathInfo ppt = rformats.getFilePathInfo(POWERPOINT);
      AssetEntry parent = recreated.parent.get();

      assertAll(
         () -> assertEquals(3, rformats.getSaveFormats().length, "save formats"),
         () -> assertEquals("sftp://sftp.example.com/pdf", pdf.getPath(), "pdf path"),
         () -> assertEquals("sftpuser", pdf.getUsername(), "pdf user"),
         () -> assertEquals("sftpSecret", pdf.getPassword(), "pdf sftp password"),
         () -> assertEquals("ftp://ftp.example.com/xls", excel.getPath(), "excel path"),
         () -> assertEquals("ftpSecret", excel.getPassword(), "excel ftp password"),
         () -> assertTrue(ppt.isUseCredential(), "powerpoint useCredential"),
         () -> assertEquals("ppt-secret-id", ppt.getSecretId(), "powerpoint secret id"),
         () -> assertEquals("bkSecret", rbackup.getServerPath().getPassword(),
                            "backup sftp password"),
         () -> assertEquals("/Reports/Monthly", parent == null ? null : parent.getPath(),
                            "nested folder"),
         () -> assertEquals(ORG, parent == null ? null : parent.getOrgID(), "folder org"));

      // the grants captured before the delete are the ones written back
      ArgumentCaptor<Permission> restored = ArgumentCaptor.forClass(Permission.class);
      verify(securityEngine).setPermission(eq(ResourceType.SCHEDULE_TASK), eq(T1),
                                           restored.capture());
      assertEquals(Set.of(new Permission.PermissionIdentity("editor", ORG)),
                   restored.getValue().getUserGrants(ResourceAction.WRITE), "user grant");
      assertEquals(Set.of(new Permission.PermissionIdentity("Viewer", ORG)),
                   restored.getValue().getRoleGrants(ResourceAction.READ), "role grant");
   }

   /**
    * Applies [delete t1, delete t2], t2 never disappears, so its delete fails to verify and the
    * delete of t1 is rolled back.
    */
   private Recreated applyDeleteThenFailingDelete(ScheduleTask stored, Permission permission)
      throws Exception
   {
      assertEquals(T1, stored.getTaskId());
      ScheduleTask other = new ScheduleTask();
      other.setName("t2");
      other.setOwner(OWNER);
      String t2 = other.getTaskId();

      Recreated recreated = new Recreated();
      AtomicBoolean deleted = new AtomicBoolean(false);
      when(scheduleManager.getScheduleTask(T1)).thenAnswer(i -> deleted.get() ? null :
         (recreated.task.get() != null ? recreated.task.get() : stored));
      when(repository.getScheduleTask(T1)).thenAnswer(i -> deleted.get() ? null : stored);
      when(scheduleManager.getScheduleTask(t2)).thenReturn(other);
      when(repository.getScheduleTask(t2)).thenReturn(other);
      when(securityEngine.getPermission(ResourceType.SCHEDULE_TASK, T1))
         .thenAnswer(i -> deleted.get() || permission == null ? null : permission.clone());
      doAnswer(i -> {
         deleted.set(true);
         return null;
      }).when(repository).removeScheduleTask(any(), eq(T1));
      // the save in place of the deleted task, and the plain save the re-create used before
      doAnswer(i -> {
         recreated.task.set(i.getArgument(1));
         recreated.parent.set(i.getArgument(2));
         deleted.set(false);
         return null;
      }).when(scheduleManager).setScheduleTask(eq(T1), any(ScheduleTask.class),
         nullable(AssetEntry.class), nullable(Principal.class), nullable(ScheduleTask.class));
      doAnswer(i -> {
         recreated.task.set(i.getArgument(1));
         deleted.set(false);
         return null;
      }).when(scheduleManager).setScheduleTask(eq(T1), any(ScheduleTask.class),
         nullable(Principal.class));

      ScheduleChangeRequest del1 = new ScheduleChangeRequest();
      del1.setVerb(ScheduleChangeRequest.VERB_DELETE);
      del1.setTaskId(T1);
      ScheduleChangeRequest del2 = new ScheduleChangeRequest();
      del2.setVerb(ScheduleChangeRequest.VERB_DELETE);
      del2.setTaskId(t2);
      ScheduleChangePlanRequest probe = new ScheduleChangePlanRequest();
      probe.setTask("delete two");
      probe.setChanges(List.of(del1, del2));
      ResolvedPlan plan = planService.resolve(probe, user);
      ScheduleApplyRequest req = new ScheduleApplyRequest();
      req.setTask("delete two");
      req.setChanges(List.of(del1, del2));
      req.setPlanHash(plan.planHash());
      req.setTaskToken(plan.taskToken());
      req.setReviewOutcome("approved");

      ApplyResult result = service.apply(req, user, null);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertFalse(deleted.get(), "t1 exists after the rollback");
      return recreated;
   }

   private static ViewsheetAction viewsheetAction() {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^" + ORG);
      action.setBookmarks(new String[]{ "(Home)" });
      action.setBookmarkUsers(new IdentityID[]{ OWNER });
      action.setBookmarkTypes(new int[]{ 0 });
      action.setEmails("a@example.com");
      action.setFileFormat("PDF");
      action.setCompressFile(true);
      return action;
   }

   private static final class Recreated {
      final AtomicReference<ScheduleTask> task = new AtomicReference<>();
      final AtomicReference<AssetEntry> parent = new AtomicReference<>();
   }

   private static final String ORG = "host-org";
   private static final IdentityID OWNER = new IdentityID("admin", ORG);
   private static final String T1 = OWNER.convertToKey() + ":t1";
   private static final int PDF =
      inetsoft.web.api.schedule.ViewsheetAction.Format.PDF.getTypeValue();
   private static final int EXCEL =
      inetsoft.web.api.schedule.ViewsheetAction.Format.EXCEL.getTypeValue();
   private static final int POWERPOINT =
      inetsoft.web.api.schedule.ViewsheetAction.Format.POWERPOINT.getTypeValue();
   private AnalyticRepository repository;
   private ScheduleManager scheduleManager;
   private SecurityEngine securityEngine;
   private Principal user;
   private ScheduleChangePlanService planService;
   private ScheduleChangesetApplyService service;
   private MockedStatic<OrganizationManager> orgStatic;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<Audit> audit;
}
