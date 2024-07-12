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
package inetsoft.uql.util;

import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.BrowseDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * BrowseData encapsulates the logic that retrieves data by ColumnCache. We
 * set the BrowseData object into editors instead of the actual data. When the
 * editor needs to open the browse data window, it can retrieve the data on
 * demand.
 *
 * @author InetSoft Technology Corp
 * @version 6.1, 7/19/2004
 */
public class BrowsedData {
   /**
    * Get a list of distinct values of a field.
    *
    * @param fieldname the fully encoded field name returned from
    *                  getFieldFullName().
    * @return list of distinct values for the field.
    */
   public static BrowseDataModel getColumnData(XQueryRepository qrep,
                                               WorksheetProcessor wsproc,
                                               String fieldname)
      throws Exception
   {
      fieldname = fieldname.trim();
      fieldname = fieldname.substring(1, fieldname.length() - 1);
      ColumnCache cache = ColumnCache.getColumnCache();

      if(fieldname.indexOf("].[") > 0) {
         String[] arr = Tool.split(fieldname, "].[", false);
         return cache.getColumnData(qrep, arr[0], arr[1],
            ThreadContext.getContextPrincipal(), null);
      }
      else if(fieldname.indexOf("]^[") > 0) {
         String[] arr = Tool.split(fieldname, "]^[", false);
         return cache.getColumnData(wsproc, AssetEntry.createAssetEntry(arr[0]),
            arr[1], ThreadContext.getContextPrincipal(),
            null);
      }

      return BrowseDataModel.builder().values(new Object[0]).build();
   }

   /**
    * Constructor.
    */
   public BrowsedData() {
   }

   /**
    * Constructor.
    */
   public BrowsedData(String query, String field, String type, Principal user,
                      VariableTable vars, boolean quotes) {
      this.query = query;
      this.field = field;
      this.type = type;
      this.user = user;
      this.vars = vars;
      this.quotes = quotes;
      sqlAvailable = true;
   }

   /**
    * Constructor.
    */
   public BrowsedData(XQueryRepository qrep, String query, String table,
                      String field, String type, Principal user,
                      VariableTable vars, boolean quotes) {
      this.qrep = qrep;
      this.query = query;
      this.table = table;
      this.field = field;
      this.type = type;
      this.user = user;
      this.vars = vars;
      this.quotes = quotes;
      sqlAvailable = false;
   }

   /**
    * Constructor.
    */
   public BrowsedData(XQuery xquery, String table, String field, String type,
                      Principal user, VariableTable vars, boolean quotes) {
      this.xquery = xquery;
      this.query = xquery == null ? null : xquery.getName();
      this.table = table;
      this.field = field;
      this.type = type;
      this.user = user;
      this.vars = vars;
      this.quotes = quotes;
      sqlAvailable = false;
   }

   public BrowsedData(XQuery xquery, String table, String field, String type, Principal user,
                      VariableTable vars, boolean quotes, boolean fixLiterals)
   {
      this.xquery = xquery;
      this.query = xquery == null ? null : xquery.getName();
      this.table = table;
      this.field = field;
      this.type = type;
      this.user = user;
      this.vars = vars;
      this.quotes = quotes;
      this.fixLiterals = fixLiterals;
      sqlAvailable = false;
   }

   /**
    * Constructor.
    */
   public BrowsedData(WorksheetProcessor proc, AssetEntry asset, String field,
                      String type, Principal user, VariableTable vars,
                      boolean quotes) {
      this.wsproc = proc;
      this.asset = asset;
      this.field = field;
      this.type = type;
      this.user = user;
      this.vars = vars;
      this.quotes = quotes;

      sqlAvailable = false;
   }

   public String[][] getBrowsedData() {
      return getBrowsedData(null, DateRangeRef.NONE_DATE_GROUP, false);
   }

   public String[][] getBrowsedData(Function<Object, String> lbFunc, int option, boolean ignoreEmpty) {
      String[][] data = null;
      user = user == null ? ThreadContext.getContextPrincipal() : user;

      if(!applyVPM) {
         user = null;
      }

      try {
         ColumnCache cache = ColumnCache.getColumnCache();

         if(asset != null) {
            data = cache.getColumnDataString(wsproc, asset, field, type, user,
               vars, quotes, lbFunc, option, ignoreEmpty);
         }
         else if(sqlAvailable) {
            data = cache.getColumnDataString(qrep, query, field, type, user,
               vars, quotes, lbFunc, option);
         }
         else {
            // there are two cases that table name doesn't exist in
            // SQLDefinition of the query. One is new query of Qeury Wizard.
            // Another is adding tables on JDBCTableGraph. They all need
            // compose sql string to get browse data
            data = cache.getColumnDataString(xquery, table, field, type,
               user, vars, quotes, lbFunc, option);

            if(fixLiterals) {
               SQLHelper helper = SQLHelper.getSQLHelper(xquery.getDataSource(), user);

               for(int i = 0; i < data[0].length; i++) {
                  data[0][i] = helper.fixStringLiteral(data[0][i]);
                  data[1][i] = helper.fixStringLiteral(data[1][i]);
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to get browse data", ex);
      }

      return data != null ? data : new String[2][0];
   }

   /**
    * Check if the name is a encoded field name.
    */
   public static boolean isFieldFullName(String name) {
      return name.indexOf("].[") > 0 || name.indexOf("]^[") > 0;
   }

   /**
    * Get variables that need to be set to get data
    */
   public static UserVariable[] getVariables(XSourceInfo sourceInfo, String tableName,
                                             AssetRepository assetRep,
                                             AssetQuerySandbox box, Principal user,
                                             VariableTable vart)
   {
      int type = sourceInfo.getType();

      if(type == XSourceInfo.ASSET) {
         Worksheet ws = null;

         if(box == null) {
            String name = sourceInfo.getSource();
            AssetEntry entry = AssetEntry.createAssetEntry(name);

            if(entry != null) {
               try {
                  ws = (Worksheet)
                     assetRep.getSheet(entry, user, false, AssetContent.ALL);
               }
               catch(Exception e) {
                  LOG.debug("Failed to load a worksheet", e);
                  return null;
               }

               box = new AssetQuerySandbox(ws);
               box.setWSName(entry.getSheetName());
               box.setBaseUser(user);
            }
         }
         else {
            ws = box.getWorksheet();
         }

         if(ws != null) {
            WSAssembly wsAssembly = tableName != null ? (WSAssembly) ws.getAssembly(tableName) :
               ws.getPrimaryAssembly();

            if(hasTableWithSQLEdited((TableAssembly) wsAssembly)) {
               List<UserVariable> list = new ArrayList<>();
               UserVariable[] vars = AssetEventUtil.executeVariables(null, box, null, wsAssembly);

               try {
                  if(vart != null) {
                     for(UserVariable var : vars) {
                        Object value = vart.get(var);

                        // only need to collect the variables which have not setted value.
                        if(value == null) {
                           list.add(var);
                        }
                     }
                  }
                  else {
                     return vars;
                  }
               }
               catch(Exception e) {
                  // do nothing
               }

               return list.size() > 0 ? list.toArray(new UserVariable[list.size()]) : null;
            }
         }
      }

      return null;
   }

   /**
    * Checks if the table is or is composed of a sql table with sql edited which might contain
    * parameters that need to be set in order for the query to run successfully.
    */
   private static boolean hasTableWithSQLEdited(TableAssembly table) {
      if(table instanceof ComposedTableAssembly) {
         TableAssembly[] childTables = ((ComposedTableAssembly) table).getTableAssemblies(false);

         for(TableAssembly childTable : childTables) {
            if(hasTableWithSQLEdited(childTable)) {
               return true;
            }
         }
      }

      return table instanceof SQLBoundTableAssembly &&
         ((SQLBoundTableAssembly) table).isSQLEdited();
   }

   private XQueryRepository qrep;
   private WorksheetProcessor wsproc;

   private String query;
   private XQuery xquery;
   private AssetEntry asset;
   private String table;
   private String field;
   private String type;
   private Principal user;
   private VariableTable vars;
   private boolean sqlAvailable;
   private boolean quotes;
   private boolean fixLiterals;
   private boolean applyVPM = true;

   private static final Logger LOG =
      LoggerFactory.getLogger(BrowsedData.class);
}
