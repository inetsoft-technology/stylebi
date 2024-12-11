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
package inetsoft.uql.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.report.XSessionManager;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.util.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XHandler;
import inetsoft.uql.table.XObjectColumn;
import inetsoft.uql.table.XTableColumnCreator;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.monitoring.MonitorLevelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.security.Principal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JDBC query handler. It is responsible for executing all JDBC queries.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
@SuppressWarnings({ "SqlNoDataSourceInspection", "SqlResolve" })
public class JDBCHandler extends XHandler {
   /**
    * Constructor.
    */
   public JDBCHandler()   {
   }

   /**
    * Replace all of the escape characters in sql string.
    */
   public static String replaceEscCharacter(String sql, List<String> tablaAliases) {
      // get all positions of the escape characters in const strings
      List<Integer> vPos = new ArrayList<>();
      // find the first begin "'" or "\""
      int begin;
      int end = -1;

      int tmp1 = sql.indexOf("'", end + 1);
      int tmp2 = sql.indexOf("\"", end + 1);
      boolean bTmp = ((tmp1 < tmp2) && (tmp1 != -1)) || (tmp2 == -1);
      begin = bTmp ? tmp1 : tmp2;

      MainFindLoop:
      while(begin != -1) {
         // find end "'" or "\"", check if it is an escape character
         end = begin + 1;

         do {
            end = sql.indexOf(bTmp ? "'" : "\"", end);

            if(end == -1) {
               break MainFindLoop;
            }

            int count = 0;
            int index = end - 1;

            while(sql.charAt(index) == '\\') {
               count++;
               index--;
            }

            if(count % 2 == 0) {
               break; // inner do loop
            }
            else {
               if(tablaAliases != null) {
                  String table = sql.substring(begin + 1, end);

                  if(tablaAliases.contains(table)) {
                     break;
                  }
               }

               end++;

               if(end >= sql.length()) {
                  break MainFindLoop;
               }
            }
         }
         while(true);

         // find all of the escape characters in this const string
         int index = begin;

         while((index = sql.indexOf("\\", index + 1)) < end && index != -1) {
            vPos.add(index);
         }

         // find the next begin "'" or "\""
         tmp1 = sql.indexOf("'", end + 1);
         tmp2 = sql.indexOf("\"", end + 1);
         bTmp = ((tmp1 < tmp2) && (tmp1 != -1)) || (tmp2 == -1);
         begin = bTmp ? tmp1 : tmp2;
      }

      // for all of the escape characters, double it
      begin = 0;
      StringBuilder res = new StringBuilder();

      for(Integer pos : vPos) {
         end = pos;
         String strBegin = sql.substring(begin, end);

         begin = end + 1;
         res.append(strBegin).append("\\\\");
      }

      res.append(sql.substring(begin));
      return res.toString();
   }

   /**
    * Get a query key for query caching.
    */
   @SuppressWarnings("SuspiciousMethodCalls")
   public String getQueryKey(JDBCDataSource jds, SQLDefinition sqlDefinition, String sql,
                             List<Object> vars, List<String> names, VariableTable params, int max,
                             int timeout)
      throws Exception
   {
      // so that it hits the cache more often
      sql = sql.replaceAll("\\) inner[0-9]+", ") inner");
      StringBuilder buffer = new StringBuilder();
      buffer.append(jds.getIdentity());
      buffer.append(sql);
      buffer.append("vars[");

      for(Object var : vars) {
         buffer.append(var).append(",");
      }

      buffer.append("] names[");

      for(String name : names) {
         if(name != null && name.startsWith("dataselcond")) {
            name = "dataselcond";
         }

         buffer.append(name).append(",");
      }

      buffer.append("]");

      String filters = SreeEnv.getProperty("jdbc.query.filter");
      Set<String> included = new HashSet<>();

      if(filters != null && !filters.trim().isEmpty()) {
         for(String filterClass : filters.trim().split(",")) {
            try {
               JDBCQueryFilter filter = (JDBCQueryFilter)
                  Class.forName(Tool.convertUserClassName(filterClass)).newInstance();
               included.addAll(filter.getIncludedParameters(params));
            }
            catch(Exception exc) {
               LOG.warn("Failed to instantiate query filter class: " + filterClass,
                  exc);
            }
         }
      }

      included.removeAll(names);

      if(!included.isEmpty()) {
         buffer.append(" filters [");

         for(String name : included) {
            buffer.append(name).append('=')
               .append(params.get(name)).append(',');
         }

         buffer.append(']');
      }

      buffer.append("MAXROW:").append(max);
      buffer.append("TIMEOUT:").append(timeout);
      buffer.append(getSelectionKey(sqlDefinition));

      return buffer.toString();
   }

   private String getSelectionKey(SQLDefinition sqlDefinition) {
      if(sqlDefinition == null) {
         return "";
      }

      XSelection selection = sqlDefinition.getSelection();

      if(selection.getColumnCount() == 0) {
         return "";
      }

      HashMap<String, Integer> map = new HashMap<>();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         map.put(selection.getColumn(i), i);
      }

      Set<String> set = map.keySet();
      List<String> list = new ArrayList<>(set);
      Collections.sort(list);
      StringBuilder builder = new StringBuilder();
      builder.append(" metainfos {");

      for(int i = 0; i < list.size(); i++) {
         String path = list.get(i);
         int idx = map.get(path);
         XMetaInfo metaInfo = selection.getXMetaInfo(idx);

         if(metaInfo == null) {
            continue;
         }

         builder.append(path);
         builder.append(":");
         builder.append(metaInfo.toString(true));
      }

      builder.append("} ");
      return builder.toString();
   }

   /**
    * Execute the query.
    * @param params parameters for query.
    * @return the result as a hierarchical tree.
    */
   @Override
   public XNode execute(XQuery query, VariableTable params, Principal user,
                        DataCacheVisitor visitor)
      throws Exception
   {
      final int max;
      int hintMax = 0;
      final int queryMax = query.getMaxRows();

      if(params != null && params.get(XQuery.HINT_MAX_ROWS) != null) {
         hintMax = Integer.parseInt(params.get(XQuery.HINT_MAX_ROWS).toString());
      }

      if(hintMax > 0 && queryMax > 0) {
         max = Math.min(hintMax, queryMax);
      }
      else if(hintMax > 0) {
         max = hintMax;
      }
      else {
         max = queryMax;
      }

      int timeout;

      if(params != null && params.get(XQuery.HINT_TIMEOUT) != null) {
         timeout = Integer.parseInt(params.get(XQuery.HINT_TIMEOUT).toString());
      }
      else {
         timeout = query.getTimeout();
      }

      long startTime = System.currentTimeMillis();
      JDBCQuery xquery = (JDBCQuery) query;
      SQLDefinition asql = xquery.getSQLDefinition();

      if(asql instanceof UniformSQL) {
         UniformSQL usql = (UniformSQL) asql;

         if(XUtil.isParsedSQL(usql) && usql.getTableCount() == 0) {
            throw new SQLException("No tables selected!");
         }

         // Bug #56316
         if(SQLHelper.getSQLHelper(usql) instanceof MongoHelper) {
            usql.setHint(UniformSQL.HINT_STATIC_SQL, true);
         }
      }

      String[] groups = XUtil.getUserGroups(user);
      String[] roleNames = XUtil.getUserRoleNames(user);

      if(user != null) {
         // @by larryl, the BasicReplet sets these two variable on the server
         // side. This ensures the variables are also set even if the execution
         // path does not go throught BasicReplet. The code in BasicReplet
         // is still needed for BC since it may have been used in script.
         // If the values are already set, don't override since the value may
         // be changed by script to be different from the real user/roles
         if(!params.contains("_USER_")) {
            String userId = user.getName();
            String userName = userId;

            if(userId != null && userId.indexOf(IdentityID.KEY_DELIMITER) > 0) {
               userName = IdentityID.getIdentityIDFromKey(userId).getName();
            }

            params.put("_USER_", userName);
         }

         if(roleNames != null && !params.contains("_ROLES_")) {
            params.put("_ROLES_", roleNames);
         }

         if(groups != null && !params.contains("_GROUPS_")) {
            params.put("_GROUPS_", groups);
         }

         xquery = (JDBCQuery) VpmProcessor.getInstance().applyConditions(xquery, params, false, user);
         xquery = (JDBCQuery) VpmProcessor.getInstance().applyHiddenColumns(xquery, params, user);
      }

      xquery = (JDBCQuery) XUtil.clearComments(xquery);
      // @by haiqiangy, set condition item to true condition if
      // user does not input parameter
      xquery.validateConditions(params);
      xquery.applyVariableTable(params);
      xquery.removeTable(params);

      // set the query variables
      prepareVariableTable(query, params);

      JDBCQueryCacheNormalizer cacheNormalizer =
         visitor instanceof XSessionManager.DataCacheResult ?
            ((XSessionManager.DataCacheResult) visitor).getCacheNormalizer() : null;
      UniformSQL uniformSQL = xquery.getSQLDefinition() instanceof UniformSQL ?
         (UniformSQL) xquery.getSQLDefinition() : null;

      // since we try to sort selection columns as much as possible when generate sql string,
      // may appear sql string was sorted but there's no cacheNormalizer available, fix by
      // regenerating a non-sorted sql string when cacheNormalizer is null.
      if(cacheNormalizer == null && uniformSQL != null && Boolean.TRUE.equals(
         uniformSQL.getHint(UniformSQL.HINT_SQL_STRING_SORTED_COLUMN, false)))
      {
         JDBCQueryCacheNormalizer cacheTest = new JDBCQueryCacheNormalizer((JDBCQuery) query.clone());

         if(cacheTest.getSortedColumnMap() != null && cacheTest.isClearedSqlString()) {
            uniformSQL.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);
            uniformSQL.clearSQLString();
         }
      }
      // if the cacheNormalizer created base on query clone, which means the current query
      // sql string may havn't applied the sorted column, need regenerate sql string to fix
      // issue like 59595.
      else if(cacheNormalizer != null && uniformSQL != null &&
         !Boolean.TRUE.equals(uniformSQL.getHint(UniformSQL.HINT_SQL_STRING_SORTED_COLUMN, false)))
      {
         uniformSQL.clearCachedString();

         if(cacheNormalizer.isClearedSqlString()) {
            uniformSQL.sqlstring = null;
         }
      }

      String sql = replaceEscCharacter(xquery.getSQLAsString(), JDBCUtil.getAllTableAliases(xquery));

      if(cacheNormalizer != null && uniformSQL != null &&
         !Boolean.TRUE.equals(uniformSQL.getHint(UniformSQL.HINT_SQL_STRING_SORTED_COLUMN, false)))
      {
         cacheNormalizer = null;
      }

      SQLDefinition def = xquery.getSQLDefinition();
      boolean isproc = def instanceof ProcedureSQL;

      if(isproc) {
         // append variable table from the variables in procedure.
         UserVariable[] defVars = ((ProcedureSQL) def).getVariables();

         for(UserVariable defVar : defVars) {
            XValueNode valueNode = defVar.getValueNode();

            // @by larryl, only use default value if the parameter is not
            // passed in
            if(params.get(defVar.getName()) == null &&
               valueNode.getVariable() == null) {
               params.put(defVar.getName(), defVar);
            }
         }
      }

      Connection conn = null;
      JDBCTableNode tnode = null; // return value
      boolean berror = false;

      try {
         // @by riveryang, change the xquery 's datasource for the extended model
         // of using additional datasource, the xquery must be cloned.
         XDataSource xds1 = xquery.getDataSource();

         // use the data source from the handler if connection parameters are not filled
         if(xds1.getParameters() != null && xds.getParameters() == null) {
            xds1 = xds;
         }

         JDBCDataSource xds2 = (JDBCDataSource) ConnectionProcessor.getInstance().getDatasource(user, xds1);

         if(!xds2.equals(xds1) || !xds2.equals(xds) ||
            xds2.isAnsiJoin() != xds.isAnsiJoin() ||
            (xds1 instanceof JDBCDataSource &&
               ((JDBCDataSource) xds1).isAnsiJoin() != xds2.isAnsiJoin()))
         {
            xquery.setDataSource(xds2);
            xds = xds2;
         }

         // a query might belong to more than one query manager. For example,
         // in an asset query sandbox, there is one query manager manages all the
         // queries; meanwhile, when a chart is reexecuted, we need to cancel the
         // previous execution if any, so there might be a specific query manager
         QueryManager queryMgr = (QueryManager) xquery.getProperty("queryManager");
         QueryManager queryMgr2 = (QueryManager) xquery.getProperty("queryManager2");

         // this query manager will be put into query info which used to cancel
         // query in query monitor service
         QueryManager queryMgr3 = null;

         if(MonitorLevelService.getMonitorLevel() > 0) {
            List<String> infos = XUtil.QUERY_INFOS.get();
            String queryId = infos != null && infos.size() > 1 ? infos.get(1) : null;

            if(queryId != null) {
               queryMgr3 = new QueryManager();
               QueryInfo info = XUtil.queryMap.get(queryId);

               if(info != null) {
                  info.setQueryManager(queryMgr3);
               }
            }
         }

         SQLExecutor executor = Drivers.getInstance().getSQLExecutor(xds2.getDriver(), xds2.getURL());

         // support custom execution of SQL
         if(executor != null) {
            // a sql might be empty
            if(sql == null || sql.trim().length() == 0) {
               return new NullTableNode();
            }

            XNode result = executor.execute(sql, params, max, xds2, xquery,
                                            queryMgr, queryMgr2, queryMgr3);

            if(result != null) {
               return result;
            }
         }

         conn = getConnection(params, user);

         if(conn == null) {
            if(!login) {
               throw new SQLException("Failed to connect to datasource[" +
                  query.getDataSource().getFullName() + "]!");
            }
            else {
               throw new NoConnectionException(Catalog.getCatalog().getString(
                  "designer.qb.jdbc.noDatabaseCon"));
            }
         }

         sql = applyQueryFilter(conn, sql, params, user);
         VarSQL varsql = new VarSQL();
         varsql.setSQLType(isproc ? VarSQL.SQLType.PROC : VarSQL.SQLType.STATEMENT);
         sql = varsql.replaceVariables(sql, params);
         List<Object> vars = varsql.getParameterValues();
         List<String> names = varsql.getParameterNames();

         String key = getQueryKey(xds, def, sql, vars, names, params, max, timeout);

         if(visitor != null && visitor.visitCache(key)) {
            return new XNode();
         }

         String[] translations = null;

         if(xds != null) {
            translations = SQLTypes.getTranslateCharsets(xds.getName());

            if(translations != null) {
               String temp = translations[1];
               translations[1] = translations[0];
               translations[0] = temp;
            }
         }

         Statement stmt = null;
         ResultSet resultSet;

         LOG.debug("Start query: {}", sql);

         try {
            // a sql might be empty
            if(sql == null || sql.trim().length() == 0) {
               return new NullTableNode();
            }

            if(!isproc && vars.size() > 0) {
               // the connection should be closed, otherwise will cause
               // problem for DB2 v9 driver
               Statement temp = null;

               try {
                  temp = conn.prepareStatement(
                     (String) SQLTypes.convert(sql, translations));
               }
               catch(SQLException se) {
                  if(se.getErrorCode() == 590342) {
                     UniformSQL usql = (UniformSQL) xquery.getSQLDefinition();
                     usql.setHint(UniformSQL.HINT_STATIC_SQL, Boolean.TRUE);
                     xquery.applyVariableTable(params);
                     sql = replaceEscCharacter(
                        xquery.getSQLAsString(), JDBCUtil.getAllTableAliases(xquery));
                     vars.clear();
                  }
               }
               finally {
                  if(temp != null) {
                     try {
                        temp.close();
                     }
                     catch(Exception ex) {
                        // ignore it
                     }
                  }
               }
            }

            XSelection xselect = (XSelection) def.getSelection().clone();

            int ret;
            int cursor = 0; // cursor position
            Integer cursorType;
            // index of out parameters
            Vector<Object[]> outparams = new Vector<>();

            String origSQL = sql;
            boolean hasNullParams = false;

            for(int i = 0, paramIdx = 0; i < vars.size(); i++, paramIdx++) {
               Object val = vars.get(i);

               if(val == null) {
                  hasNullParams = true;
                  break;
               }
            }

            Map<String, Integer> redefinedParams = new HashMap<>();
            int retry = 0;
            // setObject(int, Boolean) doesn't work on DB2
            boolean boolToStr = xds.checkDatabaseType(JDBCDataSource.JDBC_DB2);
            boolean strToBool = false;
            boolean exeflag = false;
            boolean querytimeout = false;

            // This loop tries for a maximum of two times.
            // Case 1) If any of the IN params has a null value specified
            //         we reconstruct the sql by omitting the NULL IN
            //         parameter and using the notation @param = ? for
            //         all other params and try executing the Statement
            //         If the execution fails, follow the normal flow using
            //         NULL value for the IN params
            // Case 2) If no IN params have NULL value, execute the loop once
            //         with the normal logic flow and the original Procedure
            //         SQL statement.
            while(retry <= 1) {
               if(isproc && hasNullParams && retry == 0) {
                  sql = reconstructNotNullSQL(sql, vars, names,
                                              (ProcedureSQL) def, redefinedParams);
               }
               else {
                  sql = origSQL;
               }

               String tsql = (String) SQLTypes.convert(sql, translations);
               stmt = isproc ? conn.prepareCall(tsql) :
                  vars.size() > 0 ? conn.prepareStatement(tsql) :
                  conn.createStatement();

               String fetchsize = SreeEnv.getProperty("jdbc.fetch.size");
               boolean mysql = xds.getDatabaseType() == JDBCDataSource.JDBC_MYSQL;

               try {
                  // by default mysql reads and caches the entire
                  // resultset, causing OOM on large dataset. it only
                  // supports setFetchSize() to Integer.MIN_VALUE to
                  // turn on streaming. MySQL only supports MIN_VALUE
                  if(mysql && !(xds.getDriver().equals("org.mariadb.jdbc.Driver") &&
                     conn.getMetaData().getDriverMajorVersion() > 2))
                  {
                     stmt.setFetchSize(Integer.MIN_VALUE);
                  }
                  else if(fetchsize != null) {
                     stmt.setFetchSize(Integer.parseInt(fetchsize));
                  }
               }
               catch(Exception ex) {
                  if(mysql) {
                     fetchsize = "" + Integer.MIN_VALUE;
                  }

                  LOG.warn("Invalid JDBC fetch size: " + fetchsize, ex);
               }

               // add pending to general query manager
               if(queryMgr != null) {
                  queryMgr.addPending(stmt);
               }

               // add pending to specific query manager
               if(queryMgr2 != null) {
                  queryMgr2.addPending(stmt);
               }

               // add pending to query manager which used for mxbean
               if(queryMgr3 != null) {
                  queryMgr3.addPending(stmt);
               }

               if(max > 0) {
                  try {
                     stmt.setMaxRows(max + 1);
                     LOG.debug("Query max rows = {}", max);
                  }
                  catch(SQLException sqle) {
                     LOG.warn("Driver does not support maximum query size", sqle);
                  }
               }

               boolean timeoutsupported = false;

               if(params != null && params.get(XQuery.HINT_TIMEOUT) != null) {
                  timeout =
                     Integer.parseInt(params.get(XQuery.HINT_TIMEOUT).toString());
               }
               else {
                  timeout = query.getTimeout();
               }

               if(timeout > 0) {
                  try {
                     stmt.setQueryTimeout(timeout);
                     timeoutsupported = true;
                     LOG.debug("Query timeout = {}", timeout);
                  }
                  catch(SQLException sqle) {
                     LOG.warn("Driver does not support query timeout", sqle);
                  }
               }

               // if has return value
               ret = (isproc && sql.startsWith("{?")) ? 1 : 0;
               cursor = 0; // cursor position
               cursorType = null;
               outparams = new Vector<>(); // index of out parameters

               // find the cursor type code
               if(isproc && xds.checkDatabaseType(JDBCDataSource.JDBC_ORACLE)) {
                  cursorType = (Integer) XUtil.field(
                     "oracle.jdbc.driver.OracleTypes", "CURSOR");
               }

               // check for return type
               if(ret > 0) {
                  Integer retType = ((ProcedureSQL) def).getReturnType();
                  String retName = ((ProcedureSQL) def).getReturnTypeName();

                  if(retType != null && cursorType != null &&
                     xds.checkDatabaseType(JDBCDataSource.JDBC_ORACLE) &&
                     retName != null && retName.equals("REF CURSOR"))
                  {
                     retType = cursorType;
                     cursor = 1;
                  }

                  outparams.add(new Object[] { ret, retType, "Return"});
                  ((CallableStatement) stmt).registerOutParameter(1, retType);
               }

               // register out parameters
               if(isproc) {
                  ProcedureSQL procSQL = (ProcedureSQL) def;
                  CallableStatement call = (CallableStatement) stmt;

                  for(int i = 0; i < procSQL.getParameterCount(); i++) {
                     ProcedureSQL.Parameter param = procSQL.getParameter(i);
                     int outIdx = i + ret + 1;

                     if(param.inout == ProcedureSQL.OUT ||
                        param.inout == ProcedureSQL.INOUT)
                     {
                        // in the redefined locate the position of the out/inout
                        // parameter in picture and change the index accordingly
                        if(hasNullParams && retry == 0) {
                           Integer outIndex = redefinedParams.get(param.name);

                           if(outIndex == null) {
                              outIdx = outIndex;
                           }
                           else {
                              continue;
                           }
                        }

                        outparams.add(
                           new Object[] { outIdx, param.sqltype, param.name });

                        if(xds.checkDatabaseType(JDBCDataSource.JDBC_ORACLE) &&
                           "REF CURSOR".equals(param.typename))
                        {
                           call.registerOutParameter(outIdx, cursorType);

                           if(cursor <= 0) {
                              cursor = outIdx;
                           }
                        }
                        // assume regular parameter
                        else if(param.sqltype < Types.OTHER ||
                                param.sqltype == Types.BLOB) {
                           call.registerOutParameter(outIdx, param.sqltype);
                        }
                        else {
                           call.registerOutParameter(outIdx, Types.CHAR);
                        }
                     }
                  }
               }

               // if sybase, call by name
               boolean sybase = xds.getDatabaseType() ==
                  JDBCDataSource.JDBC_SYBASE;
               boolean informix = xds.getDatabaseType() ==
                  JDBCDataSource.JDBC_INFORMIX;
               boolean db2 = xds.getDatabaseType() == JDBCDataSource.JDBC_DB2;
               Method nameFunc = null;

               if(sybase && def instanceof ProcedureSQL) {
                  Class[] nameP = {int.class, String.class };

                  try {
                     nameFunc =
                        stmt.getClass().getMethod("setParameterName", nameP);
                  }
                  catch(Throwable ignore) {
                  }
               }

               if("true".equals(SreeEnv.getProperty("mv.debug"))) {
                  @SuppressWarnings("MalformedFormatString")
                  String msg = String.format(
                     "%1$tF %1$tT: execute \"%s\"", new STime(), sql.trim());
                  LOG.debug(msg, new Exception("Stack trace"));
               }
               else {
                  if(LOG.isDebugEnabled()) {
                     LOG.debug("About to execute \"{}\"", sql.trim());
                  }
               }

               // @by mikec, we record the in/inout parameter index here.
               // because in a procedure the in/inout parameter may not
               // always at the very begining and may not be continous.
               // remember their index here so that in the next step we
               // can set correct value to correct parameter index.
               List<Integer> inparams = new ArrayList<>();

               if(isproc) {
                  ProcedureSQL procSQL = (ProcedureSQL) def;

                  for(int i = 0; i < procSQL.getParameterCount(); i++) {
                     ProcedureSQL.Parameter param = procSQL.getParameter(i);

                     if(param.inout == ProcedureSQL.IN ||
                        param.inout == ProcedureSQL.INOUT) {
                        inparams.add(i);
                     }
                  }
               }

               int nullCount = 0;

               // set the in and inout parameters
               for(int i = 0, paramIdx = 0; i < vars.size(); i++, paramIdx++) {
                  Object val = vars.get(i);
                  int sqltype = Types.CHAR;
                  int inIdx = (isproc ? inparams.get(i) : i) + ret + 1;

                  // @by larryl, in sybase, a BIT parameter can not be set to null.
                  // So if the parameter is BIT (mapped to BOOLEAN in our type), we
                  // pass a false to avoid the exception. This does not appear to
                  // affect other database so we don't check for db type here
                  if(isproc) {
                     String pname = names.get(i);
                     ProcedureSQL procSQL = (ProcedureSQL) def;
                     ProcedureSQL.Parameter par = procSQL.getParameter(pname);

                     if(par != null) {
                        sqltype = par.sqltype;
                     }

                     // @by larryl, oracle does not like OTHER type
                     if(sqltype == Types.OTHER) {
                        sqltype = Types.CHAR;
                     }

                     // @by larryl, sybase returns 35 for univarchar type but if
                     // you use 35 in setNull, it throws up, so we just pass CHAR
                     if(sqltype == 35 &&
                        xds.checkDatabaseType(JDBCDataSource.JDBC_SYBASE))
                     {
                        sqltype = Types.CHAR;
                     }

                     if(!procSQL.isVariable(pname)) {
                        val = procSQL.getEmbededInParameterValue(pname);
                     }
                     else {
                        if(val instanceof UserVariable) {
                           XValueNode node = ((UserVariable) val).getValueNode();
                           val = node == null ? null : node.getValue();
                        }
                     }

                     if(val == null && par != null && par.sqltype == Types.BIT) {
                        val = Boolean.FALSE;
                     }
                  }
                  else {
                     if(val instanceof UserVariable) {
                        XValueNode node = ((UserVariable) val).getValueNode();
                        val = node == null ? null : node.getValue();
                     }
                  }

                  val = XUtil.toSQLValue(val, sqltype);

                  if(val == null) {
                     nullCount++;

                     if(!isproc || !hasNullParams || retry != 0) {
                        LOG.debug("  inputParameter #{} = NULL {}", i + 1, sqltype);
                        ((PreparedStatement) stmt).setNull(inIdx, sqltype);
                     }
                  }
                  else {
                     if(isproc && hasNullParams && retry == 0 && vars.get(i) != null) {
                        LOG.debug("  inputParameter #{}={} \" ({})", i + 1 - nullCount, val,
                                  val.getClass().getName());
                        ((PreparedStatement) stmt).setObject(inIdx - nullCount,
                                                             SQLTypes.convert(val, translations));
                     }
                     else if(retry != 0 || vars.get(i) != null) {
                        LOG.debug("  inputParameter #{}=\"{}\" ({})", i + 1, val,
                                  val.getClass().getName());

                        try {
                           // @by billh, for informix where clause "column=?",
                           // if we use setObject, the returned result is invalid,
                           // but if use setString, the returned result is OK.
                           // Tested even if a numeric column, to use setString is
                           // OK, which might occur when we could not get column
                           // type properly, e.g. a sql formula created in binding
                           if(informix && val instanceof String) {
                              ((PreparedStatement) stmt).setString(inIdx,
                                 (String) SQLTypes.convert(val, translations));
                           }
                           else if(db2 && val instanceof Number) {
                              ((PreparedStatement) stmt).
                                 setString(inIdx, Tool.toString(val));
                           }
                           else if(boolToStr && val instanceof Boolean) {
                              ((PreparedStatement) stmt).
                                 setString(inIdx, Tool.toString(val));
                           }
                           else if(strToBool && val instanceof String &&
                              (val.toString().equalsIgnoreCase("true") ||
                              val.toString().equalsIgnoreCase("false")))
                           {
                              ((PreparedStatement) stmt).setBoolean(inIdx,
                                 val.toString().equalsIgnoreCase("true"));
                           }
                           else {
                              ((PreparedStatement) stmt).setObject(inIdx,
                                 SQLTypes.convert(val, translations));
                           }
                        }
                        catch(Exception ex) {
                           try {
                             ((PreparedStatement) stmt).setObject(inIdx,
                                 SQLTypes.convert(val.toString(), translations));
                           }
                           catch(Exception ex2) {
                              throw ex;
                           }
                        }
                     }
                  }

                  // associate name with parameter so it's not order dependent
                  // since sybase drivers return the parameter in wrong order
                  if(nameFunc != null) {
                     ProcedureSQL procSQL = (ProcedureSQL) def;
                     ProcedureSQL.Parameter param = null;

                     for(; paramIdx < procSQL.getParameterCount(); paramIdx++) {
                        param = procSQL.getParameter(paramIdx);

                        if((param.inout == ProcedureSQL.IN ||
                            param.inout == ProcedureSQL.INOUT) &&
                           !param.typename.equals("REF CURSOR"))
                        {
                           break;
                        }

                        param = null;
                     }

                     if(hasNullParams && retry == 0) {
                        Integer inIndex = redefinedParams.get(param.name);

                        if(inIndex != null) {
                           inIdx = inIndex;
                        }
                        else {
                           inIdx = -1;
                        }
                     }

                     if(param != null && inIdx != -1) {
                        try {
                           nameFunc.invoke(stmt, inIdx, "@" + param.name);
                        }
                        catch(Throwable ex) {
                           LOG.error("Failed to set parameter name on statement: " +
                              param.name, ex);
                        }
                     }
                  }
               }

               long begin = System.currentTimeMillis();

               try {
                  if(isproc || vars.size() > 0) {
                     exeflag = ((PreparedStatement) stmt).execute();
                  }
                  else {
                     exeflag = stmt.execute(tsql);
                  }

                  break;
               }
               catch(SQLException ex) {
                  long end = System.currentTimeMillis();

                  querytimeout = timeout > 0 && timeoutsupported &&
                                 ((timeout * 1000) <= (end - begin));

                  boolean cancelled = JDBCUtil.isCancelled(ex, stmt);

                  // @by alam, fix bug1291109112159, for sql2000 where clause
                  // "column=?", if parameter type is boolean, boolean type will be
                  // convert to bit, bit is saved as 0/1 in sql2000, this may occur
                  // error. So if SQLException is caused by this reason, we just
                  // treat boolean parameter as string.
                  if(ex.getErrorCode() == 245) {
                     boolToStr = true;
                     retry++;
                  }
                  // @by yu, if we recieve "true" for a boolean parameter, try to
                  // setBoolean for access
                  else if(ex.getErrorCode() == -3030) {
                     strToBool = true;
                     retry++;
                  }
                  // For Bug #6561, Due to the change for bug1400830067750
                  // modern JDBC DB2 drivers which do support setObject
                  // for booleans are not functioning.  Revert the behavior back
                  // to 11.5 behavior if DB2 and -420 error code is thrown.
                  else if(xds.checkDatabaseType(JDBCDataSource.JDBC_DB2) &&
                     ex.getErrorCode() == -420)
                  {
                     boolToStr = false;
                     retry++;
                  }
                  else if(!querytimeout && !cancelled &&
                          isproc && hasNullParams && retry == 0)
                  {
                     retry++;
                  }
                  else {
                     // for same db, querytimeout will be checked as cancelled
                     if(querytimeout) {
                        LOG.debug("Query timeout in {} ms: {}", (end - begin), sql);
                        String vsobj = XUtil.VS_ASSEMBLY.get();
                        String tmsg = "Query timeout.";

                        if(vsobj != null) {
                           tmsg = "Query timeout: " + vsobj;
                        }

                        Exception e = new MessageException(tmsg, ex);

                        /**
                         * Older versions of MySQL ( < v5.6) do not properly optimize
                         * inner select queries, so alert the user of this issue
                         */
                        if(xds.checkDatabaseType(JDBCDataSource.JDBC_MYSQL) && max != 0) {
                           String msg = "For versions of MySQL before 5.6, make sure" +
                              " the Design mode sample data size is set to 0 as a " +
                              "non-zero value will cause queries to run much slower";
                           LOG.warn(msg);
                           e = new MessageException(msg, e);
                        }

                        throw e;
                     }

                     if(cancelled) {
                        LOG.info("Failed to execute statement: {}", ex.getMessage());
                        break;
                     }

                     String product = SQLHelper.getProductName(query.getDataSource());

                     if(SQLHelper.isExpException(product, ex)) {
                        throw new SQLExpressionFailedException(ex);
                     }

                     LOG.debug("Failed to execute statement: {}", ex.getMessage(), ex);
                     throw ex;
                  }
               }
            }

            if(querytimeout) {
               tnode = new JDBCTimeoutTableNode(outparams, conn, stmt, xds);
            }
            else if(isproc && xds.checkDatabaseType(JDBCDataSource.JDBC_ORACLE)) {
               if(cursor > 0) {
                  Object rc = ((CallableStatement) stmt).getObject(cursor);

                  if(!(rc instanceof ResultSet)) {
                     throw new SQLException("Procedure does not return a " +
                                            " resultset:" + sql);
                  }

                  tnode = new JDBCTableNode((ResultSet) rc, conn, stmt, xselect, xds,
                                            cacheNormalizer != null ?
                                               cacheNormalizer.getSortedColumnMap() : null);
                  conn = null; // connection released in JDBCTableNode
               }
               else {
                  tnode = new JDBCNoResultTableNode(outparams, conn, stmt, xds);
               }
            }
            else {
               // it's possible for update count to be returned from
               // getResultSet in stored procedures due to temp table.
               // This is not considered a real result set and should be ignored.
               // Note that we only take the first result set and are ignoring
               // multiple result sets that are possible from a stored procedure
               // This will require future enhancement.
               // fix customer bug1321415463766, if updCount != -1, we should
               // continue to getMoreResults
               int updCount = stmt.getUpdateCount();

               do {
                  resultSet = exeflag ? stmt.getResultSet() : null;

                  // For Sybase jconnect, not tested with others:
                  // update count: resultSet == null but updCount > -1
                  // result set: resultSet not null updCount == -1
                  if(resultSet == null && updCount == -1) {
                     break;
                  }

                  if(resultSet == null) {
                     exeflag = stmt.getMoreResults();
                     updCount = stmt.getUpdateCount();
                     continue;
                  }

                  if(xds2.supportsCancel() && QueryManager.isCancelled(stmt)) {
                     throw new CancelledException("Query is cancelled: " + sql);
                  }

                  tnode = new JDBCTableNode(resultSet, conn, stmt, xselect, xds,
                                            cacheNormalizer != null ?
                                               cacheNormalizer.getSortedColumnMap() : null);
                  break;
               }
               while(exeflag || updCount != -1);

               // @by larryl, if no resultset is returned, use the out parameters
               if(tnode == null) {
                  tnode = new JDBCNoResultTableNode(outparams, conn, stmt, xds);
               }

               // one connection can only support a limited number of open
               // cursors, for oracle it's 300, and for sql server it is 1.
               // we only allow one connection to be used in each cursor.
               // this way we will never run out of open cursor on a conn
               conn = null; // connection released in JDBCTableNode
            }

            if(tnode != null) {
               // @by gregm
               // Transfer ownership of query managers to JDBCTableNode so
               // that it can remove the statement when it completes
               tnode.addQueryManager(queryMgr);
               tnode.addQueryManager(queryMgr2);
               tnode.addQueryManager(queryMgr3);
               tnode.setAttribute("join.table.maxrows", query.getProperty("join.table.maxrows"));
            }

            if(!querytimeout) {
               long duration = System.currentTimeMillis() - startTime;
               LOG.debug("Finish query in {} ms: {}", duration, sql);
            }

            // the query might be cancelled without a sql exception, here we
            // double check query manager to not return an invalid node
            if(xds2.supportsCancel() && QueryManager.isCancelled(stmt)) {
               throw new CancelledException("Query is cancelled: " + sql);
            }

            return def.select(tnode);
         }
         catch(Exception ex) {
            berror = true;

            boolean cancelled = xds2.supportsCancel() &&
               QueryManager.isCancelled(stmt);

            try {
               if(stmt != null) {
                  stmt.close();
               }
            }
            catch(Exception ignore) {
            }

            // ignore exception if the query was cancelled explicitly add the
            // unique substring to isCancelled for other dbs if not included
            if(!JDBCUtil.isCancelled(ex, stmt)) {
               LOG.debug(String.format(
                  "Query cancelled, SQL: %s\nParameters: %s", sql, params));
            }

            // user cancelled?
            if(cancelled) {
               throw new CancelledException(ex);
            }

            throw ex;
         }
      }
      finally {
         if(tnode == null || berror) {
            // release the connection directly
            Tool.closeQuietly(conn);
         }
      }
   }

   /**
    * Applies any query filters to a SQL statement about to be executed.
    *
    * @param connection the database connection being used to execute the query.
    * @param sql        the SQL statement to be executed.
    * @param parameters the query parameters.
    * @param user       the user for whom the query is being executed.
    *
    * @return the new SQL statement to execute.
    *
    * @throws Exception if the query filter could not be applied.
    */
   private String applyQueryFilter(Connection connection, String sql,
                                   VariableTable parameters,
                                   Principal user) throws Exception
   {
      String filterProperty = SreeEnv.getProperty("jdbc.query.filter");
      String result = sql;

      if(filterProperty != null && !filterProperty.trim().isEmpty()) {
         for(String filterClass : filterProperty.trim().split(",")) {
            JDBCQueryFilter filter =
               (JDBCQueryFilter) Class.forName(
                  Tool.convertUserClassName(filterClass)).newInstance();
            String newSql = filter.filterQuery(
               xds, connection, result, parameters, user);

            if(newSql != null) {
               result = newSql;
            }
         }
      }

      return result;
   }

   /**
    * Reconstructs the Procedure call by using @param = ? for all not
    * null IN params, all INOUT and all OUT params.
    */
   private String reconstructNotNullSQL(String sql, List vars, List names,
                                        ProcedureSQL procSQL,
                                        Map<String, Integer> redefinedParams) {
      int outParam = sql.startsWith("{?") ? 1 : 0;
      List<String> parameters = new ArrayList<>();
      int index = outParam;

      for(int i = 0; i < procSQL.getParameterCount(); i++) {
         ProcedureSQL.Parameter par = procSQL.getParameter(i);
         boolean addParameter = true;

         for(int j = 0; j < vars.size(); j++) {
            Object val = vars.get(j);
            ProcedureSQL.Parameter inPar =
               procSQL.getParameter((String) names.get(j));

            if(par == inPar && par.inout == ProcedureSQL.IN) {
               // input parameter, do not add if null
               if(val == null) {
                  addParameter = false;
               }
            }
         }

         if(addParameter) {
            parameters.add("@" + par.name + " = ?");
            redefinedParams.put(par.name, ++index);
         }
      }

      String sqlStatement = "(";

      for(int i = 0; i < parameters.size(); i++) {
         if(i != (parameters.size() - 1)) {
            sqlStatement += parameters.get(i) + ", ";
         }
         else {
            sqlStatement += parameters.get(i);
         }
      }

      sqlStatement += ")";

      int open = sql.indexOf('(');
      int close = sql.indexOf(')');
      String beforeBrace = sql.substring(0, open);
      String afterBrace = sql.substring(close + 1, sql.length());

      if(parameters.size() > 0) {
         return (beforeBrace + sqlStatement + afterBrace);
      }
      else {
         return (beforeBrace + afterBrace);
      }
   }

   class JDBCTimeoutTableNode extends JDBCNoResultTableNode {
      public JDBCTimeoutTableNode(Vector outparams, Connection conn,
                                  Statement stmt, XDataSource xds)
         throws SQLException
      {
         super(outparams, conn, stmt, xds);
      }

      /**
       * Check if a table is a result of timeout.
       */
      @Override
      public boolean isTimeoutTable() {
         return true;
      }
   }

   class JDBCNoResultTableNode extends JDBCTableNode {
      public JDBCNoResultTableNode(Vector outparams, Connection conn, Statement stmt,
                                   XDataSource xds)
      {
         super();
         super.stmt = stmt;
         super.conn = conn;
         super.xds = xds;
         this.outparams = outparams;
         this.sqlTypesHelper = SQLTypes.getSQLTypes((JDBCDataSource) xds);
      }

      @Override
      public boolean next() {
         boolean result = idx++ < 1;

         if(!result) {
            this.close();
         }

         return result;
      }

      @Override
      public boolean rewind() {
         LOG.warn("Rewind is unsupported in JDBCNoResultTableNode");
         return false;
      }

      @Override
      public boolean isRewindable() {
         return false;
      }

      @Override
      public int getColCount() {
         return outparams.size();
      }

      @Override
      public XTableColumnCreator getColumnCreator(int col) {
         return XObjectColumn.getCreator();
      }

      @Override
      public String getName(int col) {
         return (String) ((Object[]) outparams.elementAt(col))[2];
      }

      @Override
      public Class getType(int col) {
         return Object.class;
      }

      @Override
      public int getSQLType(int col) {
         return (Integer) ((Object[]) outparams.elementAt(col))[1];
      }

      @Override
      public int getLength(int col) {
         return 10;
      }

      @Override
      public Object getObject(int col) {
         int pos = (Integer) ((Object[]) outparams.elementAt(col))[0];

         try {
            return sqlTypesHelper.getObject((CallableStatement) stmt,
                                            pos, getSQLType(col));
         }
         catch(Exception e) {
            LOG.error("Failed to get callable statement output parameter {}", pos, e);
            this.close();
            return null;
         }
      }

      int idx = 0;
      Vector outparams;
   }

   /**
    * Connect to the data source.
    * @param datasource the data source name.
    * @param params parameters for connection.
    */
   @Override
   @SuppressWarnings("SynchronizeOnNonFinalField")
   public void connect(XDataSource datasource, VariableTable params)
      throws Exception
   {
      if(datasource.isFromPortal()) {
         this.xds = (JDBCDataSource) datasource.clone();
      }
      else {
         this.xds =  (JDBCDataSource) ConnectionProcessor.getInstance().getDatasource(
            ThreadContext.getContextPrincipal(), datasource).clone();
      }

      this.sqlTypes = SQLTypes.getSQLTypes(this.xds);
      login = xds.getParameters() != null;

      if(params != null) {
         String name = xds.getFullName();
         String user = (String) params.get(XUtil.DB_USER_PREFIX + name);
         String passwd = (String) params.get(XUtil.DB_PASSWORD_PREFIX + name);

         synchronized(xds) {
            if((xds.getUser() == null || xds.getUser().length() == 0) &&
               user != null)
            {
               xds.setUser(user);
            }

            if((xds.getPassword() == null || xds.getPassword().length() == 0) &&
               passwd != null)
            {
               xds.setPassword(passwd);
            }
         }
      }
   }

   /**
    * Test the data source.
    * @param datasource the data source name.
    * @param params parameters for connection.
    */
   @Override
   public void testDataSource(XDataSource datasource,
                              VariableTable params) throws Exception
   {
      Connection conn = connect((JDBCDataSource) datasource.clone(), params);

      if(conn == null) {
         throw new SQLException("Failed to connect to datasource[" +
                                datasource.getFullName() + "]!");
      }

      try {
         Statement stmt = conn.createStatement();
         stmt.close();
      }
      finally {
         conn.close();
      }
   }

   /**
    * Build the meta data of this data source as a XNode tree. This
    * method will rebuild the meta data tree everytime it's called.
    * The meta data should be cached by the caller.
    * @param mtype meta data type. It it is not, the name of the mtype
    * node must be a valid table. A list of table columns is returned.
    * @return return the root node of the meta data tree.
    */
   @Override
   public XNode getMetaData(XNode mtype) throws Exception {
      // datasource name
      // TABLE
      // [catalog]
      // [schema]
      // table name
      // View
      // ...
      // PROCEDURE
      // [catalog]
      // [schema]
      // procedure name

      synchronized(metaLock) {
         Connection conn = null;

         String additional = null;

         if(mtype != null) {
            additional = (String) mtype.getAttribute("additional");
         }

         try {
            // this is a hack used to refresh database meta data
            if(mtype != null && mtype.getAttribute("reset") != null) {
               metaroot = null;
               mtype = null;
            }

            String tname = Thread.currentThread().getName();
            boolean refresh = tname.startsWith(REFRESH_META_DATA);

            // use cached value
            // fix bug1336124101581, if refresh MetaData, don't use cache
            if(!refresh && mtype == null && metaroot != null) {
               return metaroot;
            }

            // @by vincentx, 2004-09-29
            // do not wait to retry connection when getting metadata
            // @by billh, fix customer bug bug1288217465382. Actually when get
            // meta data, we do not need user info, but connection will be reused,
            // so we have to provide proper user info here
            conn = getConnection(xds, additional, ThreadContext.getContextPrincipal(),
               null, null);

            if(conn == null) {
               throw new RuntimeException("Can't get connection: " +
                  xds.getFullName());
            }

            DatabaseMetaData meta = conn.getMetaData();

            String queryType = mtype == null ?
               null : (String) mtype.getAttribute("type");

            if(mtype == null) {
               LOG.warn("Obtaining full meta-data tree. This use " +
                     "is deprecated. Get meta-data by catalog/schema.",
                  new Exception("Stack trace"));
               return getTableViewProcedureList();
            }
            else if("DBPROPERTIES".equals(queryType)) {
               return getDatabaseProperties(meta);
            }
            else if("TABLETYPES".equals(queryType)) {
               return getTableTypeList(meta, additional);
            }
            else if("SCHEMAS".equals(queryType)) {
               return getSchemaList(meta, additional);
            }
            else if(queryType != null && queryType.startsWith("SCHEMATABLES_")) {
               return getSchemaTableList(meta, mtype);
            }
            else if("SCHEMAPROCEDURES".equals(queryType)) {
               return getSchemaProcedureList(meta, mtype);
            }
            else if("KEYRELATION".equals(queryType)) {
               return getKeyRelationship(meta, mtype);
            }
            else if("PRIMARYKEY".equals(queryType)) {
               return getPrimaryKeys(meta, mtype);
            }
            else if("SQL".equals(queryType)) {
               return getSQLColumns(conn, mtype);
            }
            else if("PROCEDURE".equals(queryType)) {
               return getProcedureColumns(meta, mtype);
            }
            else if("scalar".equals(queryType)) {
               String quoteString = meta.getIdentifierQuoteString();
               XNode root = new XNode(xds.getFullName());
               root.setAttribute("identifierQuoteString", quoteString);
               // @by vincentx, 2004-09-23, fix bug1095664473035
               // choose sqlhelper according to Database Product Name
               root.setAttribute("DBProductName", meta.getDatabaseProductName());

               try {
                  int major = meta.getDatabaseMajorVersion();
                  int minor = meta.getDatabaseMinorVersion();
                  root.setAttribute("DBProductVersion", major + "." + minor);
               }
               catch(Throwable ex) {
                  // ignore it
                  root.setAttribute("DBProductVersion", "unknown");
               }

               return root;
            }
            else {
               return getTableColumns(conn, meta, mtype);
            }
         }
         finally {
            Tool.closeQuietly(conn);
         }
      }
   }

   /**
    * Release the get meta lock
    *
    * @param timeout wait timeout
    * @throws InterruptedException
    */
   public void waitMetaLock(int timeout) throws InterruptedException {
      metaLock.wait(timeout);
   }

   public XNode getRootMetaData(JDBCDataSource dataSource, String queryType) throws Exception {
      return getRootMetaData(dataSource, queryType, null);
   }

   public XNode getRootMetaData(JDBCDataSource dataSource, String queryType, String additional)
      throws Exception
   {
      XRepository repository = XFactory.getRepository();
      Object session = System.getProperty("user.name");
      XSessionManager.getSessionManager().bind(session);

      XNode query = new XNode();
      query.setAttribute("type", queryType);
      query.setAttribute("additional", additional);
      // get child meta-data through repository so that cache is used/updated
      return repository.getMetaData(session, dataSource, query, true, null);
   }

   /**
    * Determines if the database is MySQL version 5 or greater.
    *
    * @param meta the database meta-data.
    *
    * @return <tt>true</tt> if MySQL 5; <tt>false</tt> otherwise.
    *
    * @throws SQLException if a database error occurs.
    */
   private boolean isMySQL5(DatabaseMetaData meta) throws SQLException {
      return (xds.getDatabaseType() == JDBCDataSource.JDBC_MYSQL) &&
         (meta.getDatabaseMajorVersion() >= 5);
   }

   private boolean isMySQL(DatabaseMetaData meta) {
      return xds.getDatabaseType() == JDBCDataSource.JDBC_MYSQL;
   }

   /**
    * Determines if the database is Oracle BI.
    *
    * @return <tt>true</tt> if Oracle BI; <tt>false</tt> otherwise.
    */
   private boolean isOBIEE() {
      return xds.getDatabaseType() == JDBCDataSource.JDBC_OBIEE;
   }

   private boolean isDremio() {
      return xds.getDatabaseType() == JDBCDataSource.JDBC_DREMIO;
   }

   private boolean isPostgres() {
      return xds.getDatabaseType() == JDBCDataSource.JDBC_POSTGRESQL;
   }

   /**
    * Gets the table types supported by the database.
    *
    * @param meta the database meta-data.
    *
    * @return the table types.
    *
    * @throws SQLException if a database error occurs.
    */
   private String[] getTableTypes(DatabaseMetaData meta) throws SQLException {
      if(isDremio()) {
         return new String[] { "TABLE", "VIEW", "PROCEDURE" };
      }

      final List<String> all = new ArrayList<>(Arrays.asList(
         "EXTERNAL TABLE", "TABLE", "VIEW", "SYNONYM", "ALIAS",
         "MATERIALIZED VIEW", "BASE TABLE"));
      Set<String> types = new TreeSet<>(Comparator.comparingInt(all::indexOf));

      if(isVertica()) {
         all.add("SYSTEM TABLE");
      }

      if(isPostgres()) {
         all.add("PARTITIONED TABLE");
      }

      try(ResultSet results = meta.getTableTypes()) {
         while(results.next()) {
            String type = results.getString(1);
            // Sybase returns types padded with spaces at the end
            type = type == null ? null : type.trim();

            if(all.contains(type)) {
               types.add(type);
            }
         }
      }

      return types.toArray(new String[types.size()]);
   }

   private boolean isVertica() {
      JDBCDataSource jdbc = getDataSource();
      return jdbc != null && jdbc.getURL() != null && jdbc.getURL().startsWith("jdbc:vertica:");
   }

   /**
    * Gets top-level properties about the database.
    *
    * @param meta the database meta-data.
    *
    * @return the database properties.
    *
    * @throws SQLException if a database error occurs.
    */
   private XNode getDatabaseProperties(DatabaseMetaData meta)
      throws SQLException
   {
      ResultSet result = null;
      String db = xds.getDefaultDatabase();
      String defaultCatalog = null;
      String defaultSchema = null;

      try {
         schema = meta.supportsSchemasInDataManipulation();
      }
      catch(Exception ignore) {
      }

      boolean mysql5 = isMySQL5(meta);
      boolean obiee = isOBIEE();
      boolean sqlServer = xds.getDatabaseType() == JDBCDataSource.JDBC_SQLSERVER;

      // if user is not supported, don't qualify the name with database
      // @by jasons, except for MySQL 5, which switches the semantics of
      //             catalog and schema
      if(schema || mysql5) {
         try {
            catalog = meta.supportsCatalogsInDataManipulation();
         }
         catch(Exception ignore) {
         }

         try {
            // @by charvi, if the catalog separator is a space or an
            // empty string, set the catSep to null.
            String catSeparator = meta.getCatalogSeparator();

            if(catSeparator == null || catSeparator.equals("") ||
               catSeparator.equals(" "))
            {
               catSeparator = null;
            }

            catSep = catSeparator;
         }
         catch(Exception ignore) {
         }
      }

      if(obiee) {
         catalog = true;
         catSep = ".";
      }

      // @by larryl, if the default database is specified, the connection will
      // only return the tables for that database. We should not use catalog
      // in the table name.
      if(db != null) {
         catalog = false;
      }

      // fix bug1336124101581, since the xds is a clone object,
      // so we need get the option from the original object
      int toption = getTableNameOption(xds);

      // count number of catalogs
      if(catalog) {
         int cnt = 0;

         try {
            result = meta.getCatalogs();

            if(result != null) {
               while(result.next()) {
                  cnt++;
               }
            }
         }
         catch(Exception ignore) {
         }
         finally {
            if(result != null) {
               result.close();
            }
         }

         if((mysql5 || obiee || sqlServer) && cnt > 1) {
            if(db == null) {
               defaultCatalog = meta.getConnection().getCatalog();
            }
            else {
               defaultCatalog = db;
            }
         }

         switch(toption) {
         case JDBCDataSource.DEFAULT_OPTION:
            catalog = cnt > 1;
            break;
         case JDBCDataSource.CATALOG_SCHEMA_OPTION:
            catalog = cnt >= 1;
            break;
         case JDBCDataSource.SCHEMA_OPTION:
         case JDBCDataSource.TABLE_OPTION:
            catalog = false;
            break;
         default:
            throw new RuntimeException("Unsupported option found: " + toption);
         }
      }

      /*
       * getting schemas causes problem in remote odbc drivers (sql server)
       */
      // count number of schemas
      if(schema) {
         int cnt = 0;

         try {
            result = meta.getSchemas();

            if(result != null) {
               while(result.next()) {
                  cnt++;
               }
            }
         } catch (Exception ignore) {
         }
         finally {
            if(result != null) {
               result.close();
            }
         }

         if(cnt > 1) {
            Statement statement = null;
            ResultSet resultSet = null;

            try {
               if(xds.getDriver().contains("teradata")) {
                  statement = meta.getConnection().createStatement();
                  resultSet = statement.executeQuery("help session");

                  if(resultSet.next()) {
                     defaultSchema = resultSet.getString(5);
                  }
               }
               else if(xds.getDatabaseType() == JDBCDataSource.JDBC_ORACLE) {
                  statement = meta.getConnection().createStatement();
                  resultSet = statement.executeQuery(
                     "select sys_context('USERENV', 'CURRENT_SCHEMA') " +
                     "from dual");

                  if(resultSet.next()) {
                     defaultSchema = resultSet.getString(1);
                  }
               }
               else if(xds.getDatabaseType() == JDBCDataSource.JDBC_SQLSERVER) {
                  statement = meta.getConnection().createStatement();
                  resultSet = statement.executeQuery("select schema_name()");

                  if(resultSet.next()) {
                     defaultSchema = resultSet.getString(1);
                  }
               }
               else if(xds.getDatabaseType() == JDBCDataSource.JDBC_DB2) {
                  statement = meta.getConnection().createStatement();
                  resultSet = statement.executeQuery(
                     "select current_schema from sysibm.sysdummy1");

                  if(resultSet.next()) {
                     defaultSchema = resultSet.getString(1);
                  }
               }
               else if(xds.getDatabaseType() == JDBCDataSource.JDBC_CLOUDSCAPE ||
                  xds.getDatabaseType() == JDBCDataSource.JDBC_INGRES)
               {
                  if(getTableNameOption(xds) != JDBCDataSource.TABLE_OPTION) {
                     defaultSchema = meta.getUserName();

                     if(defaultSchema == null && xds.getUser() != null &&
                        !xds.getUser().isEmpty())
                     {
                        defaultSchema = xds.getUser();
                     }
                  }
               }
               else if(xds.getDatabaseType() == JDBCDataSource.JDBC_POSTGRESQL) {
                  defaultSchema = "public";
               }
            }
            catch(SQLException exc) {
               LOG.warn("Failed to get default schema name", exc);
            }
            finally {
               if(resultSet != null) {
                  try {
                     resultSet.close();
                  }
                  catch(SQLException ignore) {
                  }
               }

               if(statement != null) {
                  try {
                     statement.close();
                  }
                  catch(SQLException ignore) {
                  }
               }
            }
         }

         switch(toption) {
         case JDBCDataSource.DEFAULT_OPTION:
            schema = cnt > 1;
            break;
         case JDBCDataSource.CATALOG_SCHEMA_OPTION:
         case JDBCDataSource.SCHEMA_OPTION:
            schema = cnt >= 1;
            break;
         case JDBCDataSource.TABLE_OPTION:
            schema = false;
            break;
         default:
            throw new RuntimeException("Unsupported option found: " + toption);
         }
      }

      // fully qualified name does not work well in informix
      if(xds.getDatabaseType() == JDBCDataSource.JDBC_INFORMIX) {
         catalog = false;
      }

      XNode properties = new XMetaDataNode(xds.getFullName());

      properties.setAttribute("catalogSep", catSep);
      properties.setAttribute("hasCatalog", catalog + "");
      properties.setAttribute("hasSchema", schema + "");
      properties.setAttribute("supportCatalog", catalog + "");

      if(defaultCatalog != null) {
         properties.setAttribute("defaultCatalog", defaultCatalog);
      }

      if(defaultSchema != null) {
         properties.setAttribute("defaultSchema", defaultSchema);
      }

      return properties;
   }

   /**
    * Gets the list of table types supported by the database.
    *
    * @param meta the database meta-data.
    *
    * @return the table types.
    *
    * @throws Exception if the table types could not be loaded.
    */
   private XNode getTableTypeList(DatabaseMetaData meta, String additional) throws Exception {
      XNode root = getRootMetaData(getDataSource(), "DBPROPERTIES", additional);

      for(String type : getTableTypes(meta)) {
         root.addChild(new XMetaDataNode(type), false, false);
      }

      return root;
   }

   /**
    * Gets the list of schemas for the specified connection.
    *
    * @param meta the database meta-data.
    *
    * @return the schema tree.
    *
    * @throws Exception if an unhandled exception occurs.
    */
   private XNode getSchemaList(final DatabaseMetaData meta, String additional)
      throws Exception
   {
      XNode root = getRootMetaData(getDataSource(), "DBPROPERTIES", additional);

      // @by stephenwebster, Fix bug1405624532928
      // When using remote repository, the state of these variables is lost
      // between interactions with the server.  Retrieve them from the cache.
      schema = "true".equals(root.getAttribute("hasSchema"));
      catalog = "true".equals(root.getAttribute("hasCatalog"));

      boolean supportCatalog = false;

      if(catalog) {
         try {

            try(ResultSet results = meta.getCatalogs()) {
               while(results.next()) {
                  String catalogName = results.getString(1);

                  if(catalogName != null) {
                     XNode node = new XMetaDataNode(catalogName);
                     node.setAttribute("supportCatalog", "true");
                     node.setAttribute("catalog", catalogName);
                     node.setAttribute("catalogSep", catSep);
                     root.addChild(node, true, false);
                     supportCatalog = true;
                  }
               }
            }
         }
         catch(Exception exc) {
            // catalogs are not supported, continue
            LOG.debug("Failed to list database catalogs", exc);
         }
      }

      if(schema) {
         try {
            if(xds.getDatabaseType() == JDBCDataSource.JDBC_SQLSERVER) {
               Connection connection = meta.getConnection();
               String schemaTable = "INFORMATION_SCHEMA.SCHEMATA";
               String schemaColumn = "SCHEMA_NAME";
               String version = JDBCUtil.getSQLServerVersion(xds);

               if(version != null && version.endsWith("2000")) {
                  schemaTable = "INFORMATION_SCHEMA.TABLES";
                  schemaColumn = "TABLE_SCHEMA";
               }

               try(Statement statement = connection.createStatement()) {
                  if(supportCatalog) {
                     for(int i = 0; i < root.getChildCount(); i++) {
                        XNode parent = root.getChild(i);
                        ResultSet results = null;

                        // @by stephenwebster, Fix bug1403878104244
                        // Moved statement execution inside try/catch
                        // If the statement fails, we do not want to prevent
                        // from loading the rest of the schemas the user has
                        // access to.  Furthermore, we can remove the node
                        // from the tree as it will be empty.
                        try {
                           results = statement.executeQuery(
                              "select " + schemaColumn + " from \"" +
                                 parent.getName() + "\"." + schemaTable);

                           Set<String> processed = new HashSet<>();

                           while(results.next()) {
                              String schemaName = results.getString(1);
                              XNode node = new XMetaDataNode(schemaName);
                              node.setAttribute("schema", schemaName);
                              node.setAttribute("supportCatalog", "true");
                              node.setAttribute("catalog", parent.getName());
                              node.setAttribute("catalogSep", catSep);

                              if(!processed.contains(schemaName)) {
                                 processed.add(schemaName);
                                 parent.addChild(node, true, false);
                              }
                           }
                        }
                        catch(Exception schemaLoadException) {
                           // @by stephenwebster, see above
                           // remove blank nodes and reset index
                           root.removeChild(i);
                           i--;

                           if(LogManager.getInstance().isDebugEnabled(LOG.getName())) {
                              LOG.debug("Failed to list database schema",
                                      schemaLoadException);
                           }
                           else {
                              LOG.warn("Failed to list database schema: {}",
                                       schemaLoadException.getMessage());
                           }

                        }
                        finally {
                           if(results != null) {
                              results.close();
                           }
                        }
                     }
                  }
                  else {

                     try(ResultSet results = statement.executeQuery(
                        "select " + schemaColumn + " from " + schemaTable)) {
                        Set<String> processed = new HashSet<>();

                        while(results.next()) {
                           String schemaName = results.getString(1);
                           XNode node = new XMetaDataNode(schemaName);
                           node.setAttribute("schema", schemaName);
                           node.setAttribute("supportCatalog", "false");

                           if(!processed.contains(schemaName)) {
                              processed.add(schemaName);
                              root.addChild(node, true, false);
                           }
                        }
                     }
                  }
               }
            }
            else {

               try(ResultSet results = meta.getSchemas()) {
                  while(results.next()) {
                     XNode parent = root;
                     String schemaName = results.getString(1);

                     XNode node = new XMetaDataNode(schemaName);
                     node.setAttribute("schema", schemaName);

                     if(supportCatalog) {
                        if(xds.getDatabaseType() == JDBCDataSource.JDBC_SYBASE) {
                           // uses database/owner semantics for catalog/schema;
                           // all schemas should be added to each catalog
                           for(int i = 0; i < root.getChildCount(); i++) {
                              parent = root.getChild(i);
                              node = new XMetaDataNode(schemaName);
                              node.setAttribute("schema", schemaName);
                              node.setAttribute("supportCatalog", "true");
                              node.setAttribute("catalog", parent.getName());
                              node.setAttribute("catalogSep", catSep);
                              parent.addChild(node, true, false);
                           }

                           continue;
                        }
                        else {
                           String catalogName =
                              results.getMetaData().getColumnCount() < 2 ?
                                 null : results.getString(2);

                           if(catalogName != null) {
                              parent = root.getChild(catalogName);

                              if(parent == null) {
                                 parent = root;
                              }
                              else {
                                 node.setAttribute("supportCatalog", "true");
                                 node.setAttribute("catalog", catalogName);
                                 node.setAttribute("catalogSep", catSep);
                              }
                           }
                        }
                     }

                     parent.addChild(node, true, false);
                  }
               }
            }
         }
         catch(Exception exc) {
            // if schemas aren't supported, just return an empty node
            LOG.debug("Failed to list database schemas", exc);
         }
      }

      return root;
   }

   /**
    * Escapes the schema or catalog name for use when getting the tables or
    * procedures in a schema.
    *
    * @param name the name to escape.
    *
    * @return the escaped name.
    */
   private String escapeSchemaName(String name) {
      String result = null;

      if(name != null) {
         switch(xds.getDatabaseType()) {
         case JDBCDataSource.JDBC_SQLSERVER:
            result = name.replaceAll("\\[", "[[]").replaceAll("%", "[%]");
            break;

         default:
            result = name;
         }
      }

      return result;
   }

   /**
    * Gets the tables in a database schema.
    *
    * @param meta  the database meta-data.
    * @param mtype the meta-data query node.
    *
    * @return the table list.
    *
    * @throws Exception if tables could not be obtained.
    */
   private XNode getSchemaTableList(DatabaseMetaData meta, XNode mtype)
      throws Exception
   {
      String additional = null;

      if(mtype != null) {
         additional = (String) mtype.getAttribute("additional");
      }

      XNode root = getRootMetaData(getDataSource(), "DBPROPERTIES", additional);
      schema = "true".equals(root.getAttribute("hasSchema"));

      String catalogName = null;
      String escapedCatalogName = null;
      String schemaName = (String) mtype.getAttribute("schema");
      String escapedSchemaName = escapeSchemaName(schemaName);
      String[] types = { (String) mtype.getAttribute("tableType") };

      if("true".equals(mtype.getAttribute("supportCatalog"))) {
         catalogName = (String) mtype.getAttribute("catalog");
         escapedCatalogName = escapeSchemaName(catalogName);
      }
      else if(mtype.getAttribute("defaultCatalog") != null) {
         catalogName = (String) mtype.getAttribute("defaultCatalog");
         escapedCatalogName = escapeSchemaName(catalogName);
      }
      else if(root.getAttribute("defaultCatalog") != null) {
         catalogName = (String) root.getAttribute("defaultCatalog");
         escapedCatalogName = escapeSchemaName(catalogName);
      }

      if(schemaName == null && root.getAttribute("defaultSchema") != null) {
         schemaName = (String) root.getAttribute("defaultSchema");
         escapedSchemaName = escapeSchemaName(schemaName);
      }

      String tableNamePattern = null;

      // fix Bug #32207, set tableNamePattern to match all for mysql.
      //
      // the default value of the "nullNamePatternMatchesAll" connection property
      // changed to false in 6.0.2, so SQLException will be throwed if the tableNamePattern is null.
      //
      // and this property was removed since release 8.0.9, no SQLException will be throwed when
      // tableNamePattern is null, but SQLException be throwed because 'WHERE' have no predicates
      // sentence.
      if(isMySQL(meta)) {
         tableNamePattern = "%";
      }

      try(ResultSet results =
             meta.getTables(escapedCatalogName, escapedSchemaName, tableNamePattern, types))
      {
         while(results.next()) {
            XNode table = new XMetaDataNode(results.getString(3));
            String type = results.getString(4).trim();

            if(type == null) {
               throw new RuntimeException(
                  "Database driver not specifying TABLE_TYPE from " +
                     "DatabaseMetaData.getTables method.");
            }

            table.setAttribute("type", type.toUpperCase());

            if("true".equals(mtype.getAttribute("supportCatalog"))) {
               table.setAttribute("supportCatalog", "true");
               table.setAttribute("catalog", catalogName);
               table.setAttribute("catalogSep", catSep);
            }

            if(schema && schemaName != null) {
               table.setAttribute("schema", schemaName);
            }

            root.addChild(table, true, false);
         }
      }

      return root;
   }

   /**
    * Gets the procedures in a database schema.
    *
    * @param meta  the database meta-data.
    * @param mtype the meta-data query node.
    *
    * @return the procedure list.
    *
    * @throws Exception if the procedures could not be obtained.
    */
   private XNode getSchemaProcedureList(DatabaseMetaData meta, XNode mtype)
      throws Exception
   {
      String additional = null;

      if(mtype != null) {
         additional = (String) mtype.getAttribute("additional");
      }

      XNode root = getRootMetaData(getDataSource(), "DBPROPERTIES", additional);
      String catalogName = null;
      String escapedCatalogName = null;
      String schemaName = (String) mtype.getAttribute("schema");
      String escapedSchemaName = escapeSchemaName(schemaName);

      if("true".equals(mtype.getAttribute("supportCatalog"))) {
         catalogName =  (String) mtype.getAttribute("catalog");
         escapedCatalogName = escapeSchemaName(catalogName);
      }
      else if(mtype.getAttribute("defaultCatalog") != null) {
         catalogName = (String) mtype.getAttribute("defaultCatalog");
         escapedCatalogName = escapeSchemaName(catalogName);
      }

      if(schemaName == null && root.getAttribute("defaultSchema") != null) {
         schemaName = (String) root.getAttribute("defaultSchema");
         escapedSchemaName = escapeSchemaName(schemaName);
      }

      Set<String> procedureNames = new HashSet<>();

      try {
         try(ResultSet results =
                meta.getProcedures(escapedCatalogName, escapedSchemaName, null))
         {
            while(results.next()) {
               if(isThreadCancelled()) {
                  throw new CancelledException();
               }

               String name = results.getString(3).trim();

               // strip ;? from the name, added in SQL Server
               int idx = name.indexOf(';');

               if(idx > 0) {
                  name = name.substring(0, idx);
               }

               XNode table = new XMetaDataNode(name);
               boolean ret =
                  results.getInt(8) == DatabaseMetaData.procedureReturnsResult;
               table.setAttribute("type", "PROCEDURE");
               table.setAttribute("return", ret);

               if("true".equals(mtype.getAttribute("supportCatalog"))) {
                  table.setAttribute("supportCatalog", "true");
                  table.setAttribute("catalog", catalogName);
                  table.setAttribute("catalogSep", catSep);
               }

               if(schema && schemaName != null) {
                  table.setAttribute("schema", schemaName);
               }

               // package name used in oracle
               if(xds.checkDatabaseType(JDBCDataSource.JDBC_ORACLE)) {
                  String pkg = results.getString(1);

                  if(pkg != null) {
                     table.setAttribute("package", pkg);
                     table.setAttribute("procedure", name);
                     table.setName(pkg + "." + name);
                  }
               }

               if(!procedureNames.contains(table.getName())) {
                  root.addChild(table, true, false);
                  procedureNames.add(table.getName());
               }
            }
         }
      }
      catch(Exception exc) {
         // silently fail if procedures are not supported
         LOG.debug("Failed to get database procedures", exc);
      }

      return root;
   }

   /**
    * Get the table view and procedure list.
    *
    * @return the table, view, and procedure list.
    */
   private XNode getTableViewProcedureList() throws Exception {
      XRepository repository = XFactory.getRepository();
      Object session = System.getProperty("user.name");
      XSessionManager.getSessionManager().bind(session);

      XNode query = new XNode();
      query.setAttribute("type", "TABLETYPES");
      // get child meta-data through repository so that cache is used/updated
      XNode root =
         repository.getMetaData(session, getDataSource(), query, true, null);

      query = new XNode();
      query.setAttribute("type", "SCHEMAS");
      // get child meta-data through repository so that cache is used/updated
      XNode schemas =
         repository.getMetaData(session, getDataSource(), query, true, null);

      for(int i = 0; i < root.getChildCount(); i++) {
         XNode typeNode = root.getChild(i);

         if(schemas.getChildCount() > 0) {
            for(int j = 0; j < schemas.getChildCount(); j++) {
               typeNode.addChild(
                  (XNode) schemas.getChild(j).clone(), false, false);
            }
         }

         Deque<XNode> stack = new ArrayDeque<>();
         stack.addLast(typeNode);

         while(!stack.isEmpty()) {
            XNode node = stack.removeLast();

            if(node.getChildCount() == 0) {
               query = new XNode();
               query.setAttribute("type", "SCHEMATABLES_" + typeNode.getName());
               query.setAttribute("tableType", typeNode.getName());
               copyAttribute(node, query, "supportCatalog");
               copyAttribute(node, query, "catalog");
               copyAttribute(node, query, "catalogSep");
               copyAttribute(node, query, "schema");

               // get child meta-data through repository so that cache is
               // used/updated
               XNode tables = repository.getMetaData(
                  session, getDataSource(), query, true, null);

               for(int j = 0; j < tables.getChildCount(); j++) {
                  node.addChild(tables.getChild(j), false, false);
               }
            }
            else {
               for(int j = 0; j < node.getChildCount(); j++) {
                  stack.addLast(node.getChild(j));
               }
            }
         }
      }

      // if driver does not support getProcedures, don't throw an exception
      try {
         boolean added = false;
         XNode typeNode = new XMetaDataNode("PROCEDURE");

         for(int i = 0; i < schemas.getChildCount(); i++) {
            typeNode.addChild(
               (XNode) schemas.getChild(i).clone(), false, false);
         }

         Deque<XNode> stack = new ArrayDeque<>();
         stack.addLast(typeNode);

         while(!stack.isEmpty()) {
            XNode node = stack.removeLast();

            if(node.getChildCount() == 0) {
               query = new XNode();
               query.setAttribute("type", "SCHEMAPROCEDURES");
               copyAttribute(node, query, "supportCatalog");
               copyAttribute(node, query, "catalog");
               copyAttribute(node, query, "catalogSep");
               copyAttribute(node, query, "schema");

               // get child meta-data through repository so that cache is
               // used/updated
               XNode procedures = repository.getMetaData(
                  session, getDataSource(), query, true, null);

               for(int j = 0; j < procedures.getChildCount(); j++) {
                  node.addChild(procedures.getChild(j), false, false);
                  added = true;
               }
            }
            else {
               for(int j = 0; j < node.getChildCount(); j++) {
                  stack.addLast(node.getChild(j));
               }
            }
         }

         if(added) {
            root.addChild(typeNode, false, false);
         }
      }
      catch(SQLException ex) {
         LOG.warn("Driver does not support procedures (" +
            xds.getName() + "): " + xds.getDriver(), ex);
      }
      catch(CancelledException exc) {
         LOG.debug("Load meta-data cancelled", exc);
      }
      catch(Throwable ex) {
         LOG.error("Driver internal error: " + ex.getMessage(), ex);
      }

      return metaroot = root;
   }

   /**
    * Copies an attribute from one node to another, if it exists.
    *
    * @param from the source node.
    * @param to   the target node.
    * @param name the attribute name.
    */
   private void copyAttribute(XNode from, XNode to, String name) {
      Object value = from.getAttribute(name);

      if(value != null) {
         to.setAttribute(name, value);
      }
   }

   /**
    * Get table name option from the original Object.
    */
   private int getTableNameOption(XDataSource ds) {
      if(ds instanceof JDBCDataSource) {
         return ((JDBCDataSource) ds).getTableNameOption();
      }

      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      ds = registry.getDataSource(ds.getFullName());

      return ((JDBCDataSource) ds).getTableNameOption();
   }

   private boolean isThreadCancelled() {
      Thread thread = Thread.currentThread();
      return (thread instanceof GroupedThread) &&
         ((GroupedThread) thread).isCancelled();
   }

   /**
    * Get the user name to use for the connection.
    */
   private String getUser(DatabaseMetaData meta, XNode mtype) throws Exception {
      String user = (String) mtype.getAttribute("schema");

      if(user == null && schema) {
         XNode query = new XNode();
         query.setAttribute("type", "DBPROPERTIES");
         // get child meta-data through repository so that cache is used/updated
         XNode root = repository.getMetaData(session, getDataSource(), query, true, null);

         if(xds.getDatabaseType() == JDBCDataSource.JDBC_SYBASE) {
            user = "dbo";
         }
         else if(xds.getDatabaseType() == JDBCDataSource.JDBC_POSTGRESQL) {
            // @by davidd v11.4, Postgres' default schema is "public"
            user = "public";
         }
         else if(root.getAttribute("defaultSchema") != null) {
            user = (String) root.getAttribute("defaultSchema");
         }
         else if(meta.supportsSchemasInTableDefinitions() &&
                 meta.getUserName() != null)
         {
            user = meta.getUserName();
         }
         else if(xds.getUser() != null && !xds.getUser().trim().equals("")) {
            user = xds.getUser();
         }
      }

      if(user ==  null && xds.getDatabaseType() == JDBCDataSource.JDBC_POSTGRESQL) {
         // @by davidd v11.4, Postgres' default schema is "public"
         user = "public";
      }

      if(user != null) {
         if(xds.getDatabaseType() == JDBCDataSource.JDBC_ORACLE) {
            user = user.toUpperCase();
         }
         else {
            try(ResultSet schemas = meta.getSchemas()) {
               // correct case
               while(schemas.next()) {
                  if(user.equalsIgnoreCase(schemas.getString(1))) {
                     user = schemas.getString(1);
                     break;
                  }
               }
            }
         }
      }

      return user;
   }

   /**
    * Get the primary key and foreign key relationship.
    *
    * @param meta  the retrieved meta data.
    * @param mtype the meta-data query node.
    *
    * @return the relationship meta-data.
    *
    * @throws Exception if an unhandled error occurs.
    */
   private XNode getKeyRelationship(DatabaseMetaData meta, XNode mtype)
      throws Exception
   {
      String cat = (String) mtype.getAttribute("catalog");
      String user = getUser(meta, mtype);
      String name = mtype.getName();
      int dot = name.lastIndexOf('.');

      if(dot > 0) {
         name = name.substring(dot + 1);
      }

      XNode root = new XNode(name);
      int cnt = 0;

      try(ResultSet result = meta.getImportedKeys(cat, user, name)) {
         while(result != null && result.next()) {
            String pkTableCat = result.getString(1);
            String pkTableSchem = result.getString(2);
            String pkTableName = result.getString(3);
            String pkColumnName = result.getString(4);
            String fkTableCat = result.getString(5);
            String fkTableSchem = result.getString(6);
            String fkTableName = result.getString(7);
            String fkColumnName = result.getString(8);

            XNode keyNode = new XNode("ImportKey" + (cnt ++));

            keyNode.setAttribute("pkTableCat", pkTableCat);
            keyNode.setAttribute("pkTableSchem", pkTableSchem);
            keyNode.setAttribute("pkTableName", pkTableName);
            keyNode.setAttribute("pkColumnName", pkColumnName);
            keyNode.setAttribute("fkTableCat", fkTableCat);
            keyNode.setAttribute("fkTableSchem", fkTableSchem);
            keyNode.setAttribute("fkTableName", fkTableName);
            keyNode.setAttribute("fkColumnName", fkColumnName);

            root.addChild(keyNode, false, false);
         }
      }
      catch(SQLException ex) {// not supported, ignore
      }

      return root;
   }

   /**
    * Get the primary keys of a table.
    *
    * @param meta  the retrieved meta data.
    * @param mtype the meta-data query node.
    *
    * @return the primary keys.
    *
    * @throws Exception if an unhandled exception occurs.
    */
   private XNode getPrimaryKeys(DatabaseMetaData meta, XNode mtype)
      throws Exception
   {
      String cat = (String) mtype.getAttribute("catalog");
      String user = getUser(meta, mtype);
      String name = mtype.getName();
      int dot = name.lastIndexOf('.');

      if(dot > 0) {
         name = name.substring(dot + 1);
      }

      XNode root = new XNode(name);

      try(ResultSet result = meta.getPrimaryKeys(cat, user, name)) {
         while(result != null && result.next()) {
            String pkTableCat = result.getString(1);
            String pkTableSchem = result.getString(2);
            String pkTableName = result.getString(3);
            String pkColumnName = result.getString(4);
            XNode keyNode = new XNode();

            keyNode.setAttribute("pkTableCat", pkTableCat);
            keyNode.setAttribute("pkTableSchem", pkTableSchem);
            keyNode.setAttribute("pkTableName", pkTableName);
            keyNode.setAttribute("pkColumnName", pkColumnName);

            root.addChild(keyNode);
         }
      }
      catch(SQLException ex) {
         // not supported, ignore
      }

      return root;
   }

   /**
    * Get the sql columns.
    * @param conn, the connection used to retrieve meta data.
    * @param mtype, the retrieved meta data.
    */
   private XNode getSQLColumns(Connection conn, XNode mtype) throws Exception {
      long starttime = System.currentTimeMillis();
      String sql = (String) mtype.getAttribute("sql");
      LOG.debug("Start query: {}", sql.trim());
      XTypeNode root = new XTypeNode(mtype.getName());
      // make sure the variable doesn't cause syntax error
      VarSQL vars = new VarSQL();
      sql = vars.replaceVariables(sql, new VariableTable());

      try(PreparedStatement stmt = conn.prepareStatement(sql)) {
         LOG.debug("About to execute: {}", sql.trim());
         ResultSetMetaData meta = stmt.getMetaData();
         SQLTypes stypes = SQLTypes.getSQLTypes(xds);

         for(int i = 0; i < meta.getColumnCount(); i++) {
            String name = meta.getColumnName(i + 1);
            String alias = meta.getColumnLabel(i + 1);
            int type = meta.getColumnType(i + 1);
            XTypeNode node =
               XSchema.createPrimitiveType(stypes.convertToXType(type));

            if(alias != null) {
               name = alias;

               // quote is returned for quoted alias for some db (access)
               if(name.startsWith("\"") && name.endsWith("\"") ||
                  name.startsWith("'") && name.endsWith("'"))
               {
                  name = name.substring(1, name.length() - 1);
               }
            }

            assert node != null;
            node.setName(name);
            root.addChild(node, false, false);
         }
      }

      long duration = System.currentTimeMillis() - starttime;

      LOG.debug("Finish query in {} ms: {}", duration, sql);

      return root;
   }

   /**
    * Get the procedure columns.
    *
    * @param meta  the retrieved meta data.
    * @param mtype the meta-data query node.
    *
    * @return the columns.
    *
    * @throws Exception if an unhandled error occurs.
    */
   private XNode getProcedureColumns(DatabaseMetaData meta, XNode mtype)
      throws Exception
   {
      ResultSet result = null;
      XTypeNode root = null;
      String cat = (String) mtype.getAttribute("catalog");
      String user = getUser(meta, mtype);
      String name = mtype.getName();
      int dot = name.lastIndexOf('.');

      if(dot > 0) {
         name = name.substring(dot + 1);
      }

      try {
         if(xds.getDatabaseType() == JDBCDataSource.JDBC_ORACLE) {
            //for fetching the procedure under specified package
            String pkg = (String) mtype.getAttribute("package");

            if(pkg == null) {
               //for fetching the procedure under root
               pkg = "";
            }
            else {
               name = (String) mtype.getAttribute("procedure");
            }

            result = meta.getProcedureColumns(pkg, user, name, null);
         }
         else {
            result = meta.getProcedureColumns(cat, user, name, null);
         }

         root = new XTypeNode(mtype.getName());

         root.setAttribute("catalog", cat);
         root.setAttribute("package", mtype.getAttribute("package"));
         root.setAttribute("procedure", mtype.getAttribute("procedure"));
         root.setAttribute("catalogSep", mtype.getAttribute("catalogSep"));
         root.setAttribute("schema", user);

         XTypeNode procParam = new XTypeNode("Parameter");
         XTypeNode returnParam = new XTypeNode("Return");
         XTypeNode resultParam = new XTypeNode("Result");
         root.addChild(procParam, false, false);
         root.addChild(returnParam, false, false);
         root.addChild(resultParam, false, false);

         // Simon Liu: I wonder if it is an unimplemented feature in informix.
         // Whatever parameters, getProcedureColumns() in informix will return
         // null, while in other databases at least a ResultSet with 0 records
         // can be return.  2002/4/12
         if(result == null) {
            return root;
         }

         // used to generated unique names in case the children have same names
         int procCnt = 1;
         int rec = -1; // >= 0 if find a cursor record

         for(int cidx = 1; result.next(); cidx++) {
            String col = result.getString(4);
            int type = result.getShort(5);
            int sqltype = result.getShort(6);
            String typename = result.getString(7);
            int length = result.getInt(9);

            // a null column is added following a PL/SQL TABLE in Oracle
            if(col == null) {
               // return value has not name
               if(type == DatabaseMetaData.procedureColumnReturn) {
                  col = "column" + cidx;
               }
               else {
                  continue;
               }
            }

            // columns in a cursor, ignore
            if(xds.checkDatabaseType(JDBCDataSource.JDBC_ORACLE) &&
               typename != null && typename.equals("PL/SQL RECORD") &&
               // return cursor columns should be added to return type
               type != DatabaseMetaData.procedureColumnReturn)
            {
               rec = type;
               continue;
            }
            // if the in-out type changes, the record ends
            // this is not a perfect solution but could catch some cases
            else if(rec >= 0 && rec != type) {
               rec = -1;
            }

            // sybase & sql server adds '@' to the front of the name
            if(col.startsWith("@")) {
               col = col.substring(1);

               if(type == DatabaseMetaData.procedureColumnUnknown) {
                  type = DatabaseMetaData.procedureColumnIn;
               }
            }

            // @by vincentx, 2004-09-08, fix bug1094463522713
            // for SQLServer, replace DatabaseMetaData.procedureColumnInOut
            // with DatabaseMetaData.procedureColumnOut
            if(xds.checkDatabaseType(JDBCDataSource.JDBC_SQLSERVER) &&
               type == DatabaseMetaData.procedureColumnInOut)
            {
               type = DatabaseMetaData.procedureColumnOut;
            }

            // if there is a dot in the name, take the last part
            for(dot = col.indexOf('.'); dot >= 0; dot = col.indexOf('.')) {
               if(dot == col.length() - 1) {
                  col = col.substring(0, dot);
               }
               else {
                  col = col.substring(dot + 1);
               }
            }

            XTypeNode xnode;

            // @by charvi, fixed bug1102477573466
            // If the sql typename is "TIMESTAMP", then set
            // the sqlType to Types.TIMESTAMP.
            if(sqlTypes instanceof inetsoft.uql.jdbc.util.OracleSQLTypes &&
               typename.equals("TIMESTAMP"))
            {
               sqltype = Types.TIMESTAMP;
            }

            if(sqlTypes instanceof inetsoft.uql.jdbc.util.OracleSQLTypes &&
               "REF CURSOR".equals(typename))
            {
               xnode = sqlTypes.createTypeNode(col, (Integer) XUtil.field(
                  "oracle.jdbc.driver.OracleTypes", "CURSOR"), null);
            }
            // @by marblew, 2005-01-01, fix bug1106711509486
            else if(sqlTypes instanceof inetsoft.uql.jdbc.util.OracleSQLTypes &&
                    "BLOB".equals(typename)) {
               xnode = sqlTypes.createTypeNode(col, (Integer) XUtil.field(
                  "oracle.jdbc.driver.OracleTypes", "BLOB"), null);
            }
            else {
               xnode = sqlTypes.createTypeNode(col, sqltype, null);
            }

            xnode.setAttribute("type", type);
            xnode.setAttribute("typename", typename);
            xnode.setAttribute("length", length);

            // if found a cursor record, add the columns to the result set
            if(rec >= 0 && (type == DatabaseMetaData.procedureColumnInOut ||
               type == DatabaseMetaData.procedureColumnOut))
            {
               type = DatabaseMetaData.procedureColumnResult;
            }

            switch(type) {
            case DatabaseMetaData.procedureColumnUnknown:
               continue;
            case DatabaseMetaData.procedureColumnIn:
            case DatabaseMetaData.procedureColumnInOut:
            case DatabaseMetaData.procedureColumnOut:
               // make sure the names are unique
               while(procParam.getChild(xnode.getName()) != null) {
                  xnode.setName(col + (procCnt++));
               }

               procParam.addChild(xnode, false, false);
               break;
            case DatabaseMetaData.procedureColumnReturn:
               // make sure the names are unique
               while(returnParam.getChild(xnode.getName()) != null) {
                  xnode.setName(col + (procCnt++));
               }

               returnParam.addChild(xnode, false, false);
               break;
            case DatabaseMetaData.procedureColumnResult:
               // make sure the names are unique
               while(resultParam.getChild(xnode.getName()) != null) {
                  xnode.setName(col + (procCnt++));
               }

               resultParam.addChild(xnode, false, false);
               break;
            }
         }
      }
      finally {
         if(result != null) {
            result.close();
         }
      }

      return root;
   }

   /**
    * Get the table/view columns. [catalog, schema, table]
    * @param conn, the connection used to retrieve meta data.
    * @param meta, the retrieved meta data.
    */
   private XNode getTableColumns(Connection conn, DatabaseMetaData meta,
                                 XNode mtype) throws Exception {
      String cat = (String) mtype.getAttribute("catalog");
      boolean catalog = "true".equals(mtype.getAttribute("supportCatalog"));
      String user = getUser(meta, mtype);

      boolean mysql5 = isMySQL5(meta);

      if(mysql5 && cat == null) {
         cat = user;
      }

      if(mysql5 && mtype.getName().contains(".") && !mtype.getName().contains(XUtil.getQuote(xds))) {
         SQLHelper helper = SQLHelper.getSQLHelper(xds);
         mtype.setName(XUtil.quoteAlias(mtype.getName(), helper));
      }

      String driverName = meta.getDriverName();
      XTypeNode resultParam = new XTypeNode("Result");
      XTypeNode root;
      boolean isSynonym;
      boolean isTableNameChecked = false;
      String tablename;

      while(true) {
         tablename = mtype.getName();

         // @by larryl, metamatrix jdbc driver returns Products.ProductCode
         // as table name. Since we assume table name does not contain
         // dot, the Products would be treated as a catalog. To change the
         // code to support dot in table name is too pervasive so we just
         // catch it here so the meta data can be found. It seems the dot
         // causes no other problem other than getting the column list.
         if(!catalog && cat != null && !mysql5) {
            tablename = cat + "." + tablename;
         }

         // @by mikec, while we always add schema as prefix of table name for
         // Sybase if the schema is not dbo, but for retrieve table column info
         // the prefix should be removed.
         // @by larryl, some database (e.g. metamtrix) allows "." in table name.
         // we should only remove the string before '.' if it's a schema
         if(!xds.checkDatabaseType(JDBCDataSource.JDBC_CLOUDSCAPE) &&
            tablename.startsWith(user + "."))
         {
            tablename = tablename.substring(tablename.lastIndexOf('.') + 1);
         }

         // For marid db, should remove quote to get columns, using quote can not get columns.
         if(mysql5 && xds.getDriver().contains("mariadb")) {
            tablename = tablename.replace(XUtil.getQuote(xds), "");
         }

         root = new XTypeNode(tablename);
         root.setAttribute("catalog", cat);
         root.setAttribute("catalogSep", mtype.getAttribute("catalogSep"));
         root.setAttribute("schema", user);
         root.addChild(resultParam);
         isSynonym = true;

         Set<String> primaryKeys = new HashSet<>();
         Map<String, Vector<String[]>> importedKeys = new HashMap<>();

         if(driverName != null && !driverName.startsWith("JDBC-ODBC")) {
            ResultSet pkNames = null;

            try {
               // @by jamshedd check if this column is a primary key
               pkNames = meta.getPrimaryKeys(cat, user, tablename);

               while(pkNames != null && pkNames.next()) {
                  primaryKeys.add(pkNames.getString(4).trim());
               }
            }
            catch(Exception exc) {
               LOG.warn("Could not extract primary key info for " + tablename, exc);
            }
            finally {
               if(pkNames != null) {
                  pkNames.close();
               }
            }

            // @by jamshedd check to see if this key references a foreign
            // key. Note: SYBASE driver throws exceptions when you try
            // and find foreign key info for views
            ResultSet fkNames = null;

            try {
               fkNames = meta.getImportedKeys(cat, user, tablename);

               while(fkNames != null && fkNames.next()) {
                  String col = fkNames.getString(8).trim();
                  Vector<String[]> vec =
                     importedKeys.computeIfAbsent(col, k -> new Vector<>());

                  String forTable = fkNames.getString(3).trim();
                  String forCol = fkNames.getString(4).trim();
                  String[] kInfoA = {forTable, forCol};
                  vec.add(kInfoA);
               }
            }
            catch(Exception fke) {
               LOG.warn("Could not extract foreign key info for " + tablename, fke);
            }
            finally {
               if(fkNames != null) {
                  fkNames.close();
               }
            }
         }

         try(ResultSet result = meta.getColumns(cat, user, tablename, null)) {
            while(result != null && result.next()) {
               isSynonym = false;
               String col = result.getString(4).trim();
               int sqltype = result.getShort(5);
               String tname = result.getString(6);
               int length = result.getInt(7);
               XTypeNode node = sqlTypes.createTypeNode(col, sqltype, tname);
               boolean isPrimary = primaryKeys.contains(col);
               Vector foreignKeys = importedKeys.get(col);

               node.setAttribute("length", length);
               node.setAttribute("PrimaryKey", isPrimary);

               if(foreignKeys != null) {
                  node.setAttribute("ForeignKey", foreignKeys);
               }

               if(resultParam.getChildIndex(node) < 0) {
                  resultParam.addChild(node);
               }
            }
         }
         catch(Exception e) {
            // to avoid a strange exception from db2:
            // [IBM][JDBC Driver] CLI0601E  ...... SQLSTATE=S1000
            if(!xds.checkDatabaseType(JDBCDataSource.JDBC_DB2) ||
               !e.getMessage().contains("CLI0601E"))
            {
               throw e;
            }
         }

         // begin to check whether table name is case insensitive.
         // for example: excute query "select * from employee" (in db2).
         // If database only has table EMPLOYEE,
         // no result will return from the database.
         // added by peter 2002-07-31.(bug1028026035015)
         if(isSynonym && !isTableNameChecked) {
            String[] types = {"TABLE", "VIEW", "MATERIALIZED VIEW"};

            try {
               // check for current schema first, use it if found
               if(xds.checkDatabaseType(JDBCDataSource.JDBC_DB2)) {
                  try(ResultSet result =
                         meta.getTables(cat, user == null ? null : user.toUpperCase(),
                                        null, types))
                  {
                     boolean found = false;

                     while(result != null && result.next()) {
                        String tableName = result.getString(3);
                        String schema = result.getString(2);

                        if(tableName.equalsIgnoreCase(tablename)) {
                           mtype.setName(tableName);
                           user = schema.toUpperCase();
                           found = true;
                           break;
                        }
                     }

                     if(found) {
                        isTableNameChecked = true;
                        continue;
                     }
                  }
               }

               // check from all schema
               boolean found = false;

               try(ResultSet result = meta.getTables(cat, null, null, types)) {
                  while(result != null && result.next()) {
                     String tableName = result.getString(3);
                     String schema = null;

                     if(xds.checkDatabaseType(JDBCDataSource.JDBC_DB2)) {
                        schema = result.getString(2);
                     }

                     if(tableName.equalsIgnoreCase(tablename)) {
                        mtype.setName(tableName);

                        if(user != null && user.equalsIgnoreCase(schema)) {
                           user = schema;
                        }

                        found = true;
                        break;
                     }
                  }
               }

               if(found) {
                  isTableNameChecked = true;
                  continue;
               }
            }
            catch(Exception e) {
               // to avoid a strange exception from db2:
               // [IBM][JDBC Driver] CLI0601E  ...... SQLSTATE=S1000
               if(!xds.checkDatabaseType(JDBCDataSource.JDBC_DB2) ||
                  !e.getMessage().contains("CLI0601E"))
               {
                  throw e;
               }
            }
         }

         break; // break the while loop
      }

      if(isSynonym) {
         String sql = "select t.table_name, t.owner " +
                      "from all_tables t, all_synonyms s " +
                      "where s.synonym_name = ? " +
                      "and t.table_name = s.table_name " +
                      "and t.owner = s.table_owner";

         try(PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tablename);

            try(ResultSet result = stmt.executeQuery()) {
               if(result.next()) {
                  String tname = result.getString(1);
                  String tuser = result.getString(2);

                  try(ResultSet res = meta.getColumns("", tuser, tname, null)) {
                     while(res.next()) {
                        String col = res.getString(4);
                        int sqltype = res.getShort(5);
                        int length = res.getInt(7);
                        XTypeNode node = sqlTypes.createTypeNode(col, sqltype, null);

                        node.setAttribute("length", length);

                        resultParam.addChild(node);
                     }
                  }
               }
               else {
                  sql = "select t.view_name, t.owner " +
                        "from all_views t, all_synonyms s " +
                        "where s.synonym_name = ? " +
                        "and t.view_name = s.table_name " +
                        "and t.owner = s.table_owner";

                  try(PreparedStatement stmt2 = conn.prepareStatement(sql)) {
                     stmt2.setString(1, tablename);

                     try(ResultSet result2 = stmt2.executeQuery()) {
                        if(result2.next()) {
                           String tname = result2.getString(1);
                           String tuser = result2.getString(2);

                           try(ResultSet res = meta.getColumns("", tuser, tname, null)) {
                              while(res.next()) {
                                 String col = res.getString(4);
                                 int sqltype = res.getShort(5);
                                 int length = res.getInt(7);
                                 XTypeNode node = sqlTypes.createTypeNode(col, sqltype,
                                                                          null);

                                 node.setAttribute("length", length);

                                 resultParam.addChild(node);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
         catch(Exception ignore) {
            // do not show exception for synonym
         }
      }

      return root;
   }

   /**
    * Close the data source connection.
    */
   @Override
   public void close() throws Exception {
   }

   /**
    * Connect to a database.
    */
   @SuppressWarnings("MagicConstant")
   public static Connection connect(JDBCDataSource xds, VariableTable params)
      throws Exception
   {
      Connection conn = null;

      try {
         conn = connect0(xds, params);
      }
      catch(Exception e) {
         // For databases using Vault database secrets engine,
         // credentials may have expired, so refresh credentials and retry.
         if(Tool.isVaultDatabaseSecretsEngine(SQLHelper.getProductName(xds))) {
            Tool.refreshDatabaseCredentials(xds);
            conn = connect0(xds, params);
         }
      }

      return conn;
   }

   /**
    * Connect to a database.
    */
   @SuppressWarnings("MagicConstant")
   public static Connection connect0(JDBCDataSource xds, VariableTable params)
      throws Exception
   {
      if(!isDriverAvailable(Tool.convertUserClassName(xds.getDriver()))) {
         throw new ClassNotFoundException(Catalog.getCatalog().getString(
            "common.datasource.classNotFound" ,xds.getDriver()));
      }

      String url = xds.getURL();
      String user = xds.isRequireLogin() ? xds.getUser() : null;
      String passwd = xds.isRequireLogin() ? xds.getPassword() : null;
      String name = xds.getFullName();

      if(user == null || user.length() == 0) {
         user = params != null ?
            (String) params.get(XUtil.DB_USER_PREFIX + name) : "";

         if(user == null) {
            user = "";
         }
      }

      if(passwd == null || passwd.length() == 0) {
         passwd = params != null ?
            (String) params.get(XUtil.DB_PASSWORD_PREFIX + name) : "";

         if(passwd == null) {
            passwd = "";
         }
      }

      Connection conn = null;

      try {
         Properties prop = new Properties();

         if(user.length() > 0) {
            prop.put("user", user);
         }

         if(passwd.length() > 0) {
            prop.put("password", passwd);
         }

         if(xds.getDatabaseType() == JDBCDataSource.JDBC_INFORMIX) {
            prop.put("DELIMIDENT", "Y");
         }

         if(xds.getDatabaseType() == JDBCDataSource.JDBC_SQLSERVER) {
            String version = JDBCUtil.getSQLServerVersion(xds);

            if(version != null && version.endsWith("2000")) {
               prop.put("SelectMethod", "Cursor");
            }
         }

         Driver driver = getDriver(xds.getDriver());

         if(driver != null) {
            conn = driver.connect(url, prop);
         }

         if(conn == null) {
            conn = getJDBCConnection(url, prop);
         }

         if(xds.getTransactionIsolation() >= 0) {
            try {
               conn.setTransactionIsolation(xds.getTransactionIsolation());
            }
            catch(Exception ex) {
               LOG.debug("Failed to set transaction isolation", ex);
            }
         }

         String db = xds.getDefaultDatabase();

         if(db != null && db.length() > 0) {
            try {
               conn.setCatalog(Tool.convertUserParameter(db));
            }
            catch(Exception ignore) {
            }
         }
      }
      catch(SQLException sqle) {
         throw sqle;
      }

      try {
         conn.setAutoCommit(false);
      }
      catch(Exception ex) {
         LOG.debug("Failed to disable auto-commit", ex);
      }

      return conn;
   }

   /**
    * Get a connection from connection pool.
    */
   public Connection getConnection(JDBCDataSource xds, Principal user)
      throws Exception
   {
      return getConnection(xds, user, null, null);
   }

   private Connection getConnection(JDBCDataSource xds, Principal user,
                                    String userName, String password)
      throws Exception
   {
      return getConnection(xds, null, user, userName, password);
   }


   private Connection getConnection(JDBCDataSource xds, String additional, Principal user,
                                    String userName, String password)
      throws Exception
   {
      Connection connection;
      xds = (JDBCDataSource) ConnectionProcessor.getInstance().getDatasource(user, xds, additional).clone();

      if(xds.isRequireLogin()) {
         if(StringUtils.isEmpty(xds.getUser())) {
            if(StringUtils.isEmpty(userName) && user instanceof XPrincipal) {
               Object usernameParam = ((XPrincipal) user).getParameter(
                  XUtil.DB_USER_PREFIX + xds.getFullName());

               if(usernameParam != null) {
                  userName = usernameParam.toString();
               }
            }

            xds.setUser(userName);
         }

         if(StringUtils.isEmpty(xds.getPassword())) {
            if(StringUtils.isEmpty(password) && user instanceof XPrincipal) {
               Object passwordParam = ((XPrincipal) user).getParameter(
                  XUtil.DB_PASSWORD_PREFIX + xds.getFullName());

               if(passwordParam != null) {
                  password = passwordParam.toString();
               }
            }

            xds.setPassword(password);
         }
      }

      DataSource ds = getConnectionPoolFactory().getConnectionPool(xds, user);

      // ds.getConnection(userName, password) is not supported for HikariDataSource
      if(userName == null || ds instanceof HikariDataSource) {
         connection = ds.getConnection();
      }
      else {
         connection = ds.getConnection(userName, password);
      }

      return connection;
   }

   /**
    * Get a connection from connection pool.
    *
    * @param params parameters for query.
    * @param user a Principal object that identifies the user for whom the
    *        connection is being obtained.
    */
   @SuppressWarnings("SynchronizeOnNonFinalField")
   private Connection getConnection(VariableTable params, Principal user)
      throws Exception
   {
      Connection conn;

      // @by larryl, optimization, if not passing the user id and password
      // in from report parameter, no need to check for them in the params
      if(!login) {
         return getConnection(xds, user);
      }

      synchronized(xds) {
         String userName = xds.getUser();
         String password = xds.getPassword();
         String name = xds.getFullName();

         if(userName == null || userName.isEmpty()) {
            try {
               userName = params != null ?
                  (String) params.get(XUtil.DB_USER_PREFIX + name) : "";
            }
            catch(Exception ex) {
               userName = null;
            }
         }
         else {
            userName = null;
         }

         if(password == null || password.isEmpty()) {
            try {
               password = params != null ?
                  (String) params.get(XUtil.DB_PASSWORD_PREFIX + name) : "";
            }
            catch(Exception e) {
               password = null;
            }
         }
         else {
            password = null;
         }

         conn = getConnection(xds, user, userName, password);
      }

      return conn;
   }

   public void resetConnection() {
      try {
         reset();
         xds = null;
      }
      catch(Exception ex) {
         LOG.error("Failed to reset JDBC handler on data source change", ex);
      }
   }

   /**
    * Reset the connection pool by removing connections of all data sources.
    */
   @Override
   public void reset() {
      super.reset();
      metaroot = null;

      if(xds != null) {
         AssetEntry entry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, xds.getFullName(), null);
         DataSourceRegistry.getRegistry().removeCacheEntry(entry);
      }

      JDBCUtil.clearTableMeta();
   }

   /**
    * Get data source.
    */
   public JDBCDataSource getDataSource() {
      return xds;
   }

   /**
    * Determines if the specified JDBC driver class is available.
    *
    * @param className the driver class name.
    *
    * @return <tt>true</tt> if available; <tt>false</tt> otherwise.
    */
   public static boolean isDriverAvailable(String className) {
      return Drivers.getInstance().getDriverClassLoader(className, null) != null;
   }

   /**
    * Gets the list of drivers that are available.
    *
    * @return the driver class names. This will never be <tt>null</tt>.
    */
   public static String[] getDrivers() {
      Set<String> driverClasses = new TreeSet<>(Drivers.getInstance().getDrivers());
      return driverClasses.toArray(new String[0]);
   }

   /**
    * Gets the JDBC driver of the specified class.
    *
    * @param className the driver class name.
    *
    * @return the driver instance.
    *
    * @throws Exception if the driver could not be loaded.
    */
   public static Driver getDriver(String className) throws Exception {
      Driver driver = null;

      if(className.equals("org.apache.hadoop.hive.jdbc.HiveDriver") ||
         className.equals("org.apache.hive.jdbc.HiveDriver"))
      {
         // @by jasonshobe, bug #2957: wrap Hive driver for JDBC compliance
         className = "inetsoft.uql.jdbc.util.HiveWrapperDriver";
      }

      ClassLoader loader = Drivers.getInstance().getDriverClassLoader(className, null);

      if(loader != null) {
         Class<?> driverClass = loader.loadClass(className);
         driver = (Driver) driverClass.getConstructor().newInstance();
      }

      return driver;
   }

   /**
    * Set driver class for target HikariDataSource.
    */
   public static void setDriverClassName(HikariConfig ds, String driverClass) {
      try {
         ds.setDriverClassName(driverClass);
      }
      catch(Exception ex) {
         // use class loader of the specific JDBC driver.
         ClassLoader oloader = Thread.currentThread().getContextClassLoader();
         ClassLoader loader = Drivers.getInstance().getDriverClassLoader(driverClass, null);
         Thread.currentThread().setContextClassLoader(loader);

         try {
            ds.setDriverClassName(driverClass);
         }
         finally {
            Thread.currentThread().setContextClassLoader(oloader);
         }
      }
   }

   /**
    * Gets a JDBC connection.
    *
    * @param url  the JDBC URL of the database.
    * @param info the connection information.
    *
    * @return a connection to the database.
    *
    * @throws SQLException if a database error occurs.
    */
   public static Connection getJDBCConnection(String url, Properties info)
      throws SQLException
   {
      return Drivers.getInstance().getDriver(url).connect(url, info);
   }

   /**
    * Gets a JDBC connection.
    *
    * @param url      the JDBC URL of the database.
    * @param user     the user name.
    * @param password the password.
    *
    * @return a connection to the database.
    *
    * @throws SQLException if a database error occurs.
    */
   public static Connection getJDBCConnection(String url, String user,
                                              String password)
      throws SQLException
   {
      Properties info = new Properties();

      if(user != null) {
         info.setProperty("user", user);
      }

      if(password != null) {
         info.setProperty("password", password);
      }

      return getJDBCConnection(url, info);
   }

   /**
    * Gets the connection pool for a data source.
    *
    * @param jdbcDataSource the JDBC data source.
    * @param principal      a principal identifying the user for whom the pool
    *                       is being obtained.
    *
    * @return the connection pool.
    */
   public static DataSource getConnectionPool(JDBCDataSource jdbcDataSource,
                                              Principal principal)
   {
      return getConnectionPoolFactory().getConnectionPool(jdbcDataSource, principal);
   }

   /**
    * Gets the connection pool factory.
    *
    * @return the connection pool factory.
    */
   public static ConnectionPoolFactory getConnectionPoolFactory() {
      ConnectionPoolFactory factory;
      POOL_LOCK.readLock().lock();

      try {
         factory = ConfigurationContext.getContext().get(POOL_KEY);
      }
      finally {
         POOL_LOCK.readLock().unlock();
      }

      if(factory == null) {
         POOL_LOCK.writeLock().lock();

         try {
            factory = ConfigurationContext.getContext().get(POOL_KEY);

            if(factory == null) {
               String className = SreeEnv.getProperty(
                  "inetsoft.uql.jdbc.ConnectionPoolFactory");

               if(className != null) {
                  try {
                     factory = (ConnectionPoolFactory)
                        Class.forName(className).newInstance();
                  }
                  catch(Exception e) {
                     LOG.warn("Failed to instantiate custom connection pool factory: " +
                        className, e);
                  }
               }

               if(factory == null) {
                  className = SreeEnv.getProperty("jdbc.connection.pool");

                  if(className != null && !className.isEmpty() && !className.equals("false")) {
                     if("inetsoft.uql.jdbc.TomcatConnectionPool".equals(className)) {
                        factory = new JNDIConnectionPoolFactory(
                           JNDIConnectionPoolFactory.Type.TOMCAT);
                     }
                     else if("inetsoft.uql.jdbc.WebLogicConnectionPool".equals(className)) {
                        factory = new JNDIConnectionPoolFactory(
                           JNDIConnectionPoolFactory.Type.WEBLOGIC);
                     }
                     else if("inetsoft.uql.jdbc.WebSphereConnectionPool".equals(className)) {
                        factory = new JNDIConnectionPoolFactory(
                           JNDIConnectionPoolFactory.Type.WEBSPHERE);
                     }
                     else {
                        LOG.warn(
                           "Deprecated connection pool implementation being used");

                        try {
                           factory = new LegacyConnectionPoolFactory();
                        }
                        catch(Exception e) {
                           LOG.warn("Failed to instantiate legacy connection pool factory", e);
                        }
                     }
                  }

                  if(factory == null) {
                     factory = new DefaultConnectionPoolFactory();
                  }
               }
            }

            ConfigurationContext.getContext().put(POOL_KEY, factory);
         }
         finally {
            POOL_LOCK.writeLock().unlock();
         }
      }

      return factory;
   }

   public static final String REFRESH_META_DATA = "refreshMetaData";

   private JDBCDataSource xds = null;
   private SQLTypes sqlTypes = null;
   private Object metaLock = new Object();
   private transient boolean login = false; // require login
   private transient boolean schema = false;
   private transient boolean catalog = false;
   private transient String catSep = ".";
   private transient XNode metaroot = null; // cached meta data

   private static final String POOL_KEY =
      JDBCHandler.class.getName() + ".connectionPoolFactory";
   private static final ReadWriteLock POOL_LOCK = new ReentrantReadWriteLock(true);

   private static final Logger LOG =
      LoggerFactory.getLogger(JDBCHandler.class);
}
