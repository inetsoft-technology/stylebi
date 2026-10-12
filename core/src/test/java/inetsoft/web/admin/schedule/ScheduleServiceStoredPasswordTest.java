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

import inetsoft.report.internal.Util;
import inetsoft.sree.schedule.*;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77192: when a task is edited, the editor sends a placeholder instead of a stored FTP
 * password. The stored password must only be kept when the path still points to the server it
 * was saved for, otherwise an editor who does not know it could send it to another server.
 * Bug #77952: a cleared password is null, never the placeholder, and two paths without a user
 * are the same login. The other same-login cases are in ScheduleSameLoginPasswordTest.
 * The save also looks up the server locations (Bug #77953), which reads SreeEnv.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleServiceStoredPasswordTest {
   @BeforeEach
   void setUp() {
      service = new ScheduleService(null, null, null, null, null, mock(DeployService.class), null,
                                    null, null, null, null, null, null);
   }

   @Test
   void keepsStoredPasswordForSameServer() throws Exception {
      assertEquals(STORED, saveToServerPassword("ftp://files.corp.example/other"));
      assertEquals(STORED, backupPassword("ftp://files.corp.example:21/other"));
   }

   @Test
   void doesNotReuseStoredPasswordForAnotherHost() throws Exception {
      assertNull(saveToServerPassword("ftp://collector.invalid/out"));
      assertNull(backupPassword("ftp://collector.invalid/out"));
   }

   @Test
   void doesNotReuseStoredPasswordForSubPathThatMovesToAnotherHost() throws Exception {
      assertNull(saveToServerPassword("ftp://files.corp.example/x@collector.invalid/out"));
      assertNull(backupPassword("ftp://files.corp.example/x@collector.invalid/out"));
   }

   @Test
   void doesNotReuseStoredPasswordForAnotherPortOrProtocol() throws Exception {
      assertNull(saveToServerPassword("ftp://files.corp.example:2121/out"));
      assertNull(backupPassword("sftp://files.corp.example/out"));
   }

   // Bug #77952, the user in the path overrides the user name field when FTPUtil logs in
   @Test
   void keepsStoredPasswordForSameUserInPath() throws Exception {
      assertEquals(STORED,
                   saveToServerPassword("ftp://bob@files.corp.example/other", "bob", "bob"));
      assertEquals(STORED, backupPassword("ftp://bob@files.corp.example/other", "bob", "bob"));
      assertEquals(STORED,
                   saveToServerPassword("ftp://bob@files.corp.example/other", "bob", "mallory"));
      assertEquals(STORED, backupPassword("ftp://bob@files.corp.example/other", "bob", "mallory"));
   }

   // a path without a user logs in the same way as another path without a user
   @Test
   void keepsStoredPasswordForSameServerWithoutUser() throws Exception {
      assertEquals(STORED, saveToServerPassword("sftp://files.corp.example/other", null, null,
                                                "sftp://files.corp.example/out"));
      assertEquals(STORED, backupPassword("sftp://files.corp.example/other", null, "",
                                          "sftp://files.corp.example/out"));
   }

   @Test
   void doesNotReuseStoredPasswordWhenUserIsAddedOrRemoved() throws Exception {
      assertNull(saveToServerPassword("ftp://files.corp.example/other", null, "mallory"));
      assertNull(backupPassword("ftp://mallory@files.corp.example/other", null, null));
      assertNull(saveToServerPassword("ftp://files.corp.example/other", "bob", null));
      assertNull(backupPassword("ftp://files.corp.example/other", "bob", ""));
   }

   // a password in the path is used instead of the stored one (Bug #77957)
   @Test
   void usesPasswordInPath() throws Exception {
      assertEquals("x", saveToServerPassword("ftp://mallory:x@files.corp.example/other"));
      assertEquals("x", backupPassword("ftp://mallory:x@files.corp.example/other"));
   }

   private String saveToServerPassword(String path) throws Exception {
      return saveToServerPassword(path, "bob", "bob");
   }

   private String saveToServerPassword(String path, String storedUser, String user)
      throws Exception
   {
      return saveToServerPassword(path, storedUser, user, STORED_PATH);
   }

   private String saveToServerPassword(String path, String storedUser, String user,
                                       String storedPath) throws Exception
   {
      ViewsheetAction oldAction = new ViewsheetAction();
      oldAction.setViewsheet("vs1");
      oldAction.setFilePath(PDF, new ServerPathInfo(storedPath, storedUser, STORED));

      GeneralActionModel model = mock(GeneralActionModel.class);
      when(model.sheet()).thenReturn("vs1");
      when(model.actionType()).thenReturn("ViewsheetAction");
      when(model.saveToServerEnabled()).thenReturn(true);
      when(model.saveFormats()).thenReturn(new String[] { String.valueOf(PDF) });
      when(model.serverFilePaths()).thenReturn(List.of(ServerPathInfoModel.builder()
         .path(path).ftp(true).username(user).password(Util.PLACEHOLDER_PASSWORD)
         .oldFormat(PDF).build()));

      ViewsheetAction action = (ViewsheetAction) service.getActionFromModel(
         model, oldAction, mock(Principal.class), "http://host/");
      return action.getFilePathInfo(PDF).getPassword();
   }

   private String backupPassword(String path) throws Exception {
      return backupPassword(path, "bob", "bob");
   }

   private String backupPassword(String path, String storedUser, String user) throws Exception {
      return backupPassword(path, storedUser, user, STORED_PATH);
   }

   private String backupPassword(String path, String storedUser, String user, String storedPath)
      throws Exception
   {
      IndividualAssetBackupAction oldAction = new IndividualAssetBackupAction();
      oldAction.setServerPaths(new ServerPathInfo(storedPath, storedUser, STORED));

      BackupActionModel model = mock(BackupActionModel.class);
      when(model.actionType()).thenReturn("BackupAction");
      when(model.backupPathsEnabled()).thenReturn(true);
      when(model.backupPath()).thenReturn(path);
      when(model.backupServerPath()).thenReturn(ServerPathInfoModel.builder()
         .path(path).ftp(true).username(user).password(Util.PLACEHOLDER_PASSWORD).build());

      IndividualAssetBackupAction action = (IndividualAssetBackupAction)
         service.getActionFromModel(model, oldAction, mock(Principal.class), "http://host/");
      return action.getServerPath().getPassword();
   }

   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
   private static final String STORED_PATH = "ftp://files.corp.example/out";
   private static final String STORED = "stored-password";
   private ScheduleService service;
}
