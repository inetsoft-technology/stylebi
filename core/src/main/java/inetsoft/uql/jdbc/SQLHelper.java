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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;

import java.awt.*;
import java.io.InputStream;
import java.security.Principal;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.*;

/**
 * Helper class of UniformSQL. It defines the API for generating SQL
 * from a SQL definition.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class SQLHelper implements KeywordProvider {
   /**
    * Mirror table.
    */
   public static final String MIRROR_TABLE = "mirror_table";
   /**
    * Concatenation table.
    */
   public static final String CONCATENATION_TABLE = "concatenation_table";
   /**
    * Union table.
    */
   public static final String UNION_TABLE = "union_table";
   /**
    * Intersect table.
    */
   public static final String INTERSECT_TABLE = "intersect_table";
   /**
    * Minus table.
    */
   public static final String MINUS_TABLE = "minus_table";
   /**
    * Distinct orderby.
    */
   public static final String DISTINCT_ORDERBY = "distinct_orderby";
   /**
    * Aggregate orderby.
    */
   public static final String AGGREGATE_ORDERBY = "aggregate_orderby";
   /**
    * Condition subquery.
    */
   public static final String CONDITION_SUBQUERY = "condition_subquery";
   /**
    * From subquery.
    */
   public static final String FROM_SUBQUERY = "from_subquery";
   /**
    * Where subquery.
    */
   public static final String WHERE_SUBQUERY = "where_subquery";
   /**
    * Full outer join.
    */
   public static final String FULL_OUTERJOIN = "full_outerjoin";
   /**
    * Hidden column orderby.
    */
   public static final String HIDDEN_ORDERBY = "hidden_orderby";
   /**
    * Aggregate column orderby.
    */
   public static final String AGGREGATE_COLUMN_ORDERBY = "aggregate_column_orderby";
   /**
    * Expression group by.
    */
   public static final String EXPRESSION_GROUPBY = "expression_groupby";
   /**
    * Expression column groupby.
    */
   public static final String EXPRESSION_COLUMN_GROUPBY = "expression_column_groupby";
   /**
    * Max rows in output.
    */
   public static final String MAXROWS = "maxrows";
   /**
    * Multi distinct.
    */
   public static final String MULTI_DISTINCT = "multi_distinct";
   /**
    * Check if grouped column is ordered in output (without explicit order by).
    */
   public static final String GROUP_ORDERED = "group_ordered";

   /**
    * Year date function.
    */
   public static final String YEAR_FUNCTION = "year";
   /**
    * Full year date function.
    */
   public static final String FULL_YEAR_FUNCTION = "fullyear";
   /**
    * Full quarter date function.
    */
   public static final String FULL_QUARTER_FUNCTION = "fullquarter";
   /**
    * Quarter date function.
    */
   public static final String QUARTER_FUNCTION = "quarter";
   /**
    * Quarter date part function.
    */
   public static final String QUARTER_PART_FUNCTION = "quarter_part";
   /**
    * Full month date function.
    */
   public static final String FULL_MONTH_FUNCTION = "fullmonth";
   /**
    * Month date function.
    */
   public static final String MONTH_FUNCTION = "month";
   /**
    * Month date part function.
    */
   public static final String MONTH_PART_FUNCTION = "month_part";
   /**
    * Week of year function.
    */
   public static final String WEEK_FUNCTION = "week";
   /**
    * Week of year part function.
    */
   public static final String WEEK_PART_FUNCTION = "week_part";
   /**
    * Full day of year function.
    */
   public static final String FULL_DAY_FUNCTION = "fullday";
   /**
    * Day of month function.
    */
   public static final String DAY_FUNCTION = "day";
   /**
    * Day of month part function.
    */
   public static final String DAY_PART_FUNCTION = "day_part";
   /**
    * Hour function.
    */
   public static final String HOUR_FUNCTION = "hour";
   /**
    * Minute function.
    */
   public static final String MINUTE_FUNCTION = "minute";
   /**
    * Minute part function.
    */
   public static final String MINUTE_PART_FUNCTION = "minute_part";
   /**
    * Second function.
    */
   public static final String SECOND_FUNCTION = "second";
   /**
    * Second function.
    */
   public static final String SECOND_PART_FUNCTION = "second_part";
   /**
    * Day of week function.
    */
   public static final String DAY_OF_WEEK_FUNCTION = "dayofweek";
   /**
    * Hour of day function.
    */
   public static final String HOUR_PART_FUNCTION = "hour_part";
   /**
    * Hour function for time type.
    */
   public static final String TIME_HOUR_FUNCTION = "time_hour";
   /**
    * Minute function for time type.
    */
   public static final String TIME_MINUTE_FUNCTION = "time_minute";
   /**
    * Second function for time type.
    */
   public static final String TIME_SECOND_FUNCTION = "time_second";
   /**
    * Hour of day function of db2 time type.
    */
   public static final String HOUR_PART_FUNCTION2 = "hour_part2";
   /**
    * Minute part function for db2 time type.
    */
   public static final String MINUTE_PART_FUNCTION2= "minute_part2";
   /**
    * Second functionfor db2 time type.
    */
   public static final String SECOND_PART_FUNCTION2 = "second_part2";
   /**
    * Hour function for time type of db2.
    */
   public static final String TIME_HOUR_FUNCTION2 = "time_hour2";
   /**
    * Minute function for time type of db2.
    */
   public static final String TIME_MINUTE_FUNCTION2 = "time_minute2";
   /**
    * Second function for time type of db2.
    */
   public static final String TIME_SECOND_FUNCTION2 = "time_second2";
   /**
    * None date function.
    */
   public static final String NONE_FUNCTION = "none";

   /**
    * Get the product name.
    * @param source the specified data source.
    * @return the product name.
    */
   public static String getProductName(XDataSource source) {
      return getProductName(source, false);
   }

   /**
    * Get the product name.
    * @param source the specified data source.
    * @param disallowConnection do not connect DB.
    * @return the product name.
    */
   public static String getProductName(XDataSource source, boolean disallowConnection) {
      if(!(source instanceof JDBCDataSource)) {
         return null;
      }

      JDBCDataSource dx = (JDBCDataSource) source;

      if(dx.getRuntimeProductName() != null) {
         return dx.getRuntimeProductName();
      }

      String type = Config.getConfig().getJDBCType(dx.getDriver());

      // @by stephenwebster, For bug1424967241696
      // This is an exceptional case.  Many of the features of SQL Server or
      // Sybase can be assumed to work with the JTDS driver.  For example, the
      // maxrows feature if not supported will cause Live Preview to split
      // datablock queries up and then SQL columns will fail to merge.
      // This check is added here since JTDS can be used for both SQL Server
      // and Sybase, but use the same driver.  This will cause the correct
      // SQLHelper to be selected for the corresponding database to better
      // support the full features of the database.
      if(dx.getURL() != null && dx.getURL().contains("jtds:sqlserver")) {
         type = "SQL Server";
      }
      else if(dx.getURL() != null && dx.getURL().contains("jtds:sybase")) {
         type = "Sybase";
      }
      else if(dx.getURL() != null && dx.getURL().contains("jdbc:h2")) {
         type = "H2";
      }
      else if(dx.getURL() != null && dx.getURL().contains("jdbc:sqlite")) {
         type = "Sqlite";
      }
      else if(dx.getURL() != null && dx.getURL().contains("jdbc:databricks")) {
         type = "databricks";
      }
      else if(dx.getDatabaseType() == JDBCDataSource.JDBC_ODBC) {
         String product = dx.getProductName();

         if(product == null && !disallowConnection) {
            XNode node = null;

            try {
               XRepository repository = XRepository.getRepository();
               Object session = repository.bind(System.getProperty("user.name"));
               XNode mtype = new XNode();
               mtype.setAttribute("type", "scalar");
               node = repository.getMetaData(session, dx, mtype, true, null);
            }
            catch(Exception ex) {
               LOG.error("Failed to get database product: " + dx, ex);
            }

            if(node != null) {
               product = (String) node.getAttribute("DBProductName");
               dx.setProductName(product);
            }
         }

         if(product != null) {
            String name = product.toLowerCase(Locale.ROOT);

            if(name.contains("access")) {
               type = "access";
            }
            else if(name.contains("informix")) {
               type = "informix";
            }
            else if(name.contains("oracle")) {
               type = "oracle";
            }
            else if(name.contains("adaptive") || name.contains("sybase") ||
                    name.contains("anywhere"))
            {
               type = "sybase";
            }
            else if(name.contains("microsoft") && name.contains("sql"))
            {
               type = "sql server";
            }
            else if(name.contains("db2")) {
               type = "db2";
            }
            else if(name.contains("mysql")) {
               type = "mysql";
            }
            else if(name.contains("postgresql")) {
               type = "postgresql";
            }
            else if(name.contains("vertica")) {
               type = "vertica";
            }
            else if(name.contains("luciddb")) {
               type = "luciddb";
            }
            else if(name.contains("impala")) {
               type = "impala";
            }
            else if(name.contains("google bigquery")) {
               type = "google bigquery";
            }
         }
      }
      else if(dx.getDatabaseType() == JDBCDataSource.JDBC_ACCESS) {
         type = "access";
      }
      else if(StringUtils.isEmpty(type)){
         type = dx.getDatabaseTypeString().toLowerCase(Locale.ROOT);
      }

      dx.setRuntimeProductName(type == null ? null : type.toLowerCase(Locale.ROOT));
      return dx.getRuntimeProductName();
   }

   /**
    * Check if the sql exception is a expression exception.
    */
   public static boolean isExpException(String product,
                                        SQLException exception)
   {
      // @by davyc, we'd better use "XOPEN SQLstate conventions" or
      // "SQL 99 conventions" to check it, now we just use a simple way
      // to handle it
      if(exception instanceof SQLSyntaxErrorException) {
         return true;
      }

      String sqlstate = exception.getSQLState();
      init();
      Set<String> experrors = expErrorState.get(product);
      boolean found = false;

      if(experrors != null) {
         found = experrors.contains(sqlstate);
      }

      return found;
   }

   private static void init() {
      if(expErrorState.size() > 0) {
         return;
      }

      // oracle: http://docs.oracle.com/cd/E11882_01/appdev.112/e10827/appd.htm
      Set<String> states = new HashSet<>();
      states.add("42000");
      expErrorState.put("oracle", states);

      // mysql: http://dev.mysql.com/doc/refman/5.5/en/error-messages-server.html
      states = new HashSet<>();
      states.add("42S22");
      states.add("42000");
      expErrorState.put("mysql", states);

      // db2: http://pic.dhe.ibm.com/infocenter/dzichelp/v2r2/index.jsp?topic=%2Fcom.ibm.db2z9.doc.codes%2Fsrc%2Ftpc%2Fdb2z_sqlstatevalues.htm
      states = new HashSet<>();
      states.add("42S22");
      states.add("42819");
      states.add("42601");
      expErrorState.put("db2", states);

      // derby: http://db.apache.org/derby/docs/10.1/ref/rrefexcept71493.html
      states = new HashSet<>();
      states.add("42X01");
      states.add("42X04");
      states.add("42819");
      expErrorState.put("derby", states);

      // (Access) SQLServer: http://technet.microsoft.com/en-us/library/aa937531%28v=SQL.80%29.aspx
      states = new HashSet<>();
      states.add("42S22");
      states.add("HY000");
      states.add("S0001");
      states.add("37000");
      expErrorState.put("access", states);
      expErrorState.put("sql server", states);

      // PostgreSQL: http://www.postgresql.org/docs/9.1/static/ecpg-errors.html
      states = new HashSet<>();
      // states.add("42S22");
      // states.add("HY000");
      expErrorState.put("postgresql", states);

      // Ingres: http://docs.actian.com/ingres/9.2/ingres-92-message-guide/1279-errors-from-jdbc
      states = new HashSet<>();
      states.add("42501");
      expErrorState.put("ingres", states);
   }

   /**
    * Generate SQLHelper correspond to the data source type
    * specified by UniformSQL.
    * @param definition - UniformSQL.
    * @return SQLHelper.
    */
   public static SQLHelper getSQLHelper(UniformSQL definition) {
      return getSQLHelper(definition, ThreadContext.getContextPrincipal());
   }

   /**
    * Generate SQLHelper correspond to the data source type
    * specified by UniformSQL.
    * @param definition - UniformSQL.
    * @return SQLHelper.
    */
   public static SQLHelper getSQLHelper(UniformSQL definition, Principal user) {
      if(definition == null) {
         return new SQLHelper();
      }

      JDBCDataSource dx = definition.getDataSource();
      SQLHelper helper = getSQLHelper(dx, user);
      helper.setUniformSql(definition);

      return helper;
   }

   /**
    * Generate SQLHelper correspond to the database type.
    * @param dbType the database type.
    * @return SQLHelper.
    */
   public static SQLHelper getSQLHelper(String dbType) {
      try {
         String dbType2 = (dbType == null) ? "" : dbType;
         Class cls = helperClasses.get(dbType2);

         if(cls == null) {
            String className = getHelperClass(dbType);
            cls = Class.forName(className);
            helperClasses.put(dbType2, cls);
         }

         return (SQLHelper) cls.newInstance();
      }
      catch(Exception ex) {
         throw new RuntimeException("SQLHelper Error: " + getHelperClass(dbType) +
            " is not properly installed!", ex);
      }
   }

   /**
    * Generate SQLHelper correspond to the data source type.
    * @param dx the data source.
    * @return SQLHelper.
    */
   public static SQLHelper getSQLHelper(XDataSource dx) {
      return getSQLHelper(dx, ThreadContext.getContextPrincipal());
   }

   /**
    * Generate SQLHelper correspond to the data source type.
    * @param dx the data source.
    * @param additional additional connection.
    * @return SQLHelper.
    */
   public static SQLHelper getSQLHelper(XDataSource dx, String additional) {
      return getSQLHelper(dx, additional, ThreadContext.getContextPrincipal());
   }

   /**
    * Generate SQLHelper correspond to the data source type.
    * @param dx the data source.
    * @return SQLHelper.
    */
   public static SQLHelper getSQLHelper(XDataSource dx, Principal user) {
      return getSQLHelper(dx, null, user);
   }

   /**
    * Generate SQLHelper correspond to the data source type.
    * @param dx the data source.
    * @return SQLHelper.
    */
   public static SQLHelper getSQLHelper(XDataSource dx, String additional, Principal user) {
      if(dx == null) {
         return new SQLHelper();
      }

      dx = ConnectionProcessor.getInstance().getDatasource(user, dx, additional);

      try {
         SQLHelper helper = getSQLHelper(getProductName(dx));

         if(dx instanceof JDBCDataSource) {
            helper.setAnsiJoin(((JDBCDataSource) dx).isAnsiJoin());
            String sqlHelperType = helper.getSQLHelperType();

            // changes:
            // 1. since only mysql requires this property at present, for the
            // reason of performance, here we only get the property for mysql
            // 2. we need to use db version to determine if the DB support
            // the function "Median", so we also get oracle's versions here
            // 3. fix bug1260414443652, if database type is derby,
            // we should get the version of derby before merge group by,
            // so that for the versions equal or higher than 10.3,
            // the expression group can merged to sql insteadof post process.
            if(((JDBCDataSource) dx).getProductVersion() == null &&
               ("mysql".equals(sqlHelperType) ||
                "oracle".equals(sqlHelperType)) ||
                "derby".equals(sqlHelperType))
            {
               XRepository repository = XRepository.getRepository();
               Object session = repository.bind(System.getProperty("user.name"));
               XNode mtype = new XNode();
               mtype.setAttribute("type", "scalar");
               mtype.setAttribute("additional", additional);

               try {
                  XNode node = repository.getMetaData(session, dx, mtype,
                                                      false, null);

                  if(node != null) {
                     String version = (String)
                        node.getAttribute("DBProductVersion");
                     helper.setProductVersion(version);

                     if("mysql".equals(helper.getSQLHelperType())) {
                        ((JDBCDataSource) dx).setProductVersion(version);
                     }
                  }
               }
               catch(Exception ex) {
                  LOG.debug("Failed to get database product version: " + dx, ex);
               }
            }
         }

         return helper;
      }
      catch(Exception ex) {
         LOG.error("Failed to initialize SQL helper for data source: " + dx, ex);
         throw new RuntimeException("SQLHelper Error: " +
            getHelperClass(getProductName(dx)) + " is not properly installed!");
      }
   }

   /**
    * Create a default SQL helper.
    */
   public SQLHelper() {
      super();
      uniformSql = defaultSql;
      caseSensitive = "true".equals(SreeEnv.getProperty("db.caseSensitive"));
      initFormatKeywords();
   }

   /**
    * Get the sql helper type.
    * @return the sql helper type.
    */
   public String getSQLHelperType() {
      return "default";
   }

   /**
    * Get the db product's version.
    */
   public String getProductVersion() {
      return version;
   }

   /**
    * Set the db product's version.
    */
   public void setProductVersion(String version) {
      this.version = version;
   }

   /**
    * Set the row limit on query output.
    */
   public void setOutputMaxRows(int rowlimit) {
      this.outmaxrows = rowlimit;
   }

   /**
    * Set the row limit on individual detail tables.
    */
   public void setInputMaxRows(int rowlimit) {
      this.inpmaxrows = rowlimit;
   }

   /**
    * Check if supports an operation.
    * @param op the specified operation.
    * @return <tt>true</tt> if supports, <tt>false</tt> otherwise.
    */
   public boolean supportsOperation(String op) {
      return supportsOperation(op, null);
   }

   /**
    * Check if requires uppercased alias in a sub query.
    * @param alias the specified alias.
    * @return <tt>true</tt> if requires uppercased alias.
    */
   public boolean requiresUpperCasedAlias(String alias) {
      return false;
   }

   /**
    * Check if supports alias sorting.
    */
   public boolean supportsAliasSorting() {
      return true;
   }

   /**
    * Check if supports field sorting.
    */
   public boolean supportsFieldSorting() {
      return true;
   }

   /**
    * Check if supports an operation.
    * @param op the specified operation.
    * @param info the advanced information.
    * @return <tt>true</tt> if supports, <tt>false</tt> otherwise.
    */
   public boolean supportsOperation(String op, String info) {
      String key = getSQLHelperType() + ":" + op;
      return !unsupported.contains(key);
   }

   public String getConnectionTestQuery() {
      return SQLHelper.getConnectionTestQuery(getSQLHelperType());
   }

   public static String getConnectionTestQuery(String type) {
      return connectionTestQuery.get(type);
   }

   /**
    * Get the aggregate function name of the specified function.
    * @param func function is one of: stddev, stddevp, var, varp, covar,
    * correl.
    * @return function name or null if function is not supported in SQL.
    */
   public final String getAggregateFunction(String func) {
      return afuncs.get(getSQLHelperType() + ":" + func);
   }

   /**
    * Get the uniform sql.
    * @return the uniform sql.
    */
   public final UniformSQL getUniformSQL() {
      return uniformSql;
   }

   /**
    * Get the function for use in sql.
    * @param func function is one of date function constants defined in this
    * class.
    * @return function call string.
    * @throws UnsupportedOperationException if the date function is not
    * supported by this database.
    */
   public final String getFunction(String func, String column) {
      String name = getSQLHelperType() + ":" + func;
      String cmd = dfuncs.get(name);

      if(cmd == null) {
         throw new UnsupportedOperationException(name);
      }

      MessageFormat fmt = new MessageFormat(cmd);

      return fmt.format(new Object[] { column });
   }

   public boolean supportFunction(String func) {
      String name = getSQLHelperType() + ":" + func;
      String cmd = dfuncs.get(name);

      return cmd != null;
   }

   /**
    * Generate SQL statement.
    * @return SQL statement.
    */
   public final String generateSentence() {
      vJoin = null;
      textJoinOrder = null;
      ansiWhereJoins = null;
      commaGroupsChanged = false;
      commaGroupsNotMovable = false;

      // make sure the table aliases don't exceed database limit
      fixTableAliases();

      // remove all the invisible order by fields
      if(!supportsOperation(SQLHelper.HIDDEN_ORDERBY) || uniformSql.isDistinct()) {
         uniformSql.removeInvisibleOrderByFields();
      }

      uniformSql.clearAliasColumn();
      int count = uniformSql.getTableCount();

      for(int i = 0; i < count; i++) {
         SelectTable table = uniformSql.getSelectTable(i);
         Object tobj = table.getName();

         if(tobj instanceof UniformSQL) {
            UniformSQL ssql = (UniformSQL) tobj;
            // generate ssql here to quote column properly
            ssql.toString();
         }
      }

      // @by henryh, allow database helper decide outer join syntax
      String sql = isAnsiJoin0() ? generateSentenceAnsi() : generateSentence0();

      if(outmaxrows > 0) {
         sql = generateMaxRowsClause(sql, outmaxrows);

         if(isFormatSQL) {
            sql = formatMaxRowsClause(sql);
         }
      }
      else {
         sql = appendLimitClause(sql);
      }

      return sql;
   }

   /**
    * Generate SQL statement.
    */
   private String generateSentence0() {
      // sub queries need to be generated first for sub query alias to work
      String from = generateFromClause();
      String sentence = generateSelectClause();

      if(sentence.trim().length() == 0) {
         return "";
      }

      String where = appendRownumLimit(generateWhereClause());
      String groupby = generateGroupByClause();
      String having = generateHavingClause();
      String orderby = generateOrderByClause();

      return sentence +
         " " +
         (from.length() > 0 ? "\n" : "") +
         from +
         " " +
         (where.length() > 0 ? "\n" : "") +
         where +
         " " +
         (groupby.length() > 0 ? "\n" : "") +
         groupby +
         " " +
         (having.length() > 0 ? "\n" : "") +
         having +
         " " +
         (orderby.length() > 0 ? "\n" : "") +
         orderby +
         " ";
   }

   /**
    * Generate SQL statement.
    * @return SQL statement.
    */
   private String generateSentenceAnsi() {
      String sentence = generateSelectClause();

      if(sentence.trim().length() == 0) {
         return "";
      }

      // @by mikec, the genereteWhereClause is called before
      // From to create the vJoin list
      String where = generateWhereClause();
      String from = generateFromClause();
      // join conditions the ANSI FROM clause couldn't write in an ON
      where = appendRownumLimit(appendAnsiWhereJoins(where));
      String groupby = generateGroupByClause();
      String having = generateHavingClause();
      String orderby = generateOrderByClause();

      return sentence +
         " " +
         (from.length() > 0 ? "\n" : "") +
         from +
         " " +
         (where.length() > 0 ? "\n" : "") +
         where +
         " " +
         (groupby.length() > 0 ? "\n" : "") +
         groupby +
         " " +
         (having.length() > 0 ? "\n" : "") +
         having +
         " " +
         (orderby.length() > 0 ? "\n" : "") +
         orderby +
         " ";
   }

   /**
    * Append limit clause for queries affected by vpm conditions.
    */
   private String appendLimitClause(String sql) {
      if(uniformSql.hasVPMCondition() && inpmaxrows > 0) {
         // The row limit syntax of the database, taken from the limited table query of a
         // placeholder table, so the names and literals of the sql can't change it. (#77698)
         String subquery = getTableWithLimit(LIMIT_PLACEHOLDER, inpmaxrows);

         // The sql is not searched for an existing limit: names and literals contain the
         // keywords (credit_limit, 'no limit'). The statement has no limit clause of its own
         // here: generateMaxRowsClause() runs only if outmaxrows > 0, and the parser refuses
         // limit and fetch first. A derived table's own limit doesn't limit this statement,
         // and a second rownum condition is still valid sql. The one limit the statement can
         // start with is a top select option (getSelectionOption()). (#77698)
         if(subquery != null) {
            if(isKeyword("limit") && subquery.contains("limit")) {
               sql += " limit " + inpmaxrows;
            }
            else if(isKeyword("top") && sql.startsWith("select") &&
               !SELECT_TOP.matcher(sql).lookingAt())
            {
               sql = sql.replaceFirst("select", "select top " + inpmaxrows);
            }
            else if(subquery.contains("fetch first")) {
               sql += " fetch first " + inpmaxrows + " rows only";
            }
            // rownum (oracle) is added to the where clause by appendRownumLimit()
         }
      }

      return sql;
   }

   /**
    * Add the input row limit of the queries affected by vpm conditions to the where clause
    * of this statement, if the database limits rows with rownum (oracle). The where clause
    * is wrapped when it is generated, not searched for in the generated sql, so the where of
    * a subquery or a derived table, and a literal containing where, are left alone. A
    * statement without a where clause is not limited. (#77698)
    */
   private String appendRownumLimit(String where) {
      if(outmaxrows > 0 || !uniformSql.hasVPMCondition() || inpmaxrows <= 0 ||
         !(this instanceof OracleSQLHelper) || where == null || !where.startsWith(WHERE))
      {
         return where;
      }

      String subquery = getTableWithLimit(LIMIT_PLACEHOLDER, inpmaxrows);

      if(subquery == null || !subquery.contains(" where rownum <= ")) {
         return where;
      }

      return WHERE + " (" + where.substring(WHERE.length()) + ") AND rownum <= " + inpmaxrows;
   }

   /**
    * Generate table clause.
    */
   protected String generateTableClause(SelectTable table) {
      Object tobj = table.getName();
      Object tname = getFromTable(tobj);
      String namestr = tname.toString().trim();
      String alias = table.getAlias();
      StringBuilder sb = new StringBuilder();
      boolean eq = Tool.equals(tobj, alias);

      // name is uniform sql?
      if(tname instanceof UniformSQL) {
         UniformSQL sub = (UniformSQL) tname;
         sub.setDataSource(uniformSql.getDataSource());
         sub.setParent(uniformSql);
         sb.append(BRACKET);
         sb.append(getSubQueryString(table, sub));
         sb.append(")");
      }
      // name is a string?
      else {
         // @by rundaz, if the table is a subquery, it shouldn't be quoted
         if(XUtil.isSubQuery(namestr)) {
            sb.append(BRACKET);
            sb.append(namestr);
            sb.append(")");
         }
         else if(JDBCDataSource.INFORMIX.equalsIgnoreCase(table.getSchema())) {
            // fix bug#9381. The from statement of informix database not support be quoted.
            sb.append(trimQuote(namestr));
         }
         else {
            String quote = getQuote();
            String qname = quoteQuotedSegments(table, namestr);
            StringBuilder nameStr = new StringBuilder();

            if(qname != null) {
               nameStr.append(qname);
            }
            else {
               fixTableName(namestr, nameStr, quote, true);
            }

            // the names written unquoted, in the case the database reads them (Bug #77643)
            sb.append(tobj instanceof String ?
               foldWrittenUnquoted(nameStr.toString(), table.getWrittenUnquoted()) : nameStr);
         }
      }

      // alias exists?
      if(alias != null) {
         String nalias = aliasmap == null ? null : aliasmap.get(alias);

         if(nalias != null) {
            alias = nalias;
         }

         if(!alias.equals(namestr) && !alias.equals(trimQuote(namestr)) &&
            !isFoldedTwin(table) || uniformSql.hasVPMCondition() && isTableSubquery())
         {
            sb.append(" ");

            // alias is equal to table? that means alias is from name, so we
            // need to trim quote from name, then it might be a valid alias
            if(eq) {
               alias = trimQuote(alias);
            }

            sb.append(quoteTableAlias(alias));
         }
      }

      return formatSubquery(sb);
   }

   /**
    * Get the SQL string for a subquery. When the database supports map key access syntax
    * (e.g. m['key']), any such expressions in the SELECT clause that do not already have an
    * alias are given a safe alias (e.g. __col_0__) so that the outer query can reference them
    * by that alias instead of a backtick-quoted expression the database would misinterpret.
    */
   protected String getSubQueryString(SelectTable table, UniformSQL sub) {
      if(!supportsMapKeySubqueryAliasing()) {
         return sub.toString().trim();
      }

      String sql = sub.toString().trim();
      Matcher matcher = MAP_KEY_ACCESS.matcher(sql);

      if(!matcher.find()) {
         return sql;
      }

      int fromIdx = findFromIndex(sql);
      String selectPart = fromIdx >= 0 ? sql.substring(0, fromIdx) : sql;
      String tail = fromIdx >= 0 ? sql.substring(fromIdx) : "";

      matcher = MAP_KEY_ACCESS.matcher(selectPart);
      StringBuilder result = new StringBuilder();
      String tableAlias = table.getAlias();
      int colIndex = 0;
      boolean modified = false;

      while(matcher.find()) {
         String expr = matcher.group();
         String afterExpr = selectPart.substring(matcher.end()).stripLeading();
         boolean hasAlias = afterExpr.length() >= 3
            && afterExpr.substring(0, 2).equalsIgnoreCase("AS")
            && Character.isWhitespace(afterExpr.charAt(2));

         if(hasAlias) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(expr));
         }
         else {
            String safeAlias = "__col_" + colIndex++ + "__";
            subQueryMapKeyAliases.put(tableAlias + "." + expr, safeAlias);
            matcher.appendReplacement(result,
               Matcher.quoteReplacement(expr + " AS " + safeAlias));
            modified = true;
         }
      }

      if(!modified) {
         return sql;
      }

      matcher.appendTail(result);
      return result + tail;
   }

   /**
    * Returns true if this database uses map key access syntax (e.g. m['key']) that cannot be
    * safely backtick-quoted as a column reference in an outer subquery SELECT. When true,
    * getSubQueryString() adds safe aliases and getValidSubAlias() returns them.
    */
   protected boolean supportsMapKeySubqueryAliasing() {
      return false;
   }

   /**
    * Find the index of the first FROM keyword that is not inside parentheses or quotes.
    */
   private static int findFromIndex(String sql) {
      int depth = 0;
      boolean inString = false;
      char stringChar = 0;

      for(int i = 0; i < sql.length(); i++) {
         char c = sql.charAt(i);

         if(inString) {
            if(c == stringChar) {
               inString = false;
            }
         }
         else if(c == '\'' || c == '"' || c == '`') {
            inString = true;
            stringChar = c;
         }
         else if(c == '(') {
            depth++;
         }
         else if(c == ')') {
            depth--;
         }
         else if(depth == 0 && i + 4 <= sql.length()) {
            String word = sql.substring(i, i + 4);

            if(word.equalsIgnoreCase("FROM")
               && (i == 0 || Character.isWhitespace(sql.charAt(i - 1)))
               && (i + 4 >= sql.length() || Character.isWhitespace(sql.charAt(i + 4))))
            {
               return i;
            }
         }
      }

      return -1;
   }

   protected void fixTableName(String namestr, StringBuilder sb, String quote, boolean selectClause) {
      // If table name has quote, it is fixed quote before, so add it directly.
      if(namestr.contains(quote)) {
         sb.append(namestr);
      }
      else {
         sb.append(quoteTableName(namestr));
      }
   }

   /**
    * Return ansi join.
    */
   private String getAnsiJoin(String op, boolean traverse) {
      if(op.equals("*=")) {
         return traverse ? " RIGHT OUTER JOIN " : " LEFT OUTER JOIN ";
      }
      else if(op.equals("=*")) {
         return traverse ? " LEFT OUTER JOIN " : " RIGHT OUTER JOIN ";
      }
      else if(op.equals("*=*")) {
         return " FULL OUTER JOIN ";
      }
      else if(op.equals("=")) {
         return " INNER JOIN ";
      }
      else if(op.equals(">=")) {
         return " INNER JOIN ";
      }
      else if(op.equals(">")) {
         return " INNER JOIN ";
      }
      else if(op.equals("<=")) {
         return " INNER JOIN ";
      }
      else if(op.equals("<")) {
         return " INNER JOIN ";
      }
      else if(op.equals("<>") || op.equals("!=")) {
         return " INNER JOIN ";
      }

      return null;
   }

   /**
    * Return ansi op.
    */
   private String getAnsiOp(String op, boolean isnot) {
      if(op.equals("*=")) {
         return isnot ? "<>" : "=";
      }
      else if(op.equals("=*")) {
         return isnot ? "<>" : "=";
      }
      else if(op.equals("*=*")) {
         return isnot ? "<>" : "=";
      }
      else if(isnot) {
         if(op.equals(">=")) {
            return "<";
         }
         else if(op.equals(">")) {
            return "<=";
         }
         else if(op.equals("<=")) {
            return ">";
         }
         else if(op.equals("<")) {
            return ">=";
         }
         else if(op.equals("=")) {
            return "<>";
         }
         else if(op.equals("<>") || op.equals("!=")) {
            return "=";
         }
      }

      return op;
   }

   /**
    * Append the join clause to the from.
    */
   protected void appendJoinClause(StringBuilder from,
      XBinaryCondition condition, String op, String table1, String table2,
      int top, XBinaryCondition previousJoin)
   {
      String clause = makeJoinClause(condition, op, table1, table2, previousJoin);

      // if the join is not the first one, the previous join clause needs
      // to be put inside parens
      if(from.length() > top && clause.trim().startsWith(op.trim())) {
         from.insert(top, "(");

         if(from.toString().endsWith(COMMA_GAP)) {
            from.setLength(isFormatSQL ?
               from.length() - 9 : from.length() - 2);
         }

         from.append(")");
      }

      from.append(clause);
   }

   /**
    * Generate Join clause of SQL statement.
    * @return join clause.
    */
   protected String makeJoinClause(XBinaryCondition condition, String join,
                                   String table1, String table2, XBinaryCondition previousJoin)
   {
      StringBuilder sb = new StringBuilder();

      if(table2 != null) {
         if(table1 == null) {
            table1 = "";
         }

         sb.append(table1);

         if(isFormatSQL) {
            sb.append("\n      ");
         }

         sb.append(join);
         sb.append(table2);

         if(isFormatSQL) {
            sb.append("\n         ");
         }

         sb.append(" ON ");
         sb.append(buildExpressionString(condition.getExpression1(), true));
         sb.append(" ");

         String op = condition.getOp();
         op = getAnsiOp(op, condition.isIsNot());
         sb.append(op);
         sb.append(" ");
         sb.append(buildExpressionString(condition.getExpression2(), true));
      }
      else {
         XBinaryCondition mergingJoin = condition;

         // use previous join relation instead of current to match non-ansi sql generation
         if(previousJoin != null) {
            mergingJoin = previousJoin;
         }

         final XNode parent = mergingJoin.getParent();
         String relation = "";

         if(parent instanceof XSet) {
            relation = ((XSet) parent).getRelation();
         }

         if("and".equals(relation)) {
            sb.append(AND);
         }
         else {
            sb.append(OR);
         }

         sb.append(buildExpressionString(condition.getExpression1(), true));
         sb.append(" ");

         String op = condition.getOp();
         op = getAnsiOp(op, condition.isIsNot());
         sb.append(op);
         sb.append(" ");
         sb.append(buildExpressionString(condition.getExpression2(), true));
      }

      sb.append(" ");

      return sb.toString();
   }

   /**
    * Get the valid alias of a column.
    */
   public String getValidSubAlias(String table, String c) {
      if(table == null) {
         return null;
      }

      if(!subQueryMapKeyAliases.isEmpty()) {
         String safeAlias = subQueryMapKeyAliases.get(table + "." + c);

         if(safeAlias != null) {
            return safeAlias;
         }
      }

      SelectTable[] tables = uniformSql.getSelectTable();

      for(SelectTable t : tables) {
         if((t.getAlias().equals(table) || t.getName().toString().equals(table))
            && t.getName() instanceof UniformSQL) {
            UniformSQL usql = (UniformSQL) t.getName();
            JDBCSelection jsel = (JDBCSelection) usql.getSelection();
            int idx = jsel.indexOfColumn(c, requiresUpperCasedAlias(c));

            if(idx != -1) {
               String valias = jsel.getValidAlias(idx, this);
               return !Tool.equals(valias, c) ? valias : null;
            }
         }
      }

      return null;
   }

   private String getOriginalColumnName(String table, String c) {
      if(table == null) {
         return null;
      }

      SelectTable[] tables = uniformSql.getSelectTable();

      for(SelectTable t : tables) {
         if((t.getAlias().equals(table) || t.getName().toString().equals(table))
            && t.getName() instanceof UniformSQL)
         {
            UniformSQL usql = (UniformSQL) t.getName();
            JDBCSelection jsel = (JDBCSelection) usql.getSelection();
            return jsel.getOriginalAlias(c);
         }
      }

      return null;
   }

   /**
    * Get valid alias.
    */
   protected String getValidAlias(JDBCSelection jsel, int col, String alias) {
      return alias;
   }

   /**
    * Check if alias length is limited.
    */
   public boolean isLimitAlias() {
      String prop = SreeEnv.getProperty("limit.alias.length");
      return "true".equals(prop);
   }

   /**
    * Generate select clause of SQL statement.
    * @return select clause.
    */
   public String generateSelectClause() {
      XSelection xselect;
      int columnCount;
      StringBuilder selectStr = new StringBuilder();
      xselect = uniformSql.getSelection();
      columnCount = xselect.getColumnCount();

      if(columnCount <= 0) {
         return "";
      }

      selectStr.append("select");

      if(uniformSql.isDistinct()) {
         selectStr.append(" distinct");
      }
      else if(uniformSql.isAll()) {
         selectStr.append(" all");
      }

      String options = getSelectionOption();

      if(options != null) {
         selectStr.append(" ");
         selectStr.append(options);
      }

      ((JDBCSelection) xselect).clearOriginalAliases();
      inheritAliases();

      int[] map = JDBCQueryCacheNormalizer.generateSortedColumnMap(uniformSql);

      for(int i = 0; i < columnCount; i++) {
         int xIdx = map != null ? map[i] : i;
         String column = xselect.getColumn(xIdx);
         boolean expr = xselect.isExpression(xIdx);
         String table = uniformSql.getTable(column);
         String subCol = uniformSql.getColumnFromPath(column);
         String subalias = getValidSubAlias(table, subCol);
         String alias = ((JDBCSelection) xselect).getValidAlias(xIdx, this);
         boolean aliasNull = alias == null || alias.equals(column);
         // the sql of the column for the parameter values of this run, e.g. a scalar subquery
         // with a sentinel parameter rewritten (Bug #77620), is generated as the text of the
         // column is. Its name, table and alias are those of the column text.
         String runSql = ((JDBCSelection) xselect).getColumnSQL(xIdx);

         if(runSql != null) {
            column = runSql;
         }

         if(subalias != null) {
            // use the same quote logic for subalias
            // if alias in subquery has quote but have not at here
            // the oracle database will notify column not found error.
            column = quoteTableAlias(table) + "." + quoteColumnAlias(subalias);

            if((alias == null || alias.length() == 0 || alias.equals(column))) {
               if(((JDBCSelection) xselect).getOriginalAlias(subalias).equals(subalias)) {
                  alias = getValidAlias((JDBCSelection) xselect, xIdx, subCol);
               }
               else {
                  alias = subalias;
               }

               if(aliasNull && alias != null && !alias.startsWith("ALIAS_")) {
                  xselect.setAlias(xIdx, alias);
               }
            }
         }
         else if(expr && table == null) {
            column = updateExpressionWithSubAlias(column);
         }
         else {
            column = getValidAggregate(column,
               ((JDBCSelection) xselect).getQuotedAggregate(xIdx));
         }

         String ocolumn = xselect.getOriginalColumn(xIdx);

         // a quoted identifier (e.g. "x y") parsed from the sql is stored without its
         // quotes, restore them so the generated sql refers to the same column. The flag
         // is per column, two columns may have the same path (Bug #77573)
         if(!expr && table == null && subalias == null &&
            ((JDBCSelection) xselect).isQuoted(xIdx))
         {
            String qname = xselect.getColumn(xIdx);
            String qseg = ((JDBCSelection) xselect).getQuotedColumn(xIdx);

            // a qualified quoted column (t."MixedCase") quotes only its column segment
            column = qseg != null ? quoteIdentifier(qname, column, qseg) :
               getQuote() + column + getQuote();
         }
         else if(uniformSql.isTableColumn(column) && subalias == null && !expr) {
            // @by larryl, if this is a table column and the original column is
            // set, we should use the real column otherwise the flags to
            // quoteColumnAlias is not accurate
            if(ocolumn == null || ocolumn.length() == 0) {
               ocolumn = column;
            }

            // prevent aggregates being quoted like this "COUNT(SA".CUSTOMERS."ZIP)"
            if(!((JDBCSelection) xselect).isAggregate(column)) {
               // if the query is create by sql query, it will fix column with "." to quote
               // catalog + schema + table. If do not have ".", but has other specific char such
               // as "/", it should fix there to quote "\" char.
               String quote = getQuote();

               if(!uniformSql.isSqlQuery() || !column.contains(quote) || requiresSpecialQuoteHandling()) {
                  if(table.contains(quote) && column.startsWith(table)) {
                     String cpart = column.substring(table.length() + 1);
                     String quotedTable = quoteTableNameWithQuotedSchema(table, true);
                     column = quotedTable + "." + quoteColumnPartAlias(cpart);
                  }
                  else {
                     column = quotePath(column, true, false, true);
                  }
               }
            }
         }
         else if(isKeyword(column) && !expr) {
            column = XUtil.quoteAlias(column, this);
         }
         else if(!XUtil.isQualifiedName(column)) {
            column = quoteExpressionCol(column);
         }

         if(!expr && table != null && subalias == null &&
            ((JDBCSelection) xselect).isQuoted(xIdx))
         {
            column = quoteIdentifier(xselect.getColumn(xIdx), column,
               ((JDBCSelection) xselect).getQuotedColumn(xIdx));
         }

         // if table changed to a subquery, replace reference to table to alias
         if(isTableSubquery()) {
            column = replaceTableByAlias(true, column);
         }
         else {
            column = replaceTableByAlias(false, column);
         }

         // the names written unquoted, in the case the database reads them (Bug #77643). The
         // sql of the column for this run is generated from its text, it has the same names
         column = foldNames(column, ((JDBCSelection) xselect).getWrittenUnquoted(xIdx));

         selectStr.append(" ");
         selectStr.append(column);

         if(alias != null && !alias.equals("") && !alias.equals(subalias) &&
            !(this instanceof InformixSQLHelper && alias.contains(".")))
         {
            boolean same = false;
            boolean part = false;

            // duplicate is true if the alias has the same name as column
            if(ocolumn != null) {
               String cstr = ocolumn.toLowerCase(Locale.ROOT);
               String astr = alias.toLowerCase(Locale.ROOT);

               same = cstr.equals(astr);
               part = cstr.endsWith("." + astr);
            }

            selectStr.append(" as ");
            selectStr.append(quoteColumnAlias(alias, same, part));
         }

         if(i != columnCount - 1) {
            selectStr.append(COMMA);
         }
      }

      if(map != null && map.length != 0) {
         uniformSql.setHint(UniformSQL.HINT_SQL_STRING_SORTED_COLUMN, true);
      }

      return selectStr.toString();
   }

   /**
    * Replace the old sub query alias by new sub query alias for expression column.
    *
    * @param expression
    * @return
    */
   private String updateExpressionWithSubAlias(String expression) {
      SelectTable[] selectTables = uniformSql.getSelectTable();

      if(selectTables == null) {
         return expression;
      }

      for(SelectTable selectTable : selectTables) {
         Object name = selectTable.getName();

         if(!(name instanceof UniformSQL)) {
            continue;
         }

         UniformSQL subQuery = (UniformSQL) name;
         XSelection selection = subQuery.getSelection();
         String alias = selectTable.getAlias();

         if(Tool.isEmptyString(alias)) {
            continue;
         }

         for(int i = 0; i < selection.getColumnCount(); i++) {
            String column = selection.getColumn(i);
            String subCol = subQuery.getColumnFromPath(column);
            String columnAlias = selection.getAlias(i);

            if(!Tool.isEmptyString(columnAlias)) {
               subCol = columnAlias;
            }

            String subalias = getValidSubAlias(alias, subCol);

            if(Tool.isEmptyString(subalias)) {
               continue;
            }

            if(Tool.isEmptyString(subCol) || Tool.equals(subalias, subCol)) {
               continue;
            }

            String oldName = quoteTableAlias(alias) + "." + quoteColumnAlias(subCol);
            String newName = quoteTableAlias(alias) + "." + quoteColumnAlias(subalias);
            expression = expression.replace(oldName, newName);
         }
      }

      return expression;
   }

   /**
    * Inherit aliases from depending sql clauses.
    */
   private void inheritAliases() {
      final XSelection xselect = uniformSql.getSelection();

      for(int i = 0; i < xselect.getColumnCount(); i++) {
         String alias = xselect.getAlias(i);

         // a column with a valid alias of its own is output by that alias, no generated
         // name stands for it (Bug #77714)
         if(alias != null && !alias.isEmpty() && isValidAlias(alias)) {
            continue;
         }

         String column = xselect.getColumn(i);
         String table = uniformSql.getTable(column);
         String subCol = uniformSql.getColumnFromPath(column);
         String subalias = getValidSubAlias(table, subCol);
         String originalName = getOriginalColumnName(table, subCol);

         if(subalias != null) {
            ((JDBCSelection) xselect).inheritAlias(i, subalias);
         }
         else if(originalName != null && !subCol.equals(originalName)) {
            ((JDBCSelection) xselect).inheritAlias(originalName, subCol);
         }
      }
   }

   /**
    * Quote alias.
    */
   private String quoteAlias(String column) {
      if(!containsKeyword(column)) {
         return XUtil.quoteAlias(column, this);
      }

      String key = column.substring(0, column.indexOf('.'));
      String col = column.substring(column.indexOf('.') + 1);

      return XUtil.quoteAlias(key, this) + "." + XUtil.quoteAlias(col, this);
   }

   /**
    * Quote columns in an expression.
    */
   protected String quoteExpressionCol(String exp) {
      return exp;
   }

   protected String quoteExpressionColumn(String exp) {
      XSelection selection = uniformSql.getSelection();

      if(selection.isExpression(exp)) {
         return exp;
      }

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String column = selection.getColumn(i);
         exp = quoteExpressionColumn0(column, exp);
      }

      return exp;
   }

   private String quoteExpressionColumn0(String column, String exp) {
      // an empty column matches at every index, so the search below would never end
      if(column == null || column.isEmpty()) {
         return exp;
      }

      int idx = 0;
      int s;

      while((s = exp.indexOf(column, idx)) >= 0) {
         if(s > 0 && Character.isUnicodeIdentifierPart(exp.charAt(s-1))){
            idx = s + 1;
            continue;
         }

         String quoteColumn = getQuoteColumn(column, exp);
         exp = exp.substring(0, s) + quoteColumn + exp.substring(s + column.length());
         idx = s + quoteColumn.length() + 1;
      }

      return exp;
   }

   private String getTable(String column, String exp) {
      String table = uniformSql.getTable(column);

      if(table == null) {
         for(SelectTable selectTable : uniformSql.getSelectTable()) {
            Object tableName = selectTable.getName();

            if(tableName instanceof String && (column.startsWith(selectTable.getAlias() + ".")
               && exp.indexOf(column) != -1 || exp.indexOf(tableName + "." + column) != -1))
            {
               Object name = selectTable.getName();

               if(name instanceof String) {
                  table = ((String) name);
               }
            }
            else if(column.startsWith(selectTable.getAlias() + ".") && exp.indexOf(column) != -1 ||
               exp.indexOf(selectTable.getAlias() + "." + column) != -1)
            {
               table = selectTable.getAlias();
            }
         }
      }

      return table;
   }

   private String getQuoteColumn(String column, String exp) {
      String quote = getQuote();

      if(uniformSql.isTableColumn(column) && (!uniformSql.isSqlQuery() || !column.contains(quote))) {
         String table = getTable(column, exp);

         if(table != null && table.contains(quote) && column.startsWith(table)) {
            String cpart = column.substring(table.length() + 1);
            column = table + "." + quoteColumnPartAlias(cpart);
         }
         else {
            column = quotePath(column);
         }
      }
      else if(isKeyword(column)) {
         column = XUtil.quoteAlias(column, this);
      }

      return column;
   }

   /**
    * Get the valid expression.
    */
   private String getValidExpression(String exp, String bcol) {
      String bcol2 = quotePath(bcol, true, true, true);
      String table = uniformSql.getTable(bcol);
      String column = uniformSql.getColumnFromPath(bcol);
      String alias = getValidSubAlias(table, column);

      if(alias != null) {
         bcol = quoteTableAlias(table) + "." + quoteColumnAlias(alias);

         if(exp.contains(table + "." + bcol2)) {
            bcol2 = table + "." + bcol2;
         }

         exp = Tool.replaceAll(exp, bcol2, bcol);
      }

      return exp;
   }

   /**
    * Get valid aggregate to replace very long column with alias.
    * @param qcol the column segment, as written, of the qualified quoted column that is the
    *             only argument of the aggregate (MixedCase for sum(t."MixedCase")), recorded
    *             by the parser, or <tt>null</tt> if not recorded.
    */
   private String getValidAggregate(String aggregate, String qcol) {
      XSelection selection = uniformSql.getSelection();
      String bcol = selection.getBaseColumn(aggregate);

      if(bcol != null) {
         return getValidExpression(aggregate, bcol);
      }

      String[] pair = splitAggregate(aggregate);

      if(pair == null) {
         return aggregate;
      }

      String form = pair[0];
      String path = pair[1];
      String npath = XUtil.removeQuote(path);
      String table = uniformSql.getTable(npath, false);

      // the column was written quoted (sum(t."MixedCase")), keep it as written on every
      // helper. The stored text is the same as the unquoted one on a case-sensitive helper,
      // and the column found ignoring case may be another column (MIXEDCASE), see #77578
      if(qcol != null && (table == null || !npath.endsWith("." + qcol))) {
         qcol = null;
      }

      String column = qcol != null ? qcol : uniformSql.getColumnFromPath(npath);
      String alias = getValidSubAlias(table, column);

      if(table == null || alias == null) {
         if(qcol != null) {
            return form + getQuotedTableName(table, true) + "." + getQuote() + qcol +
               getQuote() + ')';
         }

         if(table != null && column != null) {
            // keep a quoted column segment as written (sum(t."MixedCase")), the column found
            // ignoring case may be another column (MIXEDCASE). Only if the stored text can't have
            // come from a parser that quotes every segment (a case-sensitive helper, generating
            // here or where the sql was parsed): there an unquoted sum(t.MixedCase) is stored as
            // sum("t"."MixedCase") and must keep the metadata case repair. A quoted
            // sum(t."MixedCase") is told apart only by the parser's record above (#77578)
            String qseg = isCaseSensitive() ? null : getQuotedSegment(path, column);
            String qcolumn;

            if(qseg != null) {
               qcolumn = getQuote() + qseg + getQuote();
            }
            // a column of a physical table written unquoted. The alias rule (always quoted on
            // oracle) would name another column (max(a."v")), see #77646. A column of a derived
            // table may be the inner select's alias, which is quoted with the alias rule there,
            // and on a case-sensitive helper the column may have been written quoted
            else if(!isCaseSensitive() && !(uniformSql.getTableName(table) instanceof UniformSQL)) {
               qcolumn = quoteAggregateColumn(column);
            }
            else {
               qcolumn = quoteColumnAlias(column);
            }

            return form + getQuotedTableName(table, true) + "." + qcolumn + ')';
         }

         if(XUtil.isQualifiedName(npath)) {
            return form + path + ')';
         }

         return aggregate;
      }

      return form + quoteTableAlias(table) + "." + quoteColumnAlias(alias) + ')';
   }

   /**
    * Get the last segment of a path if it is a quoted identifier ("MixedCase" or `MixedCase`)
    * that names the column, ignoring case, and its qualifier is not quoted too.
    * @return the segment without its quotes, or <tt>null</tt> if not quoted.
    */
   private static String getQuotedSegment(String path, String column) {
      int len = path.length();

      for(char q : new char[] { '"', '`' }) {
         if(len > 1 && path.charAt(len - 1) == q) {
            int start = path.lastIndexOf(q, len - 2);
            String seg = start >= 0 ? path.substring(start + 1, len - 1) : null;

            if(seg != null && seg.equalsIgnoreCase(column)) {
               String qualifier = path.substring(0, start);

               // a quoted qualifier ("q"."MixedCase") may come from a parser that quotes every
               // segment (parsed under a case-sensitive helper), the quotes don't show the source
               if(qualifier.length() > 2 && qualifier.charAt(0) == q && qualifier.endsWith(q + ".")) {
                  return null;
               }

               return seg;
            }
         }
      }

      return null;
   }

   /**
    * Get the formula part and aggregate part from an aggregate expression.
    * For example, providing "sum(t.c)", the result is ["sum", "t.c"].
    */
   private String[] splitAggregate(String aggregate) {
      if(!aggregate.endsWith(")") ||
         (aggregate.indexOf(')') != aggregate.lastIndexOf(')')))
      {
         return null;
      }

      int index = aggregate.indexOf('(');

      if(index <= 0) {
         return null;
      }

      // count(distinct column) pattern
      int index2 = aggregate.indexOf("distinct ", index + 1);

      if(index2 != index + 1) {
         index2 = aggregate.indexOf("DISTINCT ", index + 1);
      }

      String form = index2 != index + 1 ? aggregate.substring(0, index + 1) :
         aggregate.substring(0, index2 + 9);
      String column = index2 != index + 1 ?
         aggregate.substring(index + 1, aggregate.length() - 1) :
         aggregate.substring(index2 + 9, aggregate.length() - 1);

      // @by billh, fix customer bug bug1306178630921
      // do not quote expression: cast(XXX as XXX) falsely
      if("cast(".equalsIgnoreCase(form)) {
         return null;
      }

      if(uniformSql.isTableColumn(column) || XUtil.isQualifiedName(column)) {
         return new String[] {form, column};
      }

      return null;
   }

   /**
    * Get the selection option.
    */
   protected String getSelectionOption() {
      return null;
   }

   /**
    * Generate from clause of SQL statement.
    * @return from clause.
    */
   public String generateFromClause() {
      // @by henryh, allow database helper decide outer join syntax
      return isAnsiJoin0() ? generateFromClauseAnsi() : generateFromClause0();
   }

   /**
    * Generate from clause of SQL statement.
    * @return from clause.
    */
   private String generateFromClause0() {
      int count = uniformSql.getTableCount();

      if(count <= 0) {
         return "";
      }

      StringBuilder from = new StringBuilder();
      from.append(FROM);

      for(int i = 0; i < count; i++) {
         SelectTable table = uniformSql.getSelectTable(i);

         // tables without an alias written in two quotings (MyTab and "MyTab") are two
         // tables, one table, as before, when the name isn't folded (Bug #77643)
         if(uniformSql.isHiddenTwin(table)) {
            continue;
         }

         if(from.length() > FROM.length()) {
            from.append(COMMA_GAP);
         }

         from.append(generateTableClause(table));
      }

      return from.toString();
   }

   // check if a table has no alias, see SQLParser addTable
   private boolean isUnaliased(SelectTable table) {
      Object name = table.getName();
      String alias = table.getAlias();

      return name instanceof String && alias != null &&
         (alias.equals(name) || alias.equals(((String) name).replace(getQuote(), "")) ||
          isFoldedTwin(table));
   }

   /**
    * Check if a table without an alias is named by its name as the database folds it ("mytab"
    * for MyTab written unquoted on postgresql), because a table of the same stored name was
    * written quoted ("MyTab"), see SQLParser addTable (Bug #77643). The name isn't an alias.
    */
   private boolean isFoldedTwin(SelectTable table) {
      Object name = table.getName();
      String alias = table.getAlias();
      String quote = getQuote();

      return name instanceof String && alias != null && !quote.isEmpty() &&
         alias.startsWith(quote) && !alias.equals(name) && alias.equalsIgnoreCase((String) name);
   }

   /**
    * Get the string (or UniformSQL) to use for the table name on FROM clause.
    */
   private Object getFromTable(Object tbl) {
      if(tbl instanceof UniformSQL) {
         UniformSQL sql = (UniformSQL) tbl;
         Object hint = sql.getHint(UniformSQL.HINT_INPUT_MAXROWS, false);

         if(!Tool.equals(inpmaxrows + "", hint)) {
            sql.setHint(UniformSQL.HINT_INPUT_MAXROWS, inpmaxrows + "");
            sql.clearCachedString();
         }

         return tbl;
      }
      else if(!isTableSubquery()) {
         return tbl.toString();
      }

      String str = tbl.toString();
      String ostr = null;

      if(str.startsWith("select ")) {
         ostr = str;
         str = "t" + System.currentTimeMillis();
      }

      String sqlstr = getTableWithLimit(str, inpmaxrows);

      if(sqlstr == null && uniformSql.hasVPMCondition()) {
         return tbl;
      }

      if(ostr != null) {
         sqlstr = sqlstr.replaceFirst(quoteTableName(str),
            "(" + ostr + ") " + str);
      }

      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(false);
      sql.setSQLString(sqlstr);
      sql.setSqlQuery(uniformSql.isSqlQuery());

      return sql;
   }

   /**
    * Get a sql string for replacing the table name so a row limit is added
    * to restrict the number of rows from the table.
    */
   protected String getTableWithLimit(String tbl, int maxrows) {
      return null;
   }

   /**
    * Get the loyal join.
    * @param vjoin the specified join list.
    * @return the loyal join if any, <tt>null</tt> otherwise.
    */
   private List<XJoin> getLoyalJoins(List<XJoin> vjoin) {
      for(int i = 0; vjoin != null && i < vjoin.size(); i++) {
         List<XJoin> joins = new ArrayList<>(vjoin);

         if(isLoyalJoin(i, joins)) {
            return joins;
         }
      }

      return null;
   }

   /**
    * Check if is a loyal join.
    * @param index the specified start point.
    * @param joins the specified join list.
    * @return <tt>true</tt> if yes, meanwhile joins reordered;
    * <tt>false</tt> otherwise.
    */
   private boolean isLoyalJoin(int index, List<XJoin> joins) {
      XJoin join = joins.get(index);
      Object[] ltables = getJoinedTable(join, true);
      Object[] rtables = getJoinedTable(join, false);

      if(ltables == null || rtables == null) {
         return false;
      }

      Object ftable = ltables[1];
      Object ttable = rtables[1];
      List<XJoin> njoins = new ArrayList<>();
      njoins.add(join);
      joins.remove(index);

      boolean result = isLoyalJoin(ftable, ttable, joins, njoins);

      if(!result && !join.isOuterJoin()) {
         result = isLoyalJoin(ttable, ftable, joins, njoins);
      }

      return result;
   }

   /**
    * Check if is a loyal join.
    * @param ftable the specified from table.
    * @param ttable the specified to table.
    * @param joins the specified join list.
    * @param njoins the specified new join list.
    * @return <tt>true</tt> if yes, meanwhile joins reordered;
    * <tt>false</tt> otherwise.
    */
   private boolean isLoyalJoin(Object ftable, Object ttable, List<XJoin> joins, List<XJoin> njoins) {
      while(true) {
         for(int i = 0; i < joins.size(); i++) {
            XJoin join2 = joins.get(i);
            Object[] ltables2 = getJoinedTable(join2, true);
            Object[] rtables2 = getJoinedTable(join2, false);

            if(ltables2 == null || rtables2 == null) {
               return false;
            }

            Object ftable2 = ltables2[1];
            Object ttable2 = rtables2[1];

            if(Tool.equals(ftable2, ftable) && Tool.equals(ttable2, ttable)) {
               njoins.add(join2);
               joins.remove(i--);
            }
            else if(Tool.equals(ftable2, ttable) &&
                    Tool.equals(ttable2, ftable)) {
               if(!join2.isOuterJoin()) {
                  njoins.add(join2);
                  joins.remove(i--);
               }
               else {
                  return false;
               }
            }
         }

         ftable = ttable;
         ttable = null;

         for(XJoin join : joins) {
            Object[] ltables2 = getJoinedTable(join, true);
            Object[] rtables2 = getJoinedTable(join, false);

            if(ltables2 == null || rtables2 == null) {
               return false;
            }

            Object ftable2 = ltables2[1];
            Object ttable2 = rtables2[1];

            if(Tool.equals(ftable2, ftable)) {
               ttable = ttable2;
               break;
            }
            else if(!join.isOuterJoin() && Tool.equals(ttable2, ftable)) {
               ttable = ftable2;
               break;
            }
         }

         if(ttable == null) {
            if(joins.isEmpty()) {
               joins.addAll(njoins);
               return true;
            }
            else {
               return false;
            }
         }
      }
   }

   /**
    * Generate from clause of SQL statement according to Ansi standard.
    * @return from clause.
    */
   private String generateFromClauseAnsi() {
      int count = uniformSql.getTableCount();

      if(count <= 0) {
         return "";
      }

      // the joins before they are reordered, to generate again without cycle conditions
      List<XJoin> vJoin0 = vJoin == null ? null : new ArrayList<>(vJoin);
      int val = -1;
      boolean sorted = false;

      // check if join priority is defined. If defined, we shoud not reorder the
      // joins. Instead, we may just follow the orders defined in XPartition
      for(int i = 0; vJoin != null && i < vJoin.size(); i++) {
         XJoin join = vJoin.get(i);
         int torder = join.getOrder();

         if(i == 0) {
            val = torder;
         }
         else if(val != torder) {
            sorted = true;
            break;
         }
      }

      // joins parsed from SQL text keep the nesting of the text, e.g.
      // a lj b on a.id = b.id join c on b.id = c.id lj d on a.id = d.id is
      // ((a lj b) join c) lj d. Reordering them (below) can move an inner join
      // to the null-supplying side of an outer join and change the result
      if(!sorted && !noCycleConditions && isTextJoinOrder()) {
         String from = generateFromClauseText(count);

         if(from != null) {
            return from;
         }
      }

      List<XJoin> njoin = sorted ? vJoin : getLoyalJoins(vJoin);

      // @by billh, if we could keep the definition loyally without changing
      // A *= B to B =* A, the join sentence should match user's need best
      if(njoin != null) {
         vJoin = njoin;
      }
      // @by larryl, sort the joins so outer joins are processed last. This
      // is to guarantee that outer joins are not inner joined with other
      // tables. For example, if we have a query of A *= B and B = C. The
      // join can be (A *= B) = C or (B = C) =* A. The second form is
      // preferred since it will guarantee all A records are returned.
      else {
         if(vJoin != null) {
            vJoin.sort(new OuterJoinComparator());
         }
      }

      // holds [Table Name] --> [Index] relationship
      Map<Object, Integer> tables = new HashMap<>();
      /*
       *
       * Holds an array of Deque<Join>.
       * e.g.  A -> B, C -> D, B -> A, A -> D, B -> C, A -> D
       *   A  B  C  D
       * A    2     2
       * B       1
       * C          1
       * D
       * The numbers indicate a Linked List of the joins between
       * the two tables at X, Y
       */
      @SuppressWarnings("unchecked")
      Deque<XJoin>[][] joins = new Deque[count][count];

      for(int c = 0; vJoin != null && c < vJoin.size(); c++) {
         XJoin join = vJoin.get(c);

         if(join != null && getAnsiJoin(join.getOp(), false) != null) {
            Object[] table1 = getJoinedTable(join, true);
            Object[] table2 = getJoinedTable(join, false);

            tables.computeIfAbsent(table1[0], k -> tables.size());
            tables.computeIfAbsent(table2[0], k -> tables.size());

            int index1 = tables.get(table1[0]);
            int index2 = tables.get(table2[0]);

            if(index1 >= count || index2 >= count) {
               LOG.warn("Join ignored, a table of the join is not in the FROM clause: {}", join);
               continue;
            }

            //alway make index < index2, then it will be a upper tri-matrix
            if(index1 > index2) {
               int tmp = index2;
               index2 = index1;
               index1 = tmp;
            }

            Deque<XJoin> j = joins[index1][index2];

            // A -> B or B -> A is the same, and hence is grouped together
            // and hence both these Joins will be added to the end of one
            // Deque<XJoin> either ROW A COLUMN B or ROW B COLUMN A
            if(j == null) {
               if(joins[index2][index1] != null) {
                  j = joins[index2][index1];
                  int tmp = index2;
                  index2 = index1;
                  index1 = tmp;
               }
               else {
                  j = new ArrayDeque<>();
               }
            }

            String op = join.getOp();
            boolean key = isKeyJoin(op);

            if(!key) {
               j.add(join);
            }
            // reorder key operation to try appling inner/outer join properly,
            // for operations like greater than and less than do not contain
            // inner/outer information, then we move them back for the first
            // key operation to be used to build correct ansi join operation
            else {
               boolean found = false;

               for(Iterator<XJoin> i = j.descendingIterator(); i.hasNext();) {
                  XJoin tjoin = i.next();
                  String top = tjoin.getOp();
                  found = isKeyJoin(top);

                  if(found) {
                     break;
                  }
               }

               if(found) {
                  j.add(join);
               }
               else {
                  j.addFirst(join);
               }
            }

            joins[index1][index2] = j;
         }
      }

      int joinCount = 0;
      int startX;
      int startY;
      boolean independantJoin = false;
      StringBuilder from = new StringBuilder();
      Set<Object> usedtables = new HashSet<>();
      List<Point> tag = new ArrayList<>();

      /*
       * This loop will evaluate a max of count * count times,
       * But it should exhaust all joins before that in most of
       * the cases and terminate even before that with all joins
       * expressed in the from clause
       */
      int jsize = vJoin == null ? 0 : vJoin.size();
      int top = 0;
      ansiWhereJoins = null;
      // a join between two tables that are already joined closes a cycle of the join graph,
      // e.g. c in a = b, b = c, a = c. It is a condition of the joined tables, not a join of
      // a new table. Only write it as one if no join is under or/not, because the joins of
      // such a condition tree are already flattened into the from clause
      boolean cycleConditions = !noCycleConditions && vJoin != null &&
         vJoin.stream().allMatch(this::isJoinPathAnded);
      // the last join step of the current group, whose ON is the outermost one
      XJoin lastJoin = null;
      SelectTable lastTable = null;
      boolean lastTablePreserved = false;
      boolean lastAnded = true;
      // conditions that filter the group after an outer join step. They stay valid as where
      // conditions through inner and left join steps, but a right join step null-extends the
      // group, so they go into its ON
      List<XJoin> pending = new ArrayList<>();
      // true if the last join step is a right join that the pending conditions go into
      boolean lastRight = false;
      // the [start, end] of each comma separated group in from, and if it has a RIGHT or FULL
      // join step outside of parentheses
      List<int[]> groupRanges = new ArrayList<>();
      List<Boolean> groupRightOrFull = new ArrayList<>();
      boolean rightOrFull = false;

      for(int i = 0; i < count; i++) {
         if(joinCount >= jsize) {
            break;
         }

         for(int j = 0; j < count; j++) {
            if(joinCount >= jsize) {
               break;
            }

            // a join between columns of one table at the start of a group would join the
            // table to itself. If the table has other joins, it's a condition of the group of
            // those joins, which reaches it when the table is joined. Otherwise the table is
            // not in a group, and the condition goes to the where clause
            if(i == j && cycleConditions && joins[i][j] != null && joins[i][j].size() > 0) {
               if(hasOtherJoins(joins, count, i)) {
                  continue;
               }

               for(XJoin cjoin : joins[i][j]) {
                  addAnsiWhereJoin(cjoin);
                  joinCount++;
               }

               joins[i][j].clear();
               continue;
            }

            // find a starting point to start constructing the from clause
            if(joins[i][j] != null && joins[i][j].size() > 0) {
               startX = i;
               startY = j;
               lastJoin = null;
               lastRight = false;
               pending.clear();

               /*
                * When no more related joins are found, seperate the
                * Join clause
                */
               if(independantJoin) {
                  from.append(COMMA_GAP);
                  top = from.length();
               }

               rightOrFull = false;
               boolean newTables = true;
               XJoin previousJoin = null;

               /*
                * We start with A-> B. Create an expression for all
                * A -> B, B -> A, A -> *, B -> * joins where A, B keep
                * changing as we evaluate more joins by modifying startX,
                * startY
                */
               while(true) {
                  XJoin join = joins[startX][startY].getFirst();
                  Object[] table1 = getJoinedTable(join, true);
                  Object[] table2 = getJoinedTable(join, false);
                  String tableOne = null;
                  String tableTwo = null;
                  boolean traverse = false;

                  if(newTables && cycleConditions && lastJoin != null &&
                     isCycleJoinCell(joins[startX][startY], table1[1], table2[1], usedtables))
                  {
                     for(XJoin cjoin : joins[startX][startY]) {
                        if(lastAnded && isCycleJoinInLastOn(cjoin, lastJoin, lastTable,
                                                            lastTablePreserved))
                        {
                           // AND, never the relation of the previous join (which can be or)
                           from.append(AND);
                           from.append(buildAnsiJoinCondition(cjoin));
                           from.append(" ");
                        }
                        else {
                           pending.add(cjoin);
                        }

                        joinCount++;
                     }

                     joins[startX][startY].clear();
                  }
                  else {
                     if(newTables) {
                        tableOne = (String) table1[0];
                        tableTwo = (String) table2[0];

                        if(usedtables.contains(table1[1])) {
                           tableOne = null;
                        }
                        else if(usedtables.contains(table2[1])) {
                           tableTwo = tableOne;
                           tableOne = null;
                           traverse = true;
                        }

                        usedtables.add(table1[1]);
                        usedtables.add(table2[1]);
                     }

                     String op = getAnsiJoin(join.getOp(), traverse);
                     appendJoinClause(from, join, op, tableOne, tableTwo, top, previousJoin);

                     if(newTables) {
                        rightOrFull = isTopRightOrFull(rightOrFull, op, tableOne == null);
                        lastJoin = join;
                        lastTable = (SelectTable) (traverse ? table1[1] : table2[1]);
                        lastTablePreserved = isPreservedTable(join, traverse);
                        lastAnded = true;
                        lastRight = join.isOuterJoin() && !join.isFullOuterJoin() &&
                           lastTablePreserved;

                        // a full join null-extends the group and the new table, so no
                        // placement of the pending conditions keeps their meaning. Generate
                        // the joins as before, without cycle conditions
                        if(join.isFullOuterJoin() && !pending.isEmpty()) {
                           return generateFromClauseAnsiAgain(vJoin0);
                        }
                     }

                     lastAnded = lastAnded && isJoinAnded(join);
                     previousJoin = join;
                     joinCount++;
                     joins[startX][startY].removeFirst();
                  }

                  // There are multiple joins between same tables
                  // e.g. A -> B's or B -> A's, evaluate the next one
                  // startX, startY remain the same
                  if(joins[startX][startY] != null &&
                     joins[startX][startY].size() > 0)
                  {
                     newTables = false;
                     continue;
                  }

                  // the right join step is complete, AND the pending conditions into its ON
                  if(lastRight && !pending.isEmpty()) {
                     if(!lastAnded) {
                        return generateFromClauseAnsiAgain(vJoin0);
                     }

                     for(XJoin cjoin : pending) {
                        from.append(AND);
                        from.append(buildAnsiJoinCondition(cjoin));
                        from.append(" ");
                     }

                     pending.clear();
                  }

                  lastRight = false;
                  tag.add(new Point(startX, startY));
                  newTables = true;

                  Point nextP = findNextStart(count, joins, tag);

                  if(nextP != null) {
                     startX = nextP.x;
                     startY = nextP.y;
                     continue;
                  }

                  // no later step null-extends the group
                  for(XJoin cjoin : pending) {
                     addAnsiWhereJoin(cjoin);
                  }

                  pending.clear();
                  groupRanges.add(new int[] { top, from.length() });
                  groupRightOrFull.add(rightOrFull);

                  // if we reach here that means no related joins found
                  // moved ahead to find other groups
                  independantJoin = true;
                  break;
               }
            }
         }
      }

      List<String> groups = new ArrayList<>();

      for(int[] range : groupRanges) {
         groups.add(from.substring(range[0], range[1]));
      }

      // the groups are separated by commas only, see generateFromClauseText
      if(String.join(COMMA_GAP, groups).contentEquals(from)) {
         from = new StringBuilder(String.join(COMMA_GAP, orderCommaGroups(groups, groupRightOrFull)));
      }

      return finishFromClause(from, usedtables, count);
   }

   /**
    * Check if a join group has a RIGHT or FULL join step outside of parentheses, after a
    * step is added to it.
    * @param rightOrFull if the group had one before the step.
    * @param op the ANSI join of the step.
    * @param joinsGroup true if the step joins a table to the group, false if it starts the
    * group. appendJoinClause puts the group before such a step in parentheses, unless the
    * database doesn't support them (MongoHelper).
    */
   private boolean isTopRightOrFull(boolean rightOrFull, String op, boolean joinsGroup) {
      if(joinsGroup && isJoinParenthesesSupported()) {
         rightOrFull = false;
      }

      return rightOrFull || isRightOrFullJoin(op);
   }

   private static boolean isRightOrFullJoin(String op) {
      return " RIGHT OUTER JOIN ".equals(op) || " FULL OUTER JOIN ".equals(op);
   }

   /**
    * Order the comma separated join groups of a from clause so no group after a comma has a
    * RIGHT or FULL join outside of parentheses. SQLite and HSQLDB give a comma and a JOIN the
    * same precedence and read x, c RIGHT OUTER JOIN d ON .. as (x, c) RIGHT OUTER JOIN d ON ..,
    * which null-extends the rows of x once instead of joining every row of x, so it returns
    * different rows than x, (c RIGHT OUTER JOIN d ON ..), the way the other databases read it.
    * An INNER or LEFT join of a group after a comma returns the same rows either way, since
    * its condition only names the tables of its group (Bug #77675).
    * <p>
    * The first such group is moved before the other groups, which returns the same rows,
    * as the groups are independent. Any other such group is put in parentheses, e.g. a FULL
    * join group after another FULL join group. When the select list has a * column, every
    * such group after the first group is put in parentheses instead of moving one, which would
    * change the order of its columns. A database without join parentheses (MongoHelper)
    * gets only the move.
    * @param groups the text of each group, in from order.
    * @param rightOrFull for each group, if it has a RIGHT or FULL join outside of parentheses.
    * @return the groups in their new order.
    */
   private List<String> orderCommaGroups(List<String> groups, List<Boolean> rightOrFull) {
      int first = rightOrFull.indexOf(Boolean.TRUE);

      if(first < 0 || first == 0 && rightOrFull.lastIndexOf(Boolean.TRUE) == 0) {
         return groups;
      }

      boolean parens = isJoinParenthesesSupported();
      boolean star = isStarSelected();
      boolean move = !parens || !star;
      List<String> ordered = new ArrayList<>();

      if(move) {
         ordered.add(groups.get(first));
      }

      for(int i = 0; i < groups.size(); i++) {
         if(move && i == first) {
            continue;
         }

         boolean nested = parens && rightOrFull.get(i) && !ordered.isEmpty();
         ordered.add(nested ? "(" + groups.get(i) + ")" : groups.get(i));
      }

      commaGroupsChanged = true;
      // moving the only such group returns the same rows and columns, anything else leaves a
      // group after a comma (in parentheses, or not on MongoHelper) or changes the order of
      // the * columns
      commaGroupsNotMovable = star || rightOrFull.indexOf(Boolean.TRUE) !=
         rightOrFull.lastIndexOf(Boolean.TRUE);
      return ordered;
   }

   /**
    * Check if the select list has a * column (* or t.*), whose columns are in from order.
    */
   private boolean isStarSelected() {
      XSelection selection = uniformSql.getSelection();
      int count = selection == null ? 0 : selection.getColumnCount();

      if(count == 0) {
         return true;
      }

      for(int i = 0; i < count; i++) {
         String column = selection.getColumn(i);

         if(column != null && (column.trim().equals("*") || column.trim().endsWith(".*"))) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the last generated sql moved or parenthesized a comma separated join group of a
    * from clause (orderCommaGroups), which needs the joins of the query to be independent of
    * the order of its from items. A parsed query isn't: SQLite reads the commas of the
    * original sql with the precedence of a JOIN (Bug #77675).
    */
   public boolean isCommaGroupsChanged() {
      return commaGroupsChanged;
   }

   /**
    * Check if the last generated sql changed the comma separated join groups of a from clause
    * (isCommaGroupsChanged) other than by moving its only group with a RIGHT or FULL join
    * outside of parentheses to the front: it has two or more such groups, so one of them is
    * still after a comma (in parentheses, or without them on MongoHelper), or its select list
    * has a * column, so the group is put in parentheses or its columns are reordered.
    */
   public boolean isCommaGroupsNotMovable() {
      return commaGroupsNotMovable;
   }

   /**
    * Add the tables that are not in any join to the from clause.
    * @param from the joined tables.
    * @param usedtables the tables in the joins.
    * @param count the table count.
    * @return the from clause.
    */
   private String finishFromClause(StringBuilder from, Set<Object> usedtables, int count) {
      if(from.length() > 0) {
         from.append(COMMA_GAP);
      }

      for(int i = 0; i < count; i++) {
         SelectTable stable = uniformSql.getSelectTable(i);

         // a twin that is one table with its twin written quoted, see generateFromClause0
         if(usedtables.contains(stable) || uniformSql.isHiddenTwin(stable)) {
            continue;
         }
         else {
            from.append(generateTableClause(stable));
            from.append(COMMA_GAP);
         }
      }

      from.insert(0, FROM);

      return from.substring(0, isFormatSQL ? from.toString().length() - 9 :
         from.toString().length() - 2);
   }

   /**
    * Check if the joins are generated in text order: some join was parsed from
    * SQL text, the query has an outer join (inner joins can be reordered
    * freely), and no outer join is written in the where clause (e.g.
    * a.id *= b.id), whose meaning the text order can't keep.
    */
   private boolean isTextJoinOrder() {
      if(textJoinOrder == null) {
         textJoinOrder = isTextJoinOrder0();
      }

      return textJoinOrder;
   }

   private boolean isTextJoinOrder0() {
      XJoin[] joins = uniformSql.getJoins();
      boolean parsed = false;
      boolean outer = false;

      if(joins == null || !isTextJoinOrderSupported()) {
         return false;
      }

      for(XJoin join : joins) {
         if(join.isWhereClauseJoin() && join.isOuterJoin()) {
            return false;
         }

         parsed = parsed || join.getJoinClause() != XJoin.UNKNOWN_CLAUSE;
         outer = outer || join.isOuterJoin();
      }

      return parsed && outer;
   }

   /**
    * Check if the database supports the text order from clause, which keeps
    * where clause joins as where conditions (comma separated tables) and can
    * join parenthesized groups of joined tables, e.g.
    * (a join b) left join (c join d) on b.id = c.id.
    * A helper that returns false gets the outer-last join order for parsed
    * joins too, which can put an inner join on the null-supplying side of an
    * outer join and return wrong rows (#77581). A helper that can't write
    * parentheses overrides appendJoinClause and isJoinParenthesesSupported
    * instead, as MongoHelper does.
    */
   protected boolean isTextJoinOrderSupported() {
      return true;
   }

   /**
    * Check if the database supports parentheses around joined tables in the
    * from clause. Without them (MongoHelper), a text order join between two
    * groups of tables is written as one flat chain when that keeps its meaning,
    * and otherwise only its right group is parenthesized.
    */
   protected boolean isJoinParenthesesSupported() {
      return true;
   }

   /**
    * Generate the ansi from clause with the joins in text order. Each ON
    * clause is one join step, in text order, followed by the joins not parsed
    * from an ON clause (e.g. added in the query editor), one table pair at a
    * time. A step that adds one table joins it to the group of the tables it
    * references; a step of two new tables starts a comma separated group; a
    * step between two groups (a parenthesized join) joins the nested groups.
    * @param count the table count.
    * @return the from clause, or <tt>null</tt> if a step doesn't fit one of
    * these forms.
    */
   private String generateFromClauseText(int count) {
      Map<Integer, List<XJoin>> onClauses = new TreeMap<>();
      Map<Set<Object>, List<XJoin>> pairs = new LinkedHashMap<>();
      // the [table clause, select table] of each side of each join
      Map<XJoin, Object[][]> joinTables = new IdentityHashMap<>();

      for(int i = 0; vJoin != null && i < vJoin.size(); i++) {
         XJoin join = vJoin.get(i);

         // same as the join matrix, which ignores these joins
         if(getAnsiJoin(join.getOp(), false) == null) {
            continue;
         }

         Object[] table1 = getJoinedTable(join, true);
         Object[] table2 = getJoinedTable(join, false);

         if(table1[1] == null || table2[1] == null) {
            return null;
         }

         joinTables.put(join, new Object[][] { table1, table2 });

         if(join.isOnClauseJoin()) {
            onClauses.computeIfAbsent(join.getJoinClause(), k -> new ArrayList<>()).add(join);
         }
         // a condition on one table that is not in an ON doesn't join anything
         else if(table1[1].equals(table2[1])) {
            return null;
         }
         else {
            Set<Object> pair = new HashSet<>(Arrays.asList(table1[1], table2[1]));
            pairs.computeIfAbsent(pair, k -> new ArrayList<>()).add(join);
         }
      }

      List<List<XJoin>> steps = new ArrayList<>(onClauses.values());
      steps.addAll(pairs.values());
      List<TextJoinGroup> groups = new ArrayList<>();

      for(List<XJoin> step : steps) {
         if(!appendTextJoinStep(groups, step, joinTables)) {
            return null;
         }
      }

      Set<Object> usedtables = new HashSet<>();
      List<String> texts = new ArrayList<>();
      List<Boolean> rightOrFull = new ArrayList<>();

      for(TextJoinGroup group : groups) {
         texts.add(group.text.toString());
         rightOrFull.add(group.rightOrFull);
         usedtables.addAll(group.tables);
      }

      StringBuilder from =
         new StringBuilder(String.join(COMMA_GAP, orderCommaGroups(texts, rightOrFull)));
      return finishFromClause(from, usedtables, count);
   }

   /**
    * Add one join step to the text order groups.
    * @return <tt>false</tt> if the step doesn't fit a text order form.
    */
   private boolean appendTextJoinStep(List<TextJoinGroup> groups, List<XJoin> step,
                                      Map<XJoin, Object[][]> joinTables)
   {
      // the conditions of the step are written as one AND list, so they must
      // be ANDed in the ON, e.g. not on b.id = c.id and (a.x = c.x or ...)
      if(!step.stream().allMatch(this::isAndedJoin)) {
         return false;
      }

      // the step is written as one join, so its joins must have one join type,
      // e.g. not an ON condition changed to an outer join in the query editor
      String stepOp = step.get(0).getOp();
      boolean outer = step.get(0).isOuterJoin();

      if(!step.stream().allMatch(join -> join.isOuterJoin() == outer &&
                                 (!outer || join.getOp().equals(stepOp))))
      {
         return false;
      }

      Map<Object, String> names = new LinkedHashMap<>();

      for(XJoin join : step) {
         for(Object[] table : joinTables.get(join)) {
            names.putIfAbsent(table[1], (String) table[0]);
         }
      }

      List<Object> newTables = new ArrayList<>();
      Set<TextJoinGroup> joined = new LinkedHashSet<>();

      for(Object table : names.keySet()) {
         TextJoinGroup group = findTextJoinGroup(groups, table);

         if(group == null) {
            newTables.add(table);
         }
         else {
            joined.add(group);
         }
      }

      TextJoinGroup group;

      if(newTables.size() == 2 && joined.isEmpty()) {
         XJoin anchor = findAnchorJoin(step, joinTables, newTables.get(0), newTables.get(1));

         if(anchor == null) {
            return false;
         }

         Object[][] tables = joinTables.get(anchor);
         // a parsed join is written with its tables in from order, e.g.
         // a join b on b.id = a.id is a INNER JOIN b, not b INNER JOIN a. Not on a
         // helper without join parentheses (MongoHelper), whose flat group joins
         // (appendFlatGroupJoins) depend on the group's first table being table1
         boolean traverse = isJoinParenthesesSupported() &&
            (anchor.isOnClauseJoin() || anchor.isWhereClauseJoin()) &&
            getSelectTableIndex(tables[0][1]) > getSelectTableIndex(tables[1][1]);
         group = new TextJoinGroup();
         String op = getAnsiJoin(anchor.getOp(), traverse);
         group.first = tables[traverse ? 1 : 0][1];
         group.firstName = (String) tables[traverse ? 1 : 0][0];
         group.leftDeep = isInnerOrLeftJoin(op);
         group.rightOrFull = isRightOrFullJoin(op);
         appendTextJoins(group, step, anchor, op, group.firstName,
                         (String) tables[traverse ? 0 : 1][0]);
         groups.add(group);
      }
      else if(newTables.size() == 1 && joined.size() == 1) {
         Object table = newTables.get(0);
         XJoin anchor = findAnchorJoin(step, joinTables, table, null);

         if(anchor == null) {
            return false;
         }

         // the new table is table1 of the join, so the join is written from
         // the other side, e.g. a *= b adding a is b RIGHT OUTER JOIN a
         boolean traverse = table.equals(joinTables.get(anchor)[0][1]);
         group = joined.iterator().next();
         String op = getAnsiJoin(anchor.getOp(), traverse);
         group.leftDeep = group.leftDeep && isInnerOrLeftJoin(op);
         group.rightOrFull = isTopRightOrFull(group.rightOrFull, op, true);
         appendTextJoins(group, step, anchor, op, null, names.get(table));
      }
      else if(newTables.isEmpty() && joined.size() == 2) {
         XJoin anchor = null;

         for(XJoin join : step) {
            Object[][] tables = joinTables.get(join);

            if(findTextJoinGroup(groups, tables[0][1]) != findTextJoinGroup(groups, tables[1][1])) {
               anchor = join;
               break;
            }
         }

         if(anchor == null) {
            return false;
         }

         TextJoinGroup left = findTextJoinGroup(groups, joinTables.get(anchor)[0][1]);
         TextJoinGroup right = findTextJoinGroup(groups, joinTables.get(anchor)[1][1]);

         boolean parens = isJoinParenthesesSupported();

         // a helper that can't write parentheses (MongoHelper, 56305) writes an
         // inner join of a group and a flat chain of inner and left joins as one
         // chain when that keeps its meaning (#77581), e.g.
         // a join b .. join (c left join d on c.id = d.id) on b.id = c.id is
         // a join b .. join c on b.id = c.id left join d on c.id = d.id
         if(!parens && !anchor.isOuterJoin()) {
            group = appendFlatGroupJoins(groups, step, joinTables, names.keySet(), left, right);

            if(group == null) {
               group = appendFlatGroupJoins(groups, step, joinTables, names.keySet(), right,
                                            left);
            }

            if(group != null) {
               // the joins of the right group follow the inner join step without parentheses
               group.rightOrFull = left.rightOrFull || right.rightOrFull;
               group.tables.addAll(names.keySet());
               return true;
            }
         }

         // the parser only reads a joined table on the right of a join as one
         // flat chain, e.g. a left join (c join d on .. join e on ..) on ..
         // Without parentheses support (MongoHelper), the left group is written
         // without them, as joins are left associative, and the right group
         // keeps them, since no flat chain keeps the meaning of
         // a join b .. left join (c join d ..) on b.id = c.id: the unity driver
         // may reject them (56305), where the outer-last order joins d to the
         // null-extended rows of c and returns wrong rows (#77581)
         group = new TextJoinGroup();
         group.leftDeep = false;
         String op = getAnsiJoin(anchor.getOp(), false);
         // the left group is in parentheses, or written without them (MongoHelper), the
         // right group always is
         group.rightOrFull = isRightOrFullJoin(op) || !parens && left.rightOrFull;
         appendTextJoins(group, step, anchor, op,
                         parens ? "(" + left.text + ")" : left.text.toString(),
                         "(" + right.flat + ")");
         group.tables.addAll(left.tables);
         group.tables.addAll(right.tables);
         groups.remove(left);
         groups.remove(right);
         groups.add(group);
      }
      else {
         return false;
      }

      group.tables.addAll(names.keySet());
      return true;
   }

   /**
    * Find the join of a step between two different tables, one of them a
    * given table.
    * @param table2 the other table, or <tt>null</tt> for any other table.
    */
   private static XJoin findAnchorJoin(List<XJoin> step, Map<XJoin, Object[][]> joinTables,
                                       Object table1, Object table2)
   {
      for(XJoin join : step) {
         Object[][] tables = joinTables.get(join);
         Object left = tables[0][1];
         Object right = tables[1][1];

         if(left.equals(right)) {
            continue;
         }

         if(left.equals(table1) && (table2 == null || right.equals(table2)) ||
            right.equals(table1) && (table2 == null || left.equals(table2)))
         {
            return join;
         }
      }

      return null;
   }

   /**
    * Get the from clause index of a select table.
    */
   private int getSelectTableIndex(Object table) {
      for(int i = 0; i < uniformSql.getTableCount(); i++) {
         if(uniformSql.getSelectTable(i) == table) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Check if a join is ANDed with the rest of its where clause or ON.
    */
   private boolean isAndedJoin(XJoin join) {
      for(XNode node = join.getParent(); node != null; node = node.getParent()) {
         if(node instanceof XSet && (((XSet) node).isIsNot() ||
            !XSet.AND.equalsIgnoreCase(((XSet) node).getRelation())))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Append the joins of one step to a text order group: the anchor join
    * with the table it adds, then the other conditions of the same ON.
    */
   private void appendTextJoins(TextJoinGroup group, List<XJoin> step, XJoin anchor, String op,
                                String table1, String table2)
   {
      appendJoinClause(group.text, anchor, op, table1, table2, 0, null);
      group.flat.append(makeJoinClause(anchor, op, table1, table2, null));
      XJoin previous = anchor;

      for(XJoin join : step) {
         if(join != anchor) {
            String joinOp = getAnsiJoin(join.getOp(), false);
            appendJoinClause(group.text, join, joinOp, null, null, 0, previous);
            group.flat.append(makeJoinClause(join, joinOp, null, null, previous));
            previous = join;
         }
      }
   }

   /**
    * Find the text order group that contains a table.
    */
   private static TextJoinGroup findTextJoinGroup(List<TextJoinGroup> groups, Object table) {
      for(TextJoinGroup group : groups) {
         if(group.tables.contains(table)) {
            return group;
         }
      }

      return null;
   }

   /**
    * A comma separated group of joined tables in a text order from clause.
    */
   private static final class TextJoinGroup {
      // the joins with each left-deep step in parentheses, like the old order
      private final StringBuilder text = new StringBuilder();
      // the same joins as one chain without the left-deep parentheses
      private final StringBuilder flat = new StringBuilder();
      private final Set<Object> tables = new HashSet<>();
      // the first table of the chain and its name in the from clause
      private Object first;
      private String firstName;
      // true if the chain has only inner and left joins and no nested group
      private boolean leftDeep;
      // true if the group has a RIGHT or FULL join step outside of parentheses
      private boolean rightOrFull;
   }

   /**
    * Write an inner join step between two text order groups as one flat chain:
    * the joins of the right group follow the step, which joins its first table,
    * e.g. a join b .. join (c left join d on c.id = d.id) on b.id = c.id is
    * a join b .. join c on b.id = c.id left join d on c.id = d.id. It keeps the
    * meaning when the step only references the left group and the first table
    * of the right group, and the right group is a chain of inner and left joins
    * (each joining a table to the tables before it).
    * @return the left group with the right group appended, or <tt>null</tt> if
    * the step can't be written this way.
    */
   private TextJoinGroup appendFlatGroupJoins(List<TextJoinGroup> groups, List<XJoin> step,
                                              Map<XJoin, Object[][]> joinTables,
                                              Set<Object> stepTables, TextJoinGroup left,
                                              TextJoinGroup right)
   {
      if(!right.leftDeep || right.first == null) {
         return null;
      }

      for(Object table : stepTables) {
         if(!left.tables.contains(table) && !table.equals(right.first)) {
            return null;
         }
      }

      XJoin anchor = findAnchorJoin(step, joinTables, right.first, null);

      if(anchor == null) {
         return null;
      }

      appendTextJoins(left, step, anchor, getAnsiJoin(anchor.getOp(), false), null,
                      right.firstName);
      // the joins of the right group after its first table
      String rest = right.flat.substring(right.firstName.length());
      left.text.append(rest);
      left.flat.append(rest);
      left.tables.addAll(right.tables);
      groups.remove(right);
      return left;
   }

   private static boolean isInnerOrLeftJoin(String op) {
      return " INNER JOIN ".equals(op) || " LEFT OUTER JOIN ".equals(op);
   }

   /**
    * Found the next start x or start y.
    */
   private Point findNextStart(int count, Deque<XJoin>[][] joins, List<Point> tag) {
      for(int i = tag.size() - 1; i >= 0; i--) {
         Point pt = tag.get(i);

         for(int k = 0; k < count; k++) {
            if(joins[pt.x][k] != null && joins[pt.x][k].size() > 0)
            {
               return new Point(pt.x, k);
            }

            if(joins[k][pt.y] != null && joins[k][pt.y].size() > 0)
            {
               return new Point(k, pt.y);
            }

            if(joins[k][pt.x] != null && joins[k][pt.x].size() > 0)
            {
               return new Point(k, pt.x);
            }

            if(joins[pt.y][k] != null && joins[pt.y][k].size() > 0)
            {
               return new Point(pt.y, k);
            }
         }
      }

      return null;
   }

   /**
    * Generate the ANSI from clause again, without writing the joins between joined tables as
    * conditions. It writes such a join as a join of its table again, so the sql fails instead
    * of returning wrong rows.
    * @param vJoin0 the joins before generateFromClauseAnsi() reordered them.
    */
   private String generateFromClauseAnsiAgain(List<XJoin> vJoin0) {
      vJoin = vJoin0;
      noCycleConditions = true;

      try {
         return generateFromClauseAnsi();
      }
      finally {
         noCycleConditions = false;
      }
   }

   /**
    * Check if a table has joins with other tables in the join matrix.
    */
   private static boolean hasOtherJoins(Deque<XJoin>[][] joins, int count, int index) {
      for(int k = 0; k < count; k++) {
         if(k != index && (joins[index][k] != null && !joins[index][k].isEmpty() ||
            joins[k][index] != null && !joins[k][index].isEmpty()))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if a matrix cell of the ANSI join walk joins no new table, i.e. if both of its
    * tables are already joined (the cell closes a cycle of the join graph) or its joins are
    * between columns of one table. Its joins are then conditions of the joined tables.
    */
   private boolean isCycleJoinCell(Deque<XJoin> cell, Object table1, Object table2,
                                   Set<Object> usedtables)
   {
      return table1 != null && table2 != null && usedtables.contains(table1) &&
         usedtables.contains(table2) && cell.stream().allMatch(this::isJoinAnded);
   }

   /**
    * Check if a join between two joined tables can be ANDed into the ON of the last join
    * step, which is the outermost ON of the group. An inner join can, unless the last step is
    * an outer join: in its ON the condition would keep the null-extended rows the inner join
    * removes, so it filters the group after that step instead (in the where clause, or in the
    * ON of a later right join step). An outer join can only if it has the outer join of the
    * last step, i.e. the table the last step joined is on the same (preserved or null
    * supplying) side of both joins. Any other outer join can't be written and is treated as
    * an inner join.
    */
   private boolean isCycleJoinInLastOn(XJoin join, XJoin lastJoin, SelectTable lastTable,
                                       boolean lastTablePreserved)
   {
      if(!lastJoin.isOuterJoin()) {
         return true;
      }

      if(!join.isOuterJoin() || join.isFullOuterJoin() || lastJoin.isFullOuterJoin()) {
         return false;
      }

      boolean leftPreserved = "*=".equals(join.getOp());
      Object preserved = getJoinedTable(join, leftPreserved)[1];
      Object optional = getJoinedTable(join, !leftPreserved)[1];
      return lastTable != null && lastTable == (lastTablePreserved ? preserved : optional);
   }

   /**
    * Check if the table a join step adds is the preserved side of its outer join.
    * @param traverse true if the step adds the left table of the join.
    */
   private static boolean isPreservedTable(XJoin join, boolean traverse) {
      return "*=".equals(join.getOp()) ? traverse : "=*".equals(join.getOp()) && !traverse;
   }

   /**
    * Check if a join is ANDed with the other conditions, i.e. no set above it is negated or
    * an or set with more than one condition. Unlike isJoinPathAnded(), the or sets of a join
    * group are not allowed: a join that is ANDed into an ON (or the where clause) as a cycle
    * condition, and a join step that a condition is ANDed after, must not be an alternative of
    * another join.
    */
   private boolean isJoinAnded(XJoin join) {
      for(XNode node = join.getParent(); node instanceof XSet; node = node.getParent()) {
         XSet set = (XSet) node;

         if(set.isIsNot() || !XSet.AND.equalsIgnoreCase(set.getRelation()) &&
            set.getChildCount() > 1)
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if a join is on a non-negated all-AND path of the where tree, allowing the or sets
    * of a join group built by UniformSQL.addJoin, which hold alternative joins of one pair of
    * tables that are written into the ON of that pair. This is the query-wide check: a join
    * group's or set is still a join of the query, so it doesn't turn cycle conditions off,
    * while isJoinAnded() keeps the cells of such a group from being written as conditions.
    */
   private boolean isJoinPathAnded(XJoin join) {
      for(XNode node = join.getParent(); node instanceof XSet; node = node.getParent()) {
         XSet set = (XSet) node;
         XNode parent = set.getParent();

         if(set.isIsNot() || !XSet.AND.equalsIgnoreCase(set.getRelation()) &&
            set.getChildCount() > 1 && !set.isGroup() &&
            !(parent instanceof XSet && ((XSet) parent).isGroup()))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Write a join as a condition, the way it's written in an ON clause.
    */
   private String buildAnsiJoinCondition(XJoin join) {
      return buildExpressionString(join.getExpression1(), true) + " " +
         getAnsiOp(join.getOp(), join.isIsNot()) + " " +
         buildExpressionString(join.getExpression2(), true);
   }

   /**
    * Add a join the ANSI FROM clause can't write to the conditions of the where clause.
    */
   private void addAnsiWhereJoin(XJoin join) {
      if(ansiWhereJoins == null) {
         ansiWhereJoins = new ArrayList<>();
      }

      ansiWhereJoins.add(buildAnsiJoinCondition(join));
   }

   /**
    * AND the joins the ANSI FROM clause couldn't write in an ON to a where clause.
    */
   private String appendAnsiWhereJoins(String where) {
      if(ansiWhereJoins == null || ansiWhereJoins.isEmpty()) {
         return where;
      }

      String joins = String.join(" " + AND, ansiWhereJoins);

      if(where == null || where.isEmpty()) {
         return WHERE + joins;
      }

      String condition = where.startsWith(WHERE) ? where.substring(WHERE.length()) : where;

      if(!isParenthesized(condition)) {
         condition = "(" + condition + ")";
      }

      return WHERE + condition + " " + AND + joins;
   }

   /**
    * Check if a condition is in one pair of parentheses, outside of quoted text.
    */
   private static boolean isParenthesized(String condition) {
      condition = condition.trim();

      if(!condition.startsWith("(") || !condition.endsWith(")")) {
         return false;
      }

      int depth = 0;
      char quote = 0;

      for(int i = 0; i < condition.length(); i++) {
         char c = condition.charAt(i);

         if(quote != 0) {
            if(c == quote) {
               quote = 0;
            }
         }
         else if(c == '\'' || c == '"') {
            quote = c;
         }
         else if(c == '(') {
            depth++;
         }
         else if(c == ')' && --depth == 0 && i < condition.length() - 1) {
            return false;
         }
      }

      return depth == 0 && quote == 0;
   }

   /**
    * Check if a join operation is a key operation.
    * @param op the specified join operation.
    * @return <tt>true</tt> if is a key operation, <tt>false</tt> otherwise.
    */
   private boolean isKeyJoin(String op) {
      return op.equals("*=") || op.equals("=*") || op.equals("*=*") ||
         op.equals("=");
   }

   /**
    * Abstract out the code for getting table name from the expresion.
    */
   private Object[] getJoinedTable(XJoin join, boolean left) {
      Object[] result = new Object[2];

      String tname = left ? join.getTable1(uniformSql) : join.getTable2(uniformSql);
      int index = uniformSql.getJoinTableIndex(tname);
      SelectTable stable = (index >= 0) ? uniformSql.getSelectTable(index) : null;
      String qname = stable != null ? null : quoteQuotedSegments(tname);
      String table = stable != null ? generateTableClause(stable) :
         qname != null ? qname : quoteTableName(tname);

      result[0] = table;
      result[1] = stable;

      return result;
   }

   /**
    * Generate where clause of SQL statement.
    * @return where clause.
    */
   public String generateWhereClause() {
      // after the ANSI FROM clause is generated, include the joins it couldn't write, so a
      // caller that looks for the where clause in the sql (appendLimitClause) finds it
      return appendAnsiWhereJoins(generateWhereClause0());
   }

   private String generateWhereClause0() {
      StringBuilder where = new StringBuilder();

      where.append(WHERE);
      XFilterNode root = uniformSql.getWhere();
      boolean oJoinPosition = ansiJoinPosition;
      String condition;

      try {
         // only joins on a non-negated all-AND path of the where tree can be moved into the
         // ANSI FROM clause
         ansiJoinPosition = !(root instanceof XSet) || isAnsiJoinTransparent((XSet) root, null);
         condition = generateConditions(root);
      }
      finally {
         ansiJoinPosition = oJoinPosition;
      }

      if(condition != null && !condition.trim().equals("")) {
         where.append(condition);
         return formatSubquery(where);
      }
      else {
         return "";
      }
   }

   /**
    * Get the order by column of a field.
    */
   protected String getOrderByColumn(String field) {
      String column = getAliasColumn(field);

      // is an expression?
      if(!XUtil.isQualifiedName(column)) {
         if(!XUtil.isQualifiedName(field)) {
            XSelection selection = uniformSql.getSelection();
            int index = selection.indexOfColumn(field);

            if(index >= 0) {
               String alias = selection.getAlias(index);

               if(alias != null && alias.length() > 0) {
                  field = alias;
               }
            }
         }

         return field;
      }
      // is not an expression?
      else {
         return column;
      }
   }

   /**
    * Two select aggregates may have the same text but different spellings (sum(t.MixedCase)
    * and sum(t."MixedCase") on a case-sensitive helper). An order by aggregate replaced by the
    * alias of a select column is sorted by the one of the same spelling, or by itself if none.
    * An order by aggregate without a record (e.g. set in the sort pane, or saved before it was
    * recorded) is not "unquoted": it is moved only to a select column without a record of the
    * same text, and otherwise keeps the alias getOrderByColumn found, as before #77578.
    * @param field the order by field.
    * @param sfield the field returned by getOrderByColumn.
    * @param qagg the quoted column recorded for the order by aggregate.
    */
   private String getOrderByAggregateAlias(JDBCSelection xselect, String field, String sfield,
                                           String qagg)
   {
      int idx = xselect.indexOfColumn(field);

      if(idx < 0 || sfield.equals(field) || !sfield.equals(xselect.getAlias(idx)) ||
         Objects.equals(qagg, xselect.getQuotedAggregate(idx)))
      {
         return sfield;
      }

      for(int i = 0; i < xselect.getColumnCount(); i++) {
         if(i != idx && field.equals(xselect.getColumn(i)) &&
            Objects.equals(qagg, xselect.getQuotedAggregate(i)))
         {
            String alias = xselect.getAlias(i);
            return alias != null && !alias.isEmpty() ? alias : field;
         }
      }

      return qagg != null ? field : sfield;
   }

   /**
    * Generate order by clause of SQL condition.
    * @return order by clause.
    */
   public String generateOrderByClause() {
      StringBuilder sort = new StringBuilder();
      JDBCSelection xselect = (JDBCSelection) uniformSql.getSelection();
      // each item with its own direction and quoting, two items may have the same text
      // ("MixedCase" and MixedCase, Bug #77573)
      OrderByItem[] orderItems = uniformSql.getOrderByItems();
      Object field;
      String order;
      Set<String> ordered = new HashSet<>();
      sort.append(ORDER_BY);

      for(int i = 0; i < orderItems.length; i++) {
         field = orderItems[i].getField();
         order = orderItems[i].getOrder();
         String sfield;

         if(field instanceof String) {
            sfield = (String) field;
            String qname = getQuotedName(sfield, uniformSql.isQuotedOrderBy(i));
            String qseg = getQuotedSegment(sfield, qname, uniformSql.getQuotedOrderByColumn(i));
            // an order by aggregate of a qualified quoted column (sum(t."MixedCase"))
            String qagg = uniformSql.getQuotedAggregate(sfield);
            String ofield = sfield;
            sfield = getOrderByColumn(sfield);
            sfield = getOrderByAggregateAlias(xselect, ofield, sfield, qagg);

            // some dbms (embedded derby) does not support sorting on field
            // in some cases. Here we try using its alias to work around
            if(!xselect.isAlias(sfield) && !supportsFieldSorting()) {
               int index = xselect.indexOfColumn(sfield);

               if(index >= 0) {
                  String alias = xselect.getAlias(index);

                  if(alias != null && alias.length() > 0) {
                     sfield = alias;
                  }
               }
            }

            int index = xselect.indexOfColumn(sfield);
            String alias = null;
            boolean expr = false;

            if(index >= 0) {
               String column = xselect.getColumn(index);
               String table = uniformSql.getTable(column);
               String c = uniformSql.getColumnFromPath(column);
               String subalias = getValidSubAlias(table, c);
               expr = xselect.isExpression(index);

               if(subalias != null) {
                  // use the same quote logic for subalias
                  // if alias in subquery has quote but have not at here
                  // the oracle database will notify column not found error.
                  sfield = quoteTableAlias(table) + "." + quoteColumnAlias(subalias);
                  alias = getValidAlias(xselect, index, c);
               }
               else {
                  alias = xselect.getValidAlias(index, this);
               }
            }

            if(xselect.isAlias(sfield) && alias != null && alias.length() > 0) {
               sfield = alias;

               if(!supportsAliasSorting()) {
                  sfield = xselect.getAliasColumn(xselect.getOriginalAlias(alias));
                  // the alias is sorted by its select column
                  qagg = index >= 0 ? xselect.getQuotedAggregate(index) : null;
               }
            }

            boolean aggr = xselect.isAggregate(sfield);

            if(aggr && !supportsOperation(AGGREGATE_COLUMN_ORDERBY) && index >= 0) {
               sfield = alias;
            }

            if(!xselect.isAlias(sfield)) {
               sfield = getValidAggregate(sfield, qagg);
            }

            if(uniformSql.isTableColumn(sfield) || uniformSql.isOrderDBField(sfield)) {
               sfield = quotePath(sfield, false, false, true);
            }
            // orderby column might be an alias, or an alias generated by JDBCSelection, not
            // a column or an expression that just starts with ALIAS_
            else if(xselect.isAlias(sfield) || GENERATED_ALIAS.matcher(sfield).matches()) {
               boolean same = false;
               boolean part = false;
               String oalias = xselect.getOriginalAlias(sfield);
               String ocolumn = xselect.getAliasColumn(oalias);

               if(ocolumn != null) {
                  String cstr = ocolumn.toLowerCase(Locale.ROOT);
                  String astr = sfield.toLowerCase(Locale.ROOT);
                  same = cstr.equals(astr);
                  part = cstr.endsWith("." + astr);
               }

               sfield = quoteColumnAlias(sfield, same, part);
            }
            else if(!aggr && !expr && isKeyword(sfield)) {
               sfield = quoteAlias(sfield);
            }
            else if(!XUtil.isQualifiedName(sfield)) {
               sfield = quoteExpressionCol(sfield);
            }

            if(qname != null) {
               sfield = quoteIdentifier(qname, sfield, qseg);
            }

            // table changed to a subquery, replace reference to table to alias
            if(isTableSubquery()) {
               sfield = replaceTableByAlias(true, sfield);
            }
            else {
               sfield = replaceTableByAlias(false, sfield);
            }

            // the names written unquoted, in the case the database reads them (Bug #77643)
            sfield = foldNames(sfield, uniformSql.getOrderByWrittenUnquoted(i));
         }
         else {
            sfield = field.toString();
         }

         // a key already generated is redundant, the first decides the direction as in sql
         if(ordered.contains(sfield)) {
            continue;
         }

         if(ordered.size() > 0) {
            sort.append(COMMA_GAP);
         }

         sort.append(sfield);

         if(order != null) {
            sort.append(" ");
            sort.append(order);
         }

         ordered.add(sfield);
      }

      if(orderItems.length > 0) {
         return sort.toString();
      }

      return "";
   }

   // the alias JDBCSelection generates for a column whose alias isn't valid
   private static final Pattern GENERATED_ALIAS = Pattern.compile("ALIAS_\\d+");

   /**
    * Get the column name for a column alias.
    */
   protected String getAliasColumn(String sfield) {
      String column = uniformSql.getSelection().getAliasColumn(sfield);

      // binding and EA maintain backup selection for alias lookup
      if(column == null) {
         column = uniformSql.getBackupSelection().getAliasColumn(sfield);
      }

      return column == null ? sfield : column;
   }

   /**
    * Generate group by clause of SQL statement.
    * @return group by clause.
    */
   public String generateGroupByClause() {
      StringBuilder group = new StringBuilder();
      group.append(GROUP_BY);

      if(uniformSql.isGroupByAll()) {
         group.append("all ");
      }

      Object[] groupField = uniformSql.getGroupBy();
      XSelection xselect = uniformSql.getSelection();

      for(int i = 0; groupField != null && i < groupField.length; i++) {
         Object sfield = groupField[i];

         if(sfield instanceof String) {
            // the quoting of this field, two fields may have the same text (Bug #77573)
            String qname = getQuotedName((String) sfield, uniformSql.isQuotedGroupBy(i));
            String qseg = getQuotedSegment((String) sfield, qname,
               uniformSql.getQuotedGroupByColumn(i));
            String column = xselect.getAliasColumn((String) sfield);
            column = column == null ? (String) sfield : column;
            String table = uniformSql.getTable(column);
            String c = uniformSql.getColumnFromPath(column);
            String subalias = getValidSubAlias(table, c);
            boolean expr = xselect.isExpression(column);
            boolean aliased = false;

            if(subalias != null) {
               // use the same quote logic for subalias
               // if alias in subquery has quote but have not at here
               // the oracle database will notify column not found error.
               column = quoteTableAlias(table) + "." + quoteColumnAlias(subalias);
               aliased = true;
            }
            else {
               String bcol = xselect.getBaseColumn(column);

               if(bcol != null) {
                  column = getValidExpression(column, bcol);
               }
            }

            if(uniformSql.isTableColumn(column) ||
               XUtil.isQualifiedName(column) && (!expr || aliased) || uniformSql.isGroupDBField(column))
            {
               String quote = getQuote();

               if(!Tool.isEmptyString(table) && (!uniformSql.isSqlQuery() || !column.contains(quote)) &&
                  table.contains(quote) && column.startsWith(table))
               {
                  String cpart = column.substring(table.length() + 1);
                  column = table + "." + quoteColumnPartAlias(cpart);
               }
               else {
                  column = quotePath(column, false, false, true);
               }
            }
            else if(!expr && isKeyword(column)) {
               column = quoteAlias(column);
            }
            // @by billh, some dbs(informix) do not support to group by
            // an expression, in this case, we try using its index instead
            else if(!supportsOperation(SQLHelper.EXPRESSION_COLUMN_GROUPBY) &&
                    !XUtil.isQualifiedName(column))
            {
               int index = xselect.indexOf(column);
               int[] map = JDBCQueryCacheNormalizer.generateSortedColumnMap(uniformSql);

               // the select list is written in sorted order, so use the position the column
               // was written at, not the column at its original position (Bug #77557)
               if(map != null && index >= 0 && index < map.length) {
                  index = JDBCQueryCacheNormalizer.generateOriginalColumnMap(map)[index];
               }

               if(index >= 0) {
                  column = Integer.toString(index + 1);
               }
            }
            // @by yuz, some dbs(derby) do not support to group by
            // an expression, in this case, we try using its alias
            else if(!supportsOperation(EXPRESSION_GROUPBY)) {
               int index = xselect.indexOf(column);

               if(index >= 0) {
                  String alias = xselect.getAlias(index);
                  column = alias == null ? column : alias;
               }
            }
            else if(!XUtil.isQualifiedName(column)) {
               column = quoteExpressionCol(column);
            }

            if(qname != null) {
               column = quoteIdentifier(qname, column, qseg);
            }

            // if table changed to a subquery, replace table by alias
            if(isTableSubquery()) {
               column = replaceTableByAlias(true, column);
            }
            else {
               column = replaceTableByAlias(false, column);
            }

            // the names written unquoted, in the case the database reads them (Bug #77643)
            sfield = foldNames(column, uniformSql.getGroupByWrittenUnquoted(i));
         }

         group.append(sfield.toString());

         if(i != groupField.length - 1) {
            group.append(COMMA_GAP);
         }
      }

      if(groupField != null && groupField.length > 0) {
         return group.toString();
      }

      return "";
   }

   /**
    * Generate having clause of SQL statement.
    * @return having clause.
    */
   public String generateHavingClause() {
      XFilterNode node = fixHaving(uniformSql.getHaving());

      try {
         this.having = true;
         String condition = generateConditions(node);

         if(condition != null && !condition.trim().equals("")) {
            StringBuilder havingClause = new StringBuilder();
            havingClause.append("having ");
            havingClause.append(condition);
            return formatSubquery(havingClause);
         }
         else {
            return "";
         }
      }
      finally {
         this.having = false;
      }
   }

   /**
    * Fix having condition.
    */
   protected XFilterNode fixHaving(XFilterNode root) {
      if(root == null) {
         return null;
      }

      // @by billh, when define having in grouping pane, expressions and
      // fields are all taken as XFields and shown in the drop down list,
      // which seems natural and easy to use. But one problem left: an
      // expression will be taken as a field mistakenly. Here we fix the
      // problem to quote column properly when generating having clause
      root = (XFilterNode) root.clone();
      fixHaving0(root);
      return root;
   }

   /**
    * Fix having condition internally.
    */
   protected void fixHaving0(XFilterNode root) {
      if(root instanceof XSet) {
         for(int i = 0; i < root.getChildCount(); i++) {
            XFilterNode node = (XFilterNode) root.getChild(i);
            fixHaving0(node);
         }
      }
      else if(root instanceof XUnaryCondition) {
         XExpression exp1 = ((XUnaryCondition) root).getExpression1();
         fixHavingExpression(exp1);
      }
      else if(root instanceof XBinaryCondition) {
         XExpression exp1 = ((XBinaryCondition) root).getExpression1();
         fixHavingExpression(exp1);
         XExpression exp2 = ((XBinaryCondition) root).getExpression2();
         fixHavingExpression(exp2);
      }
      else if(root instanceof XTrinaryCondition) {
         XExpression exp1 = ((XTrinaryCondition) root).getExpression1();
         fixHavingExpression(exp1);
         XExpression exp2 = ((XTrinaryCondition) root).getExpression2();
         fixHavingExpression(exp2);
         XExpression exp3 = ((XTrinaryCondition) root).getExpression3();
         fixHavingExpression(exp3);
      }
   }

   /**
    * Fix a having expression.
    */
   protected void fixHavingExpression(XExpression exp) {
      if(!XExpression.FIELD.equals(exp.getType())) {
         return;
      }

      Object val = exp.getValue();

      if(!(val instanceof String)) {
         return;
      }

      String fld = (String) val;

      if(uniformSql.isTableColumn(fld)) {
         return;
      }

      if(XUtil.isQualifiedName(fld)) {
         return;
      }

      exp.setValue(fld, XExpression.EXPRESSION);
   }

   /**
    * Generate condition string of where and having clause.
    * @param filterNode filter node.
    * @return condition string.
    */
   public String generateConditions(XFilterNode filterNode) {
      StringBuilder condition = new StringBuilder();

      if(filterNode instanceof XSet) {
         condition.append(buildConditionString((XSet) filterNode));
      }
      else if(filterNode instanceof XExpressionCondition) {
         condition.append(
            buildConditionString((XExpressionCondition) filterNode));
      }
      else if(filterNode instanceof XUnaryCondition) {
         condition.append(buildConditionString((XUnaryCondition) filterNode));
      }
      else if(filterNode instanceof XBinaryCondition) {
         condition.append(buildConditionString((XBinaryCondition) filterNode));
      }
      else if(filterNode instanceof XTrinaryCondition) {
         condition.append(buildConditionString((XTrinaryCondition) filterNode));
      }

      return condition.toString();
   }

   /**
    * Generate condition string for XExpressionCondition.
    * @param condition XExpressionCondition.
    * @return condition string.
    */
   protected String buildConditionString(XExpressionCondition condition) {
      XExpression exp = condition.getExpression();
      return buildExpressionString(exp);
   }

   /**
    * Generate condition string for XUnaryCondition.
    * @param condition XUnaryCondition.
    * @return condition string.
    */
   protected String buildConditionString(XUnaryCondition condition) {
      String str;
      String op = condition.getOp();
      XExpression expression1 = condition.getExpression1();

      if(XUnaryCondition.isPrefixOp(op)) {
         if(XUtil.isAggregateFunction(op)) {
            str = op +
               "(" +
               buildExpressionString(expression1) +
               ")";
         }
         else {
            str = op +
               " " +
               buildExpressionString(expression1);
         }
      }
      else {
         if(op != null && op.equalsIgnoreCase("is null") && condition.isIsNot())
         {
            str = buildExpressionString(expression1) + " is not null";
         }
         else if(op != null && op.length() > 0) {
            str = buildExpressionString(expression1) + " " + op;
         }
         else {
            str = buildExpressionString(expression1);
         }
      }

      if(condition.isIsNot() && !op.equalsIgnoreCase("is null")) {
         str = "not (" + str + ")";
      }

      return str;
   }

   /**
    * Generate condition string for XBinaryCondition.
    * @param condition XBinaryCondition.
    * @return condition string.
    */
   protected String buildConditionString(XBinaryCondition condition) {
      // @by henryh, allow database helper decide outer join syntax
      return isAnsiJoin0() ? buildConditionStringAnsi(condition)
         : buildConditionString0(condition);
   }

   /**
    * Generate condition string for XBinaryCondition.
    * @return condition string.
    */
   private String buildConditionString0(XBinaryCondition condition) {
      String op = condition.getOp();
      StringBuilder buffer = new StringBuilder();
      XExpression expression1 = condition.getExpression1();
      XExpression expression2 = condition.getExpression2();
      boolean fld1 = condition instanceof XJoin || !having &&
         expression1.getType().equals(XExpression.FIELD);
      boolean fld2 = condition instanceof XJoin || !having &&
         expression2.getType().equals(XExpression.FIELD);

      if(XBinaryCondition.isPrefixOp(op)) {
         if(condition.isIsNot()) {
            buffer.append("not (");
         }

         buffer.append(op);
         buffer.append(" ");
         buffer.append(buildExpressionString(expression1, fld1));
         buffer.append(" ");
         buffer.append(buildExpressionString(expression2, fld2));

         if(condition.isIsNot()) {
            buffer.append(")");
         }
      }
      else if(XBinaryCondition.isPostfixOp(op)) {
         if(condition.isIsNot()) {
            buffer.append("not (");
         }

         buffer.append(buildExpressionString(expression1, fld1));
         buffer.append(" ");
         buffer.append(buildExpressionString(expression2, fld2));
         buffer.append(" ");
         buffer.append(op);

         if(condition.isIsNot()) {
            buffer.append(")");
         }
      }
      else {
         if(condition.isIsNot()) {
            buffer.append("not (");
         }

         String str1 = buildExpressionString(expression1, fld1);
         String str2 = buildExpressionString(expression2, fld2);

         if(having && expression2.getValue() instanceof UniformSQL &&
            ((UniformSQL)expression2.getValue()).getSelection().getColumnCount() == 1)
         {
            UniformSQL subSql = (UniformSQL) expression2.getValue();
            XSelection sec = subSql.getSelection();
            String alias = sec.getAlias(0);

            // a generated subquery names its column by the alias generateSelectClause just
            // emitted for it when str2 was built (an ALIAS_n for a name the database can't
            // take), a kept sql text by the stored alias (Bug #77711)
            if(alias != null && !subSql.hasSQLString() && sec instanceof JDBCSelection) {
               alias = ((JDBCSelection) sec).getValidAlias(0, this);
            }

            if(alias != null) {
               str2 = "(select " + XUtil.quoteAlias(alias, this) + " from" +
                  str2 + "inner" + System.currentTimeMillis() + ")";
            }
         }

         // if a condition is outer join, it will be handled in the from clause
         // and the expression is returned as an empty string
         if(str1.equals("")) {
            buffer.append(str2);
         }
         else if(str2.equals("")) {
            buffer.append(str1);
         }
         else {
            // @by larryl, if a variable is used with IN and no parenthesis
            // is added around the variable, add it automatically so the sql
            // can work correctly
            if(op.equalsIgnoreCase("in") && !(str2.startsWith("(") && str2.endsWith(")"))) {
               str2 = "(" + str2 + ")";
            }

            // in with an empty list causes a sql error. (44541)
            if(op.equalsIgnoreCase("in") && "()".equals(str2.replaceAll("\\s", ""))) {
               // replace with a false condition
               buffer.append(str1);
               buffer.append(" <> ");
               buffer.append(str1);
            }
            else if(Tool.equals(str2, ("'" + XConstants.CONDITION_NULL_VALUE + "'"))) {
               buffer.append(str1);
               // <> and != carry a negation, the not ( ) wrapper comes from isNot
               String trimmedOp = op == null ? "" : op.trim();
               buffer.append("<>".equals(trimmedOp) || "!=".equals(trimmedOp) ?
                  " IS NOT NULL" : " IS NULL");
            }
            else if(Tool.equals(str2, ("'" + XConstants.CONDITION_EMPTY_STRING + "'"))) {
               buffer.append(str1);
               buffer.append(" ");
               buffer.append(op);
               buffer.append(" ''");
            }
            else if(Tool.equals(str2, ("'" + XConstants.CONDITION_NULL_STRING + "'"))) {
               buffer.append(str1);
               buffer.append(" ");
               buffer.append(op);
               buffer.append(" 'null'");
            }
            else {
               buffer.append(str1);
               buffer.append(" ");
               buffer.append(op);
               buffer.append(" ");
               buffer.append(str2);
            }
         }

         if(condition.isIsNot()) {
            buffer.append(")");
         }
      }

      return buffer.toString();
   }

   /**
    * Generate condition string for XBinaryCondition.
    * @return condition string.
    */
   private String buildConditionStringAnsi(XBinaryCondition condition) {
      if(condition instanceof XJoin) {
         XJoin join = (XJoin) condition;

         // an inner join the parser found in the where clause stays a where
         // condition. Moving it into the from clause can make it an ON
         // condition of an outer join, which keeps rows the where removes
         if(join.isWhereClauseJoin() && !join.isOuterJoin() && isTextJoinOrder()) {
            return buildConditionString0(condition);
         }

         if(isAnsiFromJoin(join)) {
            if(vJoin == null) {
               vJoin = new ArrayList<>();
            }

            vJoin.add(join);
            return "";
         }
      }

      return buildConditionString0(condition);
   }

   /**
    * Check if a join found in the condition tree can be moved into the ANSI FROM clause.
    * A join that is not moved is rendered in place as an ordinary predicate. An outer join
    * is always moved, since its operator can't be written in a WHERE clause. Any other join
    * is moved only if it's in the WHERE clause (not HAVING), it's on a non-negated all-AND
    * path from the root (or in a join group built by UniformSQL.addJoin), its operator has
    * an ANSI join (a != only from an ON clause in text join order), and both of its tables
    * are in the FROM clause of this query level (so a correlation to an outer query stays in
    * WHERE).
    */
   private boolean isAnsiFromJoin(XJoin join) {
      if(join.isOuterJoin()) {
         return true;
      }

      if(having || !ansiJoinPosition || getAnsiJoin(join.getOp(), false) == null) {
         return false;
      }

      // a != is written in place, as before != had an ANSI join, except for a != the parser
      // found in an inner join ON of a query in text join order, which stays in that ON so
      // it's kept on the null-supplying side of an outer join. A != in WHERE, from a query
      // saved before the clause was recorded, or without text join order (e.g. no outer
      // join) can't move a predicate out of an outer join, and moving it into the
      // from clause there can join a table twice
      if("!=".equals(join.getOp()) && !(join.isOnClauseJoin() && isTextJoinOrder())) {
         return false;
      }

      // the same table resolution as getJoinedTable, which writes the join
      return uniformSql.getJoinTableIndex(join.getTable1(uniformSql)) >= 0 &&
         uniformSql.getJoinTableIndex(join.getTable2(uniformSql)) >= 0;
   }

   /**
    * Check if the children of a condition set are in the same join position as the set
    * itself, i.e. if a join in the set is a join of the whole query when the set is.
    * @param set the condition set.
    * @param parent the parent set of the condition set, or null if it's the root.
    */
   private static boolean isAnsiJoinTransparent(XSet set, XSet parent) {
      if(set.isIsNot()) {
         return false;
      }

      // the or sets of a join group built by UniformSQL.addJoin hold alternative joins of
      // the same pair of tables, which makeJoinClause() writes into the join ON clause
      return XSet.AND.equalsIgnoreCase(set.getRelation()) || set.getChildCount() <= 1 ||
         set.isGroup() || (parent != null && parent.isGroup());
   }

   /**
    * Generate condition string for XTrinaryCondition.
    * @param condition XTrinaryCondition.
    * @return condition string.
    */
   protected String buildConditionString(XTrinaryCondition condition) {
      String op = condition.getOp();
      XExpression expression1 = condition.getExpression1();
      XExpression expression2 = condition.getExpression2();
      XExpression expression3 = condition.getExpression3();
      StringBuilder buffer = new StringBuilder();

      if(condition.isIsNot()) {
         buffer.append("not (");
      }

      buffer.append(buildExpressionString(expression1));
      buffer.append(" ");
      buffer.append(op);
      buffer.append(" ");
      buffer.append(buildExpressionString(expression2));
      buffer.append(" and ");
      buffer.append(buildExpressionString(expression3));

      if(condition.isIsNot()) {
         buffer.append(")");
      }

      return buffer.toString();
   }

   /**
    * Generate condition string for XSet.
    * @param condition condition set.
    * @return condition string.
    */
   protected String buildConditionString(XSet condition) {
      StringBuilder buffer = new StringBuilder();
      String relStr = GAP + condition.getRelation() + " ";
      int childCount;
      XNode node;
      childCount = condition.getChildCount();
      // the children of this set are in a join position
      final boolean joinPosition = ansiJoinPosition;
      // IS binds tighter than NOT, so the operand of x IS [NOT] TRUE/FALSE/UNKNOWN
      // must be grouped or a negated operand negates the whole test
      final boolean truthTest = isTruthTest(condition);

      for(int i = 0; i < childCount; i++) {
         String tmpStr = "";
         boolean wrapped = false;
         node = condition.getChild(i);
         ansiJoinPosition = joinPosition;

         if(node instanceof XFilterNode) {
            if(node instanceof XSet) {
               String relation = condition.getRelation();
               ansiJoinPosition = joinPosition &&
                  isAnsiJoinTransparent((XSet) node, condition);
               String childStr;

               try {
                  childStr = buildConditionString((XSet) node);
               }
               finally {
                  ansiJoinPosition = joinPosition;
               }

               if(!relation.equals(((XSet) node).getRelation()) || ((XSet) node).isGroup()) {
                  if(!childStr.equals("")) {
                     tmpStr = "(" + childStr + ")";
                     wrapped = true;
                  }
               }
               else {
                  tmpStr = childStr;
               }
            }
            else if(node instanceof XExpressionCondition) {
               tmpStr = "(" + buildConditionString((XExpressionCondition) node) + ")";
               wrapped = true;
            }
            else if(node instanceof XUnaryCondition) {
               tmpStr = buildConditionString((XUnaryCondition) node);
            }
            else if(node instanceof XBinaryCondition) {
               tmpStr = buildConditionString((XBinaryCondition) node);
            }
            else if(node instanceof XTrinaryCondition) {
               tmpStr = buildConditionString((XTrinaryCondition) node);
            }
         }
         else {
            tmpStr = node.toString();
         }

         // an ansi outer join operand prints as "" and must stay empty
         if(truthTest && i == 0 && !wrapped && !tmpStr.isEmpty()) {
            tmpStr = "(" + tmpStr + ")";
         }

         buffer.append(tmpStr);

         // @by peterx, if ansiJoin is true and node is outer join,
         // the length of tmpStr is zero
         if(i != childCount - 1 && tmpStr.length() > 0) {
            buffer.append(relStr);
         }
      }

      String str = buffer.toString();

      // strip orphan op, possible if ansi join and last cond is outer join
      if(str.endsWith(relStr)) {
         str = str.substring(0, str.length() - relStr.length());
      }

      if(condition.isIsNot()) {
         str = "not (" + str + ")";
      }

      return str;
   }

   /**
    * Check if the set is a truth test, x IS [NOT] TRUE/FALSE/UNKNOWN, as built by the
    * parser's boolean_test rule.
    */
   static boolean isTruthTest(XSet condition) {
      String relation = condition.getRelation();

      if(relation == null || condition.getChildCount() != 2) {
         return false;
      }

      relation = relation.trim();

      // most sets are and/or, skip the regex for them
      if(!relation.regionMatches(true, 0, "is", 0, 2)) {
         return false;
      }

      relation = relation.replaceAll("\\s+", " ");

      if(!relation.equalsIgnoreCase("is") && !relation.equalsIgnoreCase("is not")) {
         return false;
      }

      if(!(condition.getChild(1) instanceof XUnaryCondition truth)) {
         return false;
      }

      String op = truth.getOp();
      XExpression exp = truth.getExpression1();

      if((op != null && !op.trim().isEmpty()) || exp == null || exp.getValue() == null) {
         return false;
      }

      String value = exp.getValue().toString().trim();

      return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false") ||
         value.equalsIgnoreCase("unknown");
   }

   /**
    * Generate condition string for XExpression.
    * @param exp expression.
    * @return expression string.
    */
   protected final String buildExpressionString(XExpression exp) {
      return buildExpressionString(exp, false);
   }

   /**
    * Build field expression.
    */
   public String buildFieldExpression(String str, boolean fld) {
      XSelection xselect = uniformSql.getSelection();
      int index = xselect.indexOfColumn(str);
      boolean validated = false;
      boolean expr = xselect.isExpression(str);

      if(index >= 0) {
         String column = xselect.getColumn(index);
         String table = uniformSql.getTable(column);
         String c = uniformSql.getColumnFromPath(column);
         String subalias = getValidSubAlias(table, c);

         if(subalias != null) {
            str = quoteTableAlias(table) + "." + quoteColumnAlias(subalias);
            validated = true;
         }
      }
      else {
          String column = str;
          String table = uniformSql.getTable(column);
          String c = uniformSql.getColumnFromPath(column);
          String subalias = getValidSubAlias(table, c);

          if(subalias != null) {
            str = quoteTableAlias(table) + "." + quoteColumnAlias(subalias);
            validated = true;
         }
      }

      if(!validated) {
        String bcol = xselect.getBaseColumn(str);

         if(bcol != null) {
            str = getValidExpression(str, bcol);
         }
      }

      if(fld || uniformSql.isTableColumn(str) || XUtil.isQualifiedName(str)) {
         if(!(vpmCondition && str.contains(getQuote()))) {
            str = quotePath(str, true, true, true);
         }
      }
      else if(!expr && isKeyword(str)) {
         str = quoteAlias(str);
      }
      else if(!XUtil.isQualifiedName(str)) {
         str = quoteExpressionCol(str);
      }

      if(isTableSubquery()) {
         str = replaceTableByAlias(true, str);
      }
      else {
         str = replaceTableByAlias(false, str);
      }

      return str;
   }

   /**
    * Generate condition string for XExpression.
    * @param exp expression.
    * @param fld the expression must be a field.
    * @return expression string.
    */
   protected final String buildExpressionString(XExpression exp, boolean fld) {
      String str;
      String type = exp.getType();
      Object value = exp.getValue();

      if(fld || type.equals(XExpression.FIELD)) {
         str = value.toString();
         str = buildFieldExpression(str, fld);

         if(exp.isQuotedField()) {
            str = quoteIdentifier(value.toString(), str, exp.getQuotedColumn());
         }

         // the names written unquoted, in the case the database reads them (Bug #77643)
         str = foldNames(str, exp.getWrittenUnquoted());
      }
      else if(type.equals(XExpression.SUBQUERY)) {
         UniformSQL sql = (UniformSQL) value;

         if(isTableSubquery()) {
            sql.setParent(uniformSql);
         }

         // a correlated column of a quoted outer table is quoted as the table (#77569)
         sql.setOuterSQL(uniformSql);
         sql.clearCachedString();

         if(sql.getDataSource() == null) {
            sql.setDataSource(uniformSql.getDataSource());
         }

         if(sql.getOrderByItems().length > 0) {
            sql.clearSQLString();
            sql.removeAllOrderByFields();
         }

         // subquery needs to be quoted consistently
         sql.setHint(UniformSQL.HINT_INPUT_MAXROWS, inpmaxrows + "");

         try {
            str = BRACKET + (isFormatSQL ? sql.toString().trim() : sql.toString())
               + ")";
         }
         finally {
            sql.setOuterSQL(null);
         }

         // if table changed to a subquery, replace reference to table to alias. The subquery
         // quoted its own qualifiers, with this query as its outer query
         if(isTableSubquery()) {
            str = replaceTableByAlias(true, str, false, false);
         }
         else {
            str = replaceTableByAlias(false, str, false, false);
         }
      }
      else if(type.equals(XExpression.EXPRESSION) && value != null) {
         str = exp.toString(value);
         str = quoteExpressionColumn(str);
         String ignoreNotChangeAliasExp = null;
         boolean aliased = false;
         String originalStr = str;

         // if table changed to a subquery, replace reference to table to alias
         if(isTableSubquery()) {
            ignoreNotChangeAliasExp = replaceTableByAlias(true, str, true);
            str = replaceTableByAlias(true, str);
         }
         else {
            str = replaceTableByAlias(false, str);
         }

         // for having, some dbs like mysql does not properly support
         // expression. Here we use alias when alias exists
         if(having) {
            JDBCSelection selection = (JDBCSelection) uniformSql.getSelection();
            int index = selection.indexOf(str);

            if(index < 0 && !Tool.isEmptyString(ignoreNotChangeAliasExp)) {
               index = selection.indexOf(ignoreNotChangeAliasExp);
            }

            if(index < 0) {
               index = selection.indexOf(originalStr);
            }

            if(index >= 0) {
               String alias = selection.getValidAlias(index, this);

               if(alias != null && alias.length() > 0 &&
                  !alias.equals(str) && requiresAliasInHaving())
               {
                  str = quoteAlias(alias);
                  aliased = true;
               }
            }
         }

         str = transformDate(str);

         // the names written unquoted, in the case the database reads them (Bug #77643)
         if(!aliased) {
            str = foldNames(str, exp.getWrittenUnquoted());
         }
      }
      else if(type.equals(XExpression.VALUE) && value != null) {
         str = exp.toString(value);
         str = transformDate(str);
      }
      else {
         str = "";
      }

      return str.trim();
   }

   /**
    * Check if requires alias in having for an expression.
    */
   protected boolean requiresAliasInHaving() {
      return false;
   }

   /**
    * Transform date formats. It may be overridden by different DB helpers.
    */
   protected String transformDate(String str) {
      str = str.trim();

      if(!str.startsWith("{d")) {
         return str;
      }

      String dateStr = str.substring(2, str.length() - 1).trim();

      if(str.indexOf('-') > -1 && str.indexOf(':') > -1) {
         dateStr = "{ts " + dateStr + "}";
      }
      else if(str.indexOf(':') > -1) {
         dateStr = "{t " + dateStr + "}";
      }
      else if(str.indexOf('-') > -1) {
         dateStr = "{d " + dateStr + "}";
      }

      return dateStr;
   }

   /**
    * Generate field name.
    * @param name field name.
    * @return field name.
    */
   protected String generateFieldName(String name) {
      return name;
   }

   /**
    * Check if is case sensitive when quote table or column.
    */
   @Override
   public boolean isCaseSensitive() {
      return caseSensitive;
   }

   /**
    * Get the case the database folds an unquoted identifier to, if the helper is of a database
    * that always folds to one case. The stored name of a column on such a database is its
    * exact name, see JDBCUtil.getFullPathOf (Bug #77643).
    */
   public IdentifierCase getIdentifierCase() {
      return IdentifierCase.UNKNOWN;
   }

   /**
    * Check if the database either ignores the case of a name or folds an unquoted name to
    * upper case (e.g. oracle, h2, derby, mysql, sql server). An unquoted order by name then
    * resolves to a select alias written unquoted in any case, or written quoted in upper case
    * (Bug #77644). A database with case-sensitive names (e.g. clickhouse, sybase ase) or one
    * that folds to lower case doesn't.
    */
   public boolean isAliasCaseInsensitive() {
      return !isCaseSensitive();
   }

   /**
    * The case a database folds an unquoted identifier to.
    */
   public enum IdentifierCase {
      UPPER, LOWER, UNKNOWN;

      /**
       * Fold an identifier written unquoted. Only a plain ascii name is folded, postgresql
       * folds ascii letters only, any other name is kept as written.
       */
      public String fold(String name) {
         if(this == UNKNOWN || name == null || !PLAIN_IDENTIFIER.matcher(name).matches()) {
            return name;
         }

         return this == UPPER ? name.toUpperCase(Locale.ROOT) : name.toLowerCase(Locale.ROOT);
      }
   }

   // a name the database folds when it's written unquoted
   private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

   /**
    * Fold the names written unquoted in the sql generated for a stored text (a select column,
    * a condition expression, a group by or order by field, a table name, or the sql of a
    * derived table saved as text). A case-sensitive helper stores such a name in quotes in the
    * case it was written, the database reads the name written unquoted in its folded case
    * (Bug #77643). The name is folded and kept in quotes. A name that references an alias is
    * generated like the alias: folded only if the alias is generated unquoted. Nothing is
    * folded if the case isn't known, or if db.foldUnquotedIdentifiers is false.
    * @param generated the sql generated for the text.
    * @param names the names of the text written unquoted, or <tt>null</tt>.
    * @return the sql with the names folded.
    */
   public String foldWrittenUnquoted(String generated, WrittenUnquoted names) {
      if(names == null || names.isEmpty() || generated == null || WrittenUnquoted.isUnfolded()) {
         return generated;
      }

      String quote = getQuote();

      // the sql generated to be saved keeps the names as stored, marked to be saved
      if(WrittenUnquoted.isMarking()) {
         return names.apply(generated, quote, name -> WrittenUnquoted.mark(name, false),
                            name -> quote + WrittenUnquoted.mark(name, true) + quote);
      }

      IdentifierCase fold = getIdentifierCase();

      if(fold == IdentifierCase.UNKNOWN || !isFoldUnquotedIdentifiers()) {
         return generated;
      }

      // a reference to an alias is generated as the alias is
      return names.apply(generated, quote, fold::fold, this::quoteColumnAlias);
   }

   /**
    * Check if the names written unquoted are generated in the case the database folds them
    * to, see foldWrittenUnquoted. The property db.foldUnquotedIdentifiers=false generates them
    * as stored, as before Bug #77643.
    */
   public static boolean isFoldUnquotedIdentifiers() {
      return !"false".equals(SreeEnv.getProperty("db.foldUnquotedIdentifiers"));
   }

   /**
    * Fold the names written unquoted in the sql generated for a stored text, see
    * foldWrittenUnquoted, and the qualifiers that name a table without an alias whose name
    * was written unquoted, as the table is generated in the from clause. Such a qualifier
    * may have been added to the text after the parse, e.g. by the metadata step.
    */
   private String foldNames(String generated, WrittenUnquoted names) {
      return foldTableQualifiers(foldWrittenUnquoted(generated, names), names);
   }

   /**
    * Generate the qualifiers that name a table without an alias whose name was written
    * unquoted (e.g. "t".id for t written unquoted) as the name of the table is generated in
    * the from clause (Bug #77643). Only a text built after the parse, without a record (e.g. by
    * the query editor, a worksheet or a vpm condition), is generated so: a parsed text has a
    * record, also empty, which says how each of its names was written, and the metadata step
    * records the qualifiers it writes. A qualifier in a nested query is of that query.
    * @param names the names written unquoted of the text, or <tt>null</tt> if it has none.
    */
   private String foldTableQualifiers(String generated, WrittenUnquoted names) {
      Map<String, String> qualifiers = names != null ? Collections.emptyMap() : getTableQualifiers();
      String quote = getQuote();

      if(generated == null || qualifiers.isEmpty() || quote.isEmpty()) {
         return generated;
      }

      List<int[]> found = WrittenUnquoted.findNames(generated, quote);
      boolean[] nested = WrittenUnquoted.getNestedNames(generated, quote);
      StringBuilder sb = new StringBuilder();
      int last = 0;

      for(int n = 0; n < found.size(); n++) {
         int start = found.get(n)[0] - quote.length();

         if(nested[n] || start < last || start > 0 && generated.charAt(start - 1) == '.') {
            continue;
         }

         for(Map.Entry<String, String> entry : qualifiers.entrySet()) {
            String name = entry.getKey();

            if(generated.startsWith(name, start) &&
               generated.startsWith(".", start + name.length()))
            {
               sb.append(generated, last, start).append(entry.getValue());
               last = start + name.length();
               break;
            }
         }
      }

      return last == 0 ? generated : sb.append(generated.substring(last)).toString();
   }

   /**
    * Get the stored names of the tables without an alias whose name was written unquoted ->
    * the name generated in the from clause. A name of two tables (MyTab and "MyTab") isn't
    * one table.
    */
   private Map<String, String> getTableQualifiers() {
      Map<String, String> qualifiers = new HashMap<>();
      Set<String> names = new HashSet<>();
      Set<String> shared = new HashSet<>();

      for(SelectTable table : uniformSql == null ? new SelectTable[0] :
         uniformSql.getSelectTable())
      {
         Object name = table.getName();

         if(!(name instanceof String) || !isUnaliased(table)) {
            continue;
         }

         if(!names.add((String) name)) {
            shared.add((String) name);
         }

         String folded = foldWrittenUnquoted((String) name, table.getWrittenUnquoted());

         if(!isFoldedTwin(table) && !folded.equals(name)) {
            qualifiers.put((String) name, folded);
         }
      }

      qualifiers.keySet().removeAll(shared);
      return qualifiers;
   }

   /**
    * The alias has special character should be quoted as "alias".
    */
   protected String quoteColumnAlias(String alias) {
      return quoteColumnAlias(alias, false, false);
   }

   /**
    * The alias has special character should be quoted as "alias".
    * @param same true if the alias is the same as the column string.
    * @param part true if the alias is the same as the column part.
    */
   protected String quoteColumnAlias(String alias, boolean same, boolean part){
      // @by larryl, if passing ', the alias is always quoted, which is not
      // correct and can cause problems for oracle
      return XUtil.quoteAlias(alias, this);
   }

   /**
    * The alias is the column part of table.calias.
    */
   protected String quoteColumnPartAlias(String alias) {
      return quoteColumnAlias(alias);
   }

   /**
    * Quote the column of a physical table written unquoted as the only argument of a
    * function (sum(t.col)), only if needed.
    */
   protected String quoteAggregateColumn(String column) {
      return quoteColumnPartAlias(column);
   }

   /**
    * Quote table name.
    * @param name the specified table name to be quoted.
    */
   protected String getQuotedTableName(String name) {
      return getQuotedTableName(name, false);
   }

   /**
    * Quote table name.
    * @param name the specified table name to be quoted.
    */
   protected String getQuotedTableName(String name, boolean selectClause) {
      String quote = getQuote();
      String qname = quoteQuotedSegments(name);

      if(qname != null) {
         return qname;
      }

      if(name.contains(quote)) {
         return name;
      }

      return quoteTableName(name, selectClause);
   }

   /**
    * Quote table name.
    * @param name the specified table name to be quoted.
    */
   public String quoteTableName(String name) {
      return quoteTableName(name, false);
   }

   /**
    * Quote table name.
    * @param name the specified table name to be quoted.
    * @param selectClause <tt>true</tt> if is in select clause, <tt>false</tt>
    * in from clause.
    */
   public String quoteTableName(String name, boolean selectClause) {
      int index = uniformSql.getTableIndex(name);

      // check if is a table alias
      if(index >= 0) {
         SelectTable stable = uniformSql.getSelectTable(index);
         String talias = stable.getAlias();
         Object tname = stable.getName();

         if(tname instanceof String) {
            tname = trimQuote((String) tname);
         }

         // if name is not equal to table but equal to alias, it must
         // be an alias. We should quote it as table alias instead
         if(!trimQuote(name).equals(tname) && name.equals(talias)) {
            if(uniformSql.isSqlQuery()) {
               return talias;
            }

            return quoteTableAlias(name);
         }
      }

      String alias = uniformSql.getTableAlias(name);
      alias = alias == null ? name : alias;
      index = uniformSql.getTableIndex(alias);
      SelectTable stable = (index >= 0) ?
         uniformSql.getSelectTable(index) : null;
      String catalog = stable == null ? null : stable.getCatalog();
      String schema = stable == null ? null : stable.getSchema();
      StringBuilder sb = new StringBuilder();
      boolean tpart = false;  // indicates name only contains the table name
      boolean cexcluded = false; // catalog excluded in table

      // Add catalog to return string, and remove from name
      if(catalog != null) {
         if(name.startsWith(catalog + ".")) {
            processCatalog(sb, catalog, selectClause);
            name = name.substring(catalog.length() + 1);
         }
         else {
           cexcluded = true;
         }
      }

      // Add schema to return string, and remove from name
      // If true, then what is left in name must only be the table name
      if(schema != null) {
         if(name.startsWith(schema + ".")) {
            processSchema(sb, schema, selectClause);
            name = name.substring(schema.length() + 1);
            tpart = true;
         }
         else {
            // neighter catalog nor schema includeded in table, tpart is true
            // if no catalog, it is a table part
            // fix bug1368783884495
            tpart = cexcluded || catalog == null;
         }
      }

      JDBCDataSource src = uniformSql.getDataSource();
      String productName = SQLHelper.getProductName(src);

      if(!tpart) {
         // @by davidd 2008-01-08
         // if table name does not contain catalog or schema,
         // we should quote it as alias to support dot properly
         // DEFAULT may have dropped the schema (when username == schema)
         // in which case we should assume name contains only the table
         if(src != null &&
            src.getTableNameOption() == JDBCDataSource.TABLE_OPTION ||
            //Since SQLite does not support catalog and Schema, table name is equal to tpart
            "sqlite".equals(productName) ||
            "mysql".equals(productName) && catalog != null)
         {
            tpart = true;
         }
      }

      // if name is already quoted, don't quote again
      // if we know name only contains table name then
      // we should quote the entire name if needed, otherwise
      // split the name on "." and quote all the pieces.
      if(!name.startsWith(getQuote())) {
         Function<String, Boolean> checkSpecial = null;

         // should quote the table name for oracle when the table name has lowercase.
         if("oracle".equals(productName)) {
            checkSpecial = tname -> {
               if(tname == null) {
                  return false;
               }

               return !Objects.equals(tname, tname.toUpperCase());
            };
         }

         name = (tpart && name.indexOf('.') < 0) ? XUtil.quoteNameSegment(name, true, this, checkSpecial)
            : XUtil.quoteName(name, this);
      }

      processTableSchema(sb, schema, selectClause);
      sb.append(name);
      return sb.toString();
   }

   /**
    * Quote the segments of a table name that were written as quoted identifiers ("a", `a`
    * or [a]) in the parsed sql. The parser stores them without their quotes, so a quoted
    * lowercase name would otherwise be read as another table on a database that folds
    * unquoted names to uppercase (#77569).
    * @param name a table name, or the table name used as a qualifier.
    * @return the name with its quoted segments quoted, or <tt>null</tt> if the name is not
    * the name of exactly one table with quoted segments.
    */
   private String quoteQuotedSegments(String name) {
      if(name == null || uniformSql == null) {
         return null;
      }

      SelectTable found = null;
      boolean same = true;

      for(int i = 0; i < uniformSql.getTableCount(); i++) {
         SelectTable table = uniformSql.getSelectTable(i);

         if(!name.equals(table.getName())) {
            continue;
         }

         // a table without an alias is named by its name
         if(name.equals(table.getAlias())) {
            found = table;
            same = true;
            break;
         }

         // several aliases of one table (a self join) written the same way
         same = same && (found == null ||
            Arrays.equals(found.getQuotedSegments(), table.getQuotedSegments()));
         found = table;
      }

      return found != null && same ? quoteQuotedSegments(found, name) : null;
   }

   /**
    * Quote the name of a table, keeping the quotes of the segments that were written as
    * quoted identifiers ("a", `a` or [a]) in the parsed sql (#77569).
    * @param table the table.
    * @return the quoted name, or <tt>null</tt> if no segment of the name was written quoted,
    * which leaves the name to the usual table name quoting.
    */
   public String quoteQuotedSegments(SelectTable table) {
      return table != null && table.getName() instanceof String ?
         quoteQuotedSegments(table, (String) table.getName()) : null;
   }

   /**
    * Quote the segments of a table name that were written as quoted identifiers in the
    * parsed sql. The other segments are quoted as by quoteName.
    * @param table the table.
    * @param name the table name.
    * @return the name with its quoted segments quoted, or <tt>null</tt> if no segment
    * needs quotes, which leaves the name to the usual table name quoting.
    */
   private String quoteQuotedSegments(SelectTable table, String name) {
      String quote = getQuote();

      // the sql generated while parsing is stored as text, without the quotes
      if(UniformSQL.isUnquoted() || table == null || table.getQuotedSegments() == null ||
         quote == null || quote.isEmpty() || !name.equals(table.getName()))
      {
         return null;
      }

      List<String> segs = splitTableName(name);
      StringBuilder sb = new StringBuilder();
      boolean quoted = false;

      for(int i = 0; i < segs.size(); i++) {
         String seg = segs.get(i);

         if(i > 0) {
            sb.append('.');
         }

         // a segment the parser quoted again (e.g. "My A" or any name on a case-sensitive
         // helper) is already stored quoted
         if(seg.isEmpty() || isQuotedTableSegment(seg) || seg.contains(quote)) {
            sb.append(seg);
         }
         else if(table.isQuotedSegment(i)) {
            sb.append(quote).append(seg).append(quote);
            quoted = true;
         }
         else if(XUtil.isSpecialName(seg, true, this)) {
            sb.append(quote).append(seg).append(quote);
         }
         else {
            sb.append(seg);
         }
      }

      return quoted ? sb.toString() : null;
   }

   /**
    * Quote a column qualified by the last segments of the name of a table with quoted
    * segments, e.g. a.id for "S"."a" written as "a".id (#77569).
    * @param tname the table of the column.
    * @param path the column.
    * @return the quoted column, or <tt>null</tt> if the qualifier is not the end of the table
    * name, or none of its segments was written quoted.
    */
   private String quoteQualifierSuffix(String tname, String path) {
      int index = uniformSql.getTableIndex(tname);
      SelectTable table = index >= 0 ? uniformSql.getSelectTable(index) : null;

      if(UniformSQL.isUnquoted() || table == null || table.getQuotedSegments() == null ||
         !tname.equals(table.getName()) || !tname.equals(table.getAlias()))
      {
         return null;
      }

      List<String> segs = splitTableName(tname);

      for(int start = 1; start < segs.size(); start++) {
         String qualifier = String.join(".", segs.subList(start, segs.size()));
         String column = path.startsWith(qualifier + ".") ?
            path.substring(qualifier.length() + 1) : null;

         // the qualifier is not another table, and the rest is one column segment
         if(column == null || column.isEmpty() || splitTableName(column).size() > 1 ||
            uniformSql.getTableIndex(qualifier) >= 0)
         {
            continue;
         }

         String quote = getQuote();
         StringBuilder sb = new StringBuilder();
         boolean quoted = false;

         for(int i = start; i < segs.size(); i++) {
            String seg = segs.get(i);
            boolean quoteSeg = table.isQuotedSegment(i) && !seg.isEmpty() &&
               !isQuotedTableSegment(seg) && !seg.contains(quote);
            sb.append(quoteSeg ? quote + seg + quote : seg).append('.');
            quoted = quoted || quoteSeg;
         }

         return quoted ? sb + XUtil.quoteNameSegment(column, this) : null;
      }

      return null;
   }

   /**
    * Quote a correlated column of a subquery whose qualifier names a table of an outer query
    * that was written with quoted segments, e.g. "c".id in exists (select 1 from C where
    * C.id = "c".id). The qualifier is stored without its quotes, and the subquery may have a
    * table of the same name in another case (#77569).
    * @return the quoted column, or <tt>null</tt> if the qualifier names a table of this
    * query, or no quoted outer table.
    */
   private String quoteOuterQualifier(String path) {
      UniformSQL outer = uniformSql.getOuterSQL();
      List<String> segs = splitTableName(path);

      if(outer == null || segs.size() < 2 || hasTable(uniformSql, path, segs)) {
         return null;
      }

      String column = segs.get(segs.size() - 1);
      String qualifier = path.substring(0, path.length() - column.length() - 1);

      for(UniformSQL sql = outer; sql != null; sql = sql.getOuterSQL()) {
         for(int i = 0; i < sql.getTableCount(); i++) {
            SelectTable table = sql.getSelectTable(i);

            // only a table without an alias is named by its name
            if(qualifier.equals(table.getAlias()) && qualifier.equals(table.getName())) {
               String qtable = quoteQuotedSegments(table, qualifier);
               return qtable == null ? null : qtable + "." + XUtil.quoteNameSegment(column, this);
            }
         }

         if(hasTable(sql, path, segs)) {
            return null;
         }
      }

      return null;
   }

   /**
    * Check if the qualifier of a column is the alias or the name of a table of a query.
    */
   private static boolean hasTable(UniformSQL sql, String path, List<String> segs) {
      String column = segs.get(segs.size() - 1);
      String qualifier = path.substring(0, path.length() - column.length() - 1);

      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable table = sql.getSelectTable(i);

         if(qualifier.equals(table.getAlias()) || qualifier.equals(table.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Split a table name at the dots that are not inside a quoted segment.
    */
   private static List<String> splitTableName(String name) {
      List<String> segs = new ArrayList<>();
      int start = 0;
      char close = 0;

      for(int i = 0; i < name.length(); i++) {
         char c = name.charAt(i);

         if(close != 0) {
            if(c == close) {
               close = 0;
            }
         }
         else if(c == '"' || c == '`') {
            close = c;
         }
         else if(c == '[') {
            close = ']';
         }
         else if(c == '.') {
            segs.add(name.substring(start, i));
            start = i + 1;
         }
      }

      segs.add(name.substring(start));
      return segs;
   }

   /**
    * Check if a table name segment is quoted.
    */
   private static boolean isQuotedTableSegment(String seg) {
      int last = seg.length() - 1;
      return last > 0 && (seg.charAt(0) == '"' && seg.charAt(last) == '"' ||
         seg.charAt(0) == '`' && seg.charAt(last) == '`' ||
         seg.charAt(0) == '[' && seg.charAt(last) == ']');
   }

   protected void processCatalog(StringBuilder sb, String catalog,
                                 boolean selectClause) {
      sb.append(XUtil.quoteAlias(catalog, this));
      sb.append(".");
   }

   protected void processSchema(StringBuilder sb, String schema,
                                boolean selectClause) {
      sb.append(XUtil.quoteNameSegment(schema, this));
      sb.append(".");
   }

   protected void processTableSchema(StringBuilder sb, String schema,
                                     boolean selectClause) {
   }

   /**
    * The alias has special character should be quoted as "alias".
    */
   public String quoteTableAlias(String alias) {
      String quote = getQuote();

      if(quote != null) {
         alias = alias.replaceAll(quote, "");
      }

      // @by larryl, is passing ', the alias is always quoted, which is not
      // correct and can cause problems for oracle
      return XUtil.quoteAlias(alias, this);
   }

   /**
    * Quote a qualified table name.
    */
   public String quotePath(String path) {
      return quotePath(path, false);
   }

   /**
    * Quote a path.
    * @param path the specified table/column/alias path.
    * @param physical <tt>true</tt> if a physical name, so alias is impossible.
    */
   public String quotePath(String path, boolean physical) {
      return quotePath(path, physical, false);
   }

   /**
    * Quote a path.
    * @param path the specified table/column/alias path.
    * @param physical <tt>true</tt> if a physical name, so alias is impossible.
    * @param force true to force to quote path for we know it must be a table
    * column.
    */
   public String quotePath(String path, boolean physical, boolean force) {
      return quotePath(path, physical, force, false);
   }

   /**
    * Quote a path.
    * @param path the specified table/column/alias path.
    * @param physical <tt>true</tt> if a physical name, so alias is impossible.
    * @param force true to force to quote path for we know it must be a table
    * column.
    */
   public String quotePath(String path, boolean physical, boolean force, boolean selectClause) {
      // table alias?
      if(uniformSql.getTableIndex(path) >= 0) {
         return quoteTableAlias(path);
      }
      // column alias?
      else if(!physical && uniformSql.getSelection().isAlias(path)) {
         return quoteColumnAlias(path);
      }

      // a correlated column of a quoted outer table ("c".id)
      String outerPath = quoteOuterQualifier(path);

      if(outerPath != null) {
         return outerPath;
      }

      // table column?
      if(force || uniformSql.isTableColumn(path)) {
         String tname = uniformSql.getTable(path);

         if(tname != null && tname.length() > 0) {
            String alias = uniformSql.getTableAlias(tname);
            String tpart;
            String cpart;

            if(path.startsWith(tname + ".")) {
               tpart = tname;
               cpart = path.substring(tname.length() + 1);
            }
            else if(path.startsWith(quoteTableAlias(tname) + ".")) {
               // @by jasons, bug1274807633991, table name may already be
               // quoted for a formula field, e.g. field['...']
               tpart = tname;
               cpart = path.substring(quoteTableAlias(tname).length() + 1);
            }
            else if(path.startsWith(alias + ".")) {
               tpart = alias;
               cpart = path.substring(alias.length() + 1);
            }
            else {
               // a qualifier written as the end of the table name, e.g. "a".id for "S"."a"
               String qpath = quoteQualifierSuffix(tname, path);

               if(qpath != null) {
                  return qpath;
               }

               tpart = null;
               cpart = path;
            }

            // table part is an alias?
            // @by larryl, if the maxrows is defined on the raw data, the name
            // of the table is going to be used as an alias to the subquery
            // with the row limitation.
            boolean talias = isTableSubquery();

            if(talias && tpart != null && tpart.split(getQuote()).length == 2) {
               tpart = trimQuote(tpart);
            }

            if(tpart != null && !talias) {
               int idx = uniformSql.getTableIndex(tpart);

               if(idx > -1) {
                  Object table = uniformSql.getSelectTable(idx).getName();

                  if(table instanceof String) {
                     table = trimQuote((String) table);
                  }

                  talias = !trimQuote(tpart).equals(table);
               }
            }

            // column part is an alias?
            boolean calias = false;
            Object table = uniformSql.getTableName(tname);
            table = table == null ? tname : table;

            if(table instanceof UniformSQL) {
               XSelection subselect = ((UniformSQL) table).getSelection();
               calias = subselect.getAliasColumn(cpart) != null;

               if(!calias) {
                  subselect = ((UniformSQL) table).getBackupSelection();
                  calias = subselect.getAliasColumn(cpart) != null;
               }

               if(!calias) {
                  calias = ((UniformSQL) table).isAliasColumn(cpart);
               }

               if(calias) {
                  uniformSql.setAliasColumn(cpart, true);
               }
            }

            if(tpart != null) {
               // if the alias has been shortened, use the new alias
               if(talias) {
                  String nalias = aliasmap == null ? null : aliasmap.get(tpart);

                  if(nalias != null) {
                     tpart = nalias;
                  }
               }

               tpart = talias ? quoteTableAlias(tpart) : getQuotedTableName(tpart, selectClause);
            }

            if(calias) {
               // oracle sql helper will quote any column alias when
               // calling the method quoteColumnAlias, which is not
               // allowed if is table.calias pattern
               cpart = tpart == null ? quoteColumnAlias(cpart) : quoteColumnPartAlias(cpart);
            }
            else {
               cpart = tpart == null ? XUtil.quoteName(cpart, this) :
                  XUtil.quoteNameSegment(cpart, this);
            }

            return tpart == null ? cpart : tpart + "." + cpart;
         }
      }

      int idx = path.lastIndexOf('.');

      if(idx > 0) {
         String table = path.substring(0, idx);
         String col = path.substring(idx + 1);
         Object tname = uniformSql.getTableName(table);

         if(table.equals(tname)) {
            String qtable = quoteQuotedSegments(table);
            return (qtable != null ? qtable : quoteTableName(table, selectClause)) + "." +
               XUtil.quoteAlias(col, this);
         }
         else if(uniformSql.getTableIndex(table) >= 0) {
            return XUtil.quoteAlias(table, this) + "." +
               XUtil.quoteAlias(col, this);
         }
      }

      if(uniformSql.isTableColumn(path)) {
         return XUtil.quoteName(path, this);
      }

      return path;
   }

   /**
    * Get the name to quote for a group by or order by field written as a quoted identifier
    * (e.g. "x y"), directly or through the alias of a quoted select column.
    * @param quoted <tt>true</tt> if the group by or order by field itself was written as a
    *               quoted identifier.
    * @return the unquoted name, or <tt>null</tt> if the field is not quoted.
    */
   private String getQuotedName(String field, boolean quoted) {
      JDBCSelection xselect = (JDBCSelection) uniformSql.getSelection();
      int idx = getAliasIndex(xselect, field);

      // an alias is generated as its column, which decides the quoting. By position, two
      // columns may have the same path (Bug #77573)
      if(idx >= 0) {
         return !xselect.isExpression(idx) && xselect.isQuoted(idx) ?
            xselect.getColumn(idx) : null;
      }

      return quoted ? field : null;
   }

   /**
    * Get the column segment, as written, of a group by or order by field written as a
    * qualified quoted identifier (t."MixedCase").
    * @param field the group by or order by field.
    * @param qname the name to quote, from getQuotedName.
    * @param segment the segment recorded for the field itself.
    * @return the segment, or <tt>null</tt> if the name is not a qualified quoted identifier.
    */
   private String getQuotedSegment(String field, String qname, String segment) {
      if(qname == null) {
         return null;
      }

      JDBCSelection xselect = (JDBCSelection) uniformSql.getSelection();
      int idx = getAliasIndex(xselect, field);

      if(idx >= 0) {
         return xselect.getQuotedColumn(idx);
      }

      return segment != null ? segment : xselect.getQuotedColumn(qname);
   }

   /**
    * Get the index of the select column a group by or order by field references by its
    * alias.
    * @return the index, or -1 if the field is not an alias other than its column.
    */
   private static int getAliasIndex(XSelection xselect, String field) {
      for(int i = 0; i < xselect.getColumnCount(); i++) {
         if(field.equals(xselect.getAlias(i)) && !field.equals(xselect.getColumn(i))) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Restore the quotes of a name written as a quoted identifier (e.g. "MixedCase") in the
    * parsed sql, which is stored without its quotes. Only the column segment is quoted, so
    * a name qualified by its table (t.MixedCase) is generated as t."MixedCase".
    * @param name the stored name, without quotes.
    * @param str the sql generated for the name.
    * @param segment the column segment recorded by the parser for a qualified quoted
    *                identifier, or <tt>null</tt> to find the column by the table of the name.
    */
   private String quoteIdentifier(String name, String str, String segment) {
      String table = uniformSql.getTable(name);
      int dot = name.lastIndexOf('.');
      String column = name;

      // the column segment of a qualified quoted identifier, recorded by the parser
      if(segment != null && name.endsWith("." + segment)) {
         column = segment;
      }
      else if(table != null && !table.isEmpty() && name.startsWith(table + ".")) {
         column = name.substring(table.length() + 1);
      }
      // the table may be stored quoted (e.g. postgresql)
      else if(dot > 0 && (uniformSql.getTableIndex(name.substring(0, dot)) >= 0 ||
         uniformSql.getTableIndex(getQuote() + name.substring(0, dot) + getQuote()) >= 0))
      {
         column = name.substring(dot + 1);
      }

      String quoted = getQuote() + column + getQuote();

      if(str.equals(column)) {
         return quoted;
      }
      else if(str.endsWith("." + column)) {
         return str.substring(0, str.length() - column.length()) + quoted;
      }

      // already quoted, or generated as an alias or a column index
      return str;
   }

   public void setVPMCondition(boolean vpm) {
      vpmCondition = vpm;
   }

   /**
    * Trim quote from table.
    */
   public String trimQuote(String table) {
      if(table == null) {
         return null;
      }

      if(table.startsWith(getQuote()) && table.endsWith(getQuote())) {
         return table.substring(1, table.length() - 1);
      }

      return table;
   }

   /**
    * Set whether to use ANSI outer join syntax.
    */
   public void setAnsiJoin(boolean ansi) {
      ansiJoin = ansi;
   }

   /**
    * Check whether to use ANSI outer join syntax.
    * @return boolean if use ANSI outer join syntax, return true, else false.
    */
   public boolean isAnsiJoin() {
      return ansiJoin || hasFullJoin;
   }

   /**
    * Check if should use ansi join syntax. True if it's set to ansi join
    * explicitly, or if the query contains outer join.
    */
   private boolean isAnsiJoin0() {
      return ansiJoin || hasOuterJoin;
   }

   /**
    * Set the uniform sql.
    */
   public void setUniformSql(UniformSQL uniformSql) {
      this.uniformSql = uniformSql;

      XJoin[] joins = uniformSql.getJoins();
      hasOuterJoin = false;
      hasFullJoin = false;

      for(int i = 0; joins != null && i < joins.length; i++) {
         if(joins[i].isOuterJoin()) {
            hasOuterJoin = true;
         }

         if(joins[i].isFullOuterJoin()) {
            hasFullJoin = true;
            break;
         }
      }
   }

   /**
    * Check if a word is a keyword.
    * @param word the specified keyword.
    * @return <tt>true</tt> is a keyword, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isKeyword(String word) {
      if(keywords.contains(word)) {
         return !"type".equals(word) || isSpecialDriver();
      }

      return containsKeyword(word);
   }

   /**
    * Check if a word contains a keyword.
    * @param word the specified keyword.
    * @return <tt>true</tt> is a keyword, <tt>false</tt> otherwise.
    */
   private boolean containsKeyword(String word) {
      int idx = word.indexOf('.');

      if(idx == -1) {
         return false;
      }

      word = word.substring(0, idx);
      return keywords.contains(word) && (!"type".equals(word) || isSpecialDriver());
   }

   /**
    * Check if the Driver is cs.jdbc.driver.CompositeDriver
    */
   private boolean isSpecialDriver() {
      //maybe use hashmap for this method in the future
      //key is driver, value is list
      //but now, only "type" is keyword for cs.jdbc.driver.CompositeDriver
      if(uniformSql == null) {
         return false;
      }

      JDBCDataSource dx = uniformSql.getDataSource();
      String driver = dx == null ? null : dx.getDriver();

      if(driver == null) {
         return false;
      }
      else if(driver.equals("cs.jdbc.driver.CompositeDriver")) {
         return true;
      }

      return false;
   }

   /**
    * Get the quote.
    * @return the quote.
    */
   @Override
   public String getQuote() {
      return "\"";
   }

   /**
    * Escapes any string literal delimiters inside of a string literal. By default, the string
    * literal delimiter is a single quote and is escaped by double single quotes.
    *
    * @param literal the string to fix.
    *
    * @return the fixed string.
    */
   public String fixStringLiteral(String literal) {
      if(literal == null || literal.length() < 2) {
         return literal;
      }

      if(literal.charAt(0) != '\'' || literal.charAt(literal.length() - 1) != '\'') {
         return literal;
      }

      String content = literal.substring(1, literal.length() - 1).replace("'", "''");
      return "'" + content + "'";
   }

   /**
    * Create a query that limits the output to the specified number of rows.
    */
   protected String generateMaxRowsClause(String sql, int maxrows) {
      return sql;
   }

   /**
    * Determines if the row limit should be applied in SQL to top-level queries.
    *
    * @return {@code true} to apply limit or {@code false} otherwise.
    */
   public boolean isApplyMaxRowsToTopLevel() {
      return false;
   }

   /**
    * Check if the alias string is valid.
    */
   public boolean isValidAlias(String alias) {
      // an valid alias should not contains '"', which will not be quoted
      // properly, nor should it be longer than 30, which is not supported
      // by oracle, sybase 28
      return alias == null || !alias.contains("\"") && !alias.contains(getQuote()) &&
         (!isLimitAlias() || getAliasLength(alias) <= 28);
   }

   /**
    * Rest the helper cache.
    */
   public static void resetCache() {
      LOAD_LOCK.lock();

      try {
         helperTable = null;
         unsupported = null;
         afuncs = null;
         dfuncs = null;
      }
      finally {
         LOAD_LOCK.unlock();
      }
   }

   /**
    * Get the alias length.
    */
   private static int getAliasLength(String alias) {
      if(alias == null) {
         return 0;
      }

      int length = alias.length();
      int counter = 0;

      // for non-ascii char, it will occupy 2 bytes, tested in oracle/sybase
      for(int i = 0; i < length; i++) {
         char c = alias.charAt(i);
         counter += c < 256 ? 1 : 2;
      }

      return counter;
   }

   /**
    * Get helper class name.
    */
   private static String getHelperClass(String dbType) {
      if(helperTable == null) {
         LOAD_LOCK.lock();

         try {
            helperTable = new Hashtable<>();
            unsupported = new HashSet<>();
            afuncs = new Hashtable<>();
            dfuncs = new Hashtable<>();

            try {
               InputStream input = XNode.class.getResourceAsStream(
                  "/inetsoft/uql/sqlhelper.xml");
               Document doc = Tool.parseXML(input);
               NodeList nlist = doc.getElementsByTagName("config");

               if(nlist != null && nlist.getLength() > 0) {
                  Element node = (Element) nlist.item(0);
                  nlist = Tool.getChildNodesByTagName(node, "helper");

                  for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
                     Element node2 = (Element) nlist.item(i);
                     String type = node2.getAttribute("type");
                     String className = node2.getAttribute("class");
                     helperTable.put(type, className);

                     Element mnode =
                        Tool.getChildNodeByTagName(node2, "maxidentifier");

                     if(mnode != null) {
                        String str = Tool.getValue(mnode);
                        maxidentifier.put(type, Integer.valueOf(str));
                     }
                  }

                  nlist = Tool.getChildNodesByTagName(node, "testQuery");

                  for(int i = 0; i < nlist.getLength(); i++) {
                     Element node2 = (Element) nlist.item(i);
                     String type = node2.getAttribute("type");
                     String testQuery = Tool.getValue(node2);
                     connectionTestQuery.put(type, testQuery);
                  }

                  nlist = Tool.getChildNodesByTagName(node, "unsupported");

                  for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
                     Element node2 = (Element) nlist.item(i);
                     String type = node2.getAttribute("type");
                     String op = node2.getAttribute("op");
                     unsupported.add(type + ":" + op);
                  }

                  nlist = Tool.getChildNodesByTagName(node, "aggregate");

                  for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
                     Element node2 = (Element) nlist.item(i);
                     String type = node2.getAttribute("type");
                     String func = node2.getAttribute("function");
                     String name = node2.getAttribute("name");

                     afuncs.put(type + ":" + func, name);
                  }

                  nlist = Tool.getChildNodesByTagName(node, "sqlfunc");
                  String mysql = "mysql";
                  String convert_tz = "convert_tz(";
                  String server_tz = SreeEnv.getProperty("mysql.server.timezone");
                  server_tz = server_tz == null ? "" : server_tz.trim();
                  boolean tz_valid = server_tz.length() > 0;
                  String tz_cmd = "";

                  if(tz_valid) {
                     String locale_tz = SreeEnv.getProperty("mysql.local.timezone");
                     locale_tz = locale_tz == null ? "" : locale_tz.trim();

                     if(locale_tz.length() <= 0) {
                        locale_tz = TimeZone.getDefault().getID();
                     }

                     tz_cmd = ",''" + locale_tz + "'',''" + server_tz + "''";
                  }

                  for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
                     Element node2 = (Element) nlist.item(i);
                     String type = node2.getAttribute("type");
                     String func = node2.getAttribute("function");
                     String cmd = node2.getAttribute("command");

                     if(mysql.equalsIgnoreCase(type) && cmd.startsWith(convert_tz))
                     {
                        if(tz_valid) {
                           cmd = cmd.substring(0, cmd.length() - 1) + tz_cmd + ")";
                        }
                        // no time zone? ignore it
                        else {
                           cmd = cmd.substring(11, cmd.length() - 1);
                        }
                     }

                     dfuncs.put(type + ":" + func, cmd);
                  }
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to load SQL helper configuration", ex);
            }
         }
         finally {
            LOAD_LOCK.unlock();
         }
      }

      String className = null;

      if(dbType != null) {
         className = SQLHelper.helperTable.get(dbType);
      }

      if(className == null) {
         className = "inetsoft.uql.jdbc.SQLHelper";
      }

      return className;
   }

   /**
    * Order outer join to the end of the list.
    */
   private static class OuterJoinComparator implements Comparator<XJoin> {
      @Override
      public int compare(XJoin j1, XJoin j2) {
         try {
            boolean outer1 = j1.isOuterJoin();
            boolean outer2 = j2.isOuterJoin();

            if(outer1 == outer2) {
               return 0;
            }
            else if(outer1) {
               return 1;
            }
            else {
               return -1;
            }
         }
         catch(Exception ex) {
            return 0;
         }
      }
   }

   /**
    * Replace the references to a table with a new alias.
    */
   private String replaceTableByAlias(boolean subQuery, String expr) {
      return replaceTableByAlias(subQuery, expr, false);
   }

   /**
    * Replace the references to a table with a new alias.
    */
   private String replaceTableByAlias(boolean subQuery, String expr, boolean ignoreNotChangeAlias) {
      return replaceTableByAlias(subQuery, expr, ignoreNotChangeAlias, true);
   }

   /**
    * Replace the references to a table with a new alias.
    * @param quoteSubqueries <tt>true</tt> to also quote the qualifiers of quoted tables in the
    * text of the subqueries in the expression, see quoteQualifiers.
    */
   private String replaceTableByAlias(boolean subQuery, String expr, boolean ignoreNotChangeAlias,
                                      boolean quoteSubqueries)
   {
      Map<String, String> subquerymap = new HashMap<>();
      final HashSet<String> subqueryAliases = new HashSet<>();

      // hide subqueires so the names in the subqueries are not touched.
      // it's up to the subquery generation to make sure the names are
      // correct in the sub-query
      expr = hideSubqueries(expr, subquerymap);
      UniformSQL sql = uniformSql;

      do {
         for(int i = 0; i < sql.getTableCount(); i++) {
            final SelectTable selectTable = sql.getSelectTable(i);
            final String alias = selectTable.getAlias();

            // Subquery aliases take priority over parent aliases
            if(alias == null || subqueryAliases.contains(alias)) {
               continue;
            }

            subqueryAliases.add(alias);
            final Object tobj = selectTable.getName();
            String alias2 = quoteTableName(alias, true);

            // if the table already has an alias, don't need to replace
            if((tobj instanceof String && alias.equals(tobj)) ||
               (aliasmap != null && aliasmap.containsKey(alias)))
            {
               String nalias = aliasmap == null ? null : aliasmap.get(alias);

               if(nalias == null && subQuery && !ignoreNotChangeAlias) {
                  nalias = alias;
               }

               if(nalias == null) {
                  continue;
               }

               nalias = quoteTableAlias(nalias);
               expr = replaceTable(expr, alias, nalias);

               if(!alias2.equals(alias)) {
                  expr = replaceTable(expr, alias2, nalias);
               }
            }
         }
      } while(subQuery && (sql = sql.getParent()) != null);

      // a table written quoted ("a") is stored without its quotes in the expression text, as
      // before, so every reader of the text sees the same text, and is quoted here (#77569)
      Map<String, String> qualifiers = getQuotedQualifiers();

      if(!qualifiers.isEmpty()) {
         expr = quoteQualifiers(expr, qualifiers);

         // a scalar subquery of the select list is stored as text, its references to the
         // tables of this query are quoted here
         if(quoteSubqueries) {
            subquerymap.replaceAll((key, sub) -> quoteQualifiers(sub, qualifiers));
         }
      }

      // restore the subquery in the expression
      expr = restoreSubqueries(expr, subquerymap);

      return expr;
   }

   /**
    * Get the qualifiers to quote in expression text: the names of the tables of this query,
    * and of its outer queries, written with quoted segments ("a", "S"."a"), and the ends of
    * such names ("a" for "S"."a"). The name of a table of an inner query hides the same name
    * in an outer query.
    * @return the qualifiers as stored, mapped to the quoted qualifiers.
    */
   private Map<String, String> getQuotedQualifiers() {
      Map<String, String> qualifiers = new HashMap<>();

      // a table changed to a subquery is referred to by its alias, which is not quoted.
      // The sql generated while parsing is stored as text, without the quotes
      if(isTableSubquery() || UniformSQL.isUnquoted()) {
         return qualifiers;
      }

      Set<String> hidden = new HashSet<>();

      for(UniformSQL sql = uniformSql; sql != null; sql = sql.getOuterSQL()) {
         List<String> names = new ArrayList<>();

         for(int i = 0; i < sql.getTableCount(); i++) {
            SelectTable table = sql.getSelectTable(i);
            Object name = table.getName();

            if(table.getAlias() != null) {
               names.add(table.getAlias());
            }

            if(name instanceof String) {
               names.add((String) name);
            }

            // only a table without an alias is named by its name
            if(table.getQuotedSegments() == null || !(name instanceof String) ||
               !name.equals(table.getAlias()) || hidden.contains(name))
            {
               continue;
            }

            String qname = quoteQuotedSegments(table, (String) name);

            if(qname == null) {
               continue;
            }

            qualifiers.putIfAbsent((String) name, qname);
            List<String> segs = splitTableName((String) name);
            List<String> qsegs = splitTableName(qname);

            for(int start = 1; qsegs.size() == segs.size() && start < segs.size(); start++) {
               String suffix = String.join(".", segs.subList(start, segs.size()));
               String qsuffix = String.join(".", qsegs.subList(start, qsegs.size()));

               if(!suffix.equals(qsuffix) && sql.getTableIndex(suffix) < 0 &&
                  !hidden.contains(suffix))
               {
                  qualifiers.putIfAbsent(suffix, qsuffix);
               }
            }
         }

         hidden.addAll(names);
      }

      return qualifiers;
   }

   /**
    * Replace the qualifiers in expression text that are written at the start of an
    * identifier, outside quotes and string literals, and followed by a dot. All the
    * qualifiers are replaced in one pass, so a qualifier is never replaced twice.
    * @param qualifiers the qualifiers mapped to their replacements.
    */
   public static String replaceQualifiers(String expr, Map<String, String> qualifiers) {
      return quoteQualifiers(expr, qualifiers);
   }

   /**
    * Quote the qualifiers in expression text that are written at the start of an identifier,
    * outside quotes and string literals, and followed by a dot.
    */
   private static String quoteQualifiers(String expr, Map<String, String> qualifiers) {
      List<String> names = new ArrayList<>(qualifiers.keySet());
      names.sort((a, b) -> b.length() - a.length());
      StringBuilder sb = new StringBuilder();
      char close = 0;

      for(int i = 0; i < expr.length(); i++) {
         char c = expr.charAt(i);

         if(close != 0) {
            if(c == close) {
               close = 0;
            }

            sb.append(c);
            continue;
         }

         char prev = i > 0 ? expr.charAt(i - 1) : ' ';
         boolean start = !Character.isUnicodeIdentifierPart(prev) && prev != '.' &&
            prev != '"' && prev != '`' && prev != ']' && prev != '$' && prev != '@';
         String found = null;

         for(int j = 0; start && found == null && j < names.size(); j++) {
            if(expr.startsWith(names.get(j) + ".", i)) {
               found = names.get(j);
            }
         }

         if(found != null) {
            sb.append(qualifiers.get(found)).append('.');
            i += found.length();
            continue;
         }

         if(c == '"' || c == '`' || c == '\'') {
            close = c;
         }
         else if(c == '[') {
            close = ']';
         }

         sb.append(c);
      }

      return sb.toString();
   }

   /**
    * Replace table name in expression.
    */
   protected String replaceTable(String expr, String alias, String nalias) {
      int idx = 0;
      int s;

      while((s = expr.indexOf(alias + ".", idx)) >= 0) {
         // if this is not the start of an identifier, don't replace
         if(s > 0 && Character.isUnicodeIdentifierPart(expr.charAt(s-1))){
            idx = s + 1;
            continue;
         }

         expr = expr.substring(0, s) +
            nalias +
            "." +
            expr.substring(s + alias.length() + 1);
         idx = s + nalias.length() + 1;
      }

      return expr;
   }

   /**
    * Replace subquery strings with coded unique subquery name and store the
    * subquery string in the map.
    */
   private String hideSubqueries(String expr, Map<String,String> subquerymap) {
      Matcher matcher = pattern.matcher(expr);
      boolean found = matcher.find();

      // the expression itself is a subquery (a subquery operand), it's not hidden so the
      // names of the outer query in it are still replaced, only the subqueries in it are
      // hidden. The match position is used, not the length of a split piece, which is short
      // by the leading match and hid a wrong region (77633)
      if(found && expr.substring(0, matcher.start()).isBlank()) {
         found = matcher.find();
      }

      if(found) {
         int s1 = matcher.start(); // starting position of the subquery
         int s2 = findClosingParen(expr, s1);
         String key = "___SUBQUERY__" + subquerymap.size() + "_inetsoft_";

         subquerymap.put(key, expr.substring(s1, s2 + 1));
         expr = expr.substring(0, s1) + key + expr.substring(s2 + 1);

         return hideSubqueries(expr, subquerymap);
      }

      return expr;
   }

   /**
    * Find the closing paren position.
    */
   private int findClosingParen(String expr, int idx) {
      int cnt = 0;
      char quote = 0;

      for(int i = idx; i < expr.length(); i++) {
         char ch = expr.charAt(i);

         if(quote != 0) {
            if(ch == quote) {
               quote = 0;
            }

            continue;
         }

         switch(ch) {
         case '"':
         case '\'':
            quote = ch;
            break;
         case '(':
            cnt++;
            break;
         case ')':
            cnt--;

            if(cnt == 0) {
               return i;
            }
            break;
         }
      }

      return expr.length() - 1;
   }

   /**
    * Restore the subqueries in the expression.
    */
   private String restoreSubqueries(String expr, Map<String,String> subquerymap) {
      for(String key : subquerymap.keySet()) {
         String val = subquerymap.get(key);
         expr = expr.replaceAll(key, Matcher.quoteReplacement(val));
      }

      return expr;
   }

   /**
    * Check if table is changed to a subquery.
    */
   protected boolean isTableSubquery() {
      // max rows now ignored on vpm (getFromTable). (61047)
      return inpmaxrows > 0 && supportsOperation(MAXROWS) && !uniformSql.hasVPMCondition();
   }

   /**
    * If a  table alias exceed maximum identifier length, map it to a short
    * alias. The aliasmap contains the mapping.
    */
   private void fixTableAliases() {
      Integer max = maxidentifier.get(getSQLHelperType());
      int maxlen = (max == null) ? 128 : max;
      Set<String> aliases = aliasmap == null ? new HashSet<>() : new HashSet<>(aliasmap.values());
      Set<String> tableNames = new HashSet<>();

      for(int i = 0; i < uniformSql.getTableCount(); i++) {
         String alias = uniformSql.getTableAlias(i);
         Object tobj = uniformSql.getTableName(alias);
         String nalias = fixTableAlias(tobj, alias, maxlen, i, aliases);
         SelectTable selectTable = uniformSql.getSelectTable(i);
         String catalog = selectTable.getCatalog() == null ? null :
            XUtil.quoteNameSegment(selectTable.getCatalog(), this);
         String schema = selectTable.getSchema() == null ? null :
            XUtil.quoteNameSegment(selectTable.getSchema(), this);
         String realTableName =
            JDBCUtil.getRealTableName(Tool.toString(selectTable.getName()), catalog, schema);

         if(alisDuplicateTableName() && Tool.equals(nalias, tobj)) {
            if(tableNames.contains(realTableName)) {
               nalias = getAutoAlias(nalias, i);
            }
            else {
               tableNames.add(realTableName);
            }
         }

         if(!alias.equals(nalias)) {
            if(aliasmap == null) {
               aliasmap = new HashMap<>();
            }

            aliasmap.put(alias, nalias);
            aliasmap.put(XUtil.removeQuote(alias), nalias);
            aliases.remove(alias);
            aliases.add(nalias);
         }
      }
   }

   /**
    * Whether auto alias the duplicate table name.
    */
   protected boolean alisDuplicateTableName() {
      return false;
   }

   /**
    * Fixes a table alias. This allows aliases to be generated for databases that require them.
    *
    * @param name    the table name.
    * @param alias   the table alias.
    * @param maxlen  the maximum length of a table alias.
    * @param index   the index of the table.
    * @param aliases the set of used aliases.
    *
    * @return the fixed alias.
    */
   protected String fixTableAlias(Object name, String alias, int maxlen, int index,
                                  Set<String> aliases)
   {
      if(alias.length() < maxlen) {
         return alias;
      }

      if(!alias.equals(name) || isTableSubquery()) {
         // don't use timestamp here since it causes the query to
         // use different table name in each generation. in case
         // of MV with VPM, it causes the query to always be different
         // between user/role. using the hashcode should be safe here
         // since we also append the table index, which is always
         // unique for each table in a query with fixed number of tables
         return getAutoAlias(alias, index);
      }

      return alias;
   }

   protected boolean requiresSpecialQuoteHandling() {
      return false;
   }

   protected String quoteTableNameWithQuotedSchema(String name, boolean selectClause) {
      //base helper does not require special handling
      return name;
   }

   private String getAutoAlias(String alias, int index) {
      return "a___" + alias.hashCode() + "_" + index;
   }

   /**
    * Format for maxRowsClause.
    */
   private String formatMaxRowsClause(String sql) {
      StringBuilder sql0 = new StringBuilder(
         sql.replaceAll("\\(select" , "(\nselect"));
      return formatSubquery(sql0);
   }

   /**
    * Format subquery condition.
    */
   private String formatSubquery(StringBuilder condition) {
      if(!isFormatSQL) {
         return condition.toString();
      }

      final String gap = "       ";
      int index = 0;
      int searchIndex;

      //If it is subquery, it will contain "(\n".
      while((searchIndex = condition.indexOf("(\n", index)) != -1) {
         //if the subquery has format, move index to continue.
         if(' ' == condition.charAt(searchIndex + 2)) {
            index = searchIndex + 2;
            continue;
         }

         //Get end index of subquery .
         int endIndex = searchBracket(condition, searchIndex);
         index = searchIndex + 1;
         int insertIndex;

         //Add indentation in subquery's every beginning of line.
         while(!((insertIndex = condition.indexOf("(\n", searchIndex)) > endIndex)) {
            if(insertIndex != -1) {
               condition.insert(insertIndex + 1, gap);
               searchIndex = insertIndex + 1;
            }
            else {
               searchIndex++;
               break;
            }
         }
      }

      return condition.toString();
   }

   /**
    * Search index of ")" which matches "(".
    */
   private int searchBracket(StringBuilder input, int searchIndex) {
      Deque<Character> stack = new ArrayDeque<>();

      while(searchIndex < input.length()) {
         char c = input.charAt(searchIndex);

         if(c == '(') {
            stack.addLast(c);
         }
         else if(c == ')') {
            stack.removeLast();

            if(stack.isEmpty()) {
               stack.clear();
               return searchIndex;
            }
         }

         searchIndex++;
      }

      stack.clear();
      return -1;
   }

   /**
    * For test run auto case. it will not formate sql.
    */
   private void initFormatKeywords() {
      isFormatSQL = "true".equals(SreeEnv.getProperty("sql.format", "true"));

      if(isFormatSQL) {
         BRACKET = "(\n";
         GAP = "\n       ";
         COMMA = ",\n      ";
         COMMA_GAP = ",\n       ";
         AND = "\n            AND ";
         OR = "\n            OR ";
         FROM = "from   ";
         WHERE = "where  ";
         ORDER_BY = "order by ";
         GROUP_BY = "group by ";
      }
      else {
         BRACKET = "(";
         GAP = " ";
         COMMA= ",";
         COMMA_GAP = ", ";
         AND = "AND ";
         OR = "OR ";
         FROM = "from ";
         WHERE = "where ";
         ORDER_BY = "order by ";
         GROUP_BY = "group by ";
      }
   }

   public int getInClauseLimit() {
      return 0;
   }

   public String fixSQLExpression(String sql) {
      return sql;
   }

   private static Hashtable<String, String> helperTable = null; // sql helper table
   private static HashSet<String> unsupported = new HashSet<>(); // unsupported
   private static Hashtable<String, String> afuncs = new Hashtable<>(); // func code -> func name
   private static Hashtable<String, String> dfuncs = new Hashtable<>(); // func code -> command
   private static Set<String> keywords = new HashSet<>(); // keywords
   private static Hashtable<String, Integer> maxidentifier = new Hashtable<>(); // type -> id len
   private static Map<String, String> connectionTestQuery = new ConcurrentHashMap<>();

   static {
      keywords.add("absolute");
      keywords.add("action");
      keywords.add("ada");
      keywords.add("add");
      keywords.add("all");
      keywords.add("allocate");
      keywords.add("alter");
      keywords.add("and");
      keywords.add("any");
      keywords.add("are");
      keywords.add("as");
      keywords.add("asc");
      keywords.add("assertion");
      keywords.add("at");
      keywords.add("authorization");
      keywords.add("avg");
      keywords.add("begin");
      keywords.add("between");
      keywords.add("bit");
      keywords.add("bit_length");
      keywords.add("both");
      keywords.add("by");
      keywords.add("cascade");
      keywords.add("cascaded");
      keywords.add("case");
      keywords.add("cast");
      keywords.add("catalog");
      keywords.add("char");
      keywords.add("char_length");
      keywords.add("character");
      keywords.add("character_length");
      keywords.add("check");
      keywords.add("close");
      keywords.add("coalesce");
      keywords.add("collate");
      keywords.add("collation");
      keywords.add("column");
      keywords.add("commit");
      keywords.add("connect");
      keywords.add("connection");
      keywords.add("constraint");
      keywords.add("constraints");
      keywords.add("continue");
      keywords.add("convert");
      keywords.add("corresponding");
      keywords.add("count");
      keywords.add("create");
      keywords.add("cross");
      keywords.add("current");
      keywords.add("current_date");
      keywords.add("current_time");
      keywords.add("current_timestamp");
      keywords.add("current_user");
      keywords.add("cursor");
      keywords.add("date");
      keywords.add("day");
      keywords.add("deallocate");
      keywords.add("dec");
      keywords.add("decimal");
      keywords.add("declare");
      keywords.add("default");
      keywords.add("deferrable");
      keywords.add("deferred");
      keywords.add("delete");
      keywords.add("desc");
      keywords.add("describe");
      keywords.add("descriptor");
      keywords.add("diagnostics");
      keywords.add("disconnect");
      keywords.add("distinct");
      keywords.add("domain");
      keywords.add("double");
      keywords.add("drop");
      keywords.add("else");
      keywords.add("end");
      keywords.add("end-exec");
      keywords.add("escape");
      keywords.add("except");
      keywords.add("exception");
      keywords.add("exec");
      keywords.add("execute");
      keywords.add("exists");
      keywords.add("external");
      keywords.add("extract");
      keywords.add("false");
      keywords.add("fetch");
      keywords.add("first");
      keywords.add("float");
      keywords.add("for");
      keywords.add("foreign");
      keywords.add("fortran");
      keywords.add("found");
      keywords.add("from");
      keywords.add("full");
      keywords.add("get");
      keywords.add("global");
      keywords.add("go");
      keywords.add("goto");
      keywords.add("grant");
      keywords.add("group");
      keywords.add("having");
      keywords.add("hour");
      keywords.add("identity");
      keywords.add("immediate");
      keywords.add("in");
      keywords.add("include");
      keywords.add("index");
      keywords.add("indicator");
      keywords.add("initially");
      keywords.add("inner");
      keywords.add("input");
      keywords.add("insensitive");
      keywords.add("insert");
      keywords.add("int");
      keywords.add("integer");
      keywords.add("intersect");
      keywords.add("interval");
      keywords.add("into");
      keywords.add("is");
      keywords.add("isolation");
      keywords.add("join");
      keywords.add("key");
      keywords.add("language");
      keywords.add("last");
      keywords.add("leading");
      keywords.add("left");
      keywords.add("level");
      keywords.add("like");
      keywords.add("local");
      keywords.add("lower");
      keywords.add("match");
      keywords.add("max");
      keywords.add("min");
      keywords.add("minute");
      keywords.add("minus");
      keywords.add("module");
      keywords.add("month");
      keywords.add("names");
      keywords.add("national");
      keywords.add("natural");
      keywords.add("nchar");
      keywords.add("next");
      keywords.add("no");
      keywords.add("none");
      keywords.add("not");
      keywords.add("null");
      keywords.add("nullif");
      keywords.add("numeric");
      keywords.add("octet_length");
      keywords.add("of");
      keywords.add("on");
      keywords.add("only");
      keywords.add("open");
      keywords.add("option");
      keywords.add("or");
      keywords.add("order");
      keywords.add("outer");
      keywords.add("output");
      keywords.add("overlaps");
      keywords.add("pad");
      keywords.add("partial");
      keywords.add("pascal");
      keywords.add("position");
      keywords.add("precision");
      keywords.add("prepare");
      keywords.add("preserve");
      keywords.add("primary");
      keywords.add("prior");
      keywords.add("privileges");
      keywords.add("procedure");
      keywords.add("public");
      keywords.add("read");
      keywords.add("real");
      keywords.add("references");
      keywords.add("relative");
      keywords.add("restrict");
      keywords.add("revoke");
      keywords.add("right");
      keywords.add("rollback");
      keywords.add("rows");
      keywords.add("schema");
      keywords.add("scroll");
      keywords.add("second");
      keywords.add("section");
      keywords.add("select");
      keywords.add("session");
      keywords.add("session_user");
      keywords.add("set");
      keywords.add("size");
      keywords.add("smallint");
      keywords.add("some");
      keywords.add("space");
      keywords.add("sql");
      keywords.add("sqlca");
      keywords.add("sqlcode");
      keywords.add("sqlerror");
      keywords.add("sqlstate");
      keywords.add("sqlwarning");
      keywords.add("substring");
      keywords.add("sum");
      keywords.add("system_user");
      keywords.add("table");
      keywords.add("temporary");
      keywords.add("then");
      keywords.add("time");
      keywords.add("timestamp");
      keywords.add("timezone_hour");
      keywords.add("timezone_minute");
      keywords.add("to");
      keywords.add("trailing");
      keywords.add("transaction");
      keywords.add("translate");
      keywords.add("translation");
      keywords.add("trim");
      keywords.add("true");
      keywords.add("type");   //keyword for cs.jdbc.driver.CompositeDriver
      keywords.add("union");
      keywords.add("unique");
      keywords.add("unknown");
      keywords.add("update");
      keywords.add("upper");
      keywords.add("usage");
      keywords.add("user");
      keywords.add("using");
      keywords.add("value");
      keywords.add("values");
      keywords.add("varchar");
      keywords.add("varying");
      keywords.add("view");
      keywords.add("when");
      keywords.add("whenever");
      keywords.add("where");
      keywords.add("with");
      keywords.add("work");
      keywords.add("write");
      keywords.add("year");
      keywords.add("zone");
   }

   private static final UniformSQL defaultSql = new UniformSQL();
   private static Map<String, Set<String>> expErrorState = new HashMap<>();
   private String BRACKET;
   private String COMMA;
   private String GAP;
   private String COMMA_GAP;
   private String AND;
   private String OR;
   private String FROM;
   private String WHERE;
   private String ORDER_BY;
   private String GROUP_BY;
   private static Pattern pattern = Pattern.compile("\\([\\s]*select[\\s]+",
                                    Pattern.CASE_INSENSITIVE);
   private static ConcurrentMap<String, Class> helperClasses = new ConcurrentHashMap<>();
   private static final Lock LOAD_LOCK = new ReentrantLock();

   protected UniformSQL uniformSql; // uniform sql
   protected int inpmaxrows = 0; // max row limit on individual tables
   protected int outmaxrows = 0; //max row limit on output
   private boolean ansiJoin = false; // ansi join flag
   private boolean hasOuterJoin = false; // outer join flag
   private boolean hasFullJoin = false; // outer join flag
   protected boolean having = false; // in having
   private boolean vpmCondition = false; // in vpm condition
   private List<XJoin> vJoin = null; // for ansi join
   // true if the condition being generated is in a join position of the where clause
   private boolean ansiJoinPosition = false;
   private Boolean textJoinOrder = null; // isTextJoinOrder of this generation
   // conditions of the joins the ANSI FROM clause wrote in the where clause
   private List<String> ansiWhereJoins = null;
   // true to generate the ANSI FROM clause without writing joins as cycle conditions
   private boolean noCycleConditions = false;
   // true if this generation moved or parenthesized a comma separated join group, see
   // orderCommaGroups (Bug #77675)
   private boolean commaGroupsChanged = false;
   // true if commaGroupsChanged did more than move the only RIGHT or FULL join group to the
   // front, see isCommaGroupsNotMovable (Bug #77675)
   private boolean commaGroupsNotMovable = false;
   private Map<String, String> aliasmap = null; // old table alias -> new alias
   private String version = "";
   private boolean isFormatSQL; //for test auto case. Test will not format sql.
   private boolean caseSensitive;

   // Matches map key access expressions like m['key2'] (ClickHouse/Databricks)
   private static final Pattern MAP_KEY_ACCESS =
      Pattern.compile("([^\\s\\[\\]]+)\\[\\s*'([^\\s']+)'\\s*\\]");
   // the table of the limited table query that gives the row limit syntax of the database
   // (appendLimitClause()). Not a name a statement uses, and without a limit keyword.
   private static final String LIMIT_PLACEHOLDER = "inetsoft_placeholder";
   // a generated select clause with a top option (getSelectionOption())
   private static final Pattern SELECT_TOP =
      Pattern.compile("select(\\s+(distinct|all))?\\s+top\\s");
   // Maps "tableAlias.originalColExpr" -> safe alias used in the inner query
   private final Map<String, String> subQueryMapKeyAliases = new HashMap<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(SQLHelper.class);
}
