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
package inetsoft.web.vswizard.event;

public class CloseObjectWizardEvent {

   public String getEditMode() {
      return editMode;
   }

   public void setEditMode(String editMode) {
      this.editMode = editMode;
   }

   public String getOriginalMode() {
      return originalMode;
   }

   public void setOriginalMode(String originalMode) {
      this.originalMode = originalMode;
   }

   public String getOldOriginalName() {
      return oldOriginalName;
   }

   public void setOldOriginalName(String oldOriginalName) {
      this.oldOriginalName = oldOriginalName;
   }

   public boolean getViewer() {
      return viewer;
   }

   public void setViewer(Boolean viewer) {
      this.viewer = viewer;
   }

   private String editMode;
   private String originalMode;
   private String oldOriginalName;
   private boolean viewer;
}
