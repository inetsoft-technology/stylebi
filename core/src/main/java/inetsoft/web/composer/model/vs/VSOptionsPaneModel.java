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
package inetsoft.web.composer.model.vs;


import java.io.Serializable;

public class VSOptionsPaneModel implements Serializable {
   public ViewsheetParametersDialogModel getViewsheetParametersDialogModel() {
      if(viewsheetParametersDialogModel == null) {
         viewsheetParametersDialogModel = new ViewsheetParametersDialogModel();
      }
      return viewsheetParametersDialogModel;
   }

   public void setViewsheetParametersDialogModel(ViewsheetParametersDialogModel viewsheetParametersDialogModel) {
      this.viewsheetParametersDialogModel = viewsheetParametersDialogModel;
   }

   public SelectDataSourceDialogModel getSelectDataSourceDialogModel() {
      if(selectDataSourceDialogModel == null) {
         selectDataSourceDialogModel = new SelectDataSourceDialogModel();
      }
      return selectDataSourceDialogModel;
   }

   public void setSelectDataSourceDialogModel(
      SelectDataSourceDialogModel selectDataSourceDialogModel)
   {
      this.selectDataSourceDialogModel = selectDataSourceDialogModel;
   }

   public int getMaxRows() {
      return maxRows;
   }

   public void setMaxRows(int maxRows) {
      this.maxRows = maxRows;
   }

   public int getSnapGrid() {
      return snapGrid;
   }

   public void setSnapGrid(int snapGrid) {
      this.snapGrid = snapGrid;
   }

   public String getAlias() {
      return alias;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   public String getDesc() {
      return desc;
   }

   public void setDesc(String desc) {
      this.desc = desc;
   }

   public boolean isUseMetaData() {
      return useMetaData;
   }

   public void setUseMetaData(boolean useMetaData) {
      this.useMetaData = useMetaData;
   }

   public boolean isPromptForParams() {
      return promptForParams;
   }

   public void setPromptForParams(boolean promptForParams) {
      this.promptForParams = promptForParams;
   }

   public boolean isSelectionAssociation() {
      return selectionAssociation;
   }

   public void setSelectionAssociation(boolean selectionAssociation) {
      this.selectionAssociation = selectionAssociation;
   }

   public boolean isCreateMv() {
      return createMv;
   }

   public void setCreateMv(boolean createMv) {
      this.createMv = createMv;
   }

   public boolean isOnDemandMvEnabled() {
      return onDemandMvEnabled;
   }

   public void setOnDemandMvEnabled(boolean onDemandMvEnabled) {
      this.onDemandMvEnabled = onDemandMvEnabled;
   }

   public boolean isServerSideUpdate() {
      return serverSideUpdate;
   }

   public void setServerSideUpdate(boolean serverSideUpdate) {
      this.serverSideUpdate = serverSideUpdate;
   }

   public boolean isListOnPortalTree() {
      return listOnPortalTree;
   }

   public void setListOnPortalTree(boolean listOnPortalTree) {
      this.listOnPortalTree = listOnPortalTree;
   }

   public int getTouchInterval() {
      return touchInterval;
   }

   public void setTouchInterval(int touchInterval) {
      this.touchInterval = touchInterval;
   }

   public boolean isWorksheet() {
      return worksheet;
   }

   public void setWorksheet(boolean worksheet) {
      this.worksheet = worksheet;
   }

   public boolean isAutoRefreshEnabled() {
      return autoRefreshEnabled;
   }

   public void setAutoRefreshEnabled(boolean autoRefreshEnabled) {
      this.autoRefreshEnabled = autoRefreshEnabled;
   }

   public boolean isMaxRowsWarning() {
      return maxRowsWarning;
   }

   public void setMaxRowsWarning(boolean maxRowsWarning) {
      this.maxRowsWarning = maxRowsWarning;
   }

   private ViewsheetParametersDialogModel viewsheetParametersDialogModel;
   private SelectDataSourceDialogModel selectDataSourceDialogModel;
   private String alias;
   private String desc;
   private boolean useMetaData;
   private boolean promptForParams;
   private boolean selectionAssociation;
   private boolean createMv;
   private boolean onDemandMvEnabled;
   private boolean serverSideUpdate;
   private boolean listOnPortalTree;
   private boolean worksheet;
   private int touchInterval;
   private int maxRows;
   private int snapGrid = 20;
   private boolean autoRefreshEnabled;
   private boolean maxRowsWarning;
}
