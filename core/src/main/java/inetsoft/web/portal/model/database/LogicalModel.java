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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Bean that represents a logical model.
 */
@JsonTypeName("logical_model")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogicalModel extends DatabaseAsset {
    /**
    * Creates a new instance of <tt>LogicalModel</tt>.
    */
   public LogicalModel() {
      super("logical_model");
   }

   /**
    * Gets the physical model.
    *
    * @return the path of the physical model.
    */
   public String getPhysicalModel() {
      return physicalModel;
   }

   /**
    * Sets the physical model.
    *
    * @param physicalModel the path of the physical model.
    */
   public void setPhysicalModel(String physicalModel) {
      this.physicalModel = physicalModel;
   }

   public List<LogicalModel> getExtendModels() {
      return extendModels;
   }

   @Nullable
   public void setExtendModels(List<LogicalModel> extendModels) {
      this.extendModels = extendModels;
   }

   @Nullable
   public String getParentModel() {
      return parentModel;
   }

   public void setParentModel(String parentModel) {
      this.parentModel = parentModel;
   }

   public String getFolderName() {
      return folderName;
   }

   public void setFolderName(String folderName) {
      this.folderName = folderName;
   }

   public String getConnection() {
      return connection;
   }

   public void setConnection(String connection) {
      this.connection = connection;
   }

   private String physicalModel;
   private List<LogicalModel> extendModels;
   private String parentModel;
   private String folderName;
   private String connection;
}
