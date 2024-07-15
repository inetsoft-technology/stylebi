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
package inetsoft.uql.script;

import inetsoft.report.script.formula.CellRange;
import inetsoft.uql.XTable;
import inetsoft.uql.util.XUtil;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * XTableArray, lets end users query an xtable object.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class XTableArray implements Scriptable, Wrapper {
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
   @Override
   public String getClassName() {
      return "XTableArray";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      XTable table = getTable0();

      if(table == null){
         return Undefined.instance;
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
         LOG.warn("Failed to get property " + name + " from " + start, ex);
      }

      return Undefined.instance;
   }

   /**
    * Get a property from the object selected by an integral index.
    */
   @Override
   public Object get(int index, Scriptable start) {
      XTable table = getTable0();

      if(table == null){
         return Undefined.instance;
      }

      table.moreRows(index);
      int rcount = table.getRowCount();
      rcount = rcount < 0 ? -rcount - 1 : rcount;

      if(index >= 0 && index < rcount) {
         return new XTableRow(table, index, map);
      }

      return Undefined.instance;
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
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
    * Indicate whether or not an indexed property is defined in an object.
    */
   @Override
   public boolean has(int index, Scriptable start) {
      XTable table = getTable0();

      if(table == null){
         return false;
      }

      table.moreRows(index);
      int rcount = table.getRowCount();
      rcount = rcount < 0 ? -rcount - 1 : rcount;
      return index >= 0 && index < rcount;
   }

   /**
    * Set a named property in this object.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      // do nothing
   }

   /**
    * Set an indexed property in this object.
    */
   @Override
   public void put(int index, Scriptable start, Object value) {
      // do nothing
   }

   /**
    * Remove a property from this object.
    */
   @Override
   public void delete(String name) {
      // do nothing
   }

   /**
    * Remove a property from this object.
    */
   @Override
   public void delete(int index) {
      // do nothing
   }

   /**
    * Get the prototype of the object.
    */
   @Override
   public Scriptable getPrototype() {
      return prototype;
   }

   /**
    * Set the prototype of the object.
    */
   @Override
   public void setPrototype(Scriptable prototype) {
      this.prototype = prototype;
   }

   /**
    * Get the parent scope of the object.
    */
   @Override
   public Scriptable getParentScope() {
      return parent;
   }

   /**
    * Set the parent scope of the object.
    */
   @Override
   public void setParentScope(Scriptable parent) {
      this.parent = parent;
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
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
    * Get the default value of the object with a given hint.
    */
   @Override
   public Object getDefaultValue(Class hint) {
      return table;
   }

   /**
    * Implement the instanceof operator.
    */
   @Override
   public boolean hasInstance(Scriptable instance) {
      return false;
   }

   /**
    * Unwrap the object by returning the wrapped value.
    * @return the wrapped value.
    */
   @Override
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
   private Scriptable parent;
   private Scriptable prototype;

   private static final Logger LOG =
      LoggerFactory.getLogger(XTableArray.class);
}
