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
import inetsoft.uql.viewsheet.ColumnOption;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DateEditorModel extends EditorModel {
   public DateEditorModel() {
      setType(ColumnOption.DATE);
   }

   public String getMinimum() {
      return minimum;
   }

   public void setMinimum(String minimum) {
      this.minimum = minimum;
   }

   public String getMaximum() {
      return maximum;
   }

   public void setMaximum(String maximum) {
      this.maximum = maximum;
   }

   public String getErrorMessage() {
      return errorMessage;
   }

   public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
   }

   private String minimum;
   private String maximum;
   private String errorMessage;
}
