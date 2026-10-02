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
package inetsoft.web.viewsheet.service;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.internal.Mailer;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.FileSystemService;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextInputVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77180: {@link VSEmailService#createSandbox} builds the sandbox used to email a bookmark.
 * It used the constructor reset, which never runs the root viewsheet's onLoad, so the onLoad only
 * ran later through {@code prepareSheet -> prepareForExport()} on the canvas path. The PDF
 * print-layout path (own or inherited from a thin wrapper's child) skips that, so emailed
 * bookmarks lost the root onLoad. createSandbox now mirrors bookmark export in
 * {@code VSExportService}: onInit once, onLoad in the reset, and input-assembly variables cleared
 * before the reset so bookmark-restored selections are not overwritten (Bug #74212).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSEmailServiceCreateSandboxTest {
   @Test
   void createSandbox_runsRootOnLoadAndOnInitOnce() throws Exception {
      Viewsheet bookmark = newBookmark(new Worksheet());
      bookmark.getViewsheetInfo().setScriptEnabled(true);
      bookmark.getViewsheetInfo().setOnInit(
         "parameter.onInit77180 = parameter.onInit77180 ? parameter.onInit77180 + 'x' : 'x';");
      bookmark.getViewsheetInfo().setOnLoad("parameter.onLoad77180 = 'ran';");

      ViewsheetSandbox box = createSandbox(bookmark);

      try {
         VariableTable vars = box.getVariableTable();
         assertEquals("ran", vars.get("onLoad77180"),
            "the root viewsheet's onLoad must run while building the email bookmark sandbox, " +
            "not only later in prepareForExport(), which the PDF print-layout path skips");
         assertEquals("x", vars.get("onInit77180"), "onInit must run exactly once");
      }
      finally {
         box.dispose();
      }
   }

   @Test
   void createSandbox_keepsBookmarkRestoredInputValueOverWorksheetDefault() throws Exception {
      // A worksheet variable with a default value feeds the sandbox variable table under the
      // same name as an input assembly. Without the 74212 clearing, applyParameterToInput()
      // would overwrite the value restored from the bookmark with the worksheet default.
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly varAssembly = new DefaultVariableAssembly(ws, "TextInput1");
      AssetVariable var = new AssetVariable("TextInput1");
      // The Object cast selects createValueNode(Object value, String name); the
      // (String, String) overload means (name, type) and would leave the value null.
      var.setValueNode(XValueNode.createValueNode((Object) "wsDefault", "TextInput1"));
      varAssembly.setVariable(var);
      ws.addAssembly(varAssembly);

      Viewsheet bookmark = newBookmark(ws);
      TextInputVSAssembly input = new TextInputVSAssembly(bookmark, "TextInput1");
      input.setSelectedObject("bookmarked");
      bookmark.addAssembly(input);

      ViewsheetSandbox box = createSandbox(bookmark);

      try {
         TextInputVSAssembly restored =
            (TextInputVSAssembly) box.getViewsheet().getAssembly("TextInput1");
         assertEquals("bookmarked", restored.getSelectedObject(),
            "the bookmark-restored input value must win over the worksheet variable default, " +
            "the same as bookmark export (VSExportService, Bug #74212)");
      }
      finally {
         box.dispose();
      }
   }

   @Test
   void createSandbox_liveVariableReachesOnLoad() throws Exception {
      // Bug #77246: a URL/prompted parameter lives only in the viewer's sandbox variable table.
      // Bookmark export copies it into the bookmark sandbox before onInit/onLoad; email must too.
      Viewsheet bookmark = newBookmark(new Worksheet());
      bookmark.getViewsheetInfo().setScriptEnabled(true);
      bookmark.getViewsheetInfo().setOnLoad(
         "parameter.seen77246 = parameter.region77246 ? parameter.region77246 : 'MISSING';");
      VariableTable liveVars = new VariableTable();
      liveVars.put("region77246", "East");

      ViewsheetSandbox box = createSandbox(bookmark, liveVars);

      try {
         VariableTable vars = box.getVariableTable();
         assertEquals("East", vars.get("region77246"),
            "the viewer's variables must be copied into the email bookmark sandbox, " +
            "the same as bookmark export (VSExportService)");
         assertEquals("East", vars.get("seen77246"),
            "the bookmark's onLoad must see the viewer's variables");
      }
      finally {
         box.dispose();
      }
   }

   @Test
   void createSandbox_keepsBookmarkRestoredInputValueOverLiveVariable() throws Exception {
      // Bug #77246 with #74212: the live table also holds the input's key with the viewer's
      // current value. The input variables must be cleared after the live variables are copied,
      // otherwise the viewer's value overwrites the value restored from the bookmark.
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly varAssembly = new DefaultVariableAssembly(ws, "TextInput1");
      AssetVariable var = new AssetVariable("TextInput1");
      var.setValueNode(XValueNode.createValueNode((Object) "wsDefault", "TextInput1"));
      varAssembly.setVariable(var);
      ws.addAssembly(varAssembly);

      Viewsheet bookmark = newBookmark(ws);
      TextInputVSAssembly input = new TextInputVSAssembly(bookmark, "TextInput1");
      input.setSelectedObject("bookmarked");
      bookmark.addAssembly(input);

      VariableTable liveVars = new VariableTable();
      liveVars.put("TextInput1", "liveValue");
      liveVars.put("region77246", "East");

      ViewsheetSandbox box = createSandbox(bookmark, liveVars);

      try {
         TextInputVSAssembly restored =
            (TextInputVSAssembly) box.getViewsheet().getAssembly("TextInput1");
         assertEquals("bookmarked", restored.getSelectedObject(),
            "the bookmark-restored input value must win over the viewer's live input value");
         assertEquals("East", box.getVariableTable().get("region77246"),
            "the viewer's other variables must still be copied");
      }
      finally {
         box.dispose();
      }
   }

   @Test
   void createSandbox_clearsScaleOnBookmarkCloneOnly() throws Exception {
      // Bug #77246: the bookmark is a clone of the live, scaled-to-screen viewsheet. Like
      // bookmark export, email must clear the scaled position/size and runtime column widths
      // on the clone, and must not touch the live viewsheet.
      Viewsheet live = newBookmark(new Worksheet());
      TableVSAssembly table = new TableVSAssembly(live, "Table1");
      live.addAssembly(table);
      TableDataVSAssemblyInfo liveInfo = (TableDataVSAssemblyInfo) table.getVSAssemblyInfo();
      liveInfo.setScaledPosition(new Point(10, 10));
      liveInfo.setScaledSize(new Dimension(50, 50));
      liveInfo.setColumnWidth(0, 123);

      Viewsheet bookmark = live.clone();
      ViewsheetSandbox box = createSandbox(bookmark, new VariableTable());

      try {
         TableDataVSAssemblyInfo info = (TableDataVSAssemblyInfo)
            ((TableVSAssembly) box.getViewsheet().getAssembly("Table1")).getVSAssemblyInfo();
         assertFalse(info.isScaled(), "the scaled position must be cleared on the bookmark");
         assertNotEquals(new Dimension(50, 50), info.getLayoutSize(),
            "the scaled size must be cleared on the bookmark");
         assertTrue(Double.isNaN(info.getColumnWidth(0)),
            "the runtime column widths must be cleared on the bookmark");

         assertTrue(liveInfo.isScaled(), "the live viewsheet's scale must not be touched");
         assertEquals(new Dimension(50, 50), liveInfo.getLayoutSize(),
            "the live viewsheet's scaled size must not be touched");
         assertEquals(123, liveInfo.getColumnWidth(0), 0.0,
            "the live viewsheet's runtime column widths must not be touched");
      }
      finally {
         box.dispose();
      }
   }

   @ParameterizedTest
   @ValueSource(ints = { FileFormatInfo.EXPORT_TYPE_PNG, FileFormatInfo.EXPORT_TYPE_PDF })
   void emailViewsheet_passesLiveVariableTableToCreateSandbox(int formatType, @TempDir Path dir)
      throws Exception
   {
      // Bug #77246: both bookmark call sites (PNG with several bookmarks written as separate
      // files, and exportViewsheet() for every other format) must hand the live sandbox's
      // variable table to createSandbox. The sentinel stops the export after the first call.
      VariableTable liveVars = new VariableTable();
      liveVars.put("region77246", "East");
      ViewsheetSandbox liveBox = mock(ViewsheetSandbox.class);
      when(liveBox.getVariableTable()).thenReturn(liveVars);

      Viewsheet vs = newBookmark(new Worksheet());
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getEntry()).thenReturn(vs.getEntry());
      when(rvs.getOriginalBookmark(anyString())).thenReturn(vs);

      FileSystemService fs = mock(FileSystemService.class);
      when(fs.getCacheFile(anyString()))
         .thenAnswer(inv -> dir.resolve((String) inv.getArgument(0)).toFile());

      List<VariableTable> passed = new ArrayList<>();
      VSEmailService service = new VSEmailService(fs) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
         {
            passed.add(vars);
            throw new IllegalStateException("sentinel77246");
         }
      };

      // PNG needs more than one bookmark to take the separate-files branch.
      String[] bookmarks = { "b1", "b2" };

      // The attachment file name is localized through the repository registry, and the real
      // exporters need Batik and the theme bean, none of which this harness starts.
      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
          MockedStatic<PortalThemesManager> themes = mockStatic(PortalThemesManager.class);
          MockedStatic<AbstractVSExporter> exporters =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS))
      {
         sutil.when(() -> SUtil.localize(anyString(), any(), anyBoolean(), any()))
            .thenReturn("vs77246");
         themes.when(PortalThemesManager::getColorTheme).thenReturn(null);
         exporters.when(() -> AbstractVSExporter.getVSExporter(
               anyInt(), any(), any(), anyBoolean(), any()))
            .thenReturn(mock(VSExporter.class));
         IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.emailViewsheet(rvs, formatType, bookmarks, false, false, false, "a@b.c",
                                   null, null, "s", "b", false, null, null));
         assertEquals("sentinel77246", ex.getMessage());
      }

      assertEquals(1, passed.size(), "createSandbox(..., liveVars) must be the overload called");
      assertSame(liveVars, passed.get(0),
         "the email bookmark sandbox must receive the live sandbox's variable table");
   }

   @ParameterizedTest
   @CsvSource({
      "4, true, false", "2, true, false", "4, false, false", "4, false, true", "2, false, false"
   })
   void emailViewsheet_closesExportStreams(int formatType, boolean exportFails,
                                           boolean includeCurrent, @TempDir Path dir)
      throws Exception
   {
      // Bug #77446: the export streams were closed only after a successful export, and the PNG
      // branch with several bookmarks opened an extra stream it never used or closed. An open
      // stream keeps the cache file locked on Windows, so list the open file descriptors.
      Path fds = Path.of("/proc/self/fd");
      assumeTrue(Files.isDirectory(fds), "needs /proc/self/fd to list open files");

      ViewsheetSandbox liveBox = mock(ViewsheetSandbox.class);
      when(liveBox.getVariableTable()).thenReturn(new VariableTable());

      Viewsheet vs = newBookmark(new Worksheet());
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getEntry()).thenReturn(vs.getEntry());
      when(rvs.getOriginalBookmark(anyString())).thenReturn(vs);

      FileSystemService fs = mock(FileSystemService.class);
      when(fs.getCacheFile(anyString()))
         .thenAnswer(inv -> dir.resolve((String) inv.getArgument(0)).toFile());

      // The export either fails in createSandbox or succeeds, and then the mailer stops the
      // email right after the export, before the cache files are deleted.
      VSEmailService service = new VSEmailService(fs) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
         {
            if(exportFails) {
               throw new IllegalStateException("sentinel77446");
            }

            return mock(ViewsheetSandbox.class);
         }

         @Override
         protected Mailer createMailer() {
            throw new IllegalStateException("sentinel77446");
         }
      };

      String[] bookmarks = { "b1", "b2" };

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
          MockedStatic<PortalThemesManager> themes = mockStatic(PortalThemesManager.class);
          MockedStatic<AbstractVSExporter> exporters =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS))
      {
         sutil.when(() -> SUtil.localize(anyString(), any(), anyBoolean(), any()))
            .thenReturn("vs77446");
         themes.when(PortalThemesManager::getColorTheme).thenReturn(null);
         exporters.when(() -> AbstractVSExporter.getVSExporter(
               anyInt(), any(), any(), anyBoolean(), any()))
            .thenReturn(mock(VSExporter.class));
         IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.emailViewsheet(rvs, formatType, bookmarks, false, false, includeCurrent,
                                   "a@b.c", null, null, "s", "b", false, null, null));
         assertEquals("sentinel77446", ex.getMessage());
      }

      assertEquals(List.of(), openFilesIn(dir),
         "emailViewsheet must close every export stream it opens");
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void emailViewsheet_closesExcelToCsvStreams(boolean excelExportFails, @TempDir Path dir)
      throws Exception
   {
      // Bug #77446: a large Excel export is written to an .xlsx file and then exported again as
      // CSV, which zips and deletes the .xlsx. The .xlsx stream must be closed before the CSV
      // export, and neither stream may stay open when either export fails.
      assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")),
                 "needs /proc/self/fd to list open files");

      ViewsheetSandbox liveBox = mock(ViewsheetSandbox.class);
      when(liveBox.getVariableTable()).thenReturn(new VariableTable());

      Viewsheet vs = newBookmark(new Worksheet());
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getEntry()).thenReturn(vs.getEntry());
      when(rvs.getOriginalBookmark(anyString())).thenReturn(vs);

      FileSystemService fs = mock(FileSystemService.class);
      when(fs.getCacheFile(anyString()))
         .thenAnswer(inv -> dir.resolve((String) inv.getArgument(0)).toFile());

      VSEmailService service = new VSEmailService(fs) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
         {
            return mock(ViewsheetSandbox.class);
         }
      };

      List<String> openAtCsvExport = new ArrayList<>();

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
          MockedStatic<PortalThemesManager> themes = mockStatic(PortalThemesManager.class);
          MockedStatic<CSVUtil> csv = mockStatic(CSVUtil.class, CALLS_REAL_METHODS);
          MockedStatic<AbstractVSExporter> exporters =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS))
      {
         sutil.when(() -> SUtil.localize(anyString(), any(), anyBoolean(), any()))
            .thenReturn("vs77446");
         themes.when(PortalThemesManager::getColorTheme).thenReturn(null);
         csv.when(() -> CSVUtil.hasLargeDataTable(rvs)).thenReturn(true);
         exporters.when(() -> AbstractVSExporter.getVSExporter(
               anyInt(), any(), any(), anyBoolean(), any()))
            .thenAnswer(inv -> {
               if((int) inv.getArgument(0) == FileFormatInfo.EXPORT_TYPE_EXCEL) {
                  if(excelExportFails) {
                     throw new IllegalStateException("sentinel77446");
                  }

                  return mock(VSExporter.class);
               }

               openAtCsvExport.addAll(openFilesIn(dir));
               throw new IllegalStateException("sentinel77446");
            });
         IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.emailViewsheet(rvs, FileFormatInfo.EXPORT_TYPE_EXCEL, new String[] { "b1" },
                                   false, false, false, "a@b.c", null, null, "s", "b", false,
                                   null, null));
         assertEquals("sentinel77446", ex.getMessage());
      }

      if(!excelExportFails) {
         assertFalse(openAtCsvExport.isEmpty(), "the csv export must have been reached");
         assertTrue(openAtCsvExport.stream().noneMatch(f -> f.contains(".xlsx")),
            "the xlsx stream must be closed before the csv export: " + openAtCsvExport);
      }

      assertEquals(List.of(), openFilesIn(dir),
         "emailViewsheet must close the xlsx and csv streams it opens");
   }

   /**
    * Lists the files under {@code dir} that this process has open, from /proc/self/fd.
    */
   private static List<String> openFilesIn(Path dir) throws IOException {
      List<String> open = new ArrayList<>();
      Path realDir = dir.toRealPath();

      try(DirectoryStream<Path> links = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
         for(Path link : links) {
            try {
               Path target = Files.readSymbolicLink(link);

               if(target.startsWith(realDir)) {
                  open.add(target.toString());
               }
            }
            catch(IOException ignore) {
               // the descriptor was closed while listing, e.g. the directory stream itself
            }
         }
      }

      return open;
   }

   private static ViewsheetSandbox createSandbox(Viewsheet bookmark) throws Exception {
      AssetEntry entry = bookmark.getEntry();
      return new VSEmailService(null)
         .createSandbox(bookmark, AbstractSheet.SHEET_RUNTIME_MODE, null, entry);
   }

   private static ViewsheetSandbox createSandbox(Viewsheet bookmark, VariableTable liveVars)
      throws Exception
   {
      AssetEntry entry = bookmark.getEntry();
      return new VSEmailService(null)
         .createSandbox(bookmark, AbstractSheet.SHEET_RUNTIME_MODE, null, entry, liveVars);
   }

   private static Viewsheet newBookmark(Worksheet ws) throws Exception {
      Viewsheet vs = new Viewsheet();

      // Viewsheet.setBaseWorksheet() is private and only reachable through a full update()
      // against an asset repository, so wire the base worksheet directly -- same approach as
      // ViewsheetSandboxProcessOnInitTest.
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/VSEmailServiceCreateSandboxTest", null);
      vs.setEntry(entry);
      return vs;
   }
}
