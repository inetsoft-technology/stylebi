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

import inetsoft.uql.XNode;
import inetsoft.uql.XTableNode;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.util.XMLUtil;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.DatabaseMetaData;
import java.util.*;

/**
 * This is a stored procedure call definition. It stored information on
 * stored procedure parameters.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ProcedureSQL implements SQLDefinition, Cloneable {
   /**
    * Input parameter.
    */
   public static final int IN = DatabaseMetaData.procedureColumnIn;
   /**
    * Output parameter.
    */
   public static final int OUT = DatabaseMetaData.procedureColumnOut;
   /**
    * Input/Output parameter.
    */
   public static final int INOUT = DatabaseMetaData.procedureColumnInOut;
   /**
    * Result from a function call.
    */
   public static final int RETURN = DatabaseMetaData.procedureColumnReturn;
   /**
    * Result from a resultset.
    */
   public static final int RESULT = DatabaseMetaData.procedureColumnResult;

   /**
    * Create a procedure sql from a xnode meta data.
    * @param meta the meta data to be set.
    */
   public void setMetaData(XNode meta) {
      String pname = SQLTypes.getSQLTypes(null).getQualifiedName(meta, null);

      // informix does not support fully qualified proc name
      setName((pname.indexOf(':') < 0) ? pname : meta.getName());
      setParameters(meta.getChild("Parameter"), false);
      XNode plist = meta.getChild("Result");

      if(plist != null) {
         for(int i = 0; i < plist.getChildCount(); i++) {
            XTypeNode type = (XTypeNode) plist.getChild(i);
            Integer obj = (Integer) type.getAttribute("length");
            int length = (obj == null) ? 0 : obj.intValue();
            addColumn(type.getName(), type.getType(), length);
         }
      }

      plist = meta.getChild("Return");

      if(plist != null && plist.getChildCount() > 0) {
         retType = (Integer) plist.getChild(0).getAttribute("sqltype");
         retTypeName = (String) plist.getChild(0).getAttribute("typename");
      }
      else {
         retType = null;
         retTypeName = null;
      }
   }

   /**
    * Set the column info.
    */
   public void setColumnInfo(XTableNode table) {
      columns.removeAllElements();

      for(int i = 0; i < table.getColCount(); i++) {
         int len = 0;
         
         if(table instanceof JDBCTableNode) {
            len = ((JDBCTableNode) table).getLength(i);
         }
         
         addColumn(table.getName(i), table.getType(i), len);
      }

      table.close();
   }

   /**
    * Get the procedure name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the procedure name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the procedure path.
    */
   public String getPath() {
      return path;
   }

   /**
    * Set the procedure path.
    */
   public void setPath(String path) {
      this.path = path;
   }

   /**
    * Add a parameter to the procedure parameter list.
    * @param name parameter name.
    * @param inout parameter type, in, out, inout.
    * @param sqltype sql type defined in java.sql.Types.
    */
   public void addParameterDefinition(String name, int inout, int sqltype,
                                      String typename) 
   {
      Parameter param = new Parameter(name);

      param.inout = inout;
      param.type = SQLTypes.getSQLTypes(null).convertToXType(sqltype);
      param.typename = typename;
      param.sqltype = sqltype;

      addParameterDefinition(param);
   }

   /**
    * Get the input parameters type.
    */
   public XTypeNode getInputType() {
      return inputType;
   }

   /**
    * Set the input parameters type.
    */
   public void setInputType(XTypeNode type) {
      this.inputType = type;
   }

   /**
    * Get the input parameter values.
    */
   public XNode getInputValue() {
      return inputValue;
   }

   /**
    * Set the input parameter values.
    */
   public void setInputValue(XNode value) {
      inputValue = value;
   }

   /**
    * Get the number of columns in the procedure.
    */
   public int getColumnCount() {
      return columns.size();
   }

   /**
    * Get a column name.
    */
   public String getColumnName(int idx) {
      return ((ColDef) columns.elementAt(idx)).name;
   }

   /**
    * Get a column type. The type is a primitive type string defined in
    * XSchema.
    */
   public String getColumnType(int idx) {
      return ((ColDef) columns.elementAt(idx)).type;
   }

   public int getColumnLength(int idx) {
      return ((ColDef) columns.elementAt(idx)).length;
   }

   /**
    * Get the procedure return type.
    * @return return type as defined in java.sql.Types.
    */
   public Integer getReturnType() {
      return retType;
   }

   /**
    * Get the return type name.
    */
   public String getReturnTypeName() {
      return retTypeName;
   }

   /**
    * Reset the sql definition.
    */
   public void clear() {
      params.removeAllElements();
      columns.removeAllElements();
      inputType.removeAllChildren();
      inputValue.removeAllChildren();
      xselect.clear();
   }

   /**
    * Get the selection column list.
    */
   @Override
   public XSelection getSelection() {
      return xselect;
   }

   /**
    * Set the selection list.
    */
   public void setSelection(XSelection xselect) {
      this.xselect = xselect;
   }

   /**
    * Check whether a parameter is defined as a variable.
    */
   public boolean isVariable(String name) {
      if(inputValue == null) {
         return false;
      }

      for(int i = 0; i < inputValue.getChildCount(); i++) {
         XValueNode var = (XValueNode) inputValue.getChild(i);
         String variable = var.getVariable();

         if(name.equals(variable)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get a variable value for a name. If the variable is not defined,
    * it returns null.
    * @param name variable name.
    * @return variable definition.
    */
   @Override
   public UserVariable getVariable(String name) {
      if(inputValue == null) {
         return null;
      }

      XValueNode value = (XValueNode) inputValue.getNode("this." + name);
      XTypeNode type = (XTypeNode) inputType.getNode("this." + name);

      if(value != null && type != null) {
         String variable = value.getVariable();
         UserVariable var = new UserVariable(variable == null ?
            name :
            variable);

         var.setPrompt(variable != null);
         var.setValueNode((XValueNode) value);
         var.setTypeNode((XTypeNode) type);
         return var;
      }

      return null;
   }

   /**
    * Set the value of a variable.
    */
   @Override
   public void setVariable(String name, XValueNode value) {
      if(inputValue == null) {
         return;
      }

      XValueNode vnode = (XValueNode) inputValue.getNode("this." + name);

      if(vnode != null) {
         vnode.setValue(value.getValue());
      }
   }

   /**
    * Get all variables (proc parameters) in this stored procedure.
    */
   public UserVariable[] getVariables() {
      if(inputValue == null) {
         return new UserVariable[0];
      }

      List<UserVariable> varList = new ArrayList<>();

      for(int i = 0; i < inputValue.getChildCount(); i++) {
         UserVariable var = getVariable(inputValue.getChild(i).getName());

         if(var != null) {
            varList.add(var);
         }
      }

      return varList.toArray(new UserVariable[0]);
   }

   /**
    * Select columns from the table.
    */
   @Override
   public XNode select(XNode root) throws Exception {
      return (xselect != null && xselect.getColumnCount() > 0) ?
         xselect.select(root) :
         root;
   }

   /**
    * Get the parameter definition count.
    */
   public int getParameterCount() {
      return params.size();
   }

   /**
    * Get specified parameter definition.
    */
   public Parameter getParameter(int idx) {
      return (Parameter) params.elementAt(idx);
   }

   /**
    * Get specified parameter definition.
    * @param name parameter name.
    */
   public Parameter getParameter(String name) {
      for(int i = 0; i < params.size(); i++) {
         if(((Parameter) params.get(i)).name.equals(name)) {
            return (Parameter) params.get(i);
         }
      }

      return null;
   }

   /**
    * Get the embeded input parameter value.
    */
   public Object getEmbededInParameterValue(String name) {
      Parameter param = getParameter(name);

      if(param != null && (param.inout == IN || param.inout == INOUT) &&
         !"REF CURSOR".equals(param.typename)) {
         XValueNode value = inputValue == null ? null :
            (XValueNode) inputValue.getNode("this." + param.name);

         if(value != null) {
            return value.getValue();
         }
      }

      return null;
   }

   /**
    * Parse the XML element that contains information on this sql.
    */
   @Override
   public void parseXML(Element node) throws Exception {
      NodeList nlist = Tool.getChildNodesByTagName(node, "name");

      if(nlist != null && nlist.getLength() > 0) {
         name = Tool.getValue(nlist.item(0));
      }

      nlist = Tool.getChildNodesByTagName(node, "path");

      if(nlist != null && nlist.getLength() > 0) {
        path = Tool.getValue(nlist.item(0));
      }

      nlist = Tool.getChildNodesByTagName(node, "return");
      if(nlist != null && nlist.getLength() > 0) {
         retType = Integer.valueOf(Tool.getValue(nlist.item(0)));
      }

      nlist = Tool.getChildNodesByTagName(node, "return_name");
      if(nlist != null && nlist.getLength() > 0) {
         retTypeName = Tool.getValue(nlist.item(0));
      }

      nlist = Tool.getChildNodesByTagName(node, "select");
      if(nlist != null && nlist.getLength() > 0) {
         xselect = XSelection.parse(Tool.getValue(nlist.item(0)));
      }

      nlist = node.getElementsByTagName("parameter_definition");
      if(nlist != null) {
         params = new Vector();
         for(int i = 0; i < nlist.getLength(); i++) {
            Parameter param = new Parameter();

            param.parseXML((Element) nlist.item(i));
            addParameterDefinition(param);
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "result_column");
      if(nlist != null) {
         for(int i = 0; i < nlist.getLength(); i++) {
            Element elem = (Element) nlist.item(i);
            String val = Tool.getAttribute(elem, "length");

            addColumn(Tool.getAttribute(elem, "name"),
               Tool.getAttribute(elem, "type"),
               (val == null) ? 0 : Integer.parseInt(val));
         }
      }

      nlist = Tool.getChildNodesByTagName(node, "inputParameter");
      if(nlist.getLength() > 0) {
         inputValue = XMLUtil.createTree(nlist.item(0), inputType);
      }
   }

   /**
    * Generate the XML segment to represent this data source.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<procedure_sql>");

      if(name != null) {
         writer.println("<name><![CDATA[" + name + "]]></name>");
      }

      // fix bug1322681122452, if the procedure create in oracle packages,
      // we need to store the path in order to get the node by the path
      if(name != null && !name.equals(path)) {
        writer.println("<path><![CDATA[" + path + "]]></path>");
      }

      if(retType != null) {
         writer.println("<return>" + retType + "</return>");
      }

      if(retTypeName != null) {
         writer.println("<return_name>" + retTypeName + "</return_name>");
      }

      if(xselect != null) {
         writer.println("<select><![CDATA[" + xselect.toString() +
            "]]></select>");
      }

      // column list
      for(int i = 0; i < columns.size(); i++) {
         ColDef col = (ColDef) columns.elementAt(i);

         writer.println("<result_column name=\"" + Tool.escape(col.name) +
            "\" type=\"" + Tool.escape(col.type) + "\" length=\"" + col.length +
            "\"/>");
      }

      writer.println("<parameters>");
      for(int i = 0; i < params.size(); i++) {
         ((Parameter) params.elementAt(i)).writeXML(writer);
      }

      writer.println("</parameters>");

      if(inputValue != null) {
         inputValue.writeXML(writer);
      }

      writer.println("</procedure_sql>");
   }

   /**
    * Add a parameter definition.
    */
   private void addParameterDefinition(Parameter param) {
      params.addElement(param);

      if((param.inout == IN || param.inout == INOUT) &&
         (param.typename == null || !param.typename.equals("REF CURSOR"))) {
         XTypeNode type = XSchema.createPrimitiveType(param.type);

         type.setName(param.name);
         inputType.addChild(type);
      }
   }

   /**
    * Add a column to the resultset of the procedure.
    */
   private void addColumn(String name, Class clstype, int length) {
      addColumn(name, Tool.getDataType(clstype), length);
   }

   /**
    * Add a column to the resultset.
    */
   private void addColumn(String name, String type, int length) {
      // make sure name and type are not null
      if(name == null) {
         name = "column" + columns.size();
      }

      if(type == null) {
         type = XSchema.STRING;
      }

      columns.addElement(new ColDef(name, type, length));
   }

   /**
    * Get the data source.
    */
   @Override
   public JDBCDataSource getDataSource() {
      return ds;
   }

   /**
    * Set the data source.
    */
   @Override
   public void setDataSource(JDBCDataSource ds) {
      this.ds = ds;
   }

   /**
    * Convert to a SQL statement.
    */
   @Override
   public String getSQLString() {
      return toString();
   }

   /**
    * Convert to a SQL statement.
    */
   public String toString() {
      SQLHelper helper = SQLHelper.getSQLHelper(ds);
      String cmd = (retType != null) ? "{? = " : "{ ";
      cmd += "call " + XUtil.quoteName(name, helper);

      for(int i = 0; i < params.size(); i++) {
         if(i > 0) {
            cmd += ",";
         }
         else {
            cmd += "(";
         }

         Parameter param = (Parameter) params.elementAt(i);

         if((param.inout == IN || param.inout == INOUT) &&
            !param.typename.equals("REF CURSOR")) {
            UserVariable var = getVariable(param.name);

            if(var != null) {
               cmd += "$(" + var.getName() + ")";
            }
            else {
               cmd += "$(" + param.name + ")";
            }
         }
         else {
            cmd += "?";
         }
      }

      return cmd + ((params.size() > 0) ? ")" : "") + "}";
   }

   /**
    * Set the parameters.
    * @param plist the parameter list node.
    * @param refresh true if refresh parameters.
    */
   public void setParameters(XNode plist, boolean refresh) {
      XNode oldType = null;
      XNode oldValue = null;

      if(refresh) {
         params = new Vector();
         oldType = inputType;
         oldValue = inputValue;
         inputType = new XTypeNode("inputParameter");
      }

      if(plist != null) {
         for(int i = 0; i < plist.getChildCount(); i++) {
            XTypeNode type = (XTypeNode) plist.getChild(i);
            Integer obj = (Integer) type.getAttribute("sqltype");
            Parameter param = new Parameter(type.getName());

            param.inout = ((Integer) type.getAttribute("type")).intValue();
            param.type = type.getType();
            param.typename = (String) type.getAttribute("typename");
            param.sqltype = (obj != null) ? obj.intValue() : -1;
            addParameterDefinition(param);
         }
      }

      if(refresh) {
         // set the old value if exist
         inputValue = inputType.newInstance();

         for(int i = 0; i < oldType.getChildCount(); i++) {
            XNode otype = oldType.getChild(i);

            for(int j = 0; j < inputType.getChildCount(); j++) {
               if(otype.equals(inputType.getChild(j))) {
                  String name = otype.getName();
                  int idx = inputValue.getChildIndex(inputValue.getChild(name));

                  if(oldValue == null) {
                     continue;
                  }

                  inputValue.setChild(idx, oldValue.getChild(name));
               }
            }
         }
      }
   }

   /**
    * Stored procedure parameter definition.
    */
   public static class Parameter implements Serializable {
      public Parameter() {
      }

      public Parameter(String name) {
         this.name = name;
      }

      public void parseXML(Element node) throws Exception {
         name = Tool.getAttribute(node, "name");
         type = Tool.getAttribute(node, "type");
         typename = Tool.getAttribute(node, "typename");

         String prop = Tool.getAttribute(node, "inout");

         if(prop != null) {
            inout = Integer.parseInt(prop);
         }

         prop = Tool.getAttribute(node, "sqltype");
         if(prop != null) {
            sqltype = Integer.parseInt(prop);
         }
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }
         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         Parameter parameter = (Parameter) o;

         if(sqltype != parameter.sqltype) {
            return false;
         }
         if(inout != parameter.inout) {
            return false;
         }
         if(name != null ? !name.equals(parameter.name) :
            parameter.name != null)
         {
            return false;
         }
         if(type != null ? !type.equals(parameter.type) :
            parameter.type != null)
         {
            return false;
         }
         return !(typename != null ? !typename.equals(parameter.typename) :
            parameter.typename != null);

      }

      public void writeXML(PrintWriter writer) {
         writer.println("<parameter_definition name=\"" + Tool.escape(name) +
            "\" type=\"" + type + "\" inout=\"" + inout + "\" typename=\"" +
            typename + "\" sqltype=\"" + sqltype + "\"/>");
      }

      public String name;
      public String type;
      public String typename;
      public int sqltype;
      public int inout = IN;
   }

   // column definition
   static class ColDef implements Serializable {
      public ColDef(String name, String type, int length) {
         this.name = name;
         this.type = type;
         this.length = length;
      }

      public String name;
      public String type;
      public int length;
   }

   @Override
   public Object clone() {
      ProcedureSQL sql = new ProcedureSQL();

      sql.name = name;
      sql.path = path;
      sql.params = (Vector) params.clone();
      sql.columns = (Vector) columns.clone();
      sql.xselect = (XSelection) xselect.clone();
      sql.retType = retType;
      sql.retTypeName = retTypeName;
      sql.inputType = inputType;
      sql.inputValue = inputValue == null ? null : (XNode) inputValue.clone();

      return sql;
   }

   String name = null; // procedure name
   String path = null; // procedure path
   Vector params = new Vector(); // parameter list, vector of Parameter
   Vector columns = new Vector(); // columns, ColDef
   XSelection xselect = new XSelection(); // selection list
   Integer retType = null; // return type, sql type
   String retTypeName = null; // return type name, 'REF CURSOR' for cursor
   XTypeNode inputType = new XTypeNode("inputParameter");
   XNode inputValue = null;
   JDBCDataSource ds;

   private static final Logger LOG =
      LoggerFactory.getLogger(ProcedureSQL.class);
}
