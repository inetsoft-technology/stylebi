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
package inetsoft.report;

import inetsoft.test.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.VSLayoutTool;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import java.awt.*;
import java.io.*;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77792, #77794: the file-supplied sizes of a TableLayout (columns, region rows, the
 * hregion count, their product and span extents) must be bounded on parse, so a corrupt or
 * forged layout fails with an ordinary Exception (or has its spans clamped) instead of an
 * OutOfMemoryError or a parse that never finishes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableLayoutParseTest {
   // ---- columns ------------------------------------------------------------------------

   @Test
   void rejectsHugeColumns() throws Exception {
      Element elem = defaultLayoutElement();
      elem.setAttribute("columns", "2147483647");
      assertParseFails(elem, "invalid layout columns");
   }

   @Test
   void rejectsColumnsOverCap() throws Exception {
      Element elem = defaultLayoutElement();
      elem.setAttribute("columns", "10001");
      assertParseFails(elem, "invalid layout columns");
   }

   @Test
   void rejectsNegativeColumns() throws Exception {
      Element elem = defaultLayoutElement();
      elem.setAttribute("columns", "-1");
      assertParseFails(elem, "invalid layout columns");
   }

   // ---- region rows --------------------------------------------------------------------

   @Test
   void rejectsHugeRowsWithRealRowHeights() throws Exception {
      Element elem = parse(layoutXml(3, 1, 2, false));
      firstRegion(elem).setAttribute("rows", "2147483647");
      assertParseFails(elem, "invalid region rows");
   }

   @Test
   void rejectsRowsWithoutRowHeights() throws Exception {
      // the #77794 repro: rows="100000" and no <rowHeight> children
      Element elem = parse(layoutXml(3, 1, 0, false));
      firstRegion(elem).setAttribute("rows", "100000");
      assertParseFails(elem, "invalid region rows");
   }

   @Test
   void rejectsNegativeRows() throws Exception {
      Element elem = parse(layoutXml(3, 1, 2, false));
      firstRegion(elem).setAttribute("rows", "-5");
      assertParseFails(elem, "invalid region rows");
   }

   // ---- cells and region count ---------------------------------------------------------

   @Test
   void rejectsCellsOverCapInOneRegion() throws Exception {
      // 101 count-valid rows x 1000 columns = 101000 cells
      assertParseFails(parse(layoutXml(1000, 1, 101, false)), "invalid region rows");
   }

   @Test
   void rejectsCellsOverCapAcrossRegions() throws Exception {
      // 2 regions x 60 count-valid rows x 1000 columns = 120000 cells
      assertParseFails(parse(layoutXml(1000, 2, 60, false)), "too many layout cells");
   }

   @Test
   void rejectsCellsOverCapCountingDroppedDuplicates() throws Exception {
      // the second region has the same path and is dropped, but it was still parsed
      assertParseFails(parse(layoutXml(1000, 2, 60, true)), "too many layout cells");
   }

   @Test
   void rejectsTooManyRegions() throws Exception {
      assertParseFails(parse(layoutXml(1, 1001, 1, false)), "too many layout regions");
   }

   @Test
   void rejectsVRegionRowsWithoutChildren() throws Exception {
      Element elem = parse(layoutXml(2, 1, 1, false, 1));
      Element vregions = Tool.getChildNodeByTagName(elem, "vregions");
      Element layoutRegion = Tool.getChildNodeByTagName(vregions, "layoutRegion");
      Tool.getChildNodeByTagName(layoutRegion, "region").setAttribute("rows", "5");
      assertParseFails(elem, "invalid region rows");
   }

   // ---- boundary shapes that must parse --------------------------------------------------

   @Test
   void parsesNarrowTallLayoutAtCap() throws Exception {
      // 1000 regions x 100 rows x 1 column = 100000 cells: per-region addRegion made this
      // re-allocate the whole spans matrix 1000 times
      TableLayout layout = assertParses(parse(layoutXml(1, 1000, 100, false)));
      assertEquals(1, layout.getColCount());
      assertEquals(100_000, layout.getRowCount());
      assertEquals(1000, layout.getRegionCount());
   }

   @Test
   void parsesWideLayoutWithManyRegionsAtCap() throws Exception {
      TableLayout layout = assertParses(parse(layoutXml(100, 1000, 1, false)));
      assertEquals(100, layout.getColCount());
      assertEquals(1000, layout.getRowCount());
      assertEquals(1000, layout.getRegionCount());
   }

   @Test
   void parsesMaxColumns() throws Exception {
      TableLayout layout = assertParses(parse(layoutXml(10_000, 1, 10, false)));
      assertEquals(10_000, layout.getColCount());
      assertEquals(10, layout.getRowCount());
      assertEquals(1, layout.getRegionCount());
   }

   // ---- spans ----------------------------------------------------------------------------

   @Test
   void clampsSpanPastTheGridAndBuildTreeFinishes() throws Exception {
      TableLayout layout = VSLayoutTool.createDefaultLayout();
      int rows = layout.getRowCount();
      int cols = layout.getColCount();
      Element elem = parse(withSpans(toXml(layout),
         "<span r=\"0\" c=\"0\" w=\"30000\" h=\"30000\"/>"));
      TableLayout parsed = assertParses(elem);

      assertEquals(new Dimension(cols, rows), parsed.getSpan(0, 0));
      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> LayoutTool.buildTree(parsed));
   }

   @Test
   void dropsSpanClampedToOneCell() throws Exception {
      TableLayout layout = VSLayoutTool.createDefaultLayout();
      int rows = layout.getRowCount();
      int cols = layout.getColCount();
      Element elem = parse(withSpans(toXml(layout),
         "<span r=\"" + (rows - 1) + "\" c=\"" + (cols - 1) + "\" w=\"30000\" h=\"30000\"/>"));
      TableLayout parsed = assertParses(elem);

      // a 1x1 span is meaningless, as in MatrixOperation.setSpan
      assertNull(parsed.getSpan(rows - 1, cols - 1));
      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> LayoutTool.buildTree(parsed));
   }

   @Test
   void clampsSpanHeightAtLastRowWithoutOverflow() throws Exception {
      Element elem = parse(withSpans(layoutXml(3, 1, 4, false),
         "<span r=\"3\" c=\"0\" w=\"2\" h=\"2147483647\"/>"));
      TableLayout parsed = assertParses(elem);
      assertEquals(new Dimension(2, 1), parsed.getSpan(3, 0));
   }

   @Test
   void dropsOverlappingSpansBeyondTheAreaBudget() throws Exception {
      // a 10x10 grid: the first span covers it all, the rest would push the area past it
      StringBuilder spans = new StringBuilder();

      for(int r = 0; r < 10; r++) {
         spans.append("<span r=\"").append(r).append("\" c=\"0\" w=\"2000000000\" ")
            .append("h=\"2000000000\"/>");
      }

      TableLayout parsed = assertParses(parse(withSpans(layoutXml(10, 1, 10, false),
                                                         spans.toString())));
      assertEquals(new Dimension(10, 10), parsed.getSpan(0, 0));

      for(int r = 1; r < 10; r++) {
         assertNull(parsed.getSpan(r, 0), "row " + r);
      }

      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> LayoutTool.buildTree(parsed));
   }

   @Test
   void skipsSpanOutsideTheGrid() throws Exception {
      Element elem = parse(withSpans(layoutXml(3, 1, 4, false),
         "<span r=\"4\" c=\"0\" w=\"2\" h=\"2\"/>" +
         "<span r=\"0\" c=\"3\" w=\"2\" h=\"2\"/>" +
         "<span r=\"-1\" c=\"0\" w=\"2\" h=\"2\"/>" +
         "<span r=\"1\" c=\"1\" w=\"2\" h=\"2\"/>"));
      TableLayout parsed = assertParses(elem);

      assertEquals(new Dimension(2, 2), parsed.getSpan(1, 1));
      assertEquals(1, countSpans(parsed));
   }

   // ---- controls -------------------------------------------------------------------------

   @Test
   void defaultLayoutRoundTrips() throws Exception {
      TableLayout layout = VSLayoutTool.createDefaultLayout();
      String xml = toXml(layout);
      TableLayout parsed = assertParses(parse(xml));

      assertEquals(xml, toXml(parsed));
      assertEquals(layout.getColCount(), parsed.getColCount());
      assertEquals(layout.getRowCount(), parsed.getRowCount());
      assertEquals(layout.getRegionCount(), parsed.getRegionCount());
   }

   @Test
   void editedMultiRegionLayoutRoundTrips() throws Exception {
      TableLayout layout = VSLayoutTool.createDefaultLayout();
      layout.setColCount(4);

      for(int i = 0; i < 5; i++) {
         BaseLayout.Region region = layout.new Region();
         region.setRowCount(1 + i % 3);
         layout.addRegion(new TableDataPath(i, TableDataPath.GROUP_HEADER, XSchema.STRING,
                                            new String[] { "g" + i }, true, false), region);
         region.setRowHeight(0, 20 + i);
         region.setRowBinding(0, i);
         region.setCellBinding(0, i % 4, new TableCellBinding(CellBinding.BIND_TEXT, "t" + i));

         if(i == 2) {
            region.setVisible(false);
         }
      }

      BaseLayout.Region tail = layout.getRegion(layout.getRegionCount() - 1);
      tail.insertRow(0);
      tail.setRowHeight(0, 33);

      layout.setColWidth(1, 80);
      layout.setColWidth(3, 120);
      layout.setSpan(0, 0, new Dimension(2, 2));
      layout.setSpan(3, 2, new Dimension(2, 3));

      for(int c = 0; c < layout.getColCount(); c++) {
         layout.addVRegion(new TableDataPath(-1, TableDataPath.HEADER, XSchema.STRING,
                                             new String[] { "c" + c }, false, true),
                           layout.new VRegion());
      }

      String xml = toXml(layout);
      TableLayout parsed = assertParses(parse(xml));

      assertEquals(xml, toXml(parsed));
      assertEquals(layout, parsed);
      assertEquals(layout.getRowCount(), parsed.getRowCount());
      assertEquals(layout.getRegionCount(), parsed.getRegionCount());
      assertEquals(new Dimension(2, 2), parsed.getSpan(0, 0));
      assertEquals(new Dimension(2, 3), parsed.getSpan(3, 2));
      int tailRow = convertRow(parsed, parsed.getRegionCount() - 1, 1);
      assertEquals("t4", parsed.getCellBinding(tailRow, 0).getValue());
      assertEquals(4, parsed.getVRegionCount());

      for(int i = 0; i < parsed.getVRegionCount(); i++) {
         assertEquals(0, parsed.getVRegion(i).getRowCount());
      }

      Element vregions = Tool.getChildNodeByTagName(parse(toXml(parsed)), "vregions");
      assertEquals(0, vregions.getElementsByTagName("rowHeight").getLength());
   }

   @Test
   void missingOrNonNumericRowsStaysLenient() throws Exception {
      Element missing = parse(layoutXml(3, 1, 0, false));
      firstRegion(missing).removeAttribute("rows");
      assertEquals(0, assertParses(missing).getRowCount());

      Element nonNumeric = parse(layoutXml(3, 1, 0, false));
      firstRegion(nonNumeric).setAttribute("rows", "abc");
      TableLayout parsed = assertParses(nonNumeric);
      assertEquals(0, parsed.getRowCount());
      assertEquals(1, parsed.getRegionCount());
   }

   @Test
   void viewsheetParseFailsWithExceptionOnForgedColumns() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new CalcTableVSAssembly(vs, "Calc1"));
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      vs.writeXML(writer);
      writer.flush();
      String xml = buf.toString();

      // control: the unmodified viewsheet parses
      Viewsheet control = new Viewsheet();
      control.parseXML(parse(xml));
      CalcTableVSAssembly calc = (CalcTableVSAssembly) control.getAssembly("Calc1");
      assertEquals(VSLayoutTool.createDefaultLayout().getColCount(),
                   ((CalcTableVSAssemblyInfo) calc.getInfo()).getTableLayout().getColCount());

      Element elem = parse(xml);
      NodeList layouts = elem.getElementsByTagName("tableLayout");
      assertTrue(layouts.getLength() > 0);

      for(int i = 0; i < layouts.getLength(); i++) {
         ((Element) layouts.item(i)).setAttribute("columns", "2147483647");
      }

      assertFails(() -> new Viewsheet().parseXML(elem), "invalid layout columns");
   }

   // ---- helpers --------------------------------------------------------------------------

   private static int convertRow(TableLayout layout, int region, int row) {
      int r = 0;

      for(int i = 0; i < region; i++) {
         r += layout.getRegion(i).getRowCount();
      }

      return r + row;
   }

   private static int countSpans(TableLayout layout) {
      int n = 0;

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            if(layout.getSpan(r, c) != null) {
               n++;
            }
         }
      }

      return n;
   }

   private static Element defaultLayoutElement() throws Exception {
      return parse(toXml(VSLayoutTool.createDefaultLayout()));
   }

   private static Element firstRegion(Element layout) {
      Element hregions = Tool.getChildNodeByTagName(layout, "hregions");
      Element layoutRegion = Tool.getChildNodeByTagName(hregions, "layoutRegion");
      return Tool.getChildNodeByTagName(layoutRegion, "region");
   }

   private static String toXml(XMLSerializable obj) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      obj.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static Element parse(String xml) throws Exception {
      return Tool.parseXML(new StringReader(xml)).getDocumentElement();
   }

   private static String withSpans(String xml, String spans) {
      return xml.replace("<spans>", "<spans>" + spans);
   }

   private static String layoutXml(int columns, int regions, int rowsEach, boolean samePath) {
      return layoutXml(columns, regions, rowsEach, samePath, 0);
   }

   /**
    * Generate a layout in the format TableLayout.writeXML writes: each hregion has one
    * <rowHeight>/<rowBinding> pair per row, and paths come from TableDataPath.writeXML.
    * Built as a string because building a big layout through the API is itself slow.
    */
   private static String layoutXml(int columns, int regions, int rowsEach, boolean samePath,
                                   int vregions)
   {
      StringBuilder buf = new StringBuilder();
      buf.append("<tableLayout columns=\"").append(columns).append("\" mode=\"")
         .append(TableLayout.CALC).append("\">\n<hregions>\n");
      StringBuilder rows = new StringBuilder();

      for(int r = 0; r < rowsEach; r++) {
         rows.append("<rowHeight row=\"").append(r).append("\" height=\"-1\"/>\n");
         rows.append("<rowBinding row=\"").append(r).append("\" binding=\"-1\"/>\n");
      }

      for(int i = 0; i < regions; i++) {
         int level = samePath ? 0 : i;
         TableDataPath path = new TableDataPath(level, TableDataPath.GROUP_HEADER,
                                                XSchema.STRING, new String[] { "g" + level },
                                                true, false);
         buf.append("<layoutRegion>\n").append(toXml(path))
            .append("<region rows=\"").append(rowsEach)
            .append("\" visible=\"true\" virtual=\"false\">\n").append(rows)
            .append("</region>\n</layoutRegion>\n");
      }

      buf.append("</hregions>\n<vregions>\n");

      for(int i = 0; i < vregions; i++) {
         TableDataPath path = new TableDataPath(-1, TableDataPath.HEADER, XSchema.STRING,
                                                new String[] { "c" + i }, false, true);
         buf.append("<layoutRegion>\n").append(toXml(path))
            .append("<region rows=\"0\" visible=\"true\" virtual=\"false\">\n</region>\n")
            .append("</layoutRegion>\n");
      }

      buf.append("</vregions>\n<cwidths>\n</cwidths>\n<spans>\n</spans>\n</tableLayout>\n");
      return buf.toString();
   }

   private static TableLayout assertParses(Element elem) {
      TableLayout layout = new TableLayout();
      assertTimeoutPreemptively(Duration.ofSeconds(10), () -> layout.parseXML(elem));
      return layout;
   }

   private static void assertParseFails(Element elem, String message) {
      assertFails(() -> new TableLayout().parseXML(elem), message);
   }

   // catches Throwable itself: JUnit's assertThrows rethrows an OutOfMemoryError, which would
   // abort the test JVM instead of failing the test. The timeout turns a regression to the
   // quadratic parse into a failure instead of a stalled build.
   private static void assertFails(Executable parse, String message) {
      Throwable thrown = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
         try {
            parse.execute();
         }
         catch(Throwable ex) {
            return ex;
         }

         return null;
      });

      assertTrue(thrown instanceof Exception, "expected an Exception, got " + thrown);
      assertTrue(thrown.getMessage() != null && thrown.getMessage().contains(message),
                 thrown.toString());
   }
}
