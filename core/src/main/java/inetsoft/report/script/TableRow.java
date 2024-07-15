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
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.internal.Util;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.util.script.ArrayObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This array represents one row in a table.
 */
public class TableRow extends ScriptableObject implements ArrayObject {
   public TableRow(XTable table, int row) {
      this(table, row, "Object", Object.class);
   }

   /**
    * @param property property name, e.g. Object, Background.
    * @param property type, e.g. Object.class, Color.class.
    */
   public TableRow(XTable table, int row, String property, Class pType) {
      this.table = table;
      this.row = row;
      this.property = property;
      this.pType = pType;
      min = (property.indexOf("Border") >= 0) ? -1 : 0;
      length = table.getColCount();

      MethodKey key = new MethodKey(table.getClass(), "set" + property,
                                    int.class, int.class, pType);
      Object method = methodCache.computeIfAbsent(key, MethodKey::getMethod);

      if(method instanceof Method) {
         setMethod = (Method) method;
      }
      else {
         LOG.error("Failed to get method: " + key);
      }

      key = new MethodKey(table.getClass(), "get" + property,
                          int.class, int.class);
      method = methodCache.computeIfAbsent(key, MethodKey::getMethod);

      if(method instanceof Method) {
         getMethod = (Method) method;
      }
      // if it is boolean type, we use "is" + property as method name string.
      else {
         key = new MethodKey(table.getClass(), "is" + property,
                             int.class, int.class);
         method = methodCache.computeIfAbsent(key, MethodKey::getMethod);

         if(method instanceof Method) {
            getMethod = (Method) method;
         }
         else {
            LOG.error("Failed to get method: " + key);
         }
      }

      setRow(row);
   }

   /**
    * Set the row index of this table row.
    */
   public void setRow(int row) {
      this.row = row;
   }

   /**
    * Get the col count from colmap.
    */
   private Object getColFromColMap(String col, Map colmap) {
      // @by mikec, first check the col name, if not found
      // check with dot appended, if still not found check with
      // case ignored.
      Object cnt = colcache.get(col);

      if(cnt != null) {
         cnt = cnt == NULL ? null : cnt;
         return cnt;
      }

      if(col != null && colmap != null) {
         cnt = colmap.get(col);

         // ignore prefix in field name
         if(cnt == null) {
            String temp = col.substring(col.lastIndexOf('.') + 1);
            cnt = colmap.get(temp);

            if(cnt != null) {
               colmap.put(col, cnt);
            }
         }

         // check ignore case
         if(cnt == null) {
            Iterator iter = colmap.keySet().iterator();
            String cname = null;

            while(iter.hasNext()) {
               String sname = (String) iter.next();

               if(sname.equalsIgnoreCase(col)) {
                  cname = sname;
                  break;
               }
            }

            if(cname != null) {
               cnt = colmap.get(cname);
               colmap.put(col, cnt);
            }
         }

         // ignore prefix in column name
         if(cnt == null) {
            Iterator iter = colmap.keySet().iterator();
            String cname = null;

            while(iter.hasNext()) {
               String sname = (String) iter.next();
               int dot = sname.indexOf('.');

               if(dot > 0 && sname.substring(dot + 1).equalsIgnoreCase(col)) {
                  cname = sname;
                  break;
               }
            }

            if(cname != null) {
               cnt = colmap.get(cname);
               colmap.put(col, cnt);
            }
         }

         if(cnt == null) {
            int idx = getColumnIndexMap().getColIndexByIdentifier(col);

            if(idx != -1) {
               cnt = idx;
               colmap.put(col, cnt);
            }
         }
      }

      Object val = cnt == null ? NULL : cnt;
      colcache.put(col, val);
      return cnt;
   }

   /**
    * Get the column header to index map.
    */
   private Map getColMap() {
      // @by larryl, if the row is 0, calling getObject(0, i) would cause
      // an infinite loop. This should never be called on formula column.
      // It is only used in CalcTableLens where columns are referenced
      // using index
      if(row != 0 && !headerInit) {
         headerInit = true;

         CrossTabFilter crosstab = null;

         if(table instanceof TableLens) {
            crosstab = Util.getCrosstab(table);
         }

         // create column map
         for(int i = 0; i < table.getColCount(); i++) {
            Object hdr = table.getObject(0, i);

            if(hdr != null) {
               String hstr = hdr.toString();
               colmap0.put(hstr, i);

               // @by larryl, for joined table, the table name will be part
               // of the column (stripped off in TableElementDef). We allow
               // the column to be referenced by using the column name without
               // the table/query name (customers.company can be referenced
               // as company).
               int dot = hstr.lastIndexOf('.');

               if(dot > 0) {
                  hstr = hstr.substring(dot + 1);

                  // this has lower priority as a column that is by default
                  // the same name as the unqualified name
                  if(colmap0.get(hstr) == null) {
                     colmap0.put(hstr, i);
                  }
               }
            }
            else {
               if(crosstab != null) {
                  Object rhstr = crosstab.getRowColumnHeader(i);

                  if(rhstr != null) {
                     colmap0.put(rhstr.toString(), i);
                  }

                  colmap0.put("Column [" + i + "]", i);
               }
            }
         }

         // create column identifier map to try to override the header map
         for(int i = 0; i < table.getColCount(); i++) {
            String identifier = table.getColumnIdentifier(i);
            Object hdr = table.getObject(0, i);

            // ignore duplicate columns (columnname.1)
            if(hdr != null && hdr.toString().startsWith(identifier + ".")) {
               String suffix = hdr.toString().substring(identifier.length() + 1);
               boolean number = suffix.chars().allMatch(c -> Character.isDigit(c));

               if(number) {
                  continue;
               }
            }

            if(identifier != null) {
               colmap0.put(identifier, i);
            }
         }
      }

      return colmap0;
   }

   /**
    * Get the row index of this table row scriptable.
    */
   public int getRow() {
      return row;
   }

   /**
    * Get property type.
    */
   @Override
   public Class getType() {
      return pType;
   }

   @Override
   public String getClassName() {
      return "TableRow";
   }

   @Override
   public boolean has(String id, Scriptable start) {
      Map colmap = getColMap();
      return id.equals("length") || getColFromColMap(id, colmap) != null ||
         super.has(id, start);
   }

   @Override
   public boolean has(int index, Scriptable start) {
      return min <= index && index < length;
   }

   @Override
   public Object get(String id, Scriptable start) {
      if(id.equals("length")) {
         return length;
      }
      else if(!notfound.contains(id)) {
         Map colmap = getColMap();
         Object col = getColFromColMap(id, colmap);

         if(col instanceof Integer) {
            if(getMethod != null) {
               try {
                  return get(table, getMethod, row, (Integer) col);
               }
               catch(Exception e) {
                  LOG.error("Failed to get table row property " +
                     id + " for column " + col, e);
               }
            }
         }
         // column not found, try base tables
         else if(!"not found".equals(col) && !"field".equals(id)) {
            TableCol tcol = findColumn(id);

            if(tcol != null && tcol.getMethod != null && tcol.row >= 0) {
               try {
                  return get(tcol.table, tcol.getMethod, tcol.row, tcol.column);
               }
               catch(Exception e) {
                  LOG.error("Failed to get table row property " +
                     id + " in base table at row " + tcol.row +
                     " and column " + tcol.column, e);
               }
            }
            else {
               // put it here so we don't go through the search next time
               colmap.put(id, "not found");
            }
         }

         notfound.add(id);
      }

      return super.get(id, start);
   }

   /**
    * This supports reference a field value by index (>= 0), or get a preview
    * row by using a negative index, e.g. field[-1]['col1'] returns the value
    * of last row at col1.
    */
   @Override
   public Object get(int index, Scriptable start) {
      // support previous row
      if(index < 0 && min >= 0) {
         // cache, optimization. js is single threaded so no synchronization is necessary
         if(row + index == prevIndex && prevRow != null) {
            return prevRow;
         }

         prevIndex = row + index;
         prevRow = new TableRow(table, row + index, property, pType);

         if(headerInit) {
            prevRow.headerInit = true;
            prevRow.colmap0 = colmap0;
         }

         return prevRow;
      }

      if(min <= index && index < length && getMethod != null) {
         try {
            return get(table, getMethod, row, index);
         }
         catch(Exception ex) {
            LOG.error("Failed to get table row indexed property: " + index, ex);
         }
      }
      else {
         String hdr = index + "";
         Map colmap = getColMap();

         if(colmap.containsKey(hdr)) {
            return get(hdr, start);
         }
      }

      return Undefined.instance;
   }

   /**
    * Get a cell value.
    */
   protected Object get(XTable table, Method getMethod, int row, int col) throws Exception {
      Object result = getMethod.invoke(table, row, col);

      if(getParentScope() == null) {
         return result;
      }

      // @by stephenwebster, Related to bug1431461209990
      // Prevent RHINO USAGE WARNING: ....
      return Context.javaToJS(result, getParentScope());
   }

   @Override
   public void put(String id, Scriptable start, Object value) {
      if(!putLocal(id, start, value)) {
         // ignore assignments to "length"--it's readonly.
         if(!id.equals("length")) {
            super.put(id, start, value);
         }
      }
   }

   // in local scope, don't pass to the current (start) scope
   boolean putLocal(String id, Scriptable start, Object value) {
      Map colmap = getColMap();
      Object col = getColFromColMap(id, colmap);

      if(col instanceof Integer) {
         put(((Integer) col).intValue(), start, value);
         notfound.remove(id);
         return true;
      }
      // column not found, try base tables
      else if(!notfound.contains(id)) {
         TableCol tcol = findColumn(id);

         if(tcol != null && tcol.setMethod != null) {
            try {
               tcol.setMethod.invoke(tcol.table, tcol.row, tcol.column,
                                     PropertyDescriptor.convert(value, pType));
               notfound.remove(id);
               return true;
            }
            catch(Exception e) {
               LOG.error("Failed to set table row property " + id +
                  " in base tabel at row " + tcol.row + " and column " +
                  tcol.column + " to " + value, e);
            }
         }
         else {
            notfound.add(id);
         }
      }

      return false;
   }

   @Override
   public void put(int index, Scriptable start, Object value) {
      // @by larryl, for CalcTableLens, the attrtable may contain the original
      // CalcTableLens instead of the expanded RuntimeCalcTableLens because
      // element script is executed before the calc formula. We let the
      // attributes to be set even if index >= length.
      if(setMethod != null) {
         try {
            setMethod.invoke(table, row, index, PropertyDescriptor.convert(value, pType));
         }
         catch(Exception e) {
            LOG.debug("Failed to set table row indexed property [" + index + "]: " + value, e);
         }
      }
      else {
         LOG.error("Property cannot be modified: " + property);
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
      Map colmap = getColMap();
      Object[] result = new Object[colmap.size() + 1];
      int i = 0;

      for(Object key : colmap.keySet()) {
         result[i++] = key;
      }

      result[result.length - 1] = "length";
      return result;
   }

   @Override
   public boolean hasInstance(Scriptable value) {
      return false;
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
    * Find column in base tables.
    */
   private TableCol findColumn(String hdr) {
      Map colmap = getColMap();
      Object col = getColFromColMap(hdr, colmap);
      int brow = -1;

      if(col != null) {
         return (col instanceof TableCol) ? (TableCol) col : null;
      }

      // find the column in base tables
      for(XTable tbl = table; tbl instanceof TableFilter;) {
         TableFilter ptbl = (TableFilter) tbl;
         tbl = ptbl.getTable();

         if(tbl == null) {
            break;
         }

         if(!Modifier.isPublic(tbl.getClass().getModifiers())) {
            continue;
         }

         for(int i = 0; i < tbl.getColCount(); i++) {
            Object name = tbl.getObject(0, i);

            if(name != null && name.toString().equals(hdr)) {
               TableCol tcol = new TableCol();
               tcol.table = tbl;
               tcol.column = i;

               if(brow != -1) {
                  tcol.row = brow;
               }
               else {
                  tcol.row = ptbl.getBaseRowIndex(row);
               }

               try {
                  tcol.getMethod = tbl.getClass().getMethod("get" + property, int.class, int.class);
               }
               catch(Throwable e) {
               }

               // @by larryl, getData() is only available in AttributeTableLens.
               // If a column is hidden, using getData() would not allow the
               // hidden column to be accessed if the tablelens does not have
               // a getData() defined. This hardcoded logic is not very clean
               // but short of pushing getData() to TableLens interface and
               // force all table lens to define it, this is the safest fix.
               if(tcol.getMethod == null && property.equals("Data")) {
                  try {
                     tcol.getMethod = tbl.getClass().getMethod("getObject", int.class, int.class);
                  }
                  catch(Throwable e) {
                  }
               }

               try {
                  tcol.setMethod = tbl.getClass().getMethod("set" + property, int.class, int.class,
                                                            pType);
               }
               catch(Throwable e) {
               }

               // @by larryl, see comments above
               if(tcol.setMethod == null && property.equals("Data")) {
                  try {
                     tcol.setMethod = tbl.getClass().getMethod("setObject", int.class, int.class,
                                                               pType);
                  }
                  catch(Throwable e) {
                  }
               }

               colmap.put(hdr, tcol);
               return tcol;
            }
         }
      }

      return null;
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

   public String toString() {
      Map colmap = getColMap();
      return super.toString() + colmap.toString();
   }

   private ColumnIndexMap getColumnIndexMap() {
      if(colIndexMap == null) {
         colIndexMap = new ColumnIndexMap(table);
      }

      return colIndexMap;
   }

   /**
    * Used to hold information about a column that is not in the current table
    * but in the base table wrapped in a condition filter or other filters.
    */
   static class TableCol {
      XTable table;
      Method setMethod = null;
      Method getMethod = null;
      int column = 0;
      int row = 0;

      public String toString() {
         return table + "[" + column + "]";
      }
   }

   private static class MethodKey {
      public MethodKey(Class cls, String method, Class ...params) {
         this.cls = cls;
         this.method = method;
         this.params = params;
      }

      public Object getMethod() {
         try {
            return cls.getMethod(method, params);
         }
         catch(NoSuchMethodException e) {
            return "";
         }
      }

      @Override
      public int hashCode() {
         return cls.hashCode() + method.hashCode() + Arrays.hashCode(params);
      }

      @Override
      public boolean equals(Object obj) {
         try {
            MethodKey key = (MethodKey) obj;
            return cls == key.cls && method.equals(key.method) && Arrays.equals(params, key.params);
         }
         catch(ClassCastException ex) {
            return false;
         }
      }

      public String toString() {
         return cls + "." + method;
      }

      private Class cls;
      private String method;
      private Class[] params;
   }

   private static final Object NULL = new Object();
   private static final Map<MethodKey, Object> methodCache = new ConcurrentHashMap<>();

   private Scriptable prototype;
   private XTable table;
   private ColumnIndexMap colIndexMap;
   private Method setMethod = null;
   private Method getMethod = null;
   private String property = "Object";
   private Class pType = Object.class;
   private int row, length, min = 0;
   // column name -> column index or TableCol
   private Map<String, Integer> colmap0 = new Object2ObjectOpenHashMap<>();
   private Map<String, Object> colcache = new Object2ObjectOpenHashMap<>(); // column cache
   private Set<String> notfound = new ObjectOpenHashSet<>(); // not found id cache
   private boolean headerInit = false; // column header initialized
   private transient TableRow prevRow = null;
   private transient int prevIndex = Integer.MIN_VALUE;

   private static final Logger LOG = LoggerFactory.getLogger(TableRow.class);
}
