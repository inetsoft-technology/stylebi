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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * Asset condition extends <tt>Condition</tt> to support sub query value.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AssetCondition extends Condition implements AssetObject {
   /**
    * Constructor.
    */
   public AssetCondition() {
      super();

      lvalue = Tool.NULL;
   }

   /**
    * Constructor.
    */
   public AssetCondition(String type) {
      super(type);

      lvalue = Tool.NULL;
   }

   /**
    * Update the condition.
    * @param ws the associated worksheet.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean update(Worksheet ws) {
      for(int i = 0; i < getValueCount(); i++) {
         Object val = getValue(i);

         if(val instanceof SubQueryValue) {
            SubQueryValue sub = (SubQueryValue) val;

            if(!sub.update(ws)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Get the assemblies depended on.
    */
   public void getDependeds(Set<AssemblyRef> set) {
      for(int i = 0; i < getValueCount(); i++) {
         Object val = getValue(i);

         if(val instanceof SubQueryValue) {
            SubQueryValue sub = (SubQueryValue) val;
            String name = sub.getQuery();
            int type = Worksheet.TABLE_ASSET;
            set.add(new AssemblyRef(new AssemblyEntry(name, type)));
         }
      }
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    * @param ws the specified worksheet.
    */
   public void renameDepended(String oname, String nname, Worksheet ws) {
      for(int i = 0; i < getValueCount(); i++) {
         Object val = getValue(i);

         if(val instanceof SubQueryValue) {
            SubQueryValue sub = (SubQueryValue) val;
            sub.renameDepended(oname, nname, ws);
         }
      }
   }

   /**
    * Replace all embeded user variables with value from variable table.
    */
   @Override
   public void replaceVariable(VariableTable vart) {
      setIgnored(false);

      for(int i = 0; vart != null && i < getValueCount(); i++) {
         Object val = getValue(i);

         if(val instanceof UserVariable) {
            UserVariable var = (UserVariable) val;
            String name = var.getName();
            Object userv = null;

            try {
               userv = vart.get(name);

               if(userv != null) {
                  setDynamicValue(i, userv);
               }
               else {
                  if(vart.isNotIgnoredNull(name)) {
                     setValue(i, null);
                     setIgnoreNullValue(false);
                  }
                  else {
                     setIgnored(true);
                  }
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to set value of user variable " +
                  name + " to " + userv, ex);
            }
         }
         else if(isVariable(val)) {
            String name = Condition.getRawValueString(val);
            name = name.substring(2, name.length() - 1);
            Object userv = null;

            try {
               userv = vart.get(name);

               if(userv != null) {
                  setValue(i, userv);
               }
               else {
                  setIgnored(true);
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to set value of variable " + name + " to " + userv, ex);
            }
         }
         else if(val instanceof SubQueryValue) {
            SubQueryValue sub = (SubQueryValue) val;
            sub.replaceVariables(vart);
         }
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      List<UserVariable> v = new ArrayList<>();

      for(int i = 0; i < getValueCount(); i++) {
         Object val = getValue(i);
         UserVariable uvar = null;

         if(val instanceof String && isVariable(val)) {
            String name = (String) val;
            name = name.substring(2, name.length() - 1);
            uvar = new UserVariable();

            if(VariableTable.isBuiltinVariable(name)) {
               continue;
            }

            uvar.setName(name);
            uvar.setAlias(name);
            uvar.setTypeNode(XSchema.createPrimitiveType(type));

            if(!v.contains(uvar)) {
               v.add(uvar);
            }
         }
         else if(val instanceof UserVariable) {
            uvar = (UserVariable) val;
            uvar.setUsedInOneOf(getOperation() == XCondition.ONE_OF);

            if(VariableTable.isBuiltinVariable(uvar.getName())) {
               continue;
            }

            if(!v.contains(uvar)) {
               v.add(uvar);
            }
         }
         else if(val instanceof SubQueryValue) {
            SubQueryValue sub = (SubQueryValue) val;
            UserVariable[] svars = sub.getAllVariables();

            for(int j = 0; j < svars.length; j++) {
               if(!v.contains(svars[j])) {
                  v.add(svars[j]);
               }
            }
         }
         else if(val instanceof ExpressionValue) {
            ExpressionValue eval = (ExpressionValue) val;
            String exp = eval.getExpression();
            String name = null;

            if(eval.getType().equals(ExpressionValue.SQL)) {
               int idx1 = exp.indexOf("$(");
               int idx2 = exp.indexOf(')');

               if(idx1 >= 0 && idx2 > idx1 + 2) {
                  name = exp.substring(idx1 + 2, idx2);
               }
            }
            else {
               int idx1 = exp.indexOf("parameter.");

               if(idx1 >= 0) {
                  name = exp.substring(idx1 + 10);
                  int j = 0;

                  for(; j < name.length(); j++) {
                     char c = name.charAt(j);

                     if(!Character.isLetterOrDigit(c) && c != 95) {
                        break;
                     }
                  }

                  name = name.substring(0, j);
               }
            }

            if(name != null) {
               uvar = new UserVariable();
               uvar.setName(name);
               uvar.setAlias(name);

               if(op == DATE_IN) {
                  DateCondition[] dateConditions = DateCondition.parseBuiltinDateConditions();

                  if(dateConditions != null) {
                     String[] lables = new String[dateConditions.length];

                     for(int k = 0; k < dateConditions.length; k++) {
                        lables[k] = dateConditions[k].getName();
                     }

                     uvar.setValues(lables);
                     uvar.setChoices(lables);
                  }

                  type = XSchema.STRING;
               }

               uvar.setTypeNode(XSchema.createPrimitiveType(type));

               if(!v.contains(uvar)) {
                  v.add(uvar);
               }
            }
         }

         if(op == ONE_OF && uvar != null) {
            uvar.setMultipleSelection(true);
         }
      }

      return v.toArray(new UserVariable[0]);
   }

   /**
    * Set the condition value object as xml.
    */
   @Override
   protected void writeConditionValue(PrintWriter writer, Object val) {
      if(val instanceof SubQueryValue) {
         SubQueryValue sub = (SubQueryValue) val;
         writer.println("<condition_data subQuery=\"true\">");
         sub.writeXML(writer);
         writer.println("</condition_data>");
      }
      else if(val instanceof ExpressionValue) {
         ExpressionValue expression = (ExpressionValue) val;
         writer.println("<condition_data expression=\"true\">");
         expression.writeXML(writer);
         writer.println("</condition_data>");
      }
      else if(val instanceof UserVariable) {
         UserVariable var = (UserVariable) val;
         writer.print("<condition_data isvariable=\"true\" name=\"" +
            Tool.escape(var.getName()) + "\"" + " prompt=\"" +
            Tool.getBooleanData(var.isPrompt()) + "\"" + " sortValue=\"" +
            Tool.getBooleanData(var.isSortValue()) + "\""  + " label=\"" +
            Tool.escape(var.getAlias()) + "\"" + " toolTip=\"" +
            Tool.escape(var.getToolTip()) + "\""
         );

         if(var.getValueNode() != null && var.getValueNode().getValue() != null)
         {
            writer.print(" value=\"" +
               Tool.escape(var.getValueNode().getValue().toString()) + "\"");
         }

         if(var.getTypeNode() != null && var.getTypeNode().getType() != null) {
            writer.print(" xtype=\"" +
               Tool.escape(var.getTypeNode().getType().toString()) + "\"");
         }

         if(var.getChoiceQuery() != null) {
            writer.print(" fieldname=\"" + Tool.escape(var.getChoiceQuery()) +
                         "\"");
         }

         writer.println("/>");
      }
      else {
         super.writeConditionValue(writer, val);
      }
   }

   /**
    * Parse the condition value.
    */
   @Override
   protected Object parseConditionValue(Element atag) throws Exception {
      String str;

      if((str = Tool.getAttribute(atag, "subQuery")) != null &&
         str.equals("true"))
      {
         Element snode = Tool.getChildNodeByTagName(atag, "subQueryValue");
         SubQueryValue val = new SubQueryValue();
         val.parseXML(snode);

         return val;
      }
      else if((str = Tool.getAttribute(atag, "expression")) != null &&
         str.equals("true"))
      {
         ExpressionValue val = new ExpressionValue();
         val.parseXML(Tool.getChildNodeByTagName(atag, "expressionValue"));

         return val;
      }
      else if((str = Tool.getAttribute(atag, "isvariable")) != null &&
         str.equals("true"))
      {
         String name = Tool.getAttribute(atag, "name");
         UserVariable var = new UserVariable(name);

         String alias = Tool.getAttribute(atag, "label");
         var.setAlias(alias != null && !alias.equals("") ? alias : name);
         var.setChoiceQuery(Tool.getAttribute(atag, "fieldname"));
         var.setToolTip(Tool.getAttribute(atag, "toolTip"));
         str = Tool.getAttribute(atag, "prompt");

         if(str != null && str.equals("false")) {
            var.setPrompt(false);
         }

         str = Tool.getAttribute(atag, "sortValue");

         if(str != null && str.equals("false")) {
           var.setSortValue(false);
         }

         str = Tool.getAttribute(atag, "value");

         if(str != null && !str.equals("")) {
            var.setValueNode(XValueNode.createValueNode((Object)str,
               "default"));
         }

         str = Tool.getAttribute(atag, "xtype");

         if(str != null && !str.equals("")) {
            var.setTypeNode(new XTypeNode(str));
         }

         return var;
      }
      else {
         return super.parseConditionValue(atag);
      }
   }

   /**
    * Initialize the runtime environment.
    */
   public void init() throws Exception {
      if(getValueCount() == 1 && (getValue(0) instanceof SubQueryValue)) {
         sub = (SubQueryValue) getValue(0);
         sub = (SubQueryValue) sub.clone();
      }
   }

   /**
    * Initialize the main table runtime environment.
    * @param mtable the specified main table.
    */
   public void initMainTable(XTable mtable) throws Exception {
      initMainTable(mtable, -1);
   }

   /**
    * Initialize the main table runtime environment.
    * @param mtable the specified main table.
    * @param mcol the specified main attribute column index.
    */
   public void initMainTable(XTable mtable, int mcol) throws Exception {
      if(sub == null) {
         init();
      }

      if(sub != null) {
         if(sub.isCorrelated()) {
            lvalue = Tool.NULL;
         }

         sub.initMainTable(mtable, mcol);
      }
   }

   /**
    * Initialize the sub table runtime environment.
    * @param stable the specified sub table.
    */
   public void initSubTable(XTable stable) throws Exception {
      if(sub == null) {
         init();
      }

      if(sub != null) {
         sub.initSubTable(stable);
      }
   }

   /**
    * Get the main attribute.
    * @return the main attribute.
    */
   public DataRef getMainAttribute() {
      return sub == null ? null : sub.getMainAttribute();
   }

   /**
    * Get the values of a row.
    * @return the values of the row.
    */
   @Override
   public List<Object> getValues() {
      return sub == null ? super.getValues() : sub.getValues();
   }

   /**
    * Set current row.
    * @param row the specified row index.
    */
   public void setCurrentRow(int row) {
      if(sub != null) {
         if(sub.isCorrelated()) {
            lvalue = Tool.NULL;
         }

         sub.setCurrentRow(row);
      }
   }

   /**
    * Get the sub query value if any.
    * @return the contained sub query value if any.
    */
   public SubQueryValue getSubQueryValue() {
      // at present, we do not support between and in
      if(getValueCount() != 1) {
         return null;
      }

      Object obj = getValue(0);

      if(!(obj instanceof SubQueryValue)) {
         return null;
      }

      return (SubQueryValue) obj;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      super.printKey(writer);
      SubQueryValue val = getSubQueryValue();

      if(val != null) {
         writer.print("[");

         if(!val.printKey(writer)) {
            return false;
         }

         writer.print("]");
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
      // contains and one-of are very time-consuming processes,
      // let's try last result of last value first
      if(isOptimized() &&
         (getOperation() == ONE_OF || getOperation() == CONTAINS) &&
         Tool.equals(value, lvalue))
      {
         return lresult;
      }

      lvalue = value;

      if(sub != null) {
         clearCache();
      }

      lresult = super.evaluate(value);

      return lresult;
   }

   /**
    * Reset the cached vaule.
    */
   public void reset() {
      lvalue = Tool.NULL;

      if(sub != null) {
         sub.reset();
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), sub, lvalue, lresult);
   }

   private transient SubQueryValue sub;
   private transient Object lvalue;
   private transient boolean lresult;

   private static final Logger LOG = LoggerFactory.getLogger(AssetCondition.class);
}
