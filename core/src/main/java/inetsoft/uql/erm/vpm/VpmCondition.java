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
package inetsoft.uql.erm.vpm;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.script.StringArray;
import inetsoft.uql.script.VpmScope;
import inetsoft.uql.util.ColumnIterator;
import inetsoft.uql.util.SQLQuoteScanner;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VpmCondition defines conditions attached to a physical table to filter
 * out data.
 *
 * @author InetSoft Technology
 * @version 8.0
 */
public class VpmCondition extends VpmObject {
   public static final int TABLE = 0;
   public static final int PHYSICMODEL = 1;

   /**
    * Constructor.
    */
   public VpmCondition() {
      super();
   }

   /**
    * Constructor.
    * @param name the specified name for the vpm condition.
    */
   public VpmCondition(String name) {
      this();

      setName(name);
   }

   /**
    * Used by getLeafNodes().
    */
   private static void getLeafNodes(XNode root, List<XNode> nodeList) {
      if(root.getChildCount() == 0) {
         nodeList.add(root);
      }
      else {
         for(int i = 0; i < root.getChildCount(); i++) {
            getLeafNodes(root.getChild(i), nodeList);
         }
      }
   }

   /**
    * Get all leaf nodes in the tree.
    */
   private static List<XNode> getLeafNodes(XNode root) {
      List<XNode> nodeList = new ArrayList<>();
      getLeafNodes(root, nodeList);
      return nodeList;
   }

   /**
    * Get the table or physical model to attach the vpm condition.
    */
   public String getTable() {
      return table;
   }

   /**
    * Set the table or physical model to attach the vpm condition.
    */
   public void setTable(String table) {
      this.table = table;
   }

   /**
    * Return the vpm condtion based on table or physical model.
    */
   public int getType() {
      return type;
   }

   /**
    * Set the vpm condtion based on table or physical model.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get the condition to be applied to filter out data.
    * @return the conditions.
    */
   public XFilterNode getCondition() {
      return conds;
   }

   /**
    * Set the condition to be applied to filter out data.
    * @param conds the specified condition.
    */
   public void setCondition(XFilterNode conds) {
      this.conds = conds;
   }

   /**
    * Check if the virtual private model should be applied.
    * @param partition the specified partition where vpm condition is attached.
    * @param tables the specified query tables.
    * @param taliases the specified query table aliases.
    * @param columns the specified query columns.
    * @param source the specified data source.
    * @param vars the specified variable table.
    * @param user the specified principal.
    * @param checkVariable true to enforce variables used in condition exist.
    * @return the condition to filter out data.
    */
   public String evaluate(String partition, String[] tables, String[] taliases,
                          String[] columns, XDataSource source, VariableTable vars,
                          Principal user, boolean checkVariable)
      throws Exception
   {
      XPartition xpart = null;

      if(partition != null) {
         XDataModel model = source == null ? null :
            XRepository.getRepository().getDataModel(source.getFullName());
         xpart = model == null ? null : model.getPartition(partition, user);
         xpart = xpart == null ? null : xpart.applyAutoAliases();
      }

      int[] targets = getTargetTables(tables, taliases, xpart);

      if(targets.length == 0) {
         return evaluate(partition, xpart, tables, taliases, columns, source, vars, user,
                         checkVariable, -1);
      }

      // Bug #77612, a table read more than once (t a join t b) needs the condition on each
      // occurrence, or the rows of the other occurrences are not filtered
      Set<String> results = new LinkedHashSet<>();

      for(int target : targets) {
         String result = evaluate(partition, xpart, tables, taliases, columns, source, vars,
                                  user, checkVariable, target);

         if(result != null && !result.isEmpty()) {
            results.add(result);
         }
      }

      if(results.size() <= 1) {
         return results.isEmpty() ? null : results.iterator().next();
      }

      StringBuilder buf = new StringBuilder();

      for(String result : results) {
         if(buf.length() > 0) {
            buf.append(" and ");
         }

         buf.append('(').append(result).append(')');
      }

      return buf.toString();
   }

   /**
    * Get the query tables the condition is evaluated for one at a time. They are the query
    * tables that are the same as the condition table (or a table of the partition), when more
    * than one query table is, at any depth (dbo.t and db1.dbo.t) or alias.
    * @return the indexes of the tables, or an empty array if at most one table is the same.
    */
   private int[] getTargetTables(String[] tables, String[] taliases, XPartition xpart) {
      if(tables == null || taliases == null || tables.length != taliases.length ||
         tables.length < 2)
      {
         return new int[0];
      }

      Set<String> ctables = new LinkedHashSet<>();

      if(type != PHYSICMODEL) {
         if(table != null) {
            ctables.add(table);
         }
      }
      // the condition table is the partition name
      else if(xpart != null) {
         Enumeration<XPartition.PartitionTable> ptables = xpart.getTables();

         while(ptables.hasMoreElements()) {
            String palias = ptables.nextElement().getName();
            Object pname = xpart.getRunTimeTable(palias, true);
            ctables.add(palias);

            if(pname instanceof String) {
               ctables.add((String) pname);
            }
         }
      }
      else {
         ctables.addAll(Arrays.asList(tables));
      }

      Set<Integer> targets = new TreeSet<>();

      for(String ctable : ctables) {
         List<Integer> matches = new ArrayList<>();

         for(int i = 0; i < tables.length; i++) {
            if(VirtualPrivateModel.getTableMatch(ctable, tables[i]) > 0) {
               matches.add(i);
            }
         }

         if(matches.size() > 1) {
            targets.addAll(matches);
         }
      }

      return targets.stream().mapToInt(Integer::intValue).toArray();
   }

   /**
    * Evaluate the condition with the fields of the target table mapped to its alias.
    * @param target the index of the query table the fields of its table are mapped to, or -1
    *               to map a field to the closest table, the first one if more than one.
    */
   private String evaluate(String partition, XPartition xpart, String[] tables,
                           String[] taliases, String[] columns, XDataSource source,
                           VariableTable vars, Principal user, boolean checkVariable,
                           int target)
      throws Exception
   {
      // create a uniform sql to maintain table information,
      // then sql helper will be able to quote fields properly
      UniformSQL sql = new UniformSQL();

      if(source instanceof JDBCDataSource) {
         sql.setDataSource((JDBCDataSource) source);
      }

      if(tables != null && taliases != null && tables.length == taliases.length) {
         for(int i = 0; i < tables.length; i++) {
            String talias = taliases[i] == null ? tables[i] : taliases[i];
            sql.addTable(talias, tables[i]);
         }
      }

      if(partition != null) {
         if(xpart != null) {
            Enumeration ptables = xpart.getTables();

            // add partition tables to quote condition fields properly
            while(ptables.hasMoreElements()) {
               XPartition.PartitionTable ptable =
                  (XPartition.PartitionTable) ptables.nextElement();
               String palias = ptable.getName();
               Object pname = xpart.getRunTimeTable(palias, true);
               pname = pname == null ? palias : pname;
               boolean contained = sql.getTableName(palias) != null;

               if(!contained) {
                  sql.addTable(palias, pname);
               }
            }
         }
      }

      SQLHelper helper = SQLHelper.getSQLHelper(source, user);
      helper.setUniformSql(sql);

      if(conds != null) {
         XFilterNode conds = (XFilterNode) this.conds.clone();
         List nodes = getLeafNodes(conds);

         for(int j = 0; j < nodes.size(); j++) {
            XFilterNode cnode = (XFilterNode) nodes.get(j);

            if(cnode instanceof XUnaryCondition) {
               XExpression exp = ((XUnaryCondition) cnode).getExpression1();
               normalizeExpression(exp, tables, taliases, target, sql, helper, vars, checkVariable);
            }
            else if(cnode instanceof XBinaryCondition) {
               XBinaryCondition bcond = (XBinaryCondition) cnode;
               // first process right expression, right expression support IN,
               // so will never be changed, left expression not support IN,
               // need to convert to OR, so will add new expressions to
               // condition, if we first process right expression, the new
               // added expression(s)' right expression will be ok
               XExpression exp = bcond.getExpression2();
               normalizeExpression(exp, tables, taliases, target, sql, helper, vars, checkVariable);
               exp = bcond.getExpression1();
               normalizeExpression(exp, tables, taliases, target, sql, helper, vars, checkVariable);
               Object value = exp.getValue();

               //fix bug#30563, for the original table, alias conditions should not be added
               if(exp.getType().equals(XExpression.FIELD) && value instanceof String) {
                  String field = (String) value;
                  String tpart = XUtil.getTablePart(field);

                  // fix Bug #31094, don't clear the conditions of the tables which
                  // are not directly used.
                  if(Arrays.asList(tables).contains(tpart) &&
                     !Arrays.asList(taliases).contains(tpart))
                  {
                     conds = null;
                  }
               }
            }
            else if(cnode instanceof XTrinaryCondition) {
               XExpression exp = cnode.getExpression1();
               normalizeExpression(exp, tables, taliases, target, sql, helper, vars, checkVariable);
               exp = ((XTrinaryCondition) cnode).getExpression2();
               normalizeExpression(exp, tables, taliases, target, sql, helper, vars, checkVariable);
               exp = ((XTrinaryCondition) cnode).getExpression3();
               normalizeExpression(exp, tables, taliases, target, sql, helper, vars, checkVariable);
            }
         }

         sql.combineWhereByAnd(conds);
      }

      String script = getScript();
      boolean noscript = (script == null || script.trim().length() == 0 ||
         XUtil.isAllComment(script));

      // no script defined? by default we apply the condition defined on GUI
      if(noscript) {
         XUtil.validateConditions(null, sql, vars, true, true);
      }

      XFilterNode conds = sql.getWhere();
      helper = SQLHelper.getSQLHelper(source, user);
      helper.setUniformSql(sql);
      helper.setVPMCondition(true);
      String condition = conds == null ? null : helper.generateConditions(conds);
      helper.setVPMCondition(false);

      if(noscript) {
         return condition;
      }

      VpmScope scope = new VpmScope();
      scope.setVariableTable(vars);
      scope.setUser(user);

      StringArray tsarray = new StringArray("talias", taliases);
      StringArray carray = new StringArray("column", columns);
      scope.setTables(tables);
      scope.putMember("taliases", tsarray);
      scope.putMember("columns", carray);
      scope.putMember("condition", condition);
      scope.putMember("partition", partition);

      if(WSExecution.getAssetQuerySandbox() != null) {
         scope.putMember("creatingMV", WSExecution.getAssetQuerySandbox().isCreatingMV());
      }
      else {
         scope.putMember("creatingMV", false);
      }

      Object result;

      // If trigger script fails, add a bogus condition so that a empty
      // result set is returned.
      if(!checkVariable) {
         result = VpmScope.execute(script, scope);
      }
      else {
         try {
            result = VpmScope.execute(script, scope);
         }
         catch(Throwable ex) {
            LOG.error("Failed to execute trigger script of conditions.", ex);
            return "1 = 2";
         }
      }

      // Bug #75669: A VPM trigger script activates its condition by referencing (or
      // assigning) the `condition` variable, relying on that value becoming the script's
      // completion value. Under Rhino a trailing statement that produced no value (e.g. a
      // non-matching if in the last loop iteration) left the completion value intact;
      // GraalJS follows current ECMAScript rules where such a statement yields undefined
      // and clobbers the loop's completion value, so the reference no longer surfaces as
      // the result. When the script used `condition` but the completion value came back
      // null, fall back to the (possibly reassigned) condition value.
      if(result == null && scope.isConditionUsed()) {
         result = scope.getMember("condition");
      }

      if(result == null) {
         return null;
      }

      if(!(result instanceof String)) {
         throw new Exception(
            "The script result of vpm condition should be a string value!");
      }

      return updateVPMTable((String) result, tables, taliases, target);
   }

   /**
    * Replace the table qualifiers in a condition with the table aliases. A qualifier names a
    * query table when its segments, quoted or not, are the same as the table's. A name inside
    * another identifier, a string literal or a comment is not a qualifier.
    * @param target the index of the table a qualifier naming that table at any depth is
    *               replaced with, or -1 for none.
    */
   private String updateVPMTable(String condition, String[] tables, String[] taliases,
                                 int target)
   {
      // Bug #77580, the query tables are stored quoted by the sql helper ("sa"."t") while the
      // script may qualify the columns with the unquoted names (sa.t.STATE), and a plain text
      // replace of "t." also hit other identifiers (xt.STATE)
      String[][] tsegments = new String[tables.length][];

      for(int i = 0; i < tables.length; i++) {
         tsegments[i] = VirtualPrivateModel.splitTableName(tables[i]);
      }

      StringBuilder result = new StringBuilder();
      int len = condition.length();
      int i = 0;

      while(i < len) {
         char c = condition.charAt(i);
         char next = i + 1 < len ? condition.charAt(i + 1) : 0;
         int end;

         if(c == '\'') {
            end = VirtualPrivateModel.readQuoted(condition, i, c, new StringBuilder());
         }
         else if(c == '-' && next == '-') {
            end = condition.indexOf('\n', i);
            end = end < 0 ? len : end;
         }
         else if(c == '/' && next == '*') {
            end = condition.indexOf("*/", i + 2);
            end = end < 0 ? len : end + 2;
         }
         else if(Character.isLetter(c) || c == '_' ||
            VirtualPrivateModel.getCloseQuote(c) != 0)
         {
            end = updateQualifier(condition, i, tsegments, tables, taliases, target, result);
            i = end;
            continue;
         }
         else if(Character.isDigit(c)) {
            end = getIdentifierEnd(condition, i);
         }
         else {
            end = i + 1;
         }

         result.append(condition, i, end);
         i = end;
      }

      return result.toString();
   }

   /**
    * Append the dotted name starting at start to the result, with the longest leading
    * segments that name a query table replaced by the table alias.
    * @param target the index of the table to use if the leading segments name that table at
    *               any depth, or -1 for none.
    * @return the index after the name.
    */
   private static int updateQualifier(String condition, int start, String[][] tsegments,
                                      String[] tables, String[] taliases, int target,
                                      StringBuilder result)
   {
      List<String> segments = new ArrayList<>();
      List<Integer> ends = new ArrayList<>();
      int end = start;

      while(true) {
         StringBuilder segment = new StringBuilder();
         int segmentStart = end;
         char close = VirtualPrivateModel.getCloseQuote(condition.charAt(end));

         if(close != 0) {
            end = VirtualPrivateModel.readQuoted(condition, end, close, segment);
         }
         else {
            end = getIdentifierEnd(condition, end);
            segment.append(condition, segmentStart, end);
         }

         segments.add(segment.toString().toLowerCase(Locale.ROOT));
         ends.add(end);

         // continue at a dot followed by another segment
         if(end + 1 < condition.length() && condition.charAt(end) == '.') {
            char c = condition.charAt(end + 1);

            if(Character.isLetterOrDigit(c) || c == '_' || c == '$' ||
               VirtualPrivateModel.getCloseQuote(c) != 0)
            {
               end++;
               continue;
            }
         }

         break;
      }

      int match = -1;

      for(int i = 0; i < tsegments.length; i++) {
         int count = tsegments[i].length;

         if(count < segments.size() && (match < 0 || count > tsegments[match].length) &&
            segments.subList(0, count).equals(Arrays.asList(tsegments[i])))
         {
            match = i;
         }
      }

      int count = match < 0 ? 0 : tsegments[match].length;

      // Bug #77612, a qualifier that is the same as the target table, at any depth, is the
      // target, so each occurrence of a table read more than once is filtered
      if(target >= 0) {
         for(int i = segments.size() - 1; i > 0; i--) {
            String[] qualifier = segments.subList(0, i).toArray(new String[0]);

            if(VirtualPrivateModel.getTableMatch(qualifier, tsegments[target]) > 0) {
               match = target;
               count = i;
               break;
            }
         }
      }

      String alias = match < 0 ? null : taliases[match];

      // an unaliased table keeps its name
      if(alias == null || alias.isEmpty() || alias.equals(tables[match])) {
         result.append(condition, start, end);
      }
      else {
         result.append(alias).append(condition, ends.get(count - 1), end);
      }

      return end;
   }

   /**
    * Get the index after the unquoted identifier starting at start.
    */
   private static int getIdentifierEnd(String text, int start) {
      int end = start;

      while(end < text.length()) {
         char c = text.charAt(end);

         if(!Character.isLetterOrDigit(c) && c != '_' && c != '$' && c != '#') {
            break;
         }

         end++;
      }

      return end;
   }

   /**
    * Normalize an expression by replacing table with table alias.
    * @param tables the specified query tables.
    * @param taliases the specified query table aliases.
    * @param checkVariable true to throw an exception if a variable used in expression doesn't
    *                      exist in vars.
    */
   private void normalizeExpression(XExpression exp, String[] tables,
                                    String[] taliases, int target, UniformSQL sql,
                                    SQLHelper helper, VariableTable vars,
                                    boolean checkVariable)
   {
      Object value = exp.getValue();

      if(exp.getType().equals(XExpression.FIELD)) {
         value = getColumn(value, tables, taliases, target, sql);

         if(value != null) {
            exp.setValue(value, exp.getType());
         }

         return;
      }
      else if(exp.getType().equals(XExpression.EXPRESSION) && value instanceof String) {
         String str = (String) value;

         if(str.startsWith("$(") && str.endsWith(")")) {
            String vname = str.substring(2, str.length() - 1);

            if(checkVariable && !VariableTable.isContextVariable(vname) &&
               !VariableTable.isBuiltinVariable(vname) && !vars.contains(vname))
            {
               throw new RuntimeException("Variable in VPM condition missing: " + vname);
            }
         }

         SQLParser parser = null;
         String[] fields = null;
         String dbType = helper.getSQLHelperType();

         try {
            SQLLexer lexer = new SQLLexer(new java.io.StringReader(str));
            parser = new SQLParser(lexer);
            parser.setPreferQuote(false);
            parser.setTime(5000);
            parser.value_exp();

            // Bug #77697, the parser stops without an error at a token it doesn't know (div,
            // ->>, over, a mysql # comment), and the columns after it were not found, so the
            // expression is iterated as when the parser fails
            if(parser.LA(1) == antlr.Token.EOF_TYPE) {
               fields = parser.getColumns();
            }
         }
         // a timeout, or a construct the parser refuses in a subquery
         catch(Exception ex) {
            fields = null;
         }

         if(fields == null) {
            final ArrayList<String> columns = new ArrayList<>();
            ColumnIterator iterator = new ColumnIterator((String) value, dbType);
            ColumnIterator.ColumnListener listener = new
               ColumnIterator.ColumnListener()
            {
               @Override
               public void nextElement(String value) {
                  columns.add(value);
               }
            };

            iterator.addColumnListener(listener);
            iterator.iterate();
            fields = columns.toArray(new String[0]);
         }

         value = replaceColumnTableName((String) value, fields, tables,
                                        taliases, target, sql, helper,
                                        ColumnIterator.getRules(dbType));
         exp.setValue(value, exp.getType());
      }
   }

   /**
    * Replace the table of each column of an expression by the table alias.
    * @param rules the quoting and comment rules of the database (ColumnIterator.getRules()).
    */
   private String replaceColumnTableName(String exp, String[] columns,
                                         String[] tables, String[] taliases, int target,
                                         UniformSQL sql, SQLHelper helper, int rules)
   {
      if(columns == null || columns.length <= 0) {
         return exp;
      }

      boolean bracket = ColumnIterator.isBracketQuote(rules);
      columns = columns.clone();

      // Bug #77697, a bracket quoted name ([T].[A]) is the name, as the parser reports it
      if(bracket) {
         for(int i = 0; i < columns.length; i++) {
            columns[i] = BRACKET_NAME.matcher(columns[i]).replaceAll("$1");
         }
      }

      String[] ncolumns = new String[columns.length];

      for(int i = 0; i < columns.length; i++) {
         ncolumns[i] = getColumn(columns[i], tables, taliases, target, sql);
      }

      for(int i = 0; i < columns.length; i++) {
         String table = XUtil.getTablePart(columns[i]);
         String column = XUtil.getColumnPart(columns[i]);
         String ncolumn = ncolumns[i];

         if(ncolumn != null) {
            // the table part of the column before the helper quotes it (o. of o.A)
            String rawPrefix = ncolumn.endsWith("." + column) ?
               ncolumn.substring(0, ncolumn.length() - column.length()) : null;
            ncolumn = helper.buildFieldExpression(ncolumn, false);
            // the table part as the helper quotes it ("Order Details". of "Order Details".A)
            String prefix = rawPrefix == null ? null : getTablePrefix(ncolumn, column, helper);
            // a bracket quoted column ([Customer's]) stays quoted, the helper may not quote it
            String bcolumn = !bracket || rawPrefix == null ? null :
               (prefix != null ? prefix : rawPrefix) + "[" + column + "]";
            // the replacement of a column written without quotes, or null to use ncolumn
            String ucolumn = null;

            if(prefix != null) {
               // Bug #77697, a quoted name with a doubled quote ("A""B") is not quoted by the
               // helper, as it contains a quote, so it is quoted as it was written
               if(column.contains("\"\"") && ncolumn.equals(prefix + column)) {
                  ncolumn = prefix + "\"" + column + "\"";
               }
               // Bug #77697, a name with $ or # (amount$, emp#no) is a valid name without
               // quotes, and a quoted name is case-sensitive (oracle), so a column written
               // without quotes isn't quoted only for its $ or #
               else if(UNQUOTED_NAME.matcher(column).matches() &&
                  !ncolumn.equals(prefix + column))
               {
                  String plain = column.replace('$', '_').replace('#', '_');
                  String nplain = helper.buildFieldExpression(rawPrefix + plain, false);

                  if(nplain.equals(prefix + plain)) {
                     ucolumn = prefix + column;
                  }
               }
            }

            // String creg = ".*['\"]?" + table + "['\"]?\\.['\"]?" + column + "(['\"]?)(\\W+.)*";
            // Bug #77663, a ' is not an identifier quote (the optional ' took the closing quote
            // of a literal), and a name ending with the table name (XT.A) is not the table
            // (a unicode name, e.g. a chinese table name, is a name too)
            // Bug #77697, a name starting with the column name (T.AB, T.A$X, T.A.C) is not the
            // column, and a name may be quoted in brackets
            String rreg = "(?<![\\w$.])" + getNamePattern(table, bracket) + "\\." +
               getNamePattern(column, bracket) + "(?![\\w$])(?!\\.)";
            exp = replaceOutsideLiterals(
               exp, Pattern.compile(rreg, Pattern.UNICODE_CHARACTER_CLASS), ncolumn, bcolumn,
               ucolumn, rules);
         }
      }

      return exp;
   }

   /**
    * Get the table part of a column built by the helper, ending with the dot.
    * @param field the column built by the helper (SQLHelper.buildFieldExpression()).
    * @param column the column part, without quotes.
    * @return the table part, or null if the field doesn't end with the column, quoted by
    * the helper or not.
    */
   private static String getTablePrefix(String field, String column, SQLHelper helper) {
      String quote = helper.getQuote();

      for(String end : new String[] { column, quote + column + quote }) {
         int len = field.length() - end.length();

         if(len > 0 && field.endsWith(end) && field.charAt(len - 1) == '.') {
            return field.substring(0, len);
         }
      }

      return null;
   }

   /**
    * Get the regular expression of a name that may be quoted.
    */
   private static String getNamePattern(String name, boolean bracket) {
      String quoted = Pattern.quote(String.valueOf(name));
      String pattern = "\"?" + quoted + "\"?";
      return bracket ? "(?:\\[" + quoted + "\\]|" + pattern + ")" : pattern;
   }

   /**
    * Replace the matches of a regular expression outside the string literals, comments and
    * variables of an expression. A quoted name ("T"."A", [T].[A]) is not a literal, so a
    * match of whole quoted names is replaced, but not a match inside quoted text, which is a
    * string in some databases (a mysql "it\"s T.A").
    * @param breplacement the replacement of a match ending with a bracket quoted name, or
    *                     null to use the replacement.
    * @param ureplacement the replacement of a match ending with a name without quotes, or
    *                     null to use the replacement.
    * @param rules the quoting and comment rules of the database (ColumnIterator.getRules()).
    */
   private static String replaceOutsideLiterals(String exp, Pattern pattern,
                                                String replacement, String breplacement,
                                                String ureplacement, int rules)
   {
      int len = exp.length();
      // the characters of the literals, comments and variables
      boolean[] hidden = new boolean[len];
      // the start of the quoted name containing each character, or -1
      int[] owner = new int[len];
      // the end of the quoted name starting at each index
      int[] nameEnd = new int[len];
      // the quotes found not closed. A quote that isn't closed doesn't open a literal or
      // name, as before literals were skipped
      String unclosed = "";
      int i = 0;

      Arrays.fill(owner, -1);

      while(i < len) {
         int end = ColumnIterator.skipComment(exp, i, rules);
         boolean name = false;

         if(end < 0) {
            end = ColumnIterator.skipVariable(exp, i);
         }

         if(end < 0) {
            char c = exp.charAt(i);
            char close = ColumnIterator.getCloseQuote(exp, i, rules);

            if(close != 0 && unclosed.indexOf(c) < 0) {
               end = SQLQuoteScanner.skipQuoted(
                  exp, i, close, ColumnIterator.isBackslashEscape(c, rules));

               // a [ that is not closed ends at a line break or another [
               if(end < 0 && c != '[') {
                  unclosed += c;
               }

               name = c != '\'';
            }
         }

         if(end < 0) {
            i++;
            continue;
         }

         for(int j = i; j < end; j++) {
            if(name) {
               owner[j] = i;
            }
            else {
               hidden[j] = true;
            }
         }

         if(name) {
            nameEnd[i] = end;
         }

         i = end;
      }

      StringBuilder result = new StringBuilder();
      Matcher matcher = pattern.matcher(exp);
      int start = 0;
      i = 0;

      while(i <= len && matcher.find(i)) {
         int mstart = matcher.start();
         int mend = matcher.end();
         boolean replace = mend > mstart &&
            (owner[mstart] < 0 || owner[mstart] == mstart) &&
            (owner[mend - 1] < 0 || nameEnd[owner[mend - 1]] == mend);

         for(int j = mstart; replace && j < mend; j++) {
            replace = !hidden[j];
         }

         if(replace) {
            char last = exp.charAt(mend - 1);
            result.append(exp, start, mstart).append(
               breplacement != null && last == ']' ? breplacement :
               ureplacement != null && last != '"' && last != ']' && last != '`' ?
                  ureplacement : replacement);
            start = i = mend;
         }
         else {
            i = mstart + 1;
         }
      }

      result.append(exp, start, len);
      return result.toString();
   }

   private String getColumn(Object value, String[] tables,
                            String[] taliases, int target, UniformSQL sql)
   {
      // Object value = exp.getValue();

      if(!(value instanceof String)) {
         return null;
      }

      String field = (String) value;
      String tpart = XUtil.getTablePart(field);

      if(tpart == null) {
         return null;
      }

      String alias = tpart;
      int find_step = -1;
      int best = 0;

      // Bug #77580, a table qualified to a different depth (dbo.t) is the same as the field's
      // table (db1.dbo.t), so prefer the closest match, e.g. db1.dbo.t in the same query
      for(int i = 0; i < tables.length; i++) {
         int match = VirtualPrivateModel.getTableMatch(tpart, tables[i]);

         if(match > best) {
            best = match;
            alias = taliases[i];
            alias = alias == null || alias.length() == 0 ? tpart : alias;
            find_step = 0;
         }
      }

      // Bug #77612, the field's table at any depth is the target, so each occurrence of a
      // table read more than once is filtered
      if(find_step == 0 && target >= 0 &&
         VirtualPrivateModel.getTableMatch(tpart, tables[target]) > 0)
      {
         alias = taliases[target];
         alias = alias == null || alias.length() == 0 ? tpart : alias;
      }

      if(find_step == -1) {
         for(int i = 0; i < tables.length; i++) {
            if(isTableField(field, tables[i])) {
               tpart = tables[i];
               alias = taliases[i];
               alias = alias == null || alias.length() == 0 ? tpart : alias;
               find_step = 1;
            }
         }

         // Bug #77612, the same table read more than once
         if(find_step == 1 && target >= 0 && isTableField(field, tables[target])) {
            tpart = tables[target];
            alias = taliases[target];
            alias = alias == null || alias.length() == 0 ? tpart : alias;
         }
      }

      String nfield = null;

      if(!alias.equals(tpart) || find_step == 1) {
         String cpart = find_step != 1 ?
            XUtil.getColumnPart(field) : field.substring(tpart.length() + 1);

         if(find_step == 1) {
            SQLHelper helper = SQLHelper.getSQLHelper(sql);
            cpart = XUtil.quoteAlias(cpart, helper);
         }

         nfield = alias + "." + cpart;
         // exp.setValue(field, exp.getType());
      }

      if(sql.getTableIndex(alias) == -1) {
         sql.addTable(alias);
      }

      return nfield;
   }

   /**
    * Check if the field is the table name and a column, not the table name alone.
    */
   private static boolean isTableField(String field, String table) {
      return table != null && field.length() > table.length() + 1 &&
         field.charAt(table.length()) == '.' &&
         field.toLowerCase(Locale.ROOT).startsWith(table.toLowerCase(Locale.ROOT));
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      VpmCondition vconds = (VpmCondition) super.clone();

      if(conds != null) {
         vconds.conds = (XFilterNode) conds.clone();
      }

      return vconds;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(table != null) {
         writer.print("<table><![CDATA[" + table + "]]></table>");
      }

      if(conds != null) {
         writer.println("<conditions>");
         conds.writeXML(writer);
         writer.println("</conditions>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      table = Tool.getChildValueByTagName(elem, "table");

      Element cnode = Tool.getChildNodeByTagName(elem, "conditions");

      if(cnode != null) {
         cnode = Tool.getFirstChildNode(cnode);
         conds = XFilterNode.createConditionNode(cnode);
      }
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" type=\"" + type + "\" ");
   }

   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);
      String str = Tool.getAttribute(elem, "type");
      type = str == null ? 0 : Integer.parseInt(str);
   }

   private String table;
   private int type;
   private XFilterNode conds;
   private static final Logger LOG = LoggerFactory.getLogger(VpmCondition.class);
   // a bracket quoted name in a column, [A] in T.[A]
   private static final Pattern BRACKET_NAME = Pattern.compile("\\[([^\\[\\]]*)\\]");
   // a name with $ or # that needs no quotes (amount$, emp#no)
   private static final Pattern UNQUOTED_NAME =
      Pattern.compile("(?=.*[$#])[\\p{L}_][\\w$#]*", Pattern.UNICODE_CHARACTER_CLASS);
}
