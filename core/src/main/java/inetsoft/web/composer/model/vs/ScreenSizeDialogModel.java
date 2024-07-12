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
package inetsoft.web.composer.model.vs;

public class ScreenSizeDialogModel {
   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public int getMinWidth() {
      return minWidth;
   }

   public void setMinWidth(int minWidth) {
      this.minWidth = minWidth;
   }

   public int getMaxWidth() {
      return maxWidth;
   }

   public void setMaxWidth(int maxWidth) {
      this.maxWidth = maxWidth;
   }

   public String getTempId() {
      return tempId;
   }

   public void setTempId(String tempId) {
      this.tempId = tempId;
   }

   private String label;
   private String description;
   private String id;
   private int minWidth;
   private int maxWidth;
   private String tempId;
}
