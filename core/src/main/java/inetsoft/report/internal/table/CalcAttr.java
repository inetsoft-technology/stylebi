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
package inetsoft.report.internal.table;

import inetsoft.report.internal.binding.OrderInfo;
import inetsoft.report.internal.binding.TopNInfo;
import inetsoft.report.lens.CalcTableLens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CalcTableLens cell attribute structure.
 */
public class CalcAttr extends CalcCellAttr {
   /**
    * Create an cell attr for the specified cell.
    */
   public CalcAttr(int row, int col) {
      super(row, col);
   }

   /**
    * Create an cell attr for the specified cell. Copy the attributes from the
    * table.
    */
   public CalcAttr(CalcTableLens table, int row, int col) {
      this(row, col);

      name = table.getCellName(row, col);
      mergeCells = table.isMergeCells(row, col);
      mergeRowGroup = table.getMergeRowGroup(row, col);
      mergeColGroup = table.getMergeColGroup(row, col);
      rowGroup = table.getRowGroup(row, col);
      colGroup = table.getColGroup(row, col);
      expansion = table.getExpansion(row, col);
   }

   /**
    * Set the attributes in the table from the attributes in this object.
    */
   public void set(CalcTableLens table, int row, int col) {
      table.setCellName(row, col, name);
      table.setMergeCells(row, col, mergeCells);
      table.setMergeRowGroup(row, col, mergeRowGroup);
      table.setMergeColGroup(row, col, mergeColGroup);
      table.setRowGroup(row, col, rowGroup);
      table.setColGroup(row, col, colGroup);
      table.setExpansion(row, col, expansion);
   }

   /**
    * Clear the attributes in the table.
    */
   public static void clear(CalcTableLens table, int row, int col) {
      table.setCellName(row, col, null);
      table.setMergeCells(row, col, false);
      table.setMergeRowGroup(row, col, null);
      table.setMergeColGroup(row, col, null);
      table.setRowGroup(row, col, null);
      table.setColGroup(row, col, null);
      table.setExpansion(row, col, CalcTableLens.EXPAND_NONE);
   }

   /**
    * Get a unique cell identifier. If cell name is defined, it's used as the
    * id. Otherwise, the row/column indexes are used to create an unique id.
    */
   public String getCellID() {
      return (name != null) ? name : getCellID(row, col);
   }

   /**
    * Get the cell identifier.
    */
   public static String getCellID(int row, int col) {
      return row + "," + col;
   }

   /**
    * Get the cell name.
    */
   public String getCellName() {
      return name;
   }

   /**
    * Set the cell name.
    */
   public void setCellName(String name) {
      this.name = name;
   }

   /**
    * Check if expanded cells should be merged.
    */
   public boolean isMergeCells() {
      return mergeCells;
   }

   /**
    * Set wheter expanded cells should be merged.
    */
   public void setMergeCells(boolean merge) {
      this.mergeCells = merge;
   }

   /**
    * Get the name of the row group cell for merging expanded cells.
    * If it's set, only the cells within the same group are merged.
    */
   public String getMergeRowGroup() {
      return mergeRowGroup;
   }

   /**
    * Set the row group for merging the cells.
    */
   public void setMergeRowGroup(String group) {
      this.mergeRowGroup = group;
   }

   /**
    * Get the name of the column group cell for merging expanded cells.
    * If it's set, only the cells within the same group are merged.
    */
   public String getMergeColGroup() {
      return mergeColGroup;
   }

   /**
    * Set the column group for merging the cells.
    */
   public void setMergeColGroup(String group) {
      this.mergeColGroup = group;
   }

   /**
    * Get the row group of this cell.
    */
   public String getRowGroup() {
      return rowGroup;
   }

   /**
    * Set the row group of this cell. Setting the row group of a cell makes
    * it a nested group/cell of the parent group. The parent group is
    * expanded first.
    */
   public void setRowGroup(String group) {
      this.rowGroup = group;
   }

   /**
    * Get the column group of this cell.
    */
   public String getColGroup() {
      return colGroup;
   }

   /**
    * Set the column group of this cell. Setting the column group of a
    * cell makes it a nested group/cell of the parent group.
    * The parent group is expanded first.
    */
   public void setColGroup(String group) {
      this.colGroup = group;
   }

   /**
    * Get the cell expansion type.
    */
   public int getExpansion() {
      return expansion;
   }

   /**
    * Set the cell expansion type. Use one of the expansion constants:
    * EXPAND_NONE, EXPAND_HORIZONTAL, EXPAND_VERTICAL.
    */
   public void setExpansion(int expansion) {
      this.expansion = expansion;
   }

   /**
    * Check if a page break should be inserted after this group.
    */
   public boolean isPageAfter() {
      return pageAfter;
   }

   /**
    * Set the page after flag. This should only be set on a vertically
    * expanding cell.
    */
   public void setPageAfter(boolean pageAfter) {
      this.pageAfter = pageAfter;
   }

   /**
    * Get the OrderInfo
    */
   public OrderInfo getOrderInfo() {
      return orderInfo;
   }

   /**
    * Sets the OrderInfo
    */
   public void setOrderInfo(OrderInfo orderInfo) {
      this.orderInfo = orderInfo;
   }

   /**
    * Get the TopNInfo
    */
   public TopNInfo getTopN() {
      return topN;
   }

   /**
    * Set the TopNInfo
    */
   public void setTopN(TopNInfo topN) {
      this.topN = topN;
   }

   /**
    * Gets the flag that determines if the cell is bound to data.
    *
    * @return <tt>true</tt> if bound; <tt>false</tt> otherwise.
    */
   public final boolean isBound() {
      return bound;
   }

   /**
    * Sets the flag that determines if the cell is bound to data.
    *
    * @param bound <tt>true</tt> if bound; <tt>false</tt> otherwise.
    */
   public final void setBound(boolean bound) {
      this.bound = bound;
   }

   public String toString() {
      return "CalcAttr[" + name + "@" + row + "," + col + "]";
   }

   @Override
   public Object clone() {
      try {
         CalcAttr attr = (CalcAttr) super.clone();
         attr.name = name;
         attr.mergeCells = mergeCells;
         attr.mergeRowGroup = mergeRowGroup;
         attr.mergeColGroup = mergeColGroup;
         attr.rowGroup = rowGroup;
         attr.colGroup = colGroup;
         attr.expansion = expansion;
         attr.pageAfter = pageAfter;
         attr.bound = bound;

         if(orderInfo != null) {
            attr.orderInfo = (OrderInfo) orderInfo.clone();
         }

         if(topN != null) {
            attr.topN = (TopNInfo) topN.clone();
         }

         return attr;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   private String name;
   private boolean mergeCells;
   private String mergeRowGroup;
   private String mergeColGroup;
   private String rowGroup;
   private String colGroup;
   private int expansion;
   private boolean pageAfter;
   private OrderInfo orderInfo;
   private TopNInfo topN;
   private boolean bound = false;
   private static final Logger LOG = LoggerFactory.getLogger(CalcAttr.class);
}
