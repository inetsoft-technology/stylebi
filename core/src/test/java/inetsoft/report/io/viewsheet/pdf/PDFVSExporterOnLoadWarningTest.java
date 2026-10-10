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

import inetsoft.report.ReportElement;
import inetsoft.report.ReportSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.SectionElementDef;
import inetsoft.report.internal.TextBased;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.Catalog;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78219: a PDF export through a print layout, the sheet's own or one inherited from an
 * embedded viewsheet, must carry the failed onLoad script warning like the canvas export.
 * The print layout PDF is built from the report sheets the exporter queues, so the test
 * checks their texts; writing the PDF needs the text shaping classes of
 * inetsoft-xml-formats, which are not on this module's test classpath.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome(importResources = "/inetsoft/report/io/viewsheet/pdf/PDFVSExporterOnLoadWarningTest.zip")
@Tag("core")
@Tag("integration")
class PDFVSExporterOnLoadWarningTest {
   // ownerr has its own print layout and an onLoad script that throws
   @Test
   void ownPrintLayoutShowsOnLoadWarning() throws Exception {
      List<String> texts = exportPrintLayoutTexts(ownErr);
      assertEquals(1, countWarnings(texts), () -> "report texts: " + texts);
   }

   // wrappererr inherits the print layout of the embedded child, its own onLoad throws
   @Test
   void inheritedPrintLayoutShowsOnLoadWarning() throws Exception {
      List<String> texts = exportPrintLayoutTexts(wrapperErr);
      assertEquals(1, countWarnings(texts), () -> "report texts: " + texts);
   }

   // own has the same print layout and an onLoad script that succeeds
   @Test
   void printLayoutWithoutOnLoadFailureHasNoWarning() throws Exception {
      List<String> texts = exportPrintLayoutTexts(own);
      assertTrue(texts.contains("OWNTEXT"), () -> "report texts: " + texts);
      assertEquals(0, countWarnings(texts), () -> "report texts: " + texts);
   }

   private List<String> exportPrintLayoutTexts(RuntimeViewsheetExtension resource)
      throws Exception
   {
      RuntimeViewsheet rvs = resource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      AbstractVSExporter exporter = (AbstractVSExporter) AbstractVSExporter.getVSExporter(
         FileFormatInfo.EXPORT_TYPE_PDF, PortalThemesManager.getColorTheme(),
         new ByteArrayOutputStream(), false, null);
      exporter.setSandbox(box);
      exporter.setAssetEntry(rvs.getEntry());
      exporter.setRuntimeViewsheet(rvs);
      exporter.export(box, "Current View", 0, null);

      Field field = PDFVSExporter.class.getDeclaredField("reportList");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<ReportSheet> reports = (List<ReportSheet>) field.get(exporter);
      assertEquals(1, reports.size(), "the sheet should be exported through its print layout");

      List<String> texts = new ArrayList<>();

      for(Object elem : reports.get(0).getAllElements()) {
         addTexts((ReportElement) elem, texts);
      }

      return texts;
   }

   private static void addTexts(ReportElement elem, List<String> texts) {
      if(elem instanceof SectionElementDef section) {
         for(ReportElement child : section.getElements()) {
            addTexts(child, texts);
         }
      }
      else if(elem instanceof TextBased text && text.getText() != null) {
         texts.add(text.getText());
      }
   }

   private static long countWarnings(List<String> texts) {
      String message = Catalog.getCatalog().getString("vs.export.onLoadScriptFailed");
      return texts.stream().filter(text -> text.contains(message)).count();
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent(String name) {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^RegXui/print/" + name);
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension ownErr =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("ownerr"));

   @RegisterExtension
   RuntimeViewsheetExtension wrapperErr =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("wrappererr"));

   @RegisterExtension
   RuntimeViewsheetExtension own =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent("own"));
}
