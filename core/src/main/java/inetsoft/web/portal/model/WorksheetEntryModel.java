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
import inetsoft.sree.WorksheetEntry;
import org.springframework.stereotype.Component;

public class WorksheetEntryModel extends RepositoryEntryModel<WorksheetEntry> {
   public WorksheetEntryModel(WorksheetEntry entry) {
      super(entry);
      materialized = getMaterialized(entry);
   }

   public boolean isMaterialized() {
      return materialized;
   }

   private boolean getMaterialized(WorksheetEntry entry) {
      return MVManager.getManager().isMaterialized(entry.getAssetEntry().toIdentifier(), true);
   }

   private boolean materialized;

   @Component
   public static final class WorksheetEntryModelFactory
      extends RepositoryEntryModelFactory<WorksheetEntry, WorksheetEntryModel>
   {
      public WorksheetEntryModelFactory() {
         super(WorksheetEntry.class);
      }

      @Override
      public WorksheetEntryModel createModel(WorksheetEntry entry) {
         return new WorksheetEntryModel(entry);
      }
   }
}
