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
package inetsoft.web.composer.model.condition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.binding.drm.DataRefModel;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConditionModel {
   public DataRefModel getField() {
      return field;
   }

   public void setField(DataRefModel field) {
      this.field = field;
   }

   public int getOperation() {
      return operation;
   }

   public void setOperation(int operation) {
      this.operation = operation;
   }

   public ConditionValueModel[] getValues() {
      return values;
   }

   public void setValues(ConditionValueModel[] values) {
      this.values = values;
   }

   public int getLevel() {
      return level;
   }

   public void setLevel(int level) {
      this.level = level;
   }

   public boolean isEqual() {
      return equal;
   }

   public void setEqual(boolean equal) {
      this.equal = equal;
   }

   public boolean isNegated() {
      return negated;
   }

   public void setNegated(boolean negated) {
      this.negated = negated;
   }

   private DataRefModel field;
   private int operation;
   private ConditionValueModel[] values;
   private int level;
   private boolean equal;
   private boolean negated;
}
