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
public class VariableListDialogModel {
   public String[] getLabels() {
      if(labels == null) {
         labels = new String[0];
      }
      return labels;
   }

   public void setLabels(String[] labels) {
      this.labels = labels;
   }

   public String[] getValues() {
      if(values == null){
         values = new String[0];
      }
      return values;
   }

   public void setValues(String[] values) {
      this.values = values;
   }

   public String getDataType() {
      return dataType;
   }

   public void setDataType(String dataType) {
      this.dataType = dataType;
   }

   private String[] labels;
   private String[] values;
   private String dataType;
}
