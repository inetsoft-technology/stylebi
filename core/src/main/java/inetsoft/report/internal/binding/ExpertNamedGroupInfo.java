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
package inetsoft.report.internal.binding;

import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import inetsoft.util.xml.VersionControlComparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;

/**
 * Expert named group info. It stores a condietion list for every group.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ExpertNamedGroupInfo implements XNamedGroupInfo, XMLSerializable {
   /**
    * Construct an expert named group info.
    */
   public ExpertNamedGroupInfo() {
      clear();
   }

   /**
    * Create a condition to the manual ordering of values.
    */
   public static ConditionList createManualCondition(DataRef ref, String name) {
      int option = DateRangeRef.NONE_DATE_GROUP;

      if(ref instanceof GroupField) {
         option = ((GroupField) ref).getOption();
      }

      if(ref instanceof DataRefWrapper) {
         ref = ((DataRefWrapper) ref).getDataRef();
      }

      return createManualCondition(ref, name, option);
   }

   /**
    * Create a condition to the manual ordering of values.
    */
   public static ConditionList createManualCondition(DataRef ref, String name, int option) {
      ConditionList conds = new ConditionList();
      Condition cond = new Condition();
      String type = ref.getTypeNode().getType();

      if(XSchema.isDateType(type) &&
         (option & DateRangeRef.PART_DATE_GROUP) == DateRangeRef.PART_DATE_GROUP)
      {
         type = XSchema.INTEGER;
      }

      cond.setType(type);
      Object obj = "".equals(name) ? null : Tool.getData(type, name, true);

      if(obj == null || "".equals(obj)) {
         cond.setOperation(XCondition.NULL);
      }
      else {
         cond.addValue(obj);
      }

      ConditionItem item = new ConditionItem(ref, cond, 0);
      conds.append(item);
      return conds;
   }

   /**
    * Get type of the named group info.
    *
    * @return the type of the named group info
    */
   @Override
   public int getType() {
      return XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO;
   }

   /**
    * Reset the named group info.
    */
   @Override
   public void clear() {
      groups.removeAllElements();
      conditions.clear();
   }

   /**
    * Check if the named group info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      return groups.size() == 0;
   }

   /**
    * Get the condition list of a group.
    *
    * @param name the group name
    * @return the condition list of the group
    */
   @Override
   public ConditionList getGroupCondition(String name) {
      ConditionList conds = conditions.get(name);

      if(conds == null) {
         conds = new ConditionList();
      }

      return conds;
   }

   public Set<String> getGroupConditionKeys() {
      return conditions.keySet();
   }

   /**
    * Set the condition list of a group.
    *
    * @param name the group name
    * @param list the condition list
    */
   public void setGroupCondition(String name, ConditionList list) {
      addGroupName(name);
      conditions.put(name, list);
   }

   /**
    * Get all the group names.
    *
    * @return all the group names
    */
   @Override
   public String[] getGroups(boolean sort) {
      String[] names = new String[groups.size()];
      groups.copyInto(names);

      if(sort) {
         Arrays.sort(names, VersionControlComparators.string);
      }

      return names;
   }

   /**
    * Add a group name.
    *
    * @param name the group name
    */
   public void addGroupName(String name) {
      if(!groups.contains(name)) {
         groups.addElement(name);
      }
   }

   /**
    * Remove a group name.
    *
    * @param name the specified group name
    */
   @Override
   public void removeGroup(String name) {
      removeGroup(groups.indexOf(name));
   }

   /**
    * Remove a group name at an index.
    *
    * @param index the specified index
    */
   private void removeGroup(int index) {
      if(index >= 0 && index < groups.size()) {
         conditions.remove(groups.elementAt(index));
         groups.removeElementAt(index);
      }
   }

   /**
    * Read serializable object.
    */
   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, IOException {

      s.defaultReadObject();
   }

   /**
    * Write serializable object.
    */
   private void writeObject(ObjectOutputStream s) throws IOException {
      s.defaultWriteObject();
   }

   /**
    * Clone it.
    */
   @Override
   public Object clone() {
      try {
         ExpertNamedGroupInfo info = (ExpertNamedGroupInfo) super.clone();

         info.conditions = Tool.deepCloneMap(conditions);
         //info.conditions = (Hashtable) conditions.clone();
         info.groups = new Vector<>(groups);

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone expert named group info", ex);
      }

      return null;
   }

   /**
    * Write to XML.
    *
    * @param writer the stream
    */
   @Override
   public void writeXML(PrintWriter writer) {
      String[] names = getGroups(false);

      if(names.length > 0) {
         writer.println("<namedgroups type=\"" + getType() + "\">");

         for(int i = 0; i < names.length; i++) {
            writer.print("<namedGroup><![CDATA[" +
               Tool.byteEncode("".equals(names[i]) ? BLANKSTRING : names[i], true) + "]]>");

            ConditionList list = getGroupCondition(names[i]);

            list.writeXML(writer);
            writer.println("</namedGroup>");
         }

         writer.println("</namedgroups>");
      }
   }

   /**
    * Parse the xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList list = Tool.getChildNodesByTagName(tag, "namedGroup");

      if(list.getLength() > 0) {
         for(int i = 0; i < list.getLength(); i++) {
            Element tag2 = (Element) list.item(i);
            String name = Tool.byteDecode(Tool.getValue(tag2));

            if(name == null || BLANKSTRING.equals(name)) {
               name = "";
            }

            NodeList conds = Tool.getChildNodesByTagName(tag2, "conditions");

            if(conds.getLength() > 0) {
               ConditionList condition = new ConditionList();

               condition.parseXML((Element) conds.item(0));
               setGroupCondition(name,
                                 Util.buildReportConditionList(condition));
            }
         }
      }
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return new UserVariable[0];
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      return true;
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ExpertNamedGroupInfo)) {
         return false;
      }

      ExpertNamedGroupInfo info2 = (ExpertNamedGroupInfo) obj;

      if(!this.conditions.equals(info2.conditions)) {
         return false;
      }

      if(!this.groups.equals(info2.groups)) {
         return false;
      }

      return true;
   }

   private Hashtable<String, ConditionList> conditions = new Hashtable<>();
   private Vector<String> groups = new Vector<>();
   private final String BLANKSTRING = "__blank__";

   private static final Logger LOG = LoggerFactory.getLogger(ExpertNamedGroupInfo.class);
}

