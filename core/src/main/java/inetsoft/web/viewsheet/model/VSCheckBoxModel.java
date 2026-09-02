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

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import org.springframework.stereotype.Component;

import java.awt.*;

public class VSCheckBoxModel extends ListInputModel<CheckBoxVSAssembly> {
   public VSCheckBoxModel(CheckBoxVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      CheckBoxVSAssemblyInfo assemblyInfo =
         (CheckBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();
      FormatInfo fmtInfo = assemblyInfo.getFormatInfo();
      TableDataPath titlepath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat compositeTitleFormat = fmtInfo.getFormat(titlepath, false);
      TableDataPath datapath = new TableDataPath(-1, TableDataPath.DETAIL);
      VSCompositeFormat compositeDetailFormat = fmtInfo.getFormat(datapath, false);

      title = assemblyInfo.getTitle();
      titleVisible = assemblyInfo.isTitleVisible();
      selectedLabels = assemblyInfo.getSelectedLabels();
      selectedObjects = assemblyInfo.getSelectedObjects();
      titleFormat = new VSFormatModel(compositeTitleFormat, assemblyInfo);
      detailFormat = new VSFormatModel(compositeDetailFormat, assemblyInfo);

      Viewsheet vs = assemblyInfo.getViewsheet();
      Dimension containerSize = assemblyInfo.getLayoutSize() != null ?
         assemblyInfo.getLayoutSize() : vs.getPixelSize(assemblyInfo);

      int titleHeight = assemblyInfo.getTitleHeight();
      Dimension size = new Dimension(containerSize.width, titleHeight);
      Point pos = new Point(0,0);
      int cellHeight = assemblyInfo.getCellHeight();
      titleFormat.setPositions(pos, size);

      int contentHeight = containerSize.height;

      if(titleVisible) {
         contentHeight = containerSize.height - titleHeight;
      }

      dataRowCount = (int) Math.floor(contentHeight / cellHeight);
      dataRowCount = contentHeight != 0 ? Math.max(1, dataRowCount) : dataRowCount;
      dataColCount = dataRowCount >= getValues().length ? 1 :
         (int) Math.ceil((double) getValues().length / dataRowCount);
      size = new Dimension(dataColCount == 0 ? 0 : containerSize.width / dataColCount,
                           cellHeight);
      detailFormat.setPositions(pos, size);
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public boolean isTitleVisible() {
      return titleVisible;
   }

   public void setTitleVisible(boolean titleVisible) {
      this.titleVisible = titleVisible;
   }

   public String[] getSelectedLabels() {
      return selectedLabels;
   }

   public void setSelectedLabels(String[] selectedLabels) {
      this.selectedLabels = selectedLabels;
   }

   public Object[] getSelectedObjects() {
      return selectedObjects;
   }

   public void setSelectedObjects(Object[] selectedObjects) {
      this.selectedObjects = selectedObjects;
   }

   public VSFormatModel getTitleFormat() {
      return titleFormat;
   }

   public void setTitleFormat(VSFormatModel titleFormat) {
      this.titleFormat = titleFormat;
   }

   public VSFormatModel getDetailFormat() {
      return detailFormat;
   }

   public void setDetailFormat(VSFormatModel detailFormat) {
      this.detailFormat = detailFormat;
   }

   public int getDataRowCount() {
      return dataRowCount;
   }

   public void setDataRowCount(int dataRowCount) {
      this.dataRowCount = dataRowCount;
   }

   public int getDataColCount() {
      return dataColCount;
   }

   public void setDataColCount(int dataColCount) {
      this.dataColCount = dataColCount;
   }

   private VSFormatModel detailFormat;
   private VSFormatModel titleFormat;
   private String title;
   private boolean titleVisible;
   private String[] selectedLabels;
   private Object[] selectedObjects;
   private int dataRowCount;
   private int dataColCount;

   @Component
   public static final class VSCheckBoxModelFactory
      extends VSObjectModelFactory<CheckBoxVSAssembly, VSCheckBoxModel>
   {
      public VSCheckBoxModelFactory() {
         super(CheckBoxVSAssembly.class);
      }

      @Override
      public VSCheckBoxModel createModel(CheckBoxVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSCheckBoxModel(assembly, rvs);
      }
   }
}
