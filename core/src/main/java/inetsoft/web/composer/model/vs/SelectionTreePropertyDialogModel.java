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

public class SelectionTreePropertyDialogModel {
   public SelectionGeneralPaneModel getSelectionGeneralPaneModel() {
      if(selectionGeneralPaneModel == null) {
         selectionGeneralPaneModel = new SelectionGeneralPaneModel();
      }

      return selectionGeneralPaneModel;
   }

   public void setSelectionGeneralPaneModel(
      SelectionGeneralPaneModel selectionGeneralPaneModel)
   {
      this.selectionGeneralPaneModel = selectionGeneralPaneModel;
   }

   public SelectionTreePaneModel getSelectionTreePaneModel() {
      if(selectionTreePaneModel == null) {
         selectionTreePaneModel = new SelectionTreePaneModel();
      }
      return selectionTreePaneModel;
   }

   public void setSelectionTreePaneModel(SelectionTreePaneModel selectionTreepane) {
      this.selectionTreePaneModel = selectionTreepane;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel)
   {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   private SelectionGeneralPaneModel selectionGeneralPaneModel;
   private SelectionTreePaneModel selectionTreePaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
