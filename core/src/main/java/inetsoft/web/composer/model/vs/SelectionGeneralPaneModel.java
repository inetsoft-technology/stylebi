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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SelectionGeneralPaneModel {
   public int getShowType() {
      return showType;
   }

   public void setShowType(int showType) {
      this.showType = showType;
   }

   public int getListHeight() {
      return listHeight;
   }

   public void setListHeight(int listHeight) {
      this.listHeight = listHeight;
   }

   public int getSortType() {
      return sortType;
   }

   public void setSortType(int sortType) {
      this.sortType = sortType;
   }

   public boolean isSingleSelection() {
      return singleSelection;
   }

   public void setSingleSelection(boolean singleSelection) {
      this.singleSelection = singleSelection;
   }

   public List<String> getSingleSelectionLevels() {
      return singleSelectionLevels;
   }

   public void setSingleSelectionLevels(List<String> singleSelectionLevels) {
      this.singleSelectionLevels = singleSelectionLevels;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }

   public void setSubmitOnChange(boolean submitOnChange) {
      this.submitOnChange = submitOnChange;
   }

   public boolean isSuppressBlank() {
      return suppressBlank;
   }

   public void setSuppressBlank(boolean suppressBlank) {
      this.suppressBlank = suppressBlank;
   }

   public boolean isShowNonContainerProps() {
      return showNonContainerProps;
   }

   public void setShowNonContainerProps(boolean showNonContainerProps) {
      this.showNonContainerProps = showNonContainerProps;
   }

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

   public SizePositionPaneModel getSizePositionPaneModel() {
      if(sizePositionPaneModel == null) {
         sizePositionPaneModel = new SizePositionPaneModel();
      }

      return sizePositionPaneModel;
   }

   public boolean isInSelectionContainer() {
      return inSelectionContainer;
   }

   public void setInSelectionContainer(boolean inSelectionContainer) {
      this.inSelectionContainer = inSelectionContainer;
   }

   public boolean isSelectFirstItem() {
      return selectFirstItem;
   }

   public void setSelectFirstItem(boolean selectFirstItem) {
      this.selectFirstItem = selectFirstItem;
   }

   private int showType;
   private int listHeight;
   private int sortType;
   private boolean submitOnChange;
   private boolean singleSelection;
   private List<String> singleSelectionLevels;
   private boolean suppressBlank;
   private boolean showNonContainerProps = true;
   private GeneralPropPaneModel generalPropPaneModel;
   private TitlePropPaneModel titlePropPaneModel;
   private SizePositionPaneModel sizePositionPaneModel;
   private boolean inSelectionContainer;
   private boolean selectFirstItem;
}
