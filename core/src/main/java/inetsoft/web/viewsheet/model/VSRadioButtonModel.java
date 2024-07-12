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

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
import org.springframework.stereotype.Component;

import java.awt.*;

public class VSRadioButtonModel extends ListInputModel<RadioButtonVSAssembly> {
   public VSRadioButtonModel(RadioButtonVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      RadioButtonVSAssemblyInfo assemblyInfo =
         (RadioButtonVSAssemblyInfo) assembly.getVSAssemblyInfo();
      FormatInfo fmtInfo = assemblyInfo.getFormatInfo();
      TableDataPath titlepath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat compositeTitleFormat = fmtInfo.getFormat(titlepath, false);
      TableDataPath datapath = new TableDataPath(-1, TableDataPath.DETAIL);
      VSCompositeFormat compositeDetailFormat = fmtInfo.getFormat(datapath, false);

      title = assemblyInfo.getTitle();
      titleVisible = assemblyInfo.isTitleVisible();
      selectedLabel = assemblyInfo.getSelectedLabel();
      selectedObject = assemblyInfo.getSelectedObject();
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
      dataColCount = dataRowCount >=  getValues().length ? 1 :
         (int) Math.ceil((double) getValues().length / dataRowCount);
      size = new Dimension(dataColCount == 0 ? 0 : containerSize.width / dataColCount,
                           cellHeight);
      detailFormat.setPositions(pos, size);
   }

   public String getTitle() {
      return title;
   }

   public boolean isTitleVisible() {
      return titleVisible;
   }

   public String getSelectedLabel() {
      return selectedLabel;
   }

   public Object getSelectedObject() {
      return selectedObject;
   }

   public VSFormatModel getTitleFormat() {
      return titleFormat;
   }

   public VSFormatModel getDetailFormat() {
      return detailFormat;
   }

   public int getDataRowCount() {
      return dataRowCount;
   }

   public int getDataColCount() {
      return dataColCount;
   }

   public int getCellHeight() {
      return cellHeight;
   }

   public void setCellHeight(int cellHeight) {
      this.cellHeight = cellHeight;
   }

   private VSFormatModel detailFormat;
   private VSFormatModel titleFormat;
   private String title;
   private boolean titleVisible;
   private String selectedLabel;
   private Object selectedObject;
   private int dataRowCount;
   private int dataColCount;
   private int cellHeight;

   @Component
   public static final class VSRadioButtonModelFactory
      extends VSObjectModelFactory<RadioButtonVSAssembly, VSRadioButtonModel>
   {
      public VSRadioButtonModelFactory() {
         super(RadioButtonVSAssembly.class);
      }

      @Override
      public VSRadioButtonModel createModel(RadioButtonVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSRadioButtonModel(assembly, rvs);
      }
   }
}
