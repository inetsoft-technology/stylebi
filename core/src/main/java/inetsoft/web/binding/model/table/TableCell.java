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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.info.TableLayoutCellInfo;
import inetsoft.web.adhoc.model.ReportFormatModel;
import inetsoft.web.viewsheet.model.VSFormatModel;

import java.awt.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TableCell {
   /**
    * Constructor.
    */
   public TableCell() {
   }

   /**
    * Constructor.
    */
   public TableCell(TableLayoutCellInfo cell, int row, int col, int region) {
      this.row = row;
      this.col = col;
      this.region = region;

      if(cell == null) {
         setMerged(true);
         return;
      }

      setText(cell.getText() == null ? "" : cell.getText());

      if(cell.getSpan() != null) {
         setSpan(cell.getSpan());
      }
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
    * Get region.
    * @return the region.
    */
   public int getRegion() {
      return region;
   }

   /**
    * Set region.
    * @param region the region.
    */
   public void setRegion(int region) {
      this.region = region;
   }

   /**
    * Get reportFormat.
    * @return the reportFormat.
    */
   public ReportFormatModel getFormat() {
      return format;
   }

   /**
    * Set reportFormat.
    */
   public void setFormat(ReportFormatModel format) {
      this.format = format;
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
    * Get is merged or not.
    * @return true for merged cells.
    */
   public boolean isMerged() {
      return merged;
   }

   /**
    * Set merged.
    * @param merged the merged.
    */
   public void setMerged(boolean merged) {
      this.merged = merged;
   }

   private String text;
   private ReportFormatModel format;
   private VSFormatModel vsFormat;
   private int row;
   private int col;
   private int region;
   private int bindingType;
   private Dimension span = null;
   private boolean merged = false;
}
