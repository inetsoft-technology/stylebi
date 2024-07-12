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
 * Data transfer object that represents the {@link UploadPropertyDialogModel} for the
 * upload property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadPropertyDialogModel {
   public UploadGeneralPaneModel getUploadGeneralPaneModel() {
      if(uploadGeneralPaneModel == null) {
         uploadGeneralPaneModel = new UploadGeneralPaneModel();
      }

      return uploadGeneralPaneModel;
   }

   public void setUploadGeneralPaneModel(
      UploadGeneralPaneModel uploadGeneralPaneModel)
   {
      this.uploadGeneralPaneModel = uploadGeneralPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel)
   {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   @Override
   public String toString() {
      return "UploadPropertyDialogModel{" +
         "uploadGeneralPaneModel=" + uploadGeneralPaneModel +
         ", vsAssemblyScriptPaneModel=" + vsAssemblyScriptPaneModel +
         '}';
   }

   private UploadGeneralPaneModel uploadGeneralPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
