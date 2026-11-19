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
import inetsoft.graph.Plotter;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.scale.Scale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.TealColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VarianceColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked chart draws its seeded Teal as the diverging ramp when the colour measure crosses zero.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DivergingRampRenderTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedChartCrossingZeroDrawsVariance() {
      VSChartInfo cinfo = pointChart(new TealColorFrameWrapper());
      LinearColorFrame frame = ramp(generator(cinfo, VizMark.MODERN_LIGHT, rows(-0.2, 0.8, 0.3),
                                              null).createEGraph());
      assertInstanceOf(AutoDivergingColorFrame.class, frame);
      assertTrue(((AutoDivergingColorFrame) frame).isDiverging());

      Scale scale = frame.getScale();
      assertTrue(scale.getMin() <= -0.2 && scale.getMax() >= 0.8,
                 "premise: the scale covers the data");
      assertEquals(-scale.getMax(), scale.getMin());

      // the stored ramp stays the seeded, unflagged Teal
      AestheticRef stored = cinfo.getColorField();
      assertSame(TealColorFrame.class, stored.getVisualFrame().getClass());
      assertInstanceOf(LinearColorFrameWrapper.class, stored.getVisualFrameWrapper());
      assertFalse(((LinearColorFrameWrapper) stored.getVisualFrameWrapper()).isAuthorRamp());
   }

   @Test
   void aPositiveMeasureStaysTeal() {
      LinearColorFrame frame = ramp(pointGraph(VizMark.MODERN_LIGHT, new TealColorFrameWrapper(),
                                               rows(1, 5, 9), null));
      assertInstanceOf(AutoDivergingColorFrame.class, frame);
      assertFalse(((AutoDivergingColorFrame) frame).isDiverging());
   }

   @Test
   void aClassicChartIsNotSubstituted() {
      LinearColorFrame frame = ramp(pointGraph(null, new TealColorFrameWrapper(),
                                               rows(-0.2, 0.8, 0.3), null));
      assertSame(TealColorFrame.class, frame.getClass());
   }

   @Test
   void anAuthorsTealIsNotSubstituted() {
      LinearColorFrame frame = ramp(pointGraph(VizMark.MODERN_LIGHT,
                                               author(new TealColorFrameWrapper()),
                                               rows(-0.2, 0.8, 0.3), null));
      assertSame(TealColorFrame.class, frame.getClass());
   }

   @Test
   void anAuthorsVarianceIsNotCentred() {
      LinearColorFrame frame = ramp(pointGraph(VizMark.MODERN_LIGHT,
                                               author(new VarianceColorFrameWrapper()),
                                               rows(-0.2, 0.8, 0.3), null));
      assertSame(VarianceColorFrame.class, frame.getClass());

      Scale scale = frame.getScale();
      assertTrue(scale.getMin() <= -0.2 && scale.getMax() >= 0.8,
                 "premise: the scale covers the data");
      assertNotEquals(-scale.getMax(), scale.getMin());
   }

   @Test
   void aBrushedTargetDecidesOnAllData() {
      Object[][] brushed = {
         { "State", "Sum(Total)", COLOR },
         { "NY", 200.0, 0.8 },
         { "TX", 300.0, 0.3 },
      };
      LinearColorFrame frame = ramp(pointGraph(VizMark.MODERN_LIGHT, new TealColorFrameWrapper(),
                                               rows(-0.2, 0.8, 0.3), brushed));
      assertInstanceOf(AutoDivergingColorFrame.class, frame);
      assertTrue(((AutoDivergingColorFrame) frame).isDiverging());
   }

   @Test
   void aRelationNodeColourFollowsTheRule() {
      VSChartDimensionRef parent = dim("Parent");
      VSChartDimensionRef child = dim("Child");
      VSAestheticRef color = colorRef(new TealColorFrameWrapper());

      RelationVSChartInfo cinfo = new RelationVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_TREE);
      cinfo.setRTChartType(GraphTypes.CHART_TREE);
      cinfo.setSourceField(parent);
      cinfo.setTargetField(child);
      cinfo.setRTSourceField(parent);
      cinfo.setRTTargetField(child);
      cinfo.setNodeColorField(color);
      cinfo.updateChartType(false);

      Object[][] rows = {
         { "Parent", "Child", COLOR },
         { "A", "B", -0.2 },
         { "A", "C", 0.8 },
         { "B", "D", 0.3 },
      };
      GraphGenerator gen = generator(cinfo, VizMark.MODERN_LIGHT, rows, null);
      EGraph graph = gen.createEGraph();
      Plotter.getPlotter(graph).plot(gen.getData());

      assertInstanceOf(RelationElement.class, graph.getElement(0));
      LinearColorFrame frame = find(((RelationElement) graph.getElement(0)).getNodeColorFrame());
      assertInstanceOf(AutoDivergingColorFrame.class, frame);
      assertTrue(((AutoDivergingColorFrame) frame).isDiverging());
   }

   @Test
   void drawnFieldsNamesTheDivergingField() {
      EGraph crossing = pointGraph(VizMark.MODERN_LIGHT, new TealColorFrameWrapper(),
                                   rows(-0.2, 0.8, 0.3), null);
      EGraph positive = pointGraph(VizMark.MODERN_LIGHT, new TealColorFrameWrapper(),
                                   rows(1, 5, 9), null);
      assertEquals(Set.of(COLOR), DivergingRamps.drawnFields(crossing));
      assertEquals(Set.of(), DivergingRamps.drawnFields(positive));
   }

   private static <T extends LinearColorFrameWrapper> T author(T wrapper) {
      wrapper.setAuthorRamp(true);
      return wrapper;
   }

   private static Object[][] rows(double... values) {
      String[] states = { "CA", "NY", "TX" };
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { "State", "Sum(Total)", COLOR };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { states[i], 100.0 * (i + 1), values[i] };
      }

      return rows;
   }

   // a point chart of Sum(Total) by State with Sum(Var) on Color; brushed rows make it a target
   private static EGraph pointGraph(VizMark mark, LinearColorFrameWrapper ramp, Object[][] all,
                                    Object[][] brushed)
   {
      return generator(pointChart(ramp), mark, all, brushed).createEGraph();
   }

   private static VSChartInfo pointChart(LinearColorFrameWrapper ramp) {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_POINT);
      cinfo.setRTChartType(GraphTypes.CHART_POINT);
      cinfo.addXField(new VSChartDimensionRef(new BaseField("State")));
      cinfo.addYField(agg("Total"));
      cinfo.setColorField(colorRef(ramp));
      cinfo.updateChartType(false);
      return cinfo;
   }

   // Sum(Var) drawn with a ramp
   private static VSAestheticRef colorRef(LinearColorFrameWrapper ramp) {
      VSChartAggregateRef var = agg("Var");
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(var);
      color.setRTDataRef(var);
      color.setVisualFrameWrapper(ramp);
      return color;
   }

   private static GraphGenerator generator(VSChartInfo cinfo, VizMark mark, Object[][] all,
                                           Object[][] brushed)
   {
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);

      return brushed == null
         ? GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(all),
                                       new VariableTable(), null, XSourceInfo.NONE, null)
         : GraphGenerator.getGenerator(assemblyInfo, new DefaultDataSet(all),
                                       new DefaultDataSet(brushed), new VariableTable(), null,
                                       XSourceInfo.NONE, null);
   }

   // the ramp the first element draws its colour with
   private static LinearColorFrame ramp(EGraph graph) {
      return find(graph.getElement(0).getColorFrame());
   }

   private static LinearColorFrame find(ColorFrame frame) {
      LinearColorFrame found = linear(frame);
      assertNotNull(found, "premise: a linear colour frame draws, got " + frame);
      return found;
   }

   // the linear frame inside highlight and brushing composites
   private static LinearColorFrame linear(VisualFrame frame) {
      if(frame instanceof LinearColorFrame) {
         return (LinearColorFrame) frame;
      }

      if(frame instanceof CompositeColorFrame) {
         CompositeColorFrame composite = (CompositeColorFrame) frame;

         for(int i = 0; i < composite.getFrameCount(); i++) {
            LinearColorFrame found = linear(composite.getFrame(i));

            if(found != null) {
               return found;
            }
         }
      }

      return null;
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

   private static final String COLOR = "Sum(Var)";
}
