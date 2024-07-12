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
package inetsoft.uql.erm.vpm;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.script.StringArray;
import inetsoft.uql.script.VpmScope;
import inetsoft.uql.util.ColumnIterator;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;

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
      if(user != null && XPrincipal.SYSTEM.equals(user.getName())) {
         return null;
      }

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

      XPartition xpart = null;

      if(partition != null) {
         XDataModel model = source == null ? null :
            XFactory.getRepository().getDataModel(source.getFullName());
         xpart = model == null ? null : model.getPartition(partition, user);

         if(xpart != null) {
            xpart = xpart.applyAutoAliases();
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
               normalizeExpression(exp, tables, taliases, sql, helper, vars, checkVariable);
            }
            else if(cnode instanceof XBinaryCondition) {
               XBinaryCondition bcond = (XBinaryCondition) cnode;
               // first process right expression, right expression support IN,
               // so will never be changed, left expression not support IN,
               // need to convert to OR, so will add new expressions to
               // condition, if we first process right expression, the new
               // added expression(s)' right expression will be ok
               XExpression exp = bcond.getExpression2();
               normalizeExpression(exp, tables, taliases, sql, helper, vars, checkVariable);
               exp = bcond.getExpression1();
               normalizeExpression(exp, tables, taliases, sql, helper, vars, checkVariable);
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
               normalizeExpression(exp, tables, taliases, sql, helper, vars, checkVariable);
               exp = ((XTrinaryCondition) cnode).getExpression2();
               normalizeExpression(exp, tables, taliases, sql, helper, vars, checkVariable);
               exp = ((XTrinaryCondition) cnode).getExpression3();
               normalizeExpression(exp, tables, taliases, sql, helper, vars, checkVariable);
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

      StringArray tarray = new StringArray("table", tables);
      StringArray tsarray = new StringArray("talias", taliases);
      StringArray carray = new StringArray("column", columns);
      scope.put("tables", scope, tarray);
      scope.put("taliases", scope, tsarray);
      scope.put("columns", scope, carray);
      scope.put("condition", scope, condition);
      scope.put("partition", scope, partition);

      if(WSExecution.getAssetQuerySandbox() != null) {
         scope.put("creatingMV", scope, WSExecution.getAssetQuerySandbox().isCreatingMV());
      }
      else {
         scope.put("creatingMV", scope, false);
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

      if(result == null) {
         return null;
      }

      if(!(result instanceof String)) {
         throw new Exception(
            "The script result of vpm condition should be a string value!");
      }

      return updateVPMTable((String) result, tables, taliases);
   }

   /**
    * replace the table in condition with taliases
    */
   private String updateVPMTable(String condition, String[] tables, String[] taliases) {
      for(int i = 0; i < tables.length; i++) {
         if(condition.indexOf(tables[i]) != -1) {
            condition = Tool.replaceAll(condition, tables[i] + ".", taliases[i] + ".");
         }
      }

      return condition;
   }

   /**
    * Normalize an expression by replacing table with table alias.
    * @param tables the specified query tables.
    * @param taliases the specified query table aliases.
    * @param checkVariable true to throw an exception if a variable used in expression doesn't
    *                      exist in vars.
    */
   private void normalizeExpression(XExpression exp, String[] tables,
                                    String[] taliases, UniformSQL sql,
                                    SQLHelper helper, VariableTable vars,
                                    boolean checkVariable)
   {
      Object value = exp.getValue();

      if(exp.getType().equals(XExpression.FIELD)) {
         value = getColumn(value, tables, taliases, sql);

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

         try {
            SQLLexer lexer = new SQLLexer(new java.io.StringReader(str));
            parser = new SQLParser(lexer);
            parser.setPreferQuote(false);
            parser.setTime(5000);
            parser.value_exp();
            fields = parser.getColumns();
         }
         catch(ParserStoppedException ex) {
            final ArrayList<String> columns = new ArrayList<>();
            ColumnIterator iterator = new ColumnIterator((String) value);
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
         catch(Exception ex) {
            // ignore it
         }

         value = replaceColumnTableName((String) value, fields, tables,
                                        taliases, sql, helper);
         exp.setValue(value, exp.getType());
      }
   }

   private String replaceColumnTableName(String exp, String[] columns,
                                         String[] tables, String[] taliases,
                                         UniformSQL sql, SQLHelper helper)
   {
      if(columns == null || columns.length <= 0) {
         return exp;
      }

      String[] ncolumns = new String[columns.length];

      for(int i = 0; i < columns.length; i++) {
         ncolumns[i] = getColumn(columns[i], tables, taliases, sql);
      }

      for(int i = 0; i < columns.length; i++) {
         String table = XUtil.getTablePart(columns[i]);
         String column = XUtil.getColumnPart(columns[i]);
         String ncolumn = ncolumns[i];

         if(ncolumn != null) {
            ncolumn = helper.buildFieldExpression(ncolumn, false);
            // String creg = ".*['\"]?" + table + "['\"]?\\.['\"]?" + column + "(['\"]?)(\\W+.)*";
            String rreg = "['\"]?" + table + "['\"]?\\.['\"]?" + column + "['\"]?";
            exp = exp.replaceAll(rreg, ncolumn);
         }
      }

      return exp;
   }

   private String getColumn(Object value, String[] tables,
                            String[] taliases, UniformSQL sql)
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

      for(int i = 0; i < tables.length; i++) {
         if(VirtualPrivateModel.isSameTable(tpart, tables[i])) {
            alias = taliases[i];
            alias = alias == null || alias.length() == 0 ? tpart : alias;
            find_step = 0;
            break;
         }
      }

      if(find_step == -1) {
         for(int i = 0; i < tables.length; i++) {
            if(tables[i] != null && field.toLowerCase().startsWith(tables[i].toLowerCase())) {
               tpart = tables[i];
               alias = taliases[i];
               alias = alias == null || alias.length() == 0 ? tpart : alias;
               find_step = 1;
            }
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
}
