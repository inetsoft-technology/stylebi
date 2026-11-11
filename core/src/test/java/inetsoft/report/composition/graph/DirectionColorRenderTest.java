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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.CompositeColorFrame;
import inetsoft.graph.aesthetic.DirectionColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.visual.ElementVO;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSChartPaletteDefaults;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked candle, stock or waterfall draws each mark in its direction's colour; every other
 * chart draws as it did.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DirectionColorRenderTest {
   private static final Color UP = new Color(0x1C857B);
   private static final Color DOWN = new Color(0xAD6119);
   private static final Color UP_DARK = new Color(0x3FBFB2);
   private static final Color DOWN_DARK = new Color(0xE8954A);
   private static final Color NEUTRAL = new Color(0x767676);
   private static final Color AUTHOR = new Color(0x00AA00);

   // day, high, close, open, low, region
   private static final Object[] RISE = { "D1", 12.0, 11.0, 10.0, 9.0, "East" };
   private static final Object[] FALL = { "D2", 12.0, 9.5, 11.0, 9.0, "East" };
   private static final Object[] RISE2 = { "D3", 14.0, 13.0, 12.0, 11.0, "West" };

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aModernCandleDrawsUpAndDown() {
      Render r = render(candle(new CandleVSChartInfo(), true), VizMark.MODERN_LIGHT);
      GraphElement elem = r.graph.getElement(0);

      assertEquals(UP, colorAt(r, elem, 0));
      assertEquals(DOWN, colorAt(r, elem, 1));
   }

   @Test
   void aDarkCandleUsesTheDarkTokens() {
      Render r = render(candle(new CandleVSChartInfo(), true), VizMark.MODERN_DARK);
      GraphElement elem = r.graph.getElement(0);

      assertEquals(UP_DARK, colorAt(r, elem, 0));
      assertEquals(DOWN_DARK, colorAt(r, elem, 1));
   }

   @Test
   void aClassicCandleIsUnchanged() {
      Render r = render(candle(new CandleVSChartInfo(), true), null);
      GraphElement elem = r.graph.getElement(0);

      assertEquals(new Color(0x3C698A), colorAt(r, elem, 0));
      assertEquals(new Color(0x3C698A), colorAt(r, elem, 1));
   }

   @Test
   void aColourFieldWins() {
      CandleVSChartInfo cinfo = candle(new CandleVSChartInfo(), true);
      VSAestheticRef color = new VSAestheticRef();
      color.setALLDataRef(dim("Region"));
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      cinfo.setColorField(color);
      Render r = render(cinfo, VizMark.MODERN_LIGHT);
      GraphElement elem = r.graph.getElement(0);

      // a rising and a falling day of one region share the series colour
      Color rise = colorAt(r, elem, 0);
      assertEquals(rise, colorAt(r, elem, 1));
      assertNotEquals(rise, colorAt(r, elem, 2), "premise: two regions, two series colours");
      assertNotEquals(UP, rise);
      assertNotEquals(DOWN, rise);
   }

   @Test
   void anAuthorSingleColourWins() {
      Color[] classic = authorColours(null);
      Color[] modern = authorColours(VizMark.MODERN_LIGHT);

      // a colour the author changed is not darkened, on a classic chart either
      assertEquals(AUTHOR, modern[0]);
      assertEquals(AUTHOR, modern[1]);
      assertArrayEquals(classic, modern);
   }

   @Test
   void aStockWithOpenDrawsDirection() {
      Render r = render(candle(new StockVSChartInfo(), true), VizMark.MODERN_LIGHT);
      GraphElement elem = r.graph.getElement(0);

      assertEquals(UP, colorAt(r, elem, 0));
      assertEquals(DOWN, colorAt(r, elem, 1));
   }

   @Test
   void aStockWithoutOpenDrawsSlotOne() {
      Render r = render(candle(new StockVSChartInfo(), false), VizMark.MODERN_LIGHT);
      GraphElement elem = r.graph.getElement(0);

      assertEquals(new Color(0x036CBF), colorAt(r, elem, 0));
      assertEquals(new Color(0x036CBF), colorAt(r, elem, 1));
   }

   @Test
   void aModernWaterfallDrawsIncreaseDecreaseAndTotal() {
      DefaultVSChartInfo winfo = new DefaultVSChartInfo();
      VSChartAggregateRef value = agg("V");
      winfo.addXField(dim("State"));
      winfo.addYField(value);
      winfo.setChartType(GraphTypes.CHART_WATERFALL);
      winfo.setRTChartType(GraphTypes.CHART_WATERFALL);
      winfo.updateChartType(false);
      Object[][] rows = {
         { "State", GraphUtil.getName(value) },
         { "A", 5.0 },
         { "B", -3.0 },
      };

      Render r = render(winfo, VizMark.MODERN_LIGHT, null, new DefaultDataSet(rows));
      GraphElement bar = element(r, e -> !"true".equals(e.getHint("_waterfall_")));
      GraphElement total = element(r, e -> "true".equals(e.getHint("_waterfall_")));

      assertEquals(UP, colorAt(r, bar, 0));
      assertEquals(DOWN, colorAt(r, bar, 1));
      assertEquals(NEUTRAL, colorAt(r, total, 0));
   }

   @Test
   void brushedRowsKeepTheirDirection() {
      CandleVSChartInfo cinfo = candle(new CandleVSChartInfo(), true);
      DataSet all = new DefaultDataSet(rows(cinfo, RISE, FALL, RISE2));
      DataSet brushed = new DefaultDataSet(rows(cinfo, RISE, FALL));
      Render r = render(cinfo, VizMark.MODERN_LIGHT, all, brushed);
      GraphElement brushedElem = element(r, e -> !e.getVar(0).startsWith(ElementVO.ALL_PREFIX));
      GraphElement allElem = element(r, e -> e.getVar(0).startsWith(ElementVO.ALL_PREFIX));
      assertTrue(hasCompanion(brushedElem.getColorFrame()),
                 "premise: the brushed rows draw through the brushing frame");

      assertEquals(UP, colorAt(r, brushedElem, 0));
      assertEquals(DOWN, colorAt(r, brushedElem, 1));

      // the all-data layer recedes from each candle's own direction
      VizContext ctx = VizContext.of(VizMark.MODERN_LIGHT);
      int brow = ((BrushDataSet) r.gen.getData()).getBaseRowCount();
      assertEquals(VSChartPaletteDefaults.companionOf(UP, ctx), colorAt(r, allElem, brow));
      assertEquals(VSChartPaletteDefaults.companionOf(DOWN, ctx), colorAt(r, allElem, brow + 1));
   }

   @Test
   void theStoredUpAndDownColoursDraw() {
      CandleVSChartInfo cinfo = candle(new CandleVSChartInfo(), true);
      ChartVSAssemblyInfo assemblyInfo = seeded(cinfo, VizMark.MODERN_LIGHT);
      VSChartInfo chart = assemblyInfo.getVSChartInfo();
      chart.getUpColorFrameWrapper().setUserColor(new Color(0x00AA00));
      chart.getDownColorFrameWrapper().setUserColor(new Color(0xAA00AA));
      Render r = render(assemblyInfo, null,
                        new DefaultDataSet(rows(cinfo, RISE, FALL, RISE2)));
      GraphElement elem = r.graph.getElement(0);

      assertEquals(new Color(0x00AA00), colorAt(r, elem, 0));
      assertEquals(new Color(0xAA00AA), colorAt(r, elem, 1));
   }

   @Test
   void aMultiStyleWaterfallWithALineKeepsItsColours() {
      DefaultVSChartInfo winfo = new DefaultVSChartInfo();
      VSChartAggregateRef value = styled(agg("V"), GraphTypes.CHART_WATERFALL);
      VSChartAggregateRef trend = styled(agg("W"), GraphTypes.CHART_LINE);
      winfo.setMultiStyles(true);
      winfo.addXField(dim("State"));
      winfo.addYField(value);
      winfo.addYField(trend);
      Object[][] rows = {
         { "State", name(value), name(trend) },
         { "A", 5.0, 10.0 },
         { "B", -3.0, -20.0 },
      };

      GraphGenerator gen = GraphGenerator.getGenerator(
         seeded(winfo, VizMark.MODERN_LIGHT), null, new DefaultDataSet(rows),
         new VariableTable(), null, XSourceInfo.NONE, null);
      // the frame each measure's element would take, as this binding cannot draw
      ColorFrame frame = gen.getColorFrame(null);

      assertFalse(frame instanceof DirectionColorFrame);

      for(int row = 0; row < 2; row++) {
         // each measure's seeded colour, whatever the sign of its value
         assertEquals(new Color(0x0490FF), frame.getColor(gen.getData(), name(trend), row));
         assertEquals(new Color(0x0490FF), frame.getColor(gen.getData(), name(value), row));
      }

      assertThrows(MessageException.class, gen::createEGraph,
                   "a waterfall cannot share a multi-style chart");
   }

   // ---- harness -----------------------------------------------------------------------------

   private record Render(GraphGenerator gen, EGraph graph) {
   }

   // the rising and falling day of a candle whose author set a single colour
   private static Color[] authorColours(VizMark mark) {
      CandleVSChartInfo cinfo = candle(new CandleVSChartInfo(), true);
      ((StaticColorFrameWrapper) cinfo.getColorFrameWrapper()).setUserColor(AUTHOR);
      Render r = render(cinfo, mark);
      GraphElement elem = r.graph.getElement(0);
      return new Color[] { colorAt(r, elem, 0), colorAt(r, elem, 1) };
   }

   private static <T extends CandleVSChartInfo> T candle(T cinfo, boolean withOpen) {
      VSChartAggregateRef high = agg("High");
      VSChartAggregateRef close = agg("Close");
      VSChartAggregateRef low = agg("Low");
      cinfo.addXField(dim("Day"));
      cinfo.setHighField(high);
      cinfo.setRTHighField(high);
      cinfo.setCloseField(close);
      cinfo.setRTCloseField(close);
      cinfo.setLowField(low);
      cinfo.setRTLowField(low);

      if(withOpen) {
         VSChartAggregateRef open = agg("Open");
         cinfo.setOpenField(open);
         cinfo.setRTOpenField(open);
      }

      int type = cinfo instanceof StockVSChartInfo ? GraphTypes.CHART_STOCK :
         GraphTypes.CHART_CANDLE;
      cinfo.setChartType(type);
      cinfo.setRTChartType(type);
      cinfo.updateChartType(false);
      return cinfo;
   }

   // rows under the headers the candle generator reads
   private static Object[][] rows(CandleVSChartInfo cinfo, Object[]... days) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Day", name(cinfo.getHighField()), name(cinfo.getCloseField()),
                              "Sum(Open)", name(cinfo.getLowField()), "Region" });

      if(cinfo.getOpenField() != null) {
         assertEquals("Sum(Open)", name(cinfo.getOpenField()), "premise: the open header");
      }

      rows.addAll(List.of(days));
      return rows.toArray(new Object[0][]);
   }

   private static Render render(VSChartInfo cinfo, VizMark mark) {
      return render(cinfo, mark, null,
                    new DefaultDataSet(rows((CandleVSChartInfo) cinfo, RISE, FALL, RISE2)));
   }

   private static Render render(VSChartInfo cinfo, VizMark mark, DataSet adata, DataSet data) {
      return render(seeded(cinfo, mark), adata, data);
   }

   // a chart as created, with its creation seed
   private static ChartVSAssemblyInfo seeded(VSChartInfo cinfo, VizMark mark) {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);
      return assemblyInfo;
   }

   private static Render render(ChartVSAssemblyInfo assemblyInfo, DataSet adata, DataSet data) {
      GraphGenerator gen = GraphGenerator.getGenerator(
         assemblyInfo, adata, data, new VariableTable(), null, XSourceInfo.NONE, null);
      return new Render(gen, gen.createEGraph());
   }

   private static Color colorAt(Render r, GraphElement elem, int row) {
      return elem.getColorFrame().getColor(r.gen.getData(), elem.getVar(0), row);
   }

   private static GraphElement element(Render r, Predicate<GraphElement> pick) {
      for(int i = 0; i < r.graph.getElementCount(); i++) {
         if(pick.test(r.graph.getElement(i))) {
            return r.graph.getElement(i);
         }
      }

      return fail("premise: the graph draws the element");
   }

   private static boolean hasCompanion(ColorFrame frame) {
      if(!(frame instanceof CompositeColorFrame composite)) {
         return false;
      }

      for(int i = 0; i < composite.getFrameCount(); i++) {
         if(composite.getFrame(i) instanceof CompanionBrushColorFrame) {
            return true;
         }
      }

      return false;
   }

   private static String name(ChartRef ref) {
      return GraphUtil.getName(ref);
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static VSChartAggregateRef styled(VSChartAggregateRef agg, int type) {
      agg.setChartType(type);
      agg.setRTChartType(type);
      return agg;
   }
}
