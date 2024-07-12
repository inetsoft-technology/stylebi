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
package inetsoft.web.binding.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.table.*;

import java.util.List;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   visible = true,
   property = "type")
@JsonSubTypes({
   @JsonSubTypes.Type(value = ChartBindingModel.class, name = "chart"),
   @JsonSubTypes.Type(value = TableBindingModel.class, name = "table"),
   @JsonSubTypes.Type(value = CrosstabBindingModel.class, name = "crosstab"),
   @JsonSubTypes.Type(value = CalcTableBindingModel.class, name = "calctable")
})
public class BindingModel {
   /**
    * Set source
    */
   public void setSource(SourceInfo source) {
      this.source = source;
   }

   /**
    * Get source.
    */
   public SourceInfo getSource() {
      return source;
   }

   /**
    * Get availableFields.
    */
   public List<DataRefModel> getAvailableFields() {
      return availableFields;
   }

   /**
    * Set availableFields
    */
   public void setAvailableFields(List<DataRefModel> availableFields) {
      this.availableFields = availableFields;
   }

   /**
    * Check is sql mergeable or not.
    * @return true if sql mergeable.
    */
   public boolean isSqlMergeable() {
      return sqlMergeable;
   }

   /**
    * Set the sql mergeable.
    */
   public void setSqlMergeable(boolean merge) {
      sqlMergeable = merge;
   }

   /**
    * Get type.
    */
   public String getType() {
      return type;
   }

   /**
    * Set type.
    */
   public void setType(String type) {
      this.type = type;
   }

   private SourceInfo source;
   private List<DataRefModel> availableFields;
   private boolean sqlMergeable = true;
   private String type;
}
