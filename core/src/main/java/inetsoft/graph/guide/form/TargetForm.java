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

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.data.AbstractDataSet;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.*;
import inetsoft.util.Tool;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class TargetForm extends GraphForm {
   /**
    * Not used. We almost always are creating more than one visual object
    * The only exception would be a Line Target without fill.
    */
   @Override
   public Visualizable createVisual(Coordinate coord) {
      return null;
   }

   /**
    * Create visual objects based on the given data and the strategy.
    */
   @Override
   public Visualizable[] createVisuals(Coordinate coord) {
      double[] data;

      // If using entire chart scope, get the uppermost parent to use for data
      // Otherwise use this coordinate for data
      if(chartScope) {
         Coordinate parent = coord;
         Coordinate ptr = parent.getParentCoordinate();

         // Traverse to top of the tree
         while(ptr != null) {
            parent = ptr;
            ptr = parent.getParentCoordinate();
         }

         data = extractData(parent);
      }
      else {
         // Extract the runtime graph data from the coordinate
         data = extractData(coord);
      }

      // Have the Graph Target Strategy figure out the band boundaries
      double[] bandBoundaries = strategy.calculateBoundaries(data);
      String[] labels;

      try {
         labels = strategy.generateLabels(bandBoundaries, labelFormats,
                                          fieldLabel == null ? field : fieldLabel,
                                          labelSpec.getFormat(), dateTarget, timeTarget);
      }
      catch(IllegalArgumentException ex) {
         // if format failed, don't format the parameter value. (53395)
         labels = strategy.generateLabels(bandBoundaries, labelFormats,
                                          fieldLabel == null ? field : fieldLabel, null,
                                          dateTarget, timeTarget);
      }

      // Generate the forms used to draw each part
      Collection<GraphForm> subForms = generateSubForms(coord, bandBoundaries, labels);

      // Merge the visuals from all of the sub-forms
      return mergeVisuals(coord, subForms);
   }

   /**
    * Merges all the visual objects from all the subforms into one array
    */
   private Visualizable[] mergeVisuals(Coordinate coord, Collection<GraphForm> subForms) {
      List<Visualizable> allVOs = new ArrayList<>();

      // Iterate through each subform and create all of the Visual Objects
      for(GraphForm form : subForms) {
         Visualizable[] vos = form.createVisuals(coord);
         Collections.addAll(allVOs, vos);
      }

      Visualizable[] visuals = new Visualizable[allVOs.size()];
      allVOs.toArray(visuals);

      return visuals;
   }

   /**
    * Generate the Form objects used internally to actually draw the elements
    * in the graph
    */
   private Collection<GraphForm> generateSubForms(Coordinate coord,
                                                  double[] bandBoundaries,
                                                  String[] labels)
   {
      PriorityQueue<GraphForm> subForms = new PriorityQueue<>(11, fComparator);

      if(bandBoundaries.length == 0) {
         // If there are no boundaries, we won't draw anything
         return subForms;
      }

      boolean inverted = Arrays.stream(getAllScales(coord, false))
         .filter(scale -> ArrayUtils.contains(scale.getFields(), getField()) &&
            scale instanceof LinearScale)
         .anyMatch(scale -> ((LinearScale) scale).isReversed());

      if(Arrays.stream(bandBoundaries).anyMatch(b -> Double.isInfinite(b))) {
         LOG.debug("Infinite target values ignored.");
         return subForms;
      }

      // Lower Rectangle (Fill below)
      if(fillBelow != null) {
         Object[] topLeft, bottomRight;
         double min = Arrays.stream(bandBoundaries).max().orElse(0);
         Object bottom = inverted ? Scale.MAX_VALUE : Scale.MIN_VALUE;

         if(xFieldTarget) {
            topLeft = new Object[] {Scale.MIN_VALUE, Scale.MAX_VALUE};
            bottomRight = new Object[] {min, bottom};
         }
         else if(inverted) {
            topLeft = new Object[] {Scale.MIN_VALUE, bottom};
            bottomRight = new Object[] {Scale.MAX_VALUE, min};
         }
         else {
            topLeft = new Object[] {Scale.MIN_VALUE, min};
            bottomRight = new Object[] {Scale.MAX_VALUE, bottom};
         }

         RectForm lowerRect = new RectForm(topLeft, bottomRight);
         lowerRect.setIgnoreNegative(true);
         lowerRect.setColor(fillBelow);
         lowerRect.setAlpha(alphaValue);
         lowerRect.setZIndex(GDefaults.TARGET_FILL_Z_INDEX);
         lowerRect.setMeasure(getMeasure());
         lowerRect.setInPlot(this.isInPlot());
         lowerRect.setFill(true);
         subForms.add(lowerRect);
      }

      // line, band, line, band, line
      Double previousBoundary = null;
      Object[] prevLineEnd = null;

      for(int i = 0; i < bandBoundaries.length; i++) {
         Object[] lineStart = createTuple(Scale.MIN_VALUE, bandBoundaries[i], coord);
         Object[] lineEnd = createTuple(Scale.MAX_VALUE, bandBoundaries[i], coord);

         // Create rectangle (band) from previous line to this line
         if(previousBoundary != null) {
            Color bandColor = bandColors.getColor(i - 1);

            // Make the rectangle only if not transparent
            if(bandColor != null) {
               RectForm band = new RectForm(lineStart, prevLineEnd);
               band.setColor(bandColor);
               band.setAlpha(alphaValue);
               band.setFill(true);
               band.setZIndex(GDefaults.TARGET_FILL_Z_INDEX);
               band.setMeasure(getMeasure());
               band.setInPlot(this.isInPlot());
               subForms.add(band);
            }
         }

         // Make this line
         int lineStyle = getLine();

         if(lineStyle != GraphConstants.NONE) {
            LineForm boundaryLine = new LineForm(lineStart, lineEnd);

            boundaryLine.setLine(lineStyle);
            boundaryLine.setZIndex(GDefaults.TARGET_LINE_Z_INDEX);
            boundaryLine.setMeasure(getMeasure());
            boundaryLine.setColor(getColor());
            boundaryLine.setInPlot(this.isInPlot());
            // avoid gap when rescaled (or projected)
            boundaryLine.setHint("fill", "true");
            subForms.add(boundaryLine);
         }

         previousBoundary = bandBoundaries[i];
         prevLineEnd = lineEnd;
      }

      // Upper Rectangle (Fill above)
      if(fillAbove != null) {
         Object[] topLeft, bottomRight;
         double max = Arrays.stream(bandBoundaries).max().orElse(0);
         Object top = inverted ? Scale.MIN_VALUE : Scale.MAX_VALUE;

         if(xFieldTarget) {
            topLeft = new Object[] {max, top};
            bottomRight = new Object[] {Scale.MAX_VALUE, Scale.MIN_VALUE};
         }
         else if(inverted) {
            topLeft = new Object[] {Scale.MIN_VALUE, max};
            bottomRight = new Object[] {Scale.MAX_VALUE, top};
         }
         else {
            topLeft = new Object[] {Scale.MIN_VALUE, top};
            bottomRight = new Object[] {Scale.MAX_VALUE, max};
         }

         RectForm upperRect = new RectForm(topLeft, bottomRight);
         upperRect.setIgnoreNegative(true);
         upperRect.setColor(fillAbove);
         upperRect.setAlpha(alphaValue);
         upperRect.setZIndex(GDefaults.TARGET_FILL_Z_INDEX);
         upperRect.setMeasure(getMeasure());
         upperRect.setInPlot(this.isInPlot());
         upperRect.setFill(true);
         subForms.add(upperRect);
      }

      // Add labels
      if(labels != null) {
         boolean hor = GTool.isHorizontal(coord.getCoordTransform());
         TextSpec spec2 = null;

         for(int i = 0; i < labels.length; i++) {
            // avoid duplicate labels
            if(i > 0 && bandBoundaries[i] == bandBoundaries[i - 1]) {
               continue;
            }

            String label = labels[i];
            LabelForm labelForm = new LabelForm(
               label, createTuple(Scale.MIN_VALUE, bandBoundaries[i], coord));
            TextSpec labelSpec = this.labelSpec;
            String fmt2 = labelFormats[i % labelFormats.length].toPattern();

            // format is already used in generateLabels, shouldn't apply
            // it again in label, otherwise the result could be duplicated
            if(fmt2.contains("{0}") || fmt2.contains("{0,")) {
               if(spec2 == null) {
                  spec2 = labelSpec.clone();
                  spec2.setFormat(null);
               }

               labelSpec = spec2;
            }

            labelForm.setLine(GraphConstants.NONE);
            labelForm.setZIndex(GDefaults.TARGET_LABEL_ZINDEX);
            labelForm.setMeasure(getMeasure());
            labelForm.setInPlot(isInPlot());
            labelForm.setCollisionModifier(hor ? VLabel.MOVE_RIGHT : VLabel.MOVE_UP);
            labelForm.setTextSpec(labelSpec);
            labelForm.setHints(getHints());
            subForms.add(labelForm);
         }
      }

      for(GraphForm form : subForms) {
         form.setHint(GraphElement.HINT_CLIP, getHint(GraphElement.HINT_CLIP));
      }

      return subForms;
   }

   // If date field or x-axis measure field, reverse x/y in tuple for vertical line
   private Object[] createTuple(Object x, Object y, Coordinate coord) {
      if(xFieldTarget) {
         // if date and scale is categorical (not TimeScale, e.g. year), values in the scale
         // are dates while the bandBoundaries are always double. need to convert double (ms)
         // to Date to avoid scale() returning NaN in LineForm.
         if(dateTarget && y instanceof Double && field != null) {
            if(coord.getVGraph().getEGraph().getScale(field) instanceof CategoricalScale) {
               y = new Date(((Double) y).longValue());
            }
         }
      }

      return xFieldTarget ? new Object[] {y, x} : new Object[] {x, y};
   }

   /**
    * Extract data from the dataset given that the data is stacked.
    * We need to recreate the stacks and use their sum values as data points
    */
   private double[] extractStackedData(Coordinate coord) {
      final DataSet dset = coord.getDataSet();
      int dataCol = dset.indexOfHeader(field);

      if(dataCol < 0) {
         return new double[0];
      }

      // Find categorical scales.
      Scale[] scales = coord.getScales();
      HashSet<String> categories = new HashSet<>();

      // Add each field with a categorical scale to the list of grouping fields
      for(Scale s : scales) {
         if(s instanceof CategoricalScale || s instanceof TimeScale) {
            Collections.addAll(categories, s.getDataFields());
         }
      }

      // Get indices of category columns in dataset
      int[] categoryIndices = categories.stream().mapToInt(c -> dset.indexOfHeader(c)).toArray();

      // Iterate through dataset.
      // Sum up all values which have equal values for categories
      // This is because these values will be part of the same stacked bar
      HashMap<List, Double> outputData = new HashMap<>();
      int rowCount = getDataRowCount(dset);
      int startRow = 0;
      int[] startEndRow = getRowRange(coord, dset, field);

      if(startEndRow != null) {
         startRow = startEndRow[0];
         rowCount = Math.min(rowCount, startEndRow[1]);
      }

      // Iterate through each row of the dataset, determine which stacked bar
      // the data is from and add to that bar
      for(int row = startRow; row < rowCount; row++) {
         final int row0 = row;
         List currVals = Arrays.stream(categoryIndices).mapToObj(i -> dset.getData(i, row0))
            .collect(Collectors.toList());

         Double measureVal;
         Object dataVal = dset.getData(dataCol, row);

         if(dataVal == null) {
            continue;
         }
         else if(dataVal instanceof Date) {
            measureVal = convertDate((Date) dataVal);
         }
         else {
            Double v = Tool.getDoubleData(dset.getData(dataCol, row));
            measureVal = v == null ? 0 : v;
         }

         Double oval = outputData.get(currVals);

         if(oval != null) {
            // Update the value for this combination of category values
            outputData.put(currVals, oval + measureVal);
         }
         else {
            outputData.put(currVals, measureVal);
         }
      }

      // Package output in double array for returning
      return outputData.values().stream().mapToDouble(v -> v).toArray();
   }

   /**
    * Extract data from the coordinate dataset in a simple 1:1 fashion.
    * Each row contains one data point.
    */
   private double[] extractNormalData(Coordinate coord) {
      DataSet dset = coord.getDataSet();
      int dataCol = dset.indexOfHeader(field);

      if(dataCol < 0) {
         return new double[0];
      }

      int startRow = 0;
      int[] startEndRow = getRowRange(coord, dset, field);
      int rowCount = getDataRowCount(dset);

      if(startEndRow != null) {
         startRow = startEndRow[0];
         rowCount = Math.min(rowCount, startEndRow[1]);
      }

      double[] data = new double[rowCount - startRow];

      // Get the data for the measure from each row
      for(int i = 0; i < data.length; i++) {
         Object val = dset.getData(dataCol, i + startRow);

         if(val instanceof Date) {
            data[i] = convertDate((Date) val);
         }
         else {
            Double d = Tool.getDoubleData(val);
            data[i] = d == null ? Tool.NULL_DOUBLE : d;
         }
      }

      return data;
   }

   /**
    * Get the rows for calculating target value.
    */
   private int getDataRowCount(DataSet dset) {
      int rowCount = dset.getRowCount();

      if(dset instanceof AbstractDataSet) {
         // don't include projected row, but should always limit to the dset since
         // it's used for getData(). (52857)
         rowCount = Math.min(rowCount, ((AbstractDataSet) dset).getRowCountUnprojected());
      }

      return rowCount;
   }

   private double convertDate(Date date) {
      return date.getTime();
   }

   /**
    * Extract the appropriate data from the coordinate for use in calculating
    * band boundaries.
    */
   private double[] extractData(Coordinate coord) {
      double[] ret;

      if(stacked && !dateTarget) {
         ret = extractStackedData(coord);
      }
      else {
         ret = extractNormalData(coord);
      }

      return ret;
   }

   /**
    * Set the strategy used to determine band parameters.
    */
   public void setStrategy(TargetStrategy strategy) {
      this.strategy = strategy;
   }

   /**
    * @return the strategy used to calculate band boundaries
    */
   public TargetStrategy getStrategy() {
      return strategy;
   }

   /**
    * Set the color to fill above the band.
    */
   public void setFillAbove(Color fillAbove) {
      this.fillAbove = fillAbove;
   }

   /**
    * Get the color to fill above the band.
    */
   public Color getFillAbove() {
      return fillAbove;
   }

   /**
    * Set the color to fill below the band.
    */
   public void setFillBelow(Color fillBelow) {
      this.fillBelow = fillBelow;
   }

   /**
    * Get the color to fill below the band.
    */
   public Color getFillBelow() {
      return fillBelow;
   }

   /**
    * Set the alpha to fill target.
    */
   @Override
   public void setAlpha(int alphaValue) {
      this.alphaValue = alphaValue;
   }

   /**
    * Get the alpha to fill target.
    */
   @Override
   public int getAlpha() {
      return alphaValue;
   }

   /**
    * Set the color frame for multiple bands generated by a statistic.
    */
   public void setBandColorFrame(CategoricalColorFrame bandColors) {
      this.bandColors = bandColors;
   }

   /**
    * Get the color frame for multiple bands generated by a statistic.
    */
   public CategoricalColorFrame getBandColorFrame() {
      return bandColors;
   }

   /**
    * Sets whether the data should be stacked for purposes of calculating
    * target lines.
    */
   public void setStacked(boolean isStacked) {
      this.stacked = isStacked;
   }

   /**
    * Gets whether the data will be considered as stacked when calculating
    * target lines.
    */
   public boolean isStacked() {
      return stacked;
   }

   /**
    * Set the label template.
    */
   public void setLabelFormats(MessageFormat... templates) {
      if(templates != null && templates.length > 0) {
         this.labelFormats = templates;
      }
   }

   /**
    * Get the label template.
    */
   public MessageFormat[] getLabelFormats() {
      return labelFormats;
   }

   /**
    * Set the text format for the target labels.
    */
   public void setTextSpec(TextSpec labelSpec) {
      this.labelSpec = labelSpec;
   }

   /**
    * Get the text format for the target labels.
    */
   public TextSpec getTextSpec() {
      return labelSpec;
   }

   /**
    * Set if the calculation is for entire chart or subgraph.
    * @param scope true for entire chart, false for subgraph.
    */
   public void setChartScope(boolean scope) {
      this.chartScope = scope;
   }

   /**
    * Check if the calculation is for entire chart or subgraph.
    */
   public boolean isChartScope() {
      return chartScope;
   }

   /**
    * Sorts forms based on their Z index.
    */
   private static class FormComparator implements Comparator<GraphForm> {
      @Override
      public int compare(GraphForm a, GraphForm b) {
         return  a.getZIndex() - b.getZIndex();
      }
   }

   /**
    * Check whether this is a date/time target.
    */
   public boolean isDateTarget() {
      return dateTarget;
   }

   /**
    * Set whether this is a date/time target.
    */
   public void setDateTarget(boolean dateTarget) {
      this.dateTarget = dateTarget;
   }

   /**
    * Check whether this is a time target.
    */
   public boolean isTimeTarget() {
      return timeTarget;
   }

   /**
    * Set whether this is a time target.
    */
   public void setTimeTarget(boolean timeTarget) {
      this.timeTarget = timeTarget;
   }

   /**
    * Check whether this is an X-Axis measure target.
    */
   public boolean isXFieldTarget() {
      return xFieldTarget;
   }

   /**
    * Set whether this is an X-Axis measure target.
    */
   public void setXFieldTarget(final boolean xFieldTargetFlag) {
      xFieldTarget = xFieldTargetFlag;
   }

   /**
    * Get the associated field.
    */
   public String getField() {
      return field;
   }

   /**
    * Set the associated field.
    */
   public void setField(String field) {
      this.field = field;
   }

   /**
    * Get the target line label.
    */
   public String getFieldLabel() {
      return this.fieldLabel;
   }

   /**
    * Set the target line label.
    */
   public void setFieldLabel(String label) {
      this.fieldLabel = label;
   }

   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      TargetForm form = (TargetForm) obj;
      return Objects.equals(field, form.field) &&
         Objects.equals(fieldLabel, form.fieldLabel) &&
         dateTarget == form.dateTarget &&
         timeTarget == form.timeTarget &&
         xFieldTarget == form.xFieldTarget &&
         Objects.equals(strategy, form.strategy) &&
         Objects.equals(fillAbove, form.fillAbove) &&
         Objects.equals(fillBelow, form.fillBelow) &&
         alphaValue == form.alphaValue &&
         Objects.equals(bandColors, form.bandColors) &&
         stacked == form.stacked &&
         chartScope == form.chartScope &&
         Objects.equals(labelSpec, form.labelSpec);
   }

   private static FormComparator fComparator = new FormComparator();
   // label format is an array to accomodate multiple labels
   private MessageFormat[] labelFormats = new MessageFormat[] {
      new MessageFormat("")
   };

   private String field = null;
   private String fieldLabel = null;
   private boolean dateTarget = false;
   private boolean timeTarget = false;
   private boolean xFieldTarget = false;
   private TargetStrategy strategy = null;
   private Color fillAbove = null;
   private Color fillBelow = null;
   private int alphaValue;
   private CategoricalColorFrame bandColors = new CategoricalColorFrame();
   private boolean stacked = false;
   private boolean chartScope = false; // true for whole chart
   private TextSpec labelSpec = new TextSpec();

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(TargetForm.class);
}
