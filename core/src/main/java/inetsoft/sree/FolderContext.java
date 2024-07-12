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
package inetsoft.sree;

/**
 * Folder context store alias about a Folder. It is used
 * internally to retrieve information about a Folder.
 */
public class FolderContext extends DefaultContext {
   /**
    * Create a folder context.
    */
   public FolderContext(String name) {
      this(name, null, null);
   }

   /**
    * Create a folder context.
    */
   public FolderContext(String name, String description) {
      this(name, description, null);
   }

   /**
    * Create a folder context.
    */
   public FolderContext(String name, String description, String alias) {
      super(name, description);
      this.alias = alias;
   }

   /**
    * Get folder alias name.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Set folder alias name.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   private String alias;
}
