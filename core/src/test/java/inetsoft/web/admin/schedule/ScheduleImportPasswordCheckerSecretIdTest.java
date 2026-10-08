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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.schedule.*;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77951, an imported local password path is only set back to a secret id of the stored task
 * that the stored task uses for the same server, and only if the secret resolves to the same user
 * name and password. Every other path is cleared, and a secret that fails to resolve only clears
 * the paths that would have matched it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleImportPasswordCheckerSecretIdTest {
   @BeforeEach
   void setUp() throws Exception {
      tool = mockStatic(Tool.class, CALLS_REAL_METHODS);
      tool.when(() -> Tool.loadCredentials(SECRET_A)).thenReturn(new ObjectMapper().readTree(
         "{\"username\":\"bob\",\"password\":\"pw-a\"}"));
      tool.when(() -> Tool.loadCredentials(SECRET_B)).thenReturn(new ObjectMapper().readTree(
         "{\"username\":\"carol\",\"password\":\"pw-b\"}"));
      tool.when(() -> Tool.loadCredentials(SECRET_BROKEN))
         .thenThrow(new RuntimeException("Failed to load credential"));
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   @Test
   void sameServerUserAndPassword_restoresSecretIdAndIsNotReportedCleared() {
      ScheduleTask stored = task(secretPath(HOST_A, SECRET_A), secretPath(HOST_B, SECRET_B),
                                 secretPath(HOST_A, SECRET_A));
      ScheduleTask imported = task(localPath(HOST_A, "bob", "pw-a"),
                                   localPath(HOST_B, "carol", "pw-b"),
                                   localPath(HOST_A, "bob", "pw-a"));

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(imported, stored);

      assertEquals(List.of(), cleared);
      assertSecret(pdf(imported), SECRET_A);
      assertSecret(excel(imported), SECRET_B);
      assertSecret(backup(imported), SECRET_A);
   }

   // the secret id of the stored task isn't bound to another server, even with its password
   @Test
   void otherServer_isCleared() {
      ScheduleTask stored = task(secretPath(HOST_A, SECRET_A), null, null);
      ScheduleTask imported = task(localPath(HOST_B, "bob", "pw-a"), null, null);

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(imported, stored);

      assertEquals(List.of(pdf(imported).getPath()), cleared);
      assertCleared(pdf(imported));
   }

   @Test
   void otherPassword_isCleared() {
      ScheduleTask stored = task(secretPath(HOST_A, SECRET_A), null, null);
      ScheduleTask imported = task(localPath(HOST_A, "bob", "guess"), null, null);

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(imported, stored);

      assertEquals(List.of(pdf(imported).getPath()), cleared);
      assertCleared(pdf(imported));
   }

   @Test
   void otherUser_isCleared() {
      ScheduleTask stored = task(secretPath(HOST_A, SECRET_A), null, null);
      ScheduleTask imported = task(localPath(HOST_A, "mallory", "pw-a"), null, null);

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(imported, stored);

      assertEquals(List.of(pdf(imported).getPath()), cleared);
      assertCleared(pdf(imported));
   }

   // Bug #77979, the user in the path overrides the secret's user name
   @Test
   void otherPathUser_isCleared() {
      ScheduleTask stored = task(secretPath(HOST_A, SECRET_A), null, null);
      ScheduleTask imported = task(
         new ServerPathInfo("ftp://mallory@" + HOST_A + "/out", "bob", "pw-a"), null, null);

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(imported, stored);

      assertEquals(List.of(pdf(imported).getPath()), cleared);
      assertCleared(pdf(imported));
   }

   // a secret id is only taken from the stored task, a new task has none
   @Test
   void newTask_isCleared() {
      ScheduleTask imported = task(localPath(HOST_A, "bob", "pw-a"), null, null);

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(imported, null);

      assertEquals(List.of(pdf(imported).getPath()), cleared);
      assertCleared(pdf(imported));
   }

   // a secret id of the imported file is never restored to another path of the file
   @Test
   void secretIdOfImportedFile_isNotRestored() {
      ScheduleTask stored = task(localPath(HOST_A, "bob", "other"), null, null);
      ScheduleTask imported = task(localPath(HOST_A, "bob", "pw-a"),
                                   secretPath(HOST_A, SECRET_A), null);

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(imported, stored);

      assertEquals(List.of(pdf(imported).getPath()), cleared);
      assertCleared(pdf(imported));
      assertSecret(excel(imported), SECRET_A);
   }

   // a secret that fails to resolve clears its path, the other paths are still checked
   @Test
   void secretLookupFails_clearsOnlyThatPath() {
      ScheduleTask stored = task(secretPath(HOST_A, SECRET_BROKEN), null,
                                 secretPath(HOST_B, SECRET_B));
      ScheduleTask imported = task(localPath(HOST_A, "bob", "pw-a"), null,
                                   localPath(HOST_B, "carol", "pw-b"));

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(imported, stored);

      assertEquals(List.of(pdf(imported).getPath()), cleared);
      assertCleared(pdf(imported));
      assertSecret(backup(imported), SECRET_B);
   }

   private static void assertSecret(ServerPathInfo path, String secretId) {
      assertTrue(path.isUseCredential(), path.getPath());
      assertEquals(secretId, path.getSecretId(), path.getPath());
      assertNull(path.getUsername(), path.getPath());
      assertNull(path.getPassword(), path.getPath());
   }

   private static void assertCleared(ServerPathInfo path) {
      assertFalse(path.isUseCredential(), path.getPath());
      assertNull(path.getSecretId(), path.getPath());
      assertEquals("", path.getPassword(), path.getPath());
   }

   private static ServerPathInfo pdf(ScheduleTask task) {
      return ((ViewsheetAction) task.getAction(0)).getFilePathInfo(PDF);
   }

   private static ServerPathInfo excel(ScheduleTask task) {
      return ((ViewsheetAction) task.getAction(0)).getFilePathInfo(EXCEL);
   }

   private static ServerPathInfo backup(ScheduleTask task) {
      return ((IndividualAssetBackupAction) task.getAction(1)).getServerPath();
   }

   private static ScheduleTask task(ServerPathInfo pdfPath, ServerPathInfo excelPath,
                                    ServerPathInfo backupPath)
   {
      ScheduleTask task = new ScheduleTask("Nightly");
      ViewsheetAction vsAction = new ViewsheetAction();

      if(pdfPath != null) {
         vsAction.setFilePath(PDF, pdfPath);
      }

      if(excelPath != null) {
         vsAction.setFilePath(EXCEL, excelPath);
      }

      task.addAction(vsAction);
      IndividualAssetBackupAction backupAction = new IndividualAssetBackupAction();

      if(backupPath != null) {
         backupAction.setServerPaths(backupPath);
      }

      task.addAction(backupAction);
      return task;
   }

   private static ServerPathInfo secretPath(String host, String secretId) {
      ServerPathInfo info = new ServerPathInfo("ftp://" + host + "/out");
      info.setUseCredential(true);
      info.setSecretId(secretId);
      return info;
   }

   private static ServerPathInfo localPath(String host, String user, String password) {
      return new ServerPathInfo("ftp://" + host + "/out", user, password);
   }

   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
   private static final int EXCEL = FileFormatInfo.EXPORT_TYPE_EXCEL;
   private static final String HOST_A = "files.corp.example";
   private static final String HOST_B = "backup.corp.example";
   private static final String SECRET_A = "sched/ftp-bob";
   private static final String SECRET_B = "sched/ftp-carol";
   private static final String SECRET_BROKEN = "sched/missing";
   private MockedStatic<Tool> tool;
}
