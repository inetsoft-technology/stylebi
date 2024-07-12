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
package inetsoft.web.vswizard.model;

import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;

public class WizardBindingFormatModel {
   public String getFieldName() {
      return fieldName;
   }

   public void setFieldName(String fieldName) {
      this.fieldName = fieldName;
   }

   public VSObjectFormatInfoModel getFormatModel() {
      return formatModel;
   }

   public void setFormatModel(VSObjectFormatInfoModel formatModel) {
      this.formatModel = formatModel;
   }

   private String fieldName;
   private VSObjectFormatInfoModel formatModel;
}