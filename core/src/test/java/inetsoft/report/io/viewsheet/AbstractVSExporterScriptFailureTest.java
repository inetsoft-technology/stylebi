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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81796: a failing component script made the export answer HTTP 500 with no file while the
 * viewer renders the dashboard without it. {@code prepareSheet} re-executes the script of every
 * assembly and let its ScriptException (already logged where it is created) abort the export.
 * The viewer skips the failing script, so the export must too.
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
class AbstractVSExporterScriptFailureTest {
   @ParameterizedTest(name = "match layout {0}, script {1}")
   @CsvSource({
      "false,noSuchFunction123();", "true,noSuchFunction123();",
      "false,throw 'boom';", "true,throw 'boom';"
   })
   void failingComponentScriptDoesNotFailTheExport(boolean match, String script)
      throws Exception
   {
      ViewsheetSandbox box = viewsheetResource.getRuntimeViewsheet().getViewsheetSandbox()
         .orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text81796");
      text.getVSAssemblyInfo().setScript(script);
      text.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(text);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVVSExporter exporter = new CSVVSExporter(out, null);
      exporter.setMatchLayout(match);

      assertDoesNotThrow(() -> {
         exporter.export(box, "Current View", 0, null);
         exporter.write();
      });
      assertTrue(out.size() > 0, "the export should be written");
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^TEST_GraphRender");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());
}
