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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TableViewGeneralPaneModel {


   public GeneralPropPaneModel getGeneralPropPaneModel() {
      if(generalPropPaneModel == null) {
         generalPropPaneModel = new GeneralPropPaneModel();
      }

      return generalPropPaneModel;
   }

   public void setGeneralPropPaneModel(GeneralPropPaneModel generalPropPaneModel) {
      this.generalPropPaneModel = generalPropPaneModel;
   }

   public TitlePropPaneModel getTitlePropPaneModel() {
      if(titlePropPaneModel == null) {
         titlePropPaneModel = new TitlePropPaneModel();
      }

      return titlePropPaneModel;
   }

   public void setTitlePropPaneModel(TitlePropPaneModel titlePropPaneModel) {
      this.titlePropPaneModel = titlePropPaneModel;
   }

   public TableStylePaneModel getTableStylePaneModel() {
      if(tableStylePaneModel == null) {
         tableStylePaneModel = new TableStylePaneModel();
      }

      return tableStylePaneModel;
   }

   public void setTableStylePaneModel(TableStylePaneModel tableStylePaneModel) {
      this.tableStylePaneModel = tableStylePaneModel;
   }

   public SizePositionPaneModel getSizePositionPaneModel() {
      if(sizePositionPaneModel == null) {
         sizePositionPaneModel = new SizePositionPaneModel();
      }

      return sizePositionPaneModel;
   }

   public void setSizePositionPaneModel(SizePositionPaneModel sizePositionPaneModel) {
      this.sizePositionPaneModel = sizePositionPaneModel;
   }

   public int getMaxRows() {
      return maxRows;
   }

   public void setMaxRows(int maxRows) {
      this.maxRows = maxRows;
   }

   public boolean isShowMaxRows() {
      return showMaxRows;
   }

   public void setShowMaxRows(boolean showMaxRows) {
      this.showMaxRows = showMaxRows;
   }

   public boolean isShowSubmitOnChange() {
      return showSubmitOnChange;
   }

   public void setShowSubmitOnChange(boolean showSubmitOnChange) {
      this.showSubmitOnChange = showSubmitOnChange;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }

   public void setSubmitOnChange(boolean submitOnChange) {
      this.submitOnChange = submitOnChange;
   }

   private boolean showMaxRows;
   private int maxRows;
   private boolean showSubmitOnChange;
   private boolean submitOnChange;
   private GeneralPropPaneModel generalPropPaneModel;
   private TitlePropPaneModel titlePropPaneModel;
   private TableStylePaneModel tableStylePaneModel;
   private SizePositionPaneModel sizePositionPaneModel;
}