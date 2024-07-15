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
package inetsoft.web.binding.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.model.graph.aesthetic.*;

import java.util.ArrayList;
import java.util.List;

public class ChartBindingModel extends BindingModel implements ChartAestheticModel,
   DateComparableModel
{
   /**
    * Constructor.
    */
   public ChartBindingModel() {
   }

   /**
    * Get the fields at x axis.
    * @return the fields at the x axis.
    */
   public List<ChartRefModel> getXFields() {
      return xfields;
   }

   /**
    * Set the field at the specified index in x axis.
    */
   public void setXFields(List<ChartRefModel> xfields) {
      this.xfields = xfields;
   }

   /**
    * Add a field to be used as x axis.
    */
   public void addXField(ChartRefModel info) {
      xfields.add(info);
   }

   /**
    * Get the fields at y axis.
    * @return the fields at the y axis.
    */
   public List<ChartRefModel> getYFields() {
      return yfields;
   }

   /**
    * Set the field at the specified index in y axis.
    */
   public void setYFields(List<ChartRefModel> yfields) {
      this.yfields = yfields;
   }

   /**
    * Add a field to be used as y axis.
    */
   public void addYField(ChartRefModel info) {
      yfields.add(info);
   }

      /**
    * Get the fields at group axis.
    * @return the fields at the group axis.
    */
   public List<ChartRefModel> getGroupFields() {
      return groupFields;
   }

   /**
    * Set the field at the specified index in group axis.
    */
   public void setGroupFields(List<ChartRefModel> groupFields) {
      this.groupFields = groupFields;
   }

   /**
    * Add a field to be used as group axis.
    */
   public void addGroupField(ChartRefModel info) {
      groupFields.add(info);
   }

   /**
    * Get all the geo columns.
    */
   public List<DataRefModel> getGeoCols() {
      return geoCols;
   }

   /**
    * Set all the geo columns.
    */
   public void setGeoCols(List<DataRefModel> geoCols) {
      this.geoCols = geoCols;
   }

   /**
    * Add a field to be used as geo column.
    * @param info the specified info to be added to geo column.
    */
   public void addGeoCol(DataRefModel info) {
      geoCols.add(info);
   }

   /**
    * Get all the geo field.
    */
   public List<ChartRefModel> getGeoFields() {
      return geoFields;
   }

   /**
    * Set all the geo field.
    */
   public void setGeoFields(List<ChartRefModel> geoFields) {
      this.geoFields = geoFields;
   }

   /**
    * Add a field to be used as geo field.
    * @param info the specified info to be added to geo field.
    */
   public void addGeoField(ChartRefModel info) {
      geoFields.add(info);
   }

   /**
    * Check if waterfall chart.
    */
   public boolean isWaterfall() {
      return waterfall;
   }

   /**
    * Set if multistyle.
    */
   public void setWaterfall(boolean waterfall) {
      this.waterfall = waterfall;
   }

   public boolean isWordCloud() {
      return wordCloud;
   }

   public void setWordCloud(boolean wordCloud) {
      this.wordCloud = wordCloud;
   }

   /**
    * Check if multistyle.
    */
   public boolean isMultiStyles() {
      return multiStyles;
   }

   /**
    * Set if multistyle.
    */
   public void setMultiStyles(boolean multi) {
      this.multiStyles = multi;
   }

   /**
    * Get charttype.
    */
   @Override
   public int getChartType() {
      return chartType;
   }

   /**
    * Set charttype.
    */
   @Override
   public void setChartType(int type) {
      this.chartType = type;
   }

   /**
    * Get the runtime chart type on the ref.
    * @return the chart type.
    */
   @Override
   public int getRTChartType() {
      return this.rtChartType;
   }

   /**
    * Set the runtime chart type on the ref.
    */
   @Override
   public void setRTChartType(int ctype) {
      this.rtChartType = ctype;
   }

   /**
    * Set AllChartAggregate model for multistyle chart.
    */
   public AllChartAggregateRefModel getAllChartAggregate() {
      return this.allAggr;
   }

   /**
    * Get AllChartAggregate model for multistyle chart.
    */
   public void setAllChartAggregate(AllChartAggregateRefModel aggr) {
      this.allAggr = aggr;
   }

   /**
    * Get color field model.
    */
   @Override
   public AestheticInfo getColorField() {
      return colorField;
   }

   /**
    * Set color field model.
    */
   @Override
   public void setColorField(AestheticInfo colorField) {
      this.colorField = colorField;
   }

   /**
    * Get shape field model.
    */
   @Override
   public AestheticInfo getShapeField() {
      return shapeField;
   }

   /**
    * Set shape field model.
    */
   @Override
   public void setShapeField(AestheticInfo shapeField) {
      this.shapeField = shapeField;
   }

   /**
    * Get shape field model.
    */
   @Override
   public AestheticInfo getSizeField() {
      return sizeField;
   }

   /**
    * Set shape field model.
    */
   @Override
   public void setSizeField(AestheticInfo sizeField) {
      this.sizeField = sizeField;
   }

   /**
    * Get shape field model.
    */
   @Override
   public AestheticInfo getTextField() {
      return textField;
   }

   /**
    * Set shape field model.
    */
   @Override
   public void setTextField(AestheticInfo textField) {
      this.textField = textField;
   }

   @Override
   public void setColorFrame(ColorFrameModel frame) {
      this.colorFrame = frame;
   }

   @Override
   public ColorFrameModel getColorFrame() {
      return colorFrame;
   }

   @Override
   public void setShapeFrame(ShapeFrameModel frame) {
      this.shapeFrame = frame;
   }

   @Override
   public ShapeFrameModel getShapeFrame() {
      return shapeFrame;
   }

   @Override
   public void setTextureFrame(TextureFrameModel frame) {
      this.textureFrame = frame;
   }

   @Override
   public TextureFrameModel getTextureFrame() {
      return textureFrame;
   }

   @Override
   public void setLineFrame(LineFrameModel frame) {
      this.lineFrame = frame;
   }

   @Override
   public LineFrameModel getLineFrame() {
      return lineFrame;
   }

   @Override
   public void setSizeFrame(SizeFrameModel frame) {
      this.sizeFrame = frame;
   }

   @Override
   public SizeFrameModel getSizeFrame() {
      return sizeFrame;
   }

   /**
    * Get the map type.
    * @return the map type.
    */
   public String getMapType() {
      return mapType;
   }

   /**
    * Set the map type.
    * @param type the map type.
    */
   public void setMapType(String type) {
      this.mapType = type;
   }

   /**
    * Set the separate graph mode.
    * @param separated the specified separate graph mode.
    */
   public void setSeparated(boolean separated) {
      this.separated = separated;
   }

   /**
    * Check if separated graph.
    * @return the separated graph mode.
    */
   public boolean isSeparated() {
      return separated;
   }

   /**
    * Set if breakdown-by fields are supported.
    */
   public void setSupportsGroupFields(boolean supportsGroupFields) {
      this.supportsGroupFields = supportsGroupFields;
   }

   /**
    * Check if breakdown-by fields are supported.
    */
   public boolean isSupportsGroupFields() {
      return supportsGroupFields;
   }
   /**
    * Set the close field.
    * @param field the specified close field.
    */
   public void setCloseField(ChartRefModel field) {
      this.closeField = field;
   }

   /**
    * Set the open field.
    * @param field the specified open field.
    */
   public void setOpenField(ChartRefModel field) {
      this.openField = field;
   }

   /**
    * Set the high field.
    * @param field the specified high field.
    */
   public void setHighField(ChartRefModel field) {
      this.highField = field;
   }

   /**
    * Set the low field.
    * @param field the specified low field.
    */
   public void setLowField(ChartRefModel field) {
      this.lowField = field;
   }

   /**
    * Get the close field.
    * @return the close field.
    */
   public ChartRefModel getCloseField() {
      return this.closeField;
   }

   /**
    * Get the open field.
    * @return the open field.
    */
   public ChartRefModel getOpenField() {
      return this.openField;
   }

   /**
    * Get the high field.
    * @return the high field.
    */
   public ChartRefModel getHighField() {
      return this.highField;
   }

   /**
    * Get the low field.
    * @return the low field.
    */
   public ChartRefModel getLowField() {
      return this.lowField;
   }

   /**
    * Get the field to used to sort the points into a path (line).
    */
   public ChartRefModel getPathField() {
      return pathField;
   }

   /**
    * Set the field to used to sort the points into a path (line).
    */
   public void setPathField(ChartRefModel pathField) {
      this.pathField = pathField;
   }

   public ChartRefModel getSourceField() {
      return sourceField;
   }

   public void setSourceField(ChartRefModel sourceField) {
      this.sourceField = sourceField;
   }

   public ChartRefModel getTargetField() {
      return targetField;
   }

   public void setTargetField(ChartRefModel targetField) {
      this.targetField = targetField;
   }

   /**
    * Check if path field is supported.
    */
   public boolean isSupportsPathField() {
      return supportsPathField;
   }

   /**
    * Set the supportsPathField.
    */
   public void setSupportsPathField(boolean supportsPathField) {
      this.supportsPathField = supportsPathField;
   }

   public boolean isPointLine() {
      return pointLine;
   }

   public void setPointLine(boolean pointLine) {
      this.pointLine = pointLine;
   }

   @Override
   @JsonIgnore
   public OriginalDescriptor getAggregateDesc() {
      return null;
   }

   public ChartRefModel getStartField() {
      return startField;
   }

   public void setStartField(ChartRefModel startField) {
      this.startField = startField;
   }

   public ChartRefModel getEndField() {
      return endField;
   }

   public void setEndField(ChartRefModel endField) {
      this.endField = endField;
   }

   public ChartRefModel getMilestoneField() {
      return milestoneField;
   }

   public void setMilestoneField(ChartRefModel milestoneField) {
      this.milestoneField = milestoneField;
   }

   public boolean isStackMeasures() {
      return stackMeasures;
   }

   public void setStackMeasures(boolean stackMeasures) {
      this.stackMeasures = stackMeasures;
   }

   @Override
   public boolean isHasDateComparison() {
      return hasDateComparison;
   }

   @Override
   public void setHasDateComparison(boolean hasDateComparison) {
      this.hasDateComparison = hasDateComparison;
   }

   @Override
   public AestheticInfo getNodeColorField() {
      return nodeColorField;
   }

   @Override
   public void setNodeColorField(AestheticInfo nodeColorField) {
      this.nodeColorField = nodeColorField;
   }

   @Override
   public AestheticInfo getNodeSizeField() {
      return nodeSizeField;
   }

   @Override
   public void setNodeSizeField(AestheticInfo nodeSizeField) {
      this.nodeSizeField = nodeSizeField;
   }

   @Override
   public void setNodeColorFrame(ColorFrameModel frame) {
      this.nodeColorFrame = frame;
   }

   @Override
   public ColorFrameModel getNodeColorFrame() {
      return nodeColorFrame;
   }

   @Override
   public void setNodeSizeFrame(SizeFrameModel frame) {
      this.nodeSizeFrame = frame;
   }

   @Override
   public SizeFrameModel getNodeSizeFrame() {
      return nodeSizeFrame;
   }

   private boolean waterfall;
   private boolean multiStyles;
   private boolean stackMeasures;
   private boolean separated = true;
   private boolean hasDateComparison;
   private int chartType = GraphTypes.CHART_AUTO;
   private int rtChartType = GraphTypes.CHART_AUTO;
   private String mapType = "";
   private AestheticInfo colorField;
   private AestheticInfo shapeField;
   private AestheticInfo sizeField;
   private AestheticInfo textField;
   private ColorFrameModel colorFrame;
   private ShapeFrameModel shapeFrame;
   private LineFrameModel lineFrame;
   private TextureFrameModel textureFrame;
   private SizeFrameModel sizeFrame;
   private AllChartAggregateRefModel allAggr; // for multistyle chart
   private List<ChartRefModel> xfields = new ArrayList<>();
   private List<ChartRefModel> yfields = new ArrayList<>();
   private List<ChartRefModel> geoFields = new ArrayList<>();
   private List<ChartRefModel> groupFields = new ArrayList<>();
   // for VSChartInfo
   private List<DataRefModel> geoCols = new ArrayList<>();
   private boolean supportsGroupFields;
   // candle report chart info
   private ChartRefModel openField;
   private ChartRefModel closeField;
   private ChartRefModel highField;
   private ChartRefModel lowField;
   // line path
   private ChartRefModel pathField;
   // tree fields
   private ChartRefModel sourceField;
   private ChartRefModel targetField;
   // gantt fields
   private ChartRefModel startField;
   private ChartRefModel endField;
   private ChartRefModel milestoneField;
   // tree chart info
   private boolean supportsPathField;
   private boolean pointLine;
   // relation chart info
   private ColorFrameModel nodeColorFrame;
   private SizeFrameModel nodeSizeFrame;
   private AestheticInfo nodeColorField;
   private AestheticInfo nodeSizeField;
   private boolean wordCloud;
}
