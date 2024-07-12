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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.calc.RunningTotalCalc;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.util.*;

/**
 * Interface for all chart info classes which maintains binding info of a chart.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface ChartInfo extends AssetObject, ChartBindable {
   /**
    * Get the color frame wrapper.
    * @return the color frame wrapper.
    */
   @Override
   ColorFrameWrapper getColorFrameWrapper();

   /**
    * Set the color frame wrapper.
    * @param wrapper the color frame wrapper.
    */
   @Override
   void setColorFrameWrapper(ColorFrameWrapper wrapper);

   /**
    * Get the size frame wrapper.
    * @return the size frame wrapper.
    */
   @Override
   SizeFrameWrapper getSizeFrameWrapper();

   /**
    * Set the size frame wrapper.
    * @param wrapper the size frame wrapper.
    */
   @Override
   void setSizeFrameWrapper(SizeFrameWrapper wrapper);

   /**
    * Get the shape frame wrapper.
    * @return the shape frame wrapper.
    */
   @Override
   ShapeFrameWrapper getShapeFrameWrapper();

   /**
    * Set the shape frame wrapper.
    * @param wrapper the shape frame wrapper.
    */
   @Override
   void setShapeFrameWrapper(ShapeFrameWrapper wrapper);

   /**
    * Get the texture frame wrapper.
    * @return the texture frame wrapper.
    */
   @Override
   TextureFrameWrapper getTextureFrameWrapper();

   /**
    * Set the texture frame wrapper.
    * @param wrapper the texture frame wrapper.
    */
   @Override
   void setTextureFrameWrapper(TextureFrameWrapper wrapper);

   /**
    * Get the line frame wrapper.
    * @return the line frame wrapper.
    */
   @Override
   LineFrameWrapper getLineFrameWrapper();

   /**
    * Set the line frame wrapper.
    * @param wrapper the line frame wrapper.
    */
   @Override
   void setLineFrameWrapper(LineFrameWrapper wrapper);

   /**
    * Get the color frame.
    * @return the color frame.
    */
   @Override
   ColorFrame getColorFrame();

   /**
    * Set the color frame.
    * @param frame the color frame.
    */
   @Override
   void setColorFrame(ColorFrame frame);

   /**
    * Get the size frame.
    * @return the size frame.
    */
   @Override
   SizeFrame getSizeFrame();

   /**
    * Set the size frame.
    * @param frame the size frame.
    */
   @Override
   void setSizeFrame(SizeFrame frame);

   /**
    * Get the shape frame.
    * @return the shape frame.
    */
   @Override
   ShapeFrame getShapeFrame();

   /**
    * Set the shape frame.
    * @param frame the shape frame.
    */
   @Override
   void setShapeFrame(ShapeFrame frame);

   /**
    * Get the texture frame.
    * @return the texture frame.
    */
   @Override
   TextureFrame getTextureFrame();

   /**
    * Set the texture frame.
    * @param frame the texture frame.
    */
   @Override
   void setTextureFrame(TextureFrame frame);

   /**
    * Get the line frame.
    * @return the line frame.
    */
   @Override
   LineFrame getLineFrame();

   /**
    * Set the line frame.
    * @param frame the line frame.
    */
   @Override
   void setLineFrame(LineFrame frame);

   /**
    * Get primary axis descriptor.
    */
   AxisDescriptor getAxisDescriptor();

   /**
    * Set the primary axis descriptor.
    */
   void setAxisDescriptor(AxisDescriptor desc);

   /**
    * Get secondary axis descriptor.
    */
   AxisDescriptor getAxisDescriptor2();

   /**
    * Set the secondary axis descriptor.
    */
   void setAxisDescriptor2(AxisDescriptor desc);

   /**
    * Set the separate graph mode.
    * @param separated the specified separate graph mode.
    */
   void setSeparatedGraph(boolean separated);

   /**
    * Check if separated graph.
    * @return the separated graph mode.
    */
   boolean isSeparatedGraph();

   /**
    * Set whether each measure should has its own style.
    */
   void setMultiStyles(boolean multi);

   /**
    * Check whether each measure should has its own style.
    */
   boolean isMultiStyles();

   /**
    * Check if aesthetics are stored in aggregates.
    */
   boolean isMultiAesthetic();

   /**
    * Get the aggregates with aesthetic binding.
    * @param runtime
    */
   List<ChartAggregateRef> getAestheticAggregateRefs(boolean runtime);

   /**
    * Get the dimensions with aesthetic binding.
    * @param runtime
    */
   List<ChartDimensionRef> getAestheticDimensionRefs(boolean runtime);

   /**
    * Get the type of the chart.
    * @return the chart type.
    */
   @Override
   int getChartType();

   /**
    * Set the type of the chart.
    * @param type the style of the chart.
    */
   @Override
   void setChartType(int type);

   /**
    * Remove the chart binding x axis fields.
    */
   void removeXFields();

   /**
    * Remove the chart binding y axis fields.
    */
   void removeYFields();

   /**
    * Remove the idx th x axis field.
    * @param idx the index of field in x axis.
    */
   void removeGroupField(int idx);

   /**
    * Get the field at the specified x axis index.
    * @param idx the index of field in x axis.
    * @return the field at the specified index in x axis.
    */
   ChartRef getXField(int idx);

   /**
    * Get the field at the specified y axis index.
    * @param idx the index of field in y axis.
    * @return the field at the specified index in y axis.
    */
   ChartRef getYField(int idx);

   /**
    * Get the fields at x axis.
    * @return the fields at the x axis.
    */
   ChartRef[] getXFields();

   /**
    * Get the fields at y axis.
    * @return the fields at the y axis.
    */
   ChartRef[] getYFields();

   /**
    * Get the number of x axis fields.
    * @return the x axis fields size.
    */
   int getXFieldCount();

   /**
    * Get the number of y axis fields.
    * @return the y axis fields size.
    */
   int getYFieldCount();

   /**
    * Get the runtime chart type of the chart.
    * @return the runtime chart type.
    */
   @Override
   int getRTChartType();

   /**
    * Set the runtime chart type of the chart.
    * @param type the runtime chart type of the chart.
    */
   @Override
   void setRTChartType(int type);

   /**
    * Get the color field.
    * @return the color field.
    */
   @Override
   AestheticRef getColorField();

   /**
    * Get the shape field.
    * @return the shape field.
    */
   @Override
   AestheticRef getShapeField();

   /**
    * Get the size field.
    */
   @Override
   AestheticRef getSizeField();

   /**
    * Get the text field.
    */
   @Override
   AestheticRef getTextField();

   /**
    * Get the field at the specified x axis index.
    * @param idx the index of field in x axis.
    * @return the field at the specified index in x axis.
    */
   ChartRef getGroupField(int idx);

   /**
    * Get the fields at x axis.
    * @return the fields at the x axis.
    */
   ChartRef[] getGroupFields();

   /**
    * Get the number of x axis fields.
    * @return the x axis fields size.
    */
   int getGroupFieldCount();

   /**
    * Get the facet coord flag for the chart.
    * @return true if chart contain facet coord .
    */
   boolean isFacet();

   /**
    * Set the facet coord flag for the chart.
    * @param facet true chart contains facet coord, false otherwise.
    */
   void setFacet(boolean facet);

   /**
    * Check whether is aggregated.
    * @return true if is aggregated, false otherwise.
    */
   boolean isAggregated();

   /**
    * Get the highlight definitions.
    */
   HighlightGroup getHighlightGroup();

   /**
    * Set the chart wide highlight.
    */
   void setHighlightGroup(HighlightGroup highlightGroup);

   /**
    * Get the text highlight definitions.
    */
   public HighlightGroup getTextHighlightGroup();

   /**
    * Set the highlight definitions.
    */
   public void setTextHighlightGroup(HighlightGroup highlightGroup);

   /**
    * Get hyperlink.
    */
   Hyperlink getHyperlink();

   /**
    * Check if it's inverted chart.
    */
   boolean isInvertedGraph();

   /**
    * Get the runtime chart type.
    * @param ctype the specified chart type.
    * @param xref the specified x ref.
    * @param yref the specified y ref.
    * @param mcount the specified measure count.
    */
   int getRTChartType(int ctype, ChartRef xref, ChartRef yref, int mcount);

   /**
    * Check if breakdown-by fields are supported.
    */
   boolean supportsGroupFields();

   /**
    * Check if path field is supported.
    */
   boolean supportsPathField();

   /**
    * Get the runtime fields in x dimension.
    */
   ChartRef[] getRTXFields();

   /**
    * Get the runtime fields in y dimension.
    */
   ChartRef[] getRTYFields();

   /**
    * Get the runtime fields at x axis.
    * @return the fields at the x axis.
    */
   ChartRef[] getRTGroupFields();

   /**
    * Remove the idx th x axis field.
    * @param idx the index of field in x axis.
    */
   void removeXField(int idx);

   /**
    * Remove the idx th y axis field.
    * @param idx the index of field in y axis.
    */
   void removeYField(int idx);

   /**
    * Set the color field.
    * @param field the specified color field.
    */
   @Override
   void setColorField(AestheticRef field);

   /**
    * Set the shape field.
    * @param field the specified shape field.
    */
   @Override
   void setShapeField(AestheticRef field);

   /**
    * Set the size field.
    * @param field the specified size field.
    */
   @Override
   void setSizeField(AestheticRef field);

   /**
    * Set the text field.
    * @param field the specified text field.
    */
   @Override
   void setTextField(AestheticRef field);

   /**
    * Set the runtime x fields.
    */
   void setRTXFields(ChartRef[] refs);

   /**
    * Set the runtime y fields.
    */
   void setRTYFields(ChartRef[] refs);

   /**
    * Add a field to be used as x axis.
    * @param field the specified field to be added to x axis.
    */
   void addXField(ChartRef field);

   /**
    * Add a field to be used as y axis.
    * @param field the specified field to be added to y axis.
    */
   void addYField(ChartRef field);

   /**
    * Add a field to the break-by list.
    */
   void addGroupField(ChartRef field);

   /**
    * Add a field to be used as x axis.
    * @param idx the index of the x axis.
    * @param field the specified field to be added to x axis.
    */
   void addXField(int idx, ChartRef field);

   /**
    * Add a field to be used as y axis.
    * @param idx the index of the y axis.
    * @param field the specified field to be added to y axis.
    */
   void addYField(int idx, ChartRef field);

   /**
    * Add a field to be used as group axis.
    * @param idx the index of the group axis.
    * @param field the specified field to be added to group axis.
    */
   void addGroupField(int idx, ChartRef field);

   /**
    * Set the field at the specified index in x axis.
    * @param idx the index of the x axis.
    * @param field the specified field to be added to x axis.
    */
   void setXField(int idx, ChartRef field);

   /**
    * Set the field at the specified index in y axis.
    * @param idx the index of the y axis.
    * @param field the specified field to be added to y axis.
    */
   void setYField(int idx, ChartRef field);

   /**
    * Set the field at the specified index in group axis.
    * @param idx the index of the group axis.
    * @param field the specified field to be added to group axis.
    */
   void setGroupField(int idx, ChartRef field);

   /**
    * Update the chart type.
    * @param seperate the seperate status of the chart.
    */
   void updateChartType(boolean seperate);

   /**
    * Update chart type by generating runtime chart type.
    * @param separated true if the chart types are maintained in aggregate,
    * false otherwise.
    * @param xrefs x refs.
    * @param yrefs y refs.
    */
   void updateChartType(boolean separated, ChartRef[] xrefs,
                        ChartRef[] yrefs);

   /**
    * Get the runtime color field.
    */
   @Override
   DataRef getRTColorField();

   /**
    * Get the runtime shape field.
    */
   @Override
   DataRef getRTShapeField();

   /**
    * Get the runtime size field.
    */
   @Override
   DataRef getRTSizeField();

   /**
    * Get the runtime text field.
    */
   @Override
   DataRef getRTTextField();

   /**
    * Get all runtime fields, including x, y and aesthetic fields.
    */
   VSDataRef[] getRTFields();

   /**
    * Get all runtime axis fields.
    */
   VSDataRef[] getRTAxisFields();

   /**
    * Get all fields, including x, y and aesthetic fields.
    */
   VSDataRef[] getFields();

   /**
    * Get all the AestheticRefs.
    * @param runtime true to get the runtime fields.
    */
   AestheticRef[] getAestheticRefs(boolean runtime);

   /**
    * Get the chart descriptor.
    */
   ChartDescriptor getChartDescriptor();

   /**
    * Set the chart descriptor.
    */
   void setChartDescriptor(ChartDescriptor chartDescriptor);

   /**
    * Get Aggregate refs.
    */
   VSDataRef[] getAggregateRefs();

   /**
    * Get ratio to increase or decrease unit width.
    */
   double getUnitWidthRatio();

   /**
    * Set ratio to increase or decrease unit width.
    */
   void setUnitWidthRatio(double ratio);

   /**
    * Get ratio to increase or decrease unit height.
    */
   double getUnitHeightRatio();

   /**
    * Set ratio to increase or decrease unit height.
    */
   void setUnitHeightRatio(double ratio);

   /**
    * Get the current effective width ratio.
    */
   double getEffectiveWidthRatio();

   /**
    * Set the current effective width ratio.
    */
   void setEffectiveWidthRatio(double ratio);

   /**
    * Get the current effective height ratio.
    */
   double getEffectiveHeightRatio();

   /**
    * Set the current effective height ratio.
    */
   void setEffectiveHeightRatio(double ratio);

   /**
    * Check if the unit width has been resized by user.
    */
   boolean isWidthResized();

   /**
    * Set if the unit width has been resized by user.
    */
   void setWidthResized(boolean resized);

   /**
    * Check if the unit height has been resized by user.
    */
   boolean isHeightResized();

   /**
    * Set if the unit height has been resized by user.
    */
   void setHeightResized(boolean resized);

   /**
    * Get the default measure of the chart if no default measure exists,
    * null will be returned.
    */
   String getDefaultMeasure();

   /**
    * Remove the chart binding breakable fields.
    */
   void removeGroupFields();

   /**
    * Remove the chart binding fields.
    */
   void removeFields();

   /**
    * Check if color frame information has been modified from the default.
    */
   boolean isColorChanged(String... vars);

   /**
    * Check if shape frame information has been modified from the default.
    */
   boolean isShapeChanged(String... vars);

   /**
    * Check if size frame information has been modified from the default.
    */
   boolean isSizeChanged(String... vars);

   /**
    * Check if each measure is assigned a different color.
    */
   boolean supportsColorFieldFrame();

   /**
    * Check if each measure is assigned a different shape.
    */
   boolean supportsShapeFieldFrame();

   /**
    * Check if each measure is assigned a different size.
    */
   boolean supportsSizeFieldFrame();

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   boolean equalsContent(Object obj);

   /**
    * Get the adhoc enabled flag for the chart.
    */
   boolean isAdhocEnabled();

   /**
    * Set the adhoc enabled flag for the chart.
    * @param adhoc true to enable adhoc on chart, false otherwise.
    */
   void setAdhocEnabled(boolean adhoc);

   /**
    * Get the map type.
    * @return the map type.
    */
   String getMapType();

   /**
    * Gets the map type used if only measures are bound to the map.
    *
    * @return the map type.
    */
   String getMeasureMapType();

   /**
    * Sets the map type used if only measures are bound to the map.
    *
    * @param type the map type.
    */
   void setMeasureMapType(String type);

   /**
    * Set the tool tip.
    */
   void setToolTip(String toolTip);

   /**
    * Get the tool tip. Returns the custom tooltip template. or null if using standard tooltip,
    */
   String getToolTip();

   /**
    * Get the user entered tooltip. This is never cleared and can be used to retrieve
    * the tooltip when switching back to custom tooltip.
    */
   String getCustomTooltip();

   /**
    * Set if the tool tip should display data from other lines in a line/area chart.
    */
   void setCombinedToolTip(boolean combinedToolTip);

   /**
    * Get if the tool tip should display data from other lines in a line/area chart.
    */
   boolean isCombinedToolTip();

   /**
    * Get the chart style.
    */
   int getChartStyle();

   /**
    * Check if this is a special donut chart, where a middle label is added to show total.
    */
   boolean isDonut();

   /**
    * Set whether this is a special donut chart.
    */
   void setDonut(boolean donut);

   /**
    * Check if tooltip should be shown.
    */
   boolean isTooltipVisible();

   /**
    * Set if tooltip should be shown.
    */
   void setTooltipVisible(boolean tooltipVisible);

   /**
    * Get runtime field by a full name.
    * @param name the specified field full name or name.
    * @return the runtime field.
    */
   VSDataRef getRTFieldByFullName(String name);

   /**
    * Get the field by a full name or name.
    * @param name the specified field full name or name.
    * @param rt true to search runtime fields and normal field, otherwise only
    * search non-runtime fields.
    * @return the field of the name.
    */
   ChartRef getFieldByName(String name, boolean rt);

   ChartRef getFieldByName(String name, boolean rt, boolean ignoreDataGroup);

   /**
    * Get the field to used to sort the points into a path (line).
    */
   ChartRef getPathField();

   /**
    * Get the runtime field to used to sort the points into a path (line).
    */
   ChartRef getRTPathField();

   /**
    * Set the field to used to sort the points into a path (line).
    */
   void setPathField(ChartRef ref);

   /**
    * Get chart aggregate model refs, for no shape aesthetic ref.
    */
   ChartRef[] getModelRefs(boolean runtime);

   /**
    * Get chart aggregate model X refs, for no shape aesthetic ref.
    */
   ChartRef[] getModelRefsX(boolean runtime);

   /**
    * Get chart aggregate model Y refs, for no shape aesthetic ref.
    */
   ChartRef[] getModelRefsY(boolean runtime);

    /**
    * Get all the binding refs from X and Y.
    */
   ChartRef[] getBindingRefs(boolean runtime);

   /**
    * Get the runtime fields.
    * @param xy true to include x/y fields.
    * @param aesthetic true to include global aesthetic fields.
    * @param aggrAesthetic true to include aesthetic fields in aggregates.
    * @param others true to include group (break-by) and path fields.
    */
   VSDataRef[] getRTFields(boolean xy, boolean aesthetic, boolean aggrAesthetic, boolean others);

   /**
    * Get all runtime fields in common groups. If an aggregate has dimensions
    * bound to aesthetic fields, it may result in different grouping from
    * the default query.
    * @return a map from groups to aggregates for each unique grouping.
    */
   Map<Set,Set> getRTFieldGroups();

   /**
    * Get all runtime fields in common groups. If an aggregate has dimensions
    * bound to aesthetic fields, it may result in different grouping from
    * the default query.
    * @return a map from groups to aggregates for each unique grouping.
    */
   default Map<Set,Set> getRTFieldGroups(DataRef[] extraGroups) {
      return getRTFieldGroups();
   }

   /**
    * Similar to getRTFieldGroups, but returns a map from group to aggregates for
    * each discrete aggregate. Since discrete aggregates are grouped by the dimensions
    * defined on the same dimension (x or y), the grouping would be different from the
    * non-discrete aggregates.
    */
   Map<Set,Set> getRTDiscreteAggregates();

   /**
    * Get all the aggregaterefs which contains the specifed dataref.
    * @param aesthetic if contains aesthetic refs.
    */
   XAggregateRef[] getAggregates(DataRef fld, boolean aesthetic);

   /**
    * Clear the runtime fields.
    */
   void clearRuntime();

   /**
    * Determine whether the data in this chart can be projected forward
    */
   // @by ChrisSpagnoli bug1427783978948 2015-4-1
   boolean canProjectForward();

   /**
    * Replace the old filed to new filed.
    * @param oldFiled replaced field.
    * @param newFiled new filed to replace the old filed.
    * @return true replace success else if false.
    */
   boolean replaceField(ChartRef oldFiled, ChartRef newFiled);

   /**
    * Determine whether the data in this chart can be projected forward
    * @param rt check runtime binding.
    */
   boolean canProjectForward(boolean rt);

   default boolean shouldAggregate(ChartRef ref) {
      if(!GraphTypes.isBoxplot(getChartType()) ||
         ref instanceof ChartAggregateRef && ((ChartAggregateRef) ref).isDiscrete())
      {
         return true;
      }

      // if aggregate calc exists, the data is always aggregated. (62428)
      boolean hasAggrCalc = Arrays.stream(getAggregateRefs())
         .map(a -> ((ChartAggregateRef) a).getDataRef())
         .anyMatch(a -> VSUtil.isAggregateCalc(a));
      return hasAggrCalc;
   }

   /**
    * Get chart dimensions/measures on x/y.
    */
   default ChartRef[] getXYFields() {
      ArrayList<ChartRef> refs = new ArrayList<>();
      ArrayList<String> names = new ArrayList<>();

      for(int i = 0; i < getYFieldCount(); i++) {
         ChartRef ref = getYField(i);

         if(!names.contains(ref.getFullName())) {
            refs.add(ref);
            names.add(ref.getFullName());
         }
      }

      for(int i = 0; i < getXFieldCount(); i++) {
         ChartRef ref = getXField(i);

         if(!names.contains(ref.getFullName())) {
            refs.add(ref);
            names.add(ref.getFullName());
         }
      }

      return refs.toArray(new ChartRef[0]);
   }

   /**
    * Get chart measures on x/y.
    */
   default ChartRef[] getXYMeasures() {
      return Arrays.stream(getXYFields())
         .filter(a -> GraphUtil.isMeasure(a)).toArray(ChartRef[]::new);
   }

   /**
    * Get the dimension in the inner-most coordinate.
    */
   default ChartRef getPrimaryDimension(boolean rt) {
      ChartRef[] primaryFields;

      if(isInvertedGraph()) {
         primaryFields = rt ? getRTYFields() : getYFields();
      }
      else {
         primaryFields = rt ? getRTXFields() : getXFields();
      }

      return primaryFields.length == 0 ? null : primaryFields[primaryFields.length - 1];
   }

   /**
    * Get all the aggregaterefs which contains the specifed dataref.
    * @param fld The target ref to search for
    * @param aesthetic if true, will include aesthetic refs, false will not.
    */
   XAggregateRef[] getAllAggregates(DataRef fld, boolean aesthetic);

   /**
    * Get all the dimensionrefs which contains the specifed dataref.
    * @param others if contains other refs as group, aesthetic fields.
    */
   XDimensionRef[] getAllDimensions(DataRef fld, boolean others);

   /**
    * @param colname the specified field full name or name.
    * @return the runtime field.
    */
   default VSDataRef getRTFieldByColName(String colname) {
      VSDataRef[] refs = getRTFields();

      for(VSDataRef ref : refs) {
         if(ref instanceof XAggregateRef &&
            ((XAggregateRef) ref).getFullName(false).equals(colname))
         {
            return ref;
         }
      }

      return null;
   }
}
