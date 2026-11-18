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
package inetsoft.graph.coord;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.LogScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scale labels of the radar spokes: one spoke when the scales agree, the outermost label
 * of each when they differ.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RadarScaleLabelTest {
   @Test
   void equalScalesShowLabelsOnTheTopSpokeOnly() {
      List<DefaultAxis> spokes = spokes(true, 6, (i, s) -> range(s, 0, 100));

      assertEquals(allButFirst(spokes.get(1)), shown(spokes.get(1)));
      assertFalse(shown(spokes.get(1)).isEmpty());

      for(int i = 0; i < 6; i++) {
         if(i != 1) {
            assertEquals(List.of(), shown(spokes.get(i)), "spoke " + i);
         }
      }
   }

   @Test
   void aOneVarRadarAlwaysQualifies() {
      EGraph graph = new EGraph();
      graph.addElement(new LineElement("cat", "m"));
      Object[][] rows = new Object[6][];
      rows[0] = new Object[] { "cat", "m" };

      for(int i = 1; i < 6; i++) {
         rows[i] = new Object[] { "c" + i, i * 10.0 };
      }

      DefaultDataSet data = new DefaultDataSet(rows);
      OneVarParallelCoord inner = new OneVarParallelCoord("m", "cat", new LinearScale("m"));
      inner.init(data);
      graph.setCoordinate(rule(new PolarCoord(inner), true));
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 400, 400);
      List<DefaultAxis> spokes = spokesOf(vgraph);

      assertEquals(5, spokes.size());
      assertEquals(allButFirst(spokes.get(1)), shown(spokes.get(1)));
      assertFalse(shown(spokes.get(1)).isEmpty());

      for(int i = 0; i < 5; i++) {
         if(i != 1) {
            assertEquals(List.of(), shown(spokes.get(i)), "spoke " + i);
         }
      }
   }

   @Test
   void theSpokeNearestTwelve() {
      int[] counts = { 3, 4, 5, 6, 7, 8 };
      int[] tops = { 0, 0, 1, 1, 1, 1 };

      for(int k = 0; k < counts.length; k++) {
         List<DefaultAxis> spokes = spokes(true, counts[k], (i, s) -> range(s, 0, 100));

         for(int i = 0; i < counts[k]; i++) {
            assertEquals(i == tops[k], !shown(spokes.get(i)).isEmpty(),
                         "n " + counts[k] + " spoke " + i);
         }
      }
   }

   @Test
   void differentScalesShowOnlyTheOutermostLabel() {
      double[] max = { 10, 1000, 50 };
      List<DefaultAxis> spokes = spokes(true, 3, (i, s) -> range(s, 0, max[i]));

      for(int i = 0; i < 3; i++) {
         DefaultAxis axis = spokes.get(i);
         int last = axis.getLabels().length - 1;

         assertEquals(List.of(last), shown(axis), "spoke " + i);
         assertEquals(axis.getScale().getTicks()[last],
                      ((Number) axis.getLabels()[last].getValue()).doubleValue(), 1e-9);
      }
   }

   @Test
   void aReversedSpokeShowsItsSmallestValueAtTheRim() {
      List<DefaultAxis> spokes = spokes(true, 3, (i, s) -> {
         range(s, 0, 20);
         ((LinearScale) s).setReversed(i == 0);
      });
      DefaultAxis axis = spokes.get(0);
      int last = axis.getLabels().length - 1;

      assertEquals(List.of(last), shown(axis));
      assertEquals(0, ((Number) axis.getLabels()[last].getValue()).doubleValue(), 1e-9);
   }

   @Test
   void differentFormatsAreDifferentScales() {
      List<DefaultAxis> spokes = spokes(true, 3, (i, s) -> {
         range(s, 0, 100);

         if(i == 1) {
            s.getAxisSpec().getTextSpec().setFormat(new DecimalFormat("0'%'"));
         }
      });

      for(int i = 0; i < 3; i++) {
         int last = spokes.get(i).getLabels().length - 1;
         assertEquals(List.of(last), shown(spokes.get(i)), "spoke " + i);
      }
   }

   @Test
   void logAndLinearDoNotMatch() {
      List<DefaultAxis> spokes = spokes(true, 3, (i, s) -> range(s, 1, 100), i -> i == 0);

      for(int i = 0; i < 3; i++) {
         int last = spokes.get(i).getLabels().length - 1;
         assertEquals(List.of(last), shown(spokes.get(i)), "spoke " + i);
      }
   }

   @Test
   void anAuthorHiddenSpokeStaysHidden() {
      List<DefaultAxis> spokes = spokes(true, 6, (i, s) -> {
         range(s, 0, 100);
         s.getAxisSpec().setLabelVisible(i != 5);
      });

      assertEquals(List.of(), shown(spokes.get(5)));
      assertFalse(shown(spokes.get(1)).isEmpty());

      // the top spoke hidden: the labels go to the next nearest, the lower of two equals
      spokes = spokes(true, 6, (i, s) -> {
         range(s, 0, 100);
         s.getAxisSpec().setLabelVisible(i != 1);
      });

      assertEquals(List.of(), shown(spokes.get(1)));
      assertFalse(shown(spokes.get(0)).isEmpty());

      for(int i : new int[] { 2, 3, 4, 5 }) {
         assertEquals(List.of(), shown(spokes.get(i)), "spoke " + i);
      }

      // differing scales: the hidden spoke stays hidden, the others show the rim
      spokes = spokes(true, 3, (i, s) -> {
         range(s, 0, 10 * (i + 1));
         s.getAxisSpec().setLabelVisible(i != 1);
      });

      assertEquals(List.of(), shown(spokes.get(1)));
      assertEquals(1, shown(spokes.get(0)).size());
      assertEquals(1, shown(spokes.get(2)).size());
   }

   @Test
   void theRuleLeavesTheSpecAlone() {
      List<AxisSpec> specs = new ArrayList<>();
      spokes(true, 6, (i, s) -> {
         range(s, 0, 100);
         specs.add(s.getAxisSpec());
      });

      assertEquals(6, specs.size());

      for(AxisSpec spec : specs) {
         assertTrue(spec.isLabelVisible());
         assertTrue(spec.isLineVisible());
         assertTrue(spec.isTickVisible());
      }
   }

   @Test
   void classicRadarUnchanged() {
      List<DefaultAxis> spokes = spokes(false, 6, (i, s) -> range(s, 0, 100));

      for(DefaultAxis axis : spokes) {
         assertEquals(allButFirst(axis), shown(axis));
      }
   }

   private static void range(Scale scale, double min, double max) {
      ((LinearScale) scale).setMin(min);
      ((LinearScale) scale).setMax(max);
   }

   private static PolarCoord rule(PolarCoord coord, boolean on) {
      coord.setSpokeLabelRule(on);
      return coord;
   }

   private interface ScaleSetup {
      void apply(int i, Scale scale);
   }

   private static List<DefaultAxis> spokes(boolean rule, int n, ScaleSetup setup) {
      return spokes(rule, n, setup, i -> false);
   }

   // the spokes of a 400x400 radar over n measures, in measure order
   private static List<DefaultAxis> spokes(boolean rule, int n, ScaleSetup setup,
                                           IntPredicate log)
   {
      String[] names = new String[n];
      Scale[] scales = new Scale[n];
      Object[] low = new Object[n];
      Object[] high = new Object[n];

      for(int i = 0; i < n; i++) {
         names[i] = "m" + i;
         scales[i] = log.test(i) ? new LogScale(names[i]) : new LinearScale(names[i]);
         low[i] = 5.0;
         high[i] = 9.0;
         setup.apply(i, scales[i]);
      }

      EGraph graph = new EGraph();
      LineElement line = new LineElement();

      for(String name : names) {
         line.addDim(name);
      }

      graph.addElement(line);
      graph.setCoordinate(rule(new PolarCoord(new ParallelCoord(scales)), rule));
      Object[][] rows = { names, low, high };
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 400);

      return spokesOf(vgraph);
   }

   private static List<DefaultAxis> spokesOf(VGraph vgraph) {
      List<DefaultAxis> spokes = new ArrayList<>();

      for(Axis axis : vgraph.getCoordinate().getAxes(false)) {
         if(axis instanceof DefaultAxis a) {
            spokes.add(a);
         }
      }

      return spokes;
   }

   private static List<Integer> shown(DefaultAxis axis) {
      List<Integer> shown = new ArrayList<>();
      VLabel[] labels = axis.getLabels();

      if(labels != null) {
         for(int i = 0; i < labels.length; i++) {
            if(labels[i].getZIndex() >= 0) {
               shown.add(i);
            }
         }
      }

      return shown;
   }

   private static List<Integer> allButFirst(DefaultAxis axis) {
      List<Integer> all = new ArrayList<>();

      for(int i = 1; i < axis.getLabels().length; i++) {
         all.add(i);
      }

      return all;
   }
}
