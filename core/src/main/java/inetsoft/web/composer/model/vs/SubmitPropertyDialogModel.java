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

import java.io.Serializable;

/**
 * Data transfer object that represents the {@link SubmitPropertyDialogModel} for the
 * submit property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmitPropertyDialogModel implements Serializable {
   public SubmitGeneralPaneModel getSubmitGeneralPaneModel() {
      if(submitGeneralPaneModel == null) {
         submitGeneralPaneModel = new SubmitGeneralPaneModel();
      }

      return submitGeneralPaneModel;
   }

   public void setSubmitGeneralPaneModel(
      SubmitGeneralPaneModel submitGeneralPaneModel)
   {
      this.submitGeneralPaneModel = submitGeneralPaneModel;
   }

   public ClickableScriptPaneModel getClickableScriptPaneModel() {
      if(clickableScriptPaneModel == null) {
         clickableScriptPaneModel = ClickableScriptPaneModel.builder().build();
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
      return "SubmitPropertyDialogModel{" +
         "submitGeneralPaneModel=" + submitGeneralPaneModel +
         ", clickableScriptPaneModel=" + clickableScriptPaneModel +
         '}';
   }

   private SubmitGeneralPaneModel submitGeneralPaneModel;
   private ClickableScriptPaneModel clickableScriptPaneModel;
}
