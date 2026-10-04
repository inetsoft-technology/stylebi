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
         quotedFields = new HashMap<>(uniformSql.quotedFields);
         quotedAggregates = new HashMap<>(uniformSql.quotedAggregates);

         if(uniformSql.groups == null) {
            groups = null;
         }
         else {
            groups = new Object[uniformSql.groups.length];
            System.arraycopy(uniformSql.groups, 0, groups, 0, groups.length);
         }

         groupQuotes = uniformSql.groupQuotes == null ? null : uniformSql.groupQuotes.clone();
         groupQuoteFields = uniformSql.groupQuoteFields == null ? null :
            uniformSql.groupQuoteFields.clone();
         groupUnquoted = uniformSql.groupUnquoted == null ? null :
            uniformSql.groupUnquoted.clone();

         where = uniformSql.where == null ?
            null : (XFilterNode) uniformSql.where.clone();
         having = uniformSql.having == null ?
            null : (XFilterNode) uniformSql.having.clone();
         sqlstring = uniformSql.sqlstring;
         sqlUnquoted = uniformSql.sqlUnquoted;

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
         parseUnquoted(() -> parser.direct_select_stmt_n_rows(UniformSQL.this));
         checkJoinOrders(parser, time);
         setParseResult(PARSE_SUCCESS);
      }
      else if(parseType == PARSE_ONLY_SELECT) {
         parseUnquoted(() -> parser.only_select(UniformSQL.this));
         setParseResult(PARSE_PARTIALLY);
      }
      else if(parseType == PARSE_ONLY_SELECT_FROM) {
         parseUnquoted(() -> parser.only_select_from(UniformSQL.this));
         setParseResult(PARSE_PARTIALLY);
      }
   }

   /**
    * Check that each query that mixes a RIGHT or FULL join with an inner join,
    * or that has a nested join on the right side of an outer join, has the same
    * joins in its regenerated sql. UniformSQL keeps the joins without their
    * order or nesting, so the sql helper picks them, and a different order or
    * nesting can change the query results (Bug #77434).
    * <p>
    * The sql is generated the way a merge generates it, with the sql helper
    * of the data source of this (the outer) query, which a subquery inherits
    * when it's generated. Without a data source, the helper that generates the
    * sql later is unknown (e.g. Oracle without ansi join generates (+) joins),
    * so such a query is refused.
    */
   private void checkJoinOrders(SQLParser parser, long time) throws Exception {
      JDBCDataSource source = getDataSource();

      for(Object obj : parser.getJoinOrderChecks()) {
         UniformSQL query = (UniformSQL) obj;
         String structure = null;
         boolean commaGroupsNotMovable = false;

         try {
            if(source != null) {
               SQLHelper helper = getCheckSQLHelper(source);
               String generated = generateCheckSQL(query, source, helper);
               commaGroupsNotMovable = helper.isCommaGroupsNotMovable();

               UniformSQL regenerated = new UniformSQL();
               regenerated.setDataSource(source);
               SQLLexer lexer = new SQLLexer(
                  new StringReader(regenerated.getQuotedSqlString(generated)));
               SQLParser parser2 = new SQLParser(lexer);
               parser2.setTime(time);
               parseUnquoted(() -> parser2.direct_select_stmt_n_rows(regenerated));
               structure = parser2.getJoinStructure(regenerated);
            }
         }
         catch(Exception ex) {
            LOG.debug("Failed to parse the generated sql to check its joins", ex);
         }

         parser.checkJoinOrder(query, structure);
         parser.checkCommaJoinGroups(query, commaGroupsNotMovable);
      }

      checkCommaJoinGroups(parser);
   }

   /**
    * Check that the regenerated sql of each query with an outer join that the join order
    * check doesn't regenerate needs at most to move its only RIGHT or FULL join group before
    * its other comma separated join groups (Bug #77675). Without a data source, its sql is
    * checked with the base sql helper, which writes the join groups of every ANSI helper
    * except MongoHelper the same way. A query whose sql can't be generated is refused, like
    * the join order check does.
    * @return true if the sql of any query was generated to check it.
    */
   private boolean checkCommaJoinGroups(SQLParser parser) throws Exception {
      JDBCDataSource source = getDataSource();
      boolean checked = false;

      for(Object obj : parser.getCommaJoinGroupChecks()) {
         UniformSQL query = (UniformSQL) obj;

         // a single join group is never reordered, don't generate it (or the subqueries of
         // the query, whose sql helper may connect to the database for its version)
         if(!hasSeveralJoinGroups(query)) {
            continue;
         }

         boolean commaGroupsNotMovable = true;
         checked = true;

         try {
            SQLHelper helper = source != null ? getCheckSQLHelper(source) : new SQLHelper();
            generateCheckSQL(query, source, helper);
            commaGroupsNotMovable = helper.isCommaGroupsNotMovable();
         }
         catch(Exception ex) {
            LOG.debug("Failed to generate the sql to check its join groups", ex);
         }

         parser.checkCommaJoinGroups(query, commaGroupsNotMovable);
      }

      return checked;
   }

   /**
    * Check if the joins of a query connect its tables into two or more join groups, which
    * SQLHelper writes as comma separated groups. A join whose tables are unknown counts as a
    * group of its own.
    */
   private static boolean hasSeveralJoinGroups(UniformSQL query) {
      XJoin[] joins = query.getJoins();

      if(joins == null || joins.length < 2) {
         return false;
      }

      Map<String, String> parents = new HashMap<>();
      int groups = 0;

      for(XJoin join : joins) {
         String table1 = join.getTable1(query);
         String table2 = join.getTable2(query);

         if(table1 == null || table2 == null) {
            return true;
         }

         String root1 = findJoinGroup(parents, table1);
         String root2 = findJoinGroup(parents, table2);

         if(root1 == null) {
            parents.put(table1, table1);
            root1 = table1;
            groups++;
         }

         if(root2 == null) {
            parents.put(table2, table2);
            root2 = table2;
            groups++;
         }

         if(!root1.equals(root2)) {
            parents.put(root2, root1);
            groups--;
         }
      }

      return groups > 1;
   }

   /**
    * Find the first table of the join group of a table, null if the table isn't in a group.
    */
   private static String findJoinGroup(Map<String, String> parents, String table) {
      String parent = parents.get(table);

      while(parent != null && !parent.equals(table)) {
         table = parent;
         parent = parents.get(table);
      }

      return parent;
   }

   /**
    * Get the sql helper of a data source to check the regenerated sql of a parsed query,
    * without connecting to the database for the product name or version, which don't change
    * the joins.
    */
   private static SQLHelper getCheckSQLHelper(JDBCDataSource source) {
      SQLHelper helper = SQLHelper.getSQLHelper(SQLHelper.getProductName(source, true));
      helper.setAnsiJoin(source.isAnsiJoin());
      return helper;
   }

   /**
    * Generate the sql of a copy of a parsed query, the way a merge generates it. A copy,
    * since generateSentence changes the query (aliases, order by).
    */
   private static String generateCheckSQL(UniformSQL query, JDBCDataSource source,
                                          SQLHelper helper)
   {
      UniformSQL copy = query.clone();

      if(source != null) {
         copy.setDataSource(source);
      }

      helper.setUniformSql(copy);
      // the join structure is checked on the names as stored (Bug #77643)
      return WrittenUnquoted.unfolded(helper::generateSentence);
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
      quotedFields = new HashMap<>();
      quotedAggregates = new HashMap<>();
      groups = null;
      groupQuotes = null;
      groupQuoteFields = null;
      groupUnquoted = null;
      where = null;
      having = null;
      distinctKey = false;
      allKey = false;
      grpall = false;
      parsedUnquotedSegments = new HashSet<>();
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

      String savedLossy = Tool.getAttribute(node, "lossy");

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
                  // the sql with the quotes of its quoted table names (#77569)
                  String quoted = Tool.getAttribute(namenode, "quotedSql");
                  String text = quoted != null ? quoted : (String) name;
                  UniformSQL sub = new UniformSQL(text, false);
                  // its names written unquoted (Bug #77643)
                  sub.setSQLStringWrittenUnquoted(
                     WrittenUnquoted.fromAttribute(text, Tool.getAttribute(namenode, "unquoted")));
                  name = sub;
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

            // the name segments written quoted in the parsed sql (#77569)
            if(namenode != null && name instanceof String) {
               stable.setQuotedSegmentsString(Tool.getAttribute(namenode, "quotedSegments"));
               // the names written unquoted (Bug #77643)
               stable.setWrittenUnquoted(
                  WrittenUnquoted.fromAttribute((String) name, Tool.getAttribute(namenode, "unquoted")));
            }
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

            // whether the alias was written quoted, absent if not known (#77616)
            String aliasQuoted = Tool.getAttribute(child, "quoted");

            if("true".equals(aliasQuoted) || "false".equals(aliasQuoted)) {
               selection.setAliasQuoted(selection.getColumnCount() - 1,
                                        Boolean.valueOf(aliasQuoted));
            }
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

         list = Tool.getChildNodesByTagName(column, "isExp");

         if(list.getLength() > 0) {
            child = (Element) list.item(0);
         }

         if(child != null) {
            selection.setExpression(selection.getColumnCount() - 1,
               "true".equals(Tool.getValue(child)));
         }

         Element quotedNode = Tool.getChildNodeByTagName(column, "quoted");
         Element quotedColumnNode = Tool.getChildNodeByTagName(column, "quotedColumn");

         // the flag of this column, two columns may have the same name (Bug #77573)
         if(quotedNode != null) {
            selection.setQuoted(selection.getColumnCount() - 1,
               "true".equals(Tool.getValue(quotedNode)));
         }
         // a qualified quoted identifier (t."MixedCase"), see writeXML
         else if(quotedColumnNode != null) {
            String seg = Tool.getAttribute(quotedColumnNode, "column");

            if(seg != null && !seg.isEmpty()) {
               selection.setQuoted(selection.getColumnCount() - 1, seg);
            }
         }

         // an aggregate of a qualified quoted column (sum(t."MixedCase")), see writeXML
         Element quotedAggregateNode = Tool.getChildNodeByTagName(column, "quotedAggregate");

         if(quotedAggregateNode != null) {
            selection.setQuotedAggregate(selection.getColumnCount() - 1,
               Tool.getAttribute(quotedAggregateNode, "column"));
         }

         // the names written unquoted (Bug #77643)
         selection.setWrittenUnquoted(selection.getColumnCount() - 1,
            WrittenUnquoted.fromAttribute(columnName, Tool.getAttribute(column, "unquoted")));
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

            // a missing direction was written as "null" (Bug #77570)
            if("null".equals(order) || "".equals(order)) {
               order = null;
            }

            OrderByItem item = new OrderByItem(field, order);
            // the quoting of this item, two items may have the same text (Bug #77573). An
            // element without it (saved before) is quoted by its text, as before
            String quote = readQuotedField(sortNode, field);

            if(hasQuotedFieldAttribute(sortNode)) {
               item.setQuoted(quote != null, getQuotedSegment(quote));
            }

            // the names written unquoted (Bug #77643)
            item.setWrittenUnquoted(
               WrittenUnquoted.fromAttribute(field, Tool.getAttribute(sortNode, "unquoted")));
            orderByList.add(item);
            setQuotedAggregate(field, Tool.getAttribute(sortNode, "quotedAggregate"));
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "groupby");

      if(nlist.getLength() > 0) {
         nlist = ((Element) nlist.item(0)).getElementsByTagName("field");
         groups = new Object[nlist.getLength()];
         groupQuotes = new String[groups.length];
         groupUnquoted = new WrittenUnquoted[groups.length];
         boolean[] known = new boolean[groups.length];

         for(int i = 0; i < nlist.getLength(); i++) {
            Element groupNode = (Element) nlist.item(i);
            String field = Tool.getValue(groupNode);
            groups[i] = field;
            // the names written unquoted (Bug #77643)
            groupUnquoted[i] = WrittenUnquoted.fromAttribute(
               field, Tool.getAttribute(groupNode, "unquoted"));

            groupQuotes[i] = readQuotedField(groupNode, field);
            known[i] = hasQuotedFieldAttribute(groupNode);
         }

         // an element without its quoting (saved before) is quoted by its text, as before
         for(int i = 0; i < groups.length; i++) {
            if(!known[i]) {
               groupQuotes[i] = quotedFields.get(groups[i]);
            }
         }

         groupQuoteFields = groups.clone();
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

      if(savedLossy != null) {
         // A saved lossy flag may predate a parser change (Bug #77477). Keep it only when
         // isLossy() can't re-derive it: parsing is off and there is a sql string. Otherwise
         // leave it null, so isLossy() re-parses the sql string with the current grammar, or,
         // with no sql string, reports false because the structure is the whole query.
         // parseResult stays as saved.
         lossy = !parseIt && sqlstring != null ? Boolean.valueOf(savedLossy.equals("true")) : null;
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
         else if(issql) {
            // a derived table is saved as the text of its sql, as before #77569 without the
            // quotes of its quoted table names, and with them in the quotedSql attribute,
            // which older builds ignore
            UniformSQL sub = (UniformSQL) name;

            if(sub.getDataSource() == null) {
               sub.setDataSource(getDataSource());
            }

            // the sql as generated without folding the names written unquoted, which are
            // saved in the unquoted attribute (Bug #77643)
            String text = WrittenUnquoted.marking(() -> toUnquotedString(sub));
            String quoted = WrittenUnquoted.marking(() -> {
               sub.clearCachedString();

               try {
                  return name.toString();
               }
               finally {
                  sub.clearCachedString();
               }
            });
            String quote = sub.getSQLHelper().getQuote();
            // the names of the text that is loaded
            WrittenUnquoted names = WrittenUnquoted.ofMarked(
               WrittenUnquoted.unmark(quoted, quote).equals(WrittenUnquoted.unmark(text, quote)) ?
                  text : quoted, quote);
            text = WrittenUnquoted.unmark(text, quote);
            quoted = WrittenUnquoted.unmark(quoted, quote);
            writer.println("<name" + (!quoted.equals(text) ?
               " quotedSql=\"" + Tool.escape(quoted) + "\"" : "") +
               (names != null ? " unquoted=\"" + names.toAttribute() + "\"" : "") +
               "><![CDATA[" + text + "]]></name>");
         }
         else {
            String quotedSegments = name instanceof String ? table.getQuotedSegmentsString() : null;
            WrittenUnquoted names = table.getWrittenUnquoted();
            writer.println("<name" + (quotedSegments != null ?
               " quotedSegments=\"" + quotedSegments + "\"" : "") +
               (names != null ? " unquoted=\"" + names.toAttribute() + "\"" : "") +
               "><![CDATA[" + name + "]]></name>");
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
         WrittenUnquoted names = selection.getWrittenUnquoted(i);
         // the names written unquoted (Bug #77643), older versions ignore them
         writer.println("<column" + WrittenUnquoted.toXMLAttribute(names) + ">");
         String column = selection.getColumn(i);
         String alias = selection.getAlias(i);
         String type = selection.getType(column);
         String tname = selection.getTable(column);
         String desc = selection.getDescription(column);
         boolean isExp = selection.isExpression(column);

         if(full && tname == null && alias != null) {
            tname = selection.getTable(alias);
         }

         writer.println("<![CDATA[" + column + "]]>");
         // whether the alias was written quoted, if known. Older versions ignore it
         Boolean aliasQuoted = selection.isAliasQuoted(i);
         writer.println("<alias" + (aliasQuoted == null ? "" : " quoted=\"" + aliasQuoted + "\"") +
                        "><![CDATA[" + (alias == null ? "" : alias) + "]]></alias>");
         writer.println("<type><![CDATA[" + (type == null ? "" : type) +
                        "]]></type>");
         writer.println("<table><![CDATA[" + (tname == null ? "" : tname) +
                        "]]></table>");
         writer.println("<description><![CDATA[" + (desc == null ? "" : desc) +
                        "]]></description>");
         writer.println("<isExp><![CDATA[" + isExp + "]]></isExp>");

         if(selection.isQuoted(i)) {
            String seg = selection.getQuotedColumn(i);

            // a qualified quoted identifier (t."MixedCase") is not written as <quoted>,
            // which older versions read as quoting the whole name ("t.MixedCase"). They
            // ignore <quotedColumn> and generate the name unquoted, as before
            if(seg != null) {
               writer.println("<quotedColumn column=\"" + Tool.escape(seg) + "\"/>");
            }
            else {
               writer.println("<quoted><![CDATA[true]]></quoted>");
            }
         }

         String qagg = selection.getQuotedAggregate(i);

         // an aggregate of a qualified quoted column (sum(t."MixedCase")). Older versions
         // ignore it and generate the aggregate as before
         if(qagg != null) {
            writer.println("<quotedAggregate column=\"" + Tool.escape(qagg) + "\"/>");
         }

         writer.println("</column>");
      }

      writer.println("<where>");

      if(where != null) {
         where.writeXML(writer);
      }

      writer.println("</where>");

      writer.print("<sortby>");
      OrderByItem[] orderItems = this.getOrderByItems();
      Object[] orderField = Arrays.stream(orderItems).map(OrderByItem::getField).toArray();
      Set<String> quotedTexts = getQuotedFieldTexts(orderItems);

      for(int i = 0; i < orderField.length; i++) {
         // the direction of the item itself, none if not set (Bug #77570)
         String order = orderItems[i].getOrder();
         writer.print("<field" + (order != null ? " order=\"" + order + "\"" : "") +
                      quotedFieldAttribute(getQuote(orderItems[i]), orderField[i], quotedTexts) +
                      quotedAggregateAttribute(orderField[i]) +
                      unquotedAttribute(orderItems[i].getWrittenUnquoted()) + "><![CDATA[");
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
         writer.print("<field" + quotedFieldAttribute(getGroupQuote(i), groupby[i], quotedTexts) +
                      unquotedAttribute(getGroupByWrittenUnquoted(i)) + "><![CDATA[");
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
      this.sqlUnquoted = null;
      // a new sql string must re-derive lossy (null keeps the lazy check in isLossy())
      this.lossy = null;

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
      // the structure is now the whole query, nothing is lost any more
      lossy = null;
   }

   /**
    * Get the sql string.
    * @return the sql string.
    */
   @Override
   public synchronized String getSQLString() {
      if(sqlstring != null) {
         WrittenUnquoted names = WrittenUnquoted.of(sqlUnquoted, sqlstring);

         // the sql of a derived table saved as text, its names written unquoted are folded
         return names == null ? sqlstring :
            getSQLHelper().foldWrittenUnquoted(sqlstring, names);
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
      // resolve the alias the same way as getTableIndex so that tables whose
      // aliases differ only in case (e.g. "A" and "a") are not confused
      int index = getTableIndex(alias);

      if(index >= 0) {
         tables.removeElementAt(index);
      }
   }

   /**
    * Check if a table name or alias matches the specified string.
    */
   private static boolean nameMatches(String name, String str, boolean ignoreCase) {
      return ignoreCase ? name.equalsIgnoreCase(str) : name.equals(str);
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
            moveQuotedAggregate((String) field, newName);
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
            renameGroupField(i, fixFieldName(group, rinfos));
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
         item.renameField(fieldName);
         moveQuotedAggregate((String) field, fieldName);
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
               selection.renameColumn(i, ncolname);
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
      OrderByItem[] items = getOrderByItems();

      if(items.length == 0) {
         return;
      }

      // each kept item with its own direction and quoted aggregate record, so an item is
      // never looked up again by its text (Bug #77570)
      List<OrderByItem> kept = new ArrayList<>();
      List<String> records = new ArrayList<>();
      Boolean wildcard = null;
      boolean changed = false;
      // an item that may be an alias or a column is dropped below. The ordinals then stay
      // ordinals, so the sql keeps running as written (Bug #77557) and not without it.
      boolean undecided = false;
      // the ordinals to convert to their select columns
      Set<Object> ordinals = new HashSet<>();

      for(OrderByItem item : items) {
         if(item.getField() instanceof String &&
            getSelectAliasField((String) item.getField(), getQuote(item) != null) == null &&
            isOtherCaseAlias((String) item.getField()))
         {
            undecided = true;
            break;
         }
      }

      for(OrderByItem item : items) {
         Object field = item.getField();
         String record = getQuotedAggregate(field);
         // the quoting of the item itself, two items may have the same text (Bug #77573)
         String quote = getQuote(item);
         // the names written unquoted, kept for the names a renamed field keeps (Bug #77643)
         WrittenUnquoted unquoted = item.getWrittenUnquoted();
         int ordinal = getOrdinal(field, quote != null);
         AliasRef aliasRef;

         if(ordinal > 0) {
            if(wildcard == null) {
               wildcard = isSelectWildcard();
            }

            // the model's select list doesn't keep the positions of a select list with a
            // wildcard, so an ordinal is kept as written
            if(!wildcard) {
               if(ordinal > getSelection().getColumnCount()) {
                  changed = true;
                  continue;
               }

               if(field instanceof Integer && !undecided &&
                  getOrdinalColumn(ordinal) != null)
               {
                  // converted below, once the other items are known
                  ordinals.add(field);
               }
            }
         }
         else if(field instanceof String &&
            ((aliasRef = getSelectAliasField((String) field, quote != null)) != null ||
             (aliasRef = getOtherCaseOrderByAlias((String) field, quote != null)) != null))
         {
            if(!aliasRef.field().equals(field)) {
               field = aliasRef.field();
               changed = true;
               // the reference is generated as resolved, a select column as the column
               unquoted = aliasRef.unquoted();
            }

            if(aliasRef.column()) {
               quote = aliasRef.quote();
               carryQuotedField(field, quote);
            }
         }
         else if(field instanceof String && isOtherCaseAlias((String) field)) {
            // it may be the alias or a column, depending on the quoting of the alias, which
            // isn't known, so it isn't guessed
            changed = true;
            continue;
         }
         else if(field instanceof String) {
            // remove order by columns that does not have table
            if(getFieldByPath((String) field) == null && getField((String) field) == null) {
               String path = getUnquotedColumnPath((String) field);
               changed = true;

               if(path == null) {
                  int idx = getUnqualifiedSelectColumn((String) field, quote);

                  if(idx < 0) {
                     continue;
                  }

                  // the select column, with its quoting (Bug #77639) and its names written
                  // unquoted (Bug #77643)
                  path = getSelection().getColumn(idx);
                  quote = getSelectQuote(idx, path);
                  carryQuotedField(path, quote);
                  unquoted = getSelectUnquoted(idx);
               }
               else {
                  unquoted = renameWrittenUnquoted(unquoted, path);
               }

               field = path;
            }
            else {
               String fp = JDBCUtil.getFullPathOf(this, (String) field, quote != null, unquoted);

               if(fp != null && !fp.equals(field)) {
                  copyQuotedField((String) field, fp);
                  field = fp;
                  changed = true;
                  unquoted = renameWrittenUnquoted(unquoted, fp);
               }
            }
         }
         else if(!(field instanceof String)) {
            String fname = field.toString();

            if(getFieldByPath(fname) == null && getField(fname) == null &&
               getSelection().getAliasColumn(fname) == null)
            {
               changed = true;
               continue;
            }
         }

         if(!addOrderByIfAbsent(kept, field, item.getOrder(), quote, unquoted)) {
            // the first item on a column decides its direction, as in sql
            changed = true;
            continue;
         }

         records.add(record);
      }

      // an ordinal that names the column of another item stays an ordinal. Merged into one
      // item, the other item's resolution, which may be wrong, would decide what runs. As an
      // ordinal, the sql runs as written (Bug #77557).
      for(int i = 0; i < kept.size(); i++) {
         Object field = kept.get(i).getField();

         if(!ordinals.contains(field)) {
            continue;
         }

         Object nfield = getOrdinalColumn((Integer) field);
         boolean shared = false;

         for(int j = 0; j < kept.size() && !shared; j++) {
            Object other = kept.get(j).getField();

            shared = j != i && (nfield.equals(other) || ordinals.contains(other) &&
               nfield.equals(getOrdinalColumn((Integer) other)));
         }

         if(!shared) {
            // the converted field takes the quoting of its select column (Bug #77573)
            String quote = getSelectQuote((Integer) field - 1, nfield);
            carryQuotedField(nfield, quote);
            kept.get(i).setField(nfield);
            kept.get(i).setQuoted(quote != null, getQuotedSegment(quote));
            // the select column is generated as the column (Bug #77643)
            kept.get(i).setWrittenUnquoted(getSelectUnquoted((Integer) field - 1));
            changed = true;
         }
      }

      if(changed) {
         removeAllOrderByFields();

         for(int i = 0; i < kept.size(); i++) {
            OrderByItem item = kept.get(i);
            orderByList.add(item);

            if(records.get(i) != null && item.getField() instanceof String) {
               setQuotedAggregate((String) item.getField(), records.get(i));
            }
         }
      }
   }

   /**
    * Add an order by item to the list unless an item already sorts on the same field with
    * the same quoting.
    * @param quote the quoting of the field, see getQuote.
    * @return <tt>true</tt> if added.
    */
   private boolean addOrderByIfAbsent(List<OrderByItem> items, Object field, String order,
                                      String quote, WrittenUnquoted unquoted)
   {
      unquoted = WrittenUnquoted.of(unquoted, field);

      for(OrderByItem item : items) {
         if(item.getField().equals(field) && Objects.equals(getQuote(item), quote) &&
            Objects.equals(item.getWrittenUnquoted(), unquoted))
         {
            return false;
         }
      }

      OrderByItem item = new OrderByItem(field, order);
      item.setQuoted(quote != null, getQuotedSegment(quote));
      item.setWrittenUnquoted(unquoted);
      items.add(item);
      return true;
   }

   /**
    * Get the names written unquoted that a field renamed by the metadata step keeps, e.g. the
    * qualifier of a column resolved to its exact name (Bug #77643).
    */
   private WrittenUnquoted renameWrittenUnquoted(WrittenUnquoted unquoted, Object field) {
      return field instanceof String ?
         JDBCUtil.renameWrittenUnquoted(this, unquoted, (String) field) : null;
   }

   /**
    * Get the select column an order by ordinal is converted to.
    */
   private Object getOrdinalColumn(int ordinal) {
      String path = getSelection().getColumn(ordinal - 1);

      if(path == null) {
         return null;
      }

      String alias = getSelection().getAlias(ordinal - 1);

      if(getDataSource() == null ||
         getDataSource().getDatabaseType() == JDBCDataSource.JDBC_ODBC ||
         alias == null || alias.length() == 0)
      {
         return path;
      }

      return alias;
   }

   /**
    * Get the quoting of the select column an order by ordinal is converted to. The path is
    * generated as an order by field, which has its own quoted flag. An alias is generated
    * as its select column, which decides the quoting.
    * @param idx the select column index.
    * @param field the field the ordinal is converted to.
    * @return the quoting, see getQuote.
    */
   private String getSelectQuote(int idx, Object field) {
      if(!(getSelection() instanceof JDBCSelection) ||
         !field.equals(getSelection().getColumn(idx)))
      {
         return null;
      }

      JDBCSelection jselect = (JDBCSelection) getSelection();

      if(!jselect.isQuoted(idx)) {
         return null;
      }

      String seg = jselect.getQuotedColumn(idx);
      return seg == null ? "" : seg;
   }

   /**
    * Get the names written unquoted of a select column, which a group by or order by field
    * converted to the select column generates as the column (Bug #77643).
    */
   private WrittenUnquoted getSelectUnquoted(int idx) {
      return getSelection() instanceof JDBCSelection ?
         ((JDBCSelection) getSelection()).getWrittenUnquoted(idx) : null;
   }

   /**
    * Add a field converted to a select column with its quoting to the text-keyed quoting,
    * which an order by or group by field added later without its quoting falls back to.
    */
   private void carryQuotedField(Object field, String quote) {
      if(quote != null && field instanceof String) {
         setQuotedField((String) field, getQuotedSegment(quote));
      }
   }

   /**
    * Get the position of the select column an order by or group by ordinal references.
    * A parsed order by ordinal is an Integer. A parsed group by ordinal, and an order by
    * ordinal loaded from xml, is a String of digits, unless it was written as a quoted
    * identifier ("1"), which is a column.
    * @param quoted <tt>true</tt> if the field was written as a quoted identifier.
    * @return the 1-based position, or 0 if the field is not an ordinal.
    */
   private int getOrdinal(Object field, boolean quoted) {
      if(field instanceof Integer) {
         return Math.max((Integer) field, 0);
      }

      if(!(field instanceof String) || quoted) {
         return 0;
      }

      String str = (String) field;

      if(str.isEmpty() || str.length() > 9) {
         return 0;
      }

      for(int i = 0; i < str.length(); i++) {
         if(str.charAt(i) < '0' || str.charAt(i) > '9') {
            return 0;
         }
      }

      return Integer.parseInt(str);
   }

   /**
    * A group by or order by field that references a select alias.
    * @param field the field to keep: the field itself, or the reference to generate.
    * @param column <tt>true</tt> if the field is the select column of the alias, quoted as
    *               <tt>quote</tt> (see getQuote(OrderByItem)), else the field keeps its own
    *               quoting.
    */
   private record AliasRef(String field, boolean column, String quote, WrittenUnquoted unquoted) {
      AliasRef(String field, boolean column, String quote) {
         this(field, column, quote, null);
      }
   }

   /**
    * Get the group by or order by field that references a select alias. A case-sensitive
    * helper (e.g. postgresql) stores an unquoted alias reference with quotes ("a") while the
    * alias itself is stored without them (Bug #77616). A database that folds unquoted names
    * gives an alias written quoted (as "A") and unquoted (as A) other names, so the quoting
    * of the alias recorded by the parser decides which references match it.
    * @param quoted <tt>true</tt> if the field was written as a quoted identifier.
    * @return the field to keep, or <tt>null</tt> if the field is not a select alias.
    */
   private AliasRef getSelectAliasField(String field, boolean quoted) {
      XSelection select = getSelection();
      String ref = getUnquotedReference(field);
      // the name of the reference: an unquoted one folded by the database, else as written
      String name = ref != null ? foldName(ref) :
         field.indexOf('"') >= 0 ? XUtil.removeQuote(field) : field;

      // a name without its quoting (e.g. set by the query editor), or a helper that doesn't
      // quote every name, matches the alias as stored, as before
      if(dataSource == null || !getSQLHelper().isCaseSensitive() ||
         ref == null && !quoted && name.equals(field))
      {
         return select.getAliasColumn(field) != null ||
            !name.equals(field) && select.getAliasColumn(name) != null ?
            new AliasRef(field, false, null) : null;
      }

      // the alias of the same name. An alias of unknown quoting matches a quoted reference of
      // the same text, as before the quoting was recorded
      for(int i = 0; i < select.getColumnCount(); i++) {
         String calias = select.getAlias(i);
         Boolean aliasQuoted = isAliasQuoted(i);

         if(calias != null && !calias.isEmpty() &&
            (aliasQuoted != null ? name.equals(aliasQuoted ? calias : foldName(calias)) :
             ref == null && calias.equals(name)))
         {
            return getAliasReference(field, i, ref != null);
         }
      }

      // an unquoted reference is folded by the database. An alias of unknown quoting stored in
      // the folded case is that name whether it was quoted or not. Spelled in another case,
      // also the spelling of the reference, it isn't guessed, see isOtherCaseAlias
      for(int i = 0; ref != null && i < select.getColumnCount(); i++) {
         if(name.equals(select.getAlias(i)) && isAliasQuoted(i) == null) {
            return getAliasReference(field, i, true);
         }
      }

      return null;
   }

   /**
    * Get the select alias that an unquoted order by name in another case than the alias
    * references, on a helper that doesn't quote every name (Bug #77644). getSelectAliasField
    * matches the alias as stored, so the name was taken as the table column of that name in
    * any case, while the database resolves an order by name to a select alias first. An
    * unquoted alias is the name in any case, a quoted one if in upper case (see
    * SQLHelper.isAliasCaseInsensitive). The name is then the alias as stored, as if written
    * in its case. A quoted alias in another case is a table column on a database that folds
    * the name, so it isn't matched. Group by isn't changed, a database resolves a group by
    * name to a table column first.
    * @param quoted <tt>true</tt> if the field was written as a quoted identifier.
    * @return the alias, or <tt>null</tt> if the field is not a select alias in another case.
    */
   private AliasRef getOtherCaseOrderByAlias(String field, boolean quoted) {
      String plain = "[A-Za-z_][A-Za-z0-9_]*";

      if(quoted || dataSource == null || getSQLHelper().isCaseSensitive() ||
         !getSQLHelper().isAliasCaseInsensitive() || !field.matches(plain))
      {
         return null;
      }

      XSelection select = getSelection();
      String upper = field.toUpperCase(Locale.ROOT);

      for(int i = 0; i < select.getColumnCount(); i++) {
         String calias = select.getAlias(i);

         if(calias != null && calias.matches(plain) &&
            (upper.equals(calias) || Boolean.FALSE.equals(isAliasQuoted(i)) &&
             upper.equals(calias.toUpperCase(Locale.ROOT))))
         {
            return new AliasRef(calias, false, null);
         }
      }

      return null;
   }

   /**
    * Get the reference to generate for a group by or order by field that matches the alias
    * of a select column.
    * @param unquoted <tt>true</tt> if the field was written unquoted.
    */
   private AliasRef getAliasReference(String field, int idx, boolean unquoted) {
      if(!unquoted) {
         return new AliasRef(field, false, null);
      }

      XSelection select = getSelection();
      String alias = getGeneratedAlias(select.getAlias(idx));

      if(alias.equals(field)) {
         return new AliasRef(field, false, null);
      }

      String column = select.getColumn(idx);

      // a table column is referenced as itself. A reference to the alias as generated could
      // also name a selected column (order by "A" with w."A" selected is ambiguous)
      if(column != null && !select.isExpression(idx) && XUtil.isQualifiedName(column)) {
         // with the names written unquoted of the select column (Bug #77643)
         return new AliasRef(column, true, getSelectQuote(idx, column), getSelectUnquoted(idx));
      }

      return new AliasRef(alias, false, null);
   }

   /**
    * Get the name of a select alias as the helper generates it, quoted. Postgresql quotes
    * the alias as stored, snowflake and exasol write a plain alias unquoted, so the database
    * folds it.
    */
   private String getGeneratedAlias(String alias) {
      String generated = getSQLHelper().quoteColumnAlias(alias);

      return generated.startsWith("\"") && generated.endsWith("\"") && generated.length() > 1 ?
         generated : "\"" + foldName(generated) + "\"";
   }

   /**
    * Check if the alias of a select column was written quoted, see
    * JDBCSelection.isAliasQuoted.
    * @return the flag, or <tt>null</tt> if not known.
    */
   private Boolean isAliasQuoted(int idx) {
      return getSelection() instanceof JDBCSelection ?
         ((JDBCSelection) getSelection()).isAliasQuoted(idx) : null;
   }

   /**
    * Check if an unquoted reference that getSelectAliasField doesn't match has the spelling of
    * a select alias of unknown quoting, in any case. The alias isn't stored in the case the
    * database folds the reference to, so an unquoted alias would be the same name and a
    * quoted one would not, and the quoting wasn't recorded (e.g. a query saved before it was).
    */
   private boolean isOtherCaseAlias(String field) {
      String name = getUnquotedReference(field);

      for(int i = 0; name != null && i < getSelection().getColumnCount(); i++) {
         String calias = getSelection().getAlias(i);

         if(calias != null && calias.equalsIgnoreCase(name) && isAliasQuoted(i) == null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the table column of a group by field that also names a select alias. A database
    * that folds unquoted names (postgresql, snowflake, exasol) resolves a group by name to a
    * table column before a select alias (Bug #77616). Other helpers keep the field as before.
    * @param quoted <tt>true</tt> if the field was written as a quoted identifier.
    * @return the column path, or <tt>null</tt> if not a column or not an alias.
    */
   private String getGroupByColumn(String field, boolean quoted) {
      String path = getUnquotedColumnPath(field);

      if(path != null) {
         return isOtherCaseAlias(field) || getSelectAliasField(field, quoted) != null ?
            path : null;
      }

      // a quoted name, stored without its quotes, is the column of that exact name
      if(!quoted || field.indexOf('.') >= 0 || dataSource == null ||
         !getSQLHelper().isCaseSensitive() || getSelection().getAliasColumn(field) == null)
      {
         return null;
      }

      for(XField xfield : fields) {
         if(xfield.getTable().length() > 0 && field.equals(xfield.getName())) {
            path = xfield.getTable() + "." + xfield.getName();
            copyQuotedField(field, path);
            return path;
         }
      }

      return null;
   }

   // the case an unquoted name is folded to: postgresql lower case, snowflake and exasol
   // upper case
   private String foldName(String name) {
      return "postgresql".equals(SQLHelper.getProductName(getDataSource(), true)) ?
         name.toLowerCase(Locale.ROOT) : name.toUpperCase(Locale.ROOT);
   }

   /**
    * Get the table column of an unquoted column reference that a case-sensitive helper
    * (e.g. postgresql) stores with quotes ("k"), which the field list doesn't find
    * (Bug #77616). A database that folds the unquoted name to one case matches only the
    * column spelled in that case, written in any case (Finding Z). A column in another case (e.g. "A" on
    * postgresql) is only named by a quoted reference, which is stored without the quotes and
    * doesn't come here, it only matches the exact name.
    * @return the column path, or <tt>null</tt> if not found.
    */
   private String getUnquotedColumnPath(String field) {
      String name = getUnquotedReference(field);

      if(name == null) {
         return null;
      }

      String folded = foldName(name);
      // a helper made case-sensitive by db.caseSensitive quotes every name but the database
      // may not fold it, so the column is matched in any case, preferring the folded one
      boolean folding = isFoldingHelper();
      XField match = null;

      for(XField xfield : fields) {
         String fname = xfield.getName() == null ? "" : xfield.getName().toString();

         if(xfield.getTable().length() == 0) {
            continue;
         }

         if(fname.equals(folded)) {
            return xfield.getTable() + "." + xfield.getName();
         }

         if(!folding && match == null && fname.equalsIgnoreCase(name)) {
            match = xfield;
         }
      }

      return match == null ? null : match.getTable() + "." + match.getName();
   }

   /**
    * Get the select column that an unqualified group by or order by name references when
    * the field list doesn't know the columns of the table (Bug #77639). Without the metadata,
    * the field list has the select column (t.k) as an expression, which the name (k) doesn't
    * find. The name is that column only if the from clause has one table (or derived table),
    * of which no column is known, and one plain column of it with the name is selected,
    * written with the same quoting: an unquoted name is folded by the database, a quoted one
    * isn't, so they aren't compared. It is called after the alias checks; a name that is a
    * select alias in any case isn't guessed.
    * @param quote the quoting of the field, see getQuote(OrderByItem).
    * @return the index of the select column, or -1 if not known.
    */
   private int getUnqualifiedSelectColumn(String field, String quote) {
      boolean quoted = quote != null;
      String ref = quoted ? null : getUnquotedReference(field);
      String name = ref != null ? ref : field;

      // a qualified quoted name (t."k") isn't unqualified. An unquoted name is an identifier
      // as the sql lexer reads it (IDENT), e.g. a chinese name, apart from an @variable
      if(getTableCount() != 1 || quoted && !quote.isEmpty() || name.isEmpty() ||
         name.indexOf('"') >= 0 ||
         !quoted && !name.matches("[A-Za-z_\\u0100-\\uFFFE][A-Za-z0-9_\\u0100-\\uFFFE]*"))
      {
         return -1;
      }

      String alias = getTableAlias(0);

      // the columns of a table with metadata are in the field list. A column left out (e.g.
      // hidden by vpm) isn't guessed. The columns of a derived table are those of its
      // select list, named by the field list after the inner column (t.k), not the name
      if(alias == null || !(getTableName(alias) instanceof UniformSQL) &&
         fields.stream().anyMatch(xfield -> alias.equals(xfield.getTable())))
      {
         return -1;
      }

      XSelection select = getSelection();

      for(int i = 0; i < select.getColumnCount(); i++) {
         String calias = select.getAlias(i);

         if(calias != null && XUtil.removeQuote(calias).equalsIgnoreCase(name)) {
            return -1;
         }
      }

      // the select list doesn't keep its columns with a wildcard
      if(isSelectWildcard()) {
         return -1;
      }

      String table = alias.replace("\"", "");
      int found = -1;

      for(int i = 0; i < select.getColumnCount(); i++) {
         String column = select.getColumn(i);

         if(column == null) {
            continue;
         }

         boolean cquoted = select instanceof JDBCSelection &&
            ((JDBCSelection) select).isQuoted(i);
         String seg = cquoted ? ((JDBCSelection) select).getQuotedColumn(i) : null;
         int dot = seg != null && column.endsWith("." + seg) ?
            column.length() - seg.length() - 1 : cquoted ? -1 : column.lastIndexOf('.');

         if(seg == null && dot >= 0) {
            seg = column.substring(dot + 1).replace("\"", "");
         }

         // a column without the qualifier of the table, or an expression, named like the
         // name may be what it references
         if(dot <= 0 || select.isExpression(i) || !XUtil.isQualifiedName(column) ||
            !table.equalsIgnoreCase(column.substring(0, dot).replace("\"", "")))
         {
            if(XUtil.removeQuote(column).equalsIgnoreCase(name) ||
               seg != null && seg.equalsIgnoreCase(name))
            {
               return -1;
            }

            continue;
         }

         if(!seg.equalsIgnoreCase(name)) {
            continue;
         }

         // the same quoting and name, and the same column as another match
         if(quoted != cquoted || quoted && !seg.equals(name) ||
            found >= 0 && !column.equals(select.getColumn(found)))
         {
            return -1;
         }

         found = i;
      }

      return found;
   }

   /**
    * Check if the helper is of a database that folds an unquoted name to one case
    * (postgresql to lower case, snowflake and exasol to upper case), see foldName.
    */
   private boolean isFoldingHelper() {
      SQLHelper helper = getSQLHelper();
      return helper instanceof PostgreSQLHelper || helper instanceof SnowflakeHelper ||
         helper instanceof ExasolHelper;
   }

   /**
    * Record a column segment that the parser stored with quotes ("MixedCase") but that was
    * written unquoted, see isParsedUnquotedSegment.
    */
   public synchronized void addParsedUnquotedSegment(String segment) {
      if(parsedUnquotedSegments == null) {
         parsedUnquotedSegments = new HashSet<>();
      }

      parsedUnquotedSegments.add(segment);
   }

   /**
    * Check if a column segment stored with quotes was written unquoted in the sql parsed
    * last. The database folds such a name, so the metadata step matches its folded case
    * (Bug #77643). Only the metadata step that follows the parse asks for it, see
    * clearParsedUnquotedSegments, since a name added later (e.g. by the query editor, with
    * the quotes and case of the column) is the exact name.
    */
   public synchronized boolean isParsedUnquotedSegment(String segment) {
      return parsedUnquotedSegments != null && parsedUnquotedSegments.contains(segment);
   }

   /**
    * Get a copy of the column segments written unquoted, see isParsedUnquotedSegment.
    */
   public synchronized Set<String> getParsedUnquotedSegments() {
      return parsedUnquotedSegments == null ?
         new HashSet<>() : new HashSet<>(parsedUnquotedSegments);
   }

   /**
    * Add column segments written unquoted, e.g. those of a derived table, which the parser
    * records in the outer query, see isParsedUnquotedSegment.
    */
   public synchronized void addParsedUnquotedSegments(Set<String> segments) {
      if(parsedUnquotedSegments == null) {
         parsedUnquotedSegments = new HashSet<>();
      }

      parsedUnquotedSegments.addAll(segments);
   }

   /**
    * Clear the record of the column segments written unquoted, see isParsedUnquotedSegment.
    */
   public synchronized void clearParsedUnquotedSegments() {
      if(parsedUnquotedSegments != null) {
         parsedUnquotedSegments.clear();
      }
   }

   /**
    * Get the name of a group by or order by field that was written unquoted and that a
    * case-sensitive helper (postgresql, snowflake, exasol) stores with quotes ("k"). A name
    * that can't be written unquoted (e.g. "My A") keeps its quotes on every helper, and a
    * quoted plain name ("K") is stored without them, so neither is returned.
    * @return the name without the quotes, or <tt>null</tt>.
    */
   private String getUnquotedReference(String field) {
      if(field.length() < 3 || field.charAt(0) != '"' ||
         field.indexOf('"', 1) != field.length() - 1 ||
         dataSource == null || !getSQLHelper().isCaseSensitive())
      {
         return null;
      }

      String name = field.substring(1, field.length() - 1);
      return name.matches("[A-Za-z_][A-Za-z0-9_]*") ? name : null;
   }

   /**
    * Check if the select list of the parsed sql has a wildcard (* or t.*). The wildcard is
    * expanded in the select list of this object, which then doesn't keep the positions of
    * the sql, so an ordinal can't be resolved against it.
    */
   private boolean isSelectWildcard() {
      if(hasWildcard(getSelection())) {
         return true;
      }

      if(sqlstring == null) {
         return false;
      }

      try {
         SQLLexer lexer = new SQLLexer(new StringReader(getQuotedSqlString(sqlstring)));
         SQLParser parser = new SQLParser(lexer);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(getDataSource());
         parser.only_select(sql);
         return hasWildcard(sql.getSelection());
      }
      catch(Exception ex) {
         // the positions of the sql are unknown
         return true;
      }
   }

   /**
    * Check if a selection has a wildcard (* or t.*) column.
    */
   public static boolean hasWildcard(XSelection selection) {
      for(int i = 0; selection != null && i < selection.getColumnCount(); i++) {
         String path = selection.getColumn(i);

         if(path != null && (path.equals("*") || path.endsWith(".*"))) {
            return true;
         }
      }

      return false;
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

      // the fields kept without checking the field list (Bug #77570, #77616)
      boolean[] keep = new boolean[groupBy.length];
      // the quoting of each field, two fields may have the same text (Bug #77573)
      String[] quotes = new String[groupBy.length];

      // the names written unquoted of each field, kept for the names a renamed field keeps
      // (Bug #77643)
      WrittenUnquoted[] unquoted = new WrittenUnquoted[groupBy.length];

      for(int i = 0; i < groupBy.length; i++) {
         quotes[i] = getGroupQuote(i);
         unquoted[i] = getGroupByWrittenUnquoted(i);
      }

      Boolean wildcard = null;
      String alias;
      AliasRef aliasRef;
      boolean changed = false;

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
                  quotes[i] = getSelectQuote(idx - 1, path);
                  carryQuotedField(path, quotes[i]);
                  unquoted[i] = getSelectUnquoted(idx - 1);
               }
            }
         }
         else if(getOrdinal(groupBy[i], quotes[i] != null) > 0) {
            if(wildcard == null) {
               wildcard = isSelectWildcard();
            }

            // an ordinal is checked against the select list, not the field list
            keep[i] = wildcard ||
               getOrdinal(groupBy[i], false) <= getSelection().getColumnCount();
         }
         else if(groupBy[i] instanceof String &&
            (alias = getGroupByColumn((String) groupBy[i], quotes[i] != null)) != null)
         {
            // a group by name is a table column before it is a select alias
            groupBy[i] = alias;
            keep[i] = changed = true;
            unquoted[i] = null;
         }
         else if(groupBy[i] instanceof String &&
            (aliasRef = getSelectAliasField((String) groupBy[i], quotes[i] != null)) != null)
         {
            // @by larryl, if group by defined on alias, keep as is otherwise
            // the fullpath may be pointing to a wrong column
            changed = changed || !aliasRef.field().equals(groupBy[i]);
            groupBy[i] = aliasRef.field();
            keep[i] = true;
            // the reference is generated as resolved, a select column as the column
            unquoted[i] = aliasRef.column() ? aliasRef.unquoted() :
               WrittenUnquoted.of(unquoted[i], groupBy[i]);

            if(aliasRef.column()) {
               quotes[i] = aliasRef.quote();
               carryQuotedField(groupBy[i], quotes[i]);
            }
         }
         else if(groupBy[i] instanceof String && isOtherCaseAlias((String) groupBy[i])) {
            // the alias or a column, not known, see syncSorting
            changed = true;
         }
         else if(groupBy[i] instanceof String) {
            String fp = JDBCUtil.getFullPathOf(this, (String) groupBy[i], quotes[i] != null,
                                               unquoted[i]);

            if(fp != null && !fp.equals(groupBy[i])) {
               copyQuotedField((String) groupBy[i], fp);
               groupBy[i] = fp;
               unquoted[i] = renameWrittenUnquoted(unquoted[i], fp);
            }
         }
      }

      // remove group by columns that does not have table
      List<Object> vec = new ArrayList<>();
      List<String> qvec = new ArrayList<>();
      List<WrittenUnquoted> uvec = new ArrayList<>();
      int idx;

      for(int i = 0; i < groupBy.length; i++) {
         Object obj = groupBy[i];

         if(keep[i]) {
            vec.add(obj);
            qvec.add(quotes[i]);
            uvec.add(unquoted[i]);
         }
         else if(obj instanceof String && getOrdinal(obj, quotes[i] != null) == 0) {
            String fname = obj.toString();

            if(getFieldByPath(fname) != null || getField(fname) != null ||
               getSelection().getAliasColumn(fname) != null) {
               vec.add(obj);
               qvec.add(quotes[i]);
               uvec.add(unquoted[i]);
            }
            else if((fname = getUnquotedColumnPath(fname)) != null) {
               vec.add(fname);
               qvec.add(quotes[i]);
               uvec.add(renameWrittenUnquoted(unquoted[i], fname));
               changed = true;
            }
            else if((idx = getUnqualifiedSelectColumn((String) obj, quotes[i])) >= 0) {
               // the select column, with its quoting (Bug #77639)
               String path = getSelection().getColumn(idx);
               String quote = getSelectQuote(idx, path);
               carryQuotedField(path, quote);
               vec.add(path);
               qvec.add(quote);
               // and the names written unquoted of the select column (Bug #77643)
               uvec.add(getSelectUnquoted(idx));
               changed = true;
            }
         }
      }

      // removed or replaced some fields
      if(changed || vec.size() != groupBy.length) {
         setGroupBy(vec.toArray(new Object[0]), qvec.toArray(new String[0]));
         setGroupByWrittenUnquoted(uvec.toArray(new WrittenUnquoted[0]));
      }
      else {
         groupQuotes = quotes;
         groupQuoteFields = groupBy.clone();
         groupUnquoted = unquoted;
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
            String oldTableAlias = getAliasMapKey(getTableFromPath(path), aliasMap);

            if(aliasMap.containsKey(oldTableAlias)) {
               String newTableAlias = (String) aliasMap.get(oldTableAlias);

               if(newTableAlias == null) {  // old table is removed
                  xselect.removeColumn(i);
                  continue;
               }
               else if(newTableAlias.equals(oldTableAlias)) {
                  continue;
               }

               JDBCSelection jselect = xselect instanceof JDBCSelection ?
                  (JDBCSelection) xselect : null;
               // the flag of this column, which keeps it under the new name
               boolean quoted = jselect != null && jselect.isQuoted(i);
               String col = getColumnFromPath(path, oldTableAlias,
                  quoted ? jselect.getQuotedColumn(i) : null, quoted);
               String alias = xselect.getAlias(i);

               if(alias != null && alias.startsWith(oldTableAlias + ".")) {
                  alias = newTableAlias + "." +
                     alias.substring(oldTableAlias.length() + 1);
               }

               String type = xselect.getType(path);
               String newPath = newTableAlias + "." + col;

               // the same column under the new table, it keeps its quoting, and the names
               // written unquoted it keeps (Bug #77643)
               if(jselect != null) {
                  WrittenUnquoted names = jselect.getWrittenUnquoted(i);
                  jselect.renameColumn(i, newPath);
                  jselect.setWrittenUnquoted(i, renamedRecord(names, newPath));
               }
               else {
                  xselect.setColumn(i, newPath);
               }

               xselect.setAlias(i, alias);
               xselect.setType(newPath, type);
               xselect.setDescription(newPath, xselect.getDescription(path));
            }
            else if(oldTableAlias.length() > 0 && getTableIndex(oldTableAlias) < 0) {
               XField field = getFieldByPath(path);

               if(field == null || field.getTable().length() > 0) {
                  int dot = path.lastIndexOf('.');

                  if(dot > 0) {
                     xselect.removeColumn(i);
                  }
               }
            }
         }
         else {
            String alias = xselect.getAlias(i);
            String type = xselect.getType(path);
            String description = xselect.getDescription(path);
            String newPath = JDBCUtil.replaceTableInExpression(tables, path, aliasMap, dataSource);

            // the table renamed in the name, a quoted name keeps its quoting, and the names
            // written unquoted it keeps (Bug #77643)
            if(xselect instanceof JDBCSelection) {
               WrittenUnquoted names = ((JDBCSelection) xselect).getWrittenUnquoted(i);
               ((JDBCSelection) xselect).renameColumn(i, newPath);
               ((JDBCSelection) xselect).setWrittenUnquoted(i, renamedRecord(names, newPath));
            }
            else {
               xselect.setColumn(i, newPath);
            }

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

      // order by, each item by its position (Bug #77573)
      OrderByItem[] orders = getOrderByItems();

      for(int i = 0; i < orders.length; i++) {
         if(orders[i].getField() instanceof String) {
            String field = (String) orders[i].getField();

            if(xselect.isAlias(field)) {
               continue;
            }

            String oldTableAlias = getAliasMapKey(getTableFromPath(field), aliasMap);

            if(aliasMap.containsKey(oldTableAlias)) {
               String newTableAlias = (String) aliasMap.get(oldTableAlias);

               if(newTableAlias == null || newTableAlias.equals(oldTableAlias)) {
                  continue;
               }

               String quote = getQuote(orders[i]);
               String col = getRenamedColumn(field, oldTableAlias, quote);
               replaceOrderBy(i, newTableAlias + "." + col, orders[i].getOrder());
               copyQuotedField(field, newTableAlias + "." + col);
            }
         }
      }

      // group by
      for(int i = 0; groups != null && i < groups.length; i++) {
         if(groups[i] instanceof String) {
            if(xselect.isAlias((String) groups[i])) {
               continue;
            }

            String oldTableAlias = getAliasMapKey(getTableFromPath((String) groups[i]), aliasMap);

            if(aliasMap.containsKey(oldTableAlias)) {
               String newTableAlias = (String) aliasMap.get(oldTableAlias);

               if(newTableAlias == null) {
                  continue;
               }

               String field = (String) groups[i];
               // the quoting of this field, which keeps its position
               String quote = getGroupQuote(i);
               String col = getRenamedColumn(field, oldTableAlias, quote);
               renameGroupField(i, newTableAlias + "." + col);
               copyQuotedField(field, (String) groups[i]);
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
    * Get the column of an order by or group by item whose table is renamed. An item of
    * an unaliased table on postgresql, snowflake or exasol is stored with in-band quotes
    * ("q"."k"), so its column keeps them ("k"). The column without them (k) doesn't match
    * the select column x."k" when there is no table metadata, and the item would be
    * dropped (Bug #77648).
    * @param quote the quoting of the item, see getQuote(OrderByItem).
    */
   private String getRenamedColumn(String path, String table, String quote) {
      if(quote == null && table != null && table.startsWith("\"") &&
         path.startsWith(table + ".") && path.length() > table.length() + 1)
      {
         return path.substring(table.length() + 1);
      }

      return getColumnFromPath(path, table, getQuotedSegment(quote), quote != null);
   }

   /**
    * Get the column part of a path whose table is renamed. The column of a quoted
    * identifier keeps its written case, it isn't looked up ignoring case, which could
    * find a column that differs only in case (MIXEDCASE for "MixedCase").
    * @param segment the column segment recorded for a qualified quoted identifier.
    * @param quoted <tt>true</tt> if the path was written as a quoted identifier.
    */
   private String getColumnFromPath(String path, String table, String segment, boolean quoted) {
      if(quoted) {
         if(segment != null && path.endsWith("." + segment)) {
            return segment;
         }

         if(table != null && !table.isEmpty() && path.startsWith(table + ".")) {
            return path.substring(table.length() + 1);
         }

         XField field = getFieldByPath(path, true);

         if(field != null && field.getName() != null) {
            return field.getName().toString();
         }
      }

      return getColumnFromPath(path);
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
         String table = getAliasMapKey(getTableFromPath(path), aliasmap);
         String quote = getSQLHelper().getQuote();

         if(aliasmap.containsKey(table)) {
            String newTable = (String) aliasmap.get(table);

            if(newTable == null) {
               return false;
            }

            String col = getColumnFromPath(path);

            if(!col.startsWith(newTable)) {
               // the names written unquoted the field keeps (Bug #77643)
               WrittenUnquoted names = expr.getWrittenUnquoted();
               expr.setValue(newTable + "." + col, XExpression.FIELD);
               expr.setWrittenUnquoted(renamedRecord(names, expr.toString()));
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
            String quoted = getInBandQuoted(table);
            return aliasmap.contains(table) || (quoted != null && aliasmap.contains(quoted));
         }
      }

      return false;
   }

   /**
    * Get the key of the alias map that a table qualifier names. A helper that keeps
    * identifier case (postgresql, snowflake, exasol) stores the alias of an unaliased
    * table with in-band quotes ("q"), while getTableFromPath may return the qualifier
    * with the quotes stripped (q), so the in-band form is tried when there is no exact
    * match (Bug #77648).
    * @return the matching key, or the table itself if no key matches.
    */
   private String getAliasMapKey(String table, Map<?, ?> aliasmap) {
      if(table == null || aliasmap.containsKey(table)) {
         return table;
      }

      String quoted = getInBandQuoted(table);
      return quoted != null && aliasmap.containsKey(quoted) ? quoted : table;
   }

   /**
    * Get a table qualifier with the in-band quotes that XUtil.getTablePart strips,
    * or null if it is empty or already quoted.
    */
   private static String getInBandQuoted(String table) {
      if(table == null || table.isEmpty() || table.startsWith("\"")) {
         return null;
      }

      return "\"" + table + "\"";
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
            removeQuotedAggregate(field);
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

      // exact match first, then case-insensitive (see getTableIndex)
      for(boolean ignoreCase : new boolean[] { false, true }) {
         for(int i = 0; i < size; i++) {
            SelectTable table = tables.elementAt(i);

            if(table.getName() instanceof String &&
               nameMatches(name, (String) table.getName(), ignoreCase))
            {
               return table.getAlias();
            }
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

      // resolve the alias the same way as getTableIndex
      int index = getTableIndex(alias);
      return index >= 0 ? tables.get(index).getName() : null;
   }

   /**
    * Get the selected table by alias.
    */
   public synchronized SelectTable getSelectTable(String alias) {
      if(alias == null) {
         return null;
      }

      // an alias match wins, resolved the same way as getTableIndex, so that
      // this agrees with getTableIndex and getTableName on which table a name
      // refers to
      int index = getTableIndex(alias);

      if(index >= 0) {
         return tables.get(index);
      }

      // otherwise match the table name, exact match first, then
      // case-insensitive
      for(boolean ignoreCase : new boolean[] { false, true }) {
         for(int i = 0; i < tables.size(); i++) {
            Object name = tables.elementAt(i).getName();

            if(name != null && nameMatches(alias, name.toString(), ignoreCase)) {
               return tables.elementAt(i);
            }
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
    * Get a field by its full path.
    * @param path the path of field in format of "table.col".
    * @param exactCase <tt>true</tt> if the column was written as a quoted identifier, so a
    *                  field whose name has the same case is preferred over one that only
    *                  matches ignoring case (e.g. "MixedCase" over MIXEDCASE).
    */
   public synchronized XField getFieldByPath(String path, boolean exactCase) {
      XField field = getFieldByPath(path);

      if(!exactCase || field == null) {
         return field;
      }

      String path2 = trimPath(path);
      int idx = path2.lastIndexOf('.');
      String col = idx < 0 ? path : path2.substring(idx + 1);
      String table = idx < 0 ? "" : path2.substring(0, idx);

      if(col.equals(String.valueOf(field.getName()))) {
         return field;
      }

      for(XField exact : fields) {
         String ftable = exact.getTable();

         if(col.equals(String.valueOf(exact.getName())) &&
            (table.isEmpty() || ftable.equalsIgnoreCase(table) ||
             trimPath(ftable).equalsIgnoreCase(table)))
         {
            return exact;
         }
      }

      return field;
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

      // exact match first, so that tables whose names differ only in case (e.g. "A" and
      // "a") resolve to the right table (#77569)
      for(boolean ignoreCase : new boolean[] { false, true }) {
         for(SelectTable selectTable : tables) {
            String table_alias = selectTable.getAlias();
            Object table_name = selectTable.getName();

            if(ignoreCase ? table.equalsIgnoreCase(table_alias) : table.equals(table_alias)) {
               return table_alias;
            }

            if(ignoreCase ? table.equalsIgnoreCase(table_name + "") :
               table.equals(table_name + ""))
            {
               return table_alias != null && table_alias.length() > 0 ?
                  table_alias : (String) table_name;
            }
         }
      }

      // a qualifier written as the end of a table name with quoted segments, e.g. "a".id
      // for "S"."a" (#77569)
      String found = null;

      for(SelectTable selectTable : tables) {
         Object table_name = selectTable.getName();

         if(selectTable.getQuotedSegments() != null && table_name instanceof String &&
            table_name.equals(selectTable.getAlias()) &&
            ((String) table_name).endsWith("." + table))
         {
            if(found != null) {
               return null;
            }

            found = (String) table_name;
         }
      }

      return found;
   }

   /**
    * Get the table index in the table list.
    * @param alias string value.
    * @return -1 if not find.
    */
   public synchronized int getTableIndex(String alias) {
      int size = tables.size();

      // exact match first, then case-insensitive, so that tables whose aliases
      // differ only in case (e.g. "A" and "a") resolve to the right table. When
      // only one table matches, the result is the same as a case-insensitive
      // search.
      for(boolean ignoreCase : new boolean[] { false, true }) {
         for(int i = 0; i < size; i++) {
            SelectTable stable = tables.get(i);
            String salias = stable.getAlias();
            Object sname = stable.getName();

            // one table with its twin, see isHiddenTwin
            if(isHiddenTwin(stable)) {
               continue;
            }

            if(salias == null && (sname instanceof String)) {
               salias = (String) sname;
            }

            if(salias == null) {
               continue;
            }

            if(nameMatches(salias, alias, ignoreCase)) {
               return i;
            }
         }
      }

      return -1;
   }

   /**
    * Check if a table is the twin written unquoted of a table without an alias of the same
    * stored name written quoted (MyTab and "MyTab", see SQLParser addTable), and the names
    * written unquoted aren't folded (db.foldUnquotedIdentifiers is false or the helper's case
    * isn't known). It's then one table with its twin, as before Bug #77643: it isn't
    * generated and doesn't name a table.
    */
   public synchronized boolean isHiddenTwin(SelectTable table) {
      Object name = table.getName();
      String alias = table.getAlias();

      if(!(name instanceof String) || alias == null || alias.equals(name) ||
         table.getWrittenUnquoted() == null)
      {
         return false;
      }

      boolean twin = false;

      for(SelectTable other : tables) {
         twin = twin || other != table && name.equals(other.getName()) && name.equals(other.getAlias());
      }

      if(!twin) {
         return false;
      }

      // not the cached helper of this query: this is called while the query is parsed, and
      // the cached helper would keep the joins parsed so far
      SQLHelper helper = SQLHelper.getSQLHelper(getDataSource());
      String quote = helper.getQuote();
      boolean twinAlias = alias.equals(((String) name).replace(quote, "")) ||
         !quote.isEmpty() && alias.startsWith(quote) && alias.equalsIgnoreCase((String) name);

      return twinAlias && (!SQLHelper.isFoldUnquotedIdentifiers() ||
         helper.getIdentifierCase() == SQLHelper.IdentifierCase.UNKNOWN);
   }

   /**
    * Get the index of the from clause table that the table part of a join column
    * (XJoin.getTable1/getTable2) refers to. The table part has its quotes removed,
    * while a table without an alias keeps its quoted name as the alias (e.g. "a"
    * when the data source quotes identifiers), so quotes are ignored when there is
    * no exact match. A case-sensitive match is preferred, since a quoted name is
    * case-sensitive (e.g. "A" and "a" can be two tables). As a last resort, a bare
    * table name refers to the one unaliased schema-qualified table it names (emp for
    * scott.emp).
    * @param table the table part of a join column.
    * @return -1 if the table is empty or not found.
    */
   public synchronized int getJoinTableIndex(String table) {
      if(table == null || table.isEmpty()) {
         return -1;
      }

      String name = stripIdentifierQuotes(table);
      int index = findJoinTable(table, false, false);
      index = index >= 0 ? index : findJoinTable(name, true, false);
      index = index >= 0 ? index : getTableIndex(table);
      index = index >= 0 ? index : findJoinTable(name, true, true);
      return index >= 0 ? index : findSchemaTable(name);
   }

   /**
    * Find the unaliased schema-qualified table (e.g. scott.emp) that a bare table name
    * (emp) refers to, as in legacy sql such as
    * "from scott.emp, scott.dept where emp.deptno = dept.deptno(+)". The table is only
    * found if it is the one unaliased table with that last name segment, an ambiguous
    * name (s1.emp and s2.emp) isn't resolved. As for the other lookups, a case-sensitive
    * match is preferred.
    * @return -1 if no single table matches.
    */
   private int findSchemaTable(String table) {
      if(table.indexOf('.') >= 0) {
         return -1;
      }

      int index = findSchemaTable(table, false);
      index = index == -1 ? findSchemaTable(table, true) : index;
      return Math.max(index, -1);
   }

   /**
    * Find the unaliased schema-qualified table whose last name segment is the table.
    * @return the index of the single matching table, -1 if no table matches, or -2 if
    * more than one table matches.
    */
   private int findSchemaTable(String table, boolean ignoreCase) {
      int index = -1;

      for(int i = 0; i < tables.size(); i++) {
         SelectTable stable = tables.get(i);
         String salias = stable.getAlias();

         // a table with an alias can only be referred to by its alias
         if(!(stable.getName() instanceof String sname) ||
            salias != null && !salias.equals(sname))
         {
            continue;
         }

         String segment = getLastNameSegment(sname);

         if(segment != null &&
            (ignoreCase ? table.equalsIgnoreCase(segment) : table.equals(segment)))
         {
            if(index >= 0) {
               return -2;
            }

            index = i;
         }
      }

      return index;
   }

   /**
    * Get the last segment of a qualified table name, without quotes (emp of scott.emp,
    * "scott"."emp" or `scott.emp`, BigQuery quotes the whole path in one pair of
    * backticks).
    * @return null if the name isn't qualified.
    */
   private static String getLastNameSegment(String name) {
      String stripped = stripIdentifierQuotes(name);
      int dot = stripped.lastIndexOf('.');
      return dot < 0 ? null : stripped.substring(dot + 1);
   }

   private int findJoinTable(String table, boolean strip, boolean ignoreCase) {
      for(int i = 0; i < tables.size(); i++) {
         SelectTable stable = tables.get(i);
         String salias = stable.getAlias();

         if(salias == null && (stable.getName() instanceof String)) {
            salias = (String) stable.getName();
         }

         if(salias == null) {
            continue;
         }

         salias = strip ? stripIdentifierQuotes(salias) : salias;

         if(ignoreCase ? table.equalsIgnoreCase(salias) : table.equals(salias)) {
            return i;
         }
      }

      return -1;
   }

   private static String stripIdentifierQuotes(String name) {
      StringBuilder buf = new StringBuilder(name.length());

      for(int i = 0; i < name.length(); i++) {
         char ch = name.charAt(i);

         if(ch != '"' && ch != '`' && ch != '[' && ch != ']') {
            buf.append(ch);
         }
      }

      return buf.toString();
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

         // the quoted segments describe the name, keep them only for the same name
         if(Tool.equals(ostable.getName(), name)) {
            nstable.setQuotedSegments(ostable.getQuotedSegments());
         }

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

         // the quoted segments describe the name, keep them only for the same name
         if(Tool.equals(ostable.getName(), name)) {
            nstable.setQuotedSegments(ostable.getQuotedSegments());
         }

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
         this.quotedAggregates = new HashMap<>(source.quotedAggregates);

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
            replaceOrderBy(i, newfield, neworder);
            return;
         }
      }
   }

   /**
    * Replace the field and sorting order of the order by item at a position. The item keeps
    * its quoting (Bug #77573).
    */
   private void replaceOrderBy(int idx, Object newfield, String neworder) {
      OrderByItem item = orderByList.elementAt(idx);
      Object oldfield = item.getField();
      String quote = getQuote(item);
      OrderByItem nitem = new OrderByItem(newfield, neworder);
      nitem.setQuoted(quote != null, getQuotedSegment(quote));
      // the names written unquoted the field keeps (Bug #77643)
      nitem.setWrittenUnquoted(newfield instanceof String ?
         renamedRecord(item.getWrittenUnquoted(), (String) newfield) : null);
      orderByList.setElementAt(nitem, idx);

      // the record follows its order by item (e.g. a renamed table)
      if(!Objects.equals(oldfield, newfield)) {
         String seg = removeQuotedAggregate(oldfield);

         if(seg != null && newfield instanceof String) {
            quotedAggregates.put((String) newfield, seg);
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
      // a record left behind would apply again to the same text added later
      quotedAggregates.clear();
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
            removeQuotedAggregate(field);
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
      // the quoting recorded by position belongs to the old list. A new list is quoted by
      // the text of its fields (isQuotedField)
      if(groups != this.groups) {
         groupQuotes = null;
         groupUnquoted = null;
      }

      this.groups = groups;

      if(groups == null) {
         having = null;
      }
   }

   /**
    * Set group by list with the quoting of each field.
    * @param groups by array.
    * @param quotes the quoting of each field: <tt>null</tt> if not written as a quoted
    *               identifier, "" for a bare quoted identifier ("x y"), or the column segment
    *               of a qualified one (MixedCase for t."MixedCase"). If the array is
    *               <tt>null</tt>, the fields are quoted by their text (isQuotedField).
    */
   public synchronized void setGroupBy(Object[] groups, String[] quotes) {
      setGroupBy(groups);
      this.groupQuotes = groups != null && quotes != null && quotes.length == groups.length ?
         quotes.clone() : null;
      this.groupQuoteFields = groupQuotes != null ? groups.clone() : null;
   }

   /**
    * Get group by list.
    * @return group by list.
    */
   public synchronized Object[] getGroupBy() {
      return groups;
   }

   /**
    * Check if a group by or order by field was written as a quoted identifier (e.g. "x y")
    * in the parsed sql. The field is stored without its quotes.
    * @param field the group by or order by field.
    * @return <tt>true</tt> if quoted, <tt>false</tt> otherwise.
    */
   public synchronized boolean isQuotedField(Object field) {
      return field instanceof String && quotedFields.containsKey(field);
   }

   /**
    * Set whether a group by or order by field was written as a quoted identifier.
    * @param field the group by or order by field.
    * @param quoted <tt>true</tt> if quoted, <tt>false</tt> otherwise.
    */
   public synchronized void setQuotedField(String field, boolean quoted) {
      if(quoted) {
         quotedFields.putIfAbsent(field, "");
      }
      else {
         quotedFields.remove(field);
      }
   }

   /**
    * Set a group by or order by field as written as a qualified quoted identifier.
    * @param segment the column segment as written, or <tt>null</tt> for a bare identifier.
    */
   public synchronized void setQuotedField(String field, String segment) {
      quotedFields.put(field, segment == null ? "" : segment);
   }

   /**
    * Get the column segment, as written, of a group by or order by field written as a
    * qualified quoted identifier (t."MixedCase").
    */
   public synchronized String getQuotedFieldColumn(Object field) {
      String seg = field instanceof String ? quotedFields.get(field) : null;
      return seg == null || seg.isEmpty() ? null : seg;
   }

   /**
    * Add an order by item unless an item of the same field and quoting exists, in which case
    * the first item decides the direction, as in sql. Unlike setOrderBy, an item of the same
    * text with another quoting ("MixedCase" and MixedCase) is a separate item (Bug #77573).
    * @param field the order by field.
    * @param order the sorting order.
    * @param quoted <tt>true</tt> if written as a quoted identifier.
    * @param segment the column segment of a qualified quoted identifier as written, or
    *                <tt>null</tt>.
    * @return <tt>true</tt> if added.
    */
   public synchronized boolean addOrderBy(Object field, String order, boolean quoted,
                                          String segment)
   {
      return addOrderBy(field, order, quoted, segment, null);
   }

   /**
    * Add an order by item unless an item of the same field, quoting and names written
    * unquoted exists, see addOrderBy(Object, String, boolean, String).
    * @param unquoted the names of the field written unquoted, see
    *                 OrderByItem.getWrittenUnquoted (Bug #77643).
    * @return <tt>true</tt> if added.
    */
   public synchronized boolean addOrderBy(Object field, String order, boolean quoted,
                                          String segment, WrittenUnquoted unquoted)
   {
      String quote = !quoted ? null : segment == null ? "" : segment;
      unquoted = WrittenUnquoted.of(unquoted, field);

      for(OrderByItem item : orderByList) {
         if(item.getField().equals(field) && Objects.equals(getQuote(item), quote) &&
            Objects.equals(item.getWrittenUnquoted(), unquoted))
         {
            return false;
         }
      }

      OrderByItem item = new OrderByItem(field, order);
      item.setQuoted(quoted, segment);
      item.setWrittenUnquoted(unquoted);
      orderByList.add(item);
      return true;
   }

   /**
    * Get the names written unquoted of the order by item at a position, see
    * OrderByItem.getWrittenUnquoted.
    * @param idx the index in getOrderByItems().
    */
   public synchronized WrittenUnquoted getOrderByWrittenUnquoted(int idx) {
      return idx >= 0 && idx < orderByList.size() ?
         orderByList.get(idx).getWrittenUnquoted() : null;
   }

   /**
    * Set the names written unquoted of each group by field, in the parsed sql (Bug #77643).
    * A case-sensitive helper stores such a name in quotes in the case it was written.
    * @param names the names of each field, recorded for its text, or <tt>null</tt>.
    */
   public synchronized void setGroupByWrittenUnquoted(WrittenUnquoted[] names) {
      this.groupUnquoted = groups != null && names != null && names.length == groups.length ?
         names.clone() : null;
   }

   /**
    * Get the names written unquoted of the group by field at a position.
    * @param idx the index in getGroupBy().
    * @return the names, or <tt>null</tt> if none or not recorded for the current field.
    */
   public synchronized WrittenUnquoted getGroupByWrittenUnquoted(int idx) {
      return groups != null && groupUnquoted != null && groupUnquoted.length == groups.length &&
         idx >= 0 && idx < groups.length ? WrittenUnquoted.of(groupUnquoted[idx], groups[idx]) :
         null;
   }

   /**
    * Set the names written unquoted of the sql string, of a derived table saved as the text of
    * its sql (Bug #77643).
    * @param names the names, recorded for the sql string, or <tt>null</tt>.
    */
   public synchronized void setSQLStringWrittenUnquoted(WrittenUnquoted names) {
      this.sqlUnquoted = names;
   }

   /**
    * Check if the order by item at a position was written as a quoted identifier.
    * @param idx the index in getOrderByItems().
    */
   public synchronized boolean isQuotedOrderBy(int idx) {
      return idx >= 0 && idx < orderByList.size() && getQuote(orderByList.get(idx)) != null;
   }

   /**
    * Get the column segment, as written, of the order by item at a position written as a
    * qualified quoted identifier (t."MixedCase").
    * @param idx the index in getOrderByItems().
    */
   public synchronized String getQuotedOrderByColumn(int idx) {
      return idx >= 0 && idx < orderByList.size() ?
         getQuotedSegment(getQuote(orderByList.get(idx))) : null;
   }

   /**
    * Check if the group by field at a position was written as a quoted identifier.
    * @param idx the index in getGroupBy().
    */
   public synchronized boolean isQuotedGroupBy(int idx) {
      return getGroupQuote(idx) != null;
   }

   /**
    * Get the column segment, as written, of the group by field at a position written as a
    * qualified quoted identifier (t."MixedCase").
    * @param idx the index in getGroupBy().
    */
   public synchronized String getQuotedGroupByColumn(int idx) {
      return getQuotedSegment(getGroupQuote(idx));
   }

   /**
    * Get the quoting of an order by item: <tt>null</tt> if not written as a quoted identifier,
    * "" for a bare quoted identifier, or the column segment of a qualified one. An item added
    * without its quoting (e.g. by setOrderBy) is quoted by its text.
    */
   private String getQuote(OrderByItem item) {
      if(!item.isQuoteSet()) {
         return item.getField() instanceof String ? quotedFields.get(item.getField()) : null;
      }

      return !item.isQuoted() ? null :
         item.getQuotedColumn() == null ? "" : item.getQuotedColumn();
   }

   /**
    * Replace a group by field with a name of the same column, under a renamed table or
    * qualifier, keeping its quoting. Writing the group by array drops it.
    */
   private void renameGroupField(int idx, Object field) {
      boolean recorded = groupQuotes != null && groupQuoteFields != null &&
         groupQuotes.length == groups.length && groupQuoteFields.length == groups.length &&
         Objects.equals(groupQuoteFields[idx], groups[idx]);
      WrittenUnquoted names = getGroupByWrittenUnquoted(idx);
      groups[idx] = field;

      if(recorded) {
         groupQuoteFields[idx] = field;
      }

      // the names written unquoted the field keeps (Bug #77643)
      if(groupUnquoted != null && groupUnquoted.length == groups.length) {
         groupUnquoted[idx] = field instanceof String ? renamedRecord(names, (String) field) : null;
      }
   }

   /**
    * Get the record of the names written unquoted of a text renamed in place (e.g. the
    * qualifier of a renamed table): the names it keeps, see WrittenUnquoted.rename.
    */
   private WrittenUnquoted renamedRecord(WrittenUnquoted names, String text) {
      return names == null ? null : names.rename(text, getSQLHelper().getQuote());
   }

   /**
    * Get the quoting of the group by field at a position, see getQuote(OrderByItem).
    */
   private String getGroupQuote(int idx) {
      if(groups == null || idx < 0 || idx >= groups.length) {
         return null;
      }

      // the quoting recorded for the field, unless the field was replaced by another name
      if(groupQuotes != null && groupQuotes.length == groups.length &&
         groupQuoteFields != null && groupQuoteFields.length == groups.length &&
         Objects.equals(groupQuoteFields[idx], groups[idx]))
      {
         return groupQuotes[idx];
      }

      return groups[idx] instanceof String ? quotedFields.get(groups[idx]) : null;
   }

   // the column segment of a quoting, see getQuote(OrderByItem)
   private static String getQuotedSegment(String quote) {
      return quote == null || quote.isEmpty() ? null : quote;
   }

   /**
    * Set the column segment, as written, of the qualified quoted column that is the only
    * argument of an order by aggregate (MixedCase for sum(t."MixedCase")). The stored text
    * of the aggregate doesn't show the quotes on a case-sensitive helper.
    * @param segment the segment, or <tt>null</tt> to clear it.
    */
   public synchronized void setQuotedAggregate(String field, String segment) {
      if(segment == null || segment.isEmpty()) {
         quotedAggregates.remove(field);
      }
      else {
         quotedAggregates.put(field, segment);
      }
   }

   /**
    * Get the column segment, as written, of the qualified quoted column that is the only
    * argument of an order by aggregate.
    * @return the segment, or <tt>null</tt> if not recorded, which doesn't mean unquoted
    *         (e.g. a query saved before it was recorded).
    */
   public synchronized String getQuotedAggregate(Object field) {
      return field instanceof String ? quotedAggregates.get(field) : null;
   }

   // remove the record of a removed order by field
   private String removeQuotedAggregate(Object field) {
      return field instanceof String ? quotedAggregates.remove(field) : null;
   }

   // keep the quoted aggregate column of a renamed order by field
   private void moveQuotedAggregate(String field, String nfield) {
      String seg = quotedAggregates.get(field);

      if(seg != null && nfield != null && !nfield.equals(field)) {
         quotedAggregates.remove(field);
         quotedAggregates.put(nfield, seg);
      }
   }

   private String quotedAggregateAttribute(Object field) {
      String seg = getQuotedAggregate(field);
      return seg != null ? " quotedAggregate=\"" + Tool.escape(seg) + "\"" : "";
   }

   private void copyQuotedField(String field, String nfield) {
      if(isQuotedField(field)) {
         setQuotedField(nfield, getQuotedFieldColumn(field));
      }
   }

   /**
    * Get the xml attribute of the names written unquoted of a group by or order by field
    * (Bug #77643). Older versions ignore it and generate the names as stored.
    */
   private static String unquotedAttribute(WrittenUnquoted names) {
      return WrittenUnquoted.toXMLAttribute(names);
   }

   /**
    * Get the xml attribute that marks a group by or order by field as a quoted identifier.
    * A qualified quoted identifier (t."MixedCase") is written as quotedColumn only, not
    * quoted="true", which older versions read as quoting the whole name ("t.MixedCase").
    * They ignore quotedColumn and generate the name unquoted, as before.
    */
   private static String quotedFieldAttribute(String quote, Object field, Set<String> quotedTexts) {
      if(quote == null) {
         // an element without the attribute is quoted by its text when read (saved before
         // the quoting was kept per element), so an unquoted field of a quoted text says so.
         // Older versions only read "true"
         return quotedTexts.contains(field) ? " quoted=\"false\"" : "";
      }

      return !quote.isEmpty() ? " quotedColumn=\"" + Tool.escape(quote) + "\"" :
         " quoted=\"true\"";
   }

   /**
    * Get the text of the group by and order by fields that are written as quoted, or that
    * read as quoted by their text.
    */
   private Set<String> getQuotedFieldTexts(OrderByItem[] orderItems) {
      Set<String> texts = new HashSet<>(quotedFields.keySet());

      for(OrderByItem item : orderItems) {
         if(item.getField() instanceof String && getQuote(item) != null) {
            texts.add((String) item.getField());
         }
      }

      for(int i = 0; groups != null && i < groups.length; i++) {
         if(groups[i] instanceof String && getGroupQuote(i) != null) {
            texts.add((String) groups[i]);
         }
      }

      return texts;
   }

   /**
    * Check if a group by or order by element has its quoting, written since the quoting is
    * kept per element (Bug #77573). Without it, the field is quoted by its text.
    */
   private static boolean hasQuotedFieldAttribute(Element node) {
      String seg = Tool.getAttribute(node, "quotedColumn");
      String quoted = Tool.getAttribute(node, "quoted");
      // a malformed value is read as absent
      return "true".equals(quoted) || "false".equals(quoted) || seg != null && !seg.isEmpty();
   }

   /**
    * Read the quoting of a group by or order by field written by quotedFieldAttribute.
    * @return the quoting of the element, see getQuote(OrderByItem).
    */
   private String readQuotedField(Element node, String field) {
      String seg = Tool.getAttribute(node, "quotedColumn");

      if("true".equals(Tool.getAttribute(node, "quoted"))) {
         setQuotedField(field, true);
         return "";
      }
      else if(seg != null && !seg.isEmpty()) {
         setQuotedField(field, seg);
         return seg;
      }

      return null;
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

            // a join merged by 'or' with a new pair of tables puts the where tree in an or
            // set. Mark it as a join group like the 'or' set of createJoinNode(), so ANSI
            // SQL generation keeps treating the joins in it as joins of the query.
            if(XSet.OR.equalsIgnoreCase(merging) && where instanceof XSet &&
               XSet.OR.equalsIgnoreCase(((XSet) where).getRelation()))
            {
               where.setGroup(true);
            }
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
      getJoins(root, joins, false);
   }

   /**
    * Get the joins (into the vector) from the condition tree. A join under a
    * negated set, e.g. "not (a.id = b.k and a.k = 1)", is a condition and not a
    * join of the query. Legacy outer joins (*=, =*, (+)) are still returned
    * there, they can't be written as a condition.
    */
   private void getJoins(XFilterNode root, List<XJoin> joins, boolean negated) {
      if(XFilterNode.isJoinJunction(root)) {
         negated = negated || root.isIsNot();

         for(int i = 0; i < root.getChildCount(); i++) {
            XNode child = root.getChild(i);

            if(child instanceof XJoin) {
               if(!negated || ((XJoin) child).isOuterJoin()) {
                  joins.add((XJoin) child);
               }
            }
            else if(child instanceof XSet) {
               getJoins((XFilterNode) child, joins, negated);
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
         cachedSQLHelper = null;

         // lossy was derived with the old data source's sql helper, e.g. a join order the new
         // helper (Oracle without ansi join) regenerates differently. Re-derive it in isLossy()
         // (Bug #77576), or the map key access check of a ClickHouse source that was skipped
         // without one. A lossy kept while parsing is off can't be re-derived (Bug #77477)
         if(parseIt && sqlstring != null) {
            lossy = null;
         }
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
      // don't cache? generate statement. The sql generated to be saved isn't cached either
      if(!cache || WrittenUnquoted.isMarking()) {
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
         equalsQuotedSegments(tables, that.tables) &&
         Objects.equals(fields, that.fields) &&
         Objects.equals(orderByList, that.orderByList) &&
         Arrays.equals(groups, that.groups) &&
         Arrays.equals(groupQuotes, that.groupQuotes) &&
         Objects.equals(quotedFields, that.quotedFields) &&
         Objects.equals(quotedAggregates, that.quotedAggregates) &&
         Objects.equals(where, that.where) &&
         Objects.equals(having, that.having) &&
         Objects.equals(dataSource, that.dataSource) &&
         Arrays.equals(columns, that.columns) &&
         Objects.equals(expressions, that.expressions) &&
         Objects.equals(orderDBFields, that.orderDBFields) &&
         Objects.equals(groupDBFields, that.groupDBFields);
   }

   /**
    * Check if the tables at each index have the same quoted name segments.
    */
   private static boolean equalsQuotedSegments(Vector<SelectTable> tables1,
                                               Vector<SelectTable> tables2)
   {
      // different tables are told apart by the table comparison
      if(tables1 == null || tables2 == null || tables1.size() != tables2.size()) {
         return true;
      }

      for(int i = 0; i < tables1.size(); i++) {
         if(!Arrays.equals(tables1.get(i).getQuotedSegments(),
                           tables2.get(i).getQuotedSegments()))
         {
            return false;
         }
      }

      return true;
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

         if(groupQuotes != null) {
            obj.groupQuotes = groupQuotes.clone();
         }

         if(groupQuoteFields != null) {
            obj.groupQuoteFields = groupQuoteFields.clone();
         }

         if(groupUnquoted != null) {
            obj.groupUnquoted = groupUnquoted.clone();
         }

         obj.quotedFields = new HashMap<>(quotedFields);
         obj.quotedAggregates = new HashMap<>(quotedAggregates);
         // a copy, the metadata step of the clone empties its own record
         obj.parsedUnquotedSegments = parsedUnquotedSegments == null ?
            new HashSet<>() : new HashSet<>(parsedUnquotedSegments);

         if(where != null) {
            obj.where = (XFilterNode) where.clone();
         }

         if(having != null) {
            obj.having = (XFilterNode) having.clone();
         }

         obj.hints = new HashMap<>(hints);
         obj.cachedSQLHelper = null;
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
    * A rule of the sql parser.
    */
   private interface ParseRule {
      void parse() throws Exception;
   }

   /**
    * Run the sql parser. The sql it generates while it parses, e.g. the text of a case
    * expression or of a scalar subquery of the select list, is stored as text, so it is
    * generated without the quotes of the quoted table names (SelectTable quotedSegments),
    * as before #77569. Those are added when the sql is generated, with the sql helper of
    * the data source, so readers of the text see the same text as before.
    */
   private static void parseUnquoted(ParseRule rule) throws Exception {
      int depth = UNQUOTED.get();
      UNQUOTED.set(depth + 1);

      try {
         rule.parse();
      }
      finally {
         UNQUOTED.set(depth);
      }
   }

   /**
    * Generate the sql of a query without the quotes of its quoted table names, as before
    * #77569, for text that is saved (see parseUnquoted).
    */
   private static String toUnquotedString(UniformSQL sql) {
      int depth = UNQUOTED.get();
      UNQUOTED.set(depth + 1);

      try {
         sql.clearCachedString();
         return sql.toString();
      }
      finally {
         UNQUOTED.set(depth);
         sql.clearCachedString();
      }
   }

   /**
    * Check if sql is generated on this thread without the quotes of the quoted table
    * names, see parseUnquoted.
    */
   static boolean isUnquoted() {
      return UNQUOTED.get() > 0;
   }

   /**
    * Parse the text of a select list column that is a scalar subquery. The parser keeps such
    * a subquery only as the text of its column, its sql generated while parsing in
    * parentheses (Bug #77620).
    * @param column the column text.
    * @param source the data source of the parsed query, which the parser writes names for
    * (e.g. quoted on postgresql), or <tt>null</tt> if not known.
    * @return the subquery, without a sql string, or <tt>null</tt> if the text isn't one
    * subquery that parses, isn't lossy and is generated to the same text again.
    */
   public static UniformSQL parseSelectListSubquery(String column, JDBCDataSource source) {
      if(column == null || !column.startsWith("(") || !column.endsWith(")")) {
         return null;
      }

      String text = column.substring(1, column.length() - 1);

      if(!text.trim().regionMatches(true, 0, "select", 0, 6)) {
         return null;
      }

      // the names in the text are written for the data source of the parsed query, the
      // subquery itself has no data source when its text is generated. Without the source
      // first, which parses the select list of the subquery as the parser did.
      UniformSQL sub = parseSelectListSubquery(column, text, null);

      if(sub == null && source != null) {
         sub = parseSelectListSubquery(column, text, source);
      }

      return sub;
   }

   private static UniformSQL parseSelectListSubquery(String column, String text,
                                                     JDBCDataSource source)
   {
      UniformSQL sub = new UniformSQL();
      sub.setDataSource(source);

      try {
         sub.parse(text, PARSE_ALL, PARSE_PERIOD);
      }
      catch(Exception ex) {
         LOG.debug("Failed to parse the select list subquery: {}", column, ex);
         return null;
      }

      if(sub.getParseResult() != PARSE_SUCCESS || sub.isLossy()) {
         return null;
      }

      sub.setDataSource(null);
      sub.clearSQLString();

      // the text was generated from the parsed subquery, a text that is generated
      // differently can't be replaced by the generated text of the parsed subquery
      if(!column.equals(getSelectListSubqueryText(sub))) {
         LOG.debug("The select list subquery is generated differently: {}", column);
         return null;
      }

      return sub;
   }

   /**
    * Generate the text of a select list column that is a scalar subquery, as the parser
    * generates it (see parseSelectListSubquery).
    */
   public static String getSelectListSubqueryText(UniformSQL sub) {
      return "(" + toUnquotedString(sub) + ")";
   }

   /**
    * Get the query that this sql is a subquery of in a condition, set while the sql of that
    * query is generated, so a correlated column can be quoted as its table (#77569).
    */
   public UniformSQL getOuterSQL() {
      return outerSQL;
   }

   /**
    * Set the query that this sql is a subquery of in a condition.
    */
   public void setOuterSQL(UniformSQL outerSQL) {
      this.outerSQL = outerSQL;
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
      if(cachedSQLHelper == null) {
         cachedSQLHelper = vpmUser != null ?
            SQLHelper.getSQLHelper(this, vpmUser) : SQLHelper.getSQLHelper(this);
      }

      return cachedSQLHelper;
   }

   public void setVpmUser(Principal user) {
      this.vpmUser = user;
      cachedSQLHelper = null;
   }

   /**
    * Check if parsing captured all information in sql string. A sql may be parsed successfully
    * but may lose some information in the structured view.
    */
   public boolean isLossy() {
      if(parseIt && lossy == null && sqlstring != null) {
         // For databases with map key access syntax (e.g. ClickHouse m['key']), if the SQL
         // contains such patterns, the column definitions will not fully capture the subscript
         // access and the raw SQL cannot be accurately regenerated. Mark as lossy to preserve
         // the original SQL string. (Bug #72243)
         if(dataSource != null && (dataSource.getDatabaseType() == JDBCDataSource.JDBC_CLICKHOUSE ||
            "databricks".equals(SQLHelper.getProductName(dataSource))))
         {
            String quoted = JDBCUtil.quoteMapKeyAccessForParsing(sqlstring);

            if(!quoted.equals(sqlstring)) {
               setLossy(true);
               return true;
            }
         }

         SQLLexer lexer = new SQLLexer(new StringReader(getQuotedSqlString(sqlstring)));
         SQLParser parser = new SQLParser(lexer);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(getDataSource());
         boolean commaGroupsChecked = false;

         try {
            parseUnquoted(() -> parser.direct_select_stmt_n_rows(sql));
            // the join order check fails the parse of sql whose regenerated joins differ,
            // e.g. a saved query parsed before the check was added (Bug #77488). It needs
            // the data source's sql helper, without one the parse result is kept
            if(sql.getDataSource() != null) {
               sql.checkJoinOrders(parser, PARSE_PERIOD);
            }
            else {
               // checked with the base sql helper, which may differ from the data source's
               // (e.g. Oracle without ansi join writes (+) joins), so the result isn't cached
               try {
                  commaGroupsChecked = sql.checkCommaJoinGroups(parser);
               }
               catch(antlr.SemanticException ex) {
                  return true;
               }
            }

            boolean result = (sql.lossy != null && sql.lossy) || isLegacyCycleJoins();

            // a check skipped (or done with the base sql helper) for the missing data source
            // isn't cached, so it runs once a caller sets the data source (e.g. BoundQuery
            // checks lossy before setting it)
            if(sql.getDataSource() == null &&
               (!parser.getJoinOrderChecks().isEmpty() || commaGroupsChecked))
            {
               return result;
            }

            setLossy(result);
         }
         catch(Exception e) {
            setLossy(true);
         }
      }

      return lossy != null && lossy;
   }

   /**
    * Check if the joins of this query close a cycle of the join graph (or join a table to
    * itself) and none of them has a recorded join clause. This is a parsed query saved before
    * the parser recorded the clauses (Bug #77475). Its structure can't tell the join order and
    * the ON/WHERE placement of the text, so regenerating it from the structure can return
    * different rows than the text. It is treated as lossy to keep the sql string instead
    * (Bug #77489).
    */
   private boolean isLegacyCycleJoins() {
      XJoin[] joins = getJoins();

      if(joins == null) {
         return false;
      }

      Map<String, String> parents = new HashMap<>();
      Set<String> pairs = new HashSet<>();

      for(XJoin join : joins) {
         if(join.getJoinClause() != XJoin.UNKNOWN_CLAUSE) {
            return false;
         }
      }

      for(XJoin join : joins) {
         String table1 = join.getTable1(this);
         String table2 = join.getTable2(this);

         if(table1 == null || table2 == null || table1.isEmpty() || table2.isEmpty()) {
            continue;
         }

         if(table1.equals(table2)) {
            return true;
         }

         // joins of the same two tables are one step, not a cycle
         if(!pairs.add(table1.compareTo(table2) < 0 ? table1 + "\0" + table2 :
                       table2 + "\0" + table1))
         {
            continue;
         }

         String root1 = findJoinRoot(parents, table1);
         String root2 = findJoinRoot(parents, table2);

         if(root1.equals(root2)) {
            return true;
         }

         parents.put(root1, root2);
      }

      return false;
   }

   private static String findJoinRoot(Map<String, String> parents, String table) {
      String parent;

      while((parent = parents.get(table)) != null) {
         table = parent;
      }

      return table;
   }

   private String getQuotedSqlString(String sql) {
      if(dataSource != null && (dataSource.getDatabaseType() == JDBCDataSource.JDBC_CLICKHOUSE ||
         "databricks".equals(SQLHelper.getProductName(dataSource))))
      {
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
   // the quoting of each group by field, see getGroupQuote, or null to use quotedFields
   private String[] groupQuotes;
   // the group by fields the quoting was recorded for
   private Object[] groupQuoteFields;
   // the names written unquoted of each group by field, kept with the text they were recorded
   // for (Bug #77643)
   private WrittenUnquoted[] groupUnquoted;
   // the names written unquoted of the sql string of a derived table saved as text (Bug #77643)
   private WrittenUnquoted sqlUnquoted;
   // group by/order by fields written as quoted identifiers -> column segment ("" if bare)
   private HashMap<String, String> quotedFields = new HashMap<>();
   // order by aggregates of a qualified quoted column (sum(t."MixedCase")) -> column segment
   private HashMap<String, String> quotedAggregates = new HashMap<>();
   private XFilterNode where; // root XFilterNode of where clause
   private XFilterNode having; // root XFilterNode of having clause
   private JDBCDataSource dataSource = null;
   private boolean distinctKey = false;
   private boolean allKey = false;
   private boolean grpall = false;
   private UniformSQL psql = null;
   private transient UniformSQL outerSQL; // the query of a condition subquery, see getOuterSQL
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
   private transient volatile SQLHelper cachedSQLHelper;
   private Boolean lossy = null;
   // the column segments stored with quotes that were written unquoted in the sql parsed last,
   // see isParsedUnquotedSegment. Not saved; when it's lost (e.g. serialized before the
   // metadata step) a segment is matched by its exact case first
   private transient Set<String> parsedUnquotedSegments = new HashSet<>();

   private static final Logger LOG = LoggerFactory.getLogger(UniformSQL.class);
   private static final ThreadLocal<Integer> UNQUOTED = ThreadLocal.withInitial(() -> 0);
}
