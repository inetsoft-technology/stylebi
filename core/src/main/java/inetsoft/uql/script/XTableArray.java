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
package inetsoft.uql.script;

import inetsoft.report.script.formula.CellRange;
import inetsoft.uql.XTable;
import inetsoft.uql.util.XUtil;
import inetsoft.util.script.graal.ScriptArrayScope;
import inetsoft.util.script.graal.ScriptScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * XTableArray, lets end users query an xtable object.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class XTableArray implements ScriptArrayScope {
   /**
    * Constructor.
    */
   public XTableArray() {
      super();
   }

   /**
    * Constructor.
    */
   public XTableArray(XTable table) {
      this();

      setTable(table);
   }

   /**
    * Get the table data.
    * @return the contained table data.
    */
   private XTable getTable0() {
      XTable table = getTable();

      if(table != lastTable) {
         lastTable = table;
         map = null;
      }

      if(table != null && map == null) {
         map = new HashMap();

         for(int i = 0; i < table.getColCount(); i++) {
            String header = XUtil.getHeader(table, i).toString();
            map.put(header, Integer.valueOf(i));
         }
      }

      return table;
   }

   /**
    * Set the table data.
    * @param table the contained table data.
    */
   protected void setTable(XTable table) {
      this.table = table;
   }

   /**
    * Get the table data.
    * @return the contained table data.
    */
   protected XTable getTable() {
      return table;
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   public String getClassName() {
      return "XTableArray";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object getMember(String name) {
      XTable table = getTable0();

      if(table == null){
         return null;
      }

      if(name.equals("length")) {
         table.moreRows(Integer.MAX_VALUE);
         return Integer.valueOf(table.getRowCount());
      }
      else if(name.equals("size")) {
         return Integer.valueOf(table.getColCount());
      }

      try {
         CellRange range = CellRange.parse(name);
         Collection cells = range.getCells(getTable());
         return range.getCollectionValue(cells);
      }
      catch(Exception ex) {
         LOG.warn("Failed to get property " + name + " from " + this, ex);
      }

      return null;
   }

   /**
    * Get a property from the object selected by an integral index.
    */
   @Override
   public Object getArrayElement(long index) {
      XTable table = getTable0();

      if(table == null){
         return null;
      }

      table.moreRows((int) index);
      int rcount = table.getRowCount();
      rcount = rcount < 0 ? -rcount - 1 : rcount;

      if(index >= 0 && index < rcount) {
         return new XTableRow(table, (int) index, map);
      }

      return null;
   }

   /**
    * Get the number of indexed elements.
    */
   @Override
   public long getArraySize() {
      XTable table = getTable0();

      if(table == null) {
         return 0;
      }

      table.moreRows(Integer.MAX_VALUE);
      return table.getRowCount();
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean hasMember(String name) {
      XTable table = getTable0();

      if(table == null){
         return false;
      }

      if(name.equals("length") || name.equals("size")) {
         return true;
      }

      return false;
   }

   /**
    * Set a named property in this object.
    */
   @Override
   public void putMember(String name, Object value) {
      // do nothing
   }

   /**
    * Remove a property from this object.
    */
   @Override
   public boolean removeMember(String name) {
      // do nothing
      return false;
   }

   /**
    * Get the parent scope of the object.
    */
   @Override
   public ScriptScope getParentScope() {
      return parent;
   }

   /**
    * Set the parent scope of the object.
    */
   public void setParentScope(ScriptScope parent) {
      this.parent = parent;
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getMemberKeys() {
      XTable table = getTable0();

      if(table == null){
         return new Object[0];
      }

      table.moreRows(Integer.MAX_VALUE);
      Object[] ids = new Object[table.getRowCount() + 2];

      for(int i = 0; i < table.getRowCount(); i++) {
         ids[i] = Integer.valueOf(i);
      }

      ids[ids.length - 2] = "length";
      ids[ids.length - 1] = "size";

      return ids;
   }

   /**
    * Unwrap the object by returning the wrapped value.
    * @return the wrapped value.
    */
   public Object unwrap() {
      return getTable0();
   }

   /**
    * Convert to string form.
    */
   public String toString() {
      XTable Table = getTable0();
      return table == null ? null : table.toString();
   }

   private XTable table;
   private XTable lastTable;
   private Map map;
   private ScriptScope parent;

   private static final Logger LOG =
      LoggerFactory.getLogger(XTableArray.class);
}
