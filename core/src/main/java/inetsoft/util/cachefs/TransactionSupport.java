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

import inetsoft.storage.*;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Cleaner;
import java.util.*;

class TransactionSupport {
   public TransactionSupport(BlobStorage<CacheMetadata> storage, CachePath path, CacheMetadata metadata, Cleaner cleaner) {
      this.storage = storage;
      this.metadata = metadata;
      this.path = path.toAbsolutePath().toString();
      this.parent = path.toAbsolutePath().getParent().toString();
      this.name = path.toAbsolutePath().getFileName().toString();
      this.transaction = storage.beginTransaction();
      this.cleanable = cleaner.register(this, new TransactionCleanAction(transaction));
   }

   public OutputStream openStream() throws IOException {
      try {
         return transaction.newStream(path, metadata);
      }
      catch(IOException e) {
         cleanable.clean();
         throw e;
      }
   }

   public BlobChannel openChannel() throws IOException {
      try {
         return transaction.newChannel(path, metadata);
      }
      catch(IOException e) {
         cleanable.clean();
         throw e;
      }
   }

   public void commit() throws IOException {
      try {
         transaction.commit();
         CacheMetadata metadata = storage.getMetadata(parent);

         if(metadata.getChildren() != null) {
            Set<String> children = new TreeSet<>(Arrays.asList(metadata.getChildren()));
            children.add(name);
            metadata.setChildren(children.toArray(new String[0]));
         }

         storage.createDirectory(parent, metadata);
      }
      finally {
         cleanable.clean();
      }
   }

   private final BlobStorage<CacheMetadata> storage;
   private final CacheMetadata metadata;
   private final BlobTransaction<CacheMetadata> transaction;
   private final String path;
   private final String parent;
   private final String name;
   private final Cleaner.Cleanable cleanable;
}
