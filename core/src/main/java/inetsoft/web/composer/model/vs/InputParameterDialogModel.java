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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InputParameterDialogModel {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getValueSource() {
      return valueSource;
   }

   public void setValueSource(String valueSource) {
      this.valueSource = valueSource;
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

   @Override
   public String toString() {
      return "InputParameterDialogModel{" +
         "name=" + name +
         ", valueSource=" + valueSource +
         ", value=" + value +
         ", type=" + type +
         '}';
   }

   private String name;
   private String valueSource;
   private Object value;
   private String type;
}
