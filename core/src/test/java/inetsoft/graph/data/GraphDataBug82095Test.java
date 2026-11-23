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

import inetsoft.graph.scale.TimeScale;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.util.XTableDataSet;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.util.SparseMatrix;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82095: DefaultDataSet.setComparator on an unknown column, hyperlinks
 * shared by a deep clone, and the TimeSeriesRow row limit.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GraphDataBug82095Test {
   private static final Object[][] K5 = {
      { "k", "v" }, { "b", 2.0 }, { "a", 1.0 }, { "c", 3.0 }, { "a", 5.0 }, { "b", 4.0 } };

   // ------------------------------------------------------------ setComparator

   @Test
   void setComparatorUnknownColumnIsIgnored() {
      DefaultDataSet data = new DefaultDataSet(K5);

      assertDoesNotThrow(() -> data.setComparator("zz", Comparator.naturalOrder()));
      assertNull(data.getComparator("zz"));
   }

   @Test
   void setComparatorKnownColumnIsSet() {
      DefaultDataSet data = new DefaultDataSet(K5);
      Comparator comp = Comparator.reverseOrder();
      data.setComparator("k", comp);

      assertSame(comp, data.getComparator("k"));
   }

   // ------------------------------------------------------------ links on clone

   @Test
   void sparseMatrixCopySharesNoState() {
      SparseMatrix matrix = new SparseMatrix();
      matrix.set(1, 2, "a");
      matrix.set(1000, 3, "b");
      SparseMatrix copy = matrix.copy();

      assertEquals("a", copy.get(1, 2));
      assertEquals("b", copy.get(1000, 3));
      assertSame(SparseMatrix.NULL, copy.get(0, 0));

      copy.set(1, 2, "c");
      copy.set(5, 5, "d");
      matrix.set(1000, 3, "e");

      assertEquals("a", matrix.get(1, 2));
      assertSame(SparseMatrix.NULL, matrix.get(5, 5));
      assertEquals("b", copy.get(1000, 3));
   }

   @Test
   void sparseMatrixCopyOfEmptyMatrixIsEmpty() {
      SparseMatrix copy = new SparseMatrix().copy();

      assertSame(SparseMatrix.NULL, copy.get(0, 0));
      copy.set(0, 0, "a");
      assertEquals("a", copy.get(0, 0));
   }

   @Test
   void filterDeepCloneDoesNotShareLinks() {
      HRef link = new HRef("L");
      AbstractDataSetFilter filter = new AbstractDataSetFilter(new DefaultDataSet(K5)) {
      };
      filter.setHyperlink(1, 0, link);
      AttributeDataSet copy = (AttributeDataSet) filter.clone(false);

      assertSame(link, copy.getHyperlink(1, 0));

      copy.setHyperlink(1, 0, new HRef("M"));

      assertSame(link, filter.getHyperlink(1, 0));
   }

   @Test
   void filterShallowCloneStillSharesLinks() {
      HRef link = new HRef("L");
      AbstractDataSetFilter filter = new AbstractDataSetFilter(new DefaultDataSet(K5)) {
      };
      filter.setHyperlink(1, 0, link);
      AttributeDataSet copy = (AttributeDataSet) filter.clone(true);
      HRef link2 = new HRef("M");
      copy.setHyperlink(1, 0, link2);

      assertSame(link2, filter.getHyperlink(1, 0));
   }

   @Test
   void vsDataSetCloneDoesNotShareLinks() {
      VSDataSet data = new VSDataSet(new DefaultTableLens(K5), new VSDataRef[0]);
      HRef link = new HRef("L");
      data.setHyperlink(1, 0, link);
      VSDataSet copy = (VSDataSet) data.clone();

      assertSame(link, copy.getHyperlink(1, 0));

      copy.setHyperlink(1, 0, new HRef("M"));

      assertSame(link, data.getHyperlink(1, 0));
   }

   @Test
   void xTableDataSetCloneDoesNotShareLinks() {
      XTableDataSet data = new XTableDataSet(new DefaultTableLens(K5));
      HRef link = new HRef("L");
      data.setHyperlink(1, 0, link);
      XTableDataSet copy = (XTableDataSet) data.clone();

      assertSame(link, copy.getHyperlink(1, 0));

      copy.setHyperlink(1, 0, new HRef("M"));

      assertSame(link, data.getHyperlink(1, 0));
   }

   // ------------------------------------------------------------ TimeSeriesRow cap

   @Test
   void maxGapsLimitsAddedRowsStrictly() {
      // Feb to Jun 2023 are missing: 5 gaps, limited to 3
      TimeSeriesRow row = monthRow(3);
      DefaultDataSet data = data(month("A", 2023, 1), month("A", 2023, 7));

      assertEquals(3, row.calculate(data).size());
   }

   @Test
   void maxGapsEqualToTheGapsAddsAll() {
      TimeSeriesRow row = monthRow(2);
      DefaultDataSet data = data(month("A", 2023, 1), month("A", 2023, 4));

      assertEquals(2, row.calculate(data).size());
   }

   @Test
   void maxGapsLimitsRangeEnds() {
      // B has only Jun in the global range Jan to Dec: 5 leading and 6 trailing gaps
      List<Object[]> rows = new ArrayList<>();

      for(int m = 1; m <= 12; m++) {
         rows.add(month("A", 2023, m));
      }

      rows.add(month("B", 2023, 6));
      TimeSeriesRow row = monthRow(2);

      assertEquals(2, row.calculate(data(rows.toArray(new Object[0][]))).size());
   }

   @Test
   void rangeEndsAreFilledBelowTheLimit() {
      List<Object[]> rows = new ArrayList<>();

      for(int m = 1; m <= 12; m++) {
         rows.add(month("A", 2023, m));
      }

      rows.add(month("B", 2023, 6));

      assertEquals(11, monthRow(2000).calculate(data(rows.toArray(new Object[0][]))).size());
   }

   private static TimeSeriesRow monthRow(int maxGaps) {
      TimeSeriesRow row = new TimeSeriesRow("Date", TimeScale.MONTH, 0);
      row.setGroupFields("G");
      row.setMaxGaps(maxGaps);
      return row;
   }

   private static Object[] month(String group, int year, int month) {
      return new Object[] { group, Timestamp.valueOf(LocalDate.of(year, month, 10).atStartOfDay()), 1 };
   }

   private static DefaultDataSet data(Object[]... rows) {
      List<Object[]> table = new ArrayList<>();
      table.add(new Object[] { "G", "Date", "Sales" });
      table.addAll(Arrays.asList(rows));
      return new DefaultDataSet(table.toArray(new Object[0][]));
   }
}
