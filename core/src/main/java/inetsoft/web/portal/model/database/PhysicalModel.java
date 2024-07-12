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
 * Bean that represents a physical model.
 */
@JsonTypeName("physical_model")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhysicalModel extends DatabaseAsset {
   /**
    * Creates a new instance of <tt>PhysicalModel</tt>.
    */
   public PhysicalModel() {
      super("physical_model");
   }

   public List<PhysicalModel> getExtendViews() {
      return extendViews;
   }

   @Nullable
   public void setExtendViews(List<PhysicalModel> extendViews) {
      this.extendViews = extendViews;
   }

   public String getParentView() {
      return parentView;
   }

   @Nullable
   public void setParentView(String parentView) {
      this.parentView = parentView;
   }

   public String getFolderName() {
      return folderName;
   }

   public void setFolderName(String folderName) {
      this.folderName = folderName;
   }

   @Override
   public String toString() {
      return "PhysicalModel{" +
         "extendViews=" + extendViews +
         ", parentView='" + parentView + '\'' +
         '}';
   }

   private List<PhysicalModel> extendViews;
   private String parentView;
   private String folderName;
}
