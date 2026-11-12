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
package inetsoft.report.lens;

import inetsoft.report.TableDataDescriptor;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.PluginsTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.test.XTableUtil;
import inetsoft.uql.XTable;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.ScriptEnv;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * Value-level tests of the rotate lens ({@code RotatedTableLens}) and of the formula lens
 * ({@code FormulaTableLens}), which computes the script expression columns of a worksheet.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RotateFormulaLensesTest {
   /**
    * The environment of worksheet formulas; it does not swallow script errors.
    */
   private final ScriptEnv wsEnv = new GraalJavaScriptEnv();

   /**
    * A plain lens that is not an attribute lens, so an attribute lens wrapped around it keeps
    * every setting itself.
    */
   private static final class PlainTable extends AbstractTableLens {
      PlainTable(Object[][] data) {
         this.data = data;
      }

      @Override
      public int getRowCount() {
         return data.length;
      }

      @Override
      public int getColCount() {
         return data[0].length;
      }

      @Override
      public Object getObject(int r, int c) {
         return data[r][c];
      }

      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      private final Object[][] data;
   }

   /**
    * A table that reports the crosstab type in its descriptor.
    */
   private static final class CrosstabBase extends DefaultTableLens {
      CrosstabBase(Object[][] data) {
         super(data);
      }

      @Override
      public TableDataDescriptor getDescriptor() {
         return new DefaultTableDataDescriptor(this) {
            @Override
            public int getType() {
               return CROSSTAB_TABLE;
            }
         };
      }
   }

   private static PlainTable plain() {
      return new PlainTable(new Object[][] {
         {"Name", "Amount"},
         {"A", 1234.5},
         {"B", 2.0},
         {"C", 7},
      });
   }

   @Test
   void rotateSwapsRowsAndColumns() {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         {"Item", "Q1", "Q2"},
         {"A", 1, 2},
         {"B", 3, 4},
         {"C", 5, 6},
      });
      base.setHeaderRowCount(1);
      base.setHeaderColCount(2);
      base.setTrailerRowCount(1);
      base.setTrailerColCount(0);
      base.setSpan(1, 0, new Dimension(2, 3));
      RotatedTableLens lens = new RotatedTableLens(base);

      assertEquals(3, lens.getRowCount());
      assertEquals(4, lens.getColCount());
      Object[][] expected = {
         {"Item", "A", "B", "C"},
         {"Q1", 1, 3, 5},
         {"Q2", 2, 4, 6},
      };
      XTableUtil.assertEquals(lens, expected);

      // the header and trailer counts swap with the rows and columns
      assertEquals(2, lens.getHeaderRowCount());
      assertEquals(1, lens.getHeaderColCount());
      assertEquals(0, lens.getTrailerRowCount());
      assertEquals(1, lens.getTrailerColCount());
      // a span is transposed too
      assertEquals(new Dimension(3, 2), lens.getSpan(0, 1));
      assertNull(lens.getSpan(1, 0));
      // writes go to the transposed cell of the base table
      lens.setObject(2, 3, 60);
      assertEquals(60, base.getObject(3, 2));

      // degenerate shapes: one row becomes one column, one column becomes one row
      XTableUtil.assertEquals(new RotatedTableLens(new DefaultTableLens(new Object[][] {
         {"a", "b", "c"}})), new Object[][] {{"a"}, {"b"}, {"c"}});
      XTableUtil.assertEquals(new RotatedTableLens(new DefaultTableLens(new Object[][] {
         {"h"}, {"v1"}, {"v2"}, {"v3"}, {"v4"}, {"v5"}})),
         new Object[][] {{"h", "v1", "v2", "v3", "v4", "v5"}});

      // a missing header value is named after its column
      DefaultTableLens blank = new DefaultTableLens(new Object[][] {
         {null, "x"},
         {"a", "b"},
      });
      RotatedTableLens rotatedBlank = new RotatedTableLens(blank);
      assertEquals("Column [0]", rotatedBlank.getObject(0, 0));
      assertEquals("a", rotatedBlank.getObject(0, 1));
   }

   @Test
   void rotateHeaderRowCountOverride() {
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         {"Item", "Q1", "Q2"},
         {"A", 1, 2},
         {"B", 3, 4},
      });
      base.setHeaderColCount(1);

      // -1 takes the base header column count
      assertEquals(1, new RotatedTableLens(base).getHeaderRowCount());
      assertEquals(1, new RotatedTableLens(base, -1).getHeaderRowCount());
      // an explicit count replaces it
      assertEquals(2, new RotatedTableLens(base, 2).getHeaderRowCount());
      assertEquals(0, new RotatedTableLens(base, 0).getHeaderRowCount());
      // and is limited to the number of base columns, the rows of the rotated table
      assertEquals(3, new RotatedTableLens(base, 3).getHeaderRowCount());
      assertEquals(3, new RotatedTableLens(base, 8).getHeaderRowCount());
   }

   @Test
   void rotateMaxColumns() {
      RotatedTableLens lens = new RotatedTableLens(numbered(50));
      assertEquals(50, lens.getColCount());

      lens.setMaxColumns(10);
      assertEquals(10, lens.getColCount());
      assertEquals(2, lens.getRowCount());

      for(int c = 0; c < 10; c++) {
         assertEquals(c == 0 ? "id" : "id" + c, lens.getObject(0, c));
         assertEquals(c == 0 ? "val" : c * 10, lens.getObject(1, c));
      }

      // a new base table is capped the same way
      lens.setTable(numbered(30));
      assertEquals(10, lens.getColCount());

      // zero is no limit, and a limit above the row count leaves every row
      RotatedTableLens unlimited = new RotatedTableLens(numbered(50));
      unlimited.setMaxColumns(0);
      assertEquals(50, unlimited.getColCount());
      unlimited.setMaxColumns(70);
      assertEquals(50, unlimited.getColCount());
   }

   /**
    * A two column table of the given number of rows: a header row, then id1.. and 10, 20...
    */
   private static DefaultTableLens numbered(int rows) {
      Object[][] data = new Object[rows][];
      data[0] = new Object[] {"id", "val"};

      for(int r = 1; r < rows; r++) {
         data[r] = new Object[] {"id" + r, r * 10};
      }

      return new DefaultTableLens(data);
   }

   @Test
   void rotateBaseIndexUnmapped() {
      RotatedTableLens lens = new RotatedTableLens(numbered(5));

      for(int i : new int[] {-1, 0, 1, 2, 4}) {
         assertEquals(-1, lens.getBaseRowIndex(i), "row " + i);
         assertEquals(-1, lens.getBaseColIndex(i), "col " + i);
      }
   }

   @Test
   void rotateColumnTypeMixed() {
      // the rotated column c holds the base row c, past the header columns of the base table
      DefaultTableLens base = new DefaultTableLens(new Object[][] {
         {"name", "c1", "c2", "c3", "c4"},
         {"ints", 1, 2, 3, 4},
         {"mixed", 1, "x", 3, 4},
         {"doubles", 1.5, 2.5, 3.5, 4.5},
         {"strings", "a", "b", "c", "d"},
      });
      base.setHeaderColCount(1);
      RotatedTableLens lens = new RotatedTableLens(base);

      assertEquals(Integer.class, lens.getColType(1));
      assertEquals(String.class, lens.getColType(2));
      assertEquals(Double.class, lens.getColType(3));
      assertEquals(String.class, lens.getColType(4));

      // only the first rows are sampled: a different value far down does not change the type
      Object[][] wide = new Object[2][153];
      wide[0][0] = "name";
      wide[1][0] = "ints";

      for(int c = 1; c < 153; c++) {
         wide[0][c] = "c" + c;
         wide[1][c] = c < 152 ? (Object) c : "late";
      }

      DefaultTableLens wideBase = new DefaultTableLens(wide);
      wideBase.setHeaderColCount(1);
      assertEquals(Integer.class, new RotatedTableLens(wideBase).getColType(1));
   }





   private FormulaTableLens formulas(DefaultTableLens base, String[] headers, String[] formulas) {
      return new FormulaTableLens(base, headers, formulas, wsEnv, null);
   }

   private FormulaTableLens wsFormulas(DefaultTableLens base, String[] headers,
                                       String[] formulas)
   {
      return new FormulaTableLens(base, headers, formulas, wsEnv, null);
   }

   private static DefaultTableLens amounts() {
      return new DefaultTableLens(new Object[][] {
         {"a"},
         {1.5},
         {2.5},
         {4.0},
      });
   }

   @Test
   void formulaMultipleAndDependentColumns() {
      FormulaTableLens lens = formulas(amounts(), new String[] {"f1", "f2", "f3"}, new String[] {
         "field['a'] + 1",
         "field['f1'] * 2",
         "field['f1'] + field['f2']",
      });

      assertEquals(4, lens.getColCount());
      XTableUtil.assertEquals(lens, new Object[][] {
         {"a", "f1", "f2", "f3"},
         {1.5, 2.5, 5.0, 7.5},
         {2.5, 3.5, 7.0, 10.5},
         {4.0, 5.0, 10.0, 15.0},
      });
   }

   @Test
   void formulaForwardReference() {
      // Only the earlier-row half is tested. The forward-row branch of TableRow2.get (row >
      // getRow(), logs and returns null) is unreachable: TableRow passes its own row field,
      // which getRow() returns, so the two are always equal. field[-1] builds a plain
      // TableRow that reads the computed value through FormulaTableLens.getObject.
      // the second column reads the first column of the row above
      FormulaTableLens lens = formulas(amounts(), new String[] {"f1", "prev"}, new String[] {
         "field['a'] * 2",
         "field['a'] + field[-1]['f1']",
      });

      assertTrue(lens.moreRows(3));
      assertNotNull(lens.getObject(2, 2), "row 2 prev column must be computed");
      assertEquals(3.0, lens.getObject(1, 1));
      // row 2 adds its own a (2.5) to the computed f1 of row 1 (3.0)
      assertEquals(5.5, lens.getObject(2, 2));
      // row 3 adds a (4.0) to the computed f1 of row 2 (5.0)
      assertEquals(9.0, lens.getObject(3, 2));
   }

   @Test
   void formulaRestrictedScripts() {
      // scripts always run restricted (Bug #77396): the flag is only recorded, and what a
      // script may do is decided by the list of permitted classes
      String allowed = "java.lang.Integer.parseInt('42')";
      String denied = "java.lang.System.getProperty('user.dir')";
      FormulaTableLens open = wsFormulas(amounts(), new String[] {"v"}, new String[] {allowed});

      assertFalse(open.isRestricted(0));
      assertTrue(open.moreRows(1));
      assertEquals(42, ((Number) open.getObject(1, 1)).intValue());

      FormulaTableLens flagged = wsFormulas(amounts(), new String[] {"v"}, new String[] {allowed});
      flagged.setRestricted(0, true);
      assertTrue(flagged.isRestricted(0));
      assertTrue(flagged.moreRows(1));
      assertEquals(42, ((Number) flagged.getObject(1, 1)).intValue());

      // a class that is not permitted fails with or without the flag
      for(boolean restricted : new boolean[] {false, true}) {
         FormulaTableLens closed = wsFormulas(amounts(), new String[] {"v"}, new String[] {denied});
         closed.setRestricted(0, restricted);
         ExpressionFailedException ex =
            assertThrows(ExpressionFailedException.class, () -> closed.moreRows(1));
         assertEquals(1, ex.getColIndex());
         assertEquals("v", ex.getColName());
         assertTrue(ex.getMessage().contains("not permitted"), ex.getMessage());
      }

      // the flag is recorded per formula column
      FormulaTableLens mixed = wsFormulas(
         amounts(), new String[] {"open", "closed"}, new String[] {allowed, allowed});
      mixed.setRestricted(1, true);
      assertFalse(mixed.isRestricted(0));
      assertTrue(mixed.isRestricted(1));

      // a script that does not use a Java class runs
      FormulaTableLens plain = wsFormulas(amounts(), new String[] {"v"}, new String[] {"1 + 2"});
      plain.setRestricted(0, true);
      assertTrue(plain.moreRows(1));
      assertEquals(3, ((Number) plain.getObject(1, 1)).intValue());
   }

   @Test
   void formulaExpressionError() {
      FormulaTableLens lens = wsFormulas(
         amounts(), new String[] {"bad"}, new String[] {"field['nope'].x.y"});

      ExpressionFailedException ex =
         assertThrows(ExpressionFailedException.class, () -> lens.moreRows(1));
      assertEquals(1, ex.getColIndex());
      assertEquals("bad", ex.getColName());
      assertNotNull(ex.getOriginalException());

      // the failed row is in the table: it reads null and the cell before it is intact
      assertEquals(1.5, lens.getObject(1, 0));
      assertNull(lens.getObject(1, 1));

      // the failure is reported once for the rows computed together: the next row is there,
      // reads null and stays aligned with the base table
      assertTrue(lens.moreRows(2));
      assertNull(lens.getObject(2, 1));
      assertEquals(2.5, lens.getObject(2, 0));
      assertEquals("bad", lens.getObject(0, 1));
   }

   @Test
   void formulaHeadersFormulasMismatch() {
      DefaultTableLens base = amounts();
      String message = "Headers and/or formulas not specified correctly!";

      RuntimeException tooManyHeaders = assertThrows(RuntimeException.class, () ->
         new FormulaTableLens(base, new String[] {"f1", "f2"}, new String[] {"1"}, wsEnv, null));
      assertEquals(RuntimeException.class, tooManyHeaders.getClass());
      assertEquals(message, tooManyHeaders.getMessage());

      RuntimeException tooManyFormulas = assertThrows(RuntimeException.class, () ->
         new FormulaTableLens(base, new String[] {"f1"}, new String[] {"1", "2"}, wsEnv, null));
      assertEquals(message, tooManyFormulas.getMessage());

      RuntimeException noHeaders = assertThrows(RuntimeException.class, () ->
         new FormulaTableLens(base, null, new String[] {"1"}, wsEnv, null));
      assertEquals(message, noHeaders.getMessage());

      // a null formula array is rejected too
      assertThrows(RuntimeException.class, () ->
         new FormulaTableLens(base, new String[] {"f1"}, null, wsEnv, null));

      // matching arrays are accepted
      XTable ok = new FormulaTableLens(base, new String[] {"f1"}, new String[] {"1"}, wsEnv, null);
      assertEquals(2, ok.getColCount());
   }
}
