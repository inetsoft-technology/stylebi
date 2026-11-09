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
   private int seq;
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
      Document doc = writeTable(null, "a<b", "Smith & Sons", "A&B Corp", "1234", "O'Brien",
                                "1 < 2 && 3 > 2", "<none>", "<null>", "Smith & Sons <NY>",
                                "x<y and y>z");
      List<String> texts = cellTexts(doc);
      assertTrue(texts.containsAll(
         List.of("a<b", "Smith & Sons", "A&B Corp", "1234", "O'Brien", "1 < 2 && 3 > 2",
                 "<none>", "<null>", "Smith & Sons <NY>", "x<y and y>z")),
                 texts.toString());
   }

   @Test
   void tableCellNeverBecomesMarkup() {
      Document doc = writeTable(null, "<b>bold</b>", "x<br>y", "l1\nl2");
      assertEquals(0, doc.select("td b").size());
      assertTrue(cellTexts(doc).containsAll(List.of("<b>bold</b>", "x<br>y")),
                 cellTexts(doc).toString());
      // only the newline conversion produces a br
      assertEquals(1, doc.select("td br").size());
   }

   @Test
   void tableCellNewlineInPlainTextKeepsText() {
      Document doc = writeTable(null, "a<b\nc");
      assertTrue(doc.select("td br").size() == 1);
      assertTrue(doc.select("td").text().contains("a<b"), doc.select("td").text());
   }

   @Test
   void tableTitleShowsTextAsWritten() {
      assertTitle(this::writeTable);
   }

   @Test
   void selectionTitlesShowTextAsWritten() {
      assertTitle(this::writeSelectionList);
      assertTitle(this::writeSelectionTree);
   }

   @Test
   void containerTitleShowsTextAsWritten() {
      assertTitle(title -> {
         SelectionListVSAssembly sel = new SelectionListVSAssembly(vs, "SLC");
         vs.addAssembly(sel);
         SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) sel.getVSAssemblyInfo();
         info.setTitleValue("plain");
         StringBuffer buf = new StringBuffer();
         coord.appendContainerTitle(buf, info, 20, 0.5, title);
         return Jsoup.parse(buf.toString());
      });
   }

   @Test
   void radioTitleShowsTextAsWritten() {
      assertTitle(this::writeRadio);
   }

   @Test
   void titleHelperHasNoRawForm() {
      assertEquals("&lt;html&gt;&lt;b&gt;Sales&lt;/b&gt;&lt;/html&gt;",
                   HTMLEscape.text("<html><b>Sales</b></html>"));
      assertEquals("&lt;none&gt;", HTMLEscape.text("<none>"));
   }

   /**
    * Check one title site: every title of the value matrix shows as written, and an html
    * wrapped title or a markup payload never produces an element.
    */
   static void assertTitle(java.util.function.Function<String, Document> site) {
      for(String value : MATRIX) {
         String text = site.apply(value).text();
         String shown = value.replace('\n', ' ');
         assertTrue(text.contains(shown), value + " -> " + text);
      }

      for(String payload : PAYLOADS) {
         Document doc = site.apply(payload);
         assertTrue(doc.text().contains(payload), payload + " -> " + doc.text());
         assertEquals(0, doc.select("b, i, img").size(), payload + " -> " + doc.html());
      }
   }

   static final String[] MATRIX = {
      "<none>", "<null>", "a<b", "Smith & Sons <NY>", "x<y and y>z", "A&B Corp", "<5", "Plain",
      "12345", "&amp;", "Smith & Sons", "a<b\nc"
   };
   static final String[] PAYLOADS = {
      "<html><b>Sales</b></html>", "<html><img src=x onerror=1></html>", "<b>x</b>",
      "<img src=x onerror=1>"
   };

   // ---- crosstab ----

   @Test
   void crosstabCellKeepsTextAndNeverBecomesMarkup() {
      Document doc = writeCrosstab("a<b", "Smith & Sons", "<b>bold</b>", "42", "<none>",
                                   "Smith & Sons <NY>");
      List<String> texts = doc.select("div").stream().map(e -> e.ownText())
         .collect(Collectors.toList());
      assertTrue(texts.contains("a<b"), texts.toString());
      assertTrue(texts.contains("Smith & Sons"), texts.toString());
      assertTrue(texts.contains("42"), texts.toString());
      assertTrue(texts.contains("<b>bold</b>"), texts.toString());
      assertTrue(texts.contains("<none>"), texts.toString());
      assertTrue(texts.contains("Smith & Sons <NY>"), texts.toString());
      assertEquals(0, doc.select("b").size());
   }

   @Test
   void crosstabCellShowsEveryMatrixValueAsWritten() {
      for(String value : MATRIX) {
         Document doc = writeCrosstab(value);
         assertTrue(doc.text().contains(value.replace('\n', ' ')), value + " -> " + doc.text());
         assertEquals(0, doc.select("b, img, i").size(), doc.html());
      }

      for(String payload : PAYLOADS) {
         Document doc = writeCrosstab(payload);
         assertTrue(doc.text().contains(payload), payload + " -> " + doc.text());
         assertEquals(0, doc.select("b, img, i").size(), doc.html());
      }
   }

   // ---- selection list / tree ----

   @Test
   void selectionListLabelShowsEveryMatrixValueAsWritten() {
      for(String value : MATRIX) {
         assertLabelAsWritten(value, writeSelectionListLabel(value));
      }

      for(String payload : PAYLOADS) {
         assertLabelAsWritten(payload, writeSelectionListLabel(payload));
      }
   }

   @Test
   void selectionTreeLabelShowsEveryMatrixValueAsWritten() {
      for(String value : MATRIX) {
         assertLabelAsWritten(value, writeSelectionTreeLabel(value));
      }

      for(String payload : PAYLOADS) {
         assertLabelAsWritten(payload, writeSelectionTreeLabel(payload));
      }
   }

   private static void assertLabelAsWritten(String value, Document doc) {
      assertTrue(doc.text().contains(value.replace('\n', ' ')), value + " -> " + doc.text());
      assertEquals(0, doc.select("b, img, i").size(), value + " -> " + doc.html());
   }


   @Test
   void selectionListLabelsTitleAndValueAttribute() {
      SelectionListVSAssembly sel = new SelectionListVSAssembly(vs, "SL");
      vs.addAssembly(sel);
      SelectionList list = new SelectionList();
      list.addSelectionValue(selValue("a<b"));
      list.addSelectionValue(selValue("O'Brien"));
      list.addSelectionValue(selValue("Smith & Sons"));
      list.addSelectionValue(selValue("<i>it</i>"));
      list.addSelectionValue(selValue("<none>"));
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
      assertTrue(text.contains("<i>it</i>"), text);
      assertTrue(text.contains("<none>"), text);
      assertEquals(0, doc.select("i").size());
      // a single quote must not truncate the single-quoted value attribute
      assertEquals(5, doc.select("input").size());
      assertTrue(doc.select("input").stream().anyMatch(e -> e.attr("value").contains("O'Brien")));
   }

   @Test
   void selectionTreeLabelsAndValueAttribute() {
      SelectionTreeVSAssembly sel = new SelectionTreeVSAssembly(vs, "ST");
      vs.addAssembly(sel);
      CompositeSelectionValue root = new CompositeSelectionValue("root", "root");
      root.getSelectionList().addSelectionValue(compValue("a<b"));
      root.getSelectionList().addSelectionValue(compValue("O'Brien"));
      root.getSelectionList().addSelectionValue(compValue("<none>"));
      sel.setCompositeSelectionValue(root);
      StringWriter sw = new StringWriter();
      new HTMLSelectionTreeHelper(coord, vs, sel).write(new PrintWriter(sw), sel);
      Document doc = Jsoup.parse(sw.toString());
      assertTrue(doc.text().contains("a<b"), doc.text());
      assertTrue(doc.text().contains("O'Brien"), doc.text());
      assertTrue(doc.text().contains("<none>"), doc.text());
      assertTrue(doc.select("input").stream().anyMatch(e -> e.attr("value").contains("O'Brien")));
   }

   // ---- coordinate helper ----

   @Test
   void textAssemblyKeepsTextAndNeverBecomesMarkup() {
      assertEquals("a<b", textOf("a<b"));
      assertEquals("x<y and y>z", textOf("x<y and y>z"));
      assertEquals("<none>", textOf("<none>"));
      assertEquals("Smith & Sons <NY>", textOf("Smith & Sons <NY>"));
      assertEquals("Smith & Sons", textOf("Smith & Sons"));
      assertEquals("A&B Corp", textOf("A&B Corp"));
      assertEquals("l1 l2", textOf("l1\nl2"));
      Document doc = writeText("<b>bold</b><br>x");
      assertEquals(0, doc.select("b").size());
      assertEquals("<b>bold</b><br>x", doc.body().text());
   }

   @Test
   void labelTextIsAlwaysEscaped() {
      assertEquals("<b>bold</b>", textOf("<b>bold</b>"));
      assertEquals("a<b", textOf("a<b"));
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
      // a <word> value is data, not markup
      assertEquals("&lt;none&gt;", HTMLEscape.text("<none>"));
      assertEquals("&lt;b&gt;x&lt;/b&gt;", HTMLEscape.text("<b>x</b>"));
      assertEquals("Smith &amp; Sons &lt;NY&gt;", HTMLEscape.text("Smith & Sons <NY>"));
      assertEquals("1234", HTMLEscape.text(1234));
   }

   // ---- helpers ----

   private Document writeSelectionList(String title) {
      SelectionListVSAssembly sel = new SelectionListVSAssembly(vs, "SLT");
      vs.addAssembly(sel);
      SelectionList list = new SelectionList();
      list.addSelectionValue(selValue("v"));
      sel.setSelectionList(list);
      ((SelectionListVSAssemblyInfo) sel.getVSAssemblyInfo()).setTitleValue(title);
      StringWriter sw = new StringWriter();
      new HTMLSelectionListHelper(coord, vs, sel).write(new PrintWriter(sw), sel);
      return Jsoup.parse(sw.toString());
   }

   private Document writeSelectionListLabel(String label) {
      SelectionListVSAssembly sel = new SelectionListVSAssembly(vs, "SLL" + (++seq));
      vs.addAssembly(sel);
      SelectionList list = new SelectionList();
      list.addSelectionValue(selValue(label));
      sel.setSelectionList(list);
      StringWriter sw = new StringWriter();
      new HTMLSelectionListHelper(coord, vs, sel).write(new PrintWriter(sw), sel);
      return Jsoup.parse(sw.toString());
   }

   private Document writeSelectionTreeLabel(String label) {
      SelectionTreeVSAssembly sel = new SelectionTreeVSAssembly(vs, "STL" + (++seq));
      vs.addAssembly(sel);
      CompositeSelectionValue root = new CompositeSelectionValue("root", "root");
      root.getSelectionList().addSelectionValue(compValue(label));
      sel.setCompositeSelectionValue(root);
      StringWriter sw = new StringWriter();
      new HTMLSelectionTreeHelper(coord, vs, sel).write(new PrintWriter(sw), sel);
      return Jsoup.parse(sw.toString());
   }

   private Document writeSelectionTree(String title) {
      SelectionTreeVSAssembly sel = new SelectionTreeVSAssembly(vs, "STT");
      vs.addAssembly(sel);
      CompositeSelectionValue root = new CompositeSelectionValue("root", "root");
      root.getSelectionList().addSelectionValue(compValue("v"));
      sel.setCompositeSelectionValue(root);
      ((SelectionTreeVSAssemblyInfo) sel.getVSAssemblyInfo()).setTitleValue(title);
      StringWriter sw = new StringWriter();
      new HTMLSelectionTreeHelper(coord, vs, sel).write(new PrintWriter(sw), sel);
      return Jsoup.parse(sw.toString());
   }

   private Document writeRadio(String title) {
      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RB");
      vs.addAssembly(radio);
      RadioButtonVSAssemblyInfo info = (RadioButtonVSAssemblyInfo) radio.getVSAssemblyInfo();
      info.setValues(new Object[] { "v1" });
      info.setLabels(new String[] { "l1" });
      info.setTitleValue(title);
      info.setTitleVisibleValue(true);
      StringWriter sw = new StringWriter();
      coord.writeList(sw, info, true, new Rectangle2D.Double(0, 0, 200, 100));
      return Jsoup.parse(sw.toString());
   }

   private Document writeTable(String title, String... values) {
      TableVSAssembly table = new TableVSAssembly(vs, "T1");
      vs.addAssembly(table);

      if(title != null) {
         TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
         info.setTitleValue(title);
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

   private Document writeText(String txt) {
      StringWriter sw = new StringWriter();
      coord.writeText(sw, new Rectangle2D.Double(0, 0, 100, 20), new VSCompositeFormat(), txt,
                      null, false, null, false, 1);
      return Jsoup.parse(sw.toString());
   }

   private String textOf(String txt) {
      return writeText(txt).body().text();
   }
}
