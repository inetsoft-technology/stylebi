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
package inetsoft.web.viewsheet.controller;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.PairsDataSet;
import inetsoft.report.composition.graph.AliasedColumnDataSet;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.web.wiz.service.WizVsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for PCB-003: {@code AssemblyImageService}'s chart-rendering branch (see
 * {@code processGetAssemblyImage1}) unwraps {@code VGraphPair#getData()} the same way
 * {@code WizVsService#extractChartData} already does and must catch StyleBI's own failed-live-query
 * fallback data (see {@code WizVsServiceCheckFailedQueryTest}) instead of silently rendering the
 * fabricated substitute as if it were real. Exercises the two corrections the refuter pass flagged:
 * (1) the data must be unwrapped ({@link WizVsService#unwrapDataSet}) before the check, since a
 * dual-axis/pareto chart's {@code VGraphPair#getData()} returns a wrapped {@code DataSetFilter}, not
 * a bare {@link VSDataSet}; (2) the check must use the non-expression-wrapped message
 * ({@code wrapExpressionError=false}), since nothing is wrong with an expression here.
 */
@Tag("core")
class AssemblyImageServiceFailedQueryTest {
   @Test
   void detectsFailedQueryOnUnwrappedVSDataSet() {
      VSDataSet vds = new VSDataSet(failedQueryTable("column region_id does not exist"), null);

      DataSet unwrapped = WizVsService.unwrapDataSet(vds);
      assertTrue(unwrapped instanceof VSDataSet, "a bare VSDataSet should unwrap to itself");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WizVsService.checkFailedQuery(((VSDataSet) unwrapped).getTable(), false));
      assertTrue(ex.getMessage().contains("column region_id does not exist"),
                 "should surface the real cause, was: " + ex.getMessage());
      assertFalse(ex.getMessage().contains("expression"),
                  "non-expression callers must not get the misleading expression-column advice, was: " +
                  ex.getMessage());
   }

   @Test
   void detectsFailedQueryThroughDataSetFilterWrapper() {
      // AliasedColumnDataSet is a generic DataSetFilter wrapper -- exercises the same wrapping shape
      // ChartVSAQuery produces for a dual-axis/pareto chart binding.
      VSDataSet vds = new VSDataSet(failedQueryTable("true"), null);
      AliasedColumnDataSet wrapped = new AliasedColumnDataSet(vds);

      DataSet unwrapped = WizVsService.unwrapDataSet(wrapped);
      assertTrue(unwrapped instanceof VSDataSet,
                 "a DataSetFilter-wrapped VSDataSet should unwrap to the underlying VSDataSet");

      assertThrows(IllegalArgumentException.class,
         () -> WizVsService.checkFailedQuery(((VSDataSet) unwrapped).getTable(), false),
         "a failed-query table wrapped behind a DataSetFilter must still be caught, not missed");
   }

   @Test
   void detectsFailedQueryThroughPairsDataSetWrapper() {
      // PairsDataSet is the scatter-matrix-specific wrapper the debugger/refuter both called out by
      // name as a concrete case where a naive instanceof VSDataSet cast (skipping unwrapDataSet)
      // would silently miss the failed-query table.
      VSDataSet vds = new VSDataSet(failedQueryTable("true"), null);
      PairsDataSet wrapped = new PairsDataSet(vds);

      DataSet unwrapped = WizVsService.unwrapDataSet(wrapped);
      assertTrue(unwrapped instanceof VSDataSet,
                 "a PairsDataSet-wrapped VSDataSet should unwrap to the underlying VSDataSet");

      assertThrows(IllegalArgumentException.class,
         () -> WizVsService.checkFailedQuery(((VSDataSet) unwrapped).getTable(), false),
         "a failed-query table wrapped behind a PairsDataSet must still be caught, not missed");
   }

   @Test
   void doesNotThrowForCleanData() {
      VSDataSet vds = new VSDataSet(new DefaultTableLens(1, 1), null);

      DataSet unwrapped = WizVsService.unwrapDataSet(vds);
      assertTrue(unwrapped instanceof VSDataSet);
      assertDoesNotThrow(() -> WizVsService.checkFailedQuery(((VSDataSet) unwrapped).getTable(), false));
   }

   private static DefaultTableLens failedQueryTable(String cause) {
      DefaultTableLens lens = new DefaultTableLens(1, 1);
      lens.setProperty(XNodeMetaTable.FAILED_QUERY_PROPERTY, cause);
      return lens;
   }
}
