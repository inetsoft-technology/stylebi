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
package inetsoft.uql.asset;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.DependencyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * SQL Bound table assembly, bound to a data source.
 *
 * @version 12.1
 * @author InetSoft Technology Corp
 */
public class SQLBoundTableAssembly extends BoundTableAssembly {
   /**
    * Constructor.
    */
   public SQLBoundTableAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public SQLBoundTableAssembly(Worksheet ws, String name) {
      super(ws, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new SQLBoundTableAssemblyInfo();
   }

   /**
    * Get the sql bound table assembly info.
    * @return the sql bound table assembly info of the sql bound table assembly.
    */
   protected SQLBoundTableAssemblyInfo getSQLBoundTableInfo() {
      return (SQLBoundTableAssemblyInfo) getTableInfo();
   }

   /**
    * Get whether the sql string has been edited
    */
   public boolean isSQLEdited() {
      return this.sqlEdited;
   }

   /**
    * Set sqlEdited
    * @param sqlEdited determines whether the sql string has been edited
    * using the unstructured interface
    */
   public void setSQLEdited(boolean sqlEdited) {
      this.sqlEdited = sqlEdited;
   }

   /**
    * Get whether the query is advanced editing.
    */
   public boolean isAdvancedEditing() {
      return advancedEditing;
   }

   /**
    * Set whether the query is advanced editing.
    */
   public void setAdvancedEditing(boolean advancedEditing) {
      this.advancedEditing = advancedEditing;
   }

   /**
    * Return an array of all variables used in this table assembly
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();
      UserVariable[] vars = null;
      mergeVariables(list, super.getAllVariables());
      JDBCQuery query = getJDBCQuery();

      try {
         AssetRepository rep = AssetUtil.getAssetRepository(false);
         XDataService service = XFactory.getDataService();
         UserVariable[] params = service.getQueryParameters(rep.getSession(),
            query, true);

         if(params != null) {
            mergeVariables(list, params);
         }

         findVarTypes(list);
      }
      catch (Exception ex) {
         LOG.debug("Failed to retrieve all variables", ex);
      }

      fixUserVariables(query.getSQLDefinition(), list);
      vars = new UserVariable[list.size()];
      list.toArray(vars);

      return vars;
   }

   private JDBCQuery getJDBCQuery() {
      return getSQLBoundTableInfo().getQuery();
   }

   private void fixUserVariables(SQLDefinition sqlDefinition, List<UserVariable> list) {
      if(sqlDefinition instanceof UniformSQL) {
         UniformSQL uniformSQL = (UniformSQL) sqlDefinition;

         try {
            fixUniformSQLInfo(uniformSQL);
         }
         catch(Exception ex) {
            LOG.error(ex.getMessage(), ex);
            return;
         }

         fixXFilterVariable(((UniformSQL) sqlDefinition).getWhere(), list);
         fixXFilterVariable(((UniformSQL) sqlDefinition).getHaving(), list);
      }
   }

   private void fixUniformSQLInfo(UniformSQL uniformSQL) throws Exception {
      if(uniformSQL.getTableCount() > 0) {
         XField[] fields = uniformSQL.getFieldList();

         if(fields.length == 0) {
            DataSourceRegistry registry = DataSourceRegistry.getRegistry();
            JDBCDataSource ds = (JDBCDataSource) registry.getDataSource(getSourceInfo().getSource());
            Principal principal = ThreadContext.getContextPrincipal();
            String userName = principal == null ? null : principal.getName();
            JDBCUtil.fixUniformSQLInfo(uniformSQL, XFactory.getRepository(), userName, ds);
         }
      }
   }

   private void fixXFilterVariable(XFilterNode xFilterNode, List<UserVariable> list) {
      if(xFilterNode == null) {
         return;
      }

      if(xFilterNode instanceof XBinaryCondition) {
         fixConditionVariable((XBinaryCondition) xFilterNode, list);
      }
      else if(xFilterNode instanceof XSet) {
         XSet conditions = (XSet) xFilterNode;
         int count = conditions.getChildCount();

         for(int i = 0; i < count; i++) {
            XNode condition = conditions.getChild(i);

            if(condition instanceof XBinaryCondition) {
               fixConditionVariable((XBinaryCondition) condition, list);
            }
            else if(condition instanceof XSet) {
               fixXFilterVariable((XSet) condition, list);
            }
         }
      }
   }

   /**
    * Make the variable can set multiple values when it is used in the condition that`s op is IN,
    * and update variable type.
    * @param condition
    */
   private void fixConditionVariable(XBinaryCondition condition, List<UserVariable> list) {
      if(condition == null || list == null || list.isEmpty()) {
         return;
      }

      Object value = condition.getExpression2().getValue();

      if(value instanceof String) {
         String varName = getVarName((String) value);

         if(varName == null) {
            return;
         }

         UserVariable var = getVariable(varName, list);
         String type = condition.getExpression1().getType();

         UserVariable userVariable = (UserVariable) var;

         if("IN".equals(condition.getOp())) {
            userVariable.setMultipleSelection(true);
         }

         if(XExpression.FIELD.equals(type)) {
            Object value1 = condition.getExpression1().getValue();
            UniformSQL uniformSQL = (UniformSQL) getJDBCQuery().getSQLDefinition();
            XField field = uniformSQL.getFieldByPath((String) value1);

            if(field != null) {
               XTypeNode typeNode = XSchema.createPrimitiveType(field.getType());
               userVariable.setTypeNode(typeNode);
               fixVariableAssembly(varName, typeNode);
            }
         }
      }
      else if(value instanceof UniformSQL) {
         fixUserVariables((UniformSQL) value, list);
      }
   }

   private void fixVariableAssembly(String varName, XTypeNode typeNode) {
      Assembly assembly = getWorksheet().getAssembly(varName);

      if(assembly instanceof VariableAssembly) {
         VariableAssembly vassembly = (VariableAssembly) assembly;
         AssetVariable variable = vassembly.getVariable();

         if(variable != null) {
            variable.setTypeNode(typeNode);
         }
      }
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

   private UserVariable getVariable(String name, List<UserVariable> list) {
      if(list == null || list.size() == 0) {
         return null;
      }

      for(int i = 0; i < list.size(); i++) {
         UserVariable var = list.get(i);

         if(var != null && Tool.equals(var.getName(), name)) {
            return var;
         }
      }

      return null;
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);

      try {
         AssetRepository rep = AssetUtil.getAssetRepository(false);
         XDataService service = XFactory.getDataService();
         JDBCQuery query = getSQLBoundTableInfo().getQuery();

         if(query != null) {
            query = query.clone();
         }

         UserVariable[] vars = service.getQueryParameters(rep.getSession(), query, true);

         if(vars != null) {
            for(int j = 0; j < vars.length; j++) {
               String name = vars[j].getName();
               Assembly[] arr = ws.getAssemblies();

               for(int k = 0; k < arr.length; k++) {
                  if(arr[k] instanceof VariableAssembly) {
                     VariableAssembly vassembly = (VariableAssembly) arr[k];
                     UserVariable var = vassembly.getVariable();

                     if(var != null && name.equals(var.getName())) {
                        set.add(new AssemblyRef(vassembly.getAssemblyEntry()));
                     }
                  }
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to retrieve all variables", ex);
      }
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      super.getAugmentedDependeds(dependeds);
      Set<AssemblyRef> dependedVariables = new HashSet<>();

      try {
         AssetRepository rep = AssetUtil.getAssetRepository(false);
         XDataService service = XFactory.getDataService();
         UserVariable[] vars = service.getQueryParameters(rep.getSession(),
                                                          getSQLBoundTableInfo().getQuery(),
                                                          true);

         if(vars != null) {
            for(int j = 0; j < vars.length; j++) {
               String name = vars[j].getName();
               Assembly[] arr = ws.getAssemblies();

               for(int k = 0; k < arr.length; k++) {
                  if(arr[k] instanceof VariableAssembly) {
                     VariableAssembly vassembly = (VariableAssembly) arr[k];
                     UserVariable var = vassembly.getVariable();

                     if(var != null && name.equals(var.getName())) {
                        dependedVariables.add(new AssemblyRef(vassembly.getAssemblyEntry()));
                     }
                  }
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to retrieve all variables", ex);
      }

      addToDependencyTypes(dependeds, dependedVariables, DependencyType.SQL_CONDITION_VARIABLE);
   }

   // find and mark variable used in 'in' condition
   private void findVarTypes(List<UserVariable> vars) {
      String sql = getSQLBoundTableInfo().getQuery().getSQLAsString();

      for(UserVariable var : vars) {
         Pattern patt = Pattern.compile("\\Win\\W+\\(\\W*\\$\\(" + var.getName() + "\\)\\W*\\)",
                                        Pattern.CASE_INSENSITIVE);
         if(patt.matcher(sql).find()) {
            var.setMultipleSelection(true);
            var.setUsedInOneOf(true);
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         SQLBoundTableAssembly assembly =
            (SQLBoundTableAssembly) super.clone();
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.println("<sqlEdited val=\"" + sqlEdited + "\"/>");
      writer.println("<advancedEditing val=\"" + advancedEditing + "\"/>");
      writer.println("<convertFromQuery val=\"" + convertFromQuery + "\"/>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "sqlEdited");

      if(node != null) {
         String value = Tool.getAttribute(node, "val");
         sqlEdited = value.equals("true");
      }

      node = Tool.getChildNodeByTagName(elem, "advancedEditing");

      if(node != null) {
         String value = Tool.getAttribute(node, "val");
         advancedEditing = value.equals("true");
      }

      node = Tool.getChildNodeByTagName(elem, "convertFromQuery");

      if(node != null) {
         String value = Tool.getAttribute(node, "val");
         convertFromQuery = value.equals("true");
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      boolean success = super.printKey(writer);

      if(!success) {
         return false;
      }

      writer.print("SQLBT[");
      SQLBoundTableAssemblyInfo info = getSQLBoundTableInfo();
      JDBCQuery query = info.getQuery();
      String sqlStr = query.getSQLDefinition().getSQLString();
      writer.print(sqlStr);
      writer.print("]");
      XSelection selection = query.getSelection();

      if(selection != null) {
         writer.print("FILED_META[");

         for(int i = 0; i < selection.getColumnCount(); i++) {
            XMetaInfo xMetaInfo = selection.getXMetaInfo(i);

            if(xMetaInfo.getXDrillInfo() != null) {
               writer.print("DRILL[");
               writer.print(xMetaInfo.getXDrillInfo().toString(true));
               writer.print("]");
            }

            if(xMetaInfo.getXFormatInfo() != null) {
               XFormatInfo xFormatInfo = xMetaInfo.getXFormatInfo();
               writer.print("FORMAT[");
               writer.print(xFormatInfo.getFormat() + xFormatInfo.getFormatSpec());
               writer.print("]");
            }
         }

         writer.print("]");
      }

      return true;
   }

   private boolean sqlEdited = false;
   private boolean advancedEditing = false;
   private boolean convertFromQuery = false;
   private static final Logger LOG = LoggerFactory.getLogger(SQLBoundTableAssembly.class);
}
