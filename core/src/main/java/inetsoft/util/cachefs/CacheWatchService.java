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

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class CacheWatchService
   implements WatchService, BlobStorage.Listener<CacheMetadata>
{
   CacheWatchService(BlobStorage<CacheMetadata> storage, PathService pathService) {
      this.storage = storage;
      this.pathService = pathService;
      storage.addListener(this);
   }

   @Override
   public void close() throws IOException {
      storage.removeListener(this);
   }

   @Override
   public WatchKey poll() {
      throw new UnsupportedOperationException();
   }

   @Override
   public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
      throw new UnsupportedOperationException();
   }

   @Override
   public WatchKey take() throws InterruptedException {
      throw new UnsupportedOperationException();
   }

   public WatchKey register(Watchable watchable,
                            Iterable<? extends WatchEvent.Kind<?>> eventTypes)
   {
      // todo register watcher
      throw new UnsupportedOperationException();
   }

   @Override
   public void blobAdded(BlobStorage.Event<CacheMetadata> event) {

   }

   @Override
   public void blobUpdated(BlobStorage.Event<CacheMetadata> event) {

   }

   @Override
   public void blobRemoved(BlobStorage.Event<CacheMetadata> event) {

   }

   private final BlobStorage<CacheMetadata> storage;
   private final PathService pathService;
}
