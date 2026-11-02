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
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.report.StyleConstants;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.OvalVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.OvalVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.web.viewsheet.service.PrintLayoutHighlightFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79414: a print layout PDF must draw an Oval with its line style. Exports a viewsheet
 * whose print layout places one oval through the real PDF exporter, which converts it with
 * VsToReportConverter.addOval and paints PageLayout.Oval, and reads the page content stream.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  PrintLayoutHighlightFixture.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PDFVSExporterPrintLayoutOvalTest {
   @Test
   void thickOvalSetsLineWidth() throws Exception {
      assertTrue(find(export(StyleConstants.THICK_LINE), "(?m)^3 w$"));
   }

   @Test
   void dashedOvalSetsDashPattern() throws Exception {
      assertTrue(find(export(StyleConstants.DASH_LINE), "(?m)^\\[ 3\\.0 \\] 0 d$"));
   }

   @Test
   void doubleOvalDrawsTwoEllipses() throws Exception {
      int thin = count(export(StyleConstants.THIN_LINE), "(?m) c$");

      assertTrue(thin > 0, "the thin oval is drawn with curves");
      assertEquals(2 * thin, count(export(StyleConstants.DOUBLE_LINE), "(?m) c$"));
   }

   // the content streams of the print layout PDF of a viewsheet with one oval
   private static String export(int style) throws Exception {
      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, new Worksheet());
      OvalVSAssembly oval = new OvalVSAssembly(vs, "Oval1");
      ((OvalVSAssemblyInfo) oval.getVSAssemblyInfo()).setLineStyleValue(style);
      vs.addAssembly(oval);

      PrintInfo printInfo = new PrintInfo();
      printInfo.setUnit("inches");
      PrintLayout layout = new PrintLayout();
      layout.setPrintInfo(printInfo);
      layout.setVSAssemblyLayouts(new ArrayList<>(List.of(
         new VSAssemblyLayout("Oval1", new Point(50, 50), new Dimension(200, 100)))));
      vs.getLayoutInfo().setPrintLayout(layout);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug79414", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      ViewsheetSandbox box = new ViewsheetSandbox(vs, Viewsheet.SHEET_RUNTIME_MODE, null,
                                                  false, entry);
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      try {
         box.processOnInit();
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);

         PDFVSExporter exporter = (PDFVSExporter) AbstractVSExporter.getVSExporter(
            FileFormatInfo.EXPORT_TYPE_PDF, null, out, false, null);
         exporter.setAssetEntry(entry);
         exporter.setSandbox(box);
         exporter.export(box, "Current View", 0, null);

         Field field = PDFVSExporter.class.getDeclaredField("reportList");
         field.setAccessible(true);
         assertEquals(1, ((List<?>) field.get(exporter)).size(),
                      "the sheet should be exported through the print layout");

         exporter.write();
      }
      finally {
         box.dispose();
      }

      return contentStreams(out.toByteArray());
   }

   // the PDF's streams, inflated when they are compressed
   private static String contentStreams(byte[] pdf) {
      String text = new String(pdf, StandardCharsets.ISO_8859_1);
      Matcher matcher = Pattern.compile("(?s)stream\r?\n(.*?)endstream").matcher(text);
      StringBuilder streams = new StringBuilder();

      while(matcher.find()) {
         byte[] data = matcher.group(1).getBytes(StandardCharsets.ISO_8859_1);
         streams.append(inflate(data)).append('\n');
      }

      return streams.toString();
   }

   private static String inflate(byte[] data) {
      Inflater inflater = new Inflater();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];

      try {
         inflater.setInput(data);

         while(!inflater.finished()) {
            int n = inflater.inflate(buf);

            if(n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
               break;
            }

            out.write(buf, 0, n);
         }

         return out.toString(StandardCharsets.ISO_8859_1);
      }
      catch(Exception ex) {
         // not compressed
         return new String(data, StandardCharsets.ISO_8859_1);
      }
      finally {
         inflater.end();
      }
   }

   private static boolean find(String text, String regex) {
      return Pattern.compile(regex).matcher(text).find();
   }

   private static int count(String text, String regex) {
      Matcher matcher = Pattern.compile(regex).matcher(text);
      int count = 0;

      while(matcher.find()) {
         count++;
      }

      return count;
   }
}
