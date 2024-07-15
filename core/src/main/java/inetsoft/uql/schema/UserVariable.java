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
package inetsoft.uql.schema;

import inetsoft.uql.AbstractCondition;
import inetsoft.uql.VariableTable;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * An user variable is a variable with a scalar value. The value can
 * either by specified or enter by users at runtime.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class UserVariable extends XVariable {
   /**
    * Display as a text field.
    */
   public static final int NONE = 0;
   /**
    * Display as a combobox.
    */
   public static final int COMBOBOX = 1;
   /**
    * Display as a list.
    */
   public static final int LIST = 2;
   /**
    * Display as radio buttons.
    */
   public static final int RADIO_BUTTONS = 3;
   /**
    * Diplay as checkboxes.
    */
   public static final int CHECKBOXES = 4;
   /**
    * Display as date combobox.
    */
   public static final int DATE_COMBOBOX = 5;

   /**
    * Create an user variable.
    */
   public UserVariable() {
      super();
   }

   /**
    * Create an user variable.
    */
   public UserVariable(String name) {
      this();
      setName(name);
      setAlias(name);
      setToolTip(toolTip);
   }

   /**
    * Set whether to prompt users for the value.
    * @param prompt true if the value should be entered by users at
    * runtime.
    */
   public void setPrompt(boolean prompt) {
      this.prompt = prompt;
   }

   /**
    * Check is the value of the variable needs to be enter by users.
    */
   public boolean isPrompt() {
      return prompt;
   }

   /**
    * Set whether to prompt users for the value.
    * @param embedded true if it is an embeded variable from bean or subreport.
    */
   public void setEmbedded(boolean embedded) {
      this.embedded = embedded;
   }

   /**
    * Check if it is an embeded variable from bean or subreport.
    */
   public boolean isEmbedded() {
      return embedded;
   }

   /**
    * Get the display style.
    * @return the display style.
    */
   public int getDisplayStyle() {

      if((choices != null && values != null &&
          choices.length == values.length && values.length > 0) ||
         choiceQuery != null)
      {
         if(isMultipleSelection()) {
            return LIST;
         }
         else {
            return COMBOBOX;
         }
      }

      return NONE;
   }

   /**
    * Check whether this parameter is used as customization parameter of a
    * report.
    */
   public boolean isCustomization() {
      return customize;
   }

   /**
    * Set the label for the user field when prompting users.
    */
   public void setAlias(String label) {
      this.label = label;
   }

   /**
    * Get the variable label.
    */
   public String getAlias() {
      return label;
   }

   /**
    * Get the variable toolTip.
    */
   public String getToolTip() {
      return toolTip;
   }

   /**
    * Set the toolTip for the user field when prompting users.
    */
   public void setToolTip(String toolTip) {
      this.toolTip = toolTip;
   }


   /**
    * Set a list of items to choose the value from.
    */
   public void setChoices(Object[] choices) {
      this.choices = choices;
      sortValues();
   }

   /**
    * Get the list of choices.
    * @return null if choice is not set.
    */
   public Object[] getChoices() {
      return choices;
   }

   /**
    * Set a list of values to choose the value from.
    */
   public void setValues(Object[] values) {
      this.values = values;
      sortValues();
   }

   /**
    * Get the list of values.
    * @return null if value is not set.
    */
   public Object[] getValues() {
      return values;
   }

   public boolean isDataTruncated() {
      return dataTruncated;
   }

   public void setDataTruncated(boolean dataTruncated) {
      this.dataTruncated = dataTruncated;
   }

   /**
    * Set the value of sortValue.
    */
   public void setSortValue(boolean sortValue) {
      this.sortValue = sortValue;
   }

   /**
    * Get the value of sortValue.
    * @return true or false.
    */
   public boolean isSortValue() {
      return sortValue;
   }

   /**
    * sort the choices if the sortValue is true.
    */
   private void sortValues() {
      if(sortValue && choices != null && values != null &&
         values.length == choices.length )
      {
         Object[] choices2 = choices.clone();
         Object[] values2 = values.clone();
         boolean[] spareLabels = new boolean[choices.length];

         for(int i = 0; i < spareLabels.length; i++) {
            spareLabels[i] = true;
         }

         for(int i = 0; i < choices.length; i++) {
            if(choices[i] == null) {
               if(values[i] != null) {
                  choices[i] = values[i];
               }
               else {
                  choices[i] = "";
               }

               choices2[i] = choices[i];
            }
         }

         Arrays.sort(choices, Tool::compare);

         for(int i = 0; i < choices.length; i++) {
            for(int j = 0; j < choices2.length; j++) {
               if(spareLabels[j] && choices[i].equals(choices2[j])) {
                  values[i] = values2[j];
                  spareLabels[j] = false;
                  break;
               }
            }
         }
      }
   }

   /**
    * Set the value of this variable.
    */
   public void setValueNode(XValueNode value) {
      this.value = value;
   }

   /**
    * Get the value of this variable.
    */
   public XValueNode getValueNode() {
      return value;
   }

   /**
    * Set the type of this variable.
    * @param xtype value type, one of the value defined in XSchema.
    */
   public void setTypeNode(XTypeNode xtype) {
      this.xtype = xtype;

      // maintain value according to type
      if(this.xtype != null && this.value == null) {
         this.value = XValueNode.createValueNode(getName(), xtype.getType());
      }
   }

   /**
    * Get the variable value type.
    */
   public XTypeNode getTypeNode() {
      return xtype;
   }

   /**
    * Evaulate the XVariable.  Will return default value from ValueNode.
    */
   @Override
   public Object evaluate(VariableTable vars) {
      if(getValueNode() != null) {
         return getValueNode().getValue();
      }

      return null;
   }

   /**
    * Set if the contents of the variable should be hidden. This is only
    * checked for string types. If it's true, the value is hidden as a
    * password field.
    */
   public void setHidden(boolean hidden) {
      this.hidden = hidden;
   }

   /**
    * Check if the value should be hidden during data entry.
    */
   public boolean isHidden() {
      return hidden;
   }

   /**
    * Set whether to allow multiple selection.
    */
   public void setMultipleSelection(boolean multi) {
      this.multi = multi;
   }

   /**
    * Check if multiple selection is allowed.
    */
   public boolean isMultipleSelection() {
      return multi;
   }

   /**
    * Set the name of the query that will be executed to generate a list of
    * values for choices.
    * @param query the query name or column/attribute name.
    */
   public void setChoiceQuery(String query) {
      // The name can be the name of a query in the
      // repository, or a fully qualified name of a column or attribute.
      // The encoding is defined in BrowsedData class and is only used
      // internally
      choiceQuery = query;
   }

   /**
    * Get the name of the query that will be executed to generate a list of
    * values for choices.
    */
   public String getChoiceQuery() {
      return choiceQuery;
   }

   /**
    * Write the variable XML representation.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<variable ");
      writeAttributes(writer);
      writer.println(">");

      writeContents(writer);

      writer.println("</variable>");
   }

   /**
    * Write attributes.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" type=\"user\"");
      writer.print(" prompt=\"" + prompt + "\"");
      writer.print(" sortValue=\"" + sortValue + "\"");
      writer.print(" embedded=\"" + embedded + "\"");
      writer.print(" multipleSelection=\"" + multi + "\"");

      if(label != null) {
         writer.print(" label=\"" + Tool.escape(label) + "\"");
      }

      if(toolTip != null) {
         writer.print(" toolTip=\"" + Tool.escape(toolTip) + "\"");
      }

      if(xtype != null) {
         writer.print(" xtype=\"" + Tool.escape(xtype.getType()) + "\"");
      }
   }

   /**
    * Write contents.
    * @param writer the specified print writer.
    */
   protected void writeContents(PrintWriter writer) {
      if(getChoiceQuery() != null) {
         writer.print("<ChoiceQuery name=\"" + Tool.escape(getChoiceQuery()) +
            "\"></ChoiceQuery>\n");
      }

      if(choices != null && values != null && choices.length == values.length) {
         writer.println("<choices>");

         for(int i = 0; i < choices.length; i++) {
            writer.print("<choice><item>");

            if(choices[i] != null) {
               writer.print("<![CDATA[" + choices[i] + "]]>");
            }

            writer.print("</item><value>");

            if(values[i] != null) {
               writer.print("<![CDATA[" + values[i] + "]]>");
            }

            writer.println("</value></choice>");
         }

         writer.println("</choices>");
      }

      if(value != null) {
         value.writeXML(writer);

         if(value.getValue() != null) {
            writer.println("<valueString><![CDATA[" +
                           Tool.getDataString(value.getValue()) +
                           "]]></valueString>");

            writer.println("<valueString2><![CDATA[" +
                           AbstractCondition.getValueString(value.getValue()) +
                           "]]></valueString2>");
         }
      }
   }

   /**
    * Parse the XML element that contains information on this variable.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      parseAttributes(elem);
      parseContents(elem);
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) throws Exception {
      super.parseAttributes(elem);

      String str = Tool.getAttribute(elem, "prompt");
      prompt = str == null || str.equalsIgnoreCase("true");
      str = Tool.getAttribute(elem, "sortValue");
      sortValue = !("false".equals(str));
      str = Tool.getAttribute(elem, "embedded");
      embedded = str != null && str.equalsIgnoreCase("true");
      label = Tool.getAttribute(elem, "label");
      toolTip = Tool.getAttribute(elem, "toolTip");
      str = Tool.getAttribute(elem, "xtype");
      xtype = (str != null) ?
         XSchema.createPrimitiveType(str) :
         new StringType();
      str = Tool.getAttribute(elem, "multipleSelection");
      multi = str != null && str.equalsIgnoreCase("true");
      str = Tool.getAttribute(elem, "customization");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   protected void parseContents(Element elem) throws Exception {
      NodeList nlist = elem.getChildNodes();

      for(int i = 0; i < nlist.getLength(); i++) {
         Node node = nlist.item(i);

         if(!(node instanceof Element)) {
            continue;
         }

         Element telem = (Element) node;

         if(telem.getTagName().equals("choices")) {
            NodeList list2 = Tool.getChildNodesByTagName(telem, "choice");
            choices = new Object[list2.getLength()];
            values = new Object[list2.getLength()];

            for(int k = 0; k < list2.getLength(); k++) {
               Element e2 = (Element) list2.item(k);
               NodeList name = Tool.getChildNodesByTagName(e2, "item");
               NodeList value = Tool.getChildNodesByTagName(e2, "value");

               if(name.getLength() > 0 && value.getLength() > 0) {
                  choices[k] = Tool.getValue(name.item(0));
                  values[k] = Tool.getValue(value.item(0));
                  values[k] = Tool.getData(getTypeNode().getType(), values[k]);
               }
            }
         }
         else if(telem.getTagName().equals("ChoiceQuery")) {
            setChoiceQuery(telem.getAttribute("name"));
         }
         else if(telem.getTagName().equals("valuenode") ||
                 telem.getTagName().equals("value"))
         {
            value = XValueNode.createValueNode(telem);
         }
         else if(telem.getTagName().equals("valueString") && value == null) {
            String val = Tool.getValue(telem);
            Object valueString = Tool.getData(getTypeNode().getType(), val);
            value = XValueNode.createValueNode(valueString, "default");
         }
      }

      if(sortValue) {
         sortValues();
      }
   }

   /**
    * Returns a clone of this object.
    */
   @Override
   public UserVariable clone() {
      UserVariable xvar = (UserVariable) super.clone();

      xvar.xtype = xtype;
      xvar.value = value;
      xvar.choices = choices;
      xvar.values = values;
      xvar.embedded = embedded;

      return xvar;
   }

   /**
    * Check if equals another object.
    * @return true if equals, false otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof UserVariable)) {
         return false;
      }

      UserVariable var2 = (UserVariable) obj;

      // name not equal?
      if(!Tool.equals(getName(), var2.getName())) {
         return false;
      }

      // label not equal?
      if(!Tool.equals(label, var2.label)) {
         return false;
      }

      // toolTip not equal?
      if(!Tool.equals(toolTip, var2.toolTip)) {
         return false;
      }

      // value not equal?
      if(!Tool.equals(value, var2.value)) {
         if(value != null && var2.value != null &&
            Tool.equals(value.getName(), var2.value.getName()) &&
            Tool.equals(value.getValue(), var2.value.getValue()) &&
            (value.getVariable() == null ||
             value.getVariable().equals(getName())) &&
            (var2.value.getVariable() == null ||
             var2.value.getVariable().equals(getName())))
         {
            // @by larryl, ignore variable setting since the value node is
            // already in a variable. UserVariable created from binding has
            // value node set to $(var), and variable created from gui
            // use value node to hold the actual values.
         }
         else {
            return false;
         }
      }

      // choice query not equal?
      if(!Tool.equals(choiceQuery, var2.choiceQuery)) {
         return false;
      }

      // choices not equal?
      if(!Tool.equals(choices, var2.choices)) {
         return false;
      }

      // values not equal?
      if(!Tool.equals(values, var2.values)) {
         return false;
      }

      if(!Tool.equals(xtype, var2.xtype)) {
         return false;
      }

      // boolean values not equal?
      if(hidden != var2.hidden || prompt != var2.prompt ||
         multi != var2.multi ||
         embedded != var2.embedded || sortValue != var2.sortValue)
      {
         return false;
      }

      return true;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return getName() != null ? getName().hashCode() : super.hashCode();
   }

   /**
    * Set if current used in one of condition.
    */
   public void setUsedInOneOf(boolean used) {
      this.usedInOneOf = used;
   }

   /**
    * Check is used in one of condition.
    */
   public boolean isUsedInOneOf() {
      return usedInOneOf;
   }

   /**
    * Check if is already executed.
    */
   public boolean isExecuted() {
      return executed;
   }

   /**
    * set is already executed.
    */
   public void setExecuted(boolean executed) {
      this.executed = executed;
   }

   /**
    * Get the "$(name)" as the variable representation.
    */
   public String toString() {
      if(getName() != null) {
         return "$(" + getName() + ")";
      }

      return super.toString();
   }

   private String label = null;
   private String toolTip = "";
   private XTypeNode xtype = new StringType();
   private XValueNode value = null;
   private Object[] choices = null;
   private Object[] values = null;
   private boolean dataTruncated = false;
   private boolean sortValue = true;
   private boolean hidden = false;
   private boolean prompt = true;
   private boolean embedded = false;
   private boolean customize = false; // true if used in customization
   private boolean usedInOneOf = false;
   private String choiceQuery = null;
   private boolean multi = false;
   private boolean executed = false;

   private static final Logger LOG = LoggerFactory.getLogger(UserVariable.class);
}
