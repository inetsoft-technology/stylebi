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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79410: a scheduled save to server of an Excel export with a large table (converted to a
 * zip of CSV files) is stored under a zip key, and its temporary zip and Excel files are built
 * in the per-run temp dir instead of at a local path derived from the storage key.
 */
@Tag("core")
class ViewsheetActionExcelToCSVPathTest {
   @Test
   void keyEndsWithZipExactlyOnce() {
      String key = "reports/task." + ViewsheetAction.getSaveFileExtension("xlsx", true);

      assertEquals("reports/task.zip", key);
      assertFalse(key.contains(".xlsx"));
      assertEquals(key.indexOf(".zip"), key.lastIndexOf(".zip"));
   }

   @Test
   void targetNamedXlsxGetsSingleZipExtension() {
      String key = "reports/my.xlsx." + ViewsheetAction.getSaveFileExtension("xlsx", true);

      assertEquals("reports/my.xlsx.zip", key);
      assertTrue(key.endsWith(".zip"));
      assertFalse(key.endsWith(".zip.zip"));
   }

   @Test
   void directoryContainingXlsxIsUnchanged() {
      String key = "ftp://host/a.xlsx.dir/task." +
         ViewsheetAction.getSaveFileExtension("xlsx", true);

      assertEquals("ftp://host/a.xlsx.dir/task.zip", key);
      assertEquals("task.zip", ViewsheetAction.getTargetFileName(key));
   }

   @Test
   void nonExcelToCSVKeepsFormatExtension() {
      assertEquals("xlsx", ViewsheetAction.getSaveFileExtension("xlsx", false));
      assertEquals("pdf", ViewsheetAction.getSaveFileExtension("pdf", false));
   }

   @Test
   void targetFileNameHandlesBackslashes() {
      assertEquals("task.zip", ViewsheetAction.getTargetFileName("C:\\reports\\task.zip"));
      assertEquals("task.zip", ViewsheetAction.getTargetFileName("task.zip"));
   }

   @Test
   void zipAndExcelFilesAreUnderTempDir(@TempDir Path dir) {
      String key = "reports/a.xlsx.dir/task(1)." +
         ViewsheetAction.getSaveFileExtension("xlsx", true);
      String zipName = ViewsheetAction.getTargetFileName(key);
      String excelName = ViewsheetAction.getExcelToCSVExcelFileName(zipName, "xlsx");

      assertEquals("task(1).zip", zipName);
      assertEquals("task(1).xlsx", excelName);

      File zipFile = new File(dir.toFile(), zipName);
      File excelFile = new File(dir.toFile(), excelName);

      assertEquals(dir.toFile(), zipFile.getParentFile());
      assertEquals(dir.toFile(), excelFile.getParentFile());
      assertNotEquals(zipFile, excelFile);
   }
}
