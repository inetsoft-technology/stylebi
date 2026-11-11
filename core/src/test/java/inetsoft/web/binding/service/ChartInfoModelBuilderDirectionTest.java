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
package inetsoft.web.binding.service;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.FormulaOptionModel;
import inetsoft.web.binding.model.SortOptionModel;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.aesthetic.StaticColorModel;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.ColorFrameModelFactory;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactory;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The editor's model carries the up and down frames only while direction colours draw, and an
 * edit of them is written back to the chart.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartInfoModelBuilderDirectionTest {
   private static final VizContext MODERN = VizContext.of(VizMark.MODERN_LIGHT);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aModernCandleSendsUpAndDown() {
      ChartBindingModel model = bind(modernCandle(), MODERN);

      assertNotNull(model.getUpColorFrame());
      assertNotNull(model.getDownColorFrame());
      assertEquals("#1C857B",
                   ((StaticColorModel) model.getUpColorFrame()).getDefaultColor().toUpperCase());
   }

   @Test
   void aClassicCandleDoesNot() {
      ChartBindingModel model = bind(candle(), VizContext.LEGACY);

      assertNull(model.getUpColorFrame());
      assertNull(model.getDownColorFrame());
   }

   @Test
   void aColourFieldStopsThem() {
      CandleVSChartInfo info = modernCandle();
      VSAestheticRef color = new VSAestheticRef();
      color.setALLDataRef(dim("Region"));
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      info.setColorField(color);
      ChartBindingModel model = bind(info, MODERN);

      assertNull(model.getUpColorFrame());
      assertNull(model.getDownColorFrame());
   }

   @Test
   void anAuthorSingleColourStopsThem() {
      CandleVSChartInfo info = modernCandle();
      ((StaticColorFrameWrapper) info.getColorFrameWrapper()).setUserColor(Color.RED);
      ChartBindingModel model = bind(info, MODERN);

      assertNull(model.getUpColorFrame());
      assertNull(model.getDownColorFrame());
   }

   @Test
   void anEditWritesTheAuthorsUp() {
      CandleVSChartInfo info = modernCandle();
      ChartBindingModel model = bind(info, MODERN);
      StaticColorModel up = (StaticColorModel) model.getUpColorFrame();
      up.setColor("#00AA00");
      up.setChanged(true);

      CandleVSChartInfo updated = update(model, info);

      assertEquals(new Color(0x00AA00), updated.getUpColorFrameWrapper().getUserColor());
      assertNull(updated.getDownColorFrameWrapper().getUserColor());
   }

   @Test
   void aResetClearsTheAuthorsUp() {
      CandleVSChartInfo info = modernCandle();
      info.getUpColorFrameWrapper().setUserColor(new Color(0x00AA00));
      ChartBindingModel model = bind(info, MODERN);
      StaticColorModel up = (StaticColorModel) model.getUpColorFrame();
      up.setColor(up.getDefaultColor());
      up.setChanged(false);

      CandleVSChartInfo updated = update(model, info);

      assertNull(updated.getUpColorFrameWrapper().getUserColor());
      assertEquals(new Color(0x1C857B), updated.getUpColorFrameWrapper().getColor());
   }

   // ---- harness -----------------------------------------------------------------------------

   private static ChartBindingModel bind(CandleVSChartInfo info, VizContext ctx) {
      return builder(ctx).createChartBinding(info, new PlotDescriptor(), false);
   }

   private static CandleVSChartInfo update(ChartBindingModel model, CandleVSChartInfo info) {
      model.setXFields(new ArrayList<>());
      model.setYFields(new ArrayList<>());
      model.setGroupFields(new ArrayList<>());
      model.setCloseField(null);
      model.setOpenField(null);
      model.setHighField(null);
      model.setLowField(null);
      CandleVSChartInfo ncinfo = (CandleVSChartInfo) info.clone();
      builder(MODERN).updateChartInfo(model, info, ncinfo, new ChartDescriptor());
      return ncinfo;
   }

   private static ChartInfoModelBuilder builder(VizContext ctx) {
      ChartRefModelFactoryService refService = mock(ChartRefModelFactoryService.class);
      when(refService.createRefModel(any(), any(), any())).thenAnswer(inv ->
         inv.getArgument(0) instanceof ChartAggregateRef ? new ChartAggregateRefModel() :
            new ChartDimensionRefModel());
      List<VisualFrameModelFactory<?, ?>> factories = new ArrayList<>();
      factories.add(new ColorFrameModelFactory.StaticColorFrameFactory());

      return new ChartInfoModelBuilder(refService, mock(ChartAestheticService.class), null,
                                       new VisualFrameModelFactoryService(factories))
      {
         @Override
         protected VizContext getVizContext() {
            return ctx;
         }

         @Override
         protected SortOptionModel createSortOptionModel() {
            return null;
         }

         @Override
         protected FormulaOptionModel createFormulaOptionModel(BAggregateRefModel cmodel) {
            return new FormulaOptionModel(false);
         }
      };
   }

   private static CandleVSChartInfo modernCandle() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      ainfo.setVSChartInfo(candle());
      ainfo.setVizMark(VizMark.MODERN_LIGHT);
      VizModernizeUtil.reseedAfterRestore(ainfo);
      CandleVSChartInfo info = (CandleVSChartInfo) ainfo.getVSChartInfo();
      assertNotNull(info.getUpColorFrameWrapper(), "premise: seeded");
      return info;
   }

   private static CandleVSChartInfo candle() {
      CandleVSChartInfo info = new CandleVSChartInfo();
      info.addXField(dim("date"));
      info.setHighField(agg("high"));
      info.setLowField(agg("low"));
      info.setOpenField(agg("open"));
      info.setCloseField(agg("close"));
      info.setChartType(GraphTypes.CHART_CANDLE);
      return info;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setGroupColumnValue(name);
      dim.setDataType(XSchema.STRING);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }
}
