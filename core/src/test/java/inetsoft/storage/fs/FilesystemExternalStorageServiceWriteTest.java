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
package inetsoft.storage.fs;

/*
 * Bug #77137: FilesystemExternalStorageService.write(String, Path) resolved the key against its
 * folder without a check, so an absolute key or one with ".." segments was written outside of
 * the external storage folder.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.*;
import java.security.Principal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class FilesystemExternalStorageServiceWriteTest {
   @BeforeEach
   void setUp() throws IOException {
      base = Files.createDirectories(tempDir.resolve("outer").resolve("base"));
      source = Files.writeString(tempDir.resolve("source.pdf"), "x");
      service = new FilesystemExternalStorageService(base);
   }

   @Test
   void relativeKey_writtenInsideBase() throws IOException {
      service.write("orgA/alice/f.pdf", source);

      assertTrue(Files.exists(base.resolve("orgA/alice/f.pdf")));
   }

   @ParameterizedTest
   @ValueSource(strings = { "../escaped.pdf", "orgA/alice/../../../escaped.pdf", "/escaped.pdf" })
   void keyOutsideBase_rejectedAndNothingWritten(String key) throws IOException {
      IOException thrown = assertThrows(IOException.class, () -> service.write(key, source));

      assertFalse(thrown.getMessage().contains(base.toString()),
                  "the message must not contain the server path");
      assertEquals(List.of(), filesOutsideBase());
   }

   @Test
   void keyNamingBase_rejected() {
      assertThrows(IOException.class, () -> service.write("orgA/..", source));
   }

   @Test
   void absoluteLocation_principalWrite_staysInsideBase() throws IOException {
      // the Linux shape: the root relative path must match the absolute location, and the key
      // must not be absolute
      Principal alice = () -> new IdentityID("alice", "orgA").convertToKey();

      try(MockedStatic<SUtil> sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
          MockedStatic<SreeEnv> sreeEnvStatic = mockStatic(SreeEnv.class))
      {
         sUtilStatic.when(SUtil::isSecurityOn).thenReturn(true);
         sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
         sUtilStatic.when(() -> SUtil.isInternalUser(any())).thenReturn(false);
         sreeEnvStatic.when(() -> SreeEnv.getProperty(anyString())).thenAnswer(
            i -> "server.save.locations".equals(i.getArgument(0)) ? "/var/reports|Var" : null);

         service.write(base.getRoot().resolve("var/reports/sub/f.pdf").toString(), source, alice);
      }

      List<Path> written;

      try(Stream<Path> files = Files.walk(base)) {
         written = files.filter(p -> p.getFileName().toString().equals("f.pdf")).toList();
      }

      assertEquals(1, written.size());
      assertEquals(List.of(), filesOutsideBase());

      if(!"\\".equals(base.getFileSystem().getSeparator())) {
         assertEquals(base.resolve("var/reports/orgA/alice/sub/f.pdf"), written.get(0));
      }
   }

   private List<Path> filesOutsideBase() throws IOException {
      try(Stream<Path> files = Files.walk(tempDir)) {
         return files.filter(Files::isRegularFile)
            .filter(p -> !p.startsWith(base) && !p.equals(source))
            .toList();
      }
   }

   @TempDir
   Path tempDir;
   private Path base;
   private Path source;
   private FilesystemExternalStorageService service;
}
