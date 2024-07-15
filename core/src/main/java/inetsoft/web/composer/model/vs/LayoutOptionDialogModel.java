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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.uql.asset.AssetEntry;

import java.util.List;

/**
 * Data transfer object that represents the {@link LayoutOptionDialogModel} for the
 * layout option dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LayoutOptionDialogModel {
   public int getSelectedValue() {
      return selectedValue;
   }

   public void setSelectedValue(int selectedValue) {
      this.selectedValue = selectedValue;
   }

   public String getObject() {
      return object;
   }

   public void setObject(String object) {
      this.object = object;
   }

   public String getTarget() {
      return target;
   }

   public void setTarget(String target) {
      this.target = target;
   }

   public boolean isShowSelectionContainerOption() {
      return showSelectionContainerOption;
   }

   public void setShowSelectionContainerOption(boolean showSelectionContainerOption) {
      this.showSelectionContainerOption = showSelectionContainerOption;
   }

   public int getNewObjectType() {
      return newObjectType;
   }

   public void setNewObjectType(int newObjectType) {
      this.newObjectType = newObjectType;
   }

   public AssetEntry getVsEntry() {
      return vsEntry;
   }

   public void setVsEntry(AssetEntry vsEntry) {
      this.vsEntry = vsEntry;
   }

   public List<AssetEntry> getColumns() {
      return columns;
   }

   public void setColumns(List<AssetEntry> columns) {
      this.columns = columns;
   }

   @Override
   public String toString() {
      return "LayoutOptionDialogModel{" +
         " selectedValue=" + selectedValue +
         ", object=" + object +
         ", target=" + target +
         '}';
   }

   private int selectedValue;
   private String object;
   private String target;
   private boolean showSelectionContainerOption;
   private int newObjectType;
   private AssetEntry vsEntry;
   private List<AssetEntry> columns;
}
