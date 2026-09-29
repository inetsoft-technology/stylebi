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
