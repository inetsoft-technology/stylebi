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
package inetsoft.web.composer.model.ws;

import inetsoft.uql.asset.AssetEntry;

public class AssetRepositoryPaneModel {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getSelectedEntry() {
      return selectedEntry;
   }

   public void setSelectedEntry(String selectedEntry) {
      this.selectedEntry = selectedEntry;
   }

   public AssetEntry getParentEntry() {
      return parentEntry;
   }

   public void setParentEntry(AssetEntry parentEntry) {
      this.parentEntry = parentEntry;
   }

   private String name;
   private String selectedEntry;
   private AssetEntry parentEntry;
}
