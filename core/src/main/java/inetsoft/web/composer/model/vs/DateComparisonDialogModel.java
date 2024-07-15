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
package inetsoft.web.composer.model.vs;

import inetsoft.util.data.CommonKVModel;

public class DateComparisonDialogModel {
   public DateComparisonDialogModel() {
      super();
   }

   public DateComparisonPaneModel getDateComparisonPaneModel() {
      if(dateComparisonPaneModel == null) {
         dateComparisonPaneModel = new DateComparisonPaneModel();
      }

      return dateComparisonPaneModel;
   }

   public void setDateComparisonPaneModel(DateComparisonPaneModel dateComparisonPaneModel) {
      this.dateComparisonPaneModel = dateComparisonPaneModel;
   }

   public String getShareFromAssembly() {
      return shareFromAssembly;
   }

   public void setShareFromAssembly(String shareFromAssembly) {
      this.shareFromAssembly = shareFromAssembly;
   }

   public CommonKVModel<String, Integer>[] getShareFromAvailableAssemblies() {
      return shareFromAvailableAssemblies;
   }

   public void setShareFromAvailableAssemblies(CommonKVModel<String, Integer>[] shareFromAvailableAssemblies) {
      this.shareFromAvailableAssemblies = shareFromAvailableAssemblies;
   }

   private DateComparisonPaneModel dateComparisonPaneModel;
   private String shareFromAssembly;
   private CommonKVModel<String, Integer>[] shareFromAvailableAssemblies;
}
