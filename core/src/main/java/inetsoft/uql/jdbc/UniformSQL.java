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

import inetsoft.uql.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringReader;
import java.security.Principal;
import java.util.List;
import java.util.*;

/**
 * The UniformSQL contains the information on a SQL select statement.
 * These include the column list, aliases, from clause, where clause,
 * group by and order by clause. The SQL supported by the UniformSQL
 * is limited. It is used primarily to store the visually defined
 * SQL statement and to replace the StructedSQL and the FreeformSQL.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class UniformSQL implements SQLDefinition, Cloneable, XMLSerializable {
   /**
    * Set the hint on limiting the number of rows in the raw data (table).
    */
   public static final String HINT_INPUT_MAXROWS = "__HINT_INPUT_MAXROWS__";
   /**
    * Set the hint on limiting the number of rows in the output of the query.
    */
   public static final String HINT_OUTPUT_MAXROWS = "__HINT_MAX_ROWS__";
   /**
    * Set the hint on treating the sql to be a static or dynamic sql.
    */
   public static final String HINT_STATIC_SQL = "__HINT_STATIC_SQL__";
   /**
    * Set the hint on whether to sort the sql columns.
    */
   public static final String HINT_SORTED_SQL = "__HINT_SORTED_SQL__";
   /**
    * Set the hint on whether the sql string have already applied sorting columns.
    */
   public static final String HINT_SQL_STRING_SORTED_COLUMN = "__HINT_SQL_STRING_SORTED_COLUMN__";
   /**
    * Set the hint on whether to not sort the sql columns.
    */
   public static final String HINT_WITHOUT_SORTED_SQL = "__HINT_WITHOUT_SORTED_SQL__";
   /**
    * Set the hint on cleared sql string to regenerate sql string with sorted column.
    */
   public static final String HINT_CLEARED_SQL_STRING = "__HINT_CLEARED_SQL_STRING__";
   /**
    * Set the hint on whether it's a user defined maxrows that cannot be ignored.
    */
   public static final String HINT_USER_MAXROWS = "__HINT_USER_MAX_ROWS__";
   /**
    * Parse status if the sql string has not being parsed.
    */
   public static final int PARSE_INIT = -1;
   /**
    * Parse status if the sql string has been successfully parsed.
    */
   public static final int PARSE_SUCCESS = 0;
   /**
    * Parse status if the sql string has been partially parsed.
    */
   public static final int PARSE_PARTIALLY = 1;
   /**
    * Parse status if the sql string parsing has failed.
    */
   public static final int PARSE_FAILED = 3;

   /**
    * XML tag of UnifomedSQL.
    */
   public static final String XML_TAG = "uniform_sql";

   /**
    * Sorting order, ascending.
    */
   public static final String SORT_ASC = "asc";
   /**
    * Sorting order, descending.
    */
   public static final String SORT_DESC = "desc";

   /**
    * Default constructure.
    */
   public UniformSQL() {
      super();
   }

   /**
    * Parse the select statement and use the result to construct UnifomedSQL.
    * @param sql the specified sql statement.
    */
   public UniformSQL(String sql) {
      this(sql, true);
   }

   /**
    * Parse the select statement and use the result to construct UnifomedSQL.
    * @param sql the specified sql statement.
    */
   public UniformSQL(String sql, boolean parseIt) {
      this();

      this.parseIt = parseIt;
      setSQLString(sql);
   }

   /**
    * Construct UniformSQL from SQLDefinition.
    * @param definition the specified sql definition.
    */
   @SuppressWarnings("deprecation")
   public UniformSQL(SQLDefinition definition) {
      this();

      if(definition instanceof StructuredSQL) {
         StructuredSQL sql = (StructuredSQL) definition;
         XSelection xselect = new JDBCSelection(sql.getSelection());
         setSelection(xselect);

         for(int i = 0; i < xselect.getColumnCount(); i++) {
            String name = xselect.getColumn(i);
            String alias = xselect.getAlias(i);

            if(name.equals(alias)) {
               xselect.setAlias(i, "");
            }
         }

         for(int i = 0; i < sql.getTableCount(); i++) {
            String name = sql.getTable(i);
            SelectTable nstable = new SelectTable(name, name);
            // nstable = fixSelectTable(nstable);
            tables.add(nstable);
         }

         // where
         XFilterNode root = new XSet("and");

         for(int i = 0; i < sql.getJoinCount(); i++) {
            StructuredSQL.Join join = sql.getJoin(i);
            XExpression exp1 = new XExpression(join.table1 + "." + join.column1,
                                               XExpression.FIELD);
            XExpression exp2 = new XExpression(join.table2 + "." + join.column2,
                                               XExpression.FIELD);
            XJoin node = new XJoin(exp1, exp2, join.op);

            root.addChild(node);
         }

         // conditions
         Enumeration keys = sql.getConditionColumns();

         while(keys.hasMoreElements()) {
            String condName = (String) keys.nextElement();
            String cond = sql.getCondition(condName);
            XFilterNode node = genCondition(condName, cond);

            if(node != null) {
               root.addChild(node);
            }
         }

         setWhere(root);

         // sort
         Enumeration skeys = sql.getSortedColumns();

         while(skeys.hasMoreElements()) {
            String scol = (String) skeys.nextElement();

            setOrderBy(scol, sql.getSorting(scol));
         }
      }
      else if(definition instanceof FreeformSQL) {
         setSQLString(definition.getSQLString());
      }
      else if(definition instanceof UniformSQL) {
         //noinspection SynchronizationOnLocalVariableOrMethodParameter
         synchronized(definition) {
            read((UniformSQL) definition);
         }
      }
   }

   /**
    * Clear cached string.
    */
   public final void clearCachedString() {
      cstring = null;
   }

   /**
    * Apply variables to this.
    */
   public final void applyVariableTable(VariableTable vars) {
      applyVariableTable(vars, this);
      // clear cached sql string after apply variable table
      clearCachedString();
   }

   /**
    * Apply variables to this.
    */
   private void applyVariableTable(VariableTable vars, UniformSQL usql) {
      applyVariableTable(usql.getWhere(), vars);
      applyVariableTable(usql.getHaving(), vars);
      SelectTable[] tables = usql.getSelectTable();

      for(SelectTable table : tables) {
         Object obj = table.getName();

         if(obj instanceof UniformSQL) {
            ((UniformSQL) obj).applyVariableTable(vars);
         }
      }
   }

   /**
    * Apply variables to a filter node.
    */
   private void applyVariableTable(XFilterNode condition, VariableTable vars) {
      if(condition == null) {
         return;
      }

      if(condition instanceof XSet) {
         XSet set = (XSet)condition;

         for(int i = 0; i < set.getChildCount(); i++) {
            XFilterNode node = (XFilterNode)set.getChild(i);
            applyVariableTable(node, vars);
         }
      }
      else if(condition instanceof XBinaryCondition) {
         XBinaryCondition bin = (XBinaryCondition) condition;
         applyParamValue(bin.getExpression1(), vars);
         applyParamValue(bin.getExpression2(), vars);
      }
      else if(condition instanceof XUnaryCondition) {
         XUnaryCondition una = (XUnaryCondition) condition;
         applyParamValue(una.getExpression1(), vars);
      }
      else if(condition instanceof XTrinaryCondition) {
         XTrinaryCondition tri = (XTrinaryCondition) condition;
         applyParamValue(tri.getExpression1(), vars);
         applyParamValue(tri.getExpression2(), vars);
         applyParamValue(tri.getExpression3(), vars);
      }
   }

   /**
    * Apply variables.
    */
   private void applyParamValue(XExpression exp, VariableTable vars) {
      Object val = exp.getValue();

      if(val instanceof UniformSQL) {
         applyVariableTable(((UniformSQL) val).getWhere(), vars);

         if(Boolean.TRUE.equals(getHint(UniformSQL.HINT_STATIC_SQL, true))) {
            SelectTable[] selectTable = ((UniformSQL) val).getSelectTable();

            if(selectTable != null) {
               for(SelectTable table : selectTable) {
                  if(table == null) {
                     continue;
                  }

                  if(table.getName() instanceof UniformSQL) {
                     ((UniformSQL) table.getName()).applyVariableTable(vars);
                  }
               }
            }
         }
      }
      else if(XExpression.EXPRESSION.equals(exp.getType())){
         String str = (String) exp.getValue();
         boolean inQuotes = str.startsWith("'") && str.endsWith("'");
         int index = 0;
         Set<String> processed = new HashSet<>();

         while(true) {
            index = str.indexOf("$(dataselcond", index);

            if(index != -1) {
               int index2 = str.indexOf(")", index);
               String name = str.substring(index + 2, index2);
               Object value = null;

               try {
                  value = vars.get(name);
               }
               catch(Exception ex) {
                  LOG.error("Failed to get value of parameter: " + name, ex);
               }

               if(value != null) {
                  String strValue = AbstractCondition.getValueSQLString(value);

                  if(inQuotes && strValue.startsWith("'")
                     && strValue.endsWith("'"))
                  {
                     str = str.substring(0, index) +
                        strValue.substring(1, strValue.length() - 1) +
                        str.substring(index2 + 1);
                  }
                  else {
                     str = str.substring(0, index) +
                        strValue + str.substring(index2 + 1);
                  }
               }
               else if(processed.contains(name)) {
                  throw new RuntimeException("Value for parameter: " + name +
                                             " not found!");
               }
               else {
                  index++;
               }

               processed.add(name);
            }
            else {
               break;
            }
         }

         exp.setValue(str, XExpression.EXPRESSION);
      }
   }

   /**
    * Copy SQL definition from a UniformSQL.
    * @param uniformSql the specified uniform sql.
    */
   public synchronized void read(UniformSQL uniformSql) {
      clear();

      if(uniformSql != null) {
         setDistinct(uniformSql.isDistinct());
         setSelection((XSelection) uniformSql.getSelection().clone());
         tables = new Vector<>(uniformSql.tables);
         fields = new Vector<>(uniformSql.fields);
         orderDBFields = new Vector<>(uniformSql.orderDBFields);
         groupDBFields = new Vector<>(uniformSql.groupDBFields);
         orderByList = new Vector<>(uniformSql.orderByList);

         if(uniformSql.groups == null) {
            groups = null;
         }
         else {
            groups = new Object[uniformSql.groups.length];
            System.arraycopy(uniformSql.groups, 0, groups, 0, groups.length);
         }

         where = uniformSql.where == null ?
            null : (XFilterNode) uniformSql.where.clone();
         having = uniformSql.having == null ?
            null : (XFilterNode) uniformSql.having.clone();
         sqlstring = uniformSql.sqlstring;

         parseIt = uniformSql.parseIt;
         parseResult = uniformSql.parseResult;

         columns = uniformSql.columns;
      }
   }

   /**
    * Parse a SQL string and store the information in this object.
    * @param sql - SQL statement.
    */
   public void parse(final String sql) {
      Runnable runnable = () -> new SQLProcessor(UniformSQL.this).parse(sql);

      parserPool.add(runnable);
   }

   /**
    * Parse a SQL string and store the information in this object.
    * @param sql - SQL statement.
    * @param parseType - indicate parse total sql statement or only partially,
    * the value must be on of PARSE_ALL, PARSE_ONLY_SELECT or
    * PARSE_ONLY_SELECT_FROM.
    * @param time - the parse process should finish in the time.
    */
   void parse(String sql, int parseType, long time) throws Exception {
      clear();
      SQLLexer lexer = new SQLLexer(new StringReader(getQuotedSqlString(sql)));
      SQLParser parser = new SQLParser(lexer);
      parser.setTime(time);
      setParseResult(PARSE_FAILED);

      if(parseType == PARSE_ALL) {
         parser.direct_select_stmt_n_rows(UniformSQL.this);
         setParseResult(PARSE_SUCCESS);
      }
      else if(parseType == PARSE_ONLY_SELECT) {
         parser.only_select(UniformSQL.this);
         setParseResult(PARSE_PARTIALLY);
      }
      else if(parseType == PARSE_ONLY_SELECT_FROM) {
         parser.only_select_from(UniformSQL.this);
         setParseResult(PARSE_PARTIALLY);
      }
   }

   /**
    * Get the selection column list.
    * @return the selection column list of the uniform sql.
    */
   @Override
   public synchronized XSelection getSelection() {
      return xselect;
   }

   /**
    * Get a variable value for a name. If the variable is not defined,
    * it returns null.
    * @param name variable name.
    * @return variable definition.
    */
   @Override
   public synchronized UserVariable getVariable(String name) {
      // do nothing
      return null;
   }

   /**
    * Set the value of a variable.
    * @param name the specified variable name.
    * @param value the value of the variable.
    */
   @Override
   public synchronized void setVariable(String name, XValueNode value) {
      // do nothing
   }

   /**
    * Select the root.
    */
   @SuppressWarnings("RedundantThrows")
   @Override
   public synchronized XNode select(XNode root) throws Exception {
      // do nothing
      return root;
   }

   /**
    * Clear the UniformSQL.
    */
   private synchronized void clear() {
      if(xselect != null) {
         xselect.clear();
      }

      tables = new Vector<>();
      fields = new Vector<>();
      orderByList = new Vector<>();
      groups = null;
      where = null;
      having = null;
      distinctKey = false;
      allKey = false;
      grpall = false;
   }

   /**
    * Read from XML.
    * @param node the specified xml node.
    */
   @Override
   public synchronized void parseXML(Element node) throws Exception {
      clear();

      String attr;

      if((attr = Tool.getAttribute(node, "parse")) != null) {
         parseIt = attr.equals("true");
      }

      if((attr = Tool.getAttribute(node, "lossy")) != null) {
         lossy = attr.equals("true");
      }

      NodeList nlist = Tool.getChildNodesByTagName(node, "all");

      if(nlist.getLength() > 0) {
         setAll(true);
      }

      nlist = Tool.getChildNodesByTagName(node, "distinct");

      if(nlist.getLength() > 0) {
         setDistinct(true);
      }

      nlist = Tool.getChildNodesByTagName(node, "table");

      Point loc;
      Point scrollLoc;

      for(int i = 0; i < nlist.getLength(); i++) {
         Element aliasnode = null, namenode = null, issqlnode = null, sqlNameNode = null;
         NodeList list;

         Element table = (Element) nlist.item(i);

         // get the saved location of the table
         Element xLoc = null, yLoc = null;

         list = Tool.getChildNodesByTagName(table, "xlocation");

         if(list.getLength() > 0) {
            xLoc = (Element) list.item(0);
         }

         list = Tool.getChildNodesByTagName(table, "ylocation");

         if(list.getLength() > 0) {
            yLoc = (Element) list.item(0);
         }

         if(xLoc == null || Tool.getValue(xLoc) == null || yLoc == null ||
            Tool.getValue(yLoc) == null)
         {
            loc = new Point(-1, -1);
         }
         else {
            loc = new Point(Integer.parseInt(Tool.getValue(xLoc)),
                            Integer.parseInt(Tool.getValue(yLoc)));
         }

         // get the scrollbar position of the table
         Element xScroll = null, yScroll = null;

         list = Tool.getChildNodesByTagName(table, "xScroll");

         if(list.getLength() > 0) {
            xScroll = (Element) list.item(0);
         }

         list = Tool.getChildNodesByTagName(table, "yScroll");

         if(list.getLength() > 0) {
            yScroll = (Element) list.item(0);
         }

         if(xScroll == null || Tool.getValue(xScroll) == null ||
            yScroll == null || Tool.getValue(yScroll) == null)
         {
            scrollLoc = new Point(0, 0);
         }
         else {
            scrollLoc = new Point(Integer.parseInt(Tool.getValue(xScroll)),
                                  Integer.parseInt(Tool.getValue(yScroll)));
         }

         // get other table attributes
         list = Tool.getChildNodesByTagName(table, "alias");

         if(list.getLength() > 0) {
            aliasnode = (Element) list.item(0);
         }

         list = Tool.getChildNodesByTagName(table, "name");

         if(list.getLength() > 0) {
            namenode = (Element) list.item(0);
         }

         if(namenode == null) {
            list = Tool.getChildNodesByTagName(table, "sqlName");

            if(list.getLength() > 0) {
               sqlNameNode = (Element) list.item(0);
            }
         }

         Element cnode = Tool.getChildNodeByTagName(table, "catalog");
         Element snode = Tool.getChildNodeByTagName(table, "schema");

         list = Tool.getChildNodesByTagName(table, "issql");

         if(list.getLength() > 0) {
            issqlnode = (Element) list.item(0);
         }

         if(aliasnode != null) {
            String alias = Tool.getValue(aliasnode);
            Object name = null;

            if(namenode != null) {
               boolean issql = issqlnode != null &&
                  "true".equals(Tool.getValue(issqlnode));
               name = Tool.getValue(namenode);

               if(issql) {
                  name = new UniformSQL((String) name, false);
               }
            }
            else if(sqlNameNode != null) {
               UniformSQL sqlName = new UniformSQL();
               sqlName.parseXML(Tool.getChildNodeByTagName(sqlNameNode, XML_TAG));
               name = sqlName;
            }

            SelectTable stable = addTable(alias, name, loc, scrollLoc);
            stable.setCatalog(Tool.getValue(cnode));
            stable.setSchema(Tool.getValue(snode));
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "column");

      JDBCSelection selection;

      if(getSelection() != null) {
         selection = (JDBCSelection) getSelection();
      }
      else {
         selection = new JDBCSelection();
         setSelection(selection);
      }

      for(int i = 0; i < nlist.getLength(); i++) {
         Element column, child = null;

         column = (Element) nlist.item(i);

         String columnName = Tool.getValue(column);

         if(columnName == null) {
            columnName = "column" + i;
         }

         selection.addColumn(columnName);

         // set alias
         NodeList list = Tool.getChildNodesByTagName(column, "alias");

         if(list.getLength() > 0) {
            child = (Element) list.item(0);
         }

         if(child != null) {
            selection.setAlias(selection.getColumnCount() - 1,
               (Tool.getValue(child) == null ? "" : Tool.getValue(child)));
         }

         // set type
         list = Tool.getChildNodesByTagName(column, "type");

         if(list.getLength() > 0) {
            child = (Element) list.item(0);
         }

         if(child != null) {
            selection.setType(columnName,
               (Tool.getValue(child) == null ? "" : Tool.getValue(child)));
         }

         // set table of column
         list = Tool.getChildNodesByTagName(column, "table");

         if(list.getLength() > 0) {
            child = (Element) list.item(0);
         }

         if(child != null) {
            selection.setTable(columnName, Tool.getValue(child));
         }

         list = Tool.getChildNodesByTagName(column, "description");

         if(list.getLength() > 0) {
            child = (Element) list.item(0);
         }

         if(child != null) {
            selection.setDescription(columnName, Tool.getValue(child));
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "where");

      if(nlist.getLength() > 0) {
         Element whereElement = (Element) nlist.item(0);
         Element firstElement = null;

         nlist = whereElement.getChildNodes();

         if(nlist != null && nlist.getLength() > 0) {
            for(int i = 0; i < nlist.getLength(); i++) {
               Node cnode = nlist.item(i);

               if(cnode instanceof Element) {
                  firstElement = (Element) cnode;
                  break;
               }
            }
         }

         if(firstElement != null) {
            XFilterNode whereNode = null;

            //noinspection IfCanBeSwitch
            if(firstElement.getTagName().equals(XSet.XML_TAG)) {
               whereNode = new XSet();
            }
            else if(firstElement.getTagName().equals(XJoin.XML_TAG)) {
               whereNode = new XJoin();
            }
            else if(firstElement.getTagName().equals(
               XExpressionCondition.XML_TAG))
            {
               whereNode = new XExpressionCondition();
            }
            else if(firstElement.getTagName().equals(XUnaryCondition.XML_TAG)) {
               whereNode = new XUnaryCondition();
            }
            else if(firstElement.getTagName().equals(XBinaryCondition.XML_TAG)){
               whereNode = new XBinaryCondition();
            }
            else if(firstElement.getTagName().equals(XTrinaryCondition.XML_TAG))
            {
               whereNode = new XTrinaryCondition();
            }

            if(whereNode != null) {
               whereNode.parseXML(firstElement);
               setWhere(whereNode);
            }
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "sortby");

      if(nlist.getLength() > 0) {
         nlist = ((Element) nlist.item(0)).getElementsByTagName("field");

         for(int i = 0; i < nlist.getLength(); i++) {
            Element sortNode = (Element) nlist.item(i);
            String field = Tool.getValue(sortNode);
            String order = Tool.getAttribute(sortNode, "order");
            orderByList.add(new OrderByItem(field, order));
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "groupby");

      if(nlist.getLength() > 0) {
         nlist = ((Element) nlist.item(0)).getElementsByTagName("field");
         groups = new Object[nlist.getLength()];

         for(int i = 0; i < nlist.getLength(); i++) {
            Element groupNode = (Element) nlist.item(i);
            String field = Tool.getValue(groupNode);
            groups[i] = field;
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "orderdbfields");

      if(nlist.getLength() > 0) {
         nlist = ((Element) nlist.item(0)).getElementsByTagName("field");
         orderDBFields = new Vector<>();

         for(int i = 0; i < nlist.getLength(); i++) {
            Element groupNode = (Element) nlist.item(i);
            String field = Tool.getValue(groupNode);
            orderDBFields.add(field);
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "groupdbfields");

      if(nlist.getLength() > 0) {
         nlist = ((Element) nlist.item(0)).getElementsByTagName("field");
         groupDBFields = new Vector<>();

         for(int i = 0; i < nlist.getLength(); i++) {
            Element groupNode = (Element) nlist.item(i);
            String field = Tool.getValue(groupNode);
            groupDBFields.add(field);
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "having");

      if(nlist.getLength() > 0) {
         Element havingElement = (Element) nlist.item(0);
         Element firstElement = null;

         nlist = havingElement.getChildNodes();

         if(nlist != null && nlist.getLength() > 0) {
            for(int i = 0; i < nlist.getLength(); i++) {
               Node cnode = nlist.item(i);

               if(cnode instanceof Element) {
                  firstElement = (Element) cnode;
                  break;
               }
            }
         }

         if(firstElement != null) {
            XFilterNode havingNode = null;

            //noinspection IfCanBeSwitch
            if(firstElement.getTagName().equals(XSet.XML_TAG)) {
               havingNode = new XSet();
            }
            else if(firstElement.getTagName().equals(XJoin.XML_TAG)) {
               havingNode = new XJoin();
            }
            else if(firstElement.getTagName().equals(
               XExpressionCondition.XML_TAG))
            {
               havingNode = new XExpressionCondition();
            }
            else if(firstElement.getTagName().equals(XUnaryCondition.XML_TAG)) {
               havingNode = new XUnaryCondition();
            }
            else if(firstElement.getTagName().equals(XBinaryCondition.XML_TAG)){
               havingNode = new XBinaryCondition();
            }
            else if(firstElement.getTagName().equals(XTrinaryCondition.XML_TAG))
            {
               havingNode = new XTrinaryCondition();
            }

            if(havingNode != null) {
               havingNode.parseXML(firstElement);
               setHaving(havingNode);
            }
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "sqlstring");

      if(nlist.getLength() > 0) {
         Element tag = (Element) nlist.item(0);
         String result = Tool.getAttribute(tag, "parseResult");

         if(result != null) {
            parseResult = Integer.parseInt(result);
            sqlstring = Tool.getValue(tag);
         }
         else {
            // if the result is not in the xml, the sql has not been parsed
            // so we call setSQLString to force it to parse it initially
            setSQLString(Tool.getValue(tag));
         }
      }
      else {
         parseResult = PARSE_SUCCESS;
      }

      Element cinode = Tool.getChildNodeByTagName(node, "columnInfo");

      if(cinode != null) {
         nlist = Tool.getChildNodesByTagName(cinode, "column");
         columns = new XField[nlist.getLength()];

         for(int i = 0; i < nlist.getLength(); i++) {
            Element cnode = (Element) nlist.item(i);
            String type = Tool.getAttribute(cnode, "type");
            String name = Tool.getValue(cnode);
            columns[i] = new XField(name);
            columns[i].setType(type);
         }
      }
   }

   /**
    * Write xml presentation to a print writer.
    * @param writer the specified print writer.
    */
   @Override
   public synchronized void writeXML(PrintWriter writer) {
      writeXML0(writer, false);
   }

   /**
    * Write xml presentation to a print writer.
    * @param writer the specified print writer.
    */
   public synchronized void writeFullXML(PrintWriter writer) {
      writeXML0(writer, true);
   }

   /**
    * Write xml presentation to a print writer.
    * @param writer the specified print writer.
    * @param full whether write full info.
    */
   private synchronized void writeXML0(PrintWriter writer, boolean full) {
      writer.print("<" + XML_TAG + " parse=\"" + parseIt + "\"");

      if(lossy != null) {
         writer.print(" lossy=\"" + lossy + "\"");
      }

      writer.println(">");

      int tableCount = getTableCount();

      if(isAll()) {
         writer.println("<all></all>");
      }
      else if(isDistinct()) {
         writer.println("<distinct></distinct>");
      }

      for(int i = 0; i < tableCount; i++) {
         SelectTable table = tables.elementAt(i);
         Point temp = table.getLocation();
         Point scrollLoc = table.getScrollLocation();
         String alias = table.getAlias();
         Object name = table.getName();
         boolean issql = name instanceof UniformSQL;

         writer.println("<table>");
         writer.println("<alias><![CDATA[" + alias + "]]></alias>");

         if(full && issql) {
            writer.println("<sqlName>");
            ((UniformSQL) name).writeXML0(writer, true);
            writer.println("</sqlName>");
         }
         else {
            writer.println("<name><![CDATA[" + name + "]]></name>");
         }

         writer.println("<issql><![CDATA[" + issql + "]]></issql>");
         writer.println("<xlocation><![CDATA[" + temp.x + "]]></xlocation>");
         writer.println("<ylocation><![CDATA[" + temp.y + "]]></ylocation>");
         writer.println("<xScroll><![CDATA[" + scrollLoc.x + "]]></xScroll>");
         writer.println("<yScroll><![CDATA[" + scrollLoc.y + "]]></yScroll>");

         String catalog = table.getCatalog();

         if(catalog != null) {
            writer.println("<catalog><![CDATA[" + catalog + "]]></catalog>");
         }

         String schema = table.getSchema();

         if(schema != null) {
            writer.println("<schema><![CDATA[" + schema + "]]></schema>");
         }

         writer.println("</table>");
      }

      JDBCSelection selection = (JDBCSelection) getSelection();
      int columnCount = selection.getColumnCount();

      for(int i = 0; i < columnCount; i++) {
         writer.println("<column>");
         String column = selection.getColumn(i);
         String alias = selection.getAlias(i);
         String type = selection.getType(column);
         String tname = selection.getTable(column);
         String desc = selection.getDescription(column);

         if(full && tname == null && alias != null) {
            tname = selection.getTable(alias);
         }

         writer.println("<![CDATA[" + column + "]]>");
         writer.println("<alias><![CDATA[" + (alias == null ? "" : alias) +
                        "]]></alias>");
         writer.println("<type><![CDATA[" + (type == null ? "" : type) +
                        "]]></type>");
         writer.println("<table><![CDATA[" + (tname == null ? "" : tname) +
                        "]]></table>");
         writer.println("<description><![CDATA[" + (desc == null ? "" : desc) +
                        "]]></description>");
         writer.println("</column>");
      }

      writer.println("<where>");

      if(where != null) {
         where.writeXML(writer);
      }

      writer.println("</where>");

      writer.print("<sortby>");
      Object[] orderField = this.getOrderByFields();

      for(int i = 0; orderField != null && i < orderField.length; i++) {
         writer.print("<field order=\"" + this.getOrderBy(orderField[i]) +
                      "\"><![CDATA[");
         writer.print(orderField[i].toString());
         writer.print("]]></field>");

         if(i != orderField.length - 1) {
            writer.print(",");
         }
      }

      writer.println("</sortby>");

      writer.print("<groupby>");
      Object[] groupby = this.getGroupBy();

      for(int i = 0; groupby != null && i < groupby.length; i++) {
         writer.print("<field><![CDATA[");
         writer.print(groupby[i].toString());
         writer.print("]]></field>");
      }

      writer.println("</groupby>");

      Vector<String> orderDBFlds = this.orderDBFields;

      if(orderDBFlds != null && orderDBFlds.size() > 0) {
         writer.println("<orderdbfields>");

         for(int i = 0; i < orderDBFlds.size(); i++) {
            writer.print("<field><![CDATA[");
            writer.print(orderDBFlds.get(i));
            writer.print("]]></field>");
         }

         writer.println("</orderdbfields>");
      }

      Vector<String> groupDBFlds = this.groupDBFields;

      if(groupDBFlds != null && groupDBFlds.size() > 0) {
         writer.println("<groupdbfields>");

         for(int i = 0; i < groupDBFlds.size(); i++) {
            writer.print("<field><![CDATA[");
            writer.print(groupDBFlds.get(i));
            writer.print("]]></field>");
         }

         writer.println("</groupdbfields>");
      }

      writer.println("<having>");

      if(having != null) {
         having.writeXML(writer);
      }

      writer.println("</having>");

      if(sqlstring != null) {
         writer.println("<sqlstring parseResult=\"" + parseResult +
                        "\"><![CDATA[" + sqlstring + "]]></sqlstring>");
      }

      if(columns != null) {
         writer.println("<columnInfo>");

         for(XField fld : columns) {
            writer.println("<column type=\"" + fld.getType() + "\">");
            writer.println("<![CDATA[" + fld.getName() + "]]>");
            writer.println("</column>");
         }

         writer.println("</columnInfo>");
      }


      writer.println("</" + XML_TAG + ">");
   }

   /**
    * Set the SQL string. If SQL string is supplied, it overrides the
    * structured SQL definition and is used directly with the database.
    * @param sqlstring the specified sql statement.
    */
   public synchronized void setSQLString(String sqlstring) {
      setSQLString(sqlstring, true);
   }

   /**
    * Set the SQL string. If SQL string is supplied, it overrides the
    * structured SQL definition and is used directly with the database.
    * @param sqlstring the specified sql statement.
    */
   public synchronized void setSQLString(String sqlstring, boolean parse) {
      this.cstring = null;
      this.sqlstring = null;

      if(parse) {
         Vector<Point> locPoints = new Vector<>();
         Vector<Point> scrollPoints = new Vector<>();
         SelectTable table;

         // saving the table locations
         for(int i = 0; i < tables.size(); i++) {
            table = tables.elementAt(i);
            locPoints.addElement(new Point(table.getLocation()));
            scrollPoints.addElement(table.getScrollLocation());
         }

         if(sqlstring != null) {
            // @by larryl, user explicitly selected not to parse, don't try to
            // parse since parsing may be expensive (and freeze the qb).
            if(parseIt) {
               parse(sqlstring);
            }
            else {
               this.sqlstring = sqlstring;
               setParseResult(PARSE_INIT);
            }
         }
         else {
            setParseResult(PARSE_SUCCESS);
         }

         for(int i = 0; i < locPoints.size() && i < tables.size(); i++) {
            table = tables.elementAt(i);
            table.setLocation(locPoints.elementAt(i));
            table.setScrollLocation(scrollPoints.elementAt(i));
         }
      }
      else {
         this.sqlstring = sqlstring;
      }
   }

   /**
    * Clear the cached sql string, so that the sql string will be generated
    * from sql helper directly.
    */
   public synchronized void clearSQLString() {
      sqlstring = null;
   }

   /**
    * Get the sql string.
    * @return the sql string.
    */
   @Override
   public synchronized String getSQLString() {
      if(sqlstring != null) {
         return sqlstring;
      }

      SQLHelper helper = getSQLHelper();
      Object inmax = getHint(HINT_INPUT_MAXROWS, true);
      Object outmax = getHint(HINT_OUTPUT_MAXROWS, false);

      if(inmax != null) {
         helper.setInputMaxRows(Integer.parseInt(inmax.toString()));
      }

      if(outmax != null) {
         helper.setOutputMaxRows(Integer.parseInt(outmax.toString()));
      }

      // @by larryl, if the sql string is generated, we mark it as being good
      // so if we switch to sql pane and switch back to main pane, we don't
      // need to parse it again to make sure the switch can happen
      String str = helper.generateSentence();

      if(str != null && !"".equals(str.trim())) {
         setParseResult(PARSE_SUCCESS);
      }
      else {
         setParseResult(PARSE_FAILED);
      }

      return str;
   }

   /**
    * Check if the SQL string is defined. If it is, it overrides the other
    * definitions.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public synchronized boolean hasSQLString() {
      return sqlstring != null;
   }

   /**
    * Get the number of tables in the 'from' clause.
    * @return the number of tables.
    */
   public synchronized int getTableCount() {
      return tables.size();
   }

   /**
    * Add a table to table list use its name as alias.
    * @param alias table alias.
    * @return the newly added SelectTable object. Return null if the table
    * already exists and is not added.
    */
   public synchronized SelectTable addTable(Object alias) {
      return addTable(alias.toString(), alias);
   }

   /**
    * Add table into table list with alias and name.
    * @param alias table alias.
    * @param name table fully qualified name.
    * @return the newly added SelectTable object. Return null if the table
    * already exists and is not added.
    */
   public synchronized SelectTable addTable(String alias, Object name) {
      return addTable(alias, name, null, null);
   }

   /**
    * Add table into table list with alias and name.
    * @param alias table alias.
    * @param name table fully qualified name.
    * @param loc table location on the link pane.
    * @return the newly added SelectTable object. Return null if the table
    * already exists and is not added.
    */
   public synchronized SelectTable addTable(String alias, Object name,
                                            Point loc, Point scroll) {
      return addTable(alias, name, loc, scroll, true);
   }

   public synchronized SelectTable addTable(String alias, Object name, Point loc, Point scroll,
                                            boolean returnNullIfExist)
   {
      SelectTable tbl = new SelectTable(alias, name);

      if(loc != null) {
         tbl.setLocation(loc);
      }

      if(scroll != null) {
         tbl.setScrollLocation(scroll);
      }

      if(!tables.contains(tbl)) {
         tables.add(tbl);
         return tbl;
      }

      if(returnNullIfExist) {
         return null;
      }

      int idx = tables.indexOf(tbl);
      return tables.get(idx);
   }

   public synchronized void addTable(SelectTable table) {
      tables.add(table);
   }

   /**
    * Remove specified table by its alias.
    * @param alias the specified table alias.
    */
   public synchronized void removeTable(String alias) {
      SelectTable table;

      for(int i = 0; i < tables.size(); i++) {
         table = tables.elementAt(i);

         if(table.getAlias().equalsIgnoreCase(alias)) {
            tables.removeElementAt(i);
            return;
         }
      }
   }

   /**
    * Get the metadata node.
    */
   @SuppressWarnings("unused")
   public synchronized XNode getMetaDataNode() {
      return root;
   }

   /**
    * Set the metadata node.
    */
   public synchronized void setMetaDataNode(XNode root) {
      this.root = root;
   }

   public void updateUniformSQL(DefaultMetaDataProvider provider, ChangeTableOptionInfo info,
                                List<RenameInfo> rinfos, String additional)
   {
      Vector<SelectTable> otables = (Vector<SelectTable>) Tool.clone(tables);
      updateSelectTable(provider, info, rinfos, additional);
      updateXSelection(otables, rinfos);
      updateOrderByFields(rinfos);
      updateGroupFields(rinfos);
      //update where
      updateXFilterNode(provider, where, info, rinfos, additional);
      //update having
      updateXFilterNode(provider, having, info, rinfos, additional);
   }

   public void updateOrderAndGroupFields(String oldName, String newName) {
      for(int i = 0; i < orderByList.size(); i++) {
         OrderByItem item = orderByList.get(i);
         Object field = item.getField();

         if(!(field instanceof String)) {
            continue;
         }

         if(Tool.equals(field, oldName)) {
            item.setField(newName);
         }
      }

      Object[] groupFields = getGroupBy();

      for(int i = 0; groupFields != null && i < groupFields.length; i++) {
         String group = groupFields[i].toString();

         if(Tool.equals(group, oldName)) {
            groupFields[i] = newName;
         }
      }
   }

   private void updateXFilterNode(DefaultMetaDataProvider provider, XFilterNode filterNode,
                                  ChangeTableOptionInfo info, List<RenameInfo> rinfos,
                                  String additional)
   {
      if(filterNode == null) {
         return;
      }

      if(filterNode instanceof XSet) {
         XSet xSet = (XSet) filterNode;
         XNode node;

         for(int i = 0; i < xSet.getChildCount(); i++) {
            node = xSet.getChild(i);

            if(node instanceof XFilterNode) {
               updateXFilterNode(provider, (XFilterNode) node, info, rinfos, additional);
            }
         }
      }
      else {
         XExpression expression1 = filterNode.getExpression1();
         XExpression expression2 = null;
         XExpression expression3 = null;

         if(filterNode instanceof XBinaryCondition) {
            expression2 = ((XBinaryCondition) filterNode).getExpression2();
         }
         else if(filterNode instanceof XTrinaryCondition) {
            expression2 = ((XTrinaryCondition) filterNode).getExpression2();
            expression3 = ((XTrinaryCondition) filterNode).getExpression3();
         }

         updateExpression(expression1, provider, info, rinfos, additional);
         updateExpression(expression2, provider, info, rinfos, additional);
         updateExpression(expression3, provider, info, rinfos, additional);
      }
   }

   private void updateExpression(XExpression expression, DefaultMetaDataProvider provider,
                                 ChangeTableOptionInfo info, List<RenameInfo> rinfos,
                                 String additional)
   {
      if(expression != null) {
         Object value = expression.getValue();

         if(value instanceof UniformSQL) {
            ((UniformSQL) value).updateUniformSQL(provider, info, rinfos, additional);
         }
         else if(value instanceof String){
            boolean found = false;
            String stringValue = (String) value;

            for(int i = 0; i < rinfos.size(); i++) {
               String v = stringValue.lastIndexOf(".") != -1 ? XUtil.getTablePart(stringValue) : "";
               RenameInfo rinfo = rinfos.get(i);

               if(Objects.equals(rinfo.getOldName(), v)) {
                  found = true;
                  break;
               }
            }

            if(found) {
               expression.setValue(fixFieldName(stringValue, rinfos));
            }
         }
      }
   }

   private void updateGroupFields(List<RenameInfo> rinfos) {
      Object[] groupby = this.getGroupBy();

      for(int i = 0; groupby != null && i < groupby.length; i++) {
         String group = groupby[i].toString();

         if(!getSelection().isAlias(group)) {
            groupby[i] = fixFieldName(group, rinfos);
         }
      }
   }

   private void updateOrderByFields(List<RenameInfo> rinfos) {
      for(int i = 0; i < orderByList.size(); i++) {
         OrderByItem item = orderByList.get(i);
         Object field = item.getField();

         if(!(field instanceof String)) {
            continue;
         }

         String fieldName = fixFieldName((String) field, rinfos);
         item.setField(fieldName);
      }
   }

   private void updateXSelection(Vector<SelectTable> otables, List<RenameInfo> rinfos) {
      JDBCSelection selection = (JDBCSelection) getSelection();
      int columnCount = selection.getColumnCount();

      for(int i = 0; i < columnCount; i++) {
         String column = selection.getColumn(i);
         String tname = selection.getTable(column);

         if(hasAlias(otables, tname)) {
            continue;
         }

         for(RenameInfo rinfo : rinfos) {
            if(Objects.equals(rinfo.getOldName(), tname) && !Objects.equals(tname, rinfo.getNewName())) {
               String nTableName = rinfo.getNewName();
               String ncolname = nTableName + "." + column.substring(tname.length() + 1);
               selection.setColumn(i, ncolname);
               String type = selection.getType(column);
               selection.setType(column, null);
               selection.setType(ncolname, type);
               selection.setTable(column, null);
               selection.setTable(ncolname, nTableName);
               String desc = selection.getDescription(column);
               selection.setDescription(column, null);
               selection.setDescription(ncolname, desc);
            }
         }
      }
   }

   private boolean hasAlias(Vector<SelectTable> otables, String tname) {
      return otables.stream().anyMatch(otable -> {
         String oname = (otable.getName() instanceof String) ? (String) otable.getName() : null;
         String oalias = otable.getAlias();
         return oalias != null && !Objects.equals(oname, oalias) && Objects.equals(oalias, tname);
      });
   }

   private void updateSelectTable(DefaultMetaDataProvider provider, ChangeTableOptionInfo info,
                                  List<RenameInfo> rinfos, String additional)
   {
      for(int i = 0; i < getTableCount(); i++) {
         fixSelectTable(getSelectTable(i), info, provider, rinfos, additional);
      }
   }

   public static String fixFieldName(String field, List<RenameInfo> rinfos) {
      if(field.indexOf('.') != -1) {
         String tname = XUtil.getTablePart(field);
         String ntableName = getNewTableName(tname, rinfos);

         if(ntableName == null) {
            return field;
         }

         return ntableName + "." + field.substring(tname.length() + 1);
      }

      return field;
   }

   /**
    * Fix a select table.
    */
   private void fixSelectTable(SelectTable stable, ChangeTableOptionInfo info,
                               DefaultMetaDataProvider provider, List<RenameInfo> rinfos,
                               String additional)
   {
      if(stable == null || !(stable.getName() instanceof String)) {
         return;
      }

      String name = (String) stable.getName();
      String alias = stable.getAlias();
      XNode node = DataDependencyTransformer.getTable(provider, name, additional);

      if(node == null) {
         return;
      }

      String catalog = (String) node.getAttribute("catalog");
      String schema = (String) node.getAttribute("schema");
      String ntableName = DataDependencyTransformer.getQualifiedTableName(
         name, info.getNewOption(), provider, additional);

      if(Objects.equals(name, ntableName)) {
         return;
      }

      stable.setCatalog(catalog);
      stable.setSchema(schema);
      stable.setName(ntableName);

      if(Tool.equals(name, alias)) {
         stable.setAlias(ntableName);
      }

      clearSQLString();
      rinfos.add(new RenameInfo(name, ntableName, RenameInfo.QUERY | RenameInfo.TABLE));
   }

   private static String getNewTableName(String tname, List<RenameInfo> rinfos) {
      for(RenameInfo rinfo : rinfos) {
         if(Objects.equals(rinfo.getOldName(), tname) && !Objects.equals(tname, rinfo.getNewName())) {
            return rinfo.getNewName();
         }
      }

      return null;
   }

   /**
    * Sychronize the model when selected table(s) change(s).
    */
   public synchronized void syncTable() {
      syncSorting();
      syncGrouping();
   }

   /**
    * Synchronize the sorting conditions when selected tables change.
    */
   @SuppressWarnings("WeakerAccess")
   public synchronized void syncSorting() {
      Object[] sortFields = getOrderByFields();

      if(sortFields == null) {
         return;
      }

      // remove order by columns that does not have table
      List<String> vec = new ArrayList<>();

      for(Object sortField : sortFields) {
         String fname = sortField.toString();

         if(getFieldByPath(fname) != null || getField(fname) != null ||
            getSelection().getAliasColumn(fname) != null) {
            vec.add(fname);
         }
      }

      boolean changed = false;

      // removed some fields
      if(vec.size() != sortFields.length) {
         sortFields = vec.toArray(new String[0]);
         changed = true;
      }

      String[] orders = new String[sortFields.length];

      for(int i = 0; i < sortFields.length; i++) {
         orders[i] = getOrderBy(sortFields[i]);

         if(sortFields[i] instanceof Integer) {
            int idx = (Integer) sortFields[i];

            if(idx <= 0 || idx > getSelection().getColumnCount()) {
               continue;
            }

            String path = getSelection().getColumn(idx - 1);

            if(path != null) {
               String alias = getSelection().getAlias(idx - 1);

               if(getDataSource() == null ||
                  getDataSource().getDatabaseType() == JDBCDataSource.JDBC_ODBC)
               {
                  sortFields[i] = path;
               }
               else {
                  sortFields[i] = alias == null || alias.length() == 0 ?
                     path : alias;
               }

               changed = true;
            }
         }
         else if(sortFields[i] instanceof String &&
            // @by larryl, if sort by defined on alias, keep as is otherwise
            // the fullpath may be pointing to a wrong column
            getSelection().getAliasColumn((String) sortFields[i]) == null)
         {
            String fp = JDBCUtil.getFullPathOf(this, (String) sortFields[i]);

            if(fp != null && !fp.equals(sortFields[i])) {
               sortFields[i] = fp;
               changed = true;
            }
         }
      }

      if(changed) {
         removeAllOrderByFields();

         for(int i = 0; i < sortFields.length; i++) {
            setOrderBy(sortFields[i], orders[i]);
         }
      }
   }

   /**
    * Synchronize the grouping conditions when selected tables change.
    */
   @SuppressWarnings("WeakerAccess")
   public synchronized void syncGrouping() {
      Object[] groupBy = getGroupBy();

      if(groupBy == null) {
         return;
      }

      for(int i = 0; i < groupBy.length; i++) {
         if(groupBy[i] instanceof Integer) {
            int idx = (Integer) groupBy[i];

            if(idx <= 0) {
               continue;
            }

            String path = getSelection().getColumn(idx - 1);

            if(path != null) {
               XField field = getFieldByPath(path);

               if(field != null && field.getTable().length() > 0) {
                  groupBy[i] = path;
               }
            }
         }
         else if(groupBy[i] instanceof String &&
            // @by larryl, if group by defined on alias, keep as is otherwise
            // the fullpath may be pointing to a wrong column
            getSelection().getAliasColumn((String) groupBy[i]) == null)
         {
            String fp = JDBCUtil.getFullPathOf(this, (String) groupBy[i]);

            if(fp != null && !fp.equals(groupBy[i])) {
               groupBy[i] = fp;
            }
         }
      }

      // remove group by columns that does not have table
      List<Object> vec = new ArrayList<>();

      for(Object obj : groupBy) {
         if(obj instanceof String) {
            String fname = obj.toString();

            if(getFieldByPath(fname) != null || getField(fname) != null ||
               getSelection().getAliasColumn(fname) != null) {
               vec.add(obj);
            }
         }
      }

      // removed some fields
      if(vec.size() != groupBy.length) {
         setGroupBy(vec.toArray(new Object[0]));
      }
   }

   /**
    * When the table names changed, check all the definition to replace.
    * @param aliasMap oldAlias->newAlias, oldAlias may equals to newAlias.
    */
   public synchronized void syncTableAlias(Hashtable<String, String> aliasMap) {
      // fields
      XField[] fields = getFieldList();

      if(fields != null) {
         for(XField field : fields) {
            String tname = field.getTable();

            if(!Tool.isEmptyString(tname)) {
               tname = (String) aliasMap.get(tname);

               if(tname != null) {
                  field.setTable(tname);
               }
               else {
                  removeFieldByPath(field.getTable() + "." +
                                       field.getName());
               }
            }
            else {
               field.setName(
                  JDBCUtil.replaceTableInExpression(tables, field.getName().toString(), aliasMap, dataSource));
            }
         }
      }

      // selection
      XSelection xselect = getSelection();

      for(int i = xselect.getColumnCount() - 1; i >= 0; i--) {
         String path = xselect.getColumn(i);

         if(isTableColumn(path)) {
            String oldTableAlias = getTableFromPath(path);

            if(aliasMap.containsKey(oldTableAlias)) {
               String newTableAlias = (String) aliasMap.get(oldTableAlias);

               if(newTableAlias == null) {  // old table is removed
                  xselect.removeColumn(path);
                  continue;
               }
               else if(newTableAlias.equals(oldTableAlias)) {
                  continue;
               }

               String col = getColumnFromPath(path);
               String alias = xselect.getAlias(i);

               if(alias != null && alias.startsWith(oldTableAlias + ".")) {
                  alias = newTableAlias + "." +
                     alias.substring(oldTableAlias.length() + 1);
               }

               String type = xselect.getType(path);
               String newPath = newTableAlias + "." + col;

               xselect.setColumn(i, newPath);
               xselect.setAlias(i, alias);
               xselect.setType(newPath, type);
               xselect.setDescription(newPath, xselect.getDescription(path));
            }
            else if(oldTableAlias.length() > 0 && getTableIndex(oldTableAlias) < 0) {
               XField field = getFieldByPath(path);

               if(field == null || field.getTable().length() > 0) {
                  int dot = path.lastIndexOf('.');

                  if(dot > 0) {
                     xselect.removeColumn(path);
                  }
               }
            }
         }
         else {
            String alias = xselect.getAlias(i);
            String type = xselect.getType(path);
            String description = xselect.getDescription(path);
            String newPath = JDBCUtil.replaceTableInExpression(tables, path, aliasMap, dataSource);
            xselect.setColumn(i, newPath);
            xselect.setAlias(i, alias);
            xselect.setType(newPath, type);
            xselect.setDescription(newPath, description);
         }
      }

      // where
      if(!syncTableAliasInConditions(getWhere(), aliasMap)) {
         if((getWhere() instanceof XSet) && getWhere().getChildCount() > 0) {
            setWhere((XFilterNode) getWhere().getChild(0));
         }
      }

      // order by
      Object[] orders = getOrderByFields();

      for(int i = 0; orders != null && i < orders.length; i++) {
         if(orders[i] instanceof String) {
            if(xselect.isAlias((String) orders[i])) {
               continue;
            }

            String oldTableAlias = getTableFromPath((String) orders[i]);

            if(aliasMap.containsKey(oldTableAlias)) {
               String newTableAlias = (String) aliasMap.get(oldTableAlias);

               if(newTableAlias == null || newTableAlias.equals(oldTableAlias)) {
                  continue;
               }

               String col = getColumnFromPath((String) orders[i]);
               String ord = getOrderBy(orders[i]);
               replaceOrderBy(orders[i], ord, newTableAlias + "." + col, ord);
            }
         }
      }

      // group by
      for(int i = 0; groups != null && i < groups.length; i++) {
         if(groups[i] instanceof String) {
            if(xselect.isAlias((String) groups[i])) {
               continue;
            }

            String oldTableAlias = getTableFromPath((String) groups[i]);

            if(aliasMap.containsKey(oldTableAlias)) {
               String newTableAlias = (String) aliasMap.get(oldTableAlias);

               if(newTableAlias == null) {
                  continue;
               }

               String col = getColumnFromPath((String) groups[i]);
               groups[i] = newTableAlias + "." + col;
            }
         }
      }

      // having
      if(!syncTableAliasInConditions(getHaving(), aliasMap)) {
         if((getHaving() instanceof XSet) && getHaving().getChildCount() > 0) {
            setHaving((XFilterNode) getHaving().getChild(0));
         }
      }
   }

   /**
    * Replace the table alias in the conditions.
    * @param node the specified filter node.
    * @param aliasmap the specified alias map.
    * @return <tt>true</tt> changed, <tt>false</tt> otherwise.
    */
   @SuppressWarnings("BooleanMethodIsAlwaysInverted")
   private synchronized boolean syncTableAliasInConditions(XFilterNode node,
                                                           Hashtable aliasmap){
      boolean b = true;

      if(node == null) {
         return true;
      }
      else if(node instanceof XSet) {
         for(int i = node.getChildCount() - 1; i >= 0; i--) {
            XFilterNode child = (XFilterNode) node.getChild(i);

            if(!syncTableAliasInConditions(child, aliasmap)) {
               if((child instanceof XSet) && child.getChildCount() > 0) {
                  node.setChild(i, child.getChild(0));
               }
               else {
                  node.removeChild(i);
               }
            }
         }

         return node.getChildCount() > 1;
      }
      else if(node instanceof XUnaryCondition) {
         b = syncTableAliasInExpression(
            ((XUnaryCondition) node).getExpression1(), aliasmap);
      }
      else if(node instanceof XBinaryCondition) {
         b = syncTableAliasInExpression(
             ((XBinaryCondition) node).getExpression1(), aliasmap);
         b = b && syncTableAliasInExpression(
             ((XBinaryCondition) node).getExpression2(), aliasmap);
      }
      else if(node instanceof XTrinaryCondition) {
         b = syncTableAliasInExpression(
            ((XTrinaryCondition) node).getExpression1(), aliasmap);
         b = b && syncTableAliasInExpression(
            ((XTrinaryCondition) node).getExpression2(), aliasmap);
         b = b && syncTableAliasInExpression(
            ((XTrinaryCondition) node).getExpression3(), aliasmap);
      }

      return b;
   }

   /**
    * Replace the table alias in the expression.
    * @param expr the specified expression.
    * @param aliasmap the specified alias map.
    * @return <tt>false</tt> if the table of fields doesn't exist any more,
    *         <tt>true</tt> otherwise.
    */
   private synchronized boolean syncTableAliasInExpression(XExpression expr,
                                                           Hashtable aliasmap) {
      if(!XExpression.FIELD.equals(expr.getType())) {
         return true;
      }

      if(expr.getValue() instanceof String) {
         if(JDBCUtil.isAggregateExpression((String) expr.getValue())) {
            String newExpression =
               JDBCUtil.replaceTableInExpression(tables, expr.getValue().toString(), aliasmap, dataSource);
            expr.setValue(newExpression, XExpression.FIELD);
            return true;
         }

         String path = (String) expr.getValue();
         String table = getTableFromPath(path);
         String quote = getSQLHelper().getQuote();

         if(aliasmap.containsKey(table)) {
            String newTable = (String) aliasmap.get(table);

            if(newTable == null) {
               return false;
            }

            String col = getColumnFromPath(path);

            if(!col.startsWith(newTable)) {
               expr.setValue(newTable + "." + col, XExpression.FIELD);
            }

            return true;
         }
         else if(table.contains(quote) && table.contains(".")) {
            String originalTable = table.replace(quote, "");

            if(aliasmap.containsKey(originalTable)) {
               return true;
            }
         }
         else {
            return aliasmap.contains(table);
         }
      }

      return false;
   }

   /**
    * Get the table name part from the path. Return "" if not indicated.
    */
   public synchronized String getTableFromPath(String path) {
      XField fld = getFieldByPath(path);

      if(fld != null && !"".equals(fld.getTable())) {
         return fld.getTable();
      }

      int idx = path.lastIndexOf('.');
      return idx < 0 ? "" : XUtil.getTablePart(path);
   }

   /**
    * Get the column name part from the path.
    */
   public synchronized String getColumnFromPath(String path) {
      XField fld = getFieldByPath(path);

      if(fld != null) {
         return fld.getName().toString();
      }

      // @by billh 2010-8-5, use the last dot is very dangerous
      SelectTable[] tables = getSelectTable();

      for(SelectTable table : tables) {
         String table_alias = table.getAlias();
         Object table_name = table.getName();
         String prefix = table_alias + ".";

         if(path.startsWith(prefix)) {
            String spath = path.substring(prefix.length());
            return XUtil.removeQuote(spath);
         }

         if(!(table_name instanceof String)) {
            continue;
         }

         prefix = table_name + ".";

         if(path.startsWith(prefix)) {
            String spath = path.substring(prefix.length());
            return XUtil.removeQuote(spath);
         }
      }

      int idx = path.lastIndexOf('.');
      return idx < 0 ? path : XUtil.getColumnPart(path);
   }

   /**
    * Remove an order by field.
    */
   @SuppressWarnings("unused")
   public synchronized void removeOrderBy(Object field) {
      for(int i = 0; i < orderByList.size(); i++) {
         if(orderByList.get(i).getField().equals(field)) {
            orderByList.remove(i);
            return;
         }
      }
   }

   /**
    * Get the selected table by index.
    */
   public synchronized SelectTable getSelectTable(int index) {
      if(index >= tables.size()) {
         return null;
      }

      return tables.elementAt(index);
   }

   /**
    * Get the alias of selected table by index.
    */
   public synchronized String getTableAlias(int index) {
      if(index >= 0 && index < tables.size()) {
         SelectTable table = tables.elementAt(index);
         return table.getAlias();
      }

      return null;
   }

   /**
    * Get the alias of the selected table by name.
    */
   public synchronized String getTableAlias(String name) {
      int size = tables.size();

      for(int i = 0; i < size; i++) {
         SelectTable table = tables.elementAt(i);

         if(table.getName() instanceof String &&
            name.equalsIgnoreCase((String) table.getName()))
         {
            return table.getAlias();
         }
      }

      return null;
   }

   /**
    * Get the location of selected table by index.
    */
   public synchronized Point getTableLocation(int index) {
      if(index >= 0 && index < tables.size()) {
         SelectTable table;

         table = tables.elementAt(index);
         return table.getLocation();
      }

      return null;
   }

   /**
    * Get the location of selected table by index.
    */
   public synchronized Point getTableScrollLocation(int index) {
      if(index >= 0 && index < tables.size()) {
         SelectTable table;

         table = tables.elementAt(index);
         return table.getScrollLocation();
      }

      return null;
   }

   /**
    * Get the name of the selected table by alias.
    */
   public synchronized Object getTableName(String alias) {
      if(alias == null) {
         return null;
      }

      for(int i = 0; i < tables.size(); i++) {
         SelectTable table = tables.elementAt(i);

         if(table.getAlias().equalsIgnoreCase(alias)) {
            return table.getName();
         }
      }

      return null;
   }

   /**
    * Get the selected table by alias.
    */
   public synchronized SelectTable getSelectTable(String alias) {
      if(alias == null) {
         return null;
      }

      for(int i = 0; i < tables.size(); i++) {
         SelectTable table = tables.elementAt(i);

         if(table.getAlias().equalsIgnoreCase(alias)) {
            return table;
         }
         else if(alias.equalsIgnoreCase(table.getName().toString())) {
            return table;
         }
      }

      return null;
   }

   /**
    * Return the select table list.
    */
   public synchronized SelectTable[] getSelectTable() {
      SelectTable[] selectTable = new SelectTable[tables.size()];

      tables.copyInto(selectTable);
      return selectTable;
   }

   /**
    * Get a field by its alias.
    * @param alias the specified alias.
    * @return field object the associated field.
    */
   public synchronized XField getField(String alias) {
      try {
         int index = Integer.parseInt(alias);

         if(index >= 1 && index <= fields.size()) {
            return fields.elementAt(index - 1);
         }
      }
      catch(Exception ex) {
         // ignore number format exception
      }

      // @by larryl, alias could be all digits, this was in the catch() block
      // which would ignore the alias with all digits
      for(int i = 0; i < fields.size(); i++) {
         XField field = fields.elementAt(i);
         String fa = field.getAlias();

         if(fa != null && fa.equalsIgnoreCase(alias)) {
            return field;
         }
      }

      return null;
   }

   /**
    * Trim the path.
    * @param path the specified table/column path.
    * @return the trimed result.
    */
   private String trimPath(String path) {
      int length = path == null ? 0 : path.length();

      if(length == 0) {
         return path;
      }

      StringBuilder sb = null;
      int last = -1;
      int index;

      while((index = path.indexOf("\"", last + 1)) >= 0) {
         if(sb == null) {
            sb = new StringBuilder();
         }

         sb.append(path, last + 1, index);
         last = index;
      }

      if(last == -1) {
         return path;
      }

      sb.append(path.substring(last + 1));
      return sb.toString();
   }

   /**
    * Get a field by its full path.
    * @param path the path of field in format of "table.col".
    */
   public synchronized XField getFieldByPath(String path) {
      String path2 = trimPath(path); // "a.b".c --> a.b.c
      int idx = path2.lastIndexOf('.');
      String col = idx < 0 ? path : path2.substring(idx + 1);
      String table = idx < 0 ? "" : path2.substring(0, idx);

      for(int i = 0; i < fields.size(); i++) {
         XField field = fields.elementAt(i);
         String fname = field.getName() == null ? "" : field.getName().toString();
         String ftable = field.getTable();
         String ftable2 = trimPath(ftable);

         if((fname.equalsIgnoreCase(col) &&
             (ftable.equalsIgnoreCase(table) ||
              ftable2.equalsIgnoreCase(table))) ||   // column
            (fname.equalsIgnoreCase(col) &&
             table.length() == 0) ||   // column name without table name
            (fname.equalsIgnoreCase(path) &&
             ftable.length() == 0))   // expression
         {
            return field;
         }

         if(field.getTable().length() != 0 &&
            path2.startsWith(field.getTable() + "."))
         {
            idx = field.getTable().length() + 1;
            col = path2.substring(idx);

            if(fname.equalsIgnoreCase(col)) {
               return field;
            }
         }
      }

      return null;
   }

   /**
    * Get field list. The field list contains all columns of tables in the
    * query. It is used primarily at design time.
    */
   public synchronized XField[] getFieldList() {
      XField[] arr = new XField[fields.size()];

      fields.copyInto(arr);
      return arr;
   }

   /**
    * Get field list. The field list contains all columns of tables in the
    * query. It is used primarily at design time.
    */
   public synchronized XField[] getFieldList(boolean includingExpression) {
      List<XField> list = new ArrayList<>(fields.size());

      for(int i = 0; i < fields.size(); i++) {
         XField field = fields.elementAt(i);

         if(includingExpression || field.getTable().length() != 0) {
            list.add(field);
         }
      }

      return list.toArray(new XField[0]);
   }

   /**
    * Add a field into field list.
    * @param field the specified field.
    */
   public synchronized void addField(XField field) {
      fields.add(field);
   }

   /**
    * Remove a field by its full path.
    * @param path the path of field in format of "table.col".
    */
   public synchronized void removeFieldByPath(String path) {
      int idx = path.lastIndexOf('.');
      String col = idx < 0 ? path : path.substring(idx + 1);
      String table = idx < 0 ? "" : path.substring(0, idx);

      for(int i = 0; i < fields.size(); i++) {
         XField field = fields.elementAt(i);
         String fname = field.getName() == null ? "" :
            field.getName().toString();

         if((fname.equalsIgnoreCase(col) &&
             field.getTable().equalsIgnoreCase(table)) ||   // column
            (fname.equalsIgnoreCase(col) &&
             table.length() == 0) ||   // column name without table name
            (fname.equalsIgnoreCase(path) &&
             field.getTable().length() == 0))   // expression
         {
            fields.remove(i);
            return;
         }
      }
   }

   /**
    * Check if is a table column.
    * @return <tt>true</tt> if is a table column, <tt>false</tt> otherwise.
    */
   public synchronized boolean isTableColumn(String column) {
      return getTable(column) != null;
   }

   public String getTable(String column) {
      return getTable(column, true);
   }

   /**
    * Get the table of a column in selection.
    * @return the table of a column.
    */
   public synchronized String getTable(String column, boolean checkExpression) {
      String tname = ((JDBCSelection) xselect).getTable(column);

      if(tname == null) {
         tname = ((JDBCSelection) xselect2).getTable(column);
      }

      if(tname == null) {
         int index = xselect.indexOf(column);

         if(index >= 0) {
            String alias = xselect.getAlias(index);

            if(alias != null && alias.length() > 0) {
               tname = ((JDBCSelection) xselect).getTable(alias);
            }
         }
      }

      if(tname == null) {
         int index = xselect2.indexOf(column);

         if(index >= 0) {
            String alias = xselect2.getAlias(index);

            if(alias != null && alias.length() > 0) {
               tname = ((JDBCSelection) xselect2).getTable(alias);
            }
         }
      }

      // for a column in selection or backup selection, its table information
      // is well maintained, so when no table found, it MUST be an expression
      // @by larryl, if this uniformSQL is a subquery in another UniformSQL,
      // it would not have the meta data in selection list and here. In this
      // case we must guess. Another possible fix is the call fixUniformSQLInfo
      // after the parsing is done. That could be expensive to be done at
      // runtime.
      if(tname != null || (getFieldList().length > 0 &&
                           (xselect.contains(column) ||
                            xselect2.contains(column))))
      {
         return tname;
      }

      // for a column not in selection or backup selection, it's hard for us
      // to judge it's a column or expression for its table info is lost.
      // Guess is dangerous. We should maintain its table info in the future
      SQLHelper provider = getSQLHelper();
      String quote = provider.getQuote();

      if(column.contains(quote) && !XUtil.isQualifiedName(column)) {
         return null;
      }

      String table = XUtil.getTablePart(column, this);

      if(table == null) {
         return null;
      }

      String column2 = XUtil.getColumnPart(column, this);
      boolean mexp = false; // might be an expression?
      int clength = column2.length();

      // if might be an expression, we take it as an expression, for the
      // possibility of a special column has '[+-*/]' or '||' is lower than
      // an expression. Here we do not use field list, because it's available
      // in design time but unavailable in runtime. The guess is inaccurate
      if(checkExpression) {
         for(int i = 0; i < clength; i++) {
            char c = column2.charAt(i);

            if(!Character.isUnicodeIdentifierPart(c) &&
               c != ' ' && c != '#' && c != '@' && c != '?' && c != '.' &&
               c != ':' && c != '[' && c != ']')
            {
               mexp = true;
               break;
            }
         }
      }

      if(mexp) {
         return null;
      }

      SelectTable[] tables = getSelectTable();

      for(SelectTable selectTable : tables) {
         String table_alias = selectTable.getAlias();
         Object table_name = selectTable.getName();

         if(table.equalsIgnoreCase(table_alias)) {
            return table_alias;
         }

         if(table.equalsIgnoreCase(table_name + "")) {
            return table_alias != null && table_alias.length() > 0 ?
               table_alias : (String) table_name;
         }
      }

      return null;
   }

   /**
    * Get the table index in the table list.
    * @param alias string value.
    * @return -1 if not find.
    */
   public synchronized int getTableIndex(String alias) {
      int size = tables.size();

      for(int i = 0; i < size; i++) {
         SelectTable stable = tables.get(i);
         String salias = stable.getAlias();
         Object sname = stable.getName();

         if(salias == null && (sname instanceof String)) {
            salias = (String) sname;
         }

         if(salias == null) {
            continue;
         }

         if(salias.equalsIgnoreCase(alias)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Find the table that the column belongs.
    */
   public synchronized String findTableForColumn(String col) {
      for(int i = 0; i < fields.size(); i++) {
         XField field = fields.elementAt(i);
         String fname = field.getName() == null ? "" : field.getName().toString();

         if(fname.equalsIgnoreCase(col) && field.getTable().length() > 0) {
            return field.getTable();
         }
      }

      return "";
   }

   /**
    * Set the table alias or add the table if it does not exist.
    * @param alias the table alias.
    * @param name the table name.
    */
   public synchronized void setTable(String alias, String name) {
      int index = getTableIndex(alias);

      if(index != -1) {
         SelectTable ostable = getSelectTable(index);
         SelectTable nstable = new SelectTable(alias, name);
         nstable.setCatalog(ostable.getCatalog());
         nstable.setSchema(ostable.getSchema());
         tables.set(index, nstable);
      }
      else {
         addTable(alias, name);
      }
   }

   /**
    * Set the table alias and location or add the table if it does not exist.
    * @param alias the table alias.
    * @param name the table name.
    * @param location the location of the top left corner of the table.
    */
   public synchronized void setTable(String alias, String name, Point location, Point scroll) {
      int index = getTableIndex(alias);

      if(index != -1) {
         SelectTable ostable = getSelectTable(index);
         SelectTable nstable = new SelectTable(alias, name, location, scroll);
         nstable.setCatalog(ostable.getCatalog());
         nstable.setSchema(ostable.getSchema());
         tables.set(index, nstable);
      }
      else {
         addTable(alias, name, location, scroll);
      }
   }

   /**
    * Set the alias of a column.
    */
   public synchronized void setAlias(int col, String alias) {
      String oalias = xselect.getAlias(col);
      xselect.setAlias(col, alias);

      if(oalias == null) {
         oalias = xselect.getColumn(col);
      }

      if(alias != null && alias.length() == 0) {
         alias = xselect.getColumn(col);
      }

      // rename grouped columns
      if(groups != null) {
         for(int i = 0; i < groups.length; i++) {
            if(groups[i].equals(oalias)) {
               groups[i] = alias;
            }
         }
      }

      for(OrderByItem item : orderByList) {
         if(item.getField().equals(oalias)) {
            item.setField(alias);
         }
      }
   }

   /**
    * Insert the field sorting order at a index.
    * @param index the specified index.
    * @param field the specified field.
    * @param order the specified order.
    */
   public synchronized void insertOrderBy(int index, Object field, String order) {
      orderByList.add(index, new OrderByItem(field, order));

      for(int i = orderByList.size() - 1; i >= index + 1; i--) {
         OrderByItem item = orderByList.elementAt(i);

         if(item.getField().equals(field)) {
            orderByList.remove(i);
         }
      }
   }

   /**
    * Set the field sorting order, or add the field to order by list if
    * it does not exist.
    * @param field the specified field.
    * @param order the specified order by.
    */
   public synchronized void setOrderBy(Object field, String order) {
      for(int i = 0; i < orderByList.size(); i++) {
         OrderByItem item = orderByList.elementAt(i);

         if(item.getField().equals(field)) {
            orderByList.setElementAt(new OrderByItem(field, order), i);
            return;
         }
      }

      orderByList.add(new OrderByItem(field, order));
   }

   /**
    * Copy order by clause from source UniformSQL.
    */
   @SuppressWarnings("unused")
   public void copyOrderBy(UniformSQL source) {
      if(source != null && source.orderByList != null) {
         this.orderByList = new Vector<>();

         for(int i = 0; i < source.orderByList.size(); i++) {
            OrderByItem item = (OrderByItem) source.orderByList.get(i).clone();
            this.orderByList.add(item);
         }
      }
   }

   /**
    * Replace the field sorting order. The new field order normally
    * equals to old field unless the table alias changed.
    * @param oldfield old sort field object.
    * @param oldorder old sort order.
    * @param newfield new sort field object.
    * @param neworder new sort order.
    */
   @SuppressWarnings("unused")
   public synchronized void replaceOrderBy(Object oldfield, String oldorder,
                                           Object newfield, String neworder)
   {
      for(int i = 0; i < orderByList.size(); i++) {
         OrderByItem item = orderByList.elementAt(i);

         if(item.getField().equals(oldfield)) {
            orderByList.setElementAt(new OrderByItem(newfield, neworder), i);
            return;
         }
      }
   }

   /**
    * Get the sorting order of a field.
    * @param field the specified field.
    * @return the order by of the field.
    */
   public synchronized String getOrderBy(Object field) {
      for(int i = 0; i < orderByList.size(); i++) {
         OrderByItem item = orderByList.elementAt(i);

         if(item.getField().equals(field)) {
            return item.getOrder();
         }
      }

      return null;
   }

   /**
    * Get all order by fields.
    * @return Order By Fields array, null if order by list is empty.
    */
   public synchronized Object[] getOrderByFields() {
      if(orderByList.size() == 0) {
         return null;
      }

      Object[] fields;

      fields = new Object[orderByList.size()];

      for(int i = 0; i < orderByList.size(); i++) {
         fields[i] = orderByList.elementAt(i).getField();
      }

      return fields;
   }

   /**
    * Get all order by fields.
    * @return Order By Fields array, null if order by list is empty.
    */
   @SuppressWarnings("WeakerAccess")
   public synchronized OrderByItem[] getOrderByItems() {
      OrderByItem[] items = new OrderByItem[orderByList.size()];
      orderByList.toArray(items);

      return items;
   }

   /**
    * Remove all elements of orderBy list.
    */
   public synchronized void removeAllOrderByFields() {
      orderByList.removeAllElements();
   }

   /**
    * Remove the invisible order by fields.
    */
   public synchronized void removeInvisibleOrderByFields() {
      if(xselect == null) {
         return;
      }

      for(int i = orderByList.size() - 1; i >= 0; i--) {
         OrderByItem item = orderByList.get(i);
         Object field = item.getField();

         if(!(field instanceof String)) {
            continue;
         }

         String sfield = (String) field;
         String column = xselect.findColumn(sfield);

         if(column == null) {
            orderByList.remove(i);
         }
      }
   }

   /**
    * Remove all tables from the selection list.
    */
   public synchronized void removeAllTables() {
      tables.removeAllElements();
   }

   /**
    * Remove all fields from the selection list.
    */
   public synchronized void removeAllFields() {
      fields.removeAllElements();
   }

   /**
    * Set group by all flag.
    */
   public synchronized void setGroupByAll(boolean grpall) {
      this.grpall = grpall;
   }

   /**
    * Check if group by all flag is true.
    */
   @SuppressWarnings("WeakerAccess")
   public synchronized boolean isGroupByAll() {
      return grpall;
   }

   /**
    * Set group by list.
    * @param groups by array.
    */
   public synchronized void setGroupBy(Object[] groups) {
      this.groups = groups;

      if(groups == null) {
         having = null;
      }
   }

   /**
    * Get group by list.
    * @return group by list.
    */
   public synchronized Object[] getGroupBy() {
      return groups;
   }

   public synchronized Vector<String> getOrderDBFields() {
      return orderDBFields;
   }

   public synchronized Vector<String> getGroupDBFields() {
      return groupDBFields;
   }

   /**
    * Get the condition tree of where clause
    * @return condition tree root.
    */
   public synchronized XFilterNode getWhere() {
      return where;
   }

   /**
    * Set the condition tree of where clause.
    * @param where conditions.
    */
   public synchronized void setWhere(XFilterNode where) {
      this.where = where;
   }

   /**
    * Get condition tree of having clause.
    */
   public synchronized XFilterNode getHaving() {
      return having;
   }

   /**
    * Set condition tree of having clause.
    */
   public synchronized void setHaving(XFilterNode having) {
      this.having = having;
   }

   /**
    * Add a join condition to the SQL.
    */
   public synchronized void addJoin(XJoin join) {
      addJoin(join, XSet.AND);
   }

   /**
    * Add a join condition to the SQL.
    */
   public synchronized void addJoin(XJoin join, String merging) {
      if(containsJoin(join)) {
         return;
      }

      XFilterNode root = getWhere();

      // merge between different tables should always be 'and'
      if(root == null) {
         combineWhere(join, merging);
      }
      else {
         // root should always be XSet
         if(!(root instanceof XSet)) {
            final XSet newRoot = new XSet(XSet.AND);
            newRoot.addChild(root);
            root = newRoot;
            where = newRoot;
         }

         boolean added = false;
         final String joinDependent = join.getTable1();
         final int numChildren = root.getChildCount();

         for(int i = 0; i < numChildren; i++) {
            final XNode table = root.getChild(i);

            // Children are XSet for "and" and "or"
            // first level of tree are tables
            if(table instanceof XSet) {
               final String dependentTable = ((XSet) table).getDependentTable();

               if(Objects.equals(joinDependent, dependentTable)) {
                  if(!Objects.equals(join.getTable2(), ((XSet) table).getIndependentTable())) {
                     merging = XSet.AND;
                     break;
                  }

                  for(int j = 0; j < table.getChildCount(); j++) {
                     final XNode child = table.getChild(j);

                     if(child instanceof XSet && Objects.equals(((XSet) child).getRelation(), merging)) {
                        child.addChild(join);
                        ((XSet) table).setGroup(true);
                        added = true;
                        break;
                     }
                  }
               }
            }
         }

         if(!added) {
            combineWhere(join, merging);
         }
      }
   }

   private XSet createJoinNode(XFilterNode node, String merging) {
      final XSet newJoin = new XSet(XSet.AND);
      final XSet andChild = new XSet(XSet.AND);
      final XSet orChild = new XSet(XSet.OR);
      newJoin.addChild(andChild);
      newJoin.addChild(orChild);

      if(XSet.AND.equals(merging)) {
         andChild.addChild(node);
      }
      else {
         orChild.addChild(node);
      }

      if(node instanceof XJoin) {
         newJoin.setDependentTable(((XJoin) node).getTable1());
         newJoin.setIndependentTable(((XJoin) node).getTable2());
      }

      return newJoin;
   }

   /**
    * Get a list of all joins.
    */
   public synchronized XJoin[] getJoins() {
      XFilterNode root = getWhere();

      if(root == null) {
         return null;
      }
      else if(root instanceof XJoin) {
         return new XJoin[] {(XJoin) root};
      }

      List<XJoin> joins = new ArrayList<>();
      getJoins(root, joins);
      return joins.toArray(new XJoin[0]);
   }

   /**
    * Return true of the table has a join.
    *
    * @param table table name.
    */
   @SuppressWarnings("unused")
   public synchronized boolean hasJoin(String table) {
      XFilterNode root = getWhere();

      if(root == null) {
         return false;
      }

      List<XJoin> joins = new ArrayList<>();
      getJoins(root, joins);

      // check if any joins use table
      for(XJoin join : joins) {
         if(join.getTable1(this).equals(table) ||
            join.getTable2(this).equals(table))
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Remove all join conditions.
    */
   public synchronized void removeAllJoins() {
      XFilterNode root = getWhere();

      if(root == null) {
         return;
      }
      else if(root instanceof XJoin) {
         setWhere(null);
         return;
      }

      root.removeAllJoins();

      if((root instanceof XSet) && root.getChildCount() == 0) {
         setWhere(null);
      }
   }

   /**
    * Add a new condition to the where condition and set OR as their relation.
    * @param condition the new condition.
    */
   @SuppressWarnings("unused")
   public synchronized void combineWhereByOr(XFilterNode condition) {
      combineWhere(condition, XSet.OR);
   }

   /**
    * Add new condition to where condition and set AND as their relation.
    * @param condition new condition
    */
   public synchronized void combineWhereByAnd(XFilterNode condition) {
      combineWhere(condition, XSet.AND);
   }

   /**
    * Add a new condition to the where condition and set the specified relation.
    * @param condition the new condition.
    * @param relation XSet.AND or XSet.OR.
    */
   private void combineWhere(XFilterNode condition, String relation) {
      if(condition == null) {
         return;
      }

      if(where == null) {
         final XSet newRoot = new XSet(XSet.AND);
         final XSet newJoin = createJoinNode(condition, relation);
         newRoot.addChild(newJoin);
         where = newRoot;
      }
      else {
         if(where instanceof XSet &&
            ((XSet) where).getRelation().equals(relation))
         {
            where.addChild(condition);
         }
         else if(condition instanceof XSet &&
            ((XSet) condition).getRelation().equals(relation))
         {
            condition.addChild(where);
            where = condition;
         }
         else {
            XFilterNode newWhere = new XSet(relation);
            newWhere.addChild(condition);
            newWhere.addChild(where);
            where = newWhere;
         }
      }

      where.setClause(XFilterNode.WHERE);
   }

   /**
    * Get the joins (into the vector) from the condition tree.
    */
   private void getJoins(XFilterNode root, List<XJoin> joins) {
      if((root instanceof XSet) &&
         ((XSet) root).getRelation().equalsIgnoreCase(XSet.AND))
      {
         for(int i = 0; i < root.getChildCount(); i++) {
            XNode child = root.getChild(i);

            if(child instanceof XJoin) {
               joins.add((XJoin) child);
            }
            else if(child instanceof XSet) {
               getJoins((XFilterNode) child, joins);
            }
         }
      }
   }

   /**
    * Check if the join already defined in the sql.
    */
   private boolean containsJoin(XJoin join) {
      XJoin[] joins = getJoins();

      for(int i = 0; joins != null && i < joins.length; i++) {
         if(joins[i].toString().equals(join.toString())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Generate a condition from StructuredSQL conditions. Like "this=$(var1)".
    * Ref StructuredSQL.substitute() to replace "this".
    * StructuredSQL uses ExprParser to parse expression.
    */
   private XFilterNode genCondition(String name, String value) {
      try {
         SQLHelper helper = getSQLHelper();
         SQLLexer lexer = new SQLLexer(new StringReader(
            replace(value, "this", XUtil.quoteName(name, helper))));
         SQLParser parser = new SQLParser(lexer);

         return parser.search_condition();
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Remove substrings in string.
    */
   @SuppressWarnings("SameParameterValue")
   private String replace(String str, String from, String to) {
      int idx;
      String ret = str;

      while((idx = ret.indexOf(from)) >= 0) {
         ret = ret.substring(0, idx) + to + ret.substring(idx + to.length());
      }

      return ret;
   }

   /**
    * Get the query database type.
    */
   @Override
   public synchronized JDBCDataSource getDataSource() {
      return dataSource;
   }

   /**
    * Set the query database type.
    */
   @Override
   public synchronized void setDataSource(JDBCDataSource dataSource) {
      if(!Tool.equals(this.dataSource, dataSource)) {
         clearCachedString();
      }

      this.dataSource = dataSource;
   }

   /**
    * Set the column selection list.
    */
   public synchronized void setSelection(XSelection selection) {
      this.xselect = selection;
   }

   /**
    * Set the backup column selection list.
    */
   public synchronized void setBackupSelection(XSelection selection) {
      this.xselect2 = selection;
   }

   /**
    * Get the backup column selection list.
    */
   public synchronized XSelection getBackupSelection() {
      return this.xselect2;
   }

   /**
    * Check if 'distinct' keyword is specified.
    */
   public synchronized boolean isDistinct() {
      return distinctKey;
   }

   /**
    * Set the distinct option.
    */
   public synchronized void setDistinct(boolean distinct) {
      distinctKey = distinct;

      if(distinct) {
         allKey = false;
      }
   }

   /**
    * Get the 'all' option of select.
    */
   public synchronized boolean isAll() {
      return allKey;
   }

   /**
    * Set the 'all' option of select.
    */
   public synchronized void setAll(boolean all) {
      allKey = all;

      if(all) {
         distinctKey = false;
      }
   }

   /**
    * Get the identifier.
    */
   public String toIdentifier() {
      return super.toString() + "{" + xselect.toIdentifier() + "}";
   }

   /**
    * To string.
    */
   public synchronized String toString() {
      // don't cache? generate statement
      if(!cache) {
         return getSQLString();
      }
      // cache? only generate statement if cached string is null
      else {
         if(cstring == null) {
            cstring = getSQLString();
         }

         return cstring;
      }
   }

   /**
    * Compare two UniformSQL without forcing to generate SQL string.
    */
   public boolean equalsStructure(Object o) {
      if(this == o) {
         return true;
      }
      if(o == null || getClass() != o.getClass()) {
         return false;
      }
      UniformSQL that = (UniformSQL) o;
      return distinctKey == that.distinctKey &&
         allKey == that.allKey &&
         grpall == that.grpall &&
         Objects.equals(xselect, that.xselect) &&
         Objects.equals(xselect2, that.xselect2) &&
         Objects.equals(tables, that.tables) &&
         Objects.equals(fields, that.fields) &&
         Objects.equals(orderByList, that.orderByList) &&
         Arrays.equals(groups, that.groups) &&
         Objects.equals(where, that.where) &&
         Objects.equals(having, that.having) &&
         Objects.equals(dataSource, that.dataSource) &&
         Arrays.equals(columns, that.columns) &&
         Objects.equals(expressions, that.expressions) &&
         Objects.equals(orderDBFields, that.orderDBFields) &&
         Objects.equals(groupDBFields, that.groupDBFields);
   }

   /**
    * Determine if this UniformSQL object is equivelent to some other object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof UniformSQL)) {
         return false;
      }

      return toString().equals(obj.toString());
   }

   /**
    * Get the addr.
    */
   public String addr() {
      return super.toString();
   }

   /**
    * Clone this object.
    */
   @Override
   public synchronized UniformSQL clone() {
      try {
         UniformSQL obj = (UniformSQL) super.clone();
         obj.setSqlQuery(isSqlQuery());

         if(xselect != null) {
            obj.xselect = (XSelection) xselect.clone();
         }

         if(xselect2 != null) {
            obj.xselect2 = (XSelection) xselect2.clone();
         }

         if(columns != null) {
            obj.columns = columns.clone();
         }

         if(expressions != null) {
            obj.expressions = (Vector<String>) expressions.clone();
         }

         if(orderDBFields != null) {
            obj.orderDBFields = (Vector<String>) orderDBFields.clone();
         }

         if(groupDBFields != null) {
            obj.groupDBFields = (Vector<String>) groupDBFields.clone();
         }

         if(tables != null) {
            obj.tables = new Vector<>();

            for(int i = 0; i < tables.size(); i++) {
               obj.tables.add((SelectTable) tables.get(i).clone());
            }
         }

         if(fields != null) {
            obj.fields = new Vector<>();

            for(int i = 0; i < fields.size(); i++) {
               XField field = (XField) fields.get(i).clone();
               obj.fields.add(field);
            }
         }

         if(orderByList != null) {
            obj.orderByList = new Vector<>();

            for(int i = 0; i < orderByList.size(); i++) {
               OrderByItem item = (OrderByItem) orderByList.get(i).clone();
               obj.orderByList.add(item);
            }
         }

         if(groups != null) {
            obj.groups = groups.clone();
         }

         if(where != null) {
            obj.where = (XFilterNode) where.clone();
         }

         if(having != null) {
            obj.having = (XFilterNode) having.clone();
         }

         obj.hints = new HashMap<>(hints);
         return obj;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * Get the sql string parsing result.
    */
   public synchronized int getParseResult() {
      return parseResult;
   }

   /**
    * Set the sql string parsing result.
    */
   public synchronized void setParseResult(int parseResult) {
      this.parseResult = parseResult;
   }

   /**
    * Check whether the query string should be parsed into structured view.
    */
   public boolean isParseSQL() {
      return parseIt;
   }

   /**
    * Set whether the query string should be parsed into structured view.
    */
   public void setParseSQL(boolean parse) {
      this.parseIt = parse;
   }

   /**
    * Set the column info.
    */
   public void setColumnInfo(XField[] columns) {
      this.columns = columns;
   }

   /**
    * Get the column info.
    */
   public XField[] getColumnInfo() {
      return columns;
   }

   /**
    * Get the parent uniform sql.
    * @return the parent uniform sql of this uniform sql.
    */
   public UniformSQL getParent() {
      return psql;
   }

   /**
    * Set the parent uniform sql to this uniform sql.
    * @param psql the specified parent uniform sql.
    */
   public void setParent(UniformSQL psql) {
      this.psql = psql;

      if(parseResult == PARSE_SUCCESS) {
         clearSQLString();
      }
   }

   /**
    * Set a hint for query generation. Hint keys are defined as constants in
    * this class.
    */
   public void setHint(String key, Object val) {
      hints.put(key, val);
   }

   /**
    * Get a hint.
    * @param key hint key.
    * @param parent true to get parent sql hint if the hint is not defined on
    * this sql.
    */
   public Object getHint(String key, boolean parent) {
      if(!hints.containsKey(key)) {
         if(psql != null && parent) {
            return psql.getHint(key, true);
         }

         return null;
      }

      return hints.get(key);
   }

   /**
    * Check if is sub query.
    */
   public boolean isSubQuery() {
      return sub;
   }

   /**
    * Set whether is sub query.
    */
   public void setSubQuery(boolean sub) {
      this.sub = sub;
   }

   /**
    * Check if is create by sql query or not.
    */
   public boolean isSqlQuery() {
      return sqlQuery;
   }

   /**
    * Set create by sql query or not.
    */
   public void setSqlQuery(boolean sql) {
      this.sqlQuery = sql;
   }

   /**
    * Check if this query has a condition created by the vpm.
    */
   public boolean hasVPMCondition() {
      return vpmCondition;
   }

   /**
    * Set if this query has a condition created by the vpm.
    */
   public void setVPMCondition(boolean vpmCondition) {
      this.vpmCondition = vpmCondition;
   }

   /**
    * Check if is cacheable.
    */
   public boolean isCacheable() {
      return cache;
   }

   /**
    * Set whether is cacheable.
    */
   public void setCacheable(boolean cache) {
      this.cache = cache;
   }

   /**
    * Get the unparseable expressions.
    */
   public synchronized String[] getExpressions() {
      return expressions.toArray(new String[0]);
   }

   /**
    * remove unparseable expression.
    */
   @SuppressWarnings("UnusedReturnValue")
   public synchronized boolean removeExpression(String expression) {
      return expressions.remove(expression);
   }

   /**
    * Add unparseable expression.
    */
   public synchronized void addExpression(String expression) {
      if(!expressions.contains(expression)) {
         expressions.add(expression);
      }
   }

   /**
    * Check if contains the specified unparseable expression.
    */
   public synchronized boolean containsExpression(String expression,
                                                  boolean strick) {
      if(strick) {
         return expressions.contains(expression);
      }
      // support expression on expression
      else {
         for(String exp : expressions) {
            if(expression.contains(exp)) {
               return true;
            }
         }

         return false;
      }
   }

   /**
    * Check if contains unparseable expressions.
    */
   public synchronized boolean containsExpressions() {
      return expressions.size() > 0;
   }

   public synchronized void addOrderDBField(String fld) {
      if(!orderDBFields.contains(fld)) {
         orderDBFields.add(fld);
      }
   }

   public synchronized void addGroupDBField(String fld) {
      if(!groupDBFields.contains(fld)) {
         groupDBFields.add(fld);
      }
   }

   public boolean isOrderDBField(String fld) {
      return orderDBFields.contains(fld);
   }

   public boolean isGroupDBField(String fld) {
      return groupDBFields.contains(fld);
   }

   public void clearOrderDBFields() {
      orderDBFields.clear();
   };

   public void clearGroupDBFields() {
      groupDBFields.clear();
   };

   /**
    * Check if is an alias column.
    */
   @SuppressWarnings("WeakerAccess")
   public boolean isAliasColumn(String column) {
      return aliasflags.contains(column);
   }

   /**
    * Set whether is an alias column.
    */
   @SuppressWarnings("WeakerAccess")
   public void setAliasColumn(String column, boolean flag) {
      if(flag) {
         aliasflags.add(column);
      }
      else {
         aliasflags.remove(column);
      }
   }

   /**
    * Clear alias column.
    */
   @SuppressWarnings("WeakerAccess")
   public void clearAliasColumn() {
      aliasflags.clear();
   }

   /**
    * Set the original joins before being transformed.
    */
   public void setOriginalJoins(Collection<XJoin> joins) {
      this.ojoins = joins;
   }

   /**
    * Get the original joins.
    */
   public Collection<XJoin> getOriginalJoins() {
      return ojoins;
   }

   public SQLHelper getSQLHelper() {
      if(vpmUser != null) {
         return SQLHelper.getSQLHelper(this, vpmUser);
      }

      return SQLHelper.getSQLHelper(this);
   }

   public void setVpmUser(Principal user) {
      this.vpmUser = user;
   }

   /**
    * Check if parsing captured all information in sql string. A sql may be parsed successfully
    * but may lose some information in the structured view.
    */
   public boolean isLossy() {
      if(parseIt && lossy == null && sqlstring != null) {
         SQLLexer lexer = new SQLLexer(new StringReader(getQuotedSqlString(sqlstring)));
         SQLParser parser = new SQLParser(lexer);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(getDataSource());

         try {
            parser.direct_select_stmt_n_rows(sql);
            setLossy(sql.lossy == null ? false : sql.lossy);
         }
         catch(Exception e) {
            setLossy(true);
         }
      }

      return lossy != null && lossy;
   }

   private String getQuotedSqlString(String sql) {
      if(dataSource != null && dataSource.getDatabaseType() == JDBCDataSource.JDBC_CLICKHOUSE) {
         return JDBCUtil.quoteMapKeyAccessForParsing(sql);
      }

      return sql;
   }

   /**
    * Set whether any information is lost in parsing.
    */
   public void setLossy(boolean lossy) {
      this.lossy = lossy;
   }

   static final int PARSE_ALL = 0;
   static final int PARSE_ONLY_SELECT = 1;
   static final int PARSE_ONLY_SELECT_FROM = 2;
   // sql parser parse period
   static final long PARSE_PERIOD = 4000;

   // sql parser thread pool
   private static ThreadPool parserPool = new ThreadPool(1, 1, "parser");

   String sqlstring = null;
   private XSelection xselect = new JDBCSelection(); // column selection list
   private XSelection xselect2 = new JDBCSelection(); // backup column
   private Vector<SelectTable> tables = new Vector<>(); // table list
   private Vector<XField> fields = new Vector<>(); // field list
   private Vector<OrderByItem> orderByList = new Vector<>(); // order by list
   private Object[] groups; // group by list
   private XFilterNode where; // root XFilterNode of where clause
   private XFilterNode having; // root XFilterNode of having clause
   private JDBCDataSource dataSource = null;
   private boolean distinctKey = false;
   private boolean allKey = false;
   private boolean grpall = false;
   private UniformSQL psql = null;
   private XField[] columns = null;
   private int parseResult = PARSE_INIT;
   private XNode root;
   private boolean parseIt = true; // whether to parse the sql string
   private Map<String, Object> hints = new HashMap<>();
   private boolean sub = false;
   private boolean cache = false;
   private boolean sqlQuery = false;
   private boolean vpmCondition = false;
   private String cstring = null;
   private Vector<String> expressions = new Vector<>(); //unparseable expressions

   private Vector<String> orderDBFields = new Vector<>(); //unparseable expressions

   private Vector<String> groupDBFields = new Vector<>(); //unparseable expressions
   private Set<String> aliasflags = new HashSet<>();
   private Collection<XJoin> ojoins; // original joins before being transformed
   private transient Principal vpmUser; // vpm user apply for studio worksheet.
   private Boolean lossy = null;

   private static final Logger LOG = LoggerFactory.getLogger(UniformSQL.class);
}
