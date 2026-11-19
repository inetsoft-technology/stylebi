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
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PolygonElement;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.TealColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked polygon-only map starts its ramp at a quarter of the range.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MapRampFloorTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedPolygonMapFloorsItsRamp() {
      AutoDivergingColorFrame frame = auto(polygonFrame(map(true, false, GraphTypes.CHART_MAP),
                                                        VizMark.MODERN_LIGHT, positive(), false,
                                                        false));
      assertEquals(0.25, frame.getLowStop());
      assertFalse(frame.isDiverging());
      assertEquals(new TealColorFrame().getColor(0.25), frame.getColor(0.0));
   }

   @Test
   void aPointMapHasNoFloor() {
      assertEquals(0, lowStop(map(true, true, GraphTypes.CHART_MAP), positive())
         .orElseGet(() -> fail("premise: an auto ramp draws")));
   }

   @Test
   void aContourMapHasNoFloor() {
      assertEquals(0, lowStop(map(true, false, GraphTypes.CHART_MAP_CONTOUR), positive())
         .orElse(0));
   }

   @Test
   void onlyAMarkedRegionOnlyNonContourMapFloors() {
      assertTrue(MapGenerator.floorsRamp(true, true, false, false));
      assertFalse(MapGenerator.floorsRamp(true, true, false, true));
      assertFalse(MapGenerator.floorsRamp(true, true, true, false));
      assertFalse(MapGenerator.floorsRamp(true, false, false, false));
      assertFalse(MapGenerator.floorsRamp(false, true, false, false));
   }

   @Test
   void anAuthorsRampHasNoFloor() {
      LinearColorFrame frame = linear(polygonFrame(map(true, false, GraphTypes.CHART_MAP),
                                                   VizMark.MODERN_LIGHT, positive(), true,
                                                   false));
      assertSame(TealColorFrame.class, frame.getClass());
   }

   @Test
   void aDivergingMapHasNoFloor() {
      AutoDivergingColorFrame frame = auto(polygonFrame(map(true, false, GraphTypes.CHART_MAP),
                                                        VizMark.MODERN_LIGHT, crossing(), false,
                                                        false));
      assertTrue(frame.isDiverging());
      assertEquals(new VarianceColorFrame().getColor(0.0), frame.getColor(0.0));
   }

   @Test
   void aBrushedMapKeepsTheFloor() {
      AutoDivergingColorFrame frame = auto(polygonFrame(map(true, false, GraphTypes.CHART_MAP),
                                                        VizMark.MODERN_LIGHT, positive(), false,
                                                        true));
      assertEquals(0.25, frame.getLowStop());
   }

   @Test
   void aClassicMapIsUnchanged() {
      LinearColorFrame frame = linear(polygonFrame(map(true, false, GraphTypes.CHART_MAP), null,
                                                   positive(), false, false));
      assertSame(TealColorFrame.class, frame.getClass());
   }

   // the highest low stop of the auto ramps the elements draw, empty when none draws
   private static OptionalDouble lowStop(VSMapInfo map, Object[][] rows) {
      EGraph graph = graph(map, VizMark.MODERN_LIGHT, rows, false, false);
      OptionalDouble stop = OptionalDouble.empty();

      for(int i = 0; i < graph.getElementCount(); i++) {
         LinearColorFrame frame = find(graph.getElement(i).getColorFrame());

         if(frame instanceof AutoDivergingColorFrame) {
            double value = ((AutoDivergingColorFrame) frame).getLowStop();
            stop = OptionalDouble.of(Math.max(stop.orElse(value), value));
         }
      }

      return stop;
   }

   private static Object[][] positive() {
      return rows(10, 50, 90);
   }

   private static Object[][] crossing() {
      return rows(-20, 80, 30);
   }

   private static Object[][] rows(double... values) {
      String[] states = { "California", "New York", "Texas" };
      Object[][] rows = new Object[values.length + 1][];
      rows[0] = new Object[] { "State", "City", COLOR };

      for(int i = 0; i < values.length; i++) {
         rows[i + 1] = new Object[] { states[i], states[i], values[i] };
      }

      return rows;
   }

   private static VSMapInfo map(boolean state, boolean city, int type) {
      VSMapInfo map = new VSMapInfo();
      map.setMeasureMapType("U.S.");

      if(state) {
         VSChartGeoRef geo = geo("State", "2");
         map.addGeoField(geo);
         map.addRTGeoField(geo);
      }

      if(city) {
         VSChartGeoRef geo = geo("City", "1001");
         map.addGeoField(geo);
         map.addRTGeoField(geo);
      }

      if(!city) {
         ((StaticShapeFrame) map.getShapeFrame()).setShape(GShape.NIL);
      }

      map.setChartType(type);
      return map;
   }

   private static EGraph graph(VSMapInfo map, VizMark mark, Object[][] rows, boolean author,
                               boolean brushed)
   {
      VSChartAggregateRef var = new VSChartAggregateRef();
      var.setDataRef(new BaseField("Var"));
      var.setFormulaValue("Sum");
      TealColorFrameWrapper wrapper = new TealColorFrameWrapper();
      wrapper.setAuthorRamp(author);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(var);
      color.setRTDataRef(var);
      color.setVisualFrameWrapper(wrapper);
      map.setColorField(color);

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(map);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);

      return GraphGenerator.getGenerator(
         assemblyInfo, brushed ? new DefaultDataSet(rows) : null, new DefaultDataSet(rows),
         new VariableTable(), null, XSourceInfo.NONE, null).createEGraph();
   }

   // the colour frame of the polygon element that draws a ramp
   private static ColorFrame polygonFrame(VSMapInfo map, VizMark mark, Object[][] rows,
                                          boolean author, boolean brushed)
   {
      EGraph graph = graph(map, mark, rows, author, brushed);

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(elem instanceof PolygonElement && find(elem.getColorFrame()) != null) {
            return elem.getColorFrame();
         }
      }

      return fail("premise: the map draws a polygon element with a ramp");
   }

   private static AutoDivergingColorFrame auto(ColorFrame frame) {
      LinearColorFrame found = linear(frame);
      assertInstanceOf(AutoDivergingColorFrame.class, found);
      return (AutoDivergingColorFrame) found;
   }

   private static LinearColorFrame linear(VisualFrame frame) {
      LinearColorFrame found = find(frame);
      assertNotNull(found, "premise: a linear colour frame draws, got " + frame);
      return found;
   }

   // the linear frame inside composites
   private static LinearColorFrame find(VisualFrame frame) {
      if(frame instanceof LinearColorFrame) {
         return (LinearColorFrame) frame;
      }

      if(frame instanceof CompositeColorFrame) {
         CompositeColorFrame composite = (CompositeColorFrame) frame;

         for(int i = 0; i < composite.getFrameCount(); i++) {
            LinearColorFrame found = find(composite.getFrame(i));

            if(found != null) {
               return found;
            }
         }
      }

      return null;
   }

   private static VSChartGeoRef geo(String name, String layer) {
      VSChartGeoRef geo = new VSChartGeoRef(new AttributeRef(null, name));
      geo.setGroupColumnValue(name);
      geo.setDataType(XSchema.STRING);
      geo.getGeographicOption().setLayerValue(layer);
      return geo;
   }

   private static final String COLOR = "Sum(Var)";
}
