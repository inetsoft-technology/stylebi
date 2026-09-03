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
