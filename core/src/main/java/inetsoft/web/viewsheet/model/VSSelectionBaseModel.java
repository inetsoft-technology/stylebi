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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class VSSelectionBaseModel<T extends AbstractSelectionVSAssembly>
   extends VSCompositeModel<T>
{
   public VSSelectionBaseModel(T assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      SelectionBaseVSAssemblyInfo assemblyInfo =
        (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();
      FormatInfo finfo = assemblyInfo.getFormatInfo();
      String measure = assemblyInfo.getMeasure();
      dropdown = assemblyInfo.getShowType() == SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE;
      singleSelection = assemblyInfo.isSingleSelection();
      showText = assemblyInfo.isShowText();
      showBar = assemblyInfo.isShowBar();
      this.measure = measure;
      sortType = assemblyInfo.getSortType();
      submitOnChange = assemblyInfo.isSubmitOnChange();
      searchString = assemblyInfo.getSearchString();
      int levels = 1;

      listHeight = assemblyInfo.getListHeight();
      cellHeight = assemblyInfo.getCellHeight();
      textWidth = (showText && measure != null) ? assemblyInfo.getMeasureSize() : 0;
      barWidth = (showBar && measure != null) ? assemblyInfo.getBarSize() : 0;

      final Dimension maxSize = assemblyInfo.getMaxSize();

      if(maxSize != null) {
         final VSFormatModel titleFormat = getTitleFormat();
         final VSFormatModel objectFormat = getObjectFormat();

         renderMaxMode(maxSize, assemblyInfo.getMaxModeZIndex(), titleFormat, objectFormat);
         this.maxMode = true;
      }

      if(assemblyInfo instanceof SelectionTreeVSAssemblyInfo) {
         SelectionTreeVSAssemblyInfo treeInfo = (SelectionTreeVSAssemblyInfo) assemblyInfo;

         if(treeInfo.getMode() == SelectionTreeVSAssemblyInfo.ID) {
            // this is recursive and is not known before hand
            levels = 10;
         }
         else {
            levels = treeInfo.getDataRefs().length;
         }
      }

      for(int i = 0; i < levels; i++) {
         TableDataPath path = SelectionBaseVSAssemblyInfo.getMeasureTextPath(i);
         VSCompositeFormat format = finfo.getFormat(path);

         if(format != null && !format.getUserDefinedFormat().isAlignmentValueDefined()) {
            format.getUserDefinedFormat().setAlignmentValue(
               format.getDefaultFormat().getAlignmentValue());
         }

         mformats.put(path.getPath()[0] + i, new VSFormatModel(format, assemblyInfo));

         VSFormatModel barFormat;
         path = SelectionBaseVSAssemblyInfo.getMeasureBarPath(i);
         format = finfo.getFormat(path);
         barFormat = new VSFormatModel(format, assemblyInfo);
         barFormat.setForeground(format == null ? "#8888FF" : barFormat.getForeground());
         mformats.put(path.getPath()[0] + i, barFormat);

         path = SelectionBaseVSAssemblyInfo.getMeasureNBarPath(i);
         format = finfo.getFormat(path);
         barFormat = new VSFormatModel(format, assemblyInfo);
         barFormat.setForeground(format == null ? "#8888FF" : barFormat.getForeground());
         mformats.put(path.getPath()[0] + i, barFormat);
      }
   }

   protected void renderMaxMode(Dimension maxSize,
                                int zIndex,
                                VSFormatModel titleFormat,
                                VSFormatModel objectFormat)
   {
      double width = Math.max(objectFormat.getWidth(), maxSize.getWidth());

      setMaxModeLayout(titleFormat, objectFormat, zIndex, maxSize.height,
         TOP_PADDING, width, LEFT_PADDING);
   }

   protected void setMaxModeLayout(VSFormatModel titleFormat, VSFormatModel objectFormat,
                                   int zIndex, int fullHeight, int top, double width, double left)
   {
      objectFormat.setLeft(left);
      objectFormat.setTop(top);
      objectFormat.setWidth(width);
      titleFormat.setWidth(width);
      objectFormat.setHeight(fullHeight);
      objectFormat.setzIndex(zIndex);
   }

   // Get the measure text/bar/negBar format
   public Map<String, VSFormatModel> getMeasureFormats() {
      return mformats;
   }

   public boolean isDropdown() {
      return dropdown;
   }

   public boolean isSingleSelection() {
      return singleSelection;
   }

   public int getListHeight() {
      return listHeight;
   }

   public int getSortType() {
      return sortType;
   }

   public double getTitleRatio() {
      return titleRatio;
   }

   public void setTitleRatio(double titleRatio) {
      this.titleRatio = titleRatio;
   }

   public int getCellHeight() {
      return cellHeight;
   }

   public boolean isShowText() {
      return showText;
   }

   public boolean isShowBar() {
      return showBar;
   }

   public String getMeasure() {
      return measure;
   }

   public void setMeasure(String measure) {
      this.measure = measure;
   }

   public int getBarWidth() {
      return barWidth;
   }

   public int getTextWidth() {
      return textWidth;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }
   /**
    * Set the search string.
    */
   public void setSearchString(String search) {
      this.searchString = search;
   }

   /**
    * Get the search string.
    */
   public String getSearchString() {
      return searchString;
   }

   public boolean isMaxMode() {
      return maxMode;
   }

   public void setMaxMode(boolean maxMode) {
      this.maxMode = maxMode;
   }

   @Override
   public String toString() {
      return "{" + super.toString() +
         "mformats=" + mformats +
         ", dropdown=" + dropdown +
         ", singleSelection=" + singleSelection +
         ", listHeight=" + listHeight +
         ", cellHeight=" + cellHeight +
         ", barWidth=" + barWidth +
         ", textWidth=" + textWidth +
         ", sortType=" + sortType +
         ", titleRatio=" + titleRatio +
         ", showText=" + showText +
         ", showBar=" + showBar +
         ", measure=" + measure +
         ", submitOnChange=" + submitOnChange +
         ", maxMode=" + maxMode +
         "} ";
   }

   // path -> format, level + ("Measure Text", "Measure Bar", "Measure Bar(-)")
   private Map<String, VSFormatModel> mformats = new HashMap<>();
   private boolean dropdown;
   private boolean singleSelection;
   private int listHeight;
   private int cellHeight;
   private int barWidth;
   private int textWidth;
   private int sortType;
   private double titleRatio = 1;
   private boolean showText;
   private boolean showBar;
   private boolean submitOnChange;
   private String searchString;
   private String measure;
   private boolean maxMode = false;
   protected static final int TOP_PADDING = 30; // padding mini-toolbar height
   protected static final int LEFT_PADDING = 20;

   private static final float MAX_MODE_WIDTH_RATIO = 0.2F;
   // default padding, and solves the scrollBar occlusion problem
   private static final int MAX_MODE_WIDTH_PADDING = 20;
}
