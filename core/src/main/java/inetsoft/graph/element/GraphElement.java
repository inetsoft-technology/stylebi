/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.element;

import com.inetsoft.build.tern.TernField;
import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.*;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * A graph element is an object that can be added to a graph to create a
 * visual perception of data. For example, a PointElement can be added to
 * a graph to draw points for data tuples.
 * <p>
 * A graph element can plot a graph for each variable. An element shares
 * a a set of dimensions, and plot a graph for each var(iable).
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class GraphElement extends Graphable {
   /**
    * No collision resolution.
    */
   @TernField
   public static final int MOVE_NONE = 0;
   /**
    * Center the objects at tick position.
    */
   @TernField
   public static final int MOVE_CENTER = 1;
   /**
    * Move objects side ways to avoid overlapping.
    */
   @TernField
   public static final int MOVE_DODGE = 2;
   /**
    * Stack objects.
    */
   @TernField
   public static final int MOVE_STACK = 4;
   /**
    * Randomly shift points to reduce overlapping.
    */
   @TernField
   public static final int MOVE_JITTER = 8;
   /**
    * Move the bar to the middle (vertically).
    */
   @TernField
   public static final int MOVE_MIDDLE = 16;
   /**
    * Dodge and center objects.
    */
   @TernField
   public static final int DODGE_SYMMETRIC = MOVE_DODGE | MOVE_CENTER;
   /**
    * Stack, dodge, and center objects.
    */
   @TernField
   public static final int STACK_SYMMETRIC = MOVE_STACK | DODGE_SYMMETRIC;

   /**
    * Hint to explode a slice of a pie, string of true or false.
    */
   @TernField
   public static final String HINT_EXPLODED = "exploded";
   /**
    * Hint to add a shining effect to paint, string of true or false.
    */
   @TernField
   public static final String HINT_SHINE = "shine";
   /**
    * Transparency value for color, a double value from 0 to 1.
    */
   @TernField
   public static final String HINT_ALPHA = "alpha";
   /**
    * Hint for the geometry max count limit, an integer value.
    */
   @TernField
   public static final String HINT_MAX_COUNT = "max_count";
   /**
    * Hint to clip the plot area for drawing this element,
    * a string of true or false.
    */
   @TernField
   public static final String HINT_CLIP = "clip";
   /**
    * The maximum plot (single sub-graph) width, a number.
    */
   @TernField
   public static final String HINT_MAX_WIDTH = "maxWidth";
   /**
    * The maximum plot (single sub-graph) height, a number.
    */
   @TernField
   public static final String HINT_MAX_HEIGHT = "maxHeight";

   /**
    * Create an empty graph element. Column binding must be defined using
    * addDim.
    */
   protected GraphElement() {
      super();

      textSpec.setFont(GDefaults.DEFAULT_TEXT_FONT);
   }

   /**
    * Create a graph element for a single column (1d).
    */
   protected GraphElement(String field) {
      this();

      addVar(field);
   }

   /**
    * Create a graph element for two columns (2d).
    */
   protected GraphElement(String field1, String field2) {
      this();

      addDim(field1);
      addVar(field2);
   }

   /**
    * Create a graph element for three columns (3d).
    */
   protected GraphElement(String field1, String field2, String field3) {
      this();

      addDim(field1);
      addDim(field2);
      addVar(field3);
   }

   /**
    * Create a visual model for the graph element.
    */
   protected VisualModel createVisualModel(DataSet data) {
      return new VisualModel(data, getColorFrame(),
         getSizeFrame(), getShapeFrame(), getTextureFrame(), getLineFrame(),
         getTextFrame());
   }

   /**
    * Add a dimension to be plotted using this element.
    * @param col the dimension identifier.
    */
   @TernMethod
   public void addDim(String col) {
      if(col != null) {
         dims.add(col);
      }
   }

   /**
    * Get the dimension at the specified index.
    * @param idx the dim index.
    */
   @TernMethod
   public String getDim(int idx) {
      return dims.get(idx);
   }

   /**
    * Get the number of dimensions specified for this element.
    */
   @TernMethod
   public int getDimCount() {
      return dims.size();
   }

   /**
    * Remove the dimension at the specified index.
    * @param idx the dim index.
    */
   @TernMethod
   public void removeDim(int idx) {
      dims.remove(idx);
   }

   /**
    * Remove all dimensions.
    */
   @TernMethod
   public void clearDims() {
      dims.clear();
   }

   /**
    * Get all the dimensions.
    */
   @TernMethod
   public String[] getDims() {
      String[] arr = new String[dims.size()];
      dims.toArray(arr);
      return arr;
   }

   /**
    * Add a variable to be plotted using this element.
    * @param col the variable identifier.
    */
   @TernMethod
   public void addVar(String col) {
      if(col != null) {
         vars.add(col);
      }
   }

   /**
    * Get the variable at the specified index.
    * @param idx the var index.
    */
   @TernMethod
   public String getVar(int idx) {
      return vars.get(idx);
   }

   /**
    * Get the number of variables specified for this element.
    */
   @TernMethod
   public int getVarCount() {
      return vars.size();
   }

   /**
    * Remove the variable at the specified index.
    * @param idx the var index.
    */
   @TernMethod
   public void removeVar(int idx) {
      vars.remove(idx);
   }

   /**
    * Remove all variables.
    */
   @TernMethod
   public void clearVars() {
      vars.clear();
   }

   /**
    * Get all the variables.
    */
   @TernMethod
   public String[] getVars() {
      return vars.toArray(new String[0]);
   }

   /**
    * Set the option to handle collisions. The collision modifiers may not be
    * supported by all elements. If the option is MOVE_STACK, the stack option
    * is also enabled.
    * @param option an option defined in this class, e.g. DODGE_SYMMETRIC.
    */
   @TernMethod
   public void setCollisionModifier(int option) {
      collision = option;
      this.stack = (option & MOVE_STACK) != 0;
   }

   /**
    * Get the collision handling option.
    * @return an int represent collision handling option,
    * an option defined in this class, e.g. DODGE_SYMMETRIC.
    */
   @TernMethod
   public int getCollisionModifier() {
      return collision;
   }

   /**
    * Set the collision modifier and the stack option.
    */
   @TernMethod
   public void setCollisionModifier(int option, boolean stack) {
      collision = option;
      this.stack = stack;
   }

   /**
    * Check if the element is stacked.
    */
   @TernMethod
   public boolean isStack() {
      return stack;
   }

   /**
    * Set whether this element should be kept inside the plot area. If set to
    * true and the element extends outside of the plot, the plot area is scaled
    * to push the object inside. The default is true.
    */
   @TernMethod
   @Override
   public void setInPlot(boolean inside) {
      this.inPlot = inside;
   }

   /**
    * Check if the element should be kept inside the plot area.
    */
   @TernMethod
   @Override
   public boolean isInPlot() {
      return inPlot;
   }

   /**
    * Set the color frame for getting the color aesthetic for each element.
    */
   @TernMethod
   public void setColorFrame(ColorFrame colors) {
      this.colors = colors;
   }

   /**
    * Get the color frame for getting the color aesthetic for each element.
    */
   @TernMethod
   public ColorFrame getColorFrame() {
      return colors;
   }

   /**
    * Set the size frame for getting the size aesthetic for each element.
    */
   @TernMethod
   public void setSizeFrame(SizeFrame sizes) {
      this.sizes = sizes;
   }

   /**
    * Get the size frame for getting the size aesthetic for each element.
    */
   @TernMethod
   public SizeFrame getSizeFrame() {
      return sizes;
   }

   /**
    * Set the shape frame for getting the shape aesthetic for each element.
    */
   @TernMethod
   public void setShapeFrame(ShapeFrame shapes) {
      this.shapes = shapes;
   }

   /**
    * Get the shape frame for getting the shape aesthetic for each element.
    */
   @TernMethod
   public ShapeFrame getShapeFrame() {
      return shapes;
   }

   /**
    * Set the texture frame for getting the texture aesthetic for each element.
    */
   @TernMethod
   public void setTextureFrame(TextureFrame textures) {
      this.textures = textures;
   }

   /**
    * Get the texture frame for getting the texture aesthetic for each element.
    */
   @TernMethod
   public TextureFrame getTextureFrame() {
      return textures;
   }

   /**
    * Set the text frame for getting the label aesthetic for each element.
    */
   @TernMethod
   public void setTextFrame(TextFrame labels) {
      this.labels = labels;
   }

   /**
    * Get the text frame for getting the label aesthetic for each element.
    */
   @TernMethod
   public TextFrame getTextFrame() {
      return labels;
   }

   /**
    * Set the line frame for getting the line aesthetic for each element.
    */
   @TernMethod
   public void setLineFrame(LineFrame lines) {
      this.lines = lines;
   }

   /**
    * Get the line frame for getting the line aesthetic for each element.
    */
   @TernMethod
   public LineFrame getLineFrame() {
      return lines;
   }

   /**
    * Check if the aesthetic frame is supported.
    */
   @TernMethod
   public boolean supportsFrame(VisualFrame frame) {
      return frame != null;
   }

   /**
    * Get the text label attributes.
    */
   @TernMethod
   public TextSpec getTextSpec() {
      return textSpec;
   }

   /**
    * Set the text label attributes.
    */
   @TernMethod
   public void setTextSpec(TextSpec textSpec) {
      this.textSpec = (textSpec == null) ? new TextSpec() : textSpec;
   }

   /**
    * Get the text placement.
    */
   @TernMethod
   public int getLabelPlacement() {
      return textPos;
   }

   /**
    * Set the text placement.
    * @param pos one of GraphConstants.TOP, LEFT, BOTTOM, RIGHT, CENTER, CENTER_FILL, AUTO.
    */
   @TernMethod
   public void setLabelPlacement(int pos) {
      textPos = pos;
   }

   /**
    * Set a comparator to control the drawing order of the visual objects
    * generated for this element.
    */
   public void setComparator(Comparator comp) {
      this.comparer = comp;
   }

   /**
    * Get the comparator.
    */
   public Comparator getComparator() {
      return comparer;
   }

   /**
    * Get the selector for selecting data to be plotted.
    */
   public GraphtDataSelector getGraphDataSelector() {
      return selector;
   }

   /**
    * Set the selector for selecting data to be plotted.
    */
   public void setGraphDataSelector(GraphtDataSelector selector) {
      this.selector = selector;
   }

   protected boolean isAccepted(DataSet data, int row) {
      return selector == null || selector.accept(data, row, null);
   }

   /**
    * Get the border line color.
    */
   @TernMethod
   public Color getBorderColor() {
      return borderColor;
   }

   /**
    * Set the border line color. If border color is at, a border will be added to an element
    * if it supports it (e.g. bar). The border line style will be fetched from LineFrame, or
    * defaults to solid line if line frame is not set.
    */
   @TernMethod(url = "#ProductDocs/chartAPI/html/Common_Chart_GraphElement_setBorderColor.htm")
   public void setBorderColor(Color borderColor) {
      this.borderColor = borderColor;
   }

   /**
    * Create the geometry objects for the chartLens.
    * @hidden
    * @param data the chartLens to plot using this element.
    * @param graph the containing graph.
    */
   public abstract void createGeometry(DataSet data, GGraph graph);

   /**
    * Scale the specified tuple using scales defined in the graph.
    * @hidden
    * @param row the row (tuple) indx in the chartLens.
    * @param vidx the variable index. If the value is negative, it is the
    * -(dimension index + 1);
    */
   protected double[] scale(DataSet data, int row, int vidx, GGraph graph) {
      ArrayList<String> cols = new ArrayList<>(dims);

      // see comment for vidx
      if(vidx < 0) {
         int didx = -(vidx + 1);

         // clear other dimensions so the tuple only contains this dimension
         for(int i = 0; i < dims.size(); i++) {
            if(i != didx) {
               cols.set(i, null);
            }
         }
      }
      // add variable to the list, [dims, var]
      else {
         cols.add(vars.get(vidx));
      }

      double[] tuple = new double[cols.size()];

      for(int i = 0; i < cols.size(); i++) {
         String col = cols.get(i);

         // if column value should be ignored
         if(col == null) {
            tuple[i] = Double.NaN;
            continue;
         }

         Scale scale = graph.getScale(col);
         Object value = data.getData(col, row);

         if(scale == null) {
            String msg = GTool.getString("em.common.graph.scaleNotfound", col);
            throw new RuntimeException(msg);
         }

         tuple[i] = scale.map(value);

         if(Double.isNaN(tuple[i]) && !isIncludeNull(data, row, col)) {
            return null;
         }
      }

      return tuple;
   }

   /**
    * Check if null value should be included in the graph. If false, null values
    * are ignored as if they don't exist in the dataset. If true, null values
    * could cause a different rendering of the data (e.g. a missing point on a
    * line chart instead of a continuous line).
    */
   boolean isIncludeNull(DataSet data, int row, String col) {
      // this can only be true if the row is added in GeoDataSet for FullMap
      return row >= data.getRowCount();
   }

   /**
    * Get the col index on the root dataset.
    * @param data the chartLens to plot using this element.
    * @param column the specified column name.
    */
   protected int getRootColIndex(DataSet data, String column) {
      int cidx = data.indexOfHeader(column);

      if(cidx < 0 && data instanceof DataSetFilter) {
         DataSetFilter filter = (DataSetFilter) data;
         return filter.getRootCol(cidx);
      }

      return cidx;
   }

   /**
    * Get the row index on the root dataset.
    * @param data the chartLens to plot using this element.
    * @param row the row index for the dataset.
    */
   protected int getRootRowIndex(DataSet data, int row) {
      if(data instanceof DataSetFilter) {
         DataSetFilter filter = (DataSetFilter) data;
         return filter.getRootRow(row);
      }

      return row;
   }

   /**
    * Get the row indexes on the root dataset.
    * @param data the chartLens to plot using this element.
    * @param rows the row indexes for the dataset.
    */
   protected int[] getRootRowIndexes(DataSet data, int[] rows) {
      int[] newRows = new int[rows.length];

      if(data instanceof DataSetFilter) {
         DataSetFilter filter = (DataSetFilter) data;

         for(int i = 0; i < rows.length; i++) {
            newRows[i] = filter.getRootRow(rows[i]);
         }
      }
      else {
         System.arraycopy(rows, 0, newRows, 0, rows.length);
      }

      return newRows;
   }

   /**
    * Get all the legend frames.
    * @return all the legend frames applied on this graph element.
    */
   @TernMethod
   public VisualFrame[] getVisualFrames() {
      return new VisualFrame[] {labels, sizes, shapes, textures, lines, colors};
   }

   /**
    * Sort dataset to prepare for generating visual objects.
    * @return sorted dataset or null if no sorting is necessary.
    */
   public SortedDataSet sortData(DataSet data, GGraph graph) {
      return null;
   }

   /**
    * Set the starting row of the dataset to use for this element.
    * The default is 0.
    */
   @TernMethod
   public void setStartRow(int start) {
      this.startRow = start;
   }

   /**
    * Get the starting row of the dataset to use for this element.
    */
   @TernMethod
   public int getStartRow() {
      return startRow;
   }

   /**
    * Set the ending row of the dataset to use for this element.
    * @param end the ending row (non-inclusive). Use -1 to use all rows.
    */
   @TernMethod
   public void setEndRow(int end) {
      this.endRow = end;
   }

   /**
    * Get the ending row of the dataset to use for this element.
    */
   @TernMethod
   public int getEndRow() {
      return endRow;
   }

   /**
    * Get the start row of the range.
    * @hidden
    */
   public int getStartRow(DataSet data) {
      return getStartRow(data, getStartRow());
   }

   /**
    * Get the start row in data using the base row index.
    * @hidden
    */
   public static int getStartRow(DataSet data, int startRow0) {
      if(startRow0 == 0) {
         return 0;
      }

      if(data instanceof SortedDataSet) {
         data = ((SortedDataSet) data).getDataSet();
      }

      if(!(data instanceof DataSetFilter)) {
         return startRow0;
      }

      DataSetFilter filter = (DataSetFilter) data;

      for(int i = 0; i < filter.getRowCount(); i++) {
         int root = filter.getRootRow(i);

         if(root >= startRow0) {
            return i;
         }
      }

      return data.getRowCount();
   }

   /**
    * Get the end row of the range after applying max shape count, mapping through
    * sorted dataset and sub-dataset.
    * @hidden
    */
   public int getEndRow(DataSet data) {
      return getEndRow(data, getStartRow(), getEndRow(), this);
   }

   /**
    * Get the end row in data using the base row index.
    * @hidden
    */
   public static int getEndRow(DataSet data, int startRow0, int endRow0, GraphElement elem) {
      Object obj = elem != null ? elem.getHint(HINT_MAX_COUNT) : null;
      int startRow = getStartRow(data, startRow0);
      int rcount = data.getRowCount() - startRow;

      // @by ChrisSpagnoli bug1422604427332 2015-2-9
      if(data instanceof AbstractDataSet) {
         rcount = ((AbstractDataSet) data).getRowCountUnprojected() - startRow;
      }

      if(obj != null) {
         int mcount = (Integer) obj;

         if(mcount < rcount && mcount > 0) {
            String msg = GTool.getString("viewer.viewsheet.chart.shapeCountMax", mcount) +
               ": " + Arrays.toString(elem.getDims()) + "," + Arrays.toString(elem.getVars());

            // showing a warning to end user is more distractive than
            // helpful. if max is very large, when the max is reached, the graph
            // is generally so over crowed that any truncation is unlikely
            // noticeable or significant
            if(mcount > 100000) {
               LOG.debug(msg);
            }
            else {
               CoreTool.addUserMessage(msg);
            }

            rcount = mcount;
         }
      }

      int endRow = getEndRow0(data, endRow0);

      if(endRow >= 0 && endRow < startRow + rcount) {
         return endRow;
      }

      return startRow + rcount;
   }

   /**
    * Get the end row of the range.
    */
   private static int getEndRow0(DataSet data, int endRow0) {
      if(endRow0 < 0) {
         return -1;
      }

      if(data instanceof SortedDataSet) {
         // SortedDataSet will add calc rows to the sort range. just get that value as
         // the true end row. (52540)
         int endRow = ((SortedDataSet) data).getEffectiveEndRow();

         // if SortedDataSet's end row is not set, don't use the effective end row, which
         // is always the entire dataset in this case, and the endRow0 will be ignored. (59322)
         if(endRow > 0 && ((SortedDataSet) data).getEndRow() >= 0) {
            return endRow;
         }

         data = ((SortedDataSet) data).getDataSet();
      }

      // add calc rows to the range of rows to use. see SortedDataSet.sort(). (51046)
      int calcRows = data instanceof AbstractDataSet
         ? ((AbstractDataSet) data).getCalcRowCount() : 0;

      // SubDataSet may wrap around the one with the calc rows. (59325)
      if(calcRows == 0 && data instanceof SubDataSet) {
         SubDataSet sub = (SubDataSet) data;

         if(sub.getDataSet() instanceof AbstractDataSet) {
            calcRows = ((AbstractDataSet) sub.getDataSet()).getCalcRowCount();
         }
      }

      if(!(data instanceof DataSetFilter)) {
         return endRow0 + calcRows;
      }

      DataSetFilter filter = (DataSetFilter) data;

      for(int i = filter.getRowCount() - 1; i >= 0; i--) {
         int root = filter.getRootRow(i);

         // if i is a calc row, root is -1. it should be ignored. (52523)
         if(root >= 0 && root < endRow0) {
            return i + 1 + calcRows;
         }
      }

      return -1;
   }

   /**
    * Get sorted dataset that only sorts the data in the start/end row range of this element.
    * @hidden
    */
   protected DataSet getSortedDataSetInRange(DataSet data, SortedDataSet sdata) {
      if(sdata == null) {
         return data;
      }

      // apply range to make sure only rows in the range is sorted. (56973)
      if(data instanceof SubDataSet && getStartRow() >= 0 && getEndRow() > getStartRow()) {
         int start = ((SubDataSet) data).getRowFromBase(getStartRow());
         int end = ((SubDataSet) data).getRowFromBase(getEndRow());

         if(start < 0) {
            start = -start - 1;
         }

         if(end < 0) {
            end = -end - 1;
         }

         sdata.setStartRow(start);
         sdata.setEndRow(end);
      }

      return sdata;
   }

   /**
    * Set the data set for the visual frame if it needs to be different.
    */
   public void setVisualDataSet(DataSet data) {
      this.vdata = data;
   }

   /**
    * Get the data set for the visual frame if it needs to be different.
    */
   @TernMethod
   public DataSet getVisualDataSet() {
      return vdata;
   }

   /**
    * Check if the text color should be automatically calculated based on background
    * color for maximum contrast.
    */
   @TernMethod
   public boolean isAutoTextColor() {
      return autoTextColor;
   }

   /**
    * Set if the text color should be automatically calculated based on background
    * color for maximum contrast. This is only supported by certain graph types.
    */
   @TernMethod
   public void setAutoTextColor(boolean autoTextColor) {
      this.autoTextColor = autoTextColor;
   }

   /**
    * Set the text spec for a tree dim's labels.
    */
   @TernMethod
   public void setTextSpec(String dim, TextSpec textSpec) {
      textSpecMap.put(dim, textSpec);
   }

   /**
    * Get the text spec for a tree dim's labels.
    */
   @TernMethod
   public TextSpec getTextSpec(String dim) {
      return textSpecMap.get(dim);
   }

   /**
    * Get all dimensions with explicit text spec set through setTextSpec().
    */
   @TernMethod
   public Set<String> getTextSpecDims() {
      return textSpecMap.keySet();
   }

   /**
    * Get a text spec for element label. Use dim/var specific spec if available, or the
    * default spec otherwise.
    */
   @TernMethod
   public TextSpec getLabelTextSpec(String dim) {
      TextSpec spec = getTextSpec(dim);
      return spec != null ? spec : getTextSpec();
   }

   /**
    * Get scale from graph or create a default scale.
    */
   protected Scale getOrCreateScale(EGraph egraph, String col, DataSet data) {
      Scale scale = egraph.getScale(col);
      return (scale == null) ? Scale.createScale(data, col) : scale;
   }

   /**
    * Get scale from graph or create a default scale.
    */
   protected Scale getOrCreateScale(GGraph graph, String col, DataSet data) {
      Scale scale = graph.getScale(col);
      return (scale == null) ? Scale.createScale(data, col) : scale;
   }

   /**
    * Sort the rows used in this element.
    * @param fields sorting fields.
    * @hidden
    */
   protected SortedDataSet createSortedDataSet(DataSet data, String... fields) {
      int start = getStartRow();
      int end = getEndRow();
      SortedDataSet sorted = new SortedDataSet(data, fields);
      sorted.setVars(getVars());
      sorted.setGroupFields(getAllGroupFields());

      // map the start/end row in the original dataset to the sub dataset
      if(start != 0 || end >= 0) {
         if(data instanceof TopDataSet) {
            data = ((TopDataSet) data).getDataSet();
         }

         // for facet graph
         if(data instanceof SubDataSet) {
            start = ((SubDataSet) data).getRowFromBase(start);
            end = ((SubDataSet) data).getRowFromBase(end);
         }

         start = (start < 0) ? -start + 1 : start;
         end = (end < 0) ? -end - 1 : end;

         sorted.setStartRow(start);
         sorted.setEndRow(end);
      }

      return sorted;
   }

   // get all dims including visual frames.
   protected String[] getAllGroupFields() {
      return Arrays.stream(getVisualFrames())
         .flatMap(f -> f instanceof CompositeVisualFrame
            ? ((CompositeVisualFrame) f).getFrames(CategoricalFrame.class) : Stream.of(f))
         .filter(f -> f instanceof CategoricalFrame)
         .map(f -> f.getField())
         .filter(f -> f != null)
         .toArray(String[]::new);
   }

   /**
    * Check if this element supports overlay.
    */
   @TernMethod
   public boolean supportsOverlay() {
      return false;
   }

   /**
    * Set the fields for ordering points on lines. If the fields are not set,
    * the points are ordered from left to right (or bottom to top for) regardless
    * of the order of the data in the dataset.
    */
   @TernMethod
   public void setSortFields(String... fields) {
      this.sortfields = (fields == null) ? new String[0] : fields;
   }

   /**
    * Get the fields for ordering points on lines.
    */
   @TernMethod
   public String[] getSortFields() {
      return sortfields;
   }

   /**
    * Return the color frame that can be used to get color for the legend of the specified frame.
    * @hidden
    */
   public ColorFrame getSharedColorFrame(VisualFrame frame) {
      return frame != null && !(frame instanceof ColorFrame) ? getColorFrame() : null;
   }

   /**
    * Get the string representation.
    */
   @Override
   public String toString() {
      String str = super.toString() + "(dims: " + dims + "; vars: " + vars + ";";

      if(colors != null) {
         str += " colors: " + colors;
      }

      if(sizes != null) {
         str += " sizes: " + sizes;
      }

      if(shapes != null) {
         str += " shapes: " + shapes;
      }

      if(textures != null) {
         str += " textures: " + textures;
      }

      if(labels != null) {
         str += " labels: " + labels;
      }

      if(lines != null) {
         str += " lines: " + lines;
      }

      return str + ")";
   }

   /**
    * Check if equals another objects in structure.
    */
   public boolean equalsContent(Object obj) {
      if(!(super.equalsContent(obj))) {
         return false;
      }

      GraphElement elem2 = (GraphElement) obj;
      return dims.equals(elem2.dims) && vars.equals(elem2.vars) &&
         equalsContent(colors, elem2.colors) &&
         equalsContent(sizes, elem2.sizes) &&
         equalsContent(shapes, elem2.shapes) &&
         equalsContent(lines, elem2.lines) &&
         equalsContent(textures, elem2.textures) &&
         equalsContent(labels, elem2.labels) &&
         collision == elem2.collision &&
         inPlot == elem2.inPlot &&
         textPos == elem2.textPos &&
         autoTextColor == elem2.autoTextColor &&
         Objects.equals(textSpec, elem2.textSpec) &&
         Objects.equals(textSpecMap, elem2.textSpecMap) &&
         Objects.equals(borderColor, elem2.borderColor) &&
         Arrays.equals(sortfields, elem2.sortfields);
   }

   private boolean equalsContent(VisualFrame f1, VisualFrame f2) {
      if(f1 != null && f2 != null) {
         return f1.equalsContent(f2);
      }

      return f1 == f2;
   }

   @Override
   public GraphElement clone() {
      try {
         GraphElement element = (GraphElement) super.clone();
         element.dims = CoreTool.deepCloneCollection(dims);
         element.vars = CoreTool.deepCloneCollection(vars);
         element.vdata = vdata;
         element.comparer = comparer;

         if(colors != null) {
            element.colors = (ColorFrame) colors.clone();
         }

         if(sizes != null) {
            element.sizes = (SizeFrame) sizes.clone();
         }

         if(shapes != null) {
            element.shapes = (ShapeFrame) shapes.clone();
         }

         if(textures != null) {
            element.textures = (TextureFrame) textures.clone();
         }

         if(labels != null) {
            element.labels = (TextFrame) labels.clone();
         }

         if(lines != null) {
            element.lines = (LineFrame) lines.clone();
         }

         if(textSpecMap != null) {
            element.textSpecMap = CoreTool.deepCloneMap(this.textSpecMap);
         }

         return element;
      }
      catch(Exception ex) {
         LOG.warn("Failed to clone: " + ex, ex);
      }

      return null;
   }

   private Vector<String> dims = new Vector<>(); // dimensions
   private Vector<String> vars = new Vector<>(); // values to plot
   // use default categorical color frame to add different colors for
   // each variable if color frame is not explicitly set
   private ColorFrame colors;
   private SizeFrame sizes;
   private ShapeFrame shapes;
   private TextureFrame textures;
   private TextFrame labels;
   private LineFrame lines;
   private int collision = MOVE_CENTER; // collision option,e.g. DODGE_SYMMETRIC
   private boolean inPlot = true;
   private TextSpec textSpec = new TextSpec();
   private int textPos = GraphConstants.AUTO;
   private transient Comparator comparer; // compare drawing order
   private int startRow = 0;
   private int endRow = -1;
   private transient DataSet vdata;
   private boolean autoTextColor = false;
   private Map<String, TextSpec> textSpecMap = new HashMap<>();
   private GraphtDataSelector selector;
   private Color borderColor;
   private String[] sortfields = {};
   private boolean stack = false;

   private static final Logger LOG = LoggerFactory.getLogger(GraphElement.class);
}
