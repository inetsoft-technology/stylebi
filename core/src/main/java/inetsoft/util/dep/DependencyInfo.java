/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.util.dep;

import inetsoft.sree.internal.SUtil;

/**
 * {@code DependencyInfo} contains descriptive information about a dependency.
 */
public class DependencyInfo {
   /**
    * Creates a new instance of {@code DependencyInfo}.
    */
   public DependencyInfo() {
   }

   /**
    * Creates a new instance of {@code DependencyInfo}.
    *
    * @param dependency the dependency.
    */
   public DependencyInfo(XAssetDependency dependency) {
      addDependency(dependency);
   }

   /**
    * Gets the path to the asset that requires the dependency.
    *
    * @return the asset path.
    */
   public String getRequiredBy() {
      return requiredBy;
   }

   /**
    * Sets the path to the asset that requires the dependency.
    *
    * @param requiredBy the asset path.
    */
   public void setRequiredBy(String requiredBy) {
      this.requiredBy = requiredBy;
   }

   /**
    * Gets a description of the dependency.
    *
    * @return the description.
    */
   public String getDescription() {
      return description;
   }

   /**
    * Sets a description of the dependency.
    *
    * @param description the description.
    */
   public void setDescription(String description) {
      this.description = description;
   }

   /**
    * Gets the last modified time of the dependency.
    *
    * @return the last modified time.
    */
   public long getLastModifiedTime() {
      return lastModifiedTime;
   }

   /**
    * Sets a last modified time of the dependency.
    *
    * @param lastModifiedTime last modified time.
    */
   public void setLastModifiedTime(long lastModifiedTime) {
      this.lastModifiedTime = lastModifiedTime;
   }

   /**
    * Adds a dependency.
    *
    * @param dependency the dependency to add.
    */
   public void addDependency(XAssetDependency dependency) {
      if(requiredBy == null) {
         if(dependency.getDependingXAsset().isVisible()) {
            if(dependency.getDependingXAsset() instanceof VirtualPrivateModelAsset &&
               dependency.getDependedXAsset() instanceof XDataSourceAsset)
            {
               requiredBy = dependency.getDependedXAsset().getPath();
            }
            else {
               requiredBy = dependency.getDependingXAsset().getPath();
            }

            requiredBy = dependency.getDependingXAsset().getPath();
            requiredBy = SUtil.getTaskNameWithoutOrg(requiredBy);
            description = dependency.toString();
            lastModifiedTime = dependency.getLastModifiedTime();
         }
         else {
            requiredBy = "";
            description = "";
            lastModifiedTime = 0;
         }
      }
      else if(dependency.getDependingXAsset().isVisible()) {
         requiredBy += ", " + SUtil.getTaskNameWithoutOrg(dependency.getDependingXAsset().getPath());
         description += " " + dependency.toString();
      }
   }

   private String requiredBy;
   private String description;
   private long lastModifiedTime;
}
