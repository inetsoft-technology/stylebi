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
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * Date range contains one or more <tt>DateCondition</tt>s.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DateRange extends DateCondition {
   /**
    * Constructor.
    */
   public DateRange() {
      super();
      conditions = new ArrayList<>();
   }

   /**
    * Add one date condition.
    * @param condition the specified date condition.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addDateCondition(XCondition condition) {
      if(conditions.contains(condition)) {
         return false;
      }

      conditions.add(condition);
      return true;
   }

   /**
    * Remove one date condition.
    * @param condition the specified date condition.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeDateCondition(XCondition condition) {
      if(!conditions.contains(condition)) {
         return false;
      }

      conditions.remove(condition);
      return true;
   }

   /**
    * Get all the date conditions.
    * @return all the date conditions.
    */
   public XCondition[] getDateConditions() {
      XCondition[] arr = new XCondition[conditions.size()];
      conditions.toArray(arr);
      return arr;
   }

   /**
    * Clear the date range.
    */
   public void clear() {
      conditions.clear();
   }

   /**
    * Check if the condition is a valid condition.
    * @return true if is valid, false otherwise.
    */
   @Override
   public boolean isValid() {
      if(conditions.size() == 0) {
         return false;
      }

      for(XCondition condition : conditions) {
         if(!condition.isValid()) {
            return false;
         }
      }

      return true;
   }

   /**
    * Evaluate this condition against the specified value object.
    * @param value the value object this condition should be compared with.
    * @return <code>true</code> if the value object meets this condition.
    */
   @Override
   public boolean evaluate(Object value) {
      boolean rc = false;

      // any condition is true?
      for(XCondition condition : conditions) {
         if(condition.evaluate(value)) {
            rc = true;
            break;
         }
      }

      return isNegated() ? !rc : rc;
   }

   /**
    * Convert this condition to sql mergeable condition.
    */
   @Override
   public Condition toSqlCondition(boolean isTimestamp) {
      // do not support
      return new Condition();
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariable(VariableTable vars) {
      for(XCondition condition : conditions) {
         condition.replaceVariable(vars);
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();

      for(XCondition condition : conditions) {
         UserVariable[] vars = condition.getAllVariables();

         for(UserVariable var : vars) {
            if(!list.contains(var)) {
               list.add(var);
            }
         }
      }

      UserVariable[] arr = new UserVariable[list.size()];
      list.toArray(arr);
      return arr;
   }

   /**
    * Write the contents.
    * @param writer the specified print writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.println("<dateConditions>");

      for(XCondition condition : conditions) {
         writer.println("<oneDateCondition>");
         condition.writeXML(writer);
         writer.println("</oneDateCondition>");
      }

      writer.println("</dateConditions>");
   }

   /**
    * Parse the contents.
    * @param elem the specified xml element.
    */
   @Override
   public void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element nnode = Tool.getChildNodeByTagName(elem, "dateConditions");
      NodeList list = Tool.getChildNodesByTagName(nnode, "oneDateCondition");

      for(int i = 0; i < list.getLength(); i++) {
         Element cnode = (Element) list.item(i);
         cnode = Tool.getFirstChildNode(cnode);
         XCondition condition =
            AbstractCondition.createXCondition(cnode);
         conditions.add(condition);
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two
    * content objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      super.printKey(writer);
      writer.print("[");
      int cnt = conditions.size();

      for(int i = 0; i < cnt; i++) {
         if(i > 0) {
            writer.print(",");
         }

         XCondition cond = conditions.get(i);
         cond.printKey(writer);
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof DateRange)) {
         return false;
      }

      DateRange range = (DateRange) obj;
      return conditions.equals(range.conditions);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), conditions);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public DateRange clone() {
      try {
         DateRange range = (DateRange) super.clone();
         range.conditions = Tool.deepCloneCollection(conditions);
         return range;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Get the label.
    * @return the label of the date condition.
    */
   @Override
   public String getLabel() {
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < conditions.size(); i++) {
         if(i > 0) {
            sb.append(", ");
         }

         XCondition cond = conditions.get(i);

         if(cond instanceof DateCondition) {
            sb.append(((DateCondition) cond).getLabel());
         }
         else {
            sb.append(cond.toString());
         }
      }

      return sb.toString();
   }

   private ArrayList<XCondition> conditions;

   private static final Logger LOG = LoggerFactory.getLogger(DateRange.class);
}
