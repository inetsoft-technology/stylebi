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
package inetsoft.report.internal.table;

import inetsoft.report.TableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.util.Tool;

import java.util.Iterator;
import java.util.Map;

/**
 * The PamraTableLens is used to store the data of map.
 *
 * @version 10.2, 12/1/2009
 * @author InetSoft Technology Corp
 */
public class ParamTableLens extends AbstractTableLens {
   public static ParamTableLens create(Map<String, Object> param,
                                       TableLens base) {
      ParamTableLens table = new ParamTableLens(param);

      if(base == null) {
         return table;
      }

      String[] headers = table.headers;
      String[] identifiers = new String[headers.length];
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base, true);

      for(int i = 0; i < identifiers.length; i++) {
         // cache is meaningless for this table lens
         int col = Util.findColumn(columnIndexMap, headers[i]);
         String iden = col >= 0 ? base.getColumnIdentifier(col) : null;
         iden = iden == null ? headers[i] : iden;
         identifiers[i] = Util.findIdentifierForSubQuery(base, iden);
      }

      table.setColumnIdentifiers(identifiers);
      return table;
   }

   public ParamTableLens(Map<String, Object> param) {
      super();
      headers = new String[param.size()];
      values = new Object[param.size()];
      Iterator<String> ite = param.keySet().iterator();
      int idx = 0;

      while(ite.hasNext()) {
         headers[idx] = ite.next();

         values[idx] = param.get(headers[idx]);
         idx++;
      }
   }

   public void setColumnIdentifiers(String[] identifiers) {
      this.identifiers = identifiers;
   }

   @Override
   public String getColumnIdentifier(int col) {
      if(col < 0 || identifiers == null || col >= identifiers.length) {
         return null;
      }

      return identifiers[col] == null ? super.getColumnIdentifier(col) :
                                        identifiers[col];
   }

   @Override
   public Object getObject(int r, int c) {
      return r == 0 && c < headers.length ? headers[c] :
         (r == 1 && c < values.length ? values[c] : null);
   }

   public int findColumn(String header) {
      if(header != null && header.length() > 0) {
         for(int i = 0; i < getColCount(); i++) {
            Object val = getObject(0, i);
            String obj = val == null ? null : val.toString();

            if(obj != null && obj.indexOf("(") >= 0 && obj.indexOf(")") >= 0)
            {
               int start = obj.indexOf("(");
               int end = obj.indexOf(")");
               obj = obj.substring(start + 1, end);
            }

            if(Tool.equals(header, obj)) {
               return i;
            }
         }

         if(header.lastIndexOf(".") > 0) {
            header = header.substring(header.lastIndexOf(".") + 1);
            int col = findColumn(header);

            if(!(col < 0)) {
               return col;
            }
         }
      }

      return -1;
   }

   @Override
   public int getColCount() {
      return headers.length;
   }

   @Override
   public int getRowCount() {
      return 2;
   }

   @Override
   public int getHeaderRowCount() {
      return 1;
   }

   private String[] headers;
   private Object[] values;
   private String[] identifiers;
}