/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.util.cachefs;

import inetsoft.storage.BlobStorage;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.lang.ref.Cleaner;

final class BlobOutputStream extends FilterOutputStream {
   public BlobOutputStream(CachePath path, CacheMetadata metadata,
                           BlobStorage<CacheMetadata> storage,
                           Cleaner cleaner)
      throws IOException
   {
      super(null);
      this.transaction = new TransactionSupport(storage, path, metadata, cleaner);
      out = transaction.openStream();
   }

   @Override
   public void close() throws IOException {
      super.close();
      transaction.commit();
   }

   private final TransactionSupport transaction;
}
