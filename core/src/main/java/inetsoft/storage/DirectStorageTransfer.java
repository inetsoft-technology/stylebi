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
package inetsoft.storage;

import com.fasterxml.jackson.core.JsonGenerator;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * {@code DirectStorageTransfer} is an implementation of {@link StorageTransfer} that directly reads
 * from and writes to the storage engines.
 */
public class DirectStorageTransfer extends AbstractStorageTransfer {
   /**
    * Creates a new instance of {@code DirectStorageTransfer}.
    *
    * @param keyValueEngine the key-value storage engine.
    * @param blobEngine     the blob storage engine.
    */
   public DirectStorageTransfer(KeyValueEngine keyValueEngine, BlobEngine blobEngine) {
      this.keyValueEngine = keyValueEngine;
      this.blobEngine = blobEngine;
   }

   @Override
   protected Stream<String> getKeyValueStoreIds() {
      return keyValueEngine.idStream();
   }

   @Override
   protected void exportStore(String id, JsonGenerator kvGenerator, JsonGenerator blobGenerator,
                              Path blobDir)
   {
      AtomicBoolean first = new AtomicBoolean(true);
      AtomicBoolean blob = new AtomicBoolean(false);
      AtomicBoolean empty = new AtomicBoolean(true);

      keyValueEngine.stream(id).forEach(pair -> {
         try {
            export(id, pair, first.get(), kvGenerator, blobGenerator, blobDir);

            if(first.compareAndSet(true, false)) {
               blob.set(pair.getValue() instanceof Blob);
               empty.set(false);
            }
         }
         catch(RuntimeException e) {
            LoggerFactory.getLogger(AbstractStorageTransfer.class).error(e.getMessage(), e);
         }
      });

      try {
         if(!empty.get()) {
            if(blob.get()) {
               blobGenerator.writeEndObject();
            }
            else {
               kvGenerator.writeEndObject();
            }
         }
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to write JSON", e);
      }
   }

   private void export(String id, KeyValuePair<?> pair, boolean first,
                       JsonGenerator kvGenerator, JsonGenerator blobGenerator, Path blobDir)
   {
      try {
         boolean blob = pair.getValue() instanceof Blob;

         if(first) {
            if(blob) {
               blobGenerator.writeObjectFieldStart(id);
            }
            else {
               kvGenerator.writeObjectFieldStart(id);
            }
         }

         if(blob) {
            exportBlob(id, pair.getKey(), (Blob<?>) pair.getValue(), blobGenerator, blobDir);
         }
         else {
            Wrapper wrapper = new Wrapper(pair.getValue());
            kvGenerator.writeObjectField(pair.getKey(), wrapper);
         }
      }
      catch(IOException e) {
         throw new
            RuntimeException("Failed to export storage. ID:" + id + " Key:" + pair.getKey(), e);
      }
   }

   private void exportBlob(String id, String key, Blob<?> blob, JsonGenerator generator, Path dir)
      throws IOException
   {
      generator.writeObjectField(key, blob);
      String digest = blob.getDigest();

      if(digest != null) {
         String path = digest.substring(0, 2) + "/" + digest.substring(2);
         Path file = dir.resolve(path);
         Files.createDirectories(file.getParent());
         blobEngine.read(id, digest, file);
      }
   }

   @Override
   protected void putKeyValue(String id, String key, Object value) {
      keyValueEngine.put(id, key, value);
   }

   @Override
   protected void saveBlob(String id, String digest, Path file) throws IOException {
      blobEngine.write(id, digest, file);
   }

   private final KeyValueEngine keyValueEngine;
   private final BlobEngine blobEngine;
}
