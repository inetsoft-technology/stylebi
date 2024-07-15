/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DataSetIndex;
import inetsoft.graph.geometry.MekkoGeometry;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.*;

import java.awt.*;
import java.awt.geom.*;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Map;

/**
 * Coordinate for creating mekko graph. Mekko graph is a very specific graph, where axes
 * and layout are derived from data calculation, beyond the simple scaling. Therefore,
 * all scales are created internally, and only AxisSpec is exposed to control the rendering
 * of axes.
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class MekkoCoord extends Coordinate {
   /**
    * Create an empty coord. Dimension and variable should be set.
    */
   public MekkoCoord() {
      yscale = new LinearScale();
      yscale.setMin(0);
      yscale.setMax(1);
      yscale.setIncrement(0.2);
      yscale.getAxisSpec().getTextSpec().setFormat(NumberFormat.getPercentInstance());
      yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE);
      yscale.getAxisSpec().setLineColor(Color.GRAY);

      xscale = new CategoricalScale();
      xscale.setFill(true);
      xscale.setGrid(true);
      xscale.getAxisSpec().setLabelGap(1);

      x2scale = new CategoricalScale();
      x2scale.setFill(true);
      x2scale.setGrid(true);
      x2scale.getAxisSpec().setLabelGap(1);
   }

   /**
    * Create a coordinate.
    * @param xdim x axis dimension.
    * @param var measure column.
    */
   public MekkoCoord(String xdim, String var) {
      this();
      setDim(xdim);
      setVar(var);
   }

   /**
    * Set the x axis dimension. This should agree with the dimension in MekkoElement.
    */
   public void setDim(String xdim) {
      this.xscale.setFields(xdim);
      this.xscale.setDataFields(xdim);
   }

   /**
    * Get the x axis dimension.
    */
   public String getDim() {
      String[] fields = xscale.getFields();
      return fields.length > 0 ? fields[0]: null;
   }

   /**
    * Set the variable column.
    */
   public void setVar(String var) {
      this.var = var;
      this.yscale.setFields(var + "%");
      this.x2scale.setFields(var);
   }

   /**
    * Get the variable column.
    */
   public String getVar() {
      return var;
   }

   /**
    * Set the y (percentage) axis spec.
    */
   public void setYAxisSpec(AxisSpec spec) {
      this.yscale.setAxisSpec(spec);
   }

   /**
    * Get the y (percentage) axis spec.
    */
   public AxisSpec getYAxisSpec() {
      return this.yscale.getAxisSpec();
   }

   /**
    * Set the x (dimension) axis spec.
    */
   public void setXAxisSpec(AxisSpec spec) {
      this.xscale.setAxisSpec(spec);
   }

   /**
    * Get the x (dimension) axis spec.
    */
   public AxisSpec getXAxisSpec() {
      return this.xscale.getAxisSpec();
   }

   /**
    * Set the top x (sub-total) axis spec.
    */
   public void setX2AxisSpec(AxisSpec spec) {
      this.x2scale.setAxisSpec(spec);
   }

   /**
    * Get the top x (sub-total) axis spec.
    */
   public AxisSpec getX2AxisSpec() {
      return this.x2scale.getAxisSpec();
   }

   @Override
   public void init(DataSet data) {
      super.init(data);

      if(xscale != null) {
         String[] dims = xscale.getDataFields();

         if(dims.length > 0) {
            Map<Object, Double> groupTotals = MekkoGeometry.calcGroupTotals(
               data, dims[0], var, 0, data.getRowCount(), this);

            x2scale.init(Arrays.stream(xscale.getValues())
                                    .map(v -> groupTotals.get(v)).toArray());

            for(Object group : groupTotals.keySet()) {
               Double weight = groupTotals.get(group);
               xscale.setWeight(group, weight);
               x2scale.setWeight(weight, weight);
            }
         }
      }
   }

   @Override
   public Point2D getPosition(double[] tuple) {
      double x = getPosition(xscale, tuple[0], GWIDTH);
      return new Point2D.Double(x, 0);
   }

   @Override
   public double getIntervalSize(double interval) {
      return 0;
   }

   @Override
   public void createAxis(VGraph vgraph) {
      if(xaxis1 == null) {
         AxisSpec spec = xscale.getAxisSpec();

         xaxis1 = createAxis(xscale, vgraph);
         xaxis1.setAxisType("x");
         xaxis1.setCenterLabel(true);
         xaxis1.setTickDown(false);

         xaxis1.setLength(GWIDTH);
         // apply coord transform so axis horizontal/vertical is correct when
         // calculating size. The screen transform will be reset in fit.
         xaxis1.getScreenTransform().preConcatenate(getCoordTransform());

         vgraph.addAxis(xaxis1);

         xaxis2 = createAxis(x2scale, vgraph);
         xaxis2.setAxisType("x");
         xaxis2.setCenterLabel(true);
         xaxis2.setLength(GWIDTH);
         xaxis2.setPrimaryAxis(xaxis1);
         xaxis2.setTickDown(false);

         xaxis2.getScreenTransform().translate(0, GHEIGHT);
         xaxis2.getScreenTransform().preConcatenate(getCoordTransform());

         vgraph.addAxis(xaxis2);

         yaxis1 = createAxis(yscale, vgraph);
         yaxis1.setAxisType("y");
         yaxis1.setLength(GHEIGHT);
         yaxis1.getScreenTransform().rotate(Math.PI / 2);
         yaxis1.getScreenTransform().preConcatenate(getCoordTransform());
         yaxis1.setTickDown(true);

         vgraph.addAxis(yaxis1);
      }
   }

   private DefaultAxis createAxis(Scale scale, VGraph vgraph) {
      DefaultAxis axis = new DefaultAxis(scale, vgraph);
      axis.setCoordinate(this);
      return axis;
   }

   /**
    * Set the screen transformation to fit the graph to the specified output
    * size.
    * @param x horizontal position.
    * @param y vertical position.
    * @param w width to scale to.
    * @param h height to scale to.
    */
   @Override
   public void fit(double x, double y, double w, double h) {
      AffineTransform c2 = getScaledCoordTransform(x, y, w, h);

      xaxis1.setAxisSize(xaxis1.getPreferredHeight());
      xaxis2.setAxisSize(xaxis2.getPreferredHeight());
      yaxis1.setAxisSize(yaxis1.getPreferredWidth());

      VGraph vgraph = getVGraph();
      double xfactor = GTool.getScaleFactor(c2, 0);
      double yfactor = GTool.getScaleFactor(c2, 90);
      double yaxis1w = yaxis1 == null ? 0 : yaxis1.getAxisSize() * xfactor;
      double xaxis1h = xaxis1 == null ? 0 : xaxis1.getAxisSize() * yfactor;
      double xaxis2h = xaxis2 == null ? 0 : xaxis2.getAxisSize() * yfactor;
      double plotw = w - yaxis1w;
      plotw = plotw < 0 ? 0 : plotw;
      double ploth = h - xaxis1h - xaxis2h;
      ploth = ploth < 0 ? 0 : ploth;
      double plotx = x + yaxis1w;
      double ploty = y + xaxis1h;

      if(vgraph != null) {
         // vgraph transform
         AffineTransform trans = new AffineTransform();

         trans.translate(plotx, ploty);
         trans.scale(plotw / GWIDTH, ploth / GHEIGHT);
         vgraph.concat(trans, true);

         // set the plot bounds before c2 transformation so the
         // getElementBounds can get the right boundary
         vgraph.setPlotBounds(new Rectangle2D.Double(plotx, ploty, plotw, ploth));

         vgraph.concat(c2, false);
         vgraph.setPlotBounds(getBounds(plotx, ploty, plotw, ploth, c2));
      }

      if(xaxis1 != null) {
         AffineTransform xtrans = new AffineTransform();
         int astyle = xscale.getAxisSpec().getAxisStyle();
         double y0 = ploty;

         xtrans.translate(plotx, y0);
         xtrans.scale(plotw / GWIDTH, 1);
         xtrans.preConcatenate(c2);
         xaxis1.setScreenTransform(xtrans);

         xaxis1.layout(getBounds(x, y, w, h, c2));
         xaxis1.setBounds(getBounds(plotx, ploty-xaxis1h, plotw, xaxis1h, c2));
      }

      if(xaxis2 != null) {
         double x2 = plotx;
         double w2 = plotw;
         AffineTransform xtrans = new AffineTransform();

         xtrans.translate(x2, ploty + ploth);
         xtrans.scale(w2 / GWIDTH, 1);
         xtrans.concatenate(GDefaults.FLIPY);
         xtrans.preConcatenate(c2);
         xaxis2.setScreenTransform(xtrans);
         xaxis2.layout(getBounds(x, y, w, h, c2));
         xaxis2.setBounds(getBounds(plotx, ploty + ploth, plotw, xaxis2h, c2));
      }

      if(yaxis1 != null) {
         AffineTransform ytrans = new AffineTransform();
         double x0 = plotx;

         ytrans.translate(x0, ploty);
         ytrans.rotate(Math.PI / 2);
         ytrans.scale(ploth / GHEIGHT, 1);
         ytrans.concatenate(GDefaults.FLIPY);
         ytrans.preConcatenate(c2);
         yaxis1.setScreenTransform(ytrans);
         yaxis1.layout(getBounds(x, ploty, w, ploth, c2));
         yaxis1.setBounds(getBounds(plotx - yaxis1w, ploty, yaxis1w, ploth, c2));
      }

      /*
      createCoordBorder(vgraph, plotx, ploty, plotw, ploth,
                        plotx, ploty, plotw, ploth, c2);
      */

      layoutText(vgraph, true);
   }

   @Override
   public int getDimCount() {
      return 1;
   }

   @Override
   public Scale[] getScales() {
      return new Scale[] { xscale, yscale, x2scale };
   }

   @Override
   public void createSubGGraph(DataSet rset, DataSetIndex ridx, DataSet dset,
                               EGraph graph, GGraph ggraph, FacetCoord facet, Map xmap, Map ymap,
                               int hint)
   {
      // do nothing
   }

   /**
    * @hidden
    */
   @Override
   public Axis[] getAxes(boolean recursive) {
      if(xaxis1 == null) {
         return new Axis[0];
      }

      return new Axis[] {xaxis1, xaxis2, yaxis1};
   }

   /**
    * @hidden
    */
   @Override
   public DefaultAxis[] getAxesAt(int axis) {
      DefaultAxis da = getAxisAt(axis);

      if(da == null) {
         return new DefaultAxis[0];
      }

      return new DefaultAxis[] {da};
   }

   /**
    * @hidden
    */
   public DefaultAxis getAxisAt(int position) {
      switch(position) {
      case TOP_AXIS:
         return xaxis2;
      case BOTTOM_AXIS:
         return xaxis1;
      case LEFT_AXIS:
         return yaxis1;
      }

      return null;
   }

   /**
    * @hidden
    */
   @Override
   public double getUnitMinWidth() {
      return 200;
   }

   /**
    * @hidden
    */
   @Override
   public double getUnitMinHeight() {
      return 200;
   }

   /**
    * @hidden
    */
   @Override
   public double getUnitPreferredWidth() {
      return 200;
   }

   /**
    * Get unit preferred height.
    * @hidden
    */
   @Override
   public double getUnitPreferredHeight() {
      return 200;
   }

   @Override
   public Object clone(boolean deep) {
      return clone();
   }

   private String var;
   private LinearScale yscale;
   private CategoricalScale xscale;
   private CategoricalScale x2scale;

   private DefaultAxis xaxis1; // bottom
   private DefaultAxis yaxis1; // left
   private DefaultAxis xaxis2; // top
   private static final long serialVersionUID = 1L;
}
