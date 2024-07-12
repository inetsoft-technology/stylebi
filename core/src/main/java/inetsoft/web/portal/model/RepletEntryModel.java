/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model;

import inetsoft.sree.RepletEntry;
import inetsoft.sree.RepositoryEntry;
import org.springframework.stereotype.Component;

public class RepletEntryModel extends RepositoryEntryModel<RepletEntry> {
   public RepletEntryModel() {
   }

   public RepletEntryModel(RepletEntry entry) {
      super(entry);
      alias = entry.getAlias();
      description = entry.getDescription();
      pregenerated = entry.isPregenerated();
      paramOnly = entry.isParamOnly();
      fileReplet = entry.isFileReplet();
      setClassType("RepletEntry");
   }

   @Override
   public RepletEntry createRepositoryEntry() {
      RepletEntry entry = new RepletEntry();
      setProperties(entry);
      return entry;
   }

   @Override
   protected void setProperties(RepositoryEntry entry) {
      super.setProperties(entry);
      RepletEntry repletEntry = (RepletEntry) entry;
      repletEntry.setAlias(alias);
      repletEntry.setDescription(description);
   }

   public String getAlias() {
      return alias;
   }

   public String getDescription() {
      return description;
   }

   public boolean isPregenerated() {
      return pregenerated;
   }

   public boolean isParamOnly() {
      return paramOnly;
   }

   public boolean isFileReplet() {
      return fileReplet;
   }

   private String alias;
   private String description;
   private boolean pregenerated;
   private boolean paramOnly;
   private boolean fileReplet;

   @Component
   public static final class RepletEntryModelFactory
      extends RepositoryEntryModelFactory<RepletEntry, RepletEntryModel>
   {
      public RepletEntryModelFactory() {
         super(RepletEntry.class);
      }

      @Override
      public RepletEntryModel createModel(RepletEntry entry) {
         return new RepletEntryModel(entry);
      }
   }
}
