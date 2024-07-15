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
package inetsoft.report.composition.execution;

import inetsoft.report.*;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.xmla.MemberObject;
import inetsoft.uql.xmla.XMLAUtil;
import inetsoft.util.Tool;

/**
 * The VSCubeTableLens class extends DefaultTableLens. It truncates the prefix
 * of data values retrieved from olap cubes.
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class VSCubeTableLens extends DefaultTableFilter
   implements DataTableLens, MemberObjectTableLens
{
   /**
    * Constructor.
    * @param lens contained base table lens.
    */
   public VSCubeTableLens(TableLens lens) {
      this(lens, null);
   }

   /**
    * Create a copy of a table lens.
    * @param lens contained base table lens.
    * @param columns the corresponding table column selection.
    */
   public VSCubeTableLens(TableLens lens, ColumnSelection columns) {
      super(lens);
      this.columns = columns;

      dimtypes = new int[lens.getColCount()];

      // optimization, dim type is used in getObject() so it's called a lot
      for(int i = 0; i < dimtypes.length; i++) {
         dimtypes[i] = getDimensionType0(i);
      }
   }

   /**
    * Get DataRef of a specified table column.
    * @param selection column selection.
    * @param header the specified table column header.
    * @return column DataRef if any.
    */
   public static DataRef getColumn(ColumnSelection selection, String header) {
      if(selection == null || header == null) {
         return null;
      }

      for(int i = 0; i < selection.getAttributeCount(); i++) {
         DataRef ref = selection.getAttribute(i);

         if(Tool.equals(ref.getAttribute(), header)) {
            return ref;
         }

         if(Tool.equals(ref.getName(), header)) {
            return ref;
         }

         if(ref instanceof ColumnRef) {
            if(Tool.equals(((ColumnRef) ref).getCaption(), header)) {
               return ref;
            }
         }
      }

      return null;
   }

   /**
    * Get dimension type.
    * @param selection the specified column selection.
    * @param header column header.
    * @return column dimension type if any.
    */
   public static int getDimensionType(ColumnSelection selection, String header)
   {
      DataRef ref = getColumn(selection, header);

      if(ref == null) {
         header = getRealHeader(header);
         ref = getColumn(selection, header);
      }

      return ref == null ? DataRef.NONE : ref.getRefType();
   }

   /**
    * Get cell display value.
    * @param str the original value.
    * @param dimType dimension type.
    * @return display value.
    */
   public static String getDisplayValue(Object obj, int dimType) {
      String str = Tool.toString(obj);

      // ignore normal type and model dimension
      if((dimType & DataRef.CUBE) != DataRef.CUBE ||
         dimType == DataRef.CUBE_MODEL_DIMENSION)
      {
         return str;
      }

      String prop = SreeEnv.getProperty("olap.table.originalContent");
      boolean isTime = (dimType & DataRef.CUBE_TIME_DIMENSION) ==
         DataRef.CUBE_TIME_DIMENSION;
      boolean truncate = false;

      if(prop == null) {
         truncate = !isTime;
      }
      else {
         truncate = !prop.equalsIgnoreCase("true");
      }

      if(truncate && obj instanceof MemberObject) {
         MemberObject mobj = (MemberObject) obj;
         str = mobj.toView();
      }

      if(dimType == DataRef.CUBE_MODEL_TIME_DIMENSION) {
         if(str.startsWith("Date.")) {
            str = str.substring("Date.".length());
         }

         if(truncate) {
            int idx0 = str.lastIndexOf(".");

            if(idx0 >= 0) {
               str = str.substring(idx0 + 1);
            }

            return str;
         }
      }

      return truncate ? getDisplayValue(str) : getDisplayFullValue(str);
   }

   /**
    * Get cell display value.
    * @param str the original value.
    * @return full display value.
    */
   public static String getDisplayFullValue(String str) {
      str = Tool.replaceAll(str, "[", "");
      str = Tool.replaceAll(str, "]", "");

      return str;
   }

   /**
    * Get cell display value.
    * @param str the original value.
    * @return display value.
    */
   public static String getDisplayValue(String str) {
      int idx0 = str.lastIndexOf(".[");
      int idx1 = str.lastIndexOf("]");

      if(idx1 >= 0 && !str.endsWith("]")) {
         int dot = str.lastIndexOf(".");

         if(dot >= 0) {
            return str.substring(dot + 1);
         }
      }

      if(idx0 >= 0 && idx1 >= 0 && idx0 < idx1) {
         return str.substring(idx0 + 2, idx1);
      }

      idx0 = str.lastIndexOf("[");

      if(idx0 >= 0 && idx1 >= 0 && idx0 < idx1) {
         str = str.substring(idx0 + 1, idx1);
      }

      return str;
   }

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   @Override
   public Object getObject(int r, int c) {
      Object obj = super.getObject(r, c);

      if(r < getHeaderRowCount()) {
         // dimension name should be kept for headers
      }
      else if(obj instanceof MemberObject || obj instanceof String) {
         return getDisplayValue(obj, dimtypes[c]);
      }

      return obj;
   }

   /**
    * Get data.
    * @param r row index.
    * @param c column index.
    * @return data in the specified cell.
    */
   @Override
   public Object getData(int r, int c) {
      TableLens tbl = getTable();
      Object obj = (tbl instanceof DataTableLens)
         ? ((DataTableLens) tbl).getData(r, c) : tbl.getObject(r, c);

      if(obj instanceof MemberObject) {
         obj = ((MemberObject) obj).getUName();
      }

      return obj;
   }

   /**
    * Get MemberObject.
    * @param r row index.
    * @param c column index.
    * @return MemberObject in the specified cell.
    */
   @Override
   public Object getMemberObject(int r, int c) {
      return super.getObject(r, c);
   }

   /**
    * Get column selection.
    * @return column selection.
    */
   public ColumnSelection getColumnSelection() {
      return columns;
   }

   /**
    * Get comparable object.
    */
   public static Object getComparableObject(Object obj, int dimType) {
      // fix bug1288061193031
      if((dimType & DataRef.CUBE) != DataRef.CUBE ||
         dimType == DataRef.CUBE_MODEL_DIMENSION)
      {
         return obj;
      }

      if(dimType == DataRef.CUBE_MODEL_TIME_DIMENSION) {
         return XMLAUtil.getDateStr((String) obj);
      }

      return getDisplayValue(obj, dimType);
   }

   /**
    * Get dimension type by column index.
    */
   private int getDimensionType0(int col) {
      String header = table.getColumnIdentifier(col);
      int type = getDimensionType(columns, header);

      if(type == DataRef.NONE) {
         header = table.getObject(0, col) + "";
         type = getDimensionType(columns, header);
      }

      return type;
   }

   /**
    * Get header name in named group.
    */
   private static String getRealHeader(String groupHeader) {
      if(groupHeader == null) {
         return groupHeader;
      }

      int idx = groupHeader.indexOf("Group(");

      if(idx >= 0) {
         groupHeader = groupHeader.substring(
            idx + "Group(".length(), groupHeader.length() - 1);
      }

      return groupHeader;
   }

   private ColumnSelection columns;
   private int[] dimtypes;
}
