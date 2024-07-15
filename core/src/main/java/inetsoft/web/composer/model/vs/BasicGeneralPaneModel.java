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
public class BasicGeneralPaneModel {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public boolean isNameEditable() {
      return this.nameEditable;
   }

   public void setNameEditable(boolean editable) {
      this.nameEditable = editable;
   }

   public String getVisible() {
      return visible;
   }

   public void setVisible(String visible) {
      this.visible = visible;
   }

   public boolean isShadow() {
      return shadow;
   }

   public void setShadow(boolean shadow) {
      this.shadow = shadow;
   }

   public boolean isEnabled() {
      return enabled;
   }

   public void setEnabled(boolean editable) {
      this.enabled = editable;
   }

   public boolean isPrimary() {
      return primary;
   }

   public void setPrimary(boolean primary) {
      this.primary = primary;
   }

   public boolean isRefresh() {
      return refresh;
   }

   public void setRefresh(boolean refresh) {
      this.refresh = refresh;
   }

   public boolean isEditable() {
      return editable;
   }

   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   public boolean isShowShadowCheckbox() {
      return showShadowCheckbox;
   }

   public void setShowShadowCheckbox(boolean showShadowCheckbox) {
      this.showShadowCheckbox = showShadowCheckbox;
   }

   public boolean isShowEnabledCheckbox() {
      return showEnabledCheckbox;
   }

   public void setShowEnabledCheckbox(boolean showEnabledCheckbox) {
      this.showEnabledCheckbox = showEnabledCheckbox;
   }

   public boolean isShowRefreshCheckbox() {
      return showRefreshCheckbox;
   }

   public void setShowRefreshCheckbox(boolean showRefreshCheckbox) {
      this.showRefreshCheckbox = showRefreshCheckbox;
   }

   public boolean isShowEditableCheckbox() {
      return showEditableCheckbox;
   }

   public void setShowEditableCheckbox(boolean showEditableCheckbox) {
      this.showEditableCheckbox = showEditableCheckbox;
   }

   public String[] getObjectNames() {
      return objectNames;
   }

   public void setObjectNames(String[] objectNames) {
      this.objectNames = objectNames;
   }

   @Override
   public String toString() {
      return "BasicGeneralPaneModel{" +
         "name='" + name + '\'' +
         ", visible=" + visible +
         ", shadow=" + shadow +
         ", editable=" + enabled +
         ", primary=" + primary +
         ", refresh=" + refresh +
         '}';
   }

   private String name;
   private boolean nameEditable = true;
   private String visible;
   private boolean shadow;
   private boolean enabled;
   private boolean primary;
   private boolean refresh;
   private boolean editable;
   private boolean showShadowCheckbox;
   private boolean showEnabledCheckbox;
   private boolean showRefreshCheckbox;
   private boolean showEditableCheckbox;
   private String[] objectNames = new String[0];
}
