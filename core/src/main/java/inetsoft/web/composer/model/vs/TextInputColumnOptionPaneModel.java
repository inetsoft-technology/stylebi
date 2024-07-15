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
public class TextInputColumnOptionPaneModel {
   public TextEditorModel getTextEditorModel() {
      if(textEditorModel == null) {
         textEditorModel = new TextEditorModel();
      }

      return textEditorModel;
   }

   public void setTextEditorModel(TextEditorModel textEditorModel) {
      this.textEditorModel = textEditorModel;
   }

   public DateEditorModel getDateEditorModel() {
      if(dateEditorModel == null) {
         dateEditorModel = new DateEditorModel();
      }

      return dateEditorModel;
   }

   public void setDateEditorModel(DateEditorModel dateEditorModel) {
      this.dateEditorModel = dateEditorModel;
   }

   public IntegerEditorModel getIntegerEditorModel() {
      if(integerEditorModel == null) {
         integerEditorModel = new IntegerEditorModel();
      }

      return integerEditorModel;
   }

   public void setIntegerEditorModel(IntegerEditorModel integerEditorModel) {
      this.integerEditorModel = integerEditorModel;
   }

   public FloatEditorModel getFloatEditorModel() {
      if(floatEditorModel == null) {
         floatEditorModel = new FloatEditorModel();
      }

      return floatEditorModel;
   }

   public void setFloatEditorModel(FloatEditorModel floatEditorModel) {
      this.floatEditorModel = floatEditorModel;
   }

   public TextEditorModel getPasswordEditorModel() {
      if(passwordEditorModel == null) {
         passwordEditorModel = new TextEditorModel();
      }

      return passwordEditorModel;
   }

   public void setPasswordEditorModel(TextEditorModel passwordEditorModel) {
      this.passwordEditorModel = passwordEditorModel;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   private TextEditorModel textEditorModel;
   private DateEditorModel dateEditorModel;
   private IntegerEditorModel integerEditorModel;
   private FloatEditorModel floatEditorModel;
   private TextEditorModel passwordEditorModel;
   private String type;
}
