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
package inetsoft.web.portal.model.database.cube.xmla;

import inetsoft.web.portal.model.database.cube.XCubeMemberModel;

public class CubeMeasureModel extends XCubeMemberModel {
   public String getFolder() {
      return folder;
   }

   public void setFolder(String folder) {
      this.folder = folder;
   }

   public String getUniqueName() {
      return uniqueName;
   }

   public void setUniqueName(String uniqueName) {
      this.uniqueName = uniqueName;
   }

   public String getCaption() {
      return caption;
   }

   public void setCaption(String caption) {
      this.caption = caption;
   }

   private String folder;
   private String uniqueName;
   private String caption;
}
