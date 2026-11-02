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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.XTable;
import inetsoft.uql.ColumnSelection;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
class CSVVSExporterHiddenColumnTest {
   private String crosstab(int hide, boolean keep) throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      CrosstabVSAssembly ct = new CrosstabVSAssembly(vs, "CT");
      ct.setSourceInfo((SourceInfo) ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().clone());
      VSCrosstabInfo ci = ct.getVSCrosstabInfo();
      VSDimensionRef d1 = new VSDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      d1.setGroupColumnValue("type");
      VSDimensionRef d2 = new VSDimensionRef(new ColumnRef(new AttributeRef(null, "year")));
      d2.setGroupColumnValue("year");
      ci.setDesignRowHeaders(new DataRef[] { d1, d2 });
      VSAggregateRef a1 = new VSAggregateRef();
      a1.setColumnValue("wind");
      a1.setFormulaValue("Sum");
      VSAggregateRef a2 = new VSAggregateRef();
      a2.setColumnValue("wind");
      a2.setFormulaValue("Max");
      ci.setDesignAggregates(new DataRef[] { a1, a2 });
      vs.addAssembly(ct);
      XTable lens = box.getVSTableLens("CT", false);
      if(hide >= 0) {
         ((CrosstabVSAssemblyInfo) ct.getVSAssemblyInfo()).addHiddenColumn(hide, lens);
      }
      return export(box, "Crosstab hide=" + hide + " keepHeader=" + keep, "CT", keep);
   }

   private String table(int zero, boolean keep) throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly t = new TableVSAssembly(vs, "TB");
      t.setSourceInfo((SourceInfo) ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().clone());
      ColumnSelection cols = new ColumnSelection();
      for(String n : new String[] { "name", "type", "year", "wind" }) {
         cols.addAttribute(new ColumnRef(new AttributeRef(null, n)));
      }
      t.setColumnSelection(cols);
      vs.addAssembly(t);
      box.getVSTableLens("TB", false);
      if(zero >= 0) {
         ((TableVSAssemblyInfo) t.getVSAssemblyInfo()).setColumnWidth(zero, 0);
      }
      return export(box, "Table width0 col=" + zero + " keepHeader=" + keep, "TB", keep);
   }

   private String calc(int zero, boolean keep) throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      CalcTableVSAssembly c = new CalcTableVSAssembly(vs, "CALC");
      c.setSourceInfo((SourceInfo) ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().clone());
      TableLayout layout = c.getTableLayout();
      layout.setColCount(3);
      String[] txt = { "A", "B", "C" };
      for(int i = 0; i < 3; i++) {
         TableCellBinding b = new TableCellBinding(CellBinding.BIND_TEXT, txt[i]);
         layout.setCellBinding(0, i, b);
      }
      vs.addAssembly(c);
      box.getVSTableLens("CALC", false);
      if(zero >= 0) {
         ((CalcTableVSAssemblyInfo) c.getVSAssemblyInfo()).setColumnWidth(zero, 0);
      }
      return export(box, "Calc width0 col=" + zero + " keepHeader=" + keep, "CALC", keep);
   }

   private String export(ViewsheetSandbox box, String label, String asm, boolean keep) throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVVSExporter ex = new CSVVSExporter(out, null);
      ex.setKeepHeader(keep);
      ex.export(box, "S", null);
      ex.write();
      String first = null;

      try(ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
         ZipEntry e;

         while((e = z.getNextEntry()) != null) {
            String s = new String(z.readAllBytes(), StandardCharsets.UTF_8);

            if(e.getName().endsWith("_" + asm + ".csv")) {
               first = s.split("\r?\n")[0];
            }
         }
      }

      assertNotNull(first, label);
      return first;
   }

   @Test
   void crosstabFirstColumnHidden() throws Exception {
      assertEquals("year,,Sum(wind)/Max(wind)", crosstab(0, true));
   }

   @Test
   void crosstabFirstColumnHiddenNoHeader() throws Exception {
      assertEquals("1986,Sum(wind),8115", crosstab(0, false));
   }

   @Test
   void crosstabNoneHidden() throws Exception {
      assertEquals("type,year,,Sum(wind)/Max(wind)", crosstab(-1, true));
   }

   @Test
   void crosstabMiddleColumnHidden() throws Exception {
      assertEquals("type,,Sum(wind)/Max(wind)", crosstab(1, true));
   }

   @Test
   void tableFirstColumnWidthZero() throws Exception {
      assertEquals("type,year,wind", table(0, true));
   }

   @Test
   void tableMiddleColumnWidthZero() throws Exception {
      assertEquals("name,year,wind", table(1, true));
   }

   @Test
   void calcTableFirstColumnWidthZero() throws Exception {
      assertEquals("B,C", calc(0, true));
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(ev());

   static OpenViewsheetEvent ev() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }
}
