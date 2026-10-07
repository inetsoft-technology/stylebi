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
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static inetsoft.web.admin.schedule.ImportTaskController.INFO_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77936, the EM schedule task import applies the stored password rule of the task editor
 * (#77192): a local password of a save-to-server path in the file is only kept when the task
 * that the import replaces already stores the same user name and password for the same server.
 * Otherwise it's cleared. Both the viewsheet action save-to-server path and the backup action
 * server path are covered.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ImportTaskStoredPasswordTest {
   @BeforeEach
   void setUp() throws Exception {
      scheduleManager = mock(ScheduleManager.class);
      AnalyticRepository repository = mock(AnalyticRepository.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      Set<IdentityID> users = Set.of(ALICE, new IdentityID("admin", ORG_A));
      when(provider.getUser(any(IdentityID.class)))
         .thenAnswer(inv -> users.contains(inv.<IdentityID>getArgument(0)) ?
            new User(inv.<IdentityID>getArgument(0)) : null);
      when(provider.getUsers()).thenReturn(users.toArray(new IdentityID[0]));
      when(provider.getGroups()).thenReturn(new IdentityID[0]);
      // the org admin administers the users of its org
      when(provider.checkPermission(any(), any(ResourceType.class), anyString(),
                                    eq(ResourceAction.ADMIN)))
         .thenAnswer(inv -> inv.<String>getArgument(2).endsWith("~;~" + ORG_A));
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER), anyString(),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);
      controller = new ImportTaskController(scheduleManager, mock(ScheduleTaskFolderService.class),
                                            repository, securityEngine);

      HttpSession session = mock(HttpSession.class);
      doAnswer(inv -> sessionAttrs.put(inv.getArgument(0), inv.getArgument(1)))
         .when(session).setAttribute(anyString(), any());
      doAnswer(inv -> sessionAttrs.remove(inv.getArgument(0)))
         .when(session).removeAttribute(anyString());
      when(session.getAttribute(anyString())).thenAnswer(inv -> sessionAttrs.get(inv.getArgument(0)));
      request = mock(HttpServletRequest.class);
      when(request.getSession(true)).thenReturn(session);

      caller = mock(XPrincipal.class);
      when(caller.getName()).thenReturn(new IdentityID("admin", ORG_A).convertToKey());
      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenAnswer(
         inv -> ThreadContext.getContextPrincipal() == caller ? ORG_A : "no-context-org");
      when(orgManager.getCurrentOrgID(any())).thenReturn(ORG_A);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      orgStatic = mockStatic(OrganizationManager.class);
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach
   void tearDown() {
      orgStatic.close();
   }

   @Test
   void overwrite_sameServerAndPassword_keepsPassword() throws Exception {
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask("Nightly"));

      ScheduleTask imported = importTask(export("Nightly", STORED_HOST), true);

      assertPasswords(imported, STORED_HOST, STORED);
   }

   @Test
   void overwrite_changedServer_clearsPassword() throws Exception {
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask("Nightly"));

      ScheduleTask imported = importTask(export("Nightly", OTHER_HOST), true);

      assertPasswords(imported, OTHER_HOST, "");
   }

   // the stored task has the same server, but not with this password
   @Test
   void overwrite_sameServerOtherStoredPassword_clearsPassword() throws Exception {
      ScheduleTask stored = storedTask("Nightly");
      ((ViewsheetAction) stored.getAction(0)).getFilePathInfo(PDF).setPassword("other");
      ((IndividualAssetBackupAction) stored.getAction(1)).getServerPath().setPassword("other");
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(stored);

      ScheduleTask imported = importTask(export("Nightly", STORED_HOST), true);

      assertPasswords(imported, STORED_HOST, "");
   }

   @Test
   void overwrite_changedUser_clearsPassword() throws Exception {
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask("Nightly"));
      String xml = export("Nightly", STORED_HOST).replace("username=\"bob\"", "username=\"eve\"");

      ScheduleTask imported = importTask(xml, true);

      assertPasswords(imported, STORED_HOST, "");
   }

   // each path is checked on its own, only the path moved to another server loses its password
   @Test
   void overwrite_onlyBackupServerChanged_clearsOnlyBackupPassword() throws Exception {
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask("Nightly"));
      String xml = export("Nightly", STORED_HOST)
         .replace("ftp://" + STORED_HOST + "/backup", "ftp://" + OTHER_HOST + "/backup");

      ScheduleTask imported = importTask(xml, true);

      ServerPathInfo vsPath = ((ViewsheetAction) imported.getAction(0)).getFilePathInfo(PDF);
      ServerPathInfo backupPath =
         ((IndividualAssetBackupAction) imported.getAction(1)).getServerPath();
      assertEquals("ftp://" + STORED_HOST + "/out", vsPath.getPath());
      assertEquals("ftp://" + OTHER_HOST + "/backup", backupPath.getPath());
      assertEquals(STORED, vsPath.getPassword(), "save-to-server password");
      assertEquals("", backupPath.getPassword(), "backup password");
   }

   // a copy replaces no stored task, so there is nothing that already holds the password
   @Test
   void copy_changedServer_clearsPassword() throws Exception {
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask("Nightly"));

      ScheduleTask imported = importTask(export("Copy", OTHER_HOST), false);

      assertEquals("alice~;~" + ORG_A + ":Copy", imported.getTaskId());
      assertPasswords(imported, OTHER_HOST, "");
   }

   @Test
   void copy_unchangedServer_clearsPassword() throws Exception {
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask("Nightly"));

      ScheduleTask imported = importTask(export("Copy", STORED_HOST), false);

      assertPasswords(imported, STORED_HOST, "");
   }

   // site admins aren't restricted, the same as the other import checks
   @Test
   void siteAdmin_changedServer_keepsPassword() throws Exception {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      when(scheduleManager.getScheduleTask(TASK_ID)).thenReturn(storedTask("Nightly"));

      ScheduleTask imported = importTask(export("Nightly", OTHER_HOST), true);

      assertPasswords(imported, OTHER_HOST, STORED);
   }

   private ScheduleTask importTask(String taskXml, boolean overwriting) throws Exception {
      String xml = "<schedule>" + taskXml + "</schedule>";
      controller.setTaskFile(FileData.builder()
         .name("tasks.xml")
         .content(Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)))
         .build(), request, caller);
      @SuppressWarnings("unchecked")
      List<ScheduleTask> parsed = (List<ScheduleTask>) sessionAttrs.get(INFO_ATTR);
      List<String> ids = parsed.stream().map(ScheduleTask::getTaskId).toList();

      ImportTaskResponse response =
         controller.importScheduleTask(ids, request, overwriting, "http://host", caller);

      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      ArgumentCaptor<ScheduleTask> captor = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(anyString(), captor.capture(), any(Principal.class));
      return captor.getValue();
   }

   private static void assertPasswords(ScheduleTask task, String host, String password) {
      ServerPathInfo vsPath = ((ViewsheetAction) task.getAction(0)).getFilePathInfo(PDF);
      ServerPathInfo backupPath = ((IndividualAssetBackupAction) task.getAction(1)).getServerPath();
      assertEquals("ftp://" + host + "/out", vsPath.getPath());
      assertEquals("ftp://" + host + "/backup", backupPath.getPath());
      assertEquals(password, vsPath.getPassword(), "save-to-server password");
      assertEquals(password, backupPath.getPassword(), "backup password");
   }

   /**
    * The stored task as the EM export writes it, with the server of its paths set to host.
    */
   private static String export(String name, String host) {
      StringWriter out = new StringWriter();
      storedTask(name).writeXML(new PrintWriter(out));
      return out.toString().replace(STORED_HOST, host);
   }

   private static ScheduleTask storedTask(String name) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(ALICE);
      task.addCondition(new NeverRunCondition());
      ViewsheetAction vsAction = new ViewsheetAction();
      vsAction.setViewsheet("1^128^__NULL__^vs1^" + ORG_A);
      vsAction.setFilePath(PDF, new ServerPathInfo("ftp://" + STORED_HOST + "/out", "bob", STORED));
      task.addAction(vsAction);
      IndividualAssetBackupAction backupAction = new IndividualAssetBackupAction();
      backupAction.setServerPaths(
         new ServerPathInfo("ftp://" + STORED_HOST + "/backup", "bob", STORED));
      task.addAction(backupAction);
      return task;
   }

   private static final String ORG_A = "orga";
   private static final IdentityID ALICE = new IdentityID("alice", ORG_A);
   private static final String TASK_ID = "alice~;~" + ORG_A + ":Nightly";
   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
   private static final String STORED_HOST = "files.corp.example";
   private static final String OTHER_HOST = "collector.invalid";
   private static final String STORED = "stored-password";

   private ScheduleManager scheduleManager;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgStatic;
   private ImportTaskController controller;
   private HttpServletRequest request;
   private XPrincipal caller;
   private final Map<String, Object> sessionAttrs = new HashMap<>();
}
