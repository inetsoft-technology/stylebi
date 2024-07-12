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

import inetsoft.report.*;
import inetsoft.report.composition.execution.VSCubeTableLens;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.xmla.MemberObject;

import java.util.HashMap;

/**
 * CubeFilter truncates data prefix in crosstab.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class CrossTabCubeFilter extends CrossTabFilter implements DataTableLens
{
   /**
    * Constructor.
    */
   public CrossTabCubeFilter(TableLens table, ColumnSelection selection,
                             int[] rowh, int[] colh, int[] dcol, Formula[] sum,
                             boolean mergeSpan)
   {
      super(table, rowh, colh, dcol, sum, false, false,
         XConstants.NONE_DATE_GROUP, XConstants.NONE_DATE_GROUP, mergeSpan);

      this.columns = selection;
   }

   public Object getData0(int r, int c) {
      return super.getObject(r, c);
   }
   /**
    * Get data.
    * @param r row index.
    * @param c column index.
    * @return data in the specified cell.
    */
   @Override
   public Object getData(int r, int c) {
      Object obj = super.getObject(r, c);

      if(obj instanceof MemberObject) {
         obj = ((MemberObject) obj).getUName();
      }

      return obj;
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

      if(!(obj instanceof MemberObject || obj instanceof String)) {
         return obj;
      }

      String header = null;

      if(isHeaderCell(r, c)) {
         // will throw exception if the cell is itself a header
         try {
            header = getHeaderName(r, c);
         }
         catch(Exception ex) {
            header = (String) obj;
         }
      }
      else {
         header = getDataName(r, c);
      }

      String name = getDisplayValue(obj, header);

      if(header == null || (header.equals(obj))) {
         return processHeader(name);
      }

      return name;
   }

   public String getDisplayValue(Object obj, String header) {
      return VSCubeTableLens.getDisplayValue(obj,
         VSCubeTableLens.getDimensionType(columns, header));
   }

   /**
    * Get the corresponding DataRef of by column header.
    */
   public DataRef getColumn(String header) {
      return VSCubeTableLens.getColumn(columns, header);
   }

   /**
    * Get header content.
    */
   @Override
   protected String getHeader0(int index) {
      String header = super.getHeader0(index);

      return NamedRangeRef.getBaseName(header);
   }

   /**
   /**
    * Create the meta info.
    */
   @Override
   protected void createXMetaInfo() {
      // for cube, don't create any default meta info, because
      // it cannot support date level
      // fix bug1288061193031
      minfos = new HashMap();
      levels = new HashMap();
   }

   /**
    * Process the crosstab header cell value.
    * @param header the cell header.
    * @return the caption of the cell header.
    */
   private String processHeader(String header) {
      StringBuilder buffer = new StringBuilder();

      if(header == null) {
         return null;
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);
         String caption = null;
         String name = ref.getAttribute();

         if((ref.getRefType() & DataRef.CUBE) != DataRef.CUBE) {
            return header;
         }

         while(ref instanceof DataRefWrapper) {
            ref = ((DataRefWrapper) ref).getDataRef();
            caption = ref.toView();
         }

         if(header.indexOf(name) >= 0 && !header.equals(name) &&
            header.indexOf("/") > 0 && caption != null)
         {
            if(buffer.toString().length() > 0) {
               buffer.append("/");
            }

            buffer.append(caption);
         }

         if(header.equals(name)) {
            return header = caption != null ? caption : header;
         }
      }

      if(buffer.toString().length() != 0) {
         return buffer.toString();
      }

      return header;
   }

   private ColumnSelection columns;
   private TableDataDescriptor desc;
}
