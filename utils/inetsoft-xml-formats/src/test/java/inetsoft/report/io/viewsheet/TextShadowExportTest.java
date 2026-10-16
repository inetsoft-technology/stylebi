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
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78063: a Text whose shadow is turned on only at runtime (a script {@code shadow = true}
 * or a design value such as "yes" that resolves to true) shows a shadow in the viewer but lost
 * it in the PNG, HTML, PDF, Excel and PowerPoint exports, because the exporters read the design
 * value {@code getShadowValue()} instead of the runtime {@code isShadow()}. Runs the real
 * exporters on the reported H16_shadowscript viewsheet (Text1, Shadow unchecked, script
 * {@code shadow = true;}) opened in viewer mode, so the script has run. It lives in this module
 * because the PNG (Batik), Excel and PowerPoint exporters need its classes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/io/viewsheet/H16_shadowscript.zip")
@Tag("core")
@Tag("integration")
class TextShadowExportTest {
   @Test
   void scriptShadowIsExported() throws Exception {
      TextVSAssemblyInfo info = getText();
      assertTrue(info.isShadow(), "the script should have turned the shadow on");
      assertFalse(info.getShadowValue(), "the design value should still be unchecked");
      Exports script = exportAll();

      useDesignShadow(info, "true");
      Exports control = exportAll();

      useDesignShadow(info, "false");
      Exports none = exportAll();

      assertShadow(control, none, "checked Shadow box");
      assertShadow(script, none, "script shadow = true");
      assertSamePixels(control.png, script.png,
                       "a script shadow should render like a checked Shadow box");
   }

   @Test
   void truthyDesignValueShadowIsExported() throws Exception {
      TextVSAssemblyInfo info = getText();

      useDesignShadow(info, "false");
      Exports none = exportAll();

      useDesignShadow(info, "yes");
      assertTrue(info.isShadow(), "\"yes\" should resolve to a runtime shadow");
      assertFalse(info.getShadowValue(), "\"yes\" is not the literal design value \"true\"");
      Exports yes = exportAll();

      assertShadow(yes, none, "design value \"yes\"");
   }

   // the export follows what the viewer shows: a checked box turned off at runtime exports
   // without a shadow
   @Test
   void runtimeShadowOffOverridesCheckedBox() throws Exception {
      TextVSAssemblyInfo info = getText();

      useDesignShadow(info, "false");
      Exports none = exportAll();

      useDesignShadow(info, "true");
      info.setShadow(false);
      assertFalse(info.isShadow());
      Exports off = exportAll();

      assertFalse(off.html.contains("text-shadow"), "html should have no text-shadow");
      assertEquals(none.pdfTextDraws, off.pdfTextDraws, "pdf should draw the text once");
      assertEquals(0, off.xlsxShadows, "xlsx text box should have no shadow");
      assertEquals(0, off.pptxShadows, "pptx text box should have no shadow");
      assertSamePixels(none.png, off.png, "png should have no shadow");
   }

   private static void assertShadow(Exports shadow, Exports none, String caseName) {
      assertTrue(shadow.html.contains("text-shadow"), caseName + ": html should have text-shadow");
      assertFalse(none.html.contains("text-shadow"), "no shadow: html should have no text-shadow");
      assertEquals(none.pdfTextDraws + 1, shadow.pdfTextDraws,
                   caseName + ": pdf should draw the shadow copy of the text");
      assertEquals(0, none.xlsxShadows, "no shadow: xlsx text box should have no shadow");
      assertTrue(shadow.xlsxShadows > 0, caseName + ": xlsx text box should have a shadow");
      assertEquals(0, none.pptxShadows, "no shadow: pptx text box should have no shadow");
      assertTrue(shadow.pptxShadows > 0, caseName + ": pptx text box should have a shadow");
      assertFalse(Arrays.equals(pixels(none.png), pixels(shadow.png)),
                  caseName + ": png should differ from the export without a shadow");
   }

   private static void assertSamePixels(byte[] expected, byte[] actual, String message) {
      assertArrayEquals(pixels(expected), pixels(actual), message);
   }

   private static int[] pixels(byte[] png) {
      try {
         BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
         assertNotNull(img, "the PNG export should decode");
         return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
      }
      catch(Exception ex) {
         throw new AssertionError(ex);
      }
   }

   // sets the design value as the property dialog or the saved XML would, with the script off
   private static void useDesignShadow(TextVSAssemblyInfo info, String dvalue) {
      info.setScriptEnabled(false);
      getShadowDynamicValue(info).setDValue(dvalue);
   }

   // OutputVSAssemblyInfo has no setter for an arbitrary shadow string, so find the shadow's
   // DynamicValue as the one setShadowValue() changes
   private static DynamicValue getShadowDynamicValue(TextVSAssemblyInfo info) {
      List<DynamicValue> values = info.getDynamicValues();
      boolean old = info.getShadowValue();
      info.setShadowValue(true);
      List<DynamicValue> candidates = values.stream()
         .filter(v -> "true".equals(v.getDValue())).toList();
      info.setShadowValue(false);
      candidates = candidates.stream().filter(v -> "false".equals(v.getDValue())).toList();
      info.setShadowValue(old);
      assertEquals(1, candidates.size(), "the shadow DynamicValue should be unique");
      return candidates.get(0);
   }

   private Exports exportAll() throws Exception {
      Exports exports = new Exports();
      exports.png = export(FileFormatInfo.EXPORT_TYPE_PNG);
      exports.html = new String(export(FileFormatInfo.EXPORT_TYPE_HTML), StandardCharsets.UTF_8);
      Matcher matcher = PDF_TEXT_DRAW.matcher(pdfContent(export(FileFormatInfo.EXPORT_TYPE_PDF)));

      while(matcher.find()) {
         exports.pdfTextDraws++;
      }

      exports.xlsxShadows =
         countShadows(export(FileFormatInfo.EXPORT_TYPE_EXCEL), "xl/drawings/");
      exports.pptxShadows =
         countShadows(export(FileFormatInfo.EXPORT_TYPE_POWERPOINT), "ppt/slides/");
      return exports;
   }

   // the PDF with its compressed streams inflated, so the page's text operators can be counted
   private static String pdfContent(byte[] pdf) throws Exception {
      String raw = new String(pdf, StandardCharsets.ISO_8859_1);
      StringBuilder content = new StringBuilder(raw);
      Matcher stream = PDF_STREAM.matcher(raw);

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

   // counts the DrawingML outer shadows in the package parts under the folder (the theme part
   // always has some, so it is left out)
   private static int countShadows(byte[] ooxml, String folder) throws Exception {
      int count = 0;

      try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ooxml))) {
         for(ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
            String name = entry.getName();

            if(name.startsWith(folder) && name.indexOf('/', folder.length()) < 0 &&
               name.endsWith(".xml"))
            {
               Matcher matcher = OUTER_SHADOW.matcher(
                  new String(zip.readAllBytes(), StandardCharsets.UTF_8));

               while(matcher.find()) {
                  count++;
               }
            }
         }
      }

      return count;
   }

   private byte[] export(int type) throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      AbstractVSExporter exporter = (AbstractVSExporter) AbstractVSExporter.getVSExporter(
         type, PortalThemesManager.getColorTheme(), out, false, null);
      exporter.setSandbox(box);
      exporter.setAssetEntry(rvs.getEntry());
      exporter.setRuntimeViewsheet(rvs);
      exporter.export(box, "Current View", 0, null);
      exporter.write();
      return out.toByteArray();
   }

   private TextVSAssemblyInfo getText() {
      Viewsheet vs = viewsheetResource.getRuntimeViewsheet().getViewsheet();
      return (TextVSAssemblyInfo) ((VSAssembly) vs.getAssembly("Text1")).getVSAssemblyInfo();
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^H16_shadowscript");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final class Exports {
      byte[] png;
      String html;
      int pdfTextDraws;
      int xlsxShadows;
      int pptxShadows;
   }

   private static final Pattern PDF_TEXT_DRAW = Pattern.compile("\\bTj\\b");
   private static final Pattern PDF_STREAM =
      Pattern.compile("stream\r?\n(.*?)endstream", Pattern.DOTALL);
   private static final Pattern OUTER_SHADOW = Pattern.compile("<a:outerShdw\\b");
}
