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
package inetsoft.uql;

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.HierarchyListModel;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

/**
 * A ConditionList stores a list of conditions/junctions to be applied to
 * the resulting data.
 *
 * @version 5.1, 9/20/2003
 * @since 5.0
 * @author InetSoft Technology Corp
 */
public class ConditionList extends HierarchyList implements Cloneable, Serializable {
   /**
    * Constructor.
    */
   public ConditionList() {
      super();
   }

   /**
    * Get the ConditionItem at the specified index.
    *
    * @param index the specified index.
    * @return the ConditionItem at the specified index.
    */
   @Override
   public ConditionItem getConditionItem(int index) {
      if(isConditionItem(index)) {
         return (ConditionItem) list.elementAt(index);
      }
      else {
         return null;
      }
   }

   /**
    * Get the ConditionItem for the specified ref.
    *
    * @param ref data ref.
    * @return the ConditionItem for the specified ref.
    */
   public ConditionItem getConditionItem(DataRef ref) {
      return processForeach(ref, (i) -> (ConditionItem) list.elementAt(i.intValue()));
   }

   /**
    * Remove By Ref.
    */
   public void remove(DataRef ref) {
      remove(ref, true);
   }

   public void remove(DataRef ref, boolean removeAll) {
      for(int i = getSize() - 1; i >= 0; i--) {
         if(isConditionItem(i)) {
            DataRef ref2 = getAttribute(i);

            if(ref2.equals(ref)) {
               removeConditionItem(i);

               if(!removeAll) {
                  return;
               }
            }
         }
      }
   }

   public ConditionItem removeConditionItem(int i) {
      int removeIndex = i;

      if(i == 0 && isJunctionOperator(i + 1)) {
         list.remove(i + 1);
      }
      else if(isJunctionOperator(i - 1)) {
         list.remove(i - 1);
         removeIndex = i - 1;
      }

      return (ConditionItem) list.remove(removeIndex);
   }

   private ConditionItem processForeach(DataRef ref, Function<Integer, ConditionItem> func) {
      for(int i = getSize() - 1; i >= 0; i--) {
         if(isConditionItem(i)) {
            DataRef ref2 = getAttribute(i);

            if(ref2.equals(ref)) {
               return func.apply(i);
            }
         }
      }

      return null;
   }

   /**
    * Get the JunctionOperator at the specified index.
    *
    * @param index the specified index.
    * @return the JunctionOperator at the specified index.
    */
   @Override
   public JunctionOperator getJunctionOperator(int index) {
      if(isJunctionOperator(index)) {
         return (JunctionOperator) list.elementAt(index);
      }
      else {
         return null;
      }
   }

   /**
    * Get the Condition at the specified index.
    *
    * @param index the specified index.
    * @return the Condition at the specified index.
    * @deprecated replaced by getXCondition.
    */
   @Deprecated
   public Condition getCondition(int index) {
      if(isConditionItem(index)) {
         return getConditionItem(index).getCondition();
      }
      else {
         return null;
      }
   }

   /**
    * Get the XCondition at the specified index.
    *
    * @param index the specified index.
    * @return the Condition at the specified index.
    */
   public XCondition getXCondition(int index) {
      if(isConditionItem(index)) {
         return getConditionItem(index).getXCondition();
      }
      else {
         return null;
      }
   }

   /**
    * Get the DataRef at the specified index.
    *
    * @param index the specified index.
    * @return the DataRef at the specified index.
    */
   public DataRef getAttribute(int index) {
      if(isConditionItem(index)) {
         return getConditionItem(index).getAttribute();
      }
      else {
         return null;
      }
   }

   /**
    * Get the junction at the specified index.
    *
    * @param index the specified index
    * @return the junction at the specified index
    */
   public int getJunction(int index) {
      if(isJunctionOperator(index)) {
         return getJunctionOperator(index).getJunction();
      }
      else {
         return JunctionOperator.AND;
      }
   }

   /**
    * Replace the ConditionItem at the specified index.
    *
    * @param index the index of the ConditionItem.
    * @param attribute the new DataRef.
    * @param condition the new Condition.
    */
   public void setConditionItem(int index, DataRef attribute,
                                XCondition condition) {
      if(isConditionItem(index)) {
         ConditionItem item = getConditionItem(index);

         item.setAttribute(attribute);
         item.setXCondition(condition);
      }
   }

   /**
    * Replace the JunctionOperator at the specified index.
    *
    * @param index the index of the JunctionOperator.
    * @param junction the new junction.
    */
   public void setJunctionOperator(int index, int junction) {
      if(isJunctionOperator(index)) {
         JunctionOperator item = getJunctionOperator(index);

         item.setJunction(junction);
      }
   }

   /**
    * Get the contained ConditionList.
    *
    * @return the contained CondtiionList.
    */
   @Override
   public ConditionList getConditionList() {
      return this;
   }

   /**
    * Get all variables of the list.
    *
    * @return the UserVariable array.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> vars = new ArrayList<>();

      for(int i = 0; i < getSize(); i += 2) {
         XCondition cond = getXCondition(i);

         if(cond != null) {
            Collections.addAll(vars, cond.getAllVariables());
         }
      }

      return vars.toArray(new UserVariable[0]);
   }

   /**
    * Replace all variables in named group if any.
    *
    * @param vart the VariableTable.
    */
   @Override
   public void replaceVariables(VariableTable vart) {
      for(int i = 0; i < getSize(); i += 2) {
         XCondition cond = getXCondition(i);

         if(cond != null) {
            cond.replaceVariable(vart);
         }
      }
   }

   /**
    * Write to the XML.
    *
    * @param writer the PrintWriter.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<conditions>");

      for(int i = 0; i < getSize(); i++) {
         Object item = list.elementAt(i);

         if(i % 2 == 0) {
            ((ConditionItem) item).writeXML(writer);
         }
         else {
            ((JunctionOperator) item).writeXML(writer);
         }
      }

      writer.println("</conditions>");
   }

   /**
    * Read in the XML representation of this object in 5.0.
    *
    * @param tag the XML element representing this object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList list = tag.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         if(!(list.item(i) instanceof Element)) {
            continue;
         }

         Element tag2 = (Element) list.item(i);

         if(tag2.getTagName().equals("condition")) {
            ConditionItem item = new ConditionItem();

            item.parseXML(tag2);
            append(item);
         }

         if(tag2.getTagName().equals("junction")) {
            JunctionOperator junc = new JunctionOperator();

            junc.parseXML(tag2);
            append(junc);
         }
      }
   }

   /**
    * To string.
    *
    * @return the string value.
    */
   public String toString() {
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < getSize(); i++) {
         if(i > 0) {
            buf.append("\n");
         }

         buf.append(list.get(i).toString());
      }

      return buf.toString();
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof ConditionList)) {
         return false;
      }

      ConditionList list2 = (ConditionList) obj;

      if(getSize() != list2.getSize()) {
         return false;
      }

      for(int i = 0; i < getSize(); i++) {
         HierarchyItem item = getItem(i);
         HierarchyItem item2 = list2.getItem(i);

         if(!item.equals(item2)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Clone it.
    *
    * @return the cloned object.
    */
   @Override
   public ConditionList clone() {
      ConditionList sel = new ConditionList();
      sel.list = cloneConditionList(list);

      return sel;
   }

   /**
    * Clone condition list.
    */
   private final Vector cloneConditionList(Vector list) {
      int size = list.size();
      Vector list2 = new Vector(size);

      for(int i = 0; i < size; i++) {
         Object obj = ((HierarchyItem) list.get(i)).clone();
         list2.add(obj);
      }

      return list2;
   }

   /**
    * Validate the condition list.
    * @param columns the specified column selection.
    */
   public synchronized void validate(ColumnSelection columns) {
      HierarchyListModel model = new HierarchyListModel(this);

      for(int i = model.getSize() - 1; i >= 0; i--) {
         if(model.isConditionItem(i)) {
            ConditionItem item = (ConditionItem) model.getElementAt(i);
            DataRef attr = item.getAttribute();

            if(!columns.containsAttribute(attr)) {
               model.removeConditionItem(i);
            }
         }
      }

      model.fixConditions();
   }

   /**
    * Negate the condition list by changing all operators and negating each
    * condition.
    */
   public void negate() {
      for(int i = 0; i < getSize(); i++) {
         if(i % 2 == 0) {
            XCondition cond =
               ((ConditionItem) list.elementAt(i)).getXCondition();
            cond.setNegated(!cond.isNegated());
         }
         else {
            JunctionOperator op = (JunctionOperator) list.elementAt(i);
            op.setJunction(op.getJunction() == JunctionOperator.AND ?
                           JunctionOperator.OR : JunctionOperator.AND);
         }
      }
   }

   public static final int ROOT_LEVEL = 0;
}
