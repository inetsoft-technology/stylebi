/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.filter;

import inetsoft.report.TableLens;
import inetsoft.report.lens.AttributeTableLens;

import java.util.BitSet;
import java.util.Hashtable;

/**
 * Table highlight filter. This filter highlight cells in a table using
 * the highlight setting and condition.
 *
 * @deprecated
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
@Deprecated
public class HighlightFilter extends AttributeTableLens
   implements Cloneable {
   /**
    * Create a highlight filter.
    */
   public HighlightFilter(TableLens table) {
      this(table, table);
   }

   /**
    * Create a highlight filter.
    *
    * @param table the base table lens to apply highlight
    * @param calcTable the calculation table lens used to calculate highlight,
    * which might not be the table of the highlight group, for in the table
    * lens, hide columns are included, and it shoule always be the source of the
    * former table.
    */
   public HighlightFilter(TableLens table, TableLens calcTable) {
      super(table);
      this.calcTable = calcTable;

      init();
   }

   /**
    * Set the highlight group setting.
    */
   public HighlightGroup getHighlightGroup(int col) {
      return (HighlightGroup) highlightGroups.get(Integer.valueOf(col));
   }

   /**
    * Get the highlight group setting.
    */
   public void setHighlightGroup(int col, HighlightGroup group) {
      highlightGroups.put(col, group);
   }

   /**
    * Set highlight groups. This method will save the cloned Hashtable into
    * object.
    */
   public void removeAllHighlightGroups() {
      highlightGroups = new Hashtable();
   }

   /**
    * This method is called before a report is generated to allow a filter
    * to refresh cached values.
    */
   @Override
   public void invalidate() {
      super.invalidate();
      init();

      processed = new BitSet();
      startRow = 0;

      for(int c = getHeaderColCount(); c < getColCount(); c++) {
      	HighlightGroup hg = getHighlightGroup(c);

      	if(hg != null) {
      	   hg.refresh();
      	}
      }
   }

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number. If EOT is passed in, this method should wait
    * until the table is fully loaded.
    * @return true if the row exists, or false if no more rows.
    */
   @Override
   public boolean moreRows(int row) {
      boolean more = super.moreRows(row);

      // @by larryl, some row number may be skipped in this call, so we need
      // to check all rows above this row to make sure highlighting is not missed
      for(; startRow <= row && super.moreRows(startRow); startRow++) {
         applyHighlight(startRow);
      }

      return more;
   }

   private void init() {
      highlighted = new BitSet[getColCount()];

      for(int i = 0; i < highlighted.length; i++) {
         highlighted[i] = new BitSet();
      }
   }

   /**
    * If highlight has not been applied to a row, check for highlight.
    */
   private void applyHighlight(int row) {
      if(row >= 0 && !processed.get(row)) {
         TableLens table = getTable();

         processed.set(row);

         final HighlightGroup all = getHighlightGroup(-1);
         ColumnHighlight allattr = null;

         if(all != null && row >= getHeaderRowCount()) {
            allattr = (ColumnHighlight) all.findGroup(calcTable, row);
         }

         for(int c = 0; c < getColCount(); c++) {
            final HighlightGroup highlightGroup = getHighlightGroup(c);
            ColumnHighlight attr = null;

            if(highlightGroup != null && row >= getHeaderRowCount()) {
               attr = (ColumnHighlight) highlightGroup.findGroup(calcTable, 
                                                                 row);
            }

            if(attr != null || allattr != null) {
               highlighted[c].set(row);

               if(attr != null && attr.getFont() != null) {
                  setFont(row, c, attr.getFont());
               }
               else if(allattr != null && allattr.getFont() != null) {
                  setFont(row, c, allattr.getFont());
               }

               if(attr != null && attr.getForeground() != null) {
                  setForeground(row, c, attr.getForeground());
               }
               else if(allattr != null && allattr.getForeground() != null) {
                  setForeground(row, c, allattr.getForeground());
               }

               if(attr != null && attr.getBackground() != null) {
                  setBackground(row, c, attr.getBackground());
               }
               else if(allattr != null && allattr.getBackground() != null) {
                  setBackground(row, c, allattr.getBackground());
               }
            }
         }
      }
   }

   /**
    * Check whether a cell is be highlighten.
    * @param row the row number of the cell.
    * @param col the col number of the cell.
    * @return true if the cell is highlighten.
    */
   public boolean isCellHighlighted(int row, int col) {
      if(!moreRows(row) || col < 0 || col >= getColCount() || row < 0) {
         return false;
      }

      return highlighted[col].get(row);
   }

   private TableLens calcTable = null;
   private Hashtable highlightGroups = new Hashtable();
   private transient BitSet processed = new BitSet();
   private transient BitSet[] highlighted;
   private transient int startRow = 0;
}

