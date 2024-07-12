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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data transfer object that represents the {@link TextInputPropertyDialogModel} for the
 * textinput property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TextInputPropertyDialogModel {
   public DataInputPaneModel getDataInputPaneModel() {
      if(dataInputPaneModel == null) {
         dataInputPaneModel = new DataInputPaneModel();
      }

      return dataInputPaneModel;
   }

   public void setDataInputPaneModel(
      DataInputPaneModel dataInputPaneModel)
   {
      this.dataInputPaneModel = dataInputPaneModel;
   }

   public TextInputGeneralPaneModel getTextInputGeneralPaneModel() {
      if(textInputGeneralPaneModel == null) {
         textInputGeneralPaneModel = new TextInputGeneralPaneModel();
      }

      return textInputGeneralPaneModel;
   }

   public void setTextInputGeneralPaneModel(
      TextInputGeneralPaneModel textInputGeneralPaneModel)
   {
      this.textInputGeneralPaneModel = textInputGeneralPaneModel;
   }

   public TextInputColumnOptionPaneModel getTextInputColumnOptionPaneModel() {
      if(textInputColumnOptionPaneModel == null) {
         textInputColumnOptionPaneModel = new TextInputColumnOptionPaneModel();
      }

      return textInputColumnOptionPaneModel;
   }

   public void setClickableScriptPaneModel(ClickableScriptPaneModel clickableScriptPaneModel) {
      ClickableScriptPaneModel = clickableScriptPaneModel;
   }

   public ClickableScriptPaneModel getClickableScriptPaneModel() {
      return ClickableScriptPaneModel;
   }

   private TextInputGeneralPaneModel textInputGeneralPaneModel;
   private DataInputPaneModel dataInputPaneModel;
   private TextInputColumnOptionPaneModel textInputColumnOptionPaneModel;
   private ClickableScriptPaneModel ClickableScriptPaneModel;
}
