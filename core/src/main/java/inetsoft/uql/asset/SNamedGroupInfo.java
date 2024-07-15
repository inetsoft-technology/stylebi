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
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.*;
import inetsoft.util.xml.VersionControlComparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;

/**
 * Simple named group info. It stores a value list for every group.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SNamedGroupInfo implements XNamedGroupInfo, XMLSerializable {
   /**
    * Construct a simple named group info.
    */
   public SNamedGroupInfo() {
      super();
      clear();
   }

   /**
    * Get type of the named group info.
    *
    * @return the type of the named group info
    */
   @Override
   public int getType() {
      return XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO;
   }

   /**
    * Reset the named group info.
    */
   @Override
   public void clear() {
      groups.clear();
      values.clear();
   }

   /**
    * Get the condition list of a group.
    *
    * @param name the group name
    * @return the condition list of the group
    */
   @Override
   public ConditionList getGroupCondition(String name) {
      List value = (List) values.get(name);
      ConditionList conds = new ConditionList();

      if(value == null || value.size() == 0) {
         return conds;
      }

      List processed = new ArrayList();

      for(int i = 0; i < value.size(); i++) {
         Condition cond = new Condition(ref.getDataType());
         Object ovalue = value.get(i);
         ovalue = "".equals(ovalue) ? null : ovalue;

         if(ovalue instanceof String && "null".equalsIgnoreCase(ovalue + "")) {
            ovalue = "null";
         }

         if(processed.contains(ovalue)) {
            continue;
         }

         if(ovalue instanceof String || ovalue == null) {
            Object val = ovalue == null ?
               null : Tool.getData(ref.getDataType(), (String) ovalue, true);
            int operation = val == null ? XCondition.NULL : XCondition.EQUAL_TO;
            cond.setOperation(operation);

            if(val != null) {
               cond.addValue(val);
            }
         }
         else {
            cond.addValue(ovalue);
         }

         processed.add(ovalue);
         ConditionItem item = new ConditionItem(ref, cond, 0);

         if(i != 0) {
            conds.append(new JunctionOperator(JunctionOperator.OR, 0));
         }

         conds.append(item);
      }

      return conds;
   }

   /**
    * Set the value of a group.
    *
    * @param name the specified group name
    * @param value the specified value
    */
   public void setGroupValue(String name, List value) {
      name = name == null ? "" : name;
      addGroupName(name);
      values.put(name, value);
   }

   /**
    * Get the value of a group.
    *
    * @param name the specified group name
    * @return group value list
    */
   public List getGroupValue(String name) {
      return values.get(name);
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
         values.remove(groups.elementAt(index));
         groups.removeElementAt(index);
      }
   }

   /**
    * Check if contains a named group.
    * @param group the specified group name.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public boolean contains(String group) {
      return groups.contains(group);
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
    * Setter of data ref.
    */
   public void setDataRef(DataRef ref) {
      this.ref = ref;
   }

   /**
    * Getter of data ref.
    */
   public DataRef getDataRef() {
      return ref;
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
         SNamedGroupInfo info = (SNamedGroupInfo) super.clone();
         info.values = (Hashtable) values.clone();
         info.groups = (Vector) groups.clone();
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
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
         writer.println("<namedgroups class=\"" + getClass().getName() + "\" type=\"" +
            getType() + "\" strictNull=\"true\">");

         for(int i = 0; i < names.length; i++) {
            writer.print("<namedGroup><![CDATA[" + names[i] + "]]>");
            List value = values.get(names[i]);

            if(value != null && value.size() > 0) {
               for(int j = 0; j < value.size(); j++) {
                  Object val = value.get(j);
                  writer.print("<value>");
                  writer.print("<![CDATA[" +
                     (Tool.equals("", val) ? BLANKSTRING : Tool.getPersistentDataString(val)) + "]]>");
                  writer.print("</value>");
               }
            }

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
      strictNull = "true".equalsIgnoreCase(Tool.getAttribute(tag, "strictNull"));
      NodeList list = Tool.getChildNodesByTagName(tag, "namedGroup");

      for(int i = 0; i < list.getLength(); i++) {
         Element tag2 = (Element) list.item(i);
         String name = Tool.getValue(tag2);

         if(name == null) {
            continue;
         }

         NodeList vnodes = Tool.getChildNodesByTagName(tag2, "value");
         ArrayList value = new ArrayList();

         for(int j = 0; j < vnodes.getLength(); j++) {
            Element vnode = (Element) vnodes.item(j);
            String val = getPersistentData(Tool.getValue(vnode));

            if(BLANKSTRING.equals(val)) {
               val = "";
            }

            if(this instanceof DCNamedGroupInfo && !Tool.isEmptyString(val) &&
               !DCNamedGroupInfo.SEPARATOR.equals(val))
            {
               value.add(Tool.parseDate(val));
            }
            else {
               value.add(val);
            }
         }

         setGroupValue(name, value);
      }
   }

   public String getPersistentData(String val) {
      return strictNull ? CoreTool.FAKE_NULL.equals(val) ? null : val : val;
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
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof SNamedGroupInfo)) {
         return false;
      }

      SNamedGroupInfo tinfo = (SNamedGroupInfo) obj;

      return Tool.equals(ref, tinfo.ref) && Tool.equals(values, tinfo.values) &&
         Tool.equals(groups, tinfo.groups);
   }

   public String toString() {
      return "SNamedGroupInfo[" + groups + ": " + values + "]";
   }

   protected Hashtable<String, List> values = new Hashtable();
   private Vector<String> groups = new Vector();
   private DataRef ref = new AttributeRef();
   private final String BLANKSTRING = "__blank__";
   private boolean strictNull = true; // for bc
   private static final Logger LOG = LoggerFactory.getLogger(SNamedGroupInfo.class);
}
