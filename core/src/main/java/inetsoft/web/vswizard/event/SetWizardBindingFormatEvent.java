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
package inetsoft.web.vswizard.event;

import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;

public class SetWizardBindingFormatEvent {
   /**
    * full name of target field.
    */
   public String getField() {
      return field;
   }

   public void setField(String field) {
      this.field = field;
   }

   /**
    * format model of target field.
    */
   public VSObjectFormatInfoModel getModel() {
      return model;
   }

   public void setModel(VSObjectFormatInfoModel model) {
      this.model = model;
   }

   private String field;
   private VSObjectFormatInfoModel model;
}