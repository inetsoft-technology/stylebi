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
package inetsoft.web.graph.model.dialog;

public class TitleFormatPaneModel {
   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getCurrentTitle() {
      return otitle;
   }

   public void setCurrentTitle(String title) {
      this.otitle = title;
   }

   public RotationRadioGroupModel getRotationRadioGroupModel() {
      return rotationRadioGroupModel;
   }

   public void setRotationRadioGroupModel(RotationRadioGroupModel rotationModel) {
      this.rotationRadioGroupModel = rotationModel;
   }

   @Override
   public String toString() {
      return "TitleFormatPaneModel{" +
         "title='" + title + '\'' +
         ", otitle='" + otitle + '\'' +
         ", rotationRadioGroupModel=" + rotationRadioGroupModel +
         '}';
   }

   private String title;
   private String otitle;
   private RotationRadioGroupModel rotationRadioGroupModel;
}
