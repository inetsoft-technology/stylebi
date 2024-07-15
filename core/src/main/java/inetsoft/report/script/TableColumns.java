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
package inetsoft.report.script;

import inetsoft.report.*;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.util.script.ArrayObject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.IntStream;

/**
 * This array represents columns in a table.
 */
public abstract class TableColumns extends ScriptableObject implements ArrayObject {
   /**
    * @param property property name, e.g. Object, Background.
    */
   public TableColumns(String property, Class pType) {
      this.property = property;
      this.pType = pType;
   }

   /**
    * Get column property type.
    */
   @Override
   public Class getType() {
      return pType;
   }

   protected abstract TableLens getElementTable();

   /**
    * Initialize table. This needs to be delayed otherwise the table may
    * be null in the constructor.
    */
   private synchronized void init() {
      TableLens ntable = getElementTable();

      if(table == ntable && table != null) {
         return;
      }

      table = ntable;

      while(table != null) {
         try {
            setMethod = table.getClass().getMethod("set" + property,
               new Class[] { int.class, pType});
            break;
         }
         catch(Exception e) {
         }

         if(table instanceof TableFilter) {
            table = ((TableFilter) table).getTable();
         }
         else {
            break;
         }
      }

      if(colProp) {
         length0 = table.getColCount();
      }
      else {
	 // @by larryl, length0 (row count) delayed to avoid deadlock
      }

      try {
         getMethod = ntable.getClass().getMethod("get" + property,
            new Class[] { int.class });
      }
      catch(Throwable e) {
      }

      // create column map
      if(colProp && table.moreRows(0)) {
         for(int i = 0; i < table.getColCount(); i++) {
            Object hdr = table.getObject(0, i);

            if(hdr != null) {
               String header = hdr.toString();
               colmap.put(header, i);
               int index = header.lastIndexOf(".");

               if(index >= 0) {
                  colmap2.put(header.substring(index + 1), i);
               }
            }
         }
      }
   }

   @Override
   public String getClassName() {
      return "TableColumns";
   }

   @Override
   public boolean has(String id, Scriptable start) {
      init();

      return id.equals("length") || colmap.containsKey(id) ||
         colmap2.containsKey(id) || super.has(id, start);
   }

   @Override
   public boolean has(int index, Scriptable start) {
      init();

      return 0 <= index && (length0 < 0 || index < length0);
   }

   @Override
   public Object get(String id, Scriptable start) {
      init();

      if(id.equals("length")) {
         return getLength();
      }
      else if(colProp) {
         int col = colmap.getOrDefault(id, -1);

         if(col < 0) {
            col = colmap2.getOrDefault(id, -1);
         }

         if(col >= 0 && getMethod != null) {
            try {
               return getMethod.invoke(table, new Object[] {col});
            }
            catch(Exception e) {
               LOG.error("Failed to get table column property: " + id, e);
            }
         }
      }

      return super.get(id, start);
   }

   @Override
   public Object get(int index, Scriptable start) {
      init();

      if(getMethod != null) {
         try {
            return getMethod.invoke(table, new Object[] {Integer.valueOf(index)});
         }
         catch(Exception e) {
            LOG.error("Failed to get table column indexed property at: " + index, e);
         }
      }

      return Undefined.instance;
   }

   @Override
   public void put(String id, Scriptable start, Object value) {
      init();

      if(colProp) {
         int col = colmap.getOrDefault(id, -1);

         if(col < 0) {
            col = colmap2.getOrDefault(id, -1);
         }

         if(col >= 0) {
            put(col, start, value);
            return;
         }
      }

      // Ignore assignments to "length"--it's readonly.
      if(!id.equals("length")) {
         super.put(id, start, value);
      }
   }

   @Override
   public void put(int index, Scriptable start, Object value) {
      init();

      // don't check for length otherwise it could cause a deadlock
      if(setMethod != null) {
         try {
            setMethod.invoke(table,
               new Object[] {Integer.valueOf(index),
               PropertyDescriptor.convert(value, pType)});
         }
         catch(InvocationTargetException e) {
            LOG.error("Failed to set table column indexed property [" + index +
               "] to " + value, e.getTargetException());
         }
         catch(Exception e) {
            LOG.error("Failed to set table column indexed property [" + index +
               "] to " + value, e);
         }
      }
      else {
         LOG.error(
            "Property can not be modified: " + property + " " + table);
      }
   }

   @Override
   public Object getDefaultValue(Class hint) {
      if(hint == ScriptRuntime.BooleanClass) {
         return Boolean.TRUE;
      }
      else if(hint == ScriptRuntime.NumberClass) {
         return ScriptRuntime.NaNobj;
      }

      return this;
   }

   @Override
   public Object[] getIds() {
      init();

      if(colProp) {
         Set<String> set = new HashSet<>(colmap.keySet());
         final Object[] result = new Object[set.size() + 1];
         final Iterator<String> iterator = set.iterator();

         for(int i = 0; iterator.hasNext(); i++) {
            result[i] = iterator.next();
         }

         result[result.length - 1] = "length";
         return result;
      }
      else {
         return IntStream.range(0, getLength()).boxed().toArray();
      }
   }

   @Override
   public boolean hasInstance(Scriptable value) {
      return false;
   }

   @Override
   public Scriptable getPrototype() {
      return prototype;
   }

   @Override
   public void setPrototype(Scriptable prototype) {
      this.prototype = prototype;
   }

   /**
    * Get display suffix.
    */
   @Override
   public String getDisplaySuffix() {
      return "[index]";
   }

   /**
    * Get suffix.
    */
   @Override
   public String getSuffix() {
      return "[]";
   }

   /**
    * Get the length of the items.
    */
   private int getLength() {
      if(length0 < 0) {
         table.moreRows(Integer.MAX_VALUE);
         length0 = table.getRowCount();
      }

      return length0;
   }

   private Scriptable prototype;
   private TableLens table;
   private Method setMethod = null;
   private Method getMethod = null;
   private String property = "Object";
   private Class pType = Object.class;
   private int length0 = -1;
   private boolean colProp = true; // use column header as index
   private Object2IntMap<String> colmap = new Object2IntOpenHashMap<>(); // column name -> index
   private Object2IntMap<String> colmap2 = new Object2IntOpenHashMap<>(); // partial column name -> index

   private static final Logger LOG =
      LoggerFactory.getLogger(TableColumns.class);
}
