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

@JsonIgnoreProperties(ignoreUnknown = true)
public class ListValuesPaneModel {
   public int getSortType() {
      return sortType;
   }

   public void setSortType(int sortType) {
      this.sortType = sortType;
   }

   public boolean isEmbeddedDataDown() {
      return embeddedDataDown;
   }

   public void setEmbeddedDataDown(boolean embeddedDataDown) {
      this.embeddedDataDown = embeddedDataDown;
   }

   public ComboBoxEditorModel getComboBoxEditorModel() {
      if(comboBoxEditorModel == null) {
         comboBoxEditorModel = new ComboBoxEditorModel();
      }

      return comboBoxEditorModel;
   }

   public void setComboBoxEditorModel(ComboBoxEditorModel comboBoxEditorModel) {
      this.comboBoxEditorModel = comboBoxEditorModel;
   }

   public boolean isSelectFirstItem() {
      return selectFirstItem;
   }

   public void setSelectFirstItem(boolean selectFirstItem) {
      this.selectFirstItem = selectFirstItem;
   }

   private int sortType;
   private boolean embeddedDataDown;
   private boolean selectFirstItem;
   private ComboBoxEditorModel comboBoxEditorModel;
}
