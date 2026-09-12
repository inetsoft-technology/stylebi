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
import inetsoft.util.script.graal.ScriptArrayScope;
import inetsoft.util.script.graal.ScriptScope;

import java.util.Iterator;
import java.util.Map;

/**
 * XTableRow, lets end users query one row of an xtable object.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class XTableRow implements ScriptArrayScope {
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
   public String getClassName() {
      return "XTableRow";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object getMember(String name) {
      if(name.equals("length")) {
         return Integer.valueOf(table.getColCount());
      }

      Integer index = (Integer) map.get(name);

      if(index != null) {
         return table.getObject(row, index.intValue());
      }

      return null;
   }

   /**
    * Get a property from the object selected by an integral index.
    */
   @Override
   public Object getArrayElement(long index) {
      if(index >= 0 && index < table.getColCount()) {
         return table.getObject(row, (int) index);
      }

      return null;
   }

   /**
    * Get the number of indexed elements.
    */
   @Override
   public long getArraySize() {
      return table.getColCount();
   }

   /**
    * Set an indexed property in this object. Ported from the Rhino
    * {@code put(int, Scriptable, Object)}, which was a no-op. (#75423)
    */
   @Override
   public void setArrayElement(long index, Object value) {
      // do nothing
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean hasMember(String name) {
      if(name.equals("length") || map.containsKey(name)) {
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

   private XTable table;
   private int row;
   protected Map map;
   private ScriptScope parent;
}
