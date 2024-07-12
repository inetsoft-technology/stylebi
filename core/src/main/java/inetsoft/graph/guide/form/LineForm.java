/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.guide.form;

import com.inetsoft.build.tern.*;
import inetsoft.graph.Visualizable;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.AbstractDataSet;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.FormVO;
import inetsoft.graph.visual.LineFormVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;
import java.util.stream.IntStream;

/**
 * This is a line form guide. It can be used to add one or more lines on a
 * graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=LineForm")
public class LineForm extends GeomForm {
   /**
    * Default constructor. The tuples must be added before it's used.
    */
   public LineForm() {
   }

   /**
    * Create a line between two fixed points. The points are in points in the
    * math coordinate.
    */
   public LineForm(Point2D pos1, Point2D pos2) {
      addPoint(pos1);
      addPoint(pos2);
   }

   /**
    * Create a line between the two points. The point is specified as a tuple
    * of values. The values are pre-scale and the scales are applied to obtain
    * the tuples before drawing.
    */
   @TernConstructor
   public LineForm(Object[] v1, Object[] v2) {
      addValues(v1);
      addValues(v2);
   }

   /**
    * Create a line between the two points. A point is specified as a tuple of
    * values. The values are post-scale so it can be specified more precisely
    * in the coordinate space. The number of values in the tuple could
    * be the same as the scales in all nested coordinates, or less than the
    * scales. If the tuple contains less number of values, it is added to all
    * inner graphs.
    */
   public LineForm(double[] tuple1, double[] tuple2) {
      addTuple(tuple1);
      addTuple(tuple2);
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   @Override
   public Visualizable createVisual(Coordinate coord) {
      Point2D[] points = getPoints(coord);
      return createVisual(points);
   }

   /**
    * Create visual objects to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual objects.
    */
   @Override
   public Visualizable[] createVisuals(Coordinate coord) {
      if(tobjs != null && tobjs.size() != 0 || getLineEquation() == null) {
         return super.createVisuals(coord);
      }

      Scale[] scales = getAllScales(coord, false);

      if(scales == null || scales.length == 0) {
         return new Visualizable[0];
      }

      // trend line might generate multiple visualizables
      DataSet data = coord.getDataSet();
      int rowCnt = data.getRowCount();

      Map<LineKey,List<Object[]>> lineTuples = new LinkedHashMap<>();
      Scale lscale = scales[scales.length - 1];
      boolean all = !(lscale instanceof LinearScale);
      String[] fields = null;
      String measure = getMeasure();
      int[] startEndRow = null;
      int projectedRows = (data instanceof AbstractDataSet)
         ? ((AbstractDataSet) data).getRowsProjectedForward() : 0;

      if(all) {
         fields = new String[] {"all"};
      }
      else if(measure != null) {
         fields = new String[] {measure};
         startEndRow = getRowRange(coord, data, measure);
      }
      else {
         List<String> list = new ArrayList<>();

         for(String field : lscale.getFields()) {
            if(field != null && !field.startsWith("__")) {
               list.add(field);
            }
         }

         fields = list.toArray(new String[list.size()]);
      }

      int startRow = 0;
      int unprojectedRowCnt = rowCnt;

      if(startEndRow != null) {
         startRow = startEndRow[0];
         unprojectedRowCnt = Math.min(rowCnt, startEndRow[1]);
         rowCnt = Math.min(rowCnt, startEndRow[1] + projectedRows);
      }

      for(int i = startRow; i < rowCnt; i++) {
         // if projected rows, we should get the real projected rows at the end of the
         // dataset instead of the next rows of the current range. (48394)
         int row = i < unprojectedRowCnt ? i
            : i - unprojectedRowCnt + data.getRowCount() - projectedRows;

         for(int j = 0; j < fields.length; j++) {
            LineKey key = createKey(data, fields[j], row);
            List<Object[]> tuObjs = lineTuples.get(key);

            // lines generated for each LineKey (e.g. color dimension value for per color).
            if(tuObjs == null) {
               lineTuples.put(key, tuObjs = new ArrayList<>());
               Color lineColor = getColor();

               // check color frame first
               if(lineColor == null && colors != null) {
                  lineColor = colors.getColor(data, getMeasure(), row);
               }

               if(lineColor != null) {
                  key.color = lineColor;
               }
            }

            Object[] tobj = new Object[scales.length];
            boolean nullExists = false;

            for(int k = 0; k < scales.length; k++) {
               Scale scale = scales[k];
               String[] flds = scale.getFields();
               String field = (all || scale != lscale) && flds.length > 0 ? flds[0] : fields[j];
               Object obj = data.getData(field, row);

               if(obj == null && scale instanceof LinearScale && !nullExists) {
                  nullExists = true;
               }
               else {
                  tobj[k] = obj;
               }
            }

            if(!nullExists) {
               tuObjs.add(tobj);
            }

            if(projectedRows > 0) {
               // @by ChrisSpagnoli bug1433223219325 2015-6-2
               // Set maxtuple only for tobj which does not contain all nulls
               for(int k = 0; k < scales.length; k++) {
                  if(tobj[k] != null) {
                     maxtuple = scale(tobj, coord);

                     // ignore null in measure. (58095)
                     if(maxtuple.length > 1 && Double.isNaN(maxtuple[maxtuple.length - 2])) {
                        maxtuple = null;
                     }

                     break;
                  }
               }
            }
         }
      }

      List<Visualizable> vobjs = new ArrayList<>();
      HashMap<TupleKey,Double> tuplesPrevPos = new HashMap<>();
      HashMap<TupleKey,Double> tuplesPrevNeg = new HashMap<>();

      for(LineKey key : lineTuples.keySet()) {
         List<Object[]> tuObjs = lineTuples.get(key);
         List<double[]> tuples = new ArrayList<>();

         for(int i = 0; i < tuObjs.size(); i++) {
            double[] tuple = scale(tuObjs.get(i), coord);

            // @by ChrisSpagnoli bug1431601287124 2015-5-21
            // Handle trend lines on stacked chart, by adding tuple with previous
            if(isStackChart()) {
               TupleKey tupleKey = new TupleKey(tuple);
               double val = tuple[tuple.length-1];

               // @by ChrisSpagnoli bug1432688330775 2015-5-28
               // Accumulate the positive and negative "stack" values separately
               if(val > 0) {
                  if(tuplesPrevPos.containsKey(tupleKey)) {
                     val += tuplesPrevPos.get(tupleKey);
                     tuplesPrevPos.put(tupleKey, val);
                  }
                  else {
                     tuplesPrevPos.put(tupleKey, val);
                  }
               }
               else {
                  if(tuplesPrevNeg.containsKey(tupleKey)) {
                     val += tuplesPrevNeg.get(tupleKey);
                     tuplesPrevNeg.put(tupleKey, val);
                  }
                  else {
                     tuplesPrevNeg.put(tupleKey, val);
                  }
               }

               tuple[tuple.length - 1] = val;
            }

            tuples.add(tuple);
         }

         Visualizable visual = createVisual(tuples, coord, key.color, maxtuple);

         if(visual != null && coord instanceof RectCoord) {
            vobjs.add(visual);

            Point2D[] points = ((LineFormVO) visual).getPoints();
            Scale xscale = ((RectCoord) coord).getXScale();
            Scale yscale = ((RectCoord) coord).getYScale();
            DataSet dataset = coord.getDataSet();
            int col = dataset.indexOfHeader(getMeasure());

            if(yscale != null && col >= 0 && dataset instanceof AbstractDataSet &&
               xscale != null && xscale.getFields().length > 0)
            {
               String xfield = xscale.getFields()[0];
               double min = yscale.getMin();
               double max = yscale.getMax();
               final int startRow0 = startRow;
               final int rowCnt0 = rowCnt;
               double[] yvalues = IntStream.range(startRow, startRow + rowCnt)
                  .mapToDouble(i -> (i - startRow0) * (points.length - 1) * 1.0 / (rowCnt0 - 1))
                  .map(i -> {
                     double y1 = points[(int) i].getY();
                     double y2 = points[(int) Math.ceil(i)].getY();
                     return y1 + (y2 - y1) * (i - (int) i);
                  })
                  .map(y -> (y / 1000) * (max - min) + min)
                  .toArray();

               int cnt = Math.min(startRow + rowCnt, yvalues.length);
               int firstProjected = rowCnt - projectedRows;
               List<Map> projectedValues = new ArrayList<>();
               boolean projected = false;

               for(int i = firstProjected; i < cnt; i++) {
                  Map values = new HashMap();

                  for(int k = 0; k < gfields.length; k++) {
                     values.put(gfields[k], key.gvalues.get(k));
                  }

                  Object xvalue = dataset.getData(xfield, i);
                  values.put(xfield, xvalue);
                  values.put(getMeasure(), yvalues[i]);
                  projectedValues.add(values);

                  projected = projected || xvalue != null;
               }

               // if data is not projected successfully, don't add them to the reset. (49765)
               if(projected) {
                  for(Map values : projectedValues) {
                     ((AbstractDataSet) dataset).addProjectedValue(values);
                  }
               }
            }
         }
      }

      return vobjs.toArray(new Visualizable[vobjs.size()]);
   }

   // @by ChrisSpagnoli bug1431601287124 2015-5-21
   private class TupleKey {
      public TupleKey(double[] t) {
         for(int i = 0; i < (t.length-1); i++) {
            tuple.add(t[i]);
         }
      }

      @Override
      public int hashCode() {
         int ret = 0;

         for(double t:tuple) {
            ret = (int)(ret * 1024 + t * 10);
         }

         return ret;
      }

      @Override
      public boolean equals(Object o) {
         if(!(o instanceof TupleKey)) {
            return false;
         }

         TupleKey tk = (TupleKey)o;

         if(tk.getTuple().size() != tuple.size()) {
            return false;
         }

         for(int i = 0; i < tuple.size(); i++) {
            if(tk.getTuple().get(i).doubleValue() != tuple.get(i).doubleValue()) {
               return false;
            }
         }

         return true;
      }

      public ArrayList<Double> getTuple() {
         return tuple;
      }

      @Override
      public String toString() {
         return tuple.toString();
      }

      private ArrayList<Double> tuple = new ArrayList<>();
   }

   /**
    * Create a key to uniquely identify a line.
    */
   private LineKey createKey(DataSet data, String field, int r) {
      LineKey key = new LineKey(field);

      for(String gfield : gfields) {
         key.gvalues.add(data.getData(gfield, r));
      }

      return key;
   }

   /**
    * Create a visual object to visualize this element.
    * @param coord the coordinate the visual object is plotted on.
    * @return the new visual object.
    */
   // @by ChrisSpagnoli bug1431601287124 2015-5-19
   // Split createVisual
   private Visualizable createVisual(List<double[]> tuples, Coordinate coord,
                                     Color color, double[] maxtuple)
   {
      Point2D[] points = null;

      // @by: ChrisSpagnoli bug1421823559175 2015-1-21
      try {
         points = getPoints(coord, tuples, maxtuple);
      }
      catch(Exception ex) {
         LOG.info(ex.getMessage(), ex);
         return null;
      }

      if(points == null || points.length == 0) {
         return null;
      }

      FormVO formvo = (FormVO) createVisual(points);
      formvo.setColor(color);
      return formvo;
   }

   /**
    * Create a visual object.
    * @param points the specified points.
    * @return the new visual object.
    */
   private Visualizable createVisual(Point2D[] points) {
      LineFormVO line = new LineFormVO(this, points);

      line.setZIndex(getZIndex());
      line.setFixedPosition(isFixedPosition());
      line.setColor(getColor());
      return line;
   }

   /**
    * Get the points on the line.
    */
   private Point2D[] getPoints(Coordinate coord) {
      if(points != null && points.size() > 0) {
         return points.toArray(new Point2D[points.size()]);
      }

      if(tobjs != null && tobjs.size() > 0) {
         tuples = new ArrayList<>();
         tuples.clear();

         // map default tobjs to default tuples
         for(int i = 0; i < tobjs.size(); i++) {
            double[] tuple = scale(tobjs.get(i), coord);
            tuples.add(tuple);
         }
      }

      // map default tuples to points
      return getPoints(coord, tuples, maxtuple);
   }

   /**
    * Get the points on the line.
    */
   private Point2D[] getPoints(Coordinate coord, List<double[]> tuples, double[] maxtuple) {
      Point2D[] points = new Point2D[tuples != null ? tuples.size() : 0];

      for(int i = 0; i < points.length; i++) {
         double[] tuple = tuples.get(i);
         Point2D pt = getPosition(coord, tuple);

         if(pt == null) {
            return new Point2D[0];
         }

         points[i] = pt;
      }

      LineEquation equation = getLineEquation();
      // @by: ChrisSpagnoli feature1379102629417 2015-1-10
      // pass the xmax from the projected X-Axis down to trend line equation.
      double xmax = Double.NEGATIVE_INFINITY;

      if(maxtuple != null) {
         xmax = coord.getPosition(maxtuple).getX();
      }

      if(equation != null) {
         equation.setXmax(xmax);

         // skip missing points (null) which shouldn't be used in trend calculation
         points = Arrays.stream(points)
            .filter(p -> !Double.isNaN(p.getY()) && !Double.isNaN(p.getX()))
            .toArray(Point2D[]::new);

         points = equation.calculate(points);
      }

      // @by ChrisSpagnoli bug1434331259187 2015-6-15
      if(isInPlot()) {
         double minLineYscreen = Double.POSITIVE_INFINITY;
         double maxLineYscreen = Double.NEGATIVE_INFINITY;

         for(int i = 0; i < points.length; i++) {
            if(!Double.isNaN(points[i].getY())) {
               minLineYscreen = Math.min(minLineYscreen, points[i].getY());
               maxLineYscreen = Math.max(maxLineYscreen, points[i].getY());
            }
         }

         coord.setTrendLineMin(minLineYscreen);
         coord.setTrendLineMax(maxLineYscreen);
      }

      return points;
   }

   /**
    * Set an equation to calculate the final line/curve from the points.
    * For example, the equation could create a curve and fit the points
    * to a straight line.
    * @hidden
    */
   public void setLineEquation(LineEquation equation) {
      this.equation = equation;
   }

   /**
    * Get the line equation.
    * @hidden
    */
   public LineEquation getLineEquation() {
      return equation;
   }

   /**
    * Get the tuple (scaled values) for the specified point.
    */
   @TernMethod
   public double[] getTuple(int idx) {
      return tuples != null ? tuples.get(idx) : null;
   }

   /**
    * Get the number of tuples.
    */
   @TernMethod
   public int getTupleCount() {
      return tuples != null ? tuples.size() : 0;
   }

   /**
    * Add a tuple in scaled tuple values.
    */
   @TernMethod
   public void addTuple(double[] tuple) {
      if(tuples == null) {
         tuples = new ArrayList<>();
      }

      tuples.add(tuple);
      staticTuples = true;
   }

   /**
    * Get the unscaled value tuple.
    */
   @TernMethod
   public Object[] getValues(int idx) {
      return tobjs != null ? tobjs.get(idx) : null;
   }

   /**
    * Get the number of value tuples.
    */
   @TernMethod
   public int getValuesCount() {
      return tobjs != null ? tobjs.size() : 0;
   }

   /**
    * Add a tuple in unscaled tuple values.
    */
   @TernMethod
   public void addValues(Object[] tobj) {
      if(tobjs == null) {
         tobjs = new ArrayList<>();
      }

      tobjs.add(GTool.unwrapArray(tobj));
   }

   /**
    * Get the specified fixed position point.
    */
   @TernMethod
   public Point2D getPoint(int idx) {
      return points != null ? points.get(idx) : null;
   }

   /**
    * Get the number of fixed position points.
    */
   @TernMethod
   public int getPointCount() {
      return points != null ? points.size() : 0;
   }

   /**
    * Add a fixed position point.
    * @param point the point location in graph. If the value is between 0 and
    * 1 (non-inclusive), it's treated as a proportion of the width/height. If the
    * value is negative, it's the distance from the right/top of the graph.
    */
   @TernMethod
   public void addPoint(Point2D point) {
      if(points == null) {
         points = new ArrayList<>();
      }

      points.add(point);
   }

   /**
    * Check if an arrow should be drawn at the starting point.
    */
   @TernMethod
   public boolean isStartArrow() {
      return startarrow;
   }

   /**
    * Set if an arrow should be drawn at the starting point.
    */
   @TernMethod
   public void setStartArrow(boolean arrow) {
      this.startarrow = arrow;
   }

   /**
    * Check if an arrow should be drawn at the end point.
    */
   @TernMethod
   public boolean isEndArrow() {
      return endarrow;
   }

   /**
    * Set if an arrow should be drawn at the end point.
    */
   @TernMethod
   public void setEndArrow(boolean arrow) {
      this.endarrow = arrow;
   }

   /**
    * Check if form is at fixed position.
    * @hidden
    */
   @Override
   public boolean isFixedPosition() {
      return getPointCount() > 0;
   }

   /**
    * Set the fields for grouping values into lines. For example, if 'State'
    * is a group field, values for each state is grouped into its own line.
    * @hidden
    */
   public void setGroupFields(String... fields) {
      this.gfields = (fields == null) ? new String[0] : fields;
   }

   /**
    * Get the fields for grouping values into lines.
    * @hidden
    */
   public String[] getGroupFields() {
      return gfields;
   }

   /**
    * Set the colors for drawing trend lines. This frame is only used if
    * lines are created from line equation.
    * @hidden
    */
   public void setColorFrame(ColorFrame colors) {
      this.colors = colors;

      //bug1335343143401, StaticColorFrame need update color.
      if(colors instanceof StaticColorFrame) {
         setColor(((StaticColorFrame) colors).getColor());
      }
   }

   /**
    * Get the colors for drawing trend lines.
    * @hidden
    */
   public ColorFrame getColorFrame() {
      return colors;
   }

   /**
    * Line key.
    */
   private static class LineKey {
      public LineKey(String field) {
         this.field = field;
      }

      public boolean equals(Object obj) {
         LineKey key = (LineKey) obj;

         return field.equals(key.field) && gvalues.equals(key.gvalues);
      }

      public int hashCode() {
         return field.hashCode() + gvalues.hashCode();
      }

      public String toString() {
         return "LineKey[" + field + ":" + gvalues + "]";
      }

      String field;
      List gvalues = new ArrayList();
      Color color = GDefaults.DEFAULT_LINE_COLOR;
   }

   // @by ChrisSpagnoli bug1431601287124 2015-5-21
   /**
    * Check if this field on the chart is "stacked".
    */
   @TernMethod
   public boolean isStackChart() {
      return stackChart;
   }

   /**
    * Set whether if this field on the chart is "stacked".
    */
   @TernMethod
   public void setStackChart(boolean stack) {
      this.stackChart = stack;
   }

   @Override
   public void layoutCompleted() {
      // clear dynamically generated info to conserve memory.
      if(!staticTuples) {
         tuples = null;
      }
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      LineForm form = (LineForm) obj;
      return Objects.equals(tuples, form.tuples) &&
         Objects.equals(tobjs, form.tobjs) &&
         Objects.equals(points, form.points) &&
         Objects.deepEquals(gfields, form.gfields) &&
         Objects.equals(equation, form.equation) &&
         Objects.equals(colors, form.colors) &&
         startarrow == form.startarrow &&
         endarrow == form.endarrow &&
         Objects.deepEquals(maxtuple, form.maxtuple) &&
         stackChart == form.stackChart;
   }

   private List<double[]> tuples;
   private List<Object[]> tobjs;
   private List<Point2D> points;
   private String[] gfields = {};
   private LineEquation equation;
   private ColorFrame colors;
   private boolean startarrow = false;
   private boolean endarrow = false;
   private double[] maxtuple;
   private boolean stackChart = false;
   private boolean staticTuples = false;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(LineForm.class);
}
