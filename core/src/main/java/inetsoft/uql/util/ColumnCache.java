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
package inetsoft.uql.util;

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.viewsheet.graph.ChartAggregateRef;
import inetsoft.util.*;
import inetsoft.web.composer.model.BrowseDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;

/**
 * ColumnCache store and retrieve column data when user browse data
 * in composer.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ColumnCache {
   /**
    * Get a singleton cache.
    */
   public static ColumnCache getColumnCache() {
      if(cache == null) {
         cache = new ColumnCache();
      }

      return cache;
   }

   /**
    * Format column data for manual sort.
    */
   public static String[][] format(BrowseDataModel browseData, String type,
                                   boolean quotes, Function<Object, String> lbFunc, int option)
   {
      Object[] value;
      Object[] label;

      // is two dimension array? data-description type
      if(browseData.existLabels()) {
         value = browseData.values();
         label = browseData.labels();
      }
      // is one dimension array? (normal type)
      else {
         value = browseData.values();
         label = browseData.values();
      }

      HashMap<Object, Object> map = new HashMap<>();
      boolean dateCol = option != DateRangeRef.NONE_DATE_GROUP;

      // fix date value by dlevel.
      if(dateCol) {
         for(int i = 0; i < label.length; i++) {
            Object lb = label[i] == null ? "" : label[i];
            Object val = value[i] == null ? "" : value[i];

            // for example: querter option, 2022-3-1 -> 2022-1-1
            if(lb instanceof Date) {
               lb = DateRangeRef.getData(option, (Date) lb);
            }

            if(val instanceof Date) {
               val = DateRangeRef.getData(option, (Date) val);
            }

            if(!map.containsKey(lb)) {
               map.put(lb, val);
            }
         }
      }

      Object[] labels = label;

      if(dateCol) {
         Set keys = map.keySet();
         labels = keys.toArray(new Object[keys.size()]);

         // sort date labels. for example: for month of year level,
         // 2020-6-1-> 6
         // 2021-1-1 -> 1
         // should be sorted to: 1, 6
         Comparator comp = new DefaultComparator();
         Arrays.sort(labels, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
               if(o1 == null || "".equals(o1)) {
                  return -1;
               }
               else if(o2 == null || "".equals(o2)) {
                  return 1;
               }

               return comp.compare(o1, o2);
            }
         });
      }

      List<String> llist = new ArrayList<>();
      List<String> vlist = new ArrayList<>();

      for(int i = 0; i < labels.length; i++) {
         Object lb = labels[i];
         Object val = dateCol ? map.get(lb) : value[i];

         // for date labels.
         if(dateCol && lbFunc != null && lb != null) {
            lb = lbFunc.apply(lb);
         }

         boolean stringLabel = lb instanceof String;
         lb = lb == null ? "" : AbstractCondition.getValueString(lb, type);

         // @by watsonn, bug1094716920565
         // also put single quote if it is "oracle.sql.INTERVALYM"
         if(quotes && (stringLabel ||
            label[i] != null && label[i].getClass().getName().equals("oracle.sql.INTERVALYM")))
         {
            lb = "'" + lb + "'";
         }

         boolean stringVal = val instanceof String;
         val = val == null || "".equals(val) ? val : AbstractCondition.getValueString(val, type);

         // @by watsonn, bug1094716920565
         // also put single quote if it is "oracle.sql.INTERVALYM"
         if(quotes && (stringVal ||
            value[i] != null && value[i].getClass().getName().equals("oracle.sql.INTERVALYM")))
         {
            val = "'" + val + "'";
         }

         if(!llist.contains(lb)) {
            llist.add((String) lb);
            vlist.add((String) val);
         }
      }

      String[] vals = vlist.toArray(new String[vlist.size()]);
      String[] lbs = llist.toArray(new String[llist.size()]);

      return new String[][] { vals, lbs };
   }

   /**
    * Initialize column cache.
    */
   private ColumnCache() {
      // delimited by ;, each item as model::entity::attribute or query::column
      String prop = SreeEnv.getProperty("replet.browseData.nocache");

      if(prop != null) {
         if(prop.equalsIgnoreCase("true")) {
            nocache = true;
         }
         else {
            String[] arr = Tool.split(prop, ';');

            for(int i = 0; i < arr.length; i++) {
               int idx = arr[i].indexOf("::");

               if(idx > 0) {
                  excluded.add(getKey(null, arr[i].substring(0, idx),
                                      arr[i].substring(idx + 2), null, null));
               }
            }
         }
      }

      prop = SreeEnv.getProperty("replet.browseData.timeout", "36000000");
      dataTable = new DataCache<>(1000, Integer.parseInt(prop));

      DataSourceRegistry.getRegistry().addRefreshedListener(evt -> dataTable.clear());
   }

   /**
    * Validate a column.
    */
   private String validateColumn(String column) {
      int idx = column.indexOf("::");

      if(idx >= 0) {
         String entity = column.substring(0, idx);
         String attr = column.substring(idx + 2);

         // for model bound table, the header is entity.attribute
         if(entity.equals("null")) {
            int index = attr.lastIndexOf(".");

            if(index > 0) {

               // parse out Geo(entity.attribute)
               final int leftParen = attr.indexOf('(');
               final int rightParen = attr.indexOf(')');

               if(leftParen > -1 && index > leftParen && index < rightParen) {
                  attr = attr.substring(leftParen + 1, rightParen);
                  index = attr.indexOf('.');
               }
            }

            entity = attr.substring(0, index);
            attr = attr.substring(index + 1);

            return entity + "::" + attr;
         }
      }

      column = ChartAggregateRef.getBaseName(column);
      return column;
   }

   /**
    * Get browse data for the specified column.
    * @return array of values, or two arrays (two dimensional, with first
    * dimension having two elements) with value and description list.
    */
   public BrowseDataModel getColumnData(XQueryRepository qrep, String query,
                                        String column, Principal user, VariableTable vars)
      throws Exception
   {
      column = validateColumn(column);

      BrowseDataModel browseData = null;
      String key = getKey(qrep, query, column, user, vars);
      String ekey = getKey(null, query, column, null, null);

      if(!nocache && !excluded.contains(ekey)) {
         browseData = dataTable.get(key);
      }

      if(browseData == null) {
         if(vars == null) {
            vars = new VariableTable();
         }

         browseData = getData(key, qrep, query, column, user, vars);
      }

      if(browseData == null) {
         return BrowseDataModel.builder().values(new Object[0]).build();
      }

      return browseData;
   }

   /**
    * Get browse data for the specified column of new query,
    * which is not in repository.
    * @return array of values, or two arrays (two dimensional, with first
    * dimension having two elements) with value and description list.
    */
   public BrowseDataModel getColumnData(XQuery query, String table, String column,
                                        Principal user, VariableTable vars)
      throws Exception
   {
      column = validateColumn(column);

      BrowseDataModel browseData = null;
      String key = getKey(null, query.getName(), formatColumn(table, column), user, vars);
      String ekey = getKey(null, query.getName(), formatColumn(table, column), null, null);

      if(!nocache && !excluded.contains(ekey)) {
         browseData = dataTable.get(key);
      }

      if(browseData == null) {
         if(vars == null) {
            vars = new VariableTable();
         }

         if(user != null) {
            vars.put("user", user.getName());
         }

         browseData = getQueryData(key, query, table, column, vars, user);
      }

      return browseData;
   }

   /**
    * Get browse data for the specified column of new query,
    * which is not in repository.
    * @return array of values, or two arrays (two dimensional, with first
    * dimension having two elements) with value and description list.
    */
   public BrowseDataModel getColumnData(WorksheetProcessor wsproc, AssetEntry entry,
                                        String column, Principal user,
                                        VariableTable vars)
      throws Exception
   {
      return getColumnData(wsproc, entry, column, user, vars, false);
   }

   /**
    * Get browse data for the specified column of new query,
    * which is not in repository.
    * @return array of values, or two arrays (two dimensional, with first
    * dimension having two elements) with value and description list.
    */
   public BrowseDataModel getColumnData(WorksheetProcessor wsproc, AssetEntry entry,
                                        String column, Principal user,
                                        VariableTable vars, boolean ignoreEmpty)
      throws Exception
   {
      column = validateColumn(column);

      BrowseDataModel val = null;
      String key = getKey(wsproc, entry.toIdentifier(), column, user, vars);
      String ekey = getKey(null, entry.toIdentifier(), column, null, null);

      if(!nocache && !excluded.contains(ekey)) {
         val = dataTable.get(key);
      }

      if(val == null) {
         if(vars == null) {
            vars = new VariableTable();
         }

         if(user != null) {
            vars.put("user", user.getName());
         }

         val = getAssetData(key, wsproc, entry, column, vars, user, ignoreEmpty);
      }

      if(val == null) {
         return BrowseDataModel.builder().values(new Object[0]).build();
      }

      return val;
   }

   /**
    * Get browse data for the specified column.
    *
    * @param lbFunc  the function used to format the date labels.
    * @param option the date level use to group the date values.
    * @return string array of values, or two arrays (two dimensional, with first
    * dimension having two elements) with value and description list.
    */
   public String[][] getColumnDataString(XQueryRepository qrep, String query,
                                         String column, String type,
                                         Principal user, VariableTable vars,
                                         boolean quotes, Function<Object, String> lbFunc, int option)
      throws Exception
   {
      column = validateColumn(column);

      BrowseDataModel result = getColumnData(qrep, query, column, user, vars);
      return format(result, type, quotes, lbFunc, option);
   }

   /**
    * Get browse data for the specified column of new query,
    * which is not in repository.
    * @return string array of values, or two arrays (two dimensional, with first
    * dimension having two elements) with value and description list.
    */
   public String[][] getColumnDataString(XQuery query, String table,
                                       String column, String type,
                                       Principal user, VariableTable vars,
                                       boolean quotes)
      throws Exception
   {
      return getColumnDataString(query, table, column, type, user, vars, quotes, null, DateRangeRef.NONE_DATE_GROUP);
   }

   /**
    * Get browse data for the specified column of new query,
    * which is not in repository.
    *
    * @param lbFunc  the function used to format the date labels.
    * @param option the date level use to group the date values.
    * @return string array of values, or two arrays (two dimensional, with first
    * dimension having two elements) with value and description list.
    */
   public String[][] getColumnDataString(XQuery query, String table,
                                         String column, String type,
                                         Principal user, VariableTable vars,
                                         boolean quotes, Function<Object, String> lbFunc, int option)
      throws Exception
   {
      column = validateColumn(column);

      if(query == null) {
         return new String[0][0];
      }

      BrowseDataModel result = getColumnData(query, table, column, user, vars);
      return format(result, type, quotes, lbFunc, option);
   }

   public String[][] getColumnDataString(WorksheetProcessor wsproc,
                                         AssetEntry entry, String column,
                                         String type, Principal user,
                                         VariableTable vars, boolean quotes,
                                         Function<Object, String> lbFunc, int option,
                                         boolean ignoreEmpty)
      throws Exception
   {
      column = validateColumn(column);
      BrowseDataModel result = getColumnData(wsproc, entry, column, user, vars, ignoreEmpty);

      return format(result, type, quotes, lbFunc, option);
   }

   /**
    * Get a session.
    */
   private Object getSession() {
      try {
         XDataService service = XFactory.getDataService();
         return service.bind(System.getProperty("user.name"));
      }
      catch(Exception e) {
         LOG.error("Failed to get data source", e);
      }

      return null;
   }

   /**
    * Format a column.
    */
   private String formatColumn(String table, String column) {
      if(table == null) {
         return column;
      }

      if(column.startsWith(table + ".")) {
         return column;
      }

      return table + "." + column;
   }

   /**
    * Get a hash key.
    */
   private String getKey(Object qrep, String query, String column,
                         Principal user, VariableTable vars) {
      StringBuilder rolestr = new StringBuilder();

      if(user == null) {
         user = ThreadContext.getContextPrincipal();
      }

      // @by davy, now we support additional connection, here should use user
      // name instead, fix customer bug1379443552753
      if(user != null) {
         rolestr.append(user.getName());
      }

      StringBuilder vs = new StringBuilder();

      if(vars != null) {
         try {
            // add query variables
            Enumeration<String> key = vars.keys();

            while(key.hasMoreElements()) {
               String vname = key.nextElement();

               // skip __service_request__ and __service_response__
               if(!(vname.startsWith("__") && vname.endsWith("__")) &&
                  !vname.equals("_ROLES_") && !vname.equals("_USER_") &&
                  !vname.equals("_GROUPS_") && !vname.equals("user"))
               {
                  vs.append("[" + vname + "=" + vars.get(vname) + "]");
               }
            }
         }
         catch(Exception e) {
         }
      }

      if(qrep != null) {
         return qrep.hashCode() + "," + query + "," + column + "," + rolestr +
            "," + vs.toString();
      }
      else {
         return query + "," + column + "," + rolestr + "," + vs.toString();
      }
   }

   /**
    * Get column data from database
    * @param qrep - query repository
    * @param query - query name
    * @param column - column name
    * @param user - user
    * @param vars - variable table
    * @return data vector
    */
   private BrowseDataModel getData(String key, XQueryRepository qrep, String query,
                                   String column, Principal user, VariableTable vars)
      throws Exception
   {
      int idx = column.indexOf("::");
      int idx2 = query.indexOf("::");

      if(user != null) {
         vars.put("user", user.getName());
      }

      if(idx >= 0 && idx2 >= 0) {
         String entity = column.substring(0, idx);
         String attr = column.substring(idx + 2);

         String ds = query.substring(idx2 + 2);
         String lmodel = query.substring(0, idx2);

         if(lmodel.equals("null")) {
            lmodel = null;
         }

         return getModelData(key, ds, lmodel, entity, attr, vars, user);
      }

      return null;
   }

   /**
    * Get column data from database.
    *
    * @param dsname the data source name
    * @param lname the logic model name
    * @param ename the entity name
    * @param aname the attribute name
    * @param vars the variable table
    */
   private BrowseDataModel getModelData(String key, String dsname, String lname, String ename,
                                        String aname, VariableTable vars, Principal user)
      throws Exception
   {
      XRepository xrep = XFactory.getRepository();
      XDataModel model = xrep.getDataModel(dsname);
      Object session = getSession();

      if(model == null) {
         LOG.error("Data model is not found: " + dsname);
         return null;
      }

      XDataSource xds = xrep.getDataSource(model.getDataSource());

      if(xds == null) {
         LOG.error("Datasource is not found: " + model.getDataSource());
         return null;
      }

      if(ename == null || ename.equals("")) {
         // it's a formula and by default formulas are non-browseable
         return null;
      }

      XNode data = XAgent.getAgent(xds).getModelData(
         model, lname, ename, aname, vars, session, user);

      if(data != null) {
         return addXNode(key, null, lname + "::" + dsname, ename + "::" + aname, data, vars);
      }

      return null;
   }

   /**
    * Get column data from database
    * @param xquery - xquery
    * @param table - table name
    * @param column - column name
    * @param vars - variable table
    */
   private BrowseDataModel getQueryData(String key, XQuery xquery, String table,
                                        String column, VariableTable vars,
                                        Principal user) throws Exception
   {
      XDataSource xds = xquery.getDataSource();
      Object session = getSession();
      XNode data = XAgent.getAgent(xds).getQueryData(xquery, table, column, vars, session, user);
      String query = xquery.getName();

      return addXNode(key, null, query, formatColumn(table, column), data, vars);
   }

   /**
    * Get column data from database
    * @param wsproc worksheet processor.
    * @param entry worksheet entry.
    * @param column - column name
    * @param vars - variable table
    */
   private BrowseDataModel getAssetData(String key, WorksheetProcessor wsproc,
                                        AssetEntry entry, String column,
                                        VariableTable vars, Principal user, boolean ignoreEmpty)
      throws Exception
   {
      vars.put("__browsed__", "true");
      vars.put("__column__", column);
      XTable tbl = wsproc.execute(entry, vars, user);
      int col = -1;
      int icol = -1; // identifier col
      int pcol = -1; // partial col

      for(int i = 0; i < tbl.getColCount(); i++) {
         if(column.equals(tbl.getObject(0, i))) {
            col = i;
            break;
         }

         String identifier = tbl.getColumnIdentifier(i);

         if(column.equals(identifier)) {
            icol = i;
         }

         int index = column.lastIndexOf(".");

         if(index >= 0) {
            String column2 = column.substring(index + 1);

            if(column2.equals(tbl.getObject(0, i))) {
               pcol = i;
            }
         }
      }

      col = col < 0 && icol >= 0 ? icol : col;
      col = col < 0 && pcol >= 0 ? pcol : col;

      Object[] res = new Object[0];
      final BrowseDataModel.Builder browseDataBuilder = BrowseDataModel.builder();

      if(col >= 0) {
         Set<Object> values = new HashSet<>();
         int max = 1000;

         try {
            final Object maxValue;

            if(vars.contains(XQuery.HINT_MAX_ROWS)) {
               maxValue = vars.get(XQuery.HINT_MAX_ROWS);
            }
            else {
               maxValue = entry.getProperty(XQuery.HINT_MAX_ROWS);
            }

            max = Integer.parseInt((String) maxValue);
            // Feature #39140, always respect the global row limit
            max = Util.getQueryLocalRuntimeMaxrow(max);
         }
         catch(Exception ignore) {
         }

         int flag = 0;

         for(int i = tbl.getHeaderRowCount(); flag < max && tbl.moreRows(i); i++) {
            Object val = tbl.getObject(i, col);

            if(!values.contains(val) && (val != null || !ignoreEmpty)) {
               values.add(val);
               flag++;
            }
         }

         if(flag == max) {
            browseDataBuilder.dataTruncated(true);
         }

         List<Object> vec = new ArrayList<>(values);
         vec.sort(comp);
         res = vec.toArray();
      }

      final BrowseDataModel browseData = browseDataBuilder.values(res).build();
      String nkey = getKey(wsproc, entry.toIdentifier(), column, user, vars);

      if(nkey.equals(key)) {
         dataTable.put(nkey, browseData);
      }

      return browseData;
   }

   /**
    * Add xnode result to cache.
    */
   private BrowseDataModel addXNode(String key, XQueryRepository qrep, String query,
                                    String column, XNode result, VariableTable vars)
   {
      // we are only dealing with jdbc, it's always XTableNode
      XTableNode table = (XTableNode) result;
      int ncol = 0;

      // @by galr, the result will be null for non jdbc data sources who doesn't
      // have an associated Agent (agent tag in inetsoft.uql.config.xml)
      if(table != null) {
         ncol = table.getColCount();
      }

      List<Object> pairs = new ArrayList<>(); // [value, label]
      List<Object> valList = new ArrayList<>();

      while(table != null && table.next()) {
         Object data = table.getObject(0);
         Object[] pair = new Object[Math.min(ncol, 2)];
         pair[0] = data;

         if(ncol > 1) {
            pair[1] = table.getObject(1);
         }

         if(!valList.contains(data)) {
            pairs.add(pair);
            valList.add(data);
         }
      }

      pairs.sort(comp2);

      List<Object> values = new ArrayList<>(); // value list
      List<Object> descs = new ArrayList<>(); // description list

      for(int i = 0; i < pairs.size(); i++) {
         Object[] pair = (Object[]) pairs.get(i);

         values.add(pair[0]);

         if(pair.length > 1) {
            descs.add(pair[1]);
         }
      }

      Object[] varr = values.toArray();
      final BrowseDataModel.Builder browseDataBuilder = BrowseDataModel.builder().values(varr);

      if(ncol > 1) {
         browseDataBuilder.labels(descs.toArray());
      }

      String nkey = getKey(qrep, query, column, null, vars);
      final BrowseDataModel browseData = browseDataBuilder.build();

      if(nkey.equals(key)) {
         dataTable.put(nkey, browseData);
      }

      return browseData;
   }

   /**
    * Clear cached data.
    */
   public void clear() {
      dataTable.clear();
   }

   private final Comparator<Object> comp = (a, b) -> {
      if(a instanceof Comparable) {
         if(b == null) {
            return 1;
         }

         //noinspection unchecked,rawtypes
         return ((Comparable) a).compareTo(b);
      }
      else {
         return Tool.compare(a, b);
      }
   };

   private final Comparator<Object> comp2 = (a, b) -> {
      Object[] p1 = (Object[]) a;
      Object[] p2 = (Object[]) b;
      Object v1 = (p1.length > 1) ? p1[1] : p1[0];
      Object v2 = (p2.length > 1) ? p2[1] : p2[0];

      return comp.compare(v1, v2);
   };

   private final DataCache<String, BrowseDataModel> dataTable;
   private final Set<String> excluded = new HashSet<>(); // excluded keys
   private boolean nocache = false; // no caching of data
   private static ColumnCache cache = null;

   private static final Logger LOG = LoggerFactory.getLogger(ColumnCache.class);
}
