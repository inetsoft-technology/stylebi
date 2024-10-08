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
package inetsoft.web.admin.content.repository;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.util.Catalog;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@Value.Modifiable
@JsonSerialize(as = ImmutableRestoreAssetTreeModel.class)
@JsonDeserialize(as = ImmutableRestoreAssetTreeModel.class)
public interface RestoreAssetTreeModel {
   String id();
   String label();
   boolean folder();
   List<RestoreAssetTreeModel> children();

   static RestoreAssetTreeModel.Builder builder() {
      return new RestoreAssetTreeModel.Builder();
   }

   final class Builder extends ImmutableRestoreAssetTreeModel.Builder {
      public RestoreAssetTreeModel.Builder from(RepositoryEntry entry,
                                                List<RestoreAssetTreeModel> children,
                                                Catalog catalog, String user)
      {
         if(entry instanceof ViewsheetEntry) {
            ViewsheetEntry vsEntry = (ViewsheetEntry) entry;
            String label = vsEntry.getName();

            id(vsEntry.getAssetEntry().toIdentifier());
            label(catalog.getString(label));
            folder(false);
            children(children);
         }
         else if(entry instanceof DefaultFolderEntry){
            DefaultFolderEntry fEntry =(DefaultFolderEntry) entry;
            String label = fEntry.getName();

            if(fEntry.isMyReport() && label != null && label.equals(SUtil.MY_REPORT)) {
               label = user;
            }

            id(fEntry.getPath());
            label(catalog.getString(label));
            folder(true);
            children(children);
         }

         return this;
      }
   }
}
