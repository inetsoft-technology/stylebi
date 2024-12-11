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
package inetsoft.graph.element;

import com.inetsoft.build.tern.*;
import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.*;
import inetsoft.graph.geometry.LineGeometry;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;
import inetsoft.util.Tool;
import org.apache.commons.lang3.ArrayUtils;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * A line element is used to add line visualization to a graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=LineElement")
public class LineElement extends StackableElement {
   public enum Type {
      STRAIGHT, STEP, JUMP
   }

   /**
    * Option to control what fields are used to group points into lines.
    */
   public enum LineGroup {
      AESTHETIC(1), GROUP(2), ALL(3), NONE(0);

      LineGroup(int mask) {
         this.mask = mask;
      }

      private int mask;
   }

   /**
    * Create an empty element. Dims and vars must be added explicitly.
    */
   public LineElement() {
   }

   /**
    * Create a line element for a single column (1d).
    */
   public LineElement(String field1) {
      addVar(field1);
   }

   /**
    * Create a line element for two columns (2d).
    */
   public LineElement(String field1, String field2) {
      addDim(field1);
      addVar(field2);
  }

   /**
    * Create a line element for three columns (3d).
    */
   @TernConstructor
   public LineElement(String field1, String field2, String field3) {
      addDim(field1);
      addDim(field2);
      addVar(field3);
   }

   /**
    * Set the line is closed (start-end points connected).
    */
   @TernMethod
   public void setClosed(boolean closed) {
      this.closed = closed;
   }

   /**
    * Check if the line is closed (start-end points connected).
    */
   @TernMethod
   public boolean isClosed() {
      return closed;
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
    * Create the geometry objects for the chartLens.
    * @hidden
    * @param data the chartLens to plot using this element.
    * @param graph the containing graph.
    */
   @Override
   public void createGeometry(DataSet data, GGraph graph) {
      SortedDataSet sdata = sortData(data, graph, getVarCount() > 0 && isStackGroup());

      if(sdata != null) {
         sorted = sdata != data;
         data = getSortedDataSetInRange(data, sdata);
      }

      VisualModel vmodel = createVisualModel(data);
      EGraph egraph = graph.getEGraph();
      boolean negGrp = isStackNegative();
      boolean stackGroup = isStackGroup();
      boolean stack = isStack();
      int max = getEndRow(data);

      // if no var, that mains the values are plotted on axes and we will
      // create one tuple for each dimension value instead of one per var
      if(getVarCount() == 0) {
         Coordinate coord = graph.getCoordinate();
         Set fields = getScaleFields(coord.getScales());
         Map<String, LineGeometry> geomap = new HashMap<>();

         for(int i = getStartRow(data); i < max; i++) {
            if(!isAccepted(data, i)) {
               continue;
            }

            LineGeometry gobj = createGeometry(this, graph, null, vmodel);
            String vname = "";

            for(int v = 0; v < getDimCount(); v++) {
               String dname = getDim(v);

               if(fields.contains(dname)) {
                  double[] tuple = scale(data, i, -(v + 1), graph);

                  if(tuple == null) {
                     continue;
                  }

                  addTuple(gobj, tuple, v, data, i, egraph);
                  vname = dname;
               }
            }

            int sidx = sdata == null ? i : sdata.getBaseRow(i);
            int ridx = getRootRowIndex(data, i);

            gobj.setTupleIndexes(new int[] {i});
            gobj.setSubRowIndexes(new int[] { sidx});
            gobj.setRowIndexes(new int[] {ridx});

            if(isRadar() && !"".equals(vname)) {
               // measures are plotted on axes, try to get one of measure name
               gobj.setColIndex(getRootColIndex(data, vname));
            }

            gobj.setClosed(isClosed());
            geomap.put(i + "", gobj);
         }

         // add geometries in sorted order
         for(String vname : sortGeometry(geomap)) {
            LineGeometry gobj = geomap.get(vname);
            graph.addGeometry(gobj);
         }
      }
      // not stack group, all points drawn as a single line
      else if(!stackGroup) {
         double top = 0, negtop = 0;
         Map<String, LineGeometry> geomap = new HashMap<>();
         Map<String, List<Integer>> tidxmap = new HashMap<>();
         Map<String, List<Integer>> rrowmap = new HashMap<>();
         Map<String, List<Integer>> srowmap = new HashMap<>();

         for(int v = 0; v < getVarCount(); v++) {
            String vname = getVar(v);
            LineGeometry gobj = createGeometry(this, graph, vname, vmodel);
            List<Integer> rrows = new ArrayList<>(); // root rows
            List<Integer> srows = new ArrayList<>(); // sub rows
            List<Integer> tidxs = new ArrayList<>(); // tuple indexes

            geomap.put(vname, gobj);
            rrowmap.put(vname, rrows);
            srowmap.put(vname, srows);
            tidxmap.put(vname, tidxs);
         }

         for(int i = getStartRow(data); i < max; i++) {
            if(!isAccepted(data, i)) {
               continue;
            }

            for(int v = 0; v < getVarCount(); v++) {
               String vname = getVar(v);
               LineGeometry gobj = geomap.get(vname);
               List<Integer> rrows = rrowmap.get(vname);
               List<Integer> srows = srowmap.get(vname);
               List<Integer> tidxs = tidxmap.get(vname);
               Scale scale = null;

               // support stack line for line element
               if(vname != null && stack) {
                  scale = graph.getScale(vname);
               }

               double[] tuple = scale(data, i, v, graph);

               if(tuple == null) {
                  continue;
               }

               if(scale != null) {
                  double val = scale.map(data.getData(vname, i));

                  if(val < 0 && negGrp) {
                     negtop = scale.add(negtop, val);
                     tuple[tuple.length - 1] = negtop;
                  }
                  else {
                     top = scale.add(top, val);
                     tuple[tuple.length - 1] = top;
                  }
               }

               int srow = sdata == null ? i : sdata.getBaseRow(i);

               tidxs.add(i);
               srows.add(srow);
               rrows.add(getRootRowIndex(data, i));
               addTuple(gobj, tuple, v, data, i, egraph);
            }
         }

         // add geometries in sorted order
         for(String vname : sortGeometry(geomap)) {
            LineGeometry gobj = geomap.get(vname);
            List<Integer> rrows = rrowmap.get(vname);
            List<Integer> srows = srowmap.get(vname);
            List<Integer> tidxs = tidxmap.get(vname);

            gobj.setTupleIndexes(tidxs.stream().mapToInt(i -> i).toArray());
            gobj.setSubRowIndexes(srows.stream().mapToInt(i -> i).toArray());
            gobj.setRowIndexes(rrows.stream().mapToInt(i -> i).toArray());
            gobj.setColIndex(getRootColIndex(data, gobj.getVar()));
            gobj.setClosed(isClosed());
            graph.addGeometry(gobj);
         }
      }
      // stack group, each point at a x position drawn on a separate line
      // whether the values are stacked is controlled by collisionModifier
      else {
         Coordinate coord = graph.getCoordinate();
         Map<String, BitSet> groupMap = new HashMap<>();
         Map<Integer, String> rowMap = new HashMap<>();
         Map<String, LineGeometry> geomap = new HashMap<>();

         initGroupMap(data, groupMap, rowMap);

         for(int v = 0; v < getVarCount(); v++) {
            String vname = getVar(v);

            for(String key: groupMap.keySet()) {
               String vkey = key + "|" + vname;
               LineGeometry gobj = createGeometry(this, graph, vname, vmodel);
               geomap.put(vkey, gobj);
            }
         }

         for(String key: groupMap.keySet()) {
            prepareDataSet(data, groupMap.get(key));
         }

         double top = 0, negtop = 0;
         double groupIdx = 0;
         Map<String, BitSet> nullMap = new HashMap<>();
         boolean first = true;

         for(int i = getStartRow(data); i < max; i++) {
            if(!isAccepted(data, i)) {
               continue;
            }

            for(int v = 0; v < getVarCount(); v++) {
               String vname = getVar(v);
               Scale scale = null;

               // support stack line for line element
               if(vname != null) {
                  scale = graph.getScale(vname);
               }

               double[] tuple = scale(data, i, v, graph);
               String key = rowMap.get(i) + "|" + vname;

               if(tuple == null) {
                  BitSet nulls = nullMap.get(key);

                  if(nulls == null) {
                     nulls = new BitSet();
                     nullMap.put(key, nulls);
                  }

                  nulls.set(i);
                  continue;
               }

               if(scale != null) {
                  double val = scale.map(data.getData(vname, i));

                  if(groupIdx != getGroupIndex(coord, tuple)) {
                     // always reset top and negtop
                     top = 0;
                     negtop = 0;

                     if(val < 0 && negGrp) {
                        negtop = val;
                     }
                     else {
                        top = val;
                     }

                     groupIdx = getGroupIndex(coord, tuple);
                     first = true;
                  }
                  else {
                     double interval = IntervalElement.getInterval(scale, negtop, val, 0, first);

                     if(val < 0 && negGrp) {
                        negtop = scale.add(negtop, interval);
                     }
                     else {
                        top = scale.add(top, interval);
                     }
                  }

                  if(!stack || Double.isNaN(val)) {
                     tuple[tuple.length - 1] = val;
                  }
                  else if(val < 0 && negGrp) {
                     tuple[tuple.length - 1] = negtop;
                  }
                  else {
                     tuple[tuple.length - 1] = top;
                  }
               }

               LineGeometry gobj = geomap.get(key);
               addTuple(gobj, tuple, v, data, i, egraph);
               first = false;
            }
         }


         // add geometries in sorted order
         for(String vkey : sortGeometry(geomap)) {
            String key = vkey.substring(0, vkey.lastIndexOf("|"));
            BitSet keyRows = groupMap.get(key);
            BitSet nulls = nullMap.get(vkey);
            LineGeometry gobj = geomap.get(vkey);
            List<Integer> rrows = new ArrayList<>(); // root rows
            List<Integer> srows = new ArrayList<>(); // sub rows
            List<Integer> tidxs = new ArrayList<>(); // tuple index (row in dataset)

            for(int i = keyRows.nextSetBit(0); i >= 0; i = keyRows.nextSetBit(i + 1)) {
               if(nulls != null && nulls.get(i)) {
                  continue;
               }

               int srow = sdata == null ? i : sdata.getBaseRow(i);

               tidxs.add(i);
               srows.add(srow);
               rrows.add(getRootRowIndex(data, i));
            }

            gobj.setTupleIndexes(tidxs.stream().mapToInt(i -> i).toArray());
            gobj.setRowIndexes(rrows.stream().mapToInt(i -> i).toArray());
            gobj.setColIndex(getRootColIndex(data, gobj.getVar()));
            gobj.setSubRowIndexes(srows.stream().mapToInt(i -> i).toArray());
            gobj.setClosed(isClosed());
            graph.addGeometry(gobj);
         }
      }
   }

   /**
    * Sort dataset to prepare for generating visual objects.
    */
   @Override
   public SortedDataSet sortData(DataSet data, GGraph graph, boolean isStack) {
      String[] sortfields = getSortFields();

      if(sortfields.length > 0) {
         return createSortedDataSet(data, sortfields);
      }

      return super.sortData(data, graph, isStack);
   }

   /**
    * Prepare the calc column for a group.
    */
   private void prepareDataSet(DataSet data, BitSet rows) {
      if(getDimCount() == 0 || rows == null) {
         return;
      }

      data = ((SortedDataSet) data).getDataSet();
      List<Integer> idxs = new ArrayList<>();

      for(int i = rows.nextSetBit(0); i >= 0; i = rows.nextSetBit(i + 1)) {
         idxs.add(i);
      }

      int[] arr = new int[idxs.size()];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = idxs.get(i);
      }

      data.prepareCalc(getDim(getDimCount() - 1), arr, true);
   }

   /**
    * Get the group (x) index.
    */
   private double getGroupIndex(Coordinate coord, double[] tuple) {
      return (coord.getDimCount() < 2) ? 0.0 : coord.getValue(tuple, 0);
   }

   /**
    * Add a tuple to geometry.
    */
   void addTuple(LineGeometry gobj, double[] tuple, int vidx,
                 DataSet data, int row, EGraph egraph)
   {
      gobj.addTuple(tuple);
   }

   /**
    * Create the geometry object.
    */
   LineGeometry createGeometry(LineElement elem, GGraph graph, String vname,
                               VisualModel vmodel)
   {
      return new LineGeometry(elem, graph, vname, vmodel);
   }

   /**
    * Optionally sort the geometries.
    */
   Collection<String> sortGeometry(Map<String,LineGeometry> map) {
      return map.keySet();
   }

   /**
    * Get all fields used by scales.
    */
   private static Set getScaleFields(Scale[] scales) {
      Set fields = new HashSet();

      if(scales == null) {
         return fields;
      }

      for(int i = 0; i < scales.length; i++) {
         String[] flds = scales[i].getFields();

         for(int j = 0; j < flds.length; j++) {
            fields.add(flds[j]);
         }
      }

      return fields;
   }

   /**
    * Create the key of the element visual object for stack line, area.
    * @param groupMap populated with key->row index BitSet
    * @param rowMap populated with row index->key
    */
   private void initGroupMap(DataSet data, Map<String, BitSet> groupMap,
                             Map<Integer, String> rowMap)
   {
      int cnt = getEndRow(data);

      for(int i = getStartRow(data); i < cnt; i++) {
         if(!isAccepted(data, i)) {
            continue;
         }

         String key = getLineGroupKey(data, i);
         BitSet rows = groupMap.get(key);

         if(rows == null) {
            groupMap.put(key, rows = new BitSet());
         }

         rows.set(i);
         rowMap.put(i, key);
      }
   }

   private String getLineGroupKey(DataSet data, int row) {
      initAesthetics(data);
      StringBuilder sb = new StringBuilder();

      for(int j = 0; j < aesthetics.size(); j++) {
         if(sb.length() > 0) {
            sb.append(',');
         }

         Object obj = data.getData(aesthetics.get(j), row);
         sb.append(GTool.toString(obj));
      }

      return sb.toString();
   }

   /**
    * Get the categorical frame.
    */
   private static VisualFrame getInnerFrame(VisualFrame frame) {
      if(frame instanceof CompositeVisualFrame) {
         CompositeVisualFrame cframe = (CompositeVisualFrame) frame;

         for(int i = 0; i < cframe.getFrameCount(); i++) {
            VisualFrame tframe = cframe.getFrame(i);

            if(tframe.getField() != null) {
               return tframe;
            }
         }
      }

      return frame;
   }

   // initialize list of aesthetics used for grouping data.
   private void initAesthetics(DataSet data) {
      if(aesthetics == null) {
         aesthetics = new ArrayList<>();
      }

      if((getLineGroup().mask & LineGroup.AESTHETIC.mask) != 0) {
         VisualFrame[] frames = {
            getColorFrame(), getSizeFrame(), getShapeFrame(),
            getTextFrame(), getLineFrame(), getTextureFrame() };

         for(VisualFrame frame : frames) {
            addAestheticDim(data, frame, aesthetics);
         }
      }

      if((getLineGroup().mask & LineGroup.GROUP.mask) != 0) {
         for(String gfield : gfields) {
            if(!aesthetics.contains(gfield)) {
               aesthetics.add(gfield);
            }
         }
      }
   }

   /**
    * Add a aesthetic dim field to aesthetic vector.
    */
   private void addAestheticDim(DataSet data, VisualFrame frame, List<String> aesthetics) {
      // it's possible to use a linear color frame (e.g. Heat) for dimension
      // such as year so we shouldn't restrict it to categorical frames
      VisualFrame cframe = getInnerFrame(frame);
      String fld = (cframe == null) ? null : cframe.getField();

      if(fld != null && !aesthetics.contains(fld)) {
         // binding measure to text should not break lines
         if(cframe instanceof TextFrame) {
            // if field is already a dimension, don't break the line
            // for that dimension
            if(!data.isMeasure(fld) && !isDim(fld)) {
               aesthetics.add(fld);
            }
         }
         // break lines on categorical fields
         else if(cframe instanceof CategoricalFrame || !data.isMeasure(fld)) {
            aesthetics.add(fld);
         }
      }
   }

   /**
    * Check if the field is a dimension.
    */
   private boolean isDim(String fld) {
      Object mapTextDim = getHint("_map_text_dim_");

      if(mapTextDim != null && Tool.equals(fld, mapTextDim.toString())) {
         return true;
      }

      Object[] geofields = (Object[]) getHint("geofields");
      Object[][] arrs = (geofields != null)
         ? new Object[][] {getDims(), geofields} : new Object[][] {getDims()};

      for(Object[] arr : arrs) {
         for(Object dim : arr) {
            if(dim.equals(fld)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if the aesthetic frame is supported. Area element doesn't support
    * shape frame.
    */
   @Override
   public boolean supportsFrame(VisualFrame frame) {
      if(!super.supportsFrame(frame)) {
         return false;
      }

      return !(frame instanceof ShapeFrame) && !(frame instanceof TextureFrame);
   }

   /**
    * Skip a null pointer in line by not ignoring the null value in processing.
    */
   @Override
   boolean isIncludeNull(DataSet data, int row, String col) {
      Object fval = getHint("line.data.from");
      Object eval = getHint("line.data.to");

      // for brush data set, only null values between self data is included
      if(fval instanceof Integer && eval instanceof Integer) {
         int from = (Integer) fval;
         int end = (Integer) eval;

         if(sorted && data instanceof DataSetFilter) {
            row = ((DataSetFilter) data).getBaseRow(row);
         }

         return row >= from && row < end;
      }

      return true;
   }

   /**
    * Set the fields for grouping values into lines. For example, if 'State'
    * is a group field, values for each state is grouped into its own line. This is
    * in addition to the dimensions specified throw visual frames.
    */
   @TernMethod
   public void setGroupFields(String... fields) {
      this.gfields = (fields == null) ? new String[0] : fields;
   }

   /**
    * Get the fields for grouping values into lines.
    */
   @TernMethod
   public String[] getGroupFields() {
      return gfields;
   }

   @Override
   protected String[] getAllGroupFields() {
      return ArrayUtils.addAll(super.getAllGroupFields(), getGroupFields());
   }

   /**
    * Set whether to ignore null is set to true (default to false). If true, the null values
    * will be ignored instead of leaving a gap in the line.
    */
   @TernMethod
   public void setIgnoreNull(boolean ignoreNull) {
      this.ignoreNull = ignoreNull;
   }

   /**
    * Check if null values are ignored.
    */
   @TernMethod
   public boolean isIgnoreNull() {
      return ignoreNull;
   }

   /**
    * Get the line style used to fill the missing (null) data point.
    */
   @TernMethod
   public int getFillLineStyle() {
      return fillLineStyle;
   }

   /**
    * Set the line style used to fill the missing (null) data point.
    */
   @TernMethod
   public void setFillLineStyle(int fillLineStyle) {
      this.fillLineStyle = fillLineStyle;
   }

   /**
    * Get the alpha to use for the fline to fill missing data point.
    */
   @TernMethod
   public double getFillLineAlpha() {
      return fillLineAlpha;
   }

   /**
    * Set the alpha to use for the fline to fill missing data point. The default is 0.5.
    */
   @TernMethod
   public void setFillLineAlpha(double fillLineAlpha) {
      this.fillLineAlpha = fillLineAlpha;
   }

   @TernMethod
   public boolean isRadar() {
      return getVarCount() == 0 && getDimCount() > 0;
   }

   /**
    * Check which fields are used for goruping points into lines.
    */
   @TernMethod
   public LineGroup getLineGroup() {
      return lineGroup;
   }

   /**
    * Set which fields are used for goruping points into lines.
    */
   @TernMethod
   public void setLineGroup(LineGroup lineGroup) {
      this.lineGroup = lineGroup;
   }

   /**
    * Get the type of line.
    */
   @TernMethod
   public Type getType() {
      return type;
   }

   /**
    * Set the type of line.
    */
   @TernMethod
   public void setType(Type type) {
      this.type = type;
   }

   /**
    * Get the line outline color.
    */
   @TernMethod
   public Color getOutlineColor() {
      return outlineColor;
   }

   /**
    * Set the line outline color. If set, a outer border is drawn on the sides of a line.
    */
   @TernMethod
   public void setOutlineColor(Color outlineColor) {
      this.outlineColor = outlineColor;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(obj instanceof LineElement) {
         LineElement elem = (LineElement) obj;
         return closed == elem.closed && startarrow == elem.startarrow &&
            endarrow == elem.endarrow && ignoreNull == elem.ignoreNull &&
            lineGroup == elem.lineGroup && type == elem.type &&
            Arrays.equals(gfields, elem.gfields) &&
            Objects.equals(outlineColor, elem.outlineColor);
      }

      return false;
   }

   private transient ArrayList<String> aesthetics = new ArrayList<>();
   private boolean closed = false;
   private boolean startarrow = false;
   private boolean endarrow = false;
   private boolean ignoreNull = false;
   private int fillLineStyle = GraphConstants.DASH_LINE;
   private double fillLineAlpha = 0.3;
   private boolean sorted = false; // data set sorted
   private String[] gfields = {};
   private LineGroup lineGroup = LineGroup.ALL;
   private Type type = Type.STRAIGHT;
   private Color outlineColor = null;
   private static final long serialVersionUID = 1L;
}
