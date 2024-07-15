/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import inetsoft.test.SreeHome;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SreeHome
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
         Arguments.of(null, home + "/templates//report.srt", "templates/report.srt")
      );
   }
}