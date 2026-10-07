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
      assertNotEquals(STORED, saveToServerPassword("ftp://collector.invalid/out"));
      assertNotEquals(STORED, backupPassword("ftp://collector.invalid/out"));
   }

   @Test
   void doesNotReuseStoredPasswordForSubPathThatMovesToAnotherHost() throws Exception {
      assertNotEquals(STORED,
                      saveToServerPassword("ftp://files.corp.example/x@collector.invalid/out"));
      assertNotEquals(STORED, backupPassword("ftp://files.corp.example/x@collector.invalid/out"));
   }

   @Test
   void doesNotReuseStoredPasswordForAnotherPortOrProtocol() throws Exception {
      assertNotEquals(STORED, saveToServerPassword("ftp://files.corp.example:2121/out"));
      assertNotEquals(STORED, backupPassword("sftp://files.corp.example/out"));
   }

   private String saveToServerPassword(String path) throws Exception {
      ViewsheetAction oldAction = new ViewsheetAction();
      oldAction.setViewsheet("vs1");
      oldAction.setFilePath(PDF, new ServerPathInfo(STORED_PATH, "bob", STORED));

      GeneralActionModel model = mock(GeneralActionModel.class);
      when(model.sheet()).thenReturn("vs1");
      when(model.actionType()).thenReturn("ViewsheetAction");
      when(model.saveToServerEnabled()).thenReturn(true);
      when(model.saveFormats()).thenReturn(new String[] { String.valueOf(PDF) });
      when(model.serverFilePaths()).thenReturn(List.of(ServerPathInfoModel.builder()
         .path(path).ftp(true).username("bob").password(Util.PLACEHOLDER_PASSWORD)
         .oldFormat(PDF).build()));

      ViewsheetAction action = (ViewsheetAction) service.getActionFromModel(
         model, oldAction, mock(Principal.class), "http://host/");
      return action.getFilePathInfo(PDF).getPassword();
   }

   private String backupPassword(String path) throws Exception {
      IndividualAssetBackupAction oldAction = new IndividualAssetBackupAction();
      oldAction.setServerPaths(new ServerPathInfo(STORED_PATH, "bob", STORED));

      BackupActionModel model = mock(BackupActionModel.class);
      when(model.actionType()).thenReturn("BackupAction");
      when(model.backupPathsEnabled()).thenReturn(true);
      when(model.backupPath()).thenReturn(path);
      when(model.backupServerPath()).thenReturn(ServerPathInfoModel.builder()
         .path(path).ftp(true).username("bob").password(Util.PLACEHOLDER_PASSWORD).build());

      IndividualAssetBackupAction action = (IndividualAssetBackupAction)
         service.getActionFromModel(model, oldAction, mock(Principal.class), "http://host/");
      return action.getServerPath().getPassword();
   }

   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
   private static final String STORED_PATH = "ftp://files.corp.example/out";
   private static final String STORED = "stored-password";
   private ScheduleService service;
}
