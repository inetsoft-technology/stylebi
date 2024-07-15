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
package inetsoft.report.composition.command;

import inetsoft.uql.AbstractCondition;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;

/**
 * Collect valiables.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class CollectVariablesCommand extends WorksheetCommand {
   /**
    * Constructor.
    */
   public CollectVariablesCommand() {
      super();
   }

   /**
    * Constructor.
    * @param variables user variables.
    * @param name assembly name.
    * @param recursive touch dependency or not.
    */
   public CollectVariablesCommand(UserVariable[] variables, String name,
                                  boolean recursive, VariableTable vart,
                                  boolean enterParameter) {
      this();
      put("name", name);
      put("recursive", "" + recursive);
      put("enterParameter", "" + enterParameter);
      infos = new VariableInfo[variables.length];

      for(int i = 0; i < infos.length; i++) {
         VariableInfo info = new VariableInfo();
         info.setUserVariable(variables[i]);

         try {
            Object values = vart.get(variables[i].getName());

            if(values != null) {
               info.setDefValue(values);
            }
         }
         catch(Exception e) {
         }

         infos[i] = info;
      }
   }

   /**
    * Write contents of asset variable.
    * @writer the specified output stream.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(infos != null) {
         writer.println("<infos>");

         for(int i = 0; i < infos.length; i++) {
            VariableInfo info = infos[i];
            info.writeXML(writer);
         }

         writer.println("</infos>");
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element infosNode = Tool.getChildNodeByTagName(tag, "infos");

      if(infosNode != null) {
         NodeList infosNodeList = infosNode.getChildNodes();
         infos = new VariableInfo[infosNodeList.getLength()];

         for(int i = 0; i < infos.length; i++) {
            VariableInfo info = new VariableInfo();
            info.parseXML((Element) infosNodeList.item(i));
            infos[i] = info;
         }
      }
   }

   /**
    * Variable info.
    */
   public static class VariableInfo implements AssetObject {
      public void setUserVariable(UserVariable variable) {
         name = variable.getName();
         alias = variable.getAlias();
         toolTip = variable.getToolTip();
         style = variable.getDisplayStyle();
         hidden = variable.isHidden();
         usedInOneOf = variable.isUsedInOneOf();
         choiceslist = variable.getChoices();
         valueslist = variable.getValues();

         if(variable.getTypeNode() != null) {
            this.type = variable.getTypeNode().getType();
         }

         if(choiceslist != null && valueslist != null &&
            valueslist.length == choiceslist.length)
         {
            choices = new String[choiceslist.length];

            for(int i = 0; i < choices.length; i++) {
               choices[i] = choiceslist[i] == null ? null : Tool.getDataString(choiceslist[i]);
            }

            values = new String[valueslist.length];

            for(int i = 0; i < values.length; i++) {
               values[i] = valueslist[i] == null ? null : Tool.getDataString(valueslist[i], this.type);
            }
         }

         if(variable.getValueNode() != null) {
            Object val = variable.getValueNode().getValue();
            setDefValue(val);

            if(val instanceof Object[]) {
               // do nothing
            }
            else if(val != null) {
               value2 = AbstractCondition.getValueString(val);
            }
         }
      }

      /**
       * Get the value as a string.
       * @param valueslist available value selection.
       */
      private String getDataString(Object val, Object[] valueslist) {
         // if the value is a number, we find the values in the valueslist
         // by using numeric comparison so 0.0 is same as 0
         if(val instanceof Number && valueslist != null) {
            for(int i = 0; i < valueslist.length; i++) {
               Object vobj = valueslist[i];

               if(vobj == null) {
                  continue;
               }

               if(vobj instanceof String) {
                  try {
                     vobj = Double.valueOf((String) vobj);
                  }
                  catch(Throwable ex) {
                     continue;
                  }
               }

               if(Tool.compare(vobj, val) == 0) {
                  return Tool.getDataString(valueslist[i]);
               }
            }
         }

         return Tool.getDataString(val);
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.println("<variableInfo class=\"" + getClass().getName() + "\"");
         writer.print(" name=\"" + Tool.escape(name) + "\"");
         writer.print(" hidden=\"" + hidden + "\"");
         writer.print(" style=\"" + Integer.toString(style) + "\"");
         writer.print(" type=\"" + type + "\"");
         writer.print(" usedInOneOf=\"" + usedInOneOf + "\"");

         if(alias != null) {
            writer.print(" alias=\"" + Tool.escape(alias) + "\"");
         }

         if(toolTip != null) {
            writer.print(" toolTip=\"" + Tool.escape(toolTip) + "\"");
         }

         writer.print(" >");

         if(value != null) {
            writer.println("<value>" + "<![CDATA[" + value + "]]></value>");
         }

         if(value2 != null) {
            writer.println("<value2>" + "<![CDATA[" + value2 + "]]></value2>");
         }

         if(choices != null) {
            writer.println("<choices>");

            for(int i = 0; i < choices.length; i++) {
               writer.println("<choice>" + "<item><![CDATA[" + choices[i] +
                  "]]></item>" + "<value><![CDATA[" + values[i] +
                  "]]></value>" + "</choice>");
            }

            writer.println("</choices>");
         }

         writer.println("</variableInfo>");
      }

      public void writeData(DataOutputStream dos) {
         try {
            dos.writeUTF(name);
            dos.writeBoolean(hidden);
            dos.writeInt(style);
            dos.writeUTF(type);

            dos.writeBoolean(alias == null);

            if(alias != null) {
               dos.writeUTF(alias);
            }

            dos.writeBoolean(toolTip == null);

            if(toolTip != null) {
               dos.writeUTF(toolTip);
            }

            dos.writeBoolean(value == null);

            if(value != null) {
               dos.writeUTF(value);
            }

            dos.writeBoolean(value2 == null);

            if(value2 != null) {
               dos.writeUTF(value2);
            }

            dos.writeBoolean(choices == null);

            if(choices != null) {
               dos.writeInt(choices.length);

               for(int i = 0; i < choices.length; i++) {
                  dos.writeUTF(choices[i]);
                  dos.writeUTF(values[i]);
               }
            }

            dos.writeBoolean(usedInOneOf);
         }
         catch (IOException e){
         }
      }

      @Override
      public void parseXML(Element tag) throws Exception {
         name = Tool.getAttribute(tag, "name");
         alias = Tool.getAttribute(tag, "alias");
         toolTip = Tool.getAttribute(tag, "toolTip");
         value = Tool.getAttribute(tag, "value");
         style = Integer.parseInt(Tool.getAttribute(tag, "style"));
         type = Tool.getAttribute(tag, "type");
         hidden = "true".equals(Tool.getAttribute(tag, "hidden"));
         usedInOneOf = "true".equals(Tool.getAttribute(tag, "usedInOneOf"));
         Element valueNode = Tool.getChildNodeByTagName(tag, "value");

         if(valueNode != null) {
            value = Tool.getValue(valueNode);
         }

         Element choiceNode = Tool.getChildNodeByTagName(tag, "choices");

         if(choiceNode != null) {
            NodeList list2 = Tool.getChildNodesByTagName(choiceNode, "choice");
            choices = new String[list2.getLength()];
            values = new String[list2.getLength()];

            for(int k = 0; k < list2.getLength(); k++) {
               Element e2 = (Element) list2.item(k);
               NodeList name = Tool.getChildNodesByTagName(e2, "item");
               NodeList value = Tool.getChildNodesByTagName(e2, "value");

               if(name.getLength() > 0 && value.getLength() > 0) {
                  choices[k] = Tool.getValue((Element) name.item(0));
                  values[k] = Tool.getValue((Element) value.item(0));
               }
            }
         }
      }

      private void setDefValue(Object val) {
         if(val instanceof Object[]) {
            Object[] arr = (Object[]) val;
            StringBuilder sb = new StringBuilder();

            for(int i = 0; i < arr.length; i++) {
               if(i > 0) {
                  sb.append('^');
               }

               sb.append(getDataString(arr[i], valueslist));
            }

            value = sb.toString();
         }
         else if(val != null) {
            value = getDataString(val, valueslist);
         }
      }

      /**
       * Get the default value.
       */
      public String getValue() {
         return value;
      }

      /**
       * Set the default value.
       */
      public void setValue(String value) {
         this.value = value;
      }

      /**
       * Get the variable label.
       */
      public String getAlias() {
         return alias;
      }

      /**
       * Get the variable toolTip.
       */
      public String getToolTip() {
         return toolTip;
      }

      /**
       * Get the list of choices.
       * @return null if choice is not set.
       */
      public String[] getChoices() {
         return choices;
      }

      /**
       * Get the list of values.
       * @return null if value is not set.
       */
      public String[] getValues() {
         return values;
      }

      /**
       * Get the variable name.
       */
      public String getName() {
         return name;
      }

      /**
       * Get the display style.
       */
      public int getStyle() {
         return style;
      }

      /**
       * Get the type of this variable.
       */
      public String getType() {
         return type;
      }

      /**
       * Is hidden variable.
       */
      public boolean isHidden() {
         return hidden;
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
       * Clone this object.
       * @return the cloned object.
       */
      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(Exception ex) {
            // ignore it
         }

         return null;
      }

      private String name;
      private String alias;
      private String toolTip;
      private int style;
      private String[] choices = new String[0];
      private String[] values = new String[0];
      private String value;
      private String value2;
      private String type;
      private boolean hidden;
      private boolean usedInOneOf;
      private Object[] choiceslist;
      private Object[] valueslist;
   }

   private VariableInfo[] infos;
}
