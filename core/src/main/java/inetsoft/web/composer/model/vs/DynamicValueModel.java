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
package inetsoft.web.composer.model.vs;

import inetsoft.sree.DynamicParameterValue;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.condition.ConditionValueModel;

import java.io.Serializable;
import java.util.Date;

public class DynamicValueModel implements Serializable {
   public DynamicValueModel() {}

   public DynamicValueModel(String value) {
      super();

      if(value == null) {
         return;
      }

      setValue(value);

      if(VSUtil.isVariableValue(value)) {
         setType(DynamicValueModel.VARIABLE);
      }
      else if(VSUtil.isScriptValue(value)) {
         setType(DynamicValueModel.EXPRESSION);
      }
      else {
         setType(DynamicValueModel.VALUE);
      }
   }

   public DynamicValueModel(Object value, String type) {
      this.value = value;
      this.type = type;
   }

   public DynamicValueModel(Object value, String type, String dataType) {
      this.value = value;
      this.type = type;
      this.dataType = dataType;
   }

   public DynamicParameterValue convertParameterValue() {
      return new DynamicParameterValue(this.value, this.type, this.dataType);
   }

   public String convertToValue() {
      if(DynamicValueModel.VALUE.equals(getType()) && getValue() instanceof Date) {
         return Tool.formatDate((Date) getValue());
      }

      return getValue() == null ? null : getValue().toString();
   }

   public Object getValue() {
      return value;
   }

   public void setValue(Object value) {
      this.value = value;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getDataType() {
      return dataType;
   }

   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   private Object value;
   private String type;
   private String dataType;

   public static final String VALUE = ConditionValueModel.VALUE;
   public static final String VARIABLE = ConditionValueModel.VARIABLE;
   public static final String EXPRESSION = ConditionValueModel.EXPRESSION;
}
