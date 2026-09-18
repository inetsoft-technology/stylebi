/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.script;

import inetsoft.report.TableLens;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TableRowTest {
   private TableRow tableRow;

   /**
    * test tablerow with get methord.   such as setBackground and getBackground
    */
   @Test
   void  testWithBackground() {
      tableRow = new TableRow(defaultTableLens, 1, "Background", Color.class);

      assertEquals(1, tableRow.getRow());
      assertTrue(tableRow.hasMember("length"));
      assertEquals(3, tableRow.getMember("length"));

      assertArrayEquals(new Object[]{ "name", "id", "date", "length" }, tableRow.getMemberKeys());
      assertEquals("[index]", tableRow.getDisplaySuffix());
      assertEquals("[]", tableRow.getSuffix());
   }

   /**
    * test tableRow with is property method, such as setLineWrap and isLineWrap
    */
   @Test
   void testWithOthers() {
      tableRow = new TableRow(defaultTableLens, 1, "LineWrap", boolean.class);

      assertTrue(tableRow.hasMember("name"));
      assertFalse(tableRow.hasMember("test"));

      // assigning an unknown column name now stores it as a local member
      tableRow.putMember("name2", "test");
      assertEquals("test", tableRow.getMember("name2"));

      TableRow row1 = (TableRow) tableRow.getArrayElement(-1);
      assertArrayEquals(new Object[]{ "name", "id", "date", "length" }, row1.getMemberKeys());

      TableRow.TableCol tableCol = new TableRow.TableCol();
      assertEquals("null[0]", tableCol.toString());
   }

   /**
    * Bug #76665: an id that resolves via neither the bound-source column map
    * (header/identifier) nor any ancestor TableFilter must fail loud instead
    * of silently returning null -- a caller such as Sum(field["typo"]) can
    * turn that silent null into a silent 0 with "fill blank with zero" on.
    */
   @Test
   void testGetMemberThrowsForUnresolvableId() {
      tableRow = new TableRow(defaultTableLens, 1, "LineWrap", boolean.class);

      assertThrows(ScriptException.class, () -> tableRow.getMember("nonexistent"));
   }

   /**
    * Bug #76665: the throw above must not break an id that was explicitly
    * assigned as a local member (e.g. field.name2 = "test" in script) -- an
    * unrelated, legitimate use of this same "not a table column" fallthrough.
    */
   @Test
   void testGetMemberStillReturnsExplicitlyAssignedMember() {
      tableRow = new TableRow(defaultTableLens, 1, "LineWrap", boolean.class);

      tableRow.putMember("name2", "test");
      assertEquals("test", tableRow.getMember("name2"));
   }

   /**
    * Regression guard for the GraalJS migration (#75423): GraalJS dispatches a member
    * read only when hasMember reports the member present, unlike Rhino, whose get()
    * was always invoked. getMember resolves a column that lives in a base table of the
    * filter chain through findColumn(), so hasMember has to consult the same path --
    * otherwise field['Col'] reads as undefined and field['Col'].substring(...) throws
    * "Cannot read property 'substring' of undefined".
    */
   @Test
   void testColumnOnlyInBaseTable() {
      tableRow = new TableRow(filtered(), 1);

      assertTrue(tableRow.hasMember("id"),
                 "a column reachable only through the base table must be reported present");
      assertEquals(1, tableRow.getMember("id"));
   }

   /**
    * getColMap() is intentionally left empty for row 0, and row 0 is the default row
    * for an assembly-level script, so findColumn() is the only path that can resolve a
    * header-named column there.
    */
   @Test
   void testColumnAtRowZero() {
      tableRow = new TableRow(filtered(), 0);

      assertTrue(tableRow.hasMember("id"), "row 0 must still resolve a base-table column");
      assertEquals("id", tableRow.getMember("id"));
   }

   /**
    * A miss must stay a miss (and be cached) rather than re-walking the base tables on
    * every hasMember probe -- GraalJS probes keys that are never columns on each read.
    */
   @Test
   void testUnknownColumnInFilteredTable() {
      tableRow = new TableRow(filtered(), 1);

      assertTrue(tableRow.hasMember("length"));
      assertFalse(tableRow.hasMember("noSuchColumn"));
      assertFalse(tableRow.hasMember("noSuchColumn"));
      assertNull(tableRow.getMember("noSuchColumn"));
   }

   /**
    * A TableRow is reused across rows through setRow(), and a TableCol resolved from a
    * base table is row-dependent (findColumn() derives it via getBaseRowIndex()), so it
    * must not be cached across rows. Caching it made every row return the value the
    * column had on the row that was probed first -- silently wrong for the whole table
    * under any row-permuting filter.
    */
   @Test
   void testBaseTableColumnFollowsSetRow() {
      // sort ascending by name, then hide every column but name: "id" is reachable
      // only through the base table, and the sort permutes the rows.
      DefaultTableLens base = new DefaultTableLens(new Object[][]{
         { "name", "id" },
         { "a", 10 },
         { "c", 20 },
         { "b", 30 }
      });
      base.moreRows(TableLens.EOT);
      SortFilter sorted = new SortFilter(base, new int[]{ 0 });
      sorted.moreRows(TableLens.EOT);
      tableRow = new TableRow(new ColumnMapFilter(sorted, new int[]{ 0 }), 1);

      // sorted order is a(10), b(30), c(20)
      assertEquals(10, tableRow.getMember("id"));

      tableRow.setRow(2);
      assertEquals(30, tableRow.getMember("id"), "row 2 must not reuse row 1's TableCol");

      tableRow.setRow(3);
      assertEquals(20, tableRow.getMember("id"), "row 3 must not reuse an earlier TableCol");
   }

   /**
    * getArrayElement() shares colmap0 with the previous-row object but gives it a
    * fresh colcache, so a TableCol cached by this row is visible to it. If the
    * TableCol carried a resolved row index, field[-1]['col'] would read *this*
    * row's cell -- field['id'] - field[-1]['id'] would be 0 on every row.
    */
   @Test
   void testPreviousRowResolvesItsOwnBaseTableCell() {
      DefaultTableLens base = new DefaultTableLens(new Object[][]{
         { "name", "id" },
         { "a", 10 },
         { "c", 20 },
         { "b", 30 }
      });
      base.moreRows(TableLens.EOT);
      SortFilter sorted = new SortFilter(base, new int[]{ 0 });
      sorted.moreRows(TableLens.EOT);
      // sorted order is a(10), b(30), c(20)
      tableRow = new TableRow(new ColumnMapFilter(sorted, new int[]{ 0 }), 2);

      // resolve and cache the base-table column on this row first
      assertEquals(30, tableRow.getMember("id"));

      TableRow prev = (TableRow) tableRow.getArrayElement(-1);
      assertEquals(10, prev.getMember("id"),
                   "field[-1] must read the previous row, not reuse this row's TableCol");
      assertEquals(30, tableRow.getMember("id"), "the current row must still be correct");
   }

   /** A filter with no base-table counterpart for row 2, like SummaryFilter's data rows. */
   public static class UnmappedRowFilter extends ColumnMapFilter {
      public UnmappedRowFilter(TableLens table, int[] map) {
         super(table, map);
      }

      @Override
      public int getBaseRowIndex(int row) {
         return row == 2 ? -1 : row;
      }
   }

   /**
    * A row with no counterpart in the base table is a *row-dependent* miss and must
    * not be recorded in the permanent notfound set: a TableRow is reused across rows
    * and hasMember() consults that set, so one such row would make the column read
    * undefined on every later row too.
    */
   @Test
   void testUnmappedRowDoesNotPoisonLaterRows() {
      DefaultTableLens base = new DefaultTableLens(new Object[][]{
         { "name", "id" },
         { "a", 10 },
         { "b", 20 },
         { "c", 30 }
      });
      base.moreRows(TableLens.EOT);
      tableRow = new TableRow(new UnmappedRowFilter(base, new int[]{ 0 }), 1);

      assertEquals(10, tableRow.getMember("id"));

      // row 2 maps to -1: reads as undefined, but must not be remembered as absent
      tableRow.setRow(2);
      assertNull(tableRow.getMember("id"));

      tableRow.setRow(3);
      assertTrue(tableRow.hasMember("id"), "an unmapped row must not mark the column absent");
      assertEquals(30, tableRow.getMember("id"), "later rows must still resolve");
   }

   /**
    * A filter exposing only the first column; "id"/"date" exist solely in the base table.
    */
   private TableLens filtered() {
      DefaultTableLens base = new DefaultTableLens(objData);
      base.moreRows(TableLens.EOT);
      return new ColumnMapFilter(base, new int[]{ 0 });
   }

   Object[][] objData = new Object[][]{
      {"name", "id", "date"},
      {"a", 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, new Date(2023 - 1900, 5, 15)},
      {"a", 3, new Date(2025 - 1900, 11, 31)},
      {"b", 2, new Date(2026 - 1900, 9, 20)}
   };

   private DefaultTableLens defaultTableLens = new DefaultTableLens(objData);
}
