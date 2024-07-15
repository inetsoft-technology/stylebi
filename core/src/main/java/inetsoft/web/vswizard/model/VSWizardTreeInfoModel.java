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
package inetsoft.web.vswizard.model;

import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BindingModel;

/**
 * For wizard tree infomation.
 *    geo, dimension/measure, formula.
 */
public class VSWizardTreeInfoModel {

   public DataRefModel[] getGrayedOutFields() {
      return grayedOutFields;
   }

   public void setGrayedOutFields(DataRefModel[] grayedOutFields) {
      this.grayedOutFields = grayedOutFields;
   }

   public String getTempAssemblyName() {
      return tempAssemblyName;
   }

   public void setTempAssemblyName(String tempAssemblyName) {
      this.tempAssemblyName = tempAssemblyName;
   }

   public BindingModel getTempBinding() {
      return tempBinding;
   }

   public void setTempBinding(BindingModel tempBinding) {
      this.tempBinding = tempBinding;
   }

   private DataRefModel[] grayedOutFields;
   private String tempAssemblyName;
   private BindingModel tempBinding;
}
