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
package inetsoft.web.portal.data;

/**
 * Bean that encapsulates a request to move an asset.
 */
public class MoveCommand {
   /**
    * Gets the path to the folder to which the asset will be copied.
    *
    * @return the new parent folder path.
    */
   public String getPath() {
      return path;
   }

   /**
    * Sets the path to the folder to which the asset will be copied.
    *
    * @param path the new parent folder path.
    */
   public void setPath(String path) {
      this.path = path;
   }

   /**
    * Gets the name of the asset being moved.
    *
    * @return the asset name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the asset being moved.
    *
    * @param name the asset name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the creation date of the asset being moved.
    *
    * @return the asset name.
    */
   public long getDate() {
      return date;
   }

   /**
    * Sets the creation date of the asset being moved.
    *
    * @param date the asset creation date.
    */
   public void setDate(Long date) {
      this.date = date;
   }

   /**
    * Gets the path of the asset being moved.
    *
    * @return the asset name.
    */
   public String getOldPath() {
      return oldPath;
   }

   /**
    * Sets the path of the asset being moved.
    *
    * @param oldPath the asset's path.
    */
   public void setOldPath(String oldPath) {
      this.oldPath = oldPath;
   }

   /**
    * Gets the old id.
    *
    * @return the asset name.
    */
   public String getId() {
      return id;
   }

   /**
    * Sets the old id.
    *
    * @param id the asset's id.
    */
   public void setId(String id) {
      this.id = id;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getType() {
      return this.type;
   }

   private String path;
   private String name;
   private long date;
   private String oldPath;
   private String id;
   private String type;
}
