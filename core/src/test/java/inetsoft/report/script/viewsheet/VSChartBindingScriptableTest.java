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

import inetsoft.graph.aesthetic.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SreeHome()
public class VSChartBindingScriptableTest {
   private ViewsheetSandbox viewsheetSandbox;

   private ChartVSAScriptable chartVSAScriptable;

   private VSChartBindingScriptable vsChartBindingScriptable;

   private ChartVSAssemblyInfo chartVSAssemblyInfo;

   @BeforeEach
   void setUp() {
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
      Object[][] xf1 = { { "state", ChartConstants.STRING },
                         { "date", ChartConstants.DATE },
                         { "product_id", ChartConstants.NUMBER } };
      Object[][] yf1 = { { "company", ChartConstants.STRING },
                         { "customer_id", ChartConstants.NUMBER } };
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_BAR);

      vsChartBindingScriptable.setXFields(xf1);
      vsChartBindingScriptable.setYFields(yf1);

      assertEquals(3, vsChartBindingScriptable.getXFields().length);
      assertEquals(2, vsChartBindingScriptable.getYFields().length);

      VSAestheticRef ref1 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("order_date", ChartConstants.DATE);

      vsChartBindingScriptable.setColorField(ref1, null, ChartConstants.DATE);
      assertEquals("order_date",
                   vsChartBindingScriptable.getColorField("order_date").getName());

      VSAestheticRef ref2 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("id", ChartConstants.NUMBER);
      vsChartBindingScriptable.setShapeField(ref2, null, ChartConstants.NUMBER);
      assertEquals("id", vsChartBindingScriptable.getShapeField("id").getName());

      vsChartBindingScriptable.setSizeField(ref1, null, ChartConstants.DATE);
      assertEquals("order_date",
                   vsChartBindingScriptable.getSizeField("order_date").getName());
   }

   @Test
   void testSetGetTextField() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      // test set text field base on a null
      VSAestheticRef ref2 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("id2", ChartConstants.NUMBER);
      vsChartBindingScriptable.setTextField(ref2, null, ChartConstants.NUMBER);
      assertEquals("id2", vsChartBindingScriptable.getTextField("id2").getName());

      // test set text field base on a ref
      vsChartBindingScriptable.setTextField("id",  ref2, ChartConstants.STRING);
      assertEquals("id2", vsChartBindingScriptable.getTextField("id").getName());

      Object[] xField = { "state", ChartConstants.STRING } ;
      Object[] yField = { "id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setYFields(new Object[] { yField });

      // test set text field base on a string
      vsChartBindingScriptable.setTextField("id",  "state", ChartConstants.STRING);
      assertEquals("state", vsChartBindingScriptable.getTextField("id").getName());
   }

   @Test
   void testSetTopNSizeFrame() {
      VSDimensionRef ref1 = new VSDimensionRef(new BaseField("state"));
      vsChartBindingScriptable.setTopN(ref1, 2);
      assertEquals(2, vsChartBindingScriptable.getTopN(ref1));
      vsChartBindingScriptable.setTopNSummaryCol(ref1, "Sum(Customer_id)");
      assertEquals("Sum(Customer_id)", vsChartBindingScriptable.getTopNSummaryCol(ref1));
      vsChartBindingScriptable.setTopNReverse(ref1, true);
      assertTrue(vsChartBindingScriptable.isTopNReverse(ref1));

      //set static size frame
      StaticSizeFrame sizeFrame = new StaticSizeFrame();
      vsChartBindingScriptable.setSizeFrame(sizeFrame);
      assertEquals(sizeFrame, vsChartBindingScriptable.getSizeFrame());
      assertTrue(vsChartBindingScriptable.getInfo().getSizeFrameWrapper().isChanged());

      //set categorical size frame
      CategoricalSizeFrame categoricalSizeFrame = new CategoricalSizeFrame();
      vsChartBindingScriptable.setSizeFrame(categoricalSizeFrame);
      assertEquals(categoricalSizeFrame, vsChartBindingScriptable.getSizeFrame());
      assertFalse(vsChartBindingScriptable.getInfo().getSizeFrameWrapper().isChanged());
   }

   /**
    * test geo maping binding, unfinished.
    */
   @Test
   void testGeoMapBinding() {
      Object[][] geof1 = { { "state", ChartConstants.STRING } };
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_MAP);
      chartVSAssemblyInfo.setMapType("U.S.");

      ColumnSelection columnSelection = new ColumnSelection();
      VSChartGeoRef ref = new VSChartGeoRef();
      ref.setGroupColumnValue("state");
      columnSelection.addAttribute(ref);

      VSMapInfo vsMapInfo = (VSMapInfo) vsChartBindingScriptable.getInfo();
      vsMapInfo.setGeoColumns(columnSelection);
      vsMapInfo.setChartType(GraphTypes.CHART_MAP);
      vsMapInfo.addRTGeoField(ref);

      vsChartBindingScriptable.setGeoFields(geof1);
      assertArrayEquals(new Object[] {"state"}, vsChartBindingScriptable.getGeoFields());

      vsChartBindingScriptable.setMapLayer("state", ChartConstants.STATE);
      assertEquals(ChartConstants.STATE, vsChartBindingScriptable.getMapLayer("state"));

      vsChartBindingScriptable.addMapping("state", "I", "Idaho");
      assertArrayEquals(new Object[] {}, vsChartBindingScriptable.getMappings("state"));

      vsChartBindingScriptable.removeMapping("state", "I");
   }

   @Test
   void testSetGetBreakdownFields() {
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      Object[][] bkField = { { "state", ChartConstants.STRING },
                             { "date", ChartConstants.DATE },
                             { "product_id", ChartConstants.NUMBER } };

      vsChartBindingScriptable.setBreakdownFields(bkField);
      assertArrayEquals(new Object[] {"state", "date", "product_id"}, vsChartBindingScriptable.getBreakdownFields());
   }

   /**
    * Test for set/get breakdown fields
    */
   @Test
   void testSetGetPathField() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      Object[] pathField = { "state", ChartConstants.STRING } ;
      vsChartBindingScriptable.setPathField(pathField);
      assertEquals( "state", vsChartBindingScriptable.getPathField());

      Object[] pathField2 = { "date", ChartConstants.DATE};
      vsChartBindingScriptable.setPathField(pathField2);
      assertEquals( "date", vsChartBindingScriptable.getPathField());

      Object[] pathField3 = { "product_id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setPathField(pathField3);
      assertEquals( "product_id", vsChartBindingScriptable.getPathField());
   }

   @Test
   void testSetGetCandleBindingField() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_CANDLE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      Object[][] candleFields = {
         { "high", ChartConstants.HIGH, ChartConstants.SUM_FORMULA, "Sum(high)" },
         { "low", ChartConstants.LOW, ChartConstants.AVERAGE_FORMULA, "Average(low)" },
         { "open", ChartConstants.OPEN, ChartConstants.SUM_FORMULA, "Sum(open)" },
         { "close", ChartConstants.CLOSE, ChartConstants.COUNT_FORMULA, "Count(close)" }
      };

      for (Object[] field : candleFields) {
         vsChartBindingScriptable.setCandleBindingField(Arrays.copyOf(field, 3));
         assertEquals(field[3], vsChartBindingScriptable.getCandleBindingField((int)field[1]).getFullName());
      }
   }

   @Test
   void testSeGetStockBindingField() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_STOCK);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      Object[][] stockFields = {
         { "high", ChartConstants.HIGH, ChartConstants.SUM_FORMULA, "Sum(high)" },
         { "low", ChartConstants.LOW, ChartConstants.AVERAGE_FORMULA, "Average(low)" },
         { "open", ChartConstants.OPEN, ChartConstants.SUM_FORMULA, "Sum(open)" },
         { "close", ChartConstants.CLOSE, ChartConstants.COUNT_FORMULA, "Count(close)" }
      };

      for (Object[] field : stockFields) {
         vsChartBindingScriptable.setStockBindingField(Arrays.copyOf(field, 3));
         assertEquals(field[3], vsChartBindingScriptable.getStockBindingField((int)field[1]).getFullName());
      }
   }

   @Test
   void testSetgetColorFrames() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_BAR);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);
      CategoricalColorFrame categoricalColorFrame = new CategoricalColorFrame();

      //check no color field,  and hav agg field
      Object[] yField = { "id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setYFields(new Object[] { yField });
      vsChartBindingScriptable.setColorFrame(categoricalColorFrame);
      assertEquals(categoricalColorFrame, vsChartBindingScriptable.getColorFrame());

      //check no X|Y field, only color field with Dim ref
      VSAestheticRef colorRef = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("order_date", ChartConstants.DATE);
      vsChartBindingScriptable.setColorField(colorRef, null, ChartConstants.DATE);

      HeatColorFrame heatColorFrame = new HeatColorFrame();
      vsChartBindingScriptable.setColorFrame(heatColorFrame);
      assertEquals(heatColorFrame, vsChartBindingScriptable.getColorFrame());
   }


   @Test
   void testSetGetShapeFrame() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_POINT);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);
      StaticShapeFrame starShapeFrame = new StaticShapeFrame();

      // check no agg field
      VSAestheticRef shapeRef = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("id", ChartConstants.NUMBER);
      vsChartBindingScriptable.setShapeField(shapeRef, null, ChartConstants.NUMBER);
      vsChartBindingScriptable.setShapeFrame(starShapeFrame);
      assertEquals(starShapeFrame, vsChartBindingScriptable.getShapeFrame());

      //check no shape field, but have aggregate field
      Object[] xField = { "product_id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[]{ xField });
      vsChartBindingScriptable.setShapeFrame(starShapeFrame);
      assertEquals(starShapeFrame, vsChartBindingScriptable.getShapeFrame());
   }

   @Test
   void testSetGetLineFrameWithAggField() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);
      StaticLineFrame staticLineFrame = new StaticLineFrame();

      //check no shape field, but have aggregate field
      Object[] xField = { "product_id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[]{ xField });
      vsChartBindingScriptable.setLineFrame(staticLineFrame);
      assertEquals(staticLineFrame, vsChartBindingScriptable.getLineFrame());
   }

   @Test
   void testSetGetLineFrameWithShapeField() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);
      StaticLineFrame staticLineFrame = new StaticLineFrame();

      // check have shape field
      VSAestheticRef shapeRef = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("id", ChartConstants.NUMBER);
      vsChartBindingScriptable.setShapeField(shapeRef, null, ChartConstants.NUMBER);
      vsChartBindingScriptable.setLineFrame(staticLineFrame);
      assertEquals(staticLineFrame, vsChartBindingScriptable.getLineFrame());
   }

   @Test
   void testSetGetTextureFrameWithAggField() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);
      StaticTextureFrame staticTextureFrame = new StaticTextureFrame();

      //check no shape field, but have aggregate field
      Object[] xField = { "product_id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[]{ xField });
      vsChartBindingScriptable.setTextureFrame(staticTextureFrame);
      assertEquals(staticTextureFrame, vsChartBindingScriptable.getTextureFrame());
   }

   @Test
   void testSetGetTextureFrameWithShapeField() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_BAR);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);
      StaticTextureFrame staticTextureFrame = new StaticTextureFrame();

      // check have shape field
      VSAestheticRef shapeRef = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("id", ChartConstants.NUMBER);
      vsChartBindingScriptable.setShapeField(shapeRef, null, ChartConstants.NUMBER);
      vsChartBindingScriptable.setTextureFrame(staticTextureFrame);
      assertEquals(staticTextureFrame, vsChartBindingScriptable.getTextureFrame());
   }
   @Test
   void testSetFormula() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      //check other field
      vsChartBindingScriptable.setPathField(new Object[] { "id", ChartConstants.NUMBER });
      vsChartBindingScriptable.setFormula("id", ChartConstants.COUNT_FORMULA, 0);
      assertEquals("Count", vsChartBindingScriptable.getFormula("id", 0));

      //check binding field
      Object[] xField = { "id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setFormula("id", ChartConstants.SUM_FORMULA, ChartConstants.BINDING_FIELD);
      assertEquals("Sum", vsChartBindingScriptable.getFormula("id", ChartConstants.BINDING_FIELD));

      //check AESTHETIC_SHAPE
      VSAestheticRef shapeRef = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("id2", ChartConstants.NUMBER);
      vsChartBindingScriptable.setShapeField(shapeRef, null, ChartConstants.NUMBER);
      vsChartBindingScriptable.setFormula("id2", ChartConstants.AVERAGE_FORMULA, ChartConstants.AESTHETIC_SHAPE);
      assertEquals("Average", vsChartBindingScriptable.getFormula("id2", ChartConstants.AESTHETIC_SHAPE));
   }

   @Test
   void testSetGetSecondaryField() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      //test set secondary field on other fields
      vsChartBindingScriptable.setPathField(new Object[] { "id", ChartConstants.NUMBER });
      vsChartBindingScriptable.setSecondaryField("id", "id2", 0);
      assertEquals("id2", vsChartBindingScriptable.getSecondaryField("id", 0));

      //test set secondary field on BINDING_FIELD
      Object[] xField = { "id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setSecondaryField("id", "id2", ChartConstants.BINDING_FIELD);
      assertEquals("id2", vsChartBindingScriptable.getSecondaryField("id", ChartConstants.BINDING_FIELD));

      //test set secondary field on AESTHETIC_SHAPE
      VSAestheticRef shapeRef = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("id", ChartConstants.NUMBER);
      vsChartBindingScriptable.setShapeField(shapeRef, null, ChartConstants.NUMBER);
      vsChartBindingScriptable.setSecondaryField("id", "id3", ChartConstants.AESTHETIC_SHAPE);
      assertEquals("id3", vsChartBindingScriptable.getSecondaryField("id", ChartConstants.AESTHETIC_SHAPE));
   }

   @Test
   void testSetGetPercentageType() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      //test set percentage type on other fields
      vsChartBindingScriptable.setPathField(new Object[] { "id", ChartConstants.NUMBER });
      vsChartBindingScriptable.setPercentageType("id", ChartConstants.PERCENTAGE_OF_GROUP, 0);
      assertEquals(ChartConstants.PERCENTAGE_OF_GROUP, vsChartBindingScriptable.getPercentageType("id", 0));

      //test set percentage type on BINDING_FIELD
      Object[] xField = { "id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setPercentageType("id", ChartConstants.PERCENTAGE_OF_GROUP, ChartConstants.BINDING_FIELD);

      assertEquals(ChartConstants.PERCENTAGE_OF_GROUP, vsChartBindingScriptable.getPercentageType("id", ChartConstants.BINDING_FIELD));

      //test set percentage type on AESTHETIC_SHAPE
      VSAestheticRef ref1 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("id2", ChartConstants.NUMBER);
      vsChartBindingScriptable.setColorField(ref1, null, ChartConstants.NUMBER);
      vsChartBindingScriptable.setPercentageType("id2", ChartConstants.PERCENTAGE_OF_GRANDTOTAL, ChartConstants.AESTHETIC_COLOR);

      assertEquals(ChartConstants.PERCENTAGE_BY_ROW, vsChartBindingScriptable.getPercentageType("id2", ChartConstants.AESTHETIC_COLOR));
   }

   @Test
   void testSetIsDiscrete() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      //check discrete on other fields
      vsChartBindingScriptable.setPathField(new Object[] { "id", ChartConstants.NUMBER });
      vsChartBindingScriptable.setDiscrete("id", true, 0);
      assertTrue(vsChartBindingScriptable.isDiscrete("id",0));

      //check discrete on BINDING_FIELD
      Object[] xField = { "id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setDiscrete("id", true, ChartConstants.BINDING_FIELD);
      assertTrue(vsChartBindingScriptable.isDiscrete("id", ChartConstants.BINDING_FIELD));

      //check discrete on AESTHETIC_COLOR
      VSAestheticRef ref1 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("id2", ChartConstants.NUMBER);
      vsChartBindingScriptable.setColorField(ref1, null, ChartConstants.NUMBER);
      vsChartBindingScriptable.setDiscrete("id2", true, ChartConstants.AESTHETIC_COLOR);
      assertTrue(vsChartBindingScriptable.isDiscrete("id2", ChartConstants.AESTHETIC_COLOR));
   }

   @Test
   void testSetGetTopN() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      //check topN on other fields
      vsChartBindingScriptable.setPathField(new Object[] { "name", ChartConstants.STRING });
      vsChartBindingScriptable.setTopN("name", 2, 0);
      assertEquals(2, vsChartBindingScriptable.getTopN("name", 0));

      //check  topN on BINDING_FIELD
      Object[] xField = { "state", ChartConstants.STRING } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setTopN("state", 3, ChartConstants.BINDING_FIELD);
      assertEquals(3, vsChartBindingScriptable.getTopN("state", ChartConstants.BINDING_FIELD));

      //check topN on AESTHETIC_COLOR
      VSAestheticRef ref1 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("date", ChartConstants.DATE);
      vsChartBindingScriptable.setColorField(ref1, null, ChartConstants.DATE);
      vsChartBindingScriptable.setTopN("date", 1, ChartConstants.AESTHETIC_COLOR);
      assertEquals(1, vsChartBindingScriptable.getTopN("date", ChartConstants.AESTHETIC_COLOR));
   }

   @Test
   void testSetGetTopNSummaryCol() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      //check topN summary column on other fields
      vsChartBindingScriptable.setPathField(new Object[] { "city", ChartConstants.STRING });

      vsChartBindingScriptable.setTopNSummaryCol("city", "id1", 0);
      assertEquals("id1", vsChartBindingScriptable.getTopNSummaryCol("city", 0));

      //check topN summary column on BINDING_FIELD
      Object[] xField = { "state", ChartConstants.STRING } ;
      Object[] yField = { "id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setYFields(new Object[] { yField });
      vsChartBindingScriptable.setTopNSummaryCol("state", "id", ChartConstants.BINDING_FIELD);

      assertEquals("id", vsChartBindingScriptable.getTopNSummaryCol("state", ChartConstants.BINDING_FIELD));

      //check topN summary column on AESTHETIC_COLOR
      VSAestheticRef ref1 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("date", ChartConstants.DATE);
      vsChartBindingScriptable.setColorField(ref1, null, ChartConstants.DATE);
      vsChartBindingScriptable.setTopNSummaryCol("date", "id", ChartConstants.AESTHETIC_COLOR);

      assertEquals("id", vsChartBindingScriptable.getTopNSummaryCol("date", ChartConstants.AESTHETIC_COLOR));
   }

   /**
    * test setTopNReverse, unfinished, need search
    */
   @Test
   void testSetGetTopNReverse() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      //test setTopNReverse on other fields
      vsChartBindingScriptable.setPathField(new Object[] { "city", ChartConstants.STRING });
      vsChartBindingScriptable.setTopNReverse("city", true, 0);
      assertFalse(vsChartBindingScriptable.isTopNReverse("city", 0));

      //test setTopNReverse on BINDING_FIELD
      Object[] xField = { "state", ChartConstants.STRING } ;
      Object[] yField = { "id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setYFields(new Object[] { yField });
      vsChartBindingScriptable.setTopNReverse("state", true,  ChartConstants.BINDING_FIELD);
      assertTrue(vsChartBindingScriptable.isTopNReverse("state", ChartConstants.BINDING_FIELD));

      // test setTopNReverse on AESTHETIC_COLOR
      VSAestheticRef ref1 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("date", ChartConstants.DATE);
      vsChartBindingScriptable.setColorField(ref1, null, ChartConstants.DATE);
      vsChartBindingScriptable.setTopNReverse("date", true, ChartConstants.AESTHETIC_COLOR);
      assertTrue(vsChartBindingScriptable.isTopNReverse("date", ChartConstants.AESTHETIC_COLOR));
   }

   @Test
   void testSetGetColumnOrder() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      //check setColumnOrder on other fields
      vsChartBindingScriptable.setPathField(new Object[] { "city", ChartConstants.STRING });
      Object[] yField = { "id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setYFields(new Object[] { yField });
      vsChartBindingScriptable.setColumnOrder("city", ChartConstants.SORT_ASC, 0, "Sum(id)");
      assertEquals(ChartConstants.SORT_ASC, vsChartBindingScriptable.getColumnOrder("city", 0));

      //test setColumnOrder on BINDING_FIELD
      Object[] xField = { "state", ChartConstants.STRING } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setColumnOrder("state", ChartConstants.SORT_DESC, ChartConstants.BINDING_FIELD, "Sum(id)");
      assertEquals(ChartConstants.SORT_DESC, vsChartBindingScriptable.getColumnOrder("state", ChartConstants.BINDING_FIELD));

      //test setColumnOrder on AESTHETIC_COLOR
      VSAestheticRef ref1 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("date", ChartConstants.DATE);
      vsChartBindingScriptable.setColorField(ref1, null, ChartConstants.DATE);
      vsChartBindingScriptable.setColumnOrder("date", ChartConstants.SORT_NONE, ChartConstants.AESTHETIC_COLOR, "Sum(id)");
      assertEquals(ChartConstants.SORT_NONE, vsChartBindingScriptable.getColumnOrder("date", ChartConstants.AESTHETIC_COLOR));
   }

   @Test
   void testSetGetGroupOrder() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      //test setGroupOrder on other fields
      vsChartBindingScriptable.setPathField(new Object[] { "date", ChartConstants.DATE });
      vsChartBindingScriptable.setGroupOrder("date", ChartConstants.QUARTER_INTERVAL, 0);
      assertEquals(ChartConstants.YEAR_INTERVAL, vsChartBindingScriptable.getGroupOrder("date", 0));

      //test setGroupOrder on BINDING_FIELD
      Object[] xField = { "date", ChartConstants.DATE } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });
      vsChartBindingScriptable.setGroupOrder("date",  ChartConstants.MONTH_INTERVAL,  ChartConstants.BINDING_FIELD);
      assertEquals(ChartConstants.MONTH_INTERVAL, vsChartBindingScriptable.getGroupOrder("date",  ChartConstants.BINDING_FIELD));

      //test setGroupOrder on AESTHETIC_COLOR
      VSAestheticRef ref1 = (VSAestheticRef) vsChartBindingScriptable
         .createAestheticRef("date", ChartConstants.DATE);
      vsChartBindingScriptable.setColorField(ref1, null, ChartConstants.DATE);
      vsChartBindingScriptable.setGroupOrder("date", ChartConstants.WEEK_INTERVAL, ChartConstants.AESTHETIC_COLOR);
      assertEquals(ChartConstants.WEEK_INTERVAL, vsChartBindingScriptable.getGroupOrder("date", ChartConstants.AESTHETIC_COLOR));
   }

   @Test
   void testSetIsTimeSeries() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      Object[] xField = { "date", ChartConstants.DATE } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });

      vsChartBindingScriptable.setTimeSeries("date", true);
      assertTrue(vsChartBindingScriptable.isTimeSeries("date"));
   }

   @Test
   void testGetIds() {
      chartVSAssemblyInfo.setChartStyle(GraphTypes.CHART_LINE);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      assertEquals(52,  vsChartBindingScriptable.getIds().length );

      assertFalse(vsChartBindingScriptable.has("testPut", null));
      vsChartBindingScriptable.put("testPut", null, "testValue");

      assertEquals("testValue", vsChartBindingScriptable.get("testPut", null));
      assertTrue(vsChartBindingScriptable.has("testPut", null));

      assertNotNull(vsChartBindingScriptable.getType("shapeFrame", null), "The returned type should not be null.");
   }

   @Test
   void testSetGetAllFrameOnMultiStyles() {
      chartVSAssemblyInfo.getVSChartInfo().setMultiStyles(true);
      vsChartBindingScriptable = new VSChartBindingScriptable(chartVSAScriptable);

      Object[] xField = { "product_id", ChartConstants.NUMBER } ;
      vsChartBindingScriptable.setXFields(new Object[] { xField });

      StaticShapeFrame starShapeFrame = new StaticShapeFrame();
      vsChartBindingScriptable.setShapeFrame(starShapeFrame);
      assertEquals(starShapeFrame, vsChartBindingScriptable.getShapeFrame());

      StaticLineFrame staticLineFrame = new StaticLineFrame();
      vsChartBindingScriptable.setLineFrame(staticLineFrame);
      assertEquals(staticLineFrame, vsChartBindingScriptable.getLineFrame());
   }
 }