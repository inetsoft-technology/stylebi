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

import java.io.Serializable;

public class GroupContainerGeneralPaneModel implements Serializable {

   public GeneralPropPaneModel getGeneralPropPane() {
      return generalPropPane;
   }

   public void setGeneralPropPane(GeneralPropPaneModel generalPropPane) {
      this.generalPropPane = generalPropPane;
   }

   public StaticImagePaneModel getStaticImagePane() {
      return staticImagePane;
   }

   public void setStaticImagePane(StaticImagePaneModel staticImagePane) {
      this.staticImagePane = staticImagePane;
   }

   public SizePositionPaneModel getSizePositionPaneModel() {
      if(sizePositionPaneModel == null) {
         sizePositionPaneModel = new SizePositionPaneModel();
      }

      return sizePositionPaneModel;
   }

   public void setSizePositionPaneModel(SizePositionPaneModel sizePositionPaneModel) {
      this.sizePositionPaneModel = sizePositionPaneModel;
   }

   private GeneralPropPaneModel generalPropPane;
   private StaticImagePaneModel staticImagePane;
   private SizePositionPaneModel sizePositionPaneModel;
}
