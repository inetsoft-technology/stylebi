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
package inetsoft.report.composition;

import java.io.Serializable;

/**
 * FormTableRow represent one row for FormTable, it maintains data and row state.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class FormTableRow implements Serializable {
   /**
    * Old row state.
    */
   public static final int OLD = 1;

   /**
    * Changed row state.
    */
   public static final int CHANGED = 2;

   /**
    * Added row state.
    */
   public static final int ADDED = 3;

   /**
    * Deleted row state.
    */
   public static final int DELETED = 4;

   /**
    * Constructure.
    * @param len the data lenth.
    */
   public FormTableRow(int len) {
      data = new Object[len];
      label = new String[len];
      state = ADDED;
   }

   public FormTableRow(int len, int index) {
      this(len);
      this.index = index;
   }

   /**
    * Constructure.
    * @param data the data values.
    * @parem index old row index.
    */
   public FormTableRow(Object[] data, int index) {
      this.data = data;
      this.index = index;
      state = OLD;

      label = new String[data.length];
   }

   /**
    * Get row state.
    */
   public int getRowState() {
      return state;
   }

   /**
    * Set row state.
    */
   public void setRowState(int state) {
      this.state = state;
   }

   /**
    * Get this row size.
    */
   public int size() {
      return data.length;
   }

   /**
    * Get cell value.
    * @param index column index.
    */
   public Object get(int index) {
      return data[index];
   }

   /**
    * Set cell value.
    * @param index column index.
    * @param value the cell value.
    */
   public void set(int index, Object value) {
      data[index] = value;

      if(state == OLD) {
         state = CHANGED;
      }
   }

   /**
    * Get cell label.
    * @param index column index.
    */
   public String getLabel(int index) {
      return label[index];
   }

   /**
    * Set cell label.
    * @param index column index.
    * @param str the cell label.
    */
   public void setLabel(int index, String str) {
      label[index] = str;

      if(state == OLD) {
         state = CHANGED;
      }
   }

   /**
    * Mark this row state to deleted.
    */
   public void delete() {
      state = DELETED;
   }

   /**
    * Commit this row, change row state to old.
    */
   public void commit() {
      state = OLD;
   }

   /**
    * Get base row index.
    */
   public int getBaseRowIndex() {
      return index;
   }

   /**
    * Return row to string.
    */
   public String toString() {
      String str = "";

      for(int i = 0; i < data.length; i++) {
         str += "[" + i + ": " + data[i] + "] ";
      }

      return str + state;
   }

   private int state = OLD;
   private int index = -1;
   private Object[] data;
   private String[] label;
}
