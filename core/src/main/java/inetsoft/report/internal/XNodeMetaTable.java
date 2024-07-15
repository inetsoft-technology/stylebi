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
package inetsoft.report.internal;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.DefaultTableDataDescriptor;
import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.XTimestamp;

import java.util.*;

/**
 * Table meta data.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XNodeMetaTable extends AbstractTableLens {
   /**
    * Create a table meta data model from a tree definition.
    */
   public XNodeMetaTable(XTypeNode root) {
      this(false, root, false);
   }

   /**
    * Create a table meta data model from a tree definition.
    */
   public XNodeMetaTable(boolean multirow, XTypeNode root) {
      this(multirow, root, false);
   }

   public XNodeMetaTable(boolean multirow, XTypeNode root, boolean fieldname) {
      this(multirow, root, fieldname, false);
   }

   /**
    * Create a table meta data model from a tree definition.
    * @param fieldname true to show field name instead of example data.
    */
   public XNodeMetaTable(boolean multirow, XTypeNode root, boolean fieldname, boolean headerOnly) {
      Object[] header, row;

      header = new Object[ncol = root == null ? 0 : root.getChildCount()];
      row = new Object[ncol];
      examplers = new Object[ncol];

      for(int i = 0; i < row.length; i++) {
         XNode child = root.getChild(i);
         header[i] = child.getAttribute("alias");

         if(header[i] == null) {
            header[i] = child.getName();
         }

         XTypeNode typeChild = null;

         if(child instanceof XSequenceNode) {
            typeChild = (XTypeNode) child.getChild(0);
         }
         else if(child instanceof XTypeNode) {
            typeChild = (XTypeNode) child;
         }
         else {
            typeChild = new StringType();
         }

         XMetaInfo minfo = typeChild.getXMetaInfo();

         if(minfo != null) {
            TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL,
               getType(typeChild), new String[] {header[i].toString()});

            if(mmap == null) {
               mmap = new HashMap();
            }

            mmap.put(path, minfo);
         }

         examplers[i] = getExampler((String) header[i], typeChild);
         row[i] = fieldname ? "[" + (Object) header[i] + "]" : examplers[i];

         types.put(child.getName(), getType(typeChild));
      }

      if(ncol == 0) {
         header = new Object[] {"Unknown"};
         row = new Object[] {"XXXXX"};
         ncol = 1;
      }

      rows.addElement(header);

      if(!headerOnly) {
         rows.addElement(row);
      }

      if(multirow && !headerOnly) {
         rows.addElement(row);
      }
   }

   /**
    * Get the number of rows in the table.
    */
   @Override
   public int getRowCount() {
      return rows.size();
   }

   /**
    * Set the number of rows in the table. If new rows are added, they are
    * copied from the last existing row.
    */
   public void setRowCount(int cnt) {
      if(rows.size() == cnt) {
         return;
      }

      rows.setSize(cnt);

      for(int i = 1; i < cnt; i++) {
         if(rows.get(i) == null) {
            rows.setElementAt(((Object[]) rows.get(i - 1)).clone(), i);
         }
      }

      fireChangeEvent();
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return ncol;
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   @Override
   public Class getColType(int col) {
      if(col < 0 || col >= examplers.length) {
         return String.class;
      }

      return examplers[col].getClass();
   }

   @Override
   public int getHeaderRowCount() {
      return 1;
   }

   @Override
   public int getHeaderColCount() {
      return 0;
   }

   @Override
   public Object getObject(int row, int col) {
      if(row < 0 || row >= rows.size()) {
         return null;
      }

      return ((Object[]) rows.elementAt(row))[col];
   }

   @Override
   public void setObject(int row, int col, Object val) {
      ((Object[]) rows.elementAt(row))[col] = val;
      fireChangeEvent();
   }

   protected String getType(XTypeNode typenode) {
      return typenode.getType();
   }

   protected String getType(String name) {
      return (String) types.get(name);
   }

   protected Object getExampler(String name, XTypeNode typenode) {
      String type = typenode.getType();
      int nameLen = (name == null) ? 1 : name.length();

      if(type.equals(XSchema.STRING)) {
         Integer lobj = (Integer) typenode.getAttribute("length");
         int len = 8;

         if(lobj != null) {
            len = Math.max(len, lobj.intValue());
         }

         len = Math.min(len, nameLen);

         return Tool.getChars('X', Math.min(15, len));
      }
      else if(type.equals(XSchema.BOOLEAN)) {
         return Boolean.TRUE;
      }
      else if(type.equals(XSchema.FLOAT)) {
         return Float.valueOf(999.99F);
      }
      else if(type.equals(XSchema.DOUBLE)) {
         return Double.valueOf(999.99);
      }
      else if(type.equals(XSchema.CHAR)) {
         return "X";
      }
      else if(type.equals(XSchema.BYTE)) {
         return Byte.valueOf((byte) 99);
      }
      else if(type.equals(XSchema.SHORT)) {
         return Short.valueOf((short) 999);
      }
      else if(type.equals(XSchema.INTEGER)) {
         return Integer.valueOf(999);
      }
      else if(type.equals(XSchema.LONG)) {
         return Long.valueOf(999);
      }
      else if(type.equals(XSchema.TIME_INSTANT)) {
         return new XTimestamp(System.currentTimeMillis());
      }
      else if(type.equals(XSchema.DATE)) {
         return new java.sql.Date(System.currentTimeMillis());
      }
      else if(type.equals(XSchema.TIME)) {
         return new java.sql.Time(System.currentTimeMillis());
      }

      return Tool.getChars('X', Math.min(5, nameLen));
   }

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   @Override
   public final TableDataDescriptor getDescriptor() {
      if(descriptor == null) {
         descriptor = new TableDataDescriptor2(this);
      }

      return descriptor;
   }

   /**
    * Table data descriptor.
    */
   public final class TableDataDescriptor2 extends DefaultTableDataDescriptor {
      public TableDataDescriptor2(XTable table) {
         super(table);
      }

      /**
       * Get meta info of a specified table data path.
       * @param path the specified table data path.
       * @return meta info of the table data path.
       */
      @Override
      public final XMetaInfo getXMetaInfo(TableDataPath path) {
         if(mmap == null || !path.isCell()) {
            return null;
         }

         return (XMetaInfo) mmap.get(path);
      }

      @Override
      public List<TableDataPath> getXMetaInfoPaths() {
         List<TableDataPath> list = new ArrayList<>();

         if(!mmap.isEmpty()) {
            list.addAll(mmap.keySet());
         }

         return list;
      }

      /**
       * Check if contains format.
       * @return true if contains format.
       */
      @Override
      public final boolean containsFormat() {
         if(cformat == 0) {
            cformat = XUtil.containsFormat(mmap) ? CONTAINED : NOT_CONTAINED;
         }

         return cformat == CONTAINED;
      }

      /**
       * Check if contains drill.
       * @return true if contains drill.
       */
      @Override
      public final boolean containsDrill() {
         if(cdrill == 0) {
            cdrill = XUtil.containsDrill(mmap) ? CONTAINED : NOT_CONTAINED;
         }

         return cdrill == CONTAINED;
      }

      /**
       * Get column ref type.
       */
      public int getRefType(String col) {
         Integer n = refTypes.get(col);
         return (n == null) ? DataRef.NONE : n;
      }

      /**
       * Set column ref type.
       */
      public void setRefType(String col, int refType) {
         refTypes.put(col, refType);
      }

      /**
       * Get default formula.
       */
      public String getDefaultFormula(String col) {
         return defFormulas.get(col);
      }

      /**
       * Set default formula.
       */
      public void setDefaultFormula(String col, String formula) {
         defFormulas.put(col, formula);
      }

      private static final int NOT_CONTAINED = 1;
      private static final int CONTAINED = 2;
      private int cformat = 0;
      private int cdrill = 0;
      private Map<String, Integer> refTypes = new HashMap();
      private Map<String, String> defFormulas = new HashMap();
   }

   private int ncol;
   private Vector rows = new Vector();
   private Map types = new HashMap();
   private Map mmap = null;
   private Object[] examplers;
   private TableDataDescriptor descriptor;
}
