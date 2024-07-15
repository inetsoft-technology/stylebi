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

import inetsoft.web.composer.model.TreeNodeModel;

import java.util.ArrayList;
import java.util.List;

public class LocalizationPaneModel {
   public TreeNodeModel getComponents() {
      return components;
   }

   public void setComponents(TreeNodeModel components) {
      this.components = components;
   }

   public List<LocalizationComponent> getLocalized() {
      if(localized == null) {
         this.localized = new ArrayList<>();
      }

      return localized;
   }

   public void setLocalized(List<LocalizationComponent> localized) {
      this.localized = localized;
   }

   private TreeNodeModel components;
   private List<LocalizationComponent> localized;
}