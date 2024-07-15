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
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * JDBC query stored JDBC query definition. It could be a freeform SQL
 * string, a structured SQL definition, or a stored procedure call.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class JDBCQuery extends XQuery {
   /**
    * Create a JDBC query.
    */
   public JDBCQuery() {
      super(XDataSource.JDBC);

      venabled = true;
   }

   /**
    * Add a variable to this query.
    * @param var variable definition.
    */
   @Override
   public void addVariable(XVariable var) {
      super.addVariable(var);

      if(var instanceof UserVariable && sql != null) {
         sql.setVariable(var.getName(), ((UserVariable) var).getValueNode());
      }
   }

   /**
    * Check if is output type of the query is available. For a non-parse SQL,
    * output type may not be available.
    */
   @Override
   public boolean isOutputTypeAvailable() {
      boolean nonparse =
         (sql instanceof UniformSQL) && !((UniformSQL) sql).isParseSQL();
      if(!nonparse) {
         return true;
      }

      XField[] cinfo = ((UniformSQL) sql).getColumnInfo();
      return cinfo != null && cinfo.length > 0;
   }

   /**
    * Get the output type of the query. The return value is either the
    * root of a subtree of the a type tree, or a one level tree with
    * each child representing a table column.
    */
   @Override
   public XTypeNode getOutputType(Object session, boolean full) {
      XTypeNode output = new XTypeNode("table");
      output.setMinOccurs(0);
      output.setMaxOccurs(XTypeNode.STAR);

      if(sql == null) {
         return output;
      }

      if(sql instanceof UniformSQL && !((UniformSQL) sql).isParseSQL()) {
         XField[] cinfo = ((UniformSQL) sql).getColumnInfo();

         if(cinfo != null && cinfo.length > 0) {
            for(int i = 0; i < cinfo.length; i++) {
               XTypeNode node = XSchema.createPrimitiveType(cinfo[i].getType());

               if(node == null) {
                  continue;
               }

               node.setName((String) cinfo[i].getName());
               output.addChild(node);
            }

            return output;
         }

         output = getOutputTypeForNonParseableSQL(output, null, session);

         // @by larryl, in case the query failed, use the saved xselect
         if(output.getChildCount() > 0) {
            return output;
         }
      }

      XSelection xselect = sql.getSelection();
      boolean cached = sql instanceof UniformSQL ||
                       xselect instanceof SQLSelection;

      if(xselect == null) {
         return output;
      }

      for(int i = 0; i < xselect.getColumnCount(); i++) {
         boolean isExpression = xselect instanceof JDBCSelection ?
            ((JDBCSelection) xselect).getTable(xselect.getColumn(i)) == null : false;
         String name = XUtil.getLastComponent(xselect.getColumn(i), '.', isExpression);
         String name2 = xselect.getColumn(i);
         String alias = xselect.getAlias(i);
         String type = xselect.getType(name2);
         XMetaInfo minfo = xselect.getXMetaInfo(i);
         String name3 = name2;
         boolean exp = false;

         if(sql instanceof UniformSQL) {
            UniformSQL usql = (UniformSQL) sql;
            int index = name2.lastIndexOf(".");
            String table = usql.getTable(name2);

            if(table != null && table.length() > 0 &&
               !name2.startsWith(table) && index > 0)
            {
               name3 = table + "." + name2.substring(index + 1);
            }

            if(full && table != null && usql.isTableColumn(name2) &&
               name2.startsWith(table + "."))
            {
               name = name2.substring((table + ".").length());
            }

            exp = !usql.isTableColumn(name2) && !usql.isTableColumn(name3) &&
                  !XUtil.isQualifiedName(name2);
         }

         XTypeNode node = (type == null || type.length() == 0) ?
            (cached && !exp ? null : new StringType()) :
            XSchema.createPrimitiveType(type);
         node.setXMetaInfo(minfo);

         if(name.equals("*") && (xselect instanceof SQLSelection)) {
            node.setName(name2);
         }
         else {
            if(node == null) {
               continue;
            }

            node.setName(name);
         }

         if(type != null && type.equals(XSchema.STRING)) {
            String fmt = xselect.getFormat(name2);

            if(fmt != null) {
               node.setAttribute("length", Integer.valueOf(fmt));
            }
         }

         if(alias == null) {
            // '*' is expanded in the designer
            if(name2.equals("*") && (xselect instanceof SQLSelection)) {
               SQLSelection ssel = (SQLSelection) xselect;
               alias = ssel.getTableCount() > 0 ? (ssel.getTable(0) + ".*") :
                  name2;
            }
            // function may contain path as parameters
            else if(name2.indexOf('(') < 0 && !name2.endsWith(".*")) {
               alias = name;
            }
            else if(name2.endsWith(".*")) {
               alias = name2;
            }
         }

         if(alias != null) {
            node.setAttribute("alias", alias);
         }

         // avoid duplicate names
         String oname = node.getName();

         for(int idx = 1; output.getChild(node.getName()) != null; idx++) {
            node.setName(oname + idx);
         }

         output.addChild(node);
      }

      return output;
   }

   /**
    * Get the output of an unparseable sql.
    */
   public XTypeNode getOutputTypeForNonParseableSQL(XTypeNode output,
                                                    VariableTable vtable,
                                                    Object session) {
      // try more efficient way first: getting ResultSetMetaData from a
      // prepared statement, which is one of the JDBC2 APIs but might not
      // be supported
      try {
         SQLHelper helper = SQLHelper.getSQLHelper(getDataSource());
         // @by stephenwebster, For Bug #8052
         // As of Version 2.5.31 of the Impala Driver, the meta data cannot be
         // retrieved properly from a prepared statement query with variables.
         if(helper instanceof ImpalaHelper && vtable != null && vtable.size() > 0 ||
            getVariableNames().hasMoreElements())
         {
            LOG.debug(
               "Impala Driver does not support meta-data retrieval from " +
                  "prepared statement queries with variables");
         }
         else {
            XRepository repository = XFactory.getRepository();
            XNode mtype = new XNode("sql");
            mtype.setAttribute("type", "SQL");
            mtype.setAttribute("cache", "false");
            mtype.setAttribute("sql", sql.toString());
            return (XTypeNode) repository.getMetaData(session, getDataSource(),
                                                      mtype, true, null);
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to get meta-data from prepared statement", ex);
      }

      // when failed, try overhead way second: executing sql and getting
      // ResultSetMetaData from the ResultSet, which is one of the JDBC1 APIs
      // and widely supported
      try {
         XRepository repository = XFactory.getRepository();
         VariableTable vtable2 = new VariableTable();

         if(vtable != null) {
            vtable2.setBaseTable(vtable);
         }

         vtable2.put(HINT_MAX_ROWS, "1");
         XTableNode result = (XTableNode) repository.execute(session, this,
            vtable2, ThreadContext.getContextPrincipal(), false, null);

         for(int i = 0; i < result.getColCount(); i++) {
            String name = result.getName(i);
            Class cls = result.getType(i);
            XTypeNode node = XSchema.createPrimitiveType(name, cls);
            output.addChild(node);
         }

         result.close();
      }
      catch(Exception ex) {
         LOG.debug("Failed to get output meta-data", ex);
      }

      return output;
   }

   /**
    * Get the XSelection object.
    */
   @Override
   public XSelection getSelection() {
      if(sql != null) {
         return sql.getSelection();
      }

      return new JDBCSelection();
   }

   /**
    * Get the SQL definition.
    */
   public SQLDefinition getSQLDefinition() {
      return sql;
   }

   /**
    * Set the SQL definition.
    */
   public void setSQLDefinition(SQLDefinition sql) {
      this.sql = sql;

      if(sql instanceof UniformSQL) {
         sql.setDataSource((JDBCDataSource) getDataSource());
      }
   }

   /**
    * Get the SQL as a string. If the SQL is specified as structured SQL
    * object, it is converted to a string conforming to SQL standard.
    */
   public String getSQLAsString() {
      if(sql instanceof UniformSQL) {
         sql.setDataSource((JDBCDataSource) getDataSource());
      }

      return sql == null ? "" : sql.toString();
   }

   /**
    * Set condition to a true condition if one of parameters in it has no value.
    */
   public void validateConditions(VariableTable params) {
      if(!(sql instanceof UniformSQL)) {
         return;
      }

      XUtil.validateConditions(this, (UniformSQL) sql, params);
   }

   public String toString() {
      return getSQLAsString();
   }

   class VariableNamesEnum extends Vector implements Enumeration {
      @Override
      public boolean hasMoreElements() {
         return current < size();
      }

      @Override
      public Object nextElement() {
         Object obj = get(current);

         current++;
         return obj;
      }

      @Override
      public boolean remove(Object o) {
         for(int i = 0; i < size(); i++) {
            if(get(i).equals(o)) {
               remove(i);
               return true;
            }
         }

         return false;
      }

      int current = 0;
   }

   /**
    * Find variables in the query.
    */
   @Override
   protected void findVariables(Map varmap) {
      if(sql instanceof ProcedureSQL) {
         UserVariable[] vars = ((ProcedureSQL) sql).getVariables();

         for(int i = 0; i < vars.length; i++) {
            XVariable ovar = (XVariable) varmap.get(vars[i].getName());

            if(!(ovar instanceof UserVariable)) {
               ovar = vars[i];
            }
            else {
               UserVariable ouvar = (UserVariable) ovar;

               // if the type is changed in query builder, don't use the
               // value otherwise the type is lost
               // @by vincentx, 2004-09-16, fix bug1095128085024
               // we should allow the user to update the variable even if
               // the original value is not null.
               if(ouvar.getTypeNode().isCompatible(vars[i].getTypeNode())) {
                  XValueNode val = (XValueNode)
                     ouvar.getTypeNode().newInstance();
                  val.setValue(vars[i].getValueNode().getValue());

                  if(vars[i].getValueNode().getValue() == null) {
                     val.setVariable(vars[i].getValueNode().getVariable());
                  }

                  ouvar.setValueNode(val);
               }
            }

            addVariable(ovar);
         }
      }
      else {
         findVariables(getSQLAsString());
      }
   }

   public boolean equals(Object obj) {
      if(obj instanceof JDBCQuery) {
         XDataSource obj_datasource = ((JDBCQuery) obj).getDataSource();

         if(!(this.getDataSource()).equals(obj_datasource)) {
            return false;
         }

         String obj_sqlString = ((JDBCQuery) obj).getSQLAsString();

         if(obj_sqlString == null) {
            obj_sqlString = "";
         }

         obj_sqlString = Tool.remove(obj_sqlString, ' ');

         String sqlString = this.getSQLAsString();

         if(sqlString == null) {
            sqlString = "";
         }

         sqlString = Tool.remove(sqlString, ' ');

         if(!sqlString.equals(obj_sqlString)) {
            return false;
         }

         return true;
      }

      return super.equals(obj);
   }

   /**
    * Check if to add vpm condition is enabled.
    * @return <tt>true</tt> if enabled, <tt>false</tt> otherwise.
    */
   public boolean isVPMEnabled() {
      return venabled;
   }

   /**
    * Set the vpm condition enabled flag.
    * @param enabled <tt>true</tt> if enabled, <tt>false</tt> otherwise.
    */
   public void setVPMEnabled(boolean enabled) {
      this.venabled = enabled;
   }

   /**
    * Create a clone of this object.
    */
   @Override
   public JDBCQuery clone() {
      JDBCQuery copy = (JDBCQuery) super.clone();

      if(sql != null) {
         copy.sql = (SQLDefinition) sql.clone();
      }

      return copy;
   }

   /**
    * Parse the XML element that contains information on this
    * data source.
    */
   @Override
   public void parseXML(Element root) throws Exception {
      NodeList nlist;

      sql = null;
      Enumeration xmlTags = defmap.keys();

      while(xmlTags.hasMoreElements()) {
         String xmlTag = (String) xmlTags.nextElement();

         nlist = Tool.getChildNodesByTagName(root, xmlTag);

         if(nlist != null && nlist.getLength() > 0) {
            sql = (SQLDefinition) Class.forName(
               (String) defmap.get(xmlTag)).newInstance();
            sql.parseXML((Element) nlist.item(0));
            break;
         }
      }

      if(sql instanceof FreeformSQL || sql instanceof StructuredSQL) {
         sql = new UniformSQL(sql);
      }

      super.parseXML(root);
      this.fixUserVariables(this.sql);

      if(!(sql instanceof UniformSQL) || !((UniformSQL) sql).isParseSQL()) {
         autoAliasExpression();
      }
   }

   /**
    * Add alias for expression automatically.JDBCFieldsPane#updateAlias has processed.
    * this for old version case.
    */
   private void autoAliasExpression() {
      if(sql == null || sql.getSelection() == null) {
         return;
      }

      XSelection selection = sql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         if(!Tool.isEmptyString(selection.getAlias(i))) {
            continue;
         }

         String column = selection.getColumn(i);

         if(sql instanceof UniformSQL) {
            SQLHelper helper = SQLHelper.getSQLHelper((UniformSQL) sql);

            if(((UniformSQL) sql).isTableColumn(column)) {
               column = helper.quotePath(column,true);
            }
         }

         String[] splitName = Tool.splitWithQuote(column, ".", '\\');
         String col = null;

         if(splitName.length > 0) {
            col = splitName[splitName.length - 1];
         }

         if(col != null && !XUtil.isQualifiedName(column)) {
            int n = 1;
            String alias = "exp_" + (n++);

            while(selection.contains(alias) || selection.getAliasColumn(alias) != null) {
               alias = "exp_" + (n++);
            }

            selection.setAlias(i, alias);
         }
      }
   }

   private void fixUserVariables(SQLDefinition sqlDefinition) {
      if(sqlDefinition instanceof UniformSQL) {
         fixXFilterVariable(((UniformSQL) sqlDefinition).getWhere());
         fixXFilterVariable(((UniformSQL) sqlDefinition).getHaving());
      }
   }

   private void fixXFilterVariable(XFilterNode xFilterNode) {
      if(xFilterNode == null) {
         return;
      }

      if(xFilterNode instanceof XBinaryCondition ) {
         fixConditionVariable((XBinaryCondition) xFilterNode);
      }
      else if(xFilterNode instanceof XSet) {
         XSet conditions = (XSet) xFilterNode;
         int count = conditions.getChildCount();

         for(int i = 0; i < count; i++) {
            XNode condition = conditions.getChild(i);

            if(condition instanceof XBinaryCondition) {
               fixConditionVariable((XBinaryCondition) condition);
            }
            else if(condition instanceof XSet) {
               fixXFilterVariable((XSet) condition);
            }
         }
      }
   }

   /**
    * Make the variable can set multiple values when it is used in the condition that`s op is IN,
    * and update variable type.
    * @param condition
    */
   private void fixConditionVariable(XBinaryCondition condition) {
      Object value = condition.getExpression2().getValue();

      if(value instanceof String) {
         String varName = getVarName((String) value);

         if(varName == null) {
            return;
         }

         XVariable var = getVariable(varName);
         String type = condition.getExpression1().getType();

         if(var instanceof UserVariable) {
            UserVariable userVariable = (UserVariable) var;

            if("IN".equals(condition.getOp())) {
               userVariable.setMultipleSelection(true);
            }

            if(XExpression.FIELD.equals(type)) {
               Object value1 = condition.getExpression1().getValue();
               UniformSQL uniformSQL = (UniformSQL) this.sql;
               XField field = uniformSQL.getFieldByPath((String) value1);

               if(field != null) {
                  userVariable.setTypeNode(XSchema.createPrimitiveType(field.getType()));
               }
            }
         }
      }
      else if(value instanceof UniformSQL) {
         fixUserVariables((UniformSQL) value);
      }
   }

   /**
    * Generate the XML segment to represent this data source.
    */
   private void writeXML0(PrintWriter writer, boolean full) {
      writer.println("<query_jdbc>");

      if(sql != null) {
         if(sql instanceof UniformSQL && full) {
            ((UniformSQL) sql).writeFullXML(writer);
         }
         else {
            sql.writeXML(writer);
         }
      }

      super.writeXML(writer);

      writer.println("</query_jdbc>");
   }

   /**
    * Generate the XML segment to represent this data source.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writeXML0(writer, false);
   }

   /**
    * Generate the XML segment to represent this data source.
    */
   public void writeFullXML(PrintWriter writer) {
      writeXML0(writer, true);
   }

   /**
    * Find variables defined in the SQL string.
    */
   private void findVariables(String sqlstr) {
      try {
         // @by larryl, optimization
         char[] arr = sqlstr.toCharArray();

         for(int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            char nch = (i < arr.length - 1) ? arr[i + 1] : ' ';

            // escape
            if(ch == '\\') {
               i++;
               continue;
            }
            else if(ch == '$' && nch == '(') {
               int idx = sqlstr.indexOf(')', i + 2);

               if(idx > 0) {
                  String name = sqlstr.substring(i + 2, idx).trim();

                  // embed flag, stripped from name
                  if(name.startsWith("@")) {
                     name = name.substring(1);
                  }

                  UserVariable var = sql.getVariable(name);
                  XValueNode vNode = new XValueNode();
                  vNode.setValue("1=1");

                  // if value is null (not defined in proc parameter pane),
                  // use the variable definition
                  if(var != null && var.getValueNode() != null &&
                     var.getValueNode().getValue() != null)
                  {
                     // if the value is not defined here, get it from
                     // the variable definition from parent

                     // @by jamshedd if the name starts with a? then
                     // it is a vpm control point and it should be set as a
                     // non promptable variable, set its value as an expression
                     // which is logically true
                     if(name.startsWith("?")) {
                        var.setValueNode(vNode);
                        var.setPrompt(false);
                     }

                     super.addVariable(var);
                  }
                  else if(getVariable(name) == null) {
                     UserVariable uVar = new UserVariable(name);

                     // @by jamshedd if the name starts with a? then
                     // it is a vpm control point and it should be set as a
                     // non promptable variable, set its value as an expression
                     // which is logically true
                     if(name.startsWith("?")) {
                        uVar.setValueNode(vNode);
                        uVar.setPrompt(false);
                     }

                     super.addVariable(uVar);
                  }

                  i = idx;
                  continue;
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to parse the variable names from SQL: " + sqlstr, ex);
      }

      this.fixUserVariables(this.sql);
   }

   private String getVarName(String varStr) {
      if(varStr == null) {
         return null;
      }

      int start = varStr.indexOf("$(");
      int end = varStr.indexOf(")");

      if(start < 0 || end < 0 || end <= start) {
         return null;
      }

      return varStr.substring(start + 2, end);
   }

//   /**
//    * Get the node value type for the SQLSelection.
//    */
//   private XTypeNode getNodeType(String path, Object session) {
//      int idx = path.indexOf(".");
//
//      if(idx < 0) {
//         return new StringType();
//      }
//
//      String qname = getName();
//      String tname = XUtil.getTablePart(path);
//      String cname = XUtil.getColumnPart(path);
//
//      if(tname.length() <= 0 || cname.length() <= 0) {
//         return new StringType();
//      }
//
//      try {
//         XRepository repository = XFactory.getRepository();
//         XQuery xquery = repository.getQuery(qname);
//         XNode mtype = new XNode(tname);
//         XNode cols = repository.getMetaData(session, xquery.getDataSource(),
//                                             mtype, true, null);
//         cols = cols.getChild("Result");
//
//         if(cols != null) {
//            XNode child = cols.getChild(cname);
//
//            if(child instanceof XTypeNode) {
//               return (XTypeNode) child;
//            }
//         }
//      }
//      catch(Exception e) {
//         return new StringType();
//      }
//
//      return new StringType();
//   }

   /**
    * Get data model this query belongs to.
    */
   private XDataModel getDataModel() {
      try {
         return XFactory.getRepository().getDataModel(
            getDataSource().getName());
      }
      catch(Exception e) {
         return null;
      }
   }

   /**
    * Remove the useless tables.
    */
   public void removeTable(VariableTable vars) {
      if(!(sql instanceof UniformSQL)) {
         return;
      }

      XUtil.removeTable(this, (UniformSQL) sql, vars);
   }

   /**
    * Apply variables to the contained sql.
    */
   public void applyVariableTable(VariableTable vars) {
      if(sql instanceof UniformSQL) {
         UniformSQL usql = (UniformSQL) sql;

         if(Boolean.TRUE.equals(usql.getHint(UniformSQL.HINT_STATIC_SQL, true))) {
            usql.applyVariableTable(vars);
         }
      }
   }

   /**
    * Set whether this query is created by end user. All queries created on worksheet,
    * as well as generated from logical model are treated as user queries.
    */
   public void setUserQuery(boolean flag) {
      this.userQuery = flag;
   }

   /**
    * Check if this query is a user query.
    */
   public boolean isUserQuery() {
      return userQuery;
   }

   private static Hashtable defmap = new Hashtable();

   static {
      defmap.put("sql", "inetsoft.uql.jdbc.FreeformSQL");
      defmap.put("structured_sql", "inetsoft.uql.jdbc.StructuredSQL");
      defmap.put("procedure_sql", "inetsoft.uql.jdbc.ProcedureSQL");
      defmap.put(UniformSQL.XML_TAG, "inetsoft.uql.jdbc.UniformSQL");
   }

   private SQLDefinition sql;
   private boolean venabled;
   // this is set when query is generated at runtime so it's not persistent
   private boolean userQuery;

   private static final Logger LOG =
      LoggerFactory.getLogger(JDBCQuery.class);
}
