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
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Ranking condition, stores ranking information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RankingCondition extends AbstractCondition implements AssetObject {
   /**
    * Constructor.
    */
   public RankingCondition() {
      super();
      type = XSchema.INTEGER;
      op = TOP_N;
      n = 0;
   }

   /**
    * Check if type is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isTypeChangeable() {
      return false;
   }

   /**
    * Set the comparison operation of this condition.
    * @param op one of the operation constants defined in this class.
    */
   @Override
   public void setOperation(int op) {
      if(op != TOP_N && op != BOTTOM_N) {
         throw new RuntimeException("Only topn or bottomn is allowed!");
      }

      super.setOperation(op);
   }

   /**
    * Check if operation is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isOperationChangeable() {
      return true;
   }

   /**
    * Check if equal is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEqualChangeable() {
      return false;
   }

   /**
    * Check if negated is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNegatedChangeable() {
      return false;
   }

   /**
    * Get the n option.
    * @return the n option.
    */
   public Object getN() {
      return n;
   }

   /**
    * Set the n option.
    * @param n the specied n.
    */
   public boolean setN(Object n) {
      if(n instanceof Integer) {
         this.n = n;
         return true;
      }

      if(n instanceof String) {
         String str = (String) n;

         if(Condition.isVariable(str)) {
            this.n = n;
            return true;
         }

         try {
            this.n = Integer.parseInt(str);
            return true;
         }
         catch(Exception ex) {
            return false;
         }
      }
      else if(n instanceof UserVariable) {
         this.n = n;
         return true;
      }

      return false;
   }

   /**
    * Check whether to group or discard other values.
    */
   public boolean isGroupOthers() {
      return others;
   }

   /**
    * Set whether to group or discard other values.
    */
   public void setGroupOthers(boolean others) {
      this.others = others;
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariable(VariableTable vars) {
      String name = null;

      if(n instanceof UserVariable) {
         UserVariable var = (UserVariable) n;
         name = var.getName();
      }
      else if(Condition.isVariable(n)) {
         name = Condition.getRawValueString(n);
         name = name.substring(2, name.length() - 1);
      }

      if(name != null) {
         try {
            Object userv = vars.get(name);

            if(userv instanceof Object[]) {
               Object[] arr = (Object[]) userv;

               if(arr.length > 0) {
                  userv = arr[0];
               }
            }

            if(userv instanceof Number) {
               n = ((Number) userv).intValue();
            }
            else if(userv instanceof String) {
               n = Integer.valueOf((String) userv);
            }
         }
         catch(Exception ex) {
            LOG.debug("Failed to parse variable value: " + name, ex);
         }
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      if(n instanceof UserVariable) {
         UserVariable uvar = (UserVariable) n;

         if(VariableTable.isBuiltinVariable(uvar.getName())) {
            return new UserVariable[0];
         }

         XValueNode vnode = XValueNode.createValueNode(AbstractCondition.
            createDefaultValue(uvar.getTypeNode().getType()), uvar.getName());

         vnode.setVariable(uvar.getName());
         uvar.setValueNode(vnode);

         return new UserVariable[] {uvar};
      }
      else if(Condition.isVariable(n)) {
         String name = Condition.getRawValueString(n);
         name = name.substring(2, name.length() - 1);
         UserVariable uvar = new UserVariable();
         XValueNode vnode = XValueNode.createValueNode(
            AbstractCondition.createDefaultValue(getType()), name);

         if(VariableTable.isBuiltinVariable(name)) {
            return new UserVariable[0];
         }

         uvar.setName(name);
         uvar.setAlias(name);
         uvar.setTypeNode(XSchema.createPrimitiveType(getType()));
         uvar.setValueNode(vnode);
         vnode.setVariable(uvar.getName());

         return new UserVariable[] {uvar};
      }

      return new UserVariable[0];
   }

   /**
    * Evaluate this condition against the specified value object.
    * @param value the value object this condition should be compared with.
    * @return <code>true</code> if the value object meets this condition.
    */
   @Override
   public boolean evaluate(Object value) {
      // do nothing, will be evaluated by table filters
      return true;
   }

   /**
    * Check if the condition is a valid condition.
    * @return true if is valid, false otherwise.
    */
   @Override
   public boolean isValid() {
      return isValid(null);
   }

   /**
    * Check if the condition is a valid condition.
    * @return true if is valid, false otherwise.
    */
   public boolean isValid(ColumnSelection columns) {
      // for calculated field, the name won't be qualified with table name in columns, but
      // ranking condition ref may have the full name
      if(ref != null && columns != null && columns.getAttribute(ref.getName(), true) == null) {
         return false;
      }

      return true;
   }

   /**
    * Get the data ref.
    * @return the data ref.
    */
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the data ref.
    * @param ref the specified data ref.
    */
   public void setDataRef(DataRef ref) {
      this.ref = ref;
   }

   /**
    * Write the contents.
    * @param writer the specified print writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      if(n instanceof UserVariable) {
         UserVariable var = (UserVariable)n;
         writer.print("<condition_data isvariable=\"true\" name=\"" +
                        Tool.escape(var.getName()) + "\"");

         if(var.getChoiceQuery() != null) {
            writer.print(" fieldname=\"" + var.getChoiceQuery() + "\"");
         }

         writer.println("/>");
      }
      else if(Condition.isVariable(n)) {
         String val = Condition.getRawValueString(n);
         String name = val.substring(2, val.length() - 1);

         writer.println("<condition_data isvariable=\"true\" name=\"" +
            Tool.escape(name) + "\">" + "</condition_data>");
      }
      else {
         writer.println("<condition_data><![CDATA[" +
            AbstractCondition.getValueString(n) + "]]></condition_data>");
      }

      if(ref != null) {
         writer.println("<aggregate>");
         ref.writeXML(writer);
         writer.println("</aggregate>");
      }
   }

   /**
    * Parse the contents.
    * @param elem the specified xml element.
    */
   @Override
   public void parseContents(Element elem) throws Exception {
      Element cnode = Tool.getChildNodeByTagName(elem, "condition_data");
      String str;

      if((str = Tool.getAttribute(cnode, "isvariable")) != null &&
         str.equals("true") &&
         (str = Tool.getAttribute(cnode, "name")) != null)
      {
         UserVariable var = new UserVariable(str);
         var.setAlias(str);
         var.setTypeNode(new XTypeNode(getType()));
         var.setChoiceQuery(Tool.getAttribute(cnode, "fieldname"));

         n = var;
      }
      else {
         String val = Tool.getValue(cnode);
         n = AbstractCondition.getObject(getType(), val);
      }

      Element anode = Tool.getChildNodeByTagName(elem, "aggregate");

      if(anode != null) {
         anode = Tool.getFirstChildNode(anode);
         this.ref = AbstractDataRef.createDataRef(anode);
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
      writer.print(n);
      writer.print(others);

      if(ref != null) {
         writer.print(",");
         ConditionUtil.printDataRefKey(ref, writer);
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equqls another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof RankingCondition)) {
         return false;
      }

      RankingCondition condition = (RankingCondition) obj;
      return Tool.equals(n, condition.n) && op == condition.op &&
         ConditionUtil.equalsDataRef(ref, condition.ref);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), n, ref, others);
   }

   /**
    * Get a textual representation of this object.
    * @return a <code>String</code> containing a textual representation of this
    * object.
    */
   public String toString() {
      StringBuilder strbuff = new StringBuilder();
      Catalog catalog = Catalog.getCatalog();
      strbuff.append(" [");

      if(negated) {
         strbuff.append(catalog.getString("is not"));
      }
      else {
         strbuff.append(catalog.getString("is"));
      }

      strbuff.append("]");
      strbuff.append(" [");

      if(op == TOP_N) {
         strbuff.append(catalog.getString("top"));
      }
      else if(op == BOTTOM_N) {
         strbuff.append(catalog.getString("bottom"));
      }

      strbuff.append("]");
      strbuff.append(" [");

      if(getN() instanceof UserVariable) {
         strbuff.append("$(").append(((UserVariable) getN()).getName()).append(")");
      }
      else {
         strbuff.append(getN().toString());
      }

      strbuff.append("]");

      if(getDataRef() != null) {
         strbuff.append("[");
         strbuff.append(catalog.getString("of"));
         strbuff.append("]");
         strbuff.append("[");
         strbuff.append(getDataRef().toString());
         strbuff.append("]");
      }

      return strbuff.toString();
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public RankingCondition clone() {
      try {
         RankingCondition rcond = (RankingCondition) super.clone();
         rcond.ref = ref == null ? null : (DataRef) ref.clone();
         return rcond;
      }
      catch(Exception ex) {
         return null;
      }
   }

   private Object n;
   private DataRef ref;
   private boolean others = false;

   private static final Logger LOG = LoggerFactory.getLogger(RankingCondition.class);
}
