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
package inetsoft.web.reportviewer.model;

import com.fasterxml.jackson.annotation.*;

import java.util.Objects;

/**
 * This is the td cell model for TableStylePageModel.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   property = "type"
)
@JsonSubTypes({
   @JsonSubTypes.Type(value = BooleanParameterModel.class, name = "BooleanParameter"),
   @JsonSubTypes.Type(value = ChoiceParameterModel.class, name = "ChoiceParameter"),
   @JsonSubTypes.Type(value = DateTimeParameterModel.class, name = "DateTimeParameter"),
   @JsonSubTypes.Type(value = DateParameterModel.class, name = "DateParameter"),
   @JsonSubTypes.Type(value = ListParameterModel.class, name = "ListParameter"),
   @JsonSubTypes.Type(value = OptionParameterModel.class, name = "OptionParameter"),
   @JsonSubTypes.Type(value = PasswordParameterModel.class, name = "PasswordParameter"),
   @JsonSubTypes.Type(value = RadioParameterModel.class, name = "RadioParameter"),
   @JsonSubTypes.Type(value = RepletParameterModel.class, name = "SimpleParameter"),
   @JsonSubTypes.Type(value = TextAreaParameterModel.class, name = "TextAreaParameter"),
   @JsonSubTypes.Type(value = TimeParameterModel.class, name = "TimeParameter")
})
public class RepletParameterModel {
   public RepletParameterModel() {
   }

   /**
    * Set replet parameter name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get replet parameter name.
    */
   public String getName() {
      return this.name;
   }

   /**
    * Set replet parameter tooltip.
    */
   public void setTooltip(String tooltip) {
      this.tooltip = tooltip;
   }

   /**
    * Get replet parameter tooltip.
    */
   public String getTooltip() {
      return this.tooltip;
   }

   /**
    * Set replet parameter default value.
    */
   public void setValue(Object value) {
      this.value = value;
   }

   /**
    * Get replet parameter default value.
    */
   public Object getValue() {
      return this.value;
   }

   /**
    * Set if the replet parameter support multi values.
    */
   public void setMulti(boolean multi) {
      this.multi = multi;
   }

   /**
    * Get if the replet parameter support multi values.
    */
   public boolean isMulti() {
      return this.multi;
   }

   /**
    * Set if the paramter value has decimal format.
    */
   public void setDecimalType(boolean decimalType) {
      this.decimalType = decimalType;
   }

   /**
    * Set if the paramter value has decimal format.
    */
   public boolean isDecimalType() {
      return this.decimalType;
   }

   /**
    * Set the alias of this parameter.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Get the alias of this parameter.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Gets a flag that indicates if the parameter is required.
    *
    * @return {@code true} if required or {@code false} if optional.
    */
   public boolean isRequired() {
      return required;
   }

   /**
    * Sets a flag that indicates if the parameter is required.
    *
    * @param required {@code true} if required or {@code false} if optional.
    */
   public void setRequired(boolean required) {
      this.required = required;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      RepletParameterModel that = (RepletParameterModel) o;
      return multi == that.multi &&
         decimalType == that.decimalType &&
         Objects.equals(name, that.name) &&
         Objects.equals(alias, that.alias) &&
         Objects.equals(tooltip, that.tooltip) &&
         Objects.equals(value, that.value) &&
         required == that.required;
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, alias, tooltip, value, multi, decimalType, required);
   }

   @Override
   public String toString() {
      return "RepletParameterModel{" +
         "name='" + name + '\'' +
         ", alias='" + alias + '\'' +
         ", tooltip='" + tooltip + '\'' +
         ", value=" + value +
         ", multi=" + multi +
         ", decimalType=" + decimalType +
         ", required=" + required +
         '}';
   }

   private String name;
   private String alias;
   private String tooltip;
   private Object value;
   private boolean multi = false;
   private boolean decimalType;
   private boolean required = false;
}
