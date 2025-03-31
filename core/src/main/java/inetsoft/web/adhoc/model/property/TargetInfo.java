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
package inetsoft.web.adhoc.model.property;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.internal.Util;
import inetsoft.util.Catalog;
import inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TargetInfo implements Serializable {
   /**
    * Line target.
    */
   public static final int LINE_TARGET = 0;
   /**
    * Band target.
    */
   public static final int BAND_TARGET = 1;
   /**
    * Statistics target.
    */
   public static final int STATISTICS_TARGET = 2;

   /**
    * Gets the target line style
    */
   public int getLineStyle() {
      return lineStyle;
   }

   /**
    * Sets the target line style.
    */
   public void setLineStyle(int style) {
      this.lineStyle = style;
   }

   /**
    * Gets the target line color.
    */
   public ColorInfo getLineColor() {
      return lineColor;
   }

   /**
    * Sets the target line color.
    */
   public void setLineColor(ColorInfo color) {
      this.lineColor = color;
   }

   /**
    * Gets the fill above color.
    */
   public ColorInfo getFillAboveColor() {
      return fillAboveColor;
   }

   /**
    * Sets the fill above color.
    */
   public void setFillAboveColor(ColorInfo color) {
      this.fillAboveColor = color;
   }

   /**
    * Gets the fill below color.
    */
   public ColorInfo getFillBelowColor() {
      return fillBelowColor;
   }

   /**
    * Sets the fill below color.
    */
   public void setFillBelowColor(ColorInfo color) {
      this.fillBelowColor = color;
   }

   /**
    * Gets alpha.
    */
   public String getAlpha() {
      return alpha;
   }

   /**
    * Sets alpha.
    */
   public void setAlpha(String alpha) {
      this.alpha = alpha;
   }

   /**
    * Gets the fill band color.
    */
   public ColorInfo getFillBandColor() {
      return fillBandColor;
   }

   /**
    * Sets fill band color.
    */
   public void setFillBandColor(ColorInfo color) {
      this.fillBandColor = color;
   }

   /**
    *w hether the scope of the target is the entire chart or just
    * the individual sub-coordinates.
    */
   public boolean isChartScope() {
      return isChartScope;
   }

   /**
    * Sets whether is chart scope.
    */
   public void setChartScope(boolean isChartScope) {
      this.isChartScope = isChartScope;
   }

   /**
    * Gets selected measure.
    */
   public MeasureInfo getMeasure() {
      return measure;
   }

   /**
    * Sets selected measure.
    */
   public void setMeasure(MeasureInfo measure) {
      this.measure = measure;
   }

   /**
    * Get the field this target label is associated with.
    */
   public String getFieldLabel() {
      return fieldLabel;
   }

   /**
    * Set the field this target label is associated with.
    */
   public void setFieldLabel(String label) {
      this.fieldLabel = label;
   }

   /**
    * Sets the descripption of the target.
    */
   public void setGenericLabel(String genericLabel) {
      this.genericLabel = genericLabel;
   }

   /**
    * Gets the descripption of the target.
    */
   public String getGenericLabel() {
     return genericLabel;
   }

   /**
    * Gets the target value.
    */
   public String getValue() {
      return value;
   }

   /**
    * Sets the target value.
    */
   public void setValue(String value) {
      this.value = value;
   }

   /**
    * Gets the target label
    */
   public String getLabel() {
      return label;
   }

   /**
    * Sets the target label.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Gets tovalue
    */
   public String getToValue() {
      return toValue;
   }

   /**
    * Sets the to value
    */
   public void setToValue(String value) {
      this.toValue = value;
   }

   /**
    * Gets the to label.
    */
   public String getToLabel() {
      return toLabel;
   }

   /**
    * Sets the to label.
    */
   public void setToLabel(String label) {
      this.toLabel = label;
   }

   /**
    * Gets the target label formats
    */
   public String getLabelFormats() {
      return labelFormats;
   }

   /**
    * Sets the target label formats
    */
   public void setLabelFormats(String labels){
      this.labelFormats = labels;
   }

   /**
    * Gets the target index.
    */
   public int getIndex() {
      return index;
   }

   /**
    * Sets the target index.
    */
   public void setIndex(int index) {
      this.index = index;
   }

   /**
    * Gets flag of the target.
    * If equlas "Line", edit or add target commited in the line tab of the dialog.
    * If equals "Band", edit or add target commited in the band tab of the dialog.
    * If equals "Stat", edit of add target commited in the statistic tab of hte dialog.
    */
   public int getTabFlag() {
      return tabFlag;
   }

   /**
    * Sets the update flag.
    */
   public void setTabFlag(int flag) {
      this.tabFlag = flag;
   }

   /**
    * Gets the string label of the target. shows in the target list
    *  of the chart proeprty dialog.
    */
   public String getTargetString() {
      Catalog catalog = Catalog.getCatalog();
      targetString = genericLabel;

      if(measure.getName() != null || fieldLabel != null) {
         String temp = fieldLabel;
         temp = temp == null || temp.isEmpty() ? measure.getName() : fieldLabel;
         targetString += " " + Catalog.getCatalog().getString("of") + " " + temp;
      }

      return targetString +
         " [" + catalog.getString(Util.getLineStyleName(lineStyle)) + "]";

   }

   /**
    * Sets the target string.
    */
   public void setTargetString(String str) {
      this.targetString = str;
   }

   /**
    * Gets the strategyInfo
    */
   public StrategyInfo getStrategyInfo() {
      return strategyInfo;
   }

   /**
    * Sets the strategyInfo
    */
   public void setStrategyInfo(StrategyInfo strategyInfo) {
      this.strategyInfo = strategyInfo;
   }

   /**
    * Check whether the target is changed(user whether click edit button)
    * @return [description]
    */
   public boolean isChanged() {
      return changed;
   }

   /**
    * Sets changed.
    */
   public void setChanged(boolean change) {
      this.changed = change;
   }

   /**
    * Get Color Frame.
    */
   public CategoricalColorModel getBandFill() {
      return bandFill;
   }

   /**
    * Set color frame.
    */
   public void setBandFill(CategoricalColorModel bandFill) {
      this.bandFill = bandFill;
   }

   /**
    * Wheter support fill below color or fill above color.
    */
   public boolean isSupportFill(){
      return supportFill;
   }

   /**
    * Sets whether support fill below or above color.
    */
   public void setSupportFill(boolean support) {
      this.supportFill = support;
   }

   private MeasureInfo measure;
   private String fieldLabel;
   private String genericLabel;
   private String value = "";
   private String label = "{0}";
   private String toValue = "";
   private String toLabel = "{0}";
   private String labelFormats = "";
   private int lineStyle = 4097;
   private ColorInfo lineColor;
   private ColorInfo fillAboveColor;
   private ColorInfo fillBelowColor;
   private String alpha = "100";
   private ColorInfo fillBandColor;
   private boolean isChartScope = false;
   private int index = -1;
   private int tabFlag = LINE_TARGET;
   private boolean changed = true;
   private String targetString;
   private StrategyInfo strategyInfo;
   private CategoricalColorModel bandFill;
   private boolean supportFill = true;
}
