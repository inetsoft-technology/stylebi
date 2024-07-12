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

import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.binding.model.ChartBindingModel;

import javax.annotation.Nullable;

public class UpdateVsWizardBindingEvent {
   /**
    * get temporary chart bindingModel
    *
    * @return bindingModel
    */
   public ChartBindingModel getBindingModel() {
      return bindingModel;
   }

   /**
    * set temporary chart bindingModel
    *
    * @param bindingModel trmporary chart binding model
    */
   public void setBindingModel(ChartBindingModel bindingModel) {
      this.bindingModel = bindingModel;
   }

   /**
    * get selectNodes for vs wizard
    *
    * @return selectNodes
    */
   public AssetEntry[] getSelectedNodes() {
      return selectedNodes;
   }

   /**
    * set selectNodes for vs wizard
    *
    * @param selectedNodes form binding tree
    */
   public void setSelectedNodes(AssetEntry[] selectedNodes) {
      this.selectedNodes = selectedNodes;
   }

   public String getDeleteFormatColumn() {
      return deleteFormatColumn;
   }

   public void setDeleteFormatColumn(String deleteFormatColumn) {
      this.deleteFormatColumn = deleteFormatColumn;
   }

   public boolean isAutoOrder() {
      return autoOrder;
   }

   @Nullable
   public void setAutoOrder(boolean autoOrder) {
      this.autoOrder = autoOrder;
   }

   private ChartBindingModel bindingModel;
   private AssetEntry[] selectedNodes;
   private String deleteFormatColumn;
   private boolean autoOrder;
}
