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
 * Bug #79693: title sites of the HTML viewsheet export that follow the viewer rule (markup and
 * the html wrapper kept, other text escaped) beyond the table, selection and radio titles
 * covered by {@link HTMLExportEscapingTest}: crosstab, check box and current selection.
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
   void crosstabTitleFollowsViewerRule() {
      assertEquals("x", crosstab("<b>x</b>").select("span b").text());
      assertEquals("Sales", crosstab("<html><b>Sales</b></html>").select("span b").text());
      assertTrue(crosstab("T<t & 'x'").text().contains("T<t & 'x'"));
   }

   @Test
   void checkBoxTitleFollowsViewerRule() {
      assertEquals("x", checkBox("<b>x</b>").select("span b").text());
      assertEquals("Sales", checkBox("<html><b>Sales</b></html>").select("span b").text());
      assertTrue(checkBox("T<t & 'x'").text().contains("T<t & 'x'"));
   }

   @Test
   void currentSelectionTitleAndOutTitleFollowViewerRuleValueStaysText() {
      Document doc = currentSelection("<html><b>Sales</b></html>", "<b>t</b>", "<b>v</b>");
      assertEquals(2, doc.select("b").size(), "title and out title keep markup");
      assertFalse(doc.html().contains("<html><b>Sales"), doc.html());
      // the viewer shows the selected value with interpolation, i.e. as text
      assertTrue(doc.text().contains("<b>v</b>"), doc.text());

      doc = currentSelection("T<t & 'x'", "O<t & 'y'", "a<b");
      assertTrue(doc.text().contains("T<t & 'x'"), doc.text());
      assertTrue(doc.text().contains("O<t & 'y'"), doc.text());
      assertTrue(doc.text().contains("a<b"), doc.text());
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
