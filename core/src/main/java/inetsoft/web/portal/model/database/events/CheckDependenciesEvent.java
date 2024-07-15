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
package inetsoft.web.portal.model.database.events;

import inetsoft.web.portal.model.database.ElementModel;

public class CheckDependenciesEvent {
   public String getDatasourceFolderPath() {
      return datasourceFolderPath;
   }

   public void setDatasourceFolderPath(String datasourceFolderPath) {
      this.datasourceFolderPath = datasourceFolderPath;
   }

   public String getDatabaseName() {
      return databaseName;
   }

   public void setDatabaseName(String databaseName) {
      this.databaseName = databaseName;
   }

   public String getDataModelFolder() {
      return dataModelFolder;
   }

   public void setDataModelFolder(String dataModelFolder) {
      this.dataModelFolder = dataModelFolder;
   }

   public String getModelName() {
      return modelName;
   }

   public void setModelName(String modelName) {
      this.modelName = modelName;
   }

   public ElementModel[] getModelElements() {
      return modelElements;
   }

   public void setModelElements(ElementModel[] modelElements) {
      this.modelElements = modelElements;
   }

   /**
    * @return the parent model name.
    */
   public String getParent() {
      return parent;
   }

   public void setParent(String parent) {
      this.parent = parent;
   }

   public boolean isNewCreate() {
      return newCreate;
   }

   public void setNewCreate(boolean newCreate) {
      this.newCreate = newCreate;
   }

   private String datasourceFolderPath;
   private String databaseName;
   private String dataModelFolder;
   private String modelName;
   private String parent;
   private ElementModel[] modelElements; // entities or attributes.
   private boolean newCreate;
}
