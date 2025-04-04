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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.LineElement;
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.*;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mozilla.javascript.ScriptableObject;

import java.awt.*;
import java.security.Principal;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@SreeHome(importResources = "ChartVSAScriptableTest.vso")
public class ChartVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;

   private ChartVSAScriptable chartVSAScriptable;

   private ChartVSAssemblyInfo chartVSAssemblyInfo;

   @Mock
   ViewsheetService viewsheetService;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs-chart-1");

      ChartVSAssembly  chartVSAssembly = new ChartVSAssembly();
      chartVSAssemblyInfo = (ChartVSAssemblyInfo) chartVSAssembly.getVSAssemblyInfo();
      chartVSAssemblyInfo.setName("chart1");

      viewsheet.addAssembly(chartVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs-chart-1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      chartVSAScriptable = new ChartVSAScriptable(viewsheetSandbox);
      chartVSAScriptable.setAssembly("chart1");
   }
   @Test
   void testAddProperties() throws Exception {
      // Call the addProperties method on the chartVSAScriptable object
      chartVSAScriptable.addProperties();

      // Assert that the "combinedTooltip" property is of type Boolean
      assert chartVSAScriptable.get("combinedTooltip", null) instanceof Boolean;
      // Create an array of keys to check
      String[] keys = {"xAxis", "yAxis", "y2Axis"};
      // Loop through the keys and assert that each property is of type AxisScriptable
      for (String key : keys) {
         assert chartVSAScriptable.get(key, null) instanceof AxisScriptable;
      }
      // Assert that the "axis" property is of type ChartArray
      assert chartVSAScriptable.get("axis", null) instanceof ChartArray;
      // Assert that the "bindingInfo" property is of type BindingInfo (commented out)
      //assert chartVSAScriptable.get("bindingInfo") instanceof BindingInfo;
      // Assert that the "singleStyle" property is of type VSChartArray
      assert chartVSAScriptable.get("singleStyle", null) instanceof VSChartArray;
      // Assert that the "dateComparisonEnabled" property is of type Boolean
      assert chartVSAScriptable.get("dateComparisonEnabled", null) instanceof Boolean;
   }

   @Test
   void testPutProperties() {
      EGraph graph = new EGraph();
      LineElement elem = new LineElement("State", "Quantity");
      graph.addElement(elem);
      chartVSAScriptable.put("graph", null, graph);
      assert chartVSAScriptable.get("graph", null) instanceof EGraph;
      //put data
      Object[][] data1 = {{"State", "Quantity"}, {"NJ", 200}, {"NY", 300}};
      DefaultDataSet dataSet1 = new DefaultDataSet(data1);
      chartVSAScriptable.put("data", null, dataSet1);
      assert chartVSAScriptable.get("data", null) instanceof TableArray;

      //put dataset
      Object[][] data2 = {{"CA", 200, false}, {"NJ", 3000, true}, {"NY", 2000, true}};
      chartVSAScriptable.put("dataset", null, data2);
      assert chartVSAScriptable.get("dataset", null) instanceof DefaultDataSet;

      //put query
      chartVSAScriptable.put("query", null, "q1");
      assert  chartVSAScriptable.get("query", null) == "q1";

      //put chart style
      chartVSAScriptable.put("chartStyle", null, StyleConstants.CHART_LINE);
      assert chartVSAssemblyInfo.getChartStyle() == StyleConstants.CHART_LINE;
   }

   @Test
   void testSetFont() {
      //Set font with valid font object
      Font font1 = new Font("Arial", Font.PLAIN, 12);
      chartVSAScriptable.setFont(font1);
      assert chartVSAScriptable.getFont().equals(font1);

      chartVSAScriptable.setForeground(Color.BLUE);
      assert chartVSAScriptable.getForeground().equals(Color.BLUE);

      chartVSAScriptable.setTipViewValue("this is a tip view value");
      chartVSAScriptable.setTipView("this is a tip view");
      assert Objects.equals(chartVSAScriptable.getTipView(), "this is a tip view");
   }


   /**
    * use a imported vs to test get all binding options
    */
   @Test
   void testGetBindingField() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox();
      Principal principal = mock(Principal.class);
      when(viewsheetService.getViewsheet(viewsheetResource.getRuntimeId(), principal))
         .thenReturn(viewsheetResource.getRuntimeViewsheet());

      //check x,y,color,shape,text,size fields on bar chart
      final ChartVSAssembly Chart1 = (ChartVSAssembly) viewsheetResource
         .getRuntimeViewsheet().getViewsheet().getAssembly("Chart1");
      ChartVSAScriptable chartVSAScriptable1 = new ChartVSAScriptable(sandbox);
      chartVSAScriptable1.setAssembly(Chart1.getName());

      String[] x1 = (String[]) chartVSAScriptable1.get("xFields", null);
      String[] y1 = (String[]) chartVSAScriptable1.get("yFields", null);
      assert Arrays.toString(x1).equals("[State]");
      assert Arrays.toString(y1).equals("[Population]");
      assert chartVSAScriptable1.get("colorField").equals("Region");
      assert chartVSAScriptable1.get("shapeField").equals("Division");
      assert chartVSAScriptable1.get("sizeField").equals("Median Income");
      assert chartVSAScriptable1.get("textField").equals("Property Value");
      assert chartVSAScriptable1.get("data") != null;
      assert chartVSAScriptable1.get("query").equals("Data");

      //check getSuffix
      String[] items = {"xFields", "yFields", "axis", "singleStyle"};
      for (String item : items) {
         assert chartVSAScriptable1.getSuffix(item).equals("[]");;
      }
      assert chartVSAScriptable1.getSuffix("highlighted").equals("");
      assert chartVSAScriptable1.getSuffix("webMapStyle").equals("");
      assert chartVSAScriptable1.getSuffix("setHyperlink").equals("()");

      //check get others
      assert chartVSAScriptable1.getAxisIds().length != 0;
      assert chartVSAScriptable1.getFieldAxisIds().length != 0;
      assert chartVSAScriptable1.getLegendIds().length != 0;
      assert chartVSAScriptable1.getTitleIds().length != 0;
      assert chartVSAScriptable1.getValueFormatIds().length != 0;
      assert chartVSAScriptable1.getEGraphIds().length != 0;

      //check geo field
      final ChartVSAssembly Map = (ChartVSAssembly) viewsheetResource
         .getRuntimeViewsheet().getViewsheet().getAssembly("Map");
      ChartVSAScriptable chartVSAScriptable2 = new ChartVSAScriptable(sandbox);
      chartVSAScriptable2.setAssembly(Map.getName());

      String[] geo = (String[]) chartVSAScriptable2.get("geoFields", null);
      assert Arrays.toString(geo).equals("[State]");
      assert chartVSAScriptable2.getSuffix("geoFields").equals("[]");

      EGraph eGraph = chartVSAScriptable2.getEGraph();
      assert eGraph != null;
      assert eGraph.getElementCount() == 1;
   }

   @Test
   void testSetHyperlinkActionVisible() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox();
      Principal principal = mock(Principal.class);
      when(viewsheetService.getViewsheet(viewsheetResource.getRuntimeId(), principal))
         .thenReturn(viewsheetResource.getRuntimeViewsheet());

      final ChartVSAssembly Chart1 = (ChartVSAssembly) viewsheetResource
         .getRuntimeViewsheet().getViewsheet().getAssembly("Chart1");

      ChartVSAScriptable chartVSAScriptable1 = new ChartVSAScriptable(sandbox);
      chartVSAScriptable1.setAssembly(Chart1.getName());

      //check set hyperlink
      chartVSAScriptable1.setHyperlink(0, "www.google.com");
      assert chartVSAScriptable1.hasHyperlink();

      //check set action visible
      chartVSAScriptable1.setActionVisible("Color Legend", true);
      chartVSAScriptable1.setActionVisible("Shape Legend", false);
      chartVSAScriptable1.setActionVisible("Size Legend", true);
      assert chartVSAScriptable1.isActionVisible("Color Legend");
      assert !chartVSAScriptable1.isActionVisible("Shape Legend");
      assert chartVSAScriptable1.isActionVisible("Size Legend");
   }

   /**
    * check add target line function @TODO
    */
   @Test
   void testAddTargetLine() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox();
      Principal principal = mock(Principal.class);
      when(viewsheetService.getViewsheet(viewsheetResource.getRuntimeId(), principal))
         .thenReturn(viewsheetResource.getRuntimeViewsheet());

      final ChartVSAssembly Chart1 = (ChartVSAssembly) viewsheetResource
         .getRuntimeViewsheet().getViewsheet().getAssembly("Chart1");
      ChartVSAScriptable chartVSAScriptable1 = new ChartVSAScriptable(sandbox);
      chartVSAScriptable1.setAssembly(Chart1.getName());

      //clrs: color
      chartVSAScriptable1.addProperties();
      ScriptableObject targetOptions = new ScriptableObject() {
         @Override
         public String getClassName() {
            return "targetOptions";
         }
      };

      targetOptions.put("lineStyle", targetOptions , GraphConstants.THIN_LINE);
      chartVSAScriptable1.addTargetLine("Sum(Population)", "0xDD99DD", new String[]{"aver"}, targetOptions);

      targetOptions.put("fillAbove",targetOptions , new Color(255, 255, 255));
      targetOptions.put("fillBelow",targetOptions , new Color(255, 255, 255));
      targetOptions.put("label",targetOptions , "{1},{1}");
      targetOptions.put("lineColor",targetOptions , new Color(255, 0, 0));
      targetOptions.put("lineStyle",targetOptions , GraphConstants.THICK_LINE);
      chartVSAScriptable1.addTargetBand("Sum(Population)", "0xDD99DD", new String[]{"min", "max"}, targetOptions);
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 2;

      chartVSAScriptable1.clearTargets();
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 0;
      chartVSAScriptable1.addConfidenceIntervalTarget("Sum(Population)","0xDDAAAA", 99, targetOptions);
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 1;

      chartVSAScriptable1.clearTargets();
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 0;
      chartVSAScriptable1.addPercentageTarget("Sum(Population)","null", 125, targetOptions);
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 1;

      chartVSAScriptable1.clearTargets();
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 0;
      chartVSAScriptable1.addPercentileTarget("Sum(Population)","null", 95, targetOptions);
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 1;

      chartVSAScriptable1.clearTargets();
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 0;
      chartVSAScriptable1.addQuantileTarget("Sum(Population)",new String[] {"0xDDAAAA","0xDDCCCC"},
                                            4, targetOptions);
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 1;

      chartVSAScriptable1.clearTargets();
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 0;
      chartVSAScriptable1.addStandardDeviationTarget("Sum(Population)",new String[] {"0xDDCCCC","0xDDAAAA","0xDDCCCC"},
                                                     new Object[] {-1,1,-2,2}, targetOptions);
      assert chartVSAScriptable1.getRTChartDescriptor().getTargetCount() == 1;
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);

      return event;
   }
   public static final String ASSET_ID = "1^128^__NULL__^ChartVSAScriptableTest";

   @RegisterExtension
   @Order(1)
   ControllersExtension controllers = new ControllersExtension();

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(), controllers);
}
