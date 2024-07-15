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
package inetsoft.util;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.internal.AssetFolder;

/**
 * {@code MetadataAwareStorage} is an interface for {@link IndexedStorage} implementations that are
 * aware of the metadata for the assets it stores.
 */
public interface MetadataAwareStorage {
   /**
    * Gets the metadata for the asset with the specified identifier.
    *
    * @param id the asset identifier.
    *
    * @return the asset entry.
    */
   AssetEntry getAssetEntry(String id);

   /**
    * Gets the metadata for the folder with the specified identifier.
    *
    * @param id the folder identifier.
    *
    * @return the asset folder.
    */
   AssetFolder getAssetFolder(String id);

   /**
    * Determines if obtaining the metadata directly is enabled for this storage.
    *
    * @return {@code true} if {@link #getAssetEntry(String)} and {@link #getAssetFolder(String)}
    *         will return a value or {@code false} if they will not.
    */
   boolean isMetadataEnabled();
}
