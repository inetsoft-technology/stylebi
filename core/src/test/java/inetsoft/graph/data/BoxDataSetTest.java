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
package inetsoft.graph.data;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BoxDataSet}.
 */
class BoxDataSetTest {

   // -----------------------------------------------------------------------
   // getBaseName tests — test through the public static method directly
   // -----------------------------------------------------------------------

   @Test
   void getBaseName_nullInput_returnsNull() {
      assertNull(BoxDataSet.getBaseName(null));
   }

   @ParameterizedTest(name = "prefix ''{0}'' → base ''{1}''")
   @CsvSource({
      "Min_Sales,    Sales",
      "Q25_Sales,    Sales",
      "Medium_Sales, Sales",
      "Q75_Sales,    Sales",
      "Max_Sales,    Sales",
   })
   void getBaseName_knownPrefix_stripsPrefix(String input, String expected) {
      assertEquals(expected.trim(), BoxDataSet.getBaseName(input.trim()));
   }

   @Test
   void getBaseName_outlierPrefix_unchanged() {
      // Outlier_ is NOT stripped by getBaseName – it is not in the condition list
      assertEquals("Outlier_Sales", BoxDataSet.getBaseName("Outlier_Sales"));
   }

   @Test
   void getBaseName_noPrefix_unchanged() {
      assertEquals("Revenue", BoxDataSet.getBaseName("Revenue"));
   }

   @Test
   void getBaseName_emptyString_returnsEmpty() {
      assertEquals("", BoxDataSet.getBaseName(""));
   }

   // -----------------------------------------------------------------------
   // Prefix constants sanity check
   // -----------------------------------------------------------------------

   @Test
   void prefixConstants_endWithUnderscore() {
      assertTrue(BoxDataSet.MIN_PREFIX.endsWith("_"));
      assertTrue(BoxDataSet.Q25_PREFIX.endsWith("_"));
      assertTrue(BoxDataSet.MEDIUM_PREFIX.endsWith("_"));
      assertTrue(BoxDataSet.Q75_PREFIX.endsWith("_"));
      assertTrue(BoxDataSet.MAX_PREFIX.endsWith("_"));
      assertTrue(BoxDataSet.OUTLIER_PREFIX.endsWith("_"));
   }

   // -----------------------------------------------------------------------
   // Helper: build a DefaultDataSet with one dimension and one measure
   // -----------------------------------------------------------------------

   /**
    * Build a dataset with one dimension column "grp" and one measure column "val".
    * Each array element in {@code values} is one row sharing the same group "G1".
    */
   private DefaultDataSet buildSingleGroupDataset(double... values) {
      Object[][] rows = new Object[values.length + 1][2];
      rows[0][0] = "grp";
      rows[0][1] = "val";
      for(int i = 0; i < values.length; i++) {
         rows[i + 1][0] = "G1";
         rows[i + 1][1] = values[i];
      }
      return new DefaultDataSet(rows);
   }

   /**
    * Build a dataset with two groups so we can verify grouping.
    */
   private DefaultDataSet buildTwoGroupDataset(double[] group1, double[] group2) {
      int total = group1.length + group2.length;
      Object[][] rows = new Object[total + 1][2];
      rows[0][0] = "grp";
      rows[0][1] = "val";
      int r = 1;
      for(double v : group1) {
         rows[r][0] = "A";
         rows[r][1] = v;
         r++;
      }
      for(double v : group2) {
         rows[r][0] = "B";
         rows[r][1] = v;
         r++;
      }
      return new DefaultDataSet(rows);
   }

   // -----------------------------------------------------------------------
   // Column structure
   // -----------------------------------------------------------------------

   @Test
   void colCount_singleMeasure_producesCorrectColumns() {
      DefaultDataSet base = buildSingleGroupDataset(1, 2, 3);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});
      // dims(1) + measures(1) * 6  = 7 columns
      assertEquals(7, box.getColCount());
   }

   @Test
   void headers_singleMeasure_containsAllPrefixedHeaders() {
      DefaultDataSet base = buildSingleGroupDataset(1, 2, 3);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});

      assertEquals("grp",                     box.getHeader(0));
      assertEquals("val",                     box.getHeader(1));
      assertEquals(BoxDataSet.MIN_PREFIX    + "val", box.getHeader(2));
      assertEquals(BoxDataSet.Q25_PREFIX    + "val", box.getHeader(3));
      assertEquals(BoxDataSet.MEDIUM_PREFIX + "val", box.getHeader(4));
      assertEquals(BoxDataSet.Q75_PREFIX    + "val", box.getHeader(5));
      assertEquals(BoxDataSet.MAX_PREFIX    + "val", box.getHeader(6));
   }

   // -----------------------------------------------------------------------
   // Single-value group — all five quartile values equal that value
   // -----------------------------------------------------------------------

   @Test
   void singleValue_allQuartilesAreEqual() {
      DefaultDataSet base = buildSingleGroupDataset(42.0);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});

      // 1 quartile row, 0 outlier rows
      assertEquals(1, box.getRowCount());

      int minIdx  = box.indexOfHeader(BoxDataSet.MIN_PREFIX    + "val");
      int q25Idx  = box.indexOfHeader(BoxDataSet.Q25_PREFIX    + "val");
      int medIdx  = box.indexOfHeader(BoxDataSet.MEDIUM_PREFIX + "val");
      int q75Idx  = box.indexOfHeader(BoxDataSet.Q75_PREFIX    + "val");
      int maxIdx  = box.indexOfHeader(BoxDataSet.MAX_PREFIX    + "val");

      Number min  = (Number) box.getData(minIdx,  0);
      Number q25  = (Number) box.getData(q25Idx,  0);
      Number med  = (Number) box.getData(medIdx,  0);
      Number q75  = (Number) box.getData(q75Idx,  0);
      Number max  = (Number) box.getData(maxIdx,  0);

      assertEquals(42.0f, min.floatValue(),  0.001f);
      assertEquals(42.0f, q25.floatValue(),  0.001f);
      assertEquals(42.0f, med.floatValue(),  0.001f);
      assertEquals(42.0f, q75.floatValue(),  0.001f);
      assertEquals(42.0f, max.floatValue(),  0.001f);
   }

   // -----------------------------------------------------------------------
   // Two-value group
   // -----------------------------------------------------------------------

   @Test
   void twoValues_quartilesCorrect() {
      // v1=2, v2=8: min=2, q25=2, median=(2+8)/2=5, q75=8, max=8
      DefaultDataSet base = buildSingleGroupDataset(2.0, 8.0);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});

      assertEquals(1, box.getRowCount());

      int minIdx  = box.indexOfHeader(BoxDataSet.MIN_PREFIX    + "val");
      int medIdx  = box.indexOfHeader(BoxDataSet.MEDIUM_PREFIX + "val");
      int maxIdx  = box.indexOfHeader(BoxDataSet.MAX_PREFIX    + "val");

      Number min  = (Number) box.getData(minIdx, 0);
      Number med  = (Number) box.getData(medIdx, 0);
      Number max  = (Number) box.getData(maxIdx, 0);

      assertEquals(2.0f,  min.floatValue(), 0.01f);
      assertEquals(5.0f,  med.floatValue(), 0.01f);
      assertEquals(8.0f,  max.floatValue(), 0.01f);
   }

   // -----------------------------------------------------------------------
   // Odd-count group — values 1,2,3,4,5
   // n=4 (0-indexed last index), so:
   //   medium = values[4*0.5=2] = 3
   //   q25    = values[4*0.25=1] = 2
   //   q75    = values[4*0.75=3] = 4
   //   iqr = 2, whiskers: lower = 2-3 = -1, upper = 4+3 = 7
   //   min = 1 (>= -1), max = 5 (<= 7) — no outliers
   // -----------------------------------------------------------------------

   @Test
   void oddCount_quartilesCorrect_noOutliers() {
      DefaultDataSet base = buildSingleGroupDataset(1, 2, 3, 4, 5);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});

      assertEquals(1, box.getRowCount());

      int minIdx = box.indexOfHeader(BoxDataSet.MIN_PREFIX    + "val");
      int q25Idx = box.indexOfHeader(BoxDataSet.Q25_PREFIX    + "val");
      int medIdx = box.indexOfHeader(BoxDataSet.MEDIUM_PREFIX + "val");
      int q75Idx = box.indexOfHeader(BoxDataSet.Q75_PREFIX    + "val");
      int maxIdx = box.indexOfHeader(BoxDataSet.MAX_PREFIX    + "val");

      assertEquals(1.0f,  ((Number) box.getData(minIdx, 0)).floatValue(), 0.01f);
      assertEquals(2.0f,  ((Number) box.getData(q25Idx, 0)).floatValue(), 0.01f);
      assertEquals(3.0f,  ((Number) box.getData(medIdx, 0)).floatValue(), 0.01f);
      assertEquals(4.0f,  ((Number) box.getData(q75Idx, 0)).floatValue(), 0.01f);
      assertEquals(5.0f,  ((Number) box.getData(maxIdx, 0)).floatValue(), 0.01f);
   }

   // -----------------------------------------------------------------------
   // Even-count group — values 1,2,3,4
   // n=3, medium = (values[1]+values[2])/2 = (2+3)/2 = 2.5
   // q25 = values[0.75] = (1+2)/2 = 1.5
   // q75 = values[2.25] = (3+4)/2 = 3.5
   // -----------------------------------------------------------------------

   @Test
   void evenCount_quartilesCorrect_noOutliers() {
      DefaultDataSet base = buildSingleGroupDataset(1, 2, 3, 4);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});

      assertEquals(1, box.getRowCount());

      int q25Idx = box.indexOfHeader(BoxDataSet.Q25_PREFIX    + "val");
      int medIdx = box.indexOfHeader(BoxDataSet.MEDIUM_PREFIX + "val");
      int q75Idx = box.indexOfHeader(BoxDataSet.Q75_PREFIX    + "val");

      assertEquals(1.5f,  ((Number) box.getData(q25Idx, 0)).floatValue(), 0.01f);
      assertEquals(2.5f,  ((Number) box.getData(medIdx, 0)).floatValue(), 0.01f);
      assertEquals(3.5f,  ((Number) box.getData(q75Idx, 0)).floatValue(), 0.01f);
   }

   // -----------------------------------------------------------------------
   // Outlier detection via 1.5 * IQR rule
   //
   // Values: 1,2,3,4,5,6,7,8,100
   // n=8, q25 = values[2] = 3, q75 = values[6] = 7
   // iqr = 4, upper fence = 7 + 6 = 13 → 100 is an outlier
   // upper whisker is capped at 13 (but largest non-outlier is 8, so
   // the actual upper whisker = min(max, fence) = min(100, 13) = 13;
   // but the row's max column will contain 13 since the max is adjusted)
   // -----------------------------------------------------------------------

   @Test
   void outlier_uppperOutlier_isRecordedAndWhiskerAdjusted() {
      DefaultDataSet base = buildSingleGroupDataset(1, 2, 3, 4, 5, 6, 7, 8, 100);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});

      // 1 quartile row + 1 outlier row
      assertEquals(2, box.getRowCount());

      int maxIdx = box.indexOfHeader(BoxDataSet.MAX_PREFIX + "val");
      Number max = (Number) box.getData(maxIdx, 0);
      // upper whisker should be at 7 + 4*1.5 = 13
      assertEquals(13.0f, max.floatValue(), 0.01f);

      // The second row is the outlier row — value should be 100
      int valIdx = box.indexOfHeader("val");
      Number outlierVal = (Number) box.getData(valIdx, 1);
      assertEquals(100.0f, outlierVal.floatValue(), 0.01f);
   }

   // -----------------------------------------------------------------------
   // Lower outlier detection
   //
   // Values: -100, 1, 2, 3, 4, 5, 6, 7, 8
   // q25=2, q75=6, iqr=4, lower fence = 2 - 6 = -4 → -100 is outlier
   // -----------------------------------------------------------------------

   @Test
   void outlier_lowerOutlier_isRecordedAndWhiskerAdjusted() {
      DefaultDataSet base = buildSingleGroupDataset(-100, 1, 2, 3, 4, 5, 6, 7, 8);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});

      assertEquals(2, box.getRowCount());

      int minIdx = box.indexOfHeader(BoxDataSet.MIN_PREFIX + "val");
      Number min = (Number) box.getData(minIdx, 0);
      // lower whisker = q25 - 1.5*iqr = 2 - 6 = -4
      assertEquals(-4.0f, min.floatValue(), 0.01f);

      int valIdx = box.indexOfHeader("val");
      Number outlierVal = (Number) box.getData(valIdx, 1);
      assertEquals(-100.0f, outlierVal.floatValue(), 0.01f);
   }

   // -----------------------------------------------------------------------
   // Multiple groups produce separate box-plot statistics
   // -----------------------------------------------------------------------

   @Test
   void twoGroups_eachHasOwnBoxStats() {
      DefaultDataSet base = buildTwoGroupDataset(
         new double[]{1, 2, 3, 4, 5},
         new double[]{10, 20, 30, 40, 50}
      );
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});
      // Two groups → 2 quartile rows (assuming no outliers)
      assertEquals(2, box.getRowCount());
   }

   // -----------------------------------------------------------------------
   // Construction: getDims / getVars round-trip
   // -----------------------------------------------------------------------

   @Test
   void getDimsAndVars_returnCorrectArrays() {
      DefaultDataSet base = buildSingleGroupDataset(1, 2, 3);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});
      assertArrayEquals(new String[]{"grp"}, box.getDims());
      assertArrayEquals(new String[]{"val"}, box.getVars());
   }

   // -----------------------------------------------------------------------
   // addDim and addVar expand the dataset
   // -----------------------------------------------------------------------

   @Test
   void addDim_newDim_addedOnce() {
      DefaultDataSet base = buildSingleGroupDataset(1, 2, 3);
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val"});
      box.addDim("grp"); // duplicate — should not add
      assertEquals(1, box.getDims().length);
   }

   @Test
   void addVar_newVar_reflected() {
      DefaultDataSet base = new DefaultDataSet(new Object[][]{
         {"grp", "val1", "val2"},
         {"G1",  10.0,   20.0},
         {"G1",  20.0,   40.0},
      });
      BoxDataSet box = new BoxDataSet(base, new String[]{"grp"}, new String[]{"val1"});
      box.addVar("val2");
      assertEquals(2, box.getVars().length);
   }
}
