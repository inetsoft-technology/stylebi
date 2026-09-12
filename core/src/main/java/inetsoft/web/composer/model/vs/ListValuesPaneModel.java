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

@JsonIgnoreProperties(ignoreUnknown = true)
public class ListValuesPaneModel implements Serializable {
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
