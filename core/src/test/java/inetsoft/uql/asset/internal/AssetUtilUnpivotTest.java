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
package inetsoft.uql.asset.internal;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.table.XSwappableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The un-pivot of the worksheet: the header row of the measure columns becomes the values of a
 * "Dimension" column and the cells become the values of a "Measure" column. The expected tables
 * are written out by hand, one row for every cell of the measure columns, in the order of the
 * rows of the source and of its columns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetUtilUnpivotTest {
   /** A table of the data with the column types given. */
   private static DefaultTableLens table(Class<?>[] types, Object[][] data) {
      return new DefaultTableLens(data) {
         @Override
         public Class<?> getColType(int col) {
            return types[col];
         }
      };
   }

   private static Object[][] rows(XSwappableTable table) {
      table.moreRows(XSwappableTable.EOT);
      List<Object[]> rows = new ArrayList<>();

      for(int r = 0; r < table.getRowCount(); r++) {
         Object[] row = new Object[table.getColCount()];

         for(int c = 0; c < row.length; c++) {
            row[c] = table.getObject(r, c);
         }

         rows.add(row);
      }

      return rows.toArray(new Object[0][]);
   }

   private static final Class<?>[] STR_INT_INT_INT =
      { String.class, Integer.class, Integer.class, Integer.class };

   private static final Object[][] SALES = {
      { "region", "Q1", "Q2", "Q3" },
      { "East", 1, 2, 3 },
      { "West", 4, 5, 6 }
   };

   @Test
   void oneHeaderColumnGivesARowPerCell() {
      XSwappableTable result = AssetUtil.unpivot(table(STR_INT_INT_INT, SALES), 1);

      assertArrayEquals(new Object[][] {
         { "region", "Dimension", "Measure" },
         { "East", "Q1", 1 }, { "East", "Q2", 2 }, { "East", "Q3", 3 },
         { "West", "Q1", 4 }, { "West", "Q2", 5 }, { "West", "Q3", 6 }
      }, rows(result));
   }

   @Test
   void typesAreTheHeaderColumnsAStringAndTheTypeOfTheMeasures() {
      XSwappableTable result = AssetUtil.unpivot(table(STR_INT_INT_INT, SALES), 1);

      assertEquals(String.class, result.getColType(0));
      assertEquals(String.class, result.getColType(1));
      assertEquals(Integer.class, result.getColType(2));
   }

   @Test
   void severalHeaderColumnsAreCopiedToEveryRow() {
      Class<?>[] types = { String.class, String.class, Integer.class, Integer.class };
      Object[][] data = {
         { "region", "shop", "Q1", "Q2" },
         { "East", "a", 1, 2 },
         { "West", "b", 3, 4 }
      };

      assertArrayEquals(new Object[][] {
         { "region", "shop", "Dimension", "Measure" },
         { "East", "a", "Q1", 1 }, { "East", "a", "Q2", 2 },
         { "West", "b", "Q1", 3 }, { "West", "b", "Q2", 4 }
      }, rows(AssetUtil.unpivot(table(types, data), 2)));
   }

   @Test
   void noHeaderColumnMakesEveryColumnAMeasure() {
      Class<?>[] types = { Integer.class, Integer.class };
      Object[][] data = { { "a", "b" }, { 1, 2 }, { 3, 4 } };

      assertArrayEquals(new Object[][] {
         { "Dimension", "Measure" },
         { "a", 1 }, { "b", 2 }, { "a", 3 }, { "b", 4 }
      }, rows(AssetUtil.unpivot(table(types, data), 0)));
   }

   // there is at least one measure column: too many header columns leave the last one
   @Test
   void headerColumnCountIsKeptInsideTheTable() {
      Class<?>[] types = { String.class, String.class, Integer.class };
      Object[][] data = { { "a", "b", "c" }, { "x", "y", 7 } };

      assertArrayEquals(new Object[][] {
         { "a", "b", "Dimension", "Measure" },
         { "x", "y", "c", 7 }
      }, rows(AssetUtil.unpivot(table(types, data), 5)));
   }

   @Test
   void negativeHeaderColumnCountIsZero() {
      Class<?>[] types = { Integer.class, Integer.class };
      Object[][] data = { { "a", "b" }, { 1, 2 } };

      assertArrayEquals(rows(AssetUtil.unpivot(table(types, data), 0)),
                        rows(AssetUtil.unpivot(table(types, data), -3)));
   }

   @Test
   void tableWithoutDataRowsHasOnlyTheHeader() {
      assertArrayEquals(new Object[][] { { "region", "Dimension", "Measure" } },
                        rows(AssetUtil.unpivot(table(STR_INT_INT_INT,
                                                     new Object[][] { SALES[0] }), 1)));
   }

   // a blank or missing header of a header column gets the default name, a header that is not
   // text its text
   @Test
   void headerColumnNamesAreText() {
      Class<?>[] types = { String.class, String.class, String.class, Integer.class };
      Object[][] data = { { null, "  ", 2024, "m" }, { "a", "b", "c", 1 } };

      assertArrayEquals(new Object[][] {
         { "Column [0]", "Column [1]", "2024", "Dimension", "Measure" },
         { "a", "b", "c", "m", 1 }
      }, rows(AssetUtil.unpivot(table(types, data), 3)));
   }

   // the names of the two new columns are made different from the header columns
   @Test
   void newColumnNamesAvoidTheHeaderColumnNames() {
      Class<?>[] types = { String.class, String.class, Integer.class };
      Object[][] data = { { "Dimension", "Measure", "m" }, { "a", "b", 1 } };

      assertArrayEquals(new Object[][] {
         { "Dimension", "Measure", "Dimension_1", "Measure_1" },
         { "a", "b", "m", 1 }
      }, rows(AssetUtil.unpivot(table(types, data), 2)));
   }

   // the header of a measure column that is not text, and a missing one, are the values of the
   // dimension column
   @Test
   void measureHeadersAreFormattedAsText() {
      Class<?>[] types = { String.class, Integer.class, Integer.class, Integer.class };
      Object[][] data = { { "region", 2023, 2024, null }, { "East", 1, 2, 3 } };

      assertArrayEquals(new Object[][] {
         { "region", "Dimension", "Measure" },
         { "East", "2023", 1 }, { "East", "2024", 2 }, { "East", null, 3 }
      }, rows(AssetUtil.unpivot(table(types, data), 1)));
   }

   @Test
   void nullMeasuresStayNull() {
      Class<?>[] types = { String.class, Integer.class, Integer.class };
      Object[][] data = { { "r", "a", "b" }, { "x", null, 2 }, { "y", 3, null } };

      assertArrayEquals(new Object[][] {
         { "r", "Dimension", "Measure" },
         { "x", "a", null }, { "x", "b", 2 }, { "y", "a", 3 }, { "y", "b", null }
      }, rows(AssetUtil.unpivot(table(types, data), 1)));
   }

   // measures of the same type keep it, dates are not turned into text
   @Test
   void measuresOfTheSameTypeAreNotConverted() {
      Class<?>[] types = { String.class, Date.class, Date.class };
      Date d1 = Date.valueOf("2024-02-29");
      Date d2 = Date.valueOf("2023-12-31");
      Object[][] data = { { "r", "start", "end" }, { "x", d1, d2 } };
      Object[][] result = rows(AssetUtil.unpivot(table(types, data), 1));

      assertEquals(d1, result[1][2]);
      assertEquals(d2, result[2][2]);
   }

   // numbers of different types are all doubles
   @Test
   void numbersOfDifferentTypesBecomeDoubles() {
      Class<?>[] types = { String.class, Integer.class, Double.class };
      Object[][] data = { { "r", "a", "b" }, { "x", 1, 2.5 } };
      XSwappableTable result = AssetUtil.unpivot(table(types, data), 1);
      Object[][] rows = rows(result);

      assertEquals(Double.class, result.getColType(2));
      assertEquals(1.0, ((Number) rows[1][2]).doubleValue());
      assertEquals(2.5, ((Number) rows[2][2]).doubleValue());
   }

   // a text column among the measures makes every measure text; numbers are written with at
   // least two decimals for the non-integer types (the "0.00##" pattern) and whole numbers as
   // they are
   @Test
   void measuresOfDifferentKindsBecomeText() {
      Class<?>[] types = { String.class, Integer.class, Double.class, String.class };
      Object[][] data = { { "r", "a", "b", "c" }, { "x", 5, 2.5, "n/a" }, { "y", null, 1234.56789, null } };
      XSwappableTable result = AssetUtil.unpivot(table(types, data), 1);

      assertEquals(String.class, result.getColType(2));
      assertArrayEquals(new Object[][] {
         { "r", "Dimension", "Measure" },
         { "x", "a", "5" }, { "x", "b", "2.50" }, { "x", "c", "n/a" },
         { "y", "a", null }, { "y", "b", "1234.5679" }, { "y", "c", null }
      }, rows(result));
   }
}
