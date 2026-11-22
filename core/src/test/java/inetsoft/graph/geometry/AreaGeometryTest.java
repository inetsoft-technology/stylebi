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
package inetsoft.graph.geometry;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.ParallelCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.AreaVO;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AreaGeometry keeps one base tuple for every tuple of the area; the tests check that the two
 * lists stay aligned when a tuple is removed and for every way AreaElement builds geometries.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AreaGeometryTest {
   private static final DefaultDataSet FIX_A = new DefaultDataSet(new Object[][] {
      { "Cat", "m1", "m2" },
      { "A", 10.0, 20.0 },
      { "B", 30.0, 40.0 } });

   private static LinearScale lin(double min, double max, String... fields) {
      LinearScale scale = new LinearScale(fields);
      scale.setMin(min);
      scale.setMax(max);
      return scale;
   }

   /** Area geometries of the categorical fixture with the given collision/stack setup. */
   private static GGraph graphOf(AreaElement elem) {
      CategoricalScale xs = new CategoricalScale("Cat");
      xs.init(FIX_A);
      elem.addDim("Cat");
      elem.addVar("m1");
      elem.addVar("m2");

      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      RectCoord coord = new RectCoord(xs, lin(0, 100, "m1", "m2"));
      egraph.setCoordinate(coord);
      return egraph.createGGraph(coord, FIX_A);
   }

   private static GGraph graphForMode(String mode) {
      AreaElement elem = new AreaElement();

      switch(mode) {
      case "stackGroup":
         elem.setCollisionModifier(GraphElement.MOVE_STACK);
         elem.setStackGroup(true);
         return graphOf(elem);
      case "notStackGroup":
         return graphOf(elem);
      case "stack":
         // cumulative branch: stacked, but all points in one line
         elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
         elem.setStackNegative(false);
         return graphOf(elem);
      default:
         // radar: no variable, one dimension per axis
         DefaultDataSet data = new DefaultDataSet(new Object[][] {
            { "m1", "m2", "m3" },
            { 10.0, 20.0, 30.0 },
            { 40.0, 50.0, 60.0 } });
         Scale[] scales = { lin(0, 100, "m1"), lin(0, 100, "m2"), lin(0, 100, "m3") };
         EGraph egraph = new EGraph();
         AreaElement radar = new AreaElement();

         for(Scale scale : scales) {
            radar.addDim(scale.getFields()[0]);
            egraph.setScale(scale.getFields()[0], scale);
         }

         egraph.addElement(radar);
         ParallelCoord coord = new ParallelCoord(scales);
         egraph.setCoordinate(coord);
         return egraph.createGGraph(coord, data);
      }
   }

   private static AreaGeometry newGeometry() {
      AreaElement elem = new AreaElement();
      EGraph egraph = new EGraph();
      RectCoord coord = new RectCoord(null, lin(0, 100, "y"));
      egraph.setCoordinate(coord);
      return new AreaGeometry(elem, egraph.createGGraph(coord, FIX_A), "y", new VisualModel());
   }

   @Test
   @DisplayName("removeTuple also removes the base tuple at the same index")
   void removeTupleRemovesBaseTuple() {
      AreaGeometry geom = newGeometry();
      double[][] bases = { { 0, 10 }, { 1, 20 }, { 2, 30 } };

      for(int i = 0; i < 3; i++) {
         geom.addTuple(new double[] { i, i + 1 });
         geom.addBaseTuple(bases[i]);
      }

      double[] removed = geom.removeTuple(1);

      assertArrayEquals(new double[] { 1, 2 }, removed);
      assertEquals(2, geom.getTupleCount());
      assertSame(bases[0], geom.getBaseTuple(0));
      assertSame(bases[2], geom.getBaseTuple(1));
      assertArrayEquals(new double[] { 2, 3 }, geom.getTuple(1));
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "stackGroup", "notStackGroup", "stack", "radar" })
   @DisplayName("every tuple has a base tuple with the same x")
   void everyTupleHasBaseTupleWithSameX(String mode) {
      GGraph ggraph = graphForMode(mode);

      assertTrue(ggraph.getGeometryCount() > 0, mode);

      for(int g = 0; g < ggraph.getGeometryCount(); g++) {
         AreaGeometry geom = (AreaGeometry) ggraph.getGeometry(g);
         assertTrue(geom.getTupleCount() > 0, mode);

         for(int k = 0; k < geom.getTupleCount(); k++) {
            double[] tuple = geom.getTuple(k);
            double[] base = geom.getBaseTuple(k);

            assertNotNull(base, mode + " base " + k);
            assertEquals(tuple.length, base.length, mode);

            if("radar".equals(mode)) {
               // the base of an axis point lies on the same axis: only its own slot differs
               for(int j = 0; j < tuple.length; j++) {
                  if(j != k) {
                     assertEquals(tuple[j], base[j], mode);
                  }
               }
            }
            else {
               assertEquals(tuple[tuple.length - 2], base[base.length - 2], mode);
            }
         }
      }
   }

   @Test
   @DisplayName("the base tuple is a copy of the tuple, not the same array")
   void baseTupleIsCopy() {
      GGraph ggraph = graphForMode("notStackGroup");
      AreaGeometry geom = (AreaGeometry) ggraph.getGeometry(0);
      double[] tuple = geom.getTuple(0);
      double[] base = geom.getBaseTuple(0);
      double baseY = base[base.length - 1];

      assertNotSame(tuple, base);
      tuple[tuple.length - 1] = baseY + 123;

      assertEquals(baseY, geom.getBaseTuple(0)[base.length - 1]);
   }

   @Test
   @DisplayName("createVisual builds an AreaVO with one point per tuple")
   void createVisualBuildsAreaVO() {
      GGraph ggraph = graphForMode("notStackGroup");
      AreaGeometry geom = (AreaGeometry) ggraph.getGeometry(0);
      RectCoord coord = (RectCoord) ggraph.getCoordinate();

      Object vo = geom.createVisual(coord);

      assertInstanceOf(AreaVO.class, vo);
      assertEquals(geom.getTupleCount(), ((AreaVO) vo).getTransformedPoints().length);
   }
}
