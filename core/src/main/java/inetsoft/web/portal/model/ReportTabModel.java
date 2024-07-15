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
package inetsoft.web.portal.model;

public class ReportTabModel {
   public boolean isExpandAllNodes() {
      return expandAllNodes;
   }

   public void setExpandAllNodes(boolean expandAllNodes) {
      this.expandAllNodes = expandAllNodes;
   }

   public boolean isShowRepositoryAsList() {
      return showRepositoryAsList;
   }

   public void setShowRepositoryAsList(boolean showRepositoryAsList) {
      this.showRepositoryAsList = showRepositoryAsList;
   }

   public boolean isSearchEnabled() {
      return searchEnabled;
   }

   public void setSearchEnabled(boolean searchEnabled) {
      this.searchEnabled = searchEnabled;
   }

   public String getWelcomePageUri() {
      return welcomePageUri;
   }

   public void setWelcomePageUri(String welcomePageUri) {
      this.welcomePageUri = welcomePageUri;
   }

   public String getLicensedComponentMsg() {
      return licensedComponentMsg;
   }

   public void setLicensedComponentMsg(String licensedComponentMsg) {
      this.licensedComponentMsg = licensedComponentMsg;
   }

   public boolean isDragAndDrop() {
      return dragAndDrop;
   }

   public void setDragAndDrop(boolean dragAndDrop) {
      this.dragAndDrop = dragAndDrop;
   }

   private boolean expandAllNodes;
   private boolean showRepositoryAsList;
   private boolean searchEnabled;
   private String welcomePageUri;
   private String licensedComponentMsg;
   private boolean dragAndDrop;
}
