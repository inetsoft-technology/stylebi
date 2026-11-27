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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82103: a scheduled viewsheet export failed when a component script threw. The scheduled
 * action now skips the failing script in its pre-run ({@code executeViewsheet}) like the manual
 * export, and the file it writes must carry the {@code vs.export.componentScriptFailed} warning.
 * The test runs the scheduled steps in order - the pre-run on the viewsheet's own sandbox, then
 * {@code exportBookmarksAndWrite} with the real exporter - and reads the warning from the written
 * file. It lives in this module because the Excel, PowerPoint and PDF writers need its classes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome(importResources = "/inetsoft/graph/GraphRenderTest.zip")
@Tag("core")
@Tag("integration")
class ViewsheetActionScriptFailureExportTest {
   @ParameterizedTest
   @ValueSource(ints = { FileFormatInfo.EXPORT_TYPE_PDF, FileFormatInfo.EXPORT_TYPE_EXCEL,
                         FileFormatInfo.EXPORT_TYPE_POWERPOINT })
   void scheduledFileCarriesTheSkippedScriptWarning(int format) throws Exception {
      String text = scheduledExport(format, "noSuchFunction123();");
      assertTrue(text.contains(WARNING), () -> "the warning should be in the file: " + text);
   }

   // control: the same export without a failing script has no warning
   @ParameterizedTest
   @ValueSource(ints = { FileFormatInfo.EXPORT_TYPE_PDF, FileFormatInfo.EXPORT_TYPE_EXCEL,
                         FileFormatInfo.EXPORT_TYPE_POWERPOINT })
   void scheduledFileWithoutFailureHasNoWarning(int format) throws Exception {
      String text = scheduledExport(format, TEXT + ".text = 'ok82103';");
      assertFalse(text.contains(WARNING), () -> "no warning expected: " + text);
   }

   /**
    * runViewsheetAction: executeViewsheet on the viewsheet's own sandbox, then the save to disk
    * of one bookmark with the format's exporter. Returns the searchable text of the file.
    */
   private String scheduledExport(int format, String script) throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      Viewsheet vs = rvs.getViewsheet();
      TextVSAssembly assembly = new TextVSAssembly(vs, TEXT);
      assembly.getVSAssemblyInfo().setScript(script);
      assembly.getVSAssemblyInfo().setScriptEnabled(true);
      assembly.getVSAssemblyInfo().setPixelOffset(new java.awt.Point(10, 10));
      assembly.getVSAssemblyInfo().setPixelSize(new java.awt.Dimension(200, 20));
      vs.addAssembly(assembly);
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();

      IdentityID user = IdentityID.getIdentityIDFromKey(rvs.getUser().getName());
      rvs.addBookmark(BOOKMARK, VSBookmarkInfo.PRIVATE, user, false);
      VSBookmarkInfo bookmark = rvs.getBookmarks(user).stream()
         .filter(info -> BOOKMARK.equals(info.getName())).findFirst().orElseThrow();

      ViewsheetAction action = new ViewsheetAction();
      Field principal = ViewsheetAction.class.getDeclaredField("principal");
      principal.setAccessible(true);
      principal.set(action, rvs.getUser());

      assertFalse((Boolean) invoke(action, "executeViewsheet",
                                   new Class<?>[] { ViewsheetSandbox.class, Assembly[].class },
                                   box, vs.getAssemblies()),
                  "the pre-run must not cancel the action");

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      VSExporter exporter = AbstractVSExporter.getVSExporter(
         format, PortalThemesManager.getColorTheme(), out, false, null);
      exporter.setMatchLayout(true);
      exporter.setAssetEntry(rvs.getEntry());
      exporter.setSandbox(box);
      VariableTable vars = box.getVariableTable();
      invoke(action, "exportBookmarksAndWrite",
             new Class<?>[] { VSExporter.class, RuntimeViewsheet.class, VariableTable.class,
                              VSBookmarkInfo[].class, List.class },
             exporter, rvs, vars, new VSBookmarkInfo[] { bookmark }, null);

      byte[] bytes = out.toByteArray();
      assertTrue(bytes.length > 0, "the file should be written");
      return format == FileFormatInfo.EXPORT_TYPE_PDF ? pdfText(bytes) : zipText(bytes);
   }

   private static Object invoke(Object target, String name, Class<?>[] types, Object... args)
      throws Exception
   {
      Method method = ViewsheetAction.class.getDeclaredMethod(name, types);
      method.setAccessible(true);

      try {
         return method.invoke(target, args);
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause() instanceof Exception cause ? cause : ex;
      }
   }

   // every entry of an Office Open XML file (xlsx, pptx), concatenated
   private static String zipText(byte[] bytes) throws Exception {
      StringBuilder text = new StringBuilder();

      try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
         for(ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            if(entry.getName().endsWith(".xml")) {
               text.append('\n').append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
         }
      }

      return text.toString();
   }

   // the PDF with its compressed streams inflated, and the shown strings of its text operators
   // joined, so a line of text can be searched whatever way the writer split it
   private static String pdfText(byte[] bytes) {
      String raw = new String(bytes, StandardCharsets.ISO_8859_1);
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

      return content + "\n" + shownStrings(content);
   }

   // the literal strings "(...)" of the content, joined; scanned by hand, because a regex with a
   // repeated alternation group overflows the stack on a long content stream
   private static String shownStrings(CharSequence content) {
      StringBuilder shown = new StringBuilder();
      int depth = 0;

      for(int i = 0; i < content.length(); i++) {
         char c = content.charAt(i);

         if(depth == 0) {
            if(c == '(') {
               depth = 1;
            }
         }
         else if(c == '\\' && i + 1 < content.length()) {
            shown.append(content.charAt(++i));
         }
         else if(c == '(') {
            depth++;
            shown.append(c);
         }
         else if(c == ')') {
            if(--depth > 0) {
               shown.append(c);
            }
         }
         else {
            shown.append(c);
         }
      }

      return shown.toString();
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^TEST_GraphRender");
      event.setViewer(true);
      return event;
   }

   // the start of vs.export.componentScriptFailed, short enough to fit on one line of the PDF
   private static final String WARNING = "A component script failed";
   private static final String TEXT = "Text82103";
   private static final String BOOKMARK = "B82103";
   private static final Pattern STREAM =
      Pattern.compile("stream\r?\n(.*?)endstream", Pattern.DOTALL);

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());
}
