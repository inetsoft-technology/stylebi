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
package inetsoft.web.binding.model.graph;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.graph.aesthetic.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartAggregateRefModel extends BAggregateRefModel
   implements ChartRefModel, ChartAestheticModel
{
   /**
    * Constructor
    */
   public ChartAggregateRefModel() {
   }

   /**
    * Constructor
    */
   public ChartAggregateRefModel(ChartAggregateRef ref, ChartInfo cinfo) {
      super(ref);

      setOriFullName(ref.getFullName(false));
      setOriView(ref.toView(false));
      setDiscrete(ref.isDiscrete());
      setSecondaryY(ref.isSecondaryY());
      setAggregated(ref.isAggregateEnabled());
   }

   /**
    * Set the original descriptor.
    */
   @Override
   public void setOriginal(OriginalDescriptor original) {
      refModelImpl.setOriginal(original);
   }

   /**
    * Get the original descriptor.
    */
   @Override
   public OriginalDescriptor getOriginal() {
      return refModelImpl.getOriginal();
   }

   @Override
   public void setRefConvertEnabled(boolean refConvertEnabled) {
      refModelImpl.setRefConvertEnabled(refConvertEnabled);
   }

   @Override
   public boolean isRefConvertEnabled() {
      return refModelImpl.isRefConvertEnabled();
   }

   /**
    * Set this ref is treat as dimension or measure.
    */
   @Override
   public void setMeasure(boolean measure) {
   }

   /**
    * Check if this model is treat as dimension or measure.
    */
   @Override
   public boolean isMeasure() {
      return true;
   }

   @Override
   public AestheticInfo getColorField() {
      return colorField;
   }

   @Override
   public void setColorField(AestheticInfo colorField) {
      this.colorField = colorField;
   }

   @Override
   public AestheticInfo getShapeField() {
      return shapeField;
   }

   @Override
   public void setShapeField(AestheticInfo shapeField) {
      this.shapeField = shapeField;
   }

   @Override
   public AestheticInfo getSizeField() {
      return sizeField;
   }

   @Override
   public void setSizeField(AestheticInfo sizeField) {
      this.sizeField = sizeField;
   }

   @Override
   public AestheticInfo getTextField() {
      return textField;
   }

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
   public void setLineFrame(LineFrameModel frame) {
      this.lineFrame = frame;
   }

   @Override
   public LineFrameModel getLineFrame() {
      return lineFrame;
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
   public void setSizeFrame(SizeFrameModel frame) {
      this.sizeFrame = frame;
   }

   @Override
   public SizeFrameModel getSizeFrame() {
      return sizeFrame;
   }

   public void setSummaryColorFrame(ColorFrameModel frame) {
      this.summaryColorFrame = frame;
   }

   public ColorFrameModel getSummaryColorFrame() {
      return summaryColorFrame;
   }

   public void setSummaryTextureFrame(TextureFrameModel frame) {
      this.summaryTextureFrame = frame;
   }

   public TextureFrameModel getSummaryTextureFrame() {
      return summaryTextureFrame;
   }

   /**
    * Sets whether this aggregate should be treated like a dimension during
    * graph construction.
    */
   public void setDiscrete(boolean discrete) {
      this.discrete = discrete;
   }

   /**
    * Check whether this aggregate should be treated like a dimension during
    * graph construction.
    */
   public boolean isDiscrete() {
      return discrete;
   }


   /**
    * Get the chart type on the ref.
    * @return the chart type.
    */
   @Override
   public int getChartType() {
      return this.chartType;
   }

   /**
    * Set the chart type on the ref.
    */
   @Override
   public void setChartType(int ctype) {
      this.chartType = ctype;
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
    * Set whether to display this measure on the secondary Y axis.
    */
   public void setSecondaryY(boolean y2) {
      this.y2 = y2;
   }

   /**
    * Check whether to display this measure on the secondary Y axis.
    */
   public boolean isSecondaryY() {
      return y2;
   }

   /**
    * Check if the aggregate ref is aggregated. It's aggregated if the aggregate
    * formula is not none, and the aggregate flag is on.
    */
   public boolean isAggregated() {
      return aggregated;
   }

   /**
    * Set whether aggregation would be applied.
    */
   public void setAggregated(boolean aggregated) {
      this.aggregated = aggregated;
   }

   /**
    * Get original full name without calculator.
    */
   public String getOriFullName() {
      return oriFullName;
   }

   /**
    * set original full name without calculator.
    */
   public void setOriFullName(String oriFullName) {
      this.oriFullName = oriFullName;
   }

   /**
    * get view without calculator.
    */
   public String getOriView() {
      return oriView;
   }

   /**
    * set view without calculator.
    */
   public void setOriView(String oriView) {
      this.oriView = oriView;
   }

   @Override
   @JsonIgnore
   public OriginalDescriptor getAggregateDesc() {
      return getOriginal();
   }

   /**
    * Create a chartRef depend on chart info.
    */
   @Override
   public ChartRef createChartRef(ChartInfo cinfo) {
      return new VSChartAggregateRef();
   }

   private ChartRefModelImpl refModelImpl = new ChartRefModelImpl();
   private int chartType;
   private int rtChartType;
   private boolean discrete;
   private AestheticInfo colorField;
   private AestheticInfo shapeField;
   private AestheticInfo sizeField;
   private AestheticInfo textField;
   private ColorFrameModel colorFrame;
   private ShapeFrameModel shapeFrame;
   private LineFrameModel lineFrame;
   private TextureFrameModel textureFrame;
   private SizeFrameModel sizeFrame;
   private ColorFrameModel summaryColorFrame;
   private TextureFrameModel summaryTextureFrame;

   private boolean y2;
   private boolean aggregated;
   private String oriFullName;
   private String oriView;
}
