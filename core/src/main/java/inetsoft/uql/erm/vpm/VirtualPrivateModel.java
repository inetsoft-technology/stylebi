/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.erm.vpm;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.erm.*;
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
import java.io.StringWriter;
import java.security.Principal;
import java.util.*;

/**
 * VirtualPrivateModel, to restrict the visibility of table columns and the
 * data returned from executed queries based upon some security criteria.
 *
 * @author  InetSoft Technology Corp.
 * @version 8.0
 */
public class VirtualPrivateModel extends VpmObject {
   /**
    * Create a virtual private model.
    */
   public VirtualPrivateModel() {
      super();

      conds = new ArrayList();
   }

   /**
    * Create a virtual private model.
    * @param name the specified name for the virtual private model.
    */
   public VirtualPrivateModel(String name) {
      this();

      setName(name);
   }

   /**
    * Get created time.
    * @return created time.
    */
   public long getCreated() {
      return created;
   }

   /**
    * Set created time.
    * @param created the specified created time.
    */
   public void setCreated(long created) {
      this.created = created;
   }

   /**
    * Get last modified.
    * @return last modified time.
    */
   public long getLastModified() {
      return modified;
   }

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   public void setLastModified(long modified) {
      this.modified = modified;
   }

   /**
    * Get the created person.
    * @return the created person.
    */
   public String getCreatedBy() {
      return createdBy;
   }

   /**
    * Set the created person
    * @param createdBy the created person.
    */
   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   /**
    * Get last modified person.
    * @return last modified person.
    */
   public String getLastModifiedBy() {
      return modifiedBy;
   }

   /**
    * Set last modified person.
    * @param modifiedBy the specified last modified person.
    */
   public void setLastModifiedBy(String modifiedBy) {
      this.modifiedBy = modifiedBy;
   }

   /**
    * Get the description of this virtual private model.
    * @return the description.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Set the description of this virtual private model.
    * @param desc the specified description.
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Determine if the specified table or alias table is assigned to this virtual private model.
    * @param table the name of the table.
    * @return <code>true</code> if the specified table is assigned to this
    *  model, <code>false</code> otherwise.
    */
   public boolean containsTable(String table, XPartition partition) {
      if(table == null) {
         return false;
      }

      if(hidden != null) {
         Enumeration cols = hidden.getHiddenColumns();

         while(cols.hasMoreElements()) {
            DataRef ref = (DataRef) cols.nextElement();
            String usedTable = ref.getEntity();

            // use alias table
            if(partition != null && partition.isAliasTable(usedTable)) {
               usedTable = partition.getRealTable(usedTable, true);
            }

            if(isSameTable(table, usedTable)) {
               return true;
            }
         }
      }

      Iterator iterator = conds.iterator();

      while(iterator.hasNext()) {
         VpmCondition cond = (VpmCondition) iterator.next();
         String condTbl = cond.getTable();

         if(isSameTable(table, condTbl)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the two tables are same
    * @hidden
    */
   public static boolean isSameTable(String tbl1, String tbl2) {
      if(tbl1 == null || tbl2 == null) {
         return false;
      }

      tbl1 = tbl1.toLowerCase();
      tbl2 = tbl2.toLowerCase();

      if(tbl1.equals(tbl2)) {
         return true;
      }

      boolean qualified1 = tbl1.indexOf('.') > 0;
      boolean qualified2 = tbl2.indexOf('.') > 0;

      if(qualified1 != qualified2) {
         tbl1= tbl1.substring(tbl1.lastIndexOf('.') + 1);
         tbl2= tbl2.substring(tbl2.lastIndexOf('.') + 1);
         return tbl1.equals(tbl2);
      }

      return false;
   }

   /**
    * Add a vpm condition to this virtual private model.
    * @param vcond the specified vpm condition to add.
    */
   public void addCondition(VpmCondition vcond) {
      int index = conds.indexOf(vcond);

      if(index < 0) {
         conds.add(vcond);
      }
      else {
         conds.set(index, vcond);
      }
   }

   /**
    * Check if contains a vpm condition in this virtual private model.
    * @param vcond the specified vpm condition.
    * @return <tt>true</tt> if contains the vpm condition, <tt>false</tt>
    * otherwise.
    */
   public boolean containsCondition(VpmCondition vcond) {
      return conds.contains(vcond);
   }

   /**
    * Get the vpm condition in this virtual private model.
    * @param name the specified name of the vpm condition.
    * @return the vpm condition if found, <tt>null</tt> otherwise.
    */
   public VpmCondition getCondition(String name) {
      Iterator iterator = conds.iterator();

      while(iterator.hasNext()) {
         VpmCondition vcond = (VpmCondition) iterator.next();

         if(vcond.getName().equals(name)) {
            return vcond;
         }
      }

      return null;
   }

   /**
    * Get all the vpm conditions.
    * @return all the vpm conditions.
    */
   public Enumeration getConditions() {
      return new IteratorEnumeration(conds.iterator());
   }

   /**
    * Remove a vpm condition from this virtual private model.
    * @param name the name of the vpm condition to remove.
    */
   public void removeCondition(String name) {
      VpmCondition vcond = getCondition(name);

      if(vcond == null) {
         return;
      }

      conds.remove(vcond);
   }

   /**
    * Remove a vpm condition from this virtual private model.
    * @param vcond the specified vpm condition to remove.
    */
   public void removeCondition(VpmCondition vcond) {
      conds.remove(vcond);
   }

   /**
    * Remove all the vpm conditions from this virtual private model.
    */
   public void removeConditions() {
      conds.clear();
   }

   /**
    * Get the hidden columns.
    * @return the hidden columns to hide columns when executing a query.
    */
   public HiddenColumns getHiddenColumns() {
      return hidden;
   }

   /**
    * Set the hidden columns.
    * @param hidden the specified hidden columns to hide columns when executing
    * a query.
    */
   public void setHiddenColumns(HiddenColumns hidden) {
      this.hidden = hidden;
   }

   /**
    * Check if the virtual private model should be applied.
    * @param tables the specified tables.
    * @param columns the specified query columns.
    * @param vars the specified variable table.
    * @param user the specified principal.
    * @param ds the used datasource.
    * @return <tt>true</tt> if should be applied, <tt>false</tt> otherwise.
    */
   public boolean evaluate(String[] tables, String[] columns,
                           VariableTable vars, Principal user,
                           String partition, String ds, boolean isTest) throws Exception
   {
      if(user != null && XPrincipal.SYSTEM.equals(user.getName())) {
         return false;
      }

      String script = getScript();

      // no script defined?
      if(script == null || script.trim().length() == 0 ||
         XUtil.isAllComment(script))
      {
         if(partition != null) {
            Iterator<VpmCondition> iterator = conds.iterator();

            while(iterator.hasNext()) {
               VpmCondition cond = iterator.next();

               if(cond.getType() == VpmCondition.PHYSICMODEL &&
                  partition.equals(cond.getTable()))
               {
                  return true;
               }
            }
         }

         XPartition partitionObj = null;

         if(ds != null) {
            XRepository repository = XFactory.getRepository();
            XDataModel model = repository.getDataModel(ds);
            partitionObj = model.getPartition(partition, user);
         }

         // contains one used table? apply the vpm
         for(int i = 0; i < tables.length; i++) {
            if(containsTable(tables[i], partitionObj)) {
               return true;
            }
         }

         // by default we do not apply the vpm
         return false;
      }

      VpmScope scope = new VpmScope();
      scope.setVariableTable(vars);
      scope.setUser(user);

      StringArray tarray = new StringArray("table", tables);
      StringArray carray = new StringArray("column", columns);
      scope.put("tables", scope, tarray);
      scope.put("columns", scope, carray);

      if(WSExecution.getAssetQuerySandbox() != null) {
         scope.put("creatingMV", scope, WSExecution.getAssetQuerySandbox().isCreatingMV());
      }
      else {
         scope.put("creatingMV", scope, false);
      }

      if(partition != null) {
         scope.put("partition", scope, partition);
      }

      Object result;

      if(isTest) {
         result = VpmScope.execute(script, scope);
      }
      else {
         //Always apply VPM in the event that trigger script fails.
         try {
            result = VpmScope.execute(script, scope);
         }
         catch(Throwable ex) {
            LOG.error("Failed to execute trigger script of lookup.", ex);
            return true;
         }
      }

      return Boolean.TRUE.equals(result);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      VirtualPrivateModel vpm = (VirtualPrivateModel) super.clone();
      vpm.conds = Tool.deepCloneCollection(conds);
      vpm.hidden = hidden == null ? null : (HiddenColumns) hidden.clone();

      return vpm;
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      /*
      writer.println("<tables>");
      Iterator iterator = tables.iterator();

      while(iterator.hasNext()) {
         String table = (String) iterator.next();
         writer.print("<table>");
         writer.print("<![CDATA[" + table + "]]>");
         writer.println("</table>");
      }

      writer.println("</tables>");
      */

      writer.println("<conditions>");
      Iterator iterator = conds.iterator();

      while(iterator.hasNext()) {
         VpmCondition cond = (VpmCondition) iterator.next();
         cond.writeXML(writer);
      }

      writer.println("</conditions>");

      if(hidden != null) {
         writer.println("<hiddenColumns>");
         hidden.writeXML(writer);
         writer.println("</hiddenColumns>");
      }

      if(desc != null) {
         writer.print("<description>");
         writer.print("<![CDATA[" + desc + "]]>");
         writer.println("</description>");
      }

      if(createdBy != null) {
         writer.print("<createdBy>");
         writer.print("<![CDATA[" + createdBy + "]]>");
         writer.println("</createdBy>");
      }

      if(modifiedBy != null) {
         writer.print("<modifiedBy>");
         writer.print("<![CDATA[" + modifiedBy + "]]>");
         writer.println("</modifiedBy>");
      }

      if(created != 0) {
         writer.print("<created>");
         writer.print("<![CDATA[" + created + "]]>");
         writer.println("</created>");
      }

      if(modified != 0) {
         writer.print("<modified>");
         writer.print("<![CDATA[" + modified + "]]>");
         writer.println("</modified>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      /*
      Element tsnode = Tool.getChildNodeByTagName(elem, "tables");
      NodeList tnodes = Tool.getChildNodesByTagName(tsnode, "table");

      for(int i = 0; i < tnodes.getLength(); i++) {
         Element tnode = (Element) tnodes.item(i);
         String table = Tool.getValue(tnode);

         if(table != null) {
            tables.add(table);
         }
      }
      */

      Element csnode = Tool.getChildNodeByTagName(elem, "conditions");
      NodeList cnodes = Tool.getChildNodesByTagName(csnode, "vpmObject");

      for(int i = 0; i < cnodes.getLength(); i++) {
         Element cnode = (Element) cnodes.item(i);
         VpmCondition cond = (VpmCondition) VpmObject.createVpmObject(cnode);
         conds.add(cond);
      }

      Element hnode = Tool.getChildNodeByTagName(elem, "hiddenColumns");

      if(hnode != null) {
         hnode = Tool.getFirstChildNode(hnode);
         hidden = (HiddenColumns) VpmObject.createVpmObject(hnode);
      }

      Element dnode = Tool.getChildNodeByTagName(elem, "description");

      if(dnode != null) {
         desc = Tool.getValue(dnode);
      }

      dnode = Tool.getChildNodeByTagName(elem, "createdBy");

      if(dnode != null) {
         createdBy = Tool.getValue(dnode);
      }

      dnode = Tool.getChildNodeByTagName(elem, "modifiedBy");

      if(dnode != null) {
         modifiedBy = Tool.getValue(dnode);
      }

      dnode = Tool.getChildNodeByTagName(elem, "created");

      if(dnode != null) {
         created = Long.parseLong(Tool.getValue(dnode));
      }

      dnode = Tool.getChildNodeByTagName(elem, "modified");

      if(dnode != null) {
         modified = Long.parseLong(Tool.getValue(dnode));
      }
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      StringWriter s = new StringWriter();
      PrintWriter p = new PrintWriter(s);
      writeXML(p);

      return s.toString();
   }

   private String desc;
   private List conds;
   private HiddenColumns hidden;
   private long created;
   private long modified;
   private String createdBy;
   private String modifiedBy;
   private static final Logger LOG = LoggerFactory.getLogger(VirtualPrivateModel.class);
}
