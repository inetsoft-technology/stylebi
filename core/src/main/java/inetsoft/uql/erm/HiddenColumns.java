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
package inetsoft.uql.erm;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.erm.vpm.VpmObject;
import inetsoft.uql.script.StringArray;
import inetsoft.uql.script.VpmScope;
import inetsoft.uql.util.XUtil;
import inetsoft.util.IteratorEnumeration;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;

/**
 * HiddenColumns defines hidden columns to hide columns when executing a query.
 *
 * @author InetSoft Technology
 * @version 8.0
 */
public class HiddenColumns extends VpmObject {
   /**
    * Constructor.
    */
   public HiddenColumns() {
      super();

      setName("");
      roles = new ArrayList<>();
      hiddens = new ArrayList<>();
   }

   /**
    * Assign a role to this virtual private model.
    * @param role the name of the role.
    */
   public void addRole(String role) {
      int index = roles.indexOf(role);

      if(index < 0) {
         roles.add(role);
      }
      else {
         roles.set(index, role);
      }
   }

   /**
    * Determine if the specified role is assigned to this virtual private model.
    * @param role the name of the role.
    * @return <code>true</code> if the specified role is assigned to this
    *  model, <code>false</code> otherwise.
    */
   public boolean containsRole(IdentityID role) {
      return roles.contains(role.getName());
   }

   /**
    * Get all the assigned roles.
    * @return all the assigned roles.
    */
   public Enumeration<String> getRoles() {
      return new IteratorEnumeration<>(roles.iterator());
   }

   /**
    * Remove a role from this virtual private model.
    * @param role the name of the role to remove.
    */
   public void removeRole(IdentityID role) {
      roles.remove(role);
   }

   /**
    * Remove all the roles from this virtual private model.
    */
   public void removeRoles() {
      roles.clear();
   }

   /**
    * Add a hidden column to this virtual private model.
    * @param column the specified hidden column to add.
    */
   public void addHiddenColumn(DataRef column) {
      int index = hiddens.indexOf(column);

      if(index < 0) {
         hiddens.add(column);
      }
      else {
         hiddens.set(index, column);
      }
   }

   /**
    * Get all the hidden columns.
    * @return all the hidden columns.
    */
   public Enumeration<DataRef> getHiddenColumns() {
      return new IteratorEnumeration<>(hiddens.iterator());
   }

   /**
    * Remove a hidden column from this virtual private model.
    * @param column the hidden column to remove.
    */
   public void removeHiddenColumn(DataRef column) {
      hiddens.remove(column);
   }

   /**
    * Remove all the hidden columns from this virtual private model.
    */
   public void removeHiddenColumns() {
      hiddens.clear();
   }

   /**
    * Check if the virtual private model should be applied.
    * @param tables the specified query tables.
    * @param columns the specified query columns.
    * @param vars the specified variable table.
    * @param user the specified principal.
    * @param isTest  if this is evaluating for a test
    * @return the to be hidden columns.
    */
   public String[] evaluate(String[] tables, String[] columns,
                            VariableTable vars, Principal user, boolean isTest,
                            String partition)
      throws Exception
   {
      if(user != null && XPrincipal.SYSTEM.equals(user.getName())) {
         return new String[0];
      }

      IdentityID[] roles = XUtil.getUserRoles(user, false);

      for(IdentityID role : roles) {
         // is a granted role? do not hide any column
         if(containsRole(role)) {
            return new String[0];
         }
      }

      String[] arr = new String[hiddens.size()];
      int i = 0;

      for(DataRef column : hiddens) {
         arr[i++] = isTest ? column.toView() : column.getName();
      }

      String script = getScript();

      // no script defined, use the hidden columns defined on gui
      if(script == null || script.trim().length() == 0 ||
         XUtil.isAllComment(script))
      {
         return arr;
      }

      VpmScope scope = new VpmScope();
      scope.setVariableTable(vars);
      scope.setUser(user);

      // use string array to support query/modify/delete the array in script
      StringArray tarray = new StringArray("table", tables);
      StringArray carray = new StringArray("column", columns);
      StringArray harray = new StringArray("hiddenColumn", arr);
      scope.put("tables", scope, tarray);
      scope.put("columns", scope, carray);
      scope.put("hiddenColumns", scope, harray);
      scope.put("partition", scope, partition);

      if(WSExecution.getAssetQuerySandbox() != null) {
         scope.put("creatingMV", scope, WSExecution.getAssetQuerySandbox().isCreatingMV());
      }
      else {
         scope.put("creatingMV", scope, false);
      }

      Object result;

      if(isTest) {
         result = VpmScope.execute(script, scope);
      }
      else {
         //If trigger script fails, return the original hidden columns.
         try {
            result = VpmScope.execute(script, scope);
         }
         catch(Throwable ex) {
            LOG.error("Failed to execute trigger script of hidden columns.", ex);
            return arr;
         }
      }

      if(result == null) {
         return new String[0];
      }

      if(!(result instanceof Object[])) {
         throw new Exception(
            "The script result of hidden columns should be a string array!");
      }

      Object[] objs = (Object[]) result;
      List<String> list = new ArrayList<>();

      for(i = 0; i < objs.length; i++) {
         if(objs[i] == null) {
            continue;
         }

         String col = objs[i].toString();

         if(partition != null && col.startsWith(partition + ".")) {
            col = col.substring(partition.length() + 1);
         }

         list.add(col);
      }

      arr = new String[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      HiddenColumns columns = (HiddenColumns) super.clone();
      columns.hiddens = Tool.deepCloneCollection(hiddens);
      columns.roles = Tool.deepCloneCollection(roles);

      return columns;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.println("<roles>");

      for(String role : roles) {
         writer.print("<role>");
         writer.print("<![CDATA[" + role + "]]>");
         writer.println("</role>");
      }

      writer.println("</roles>");

      writer.println("<hiddenColumns>");

      for(DataRef column : hiddens) {
         column.writeXML(writer);
      }

      writer.println("</hiddenColumns>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element rsnode = Tool.getChildNodeByTagName(elem, "roles");
      NodeList rnodes = Tool.getChildNodesByTagName(rsnode, "role");

      for(int i = 0; i < rnodes.getLength(); i++) {
         Element rnode = (Element) rnodes.item(i);
         String role = Tool.getValue(rnode);

         if(role!= null) {
            roles.add(role);
         }
      }

      Element hnode = Tool.getChildNodeByTagName(elem, "hiddenColumns");
      NodeList cnodes = Tool.getChildNodesByTagName(hnode, "dataRef");

      for(int i = 0; i < cnodes.getLength(); i++) {
         Element cnode = (Element) cnodes.item(i);
         DataRef column = AbstractDataRef.createDataRef(cnode);
         hiddens.add(column);
      }
   }

   private List<String> roles;
   private List<DataRef> hiddens;
   private static final Logger LOG = LoggerFactory.getLogger(HiddenColumns.class);
}
