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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SQLBoundTableAssembly;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.util.log.LogContext;
import org.slf4j.MDC;

import java.util.*;

/**
 * SQL Bound query executes an SQL bound table assembly.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class SQLBoundQuery extends BoundQuery {
   /**
    * Create an asset query.
    */
   public SQLBoundQuery(int mode, AssetQuerySandbox box, boolean stable, boolean metadata) {
      super(mode, box, stable, metadata);
   }

   /**
    * Create an asset query.
    */
   public SQLBoundQuery(int mode, AssetQuerySandbox box, SQLBoundTableAssembly table,
                        boolean stable, boolean metadata)
      throws Exception
   {
      this(mode, box, stable, metadata);
      this.table = table;
      this.table.update();
      this.nquery = ((SQLBoundTableAssemblyInfo) table.getInfo()).getQuery();
      this.oquery = (JDBCQuery) nquery.clone();
      this.xquery = (JDBCQuery) nquery.clone();

      if(nquery != null) {
         nquery.setName(box.getWSName() + "." +
                        getTableDescription(table.getName()));
      }

      smergeable = oquery != null && XUtil.isQueryMergeable(oquery);
      smergeable = super.isSourceMergeable() && smergeable;
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() throws Exception {
      // For sql edit mode, the sql should not be changed, so all the other actions such as
      // formula and condition should all execute by post. So return false to source mergeable.
      if(((SQLBoundTableAssembly) table).isSQLEdited()) {
         UniformSQL sql = (UniformSQL) nquery.getSQLDefinition();

         // should allow merging if sql is parseable. otherwise sql expression column
         // may fail. (59902)
         // if parsed query doesn't capture all information, don't merge. (59930)
         if(sql.getParseResult() != UniformSQL.PARSE_SUCCESS || sql.isLossy()) {
            return false;
         }

         // if the UniformSQL is not populated, don't merge. (61066)
         if(sql.getBackupSelection().isEmpty() && sql.getSelection().isEmpty() ||
            sql.getTableCount() == 0)
         {
            return false;
         }
      }

      return smergeable;
   }

   @Override
   protected TableLens getPostBaseTableLens(VariableTable vars) throws Exception {
      TableLens tbl = super.getPostBaseTableLens(vars);

      if(tbl != null) {
         ColumnSelection ocols = table.getColumnSelection(false);
         ColumnSelection cols = new ColumnSelection();
         boolean hasData = tbl.moreRows(tbl.getHeaderRowCount());

         // sql bound query column type may not be correctly set if the query contains parameter
         // so the column list is populated from parsed query. since user can't manually set the
         // column type for sql bound query, it's safe to always use the actual type returned
         // from the query.
         for(int i = 0; i < tbl.getColCount(); i++) {
            String name = tbl.getObject(0, i) + "";
            AttributeRef attributeRef = new AttributeRef(name);
            ColumnRef ref = new ColumnRef(attributeRef);
            boolean found = false;

            if(!hasData) {
               // no data, preserve original type if possible
               for(int j = 0; j < ocols.getAttributeCount(); j++) {
                  DataRef oref = ocols.getAttribute(j);

                  if(Objects.equals(oref.getName(), name)) {
                     ref.setDataType(oref.getDataType());
                     found = true;
                     break;
                  }
               }
            }

            if(hasData || !found) {
               ref.setDataType(Tool.getDataType(tbl.getColType(i)));
            }

            cols.addAttribute(ref);
         }

         // preserve any expression columns
         if(cols.getAttributeCount() < ocols.getAttributeCount()) {
            for(int i = 0; i < ocols.getAttributeCount(); i++) {
               DataRef ref = ocols.getAttribute(i);

               if(ref instanceof ColumnRef) {
                  ref = ((ColumnRef) ref).getDataRef();

                  if(ref instanceof ExpressionRef) {
                     boolean found = false;

                     for(int j = 0; j < cols.getAttributeCount(); j++) {
                        if(cols.getAttribute(j).getName().equals(ref.getName())) {
                           found = true;
                           break;
                        }
                     }

                     if(!found) {
                        ref = (ColumnRef) ocols.getAttribute(i).clone();

                        if(i < cols.getAttributeCount()) {
                           cols.addAttribute(i, ref);
                        }
                        else {
                           cols.addAttribute(ref);
                        }
                     }
                  }
               }
            }
         }

         if(cols.getAttributeCount() > 0 && !equalsColumnSelection(ocols, cols)) {
            table.setColumnSelection(cols, false);
         }
      }

      return tbl;
   }

   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      return table instanceof SQLBoundTableAssembly && ((SQLBoundTableAssembly) table).isSQLEdited()
         ? new ColumnSelection() : super.getDefaultColumnSelection0();
   }

   @Override
   protected Collection<?> getLogRecord() {
      if(table != null) {
         String name = Tool.buildString(MDC.get(LogContext.WORKSHEET.name()), ".",
            table.getInfo().getAbsoluteName());
         return Collections.singleton(LogContext.QUERY.getRecord(name));
      }
      else if(xquery != null) {
         String name = getDataSourceLogRecord() + "Embedded";
         return Collections.singleton(LogContext.QUERY.getRecord(name));
      }

      return Collections.emptySet();
   }

   private boolean equalsColumnSelection(ColumnSelection ocols, ColumnSelection ncols) {
      if(ocols.getAttributeCount() != ncols.getAttributeCount()) {
         return false;
      }

      boolean equal = true;

      for(int i = 0; i < ncols.getAttributeCount(); i++) {
         DataRef attribute = ncols.getAttribute(i);

         if(ocols.indexOfAttribute(attribute) < 0) {
            return false;
         }
      }

      return equal;
   }

   private boolean smergeable; // source mergeable
}
