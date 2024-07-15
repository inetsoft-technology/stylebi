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
package inetsoft.report.internal.info;

import inetsoft.report.*;
import inetsoft.report.internal.binding.*;
import inetsoft.report.style.TableStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Table is the most common element on a report. It can be used to
 * present a dataset in a tabular grid. Tables can span across pages,
 * and can be wrapped horizontally if the table is wider than a page.
 * Table presentation attributes are retrieved from table lenses, or
 * through a table style. Built-in table styles are in inetsoft.report.style
 * package.
 */
public class TableElementInfo extends ElementInfo implements TableGroupableInfo {
   /**
    * construct the class
    */
   public TableElementInfo() {
      super();
   }

   /**
    * Get table layout.
    */
   public TableLayout getTableLayout() {
      return tlayout;
   }

   /**
    * Set table layout.
    */
   public void setTableLayout(TableLayout tlayout) {
      this.tlayout = tlayout;
   }

   /**
    * Get the table layout.
    */
   public int getLayout() {
      return autosize;
   }

   /**
    * Set the table layout.
    */
   public void setLayout(int layout) {
      autosize = layout;
   }

   /**
    * Get cell padding space.
    */
   public Insets getPadding() {
      return padding;
   }

   /**
    * Set cell padding space.
    */
   public void setPadding(Insets padding) {
      this.padding = padding;
   }

   /**
    * Set the table widow/orphan control option.
    * @param orphan true to eliminate widow/orphan rows.
    */
   public void setOrphanControl(boolean orphan) {
      this.orphan = orphan;
   }

   /**
    * Check the current widow/orphan control setting.
    * @return widow/orphan control option.
    */
   public boolean isOrphanControl() {
      return this.orphan;
   }

   /**
    * Set whether the table can be broken in to pages. Default to true.
    */
   public void setBreakable(boolean breakable) {
      this.breakable = breakable;
   }

   /**
    * Check if the table can span pages.
    */
   public boolean isBreakable() {
      return breakable;
   }

   /**
    * Set the filter info holder in this table.
    */
   @Override
   public void setBindingAttr(BindingAttr infos) {
      filters = infos;
   }

   /**
    * Get the filter info holder of this table.
    */
   @Override
   public BindingAttr getBindingAttr() {
      return filters;
   }

   /**
    * Set the column widths embedding option.
    */
   public void setEmbedWidth(boolean fix) {
      fixWidth = fix;
   }

   /**
    * Check if column widths are embedded.
    */
   public boolean isEmbedWidth() {
      return fixWidth;
   }

   /**
    * Set the table style.
    */
   public void setStyle(TableStyle style) {
      this.style = style;
   }

   /**
    * Get the table style.
    */
   public TableStyle getStyle() {
      return style;
   }

   @Override
   public Object clone() {
      try {
         TableElementInfo tinfo = (TableElementInfo) super.clone();

         if(style != null) {
            tinfo.setStyle((TableStyle) style.clone());
         }

         if(filters != null) {
            tinfo.setBindingAttr((BindingAttr) filters.clone());
         }

         if(tlayout != null) {
            tinfo.setTableLayout((TableLayout) tlayout.clone());
         }

         return tinfo;
      }
      catch(Exception e) {
         LOG.error("Failed to clone table element info", e);
      }

      return null;
   }

   /**
    * Get the name of the tag of the root of the properties xml tree.
    */
   @Override
   public String getTagName() {
      return "tableElementInfo";
   }

   /**
    * Create an ElementInfo.
    */
   @Override
   protected ElementInfo create() {
      return new TableElementInfo();
   }

   /**
    * This method must be overriden by subclass to return the default info in
    * section.
    */
   @Override
   public ElementInfo createInSection(boolean autoResize, String name) {
      TableElementInfo info =
         (TableElementInfo) super.createInSection(autoResize, name);
      info.autosize = ReportSheet.TABLE_FIT_PAGE;
      return info;
   }

   private TableStyle style; // table style
   private BindingAttr filters = null;
   private boolean fixWidth = false;

   // table attributes
   private int autosize;
   private Insets padding;
   private boolean orphan; // orphan control //@info
   private boolean breakable = true; // table on page page

   private TableLayout tlayout;

   private static final Logger LOG =
      LoggerFactory.getLogger(TableElementInfo.class);
}
