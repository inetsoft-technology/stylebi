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
package inetsoft.web.composer.ws.assembly;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.AbstractTableAssembly;
import inetsoft.web.binding.model.AggregateInfoModel;
import inetsoft.web.composer.ws.assembly.tableinfo.TableAssemblyInfoModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that represents a runtime table.
 */
public class TableAssemblyModel extends WSAssemblyModel {
   public TableAssemblyModel(AbstractTableAssembly assembly, RuntimeWorksheet rws) {
      super(assembly, rws);

      setCrosstab(assembly.isCrosstab());
      setTableClassType(assembly.getClass().getSimpleName());
   }

   @Override
   public String getClassType() {
      return "TableAssembly";
   }

   public int getTotalRows() {
      return totalRows;
   }

   public void setTotalRows(int totalRows) {
      this.totalRows = totalRows;
   }

   public boolean getRowsCompleted() {
      return rowsCompleted;
   }

   public void setRowsCompleted(boolean moreRows) {
      this.rowsCompleted = moreRows;
   }

   public boolean isHasMaxRow() {
      return hasMaxRow;
   }

   public void setHasMaxRow(boolean hasMaxRow) {
      this.hasMaxRow = hasMaxRow;
   }

   public boolean isCrosstab() {
      return crosstab;
   }

   public void setCrosstab(boolean crosstab) {
      this.crosstab = crosstab;
   }

   public String getTableClassType() {
      return tableClassType;
   }

   public void setTableClassType(String tableClassType) {
      this.tableClassType = tableClassType;
   }

   public TableAssemblyInfoModel getInfo() {
      return info;
   }

   public void setInfo(TableAssemblyInfoModel info) {
      this.info = info;
   }

   public List<ColumnInfoModel> getColInfos() {
      if(colInfos == null) {
         colInfos = new ArrayList<>();
      }

      return colInfos;
   }

   public void setColInfos(List<ColumnInfoModel> colInfos) {
      this.colInfos = colInfos;
   }

   public String getExceededMaximum() {
      return exceededMaximum;
   }

   public void setExceededMaximum(String exceededMaximum) {
      this.exceededMaximum = exceededMaximum;
   }

   public boolean isColumnTypeEnabled() {
      return columnTypeEnabled;
   }

   public void setColumnTypeEnabled(boolean columnTypeEnabled) {
      this.columnTypeEnabled = columnTypeEnabled;
   }

   public AggregateInfoModel getAggregateInfo() {
      return aggregateInfo;
   }

   public void setAggregateInfo(AggregateInfoModel aggregateInfo) {
      this.aggregateInfo = aggregateInfo;
   }

   private int totalRows;
   private boolean rowsCompleted;
   private boolean columnTypeEnabled;
   private boolean hasMaxRow;
   private boolean crosstab;
   private String tableClassType;
   private TableAssemblyInfoModel info;
   private List<ColumnInfoModel> colInfos;
   private String exceededMaximum;
   private AggregateInfoModel aggregateInfo;
}
