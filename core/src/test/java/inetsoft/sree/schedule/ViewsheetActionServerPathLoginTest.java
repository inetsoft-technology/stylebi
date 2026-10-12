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

import inetsoft.sree.RepletRequest;
import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77970: a save-to-server path is formatted from the task parameters before it is uploaded,
 * and a parameter value must not choose the user that the stored password is sent as.
 */
@Tag("core")
class ViewsheetActionServerPathLoginTest {
   @Test
   void parameterInUserPartDoesNotSendStoredPassword() throws Throwable {
      ServerPathInfo info =
         new ServerPathInfo("ftp://{u}@files.corp.example/out/r", "alice", "alice-pw");

      for(String u : new String[] { "bob", "alice" }) {
         String url = format(info, Map.of("u", u));
         assertEquals("ftp://" + u + "@files.corp.example/out/r.pdf", url);
         assertThrows(Exception.class, () -> upload(url, info), u);
      }

      assertTrue(logins.isEmpty(), logins.toString());
   }

   @Test
   void parameterInDirectoryCannotChangeUser() throws Throwable {
      ServerPathInfo info =
         new ServerPathInfo("ftp://alice@files.corp.example/out/{d}/r", "alice", "alice-pw");

      upload(format(info, Map.of("d", "x")), info);
      assertEquals(List.of("alice/alice-pw"), logins);

      // '@' is not escaped in a parameter value, so it moves the user part
      String url = format(info, Map.of("d", "x@files.corp.example"));
      assertEquals("files.corp.example", FTPUtil.parseEndpoint(url).host());
      assertThrows(Exception.class, () -> upload(url, info));
      assertEquals(List.of("alice/alice-pw"), logins);
   }

   // the same steps that ViewsheetAction.run() takes before it calls FTPUtil.uploadToFTP()
   private static String format(ServerPathInfo info, Map<String, Object> params)
      throws Exception
   {
      ViewsheetAction action = new ViewsheetAction("vs", new RepletRequest(new HashMap<>(params)));
      Object[] msgParams = { "vs", new Date(), "alice" };
      Method getPath =
         ViewsheetAction.class.getDeclaredMethod("getPath", String.class, Object[].class);
      getPath.setAccessible(true);
      String path = (String) getPath.invoke(action, info.getPath(), msgParams);
      path = MessageFormat.format(path, msgParams);
      return MessageFormat.format(path + ".pdf", msgParams);
   }

   private void upload(String url, ServerPathInfo info) throws Throwable {
      Path path = tempDir.resolve("r.pdf");
      Files.writeString(path, "report");
      File file = path.toFile();

      try(MockedConstruction<FTPClient> ftp = mockConstruction(FTPClient.class, (m, ctx) -> {
         when(m.getReplyCode()).thenReturn(220);
         when(m.login(anyString(), anyString())).thenAnswer(inv -> {
            logins.add(inv.getArgument(0) + "/" + inv.getArgument(1));
            return true;
         });
         when(m.storeFile(anyString(), any())).thenReturn(true);
      }))
      {
         FTPUtil.uploadToFTP(url, file, info, false);
      }
   }

   private final List<String> logins = new ArrayList<>();

   @TempDir
   Path tempDir;
}
