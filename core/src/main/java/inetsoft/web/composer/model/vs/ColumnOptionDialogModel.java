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

@JsonIgnoreProperties(ignoreUnknown = true)
public class ColumnOptionDialogModel {

   public boolean isEnableColumnEditing() {
      return enableColumnEditing;
   }

   public void setEnableColumnEditing(boolean enableColumnEditing) {
      this.enableColumnEditing = enableColumnEditing;
   }

   public String getInputControl() {
      return inputControl;
   }

   public void setInputControl(String inputControl) {
      this.inputControl = inputControl;
   }

   public Object getEditor() {
      return editor;
   }

   public void setEditor(EditorModel editor) {
      this.editor = editor;
   }

   public ComboBoxEditorModel getComboBoxBlankEditor() {
      return comboBoxBlankEditor;
   }

   public void setComboBoxBlankEditor(ComboBoxEditorModel comboBoxBlankEditor) {
      this.comboBoxBlankEditor = comboBoxBlankEditor;
   }

   private boolean enableColumnEditing;
   private String inputControl;
   private EditorModel editor;
   private ComboBoxEditorModel comboBoxBlankEditor;
}
