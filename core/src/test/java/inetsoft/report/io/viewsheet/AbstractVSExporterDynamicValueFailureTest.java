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
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82120: a failing dynamic property expression ("=expression") only wrote a WARN to the
 * log during an export, while a failing component script already put a warning on the document
 * (#8467). The first failing assembly also ended the execution of the dynamic values of the
 * assemblies after it.
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
class AbstractVSExporterDynamicValueFailureTest {
   @ParameterizedTest(name = "match layout {0}")
   @ValueSource(booleans = { false, true })
   void failingDynamicValueIsReportedAndLaterAssembliesStillRun(boolean match) throws Exception {
      ViewsheetSandbox box = open("=noSuchFunction123()", "='second ok'");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVVSExporter exporter = new CSVVSExporter(out, null);
      exporter.setMatchLayout(match);

      assertDoesNotThrow(() -> {
         exporter.export(box, "Current View", 0, null);
         exporter.write();
      });

      TextVSAssembly warning = exporter.viewsheet.getWarningTextAssembly(false);
      assertNotNull(warning, "the document should carry a warning");
      assertTrue(warning.getText().contains("component script failed"), warning.getText());
      // the assembly after the failing one still got its dynamic value
      TextVSAssembly second = (TextVSAssembly) exporter.viewsheet.getAssembly("TextB82120");
      assertEquals("second ok", second.getText());
   }

   @ParameterizedTest(name = "match layout {0}")
   @ValueSource(booleans = { false, true })
   void workingDynamicValueAddsNoWarning(boolean match) throws Exception {
      ViewsheetSandbox box = open("='first ok'", "='second ok'");
      CSVVSExporter exporter = new CSVVSExporter(new ByteArrayOutputStream(), null);
      exporter.setMatchLayout(match);
      exporter.export(box, "Current View", 0, null);
      assertNull(exporter.viewsheet.getWarningTextAssembly(false), "no warning expected");
   }

   private ViewsheetSandbox open(String first, String second) {
      ViewsheetSandbox box = viewsheetResource.getRuntimeViewsheet().getViewsheetSandbox()
         .orElseThrow();
      Viewsheet vs = box.getViewsheet();
      add(vs, "TextA82120", first);
      add(vs, "TextB82120", second);
      return box;
   }

   private static void add(Viewsheet vs, String name, String text) {
      TextVSAssembly assembly = new TextVSAssembly(vs, name);
      ((TextVSAssemblyInfo) assembly.getVSAssemblyInfo()).setTextValue(text);
      vs.addAssembly(assembly);
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
