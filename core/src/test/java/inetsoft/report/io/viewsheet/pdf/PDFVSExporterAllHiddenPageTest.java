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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81058: a viewsheet whose assemblies are all hidden used to export a blank default
 * 612x792 page before the viewsheet-sized page, because the all-hidden clip was set before
 * the page was created and PDFPrinter.setClip() starts a page. It must be a single page of
 * the viewsheet's size.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/io/viewsheet/pdf/S22_shapes.zip")
@Tag("core")
@Tag("integration")
class PDFVSExporterAllHiddenPageTest {
   @Test
   void allHiddenViewsheetExportsASingleViewsheetSizedPage() throws Exception {
      for(Assembly assembly : getBox().getViewsheet().getAssemblies()) {
         ((VSAssembly) assembly).getVSAssemblyInfo().setVisibleValue("hide");
      }

      int[] size = exportPageSize();
      assertNotEquals(612, size[0], "no blank default-size page should be exported");
      assertTrue(size[0] > 1 && size[1] > 1);
   }

   private int[] exportPageSize() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox box = getBox();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      AbstractVSExporter exporter = (AbstractVSExporter) AbstractVSExporter.getVSExporter(
         FileFormatInfo.EXPORT_TYPE_PDF, PortalThemesManager.getColorTheme(), out, false, null);
      exporter.setSandbox(box);
      exporter.setAssetEntry(rvs.getEntry());
      exporter.setRuntimeViewsheet(rvs);
      exporter.export(box, "Current View", 0, null);
      exporter.write();

      String pdf = out.toString(StandardCharsets.ISO_8859_1);
      Matcher matcher = MEDIA_BOX.matcher(pdf);
      assertTrue(matcher.find(), "the PDF should have a MediaBox");
      int[] size = { Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)) };
      assertFalse(matcher.find(), "the viewsheet should be exported on a single page");
      return size;
   }

   private ViewsheetSandbox getBox() {
      return viewsheetResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^S22_shapes");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private static final Pattern MEDIA_BOX =
      Pattern.compile("/MediaBox\\s*\\[\\s*0\\s+0\\s+(\\d+)\\s+(\\d+)\\s*]");
}
