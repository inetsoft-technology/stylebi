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
package inetsoft.uql.asset.internal;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.util.Tool;

import java.util.Enumeration;
import java.util.HashSet;

/**
 * Helper class for getting a list of all base attributes that are
 * referenced by a data REF.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class CompositeColumnHelper {
   /**
    * Constructor.
    */
   public CompositeColumnHelper() {
      super();
   }

   /**
    * Constructor.
    */
   public CompositeColumnHelper(TableAssembly table) {
      this();
      setTable(table);
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    */
   public DataRef[] getAttributes(DataRef col) {
      HashSet<DataRef> cols = new HashSet<>();
      cols.add(col);

      return getAttributes(cols, table, csel);
   }

   /**
    * Get a list of all attributes that are referenced by this object.
    */
   public DataRef[] getAttributes(HashSet<DataRef> cols, TableAssembly table,
                                  ColumnSelection csel) {
      HashSet<DataRef> ncols = new HashSet<>();

      for(DataRef col : cols) {
         col = DataRefWrapper.getBaseDataRef(col);

         if(col instanceof AliasDataRef) {
            col = ((AliasDataRef) col).getDataRef();
         }

         if(col instanceof ExpressionRef) {
            Enumeration<AttributeRef> refs = col.getAttributes();

            while(refs.hasMoreElements()) {
               ncols.add(fixColumn(refs.nextElement(), csel));
            }
         }
         else {
            ncols.add(fixColumn(col, csel));
         }
      }

      cols = ncols;

      if(table instanceof MirrorTableAssembly) {
         table = ((MirrorTableAssembly) table).getTableAssembly();
         ColumnSelection selection = table == null ?
            null : table.getColumnSelection();

         if(table != null && selection != null) {
            ncols = new HashSet<>();

            for(DataRef col : cols) {
               for(int i = 0; i < selection.getAttributeCount(); i++) {
                  DataRef dref = selection.getAttribute(i);
                  DataRef tref = AssetUtil.getOuterAttribute(table.getName(), dref);

                  if(Tool.equals(col, tref)) {
                     ncols.add(dref);
                     break;
                  }
               }
            }

            return getAttributes(ncols, table, table.getColumnSelection());
         }
      }

      DataRef[] res = new DataRef[cols.size()];
      return cols.toArray(res);
   }

   private DataRef fixColumn(DataRef col, ColumnSelection csel) {
      if(csel != null) {
         int idx = csel.indexOfAttribute(col);

         if(idx == -1) {
            for(int i = 0; i < csel.getAttributeCount(); i++) {
               DataRef ncol = csel.getAttribute(i);

               if(equalsName(ncol, col)) {
                  return ncol;
               }
            }
         }
         else {
            return csel.getAttribute(idx);
         }
      }

      return col;
   }

   /**
    * Check if the 2 refs have equal names.
    */
   private boolean equalsName(DataRef col1, DataRef col2) {
      if(col1 == null || col2 == null) {
         return false;
      }

      if(col1.getEntity() != null && col2.getEntity() != null)
      {
         boolean equal = col1.getEntity().equals(col2.getEntity()) &&
            col1.getAttribute().equals(col2.getAttribute());

         if(!equal) {
            String name1 = col1.getName();
            String name2 = col2.getName();

            equal = name1.endsWith(name2) || name2.endsWith(name1);
         }

         return equal;
      }

      return getName(col1).equals(getName(col2));
   }

   /**
    * Get the alias or attribute of the data ref.
    */
   public String getName(DataRef col) {
      String name = col instanceof ColumnRef ? ((ColumnRef) col).getAlias() :
                                                null;
      return name == null || name.length() == 0 ? col.getAttribute() : name;
   }

   /**
    * Set table.
    */
   public void setTable(TableAssembly table) {
      this.table = table;

      if(table != null) {
         this.csel = table.getColumnSelection();
      }
   }

   /**
    * Set column selection.
    */
   public void setColumnSelection(ColumnSelection csel) {
      this.csel = csel;
   }

   private TableAssembly table;
   private ColumnSelection csel;
}
