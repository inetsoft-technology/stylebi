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
package inetsoft.web.portal.model;

import inetsoft.mv.MVManager;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.ViewsheetEntry;
import org.springframework.stereotype.Component;

public class ViewsheetEntryModel extends RepositoryEntryModel<ViewsheetEntry> {
   public ViewsheetEntryModel() {
   }

   public ViewsheetEntryModel(ViewsheetEntry entry) {
      super(entry);
      alias = entry.getAlias();
      description = entry.getDescription();
      snapshot = entry.isSnapshot();
      setClassType("ViewsheetEntry");
      materialized = getMaterialized(entry);
   }

   @Override
   public ViewsheetEntry createRepositoryEntry() {
      ViewsheetEntry entry = new ViewsheetEntry();
      setProperties(entry);
      return entry;
   }

   @Override
   protected void setProperties(RepositoryEntry entry) {
      super.setProperties(entry);
      ViewsheetEntry viewsheetEntry = (ViewsheetEntry) entry;
      viewsheetEntry.setAlias(alias);
      viewsheetEntry.setDescription(description);
   }

   public String getAlias() {
      return alias;
   }

   public String getDescription() {
      return description;
   }

   public boolean isSnapshot() {
      return snapshot;
   }

   public boolean isMaterialized() {
      return materialized;
   }

   private boolean getMaterialized(ViewsheetEntry entry) {
      return MVManager.getManager().isMaterialized(entry.getAssetEntry().toIdentifier(), false);
   }

   private String alias;
   private String description;
   private boolean snapshot;
   private boolean materialized;

   @Component
   public static final class ViewsheetEntryModelFactory
      extends RepositoryEntryModelFactory<ViewsheetEntry, ViewsheetEntryModel>
   {
      public ViewsheetEntryModelFactory() {
         super(ViewsheetEntry.class);
      }

      @Override
      public ViewsheetEntryModel createModel(ViewsheetEntry entry) {
         return new ViewsheetEntryModel(entry);
      }
   }
}
