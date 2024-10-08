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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.viewsheet.ColumnOption;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FloatEditorModel extends EditorModel {
   public FloatEditorModel() {
      setType(ColumnOption.FLOAT);
   }

   public Float getMinimum() {
      return minimum;
   }

   public void setMinimum(Float minimum) {
      this.minimum = minimum;
   }

   public Float getMaximum() {
      return maximum;
   }

   public void setMaximum(Float maximum) {
      this.maximum = maximum;
   }

   public String getErrorMessage() {
      return errorMessage;
   }

   public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
   }

   private Float minimum;
   private Float maximum;
   private String errorMessage;
}
