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
import jakarta.mail.MessagingException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
      List<VariableTable> passed = new ArrayList<>();
      VSEmailService service = new VSEmailService(cacheIn(dir)) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
         {
            passed.add(vars);
            throw new IllegalStateException("sentinel77246");
         }
      };

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
         email(service, formatType, liveVars));
      assertEquals("sentinel77246", ex.getMessage());

      assertEquals(1, passed.size(), "createSandbox(..., liveVars) must be the overload called");
      assertSame(liveVars, passed.get(0),
         "the email bookmark sandbox must receive the live sandbox's variable table");
      // Bug #77529: the streams were left open and the partial files left in the cache dir,
      // which also made the @TempDir cleanup fail on Windows.
      assertEquals(List.of(), cachedFiles(dir),
         "a failed export must close and delete its partial attachments");
   }

   @Test
   void emailViewsheet_failedPngExportKeepsLaterNamesItNeverCreated(@TempDir Path dir)
      throws Exception
   {
      // Bug #77529: the PNG names of all bookmarks are picked up front, but each file is only
      // created when its bookmark is exported. A concurrent email of the same viewsheet may
      // pick and write a name this export has not reached yet, so a failure on the first
      // bookmark must delete only the files this export created.
      Path other = dir.resolve("vs77246_2.png");
      VSEmailService service = new VSEmailService(cacheIn(dir)) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
            throws Exception
         {
            // stands in for the concurrent export writing its own image
            Files.writeString(other, "other77529");
            throw new IllegalStateException("sentinel77529");
         }
      };

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
         email(service, FileFormatInfo.EXPORT_TYPE_PNG, new VariableTable()));
      assertEquals("sentinel77529", ex.getMessage());

      assertEquals(List.of("vs77246_2.png"), cachedFiles(dir),
         "the failed export must delete its own partial image and keep the later name, " +
         "which another export wrote");
      assertEquals("other77529", Files.readString(other));
   }

   /**
    * Bug #77529: an Excel email of a large table is written as CSV from an .xlsx intermediate,
    * with both streams open at once. A failure in either pass must close and delete both the
    * attachment and the intermediate. With two bookmarks, the first createSandbox call is in
    * the Excel pass and the third is in the CSV pass, after the intermediate was closed.
    */
   @ParameterizedTest
   @ValueSource(ints = { 1, 3 })
   void emailViewsheet_failedExcelToCsvExportDeletesAttachmentAndIntermediate(
      int failAt, @TempDir Path dir) throws Exception
   {
      int[] calls = { 0 };
      VSEmailService service = new VSEmailService(cacheIn(dir)) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
         {
            if(++calls[0] == failAt) {
               throw new IllegalStateException("sentinel77529");
            }

            return mock(ViewsheetSandbox.class);
         }
      };

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
         email(service, FileFormatInfo.EXPORT_TYPE_EXCEL, new VariableTable(), true));
      assertEquals("sentinel77529", ex.getMessage());

      assertEquals(failAt, calls[0]);
      assertEquals(List.of(), cachedFiles(dir),
         "a failed Excel->CSV export must close and delete the attachment and the .xlsx");
      assertEquals(List.of(), leftoverDirectories(dir),
         "a failed Excel->CSV export must also remove its per-call UUID directory, not just " +
         "the files inside it");
   }

   /**
    * Review round 1 on #77565: {@code cachedFiles()} only sees regular files, so it cannot tell
    * whether {@code excelDir} itself -- the per-call UUID directory {@code createTmpDir()}
    * creates for the excel->CSV intermediate -- was actually removed. Here the exporter is a
    * bare {@code VSExporter} mock (not a real {@code CSVVSExporter}), so {@code
    * removeCSVFiles()} never runs and {@code excelFile} is still sitting in {@code excelDir}
    * when the export finishes; only the outer {@code finally}'s {@code Tool.deleteFile(excelDir)}
    * removes it. Without that call this test would still see an empty {@code excelDir} left
    * behind, which {@link #cachedFiles} cannot detect.
    */
   @Test
   void emailViewsheet_succeededExcelToCsvExportDeletesIntermediateDirectory(@TempDir Path dir)
      throws Exception
   {
      Mailer mailer = mock(Mailer.class);
      VSEmailService service = new VSEmailService(cacheIn(dir)) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
         {
            return mock(ViewsheetSandbox.class);
         }

         @Override
         protected Mailer createMailer() {
            return mailer;
         }
      };

      email(service, FileFormatInfo.EXPORT_TYPE_EXCEL, new VariableTable(), true);

      assertEquals(List.of(), leftoverDirectories(dir),
         "a successful Excel->CSV export must not leave its per-call UUID directory behind");
   }

   /**
    * Bug #77565 (independent verification test -- not written by the fixer): two concurrent
    * interactive-Email-dialog calls on the SAME viewsheet (same localized name, so the same
    * ".xlsx" base name) must not resolve the excel->CSV intermediate to the same cache path.
    * Pre-fix, {@code VSEmailService.java:196} computed
    * {@code fileSystemService.getCacheFile(fname + ".xlsx")} with no per-call isolation, so both
    * calls got the identical path: one call's {@code FileOutputStream} open could truncate the
    * other's in-flight write, or the call that finished first could delete the file out from
    * under the one still running ({@code CSVVSExporter.removeCSVFiles()}).
    * <p>
    * This drives two real concurrent {@code emailViewsheet()} calls on two separate threads,
    * synchronized with a {@link CyclicBarrier} so both threads' excel intermediate
    * {@code FileOutputStream}s are open on disk at the same instant (not just "close in time" --
    * actually concurrently open), and records -- via the mocked
    * {@code FileSystemService.getFile(dir, name)}, which is exactly what
    * {@code VSEmailService.createTmpDir()}/the {@code excelFile} resolution calls -- the real
    * path each thread resolved. If the fix is reverted, the two threads resolve the identical
    * path and this test fails (verified by temporarily reverting the fix and re-running this
    * exact test, see 04-verify.md).
    */
   @Test
   void emailViewsheet_concurrentExcelToCsvCallsDoNotShareIntermediateDirectory(@TempDir Path dir)
      throws Exception
   {
      FileSystemService fs = cacheIn(dir);
      Map<Long, File> excelFileByThread = new ConcurrentHashMap<>();

      // cacheIn() already stubs getFile(String, String); re-stub it here to also record, per
      // calling thread, the File resolved for the ".xlsx" name -- this is the exact call
      // VSEmailService's *fixed* code makes to compute excelFile once createTmpDir() hands back
      // excelDir.
      doAnswer(inv -> {
         String parent = inv.getArgument(0);
         String name = inv.getArgument(1);
         File f = new File(parent, name);

         if(name.endsWith(".xlsx")) {
            excelFileByThread.put(Thread.currentThread().getId(), f);
         }

         return f;
      }).when(fs).getFile(anyString(), anyString());

      // Also hook the single-argument getCacheFile(String), which is the call the *pre-fix*
      // code uses to compute excelFile directly (fileSystemService.getCacheFile(fname +
      // ".xlsx")). Hooking both call shapes means this probe (and therefore this test) detects
      // the collision correctly whichever of the two code shapes is in the tree when it runs --
      // this is what lets the same, unmodified test demonstrate the pre-fix collision below.
      doAnswer(inv -> {
         String name = inv.getArgument(0);
         File f = dir.resolve(name).toFile();

         if(name.endsWith(".xlsx")) {
            excelFileByThread.put(Thread.currentThread().getId(), f);
         }

         return f;
      }).when(fs).getCacheFile(anyString());

      CyclicBarrier barrier = new CyclicBarrier(2);
      Map<Long, Integer> calls = new ConcurrentHashMap<>();

      VSEmailService service = new VSEmailService(fs) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
            throws Exception
         {
            long tid = Thread.currentThread().getId();

            // Only the very first createSandbox call on this thread's run -- which happens
            // inside the Excel pass, with this thread's own excel-intermediate
            // FileOutputStream already open -- waits for the other thread. This puts both
            // threads' excel intermediates open on disk at the same instant before either
            // continues.
            if(calls.merge(tid, 1, Integer::sum) == 1) {
               barrier.await(10, TimeUnit.SECONDS);
            }

            return mock(ViewsheetSandbox.class);
         }

         @Override
         protected Mailer createMailer() {
            return mock(Mailer.class);
         }
      };

      ExecutorService pool = Executors.newFixedThreadPool(2);

      try {
         List<Future<?>> futures = new ArrayList<>();

         for(int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
               try {
                  email(service, FileFormatInfo.EXPORT_TYPE_EXCEL, new VariableTable(), true);
               }
               catch(Exception e) {
                  throw new RuntimeException(e);
               }
            }));
         }

         for(Future<?> future : futures) {
            future.get(20, TimeUnit.SECONDS);
         }
      }
      finally {
         pool.shutdownNow();
      }

      assertEquals(2, excelFileByThread.size(),
         "both concurrent calls must have resolved their own excel intermediate path");

      List<File> paths = new ArrayList<>(excelFileByThread.values());
      assertNotEquals(paths.get(0).getParentFile(), paths.get(1).getParentFile(),
         "two concurrent emails of the same viewsheet must not share the excel intermediate's " +
         "directory -- this is the actual bug #77565 collision");
      assertEquals(paths.get(0).getName(), paths.get(1).getName(),
         "the user-visible .xlsx base name must stay the same for both concurrent calls");

      assertEquals(List.of(), cachedFiles(dir),
         "both concurrent calls must finish and fully clean up their own intermediate directory");
   }

   /**
    * Bug #77529: the attachments are deleted once the email is sent or its send fails. A PNG
    * email of several bookmarks also opened, and never wrote, closed or deleted, a file with
    * the base name.
    */
   @ParameterizedTest
   @CsvSource({
      FileFormatInfo.EXPORT_TYPE_PNG + ", false", FileFormatInfo.EXPORT_TYPE_PNG + ", true",
      FileFormatInfo.EXPORT_TYPE_PDF + ", false", FileFormatInfo.EXPORT_TYPE_PDF + ", true" })
   void emailViewsheet_deletesAttachmentsAfterSend(int formatType, boolean sendFails,
                                                   @TempDir Path dir)
      throws Exception
   {
      Mailer mailer = mock(Mailer.class);
      List<String> filesAtSend = new ArrayList<>();
      doAnswer(inv -> {
         filesAtSend.addAll(cachedFiles(dir));

         if(sendFails) {
            throw new MessagingException("send77529");
         }

         return null;
      }).when(mailer).send(any(), any(), any(), any(), any(), any(), any(File.class), any(),
                           anyBoolean(), anyBoolean());

      VSEmailService service = new VSEmailService(cacheIn(dir)) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
         {
            return mock(ViewsheetSandbox.class);
         }

         @Override
         protected Mailer createMailer() {
            return mailer;
         }
      };

      if(sendFails) {
         assertThrows(MessagingException.class, () ->
            email(service, formatType, new VariableTable()));
      }
      else {
         email(service, formatType, new VariableTable());
      }

      assertEquals(List.of(), cachedFiles(dir),
         "the attachments must be closed and deleted after the send");
      assertEquals(formatType == FileFormatInfo.EXPORT_TYPE_PNG ?
                      List.of("vs77246.html", "vs77246_1.png", "vs77246_2.png") :
                      List.of("vs77246.pdf"),
                   filesAtSend, "the attachments to send");
   }

   /**
    * Bug #77567: the per-bookmark create-sandbox/export/dispose sequence occurs at two sites --
    * the PNG multi-file branch above (formatType == PNG, more than one bookmark) and
    * exportViewsheet() for every other format (formatType == PDF here) -- and both called
    * exporter.export(sandbox, ...) then sandbox.dispose() with no try/finally, so a throw from
    * export() on one bookmark skipped dispose() for that sandbox. Skipping dispose() skips
    * ViewsheetSandbox's QueryManager.cancel()/AssetDataCache cancellation, leaving any query
    * still in flight in that sandbox running uncancelled.
    */
   @ParameterizedTest
   @ValueSource(ints = { FileFormatInfo.EXPORT_TYPE_PNG, FileFormatInfo.EXPORT_TYPE_PDF })
   void emailViewsheet_disposesSandboxWhenExportThrows(int formatType, @TempDir Path dir)
      throws Exception
   {
      List<ViewsheetSandbox> sandboxes = new ArrayList<>();
      VSEmailService service = new VSEmailService(cacheIn(dir)) {
         @Override
         protected ViewsheetSandbox createSandbox(Viewsheet bookmark, int mode,
                                                  Principal principal, AssetEntry entry,
                                                  VariableTable vars)
         {
            ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
            sandboxes.add(sandbox);
            return sandbox;
         }
      };

      ViewsheetSandbox liveBox = mock(ViewsheetSandbox.class);
      when(liveBox.getVariableTable()).thenReturn(new VariableTable());

      Viewsheet vs = newBookmark(new Worksheet());
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getEntry()).thenReturn(vs.getEntry());
      when(rvs.getOriginalBookmark(anyString())).thenReturn(vs);

      // more than one bookmark so PNG takes the multipleFiles branch (site 1) and PDF still
      // loops more than once inside exportViewsheet (site 2).
      String[] bookmarks = { "b1", "b2" };
      VSExporter exporter = mock(VSExporter.class);
      doThrow(new IllegalStateException("sentinel77567"))
         .when(exporter).export(any(ViewsheetSandbox.class), eq("b1"), eq(1), any());

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
          MockedStatic<PortalThemesManager> themes = mockStatic(PortalThemesManager.class);
          MockedStatic<AbstractVSExporter> exporters =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS);
          MockedStatic<CSVUtil> csv = mockStatic(CSVUtil.class, CALLS_REAL_METHODS))
      {
         csv.when(() -> CSVUtil.hasLargeDataTable(any())).thenReturn(false);
         sutil.when(() -> SUtil.localize(anyString(), any(), anyBoolean(), any()))
            .thenReturn("vs77567");
         themes.when(PortalThemesManager::getColorTheme).thenReturn(null);
         exporters.when(() -> AbstractVSExporter.getVSExporter(
               anyInt(), any(), any(), anyBoolean(), any()))
            .thenReturn(exporter);

         IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.emailViewsheet(rvs, formatType, bookmarks, false, false, false, "a@b.c",
                                   null, null, "s", "b", false, null, null));
         assertEquals("sentinel77567", ex.getMessage());
      }

      assertEquals(1, sandboxes.size(),
         "export must fail on the first bookmark, before a second sandbox is created");
      verify(sandboxes.get(0)).dispose();
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

      FileSystemService fs = cacheIn(dir);

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

      FileSystemService fs = cacheIn(dir);

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
   /**
    * Email the test viewsheet to one address with two bookmarks (so PNG takes the separate
    * files branch) through mocked exporters.
    */
   private static void email(VSEmailService service, int formatType, VariableTable liveVars)
      throws Exception
   {
      email(service, formatType, liveVars, false);
   }

   /**
    * @param largeData true if the viewsheet has a large table, which makes an Excel email a
    *                  CSV one.
    */
   private static void email(VSEmailService service, int formatType, VariableTable liveVars,
                             boolean largeData)
      throws Exception
   {
      ViewsheetSandbox liveBox = mock(ViewsheetSandbox.class);
      when(liveBox.getVariableTable()).thenReturn(liveVars);

      Viewsheet vs = newBookmark(new Worksheet());
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(liveBox));
      when(rvs.getEntry()).thenReturn(vs.getEntry());
      when(rvs.getOriginalBookmark(anyString())).thenReturn(vs);

      // PNG needs more than one bookmark to take the separate-files branch.
      String[] bookmarks = { "b1", "b2" };

      // The attachment file name is localized through the repository registry, and the real
      // exporters need Batik and the theme bean, none of which this harness starts.
      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
          MockedStatic<PortalThemesManager> themes = mockStatic(PortalThemesManager.class);
          MockedStatic<AbstractVSExporter> exporters =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS);
          MockedStatic<CSVUtil> csv = mockStatic(CSVUtil.class, CALLS_REAL_METHODS))
      {
         csv.when(() -> CSVUtil.hasLargeDataTable(any())).thenReturn(largeData);
         sutil.when(() -> SUtil.localize(anyString(), any(), anyBoolean(), any()))
            .thenReturn("vs77246");
         themes.when(PortalThemesManager::getColorTheme).thenReturn(null);
         exporters.when(() -> AbstractVSExporter.getVSExporter(
               anyInt(), any(), any(), anyBoolean(), any()))
            .thenReturn(mock(VSExporter.class));
         service.emailViewsheet(rvs, formatType, bookmarks, false, false, false, "a@b.c",
                                null, null, "s", "b", false, null, null);
      }
   }

   private static FileSystemService cacheIn(Path dir) {
      FileSystemService fs = mock(FileSystemService.class);
      when(fs.getCacheFile(anyString()))
         .thenAnswer(inv -> dir.resolve((String) inv.getArgument(0)).toFile());

      // Bug #77565: the excel->CSV path now puts its .xlsx intermediate in its own per-call
      // subdirectory (VSEmailService.createTmpDir()) instead of directly under the cache dir,
      // so the mock must also support getCacheDirectory()/getFile(..) for that to work.
      try {
         when(fs.getCacheDirectory()).thenReturn(dir.toString());
      }
      catch(IOException e) {
         throw new RuntimeException(e);
      }

      when(fs.getFile(anyString())).thenAnswer(inv -> new File((String) inv.getArgument(0)));
      when(fs.getFile(anyString(), anyString())).thenAnswer(inv ->
         new File((String) inv.getArgument(0), (String) inv.getArgument(1)));

      return fs;
   }

   /**
    * Lists every regular file under {@code dir}, recursively, as paths relative to {@code dir}.
    * Recursive (not just {@code dir.toFile().list()}) since Bug #77565's fix nests the excel->CSV
    * intermediate under its own unique subdirectory of {@code dir} rather than directly in it.
    */
   private static List<String> cachedFiles(Path dir) {
      if(!Files.exists(dir)) {
         return List.of();
      }

      try(Stream<Path> paths = Files.walk(dir)) {
         return paths.filter(Files::isRegularFile)
            .map(dir::relativize)
            .map(Path::toString)
            .sorted()
            .toList();
      }
      catch(IOException e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Lists the first-level subdirectories still under {@code dir} (not recursive). Bug #77565,
    * review round 1: {@link #cachedFiles} filters to regular files, so it cannot see an empty
    * leftover {@code excelDir} -- the excel->CSV intermediate's per-call UUID directory -- if
    * the production code's {@code Tool.deleteFile(excelDir)} call were ever removed or skipped.
    */
   private static List<String> leftoverDirectories(Path dir) {
      if(!Files.exists(dir)) {
         return List.of();
      }

      try(Stream<Path> paths = Files.list(dir)) {
         return paths.filter(Files::isDirectory)
            .map(dir::relativize)
            .map(Path::toString)
            .sorted()
            .toList();
      }
      catch(IOException e) {
         throw new RuntimeException(e);
      }
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
