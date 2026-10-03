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
import inetsoft.report.io.viewsheet.html.HTMLVSExporter;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.ExternalStorageService;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77621: exportBookmarks() disposed each bookmark sandbox right after its export(), but
 * every caller calls exporter.write() only after exportBookmarks() returns. A print-layout PDF
 * paints in write(), and its table highlights evaluate script condition values against the
 * bookmark sandbox, so the sandboxes must be disposed only after write(). Drives the scheduled
 * email and save-to-server paths of runViewsheetAction(), including the excel->CSV second pass.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  ViewsheetActionBookmarkDisposeOrderTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetActionBookmarkDisposeOrderTest {
   @Configuration
   static class TestConfig {
      @Bean
      public ExternalStorageService externalStorageService() {
         return mock(ExternalStorageService.class);
      }
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void pdf_disposesEveryBookmarkSandboxAfterWrite(boolean save, @TempDir Path dir)
      throws Throwable
   {
      VSExporter pdf = mock(VSExporter.class);
      ViewsheetAction action = action(save, FileFormatInfo.EXPORT_TYPE_PDF, dir);
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_PDF, pdf);

      try(Harness harness = new Harness(false)) {
         run(action);
         List<ViewsheetSandbox> boxes = harness.boxes.constructed();

         assertEquals(2, boxes.size());
         assertWrittenBeforeDisposed(pdf, boxes);
      }
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void excelToCsv_disposesEachPassAfterItsOwnWrite(boolean save, @TempDir Path dir)
      throws Throwable
   {
      VSExporter excel = mock(VSExporter.class);
      VSExporter csv = mock(VSExporter.class);
      ViewsheetAction action = action(save, FileFormatInfo.EXPORT_TYPE_EXCEL, dir);
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_EXCEL, excel);
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_CSV, csv);

      try(Harness harness = new Harness(true)) {
         run(action);
         List<ViewsheetSandbox> boxes = harness.boxes.constructed();

         assertEquals(4, boxes.size(), "the CSV pass exports the bookmarks again");
         assertWrittenBeforeDisposed(excel, boxes.subList(0, 2));
         assertWrittenBeforeDisposed(csv, boxes.subList(2, 4));

         // the first pass's sandboxes are released before the CSV pass starts
         for(ViewsheetSandbox box : boxes.subList(0, 2)) {
            InOrder order = inOrder(box, csv);
            order.verify(box).dispose();
            order.verify(csv).export(same(boxes.get(2)), anyString(), any());
         }
      }
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void writeThrows_stillDisposesEveryBookmarkSandbox(boolean save, @TempDir Path dir)
      throws Throwable
   {
      VSExporter pdf = mock(VSExporter.class);
      doThrow(new IllegalStateException("sentinel77621")).when(pdf).write();
      ViewsheetAction action = action(save, FileFormatInfo.EXPORT_TYPE_PDF, dir);
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_PDF, pdf);

      try(Harness harness = new Harness(false)) {
         Throwable ex = assertThrows(Throwable.class, () -> run(action));
         assertEquals("sentinel77621", rootCause(ex).getMessage());
         List<ViewsheetSandbox> boxes = harness.boxes.constructed();

         assertEquals(2, boxes.size());
         assertWrittenBeforeDisposed(pdf, boxes);
      }
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void exportThrows_disposesSandboxesCreatedSoFar(boolean save, @TempDir Path dir)
      throws Throwable
   {
      VSExporter pdf = mock(VSExporter.class);
      // the second bookmark's export throws
      doNothing().doThrow(new IllegalStateException("sentinel77621"))
         .when(pdf).export(any(ViewsheetSandbox.class), anyString(), any());
      ViewsheetAction action = action(save, FileFormatInfo.EXPORT_TYPE_PDF, dir);
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_PDF, pdf);

      try(Harness harness = new Harness(false)) {
         Throwable ex = assertThrows(Throwable.class, () -> run(action));
         assertEquals("sentinel77621", rootCause(ex).getMessage());
         List<ViewsheetSandbox> boxes = harness.boxes.constructed();

         assertEquals(2, boxes.size());
         verify(pdf, never()).write();

         for(ViewsheetSandbox box : boxes) {
            verify(box).dispose();
         }
      }
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void html_disposesTheOnlyExportedSandboxAfterWrite(boolean save, @TempDir Path dir)
      throws Throwable
   {
      // Bug #61272: HTML exports only the first bookmark
      HTMLVSExporter html = mock(HTMLVSExporter.class);
      ViewsheetAction action = action(save, FileFormatInfo.EXPORT_TYPE_HTML, dir);
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_HTML, html);

      try(Harness harness = new Harness(false)) {
         run(action);
         List<ViewsheetSandbox> boxes = harness.boxes.constructed();

         assertEquals(1, boxes.size());
         assertWrittenBeforeDisposed(html, boxes);
      }
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void pdf_neverDisposesTheRuntimeSandbox(boolean save, @TempDir Path dir) throws Throwable {
      VSExporter pdf = mock(VSExporter.class);
      ViewsheetAction action = action(save, FileFormatInfo.EXPORT_TYPE_PDF, dir);
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_PDF, pdf);
      ViewsheetSandbox liveBox =
         action.getRuntimeViewsheet(null, null).getViewsheetSandbox().orElseThrow();

      try(Harness harness = new Harness(false)) {
         run(action);

         assertEquals(2, harness.boxes.constructed().size());
         verify(liveBox, never()).dispose();
      }
   }

   @Test
   void throwBeforeCsvPass_firstPassAlreadyDisposed(@TempDir Path dir) throws Throwable {
      VSExporter excel = mock(VSExporter.class);
      ViewsheetAction action = action(false, FileFormatInfo.EXPORT_TYPE_EXCEL, dir);
      stubExporter(action, FileFormatInfo.EXPORT_TYPE_EXCEL, excel);
      // the CSV pass fails to start, after the Excel pass has written
      doThrow(new IllegalStateException("sentinel77621")).when(action)
         .getVSExporter(eq(FileFormatInfo.EXPORT_TYPE_CSV), any(OutputStream.class), any());

      try(Harness harness = new Harness(true)) {
         try {
            run(action);
         }
         catch(Throwable ex) {
            assertEquals("sentinel77621", rootCause(ex).getMessage());
         }

         verify(action).getVSExporter(eq(FileFormatInfo.EXPORT_TYPE_CSV),
                                      any(OutputStream.class), any());
         List<ViewsheetSandbox> boxes = harness.boxes.constructed();

         assertEquals(2, boxes.size());
         assertWrittenBeforeDisposed(excel, boxes);
      }
   }

   @Test
   void alertFilteredBookmarks_createNoSandbox() throws Throwable {
      VSExporter pdf = mock(VSExporter.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getOriginalBookmark(anyString(), any())).thenAnswer(inv -> mock(Viewsheet.class));
      VSBookmarkInfo[] bookmarks = { bookmark("b1"), bookmark("b2"), bookmark("b3") };
      Method method = ViewsheetAction.class.getDeclaredMethod(
         "exportBookmarksAndWrite", VSExporter.class, RuntimeViewsheet.class,
         VariableTable.class, VSBookmarkInfo[].class, List.class);
      method.setAccessible(true);

      try(MockedConstruction<ViewsheetSandbox> boxes = mockConstruction(ViewsheetSandbox.class)) {
         // only b2 passed the alert condition
         method.invoke(new ViewsheetAction(), pdf, rvs, new VariableTable(), bookmarks,
                       List.of("b2"));

         assertEquals(1, boxes.constructed().size());
         verify(pdf, times(1)).export(any(ViewsheetSandbox.class), anyString(), any());
         verify(rvs).getOriginalBookmark(startsWith("b2"), any());
         verify(rvs, never()).getOriginalBookmark(startsWith("b1"), any());
         verify(rvs, never()).getOriginalBookmark(startsWith("b3"), any());
         assertWrittenBeforeDisposed(pdf, boxes.constructed());
      }
   }

   private static void assertWrittenBeforeDisposed(VSExporter exporter,
                                                   List<ViewsheetSandbox> boxes)
      throws Exception
   {
      for(ViewsheetSandbox box : boxes) {
         InOrder order = inOrder(exporter, box);
         order.verify(exporter).export(same(box), anyString(), any());
         order.verify(exporter).write();
         order.verify(box).dispose();
         verify(box, times(1)).dispose();
      }
   }

   private static void stubExporter(ViewsheetAction action, int type, VSExporter exporter) {
      doReturn(exporter).when(action).getVSExporter(eq(type), any(OutputStream.class), any());
   }

   private static ViewsheetAction action(boolean save, int format, Path dir) throws Throwable {
      ViewsheetAction action = spy(new ViewsheetAction());
      action.setBookmarks(new String[] { "b1", "b2" });
      action.setBookmarkTypes(new int[] { VSBookmarkInfo.ALLSHARE, VSBookmarkInfo.ALLSHARE });
      action.setBookmarkUsers(new IdentityID[] { OWNER, OWNER });

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug77621", null,
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
      when(rvs.getID()).thenReturn("rvs77621");
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getOriginalBookmark(anyString(), any())).thenAnswer(inv -> mock(Viewsheet.class));
      doReturn(rvs).when(action).getRuntimeViewsheet(any(), any());

      if(save) {
         doReturn("").when(action).getScheduleEmails(any());
         action.setFilePath(format, new ServerPathInfo(
            dir.resolve("out77621").toString().replace('\\', '/')));
      }
      else {
         doReturn("a@b.c").when(action).getScheduleEmails(any());
         doReturn(format).when(action).getFileType();
      }

      return action;
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

   private static Throwable rootCause(Throwable ex) {
      while(ex.getCause() != null && ex.getCause() != ex) {
         ex = ex.getCause();
      }

      return ex;
   }

   /**
    * Mocks the bookmark lookup, the bookmark sandboxes, the large-table check and the mail
    * service. The bookmark sandboxes are mocks, so no query runs.
    */
   private static final class Harness implements AutoCloseable {
      Harness(boolean largeTable) {
         VSBookmarkInfo[] infos = { bookmark("b1"), bookmark("b2") };
         vsUtil = mockStatic(VSUtil.class, CALLS_REAL_METHODS);
         vsUtil.when(() -> VSUtil.getBookmarks(any(AssetEntry.class), any())).thenReturn(infos);
         csvUtil = mockStatic(CSVUtil.class, CALLS_REAL_METHODS);
         csvUtil.when(() -> CSVUtil.hasLargeDataTable(any())).thenReturn(largeTable);
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

   private static VSBookmarkInfo bookmark(String name) {
      VSBookmarkInfo bookmark = mock(VSBookmarkInfo.class);
      when(bookmark.getName()).thenReturn(name);
      when(bookmark.getType()).thenReturn(VSBookmarkInfo.ALLSHARE);
      when(bookmark.getOwner()).thenReturn(OWNER);
      return bookmark;
   }

   private static final IdentityID OWNER = new IdentityID("admin", "host-org");
}
