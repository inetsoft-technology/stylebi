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
package inetsoft.web.graph.model.dialog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.viewsheet.graph.TitleDescriptor;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TitleFormatDialogModel {
   public TitleFormatDialogModel() {
   }

   public TitleFormatDialogModel(TitleDescriptor titleDesc, String oldTitle) {
      this.oldTitle = oldTitle;
      String title = titleDesc.getTitle();
      String titleValue = titleDesc.getTitleValue();

      if(title != null && title.isEmpty()) {
         title = null;
      }

      if(titleValue != null && titleValue.isEmpty()) {
         titleValue = null;
      }

      if(title != null) {
         this.oldTitle = title;
      }

      // Create TitleFormatPaneModel.
      titleFormatPaneModel = new TitleFormatPaneModel();
      titleFormatPaneModel.setTitle(titleValue != null && !"".equals(titleValue) ?
         titleValue : this.oldTitle);
      titleFormatPaneModel.setCurrentTitle(this.oldTitle);
      RotationRadioGroupModel rotationModel = new RotationRadioGroupModel();
      Number rotation = titleDesc.getTextFormat().getRotation();

      if(rotation != null) {
         rotationModel.setRotation(rotation.toString());
      }

      titleFormatPaneModel.setRotationRadioGroupModel(rotationModel);
   }

   public void updateTitleProperties(TitleDescriptor titleDesc) {
      String title = titleFormatPaneModel.getTitle();

      if(!Objects.equals(title, this.oldTitle)) {
         if(titleDesc.getTitleValue() != null &&
            "=".equals(titleDesc.getTitleValue()) || title == null)
         {
            titleDesc.setTitleValue("");
         }
         else {
            titleDesc.setTitleValue(title);
         }
      }

      RotationRadioGroupModel rotationModel =
         titleFormatPaneModel.getRotationRadioGroupModel();
      String rotation = rotationModel.getRotation();

      if(rotation != null) {
         titleDesc.getTextFormat().setRotation(Float.parseFloat(rotation));
      }
   }

   public TitleFormatPaneModel getTitleFormatPaneModel() {
      return titleFormatPaneModel;
   }

   public void setTitleFormatPaneModel(TitleFormatPaneModel titleFormatPaneModel) {
      this.titleFormatPaneModel = titleFormatPaneModel;
   }

   public String getOldTitle() {
      return oldTitle;
   }

   public void setOldTitle(String title) {
      this.oldTitle = title;
   }

   @Override
   public String toString() {
      return "TitleFormatDialogModel{" +
         "oldTitle='" + oldTitle + '\'' +
         ", titleFormatPaneModel=" + titleFormatPaneModel +
         '}';
   }

   private String oldTitle;
   private TitleFormatPaneModel titleFormatPaneModel;
}
