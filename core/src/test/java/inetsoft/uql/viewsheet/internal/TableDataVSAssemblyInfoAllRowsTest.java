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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.BoundTableAssemblyInfo;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Include all rows" (Bug #82313) is kept on the table/crosstab assembly and on the bound table
 * the query runs on: it is saved, cloned and compared like the rest of the binding.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableDataVSAssemblyInfoAllRowsTest {
   @Test
   void setReportsWhetherTheListChanged() {
      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();

      assertTrue(info.getAllRows().isEmpty());
      assertFalse(info.setAllRows(null));
      assertFalse(info.setAllRows(List.of()));
      assertTrue(info.setAllRows(List.of("Product")));
      assertFalse(info.setAllRows(List.of("Product")));
      assertTrue(info.setAllRows(List.of()));
      assertTrue(info.getAllRows().isEmpty());
   }

   @Test
   void deepCloneKeepsItsOwnList() {
      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();
      info.setAllRows(List.of("Product"));

      CrosstabVSAssemblyInfo copy = (CrosstabVSAssemblyInfo) info.clone(false);
      info.setAllRows(List.of("Product", "Order"));

      assertEquals(List.of("Product"), copy.getAllRows());
   }

   @Test
   void survivesSaveAndLoad() throws Exception {
      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();
      info.setAllRows(List.of("Product", "Order Item"));

      StringWriter out = new StringWriter();
      info.writeXML(new PrintWriter(out));
      Document doc = Tool.parseXML(
         new ByteArrayInputStream(out.toString().getBytes(StandardCharsets.UTF_8)));
      CrosstabVSAssemblyInfo loaded = new CrosstabVSAssemblyInfo();
      loaded.parseXML(doc.getDocumentElement());

      assertEquals(List.of("Product", "Order Item"), loaded.getAllRows());
   }

   /** A saved viewsheet loaded in a fresh process builds its assemblies from this XML only. */
   @Test
   void survivesSavingAndReloadingTheWholeViewsheet() throws Exception {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      vs.addAssembly(crosstab);
      ((CrosstabVSAssemblyInfo) crosstab.getInfo()).setAllRows(List.of("Product"));
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      ((TableVSAssemblyInfo) table.getInfo()).setAllRows(List.of("Order", "Product"));
      vs.addAssembly(new CrosstabVSAssembly(vs, "Plain"));

      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      vs.writeXML(writer);
      writer.flush();
      Document doc = Tool.parseXML(
         new ByteArrayInputStream(out.toString().getBytes(StandardCharsets.UTF_8)));
      Viewsheet loaded = new Viewsheet();
      loaded.parseXML(doc.getDocumentElement());

      assertEquals(List.of("Product"),
                   ((CrosstabVSAssemblyInfo) loaded.getAssembly("Crosstab1").getInfo())
                      .getAllRows());
      assertEquals(List.of("Order", "Product"),
                   ((TableVSAssemblyInfo) loaded.getAssembly("Table1").getInfo()).getAllRows());
      assertTrue(((CrosstabVSAssemblyInfo) loaded.getAssembly("Plain").getInfo())
                    .getAllRows().isEmpty());
   }

   @Test
   void boundTableKeepsItAcrossCloneAndXml() throws Exception {
      BoundTableAssemblyInfo info = new BoundTableAssemblyInfo();
      assertTrue(info.getAllRows().isEmpty());
      info.setAllRows(List.of("Product"));

      BoundTableAssemblyInfo copy = (BoundTableAssemblyInfo) info.clone();
      info.setAllRows(null);
      assertEquals(List.of("Product"), copy.getAllRows());

      StringWriter out = new StringWriter();
      copy.writeXML(new PrintWriter(out));
      assertTrue(out.toString().contains("<allRows>"), out.toString());

      // an unset table writes nothing, so the XML (the query cache key) of every existing table
      // is unchanged
      StringWriter plain = new StringWriter();
      info.writeXML(new PrintWriter(plain));
      assertFalse(plain.toString().contains("allRows"), plain.toString());
   }
}
