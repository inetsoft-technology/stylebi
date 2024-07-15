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
package inetsoft.report;

import inetsoft.report.style.TableStyle;

import java.awt.*;
import java.text.Format;
import java.util.Enumeration;

/**
 * Table is the most common element on a report. It can be used to
 * present a dataset in a tabular grid. Tables can span across pages,
 * and can be wrapped horizontally if the table is wider than a page.
 * Table presentation attributes are retrieved from table lenses, or
 * through a table style. Built-in table styles are in inetsoft.report.style
 * package.
 */
public interface TableElement extends ReportElement {

   /**
    * Get the fixed column widths in pixels.
    */
   public int[] getFixedWidths();

   /**
    * Set the fixed column widths in pixels.
    */
   public void setFixedWidths(int[] ws);

   /**
    * Get the table layout policy.
    */
   public int getLayout();

   /**
    * Set the table layout policy.
    */
   public void setLayout(int layout);

   /**
    * Get cell padding space.
    */
   public Insets getPadding();

   /**
    * Set cell padding space.
    */
   public void setPadding(Insets padding);

   /**
    * Get the table layout of the table.
    */
   public TableLayout getTableLayout();

   /**
    * Set table layout.
    */
   public void setTableLayout(TableLayout tlayout);

   /**
    * Get the table lens.
    */
   public TableLens getTable();

   /**
    * Set the table lens.
    */
   public void setTable(TableLens table);

   /**
    * Get the column width in pixels.
    */
   public float getColWidth(int col);

   /**
    * Set the table widow/orphan control option.
    * @param orphan true to eliminate widow/orphan rows.
    */
   public void setOrphanControl(boolean orphan);

   /**
    * Check the current widow/orphan control setting.
    * @return widow/orphan control option.
    */
   public boolean isOrphanControl();

   /**
    * Set whether the table can be broken in to pages. Default to true.
    */
   public void setBreakable(boolean breakable);

   /**
    * Check if the table can span pages.
    */
   public boolean isBreakable();
}
