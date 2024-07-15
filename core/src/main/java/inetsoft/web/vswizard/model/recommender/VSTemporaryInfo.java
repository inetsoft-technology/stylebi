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
package inetsoft.web.vswizard.model.recommender;

import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.util.Tool;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;

import java.awt.*;
import java.util.*;

/**
 * VSTemporaryInfo holding the temporary info when creating/editing a viewsheet
 * visualization object in vs wizard.
 *
 * @version 13.2
 * @author  InetSoft Technology Corp.
 */
public class VSTemporaryInfo implements Cloneable {
   /**
    * Setter for the recommendation model in vs wizard.
    */
   public void setRecommendationModel(VSRecommendationModel recommendationModel) {
      this.recommendationModel = recommendationModel;
   }

   /**
    * Getter for the recommendation model in vs wizard.
    */
   public VSRecommendationModel getRecommendationModel() {
      return recommendationModel;
   }

   /**
    * when edit assembly enters object wizard,
    * set the original model in vs wizard.
    */
   public void setOriginalModel(VSWizardOriginalModel originalModel) {
      this.originalModel = originalModel;
   }

   /**
    * when exiting object wizard, get the original model in vs wizard
    * @return
    */
   public VSWizardOriginalModel getOriginalModel() {
      return originalModel;
   }

     /**
    * Getter for tempChart which is used to hold temporary binding informations.
    * 1. AggregateInfo, used to distinguish dimensions and measures(include converted fields).
    * 2. GeoRefs, used to store geo refs which were setted/edited in binding tree.
    * 3. Dimensions and aggregates, the current binding dimensions and aggregates which shared
    *   by all the recommend assemblies.
    */
   public ChartVSAssembly getTempChart() {
      return tempChart;
   }

   /**
    * Setter for tempChart which is used to hold temporary binding informations.
    */
   public void setTempChart(ChartVSAssembly tempChart) {
      this.tempChart = tempChart;
   }

   /**
    * Getter for the position which is the current created/edit assembly in object wizard.
    */
   public Point getPosition() {
      return position;
   }

   /**
    * Setter for the position which is the current created/edit assembly in object wizard.
    */
   public void setPosition(Point position) {
      this.position = position;
   }

   /**
    * Getter for format map.
    */
   public Map<String, TempFieldFormat> getFormatMap() {
      return formatMap;
   }

   /**
    * Get the user format for a column.
    */
   public VSFormat getUserFormat(String name) {
      TempFieldFormat tempFieldFormat = formatMap.get(name);

      if(tempFieldFormat == null) {
         return null;
      }

      return tempFieldFormat.getUserFormat() != null ? tempFieldFormat.getUserFormat() : null;
   }

   /**
    * Get the format for a column.
    */
   public VSFormat getFormat(String name) {
      TempFieldFormat tempFieldFormat = formatMap.get(name);

      if(tempFieldFormat == null) {
         return null;
      }

      return tempFieldFormat.getUserFormat() != null ? tempFieldFormat.getUserFormat() :
         tempFieldFormat.getDefaultFormat();
   }

   /**
    * Get the format for a column.
    */
   public VSFormat getDefaultFormat(String name) {
      TempFieldFormat tempFieldFormat = formatMap.get(name);

      return tempFieldFormat == null ? null : tempFieldFormat.getDefaultFormat();
   }

   /**
    * Put format for format map
    */
   public void setFormat(String value, VSFormat format) {
      TempFieldFormat tempFieldFormat = formatMap.get(value);

      if(tempFieldFormat == null) {
         tempFieldFormat = new TempFieldFormat();
         formatMap.put(value, tempFieldFormat);;
      }

      tempFieldFormat.setUserFormat(format);
   }

   /**
    * Put format for format map
    */
   public void setDefaultFormat(String value, VSFormat format) {
      TempFieldFormat tempFieldFormat = formatMap.get(value);

      if(tempFieldFormat == null) {
         tempFieldFormat = new TempFieldFormat();
         formatMap.put(value, tempFieldFormat);;
      }

      tempFieldFormat.setDefaultFormat(format);
   }

   /**
    * Setter for description of the text which is set by preview pane.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Getter for description.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Getter for wizard previewPaneSize.
    */
   public Dimension getPreviewPaneSize() {
      return previewPaneSize;
   }

   /**
    * Setter for wizard previewPaneSize.
    */
   public void setPreviewPaneSize(Dimension previewPaneSize) {
      this.previewPaneSize = previewPaneSize;
   }

   public void destroyTempBindingInfo() {
      destroyed = true;
      position = null;
      description = null;
      tempChart = null;
      originalModel = null;
      recommendationModel = null;
      selectedType = null;
      selectedSubType = null;
      formatMap = new HashMap<>();
      recommendLatestTime = null;
   }

   public boolean isDestroyed() {
      return destroyed;
   }

   /**
    * Set the user EXPLICITLY selected type.
    */
   public void setSelectedType(VSRecommendType type) {
      this.selectedType = type;
   }

   public VSRecommendType getSelectedType() {
      return selectedType;
   }

   public VSSubType getSelectedSubType() {
      return selectedSubType;
   }

   /**
    * Set the user EXPLICITLY selected sub-type.
    */
   public void setSelectedSubType(VSSubType selectedSubType) {
      this.selectedSubType = selectedSubType;
   }

   public void removeFormat(String name) {
      formatMap.remove(name);
   }

   public boolean isAutoOrder() {
      return autoOrder;
   }

   public void setAutoOrder(boolean autoOrder) {
      this.autoOrder = autoOrder;
   }

   public Date getRecommendLatestTime() {
      return recommendLatestTime;
   }

   public void setRecommendLatestTime(Date recommendLatestTime) {
      this.recommendLatestTime = recommendLatestTime;
   }

   public boolean isShowLegend() {
      return showLegend;
   }

   public void setShowLegend(boolean showLegend) {
      this.showLegend = showLegend;
   }

   public void setBrandNewColumn(String name, boolean isBrandNew) {
      this.columnBrandNewMap.put(name, isBrandNew);
   }

   public boolean isBrandNewColumn(String name) {
      return this.columnBrandNewMap.get(name) == null ? false : this.columnBrandNewMap.get(name);
   }

   @Override
   public Object clone() throws CloneNotSupportedException {
      VSTemporaryInfo clone = (VSTemporaryInfo) super.clone();

      if(position != null) {
         clone.position = new Point(position);
      }

      if(previewPaneSize != null) {
         clone.previewPaneSize = new Dimension(previewPaneSize);
      }

      clone.description = description;
      clone.recommendationModel = (VSRecommendationModel) Tool.clone(recommendationModel);
      clone.originalModel = (VSWizardOriginalModel) Tool.clone(originalModel);

      clone.tempChart = (ChartVSAssembly) Tool.clone(tempChart);
      clone.selectedType = selectedType;
      clone.selectedSubType = (VSSubType) Tool.clone(selectedSubType);
      clone.formatMap = (Map<String, TempFieldFormat>) Tool.clone(formatMap);
      clone.autoOrder = autoOrder;
      clone.recommendLatestTime = (Date) Tool.clone(recommendLatestTime);
      clone.showLegend = showLegend;

      return clone;
   }

   public static class TempFieldFormat {
      public VSFormat getDefaultFormat() {
         return defaultFormat;
      }

      public void setDefaultFormat(VSFormat defaultFormat) {
         this.defaultFormat = defaultFormat;
      }

      public VSFormat getUserFormat() {
         return userFormat;
      }

      public void setUserFormat(VSFormat userFormat) {
         this.userFormat = userFormat;
      }

      private VSFormat defaultFormat;
      private VSFormat userFormat;
   }

   public static final String HEADER_FORMAT_STRING = "_wizard_header";

   private Point position;
   private Dimension previewPaneSize;
   private String description;
   private VSRecommendationModel recommendationModel;
   private VSWizardOriginalModel originalModel;
   private ChartVSAssembly tempChart;
   private VSRecommendType selectedType;
   private VSSubType selectedSubType;
   private Map<String, TempFieldFormat> formatMap = new HashMap<>();
   private boolean autoOrder = true;
   private Date recommendLatestTime;
   private boolean showLegend = true;
   private boolean destroyed;
   private Map<String, Boolean> columnBrandNewMap = new HashMap<>();
}
