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

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.axis.PolarAxis;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.StackRange;
import inetsoft.graph.visual.BarVO;
import inetsoft.graph.visual.LineVO;
import inetsoft.graph.visual.VisualObject;

import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Chart fixtures for the polar tests: pie (also 3D), radial wedges and radar charts, plotted
 * through {@code Plotter.plotAndLayout} the way the chart generators build them.
 */
public final class PolarChartFixtures {
   private PolarChartFixtures() {
   }

   /**
    * Pie: interval element without dimension, stacked, absolute-value stack range on the measure
    * (as {@code GraphGenerator.fixMScale} does for a pie), THETA polar coordinate.
    */
   public static class Pie {
      public String[] cats = { "A", "B", "C" };
      public Double[] values = { 10.0, 30.0, 60.0 };
      public boolean labels;
      public boolean threeD;
      public double pieRatio;
      public double rotate;
      public Boolean reflectVertical;
      public int width = 600;
      public int height = 400;
      public VGraph vg;
      private EGraph graph;

      public Pie labels(String prefix) {
         labels = true;

         for(int i = 0; i < cats.length; i++) {
            cats[i] = prefix + (i + 1);
         }

         return this;
      }

      private EGraph build() {
         graph = new EGraph();
         IntervalElement elem = new IntervalElement();

         elem.addVar("m");
         elem.setCollisionModifier(GraphElement.MOVE_STACK);
         elem.setColorFrame(new CategoricalColorFrame("Cat"));

         if(labels) {
            elem.setTextFrame(new DefaultTextFrame("Cat"));
         }

         graph.addElement(elem);

         LinearScale ys = new LinearScale("m");
         StackRange range = new StackRange();

         range.setAbsoluteValue(true);
         ys.setScaleRange(range);
         graph.setScale("m", ys);

         PolarCoord coord = threeD ? new PolarCoord(new Rect25Coord(null, ys)) : new PolarCoord(ys);

         coord.setType(PolarCoord.THETA);

         if(pieRatio > 0) {
            coord.setPieRatio(pieRatio);
         }

         if(rotate != 0) {
            coord.rotate(rotate);
         }

         if(reflectVertical != null) {
            coord.reflect(reflectVertical);
         }

         graph.setCoordinate(coord);
         return graph;
      }

      private DefaultDataSet data() {
         Object[][] rows = new Object[cats.length + 1][];

         rows[0] = new Object[] { "Cat", "m" };

         for(int i = 0; i < cats.length; i++) {
            rows[i + 1] = new Object[] { cats[i], values[i] };
         }

         return new DefaultDataSet(rows);
      }

      public Pie plot() {
         vg = Plotter.getPlotter(build()).plotAndLayout(data(), 0, 0, width, height);
         return this;
      }

      /** clone the unplotted graph once per size, then plot each clone as production does. */
      public List<VGraph> plotClones(int[][] sizes) {
         EGraph original = build();
         List<EGraph> clones = new ArrayList<>();
         List<VGraph> result = new ArrayList<>();

         for(int i = 0; i < sizes.length; i++) {
            clones.add((EGraph) original.clone());
         }

         for(int i = 0; i < sizes.length; i++) {
            result.add(Plotter.getPlotter(clones.get(i))
                          .plotAndLayout(data(), 0, 0, sizes[i][0], sizes[i][1]));
         }

         return result;
      }

      public PolarCoord coord() {
         return (PolarCoord) vg.getCoordinate();
      }

      public List<BarVO> bars() {
         List<BarVO> list = new ArrayList<>();

         for(VisualObject vo : GTool.getVOs(vg)) {
            if(vo instanceof BarVO) {
               list.add((BarVO) vo);
            }
         }

         return list;
      }
   }

   /** Absolute angle extent in degrees of a pie slice shape (a full ellipse is 360). */
   public static double extent(Shape shape) {
      if(shape instanceof Arc2D) {
         return Math.abs(((Arc2D) shape).getAngleExtent());
      }

      assertEllipse(shape);
      return 360;
   }

   private static void assertEllipse(Shape shape) {
      if(!(shape instanceof Ellipse2D)) {
         throw new AssertionError("neither Arc2D nor Ellipse2D: " + shape);
      }
   }

   /**
    * Radar chart: line (or area) element with the measures as dimensions, no var, over a
    * ParallelCoord wrapped in a PolarCoord (RadarGraphGenerator.createEGraph0).
    */
   public static class Radar {
      public int n = 3;
      public boolean area;
      public String namePrefix = "m";
      public double[][] rows = { { 50, 50, 50 } };
      public Double[][] nullableRows;
      public double pieRatio;
      public double rotate;
      public boolean labelVisible = true;
      public boolean fixedScale;
      public boolean emptyData;
      public int width = 600;
      public int height = 400;
      public VGraph vg;

      public Radar n(int n) {
         this.n = n;
         rows = new double[3][n];

         for(int r = 0; r < 3; r++) {
            for(int i = 0; i < n; i++) {
               rows[r][i] = 20 * (r + 1) + 3 * i;
            }
         }

         return this;
      }

      public String name(int i) {
         return namePrefix + (i + 1);
      }

      public Radar plot() {
         EGraph graph = new EGraph();
         GraphElement elem = area ? new AreaElement() : new LineElement();
         LinearScale[] scales = new LinearScale[n];
         int nrows = emptyData ? 0 : (nullableRows != null ? nullableRows.length : rows.length);
         Object[][] data = new Object[nrows + 1][n];

         for(int i = 0; i < n; i++) {
            elem.addDim(name(i));
            scales[i] = new LinearScale(name(i));

            if(fixedScale) {
               scales[i].setMin(0);
               scales[i].setMax(100);
            }

            graph.setScale(name(i), scales[i]);
            data[0][i] = name(i);
         }

         for(int r = 0; r < nrows; r++) {
            for(int i = 0; i < n; i++) {
               data[r + 1][i] = nullableRows != null ? nullableRows[r][i] : (Object) rows[r][i];
            }
         }

         graph.addElement(elem);

         ParallelCoord parallel = new ParallelCoord(scales);

         if(!labelVisible) {
            parallel.getAxisLabelScale().getAxisSpec().setLabelVisible(false);
         }

         PolarCoord coord = new PolarCoord(parallel);

         if(pieRatio > 0) {
            coord.setPieRatio(pieRatio);
         }

         if(rotate != 0) {
            coord.rotate(rotate);
         }

         graph.setCoordinate(coord);
         vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(data), 0, 0, width, height);
         return this;
      }

      public PolarCoord coord() {
         return (PolarCoord) vg.getCoordinate();
      }

      public List<DefaultAxis> measureAxes() {
         List<DefaultAxis> list = new ArrayList<>();

         for(int i = 0; i < vg.getAxisCount(); i++) {
            if(vg.getAxis(i) instanceof DefaultAxis) {
               list.add((DefaultAxis) vg.getAxis(i));
            }
         }

         return list;
      }

      public PolarAxis polarAxis() {
         for(int i = 0; i < vg.getAxisCount(); i++) {
            Axis axis = vg.getAxis(i);

            if(axis instanceof PolarAxis) {
               return (PolarAxis) axis;
            }
         }

         return null;
      }

      public List<LineVO> lines() {
         List<LineVO> list = new ArrayList<>();

         for(VisualObject vo : GTool.getVOs(vg)) {
            if(vo instanceof LineVO) {
               list.add((LineVO) vo);
            }
         }

         return list;
      }
   }

   /** Radial wedge fixture: A 40, B 80, C 60 over categorical x and linear y 0..100. */
   public static class WedgeSpec {
      public int axisStyle = -1;
   }

   public static VGraph wedges() {
      return wedges(new WedgeSpec());
   }

   public static VGraph wedges(WedgeSpec spec) {
      Object[][] rows = { { "Cat", "m" }, { "A", 40.0 }, { "B", 80.0 }, { "C", 60.0 } };
      EGraph graph = new EGraph();
      CategoricalScale xs = new CategoricalScale("Cat");
      LinearScale ys = new LinearScale("m");

      graph.addElement(new IntervalElement("Cat", "m"));
      ys.setMin(0);
      ys.setMax(100);
      ys.setIncrement(25);

      if(spec.axisStyle >= 0) {
         ys.getAxisSpec().setAxisStyle(spec.axisStyle);
      }

      graph.setScale("Cat", xs);
      graph.setScale("m", ys);
      graph.setCoordinate(new PolarCoord(new RectCoord(xs, ys)));

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 600, 400);
   }
}
