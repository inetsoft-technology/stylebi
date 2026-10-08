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

import com.jcraft.jsch.*;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.schedule.*;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.Tool;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.*;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77979: the editors and the import only get a placeholder (or the same value) for a stored
 * save-to-server password. A stored password must only be kept for a path that logs in to the
 * same server as the same user, the user in the path overriding the user name field as the
 * uploader does. Each case saves the action with the real ScheduleService, stores and loads the
 * path as XML and uploads with the real FTPUtil to a recording FTP or SFTP client.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleSameLoginPasswordTest {
   @BeforeEach
   void setUp() {
      SreeEnv.setProperty(LOCATIONS, LOCATION);
      service = new ScheduleService(null, null, null, null, null, mock(DeployService.class), null,
                                    null, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty(LOCATIONS, null);
   }

   // viewsheet action save-to-server path

   @Test
   void changedPathUserDoesNotKeepLocationPassword() throws Throwable {
      ServerPathInfo saved = saveViewsheet(HOST + "/reports/a.pdf", "svc", LOC_PWD,
                                           "ftp://attacker@files.corp.example/other/a.pdf", "svc",
                                           PLACEHOLDER);

      assertNoPassword(saved);
      assertEquals("attacker/", upload(saved));
   }

   @Test
   void changedUserFieldDoesNotKeepPassword() throws Throwable {
      ServerPathInfo saved = saveViewsheet(HOST + "/x/a.pdf", "bob", BOB_PWD,
                                           HOST + "/x/a.pdf", "attacker", PLACEHOLDER);

      assertNoPassword(saved);
      assertEquals("attacker/", upload(saved));

      // the location's own user doesn't get another user's password either
      saved = saveViewsheet(HOST + "/x/a.pdf", "bob", BOB_PWD, HOST + "/x/a.pdf", "svc",
                            PLACEHOLDER);
      assertNoPassword(saved);
   }

   @Test
   void changedPathUserDoesNotKeepOwnPassword() throws Throwable {
      ServerPathInfo saved = saveViewsheet(HOST + "/x/a.pdf", "bob", BOB_PWD,
                                           "ftp://attacker@files.corp.example/x/a.pdf", "bob",
                                           PLACEHOLDER);

      assertNoPassword(saved);
      assertEquals("attacker/", upload(saved));
   }

   @Test
   void removedUserNameDoesNotKeepPassword() throws Throwable {
      ServerPathInfo saved = saveViewsheet(HOST + "/x/a.pdf", "bob", BOB_PWD,
                                           HOST + "/x/a.pdf", "", PLACEHOLDER);

      assertNoPassword(saved);
   }

   @Test
   void sftpChangedUserDoesNotKeepPassword() throws Throwable {
      ServerPathInfo saved = saveViewsheet(SFTP_HOST + "/x/a.pdf", "bob", BOB_PWD,
                                           "sftp://attacker@files.corp.example/x/a.pdf", "bob",
                                           PLACEHOLDER);
      assertNoPassword(saved);
      assertEquals("attacker/", upload(saved));

      saved = saveViewsheet(SFTP_HOST + "/x/a.pdf", "bob", BOB_PWD, SFTP_HOST + "/x/a.pdf",
                            "attacker", PLACEHOLDER);
      assertNoPassword(saved);
      assertEquals("attacker/", upload(saved));
   }

   @Test
   void sameLoginKeepsPassword() throws Throwable {
      // rename the file
      ServerPathInfo saved = saveViewsheet(HOST + "/x/a.pdf", "bob", BOB_PWD,
                                           HOST + "/x/b.pdf", "bob", PLACEHOLDER);
      assertEquals("bob/" + BOB_PWD, upload(saved));

      // change the folder
      saved = saveViewsheet(HOST + "/x/a.pdf", "bob", BOB_PWD, HOST + "/y/c.pdf", "bob",
                            PLACEHOLDER);
      assertEquals("bob/" + BOB_PWD, upload(saved));

      // move the user from the field into the path
      saved = saveViewsheet(HOST + "/x/a.pdf", "bob", BOB_PWD,
                            "ftp://bob@files.corp.example/x/a.pdf", "", PLACEHOLDER);
      assertEquals("bob/" + BOB_PWD, upload(saved));

      // the location's own path
      saved = saveViewsheet(HOST + "/reports/a.pdf", "svc", LOC_PWD, HOST + "/reports/b.pdf",
                            "svc", PLACEHOLDER);
      assertEquals("svc/" + LOC_PWD, upload(saved));

      // sftp
      saved = saveViewsheet(SFTP_HOST + "/x/a.pdf", "bob", BOB_PWD, SFTP_HOST + "/y/a.pdf", "bob",
                            PLACEHOLDER);
      assertEquals("bob/" + BOB_PWD, upload(saved));
   }

   @Test
   void serverChangeDoesNotKeepPassword() throws Throwable {
      ServerPathInfo saved = saveViewsheet(HOST + "/x/a.pdf", "bob", BOB_PWD,
                                           "ftp://collector.invalid/x/a.pdf", "bob", PLACEHOLDER);

      assertNoPassword(saved);
   }

   @Test
   void typedPasswordIsUsed() throws Throwable {
      ServerPathInfo saved = saveViewsheet(HOST + "/x/a.pdf", "bob", BOB_PWD,
                                           "ftp://attacker@files.corp.example/other/a.pdf", "bob",
                                           "typed");

      assertEquals("attacker/typed", upload(saved));
   }

   // backup action server path

   @Test
   void backupChangedPathUserDoesNotKeepPassword() throws Throwable {
      ServerPathInfo saved = saveBackup(new ServerPathInfo(HOST + "/backup/a.zip", "bob", BK_PWD),
                                        "ftp://attacker@files.corp.example/other/a.zip", "bob");
      assertNoPassword(saved);
      assertEquals("attacker/", upload(saved));

      saved = saveBackup(
         new ServerPathInfo("ftp://bob@files.corp.example/backup/a.zip", null, BK_PWD),
         "ftp://attacker@files.corp.example/backup/a.zip", null);
      assertNoPassword(saved);
      assertEquals("attacker/", upload(saved));
   }

   @Test
   void backupNeverStoresPlaceholder() throws Throwable {
      // the user name field changes
      ServerPathInfo saved = saveBackup(new ServerPathInfo(HOST + "/backup/a.zip", "bob", BK_PWD),
                                        HOST + "/backup/a.zip", "attacker");
      assertNoPassword(saved);
      assertEquals("attacker/", upload(saved));

      // the server changes
      saved = saveBackup(new ServerPathInfo(HOST + "/backup/a.zip", "bob", BK_PWD),
                         "ftp://collector.invalid/backup/a.zip", "bob");
      assertNoPassword(saved);

      // a new action
      saved = saveBackup(null, HOST + "/backup/a.zip", "bob");
      assertNoPassword(saved);
   }

   @Test
   void backupSameLoginKeepsPassword() throws Throwable {
      ServerPathInfo saved = saveBackup(new ServerPathInfo(HOST + "/backup/a.zip", "bob", BK_PWD),
                                        HOST + "/other/b.zip", "bob");
      assertEquals("bob/" + BK_PWD, upload(saved));

      saved = saveBackup(new ServerPathInfo(HOST + "/backup/a.zip", "bob", BK_PWD),
                         "ftp://bob@files.corp.example/backup/a.zip", null);
      assertEquals("bob/" + BK_PWD, upload(saved));
   }

   // task import

   @Test
   void importChangedPathUserClearsPassword() {
      ScheduleTask imported = viewsheetTask(
         new ServerPathInfo("ftp://attacker@files.corp.example/other/a.pdf", "svc", LOC_PWD));

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(
         imported, viewsheetTask(new ServerPathInfo(HOST + "/reports/a.pdf", "svc", LOC_PWD)));

      assertEquals(List.of("ftp://attacker@files.corp.example/other/a.pdf"), cleared);
      assertEquals("", pathOf(imported).getPassword());
   }

   @Test
   void importSameLoginKeepsPassword() {
      // the user moved from the field into the path
      ScheduleTask imported = viewsheetTask(
         new ServerPathInfo("ftp://svc@files.corp.example/other/a.pdf", null, LOC_PWD));

      List<String> cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(
         imported, viewsheetTask(new ServerPathInfo(HOST + "/reports/a.pdf", "svc", LOC_PWD)));

      assertEquals(List.of(), cleared);
      assertEquals(LOC_PWD, pathOf(imported).getPassword());

      // the same user name field on another folder
      imported = viewsheetTask(new ServerPathInfo(HOST + "/other/a.pdf", "svc", LOC_PWD));
      cleared = ScheduleImportPasswordChecker.clearUnboundPasswords(
         imported, viewsheetTask(new ServerPathInfo(HOST + "/reports/a.pdf", "svc", LOC_PWD)));

      assertEquals(List.of(), cleared);
      assertEquals(LOC_PWD, pathOf(imported).getPassword());
   }

   private ServerPathInfo saveViewsheet(String oldPath, String oldUser, String oldPassword,
                                        String path, String username, String password)
      throws Exception
   {
      ViewsheetAction oldAction = new ViewsheetAction();
      oldAction.setViewsheet("vs1");
      oldAction.setFilePath(PDF, roundTrip(new ServerPathInfo(oldPath, oldUser, oldPassword)));

      GeneralActionModel model = mock(GeneralActionModel.class);
      when(model.sheet()).thenReturn("vs1");
      when(model.actionType()).thenReturn("ViewsheetAction");
      when(model.saveToServerEnabled()).thenReturn(true);
      when(model.saveFormats()).thenReturn(new String[] { String.valueOf(PDF) });
      when(model.serverFilePaths()).thenReturn(List.of(ServerPathInfoModel.builder()
         .path(path).ftp(true).username(username).password(password).oldFormat(PDF).build()));

      ViewsheetAction action = (ViewsheetAction) service.getActionFromModel(
         model, oldAction, mock(Principal.class), "http://host/");
      return roundTrip(action.getFilePathInfo(PDF));
   }

   private ServerPathInfo saveBackup(ServerPathInfo oldPath, String path, String username)
      throws Exception
   {
      IndividualAssetBackupAction oldAction = null;

      if(oldPath != null) {
         oldAction = new IndividualAssetBackupAction();
         oldAction.setServerPaths(roundTrip(oldPath));
      }

      BackupActionModel model = mock(BackupActionModel.class);
      when(model.actionType()).thenReturn("BackupAction");
      when(model.backupPathsEnabled()).thenReturn(true);
      when(model.backupPath()).thenReturn(path);
      when(model.backupServerPath()).thenReturn(ServerPathInfoModel.builder()
         .path(path).ftp(true).username(username).password(PLACEHOLDER).build());

      IndividualAssetBackupAction action = (IndividualAssetBackupAction)
         service.getActionFromModel(model, oldAction, mock(Principal.class), "http://host/");
      ServerPathInfo saved = roundTrip(action.getServerPath());
      assertNotEquals(PLACEHOLDER, saved.getPassword());
      return saved;
   }

   // the task is stored with the password encrypted and loaded again before it runs
   private static ServerPathInfo roundTrip(ServerPathInfo info) throws Exception {
      StringWriter xml = new StringWriter();

      try(PrintWriter writer = new PrintWriter(xml)) {
         info.writeXML(writer);
      }

      assertFalse(xml.toString().contains(PLACEHOLDER), xml.toString());
      ServerPathInfo loaded = new ServerPathInfo();
      loaded.parseXML(Tool.parseXML(new StringReader(xml.toString())).getDocumentElement());
      return loaded;
   }

   /**
    * Uploads a file to the path with the real FTPUtil and returns the user/password it logged in
    * with.
    */
   private String upload(ServerPathInfo info) throws Throwable {
      Path file = Files.writeString(dir.resolve("a.pdf"), "report");
      List<String> logins = new ArrayList<>();

      if(info.isSFTP()) {
         Session session = mock(Session.class);
         String[] user = new String[1];
         doAnswer(inv -> logins.add(user[0] + "/" + Objects.toString(inv.getArgument(0), "")))
            .when(session).setPassword(nullable(String.class));
         when(session.openChannel("sftp")).thenReturn(mock(ChannelSftp.class));

         try(MockedConstruction<JSch> jsch = mockConstruction(JSch.class, (m, ctx) -> {
            when(m.getSession(nullable(String.class), anyString())).thenAnswer(inv -> {
               user[0] = inv.getArgument(0);
               assertEquals("files.corp.example", inv.getArgument(1));
               return session;
            });
         }))
         {
            FTPUtil.uploadToFTP(info.getPath(), file.toFile(), info, false);
            assertEquals(1, jsch.constructed().size());
         }
      }
      else {
         try(MockedConstruction<FTPClient> ftp = mockConstruction(FTPClient.class, (m, ctx) -> {
            when(m.getReplyCode()).thenReturn(220);
            when(m.login(nullable(String.class), nullable(String.class))).thenAnswer(inv -> {
               logins.add(inv.getArgument(0) + "/" + Objects.toString(inv.getArgument(1), ""));
               return true;
            });
            when(m.storeFile(anyString(), any())).thenReturn(true);
         }))
         {
            FTPUtil.uploadToFTP(info.getPath(), file.toFile(), info, false);
            verify(ftp.constructed().get(0)).connect("files.corp.example");
         }
      }

      assertEquals(1, logins.size(), logins.toString());
      return logins.get(0);
   }

   // a stored path without a password loads with an empty one
   private static void assertNoPassword(ServerPathInfo info) {
      assertTrue(Tool.isEmptyString(info.getPassword()), info.getPassword());
   }

   private static ScheduleTask viewsheetTask(ServerPathInfo path) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("vs1");
      action.setFilePath(PDF, path);
      ScheduleTask task = new ScheduleTask("admin~;~host-org:t1");
      task.addAction(action);
      return task;
   }

   private static ServerPathInfo pathOf(ScheduleTask task) {
      return ((ViewsheetAction) task.getAction(0)).getFilePathInfo(PDF);
   }

   @TempDir
   Path dir;
   private ScheduleService service;

   private static final String LOCATIONS = "server.save.locations";
   private static final String LOC_PWD = "S3cretAdminPwd";
   private static final String LOCATION = "ftp://files.corp.example/reports|Reports|svc|" + LOC_PWD;
   private static final String BOB_PWD = "BobPwd";
   private static final String BK_PWD = "BkPwd";
   private static final String HOST = "ftp://files.corp.example";
   private static final String SFTP_HOST = "sftp://files.corp.example";
   private static final String PLACEHOLDER = Util.PLACEHOLDER_PASSWORD;
   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
}
