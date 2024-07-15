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
package inetsoft.web.composer.ws.assembly;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.AbstractCondition;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;

import java.util.Arrays;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
// Sourced from CollectVariablesCommand.VariableInfo
public class VariableAssemblyModelInfo {
   public VariableAssemblyModelInfo() {}

   public VariableAssemblyModelInfo(UserVariable variable) {
      name = variable.getName();
      alias = variable.getAlias();
      toolTip = variable.getToolTip();
      style = variable.getDisplayStyle();
      hidden = variable.isHidden();
      usedInOneOf = variable.isUsedInOneOf() || variable.isMultipleSelection();
      Object[] choiceslist = variable.getChoices();
      Object[] valueslist = variable.getValues();
      description = variable.toString();
      dataTruncated = variable.isDataTruncated();

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
         setDefValue(val, valueslist);

         if(val instanceof Object[]) {
            // do nothing
         }
         else if(val != null) {
            value2 = AbstractCondition.getValueString(val);
         }
      }
   }

   public VariableAssemblyModelInfo(UserVariable variable, Object value) {
      this(variable);

      if(value == null) {
         this.value = null;
         return;
      }

      if(value instanceof Object[]) {
         this.value = (Object[]) value;
      }
      else {
         this.value = new Object[1];
         this.value[0] = value;
      }
   }

   private void setDefValue(Object val, Object[] valueslist) {
      if(val instanceof Object[]) {
         Object[] arr = (Object[]) val;

         for(int i = 0; i < arr.length; i++) {
            arr[i] = getDataString(arr[i], valueslist);
         }

         value = arr;
      }
      else if(val != null) {
         String temp = getDataString(val, valueslist);

         if(val instanceof String && Tool.equals(temp, val)) {
            value = ((String) val).split(",");
         }
         else {
            value = temp.split("\\^");
         }
      }
   }

   /**
    * Get the value as a string.
    *
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

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getAlias() {
      return alias;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   public String getToolTip() {
      return toolTip;
   }

   public void setToolTip(String toolTip) {
      this.toolTip = toolTip;
   }

   public int getStyle() {
      return style;
   }

   public void setStyle(int style) {
      this.style = style;
   }

   public String[] getChoices() {
      return choices;
   }

   public void setChoices(String[] choices) {
      this.choices = choices;
   }

   public boolean isDataTruncated() {
      return dataTruncated;
   }

   public void setDataTruncated(boolean dataTruncated) {
      this.dataTruncated = dataTruncated;
   }

   public String[] getValues() {
      return values;
   }

   public void setValues(String[] values) {
      this.values = values;
   }

   public Object[] getValue() {
      return value;
   }

   public void setValue(Object[] value) {
      this.value = value;
   }

   public String getValue2() {
      return value2;
   }

   public void setValue2(String value2) {
      this.value2 = value2;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public boolean getHidden() {
      return hidden;
   }

   public void setHidden(boolean hidden) {
      this.hidden = hidden;
   }

   public boolean getUsedInOneOf() {
      return usedInOneOf;
   }

   public void setUsedInOneOf(boolean usedInOneOf) {
      this.usedInOneOf = usedInOneOf;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public boolean isUserSelected() {
      return userSelected;
   }

   public void setUserSelected(boolean userSelected) {
      this.userSelected = userSelected;
   }

   public static VariableTable getVariableTable(List<VariableAssemblyModelInfo> varInfos) {
      VariableTable vars = new VariableTable();

      if(varInfos != null) {
         for(VariableAssemblyModelInfo var :  varInfos) {
            Object[] values = Arrays.stream(var.getValue())
               .map((val) -> val == null ? null : val.toString())
               .map((val) -> val == null || val.length() == 0 ? null :
                  Tool.getData(var.getType(), val))
               .toArray(Object[]::new);
            vars.put(var.getName(), values.length == 1 ? values[0] : values);
         }
      }

      return vars;
   }

   private String name;
   private String alias;
   private String toolTip;
   private int style;
   private String[] choices;
   private String[] values;
   private boolean dataTruncated;
   private Object[] value;
   private String value2;
   private String type;
   private boolean hidden;
   private boolean usedInOneOf;
   private String description;
   private boolean userSelected;
}
