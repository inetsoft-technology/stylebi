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
package inetsoft.web.vswizard.model;

import inetsoft.util.data.CommonKVModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;

import java.util.List;

public class WizardBindingModel {
   public WizardBindingModel() {
   }

   public WizardBindingModel(String sourceName, int assemblyType) {
      this.sourceName = sourceName;
      this.assemblyType = assemblyType;
   }

   public ChartBindingModel getChartBindingModel() {
      return chartBindingModel;
   }

   public void setChartBindingModel(ChartBindingModel chartBindingModel) {
      this.chartBindingModel = chartBindingModel;
   }

   public TableBindingModel getTableBindingModel() {
      return tableBindingModel;
   }

   public void setTableBindingModel(TableBindingModel model) {
      this.tableBindingModel = model;
   }

   public void setFilterBindingModel(FilterBindingModel filterBindingModel) {
       this.filterBindingModel = filterBindingModel;
   }

   public FilterBindingModel getFilterBindingModel() {
       return filterBindingModel;
   }

   public void setSourceName(String sourceName) {
      this.sourceName = sourceName;
   }

   public String getSourceName() {
      return sourceName;
   }

   public int getAssemblyType() {
      return assemblyType;
   }

   public void setAssemblyType(int assemblyType) {
      this.assemblyType = assemblyType;
   }

   public boolean isAutoOrder() {
      return autoOrder;
   }

   public void setAutoOrder(boolean autoOrder) {
      this.autoOrder = autoOrder;
   }

   public boolean isShowLegend() {
      return showLegend;
   }

   public void setShowLegend(boolean show) {
      this.showLegend = show;
   }

   public List<CommonKVModel<String, String>> getFixedFormulaMap() {
      return fixedFormulaMap;
   }

   public void setFixedFormulaMap(List<CommonKVModel<String, String>> fixedFormulaMap) {
      this.fixedFormulaMap = fixedFormulaMap;
   }

   private String sourceName;
   private int assemblyType;
   private boolean autoOrder;
   private boolean showLegend;
   private List<CommonKVModel<String, String>> fixedFormulaMap;
   private ChartBindingModel chartBindingModel;
   private TableBindingModel tableBindingModel;
   private FilterBindingModel filterBindingModel;
}
