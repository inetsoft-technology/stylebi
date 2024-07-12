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
package inetsoft.uql.asset;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.OrderedMap;
import inetsoft.util.Tool;
import inetsoft.util.xml.VersionControlComparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * NamedGroupInfo stores named group information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class NamedGroupInfo implements XNamedGroupInfo, AssetObject {
   /**
    * Constructor.
    */
   public NamedGroupInfo() {
      super();

      this.map = new OrderedMap<>();
      this.others = XConstants.GROUP_OTHERS;
   }

   /**
    * Get type of the named group info.
    * @return the type of the named group info.
    */
   @Override
   public int getType() {
      return ASSET_NAMEDGROUP_INFO;
   }

   /**
    * Get all the group names.
    * @return all the group names.
    */
   @Override
   public String[] getGroups(boolean sort) {
      Set<String> set = map.keySet();
      String[] groups = new String[set.size()];
      set.toArray(groups);

      if(sort) {
         Arrays.sort(groups, VersionControlComparators.string);
      }

      return groups;
   }

   /**
    * Get the condition list.
    * @param group the specified group name.
    * @return the associated condition list.
    */
   @Override
   public ConditionList getGroupCondition(String group) {
      return map.get(group);
   }

   /**
    * Set the condition list.
    * @param group the specified group name.
    * @param conditions the secified condition list.
    */
   public void setGroupCondition(String group, ConditionList conditions) {
      map.put(group, conditions);
   }

   /**
    * Set the condition list.
    * @param idx the index of the key.
    * @param group the specified group name.
    * @param conditions the secified condition list.
    */
   public void setGroupCondition(int idx, String group, ConditionList conditions) {
      map.put(idx, group, conditions);
   }

   /**
    * Set other groups option.
    * @param others other group option.
    */
   public void setOthers(int others) {
      this.others = others;
   }

   /**
    * Get other groups option.
    * @return other group option.
    */
   public int getOthers() {
      return others;
   }

   /**
    * Remove the condition list.
    * @param group the specified group name.
    */
   @Override
   public void removeGroup(String group) {
      map.remove(group);
   }

   /**
    * Reset the named group info.
    */
   @Override
   public void clear() {
      map.clear();
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      for(ConditionList cond : map.values()) {
         cond.replaceVariables(vars);
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();

      for(ConditionList cond : map.values()) {
         UserVariable[] vars = cond.getAllVariables();

         for(UserVariable var : vars) {
            if(!list.contains(var)) {
               list.add(var);
            }
         }
      }

      UserVariable[] vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   /**
    * Get the assemblies depended on.
    * @param ws the specified worksheet.
    * @param set the set stores the assemblies depended on.
    */
   public void getDependeds(Worksheet ws, Set<AssemblyRef> set) {
      for(ConditionList conds : map.values()) {
         AssetUtil.getConditionDependeds(ws, conds, set);
      }
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Worksheet ws) {
      for(ConditionList conds : map.values()) {
         ConditionUtil.renameConditionListWrapper(conds, oname, nname, ws);
      }
   }

   /**
    * Check if the named group info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      return map.size() == 0;
   }

   /**
    * Update the assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean update(Worksheet ws) {
      String[] groups = getGroups();

      for(String group : groups) {
         ConditionList conds = getGroupCondition(group);
         conds = (ConditionList) ConditionUtil.updateConditionListWrapper(conds, ws);
         setGroupCondition(group, conds);
      }

      return true;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<namedGroupInfo class=\"" + getClass().getName() +
      "\" others=\"" + others + "\">");
      writeContents(writer);
      writer.println("</namedGroupInfo>");
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   protected void writeContents(PrintWriter writer) {
      // don't sort since the order may be significant (48630).
      String[] groups = getGroups(false);

      for(String group : groups) {
         ConditionList conditions = getGroupCondition(group);
         writer.println("<oneGroup>");
         writer.print("<group>");
         writer.print("<![CDATA[" + group + "]]>");
         writer.println("</group>");
         conditions.writeXML(writer);
         writer.println("</oneGroup>");
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      String prop = Tool.getAttribute(elem, "others");

      if(prop != null) {
         setOthers(Integer.parseInt(prop));
      }

      parseContents(elem);
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      NodeList nodes = Tool.getChildNodesByTagName(elem, "oneGroup");

      for(int i = 0; i < nodes.getLength(); i++) {
         Element onode = (Element) nodes.item(i);
         Element gnode = Tool.getChildNodeByTagName(onode, "group");
         String group = Tool.getValue(gnode);
         Element cnode = Tool.getChildNodeByTagName(onode, "conditions");
         ConditionList conditions = new ConditionList();
         conditions.parseXML(cnode);
         map.put(group, conditions);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         NamedGroupInfo info = (NamedGroupInfo) super.clone();
         info.map = Tool.deepCloneMap(map);
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("NINFO[");
      writer.print(others);
      String[] groups = getGroups();

      for(String group : groups) {
         writer.print(",");
         writer.print(group);
         writer.print(":");
         ConditionList conds = getGroupCondition(group);
         ConditionUtil.printConditionsKey(conds, writer);
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object.
    * @return true if equals, false otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof NamedGroupInfo)) {
         return false;
      }

      NamedGroupInfo info2 = (NamedGroupInfo) obj;
      return map.equals(info2.map) && others == info2.others;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      String[] groups = getGroups();
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < groups.length; i++) {
         if(i > 0) {
            sb.append("\n");
         }

         sb.append(groups[i]);
      }

      return sb.toString();
   }

   private OrderedMap<String, ConditionList> map;
   private int others;

   private static final Logger LOG = LoggerFactory.getLogger(NamedGroupInfo.class);
}
