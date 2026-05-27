/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.storage;

import java.io.Serializable;
import java.util.Set;

/**
 * Message to clear multiple blob cache entries in a single operation.
 */
public class ClearAllBlobCacheMessage implements Serializable {
   public ClearAllBlobCacheMessage(String storeId, Set<String> digests) {
      this.storeId = storeId;
      this.digests = digests;
   }

   public String getStoreId() {
      return storeId;
   }

   public Set<String> getDigests() {
      return digests;
   }

   private final String storeId;
   private final Set<String> digests;
   private static final long serialVersionUID = 1L;
}
