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

import inetsoft.sree.DefaultFolderEntry;
import inetsoft.sree.RepositoryEntry;
import org.springframework.stereotype.Component;

public class DefaultFolderEntryModel<T extends DefaultFolderEntry> extends RepositoryEntryModel<T> {
   public DefaultFolderEntryModel() {
   }

   public DefaultFolderEntryModel(T entry) {
      super(entry);
      alias = entry.getAlias();
      description = entry.getDescription();
      setClassType("DefaultFolderEntry");
   }

   @Override
   public DefaultFolderEntry createRepositoryEntry() {
      DefaultFolderEntry entry = new DefaultFolderEntry();
      setProperties(entry);
      return entry;
   }

   @Override
   protected void setProperties(RepositoryEntry entry) {
      super.setProperties(entry);
      DefaultFolderEntry defaultFolderEntry = (DefaultFolderEntry) entry;
      defaultFolderEntry.setAlias(alias);
      defaultFolderEntry.setDescription(description);
   }

   public String getAlias() {
      return alias;
   }

   public String getDescription() {
      return description;
   }

   private String alias;
   private String description;

   @Component
   public static final class DefaultFolderEntryModelFactory
      extends RepositoryEntryModelFactory<DefaultFolderEntry, DefaultFolderEntryModel<DefaultFolderEntry>>
   {
      public DefaultFolderEntryModelFactory() {
         super(DefaultFolderEntry.class);
      }

      @Override
      public DefaultFolderEntryModel createModel(DefaultFolderEntry entry) {
         return new DefaultFolderEntryModel(entry);
      }
   }
}
