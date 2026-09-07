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
package inetsoft.report.composition.execution;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.SingleTimeInfo;
import inetsoft.uql.viewsheet.TimeInfo;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VSFILT-009 part 2: a real (non-mocked) execution of {@link TimeSliderVSAQuery#getData()} for
 * a {@code TimeSliderVSAssembly} bound to a numeric measure column via {@code SingleTimeInfo}
 * with {@code TimeInfo.NUMBER} -- exactly the shape {@code WizVsService.bindColumn} produces
 * (see {@code WizVsService.java:848-870}) and, per case-vsfilt-009's diagnosis docs, the one
 * combination with zero real-execution test coverage anywhere in the repo. Mirrors
 * {@link CrosstabNamedGroupEndToEndTest}'s from-scratch worksheet/sandbox wiring, and adds the
 * "S_"+table selection-mirror table that every {@code AbstractSelectionVSAssembly} query
 * requires (normally built by {@code Viewsheet.createSelectionTables()}, which needs a live
 * {@code AssetRepository} this from-scratch test doesn't have).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TimeSliderVSAQueryNumericRangeTest {
   @Test
   void numericTimeSliderResolvesRealMinMaxRange() throws Exception {
      Worksheet ws = new Worksheet();
      Object[][] rows = new Object[][] {
         { "DISCOUNT" },
         { 0.00 }, { 0.05 }, { 0.10 }, { 0.15 }, { 0.20 },
      };

      buildEmbeddedTable(ws, "Query1", rows, true);
      // the "S_"+table selection-mirror table every AbstractSelectionVSAssembly query is bound
      // against -- normally created by Viewsheet.createSelectionTables()/resetWS()
      buildEmbeddedTable(ws, "S_Query1", rows, false);

      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "RangeSlider1");
      slider.setTableName("Query1");

      ColumnRef discountRef = new ColumnRef(new AttributeRef("DISCOUNT"));
      discountRef.setDataType(XSchema.DOUBLE);
      SingleTimeInfo tinfo = new SingleTimeInfo();
      tinfo.setDataRef(discountRef);
      tinfo.setRangeTypeValue(TimeInfo.NUMBER);
      slider.setTimeInfo(tinfo);
      vs.addAssembly(slider);

      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(box, wbox);

      Object data = new TimeSliderVSAQuery(box, "RangeSlider1").getData();

      assertNotNull(data, "getData() must resolve a real min/max range for a populated " +
         "numeric column, not silently return null");
      Object[] range = (Object[]) data;
      assertEquals(0.00, ((Number) range[0]).doubleValue(), 0.0001, "min");
      assertEquals(0.20, ((Number) range[1]).doubleValue(), 0.0001, "max");
   }

   /**
    * VSFILT-009 (round 2): a numeric TimeSlider's {@code SingleTimeInfo.rangeSize} is persisted
    * the first time it successfully computes a tick step (see
    * {@code TimeSliderVSAQuery.refreshSingleSelectionValue}'s NUMBER branch). If the column's
    * live data range later shrinks well below that persisted step size -- e.g. after a reload,
    * once the bound column's real range no longer resembles the one that produced the original
    * step -- the "only grow, never shrink" comparison at that branch's
    * {@code if(rsize0 > tinfo.getRangeSizeValue())} check always keeps the stale, oversized
    * step, collapsing {@code getPreferredTicks} down to its 2-point (min/max only) fallback --
    * a slider with no real granularity to drag, i.e. exactly the reported "doesn't filter"
    * symptom.
    */
   @Test
   void staleRangeSizeIsRecomputedWhenDataRangeShrinks() throws Exception {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, "RangeSlider1");
      slider.setTableName("Query1");

      ColumnRef discountRef = new ColumnRef(new AttributeRef("DISCOUNT"));
      discountRef.setDataType(XSchema.DOUBLE);
      SingleTimeInfo tinfo = new SingleTimeInfo();
      tinfo.setDataRef(discountRef);
      tinfo.setRangeTypeValue(TimeInfo.NUMBER);
      slider.setTimeInfo(tinfo);
      vs.addAssembly(slider);

      // Pass 1: a wide range lets the NUMBER branch auto-compute and persist a rangeSize
      // (SingleTimeInfo.rangeSizeValue, the "D" value) sized for 0..20 -- this is what a real
      // control does the first time it successfully renders.
      Object[][] wideRows = new Object[][] {
         { "DISCOUNT" },
         { 0.0 }, { 5.0 }, { 10.0 }, { 15.0 }, { 20.0 },
      };
      runOnePass(vs, wideRows);
      assertTrue(slider.getSelectionList().getSelectionValueCount() > 0,
         "sanity: the first pass over the wide range must itself produce a non-empty " +
         "selection list");
      double persistedRangeSize = tinfo.getRangeSizeValue();
      assertTrue(persistedRangeSize > 0, "first pass must persist a non-zero rangeSize");

      // Pass 2: simulates a reload -- reuses the same assembly/tinfo, so the persisted
      // rangeSize carries over exactly as SingleTimeInfo's D-value/R-value split makes it
      // across a real save/reopen -- but the column's live data range has since shrunk well
      // below the persisted step size.
      Object[][] narrowRows = new Object[][] {
         { "DISCOUNT" },
         { 0.00 }, { 0.05 }, { 0.10 }, { 0.15 }, { 0.20 },
      };
      runOnePass(vs, narrowRows);

      // With the bug, the persisted (too-large) rangeSize is kept unconditionally, so
      // getPreferredTicks collapses to its 2-point (min/max only) fallback -- no usable
      // granularity to drag. A correctly-recomputed step for a 0..0.20 range should produce
      // several intermediate ticks.
      assertTrue(slider.getSelectionList().getSelectionValueCount() > 2,
         "a stale persisted rangeSize (" + persistedRangeSize + ") from a wider prior data " +
         "range must not be kept once the live data range has shrunk below it -- got only " +
         slider.getSelectionList().getSelectionValueCount() + " selection value(s)");
   }

   private static void runOnePass(Viewsheet vs, Object[][] rows) throws Exception {
      Worksheet ws = new Worksheet();
      buildEmbeddedTable(ws, "Query1", rows, true);
      buildEmbeddedTable(ws, "S_Query1", rows, false);

      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(box, wbox);

      TimeSliderVSAQuery query = new TimeSliderVSAQuery(box, "RangeSlider1");
      Object data = query.getData();
      assertNotNull(data, "getData() must resolve a real min/max range for a populated " +
         "numeric column, not silently return null");
      query.refreshSelectionValue(data);
   }

   private static void buildEmbeddedTable(
      Worksheet ws, String name, Object[][] rows, boolean visible)
   {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cs = new ColumnSelection();
      ColumnRef discountCol = new ColumnRef(new AttributeRef("DISCOUNT"));
      discountCol.setDataType(XSchema.DOUBLE);
      cs.addAttribute(discountCol);
      table.setColumnSelection(cs, false);
      table.setEmbeddedData(new XEmbeddedTable(new String[]{ "double" }, rows));
      table.setVisible(visible);
      ws.addAssembly(table);
   }
}
