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

import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.execution.ReportWorksheetProcessor;
import inetsoft.report.internal.binding.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.Condition;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.script.XTableArray;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.rgraph.TableNode;
import inetsoft.uql.util.sqlparser.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.*;
import inetsoft.util.MessageFormat;
import inetsoft.util.audit.*;
import inetsoft.util.script.*;

import java.awt.*;
import java.beans.*;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.*;
import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.*;
import java.text.*;
import java.util.Date;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.regex.*;
import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.zaxxer.hikari.HikariConfig;
import org.mozilla.javascript.Undefined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

/**
 * Utility methods for inetsoft.uql packages.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public final class XUtil {
   /**
    * Thread local variable to contain query infomation. Set an array with two
    * string: the name of the asset in which the query was executed and the
    * unique id for the query;
    */
   public static final ThreadLocal<List<String>> QUERY_INFOS = new ThreadLocal<>();

   /**
    * Thread local variable containing the current viewsheet assembly associated
    * with this query.
    */
   public static final ThreadLocal<String> VS_ASSEMBLY = new ThreadLocal<>();

   /**
    * Query map stores query and query info pairs.
    */
   public static final Map<String, QueryInfo> queryMap = new ConcurrentHashMap<>();

   /**
    * Database user name prefix.
    */
   public static final String DB_USER_PREFIX = "_Db_User_";
   /**
    * Database password prefix.
    */
   public static final String DB_PASSWORD_PREFIX = "_Db_Password_";

   /**
    * Database name.
    */
   public static final String DB_NAME_PARAMETER_NAME = "^Db_Name^";

   /**
    * Expand SQL columns from " select * ".
    * <p>
    * The datasource is assumed to have been connected on this session,
    * if not, the code call this method should take the responsibility
    * to explicitly connect the datasource.
    */
   public static void expandSQLColumns(XRepository repository, XTypeNode root,
                                       XQuery xquery, XDataSource dx,
                                       Object session) throws Exception {
      // expand '*' in SQL select
      for(int k = 0; k < root.getChildCount(); k++) {
         String child = root.getChild(k).getName();
         boolean containsStar = child.endsWith(".*");
         boolean isStar = child.equals("*");

         if(containsStar || isStar) {
            // @by mikec
            // Removed the code to invoke connection parameter dialog.
            // for if we invoke a java based GUI for connection parameter,
            // when this method be called from sree package, the program
            // will hung up on server side waiting for connection.
            // Any code call this method must take the responsibility
            // to show up the connection parameters dialog in a properly
            // way.

            String[] tnames = new String[3];
            String tname = null;

            // contains star?
            if(containsStar) {
               tname = child.substring(0, child.length() - 2);
            }
            // is star?
            else {
               XAgent agent = XAgent.getAgent(xquery);
               Object[] tables = agent.getTables(xquery);

               if(tables.length > 0) {
                  tname = tables[0] == null ? null : tables[0].toString();
               }
            }

            if(tname == null) {
               LOG.warn("can not expand node: {}", xquery);
               continue;
            }

            for(int i = 0; i < 3; i++) {
               int idx = tname.lastIndexOf('.');

               if(idx < 1) {
                  tnames[i] = tname;
                  break;
               }
               else {
                  tnames[i] = tname.substring(idx + 1);
                  tname = tname.substring(0, idx);
               }
            }

            XNode mtype = new XNode(tnames[0]);
            mtype.setAttribute("schema", tnames[1]);
            mtype.setAttribute("catalog", tnames[2]);

            XNode cols = repository.getMetaData(session, dx, mtype, true, null);
            cols = cols.getChild("Result");

            if(cols != null) {
               root.removeChild(k);
               for(int c = 0; c < cols.getChildCount(); c++) {
                  root.insertChild(k++, cols.getChild(c));
               }

               k--;
            }
         }
      }
   }

   /**
    * Remove quote.
    */
   public static String removeQuote(String name) {
      if(name == null) {
         return name;
      }

      int length = name.length();
      int idx = name.indexOf('\"');

      if(idx < 0 || (idx > 0 && name.charAt(idx - 1) != '.')) {
         return name;
      }

      int idx2 = name.indexOf('\"', idx + 1);

      if(idx2 < 0 || (idx2 < length - 1 && name.charAt(idx2 + 1) != '.')) {
         return name;
      }

      name = name.substring(0, idx) + name.substring(idx + 1, idx2) +
         name.substring(idx2 + 1);

      return removeQuote(name);
   }

   /**
    * Get the catalog part in table.
    * @return the catalog part in table.
    */
   public static String getCatalog(String tname) {
      int index = tname == null ? -1 :
         Tool.indexOfWithQuote(tname, ".", (char) 0);

      if(index < 0) {
         return null;
      }

      // catalog.schema.table
      String catalog = tname.substring(0, index);
      tname = tname.substring(index + 1);
      index = Tool.indexOfWithQuote(tname, ".", (char) 0);

      return index < 0 ? null : catalog;
   }

   /**
    * Clear the comments in the sql statement of a query.
    * @param query the specified query to modify.
    * @return the new query after the process.
    */
   public static XQuery clearComments(XQuery query) {
      if(!(query instanceof JDBCQuery)) {
         return query;
      }

      query = (XQuery) query.clone();
      SQLDefinition definition = ((JDBCQuery) query).getSQLDefinition();

      if(!(definition instanceof UniformSQL)) {
         return query;
      }

      UniformSQL sql = (UniformSQL) definition;

      if(XUtil.isParsedSQL(sql)) {
         return query;
      }

      String str = sql.getSQLString();

      final StringBuilder sb = new StringBuilder();
      SQLIterator iterator = new SQLIterator(str);
      SQLIterator.SQLListener listener = (type, value, comment) -> {
         switch(type) {
         case SQLIterator.TEXT_ELEMENT:
            sb.append(value);
            break;
         case SQLIterator.COLUMN_ELEMENT:
            sb.append(value);
            break;
         case SQLIterator.WHERE_ELEMENT:
            sb.append(value);
            break;
         default:
            // do nothing
            break;
         }
      };

      iterator.addSQLListener(listener);
      iterator.iterate();

      sql.setSQLString(sb.toString(), false);

      return query;
   }

   /**
    * Check if a uniform sql is parsed.
    * @param sql the specified uniform sql.
    * @return <tt>true</tt> if parsed, <tt>false</tt> otherwise.
    */
   public static boolean isParsedSQL(UniformSQL sql) {
      return (sql.isParseSQL() || sql.containsExpressions()) &&
         (!sql.hasSQLString() ||
          sql.getParseResult() == UniformSQL.PARSE_SUCCESS);
   }

   /**
    * Get the physical columns of an attribute.
    * @param partition the specified partition holds the attribute.
    * @param attr the specified attribute.
    * @return the physical columns of the attribute.
    */
   public static String[] getPhysicalColumns(XPartition partition,
                                             XAttribute attr) {
      String[] columns = attr.getColumns();

      for(int i = 0; i < columns.length; i++) {
         int index = columns[i].lastIndexOf('.');
         String table = columns[i].substring(0, index);
         String column = columns[i].substring(index + 1);

         if(partition.isAlias(table)) {
            table = partition.getAliasTable(table, true);
         }

         columns[i] = table + "." + column;
      }

      return columns;
   }

   /**
    * Get the tables of a logical model.
    * @param lmodel the specified logical model.
    * @param partition the specified partition.
    * @return the physical tables of a logical model.
    */
   public static String[] getTables(XLogicalModel lmodel, XPartition partition) {
      // @by stephenwebster, make key into cache unique for extended partitions.
      String partitionName = (partition.getBasePartition() != null ?
         partition.getBasePartition().getName() + "^^" + partition.getName() :
         partition.getName());
      XPartition tempPartition = partitionDataCache.get(partitionName);

      if(tempPartition == null ) {
         tempPartition = partition.applyAutoAliases();
         partitionDataCache.put(partitionName, tempPartition);
      }

      partition = tempPartition;

      Set set = new HashSet();
      Enumeration entities = lmodel.getEntities();

      while(entities.hasMoreElements()) {
         XEntity entity = (XEntity) entities.nextElement();
         Enumeration attributes = entity.getAttributes();

         while(attributes.hasMoreElements()) {
            XAttribute attribute = (XAttribute) attributes.nextElement();

            if(attribute.isExpression()) {
               continue;
            }

            String table = attribute.getTable();

            if(table == null || table.length() == 0) {
               continue;
            }

            if(partition.isAlias(table)) {
               table = partition.getAliasTable(table, true);
            }

            set.add(table);
         }
      }

      String[] tables = new String[set.size()];
      set.toArray(tables);

      return tables;
   }

   /**
    * Get the sub query column.
    */
   public static String getSubQueryColumn(String column, UniformSQL sql, String talias) {
      XSelection selection = sql.getSelection();

      if(selection.isAlias(column)) {
         return talias == null || talias.length() == 0 ? column :
            talias + "." + column;
      }

      selection = sql.getBackupSelection();

      if(selection.isAlias(column)) {
         return talias == null || talias.length() == 0 ? column :
            talias + "." + column;
      }

      if(!sql.isTableColumn(column)) {
         return column;
      }

      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable stable = sql.getSelectTable(i);
         Object name = stable.getName();
         String alias = stable.getAlias();
         alias = alias == null || alias.length() == 0 ? name.toString() : alias;

         if(column.startsWith(alias + ".")) {
            column = column.substring(alias.length() + 1);
            return talias == null || talias.length() == 0 ? column :
               talias + "." + column;
         }

         if((name instanceof String) && column.startsWith(name + ".")) {
            column = column.substring(((String) name).length() + 1);
            return talias == null || talias.length() == 0 ? column :
               talias + "." + column;
         }
      }

      return column;
   }

   /**
    * Use different path split for data model folder to distinguish
    * with base data model name and model name.
    *
    * @param dataSource   the datasource path.
    * @param folder       the folder of data model.
    * @param base         the base data model name.
    * @param name         the data model name.
    * @return
    */
   public static String getDataModelDisplayPath(String dataSource, String folder,
                                                String base, String name)
   {
      if(dataSource == null || name == null) {
         return null;
      }

      String path = dataSource;

      if(folder != null) {
         path += DATAMODEL_FOLDER_SPLITER + folder;
      }

      if(base != null) {
         path += DATAMODEL_PATH_SPLITER + base;
      }

      return path + DATAMODEL_PATH_SPLITER + name;
   }

   /**
    * Format an object with the specified format.
    */
   public static String format(Format format, Object val) {
      return format(format, val, false);
   }

   /**
    * Get the table alias of a table in its uniform sql.
    * @param sql the specified uniform sql.
    * @param tname the specified table name.
    * @param count the count to ignore same table names.
    * @return the table alias if any, <tt>null</tt> otherwise.
    */
   public static String getTableAlias(UniformSQL sql, String tname, int count) {
      if(!XUtil.isParsedSQL(sql)) {
         return null;
      }
      else {
         int counter = -1;

         for(int i = 0; i < sql.getTableCount(); i++) {
            SelectTable table = sql.getSelectTable(i);
            Object name = table.getName();

            if(tname.equals(name)) {
               counter++;

               if(counter >= count) {
                  return table.getAlias();
               }
            }
         }
      }

      return null;
   }

   /**
    * Get the tables of a sql.
    * @param sql the specified sql.
    * @return the tables of the sql.
    */
   public static String[] getTables(UniformSQL sql) {
      if(!XUtil.isParsedSQL(sql)) {
         return getTablesForSQLString(sql.getSQLString(),
                                                                           SQLIterator.COMMENT_TABLE);
      }
      else {
         List list = new ArrayList();

         for(int i = 0; i < sql.getTableCount(); i++) {
            SelectTable table = sql.getSelectTable(i);
            Object name = table.getName();

            // ignore sub
            if(name instanceof String) {
               list.add(name);
            }
         }

         String[] arr = new String[list.size()];
         list.toArray(arr);
         return arr;
      }
   }

   /**
    * Merge a sequence into another sequence.
    */
   public static void merge(XSequenceNode to, XSequenceNode from) {
      to.setName(from.getName());
      to.setValue(from.getValue());

      for(int i = 0; i < from.getChildCount(); i++) {
         to.addChild(from.getChild(i));
      }

      Enumeration keys = from.getAttributeNames();

      while(keys.hasMoreElements()) {
         String name = (String) keys.nextElement();

         to.setAttribute(name, from.getAttribute(name));
      }
   }

   /**
    * Check if a node is resursive under the root.
    */
   public static boolean isRecursive(XNode root, XNode node) {
      // @by larryl, a leaf node can never be recursive
      if(node.getChildCount() == 0) {
         return false;
      }

      for(XNode onode = node, pnode = node.getParent(); pnode != null &&
         onode != root; onode = pnode, pnode = pnode.getParent())
      {
         if(pnode.getName().equals(node.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if is support median function.
    */
   public static boolean supportMedian(SQLHelper helper) {
      String type = helper == null ? null : helper.getSQLHelperType();

      if("oracle".equals(type)) {
         String versionStr = helper.getProductVersion();
         int idx = versionStr.indexOf('.');
         int ver = -1;

         if(idx > 0) {
            versionStr = versionStr.substring(0, idx);
         }

         try {
            ver = Integer.parseInt(versionStr);

            if(ver >= 10) {
               return true;
            }
         }
         catch(Exception e) {
            // ignore it
         }
      }

      return false;
   }

   /**
    * Get the last component of a delimited string.
    */
   public static String getLastComponent(String name, char sep, boolean isExpression) {
      // expression, don't strip
      if(name.indexOf('(') >= 0 && name.indexOf(')') >= 0) {
         return name;
      }

      // if this is an expression instead of table.column
      if(isExpression) {
         return name;
      }

      int idx = name.lastIndexOf(sep);

      return (idx >= 0) ? name.substring(idx + 1) : name;
   }


   /**
    * Call a method on an object without causing exception if the class or
    * method is not in the jvm (e.g. jdk1.2 methods used in jdk1.1)
    */
   public static Object call(Object obj, String clsname, String method,
                             Class[] params, Object[] args) {
      return Tool.call(obj, clsname, method, params, args);
   }

   /**
    * Call a method on an object without causing exception if the class or
    * method is not in the jvm (e.g. jdk1.2 methods used in jdk1.1)
    */
   public static Object field(Class cls, String field) {
      try {
         Field member = cls.getField(field);

         return member.get(null);
      }
      catch(Throwable e) {
         return null;
      }
   }

   /**
    * Call a method on an object without causing exception if the class or
    * method is not in the jvm (e.g. jdk1.2 methods used in jdk1.1)
    */
   public static Object field(String cls, String field) {
      try {
         return field(Class.forName(cls), field);
      }
      catch(Throwable e) {
         return null;
      }
   }

   /**
    * Get the string representation for view.
    */
   public static final String toView(Object dataRef) {
      if(dataRef == null) {
         return null;
      }

      Class cls = dataRef.getClass();

      try {
         Method method = cls.getMethod("toView", new Class[0]);

         if(method != null) {
            return (String) method.invoke(dataRef, new Object[0]);
         }
      }
      catch(Exception ex) {
         // ignore it
      }

      return null;
   }

   /**
    * Check if a name is special.
    * @param quoteKeyword true to quote if the name matches a sql keyword.
    */
   public static final boolean isSpecialName(String str, boolean quoteKeyword,
                                             KeywordProvider provider) {
      String quote = provider == null ? "\"" : provider.getQuote();

      if(str.startsWith(quote) && str.endsWith(quote) && str.length() > 1) {
         return false;
      }

      if(provider != null && provider.isCaseSensitive()) {
         return true;
      }

      return isSpecial(str, quoteKeyword, provider);
   }

   /**
    * Check if a string contains any character in the substring.
    */
   public static final boolean isSpecial(String str, KeywordProvider provider) {
      return isSpecial(str, true, provider);
   }

   /**
    * Check if a string contains any character in the substring.
    * @param quoteKeyword true to quote if the name matches a sql keyword.
    */
   public static final boolean isSpecial(String str, boolean quoteKeyword,
                                         KeywordProvider provider) {
      int length = str.length();
      str = str.toLowerCase();
      provider = provider == null ? new SQLHelper() : provider;
      String quote = provider.getQuote();

      // check for sql keyword
      if(quoteKeyword && provider.isKeyword(str)) {
         return true;
      }

      if(str.startsWith(quote) && str.endsWith(quote) && length > 1) {
         return false;
      }

      for(int i = 0; i < length; i++) {
         char ic = str.charAt(i);

         switch(ic) {
         case '_': // quote alias with '_' as the first char for oracle
            if(i == 0) {
               return true;
            }

            break;
         case ' ':
         case '$':
         case '@':
         case '&':
         case '/':
         case '\'':
         case '\\':
         case '(':
         case ')':
         case '#':
         case '*':
         case '-':
         case '+':
         case '~':
         case '`':
         case '!':
         case '%':
         case '^':
         case '=':
         case '{':
         case '}':
         case '[':
         case ']':
         case '|':
         case ':':
         case ';':
         case '<':
         case '>':
         case ',':
         case '?':
         // ideographic comma http://www.unicodemap.org/details/0x3001/index.html
         case '\u3001':
            return true;
         }
      }

      // if first char is a number, return true
      return length > 0 && Character.isDigit(str.charAt(0));
   }

   public static final boolean shouldNotQuote(String str) {
      if(str.startsWith("$(") && str.endsWith(")")) {
         return true;
      }

      return false;
   }

   /**
    * Quote one name segment.
    * @param name the specified table/column name.
    */
   public static String quoteNameSegment(String name, KeywordProvider provider) {
      return quoteNameSegment(name, true, provider);
   }

   /**
    * Quote one name segment.
    * @param name the specified table/column name.
    */
   public static String quoteNameSegment(String name, boolean quoteKeyword,
                                         KeywordProvider provider)
   {
      return quoteNameSegment(name, quoteKeyword, provider, null);
   }

   /**
    * Quote one name segment.
    * @param name the specified table/column name.
    */
   public static String quoteNameSegment(String name, boolean quoteKeyword,
                                         KeywordProvider provider,
                                         Function<String, Boolean> checkSpecial)
   {
      provider = provider == null ? new SQLHelper() : provider;
      String quote = provider.getQuote();
      boolean isSpecial = isSpecialName(name, quoteKeyword, provider) ||
         checkSpecial != null && checkSpecial.apply(name);

      if(!isSpecial && name.indexOf('.') < 0) {
         return name;
      }

      if(name.startsWith(quote) && name.endsWith(quote) && name.length() > 1) {
         return name;
      }

      return isSpecial || name.indexOf('.') >= 0 ? quote + name + quote : name;
   }

   /**
    * Quote the name components if there is space.
    * @param name the specified table/column name.
    */
   public static String quoteName(String name, KeywordProvider provider) {
      return quoteName(name, true, provider);
   }

   /**
    * Quote the name components if there is space.
    * @param name the specified table/column name.
    * @param quoteKeyword true to quote if the name matches a sql keyword.
    */
   public static String quoteName(String name, boolean quoteKeyword,
                                  KeywordProvider provider) {
      provider = provider == null ? new SQLHelper() : provider;
      String quote = provider.getQuote();

      if(!isSpecialName(name, quoteKeyword, provider) && name.indexOf('.') < 0) {
         return name;
      }

      if(name.startsWith(quote) && name.endsWith(quote) && name.length() > 1) {
         return name;
      }

      String[] arr = Tool.splitWithQuote(name, ".", quote.charAt(0));
      StringBuilder str = new StringBuilder();

      for(int i = 0; i < arr.length; i++) {
         if(i > 0) {
            str.append(".");
         }

         if(isSpecialName(arr[i], quoteKeyword, provider)) {
            str.append(quote + arr[i] + quote);
         }
         else {
            str.append(arr[i]);
         }
      }

      return str.toString();
   }

   /**
    * Quote the alias if it contains special characters.
    */
   public static String quoteAlias(String alias, KeywordProvider provider) {
      provider = provider == null ? new SQLHelper() : provider;
      String quote = null;

      // don't quote embedded variable as the text in embedded variable should
      // be inserted to sql as is
      if(alias.startsWith("$(@")) {
         return alias;
      }

      if((isSpecial(alias, provider) || alias.indexOf('.') >= 0) &&
         !isSessionVariable(alias))
      {
         quote = provider.getQuote();
      }

      if(quote != null && alias.startsWith(quote) && alias.endsWith(quote) &&
         alias.length() > 1)
      {
         return alias;
      }

      return quote == null || " ".equals(quote) || "".equals(quote) ?
         alias : quote + alias + quote;
   }

   public static String getQuote(JDBCDataSource xds) {
      SQLHelper helper = SQLHelper.getSQLHelper(xds);
      return helper.getQuote();
   }

   /**
    * Check if the target name is a subquery.
    */
   public static boolean isSubQuery(String tableName) {
      if(tableName == null) {
         return false;
      }

      String table0 = tableName.toLowerCase();
      return table0.startsWith("select") &&
         (table0.indexOf(" from ") > -1 || table0.indexOf("\nfrom ") > -1);
   }

   /**
    * Check if the string is a session variable, _USER_, _ROLES_, _GROUPS_.
    */
   private static boolean isSessionVariable(String alias) {
      return "_USER_".equals(alias) || "_ROLES_".equals(alias) ||
             "_GROUPS_".equals(alias);
   }

   /**
    * Populate the choice list from the result of a query.
    */
   public static void setChoiceList(UserVariable uvar, XNode result) {
      if(result instanceof XTableNode) {
         XTableNode table = (XTableNode) result;
         List<Object> itemsV = new ArrayList<>();
         List<Object> valuesV = new ArrayList<>();

         while(table.next()) {
            int colCount = table.getColCount();

            for(int i = 0; i < 2 && i < colCount; i++) {
               if(i == 0) {
                  Object obj = table.getObject(i);
                  valuesV.add(obj);

                  if(colCount == 1) {
                     itemsV.add(obj);
                  }
               }
               else if(i == 1) {
                  itemsV.add(table.getObject(i));
               }
            }
         }

         uvar.setValues(valuesV.toArray(new Object[0]));
         uvar.setChoices(itemsV.toArray(new Object[0]));
      }
   }

   /**
    * Select a Date from the Date array.
    * @param filter The rules of selection. Its value can be SELECT_MAX(true)
    *               or SELECT_MIN.
    */
   public static Object selectDate(Object[] arr, boolean filter) {
      if(arr == null || arr.length == 0) {
         return null;
      }

      Date val = (Date) arr[0];

      for(int i = 1; i < arr.length; i++) {
         if(arr[i] == null) {
            continue;
         }

         if(filter && ((Date) arr[i]).getTime() > val.getTime() ||
            !filter && ((Date) arr[i]).getTime() < val.getTime())
         {
            val = (Date) arr[i];
         }
      }

      return val;
   }

   /**
    * Select a String from the String array.
    * @param filter The rules of selection. Its value can be SELECT_MAX(true)
    *               or SELECT_MIN.
    */
   public static Object selectString(Object[] arr, boolean filter) {
      if(arr == null || arr.length == 0) {
         return null;
      }

      String val = (String) arr[0];

      for(int i = 1; i < arr.length; i++) {
         String val2 = (String) arr[i];

         if(val2 == null) {
            continue;
         }

         int result = Tool.compare(val2, val);

         if(filter && result > 0 || !filter && result < 0) {
            val = val2;
         }
      }

      return val;
   }

   /**
    * Find variables defined in the string.
    */
   public static List<UserVariable> findVariables(String str) {
      List<UserVariable> vars = new ArrayList<>();

      if(str == null) {
         return vars;
      }

      // @by larryl, optimization
      char[] arr = str.toCharArray();

      for(int i = 0; i < arr.length; i++) {
         char ch = arr[i];
         char nch = (i < arr.length - 1) ? arr[i + 1] : ' ';
         // escape
         if(ch == '\\') {
            continue;
         }
         else if(ch == '$' && nch == '(') {
            int idx = str.indexOf(')', i + 2);

            if(idx > 0) {
               String name = str.substring(i + 2, idx).trim();

               // embed flag, stripped from name
               if(name.startsWith("@")) {
                  name = name.substring(1);
               }

               vars.add(new UserVariable(name));
               i = idx;
               continue;
            }
         }
      }

      return vars;
   }

   /**
    * Find attribute refs which are wrapped by "field['" and "']", or
    * "field('" and "')".
    *
    * @param expression the specified expression
    * @return an enumeration contains Attribute refs
    */
   public static Enumeration findAttributes(String expression) {
      ArrayList attrs = new ArrayList();

      String[] fieldFrontArray = {"field['", "field('"};
      String[] fieldBackArray = {"']", "')"};

      for(int i = 0; expression != null && i < fieldFrontArray.length; i++) {
         int startPos = expression.indexOf(fieldFrontArray[i]);
         int endPos = 0;

         StringBuilder retStr = new StringBuilder();

         while(startPos > -1) {
            retStr.append(expression.substring(0, startPos));
            endPos = expression.indexOf(fieldBackArray[i], startPos + 7);

            if(endPos > startPos) {
               String fname = expression.substring(startPos + 7, endPos).trim();
               int dot = fname.lastIndexOf('.');
               AttributeRef newAttributeRef;

               if(dot >= 0) {
                  newAttributeRef = new AttributeRef(fname.substring(0, dot),
                                                     fname.substring(dot + 1));
               }
               else {
                  newAttributeRef = new AttributeRef(null, fname);
               }

               if(!attrs.contains(newAttributeRef)) {
                  attrs.add(newAttributeRef);
               }

               retStr.append(expression.substring(startPos + 7, endPos));
               retStr.append(expression.substring(endPos + 2));
               expression = retStr.toString();
               retStr.delete(0, retStr.length() - 1);
               startPos = expression.indexOf(fieldFrontArray[i]);
            }
            else {
               break;
            }
         }
      }

      return new IteratorEnumeration(attrs.iterator());
   }

   /**
    * Parse date and return it in default formats.
    */
   public static String parseDate(String dateStr) {
      Pattern re_date;
      Pattern re_time;
      Pattern re_timestamp;
      Pattern re_time_old;
      Pattern re_timestamp_old;

      try {
         re_date = Pattern.compile(
            "\\{d\\s+\\'\\d{4}\\-\\d{2}\\-\\d{2}\\'\\}");
         re_time = Pattern.compile(
            "\\{t\\s+\\'\\d{2}\\:\\d{2}\\:\\d{2}\\.?\\d{0,3}\\'\\}");
         re_timestamp = Pattern.compile("\\{ts\\s+\\'\\d{4}" +
            "\\-\\d{2}\\-\\d{2}\\s+\\d{2}\\:\\d{2}\\:\\d{2}\\.?\\d{0,3}\\'\\}");
         re_time_old = Pattern.compile(
            "\\{d\\s+\\'\\d{2}\\:\\d{2}\\:\\d{2}\\.?\\d{0,3}\\'\\}");
         re_timestamp_old = Pattern.compile(
            "\\{d\\s+\\'\\d{4}\\-\\d{2}\\-\\d{2}\\s+" +
            "\\d{2}\\:\\d{2}\\:\\d{2}\\.?\\d{0,3}\\'\\}");

         if(re_date.matcher(dateStr).matches() ||
            re_time.matcher(dateStr).matches() ||
            re_timestamp.matcher(dateStr).matches())
         {
            return dateStr;
         }
         // handles backward compatibility
         else if(re_time_old.matcher(dateStr).matches()) {
            return ("{t " + dateStr.substring(3, dateStr.length()));
         }
         else if(re_timestamp_old.matcher(dateStr).matches()) {
            return ("{ts " + dateStr.substring(3, dateStr.length()));
         }
         else { // returns null if it is not a time format
            return null;
         }
      }
      catch(PatternSyntaxException e) {
         return null;
      }
   }

   public static String getUserName(Principal user) {
      IdentityID id = user instanceof XPrincipal ? ((XPrincipal) user).getIdentityID() : null;
      return id == null ? null : id.getName();
   }

   /**
    * Gets the roles for the user identified by the specified Principal.
    * @param user a Principal object that identifies the user.
    * @return an array of role names.
    */
   public static String[] getUserRoleNames(Principal user) {
      IdentityID[] ids = getUserRoles(user, false);
      return ids == null ? null : Arrays.stream(ids).map(id -> id.getName()).distinct().toArray(String[]::new);
   }

   /**
    * Gets the roles for the user identified by the specified Principal.
    * @param user a Principal object that identifies the user.
    * @return an array of role names.
    */
   public static IdentityID[] getUserRoles(Principal user) {
      return getUserRoles(user, false);
   }

   /**
    * Gets the roles for the user identified by the specified Principal.
    * @param user a Principal object that identifies the user.
    * @param includeOrg whether to include organization in group name.
    * @return an array of role names.
    */
   public static IdentityID[] getUserRoles(Principal user, boolean includeOrg) {
      if(user == null) {
         return new IdentityID[0];
      }

      IdentityID userID = IdentityID.getIdentityIDFromKey(user.getName());

      if(Identity.UNKNOWN_USER.equals(userID.name)) {
         if(user instanceof XPrincipal) {
            return ((XPrincipal) user).getRoles();
         }
         else {
            return new IdentityID[0];
         }
      }

      XIdentityFinder finder = getXIdentityFinder();
      IdentityID[] userRoles = finder.getUserRoles(user);

      if(!includeOrg) {
         IdentityID[] roles = new IdentityID[userRoles.length];
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

         if(provider != null) {
            for(int i = 0; i < userRoles.length; i++) {
               Identity identity =
                  provider.findIdentity(new DefaultIdentity(userRoles[i], Identity.ROLE));
               roles[i] = identity == null ? userRoles[i] : identity.getIdentityID();
            }

            userRoles = roles;
         }
      }

      return userRoles;
   }

   /**
    * Gets the groups for the user identified by the specified Principal.
    * @param user a Principal object that identifies the user.
    * @return an array of group names.
    */
   public static String[] getUserGroups(Principal user) {
      return getUserGroups(user, false);
   }

   /**
    * Gets the groups for the user identified by the specified Principal.
    * @param user a Principal object that identifies the user.
    * @param includeOrg whether to include organization in group name.
    * @return an array of group names.
    */
   public static String[] getUserGroups(Principal user, boolean includeOrg) {
      if(user == null) {
         return new String[0];
      }

      String name = user.getName();

      if(Identity.UNKNOWN_USER.equals(name)) {
         if(user instanceof XPrincipal) {
            return ((XPrincipal) user).getGroups();
         }
         else {
            return new String[0];
         }
      }

      XIdentityFinder finder = getXIdentityFinder();
      String[] userGroups = finder.getUserGroups(user);

      if(!includeOrg) {
         String[] groups = new String[userGroups.length];
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

         if(provider != null) {
            for(int i = 0; i < userGroups.length; i++) {
               Identity identity =
                  provider.findIdentity(new DefaultIdentity(userGroups[i], Identity.GROUP));
               groups[i] = identity == null ? userGroups[i] : identity.getName();
            }

            userGroups = groups;
         }
      }

      return userGroups;
   }

   public static AuthenticationProvider getSecurityProvider(String providerName) {
      SecurityEngine securityEngine = SecurityEngine.getSecurity();

      if(providerName != null && securityEngine.getAuthenticationChain().isPresent()) {
         return securityEngine.getAuthenticationChain().get().stream()
            .filter((p) -> (Catalog.getCatalog().getString(p.getProviderName()).equals(providerName)) || p.getProviderName().equals(providerName))
            .findAny()
            .orElseGet(securityEngine::getSecurityProvider);
      }

      return securityEngine.getSecurityProvider();
   }

   public static Comparator<String> getOrganizationComparator() {
      final List<String> builtinOrgs = Arrays.asList(
         Organization.getDefaultOrganizationID(),
         Organization.getSelfOrganizationID());
      Comparator<String> comp = (a, b) -> {
         int rc = a.compareTo(b);

         if(rc != 0) {
            int aIdx = builtinOrgs.indexOf(a);
            int bIdx = builtinOrgs.indexOf(b);

            if(aIdx >= 0 || bIdx >= 0) {
               aIdx = (aIdx < 0) ? 100 : aIdx;
               bIdx = (bIdx < 0) ? 100 : bIdx;
               return aIdx - bIdx;
            }
         }

         return rc;
      };

      return comp;
   }

   /**
    * Get all the users.
    * @return all the users.
    */
   public static IdentityID[] getUsers() {
      return getXIdentityFinder().getUsers();
   }

   /**
    * Add line numbering to the text.
    */
   public static String numbering(String txt) {
      StringBuilder buf = new StringBuilder();
      String[] lines = Tool.split(txt, '\n');

      for(int i = 0; i < lines.length; i++) {
         String numStr = "  " + (i + 1);

         buf.append(numStr.substring(numStr.length() - 3) + "   " + lines[i]);

         if(i < lines.length - 1) {
            buf.append("\n");
         }
      }

      return buf.toString();
   }

   /**
    * Get the query by its name.
    * @param qname the specified query name.
    * repository first.
    */
   public static XQuery getXQuery(String qname) {
      //Do not support query.
      return null;
   }

   /**
    * Check if a query is mergeable in query generator.
    * @param query the specified query.
    * @return true if the specified query is mergeable, false otherwise.
    */
   public static boolean isQueryMergeable(XQuery query) {
      // query is not a jdbc query
      if(!(query instanceof JDBCQuery)) {
         return false;
      }

      JDBCQuery jquery = (JDBCQuery) query;

      // sql is not a uniform sql
      if(!(jquery.getSQLDefinition() instanceof UniformSQL)) {
         return false;
      }

      UniformSQL sql = (UniformSQL) jquery.getSQLDefinition();

      // parse sql failed
      if(!XUtil.isParsedSQL(sql)) {
         return false;
      }

      if(sql.isLossy()) {
         return false;
      }

      JDBCDataSource ds = (JDBCDataSource) jquery.getDataSource();

      if(ds == null || ds.getDatabaseType() == JDBCDataSource.JDBC_HIVE &&
         !Drivers.getInstance().isHiveEnabled())
      {
         return false;
      }

      // queries on blacklist is not merged with binding info
      String nopushdown = SreeEnv.getProperty("jdbc.pushdown.blacklist", "");

      if(!nopushdown.isEmpty()) {
         if(nopushdown.equals("*")) {
            return false;
         }

         String[] names = Tool.split(nopushdown, ',');

         for(String name : names) {
            if(name.equals(jquery.getName())) {
               return false;
            }
         }
      }

      // if the sql string was explicitily defined embeded variable
      // we should not merge it since that variable will be removed
      // from uniform sql structure by the ui validation.
      if(sql.hasSQLString()) {
         String sqlstr = sql.getSQLString();

         try {
            Pattern p = Pattern.compile("\\$\\(\\@.+\\)");
            Matcher matcher = p.matcher(sqlstr);

            if(matcher.find()) {
               return false;
            }
         }
         catch(PatternSyntaxException exc) {
         }
      }

      Object[] grpby = sql.getGroupBy();

      // sql contains 'group by'
      if(grpby != null && grpby.length != 0) {
         return false;
      }

      // aggregate sql
      JDBCSelection sel = (JDBCSelection) sql.getSelection();
      int cnt = sel == null ? 0 : sel.getColumnCount();

      for(int i = 0; i < cnt; i++) {
         String path = sel.getColumn(i);

         if(sel.isAggregate(path)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if is an aggregate function.
    */
   public static boolean isAggregateFunction(String op) {
      if(op == null) {
         return false;
      }

      return op.equalsIgnoreCase("SUM") || op.equalsIgnoreCase("COUNT") ||
         op.equalsIgnoreCase("MAX") || op.equalsIgnoreCase("MIN") ||
         op.equalsIgnoreCase("AVG") || op.equalsIgnoreCase("AVERAGE");
   }

   /**
    * Get the table part of a column.
    */
   public static String getTablePart(String path) {
      return getTablePart(path, null);
   }

   /**
    * Get the table part of a column.
    */
   public static String getTablePart(String path, UniformSQL sql) {
      return getTablePart(path, sql, true, true);
   }

   /**
    * Get the table part of a column.
    */
   private static String getTablePart(String path, UniformSQL sql,
                                      boolean fuzzy, boolean quote) {
      int index = path.lastIndexOf('.');

      if(index < 0) {
         return null;
      }

      SelectTable[] tables = sql == null ? null : sql.getSelectTable();
      String tname = path.substring(0, index);

      if(quote && tname.length() > 2 && tname.charAt(0) == '\"' &&
         tname.charAt(tname.length() - 1) == '\"')
      {
         tname = tname.substring(1, tname.length() - 1);
      }

      if(tables == null) {
         return tname;
      }

      boolean found = false;

      for(int j = 0; j < tables.length; j++) {
         String table_alias = tables[j].getAlias();
         Object table_name = tables[j].getName();

         if(tname.equals(table_alias)) {
            found = true;
            break;
         }

         if(tname.equals(table_name)) {
            found = true;
            break;
         }
      }

      if(found) {
         return tname;
      }

      String tname2 = getTablePart(tname, sql, false, true);

      if(tname2 != null && tname2.length() > 0) {
         return tname2;
      }

      return fuzzy ? tname : null;
   }

   /**
    * Get the column part of a column.
    */
   public static String getColumnPart(String path) {
      return getColumnPart(path, null);
   }

   /**
    * Get the column part of a column.
    */
   public static String getColumnPart(String path, UniformSQL sql) {
      SelectTable[] tables = sql == null ? null : sql.getSelectTable();

      if(tables != null && tables.length > 0) {
         String table = getTablePart(path, sql, false, false);

         if(table != null) {
            String prefix = table + '.';

            if(path.startsWith(prefix)) {
               String cname = path.substring(prefix.length());

               if(cname.length() > 2 && cname.charAt(0) == '\"' &&
                  cname.charAt(cname.length() - 1) == '\"')
               {
                  cname = cname.substring(1, cname.length() - 1);
               }

               return cname;
            }
         }
      }

      int index = path.lastIndexOf('.');

      if(index < 0) {
         return path;
      }

      int length = path.length();

      // ends with dot
      if(index == length - 1) {
         if(index == 0) {
            return path;
         }

         index = path.lastIndexOf('.', index - 1);

         if(index < 0) {
            return path;
         }
      }

      String cname = path.substring(index + 1);

      if(cname.length() > 2 && cname.charAt(0) == '\"' &&
         cname.charAt(cname.length() - 1) == '\"')
      {
         cname = cname.substring(1, cname.length() - 1);
      }

      return cname;
   }

   /**
    * Check if a sql expression is valid.
    * @param exp the specified sql expression.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   public static boolean isSQLExpressionValid(String exp) {
      return isSQLExpressionValid(exp, null);
   }

   /**
    * Check if a sql expression is valid.
    * @param exp the specified sql expression.
    * @param comp the specified component to show error message.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   public static boolean isSQLExpressionValid(String exp, Component comp) {
      if(exp == null || exp.length() == 0) {
         return true;
      }

      SQLParser parser = null;

      try {
         SQLLexer lexer = new SQLLexer(new StringReader(exp));
         parser = new SQLParser(lexer);
         parser.value_exp();
      }
      catch(Exception ex) {
         LOG.debug("Failed to parse SQL expression: " + exp, ex);
         return false;
      }

      /*
       * @by larryl, we shouldn't require field[] in all expressions. For
       * example an expression to return the current time in db2 would be
       * "current timestamp" and is perfectly valid
      boolean result = parser == null ? true : !parser.hasField();
      Catalog catalog = Catalog.getCatalog();

      if(!result && comp != null) {
         GuiTool.showMessageDialog(comp,
            catalog.getString("common.invalidSQLExpression"),
            catalog.getString("Invalid Expression"),
            JOptionPane.WARNING_MESSAGE);
      }

      return result;
      */
      return true;
   }

   /**
    * Check if is a qualified table/column name in sql.
    * @param name the specified name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public static boolean isQualifiedName(final String name) {
      if(name == null) {
         return true;
      }

      boolean quoted = false;
      int lindex = -1;
      int length = name.length();

      for(int i = 0; i < length; i++) {
         char c = name.charAt(i);

         // quote?
         if(c == '"') {
            if(!quoted) {
               if(i > 0) {
                  char c2 = name.charAt(i - 1);

                  if(c2 != '.') {
                     return false;
                  }
               }

               lindex = i;
            }
            else {
               if(i < name.length() - 1) {
                  char c2 = name.charAt(i + 1);

                  if(c2 != '.') {
                     return false;
                  }
               }

               if(lindex == i - 1) {
                  return false;
               }
            }

            quoted = !quoted;
            continue;
         }
         // in quote?
         else if(quoted) {
            continue;
         }
         // blank?
         else if(c == ' ') {
            if(i == 0) {
               return false;
            }

            if(i == name.length() - 1) {
               return false;
            }

            if(i > 0) {
               char c2 = name.charAt(i - 1);

               if(c2 == '.') {
                  return false;
               }
            }

            if(i < name.length() - 1) {
               char c2 = name.charAt(i + 1);

               if(c2 == '.') {
                  return false;
               }
            }

            continue;
         }
         // dot?
         else if(c == '.') {
            if(i == 0) {
               return false;
            }

            if(i == name.length() - 1) {
               return false;
            }

            if(i > 0) {
               char c2 = name.charAt(i - 1);

               if(c2 == '.') {
                  return false;
               }
            }

            if(i < name.length() - 1) {
               char c2 = name.charAt(i + 1);

               if(c2 == '.') {
                  return false;
               }
            }

            continue;
         }
         // digit
         else if(Character.isDigit(c)) {
            if(i == 0) {
               return false;
            }

            if(i > 0) {
               char c2 = name.charAt(i - 1);

               if(c2 == '.') {
                  return false;
               }
            }

            continue;
         }
         // letter/digit?
         else if(Character.isLetterOrDigit(c)) {
            continue;
         }
         // special char? pay attention to sum(col)
         else if(c == '_' || c =='$' || c == '@' || c == '&' || c == '~' ||
            c == '#' || c == '{' || c == '}' || c == '[' || c == ']' ||
            c == ':' || c == '(' || (c == ')' && i != length - 1) || c == '-')
         {
            continue;
         }
         else {
            return false;
         }
      }

      // unfinished?
      if(quoted) {
         return false;
      }

      // blank?
      if(name.length() == 0) {
         return false;
      }

      return true;
   }

   /**
    * Get an xtable header at a col index. If the header is not available,
    * "Column [col]" like "Column[3]" will be returned.
    * @param table the specified xtable.
    * @param c the specified col.
    * @return xtable header at the specified col index.
    */
   public static Object getHeader(XTable table, int c) {
      Object header = table.moreRows(0) && table.getHeaderRowCount() > 0 ?
         table.getObject(0, c) : null;

      return header == null || header.equals("") || header.toString().trim().isEmpty() ?
         getDefaultColumnName(c) : header;
   }

   /**
    * Get the default column name for a given index.
    *
    * @param idx the column index.
    *
    * @return the default column name.
    */
   public static String getDefaultColumnName(int idx) {
      return "Column [" + idx + "]";
   }

   /**
    * Get the logical models.
    * @param source the specified data source.
    * @return the logical models.
    */
   public static String[] getLogicalModels(String source) throws Exception {
      XRepository repository = XFactory.getRepository();
      XDataModel model = repository.getDataModel(source);
      List list = new ArrayList();

      if(model != null) {
         for(String name : model.getLogicalModelNames()) {
            XLogicalModel lmodel = model.getLogicalModel(name);

            if(lmodel != null) {
               list.add(name);
            }
         }
      }

      String[] arr = new String[list.size()];
      list.toArray(arr);
      return arr;
   }

   /**
    * Get the entities.
    * @param source the specified data source.
    * @param lname the specified logical model.
    * @param user the specified user.
    * @return the entities.
    */
   public static String[] getEntities(String source, String lname,
                                      Principal user, boolean hideAttributes)
                                      throws Exception {
      XRepository repository = XFactory.getRepository();
      XDataModel model = repository.getDataModel(source);
      XLogicalModel lmodel = model == null ?
         null : model.getLogicalModel(lname, user, hideAttributes);

      if(lmodel == null) {
         return new String[0];
      }

      Enumeration entities = lmodel.getEntities();
      List list = new ArrayList();

      while(entities.hasMoreElements()) {
         XEntity entity = (XEntity) entities.nextElement();
         list.add(entity.getName());
      }

      String[] arr = new String[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Get the attributes.
    * @param source the specified data source.
    * @param lname the specified logical model.
    * @param ename the specified entity.
    * @param user the specified user.
    * @param vpm true to apply hidden column in vpm.
    * @return the attributes.
    */
   public static XAttribute[] getAttributes(String source, String lname,
                                            String ename, Principal user,
                   boolean hideAttributes,
                   boolean vpm)
      throws Exception
   {
      return getAttributes(source, lname, ename, user, hideAttributes,
                           vpm, null);
   }

   /**
    * Get the attributes.
    * @param source the specified data source.
    * @param lname the specified logical model.
    * @param ename the specified entity.
    * @param user the specified user.
    * @return the attributes.
    */
   public static XAttribute[] getAttributes(String source, String lname,
                                            String ename, Principal user,
                                            boolean hideAttributes,
                                            boolean vpm, VariableTable vars)
      throws Exception
   {
      XRepository repository = XFactory.getRepository();
      XDataModel model = repository.getDataModel(source);

      if(model == null) {
         return new XAttribute[0];
      }

      XLogicalModel lmodel = model.getLogicalModel(lname, user, hideAttributes);

      if(lmodel == null) {
         return new XAttribute[0];
      }

      XPartition partition = model.getPartition(lmodel.getPartition(), user);

      if(partition == null) {
         return new XAttribute[0];
      }

      // suppose that every entity and every attribute will be included
      // in the generated query, which is more like a query does
      String[] tables = getTables(lmodel, partition);
      String[] cols = getColumns(lmodel, partition);
      BiFunction<String, String, Boolean> hcolumns = null;
      List<XEntity> entities = new ArrayList<>();

      if(vpm) {
         hcolumns = VpmProcessor.getInstance().getHiddenColumnsSelector(
            tables, cols, source, partition.getName(), vars, user);
      }

      if(ename != null) {
         XEntity entity = lmodel.getEntity(ename);

         if(entity != null) {
            entities.add(entity);
         }
      }
      else {
         Enumeration<XEntity> enumeration = lmodel.getEntities();

         while(enumeration.hasMoreElements()) {
            entities.add(enumeration.nextElement());
         }
      }

      List<XAttribute> list = new ArrayList<>();

      for(int i = 0; i < entities.size(); i++) {
         XEntity entity = entities.get(i);
         Enumeration<XAttribute> attributes = entity.getAttributes();

         while(attributes.hasMoreElements()) {
            XAttribute attribute = attributes.nextElement();
            attribute.setEntity(entity.getName());
            String[] col; // alias table column has been fix to db table column
            String[] aliasCols = attribute.getColumns(); // original alias table column

            if(attribute instanceof ExpressionAttribute) {
               ExpressionAttribute exp = (ExpressionAttribute) attribute;

               if(exp.isParseable()) {
                  col = XUtil.getPhysicalColumns(partition, attribute);
               }
               else {
                  col = new String[] {};
               }
            }
            else {
               col = XUtil.getPhysicalColumns(partition, attribute);
            }

            boolean visible = true;

            for(int j = 0; j < col.length; j++) {
               // if col of database table or alias table has been hidden
               if(hcolumns != null && (hcolumns.apply(null, col[j])
                  || j < aliasCols.length && hcolumns.apply(null, aliasCols[j])))
               {
                  visible = false;
                  break;
               }
            }

            if(visible) {
               list.add(attribute);
            }
         }
      }

      XAttribute[] arr = new XAttribute[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Get the compiled representation of a regular expression.
    *
    * @param expr the filter expression. This parameter is ignored if both
    *             <i>basic</i> and <i>regular</i> are <code>false</code>.
    * @param basic <code>true</code> if the expression is a basic expression.
    * @param regular <code>true</code> if the expression is a regular
    *                expression.
    *
    * @return the compiled representation of a regular expression.
    */
   public static Pattern getPattern(String expr, boolean basic, boolean regular)
   {
      Pattern re = null;

      if(basic) {
         if(expr != null) {
            StringBuilder buf = new StringBuilder().append(expr);
            int pos = 0;

            // escape all metacharacters except '*'
            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('\\', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\\\");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('.', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\.");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('?', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\?");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('+', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\+");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('[', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\[");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf(']', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\]");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('(', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\(");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf(')', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\)");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('{', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\{");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('}', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\}");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('$', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\$");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('^', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\^");
               pos += 2;
            }

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('|', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, "\\|");
               pos += 2;
            }

            // replace '*' with '.*'

            pos = 0;

            while(pos < buf.length() &&
               (pos = buf.toString().indexOf('*', pos)) >= 0)
            {
               buf.replace(pos, pos + 1, ".*");
               pos += 2;
            }

            String str = buf.toString();

            // allow matching any part of the name
            if(!str.startsWith("^")) {
               str = ".*" + str;
            }

            if(!str.endsWith("$")) {
               str = str + ".*";
            }

            try {
               re = Pattern.compile(str, Pattern.CASE_INSENSITIVE);
            }
            catch(Exception exc) {
               throw new IllegalArgumentException(
                  Catalog.getCatalog().getString("Invalid filter expression"));
            }
         }
      }
      else if(regular) {
         if(expr != null) {
            try {
               re = Pattern.compile(expr, Pattern.CASE_INSENSITIVE);
            }
            catch(Exception exc) {
               throw new IllegalArgumentException(
                  Catalog.getCatalog().getString("Invalid filter expression"));
            }
         }
      }

      return re;
   }

   /**
    * Remove the useless tables.
    */
   public static void removeTable(XQuery query, UniformSQL usql,
                                  VariableTable params) {
      if(usql == null || usql.hasSQLString()) {
         return;
      }

      // remove useless join table if remove.useless.joinTable is true
      if(!"true".equals(SreeEnv.getProperty("remove.useless.joinTable"))) {
         return;
      }

      XDataSource ds = query.getDataSource();
      SQLHelper helper = SQLHelper.getSQLHelper(ds);
      removeTable(query, usql, params, helper);
   }

   /**
    * Remove the useless tables.
    */
   private static void removeTable(XQuery query, UniformSQL usql,
                                   VariableTable params, SQLHelper helper) {
      SelectTable[] tables = usql.getSelectTable();

      // handle sub queries recursively
      for(int i = 0; i < tables.length; i++) {
         Object table = tables[i].getName();

         if(table instanceof UniformSQL) {
            removeTable(query, (UniformSQL) table, params, helper);
         }
      }

      // only one table? do nothing
      if(tables.length <= 1) {
         return;
      }

      boolean removed;

      do {
         tables = usql.getSelectTable();
         removed = false;

         for(int i = tables.length - 1; i >= 0; i--) {
            SelectTable stable = tables[i];
            Object tname = stable.getName();
            String talias = stable.getAlias();

            // correct table alias
            if(talias == null || talias.length() == 0) {
               talias = tname.toString();
            }

            // if table is not used
            if(!isTableUsed(usql, tname, talias, helper)) {
               removed = removeTable0(usql, tname, talias, helper) || removed;
            }
         }
      }
      while(removed);
   }

   /**
    * Check if table is used in sql.
    */
   private static boolean isTableUsed(UniformSQL sql, Object tname,
                                      String talias, SQLHelper helper) {
      // 1. used in selection?
      XSelection select = sql.getSelection();

      for(int i = 0; i < select.getColumnCount(); i++) {
         String col = select.getColumn(i);

         if(isTableUsed(col, talias, tname, helper, true)) {
            return true;
         }
      }

      // 2. used in where?
      XFilterNode filter = sql.getWhere();

      if(isTableUsedInWhere(filter, tname, talias, helper)) {
         return true;
      }

      // 3. used in group by?
      Object[] groups = sql.getGroupBy();

      for(int i = 0; groups != null && i < groups.length; i++) {
         String group = groups[i] + "";

         if(isTableUsed(group, talias, tname, helper, true)) {
            return true;
         }
      }

      // 4. used in having?
      filter = sql.getHaving();

      if(isTableUsedInWhere(filter, tname, talias, helper)) {
         return true;
      }

      // 5. used in order by?
      Object[] orders = sql.getOrderByFields();

      for(int i = 0; orders != null && i < orders.length; i++) {
         String order = orders[i] + "";

         if(isTableUsed(order, talias, tname, helper, true)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if table is used in the specified column.
    */
   private static boolean isTableUsed(String col, String talias, Object tname,
                                      SQLHelper helper, boolean prefix) {
      if(col == null || col.length() == 0) {
         return false;
      }

      if(prefix && col.indexOf(talias + ".") >= 0) {
         return true;
      }
      else if(!prefix && col.equals(talias)) {
         return true;
      }

      if(prefix && tname instanceof String && col.indexOf(tname + ".") >= 0) {
         return true;
      }
      else if(!prefix && tname instanceof String && col.equals(tname)) {
         return true;
      }

      talias = quoteAlias(talias, helper);

      if(prefix && col.indexOf(talias + ".") >= 0) {
         return true;
      }
      else if(!prefix && col.equals(talias)) {
         return true;
      }

      if(tname instanceof String) {
         tname = quoteName((String) tname, helper);

         if(prefix && tname instanceof String && col.indexOf(tname + ".") >= 0) {
            return true;
         }
         else if(!prefix && tname instanceof String && col.equals(tname)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if table is used in the specified filter.
    */
   private static boolean isTableUsedInWhere(XFilterNode filter, Object tname,
                                             String talias, SQLHelper helper) {
      if(filter == null) {
         return false;
      }

      if(filter instanceof XSet) {
         XSet set = (XSet) filter;

         for(int i = 0; i < set.getChildCount(); i++) {
            XFilterNode node = (XFilterNode) set.getChild(i);

            if(isTableUsedInWhere(node, tname, talias, helper)) {
               return true;
            }
         }
      }
      // join? do not consider it
      else if(filter instanceof XJoin) {
         return false;
      }
      else if(filter instanceof XUnaryCondition) {
         XUnaryCondition cond = (XUnaryCondition) filter;
         XExpression exp = cond.getExpression1();
         String col = exp == null ? "" : exp.getValue() + "";

         if(isTableUsed(col, talias, tname, helper, true)) {
            return true;
         }
      }
      else if(filter instanceof XBinaryCondition) {
         XBinaryCondition bcond = (XBinaryCondition) filter;
         XExpression exp = bcond.getExpression1();
         String col = exp == null ? "" : exp.getValue() + "";

         if(isTableUsed(col, talias, tname, helper, true)) {
            return true;
         }

         exp = bcond.getExpression2();
         col = exp == null ? "" : exp.getValue() + "";

         if(isTableUsed(col, talias, tname, helper, true)) {
            return true;
         }
      }
      else if(filter instanceof XTrinaryCondition) {
         XTrinaryCondition cond = (XTrinaryCondition) filter;
         XExpression exp = cond.getExpression1();
         String col = exp == null ? "" : exp.getValue() + "";

         if(isTableUsed(col, talias, tname, helper, true)) {
            return true;
         }

         exp = cond.getExpression2();
         col = exp == null ? "" : exp.getValue() + "";

         if(isTableUsed(col, talias, tname, helper, true)) {
            return true;
         }

         exp = cond.getExpression3();
         col = exp == null ? "" : exp.getValue() + "";

         if(isTableUsed(col, talias, tname, helper, true)) {
            return true;
         }
      }
      else if(filter instanceof XExpressionCondition) {
         XExpressionCondition cond = (XExpressionCondition) filter;
         XExpression exp = cond.getExpression();
         String col = exp == null ? "" : exp.getValue() + "";

         if(isTableUsed(col, talias, tname, helper, true)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Remove table internally.
    */
   private static boolean removeTable0(UniformSQL sql, Object tname,
                                       String talias, SQLHelper helper) {
      XFilterNode filter = sql.getWhere();
      List pairs = getJoins(filter, sql, tname, talias, helper,
                            new ArrayList());

      // if the table joins with two tables, we take is as a bridge
      if(pairs.size() >= 2) {
         return false;
      }
      else if(pairs.size() == 1) {
         XJoin join = (XJoin) pairs.get(0);
         Collection joins = sql.getOriginalJoins();

         // do not remove original joins
         if(joins != null && joins.contains(join)) {
            return false;
         }

         String table = join.getTable1(sql);
         boolean leftTable = isTableUsed(table, talias, tname, helper, false);
         String op = join.getOp();
         boolean outer = leftTable ? op != null && op.endsWith("=*") :
            op != null && op.startsWith("*=");

         // do not remove non-outer join if remove outer join only
         if(!outer && "true".equals(
            SreeEnv.getProperty("remove.outerJoin.only")))
         {
            return false;
         }
      }

      // remove it from tables
      sql.removeTable(talias);

      // remove it from joins
      removeTableInWhere(filter, sql, tname, talias, helper);
      return true;
   }

   /**
    * Get the join pairs of the table to be removed.
    */
   private static List getJoins(XFilterNode filter, UniformSQL sql,
                                Object tname, String talias,
                                SQLHelper helper, List pairs) {
      if(!(filter instanceof XSet)) {
         if(filter instanceof XJoin) {
            XJoin join = (XJoin) filter;
            String table = join.getTable1(sql);
            boolean found = isTableUsed(table, talias, tname, helper, false);

            if(found) {
               String pair = join.getTable2(sql);

               if(pair != null && pair.length() > 0 && !pairs.contains(join)) {
                  pairs.add(join);
               }
            }

            if(!found) {
               table = join.getTable2(sql);
               found = isTableUsed(table, talias, tname, helper, false);

               if(found) {
                  String pair = join.getTable1(sql);

                  if(pair != null && pair.length() > 0 && !pairs.contains(join))
                  {
                     pairs.add(join);
                  }
               }
            }
         }

         return pairs;
      }

      XSet set = (XSet) filter;

      for(int i = set.getChildCount() - 1; i >= 0; i--) {
         XFilterNode node = (XFilterNode) set.getChild(i);

         if(node instanceof XSet) {
            getJoins(node, sql, tname, talias, helper, pairs);
         }
         else if(node instanceof XJoin) {
            getJoins(node, sql, tname, talias, helper, pairs);
         }
      }

      return pairs;
   }

   /**
    * Check if table is used in the specified filter.
    */
   private static void removeTableInWhere(XFilterNode filter, UniformSQL sql,
                                          Object tname, String talias,
                                          SQLHelper helper) {
      if(!(filter instanceof XSet)) {
         if(filter instanceof XJoin) {
            XJoin join = (XJoin) filter;
            String table = join.getTable1(sql);
            boolean found = isTableUsed(table, talias, tname, helper, false);

            if(!found) {
               table = join.getTable2(sql);
               found = isTableUsed(table, talias, tname, helper, false);
            }

            if(found) {
               sql.setWhere(null);
            }
         }

         return;
      }

      XSet set = (XSet) filter;

      for(int i = set.getChildCount() - 1; i >= 0; i--) {
         XFilterNode node = (XFilterNode) set.getChild(i);

         if(node instanceof XSet) {
            removeTableInWhere(node, sql, tname, talias, helper);
         }
         else if(node instanceof XJoin) {
            XJoin join = (XJoin) node;
            String table = join.getTable1(sql);
            boolean found = isTableUsed(table, talias, tname, helper, false);

            if(!found) {
               table = join.getTable2(sql);
               found = isTableUsed(table, talias, tname, helper, false);
            }

            if(found) {
               set.removeChild(i);
            }
         }
      }
   }

   /**
    * Set condition to a true condition if one of parameters in it has no value.
    * @deprecated should use the next method.
    */
   @Deprecated
   public static boolean validateConditions(XQuery query, UniformSQL usql, VariableTable params) {
      return validateConditions(query, usql, params, true);
   }

   /**
    * Set condition to a true condition if one of parameters in it has no value.
    */
   public static boolean validateConditions(XQuery query, UniformSQL usql, VariableTable params,
                                            boolean include)
   {
      return validateConditions(query, usql, params, include, false);
   }
   /**
    * Set condition to a true condition if one of parameters in it has no value.
    * @forVpm true if this validation is for vpm conditions, else false.
    */
   public static boolean validateConditions(XQuery query, UniformSQL usql,
                                            VariableTable params, boolean include,
                                            boolean forVpm)
   {
      if(usql.hasSQLString()) {
         return false;
      }

      boolean changed = false;
      SelectTable[] tables = usql.getSelectTable();

      for(int i = 0; i < tables.length; i++) {
         Object table = tables[i].getName();

         if(table instanceof UniformSQL) {
            changed = validateConditions(query, (UniformSQL) table, params, include, forVpm) ||
               changed;
         }
      }

      XFilterNode condition = usql.getWhere();

      if(condition instanceof XBinaryCondition || condition instanceof XSet) {
         processSpecificCondition(usql, condition, params, false);
      }

      // don't remove null paramter for vpm conditions, then variables in vpm condition
      // will not to be removed.
      if(!forVpm) {
         ChangedInfo info = removeNoParamConditions(query, condition, params, include);
         changed = info.changed || changed;

         if(info.empty) {
            usql.setWhere(null);
         }
         else if(condition instanceof XSet && condition.getChildCount() == 1) {
            changed = true;
         }
      }

      condition = usql.getHaving();

      if(condition instanceof XBinaryCondition || condition instanceof XSet) {
         processSpecificCondition(usql, condition, params, true);
      }

      if(!forVpm) {
         ChangedInfo info = removeNoParamConditions(query, condition, params, include);
         changed = info.changed || changed;

         if(info.empty) {
            usql.setHaving(null);
         }
         else if(condition instanceof XSet && condition.getChildCount() == 1) {
            usql.setHaving((XFilterNode) condition.getChild(0));
            changed = true;
         }
      }

      if(changed) {
         usql.clearCachedString();
      }

      return changed;
   }

   /**
    * Check if condition has param with special value
    * 'NULL_VALUE' or 'EMPTY_STRING'.
    * @param usql use to set the where condition.
    * @param condition then sql condition.
    * @param params, variable parameter which is user entered, if has a
    * special value, then process it.
    */
   private static void processSpecificCondition(UniformSQL usql,
      XFilterNode condition, VariableTable params, Boolean isHaving)
   {
      if(condition instanceof XBinaryCondition) {
         XBinaryCondition filterNode;
         XBinaryCondition bin = (XBinaryCondition) condition;
         String op = bin.getOp();
         String value = bin.getExpression2().toString().trim();

         if(value.startsWith("$(")) {
            value = value.substring(2, value.lastIndexOf(')'));

            try{
               Object val = params.get(value);

               if(Tool.equals(val, (XConstants.CONDITION_NULL_VALUE))) {
                  filterNode = new XBinaryCondition(bin.getExpression1(),
                     new XExpression("IS NULL", XExpression.VALUE), "");

                  if(isHaving) {
                     usql.setHaving(filterNode);
                  }
                  else {
                     usql.setWhere(filterNode);
                  }
               }
               else if(Tool.equals(val, (XConstants.CONDITION_EMPTY_STRING))) {
                  filterNode = new XBinaryCondition(bin.getExpression1(),
                     new XExpression("''", XExpression.VALUE), op);

                  if(isHaving) {
                     usql.setHaving(filterNode);
                  }
                  else {
                     usql.setWhere(filterNode);
                  }
               }
               else if(Tool.equals(val, (XConstants.CONDITION_NULL_STRING))) {
                  filterNode = new XBinaryCondition(bin.getExpression1(),
                     new XExpression("'null'", XExpression.VALUE), op);

                  if(isHaving) {
                     usql.setHaving(filterNode);
                  }
                  else {
                     usql.setWhere(filterNode);
                  }
               }
            }
            catch(Exception e) {
            }
         }
      }
      else if(condition instanceof XSet && condition.getChildCount() > 0) {
          XSet set = (XSet) condition;

          for(int i = 0; i < set.getChildCount(); i++) {
            XFilterNode node = (XFilterNode) set.getChild(i);
            processSpecificCondition(usql, node, params, isHaving);
         }
      }
   }

   /**
    * Check if condition has param without value;
    * @param include, when set to true, will return true
    * even if the param was not defined in params, otherwise will
    * ony return true when a param was defined and set to null
    * in params.
    * @return true if no condition left.
    */
   public static ChangedInfo removeNoParamConditions(XQuery query, XFilterNode condition,
                                                     VariableTable params, boolean include) {
      ChangedInfo info = new ChangedInfo();

      if(condition == null) {
         return info;
      }

      if(condition instanceof XSet) {
         XSet set = (XSet) condition;

         for(int i = 0; i < set.getChildCount(); i++) {
            XFilterNode node = (XFilterNode) set.getChild(i);
            ChangedInfo sinfo = removeNoParamConditions(query, node, params, include);
            info.changed = sinfo.changed || info.changed;

            if(sinfo.empty) {
               set.removeChild(i);
               info.changed = true;
               i--;
            }
         }

         // if the parent set has only one child, move the single child of this
         // set to the parent so we don't have unnecessary set (parens) when
         // joins are removed
         if(set.getChildCount() == 1) {
            XSet parent = (XSet) set.getParent();

            if(parent != null) {
               // @by larryl, don't use getChildIndex() which uses equals() to
               // compare. By comparing instances we are assured it would not
               // find the wrong child.
               for(int i = 0; i < parent.getChildCount(); i++) {
                  if(parent.getChild(i) == set) {
                     final XNode child = set.getChild(0);
                     final String relation = set.getRelation();

                     // if the child is an XSet then only pull up if the merging rule is the same
                     if(!(child instanceof XSet) ||
                        Objects.equals(relation, ((XSet) child).getRelation()))
                     {
                        parent.setChild(i, child);
                        info.changed = true;
                        break;
                     }
                  }
               }
            }
         }
         else if(set.getChildCount() == 0) {
            info.empty = true;
            return info;
         }

         return info;
      }
      else if(condition instanceof XBinaryCondition) {
         XBinaryCondition bin = (XBinaryCondition) condition;
         String op = bin.getOp();

         if(isNullParam(query, op, bin.getExpression1(), params, include) ||
            isNullParam(query, op, bin.getExpression2(), params, include))
         {
            info.empty = !hasSubQueryCondition(bin);
            info.changed = true;
            return info;
         }
      }
      else if(condition instanceof XUnaryCondition) {
         XUnaryCondition una = (XUnaryCondition) condition;
         String op = una.getOp();

         if(isNullParam(query, op, una.getExpression1(), params, include)) {
            info.empty = !hasSubQueryCondition(una);
            info.changed = true;
            return info;
         }
      }
      else if(condition instanceof XTrinaryCondition) {
         XTrinaryCondition tri = (XTrinaryCondition) condition;
         String op = tri.getOp();

         if(isNullParam(query, op, tri.getExpression1(), params, include) ||
            isNullParam(query, op, tri.getExpression2(), params, include) ||
            isNullParam(query, op, tri.getExpression3(), params, include))
         {
            info.empty = !hasSubQueryCondition(tri);
            info.changed = true;
            return info;
         }
      }

      return info;
   }

   /**
    * Check if all params has value.
    * @param includeNotExisted, when set to true, will return true
    * even if the parm was not defined in params, otherwise will
    * ony return true when a parm was defined and set to null
    * in params.
    */
   private static boolean isNullParam(XQuery query, String op,
                                      XExpression exp, VariableTable params,
                                      boolean includeNotExisted) {
      Object val = exp.getValue();

      if(val instanceof UniformSQL) {
         return validateConditions(query, (UniformSQL) val, params, includeNotExisted);
      }
      else if(isNullParam(query, op, exp.toString(), params, includeNotExisted)) {
         return true;
      }

      return false;
   }

   private static boolean hasSubQueryCondition(XFilterNode node) {
      if(node instanceof XUnaryCondition) {
         XUnaryCondition cond = (XUnaryCondition) node;
         return hasSubQueryCondition(cond.getExpression1());
      }
      else if(node instanceof XBinaryCondition) {
         XBinaryCondition cond = (XBinaryCondition) node;
         return hasSubQueryCondition(cond.getExpression1()) ||
            hasSubQueryCondition(cond.getExpression2());
      }
      else if(node instanceof XTrinaryCondition) {
         XTrinaryCondition cond = (XTrinaryCondition) node;
         return hasSubQueryCondition(cond.getExpression1()) ||
            hasSubQueryCondition(cond.getExpression2()) ||
            hasSubQueryCondition(cond.getExpression3());
      }

      return false;
   }

   private static boolean hasSubQueryCondition(XExpression expression) {
      if(expression.getValue() instanceof UniformSQL) {
         return hasCondition((UniformSQL) expression.getValue());
      }

      return false;
   }

   private static boolean hasCondition(UniformSQL uniformSQL) {
      XFilterNode node = uniformSQL.getWhere();

      if(node != null && node.getChildCount() != 0) {
         return true;
      }

      for(int i = 0; i < uniformSQL.getTableCount(); i++) {
         SelectTable table = uniformSQL.getSelectTable(i);
         Object name = table.getName();

         if(name instanceof UniformSQL && hasCondition((UniformSQL) name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if all params has value.
    * @param includeNotExisted, when set to true, will return true
    * even if the parm was not defined in params, otherwise will
    * ony return true when a parm was defined and set to null
    * in params.
    */
   private static boolean isNullParam(XQuery query, String op,
                                      String exp, VariableTable params,
                                      boolean includeNotExisted) {
      for(int i = 0; i < exp.length(); i++) {
         char ch = exp.charAt(i);
         char nch = (i < exp.length() - 1) ? exp.charAt(i + 1) : ' ';

         // escape
         if(ch == '\\') {
            i++;
            continue;
         }
         else if(ch == '$' && nch == '(') {
            int idx = exp.indexOf(')', i + 2);

            if(idx > 0) {
               String name = exp.substring(i + 2, idx).trim();

               // embed flag, stripped from name
               if(name.startsWith("@")) {
                  name = name.substring(1);
               }

               try {
                  // @by jasons, check if the parameter is query variable. If
                  // it is, don't remove it, it will be populated later.
                  XVariable var = query != null ? query.getVariable(name) :
                     null;

                  //if include not existed, always check val
                  //else only check val when contains name
                  boolean exists = includeNotExisted ? true :
                     params.contains(name);

                  Object val = params.get(name);

                  if(exists && (val == null || (val instanceof Object[] && ((Object[]) val).length == 0)) &&
                     !(var instanceof QueryVariable))
                  {
                     return true;
                  }

                  if(val instanceof UserVariable) {
                     UserVariable uvar = (UserVariable) val;
                     XValueNode vnode = uvar.getValueNode();

                     if(vnode == null || vnode.getValue() == null) {
                        return true;
                     }
                  }

                  // @by billh, for in, an empty array means null
                  if("in".equalsIgnoreCase(op)) {
                     if(val != null && val.getClass().isArray()) {
                        if(Array.getLength(val) == 0) {
                           return true;
                        }
                     }
                  }
               }
               catch(Exception e) {
                  return true;
               }

               i = idx;
               continue;
            }
         }
      }

      return false;
   }

   /**
    * Convert an object to SQL type.
    */
   public static Object toSQLValue(Object val, int sqltype) {
      if(val == null) {
         return null;
      }

      // @by larryl, make sure TIME type is passed as Time otherwise
      // DB2 will throw datetime value overflow
      if(val instanceof java.util.Date && sqltype == Types.TIME) {
         return new Time(((java.util.Date) val).getTime());
      }
      // java.sql.Date would not work for DateTime sql type
      else if(!(val.getClass().getName().startsWith("java.sql")) &&
          val instanceof java.util.Date) {
         return new java.sql.Timestamp(((java.util.Date) val).getTime());
      }
      else if(val instanceof XValueNode) {
         return ((XValueNode) val).toSQLValue();
      }
      else if(val instanceof XSequenceNode) {
         XSequenceNode snode = (XSequenceNode) val;
         Object ret = snode.getValue();

         if(ret != null) {
            return ret;
         }

         if(snode.getChildCount() == 1) {
            return snode.getChild(0).getValue();
         }

         Object[] arr = new Object[snode.getChildCount()];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = snode.getChild(i).getValue();
         }

         return arr;
      }

      return val;
   }

   /**
    * Print an XML DOM node to the standard error stream.
    *
    * @param node the node to print.
    */
   public static void printDOMNode(Node node) {
      try {
         TransformerFactory factory = TransformerFactory.newInstance();

         try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
         }
         catch(IllegalArgumentException e) {
            LOG.debug("Transformer attribute {} not supported", XMLConstants.ACCESS_EXTERNAL_DTD);
         }

         try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
         }
         catch(IllegalArgumentException e) {
            LOG.debug(
               "Transformer attribute {} not supported", XMLConstants.ACCESS_EXTERNAL_STYLESHEET);
         }

         Transformer transformer = factory.newTransformer();
         transformer.setOutputProperty(OutputKeys.INDENT, "yes");
         transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

         DOMSource source = new DOMSource(node);
         StreamResult result = new StreamResult(System.err);

         transformer.transform(source, result);
      }
      catch(Exception e) {
         LOG.debug("Failed to print DOM node", e);
      }
   }

   /**
    * Print an xnode.
    */
   public static void printXNode(XNode node) {
      if(node == null) {
         System.err.print("null");
         return;
      }

      printXNode(node, 0, false);
   }

   /**
    * Print an xnode with the specified level.
    */
   public static void printXNode(XNode node, int level, boolean attr) {
      printXNode(node, level, attr, System.err);
   }

   public static void printXNode(XNode node, int level, boolean attr, PrintStream stream) {
      for(int i = 0; i < level; i++) {
         stream.print("\t");
      }

      if(attr) {
         stream.print("attribute: ");
      }

      Object nval = node.getValue();

      if(node instanceof XFilterNode) {
         nval = node.toString();
      }

      String cls = node.getClass().getName();
      int index = cls.lastIndexOf('.');

      if(index >= 0) {
         cls = cls.substring(index + 1);
      }

      stream.print(node.getName() + "{" + cls + '@' + node.addr() + "}(" +
         (nval == null ? "" : nval.toString()) + ")[");
      Enumeration<String> attrs = node.getAttributeNames();
      boolean first = true;

      while(attrs.hasMoreElements()) {
         if(first) {
            first = false;
         }
         else {
            stream.print(", ");
         }

         String key = attrs.nextElement();
         Object val = node.getAttribute(key);
         stream.print(key + ": " + val);
      }

      stream.println("]");

      if(!attr && (node instanceof XTypeNode)) {
         XTypeNode tnode = (XTypeNode) node;

         for(int i = 0; i < tnode.getAttributeCount(); i++) {
            printXNode(tnode.getAttribute(i), level + 1, true, stream);
         }
      }

      if(!attr) {
         for(int i = 0; i < node.getChildCount(); i++) {
            printXNode(node.getChild(i), level + 1, false, stream);
         }
      }
   }

   /**
    * Get the identity finder.
    * @return the identity finder.
    */
   public static XIdentityFinder getXIdentityFinder() {
      IDENTITY_LOCK.lock();

      try {
         XIdentityFinder finder = ConfigurationContext.getContext().get(IDENTITY_KEY);

         if(finder == null) {
            finder = new XIdentityFinder() {
               @Override
               public IdentityID[] getUserRoles(Principal user) {
                  if(user instanceof XPrincipal) {
                     return ((XPrincipal) user).getRoles();
                  }

                  return new IdentityID[0];
               }

               @Override
               public String[] getUserGroups(Principal user) {
                  if(user instanceof XPrincipal) {
                     return ((XPrincipal) user).getGroups();
                  }

                  return new String[0];
               }

               @Override
               public String getUserOrganizationId(Principal user) {
                  if(user instanceof XPrincipal) {
                     return ((XPrincipal) user).getOrgId();
                  }

                  return null;
               }

               @Override
               public IdentityID[] getUsers() {
                  return new IdentityID[] {new IdentityID("anonymous", Organization.getDefaultOrganizationID())};
               }

               @Override
               public XPrincipal create(Identity id) {
                  return new XPrincipal(
                     id.getIdentityID(), new IdentityID[0], new String[0], id.getOrganizationID());
               }

               @Override
               public boolean isSecurityExisting() {
                  return false;
               }
            };

            ConfigurationContext.getContext().put(IDENTITY_KEY, finder);
         }

         return finder;
      }
      finally {
         IDENTITY_LOCK.unlock();
      }
   }

   /**
    * Get the identity finder.
    * @param finder the identity finder.
    */
   public static void setXIdentityFinder(XIdentityFinder finder) {
      IDENTITY_LOCK.lock();

      try {
         if(finder == null) {
            ConfigurationContext.getContext().remove(IDENTITY_KEY);
         }
         else {
            ConfigurationContext.getContext().put(IDENTITY_KEY, finder);
         }
      }
      finally {
         IDENTITY_LOCK.unlock();
      }
   }

   /**
    * Check if contains format.
    * @return true if contains format.
    */
   public static boolean containsFormat(Map mmap) {
      if(mmap == null) {
         return false;
      }

      Collection vals = mmap.values();
      Iterator it = vals.iterator();

      while(it.hasNext()) {
         Object obj = it.next();

         if(Tool.NULL == obj) {
            continue;
         }

         XMetaInfo info = (XMetaInfo) obj;
         XFormatInfo format = info != null ? info.getXFormatInfo() : null;

         if(format != null && !format.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if contains drill.
    * @return true if contains drill.
    */
   public static boolean containsDrill(Map mmap) {
      if(mmap == null) {
         return false;
      }

      Collection vals = mmap.values();
      Iterator it = vals.iterator();

      while(it.hasNext()) {
         Object obj = it.next();

         if(Tool.NULL == obj) {
            continue;
         }

         XMetaInfo info = (XMetaInfo) obj;
         XDrillInfo drill = info != null ? info.getXDrillInfo() : null;

         if(drill != null && !drill.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Copy the user database credentials from user to the variable table.
    */
   public static void copyDBCredentials(XPrincipal user, VariableTable vtable) {
      if(user == null) {
         return;
      }

      Enumeration pnames = user.getPropertyNames();

      while(pnames.hasMoreElements()) {
         String pname = (String) pnames.nextElement();

         if(pname.startsWith("_Db_")) {
            vtable.put(pname, user.getProperty(pname));
         }
      }
   }

   /**
    * Get the tables of a sql string.
    *
    * @return the tables of a sql string.
    */
   public static String[] getTablesForSQLString(String sql, final int stype) {
      final List list = new ArrayList();
      SQLIterator iterator = new SQLIterator(sql);
      SQLIterator.SQLListener listener = (type, value, comment) -> {
         if(type == stype) {
            list.add(value);
         }
      };

      iterator.addSQLListener(listener);
      iterator.iterate();
      String[] arr = new String[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Format an object with the specified format.
    *
    * @param format     the format.
    * @param val        the value that is to be formated use the format.
    * @param ignoreDate do not format if the format is DateFormat and the value
    *                   is not date type.
    */
   public static String format(Format format, Object val, boolean ignoreDate) {
      // support date-part passed in as number, see ExtendedDateFormat.format()
      if(format instanceof ExtendedDateFormat && val instanceof Number) {
         double dval = ((Number) val).doubleValue();

         if(dval > -12 && dval < 1000 && Math.ceil(dval) == Math.floor(dval)) {
            return format.format(new Date((long) dval));
         }
      }

      // do not special check date, so part level of date will also format
      // correct, for the case user set format to a number such as set to
      // customer_id, we also format it
      // if the val is from a measure ref, don't apply the date format to it
      if(ignoreDate) {
         ignoreDate = format instanceof DateFormat && !(val instanceof Date);
      }

      if(val == null || ignoreDate) {
         return val == null ? "" : Tool.toString(val);
      }

      if(format == null) {
         if(val instanceof Time) {
            return Tool.getTimeFormat().format(val);
         }
         else if(val instanceof java.sql.Date) {
            return Tool.getDateFormat().format(val);
         }
         else if(val instanceof Date) {
            return Tool.getDateTimeFormat().format(val);
         }

         return Tool.toString(val);
      }

      if(format instanceof java.text.MessageFormat ||
         format instanceof MessageFormat)
      {
         Object[] arr = val instanceof Object[] ?
            (Object[]) val : (val == null ? new Object[0] : new Object[]{ val });
         return format.format(arr);
      }

      if((format instanceof NumberFormat) && val != null &&
         !(val instanceof Number))
      {
         try {
            return format.format(Double.valueOf(val.toString()));
         }
         catch(Exception ex) {
            // if failed, just return a string to avoid exception
            return Tool.toString(val);
         }
      }

      if((format instanceof NumberFormat) && (val instanceof Number)) {
         try {
            return format.format(Double.valueOf(val.toString()));
         }
         catch(Exception ex) {
            // if failed, just return a string to avoid exception
            return Tool.toString(val);
         }
      }

      // if format is not support, return original value to ignore exception
      try {
         return format.format(val);
      }
      catch(Exception ex) {
         return Tool.toString(val);
      }
   }

   /**
    * Get the columns of a logical model.
    *
    * @param lmodel    the specified logical model.
    * @param partition the specified partition.
    *
    * @return the physical columns of a logical model.
    */
   public static String[] getColumns(XLogicalModel lmodel, XPartition partition) {
      // @by stephenwebster, make key into cache unique for extended partitions.
      String partitionName = partition.getBasePartition() != null ?
         partition.getBasePartition().getName() + "^^" + partition.getName() :
         partition.getName();
      XPartition tempPartition = partitionDataCache.get(partitionName);

      if(tempPartition == null) {
         tempPartition = partition.applyAutoAliases();
         partitionDataCache.put(partitionName, tempPartition);
      }

      partition = tempPartition;

      Set set = new HashSet();
      Enumeration entities = lmodel.getEntities();

      while(entities.hasMoreElements()) {
         XEntity entity = (XEntity) entities.nextElement();
         Enumeration attributes = entity.getAttributes();

         while(attributes.hasMoreElements()) {
            XAttribute attribute = (XAttribute) attributes.nextElement();
            String[] columns = getPhysicalColumns(partition, attribute);

            for(int i = 0; i < columns.length; i++) {
               set.add(columns[i]);
            }
         }
      }

      String[] columns = new String[set.size()];
      set.toArray(columns);

      return columns;
   }

   /**
    * Change info.
    */
   private static final class ChangedInfo {
      public boolean changed;
      public boolean empty;
   }

   /**
    * Convert the Condition(Variable/Expression) when op is DateIn
    * to sql mergeable condition.
    */
   public static Condition convertVariableCondition(XCondition xcond,
      VariableTable vars, boolean isTimestamp)
    {
      if(!(xcond instanceof Condition)) {
         return null;
      }

      Condition cond = (Condition) xcond;

      for(int i = 0; i < cond.getValueCount(); i++) {
         Object val = cond.getValue(i);

         if(val instanceof UserVariable) {
            UserVariable var = (UserVariable) val;
            String name = var.getName();

            if(name == null || name.length() == 0) {
               return null;
            }

            Object userv = null;

            try {
               userv = vars.get(name);

               if(userv == null) {
                  return null;
               }

               cond = cond.toSqlCondition(isTimestamp, userv.toString());
            }
            catch(Exception ex) {
               LOG.error("Failed to set value of user variable "
                  + name + " to " + userv, ex);
            }
         }
         else if(val instanceof ExpressionValue){
            ExpressionValue eval = (ExpressionValue) val;
            String exp = eval.getExpression().replaceAll("\"", "");

            if(exp == null || exp.length() == 0) {
               return null;
            }

            cond = cond.toSqlCondition(isTimestamp, exp);
         }
         else {
            cond = cond.toSqlCondition(isTimestamp, val.toString());
         }

         if(cond == null) {
            return null;
         }

         return cond;
      }

      return null;
   }

   public static void convertDateCondition(ConditionList conds,
       VariableTable vars)
   {
      convertDateCondition(conds, vars, false);
   }

   /**
    * Convert the DateCondition to sql mergeable condition.
    * @param isMV is used in mv execution.
    */
   public static void convertDateCondition(ConditionList conds,
      VariableTable vars, boolean isMV)
   {
      for(int i = 0; i < conds.getConditionSize(); i++) {
         ConditionItem item = conds.getConditionItem(i);

         if(item == null) {
            continue;
         }

         boolean isTimestamp = XSchema.TIME_INSTANT.equals(item.getAttribute().getDataType());
         XCondition xcond = item.getXCondition();
         Condition cond = null;

         if(!(xcond instanceof DateCondition) || (xcond instanceof DateRange)) {
            if(xcond.getOperation() == XCondition.DATE_IN) {
               cond = convertVariableCondition(xcond, vars, isTimestamp);
            }
            else {
               continue;
            }
         }
         else {
            cond = ((DateCondition) xcond).toSqlCondition(isTimestamp);
         }

         if(cond == null) {
            return;
         }

         cond.setType(xcond.getType());
         item.setXCondition(cond);
         conds.setItem(i, item);

         if(isMV) {
            convertDateRange(cond, isTimestamp);
         }
      }
   }

   /**
    * Convert the DateCondition to sql mergeable condition.
    */
   private static void convertDateRange(Condition cond, boolean isTimestamp) {
      if(cond.getOperation() != Condition.BETWEEN) {
         return;
      }

      Object obj0 = cond.getValue(0);
      Object obj1 = cond.getValue(1);

      if(!(obj0 instanceof java.util.Date) && !(obj1 instanceof java.util.Date)) {
         return;
      }
      else if(isTimestamp && obj0 instanceof java.sql.Timestamp) {
         return;
      }

      Calendar calendar = CoreTool.calendar.get();
      calendar.setTime((java.util.Date) obj0);
      calendar.set(Calendar.HOUR_OF_DAY, 0);
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);

      java.sql.Timestamp time1 = new java.sql.Timestamp(calendar.getTimeInMillis());

      calendar.setTime((java.util.Date) obj1);
      calendar.set(Calendar.HOUR_OF_DAY, 23);
      calendar.set(Calendar.MINUTE, 59);
      calendar.set(Calendar.SECOND, 59);
      calendar.set(Calendar.MILLISECOND, 999);

      java.sql.Timestamp time2 = new java.sql.Timestamp(calendar.getTimeInMillis());

      cond.setValue(0, time1);
      cond.setValue(1, time2);
   }

   /**
    * Create a replet command string for a hyperlink. At runtime, if this method
    * is called, inetsoft.sree must be loaded into jvm, for its a sree function.
    */
   public static String getCommand(Hyperlink.Ref link, String servlet) {
      try {
         Class[] params = new Class[] {Hyperlink.Ref.class, String.class};
         Object[] args = new Object[] {link, servlet};
         return (String) call(null, "inetsoft.sree.internal.SUtil",
                              "getCommand", params, args);
      }
      catch(Exception ex) {
         LOG.error(
                     "Failed to get Javascript for command: " + link, ex);
         throw new RuntimeException(ex);
      }
   }

   /**
    * Create join for tables which are from partition but not in query.
    */
   public static void applyPartition(UniformSQL sql, XPartition partition,
      Set tableset, Set originating, Set others)
   {
      if(partition == null) {
         return;
      }

      XRelationship[] rels = partition.findRelationships(originating, others);
      XJoin[] joins = sql.getJoins();

      for(int i = 0; i < rels.length; i++) {
         String leftTable = rels[i].getDependentTable();
         String rightTable = rels[i].getIndependentTable();

         // intermediate joins may have already been created
         for(int l = 0; joins != null && l < joins.length; l++) {
            if((leftTable.equals(joins[l].getTable1(sql)) &&
               rightTable.equals(joins[l].getTable2(sql))) ||
               (leftTable.equals(joins[l].getTable2(sql)) &&
               rightTable.equals(joins[l].getTable1(sql))))
            {
               continue;
            }
         }

         if(!tableset.contains(leftTable)) {
            Object leftalias = partition.getRunTimeTable(leftTable, true);

            if(leftalias != null) {
               sql.addTable(leftTable, leftalias);
            }
            else {
               sql.addTable(leftTable);
            }

            tableset.add(leftTable);
         }

         if(!tableset.contains(rightTable)) {
            Object rightalias = partition.getRunTimeTable(rightTable, true);

            if(rightalias != null) {
               sql.addTable(rightTable, rightalias);
            }
            else {
               sql.addTable(rightTable);
            }

            tableset.add(rightTable);
         }

         XJoin join = new XJoin(
            new XExpression(rels[i].getDependentTable() +
                            "." + rels[i].getDependentColumn(),
                            XExpression.FIELD),
            new XExpression(rels[i].getIndependentTable() + "." +
                            rels[i].getIndependentColumn(),
                            XExpression.FIELD),
            rels[i].getJoinType());

         sql.addJoin(join, rels[i].getMerging());
      }
   }

   /**
    * Execute a query.
    * @param name query name.
    * @param val query parameters as an array of pairs. Each pair is an
    * array of name and value.
    */
   public static Object runQuery(String name, Object val, Principal user, VariableTable vars) {
      return runQuery(name, val, user, vars, true);
   }

   /**
    * Execute a query.
    * @param name query name.
    * @param val query parameters as an array of pairs. Each pair is an
    * array of name and value.
    */
   public static Object runQuery(String name, Object val, Principal user,
                                 VariableTable vars, boolean disableVPM)
   {
      return runQuery(name, val, user, vars, disableVPM, false);
   }

   public static Object runQuery(String name, Object val, Principal user,
                                 VariableTable vars, boolean disableVPM,
                                 boolean reportScript)
   {
      if(vars == null) {
         vars = new VariableTable();
      }
      else {
         // run query should not affect the original variable table.
         vars = vars.clone();
      }

      // log execution
      QueryRecord queryRecord = null;

      try {
         // BC, same as 11.1 XUtil.runQuery and ReportJavaScriptEngine.runQuery
         vars = JSObject.convertToVariableTable(val, vars, reportScript);

         String userSessionID = user instanceof XPrincipal
            ? ((XPrincipal) user).getSessionID()
            : XSessionService.createSessionID(XSessionService.USER, null);
         String execType = QueryRecord.EXEC_TYPE_START;
         Timestamp execTimestamp = new Timestamp(System.currentTimeMillis());
         queryRecord = new QueryRecord(
            null, userSessionID, null, null, execType, execTimestamp,
            QueryRecord.EXEC_STATUS_SUCCESS, null);

         XTable table;
         String message = null;

         // check for worksheet e.g. ws:global:f1/worksheet1
         if(name.startsWith("ws:")) {
            AssetEntry entry = AssetEntry.createAssetEntry(name, ((XPrincipal) user).getOrgId());
            boolean noPermission = SreeEnv.getProperty("security.provider").equals("") &&
               !VpmProcessor.useVpmSecurity();

            // @by jasonshobe, fix bug1400096326732: don't check permissions if
            // being invoked from report (preserve b.c. after bug1368179287358)
            if(!reportScript) {
               IdentityID userID = user instanceof SRPrincipal ? ((SRPrincipal) user).getClientUserID() : null;
               boolean userAsset = entry.getScope() == AssetRepository.USER_SCOPE && Tool.equals(entry.getUser(), userID);

               if(!noPermission && !userAsset && entry.getScope() != AssetRepository.REPORT_SCOPE) {
                  try {
                     AssetEntry parent = entry;

                     // check worksheet and its folder permission
                     while(user != null && !parent.isRoot()) {
                        if(!SecurityEngine.getSecurity().checkPermission(
                           user, ResourceType.ASSET, parent.getPath(), ResourceAction.READ))
                        {
                           message = Catalog.getCatalog().getString(
                              "em.common.security.no.permission",
                              entry.getPath());
                        }

                        parent = parent.getParent();
                     }
                  }
                  catch(Exception ex) {
                     LOG.error(
                        "Failed to check asset permission: " + entry, ex);
                     return Undefined.instance;
                  }

                  if(message != null) {
                     throw new ScriptException(message);
                  }
               }
            }

            if(queryRecord != null) {
               String execSessionID = XSessionService.createSessionID(
                  XSessionService.WORKSHEET, entry.getPath());
               queryRecord.setExecSessionID(execSessionID);
               queryRecord.setObjectName(entry.getPath());
               queryRecord.setObjectType(QueryRecord.OBJECT_TYPE_WORKSHEET);
               Audit.getInstance().auditQuery(queryRecord, user);
               queryRecord.setExecType(QueryRecord.EXEC_TYPE_FINISH);
            }

            ReportWorksheetProcessor proc = new ReportWorksheetProcessor();

            table = proc.execute(entry, vars, user);

            if(queryRecord != null) {
               queryRecord.setExecTimestamp(new Timestamp(System.currentTimeMillis()));
               queryRecord.setExecStatus(QueryRecord.EXEC_STATUS_SUCCESS);
            }
         }
         else {
            return Undefined.instance;
         }

         return new XTableArray(table);
      }
      catch(Throwable ex) {
         if(queryRecord != null) {
            queryRecord.setExecType(QueryRecord.EXEC_TYPE_FINISH);
            queryRecord.setExecTimestamp(new Timestamp(System.currentTimeMillis()));
            queryRecord.setExecStatus(QueryRecord.EXEC_STATUS_FAILURE);
            queryRecord.setExecError(ex.getMessage());
         }

         LOG.error("Failed to run query: " + name, ex);

         if(ex instanceof ScriptException) {
            // vs
            if(!reportScript) {
               CoreTool.addUserWarning(ex.getMessage());
            }
            // adhoc
            else {
               throw (ScriptException) ex;
            }
         }
      }
      finally {
         if(queryRecord != null) {
            Audit.getInstance().auditQuery(queryRecord, user);
         }
      }

      return Undefined.instance;
   }

   /**
    * Get the default format for the date range.
    */
   public static SimpleDateFormat getDefaultDateFormat(int daterange) {
      return getDefaultDateFormat(daterange, XSchema.TIME_INSTANT);
   }

   /**
    * Get the default format for the date range.
    */
   public static SimpleDateFormat getDefaultDateFormat(int daterange, String dtype) {
      if(!XSchema.isDateType(dtype) && !XSchema.isNumericType(dtype) && dtype != null) {
         return null;
      }

      if(daterange < 0) {
         return null;
      }

      String[] patterns = DEFAULT_PATTERN.get(daterange);

      if(patterns == null) {
         return null;
      }

      Locale local = Catalog.getCatalog().getLocale();
      local = local == null ? Locale.getDefault() : local;
      SimpleDateFormat[] fmts = new SimpleDateFormat[patterns.length];

      for(int i = 0;  i < patterns.length; i++) {
         fmts[i] = new ExtendedDateFormat(patterns[i], local);
      }

      return XSchema.TIME.equals(dtype) && fmts.length > 1? fmts[1] : fmts[0];
   }

   /**
    * Add table into SQLDefinition.
    */
   public static void addTable(UniformSQL sql, XPartition partition, String tbl) {
      Object alias = partition.getRunTimeTable(tbl, true);
      SelectTable stable = alias == null?
         sql.addTable(tbl, tbl, null, null, false) :
         sql.addTable(tbl, alias, null, null, false);
      XPartition.PartitionTable ptable = partition.getPartitionTable(tbl);

      if(ptable != null) {
         stable.setCatalog(ptable.getCatalog());
         stable.setSchema(ptable.getSchema());
      }
   }

   /**
    * Check the script if is all comment.
    * @param script the script.
    * @return if is all comment.
    */
   public static boolean isAllComment(String script) {
      String [] lines = script.split("\n");
      boolean multiLine = false;

      for(int i = 0; i < lines.length; i++) {
         String line = lines[i].trim();

         if(multiLine) {
            multiLine = !line.endsWith("*/");
            continue;
         }
         else if(line.startsWith("/*")) {
            multiLine = true;
            continue;
         }
         else if(line.startsWith("//")) {
            continue;
         }

         return false;
      }

      return true;
   }

   /**
    * Replace variable in a string with its value.
    */
   public static String replaceVariable(String str, VariableTable vars) {
      return replaceVariable(str, vars, false);
   }

   public static String replaceVariable(String str, VariableTable vars, boolean doEncode) {
      if(str != null) {
         int st;
         int ed;
         int idx = 0;

         while(true) {
            st = str.indexOf("$(", idx);
            ed = str.indexOf(')', st);

            if(st >= 0 && ed > st + 2) {
               String varName = str.substring(st + 2, ed);
               String varValue = null;

               try {
                  Object obj = vars.get(varName);

                  if(obj != null) {
                     if(obj.getClass().isArray()) {
                        int len = Array.getLength(obj);

                        for(int i = 0; i < len; i++) {
                           String s = Tool.toString(Array.get(obj, i));

                           if(doEncode) {
                              s = Tool.encodeURL(s);
                           }

                           varValue = varValue == null ? s : varValue + "," + s;
                        }
                     }
                     else {
                        varValue = obj.toString();

                        if(doEncode) {
                           varValue = Tool.encodeURL(varValue);
                        }
                     }
                  }
               }
               catch(Exception e) {
               }

               if(varValue != null) {
                  str = str.substring(0, st) + varValue + str.substring(ed + 1);
                  idx = st + varValue.length();
               }
               else {
                  idx = ed;
               }
            }
            else {
               break;
            }
         }
      }

      return str;
   }

   /**
    * Replace with environment variable.
    */
   public static String replaceEnvVariable(String str) {
      if(str == null) {
         return str;
      }

      int idx0 = -1;
      int idx1 = -1;

      while(true) {
         idx0 = str.indexOf("$(", idx0);

         if(idx0 != -1) {
            idx1 = str.indexOf(')', idx0);

            if(idx1 != -1) {
               String var = str.substring(idx0 + 2, idx1);
               String val = SreeEnv.getProperty(var);
               val = val == null ? System.getProperty(var) : val;

               if(val != null) {
                  str = str.substring(0, idx0) + val + str.substring(idx1 + 1);
               }
               else {
                  idx0 = idx1;
               }
            }
            else {
               break;
            }
         }
         else {
            break;
         }
      }

      return str;
   }

   /**
    * Get logic model.
    */
   public static XLogicalModel getLogicModel(XSourceInfo sinfo, Principal user) {
      if(sinfo == null || sinfo.getType() != XSourceInfo.MODEL) {
         return null;
      }

      DataSourceRegistry reg = DataSourceRegistry.getRegistry();
      String dname = sinfo.getPrefix();
      XDataSource ds = reg.getDataSource(dname);
      XDataModel model = ds == null ? null : reg.getDataModel(ds.getFullName());
      return model == null ? null : model.getLogicalModel(sinfo.getSource(),
         user);
   }

   /**
    * Gets the items to be added to a hierarchy list for the specified
    * condition node.
    *
    * @param condition     the condition to add.
    *
    * @return the list of items to be appended to the hierarchy list.
    */
   public static List<HierarchyItem> constructConditionList(XFilterNode condition)
   {
      List<HierarchyItem> target = new ArrayList<>();
      constructConditionList(target, condition, 0);
      return target;
   }

   /**
    * Gets the items to be added to a hierarchy list for the specified
    * condition node.
    *
    * @param target        the list of hierarchy items to be appended.
    * @param condition     the condition to add.
    * @param level         the grouping level of the condition.
    */
   private static void constructConditionList(List<HierarchyItem> target,
                                              XFilterNode condition,
                                              int level)
   {
      if(condition == null) {
         return;
      }

      if(condition instanceof XSet) {
         XSet tmpXSet = (XSet) condition;
         int childCount = tmpXSet.getChildCount();

         for(int i = 0; i < childCount; i++) {
            XNode node = tmpXSet.getChild(i);

            if(node instanceof XFilterNode) {
               if(node instanceof XSet) {
                  XSet tmpChildXSet = (XSet) node;

                  constructConditionList(
                     target, tmpChildXSet, level + 1);

                  if(tmpChildXSet.getChildCount() > 0) {
                     XSet oldXSet = (XSet) condition;
                     XSet newXSet = new XSet(oldXSet.getRelation());

                     newXSet.setIsNot(oldXSet.isIsNot());
                     target.add(new XSetItem(newXSet, level));
                  }
               }
               else {
                  target.add(new XFilterNodeItem((XFilterNode) node, level));

                  XSet oldXSet = (XSet) condition;
                  XSet newXSet = new XSet(oldXSet.getRelation());

                  newXSet.setIsNot(oldXSet.isIsNot());

                  target.add(new XSetItem(newXSet, level));
               }
            }
         }
      }
      else {
         target.add(new XFilterNodeItem((XFilterNode) condition, level));
      }

      // delete last XSet
      int index = target.size() - 1;

      if(index >= 0 && index < target.size() && (index % 2 == 1)) {
         target.remove(index);
      }
   }

   /**
    * Gets the columns available for auto join.
    *
    * @param tables              the avaialable tables
    * @param metaProvider        the meta data provider for the datasource
    * @param metaInfoAvailable   meta data info is available
    * @return a map of available columns to use for auto join
    */
   public static Map<String, Object> getAutoJoinColumns(TableNode[] tables,
      MetaDataProvider metaProvider, boolean metaInfoAvailable)
   {
      Map<String, Object> availableColumns = new HashMap<>();

      if(metaInfoAvailable) {
         for(TableNode table : tables) {
            int numCols = table.getColumnCount();

            for(int j = 0; j < numCols; j++) {
               String col = table.getColumn(j);
               List relStrs = table.getForeignKeys(col);

               if(relStrs != null) {
                  availableColumns.put(table.getName() + "." + col, relStrs);
               }
            }
         }
      }
      else {
         for(int i = 0; i < tables.length - 1; i++) {
            for(int j = 0; j < tables[i].getColumnCount(); j++) {
               String col = tables[i].getColumn(j);

               if(availableColumns.containsKey(col)) {
                  continue;
               }

               for(int k = i + 1; k < tables.length; k++) {
                  for(int l = 0; l < tables[k].getColumnCount(); l++) {
                     if(tables[k].getColumn(l).equals(col)) {
                        @SuppressWarnings("unchecked")
                        List<String> colTables = (List<String>) availableColumns.get(col);

                        if(colTables == null) {
                           colTables = new ArrayList<>();
                           colTables.add(tables[i].getName());
                           availableColumns.put(col, colTables);
                        }

                        colTables.add(tables[k].getName());
                     }
                  }
               }
            }
         }

         Set<String> primaryKeys = new HashSet<>();

         for(TableNode table : tables) {
            try {
               XNode root = metaProvider.getPrimaryKeys(
                  (XNode) table.getUserObject());

               for(int i = 0; i < root.getChildCount(); i++) {
                  XNode key = root.getChild(i);
                  String pkColumn = (String) key.getAttribute("pkColumnName");

                  if(pkColumn != null) {
                     primaryKeys.add(pkColumn);
                  }
               }
            }
            catch(Exception ex) {
               LOG.warn(ex.getMessage(), ex);
            }
         }

         if(primaryKeys.size() > 0) {
            Iterator keys = availableColumns.keySet().iterator();

            while(keys.hasNext()) {
               String col = (String) keys.next();

               if(!primaryKeys.contains(col)) {
                  keys.remove();
               }
            }
         }
      }

      return availableColumns;
   }

   /**
    * Check if the column is primary key or not.
    */
   public static boolean isPrimaryKey(String columnName, XNode primaryKeys) {
      for(int i = 0; i < primaryKeys.getChildCount(); i++) {
         if(columnName.equals(
            primaryKeys.getChild(i).getAttribute("pkColumnName")))
         {
            return true;
         }

         if(primaryKeys.getChild(i) instanceof XSequenceNode) {
            XSequenceNode snode = (XSequenceNode) primaryKeys.getChild(i);

            if(snode.getChildCount() > 1) {
               return false;
            }

            for(int j = 0; j < snode.getChildCount(); j++) {
               if(columnName.equals(
                  snode.getChild(j).getAttribute("pkColumnName")))
               {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /**
    * Add description from worksheet column to the target columnref.
    * @param ref     the target columnref which need to set description.
    * @param columns [description]
    */
   private static void addDescriptionFromWSColumn(DataRef ref, ColumnSelection columns) {
      if(ref == null) {
         return;
      }

      ColumnRef colRef = (ColumnRef) columns.findAttribute(ref);

      if(colRef != null) {
         addDescriptionFromColumn(ref, colRef);
         return;
      }

      String name = ref.getAttribute();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef dref = columns.getAttribute(i);

         if(dref == null) {
            continue;
         }

         ColumnRef cref = (ColumnRef) dref;
         String alias = cref.getAlias();
         String name0 = alias != null && !"".equals(alias) ?
            alias : cref.getAttribute();

         if(name.equals(name0)) {
            addDescriptionFromColumn(ref, cref);
         }
      }
   }

   /**
    * Add description from source columnref to target.
    * @param target  the target field/column need to add description.
    * @param source the columnref which descriptoin comes from.
    */
   private static void addDescriptionFromColumn(DataRef target, ColumnRef source) {
      if(target instanceof BaseField) {
         ((BaseField) target).setDescription(source.getDescription());
      }
      else if(target instanceof ColumnRef) {
         ((ColumnRef) target).setDescription(source.getDescription());
      }
   }

   /**
    * Add descriptions from source of vs assembly.
    * @param assembly the vs asssembly name.
    * @param columns  the columnselectoin need add descriptions.
    */
   public static void addDescriptionsFromSource(VSAssembly assembly,
      ColumnSelection columns)
   {
      if(!(assembly instanceof DataVSAssembly) || columns == null) {
         return;
      }

      inetsoft.uql.asset.SourceInfo sinfo = ((DataVSAssembly) assembly).getSourceInfo();
      Viewsheet vs = assembly.getViewsheet();
      AssetEntry baseEntry = vs.getBaseEntry();

      if(sinfo == null || baseEntry == null) {
         return;
      }

      try {
         if(baseEntry.isLogicModel()) {
            // Do not directly change the assembly's SourceInfo prefix since certain logic assumes
            // that it is null. Instead clone the SourceInfo, set the prefix on the clone, and use
            // the clone to obtain the logical model descriptions
            if(sinfo.getPrefix() == null) {
               SourceInfo cinfo = (SourceInfo) sinfo.clone();
               cinfo.setPrefix(baseEntry.getProperty("prefix"));
               addDescriptionsFromLM(cinfo, columns);
            }
            else {
               addDescriptionsFromLM(sinfo, columns);
            }
         }
         else if(baseEntry.isWorksheet()){
            addDescriptionsFromWSTable(sinfo.getSource(), baseEntry, columns);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to add descriptions from source.", ex);
      }
   }

   /**
    * Add descriptions from logical model.
    * @param sinfo   the sourceinfo to get logical model.
    * @param columns the columnselection need add descriptions.
    */
   private static void addDescriptionsFromLM(SourceInfo sinfo, ColumnSelection columns)
      throws Exception
   {
      if(sinfo == null) {
         return;
      }

      XRepository repository = XFactory.getRepository();
      XDataModel dataModel = repository.getDataModel(sinfo.getPrefix());

      if(dataModel != null) {
         XLogicalModel model = dataModel.getLogicalModel(sinfo.getSource());

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef col = (ColumnRef) columns.getAttribute(i);
            addDescriptionForColumnRef(col, model);
         }
      }
   }

   /**
    * Add columns description from binding worksheet to target
    * assembly columnselection.
    * @param tname   the worksheet TableAssembly name.
    * @param wsEntry the asset entry of the binding worksheet.
    * @param columns the columnselection of the target assembly which
    * binding the worksheet.
    */
   private static void addDescriptionsFromWSTable(String tname,
      AssetEntry wsEntry, ColumnSelection columns)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      Worksheet sheet = (Worksheet) repository.getSheet(
         wsEntry, null, false, AssetContent.ALL);

      if(sheet == null) {
         return;
      }

      Assembly wsobj = sheet.getAssembly(tname);
      Assembly[] assemblies = sheet.getAssemblies();
      ColumnSelection columns1 = null;

      if(wsobj == null || assemblies == null || assemblies.length == 0) {
         return;
      }

      for(int i = 0; i < assemblies.length; i++) {
         if(Tool.equals(wsobj, assemblies[i])) {
            TableAssembly tassembly = (TableAssembly) assemblies[i];
            columns1 = tassembly.getColumnSelection();
            break;
         }
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref = columns.getAttribute(i);
         addDescriptionFromWSColumn(ref, columns1);
      }
   }

   /**
    * Add descriptions from source of ws table assembly.
    * @param tassembly the vs asssembly name.
    * @param columns   the columnselectoin need add descriptions.
    */
   public static void addDescriptionsFromSource(TableAssembly tassembly, ColumnSelection columns) {
      try {
         if(tassembly instanceof BoundTableAssembly) {
            XUtil.addDescriptionsFromLM(
               ((BoundTableAssembly) tassembly).getSourceInfo(), columns);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to add descriptions from source", ex);
      }
   }

   /**
    * Add description from logical model to the columnref.
    */
   private static void addDescriptionForColumnRef(ColumnRef col, XLogicalModel model) {
      if(col == null || model == null) {
         return;
      }

      DataRef dref = col.getDataRef();

      // logical model cannot add expression column directly, so just skip.
      if(dref == null || !(dref instanceof AttributeRef)) {
         return;
      }

      AttributeRef ref = (AttributeRef) col.getDataRef();
      XEntity entity = null;
      XAttribute attribute = null;

      if(ref.getEntity() == null) {
         String attr = ref.getAttribute();
         String[] cattr = attr.split(":");

         if(cattr.length == 2) {
            entity = model.getEntity(cattr[0]);
            attribute = entity == null ? null : entity.getAttribute(cattr[1]);
         }
      }
      else {
         entity = model.getEntity(ref.getEntity());
         attribute = entity == null ? null :
            entity.getAttribute(ref.getAttribute());
      }

      if(attribute != null) {
         col.setDescription(attribute.getDescription());
      }
   }

   /**
    * Return base table assembly by mirror table assembly.
    */
   public static TableAssembly getBaseTableAssembly(TableAssembly tassembly) {
      if(tassembly == null || !(tassembly instanceof MirrorTableAssembly)) {
         return tassembly;
      }

      TableAssembly assembly = (TableAssembly) tassembly.clone();

      while(assembly instanceof MirrorTableAssembly) {
         assembly = ((MirrorTableAssembly) assembly).getTableAssembly();
      }

      return assembly;
   }

   /**
    * Get the description of a entity.
    */
   public static String getEntityDescription(XLogicalModel lmodel, String ename)
      throws Exception
   {
      if(lmodel != null) {
         XEntity entity = lmodel.getEntity(ename);
         return entity == null ? null : entity.getDescription();
      }

      return null;
   }

   /**
    * Get the description of a logical model.
    * @param source the specified data source.
    * @param lname the name of the specified logical model.
    */
   public static String getLogicalModelDescription(String source, String lname)
      throws Exception
   {
      XRepository repository = XFactory.getRepository();
      XDataModel model = repository.getDataModel(source);

      return model == null ? "" : model.getLogicalModel(lname).getDescription();
   }

   public static void applyProperties(HikariConfig ds, Map<String, String> properties,
                                      String prefix, String dataSource)
   {
      applyProperties(ds, properties, prefix, dataSource, false);
   }

   public static void applyProperties(HikariConfig ds, Properties properties, String prefix,
                                      String dataSource, boolean jdbc4)
   {
      Map<String, String> map = new HashMap<>();

      for(String property : properties.stringPropertyNames()) {
         map.put(property, properties.getProperty(property));
      }

      applyProperties(ds, map, prefix, dataSource, jdbc4);
   }

   // apply hikari properties. all properties with the specified prefix are treated as data
   // source properties
   public static void applyProperties(HikariConfig ds, Map<String, String> properties,
                                      String prefix, String dataSource, boolean jdbc4)
   {
      BeanInfo beanInfo = null;

      for(Map.Entry<String, String> e : properties.entrySet()) {
         String propertyName = e.getKey();

         if(prefix.isEmpty() || propertyName.startsWith(prefix)) {
            String propertyValue = e.getValue();
            propertyName = propertyName.substring(prefix.length());

            if(!jdbc4 && "connectionTestQuery".equals(propertyName)) {
               ds.setConnectionTestQuery(propertyValue);
            }

            try {
               if(beanInfo == null) {
                  beanInfo = Introspector.getBeanInfo(ds.getClass());
               }

               for(PropertyDescriptor property :
                  beanInfo.getPropertyDescriptors())
               {
                  if(property.getName().equals(propertyName)) {
                     PropertyEditor editor =
                        property.createPropertyEditor(null);

                     if(editor == null) {
                        editor = PropertyEditorManager.findEditor(
                           property.getPropertyType());
                     }

                     if(editor != null) {
                        editor.setAsText(propertyValue);
                        property.getWriteMethod()
                           .invoke(ds, editor.getValue());
                     }
                     else {
                        LOG.warn(
                           "Failed to set connection property {} on data source {}, failed to " +
                           "get property editor for type: {}", propertyName, dataSource,
                           property.getPropertyType().getName());
                     }

                     break;
                  }
               }
            }
            catch(Exception ex) {
               LOG.warn(
                  "Failed to set connection pool property {} on data source {} to {}",
                  propertyName, dataSource, propertyValue, ex);
            }
         }
      }
   }

   public static void updateQueryFolderPermission(String oname, String nname, PermissionUpdater fn) {
      AssetEntry oentry =
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.QUERY_FOLDER, oname, null);
      AssetEntry nentry =
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.QUERY_FOLDER, nname, null);
      Resource oresource = AssetUtil.getSecurityResource(oentry);
      Resource nresource = AssetUtil.getSecurityResource(nentry);
      fn.updatePermission(oresource.getType(), oresource.getPath(), nresource.getPath());
   }

   public static String getScriptType(String schemaType) {
      String type;

      switch(schemaType) {
         case XSchema.BOOLEAN:
            type = "bool";
            break;
         case XSchema.BYTE:
         case XSchema.DECIMAL:
         case XSchema.DOUBLE:
         case XSchema.FLOAT:
         case XSchema.INTEGER:
         case XSchema.LONG:
         case XSchema.SHORT:
            type = "number";
            break;
         case XSchema.CHAR:
         case XSchema.CHARACTER:
         case XSchema.STRING:
            type = "string";
            break;
         case XSchema.DATE:
         case XSchema.TIME:
         case XSchema.TIME_INSTANT:
            type = "+Date";
            break;
         default:
            type = "?";
      }

      return type;
   }

   /**
    * Check if a datasource name is valid.
    *
    * @param repository the XRepository
    * @param dname the specified data source name
    * @return String valid, or appropriate error message for invalid name
    */
   public static String isDataSourceNameValid(XRepository repository,
                                              String dname, String parent)
   {
      String nameValidity = isNameValid(dname);

      if(!nameValidity.equals("Valid")) {
         return nameValidity;
      }

      Catalog catalog = Catalog.getCatalog();

      try {
         String[] names = repository.getDataSourceNames();
         String duplicate = catalog.getString("common.datasource.duplicateName") + ": ";

         for(String name : names) {
            if(dname.equals(name)) {
               return duplicate + dname;
            }
         }

         names = repository.getSubfolderNames(parent);

         for(String name : names) {
            name = DataSourceFolder.getDisplayName(name);

            if(Tool.equals(dname, name, false)) {
               return duplicate + dname;
            }
         }

         // don't add catalog.getString on the word since it was used to
         // compare with "Valid" in other files
         return "Valid";
      }
      catch(Exception ex) {
         LOG.debug("Failed to check data source name validity", ex);
         return catalog.getString("Invalid Name");
      }
   }

   /**
    * Check if a common name is valid. A valid name should not be null, empty,
    * or several symbols, such as "/", "\", "^", ":", "*", """, "|", "<", ">"
    * and "?", because of locking mechanism.
    *
    * @param name the common name.
    * @return String valid, or appropriate error message for invalid name.
    */
   public static String isNameValid(String name) {
      Catalog catalog = Catalog.getCatalog();

      if(name == null || name.trim().length() == 0) {
         return catalog.getString("common.datasource.nameNotEmpty");
      }

      if(name.equals("/")||name.equals("\\"))
      {
         return catalog.getString("Invalid Name") + ": \"" + name + "\"";
      }

      if(name.contains("/") || name.contains("\\")) {
         return catalog.getString("common.datasource.slashNotAllowed");
      }

      if(name.contains("^")) {
         return catalog.getString("common.datasource.specialCharNotAllowed");
      }

      if(name.contains(":") || name.contains("*") || name.contains("\"") || name.contains("|") ||
         name.contains("<") || name.contains(">") || name.contains("?"))
      {
         return catalog.getString("Invalid Name") + ": \"" + name + "\"";
      }

      return "Valid";
   }

   /**
    * Get additional connections.
    */
   public static String[] getConnections(XRepository rep, String dsname) {
      try {
         XDataSource ds = rep.getDataSource(dsname);

         if(ds instanceof AdditionalConnectionDataSource) {
            return ((AdditionalConnectionDataSource<?>) ds).getDataSourceNames();
         }
      }
      catch(RemoteException exc) {
         LOG.error(exc.getMessage(), exc);
      }


      return new String[] {};
   }

   /**
    * Return if the target ref is a date dimension.
    */
   public static boolean isDateDim(DataRef ref) {
      if(!(ref instanceof GroupField) && !(ref instanceof XDimensionRef)) {
         return false;
      }

      if(XSchema.isDateType(ref.getDataType())) {
         return true;
      }

      if(!XSchema.isNumericType(ref.getDataType())) {
         return false;
      }

      int dlevel = -1;

      if(ref instanceof XDimensionRef) {
         dlevel = ((XDimensionRef) ref).getDateLevel();
      }
      else if(ref instanceof GroupField) {
         OrderInfo order = ((GroupField) ref).getOrderInfo();
         dlevel = order.getOption();
      }

      return (dlevel & DateRangeRef.PART_DATE_GROUP) != 0;
   }

   /**
    * Find the date range ref.
    */
   public static DateRangeRef findDateRage(DataRef ref) {
      while(ref instanceof DataRefWrapper) {
         if(ref instanceof DateRangeRef) {
            return (DateRangeRef) ref;
         }

         ref = ((DataRefWrapper) ref).getDataRef();
      }

      return null;
   }

   public static <T> T withFixedDateFormat(Object source, Supplier<T> action) {
      CoreTool.useDatetimeWithMillisFormat.set(isMillisInFormatRequired(source));

      try {
         return action.get();
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
      finally {
         CoreTool.useDatetimeWithMillisFormat.set(false);
      }
   }

   public static void withFixedDateFormat(Object source, Runnable action) {
      CoreTool.useDatetimeWithMillisFormat.set(isMillisInFormatRequired(source));

      try {
         action.run();
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
      finally {
         CoreTool.useDatetimeWithMillisFormat.set(false);
      }
   }

   public static boolean isMillisInFormatRequired(Object target) {
      if(target instanceof Boolean) {
         return (boolean) target;
      }
      else if(target instanceof String source) {
         XDataSource xds = Tool.isEmptyString(source) ?
            null : DataSourceRegistry.getRegistry().getDataSource(source);
         return isMillisInFormatRequired(xds);
      }
      else if(target instanceof XDataSource xds) {
         return xds != null ? SQLHelper.getSQLHelper(xds) instanceof DatabricksHelper : false;
      }
      // ignore addition usage, since binding timestamp + additional tables is not a common usage,
      // so no need to spend a lot of effort to figure it out which table the selection value data come from.
      else if(target instanceof BindableVSAssembly assembly) {
         AssemblyRef[] assemblyRefs = assembly == null ? null : assembly.getDependedWSAssemblies();

         if(assemblyRefs == null || assemblyRefs.length == 0) {
            return false;
         }

         Worksheet ws = assembly.getWorksheet();

         if(ws != null && assemblyRefs.length == 1) {
            Assembly wsAssembly = ws.getAssembly(assemblyRefs[0].getEntry().getName());

            if(wsAssembly instanceof TableAssembly tableAssembly) {
               return isMillisInFormatRequired(tableAssembly.getSource());
            }
         }
      }

      return false;
   }

   private static final Map<Integer,String[]> DEFAULT_PATTERN = new HashMap<>();

   static {
      DEFAULT_PATTERN.put(DateRangeRef.YEAR_INTERVAL, new String[]{"yyyy"});
      DEFAULT_PATTERN.put(DateRangeRef.YEAR_OF_FULL_WEEK, new String[]{"yyyy"});
      DEFAULT_PATTERN.put(DateRangeRef.QUARTER_INTERVAL, new String[]{"yyyy QQQ"});
      DEFAULT_PATTERN.put(DateRangeRef.QUARTER_OF_FULL_WEEK, new String[]{"yyyy QQQ"});
      DEFAULT_PATTERN.put(DateRangeRef.MONTH_INTERVAL, new String[]{"yyyy MMM"});
      DEFAULT_PATTERN.put(DateRangeRef.MONTH_OF_FULL_WEEK, new String[]{"yyyy MMM"});
      DEFAULT_PATTERN.put(DateRangeRef.WEEK_INTERVAL, new String[]{"yyyy-MM-dd"});
      DEFAULT_PATTERN.put(DateRangeRef.DAY_INTERVAL, new String[]{"yyyy-MM-dd"});
      DEFAULT_PATTERN.put(DateRangeRef.HOUR_INTERVAL, new String[]{"yyyy-MM-dd HH", "HH"});
      DEFAULT_PATTERN.put(DateRangeRef.MINUTE_INTERVAL, new String[]{"yyyy-MM-dd HH:mm", "HH:mm"});
      DEFAULT_PATTERN.put(DateRangeRef.SECOND_INTERVAL,
         new String[]{"yyyy-MM-dd HH:mm:ss", "HH:mm:ss"});
      DEFAULT_PATTERN.put(DateRangeRef.QUARTER_OF_YEAR_PART, new String[]{"QQQ"});
      DEFAULT_PATTERN.put(DateRangeRef.MONTH_OF_YEAR_PART, new String[]{"MMM"});
      DEFAULT_PATTERN.put(DateRangeRef.DAY_OF_WEEK_PART, new String[]{"EEE"});
   }

   public static final boolean SELECT_MAX = true;
   public static final boolean SELECT_MIN = false;

   // for portal data model, ignore additional connection.
   public static final String OUTER_MOSE_LAYER_DATABASE = "^default^";
   public static final String PORTAL_DATA = "portal_data";
   public static final String DATASOURCE_ADDITIONAL = "additional";
   public static final String DATAMODEL_FOLDER_SPLITER = "^__^";
   public static final String DATAMODEL_PATH_SPLITER = "^";
   public static final String ADDITIONAL_DS_CONNECTOR = "::";

   private static final Logger LOG = LoggerFactory.getLogger(XUtil.class);

   private static final String IDENTITY_KEY = XUtil.class.getName() + ".identity";
   private static final Lock IDENTITY_LOCK = new ReentrantLock();

   // @by stephenwebster, For bug1416867569612, add short term cache for getting
   // the applyAutoAliases result. This saves significant time when in a tight
   // loop, such as repeated calls to getAttributes
   private static DataCache<String, XPartition> partitionDataCache = new DataCache<>(5, 1000);

   @FunctionalInterface
   public interface PermissionUpdater {
      void updatePermission(ResourceType type, String oldResource, String newResource);
   }
}
