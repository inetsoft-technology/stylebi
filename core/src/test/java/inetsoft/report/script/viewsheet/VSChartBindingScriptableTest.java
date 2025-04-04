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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@SreeHome()
public class VSChartBindingScriptableTest {
   @Mock
   ViewsheetSandbox viewsheetSandbox;

   private ChartVSAScriptable chartVSAScriptable;

   private VSChartBindingScriptable vsChartBindingScriptable;

   private ChartVSAssemblyInfo chartVSAssemblyInfo;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs-chart-binding-1");

      ChartVSAssembly chartVSAssembly = new ChartVSAssembly();
      chartVSAssemblyInfo = (ChartVSAssemblyInfo) chartVSAssembly.getVSAssemblyInfo();
      chartVSAssemblyInfo.setName("chart1");
      viewsheet.addAssembly(chartVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs-chart-binding-1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      chartVSAScriptable = new ChartVSAScriptable(viewsheetSandbox);
      chartVSAScriptable.setAssembly("chart1");
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);
   }

   @Test
   void testSetFieldsBinding() {
      Object[][] xf1 = {{ "state", ChartConstants.STRING},
                        {"date", ChartConstants.DATE},
                        { "product_id", ChartConstants.NUMBER }};
      Object[][] yf1 = {{"company", ChartConstants.STRING},
                        {"customer_id", ChartConstants.NUMBER}};
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_BAR);

      vsChartBindingScriptable.setXFields(xf1);
      vsChartBindingScriptable.setYFields(yf1);

      assert vsChartBindingScriptable.getXFields().length == 3;
      assert vsChartBindingScriptable.getYFields().length == 2;

      VSAestheticRef ref1 = (VSAestheticRef)vsChartBindingScriptable
         .createAestheticRef("order_date", ChartConstants.DATE);

      vsChartBindingScriptable.setColorField(ref1, null, ChartConstants.DATE);
      assert Objects.equals(vsChartBindingScriptable.getColorField("order_date").getName(), "order_date");

      VSAestheticRef ref2 = (VSAestheticRef)vsChartBindingScriptable
         .createAestheticRef("id", ChartConstants.NUMBER);
      vsChartBindingScriptable.setShapeField(ref2, null, ChartConstants.NUMBER);
      assert Objects.equals(vsChartBindingScriptable.getShapeField("id").getName(), "id");

      vsChartBindingScriptable.setSizeField(ref1, null, ChartConstants.DATE);
      assert Objects.equals(vsChartBindingScriptable.getSizeField("order_date").getName(), "order_date");

      vsChartBindingScriptable.setTextField(ref2, null, ChartConstants.NUMBER);
      assert Objects.equals(vsChartBindingScriptable.getTextField("id").getName(), "id");
   }

   /**
    * test geo maping binding, unfinished.
    */
   @Test
   void testGeoMapBinding() {
      Object[][] geof1 = { {"state", ChartConstants.STRING}};
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_MAP);
      chartVSAssemblyInfo.setMapType("U.S.");

      ColumnSelection columnSelection = new ColumnSelection();
      VSChartGeoRef ref = new VSChartGeoRef();
      ref.setGroupColumnValue("state");
      columnSelection.addAttribute(ref);
      VSMapInfo vsMapInfo = (VSMapInfo)vsChartBindingScriptable.getInfo();
      vsMapInfo.setGeoColumns(columnSelection);

      vsChartBindingScriptable.setGeoFields(geof1);
      assert vsChartBindingScriptable.getGeoFields().length == 1;

      vsMapInfo.addRTGeoField(ref);
      vsChartBindingScriptable.setMapLayer("state", ChartConstants.STATE);
      assert Objects.equals(vsChartBindingScriptable.getMapLayer("state"), ChartConstants.STATE);

      //todo
      /*vsChartBindingScriptable.addMapping("state","s1","state0113");
      assert vsChartBindingScriptable.getMappings("state").length == 1;
      vsChartBindingScriptable.removeMapping("state", "state0113");
      assert vsChartBindingScriptable.getMappings("state").length == 0;
      */

   }
}
