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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.XAgent;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.Map;

/**
 * JDBCAgent provides utility methods for jdbc datasource.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class JDBCAgent extends XAgent {
   /**
    * Get the fully qualified name of a meta data node.
    */
   @Override
   public String getQualifiedName(XNode node, XDataSource xds) {
      JDBCDataSource jds = (JDBCDataSource) xds;
      return SQLTypes.getSQLTypes(jds).getQualifiedName(node, jds);
   }

   /**
    * Get the user name for the JDBC datasource
    */
   @Override
   public String getUser(XDataSource xds) {
      return ((JDBCDataSource)xds).getUser();
   }

   /**
    * Get the table columns description.
    * @param table the table name.
    * @param xds datasource object.
    * @param session xengine session.
    * @return column nodes.
    */
   @Override
   public XTypeNode[] getColumns(String table, XDataSource xds,
                                 Object session) throws Exception
   {
      return JDBCUtil.getTableColumns(table, xds, session);
   }

   /**
    * Get the table columns description.
    * @param table the table name.
    * @param query query object.
    * @param session xengine session.
    * @return column nodes.
    */
   @Override
   public XTypeNode[] getColumns(String table, XQuery query,
                                 Object session) throws Exception {
      return JDBCUtil.getTableColumns(table, query, session);
   }

   /**
    * Get a list of all tables that can be merged into this query safely.
    * @param query query object.
    * @return table aliases to table real name mapping. All aliases are related
    * to the query tables.
    */
   @Override
   public Map getRelatedTables(XQuery query) {
      return JDBCUtil.getAllRelatedTables((JDBCQuery) query);
   }

   /**
    * Get a list of all tables of this query.
    * @param xquery the specified query object.
    * @return list of table names.
    */
   @Override
   public Object[] getTables(XQuery xquery) {
      JDBCQuery query = (JDBCQuery) xquery;
      SQLDefinition def = query.getSQLDefinition();

      if(!(def instanceof UniformSQL)) {
         return new Object[0];
      }

      UniformSQL sql = (UniformSQL) def;
      Object[] tables = new Object[sql.getTableCount()];

      for(int i = 0; i < tables.length; i++) {
         SelectTable stable = sql.getSelectTable(i);
         tables[i] = stable.getName();
      }

      return tables;
   }

   /**
    * Get column data from database.
    *
    * @param model data model.
    * @param lname the logic model name.
    * @param ename the entity name.
    * @param aname the attribute name.
    * @param vars the variable table.
    * @param session xengine session.
    * @return null if retriving column data failed.
    */
   @Override
   public XNode getModelData(XDataModel model, String lname, String ename,
                             String aname, VariableTable vars, Object session,
                             Principal user) throws Exception
   {
      XDataSource xds =
         XFactory.getRepository().getDataSource(model.getDataSource());

      if(lname == null) {
         SQLDefinition sql = createDistinctSelect(ename, aname, null, null);
         return runQuery(xds, sql, vars, session, null, user);
      }

      XDataService xrep = XFactory.getDataService();
      XLogicalModel lmodel = model.getLogicalModel(lname, user);
      String partition = lmodel.getPartition();
      XPartition xPartition = model.getPartition(partition, user);
      SQLHelper helper = SQLHelper.getSQLHelper(xds);
      XEntity entity = lmodel.getEntity(ename);

      //bug1372044029228, get entity from child logical model.
      if(entity == null) {
         for(String name : lmodel.getLogicalModelNames()) {
            XLogicalModel childModel = lmodel.getLogicalModel(name);

            if(childModel != null) {
               entity = childModel.getEntity(ename);
            }
         }
      }

      if(entity == null) {
         LOG.error("Entity is not found: " + ename);
         return null;
      }

      XAttribute attr = entity.getAttribute(aname);

      if(attr == null) {
         LOG.error("Attribute is not found: " + aname);
         return null;
      }

      if(!attr.isBrowseable()) {
         LOG.info("Attribute is not browsable: " + aname);
         return null;
      }

      if(attr.getTable() != null) {
         xPartition = xPartition.applyAutoAliases();

         // we can take the first table/column for the attr since
         // only one table and column are set for browsable column attrs
         String tableRealName = xPartition.getAliasTable(attr.getTable(), true);
         tableRealName = XUtil.quoteName(tableRealName == null ||
                                         tableRealName.equals("") ?
                                         attr.getTable() : tableRealName,
                                         helper);
         // @by billh, fix customer bug bug1306453114264
         // String tableAlias = XUtil.quoteName(attr.getTable(), helper);
         String tableAlias = attr.getTable();
         String colname =
            tableAlias + "." + XUtil.quoteAlias(attr.getColumn(), helper);
         XPartition.PartitionTable temp =
            xPartition.getPartitionTable(tableAlias);
         Object catalog = null;
         Object schema = null;

         if(temp != null) {
            if(temp.getType() == 1) {
               Object rtable =
                  xPartition.getRunTimeTable(attr.getTable(), true);
               tableRealName = rtable == null ? null : rtable.toString();
               colname = XUtil.quoteAlias(attr.getColumn(), helper);
            }

            catalog = temp.getCatalog();
            schema = temp.getSchema();
         }

         SQLDefinition sql = createDistinctSelect(tableAlias, tableRealName,
            colname, catalog, schema);
         return runQuery(xds, sql, vars, session, partition, user);
      }
      // @by larryl, this must be an expression, use query generator to
      // generator a query. This breaks the dependency but there is no other way
      // unless we want to reorganize the classes.
      else {
         XDataSelection columns = new XDataSelection(true);
         columns.setSource(xds.getFullName() + "." + lname);
         columns.addAttribute(
            new inetsoft.report.internal.binding.BaseField(ename, aname));
         columns.setDistinct(true);

         JDBCQuery qry = (JDBCQuery)
            inetsoft.report.internal.binding.QueryGenerator.getXQuery(
               columns, vars, ThreadContext.getContextPrincipal());
         SQLDefinition sql = qry.getSQLDefinition();
         return runQuery(xds, sql, vars, session, partition, user);
      }
   }

   /**
    * Get column data from database.
    * @param query query object.
    * @param column column name.
    * @param session xengine session.
    * @return null if retriving column data failed.
    */
   @Override
   public XNode getQueryData(XQuery query, String column,
                             VariableTable vars, Object session, Principal user)
         throws Exception
   {
      XDataService xrep = XFactory.getDataService();
      XDataSource xds = query.getDataSource();

      // @by mikec, 2004-4-9
      // try connect datasource first, getColumnTableInfo always assume the
      // connection is avaliable.
      xrep.connect(session, xds, vars);

      if(xds instanceof JDBCDataSource) {
         SQLDefinition sqldef = getColumnTableSQL(query, column, session);

         if(sqldef == null) {
            int index = column.lastIndexOf(".");
            String table = index == -1 ? null : column.substring(0, index);

            if(table != null && table.length() > 0) {
               sqldef = createDistinctSelect(table, column, null, null);
            }
         }

         if(sqldef != null) {
            return runQuery(xds, sqldef, vars, session,
                            query.getPartition(), user);
         }
      }

      return null;
   }

   /**
    * Get column data from database of new query without SQLDefinition
    * @param query query object.
    * @param column column name.
    * @param table table name.
    * @param session xengine session.
    * @return null if retriving column data failed.
    */
   @Override
   public XNode getQueryData(XQuery query, String table, String column,
                             VariableTable vars, Object session, Principal user)
         throws Exception
   {
      XDataService xrep = XFactory.getDataService();
      XDataSource xds = query.getDataSource();

      if(xds instanceof JDBCDataSource) {
         SQLHelper helper = null;
         UniformSQL usql = null;

         if(query instanceof JDBCQuery) {
            SQLDefinition sql = ((JDBCQuery) query).getSQLDefinition();

            if(sql instanceof UniformSQL) {
               usql = (UniformSQL) sql;

               if(usql.getDataSource() == null) {
                  usql.setDataSource((JDBCDataSource) xds);
               }

               helper = SQLHelper.getSQLHelper(usql);
               XSelection selection = usql.getSelection();
               int index = selection.indexOf(column);
               String fullcol = table + "." + column;

               if(index < 0) {
                  index = selection.indexOf(fullcol);
               }

               if(index < 0) {
                  String column2 = selection.getAliasColumn(column);

                  if(column2 != null && column2.length() > 0) {
                     column = column2;
                  }
                  else {
                     column2 = selection.getAliasColumn(fullcol);

                     if(column2 != null && column2.length() > 0) {
                        column = column2;
                     }
                     else {
                        index = selection.indexOfColumn(column);
                        column2 = index >= 0 ? selection.getColumn(index) : null;

                        if(column2 != null && column2.length() > 0 &&
                           usql.isTableColumn(column2) &&
                           // table must be matched, fix bug1362387136919
                           (table == null || usql.getTable(column2).equals(table)))
                        {
                           column = column2;
                        }
                     }
                  }
               }
            }
         }

         helper = helper != null ? helper : SQLHelper.getSQLHelper(xds);
         String tname = null;
         SelectTable stable = usql != null ? usql.getSelectTable(table) : null;
         String talias = stable == null ? null : stable.getAlias();
         talias = Tool.equals(talias, table) ? null : talias;
         talias = talias == null ? null : helper.quoteTableAlias(talias);

         // @by rundaz, if the table is a subquery, it should not be quoted
         if(XUtil.isSubQuery(table))  {
            tname = table;
         }
         else {
            tname = usql != null ?
               helper.quoteTableName(table) : XUtil.quoteName(table, helper);
         }

         talias = talias != null ? talias : tname;
         String cname = usql != null && usql.isTableColumn(column) ?
            helper.quotePath(column) : XUtil.quoteName(column, helper);
         Object catalog = stable == null ? null : stable.getCatalog();
         Object schema = stable == null ? null : stable.getSchema();
         SQLDefinition sql = createDistinctSelect(talias, tname, cname, catalog,
                                                  schema);

         if("mongodb.jdbc.MongoDriver".equals(((JDBCDataSource) xds).getDriver()) &&
            sql instanceof UniformSQL)
         {
            ((UniformSQL) sql).setDistinct(false);
         }

         return runQuery(xds, sql, vars, session, query.getPartition(), user);
      }

      return null;
   }

   /**
    * Check if the data source connection is reusable.
    * @return true if is reusable, false otherwise.
    */
   @Override
   public boolean isConnectionReusable() {
      return true;
   }

   /**
    * Execute a sql string.
    */
   private XNode runQuery(XDataSource xds, SQLDefinition sql,
                          VariableTable vars, Object session, String partition,
                          Principal user) throws Exception {
      XDataService xrep = XFactory.getDataService();
      JDBCQuery xquery = new JDBCQuery();

      if(sql.getDataSource() == null) {
         sql.setDataSource((JDBCDataSource) xds);
      }

      xquery.setDataSource(xds);
      xquery.setSQLDefinition(sql);

      if(partition != null) {
         xquery.setPartition(partition);
      }

      return xrep.execute(session, xquery, vars, user, false, null);
   }

   /**
    * Creates a distinct select SQL definition for the specified column and
    * table.
    */
   private UniformSQL createDistinctSelect(String table, String column,
                                           Object catalog, Object schema) {
      return createDistinctSelect(table, table, column, catalog, schema);
   }

   /**
    * Creates a distinct select SQL definition for the specified column and
    * table.
    */
   private UniformSQL createDistinctSelect(String tableAlias, String table,
      String column, Object catalog, Object schema)
   {
      UniformSQL sql = new UniformSQL();
      JDBCSelection sel = (JDBCSelection) sql.getSelection();

      // @rundaz, if the table is a subquery table, its tablealias should be
      // generated
      if(XUtil.isSubQuery(table)) {
         tableAlias = table.substring(0, 3) + table.hashCode();
      }

      SelectTable stable = sql.addTable(tableAlias, table);
      stable.setCatalog(catalog);
      stable.setSchema(schema);

      sel.addColumn(column);
      sel.setTable(column, tableAlias);
      sql.addField(new XField(column, null, tableAlias));
      sql.setDistinct(true);
      sql.setParseResult(UniformSQL.PARSE_SUCCESS);

      return sql;
   }

   /**
    * Gets the SQL definition for a distinct select of the specified column and
    * query.
    */
   private SQLDefinition getColumnTableSQL(XQuery xquery, String column,
                                           Object session)
         throws Exception
   {
      try {
         XRepository xrep = XFactory.getRepository();

         if(xquery instanceof JDBCQuery) {
            JDBCQuery query = (JDBCQuery) xquery;
            SQLDefinition sqlDef = query.getSQLDefinition();
            JDBCDataSource xds = (JDBCDataSource) query.getDataSource();

            if(sqlDef instanceof UniformSQL) {
               UniformSQL sql = (UniformSQL) sqlDef;
               SQLHelper helper = SQLHelper.getSQLHelper(sql);
               XSelection selection = sql.getSelection();
               int index = selection.indexOf(column);

               if(index < 0) {
                  String column2 = selection.getAliasColumn(column);

                  if(column2 != null && column2.length() > 0) {
                     column = column2;
                  }
                  else {
                     index = selection.indexOfColumn(column);
                     column2 = index >= 0 ? selection.getColumn(index) : null;

                     if(column2 != null && column2.length() > 0 &&
                        sql.isTableColumn(column2))
                     {
                        column = column2;
                     }
                  }
               }

               helper.setUniformSql(sql);
               JDBCUtil.fixUniformSQLInfo(
                  sql, xrep, session, (JDBCDataSource) xquery.getDataSource());
               XField field = sql.getFieldByPath(column);

               if(field == null) {
                  field = sql.getField(column);

                  if(field != null && field.getAlias() != null &&
                     // @by larryl, this check shouldn't be necessary but the
                     // getField() would use a numeric string as an index to
                     // get field by position (for unknown reason), so if a
                     // column is a digit, it would be incorrectly matched with
                     // a field. This is really a problem with getField()
                     field.getAlias().equalsIgnoreCase(column))
                  {
                     column = field.getName().toString();
                  }
               }

               if(field != null) {
                  SQLDefinition ret = null;

                  if(field.getTable() != null && field.getTable().length() != 0)
                  {
                     Object tname = sql.getTableName(field.getTable());
                     SelectTable stable = sql.getSelectTable(field.getTable());
                     String cname = helper.quotePath(column);
                     String talias = stable == null ? null : stable.getAlias();
                     talias = Tool.equals(talias, tname) ? null : talias;

                     if(tname instanceof String) {
                        tname = helper.quoteTableName((String) tname);
                        talias = talias == null ? null :
                           helper.quoteTableAlias(talias);
                     }
                     else if(tname != null) {
                        tname = tname.toString();
                        talias = talias == null ? null :
                           helper.quoteTableAlias(talias);
                     }
                     else {
                        tname = helper.quoteTableName(field.getTable());
                     }

                     talias = talias != null ? talias : (String) tname;
                     Object catalog = stable == null ?
                        null : stable.getCatalog();
                     Object schema = stable == null ? null : stable.getSchema();
                     ret = createDistinctSelect(talias, (String) tname, cname,
                                                catalog, schema);
                  }
                  // sql expression
                  else {
                     // do not use freeform sql to apply vpm properly
                     ret = (UniformSQL) sql.clone();
                     ((UniformSQL) ret).clearSQLString();

                     Object[] groups = ((UniformSQL) ret).getGroupBy();
                     JDBCSelection selection2 =
                        (JDBCSelection) ret.getSelection();

                     if(groups != null && groups.length > 0) {
                        for(int i = 0; i < groups.length; i++) {
                           Object sfield = groups[i];

                           if(sfield instanceof String) {
                              String col =
                                 selection2.getAliasColumn((String) sfield);
                              col = col == null ? (String) sfield : col;
                              groups[i] = col;
                           }
                        }
                     }

                     ((UniformSQL) ret).setGroupBy(groups);
                     selection2.clear(false);
                     ((UniformSQL) ret).setDistinct(true);
                     selection2.addColumn(column);
                  }

                  return ret;
               }
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to create distinct column values SQL " +
            "for column " + column + " in " + xquery, e);
      }

      return null;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(JDBCAgent.class);
}

