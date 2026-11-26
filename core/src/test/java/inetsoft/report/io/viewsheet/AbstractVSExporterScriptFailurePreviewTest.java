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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.FileSystemService;
import inetsoft.util.ThreadContext;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.composer.vs.controller.ExportControllerService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81796: the Composer Preview export (ExportControllerService) re-runs every component
 * script in its design-mode print-layout loop. A failing script there must not fail the export.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/graph/GraphRenderTest.zip")
@Tag("core")
@Tag("integration")
class AbstractVSExporterScriptFailurePreviewTest {
   @Autowired
   ViewsheetService viewsheetService;
   @Autowired
   CoreLifecycleService coreLifecycleService;

   @Test
   void previewPrintLayoutExportSkipsFailingTextScript() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      System.out.println("P3-PREVIEW mode=" + box.getMode() + " design=" +
                         (box.getMode() == inetsoft.uql.asset.AbstractSheet.SHEET_DESIGN_MODE));
      Viewsheet vs = box.getViewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text81796");
      text.getVSAssemblyInfo().setScript("noSuchFunction123();");
      text.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(text);
      // CSV needs a table, a plain one with no script
      vs.addAssembly(AbstractVSExporterScriptFailureDataTest.createAssembly(vs, "table"));

      ExportControllerService exportService = new ExportControllerService(
         viewsheetService, coreLifecycleService,
         new BinaryTransferService(FileSystemService.getInstance()));

      try {
         exportService.exportViewsheet(
            viewsheetResource.getRuntimeId(), FileFormatInfo.EXPORT_TYPE_CSV,
            "CSV", false, false, false, true, true, false, new String[0], false, false,
            new CSVConfig(), ThreadContext.getPrincipal());
      }
      catch(Throwable ex) {
         System.out.println("P3-CAUGHT preview " + ex.getClass().getName() + " <- " +
            String.join(" <- ", AbstractVSExporterScriptFailureDataTest.topFrames(ex, 12)));
         throw ex;
      }
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^TEST_GraphRender");
      event.setViewer(false);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());
}
