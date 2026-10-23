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
package inetsoft.web.admin.ai.repository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class AdminAssetImportControllerTest {
   @Test
   void acceptsZipWithRootJarFileInfo() throws Exception {
      assertDoesNotThrow(() ->
         AdminAssetImportController.requireExportPackage(zip("JarFileInfo.xml", "other.txt")));
   }

   @Test
   void refusesZipWithoutJarFileInfo() throws Exception {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         AdminAssetImportController.requireExportPackage(zip("readme.txt")));
      assertTrue(ex.getMessage().contains("not a StyleBI export package"));
   }

   @Test
   void refusesJarFileInfoNestedInAFolder() throws Exception {
      assertThrows(IllegalArgumentException.class, () ->
         AdminAssetImportController.requireExportPackage(zip("sub/JarFileInfo.xml")));
   }

   @Test
   void refusesNonZipBytes() {
      assertThrows(IllegalArgumentException.class, () ->
         AdminAssetImportController.requireExportPackage(
            "not a zip".getBytes(StandardCharsets.UTF_8)));
   }

   private static byte[] zip(String... names) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ZipOutputStream out = new ZipOutputStream(bytes)) {
         for(String name : names) {
            out.putNextEntry(new ZipEntry(name));
            out.write("x".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
         }
      }

      return bytes.toByteArray();
   }
}
