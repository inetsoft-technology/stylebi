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
package inetsoft.uql.jdbc.util;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.report.internal.Util;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.erm.XPartition.PartitionTable;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.web.admin.content.database.*;
import inetsoft.web.admin.content.database.types.*;
import inetsoft.web.composer.model.ws.BasicSQLQueryModel;
import inetsoft.web.composer.model.ws.SQLQueryDialogModel;
import inetsoft.web.portal.controller.database.QueryGraphModelService;
import inetsoft.web.portal.model.database.*;

import java.awt.*;
import java.security.Principal;
import java.sql.Statement;
import java.util.List;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC utility functions.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class JDBCUtil {
   /**
    * Get the uppercase name.
    * @param tnode the specified table node.
    * @param name the specified table name.
    * @param sql the specified uniform sql.
    * @return the uppercase name.
    */
   private static String getUpperCaseName(XNode tnode, String name,
                                          UniformSQL sql) {
      String catalog = (String) tnode.getAttribute("catalog");
      StringBuilder sb = new StringBuilder();
      String separator = (String) tnode.getAttribute("catalogSep");
      separator = separator == null ? "." : separator;
      SQLHelper helper = SQLHelper.getSQLHelper(sql);

      if(catalog != null && name.startsWith(catalog + separator)) {
         if(XUtil.isSpecial(catalog, helper)) {
            sb.append(catalog);
         }
         else {
            sb.append(catalog.toUpperCase());
         }

         sb.append(separator);
         name = name.substring(catalog.length() + 1);
      }

      String schema = (String) tnode.getAttribute("schema");

      if(schema != null && name.startsWith(schema + ".")) {
         if(XUtil.isSpecial(schema, helper)) {
            sb.append(schema);
         }
         else {
            sb.append(schema.toUpperCase());
         }

         sb.append(".");
         name = name.substring(schema.length() + 1);
      }

      if(XUtil.isSpecialName(name, true, helper)) {
         sb.append(name);
      }
      else {
         sb.append(name.toUpperCase());
      }

      return sb.toString();
   }

   /**
    * Fix the xselection in the UniformSQL by connecting the database.
    */
   public static void fixUniformSQLSelection(SQLDefinition osql, SQLDefinition nsql) {
      if(osql == null || nsql == null) {
         return;
      }

      XSelection osel = osql.getSelection();
      XSelection nsel = nsql.getSelection();

      for(int i = 0; i < osel.getColumnCount(); i++) {
         String column = osel.getColumn(i);
         String alias = osel.getAlias(i);
         XMetaInfo meta = osel.getXMetaInfo(i);
         String col = alias != null ? alias : column;

         int idx = nsel.indexOfColumn(col);
         nsel.setXMetaInfo(idx, meta);
      }
   }

   /**
    * Fix the information in the UniformSQL by connecting the database.
    */
   public static void fixUniformSQLInfo(UniformSQL sql, XRepository repository,
                                        Object session, JDBCDataSource xds)
      throws Exception
   {
      fixUniformSQLInfo(sql, repository, session, xds, null);
   }

   /**
    * Fix the information in the UniformSQL by connecting the database.
    */
   public static void fixUniformSQLInfo(UniformSQL sql, XRepository repository, Object session,
                                        JDBCDataSource xds, Principal principal)
      throws Exception
   {
      synchronized(sql) {
         if(sql.getTableCount() <= 0 || sql.getFieldList().length > 0) {
            fixSelectionInfo(sql);
            sql.syncTable();
            return;
         }
      }

      // for oracle, table name should be in upper case
      // @comment davidd 2008-01-09
      // Oracle supports lowercase names when quoted
      // ie. select * from "lowercaseTable"
      // Discussed with larryl, deemed not critical
      XNode mtype = new XNode();
      mtype.setAttribute("type", "DBPROPERTIES");
      XNode root = repository.getMetaData(session, xds, mtype, true, null);
      String datasourceType = Config.getJDBCType(xds.getDriver());

      if(datasourceType.equalsIgnoreCase("oracle")) {
         SelectTable[] tables = sql.getSelectTable();
         sql.removeAllTables();

         for(int i = 0; i < tables.length; i++) {
            String alias = tables[i].getAlias();
            Object name = tables[i].getName();
            Point loc = tables[i].getLocation();
            Point scroll = tables[i].getScrollLocation();

            if(!(name instanceof String)) {
               sql.addTable(alias, name, loc, scroll);
               continue;
            }

            XNode tnode = SQLTypes.getSQLTypes(xds).getQualifiedTableNode(
               name.toString(),
               "true".equals(root.getAttribute("hasCatalog")),
               "true".equals(root.getAttribute("hasSchema")),
               (String) root.getAttribute("catalogSep"), xds,
               tables[i].getCatalog(), tables[i].getSchema());
            name = getUpperCaseName(tnode, name.toString(), sql);
            SelectTable ntable = sql.addTable(alias, name, loc, scroll);

            // table already exists
            if(ntable == null) {
               continue;
            }

            ntable.setCatalog(tables[i].getCatalog());
            ntable.setSchema(tables[i].getSchema());
         }
      }

      // add XFields
      try {
         for(int i = 0; i < sql.getTableCount(); i++) {
            String alias = sql.getTableAlias(i);
            Object name = sql.getTableName(alias);
            SelectTable stable = sql.getSelectTable(i);
            XNode table;

            if(name instanceof UniformSQL) {
               UniformSQL sql1 = (UniformSQL) name;
               fixUniformSQLInfo(sql1, repository, session, xds);
               XSelection xSelects = sql1.getSelection();

               for(int j = 0; j < xSelects.getColumnCount(); j++) {
                  String calias = xSelects.getAlias(j);
                  String cname = xSelects.getColumn(j);

                  if(calias != null && calias.length() > 0 && !calias.equals(cname)) {
                     cname = calias;
                  }

                  cname = XUtil.getSubQueryColumn(cname, sql1, null);
                  sql.addField(new XField(null, cname, alias));
               }
            }
            else {
               table = SQLTypes.getSQLTypes(xds).getQualifiedTableNode(
                  name.toString(),
                  "true".equals(root.getAttribute("hasCatalog")),
                  "true".equals(root.getAttribute("hasSchema")),
                  (String) root.getAttribute("catalogSep"), xds,
                  stable.getCatalog(), stable.getSchema());
               String sourceName = SQLTypes.getSQLTypes(xds).getQualifiedName(table, xds);
               String dataSourceName = sql.getDataSource() != null ?
                  sql.getDataSource().getFullName() : null;
               BiFunction<String, String, Boolean> vpmHiddenCols = VpmProcessor.getInstance()
                  .getHiddenColumnsSelector(
                     new String[] { sourceName }, new String[0], dataSourceName,
                     null, null, principal);
               table.setAttribute("supportCatalog", root.getAttribute("supportCatalog"));
               XTypeNode cols = getTableColumns(table, repository, session, xds);

               for(int j = 0; j < cols.getChildCount(); j++) {
                  XTypeNode colnode = (XTypeNode) cols.getChild(j);
                  String cname = colnode.getName();

                  if(vpmHiddenCols.apply(sourceName, cname)) {
                     continue;
                  }

                  XField field = new XField(cname, cname, alias, colnode.getType());
                  sql.addField(field);
               }
            }
         }
      }
      catch(Exception e) {
         String msg = e.getMessage() +
            " -- this may be a result of one of the following;\n" +
            "1. You are using a Microsoft Access datasource that is " +
            "configured to require a login.\n2. The wrong ODB driver is " +
            "installed for your database.\n3. The network connection has " +
            "been terminated.\n4. The database is no longer running.";
         LOG.error(msg, e);
      }

      generateXFieldsFromSelection(sql);

      // fix selection info
      fixSelectionInfo(sql);

      // expand "*"
      expandAsterisk(sql);
      fixWhereInfo(sql);
      sql.syncTable();
   }

   /**
    * Clear the table meta cache.
    */
   public static void clearTableMeta() {
      tablemeta.clear();
   }

   /**
    * Get the table columns description.
    */
   public static XTypeNode getTableColumns(XNode table, XRepository repository,
                  Object session, XDataSource xds)
      throws Exception
   {
      String key = xds.getFullName();

      if(xds instanceof JDBCDataSource &&
         ((JDBCDataSource) xds).getBaseDatasource() != null)
      {
         key = ((JDBCDataSource) xds).getBaseDatasource().getFullName() +
            "__" + key;
      }

      Pair p1 = new Pair(table, key);
      XTypeNode cols = (XTypeNode) tablemeta.get(p1);

      if(cols == null) {
         XNode meta = repository.getMetaData(session, xds, table, true, null);

         if(meta instanceof XTypeNode) {
            XNode thisResult = meta.getNode("this.Result");
            cols = thisResult instanceof XTypeNode ? (XTypeNode) thisResult : null;
         }

         cols = cols == null ? new XTypeNode() : cols;
         tablemeta.put(p1, cols);
      }

      return cols;
   }

   /**
    * Generate the XFields from the selection.
    * This function is called after the XFields from selected tables already
    * generated, so table and column information can be retrieved.
    */
   private static void generateXFieldsFromSelection(UniformSQL sql) {
      XSelection select = sql.getSelection();

      for(int i = 0; i < select.getColumnCount(); i++) {
         String path = select.getColumn(i);
         XField field = sql.getFieldByPath(path);

         if(field == null && !path.endsWith("*") &&
            !path.equals(select.getAlias(i)))
         {
            // is expression not exists in field list and have alias
            field = new XField(select.getAlias(i), path, "",
                               select.getType(path));
            sql.addField(field);
         }
         else if(field != null) {
            field.setAlias(select.getAlias(i));
         }
      }
   }

   /**
    * Fix some selection info like full path, type which may not come from the
    * parser.
    */
   public static void fixSelectionInfo(UniformSQL sql) {
      JDBCSelection xselect = (JDBCSelection) sql.getSelection();

      for(int i = 0; i < xselect.getColumnCount(); i++) {
         String path = xselect.getColumn(i);
         String alias = xselect.getAlias(i);
         String fp = getFullPathOf(sql, path);

         if(fp != null) {
            path = fp;
         }

         XField field = sql.getFieldByPath(path);

         if(field != null && field.getTable().length() > 0) {
            xselect.setColumn(i, path);
            xselect.setAlias(i, alias);
            xselect.setTable(path, field.getTable());

            // get type
            if(xselect.getType(path) == null) {
               xselect.setType(path, field.getType());
            }
         }
      }
   }

   /**
    * Get full path of column.
    * @param sql Uniform SQL object
    * @param path column name
    */
   public static String getFullPathOf(UniformSQL sql, String path) {
      String res = null;
      int idx = path.lastIndexOf('.');
      String table, col;

      if(idx > 0) {
         table = path.substring(0, idx);
         int tableIdx = sql.getTableIndex(table);

         if(tableIdx < 0) {
            // is not a column
            return null;
         }

         String newTable = sql.getTableAlias(tableIdx);

         if(!newTable.equals(table)) {
            // format table case
            table = newTable;
         }

         col = path.substring(idx + 1);
      }
      else {
         table = sql.findTableForColumn(path);

         if(table.length() == 0) {  // is not a column from table
            return null;
         }

         col = path;
      }

      res = table + "." + col;
      XField field = sql.getFieldByPath(res);

      if(field != null && field.getTable().length() > 0) {
         if(!field.getName().equals(col)) {
            // format column case
            res = table + "." + field.getName();
         }
      }

      return res;
   }

   /**
    * Expand "*" in the selection
    */
   public static void expandAsterisk(UniformSQL sql) {
      JDBCSelection select = (JDBCSelection) sql.getSelection();
      JDBCSelection newSelect = new JDBCSelection();

      for(int i = 0; i < select.getColumnCount(); i++) {
         String path = select.getColumn(i);

         if(path.equals("*")) {
            XField[] fields = sql.getFieldList();

            for(int j = 0; j < fields.length; j++) {
               String table = fields[j].getTable();

               if(table.length() > 0) {
                  path = table + "." + fields[j].getName();

                  if(select.contains(path)) {
                     continue;
                  }

                  newSelect.addColumn(path);
                  newSelect.setType(path, fields[j].getType());
                  newSelect.setTable(path, table);
               }
            }

            continue;
         }
         else if(path.endsWith(".*")) {
            String table = path.substring(0, path.length() - 2);

            if(sql.getTableIndex(table) >= 0) {
               XField[] fields = sql.getFieldList();

               for(int j = 0; j < fields.length; j++) {
                  if(table.equalsIgnoreCase(fields[j].getTable())) {
                     path = fields[j].getTable() + "." + fields[j].getName();

                     if(select.contains(path)) {
                        continue;
                     }

                     newSelect.addColumn(path);
                     newSelect.setType(path, fields[j].getType());
                     newSelect.setTable(path, table);
                  }
               }

               continue;
            }
         }

         newSelect.addColumn(path);

         int aidx = newSelect.getColumnCount() - 1;
         newSelect.setAlias(aidx, select.getAlias(i));
         newSelect.setType(path, select.getType(path));
         newSelect.setDescription(path, select.getDescription(path));
         newSelect.setTable(path, select.getTable(path));
         newSelect.setXMetaInfo(aidx, select.getXMetaInfo(i));
      }

      sql.setSelection(newSelect);
   }

   /**
    * Fix the info in Where clause of UniformSQL.
    */
   public static void fixWhereInfo(UniformSQL sql) {
      XJoin[] joins = sql.getJoins();

      if(joins != null) {
         HashSet<XJoin> hash = new HashSet<>();

         for(int i = 0; i < joins.length; i++) {
            hash.add(joins[i]);
         }

         sql.setWhere(fixFakeJoins(sql, sql.getWhere(), hash));
      }
   }

   /**
    * Normalize an expression.
    */
   private static void normalizeExpression(XExpression exp, UniformSQL sql) {
      String ex1 = exp.toString();
      String fullPath1 = getFullPathOf(sql, ex1);

      if(fullPath1 != null) {
         String type = sql.isTableColumn(fullPath1) ? XExpression.FIELD :
            exp.getType();
         exp.setValue(fullPath1, type);
      }
   }

   /**
    * Change all fake joins to XBinaryCondition.
    */
   private static XFilterNode fixFakeJoins(UniformSQL sql, XFilterNode root,
                                           HashSet<XJoin> hash) {
      if(root instanceof XSet) {
         for(int i = 0; i < ((XSet) root).getChildCount(); i++) {
            XFilterNode node = (XFilterNode) root.getChild(i);
            root.setChild(i, fixFakeJoins(sql, node, hash));
         }
      }
      else if(root instanceof XJoin && !hash.contains(root)) {
         XBinaryCondition newNode = new XBinaryCondition(
            ((XJoin) root).getExpression1(),
            ((XJoin) root).getExpression2(), ((XJoin) root).getOp());

         newNode.setName(root.getName());
         fixFakeJoins(sql, newNode, hash);
         return newNode;
      }
      else if(root instanceof XUnaryCondition) {
         XExpression exp1 = ((XUnaryCondition) root).getExpression1();
         normalizeExpression(exp1, sql);
      }
      else if(root instanceof XBinaryCondition) {
         XExpression exp1 = ((XBinaryCondition) root).getExpression1();
         normalizeExpression(exp1, sql);
         XExpression exp2 = ((XBinaryCondition) root).getExpression2();
         normalizeExpression(exp2, sql);
      }
      else if(root instanceof XTrinaryCondition) {
         XExpression exp1 = ((XTrinaryCondition) root).getExpression1();
         normalizeExpression(exp1, sql);
         XExpression exp2 = ((XTrinaryCondition) root).getExpression2();
         normalizeExpression(exp2, sql);
         XExpression exp3 = ((XTrinaryCondition) root).getExpression3();
         normalizeExpression(exp3, sql);
      }

      return root;
   }

   /**
    * Get all the tables referenced in the condition.
    */
   public static Set<String> getTables(XFilterNode root) {
      // @by larryl, use ordered set so the order is deterministic
      Set<String> tables = new OrderedSet<>();
      getTables0(root, tables);

      return tables;
   }

   private static void getTables0(XFilterNode root, Set<String> tables) {
      if(root instanceof XBinaryCondition) {
         XBinaryCondition cond = (XBinaryCondition) root;

         getTable(cond.getExpression1(), tables);
         getTable(cond.getExpression2(), tables);
      }
      else if(root instanceof XTrinaryCondition) {
         XTrinaryCondition cond = (XTrinaryCondition) root;

         getTable(cond.getExpression1(), tables);
         getTable(cond.getExpression2(), tables);
         getTable(cond.getExpression3(), tables);
      }
      else if(root instanceof XUnaryCondition) {
         XUnaryCondition cond = (XUnaryCondition) root;

         getTable(cond.getExpression1(), tables);
      }
      else if(root != null) {
         for(int i = 0; i < root.getChildCount(); i++) {
            XNode child = root.getChild(i);

            if(child instanceof XFilterNode) {
               getTables0((XFilterNode) child, tables);
            }
         }
      }
   }

   /**
    * Get columns info of specifed table.
    * @param path table path
    * @param query query object
    * @return XTypeNode list
    */
   public static XTypeNode[] getTableColumns(String path, XQuery query,
                                             Object session) {
      XDataSource ds = query == null ? null : query.getDataSource();
      XTypeNode[] result = getTableColumns(path, ds, session);

      if(result.length != 0) {
         return result;
      }

      if(query.getPartition() != null) {
         XDataModel model =
            DataSourceRegistry.getRegistry().getDataModel(ds.getFullName());
         XPartition partition = model.getPartition(query.getPartition());
         PartitionTable view = partition.getPartitionTable(path);

         if(view != null) {
            return view.getColumns();
         }
      }

      return new XTypeNode[0];
   }

   /**
    * Get columns info of specifed table.
    * @param path - table path
    * @return XTypeNode list
    */
   public static XTypeNode[] getTableColumns(String path, XDataSource xds,
                                             Object session)
   {
      ArrayList<XTypeNode> list = new ArrayList<>();
      String table, schema, catalog;
      table = schema = catalog = null;
      String[] array = Tool.splitWithQuote(path, ".", '\\');
      boolean supportCatalog = false;

      try {
         table = array[array.length - 1];
         schema = array[array.length - 2];
         catalog = array[array.length - 3];
         supportCatalog = true;
      }
      catch(Exception ex) {
         // ignore it
      }

      if(table != null && table.length() > 0) {
         try {
            XRepository repository = XFactory.getRepository();
            XNode mtype = new XNode(table);

            // get cols for schema.catalog.table or catalog.table
            mtype.setAttribute("schema", schema);
            mtype.setAttribute("catalog", catalog);
            mtype.setAttribute("supportCatalog", supportCatalog + "");
            XNode cols = repository.getMetaData(session, xds, mtype, true,null);
            cols = cols.getChild("Result");

            // failed? try getting cols for catalog.schema.table or schema.table
            if(cols == null || cols.getChildCount() == 0) {
               mtype.setAttribute("schema", catalog);
               mtype.setAttribute("catalog", schema);
               mtype.setAttribute("supportCatalog", supportCatalog + "");
               cols = repository.getMetaData(session, xds, mtype, true, null);
               cols = cols.getChild("Result");
            }

            if(cols != null) {
               for(int i = 0; i < cols.getChildCount(); i++) {
                  list.add((XTypeNode) cols.getChild(i));
               }
            }
         }
         catch(Exception e) {
            LOG.error("Failed to get meta-data for table: " + table, e);
         }
      }

      return (XTypeNode[]) list.toArray(new XTypeNode[list.size()]);
   }

   /**
    * Get all tables which have link to this query's tables.
    * @return table aliases and the real physical table.
    */
   public static Map<?, ?> getAllRelatedTables(JDBCQuery query) {
      Map<Object, Object> map = new HashMap<>();
      SQLDefinition sql = query.getSQLDefinition();

      if(sql instanceof UniformSQL) {
         UniformSQL usql = (UniformSQL) sql;

         if(usql.getParseResult() == UniformSQL.PARSE_SUCCESS) {
            try {
               XDataModel model = XFactory.getRepository().getDataModel(
                  query.getDataSource().getFullName());

               if(model != null) {
                  String[] tbls = new String[usql.getTableCount()];

                  for(int i = 0; i < usql.getTableCount(); i++) {
                    tbls[i] =
                       usql.getTableName(usql.getTableAlias(i)).toString();
                  }

                  XPartition partition =
                     model.getPartition(query.getPartition());

                  if(partition != null) {
                     partition = partition.applyAutoAliases();

                     for(int i = 0; i < tbls.length; i++) {
                        JDBCModelHandler.findRelatedTables(tbls[i], map,
                                                           partition);
                     }
                  }
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to get related tables in partition", ex);
            }
         }
      }

      return map;
   }

   /**
    * Find a partition in the model that contains all tables in the list.
    * The partition is a runtime partition which already applied auto alias.
    */
   public static XPartition findPartitionContaining(XDataModel model, String[] tbls) {
      XPartition partition = null;

      // find a partition that contains all tables in the query
      for(String pname : model.getPartitionNames()) {
         XPartition part = model.getPartition(pname);
         boolean notfound = false;

         for(int i = 0; i < tbls.length; i++) {
            if(!part.containsTable(tbls[i])) {
               notfound = true;
               break;
            }
         }

         if(!notfound) {
            partition = part;
            break;
         }
      }

      if(partition != null) {
         partition = partition.applyAutoAliases();
      }

      return partition;
   }

   /**
    * Check if a column is an aggregate expression.
    */
   public static boolean isAggregateExpression(String exp) {
      exp = exp.toLowerCase().trim();

      for(int i = 0; i < aggregateFunction.length; i++) {
         int idx = exp.indexOf(aggregateFunction[i]);

         if(idx >= 0 && exp.length() > (idx  + aggregateFunction[i].length()) &&
            exp.charAt(idx + aggregateFunction[i].length()) == '(' &&
            exp.charAt(exp.length() -1) == ')') {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if table is used in the expression.
    */
   private static void getTable(XExpression expr, Set<String> tables) {
      Object val = expr.getValue();

      if(expr.getType().equals(XExpression.FIELD) && val instanceof String) {
         String str = val.toString();
         int dot = str.lastIndexOf('.');

         if(dot > 0) {
            tables.add(str.substring(0, dot));
         }
      }
   }

   /**
    * Check if this exception is a user cancel exception.
    */
   public static boolean isCancelled(Exception ex, Statement stmt) {
      boolean cancelled = false;

      if(ex != null) {
         String msg = ex.getMessage();

         if(msg != null) {
            cancelled = msg.indexOf("ORA-01013:") >= 0 ||
               msg.indexOf("ORA-01009:") >= 0 ||
               msg.indexOf("ORA-01002:") >= 0;
         }
      }

      if(stmt != null && !cancelled) {
         cancelled = QueryManager.isCancelled(stmt);
      }

      return cancelled;
   }

   static class Pair {
      public Pair(Object o1, Object o2) {
         this.o1 = o1;
         this.o2 = o2;
      }

      @Override
      public boolean equals(Object o) {
         if(o instanceof Pair) {
            Pair po = (Pair) o;
            return Tool.equals(o1, po.o1) &&
               Tool.equals(o2, po.o2);
         }

         return false;
      }

      @Override
      public int hashCode() {
         return (o1 == null ? 0 : o1.hashCode()) +
            (o2 == null ? 0 : o2.hashCode());
      }

      Object o1;
      Object o2;
   }

   /**
    * Get the sql server version.
    */
   public static String getSQLServerVersion(JDBCDataSource dx) {
      if(dx == null) {
         return null;
      }

      String url = dx.getURL();

      if(url.indexOf("jdbc:microsoft:sqlserver") >= 0) {
         return JDBCDataSource.SQLSERVER + "2000";
      }
      else if(url.indexOf("jdbc:sqlserver") >= 0) {
         return JDBCDataSource.SQLSERVER + "2005";
      }
      else {
         return null;
      }
   }

   /**
    * Get JDBC database type.
    * @param type type
    * @return DatabaseType. <tt>null</tt> if not fount.
    */
   public static DatabaseType getJDBCDatabaseType(String type) {
      return databaseTypes.stream()
         .filter(dbType -> dbType.getType().equals(type))
         .findFirst().orElse(null);
   }

   /**
    * Finds the database type that supports the specific driver class.
    * @param driverClass the fully-qualified class name of the JDBC driver.
    * @return the matching database type.
    */
   public static DatabaseType<?> getDatabaseTypeForDriver(String driverClass) {
      return databaseTypes.stream()
         .filter(dbType -> dbType.supportsDriverClass(driverClass) &&
            !CustomDatabaseType.TYPE.equals(dbType.getType())
         )
         .findFirst().orElse(getJDBCDatabaseType(CustomDatabaseType.TYPE));
   }

   /**
    * Build Database Definition.
    * @return DatabaseDefinition
    */
   public static DatabaseDefinition buildDatabaseDefinition(JDBCDataSource dataSource) {
      String driver = dataSource.getDriver();
      DatabaseType type;

      if(dataSource.isCustom()) {
         type = getJDBCDatabaseType(CustomDatabaseType.TYPE);
      }
      else {
         type = getDatabaseTypeForDriver(driver);
      }

      return buildDatabaseDefinition(dataSource, type);
   }

   /**
    * Build Database Definition.
    * @param jdbcDataSource JDBCDataSource
    * @param type jdbc driver type.
    * @return DatabaseDefinition
    */
   public static DatabaseDefinition buildDatabaseDefinition(JDBCDataSource jdbcDataSource,
                                                            DatabaseType type)
   {
      DatabaseDefinition result = new DatabaseDefinition();

      String url = jdbcDataSource.getURL();
      String driver = jdbcDataSource.getDriver();

      result.setName(jdbcDataSource.getName());
      result.setOldName(jdbcDataSource.getName());
      result.setType(type.getType());
      result.setDescription(jdbcDataSource.getDescription());

      result.setAuthentication(new AuthenticationDetails());
      result.getAuthentication().setRequired(jdbcDataSource.isRequireLogin());

      if(jdbcDataSource.isRequireLogin()) {
         result.getAuthentication().setUseCredentialId(jdbcDataSource.isUseCredentialId());

         if(!jdbcDataSource.isUseCredentialId()) {
            result.getAuthentication().setUserName(jdbcDataSource.getUser());
            result.getAuthentication().setPassword(
               Tool.isEmptyString(jdbcDataSource.getPassword()) ? "" : Util.PLACEHOLDER_PASSWORD);
         }
         else {
            result.getAuthentication().setCredentialId(jdbcDataSource.getCredentialId());
         }
      }

      result.setInfo(type.createDatabaseInfo());
      result.getInfo().setProductName(jdbcDataSource.getProductName());
      result.getInfo().setPoolProperties(jdbcDataSource.getPoolProperties());

      result.setNetwork(type.parse(driver, url, result.getInfo()));
      result.setDeletable(true);
      result.setUnasgn(jdbcDataSource.isUnasgn());
      result.getInfo().setCustomEditMode(jdbcDataSource.isCustomEditMode() || type instanceof CustomDatabaseType);
      result.getInfo().setCustomUrl(jdbcDataSource.getCustomUrl() == null ? url : jdbcDataSource.getCustomUrl());
      result.setAnsiJoin(jdbcDataSource.isAnsiJoin());
      result.setDefaultDatabase(jdbcDataSource.getDefaultDatabase());
      result.setTableNameOption(jdbcDataSource.getTableNameOption());
      result.setTransactionIsolation(jdbcDataSource.getTransactionIsolation());
      result.setChangeDefaultDB(!Tool.isEmptyString(jdbcDataSource.getDefaultDatabase()));

      String testQuery = SreeEnv.getProperty(
         "inetsoft.uql.jdbc.pool." + jdbcDataSource.getFullName() + ".connectionTestQuery");

      if(type.getType().equals(CustomDatabaseType.TYPE)) {
         CustomDatabaseType.CustomDatabaseInfo customInfo =
            (CustomDatabaseType.CustomDatabaseInfo) result.getInfo();
         customInfo.setTestQuery(testQuery);
      }
      else if(type.getType().equals(AccessDatabaseType.TYPE)) {
         AccessDatabaseType.AccessDatabaseInfo accessInfo =
            (AccessDatabaseType.AccessDatabaseInfo) result.getInfo();
         accessInfo.setTestQuery(testQuery);
      }

      return result;
   }

   /**
    * Check full outer join support
    * @param xds JDBCDataSource
    * @param portalData true if called for portal data model, else not.
    */
   public static boolean supportFullOuterJoin(JDBCDataSource xds, boolean portalData) {
      if(xds != null) {
         SQLHelper helper = null;

         if(portalData) {
            // portal data model don't need match datasource for user.
            helper = SQLHelper.getSQLHelper(xds, null, null);
         }
         else {
            helper = SQLHelper.getSQLHelper(xds);
         }

         if(helper != null) {
            return helper.supportsOperation(SQLHelper.FULL_OUTERJOIN);
         }
      }

      return false;
   }

   /**
    * Format url by databaseDefinition
    * @param databaseDefinition DatabaseDefinition
    * @return url string. <tt>null</tt> if DatabaseType is Not Found.
    */
   public static String formatUrl(DatabaseDefinition databaseDefinition) {
      assert databaseDefinition != null;
      DatabaseType jdbcDatabaseType = getJDBCDatabaseType(databaseDefinition.getType());

      if(jdbcDatabaseType == null) {
         LOG.warn("JDBC Database Type {} is Not Found! ", databaseDefinition.getType());

         return null;
      }

      return jdbcDatabaseType.formatUrl(databaseDefinition.getNetwork(), databaseDefinition.getInfo());
   }

   /**
    * Creates the query for a table.
    *
    * @param dataSource the data source.
    * @param tablesMap  the selected table name -> entry map.
    * @param columns    the selected column names.
    * @param joins      the table join definitions.
    * @param conditions the query condition list.
    * @return the SQL definition.
    */
   public static UniformSQL createSQL(JDBCDataSource dataSource,  Map<String, AssetEntry> tablesMap,
                                      String[] columns, XJoin[] joins, List<DataConditionItem> conditions,
                                      Principal principal)
      throws Exception
   {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource);
      sql.setSqlQuery(true);
      String[] tables = JDBCUtil.getTableNames(tablesMap);

      for(String table : tables) {
         SelectTable selectTable = sql.addTable(table);
         AssetEntry tableEntry = tablesMap.get(table);

         if(tableEntry != null) {
            selectTable.setCatalog(tableEntry.getProperty(XSourceInfo.CATALOG));
            selectTable.setSchema(tableEntry.getProperty(XSourceInfo.SCHEMA));
         }
      }

      String quote = XUtil.getQuote(dataSource);
      JDBCSelection selection = new JDBCSelection();
      Set<String> aliases = new HashSet<>();
      SQLHelper sqlHelper = SQLHelper.getSQLHelper(sql);

      for(String path : columns) {
         String table = null;
         String column;
         String alias;

         int index = -1;
         boolean quoted = path.endsWith(quote);

         if(quoted) {
            index = path.lastIndexOf(quote, path.length() - 1 - quote.length());
            index = path.lastIndexOf('.', index);
         }
         else {
            index = path.lastIndexOf('.');
         }

         int counter = 1;

         if(index < 0) {
            alias = column = path;
         }
         else {
            alias = column = path.substring(index + 1);

            if(quoted) {
               alias = column = alias.substring(1, alias.length() - 1);
            }

            table = path.substring(0, index);
         }

         while(aliases.contains(alias)) {
            ++counter;
            alias = column + counter;
         }

         String type = XField.STRING_TYPE;
         AssetEntry columnEntry =
            JDBCUtil.getTargetEntryByName(tablesMap.get(table), path, dataSource, principal);
         type = columnEntry != null && columnEntry.getProperty("dtype") != null ?
            columnEntry.getProperty("dtype") : type;

         aliases.add(alias);
         index = selection.addColumn(path);
         selection.setTable(path, table);
         selection.setAlias(index, selection.getValidAlias(index, alias, sqlHelper));
         selection.setType(path, type);
      }

      sql.setSelection(selection);

      for(XJoin join : joins) {
         sql.addJoin(join);
      }

      VariableTable vtable = new VariableTable(false);
      XFilterNode conditionNode = JDBCUtil.createConditionXFilterNode(conditions);
      XFilterNode root = sql.getWhere();

      if(root == null) {
         sql.setWhere(conditionNode);
      }
      else {
         XSet newroot = new XSet(XSet.AND);
         newroot.addChild(root);
         newroot.addChild(conditionNode);
         sql.setWhere(newroot);
      }

      sql.applyVariableTable(vtable);
      sql.clearSQLString();
      sql.clearCachedString();

      return sql;
   }

   /**
    * Gets the names of the selected tables.
    *
    * @return the table names.
    */
   public static String[] getTableNames(Map<String, AssetEntry> tablesMap) {
      ArrayList<AssetEntry> tables = new ArrayList<>(tablesMap.values());

      String[] result = new String[tables.size()];

      for(int i = 0; i < tables.size(); i++) {
         result[i] = tables.get(i).getProperty("source");
      }

      return result;
   }

   public static AssetEntry getTargetEntryByName(AssetEntry parentEntry, String columnPath,
                                                 JDBCDataSource dataSource, Principal principal)
   {
      if(parentEntry == null) {
         return null;
      }

      AssetRepository assetRepository = ViewsheetEngine.getViewsheetEngine().getAssetRepository();
      AssetEntry.Selector selector =
         new AssetEntry.Selector(AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL);

      try {
         AssetEntry[] entries = assetRepository.getEntries(
            parentEntry, principal, ResourceAction.READ, selector);
         SQLHelper sqlHelper = SQLHelper.getSQLHelper(dataSource);
         String noQuoteColPath = trimColumnPathQuote(columnPath, sqlHelper);

         for(AssetEntry entry : entries) {
            String path = entry.getProperty("source") + "." + entry.getProperty("attribute");
            String qualifiedPath =
               entry.getProperty("source") + "." + entry.getProperty("qualifiedAttribute");

            if(Tool.equals(path, columnPath)) {
               return entry;
            }

            if(Tool.equals(noQuoteColPath, trimColumnPathQuote(path, sqlHelper)) ||
               Tool.equals(noQuoteColPath, trimColumnPathQuote(qualifiedPath, sqlHelper)))
            {
               return entry;
            }
         }
      }
      catch(Exception e) {
         // ignore
      }

      return null;
   }

   private static String trimColumnPathQuote(String columnPath, SQLHelper sqlHelper) {
      if(sqlHelper == null) {
         return columnPath;
      }

      String quote = sqlHelper.getQuote();
      String[] arr = Tool.splitWithQuote(columnPath, ".", quote.charAt(0));
      StringBuilder stringBuilder = new StringBuilder();

      for(int i = 0; i < arr.length; i++) {
         stringBuilder.append(arr[i]);

         if(i != arr.length - 1) {
            stringBuilder.append('.');
         }
      }

      return stringBuilder.toString();
   }

   public static XFilterNode createXFilterNode(Clause c) throws Exception {
      XFilterNode xcondition = XFilterNode.createConditionNode(c.getOperation().getSymbol());

      if(xcondition instanceof XUnaryCondition) {
         ClauseValue value1 = c.getValue1();
         String expression1x = value1.getExpression();
         String type1x = checkType(value1.getType());
         XExpression xex = createXExpression(type1x, expression1x, value1.getQuery());
         xex.toString();
         ((XUnaryCondition) xcondition).setExpression1(xex);
      }
      else if(xcondition instanceof XBinaryCondition) {
         ClauseValue value1 = c.getValue1();
         String expression1x = value1.getExpression();
         String type1x = checkType(value1.getType());
         XExpression xe1x = createXExpression(type1x, expression1x, value1.getQuery());
         ((XBinaryCondition) xcondition).setExpression1(xe1x);

         ClauseValue value2 = c.getValue2();
         String expression2x = value2.getExpression();
         String type2x = checkType(value2.getType());
         XExpression xe2x = createXExpression(type2x, expression2x, value2.getQuery());
         ((XBinaryCondition) xcondition).setExpression2(xe2x);
      }
      else if(xcondition instanceof XTrinaryCondition) {
         ClauseValue value1 = c.getValue1();
         String expression1x = value1.getExpression();
         String type1x = checkType(value1.getType());
         XExpression xe1x = createXExpression(type1x, expression1x, value1.getQuery());
         ((XTrinaryCondition) xcondition).setExpression1(xe1x);

         ClauseValue value2 = c.getValue2();
         String expression2x = value2.getExpression();
         String type2x = checkType(value2.getType());
         XExpression xe2x = createXExpression(type2x, expression2x, value2.getQuery());
         ((XTrinaryCondition) xcondition).setExpression2(xe2x);

         ClauseValue value3 = c.getValue3();
         String expression3x = value3.getExpression();
         String type3x = checkType(value3.getType());
         XExpression xe3x = createXExpression(type3x, expression3x, value3.getQuery());
         ((XTrinaryCondition) xcondition).setExpression3(xe3x);
      }

      xcondition.setIsNot(c.isNegated());

      return xcondition;
   }

   public static Clause createCondition(XFilterNode xfn, XDataSource datasource) {
      Clause c = new Clause();

      if(xfn instanceof XUnaryCondition) {
         c.setValue1(getClauseValue(xfn.getExpression1(), datasource));
         Operation op = new Operation();
         op.setName(XFilterNode.getOpName(((XUnaryCondition) xfn).getOp()));
         op.setSymbol(((XUnaryCondition) xfn).getOp());
         c.setOperation(op);
         c.setValue2(new ClauseValue(XExpression.VALUE));
         c.setValue3(new ClauseValue(XExpression.VALUE));
      }
      else if(xfn instanceof XBinaryCondition) {
         c.setValue1(getClauseValue(xfn.getExpression1(), datasource));
         c.setValue2(getClauseValue(((XBinaryCondition) xfn).getExpression2(), datasource));

         Operation op = new Operation();
         op.setName(XFilterNode.getOpName(((XBinaryCondition) xfn).getOp()));
         op.setSymbol(((XBinaryCondition) xfn).getOp());
         c.setOperation(op);
         c.setValue3(new ClauseValue(XExpression.VALUE));
      }
      else if(xfn instanceof XTrinaryCondition) {
         c.setValue1(getClauseValue(xfn.getExpression1(), datasource));
         c.setValue2(getClauseValue(((XTrinaryCondition) xfn).getExpression2(), datasource));
         c.setValue3(getClauseValue(((XTrinaryCondition) xfn).getExpression3(), datasource));

         Operation op = new Operation();
         op.setName(XFilterNode.getOpName(((XTrinaryCondition) xfn).getOp()));
         op.setSymbol(((XTrinaryCondition) xfn).getOp());
         c.setOperation(op);
      }

      c.setNegated(xfn.isIsNot());
      c.setValue(xfn.toString());

      return c;
   }

   private static ClauseValue getClauseValue(XExpression xe, XDataSource dataSource) {
      String value = convertExpressionToString(xe.getValue());
      String type =  correctType(value, xe.getType());
      ClauseValue clauseValue = new ClauseValue(type);

      if(XExpression.SUBQUERY.equals(xe.getType())) {
         SQLQueryDialogModel model = new SQLQueryDialogModel();
         model.setDataSource(dataSource.getFullName());
         model.getDataSources().add(dataSource.getFullName());
         BasicSQLQueryModel simpleQueryModel = new BasicSQLQueryModel();
         simpleQueryModel.setSqlParseResult(Catalog.getCatalog().getString("designer.qb.parseInit"));

         boolean supportOuterFullJoin = true;
         SQLHelper helper = SQLHelper.getSQLHelper(dataSource);

         if(!helper.supportsOperation(SQLHelper.FULL_OUTERJOIN)) {
            supportOuterFullJoin = false;
         }

         model.getSupportsFullOuterJoin().add(supportOuterFullJoin);
         Object expressionValue = xe.getValue();

         if(expressionValue instanceof UniformSQL) {
            UniformSQL sql = (UniformSQL) expressionValue;
            simpleQueryModel.setSqlString(sql.getSQLString());
            simpleQueryModel.setSqlEdited(true);
            model.setFreeFormSqlEnabled(true);
            simpleQueryModel.setSqlParseResult(getParseResult(sql.getParseResult()));
         }

         model.setSimpleModel(simpleQueryModel);
         clauseValue.setQuery(model);
      }
      else {
         clauseValue.setExpression(value);
      }

      return clauseValue;
   }

   public static String getParseResult(int parseResult) {
      switch(parseResult) {
         case UniformSQL.PARSE_SUCCESS:
            return Catalog.getCatalog().getString("designer.qb.parseSuccess");
         case UniformSQL.PARSE_PARTIALLY:
            return Catalog.getCatalog().getString("designer.qb.parsePartially");
         case UniformSQL.PARSE_FAILED:
            return Catalog.getCatalog().getString("designer.qb.parseFailed");
         default:
            return Catalog.getCatalog().getString("designer.qb.parseInit");
      }
   }

   public static void fixTableLocation(UniformSQL sql) {
      if(sql != null) {
         for(int i = 0; i < sql.getTableCount(); i++) {
            Point location = sql.getTableLocation(i);

            if(location.x < 0 || location.y < 0) {
               initSelectTableLocation(sql);
               break;
            }
         }
      }
   }

   public static void initSelectTableLocation(UniformSQL sql) {
      if(sql != null) {
         int defaultPadding = QueryGraphModelService.DEFAULT_GRAPH_VIEW_PADDING;
         int graphNodeHeight = QueryGraphModelService.GRAPH_NODE_HEIGHT;
         int addNodeTopGap = QueryGraphModelService.ADD_NODE_TOP_GAP;

         for(int i = 0; i < sql.getTableCount(); i++) {
            SelectTable selectTable = sql.getSelectTable(i);
            selectTable.setLocation(
               new Point(defaultPadding, defaultPadding + (graphNodeHeight + addNodeTopGap) * i));
         }
      }
   }

   public static String getTablePath(JDBCDataSource dataSource, XNode tableNode) {
      StringBuilder path =
         new StringBuilder().append(dataSource.getFullName()).append('/');
      path.append(tableNode.getAttribute("type")).append('/');

      if(tableNode.getAttribute("catalog") != null) {
         path.append(tableNode.getAttribute("catalog")).append('/');
      }

      if(tableNode.getAttribute("schema") != null) {
         path.append(tableNode.getAttribute("schema")).append('/');
      }

      path.append(tableNode.getName());
      return path.toString();
   }

   public static String getTablePath(String datasource, Object type, Object catalog, Object schema,
                                     String tableName)
   {
      StringBuilder path =
         new StringBuilder().append(datasource).append('/');
      path.append(type).append('/');

      if(catalog != null) {
         path.append(catalog).append('/');
      }

      if(schema != null) {
         path.append(schema).append('/');
      }

      path.append(tableName);
      return path.toString();
   }

   public static XFilterNode createConditionXFilterNode(List<DataConditionItem> conditions)
      throws Exception
   {
      HierarchyList hl = new FilterList();
      HierarchyListModel clm = new HierarchyListModel(hl);

      for(DataConditionItem ci : conditions) {
         if(ci instanceof Clause) {
            XFilterNode xfn = JDBCUtil.createXFilterNode((Clause) ci);
            XFilterNodeItem xfni = new XFilterNodeItem(xfn, ci.getLevel());
            clm.append(xfni);
         }
         else if(ci instanceof Conjunction) {
            XSet xs = new XSet(((Conjunction) ci).getConjunction());
            xs.setIsNot(((Conjunction) ci).isIsNot());
            XSetItem xsi = new XSetItem(xs, ci.getLevel());
            clm.append(xsi);
         }
      }

      clm.fixConditions();
      ConditionListHandler handler = new ConditionListHandler();
      return handler.createXFilterNode(clm.getHierarchyList());
   }

   public static List<String> getAllTableAliases(JDBCQuery xquery) {
      List<String> aliases = new ArrayList<>();
      UniformSQL sqlDefinition = (UniformSQL) xquery.getSQLDefinition();

      for(int i = 0; i < sqlDefinition.getTableCount(); i++) {
         aliases.add(sqlDefinition.getTableAlias(i));
      }

      return aliases;
   }

   public static String replaceTableInExpression(Vector<SelectTable> tables, String expression,
                                                 Hashtable<String, String> aliasMap, JDBCDataSource dataSource)
   {
      if(Tool.isEmptyString(expression) || aliasMap == null ||
         aliasMap.size() == 0 || dataSource == null)
      {
         return expression;
      }

      SQLHelper helper = SQLHelper.getSQLHelper(dataSource);
      Set<String> keys = aliasMap.keySet();

      for(String key : keys) {
         String newAlias = aliasMap.get(key);
         String tableName = getTableName(tables, newAlias);
         String oldAlias = getQuoteAlias(key, helper, tableName);
         expression = expression.replaceAll(oldAlias + ".", getQuoteAlias(newAlias, helper, tableName) + ".");
      }

      return expression;
   }

   private static String getTableName(Vector<SelectTable> tables, String alias) {
      for(SelectTable table : tables) {
         if(Tool.equals(table.getAlias(), alias)) {
            return (String) table.getName();
         }
      }

      return null;
   }

   public static String getRealTableName(SelectTable table) {
      return getRealTableName(Tool.toString(table.getName()), table.getCatalog(), table.getSchema());
   }

   public static String getRealTableName(String tableFullName, String catalog, String schema) {
      if(!tableFullName.contains(".")) {
         return tableFullName;
      }

      if(catalog != null) {
         tableFullName = tableFullName.startsWith(catalog) ?
            tableFullName.substring(catalog.length() + 1) : tableFullName;
      }

      if(schema != null) {
         tableFullName = tableFullName.startsWith(schema) ?
            tableFullName.substring(schema.length() + 1) : tableFullName;
      }

      return tableFullName;
   }

   private static String getQuoteAlias(String alias, SQLHelper helper, String tableName) {
      if(!Tool.equals(alias, tableName)) {
         alias = XUtil.quoteAlias(alias, helper);
      }
      else {
         alias = helper.quoteTableName(alias);
      }

      return alias;
   }

   /**
    * Convert the expression value to string.
    * @param value expression value.
    * @return
    */
   private static String convertExpressionToString(Object value) {
      if(value instanceof UniformSQL) {
         return ((UniformSQL) value).getSQLString();
      }
      else if(value instanceof String[]) {
         return String.join(",", ((String[]) value));
      }

      return (String) value;
   }

   /**
    * Since the Session Data and Variable types get stored as Expression types, this method replaces Expression type with appropriate type.
    *
    * @param value the value of the expression.
    * @param type  the type of the expression.
    *
    * @return real type name.
    */
   private static String correctType(Object value, String type) {
      if(value instanceof String) {
         String strValue = (String) value;
         boolean isSession = type.equals(XExpression.EXPRESSION) && ("$(_USER_)".equals(strValue) ||
            "$(_GROUPS_)".equals(value) ||  "$(_ROLES_)".equals(strValue));
         boolean isVariable = type.equals(XExpression.EXPRESSION) && strValue.length() >= 3 &&
            strValue.startsWith("$(");

         if(isSession) {
            return "Session Data";
         }
         else if(isVariable) {
            return XExpression.VARIABLE;
         }
         else {
            return type;
         }
      }

      return type;
   }

   /**
    * Converts Session Data and Variable types to Expression types.
    *
    * @param type  the type of the expression.
    *
    * @return real type name.
    */
   private static String checkType(String type) {
      if("Session Data".equals(type) || type.equals(XExpression.VARIABLE)) {
         return XExpression.EXPRESSION;
      }
      else {
         return type;
      }
   }

   private static XExpression createXExpression(String type, String expression,
                                                SQLQueryDialogModel model)
      throws Exception
   {
      Object value = expression;

      if(XExpression.SUBQUERY.equals(type)) {
         BasicSQLQueryModel queryModel = model.getSimpleModel();

         if(queryModel.getSqlEdited()) {
            String sqlString = queryModel.getSqlString();
            String dataSource = model.getDataSource();
            XDataSource xds = XFactory.getRepository().getDataSource(dataSource);
            UniformSQL sql = new UniformSQL();
            sql.setDataSource((JDBCDataSource) xds);

            synchronized(sql) {
               sql.setParseSQL(true);
               sql.setSQLString(sqlString, true);
               sql.wait();
            }

            value = sql;
         }
         else {
            value = JDBCUtil.createSQL(
               (JDBCDataSource) XFactory.getRepository().getDataSource(model.getDataSource()),
               queryModel.getTables(), queryModel.getSelectedColumns(),
               queryModel.toXJoins(), queryModel.getConditionList(),
               ThreadContext.getContextPrincipal());
         }
      }

      return new XExpression(value, type);
   }

   public static String quoteMapKeyAccessForParsing(String sqlString) {
      if(Tool.isEmptyString(sqlString)) {
         return sqlString;
      }

      Pattern pattern = Pattern.compile(MAP_KEY_ACCESS_PATTERN);
      Matcher matcher = pattern.matcher(sqlString);

      if(!matcher.find()) {
         return sqlString;
      }

      StringBuilder result = new StringBuilder();
      matcher.reset();

      while(matcher.find()) {
         String matchedWhole = matcher.group();
         matchedWhole = Tool.buildString("\"", matchedWhole, "\"");
         matcher.appendReplacement(result, Matcher.quoteReplacement(matchedWhole));
      }

      matcher.appendTail(result);

      return result.toString();
   }

   // table xnode->XTypeNode(columns)
   private static Hashtable<Pair, XTypeNode> tablemeta = new Hashtable<>();

   private static final String [] aggregateFunction =
      {"sum", "count", "avg", "min", "max"};

   private static final List<DatabaseType> databaseTypes;
   private static final String MAP_KEY_ACCESS_PATTERN = "([^\\s\\[\\]]+)\\[\\s*'([^\\s']+)'\\s*\\]";

   static {
      databaseTypes = new ArrayList<>();

      databaseTypes.add(new AccessDatabaseType());
      databaseTypes.add(new DB2DatabaseType());
      databaseTypes.add(new DB2V8DatabaseType());
      databaseTypes.add(new InformixDatabaseType());
      databaseTypes.add(new MySQLDatabaseType());
      databaseTypes.add(new OracleDatabaseType());
      databaseTypes.add(new PostgreSQLDatabaseType());
      databaseTypes.add(new SQLServerDatabaseType());
      databaseTypes.add(new CustomDatabaseType());
   }

   private static final Logger LOG = LoggerFactory.getLogger(JDBCUtil.class);
}
