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
package inetsoft.uql.asset;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * Bound table assembly, bound to a data source.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class BoundTableAssembly extends AbstractTableAssembly {
   /**
    * Constructor.
    */
   public BoundTableAssembly() {
      super();

      cassemblies = new ArrayList();
   }

   /**
    * Constructor.
    */
   public BoundTableAssembly(Worksheet ws, String name) {
      super(ws, name);

      cassemblies = new ArrayList();
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new BoundTableAssemblyInfo();
   }

   /**
    * Get the bound table assembly info.
    * @return the bound table assembly info of the bound table assembly.
    */
   protected BoundTableAssemblyInfo getBoundTableInfo() {
      return (BoundTableAssemblyInfo) getTableInfo();
   }

   /**
    * Check if the mirror assembly is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      super.checkValidity(checkCrossJoins);
      getSourceInfo().checkValidity();

      for(int i = 0; i < cassemblies.size(); i++) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);
         cond.checkValidity();
      }
   }

   /**
    * Get the source info.
    * @return the source info of the bound table assembly.
    */
   @Override
   public SourceInfo getSourceInfo() {
      return getBoundTableInfo().getSourceInfo();
   }

   /**
    * Set the source info.
    * @param source the specified source info.
    */
   @Override
   public void setSourceInfo(SourceInfo source) {
      getBoundTableInfo().setSourceInfo(source);
   }

   /**
    * Get the condition assembly.
    * @param index the specified index.
    * @return the condition assembly.
    */
   public ConditionAssembly getConditionAssembly(int index) {
      return (ConditionAssembly) cassemblies.get(index);
   }

   /**
    * Get all the condition assemblies.
    * @return all the condition assemblies.
    */
   public ConditionAssembly[] getConditionAssemblies() {
      shrinkConditionAssemblies();
      ConditionAssembly[] arr = new ConditionAssembly[cassemblies.size()];
      cassemblies.toArray(arr);
      return arr;
   }

   /**
    * Get the condition assembly count.
    * @return the condition assembly count.
    */
   public int getConditionAssemblyCount() {
      return cassemblies.size();
   }

   /**
    * Add one condition assembly.
    * @param cond the specified condition assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addConditionAssembly(ConditionAssembly cond) {
      cond = (ConditionAssembly) cond.clone();
      int index = cassemblies.indexOf(cond);

      if(index >= 0) {
         cassemblies.remove(index);
         cassemblies.add(index, cond);
      }
      else {
         cassemblies.add(cond);
      }

      return true;
   }

   /**
    * Remove the condition assembly.
    * @param index the specified index.
    */
   public void removeConditionAssembly(int index) {
      cassemblies.remove(index);
   }

   /**
    * Remove the condition assembly.
    * @param cassembly the specified condition assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeConditionAssembly(ConditionAssembly cassembly) {
      return removeConditionAssembly(cassembly.getName());
   }

   /**
    * Remove the condition assembly.
    * @param name the specified condition assembly name.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeConditionAssembly(String name) {
      for(int i = 0; i < cassemblies.size(); i++) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);

         if(cond.getName().equals(name)) {
            cassemblies.remove(i);
            return true;
         }
      }

      return false;
   }

   /**
    * Remove all the condition assemblies.
    */
   public void removeConditionAssemblies() {
      cassemblies.clear();
   }

   /**
    * Update the assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      if(!super.update()) {
         return false;
      }

      realAggregateInfo = null;
      int size = cassemblies.size();

      for(int i = 0; i < size; i++) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);
         cond = (ConditionAssembly) ConditionUtil.updateConditionListWrapper(cond, ws);

         if(cond == null) {
            return false;
         }

         cassemblies.set(i, cond.clone());
      }

      return true;
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      super.renameDepended(oname, nname);

      for(int i = 0; i < cassemblies.size(); i++) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);

         if(cond.getName().equals(oname)) {
            ConditionAssembly assembly2 = ws == null ? null :
               (ConditionAssembly) ws.getAssembly(nname);

            if(assembly2 == null) {
               LOG.error("Condition assembly not found: " + nname);
               continue;
            }

            assembly2.update();
            assembly2 = (ConditionAssembly) assembly2.clone();
            cassemblies.set(i, assembly2);
         }
      }
   }

   /**
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   @Override
   public void setWorksheet(Worksheet ws) {
      super.setWorksheet(ws);

      for(int i = 0; i < cassemblies.size(); i++) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);
         cond.setWorksheet(ws);
      }
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      super.replaceVariables(vars);

      for(int i = 0; i < cassemblies.size(); i++) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);
         cond.replaceVariables(vars);
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      UserVariable[] vars = super.getAllVariables();
      List list = new ArrayList();
      mergeVariables(list, vars);

      for(int i = 0; i < cassemblies.size(); i++) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);
         vars = cond.getAllVariables();
         mergeVariables(list, vars);
      }

      vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);
      shrinkConditionAssemblies();

      for(int i = 0; i < cassemblies.size(); i++) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);
         AssetUtil.getConditionDependeds(ws, cond, set);
      }
   }

   /**
    * Shrink condition assemblies.
    */
   private void shrinkConditionAssemblies() {
      for(int i = cassemblies.size() - 1; i >= 0; i--) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);
         SourceInfo source = cond.getAttachedSource();

         if(!Tool.equals(source, getSourceInfo())) {
            cassemblies.remove(i);
         }
      }
   }

   /**
    * Get the sql helper.
    */
   public SQLHelper getSQLHelper() {
      String src = getSource();

      try {
         XRepository repository = XFactory.getRepository();
         XDataSource ds = repository.getDataSource(src);
         return ds == null ? null : SQLHelper.getSQLHelper(ds);
      }
      catch(Exception ex) {
         LOG.error("Failed to get SQL helper for data source " + src, ex);
      }

      return null;
   }

   /**
    * Get the source of the table assembly.
    * @return the source of the table assembly.
    */
   @Override
   public String getSource() {
      return getSourceInfo().getPrefix();
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      writer.println("<conditionAssemblies>");

      for(int i = 0; i < cassemblies.size(); i++) {
         ConditionAssembly cond = (ConditionAssembly) cassemblies.get(i);
         cond.writeXML(writer);
      }

      writer.println("</conditionAssemblies>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element csnode = Tool.getChildNodeByTagName(elem, "conditionAssemblies");
      NodeList cnodes = Tool.getChildNodesByTagName(csnode, "assembly");

      for(int i = 0; i < cnodes.getLength(); i++) {
         Element cnode = (Element) cnodes.item(i);
         ConditionAssembly assembly =
            (ConditionAssembly) AbstractWSAssembly.createWSAssembly(cnode, ws);
         cassemblies.add(assembly);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         BoundTableAssembly assembly = (BoundTableAssembly) super.clone();
         assembly.cassemblies = cloneAssemblyList(cassemblies);
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Clone assembly list.
    */
   private final ArrayList cloneAssemblyList(ArrayList list) {
      int size = list.size();
      ArrayList list2 = new ArrayList(size);

      for(int i = 0; i < size; i++) {
         Object obj = ((Assembly) list.get(i)).clone();
         list2.add(obj);
      }

      return list2;
   }

   /**
    * Get the hash code only considering content.
    * @return the hash code only considering content.
    */
   @Override
   public int getContentCode() {
      SourceInfo sinfo = getSourceInfo();

      return sinfo.hashCode();
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

      writer.print("BT[");
      SourceInfo sinfo = getSourceInfo();
      writer.print(sinfo == null ? null : sinfo.toString());
      int count = getConditionAssemblyCount();
      writer.print(",");
      writer.print(count);

      for(int i = 0; i < count; i++) {
         ConditionAssembly cassembly = getConditionAssembly(i);
         writer.print(",");
         ConditionUtil.printConditionsKey(cassembly, writer);
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(!(obj instanceof BoundTableAssembly)) {
         return false;
      }

      BoundTableAssembly btable = (BoundTableAssembly) obj;
      SourceInfo sinfo = getSourceInfo();

      if(!Tool.equals(sinfo, btable.getSourceInfo())) {
         return false;
      }

      if(getConditionAssemblyCount() != btable.getConditionAssemblyCount()) {
         return false;
      }

      for(int i = 0; i < getConditionAssemblyCount(); i++) {
         ConditionAssembly cassembly1 = getConditionAssembly(i);
         ConditionAssembly cassembly2 = btable.getConditionAssembly(i);

         if(!ConditionUtil.equalsConditionListWrapper(cassembly1, cassembly2)) {
            return false;
         }
      }

      return true;
   }

   @Override
   public void setAggregateInfo(AggregateInfo info) {
      super.setAggregateInfo(info);
      realAggregateInfo = null;
   }

   /**
    * Set the runtime to process AggregateInfo.
    * @param info
    */
   public void setRuntimeAggregateInfo(AggregateInfo info) {
      realAggregateInfo = getAggregateInfo();
      super.setAggregateInfo(info);
   }

   private ArrayList cassemblies;

   private static final Logger LOG =
      LoggerFactory.getLogger(BoundTableAssembly.class);
}
