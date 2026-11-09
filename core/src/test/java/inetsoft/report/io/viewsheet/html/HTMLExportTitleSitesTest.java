/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.io.viewsheet.html;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ThreadContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79693: title sites of the HTML viewsheet export (every title, including one wrapped in
 * html tags, shows as written) beyond the table, selection and radio titles covered by
 * {@link HTMLExportEscapingTest}: crosstab, check box and current selection.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class HTMLExportTitleSitesTest {
   private Principal oldPrincipal;
   private Viewsheet vs;
   private HTMLCoordinateHelper coord;

   @BeforeEach
   void setUp() {
      oldPrincipal = ThreadContext.getPrincipal();
      vs = new Viewsheet();
      coord = new HTMLCoordinateHelper();
      coord.setViewsheet(vs);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setPrincipal(oldPrincipal);
   }

   @Test
   void crosstabTitleShowsTextAsWritten() {
      HTMLExportEscapingTest.assertTitle(this::crosstab);
   }

   @Test
   void checkBoxTitleShowsTextAsWritten() {
      HTMLExportEscapingTest.assertTitle(this::checkBox);
   }

   @Test
   void currentSelectionTitleAndOutTitleShowTextAsWritten() {
      HTMLExportEscapingTest.assertTitle(title -> currentSelection(title, "ot", "ov"));
      HTMLExportEscapingTest.assertTitle(title -> currentSelection("ct", title, "ov"));

      Document doc = currentSelection("<html><b>Sales</b></html>", "<b>t</b>", "<b>v</b>");
      assertEquals(0, doc.select("b").size(), doc.html());
      assertTrue(doc.text().contains("<html><b>Sales</b></html>"), doc.text());
      assertTrue(doc.text().contains("<b>t</b>"), doc.text());
      assertTrue(doc.text().contains("<b>v</b>"), doc.text());
   }

   private Document crosstab(String title) {
      CrosstabVSAssembly ct = new CrosstabVSAssembly(vs, "C1");
      vs.addAssembly(ct);
      CrosstabVSAssemblyInfo info = (CrosstabVSAssemblyInfo) ct.getVSAssemblyInfo();
      info.setTitleValue(title);
      info.setTitleVisibleValue(true);
      DefaultTableLens base = new DefaultTableLens(2, 1);
      base.setObject(0, 0, "H");
      base.setObject(1, 0, "v");
      base.setFont(0, 0, new Font("Dialog", Font.PLAIN, 12));
      base.setFont(1, 0, new Font("Dialog", Font.PLAIN, 12));
      base.setHeaderRowCount(1);
      StringWriter sw = new StringWriter();
      new HTMLCrosstabHelper(coord, vs, ct).write(new PrintWriter(sw), ct, new VSTableLens(base));
      return Jsoup.parse(sw.toString());
   }

   private Document checkBox(String title) {
      CheckBoxVSAssembly cb = new CheckBoxVSAssembly(vs, "CB");
      vs.addAssembly(cb);
      CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) cb.getVSAssemblyInfo();
      info.setValues(new Object[] { "v1" });
      info.setLabels(new String[] { "l1" });
      info.setTitleValue(title);
      info.setTitleVisibleValue(true);
      StringWriter sw = new StringWriter();
      coord.writeList(sw, info, false, new Rectangle2D.Double(0, 0, 200, 100));
      return Jsoup.parse(sw.toString());
   }

   private Document currentSelection(String title, String outTitle, String outValue) {
      CurrentSelectionVSAssembly cs = new CurrentSelectionVSAssembly(vs, "CS");
      vs.addAssembly(cs);
      CurrentSelectionVSAssemblyInfo info = (CurrentSelectionVSAssemblyInfo) cs.getVSAssemblyInfo();
      info.setTitleValue(title);
      info.setTitleVisibleValue(true);
      info.setShowCurrentSelection(true);
      info.setOutSelectionValue("n1", outTitle, outValue);
      StringWriter sw = new StringWriter();
      coord.writeCurrentSelection(sw, info, vs);
      return Jsoup.parse(sw.toString());
   }
}
