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
package inetsoft.graph.visual;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.DefaultTextFrame;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.coord.Rect25Coord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.StackRange;

import java.util.ArrayList;
import java.util.List;

/**
 * A 3D pie plotted through {@code Plotter.plotAndLayout} the way the chart generators build it:
 * interval element without dimension, stacked, absolute-value stack range on the measure,
 * THETA polar coordinate over a {@code Rect25Coord}.
 */
public class Pie3DChartFixture {
   public String[] cats = { "A", "B", "C" };
   public Object[] values = { 10.0, 30.0, 60.0 };
   /** values of a second measure "n" in the same coordinate, or null for one measure. */
   public Object[] values2;
   public boolean labels;
   public boolean exploded;
   public boolean emptyData;
   public int width = 600;
   public int height = 400;
   public VGraph vg;

   public Pie3DChartFixture values(Object... values) {
      this.values = values;
      cats = new String[values.length];

      for(int i = 0; i < values.length; i++) {
         cats[i] = "" + (char) ('A' + i);
      }

      return this;
   }

   public Pie3DChartFixture labels(String prefix) {
      labels = true;

      for(int i = 0; i < cats.length; i++) {
         cats[i] = prefix + (i + 1);
      }

      return this;
   }

   private EGraph build() {
      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement();

      elem.addVar("m");

      if(values2 != null) {
         elem.addVar("n");
      }

      elem.setCollisionModifier(GraphElement.MOVE_STACK);
      elem.setColorFrame(new CategoricalColorFrame("Cat"));

      if(labels) {
         elem.setTextFrame(new DefaultTextFrame("Cat"));
      }

      if(exploded) {
         elem.setHint(GraphElement.HINT_EXPLODED, "true");
      }

      graph.addElement(elem);

      LinearScale ys = new LinearScale("m");
      StackRange range = new StackRange();
      range.setAbsoluteValue(true);
      ys.setScaleRange(range);
      graph.setScale("m", ys);

      if(values2 != null) {
         graph.setScale("n", ys);
      }

      PolarCoord coord = new PolarCoord(new Rect25Coord(null, ys));
      coord.setType(PolarCoord.THETA);
      graph.setCoordinate(coord);
      return graph;
   }

   private DefaultDataSet data() {
      int n = emptyData ? 0 : cats.length;
      Object[][] rows = new Object[n + 1][];
      rows[0] = values2 == null ? new Object[] { "Cat", "m" } : new Object[] { "Cat", "m", "n" };

      for(int i = 0; i < n; i++) {
         rows[i + 1] = values2 == null ? new Object[] { cats[i], values[i] }
            : new Object[] { cats[i], values[i], values2[i] };
      }

      return new DefaultDataSet(rows);
   }

   public Pie3DChartFixture plot() {
      vg = Plotter.getPlotter(build()).plotAndLayout(data(), 0, 0, width, height);
      return this;
   }

   /**
    * Plot without layout: the geometries still hold their tuples (plotAndLayout clears them).
    */
   public Pie3DChartFixture plotOnly() {
      vg = Plotter.getPlotter(build()).plot(data());
      return this;
   }

   /** The 3D pie visual objects (one per measure). */
   public List<Pie3DVO> pies() {
      List<Pie3DVO> list = new ArrayList<>();

      for(VisualObject vo : GTool.getVOs(vg)) {
         if(vo instanceof Pie3DVO) {
            list.add((Pie3DVO) vo);
         }
      }

      return list;
   }

   public Pie3DVO pie() {
      return pies().get(0);
   }
}
