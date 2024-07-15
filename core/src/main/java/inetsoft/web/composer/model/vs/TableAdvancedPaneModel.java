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
public class TableAdvancedPaneModel {
   public TipPaneModel getTipPaneModel() {
      if(tipPaneModel == null) {
         tipPaneModel = new TipPaneModel();
      }
      return tipPaneModel;
   }

   public void setTipPaneModel(TipPaneModel tipPaneModel) {
      this.tipPaneModel = tipPaneModel;
   }

   public boolean isFormVisible() {
      return formVisible;
   }

   public void setFormVisible(boolean formVisible) {
      this.formVisible = formVisible;
   }

   public boolean isEmbeddedEnabled() {
      return embeddedEnabled;
   }

   public void setEmbeddedEnabled(boolean embeddedEnabled) {
      this.embeddedEnabled = embeddedEnabled;
   }

   public boolean isShrinkEnabled() {
      return shrinkEnabled;
   }

   public void setShrinkEnabled(boolean shrinkEnabled) {
      this.shrinkEnabled = shrinkEnabled;
   }

   public boolean isShrink() {
      return shrink;
   }

   public void setShrink(boolean shrink) {
      this.shrink = shrink;
   }

   public boolean isEmbeddedTable() {
      return embeddedTable;
   }

   public void setEmbeddedTable(boolean embeddedTable) {
      this.embeddedTable = embeddedTable;
   }

   public boolean isEnableAdhoc() {
      return enableAdhoc;
   }

   public void setEnableAdhoc(boolean enableAdhoc) {
      this.enableAdhoc = enableAdhoc;
   }

   public boolean isForm() {
      return form;
   }

   public void setForm(boolean form) {
      this.form = form;
   }

   public boolean isInsert() {
      return insert;
   }

   public void setInsert(boolean insert) {
      this.insert = insert;
   }

   public boolean isDel() {
      return del;
   }

   public void setDel(boolean del) {
      this.del = del;
   }

   public boolean isEdit() {
      return edit;
   }

   public void setEdit(boolean edit) {
      this.edit = edit;
   }

   public boolean isWriteBack() {
      return writeBack;
   }

   public void setWriteBack(boolean writeBack) {
      this.writeBack = writeBack;
   }

   private boolean formVisible;
   private boolean embeddedEnabled;
   private boolean shrinkEnabled;
   private boolean shrink;
   private boolean embeddedTable;
   private boolean enableAdhoc;
   private boolean form;
   private boolean insert;
   private boolean del;
   private boolean edit;
   private boolean writeBack;
   private TipPaneModel tipPaneModel;
}