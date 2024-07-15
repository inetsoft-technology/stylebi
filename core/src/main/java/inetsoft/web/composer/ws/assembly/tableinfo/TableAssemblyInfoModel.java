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
package inetsoft.web.composer.ws.assembly.tableinfo;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.composer.model.ws.WSTableStatusIndicatorTooltipContainer;
import inetsoft.web.composer.ws.assembly.WSAssemblyInfoModel;

/**
 * Wrapper class for TableAssemblyInfo
 */
public class TableAssemblyInfoModel extends WSAssemblyInfoModel {
   public TableAssemblyInfoModel(TableAssemblyInfo info) {
      super(info);
      setHasAggregate(info.isAggregateDefined());
      setDistinct(info.isDistinct());
      setAggregate(info.isAggregate());
      setHasCondition(info.isConditionDefined());
      setHasExpression(info.isExpressionDefined());
      setHasSort(info.isSortDefined());
      setLive(info.isLiveData());
      setSqlMergeable(info.isSQLMergeable());
      setRuntime(info.isRuntime());
      setEditMode(info.isEditMode());
      setVisibleTable(info.isVisibleTable());
      setRuntimeSelected(info.isRuntimeSelected());
      setPrivateSelection(convertColumnSelection(info.getPrivateColumnSelection()));
      setPublicSelection(convertColumnSelection(info.getPublicColumnSelection()));
   }

   private ColumnRefModel[] convertColumnSelection(ColumnSelection columns) {
      final ColumnRefModel[] columnRefModels = new ColumnRefModel[columns.getAttributeCount()];

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         columnRefModels[i] = new ColumnRefModel((ColumnRef) columns.getAttribute(i));
      }

      return columnRefModels;
   }

   public boolean getDistinct() {
      return distinct;
   }

   public void setDistinct(boolean distinct) {
      this.distinct = distinct;
   }

   public boolean getLive() {
      return live;
   }

   public void setLive(boolean live) {
      this.live = live;
   }

   public boolean getRuntime() {
      return runtime;
   }

   public void setRuntime(boolean runtime) {
      this.runtime = runtime;
   }

   public boolean getEditMode() {
      return editMode;
   }

   public void setEditMode(boolean editMode) {
      this.editMode = editMode;
   }

   public boolean getAggregate() {
      return aggregate;
   }

   public void setAggregate(boolean aggregate) {
      this.aggregate = aggregate;
   }

   public boolean getHasAggregate() {
      return hasAggregate;
   }

   public void setHasAggregate(boolean hasAggregate) {
      this.hasAggregate = hasAggregate;
   }

   public boolean getHasCondition() {
      return hasCondition;
   }

   public void setHasCondition(boolean hasCondition) {
      this.hasCondition = hasCondition;
   }

   public boolean getHasExpression() {
      return hasExpression;
   }

   public void setHasExpression(boolean hasExpression) {
      this.hasExpression = hasExpression;
   }

   public boolean getHasSort() {
      return hasSort;
   }

   public void setHasSort(boolean hasSort) {
      this.hasSort = hasSort;
   }

   public boolean getSqlMergeable() {
      return sqlMergeable;
   }

   public void setSqlMergeable(boolean sqlMergeable) {
      this.sqlMergeable = sqlMergeable;
   }

   public boolean getVisibleTable() {
      return visibleTable;
   }

   public void setVisibleTable(boolean visibleTable) {
      this.visibleTable = visibleTable;
   }

   public boolean getRuntimeSelected() {
      return runtimeSelected;
   }

   public void setRuntimeSelected(boolean runtimeSelected) {
      this.runtimeSelected = runtimeSelected;
   }

   public WSTableStatusIndicatorTooltipContainer getTooltipContainer() {
      return tooltipContainer;
   }

   public void setTooltipContainer(
      WSTableStatusIndicatorTooltipContainer tooltipContainer)
   {
      this.tooltipContainer = tooltipContainer;
   }

   public ColumnRefModel[] getPrivateSelection() {
      return privateSelection;
   }

   public void setPrivateSelection(ColumnRefModel[] privateSelection) {
      this.privateSelection = privateSelection;
   }

   public ColumnRefModel[] getPublicSelection() {
      return publicSelection;
   }

   public void setPublicSelection(ColumnRefModel[] publicSelection) {
      this.publicSelection = publicSelection;
   }

   private boolean distinct;
   private boolean live;
   private boolean runtime;
   private boolean editMode;
   private boolean aggregate;
   private boolean hasAggregate;
   private boolean hasCondition;
   private boolean hasExpression;
   private boolean hasSort;
   private boolean sqlMergeable;
   private boolean visibleTable;
   private boolean runtimeSelected;
   private WSTableStatusIndicatorTooltipContainer tooltipContainer;
   private ColumnRefModel[] privateSelection;
   private ColumnRefModel[] publicSelection;
}
