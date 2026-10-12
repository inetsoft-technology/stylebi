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
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.schedule.ViewsheetAction;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.PrintLayoutResolver;
import inetsoft.web.composer.vs.controller.ExportControllerService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.VSEmailService;
import inetsoft.web.viewsheet.service.VSExportService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78131: a PDF of several states (the current view and bookmarks) of a thin wrapper that
 * inherits the print layout of an embedded viewsheet (#76774) lost the canvas pages of the
 * states that show wrapper content, and with two canvas states it was a malformed file. The
 * inheritance is decided per state from runtime visibility, while write() emits either the
 * print layout reports or the canvas pages. Every state of one PDF must now take the same
 * branch: the print layout only when every state inherits it, otherwise the canvas.
 * <p>
 * wrappermode embeds child (own Letter print layout) and has a hidden ComboBox Mode and a Text
 * Note ("NOTEDETAIL") whose script shows it when Mode is "detail", so a "detail" state draws
 * the canvas and a "summary" state inherits the layout. The states go through the real export
 * callers and real bookmarks. The PDF is checked on its bytes; it lives in this module because
 * a canvas page with text needs its classes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome(importResources = "/inetsoft/report/io/viewsheet/pdf/PDFVSExporterMixedStateTest.zip")
@Tag("core")
@Tag("integration")
class PDFVSExporterMixedStateTest {
   // inherited state first, then a canvas state: every caller must decide before the first
   // state is exported
   @ParameterizedTest
   @EnumSource(Caller.class)
   void inheritedThenCanvasExportsBothCanvasPages(Caller caller) throws Exception {
      assertCanvas(export(caller, SUMMARY, DETAIL), 2, 1);
   }

   // regression guard for #76774: every state inherits, one Letter page per state
   @ParameterizedTest
   @EnumSource(Caller.class)
   void allInheritedStatesUsePrintLayout(Caller caller) throws Exception {
      assertPrintLayout(export(caller, SUMMARY, SUMMARY), 2);
   }

   // bookmarks only, without the current view: the decision is made over the bookmarks alone
   @ParameterizedTest
   @EnumSource(value = Caller.class, names = { "EXPORT_DIALOG", "REST_API", "EMAIL" })
   void bookmarksOnlyInheritedThenCanvasExportsBothCanvasPages(Caller caller) throws Exception {
      RuntimeViewsheet rvs = wrapper.getRuntimeViewsheet();
      saveBookmark(rvs, "Summary0", SUMMARY);
      saveBookmark(rvs, "Detail1", DETAIL);
      assertCanvas(new Pdf(caller.export(rvs, false, "Summary0", "Detail1")), 2, 1);
   }

   // the cases below run the exporter's decision once, each through another caller

   // canvas state first, then an inherited state
   @Test
   void canvasThenInheritedExportsBothCanvasPages() throws Exception {
      assertCanvas(export(Caller.REST_API, DETAIL, SUMMARY), 2, 1);
   }

   // two canvas states before an inherited one wrote a stray canvas PDF before the reports
   @Test
   void twoCanvasStatesAndInheritedIsOneValidFile() throws Exception {
      assertCanvas(export(Caller.EXPORT_DIALOG, DETAIL, DETAIL, SUMMARY), 3, 2);
   }

   @Test
   void inheritedBetweenCanvasStatesIsOneValidFile() throws Exception {
      assertCanvas(export(Caller.SCHEDULE, DETAIL, SUMMARY, DETAIL), 3, 2);
   }

   @Test
   void allCanvasStatesUseCanvas() throws Exception {
      assertCanvas(export(Caller.EMAIL, DETAIL, DETAIL), 2, 2);
   }

   // a sheet with its own print layout is not affected by the inheritance decision
   @Test
   void ownPrintLayoutWithBookmarkUsesPrintLayout() throws Exception {
      RuntimeViewsheet rvs = own.getRuntimeViewsheet();
      addBookmark(rvs, "Own1");
      assertPrintLayout(new Pdf(Caller.EXPORT_DIALOG.export(rvs, true, "Own1")), 2);
   }

   // a single state exported without prepareStates() keeps its own decision
   @Test
   void singleInheritedStateUsesPrintLayout() throws Exception {
      RuntimeViewsheet rvs = wrapper.getRuntimeViewsheet();
      setMode(rvs, SUMMARY);
      assertPrintLayout(exportDirect(rvs.getViewsheetSandbox().orElseThrow()), 1);
   }

   @Test
   void singleCanvasStateUsesCanvas() throws Exception {
      RuntimeViewsheet rvs = wrapper.getRuntimeViewsheet();
      setMode(rvs, DETAIL);
      assertCanvas(exportDirect(rvs.getViewsheetSandbox().orElseThrow()), 1, 1);
   }

   // a caller that skips prepareStates() and exports a canvas state first must not append the
   // print layout reports after the canvas pages (the backstop for a future caller)
   @Test
   void withoutPrepareStatesCanvasFirstKeepsCanvas() throws Exception {
      RuntimeViewsheet rvs = wrapper.getRuntimeViewsheet();
      saveBookmark(rvs, "Detail1", DETAIL);
      saveBookmark(rvs, "Detail2", DETAIL);
      saveBookmark(rvs, "Summary1", SUMMARY);
      List<ViewsheetSandbox> boxes = new ArrayList<>();

      try {
         for(String name : new String[] { "Detail1", "Detail2", "Summary1" }) {
            boxes.add(createBookmarkSandbox(rvs, name));
         }

         ByteArrayOutputStream out = new ByteArrayOutputStream();
         AbstractVSExporter exporter = createExporter(out);
         exporter.setSandbox(rvs.getViewsheetSandbox().orElseThrow());
         exporter.setAssetEntry(rvs.getEntry());
         exporter.setRuntimeViewsheet(rvs);

         for(int i = 0; i < boxes.size(); i++) {
            exporter.export(boxes.get(i), "Bookmark" + i, i, null);
         }

         exporter.write();
         assertCanvas(new Pdf(out.toByteArray()), 3, 2);
      }
      finally {
         boxes.forEach(ViewsheetSandbox::dispose);
      }
   }

   /**
    * Export the states of wrappermode. The first mode is the current view, the others are
    * bookmarks; the scheduler exports bookmarks only, so there the first one is a bookmark too.
    */
   private Pdf export(Caller caller, String... modes) throws Exception {
      RuntimeViewsheet rvs = wrapper.getRuntimeViewsheet();
      int bookmarkCount = caller.current ? modes.length - 1 : modes.length;
      String[] bookmarks = new String[bookmarkCount];

      for(int i = 0; i < bookmarkCount; i++) {
         String mode = modes[modes.length - bookmarkCount + i];
         bookmarks[i] = mode + i;
         saveBookmark(rvs, bookmarks[i], mode);
      }

      if(caller.current) {
         setMode(rvs, modes[0]);
      }

      return new Pdf(caller.export(rvs, caller.current, bookmarks));
   }

   // set Mode on the live sheet and save the state as a bookmark
   private static void saveBookmark(RuntimeViewsheet rvs, String name, String mode)
      throws Exception
   {
      setMode(rvs, mode);
      addBookmark(rvs, name);
   }

   private static void addBookmark(RuntimeViewsheet rvs, String name) throws Exception {
      rvs.addBookmark(name, VSBookmarkInfo.PRIVATE,
                      IdentityID.getIdentityIDFromKey(rvs.getUser().getName()), false);
   }

   private static void setMode(RuntimeViewsheet rvs, String mode) throws Exception {
      Viewsheet vs = rvs.getViewsheet();
      ComboBoxVSAssemblyInfo info = (ComboBoxVSAssemblyInfo)
         ((VSAssembly) vs.getAssembly("Mode")).getVSAssemblyInfo();
      info.setSelectedObject(mode);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      // the precondition of every case: the state inherits exactly when Mode is "summary"
      assertEquals(SUMMARY.equals(mode), PrintLayoutResolver.getInheritedOwner(vs) != null,
                   "live state " + mode);
   }

   // the sandbox of a bookmark as the export callers restore it
   private static ViewsheetSandbox createBookmarkSandbox(RuntimeViewsheet rvs, String name)
      throws Exception
   {
      Viewsheet vs = rvs.getOriginalBookmark(name, rvs.getEntry().getOrgID());
      ViewsheetSandbox box = new ViewsheetSandbox(vs, Viewsheet.SHEET_RUNTIME_MODE,
                                                  rvs.getUser(), false, rvs.getEntry());
      box.processOnInit();
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      return box;
   }

   private static Pdf exportDirect(ViewsheetSandbox box) throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      AbstractVSExporter exporter = createExporter(out);
      exporter.setSandbox(box);
      exporter.setAssetEntry(box.getAssetEntry());
      exporter.export(box, "Current View", 0, null);
      exporter.write();
      return new Pdf(out.toByteArray());
   }

   private static AbstractVSExporter createExporter(ByteArrayOutputStream out) {
      return (AbstractVSExporter) AbstractVSExporter.getVSExporter(
         FileFormatInfo.EXPORT_TYPE_PDF, PortalThemesManager.getColorTheme(), out, false, null);
   }

   // every state is drawn on the canvas, the detail states with the wrapper's Note
   private static void assertCanvas(Pdf pdf, int states, int detailStates) {
      assertValidFile(pdf);
      assertEquals(states, pdf.pages, () -> "one page per state expected: " + pdf);
      assertEquals(0, pdf.letterPages, () -> "no print layout page expected: " + pdf);
      assertEquals(detailStates, pdf.count("NOTEDETAIL"),
                   () -> "each detail state should show the Note: " + pdf);
   }

   // every state is drawn through the print layout, one Letter page per state
   private static void assertPrintLayout(Pdf pdf, int states) {
      assertValidFile(pdf);
      assertEquals(states, pdf.pages, () -> "one page per state expected: " + pdf);
      assertEquals(states, pdf.letterPages, () -> "Letter print layout pages expected: " + pdf);
      assertEquals(0, pdf.count("NOTEDETAIL"), pdf::toString);
   }

   private static void assertValidFile(Pdf pdf) {
      assertEquals(1, pdf.headers, () -> "exactly one PDF header expected: " + pdf);
      assertTrue(pdf.startXrefValid, () -> "startxref should point at the xref table: " + pdf);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent(String name) {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^RegXui/print/" + name);
      event.setViewer(true);
      return event;
   }

   private static Object invoke(Object target, String name, Class<?>[] types, Object... args)
      throws Exception
   {
      Method method = target.getClass().getDeclaredMethod(name, types);
      method.setAccessible(true);
      return method.invoke(target, args);
   }

   /**
    * The real callers that export several states into one PDF exporter.
    */
   enum Caller {
      // the viewer Export dialog and the path-based /export/viewsheet/<path> URL
      EXPORT_DIALOG(true) {
         @Override
         byte[] export(RuntimeViewsheet rvs, boolean current, String... bookmarks)
            throws Exception
         {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            invoke(new ExportControllerService(null, null, null), "writeViewsheetExport",
                   SERVICE_TYPES, rvs, createExporter(out), rvs.getUser(), true, false,
                   current, bookmarks, false, false);
            return out.toByteArray();
         }
      },
      // the REST API (enterprise ViewsheetApiService)
      REST_API(true) {
         @Override
         byte[] export(RuntimeViewsheet rvs, boolean current, String... bookmarks)
            throws Exception
         {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            invoke(new VSExportService(null, null, null, null, null, null),
                   "writeViewsheetExport", SERVICE_TYPES, rvs, createExporter(out),
                   rvs.getUser(), true, false, current, bookmarks, false, false);
            return out.toByteArray();
         }
      },
      // an email with one combined file
      EMAIL(true) {
         @Override
         byte[] export(RuntimeViewsheet rvs, boolean current, String... bookmarks)
            throws Exception
         {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            invoke(new VSEmailService(null), "exportViewsheet",
                   new Class<?>[] { RuntimeViewsheet.class, Principal.class, int.class,
                                    String[].class, java.io.OutputStream.class,
                                    inetsoft.report.io.csv.CSVConfig.class, boolean.class,
                                    boolean.class, boolean.class, boolean.class,
                                    java.io.File.class, boolean.class },
                   rvs, rvs.getUser(), FileFormatInfo.EXPORT_TYPE_PDF, bookmarks, out, null,
                   true, false, false, current, null, false);
            return out.toByteArray();
         }
      },
      // a scheduled email or save with several bookmarks
      SCHEDULE(false) {
         @Override
         byte[] export(RuntimeViewsheet rvs, boolean current, String... bookmarks)
            throws Exception
         {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ViewsheetAction action = new ViewsheetAction();
            Field principal = ViewsheetAction.class.getDeclaredField("principal");
            principal.setAccessible(true);
            principal.set(action, rvs.getUser());
            IdentityID user = IdentityID.getIdentityIDFromKey(rvs.getUser().getName());
            List<VSBookmarkInfo> infos = new ArrayList<>();

            for(String bookmark : bookmarks) {
               infos.add(rvs.getBookmarks(user).stream()
                            .filter(info -> bookmark.equals(info.getName()))
                            .findFirst().orElseThrow());
            }

            VSExporter exporter = createExporter(out);
            exporter.setMatchLayout(true);
            exporter.setAssetEntry(rvs.getEntry());
            exporter.setSandbox(rvs.getViewsheetSandbox().orElseThrow());
            VariableTable vars = rvs.getViewsheetSandbox().orElseThrow().getVariableTable();
            invoke(action, "exportBookmarksAndWrite",
                   new Class<?>[] { VSExporter.class, RuntimeViewsheet.class,
                                    VariableTable.class, VSBookmarkInfo[].class, List.class },
                   exporter, rvs, vars, infos.toArray(new VSBookmarkInfo[0]), null);
            return out.toByteArray();
         }
      };

      Caller(boolean current) {
         this.current = current;
      }

      abstract byte[] export(RuntimeViewsheet rvs, boolean current, String... bookmarks)
         throws Exception;

      // whether the caller exports the current view
      final boolean current;
   }

   /**
    * What the written PDF bytes show.
    */
   private static final class Pdf {
      Pdf(byte[] bytes) {
         raw = new String(bytes, StandardCharsets.ISO_8859_1);
         content = inflate(raw);
         headers = count(raw, "%PDF-");
         pages = countMatches(PAGE, raw);
         Matcher box = MEDIA_BOX.matcher(raw);

         while(box.find()) {
            mediaBoxes.add(box.group(1) + "x" + box.group(2));
         }

         letterPages = (int) mediaBoxes.stream().filter("612x792"::equals).count();
         Matcher xref = START_XREF.matcher(raw);
         int offset = -1;

         while(xref.find()) {
            offset = Integer.parseInt(xref.group(1));
         }

         startXrefValid = offset >= 0 && raw.startsWith("xref", offset);
      }

      int count(String text) {
         return count(content, text);
      }

      @Override
      public String toString() {
         return "headers=" + headers + ", pages=" + pages + ", mediaBoxes=" + mediaBoxes +
            ", startxref valid=" + startXrefValid + ", NOTEDETAIL=" + count("NOTEDETAIL") +
            ", size=" + raw.length();
      }

      private static int count(String text, String part) {
         int count = 0;

         for(int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            count++;
         }

         return count;
      }

      private static int countMatches(Pattern pattern, String text) {
         Matcher matcher = pattern.matcher(text);
         int count = 0;

         while(matcher.find()) {
            count++;
         }

         return count;
      }

      // the PDF with its compressed streams inflated, so the page texts can be searched
      private static String inflate(String raw) {
         StringBuilder content = new StringBuilder(raw);
         Matcher stream = STREAM.matcher(raw);

         while(stream.find()) {
            Inflater inflater = new Inflater();
            inflater.setInput(stream.group(1).getBytes(StandardCharsets.ISO_8859_1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];

            try {
               while(!inflater.finished()) {
                  int n = inflater.inflate(buf);

                  if(n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                     break;
                  }

                  out.write(buf, 0, n);
               }
            }
            catch(DataFormatException ignore) {
               // not a deflated stream (an image, say)
            }
            finally {
               inflater.end();
            }

            content.append('\n').append(out.toString(StandardCharsets.ISO_8859_1));
         }

         return content.toString();
      }

      private final String raw;
      private final String content;
      private final int headers;
      private final int pages;
      private final List<String> mediaBoxes = new ArrayList<>();
      private final int letterPages;
      private final boolean startXrefValid;
   }

   @RegisterExtension
   RuntimeViewsheetExtension wrapper =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("wrappermode"));

   @RegisterExtension
   RuntimeViewsheetExtension own =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("own"));

   private static final String SUMMARY = "summary";
   private static final String DETAIL = "detail";
   private static final Class<?>[] SERVICE_TYPES = {
      RuntimeViewsheet.class, VSExporter.class, Principal.class, boolean.class, boolean.class,
      boolean.class, String[].class, boolean.class, boolean.class };
   private static final Pattern PAGE = Pattern.compile("/Type\\s*/Page(?![a-zA-Z])");
   private static final Pattern MEDIA_BOX = Pattern.compile(
      "/MediaBox\\s*\\[\\s*0\\s+0\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s*\\]");
   private static final Pattern START_XREF = Pattern.compile("startxref\\s+(\\d+)");
   private static final Pattern STREAM =
      Pattern.compile("stream\r?\n(.*?)endstream", Pattern.DOTALL);
}
