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
package inetsoft.graph.data;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link TopDataSet} wrap protocol, driven through {@link DataSetIndex}.
 */
@Tag("core")
class TopDataSetTest {
   // ---------------------------------------------------------------- helpers

   private static DefaultDataSet k5() {
      return new DefaultDataSet(new Object[][]{
         {"k", "v"}, {"b", 2.0}, {"a", 1.0}, {"c", 3.0}, {"a", 5.0}, {"b", 4.0}});
   }

   /**
    * Top data set whose wrap returns another instance and records the prototypes it was given
    * in a list shared by every clone.
    */
   private static class TW extends TopDataSet {
      TW(DataSet base, boolean calcInSub, List<TopDataSet> protos) {
         super(base);
         this.calcInSub = calcInSub;
         this.protos = protos;
      }

      @Override
      public DataSet wrap(DataSet base, TopDataSet proto) {
         protos.add(proto);
         return new TW(base, calcInSub, protos);
      }

      @Override
      public boolean isCalcInSub() {
         return calcInSub;
      }

      private final boolean calcInSub;
      private final List<TopDataSet> protos;
   }

   private static class RT implements CalcColumn {
      @Override
      public Object calculate(DataSet data, int row, boolean first, boolean last) {
         return (double) row;
      }

      @Override
      public String getHeader() {
         return "rt";
      }

      @Override
      public Class getType() {
         return Double.class;
      }

      @Override
      public boolean isMeasure() {
         return true;
      }

      @Override
      public String getField() {
         return "v";
      }
   }

   private static class AddRow implements CalcRow {
      @Override
      public List<Object[]> calculate(DataSet data) {
         return Collections.singletonList(new Object[]{"z", 9.0});
      }

      @Override
      public Comparator getComparator(String col) {
         return null;
      }
   }

   private static DataSet subOfA(TW top) {
      DataSetIndex index = new DataSetIndex(top, false);
      index.addIndex("k");
      Map<String, Object> conds = new HashMap<>();
      conds.put("k", "a");
      return index.createSubDataSet(conds, true);
   }

   // ------------------------------------------------------------------ tests

   @Test
   void subDataSetIsWrappedInTheTopDataSetOnce() {
      List<TopDataSet> protos = new ArrayList<>();
      TW top = new TW(k5(), false, protos);

      DataSet result = subOfA(top);

      assertTrue(result instanceof TopDataSet);
      DataSet inner = ((TopDataSet) result).getDataSet();
      assertTrue(inner instanceof SubDataSet);
      assertEquals(2, inner.getRowCount());
      assertEquals(1, ((SubDataSet) inner).getBaseRow(0));
      assertEquals(3, ((SubDataSet) inner).getBaseRow(1));

      // the index works on a copy of the original, so the prototype is a copy of it
      assertEquals(1, protos.size());
      assertTrue(protos.get(0) instanceof TW);
      assertSame(top.getDataSet(), protos.get(0).getDataSet());
   }

   @Test
   void calcInSubDefaultsToFalseAndSubclassOverrideIsRead() {
      List<TopDataSet> protos = new ArrayList<>();

      TopDataSet plain = new TopDataSet(k5()) {
         @Override
         public DataSet wrap(DataSet base, TopDataSet proto) {
            return base;
         }
      };

      assertFalse(plain.isCalcInSub());
      assertFalse(new TW(k5(), false, protos).isCalcInSub());
      assertTrue(new TW(k5(), true, protos).isCalcInSub());
   }

   @Test
   void calcColumnsStayOnTheWrapperByDefault() {
      TW top = new TW(k5(), false, new ArrayList<>());
      top.addCalcColumn(new RT());

      TopDataSet wrapper = (TopDataSet) subOfA(top);

      assertEquals(1, wrapper.getCalcColumns(true).size());
      assertEquals("rt", wrapper.getCalcColumns(true).get(0).getHeader());
      assertTrue(((SubDataSet) wrapper.getDataSet()).getCalcColumns(true).isEmpty());
   }

   @Test
   void calcColumnsGoToTheSubDataSetWhenCalcIsInSub() {
      TW top = new TW(k5(), true, new ArrayList<>());
      top.addCalcColumn(new RT());

      TopDataSet wrapper = (TopDataSet) subOfA(top);

      assertTrue(wrapper.getCalcColumns(true).isEmpty());
      List<CalcColumn> inSub = ((SubDataSet) wrapper.getDataSet()).getCalcColumns(true);
      assertEquals(1, inSub.size());
      assertEquals("rt", inSub.get(0).getHeader());
   }

   @ParameterizedTest
   @ValueSource(booleans = {false, true})
   void calcRowsAlwaysStayOnTheWrapper(boolean calcInSub) {
      TW top = new TW(k5(), calcInSub, new ArrayList<>());
      CalcRow row = new AddRow();
      top.addCalcRow(row);

      TopDataSet wrapper = (TopDataSet) subOfA(top);

      assertTrue(wrapper.getCalcRows().contains(row));
   }

   @Test
   void wrapperReadsTheSubRows() {
      TW top = new TW(k5(), false, new ArrayList<>());
      TopDataSet wrapper = (TopDataSet) subOfA(top);
      SubDataSet inner = (SubDataSet) wrapper.getDataSet();

      assertEquals(2, wrapper.getRowCount());
      assertEquals(1.0, wrapper.getData("v", 0));
      assertEquals(5.0, wrapper.getData("v", 1));
      assertEquals(0, wrapper.getBaseRow(0));
      assertEquals(1, wrapper.getBaseRow(1));
      assertEquals(1, inner.getBaseRow(wrapper.getBaseRow(0)));
      assertEquals(3, inner.getBaseRow(wrapper.getBaseRow(1)));
   }
}
