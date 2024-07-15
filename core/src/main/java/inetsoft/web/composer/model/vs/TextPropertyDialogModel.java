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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data transfer object that represents the {@link TextPropertyDialogModel} for the
 * text property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TextPropertyDialogModel {
   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public TextGeneralPaneModel getTextGeneralPaneModel() {
      if(textGeneralPaneModel == null) {
         textGeneralPaneModel = new TextGeneralPaneModel();
      }

      return textGeneralPaneModel;
   }

   public void setTextGeneralPaneModel(
      TextGeneralPaneModel textGeneralPaneModel)
   {
      this.textGeneralPaneModel = textGeneralPaneModel;
   }

   public DataOutputPaneModel getDataOutputPaneModel() {
      if(dataOutputPaneModel == null) {
         dataOutputPaneModel = new DataOutputPaneModel();
      }

      return dataOutputPaneModel;
   }

   public void setDataOutputPaneModel(
      DataOutputPaneModel dataOutputPaneModel)
   {
      this.dataOutputPaneModel = dataOutputPaneModel;
   }

   public ClickableScriptPaneModel getClickableScriptPaneModel() {
      if(clickableScriptPaneModel == null) {
         clickableScriptPaneModel = ClickableScriptPaneModel.builder()
            .scriptExpression("")
            .onClickExpression("")
            .scriptEnabled(false)
            .build();
      }

      return clickableScriptPaneModel;
   }

   public void setClickableScriptPaneModel(
      ClickableScriptPaneModel clickableScriptPaneModel)
   {
      this.clickableScriptPaneModel = clickableScriptPaneModel;
   }

   @Override
   public String toString() {
      return "TextPropertyDialogModel{" +
         "id='" + id + '\'' +
         ", textGeneralPaneModel=" + textGeneralPaneModel +
         ", dataOutputPaneModel=" + dataOutputPaneModel +
         ", clickableScriptPaneModel=" + clickableScriptPaneModel +
         '}';
   }

   private String id;
   private TextGeneralPaneModel textGeneralPaneModel;
   private DataOutputPaneModel dataOutputPaneModel;
   private ClickableScriptPaneModel clickableScriptPaneModel;
}