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
package inetsoft.web.binding.model.table;

import inetsoft.report.TableDataPath;
import inetsoft.web.viewsheet.model.VSFormatModel;

import java.awt.*;

public class CalcTableCell {
   /**
    * Constructor.
    */
   public CalcTableCell() {
   }

   /**
    * Get text.
    * @return the text.
    */
   public String getText() {
      return text;
   }

   /**
    * Set text.
    * @param text the text.
    */
   public void setText(String text) {
      this.text = text;
   }

   /**
    * Set row.
    * @param r the row.
    */
   public void setRow(int r) {
      this.row = r;
   }

   /**
    * Get row.
    * @return the row.
    */
   public int getRow() {
      return row;
   }

   /**
    * Get col.
    * @return the col.
    */
   public int getCol() {
      return col;
   }

   /**
    * Set the binding cell type for the cell, group, summary or detail.
    * @param the bindingtype.
    */
   public void setBindingType(int bindingType) {
      this.bindingType = bindingType;
   }

   /**
    * Get the binding cell type for the cell, group, summary or detail.
    * @return the bindingType.
    */
   public int getBindingType() {
      return this.bindingType;
   }

   /**
    * Set col.
    * @param c the col.
    */
   public void setCol(int c) {
      this.col = c;
   }

   /**
    * Get vs format.
    * @return the vs format.
    */
   public VSFormatModel getVsFormat() {
      return vsFormat;
   }

   /**
    * Set format.
    * @param format the format.
    */
   public void setVsFormat(VSFormatModel vsformat) {
      this.vsFormat = vsformat;
   }

   /**
    * Get span.
    * @return the span.
    */
   public Dimension getSpan() {
      return span;
   }

   /**
    * Set span.
    * @param span the span.
    */
   public void setSpan(Dimension span) {
      this.span = span;
   }

   /**
    * Get base cell info.
    * @return base cell info.
    */
   public Rectangle getBaseInfo() {
      return baseInfo;
   }

   /**
    * Set base cell info.
    * @param info the base cell info.
    */
   public void setBaseInfo(Rectangle info) {
      baseInfo = info;
   }

   /**
    * Get base cell info.
    * @return base cell info.
    */
   public TableDataPath getCellPath() {
      return tpath;
   }

   /**
    * Set base cell info.
    * @param info the base cell info.
    */
   public void setCellPath(TableDataPath path) {
      tpath = path;
   }

   private String text;
   private VSFormatModel vsFormat;
   private int row;
   private int col;
   private int bindingType;
   private Dimension span = null;
   private Rectangle baseInfo = null;
   private TableDataPath tpath = null;
}
