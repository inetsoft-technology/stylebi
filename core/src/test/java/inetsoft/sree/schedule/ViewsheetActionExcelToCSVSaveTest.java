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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.ExternalStorageService;
import inetsoft.storage.fs.FilesystemExternalStorageService;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79410: a scheduled save to server of an Excel export of a dashboard with a table over
 * the Excel row limit (saved as a zip of CSV files) built the zip at the raw target path and
 * uploaded a bare file name, so the upload failed with NoSuchFileException and the zip was left
 * outside the external storage. Drives the real save-to-server branch of runViewsheetAction()
 * with a FilesystemExternalStorageService rooted in a temp dir.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  ViewsheetActionExcelToCSVSaveTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetActionExcelToCSVSaveTest {
   @Configuration
   static class TestConfig {
      @Bean
      public ExternalStorageService externalStorageService() {
         return mock(ExternalStorageService.class);
      }
   }

   @BeforeEach
   void setUp(@TempDir Path storageRoot) throws Exception {
      this.storageRoot = storageRoot;
      reset(storage);
      FilesystemExternalStorageService fs = new FilesystemExternalStorageService(storageRoot);
      doAnswer(inv -> {
         fs.write(inv.getArgument(0, String.class), inv.getArgument(1, Path.class),
                  inv.getArgument(2, Principal.class));
         return null;
      }).when(storage).write(anyString(), any(Path.class), any());
   }

   @Test
   void excelToCsv_savesTheZipFromTheTempDirUnderAZipKey(@TempDir Path out) throws Throwable {
      // the reported case: the target folder exists on the scheduler's local file system
      Path reports = Files.createDirectories(out.resolve("reports"));
      ViewsheetAction action = action(reports.resolve("task").toString().replace('\\', '/'));

      try(Harness ignored = new Harness()) {
         run(action);
      }

      Path stored = storedFile("task.zip");
      assertArrayEquals(ZIP_CONTENT, Files.readAllBytes(stored));
      assertEquals(List.of(stored), storedFiles(), "only the zip is stored");
      assertFalse(Files.exists(reports.resolve("task.zip")),
                  "nothing is written at the raw target path");
      assertEquals(List.of(), listFiles(reports));
   }

   @Test
   void excelToCsv_relativeTarget_writesNothingToTheWorkingDirectory() throws Throwable {
      String folder = "bug79410-" + UUID.randomUUID();
      ViewsheetAction action = action(folder + "/task");

      try(Harness ignored = new Harness()) {
         run(action);
      }
      finally {
         Path leaked = Paths.get(folder);

         if(Files.exists(leaked)) {
            for(Path p : listFiles(leaked)) {
               Files.deleteIfExists(p);
            }

            Files.deleteIfExists(leaked);
            fail("a file was written to the working directory: " + leaked.toAbsolutePath());
         }
      }

      Path stored = storedFile("task.zip");
      assertTrue(stored.getParent().endsWith(folder), stored.toString());
      assertArrayEquals(ZIP_CONTENT, Files.readAllBytes(stored));
   }

   private Path storedFile(String name) throws Exception {
      List<Path> files = storedFiles();
      return files.stream().filter(p -> p.getFileName().toString().equals(name)).findFirst()
         .orElseThrow(() -> new AssertionError(name + " not stored, storage has " + files));
   }

   private List<Path> storedFiles() throws Exception {
      try(Stream<Path> stream = Files.walk(storageRoot)) {
         return stream.filter(Files::isRegularFile).toList();
      }
   }

   private static List<Path> listFiles(Path dir) throws Exception {
      try(Stream<Path> stream = Files.list(dir)) {
         return stream.toList();
      }
   }

   private static ViewsheetAction action(String target) throws Throwable {
      ViewsheetAction action = spy(new ViewsheetAction());
      action.setBookmarks(new String[] { "b1" });
      action.setBookmarkTypes(new int[] { VSBookmarkInfo.ALLSHARE });
      action.setBookmarkUsers(new IdentityID[] { OWNER });

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug79410", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      doReturn(entry).when(action).buildAssetEntry(any());
      doReturn(entry).when(action).getViewsheetEntry();

      AssetQuerySandbox liveAbox = mock(AssetQuerySandbox.class);
      when(liveAbox.getVariableTable()).thenReturn(new VariableTable());
      ViewsheetSandbox liveBox = mock(ViewsheetSandbox.class);
      when(liveBox.getVariableTable()).thenReturn(new VariableTable());
      when(liveBox.getAssetQuerySandbox()).thenReturn(liveAbox);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssemblies()).thenReturn(new Assembly[0]);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("rvs79410");
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getOriginalBookmark(anyString(), any())).thenAnswer(inv -> mock(Viewsheet.class));
      doReturn(rvs).when(action).getRuntimeViewsheet(any(), any());

      doReturn("").when(action).getScheduleEmails(any());
      action.setFilePath(FileFormatInfo.EXPORT_TYPE_EXCEL, new ServerPathInfo(target));

      // each exporter writes its bytes to the stream it was created with
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_EXCEL, XLSX_CONTENT);
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_CSV, ZIP_CONTENT);
      return action;
   }

   private static void stubExporter(ViewsheetAction action, int type, byte[] content) {
      doAnswer(inv -> {
         OutputStream stream = inv.getArgument(1);
         VSExporter exporter = mock(VSExporter.class);
         doAnswer(w -> {
            stream.write(content);
            stream.flush();
            return null;
         }).when(exporter).write();
         return exporter;
      }).when(action).getVSExporter(eq(type), any(OutputStream.class), any());
   }

   private static void run(ViewsheetAction action) throws Throwable {
      Method method = ViewsheetAction.class.getDeclaredMethod(
         "runViewsheetAction", Principal.class, ScheduleViewsheetService.class);
      method.setAccessible(true);

      try {
         method.invoke(action, new XPrincipal(OWNER), mock(ScheduleViewsheetService.class));
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause();
      }
   }

   /**
    * Mocks the bookmark lookup, the bookmark sandboxes, the large-table check (true, so the
    * Excel export is converted to a zip of CSV files) and the mail service.
    */
   private static final class Harness implements AutoCloseable {
      Harness() {
         VSBookmarkInfo bookmark = mock(VSBookmarkInfo.class);
         when(bookmark.getName()).thenReturn("b1");
         when(bookmark.getType()).thenReturn(VSBookmarkInfo.ALLSHARE);
         when(bookmark.getOwner()).thenReturn(OWNER);
         vsUtil = mockStatic(VSUtil.class, CALLS_REAL_METHODS);
         vsUtil.when(() -> VSUtil.getBookmarks(any(AssetEntry.class), any()))
            .thenReturn(new VSBookmarkInfo[] { bookmark });
         csvUtil = mockStatic(CSVUtil.class, CALLS_REAL_METHODS);
         csvUtil.when(() -> CSVUtil.hasLargeDataTable(any())).thenReturn(true);
         boxes = mockConstruction(ViewsheetSandbox.class);
         mail = mockConstruction(DefaultScheduleMailService.class);
      }

      @Override
      public void close() {
         mail.close();
         boxes.close();
         csvUtil.close();
         vsUtil.close();
      }

      final MockedStatic<VSUtil> vsUtil;
      final MockedStatic<CSVUtil> csvUtil;
      final MockedConstruction<ViewsheetSandbox> boxes;
      final MockedConstruction<DefaultScheduleMailService> mail;
   }

   @Autowired
   private ExternalStorageService storage;
   private Path storageRoot;

   private static final IdentityID OWNER = new IdentityID("admin", "host-org");
   private static final byte[] XLSX_CONTENT = "xlsx-79410".getBytes(StandardCharsets.UTF_8);
   private static final byte[] ZIP_CONTENT = "zip-79410".getBytes(StandardCharsets.UTF_8);
}
