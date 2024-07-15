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

import inetsoft.uql.XTable;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.util.Iterator;
import java.util.Map;

/**
 * XTableRow, lets end users query one row of an xtable object.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class XTableRow implements Scriptable {
   /**
    * Constructor.
    */
   public XTableRow(XTable table, int row, Map map) {
      this.table = table;
      this.row = row;
      this.map = map;
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "XTableRow";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if(name.equals("length")) {
         return Integer.valueOf(table.getColCount());
      }

      Integer index = (Integer) map.get(name);

      if(index != null) {
         return table.getObject(row, index.intValue());
      }

      return Undefined.instance;
   }

   /**
    * Get a property from the object selected by an integral index.
    */
   @Override
   public Object get(int index, Scriptable start) {
      if(index >= 0 && index < table.getColCount()) {
         return table.getObject(row, index);
      }

      return Undefined.instance;
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      if(name.equals("length") || map.containsKey(name)) {
         return true;
      }

      return false;
   }

   /**
    * Indicate whether or not an indexed property is defined in an object.
    */
   @Override
   public boolean has(int index, Scriptable start) {
      return index >= 0 && index < table.getColCount();
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
      int counter = 0;
      Object[] ids = new Object[table.getColCount() + map.size() + 1];

      for(int i = 0; i < table.getColCount(); i++) {
         ids[counter++] = Integer.valueOf(i);
      }

      Iterator keys = map.keySet().iterator();

      while(keys.hasNext()) {
         ids[counter++] = keys.next();
      }

      ids[counter++] = "length";

      return ids;
   }

   /**
    * Get the default value of the object with a given hint.
    */
   @Override
   public Object getDefaultValue(Class hint) {
      return this;
   }

   /**
    * Implement the instanceof operator.
    */
   @Override
   public boolean hasInstance(Scriptable instance) {
      return false;
   }

   private XTable table;
   private int row;
   protected Map map;
   private Scriptable parent;
   private Scriptable prototype;
}
