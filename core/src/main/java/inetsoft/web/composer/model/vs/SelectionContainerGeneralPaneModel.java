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

public class SelectionContainerGeneralPaneModel {
   public boolean isShowCurrentSelection() {
      return showCurrentSelection;
   }

   public void setShowCurrentSelection(boolean showCurrentSelection) {
      this.showCurrentSelection = showCurrentSelection;
   }

   public boolean isAdhocEnabled() {
      return adhocEnabled;
   }

   public void setAdhocEnabled(boolean adhocEnabled) {
      this.adhocEnabled = adhocEnabled;
   }

   public GeneralPropPaneModel getGeneralPropPaneModel() {
      if(generalPropPaneModel == null) {
         generalPropPaneModel = new GeneralPropPaneModel();
      }

      return generalPropPaneModel;
   }

   public void setGeneralPropPaneModel(GeneralPropPaneModel generalPropPaneModel) {
      this.generalPropPaneModel = generalPropPaneModel;
   }

   public TitlePropPaneModel getTitlePropPaneModel() {
      if(titlePropPaneModel == null) {
         titlePropPaneModel = new TitlePropPaneModel();
      }

      return titlePropPaneModel;
   }

   public void setTitlePropPaneModel(TitlePropPaneModel titlePropPaneModel) {
      this.titlePropPaneModel = titlePropPaneModel;
   }

   public SizePositionPaneModel getSizePositionPaneModel() {
      if(sizePositionPaneModel == null) {
         sizePositionPaneModel = new SizePositionPaneModel();
      }

      return sizePositionPaneModel;
   }

   private boolean showCurrentSelection;
   private boolean adhocEnabled;
   private GeneralPropPaneModel generalPropPaneModel;
   private TitlePropPaneModel titlePropPaneModel;
   private SizePositionPaneModel sizePositionPaneModel;
}
