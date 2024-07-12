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
package inetsoft.web.reportviewer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * This is the td cell model for TableStylePageModel.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChoiceParameterModel extends RepletParameterModel {
   public ChoiceParameterModel() {
      super();
   }

   public void setChoicesLabel(Object[] labels) {
      this.choicesLabel = labels;
   }

   public Object[] getChoicesLabel() {
      return this.choicesLabel;
   }

   public void setChoicesValue(Object[] values) {
      this.choicesValue = values;
   }

   public Object[] getChoicesValue() {
      return this.choicesValue;
   }

   public boolean isDataTruncated() {
      return dataTruncated;
   }

   public void setDataTruncated(boolean dataTruncated) {
      this.dataTruncated = dataTruncated;
   }

   private Object[] choicesLabel;
   private Object[] choicesValue;
   private boolean dataTruncated;
}
