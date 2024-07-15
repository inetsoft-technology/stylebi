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
package inetsoft.web.binding.model.table;

import inetsoft.report.TableCellBinding;

public class CellBindingInfo {
   /**
    * Create a default CellBindingInfo.
    */
   public CellBindingInfo() {
   }

   /**
    * Create a CellBindingInfo according to table cell binding.
    */
   public CellBindingInfo(TableCellBinding bind) {
      this.type = bind.getType();
      this.btype = bind.getBType();
      this.name = bind.getName();
      this.expansion = bind.getExpansion();
      this.mergeCells = bind.isMergeCells();
      this.mergeRowGroup = bind.getMergeRowGroup();
      this.mergeColGroup = bind.getMergeColGroup();
      this.rowGroup = bind.getRowGroup();
      this.colGroup = bind.getColGroup();
      this.value = bind.getValue();
      this.formula = bind.getFormula();
      this.timeSeries = bind.isTimeSeries();
      this.order = new OrderModel(bind.getOrderInfo(true));
      this.topn = new TopNModel(bind.getTopN(true));
   }

   /**
    * Set cell type(text/binding/formula).
    * @param type the cell type.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get cell type.
    * @return the cell type.
    */
   public int getType() {
      return type;
   }

   /**
    * Set binding type(detail/group/summary).
    * @param type the binding type.
    */
   public void setBtype(int type) {
      this.btype = type;
   }

   /**
    * Get binding type.
    * @return the binding type.
    */
   public int getBtype() {
      return btype;
   }

   /**
    * Set cell name.
    * @param name the cell name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get cell name.
    * @return cell name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the runtime (generated) cell name.
    */
   public void setRuntimeName(String name) {
      this.runtimeName = name;
   }

   /**
    * Get the runtime (generated) cell name.
    */
   public String getRuntimeName() {
      return runtimeName;
   }

   /**
    * Set expansion type.
    * @param expansion the expansion type.
    */
   public void setExpansion(int expansion) {
      this.expansion = expansion;
   }

   /**
    * Get expansion.
    * @return the expansion.
    */
   public int getExpansion() {
      return expansion;
   }

   /**
    * Set merge cells or not.
    * @param mergeCells is merged cells.
    */
   public void setMergeCells(boolean mergeCells) {
      this.mergeCells = mergeCells;
   }

   /**
    * Get is merge cells.
    * @return true if is merge cells.
    */
   public boolean getMergeCells() {
      return mergeCells;
   }

   /**
    * Set merge row group value.
    * @param mergeRowGroup the merge row group value.
    */
   public void setMergeRowGroup(String mergeRowGroup) {
      this.mergeRowGroup = mergeRowGroup;
   }

   /**
    * Get merge row group value.
    * @return merge row group value.
    */
   public String getMergeRowGroup() {
      return mergeRowGroup;
   }

   /**
    * Set merge col group value.
    * @param mergeColGroup the merge col group value.
    */
   public void setMergeColGroup(String mergeColGroup) {
      this.mergeColGroup = mergeColGroup;
   }

   /**
    * Get merge col group value.
    * @return merge col group value.
    */
   public String getMergeColGroup() {
      return mergeColGroup;
   }

   /**
    * Set row group value.
    * @param rowGroup the row group value.
    */
   public void setRowGroup(String rowGroup) {
      this.rowGroup = rowGroup;
   }

   /**
    * Get row group value.
    * @return row group value.
    */
   public String getRowGroup() {
      return rowGroup;
   }

   /**
    * Set col group value.
    * @param colGroup the col group value.
    */
   public void setColGroup(String colGroup) {
      this.colGroup = colGroup;
   }

   /**
    * Get col group value.
    * @return col group value.
    */
   public String getColGroup() {
      return colGroup;
   }

   /**
    * Set cell value.
    * @param value the cell value.
    */
   public void setValue(String value) {
      this.value = value;
   }

   /**
    * Get value.
    * @return  value.
    */
   public String getValue() {
      return value;
   }

   /**
    * Set cell formula.
    * @param formula the cell formula.
    */
   public void setFormula(String formula) {
      this.formula = formula;
   }

   /**
    * Get cell formula.
    * @return cell formula.
    */
   public String getFormula() {
      return formula;
   }

   public boolean isTimeSeries() {
      return timeSeries;
   }

   public void setTimeSeries(boolean timeSeries) {
      this.timeSeries = timeSeries;
   }

   /**
    * Set order model.
    * @param order the ordermodel.
    */
   public void setOrder(OrderModel order) {
      this.order = order;
   }

   /**
    * Get order model.
    * @return order model.
    */
   public OrderModel getOrder() {
      return order;
   }

   /**
    * Set topn model.
    * @param topn the topn model.
    */
   public void setTopn(TopNModel topn) {
      this.topn = topn;
   }

   /**
    * Get topn model.
    * @return topn model.
    */
   public TopNModel getTopn() {
      return topn;
   }

   private int type;
   private int btype;
   private String name;
   private String runtimeName;
   private int expansion;
   private boolean mergeCells;
   private String mergeRowGroup;
   private String mergeColGroup;
   private String rowGroup;
   private String colGroup;
   private String value;
   private String formula;
   private boolean timeSeries;
   private OrderModel order = new OrderModel();
   private TopNModel topn = new TopNModel();
}
