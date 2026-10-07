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
package inetsoft.sree.schedule;

import inetsoft.util.Tool;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77192: the server that a schedule path points to is parsed once, in the same way for the
 * upload and for the checks, and a stored credential is only sent to the server of the saved
 * path.
 */
@Tag("core")
class FTPUtilEndpointTest {
   @Test
   void parsesHostPortAndPath() throws Exception {
      FTPUtil.Endpoint endpoint = FTPUtil.parseEndpoint("ftp://files.corp.example:2121/out/r.pdf");

      assertFalse(endpoint.sftp());
      assertEquals("files.corp.example", endpoint.host());
      assertEquals(2121, endpoint.port());
      assertEquals("/out/r.pdf", endpoint.path());
      assertNull(endpoint.userInfo());
   }

   @Test
   void hostIsTakenAfterTheLastAt() throws Exception {
      FTPUtil.Endpoint endpoint =
         FTPUtil.parseEndpoint("ftp://files.corp.example/x@collector.invalid/r.pdf");

      assertEquals("collector.invalid", endpoint.host());
      assertEquals("files.corp.example/x", endpoint.userInfo());
      assertEquals("/r.pdf", endpoint.path());
   }

   @Test
   void parsesUserInfoSftpAndSchemeLessPaths() throws Exception {
      FTPUtil.Endpoint user = FTPUtil.parseEndpoint("ftp://bob:p@ss@files.corp.example/r.pdf");
      assertEquals("files.corp.example", user.host());
      assertEquals("bob:p@ss", user.userInfo());

      FTPUtil.Endpoint sftp = FTPUtil.parseEndpoint("SFTP://files.corp.example:2222/r.pdf");
      assertTrue(sftp.sftp());
      assertEquals("files.corp.example", sftp.host());
      assertEquals(2222, sftp.port());

      FTPUtil.Endpoint schemeLess = FTPUtil.parseEndpoint("files.corp.example/out/r.pdf");
      assertFalse(schemeLess.sftp());
      assertEquals("files.corp.example", schemeLess.host());
      assertEquals("/out/r.pdf", schemeLess.path());
   }

   @Test
   void sameServerComparesProtocolHostAndEffectivePort() throws Exception {
      FTPUtil.Endpoint endpoint = FTPUtil.parseEndpoint("ftp://files.corp.example/a.pdf");

      assertTrue(endpoint.isSameServer(FTPUtil.parseEndpoint("ftp://FILES.corp.example:21/b")));
      assertFalse(endpoint.isSameServer(FTPUtil.parseEndpoint("ftp://files.corp.example:2121/b")));
      assertFalse(endpoint.isSameServer(FTPUtil.parseEndpoint("sftp://files.corp.example/b")));
      assertFalse(endpoint.isSameServer(
         FTPUtil.parseEndpoint("ftp://files.corp.example.collector.invalid/b")));
      assertTrue(FTPUtil.parseEndpoint("sftp://h/a")
                    .isSameServer(FTPUtil.parseEndpoint("sftp://h:22/b")));
      assertFalse(FTPUtil.parseEndpoint("/local/dir/a")
                     .isSameServer(FTPUtil.parseEndpoint("/local/dir/a")));
   }

   @Test
   void uploadSendsCredentialToTheServerOfTheSavedPath() throws Throwable {
      ServerPathInfo info = credentialPath("ftp://files.corp.example:2121/out/{0}");

      try(MockedStatic<Tool> tool = mockTool(); MockedConstruction<FTPClient> ftp = mockFtp()) {
         FTPUtil.uploadToFTP("ftp://files.corp.example:2121/out/r.pdf", file(), info, false);
      }

      assertEquals(List.of("files.corp.example:2121"), connected);
      assertEquals(List.of("own-user/own-password"), logins);
   }

   @Test
   void uploadRejectsFormattedPathThatPointsToAnotherServer() throws Throwable {
      ServerPathInfo info = credentialPath("ftp://files.corp.example/out/{0}");

      try(MockedStatic<Tool> tool = mockTool(); MockedConstruction<FTPClient> ftp = mockFtp()) {
         // a parameter value containing an '@' would move the login to another host
         assertThrows(Exception.class, () -> FTPUtil.uploadToFTP(
            "ftp://files.corp.example/out/x@collector.invalid/r.pdf", file(), info, false));
         tool.verify(() -> Tool.loadCredentials(anyString()), never());
         assertTrue(ftp.constructed().isEmpty());
      }

      assertTrue(connected.isEmpty());
   }

   @Test
   void uploadRejectsStoredPasswordForAnotherServer() throws Throwable {
      ServerPathInfo info = new ServerPathInfo("ftp://files.corp.example/out/{0}", "bob", "pw");

      try(MockedConstruction<FTPClient> ftp = mockFtp()) {
         assertThrows(Exception.class, () -> FTPUtil.uploadToFTP(
            "ftp://files.corp.example/out/x@collector.invalid/r.pdf", file(), info, false));
         assertTrue(ftp.constructed().isEmpty());
      }
   }

   @Test
   void passwordInThePathLogsInAsBeforeTheSplit() throws Throwable {
      // Bug #77957, the password is moved out of the path; the upload logs in with the same user,
      // password and server
      ServerPathInfo info = new ServerPathInfo("ftp://u:p@w@files.corp.example:2121/out/{0}");
      assertEquals("ftp://u@files.corp.example:2121/out/{0}", info.getPath());

      try(MockedConstruction<FTPClient> ftp = mockFtp()) {
         FTPUtil.uploadToFTP(MessageFormat.format(info.getPath(), "r.pdf"), file(), info, false);
      }

      assertEquals(List.of("files.corp.example:2121"), connected);
      assertEquals(List.of("u/p@w"), logins);
   }

   @Test
   void passwordInThePathIsNotSentToAnotherServer() throws Throwable {
      // Bug #77957, a parameter value with an '@' moved the host, and the password in the path
      // went with it
      ServerPathInfo info = new ServerPathInfo("ftp://u:pw@files.corp.example/out/{0}");
      String url = MessageFormat.format(info.getPath(), "x@127.0.0.1#");
      assertEquals("127.0.0.1", FTPUtil.parseEndpoint(url).host());

      try(MockedConstruction<FTPClient> ftp = mockFtp()) {
         Exception ex = assertThrows(Exception.class,
                                     () -> FTPUtil.uploadToFTP(url, file(), info, false));
         assertTrue(ex.getMessage().contains("the server does not match the saved path"),
                    ex.getMessage());
         assertTrue(ftp.constructed().isEmpty());
      }
   }

   @Test
   void passwordInThePathWithParameterHostIsRefused() throws Throwable {
      // Bug #77957, the saved path can't be parsed, so it can't be bound to a server
      ServerPathInfo host = new ServerPathInfo("ftp://u:pw@{0}/out/r.pdf");
      ServerPathInfo port = new ServerPathInfo("ftp://u:pw@files.corp.example:{0}/out/r.pdf");

      try(MockedConstruction<FTPClient> ftp = mockFtp()) {
         Exception ex = assertThrows(Exception.class, () -> FTPUtil.uploadToFTP(
            MessageFormat.format(host.getPath(), "collector.invalid"), file(), host, false));
         assertTrue(ex.getMessage().contains("the server does not match the saved path"),
                    ex.getMessage());
         ex = assertThrows(Exception.class, () -> FTPUtil.uploadToFTP(
            MessageFormat.format(port.getPath(), "21"), file(), port, false));
         assertTrue(ex.getMessage().contains("the server does not match the saved path"),
                    ex.getMessage());
         assertTrue(ftp.constructed().isEmpty());
      }
   }

   @Test
   void emptyPasswordInThePathIsLeftInThePath() throws Throwable {
      ServerPathInfo info = new ServerPathInfo("ftp://u:@files.corp.example:2121/r.pdf");
      assertEquals("ftp://u:@files.corp.example:2121/r.pdf", info.getPath());
      assertNull(info.getPassword());

      try(MockedConstruction<FTPClient> ftp = mockFtp()) {
         FTPUtil.uploadToFTP(info.getPath(), file(), info, false);
      }

      assertEquals(List.of("u/"), logins);
   }

   @Test
   void splitPasswordFollowsParseEndpoint() throws Exception {
      for(String path : new String[] {
         "ftp://u:pw@h/x", "u:pw@h/x", "FTP://u:pw@h/x", "SFTP://u:pw@h:2222/x",
         "ftp://u:p@w@h/x", "ftp://a@b:c@h/x", "ftp://u:p w+%41/#?@h/x?append=true",
         "ftp://u:pw@h/a@other.example#/r.pdf" })
      {
         boolean sftp = path.toLowerCase().startsWith("sftp://");
         FTPUtil.PathPassword split = FTPUtil.splitPassword(path, sftp);
         FTPUtil.Endpoint before = FTPUtil.parseEndpoint(path);
         FTPUtil.Endpoint after = FTPUtil.parseEndpoint(split.path());

         assertEquals(before.userInfo(), split.user() + ":" + split.password(), path);
         assertEquals(split.user(), after.userInfo(), path);
         assertEquals(before.host(), after.host(), path);
         assertEquals(before.port(), after.port(), path);
         assertEquals(before.path(), after.path(), path);
      }

      assertNull(FTPUtil.splitPassword("ftp://u@h/x", false));
      assertNull(FTPUtil.splitPassword("ftp://h/x", false));
      assertNull(FTPUtil.splitPassword("ftp://u:@h/x", false));
      assertNull(FTPUtil.splitPassword(null, false));
   }

   @Test
   void storedPasswordIsNotSentAsAnotherUser() throws Throwable {
      // Bug #77970, a task parameter in the user part of the path changed the login user, and the
      // stored password was sent with it
      ServerPathInfo info =
         new ServerPathInfo("ftp://{u}@files.corp.example/out/{0}", "alice", "alice-pw");

      try(MockedConstruction<FTPClient> ftp = mockFtp()) {
         Exception ex = assertThrows(Exception.class, () -> FTPUtil.uploadToFTP(
            "ftp://bob@files.corp.example/out/r.pdf", file(), info, false));
         assertTrue(ex.getMessage().contains("the user does not match the saved path"),
                    ex.getMessage());
         assertTrue(ftp.constructed().isEmpty());
      }

      assertTrue(logins.isEmpty());
   }

   @Test
   void storedPasswordIsNotSentAsUserAddedByParameter() throws Throwable {
      // Bug #77970, a user added to a path without one overrides the stored user name
      ServerPathInfo info =
         new ServerPathInfo("ftp://files.corp.example/{0}", "alice", "alice-pw");

      try(MockedConstruction<FTPClient> ftp = mockFtp()) {
         Exception ex = assertThrows(Exception.class, () -> FTPUtil.uploadToFTP(
            "ftp://bob@files.corp.example/r.pdf", file(), info, false));
         assertTrue(ex.getMessage().contains("the user does not match the saved path"),
                    ex.getMessage());
         assertTrue(ftp.constructed().isEmpty());
      }
   }

   @Test
   void storedPasswordIsSentAsTheSavedUser() throws Throwable {
      ServerPathInfo field = new ServerPathInfo("ftp://files.corp.example/out/{0}", "alice", "pw");
      ServerPathInfo path = new ServerPathInfo("ftp://alice@files.corp.example/out/{0}", "x", "pw");

      try(MockedConstruction<FTPClient> ftp = mockFtp()) {
         FTPUtil.uploadToFTP("ftp://files.corp.example/out/r.pdf", file(), field, false);
         FTPUtil.uploadToFTP("ftp://alice@files.corp.example/out/r.pdf", file(), path, false);
      }

      assertEquals(List.of("alice/pw", "alice/pw"), logins);
   }

   @Test
   void pathWithItsOwnPasswordDoesNotUseTheStoredOne() throws Throwable {
      // the formatted path brings its own password, the stored one is not sent
      ServerPathInfo info =
         new ServerPathInfo("ftp://{u}@files.corp.example/out/{0}", "alice", "alice-pw");

      try(MockedConstruction<FTPClient> ftp = mockFtp()) {
         FTPUtil.uploadToFTP("ftp://bob:bob-pw@files.corp.example/out/r.pdf", file(), info, false);
      }

      assertEquals(List.of("bob/bob-pw"), logins);
   }

   @Test
   void credentialIsNotSentAsAnotherUser() throws Throwable {
      // Bug #77970, the user in the path overrides the user name of the secret
      ServerPathInfo user = credentialPath("ftp://{u}@files.corp.example/out/{0}");
      ServerPathInfo none = credentialPath("ftp://files.corp.example/out/{0}");

      try(MockedStatic<Tool> tool = mockTool(); MockedConstruction<FTPClient> ftp = mockFtp()) {
         Exception ex = assertThrows(Exception.class, () -> FTPUtil.uploadToFTP(
            "ftp://bob@files.corp.example/out/r.pdf", file(), user, false));
         assertTrue(ex.getMessage().contains("the user does not match the saved path"),
                    ex.getMessage());
         ex = assertThrows(Exception.class, () -> FTPUtil.uploadToFTP(
            "ftp://bob@files.corp.example/out/r.pdf", file(), none, false));
         assertTrue(ex.getMessage().contains("the user does not match the saved path"),
                    ex.getMessage());
         assertTrue(ftp.constructed().isEmpty());
      }

      assertTrue(logins.isEmpty());
   }

   @Test
   void credentialIsSentAsTheSavedUser() throws Throwable {
      ServerPathInfo info = credentialPath("ftp://own-user@files.corp.example/out/{0}");

      try(MockedStatic<Tool> tool = mockTool(); MockedConstruction<FTPClient> ftp = mockFtp()) {
         FTPUtil.uploadToFTP("ftp://own-user@files.corp.example/out/r.pdf", file(), info, false);
      }

      assertEquals(List.of("own-user/own-password"), logins);
   }

   @Test
   void sftpStoredPasswordIsNotSentAsAnotherUser() throws Throwable {
      ServerPathInfo info =
         new ServerPathInfo("sftp://{u}@files.corp.example/out/{0}", "alice", "alice-pw");

      try(MockedConstruction<com.jcraft.jsch.JSch> jsch =
             mockConstruction(com.jcraft.jsch.JSch.class))
      {
         Exception ex = assertThrows(Exception.class, () -> FTPUtil.uploadToFTP(
            "sftp://bob@files.corp.example/out/r.pdf", file(), info, false));
         assertTrue(ex.getMessage().contains("the user does not match the saved path"),
                    ex.getMessage());
         assertTrue(jsch.constructed().isEmpty());
      }
   }

   private static ServerPathInfo credentialPath(String path) {
      ServerPathInfo info = new ServerPathInfo(path);
      info.setUseCredential(true);
      info.setSecretId(OWN_ID);
      return info;
   }

   private File file() throws Exception {
      Path path = tempDir.resolve("r.pdf");
      Files.writeString(path, "report");
      return path.toFile();
   }

   private static MockedStatic<Tool> mockTool() {
      MockedStatic<Tool> tool = mockStatic(Tool.class, CALLS_REAL_METHODS);
      // cloud stand-in: the secrets manager answers for the id
      tool.when(() -> Tool.decryptPassword(eq(OWN_ID), anyBoolean()))
         .thenReturn("{\"username\":\"own-user\",\"password\":\"own-password\"}");
      return tool;
   }

   private MockedConstruction<FTPClient> mockFtp() {
      return mockConstruction(FTPClient.class, (m, ctx) -> {
         doAnswer(inv -> {
            connected.add(inv.getArgument(0) + ":" + inv.getArgument(1));
            return null;
         }).when(m).connect(anyString(), anyInt());
         when(m.getReplyCode()).thenReturn(220);
         when(m.login(anyString(), anyString())).thenAnswer(inv -> {
            logins.add(inv.getArgument(0) + "/" + inv.getArgument(1));
            return true;
         });
         when(m.storeFile(anyString(), any())).thenReturn(true);
      });
   }

   private static final String OWN_ID = "own-secret";
   private final List<String> connected = new ArrayList<>();
   private final List<String> logins = new ArrayList<>();

   @TempDir
   Path tempDir;
}
