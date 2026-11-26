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

package inetsoft.web.binding.handler;

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLayout;
import inetsoft.report.filter.*;
import inetsoft.report.internal.table.*;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81700: row copy/paste in the freehand table layout (delete/insert/append row)
 * dropped the column-0 hyperlink and highlight of every row below the operation, because
 * CellInfoHandler.set() wrote the (null) row-level hyperlink/highlight onto the cell path.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CellInfoHandlerRowLinkTest {
   // rows: 0 header, 1 detail, 2 detail, 3 trailer, 4 trailer
   private static CalcTableVSAssemblyInfo build() {
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      vs.addAssembly(calc);
      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) calc.getInfo();
      info.setHeaderRowCount(1);
      info.setTrailerRowCount(2);
      TableLayout layout = calc.getTableLayout();
      layout.setColCount(3);

      while(layout.getRowCount() < 5) {
         layout.insertRow(1);
      }

      info.setHyperlinkAttr(new TableHyperlinkAttr());
      info.setHighlightAttr(new TableHighlightAttr());

      for(int i = 0; i < 5; i++) {
         mark(info, info.getCellDataPath(i, 0), "r" + i);
         mark(info, info.getCellDataPath(i, 1), "c" + i);
      }

      return info;
   }

   private static void mark(CalcTableVSAssemblyInfo info, TableDataPath p, String tag) {
      info.getHyperlinkAttr().setHyperlink(p, new Hyperlink("http://x/" + tag));
      info.getHighlightAttr().setHighlight(p, highlight(tag));
      VSCompositeFormat f = new VSCompositeFormat();
      VSFormat u = new VSFormat();
      u.setBackground(Color.RED);
      f.setUserDefinedFormat(u);
      info.getFormatInfo().setFormat(p, f);
   }

   private static HighlightGroup highlight(String tag) {
      Condition cond = new Condition();
      cond.addValue("b");
      cond.setOperation(XCondition.EQUAL_TO);
      ConditionList cl = new ConditionList();
      cl.append(new ConditionItem(new ColumnRef(new AttributeRef("col1")), cond, 0));
      ColumnHighlight ch = new ColumnHighlight();
      ch.setConditionGroup(cl);
      ch.setBackground(Color.BLUE);
      HighlightGroup hg = new HighlightGroup();
      hg.addHighlight(tag, ch);
      return hg;
   }

   private static void op(CalcTableVSAssemblyInfo info, String op, int row) {
      new TableLayoutHandler().doOperation(info, op, new Rectangle(0, row, 1, 1), 1);
   }

   private static String link(CalcTableVSAssemblyInfo info, int r, int c) {
      Hyperlink h = info.getHyperlinkAttr().getHyperlink(info.getCellDataPath(r, c));
      return h == null ? null : h.getLink();
   }

   private static String hili(CalcTableVSAssemblyInfo info, int r, int c) {
      HighlightGroup g = info.getHighlightAttr().getHighlight(info.getCellDataPath(r, c));
      return g == null || g.getNames().length == 0 ? null : g.getNames()[0];
   }

   private static void assertCell(CalcTableVSAssemblyInfo info, int r, int c, String n) {
      String tag = (c == 0 ? "r" : "c") + n;
      assertEquals("http://x/" + tag, link(info, r, c), "link [" + r + "," + c + "]");
      assertEquals(tag, hili(info, r, c), "highlight [" + r + "," + c + "]");
      assertNotNull(info.getFormatInfo().getFormat(info.getCellDataPath(r, c)),
                    "format [" + r + "," + c + "]");
   }

   @Test
   void deleteFirstTrailerRowKeepsColumnZeroMarks() {
      CalcTableVSAssemblyInfo info = build();
      op(info, "deleteRow", 3);

      assertEquals(4, info.getTableLayout().getRowCount());
      assertCell(info, 0, 0, "0");
      assertCell(info, 2, 0, "2");
      assertCell(info, 3, 0, "4");
      assertCell(info, 3, 1, "4");
   }

   @Test
   void deleteDetailRowShiftsColumnZeroAndOneMarks() {
      CalcTableVSAssemblyInfo info = build();
      op(info, "deleteRow", 1);

      assertEquals(4, info.getTableLayout().getRowCount());
      assertCell(info, 0, 0, "0");
      assertCell(info, 1, 0, "2");
      assertCell(info, 2, 0, "3");
      assertCell(info, 3, 0, "4");
      assertCell(info, 1, 1, "2");
      assertCell(info, 2, 1, "3");
      assertCell(info, 3, 1, "4");
   }

   @Test
   void insertRowShiftsColumnZeroMarks() {
      CalcTableVSAssemblyInfo info = build();
      op(info, "insertRow", 3);

      assertEquals(6, info.getTableLayout().getRowCount());
      assertCell(info, 0, 0, "0");
      assertCell(info, 2, 0, "2");
      assertCell(info, 4, 0, "3");
      assertCell(info, 5, 0, "4");
      assertCell(info, 4, 1, "3");
      assertCell(info, 5, 1, "4");
   }

   @Test
   void appendRowKeepsColumnZeroMarksOnShiftedRows() {
      CalcTableVSAssemblyInfo info = build();
      op(info, "appendRow", 3);

      assertEquals(6, info.getTableLayout().getRowCount());
      assertCell(info, 0, 0, "0");
      assertCell(info, 3, 0, "3");
      assertCell(info, 5, 0, "4");
      assertCell(info, 5, 1, "4");
   }

   @Test
   void rowLevelMarksMoveWithTheirRow() {
      CalcTableVSAssemblyInfo info = build();
      TableLayout layout = info.getTableLayout();
      TableDataPath row4 = layout.getRowDataPath(4);
      info.getHyperlinkAttr().setHyperlink(row4, new Hyperlink("http://x/row4"));
      info.getHighlightAttr().setHighlight(row4, highlight("row4"));

      op(info, "deleteRow", 3);

      TableDataPath row3 = layout.getRowDataPath(3);
      assertEquals("http://x/row4", info.getHyperlinkAttr().getHyperlink(row3).getLink());
      assertNotNull(info.getHighlightAttr().getHighlight(row3));
   }

   @Test
   void deletedRowLevelMarksDoNotBleedOntoShiftedRow() {
      CalcTableVSAssemblyInfo info = build();
      TableLayout layout = info.getTableLayout();
      TableDataPath row3 = layout.getRowDataPath(3);
      info.getHyperlinkAttr().setHyperlink(row3, new Hyperlink("http://x/row3"));
      info.getHighlightAttr().setHighlight(row3, highlight("row3"));

      op(info, "deleteRow", 3);

      TableDataPath after = layout.getRowDataPath(3);
      assertNull(info.getHyperlinkAttr().getHyperlink(after));
      assertNull(info.getHighlightAttr().getHighlight(after));
   }
}
