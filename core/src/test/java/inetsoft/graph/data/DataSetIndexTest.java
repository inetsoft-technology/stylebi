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

import inetsoft.graph.internal.GDefaults;
import org.junit.jupiter.api.*;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DataSetIndex}.
 * <p>
 * The index's {@code normalize}, {@code createIndexMap}, and
 * {@code createSubDataSet} methods are private; we exercise them through the
 * public API (constructor, {@link DataSetIndex#addIndex}, and
 * {@link DataSetIndex#createSubDataSet(Map, boolean)}).
 */
class DataSetIndexTest {

   // -----------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------

   /**
    * Convenience: build a DefaultDataSet from rows + header row.
    * Example: buildDataSet({"col1","col2"}, {"A",1}, {"B",2})
    */
   private static DefaultDataSet build(Object[]... rowsIncludingHeader) {
      return new DefaultDataSet(rowsIncludingHeader);
   }

   // -----------------------------------------------------------------------
   // getDataSet – basic round-trip
   // -----------------------------------------------------------------------

   @Test
   void getDataSet_returnsCopiedDataSet() {
      DefaultDataSet base = build(
         new Object[]{"cat", "val"},
         new Object[]{"A",  1.0},
         new Object[]{"B",  2.0}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      DataSet ds = idx.getDataSet();
      assertNotNull(ds);
      // the internal copy should have same column count
      assertEquals(base.getColCount(), ds.getColCount());
   }

   // -----------------------------------------------------------------------
   // addIndex / getIndices
   // -----------------------------------------------------------------------

   @Test
   void addIndex_knownColumn_appearsInIndices() {
      DefaultDataSet base = build(
         new Object[]{"cat", "val"},
         new Object[]{"A",  1.0}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("cat");
      assertTrue(idx.getIndices().contains("cat"));
   }

   @Test
   void addIndex_unknownColumn_notInIndices() {
      DefaultDataSet base = build(
         new Object[]{"cat", "val"},
         new Object[]{"A",  1.0}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("no_such_column");
      assertFalse(idx.getIndices().contains("no_such_column"));
   }

   @Test
   void addIndex_duplicate_storedOnce() {
      DefaultDataSet base = build(
         new Object[]{"cat"},
         new Object[]{"A"}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("cat");
      idx.addIndex("cat"); // duplicate
      assertEquals(1, idx.getIndices().size());
   }

   // -----------------------------------------------------------------------
   // createSubDataSet — single condition
   // -----------------------------------------------------------------------

   @Test
   void createSubDataSet_singleCondition_filtersRows() {
      DefaultDataSet base = build(
         new Object[]{"cat", "val"},
         new Object[]{"A",  10.0},
         new Object[]{"B",  20.0},
         new Object[]{"A",  30.0}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("cat");

      Map<String, Object> cond = new HashMap<>();
      cond.put("cat", "A");
      DataSet sub = idx.createSubDataSet(cond, true);

      assertNotNull(sub);
      assertEquals(2, sub.getRowCount());

      // All returned rows should have cat == "A"
      int catCol = sub.indexOfHeader("cat");
      for(int r = 0; r < sub.getRowCount(); r++) {
         assertEquals("A", sub.getData(catCol, r));
      }
   }

   @Test
   void createSubDataSet_conditionNoMatch_returnsEmptyDataSet() {
      DefaultDataSet base = build(
         new Object[]{"cat", "val"},
         new Object[]{"A",  10.0},
         new Object[]{"B",  20.0}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("cat");

      Map<String, Object> cond = new HashMap<>();
      cond.put("cat", "Z");
      DataSet sub = idx.createSubDataSet(cond, true);

      assertNotNull(sub);
      assertEquals(0, sub.getRowCount());
   }

   // -----------------------------------------------------------------------
   // createSubDataSet — multi-condition AND filter
   // -----------------------------------------------------------------------

   @Test
   void createSubDataSet_multiCondition_andFilter() {
      DefaultDataSet base = build(
         new Object[]{"cat", "region", "val"},
         new Object[]{"A",  "East",   10.0},
         new Object[]{"A",  "West",   20.0},
         new Object[]{"B",  "East",   30.0},
         new Object[]{"A",  "East",   40.0}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("cat");
      idx.addIndex("region");

      Map<String, Object> cond = new HashMap<>();
      cond.put("cat",    "A");
      cond.put("region", "East");
      DataSet sub = idx.createSubDataSet(cond, true);

      assertNotNull(sub);
      // rows 0 and 3 match
      assertEquals(2, sub.getRowCount());

      int catCol    = sub.indexOfHeader("cat");
      int regionCol = sub.indexOfHeader("region");
      for(int r = 0; r < sub.getRowCount(); r++) {
         assertEquals("A",    sub.getData(catCol,    r));
         assertEquals("East", sub.getData(regionCol, r));
      }
   }

   // -----------------------------------------------------------------------
   // createSubDataSet — Object[] value (OR within a column)
   // -----------------------------------------------------------------------

   @Test
   void createSubDataSet_arrayValue_orWithinColumn() {
      DefaultDataSet base = build(
         new Object[]{"cat", "val"},
         new Object[]{"A",  1.0},
         new Object[]{"B",  2.0},
         new Object[]{"C",  3.0},
         new Object[]{"A",  4.0}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("cat");

      Map<String, Object> cond = new HashMap<>();
      cond.put("cat", new Object[]{"A", "B"});
      DataSet sub = idx.createSubDataSet(cond, true);

      assertNotNull(sub);
      // rows with A or B: rows 0, 1, 3 = 3 rows
      assertEquals(3, sub.getRowCount());
   }

   // -----------------------------------------------------------------------
   // normalize — tested indirectly via createSubDataSet with numeric column
   // -----------------------------------------------------------------------

   @Test
   void normalize_numericColumn_matchesAsDouble() {
      // val column contains Integer values; the index normalises them to Double.
      DefaultDataSet base = build(
         new Object[]{"grp", "val"},
         new Object[]{"X",   Integer.valueOf(5)},
         new Object[]{"X",   Integer.valueOf(10)},
         new Object[]{"Y",   Integer.valueOf(5)}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("val");

      Map<String, Object> cond = new HashMap<>();
      // Pass a Double — should match the normalised Integer(5) stored as Double
      cond.put("val", 5.0);
      DataSet sub = idx.createSubDataSet(cond, true);

      assertNotNull(sub);
      assertEquals(2, sub.getRowCount());
   }

   // -----------------------------------------------------------------------
   // normalize — NULL_STR sentinel → null  (indirectly via createSubDataSet)
   // -----------------------------------------------------------------------

   @Test
   void normalize_nullStrSentinel_treatedAsNull() {
      // Put GDefaults.NULL_STR in a cell; the index should store it as null.
      // A query for null should then find that row.
      Object nullSentinel = GDefaults.NULL_STR;

      DefaultDataSet base = build(
         new Object[]{"cat"},
         new Object[]{nullSentinel},
         new Object[]{"A"}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("cat");

      Map<String, Object> cond = new HashMap<>();
      cond.put("cat", (Object) null);
      DataSet sub = idx.createSubDataSet(cond, true);

      assertNotNull(sub);
      assertEquals(1, sub.getRowCount());
   }

   // -----------------------------------------------------------------------
   // createIndexMap — rows sharing a value share a BitSet entry
   // (verified by querying two matching rows)
   // -----------------------------------------------------------------------

   @Test
   void createIndexMap_sharedValue_returnsAllMatchingRows() {
      DefaultDataSet base = build(
         new Object[]{"cat", "val"},
         new Object[]{"same", 1.0},
         new Object[]{"diff", 2.0},
         new Object[]{"same", 3.0},
         new Object[]{"same", 4.0}
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("cat");

      Map<String, Object> cond = new HashMap<>();
      cond.put("cat", "same");
      DataSet sub = idx.createSubDataSet(cond, true);

      assertEquals(3, sub.getRowCount());
   }

   // -----------------------------------------------------------------------
   // xMaxCounts / yMaxCounts round-trip
   // -----------------------------------------------------------------------

   @Test
   void xMaxCounts_setAndGet_returnsValue() {
      DefaultDataSet base = build(new Object[]{"col"}, new Object[]{"v"});
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.setXMaxCount("key1", 42);
      assertEquals(42, (int) idx.getXMaxCount("key1"));
   }

   @Test
   void xMaxCounts_missingKey_returnsNull() {
      DefaultDataSet base = build(new Object[]{"col"}, new Object[]{"v"});
      DataSetIndex idx = new DataSetIndex(base, false);
      assertNull(idx.getXMaxCount("nonexistent"));
   }

   @Test
   void yMaxCounts_setAndGet_returnsValue() {
      DefaultDataSet base = build(new Object[]{"col"}, new Object[]{"v"});
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.setYMaxCount("keyY", 99);
      assertEquals(99, (int) idx.getYMaxCount("keyY"));
   }

   // -----------------------------------------------------------------------
   // Constructor with key collection
   // -----------------------------------------------------------------------

   @Test
   void constructorWithKeys_indexesSpecifiedColumns() {
      DefaultDataSet base = build(
         new Object[]{"a", "b"},
         new Object[]{"x", 1.0}
      );
      List<String> keys = Arrays.asList("a");
      DataSetIndex idx = new DataSetIndex(base, keys, false);
      assertTrue(idx.getIndices().contains("a"));
      assertFalse(idx.getIndices().contains("b"));
   }

   // -----------------------------------------------------------------------
   // normalize — Date subclass (java.sql.Date → Timestamp) indirectly
   // -----------------------------------------------------------------------

   @Test
   void normalize_sqlDate_normalizedToTimestamp() {
      long millis = System.currentTimeMillis();
      java.sql.Date sqlDate = new java.sql.Date(millis);
      Timestamp ts          = new Timestamp(millis);

      DefaultDataSet base = build(
         new Object[]{"ts_col"},
         new Object[]{sqlDate},
         new Object[]{new java.sql.Date(millis + 86_400_000L)}  // tomorrow
      );
      DataSetIndex idx = new DataSetIndex(base, false);
      idx.addIndex("ts_col");

      // Query with a Timestamp equal to sqlDate after normalisation
      Map<String, Object> cond = new HashMap<>();
      cond.put("ts_col", ts);
      DataSet sub = idx.createSubDataSet(cond, true);

      assertNotNull(sub);
      // Both dates normalise to Timestamp; the one with the same millis matches
      assertEquals(1, sub.getRowCount());
   }
}
