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
package inetsoft.web.composer.model.ws;

import javax.annotation.Nullable;

public class UpdateFreeFormSQLPaneEvent {

   public UpdateFreeFormSQLPaneEvent() {
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public void setRuntimeId(String runtimeId) {
      this.runtimeId = runtimeId;
   }

   public FreeFormSQLPaneModel getFreeFormSqlPaneModel() {
      return freeFormSQLPaneModel;
   }

   public void setFreeFormSqlPaneModel(FreeFormSQLPaneModel freeFormSQLPaneModel) {
      this.freeFormSQLPaneModel = freeFormSQLPaneModel;
   }

   @Nullable
   public boolean isExecuteQuery() {
      return executeQuery;
   }

   public void setExecuteQuery(boolean executeQuery) {
      this.executeQuery = executeQuery;
   }

   private String runtimeId;
   private boolean executeQuery;
   private FreeFormSQLPaneModel freeFormSQLPaneModel;
}
