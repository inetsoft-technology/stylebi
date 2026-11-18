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
import inetsoft.report.io.excel.SheetMaxRowsException;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79764: when a component hits the Excel sheet max rows limit, the export wrote the warning
 * but stopped writing the remaining components, because the catch of
 * {@link SheetMaxRowsException} was outside the loop over the assemblies. Only the component that
 * hits the limit is cut; every other component is still written, and the warning is recorded once.
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
class AbstractVSExporterSheetMaxRowsTest {
   @Test
   void remainingComponentsAreWrittenAfterSheetMaxRows() throws Exception {
      Viewsheet vs = getBox().getViewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "TextA79764"));
      vs.addAssembly(new TextVSAssembly(vs, "TextB79764"));
      vs.addAssembly(new TextVSAssembly(vs, "TextC79764"));
      LimitedExporter exporter = new LimitedExporter();

      exporter.export(getBox(), "Current View", 0, null);

      // the first text written hits the limit, the other two must still be written
      assertEquals(3, exporter.attempted.size(), exporter.attempted.toString());
      assertEquals(15, exporter.maxRows());
   }

   private ViewsheetSandbox getBox() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      return rvs.getViewsheetSandbox().orElseThrow();
   }

   private static final class LimitedExporter extends CSVVSExporter {
      LimitedExporter() {
         super(new ByteArrayOutputStream(), null);
      }

      @Override
      protected void writeText(VariableTable variableTable, TextVSAssembly assembly) {
         String name = assembly.getAbsoluteName();

         if(name.endsWith("79764")) {
            attempted.add(name);

            if(attempted.size() == 1) {
               throw new SheetMaxRowsException(15);
            }
         }
      }

      int maxRows() {
         return getMaxRows();
      }

      final List<String> attempted = new ArrayList<>();
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource = new RuntimeViewsheetExtension(
      createEvent());

   private static inetsoft.web.viewsheet.event.OpenViewsheetEvent createEvent() {
      inetsoft.web.viewsheet.event.OpenViewsheetEvent event =
         new inetsoft.web.viewsheet.event.OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^TEST_GraphRender");
      event.setViewer(true);
      return event;
   }
}
