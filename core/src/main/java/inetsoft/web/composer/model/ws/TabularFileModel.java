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

public class TabularFileModel {
   public boolean isFolder() {
      return folder;
   }

   public void setFolder(boolean folder) {
      this.folder = folder;
   }

   public String getPath() {
      return path;
   }

   public void setPath(String path) {
      this.path = path;
   }

   public String getAbsolutePath() {
      return absolutePath;
   }

   public void setAbsolutePath(String absolutePath) {
      this.absolutePath = absolutePath;
   }

   private boolean folder;
   private String path;
   private String absolutePath;
}
