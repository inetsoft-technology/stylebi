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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.Common;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.springframework.stereotype.Component;

import java.awt.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSRangeSliderModel extends VSObjectModel<TimeSliderVSAssembly> {
   public VSRangeSliderModel(TimeSliderVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      TimeSliderVSAssemblyInfo assemblyInfo =
        (TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo();
      SelectionList slist = assemblyInfo.getSelectionList();
      labels = new String[slist == null ? 0 : slist.getSelectionValueCount()];
      values = new String[slist == null ? 0 : slist.getSelectionValueCount()];
      selectStart = -1;

      if(slist != null) {
         for(int i = 0; i < labels.length; i++) {
            SelectionValue sval = slist.getSelectionValue(i);

            labels[i] = sval.getLabel();
            values[i] = sval.getValue();

            if(sval.isSelected()) {
               if(selectStart < 0) {
                  selectStart = i;
               }

               selectEnd = i;
            }
         }
      }

      if(selectStart == -1) {
         selectStart = 0;
         selectEnd = Math.max(0, labels.length - 1);
      }

      minVisible = assemblyInfo.isMinVisible();
      maxVisible = assemblyInfo.isMaxVisible();
      tickVisible = assemblyInfo.isTickVisible();
      currentVisible = assemblyInfo.isCurrentVisible();
      title = assembly.getTitle();
      hidden = assemblyInfo.isHidden();
      upperInclusive = assemblyInfo.isUpperInclusive();
      composite = assemblyInfo.isComposite();
      adhocFilter = assemblyInfo.isAdhocFilter();
      submitOnChange = assemblyInfo.isSubmitOnChange();

      if(composite && !upperInclusive) {
         selectEnd = Math.min(labels.length - 1, selectEnd + 1);
      }

      DataRef[] refs = assemblyInfo.getDataRefs();
      dataType = refs.length == 1 && refs[0] != null ? refs[0].getDataType() : null;
      VSAssembly container = assembly.getContainer();
      final VSFormatModel objectFormat = this.getObjectFormat();
      Dimension maxSize = assemblyInfo.getMaxSize();

      if(maxSize != null) {
         double width = objectFormat.getWidth();
         double height = objectFormat.getHeight();
         double left = 0, top = 0;

         if(maxSize.getHeight() > height) {
            top = VSEventUtil.calcCenterStart(maxSize.getHeight(), height);
         }

         if(maxSize.getWidth() > width) {
            width = maxSize.getWidth() * 0.8; // 80%. full is not good
            left = VSEventUtil.calcCenterStart(maxSize.getWidth(), width);
         }

         objectFormat.setPositions(left, top, width, height);
         objectFormat.setzIndex(assemblyInfo.getMaxModeZIndex());
         this.maxMode = true;
      }

      renderSizeWithParentMaxMode(assembly.getContainer());
      TableDataPath titlepath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat compositeTitleFormat =
         assemblyInfo.getFormatInfo().getFormat(titlepath, false);
      titleFormat = new VSFormatModel(compositeTitleFormat, assemblyInfo);
      int titleHeight = assemblyInfo.getTitleHeight();
      Dimension size = new Dimension((int) getObjectFormat().getWidth(), titleHeight);
      titleFormat.setPositions(new Point(0, 0), size);

      if(maxSize == null && container instanceof CurrentSelectionVSAssembly) {
         int width = (int) objectFormat.getWidth();
         int height = (int) objectFormat.getHeight();
         final TableDataPath path = new TableDataPath(-1, TableDataPath.OBJECT);
         final VSCompositeFormat format = container.getFormatInfo().getFormat(path);
         final Insets borders = format.getBorders();

         // when a range slider is the child of a selection container its pushed over by the borders
         if(borders != null) {
            width -= Common.getLineWidth(borders.left);
            width -= Common.getLineWidth(borders.right);
         }

         height = hidden ? titleHeight : height + titleHeight;
         Dimension objSize = new Dimension(width, height);
         objectFormat.setSize(objSize);
         titleFormat.setSize(new Dimension(width, titleHeight));

         CurrentSelectionVSAssemblyInfo containerInfo =
            ((CurrentSelectionVSAssemblyInfo) container.getVSAssemblyInfo());
         titleRatio = containerInfo.getTitleRatio();
         titleRatio = Double.isNaN(titleRatio) ? 0.5 : titleRatio;
         supportRemoveChild = containerInfo.isAdhocEnabled();
      }

      // Need to calculate width without borders, otherwise the bar goes over border
      TableDataPath path = new TableDataPath(-1, TableDataPath.OBJECT);
      VSCompositeFormat format = assembly.getFormatInfo().getFormat(path);
      this.maxRangeBarWidth = (int) objectFormat.getWidth();
      Insets borders = format.getBorders();

      if(borders != null) {
         float leftBorder = Common.getLineWidth(borders.left);
         float rightBorder = Common.getLineWidth(borders.right);
         this.maxRangeBarWidth -= (leftBorder + rightBorder);
      }
   }

   private void renderSizeWithParentMaxMode(VSAssembly objContainer) {
      if(objContainer instanceof CurrentSelectionVSAssembly) {
         MaxModeSupportAssemblyInfo maxModeInfo =
            (CurrentSelectionVSAssemblyInfo) objContainer.getVSAssemblyInfo();
         Dimension containerMaxSize = maxModeInfo.getMaxSize();

         if(containerMaxSize != null) {
            final VSFormatModel objectFormat = getObjectFormat();
            double width = Math.max(objectFormat.getWidth(), containerMaxSize.getWidth());
            objectFormat.setWidth(width);
         }
      }
   }

   public boolean isMinVisible() {
      return minVisible;
   }

   public boolean isMaxVisible() {
      return maxVisible;
   }

   public boolean isTickVisible() {
      return tickVisible;
   }

   public boolean isCurrentVisible() {
      return currentVisible;
   }

   public String[] getLabels() {
      return labels;
   }

   public String[] getValues() {
      return values;
   }

   public int getSelectStart() {
      return selectStart;
   }

   public int getSelectEnd() {
      return selectEnd;
   }

   public boolean getHidden() {
      return hidden;
   }

   public boolean isUpperInclusive() {
      return upperInclusive;
   }

   public boolean isComposite() {
      return composite;
   }

   public String getDataType() {
      return dataType;
   }

   public boolean isSupportRemoveChild() {
      return supportRemoveChild;
   }

   public boolean isAdhocFilter() {
      return adhocFilter;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }

   public String getTitle() {
      return title;
   }

   public double getTitleRatio() {
      return titleRatio;
   }

   public VSFormatModel getTitleFormat() {
      return titleFormat;
   }

   public float getMaxRangeBarWidth() {
      return maxRangeBarWidth;
   }

   public boolean isMaxMode() {
      return maxMode;
   }

   @Override
   public String toString() {
      return "{" + super.toString() + " " +
         "minVisible: " + minVisible + " " +
         "maxVisible: " + maxVisible + " " +
         "labels: " + java.util.Arrays.toString(labels) + " " +
         "values: " + java.util.Arrays.toString(values) + " " +
         "selectStart:" + selectStart + " " +
         "selectEnd:" + selectEnd + " " +
         "hidden:" + hidden + " " +
         "upperInclusive:" + upperInclusive + " " +
         "composite:" + composite + " " +
         "dataType:" + dataType + " " +
         "supportRemoveChild:" + supportRemoveChild + " " +
         "adhocFilter:" + adhocFilter + " " +
         "submitOnChange:" + submitOnChange + " " +
         "titleRatio:" + titleRatio + " " +
         "title:" + title + " " +
         "maxRangeBarWidth:" + maxRangeBarWidth + "} ";
   }

   private boolean minVisible;
   private boolean maxVisible;
   private boolean tickVisible;
   private boolean currentVisible;
   private String[] labels;
   private String[] values;
   private int selectStart;
   private int selectEnd;
   private boolean hidden;
   private boolean upperInclusive;
   private boolean composite;
   private String dataType;
   private boolean supportRemoveChild;
   private boolean adhocFilter;
   private boolean submitOnChange;
   private String title;
   private double titleRatio = 1;
   private VSFormatModel titleFormat;
   private float maxRangeBarWidth;
   private boolean maxMode;

   @Component
   public static final class VSRangeSliderModelFactory
      extends VSObjectModelFactory<TimeSliderVSAssembly, VSRangeSliderModel>
   {
      public VSRangeSliderModelFactory() {
         super(TimeSliderVSAssembly.class);
      }

      @Override
      public VSRangeSliderModel createModel(TimeSliderVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSRangeSliderModel(assembly, rvs);
      }
   }
}
