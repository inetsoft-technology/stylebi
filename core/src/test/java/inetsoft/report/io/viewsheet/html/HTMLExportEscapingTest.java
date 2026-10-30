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
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79693: the HTML viewsheet export wrote cell, label and title text without escaping, so
 * text such as {@code a<b} was swallowed by the browser's HTML parser. The output is parsed with
 * jsoup (an HTML5 parser) and the text a browser would show is compared with the input.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class HTMLExportEscapingTest {
   private static final Font FONT = new Font("Dialog", Font.PLAIN, 12);
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

   // ---- table ----

   @Test
   void tableCellKeepsPlainText() {
      Document doc = writeTable(false, "a<b", "Smith & Sons", "A&B Corp", "1234", "O'Brien",
                                "1 < 2 && 3 > 2");
      List<String> texts = cellTexts(doc);
      assertTrue(texts.containsAll(
         List.of("a<b", "Smith & Sons", "A&B Corp", "1234", "O'Brien", "1 < 2 && 3 > 2")),
                 texts.toString());
   }

   @Test
   void tableCellKeepsViewerHtml() {
      Document doc = writeTable(false, "<b>bold</b>", "x<br>y", "l1\nl2");
      assertEquals("bold", doc.select("td b").text());
      assertEquals(2, doc.select("td br").size());
   }

   @Test
   void tableCellNewlineInPlainTextKeepsText() {
      Document doc = writeTable(false, "a<b\nc");
      assertTrue(doc.select("td br").size() == 1);
      assertTrue(doc.select("td").text().contains("a<b"), doc.select("td").text());
   }

   @Test
   void tableTitleIsEscaped() {
      Document doc = writeTable(true, "v");
      assertTrue(doc.text().contains("T<t & 'x'"), doc.text());
   }

   // ---- crosstab ----

   @Test
   void crosstabCellKeepsPlainTextAndHtml() {
      Document doc = writeCrosstab("a<b", "Smith & Sons", "<b>bold</b>", "42");
      List<String> texts = doc.select("div").stream().map(e -> e.ownText())
         .collect(Collectors.toList());
      assertTrue(texts.contains("a<b"), texts.toString());
      assertTrue(texts.contains("Smith & Sons"), texts.toString());
      assertTrue(texts.contains("42"), texts.toString());
      assertEquals("bold", doc.select("b").text());
   }

   // ---- selection list / tree ----

   @Test
   void selectionListLabelsTitleAndValueAttribute() {
      SelectionListVSAssembly sel = new SelectionListVSAssembly(vs, "SL");
      vs.addAssembly(sel);
      SelectionList list = new SelectionList();
      list.addSelectionValue(selValue("a<b"));
      list.addSelectionValue(selValue("O'Brien"));
      list.addSelectionValue(selValue("Smith & Sons"));
      list.addSelectionValue(selValue("<i>it</i>"));
      sel.setSelectionList(list);
      ((SelectionListVSAssemblyInfo) sel.getVSAssemblyInfo()).setTitleValue("T<t & 'x'");
      StringWriter sw = new StringWriter();
      new HTMLSelectionListHelper(coord, vs, sel).write(new PrintWriter(sw), sel);
      Document doc = Jsoup.parse(sw.toString());
      String text = doc.text();
      assertTrue(text.contains("a<b"), text);
      assertTrue(text.contains("O'Brien"), text);
      assertTrue(text.contains("Smith & Sons"), text);
      assertTrue(text.contains("T<t & 'x'"), text);
      assertEquals("it", doc.select("i").text());
      // a single quote must not truncate the single-quoted value attribute
      assertEquals(4, doc.select("input").size());
      assertTrue(doc.select("input").stream().anyMatch(e -> e.attr("value").contains("O'Brien")));
   }

   @Test
   void selectionTreeLabelsAndValueAttribute() {
      SelectionTreeVSAssembly sel = new SelectionTreeVSAssembly(vs, "ST");
      vs.addAssembly(sel);
      CompositeSelectionValue root = new CompositeSelectionValue("root", "root");
      root.getSelectionList().addSelectionValue(compValue("a<b"));
      root.getSelectionList().addSelectionValue(compValue("O'Brien"));
      sel.setCompositeSelectionValue(root);
      StringWriter sw = new StringWriter();
      new HTMLSelectionTreeHelper(coord, vs, sel).write(new PrintWriter(sw), sel);
      Document doc = Jsoup.parse(sw.toString());
      assertTrue(doc.text().contains("a<b"), doc.text());
      assertTrue(doc.text().contains("O'Brien"), doc.text());
      assertTrue(doc.select("input").stream().anyMatch(e -> e.attr("value").contains("O'Brien")));
   }

   // ---- coordinate helper ----

   @Test
   void textAssemblyKeepsPlainTextAndViewerHtml() {
      assertEquals("a<b", textOf("a<b", true));
      assertEquals("Smith & Sons", textOf("Smith & Sons", true));
      assertEquals("A&B Corp", textOf("A&B Corp", true));
      assertEquals("l1 l2", textOf("l1\nl2", true));
      Document doc = writeText("<b>bold</b><br>x", true);
      assertEquals("bold", doc.select("b").text());
      assertEquals(1, doc.select("br").size());
   }

   @Test
   void labelTextIsAlwaysEscaped() {
      assertEquals("<b>bold</b>", textOf("<b>bold</b>", false));
      assertEquals("a<b", textOf("a<b", false));
   }

   @Test
   void comboBoxOptionsAreEscaped() {
      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "CB");
      vs.addAssembly(combo);
      ComboBoxVSAssemblyInfo info = (ComboBoxVSAssemblyInfo) combo.getVSAssemblyInfo();
      info.setValues(new Object[] { "O'Brien", "<none>", "v3" });
      info.setLabels(new String[] { "a<b", "<none>", "Smith & Sons" });
      info.setSelectedObject("v3");
      StringWriter sw = new StringWriter();
      coord.writeComboBox(sw, info, new Rectangle2D.Double(0, 0, 100, 20));
      Document doc = Jsoup.parse(sw.toString());
      assertEquals(List.of("a<b", "<none>", "Smith & Sons"),
                   doc.select("option").stream().map(e -> e.text()).collect(Collectors.toList()));
      assertEquals(List.of("O'Brien", "<none>", "v3"),
                   doc.select("option").stream().map(e -> e.attr("value"))
                      .collect(Collectors.toList()));
   }

   @Test
   void textInputAndButtonAreEscaped() {
      TextInputVSAssembly input = new TextInputVSAssembly(vs, "TI");
      vs.addAssembly(input);
      StringWriter sw = new StringWriter();
      coord.writeTextInput(sw, new Rectangle2D.Double(0, 0, 100, 20), input,
                           input.getVSAssemblyInfo().getFormat(), "a<b & </textarea>x", 1);
      assertEquals("a<b & </textarea>x", Jsoup.parse(sw.toString()).select("textarea").text());

      sw = new StringWriter();
      coord.writeButton(sw, new Rectangle2D.Double(0, 0, 100, 20),
                        input.getVSAssemblyInfo().getFormat(), "O'Brien <go>", 1);
      assertEquals("O'Brien <go>", Jsoup.parse(sw.toString()).select("input").attr("value"));
   }

   // ---- shared helper rules ----

   @Test
   void escapeRules() {
      assertEquals("a&lt;b &amp; &gt;", HTMLEscape.text("a<b & >"));
      assertEquals("O'Brien \"q\"", HTMLEscape.text("O'Brien \"q\""));
      assertEquals("O&#39;Brien &quot;q&quot; &lt;", HTMLEscape.attribute("O'Brien \"q\" <"));
      assertNull(HTMLEscape.text(null));
      // markup is kept where the viewer would render it
      assertEquals("<b>x</b>", HTMLEscape.viewerCellText("<b>x</b>"));
      assertEquals("<a href='u'>x</a>", HTMLEscape.viewerText("<a href='u'>x</a>"));
      // and escaped otherwise
      assertEquals("a&lt;b", HTMLEscape.viewerCellText("a<b"));
      assertEquals("a&lt;b", HTMLEscape.viewerText("a<b"));
      assertEquals("1234", HTMLEscape.viewerCellText(1234));
   }

   // ---- helpers ----

   private Document writeTable(boolean withTitle, String... values) {
      TableVSAssembly table = new TableVSAssembly(vs, "T1");
      vs.addAssembly(table);

      if(withTitle) {
         TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
         info.setTitleValue("T<t & 'x'");
         info.setTitleVisibleValue(true);
      }

      StringWriter sw = new StringWriter();
      new HTMLTableHelper(coord, vs, table).write(new PrintWriter(sw), table, lens(values));
      return Jsoup.parse(sw.toString());
   }

   private Document writeCrosstab(String... values) {
      CrosstabVSAssembly ct = new CrosstabVSAssembly(vs, "C1");
      vs.addAssembly(ct);
      StringWriter sw = new StringWriter();
      new HTMLCrosstabHelper(coord, vs, ct).write(new PrintWriter(sw), ct, lens(values));
      return Jsoup.parse(sw.toString());
   }

   private static SelectionValue selValue(String label) {
      SelectionValue value = new SelectionValue(label, label);
      value.setFormat(new VSCompositeFormat());
      return value;
   }

   private static CompositeSelectionValue compValue(String label) {
      CompositeSelectionValue value = new CompositeSelectionValue(label, label);
      value.setFormat(new VSCompositeFormat());
      return value;
   }

   private static VSTableLens lens(String... values) {
      DefaultTableLens base = new DefaultTableLens(values.length + 1, 1);
      base.setObject(0, 0, "H");
      base.setFont(0, 0, FONT);

      for(int i = 0; i < values.length; i++) {
         base.setObject(i + 1, 0, values[i]);
         base.setFont(i + 1, 0, FONT);
      }

      base.setHeaderRowCount(1);
      return new VSTableLens(base);
   }

   private static List<String> cellTexts(Document doc) {
      return doc.select("td > div").stream().map(e -> e.wholeText()).collect(Collectors.toList());
   }

   private Document writeText(String txt, boolean viewerHtml) {
      StringWriter sw = new StringWriter();
      coord.writeText(sw, new Rectangle2D.Double(0, 0, 100, 20), new VSCompositeFormat(), txt,
                      null, false, null, false, 1, viewerHtml);
      return Jsoup.parse(sw.toString());
   }

   private String textOf(String txt, boolean viewerHtml) {
      return writeText(txt, viewerHtml).body().text();
   }
}
