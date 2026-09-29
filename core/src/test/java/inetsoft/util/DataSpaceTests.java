/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util;

import inetsoft.test.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataSpaceTests {
   @ParameterizedTest(name = "should get correct path [{index}] with dir ''{0}'' and file ''{1}''")
   @MethodSource
   void shouldGetCorrectPath(String dir, String file, String expected) {
      DataSpace dataSpace = DataSpace.getDataSpace();
      assertEquals(expected, dataSpace.getPath(dir, file));
   }

   static Stream<Arguments> shouldGetCorrectPath() {
      String home = ConfigurationContext.getContext().getHome();
      System.err.println("Testing with home: " + home);

      return Stream.of(
         Arguments.of(null, "file.txt", "file.txt"),
         Arguments.of("", "file.txt", "file.txt"),
         Arguments.of("/", "file.txt", "file.txt"),
         Arguments.of("/folder", "file.txt", "folder/file.txt"),
         Arguments.of("folder/", "file.txt", "folder/file.txt"),
         Arguments.of("/folder/", "file.txt", "folder/file.txt"),
         Arguments.of("folder1/folder2", "file.txt", "folder1/folder2/file.txt"),
         Arguments.of("/folder1/folder2", "file.txt", "folder1/folder2/file.txt"),
         Arguments.of("folder1/folder2/", "file.txt", "folder1/folder2/file.txt"),
         Arguments.of("/folder1/folder2/", "file.txt", "folder1/folder2/file.txt"),
         Arguments.of(null, "/file.txt", "file.txt"),
         Arguments.of(null, "folder/file.txt", "folder/file.txt"),
         Arguments.of(null, "/folder/file.txt", "folder/file.txt"),
         Arguments.of("", "/file.txt", "file.txt"),
         Arguments.of("", "folder/file.txt", "folder/file.txt"),
         Arguments.of("", "/folder/file.txt", "folder/file.txt"),
         Arguments.of("/", "folder/file.txt", "folder/file.txt"),
         Arguments.of("/", "/folder/file.txt", "folder/file.txt"),
         Arguments.of("/", "/file.txt", "file.txt"),
         Arguments.of("folder1", "folder3/file.txt", "folder1/folder3/file.txt"),
         Arguments.of("folder1", "folder3/file.txt", "folder1/folder3/file.txt"),
         Arguments.of("folder1", "/folder3/file.txt", "folder1/folder3/file.txt"),
         Arguments.of("folder1/folder2", "folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("folder1/folder2", "folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("folder1/folder2", "/folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("/folder1/folder2", "folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("/folder1/folder2", "folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("/folder1/folder2", "/folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("folder1/folder2/", "folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("folder1/folder2/", "folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("folder1/folder2/", "/folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("/folder1/folder2/", "folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("/folder1/folder2/", "folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("/folder1/folder2/", "/folder3/file.txt", "folder1/folder2/folder3/file.txt"),
         Arguments.of("file.txt", null, "file.txt"),
         Arguments.of("file.txt", "", "file.txt"),
         Arguments.of("file.txt", "/", "file.txt"),
         Arguments.of(home, "file.txt", "file.txt"),
         Arguments.of(null, home + "/templates//report.srt", "templates/report.srt"),
         // Bug #75321: the unresolved "$(sree.home)" placeholder (from fs.files /
         // fs.bs.files defaults) must be normalized like the resolved home so it
         // doesn't create a literal "$(sree.home)" node in the data space.
         Arguments.of(null, "$(sree.home)/fs.xml", "fs.xml"),
         Arguments.of(null, "$(sree.home)/organization0/fs.xml", "organization0/fs.xml"),
         Arguments.of("$(sree.home)", "bs.xml", "bs.xml"),
         Arguments.of("$(sree.home)/organization0", "bs.xml", "organization0/bs.xml"),
         // placeholder-only directory with no file strips to an empty path
         Arguments.of("$(sree.home)", null, ""),
         // placeholder immediately followed by the file name (no separator slash)
         Arguments.of(null, "$(sree.home)fs.xml", "fs.xml")
      );
   }

   /**
    * Bug #77339: the registry save staleness token is the digest DataSpace reports. It must be the
    * lower-case hex MD5 of the content, null for a missing file or a directory, and independent of
    * the commit time.
    */
   @Test
   void getDigestIdentifiesContentNotCommitTime() throws Exception {
      DataSpace space = DataSpace.getDataSpace();
      String dir = "test77339-digest";
      String file = "content.txt";
      byte[] first = "first".getBytes(StandardCharsets.UTF_8);
      byte[] second = "second".getBytes(StandardCharsets.UTF_8);

      try {
         assertNull(space.getDigest(dir, file), "missing file");
         space.withOutputStream(dir, file, out -> out.write(first));
         String digest = space.getDigest(dir, file);
         assertEquals(md5(first), digest);
         assertTrue(space.isDirectory(dir), "the parent must be a stored directory");
         assertNull(space.getDigest(null, dir), "directory");

         long lastModified = space.getLastModified(dir, file);
         space.withOutputStream(dir, file, lastModified, out -> out.write(second));
         assertEquals(lastModified, space.getLastModified(dir, file));
         assertEquals(md5(second), space.getDigest(dir, file),
                      "a commit of other content in the same millisecond must change the digest");

         space.withOutputStream(dir, file, lastModified + 1000, out -> out.write(first));
         assertEquals(digest, space.getDigest(dir, file),
                      "the same content committed at another time must keep the digest");
      }
      finally {
         space.delete(dir, file);
         space.delete(null, dir);
      }
   }

   private static String md5(byte[] content) throws Exception {
      return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(content));
   }
}
