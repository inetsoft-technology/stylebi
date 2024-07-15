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
package inetsoft.storage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.internal.cluster.Cluster;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ClusterStorageTransfer extends AbstractStorageTransfer {
   @Override
   protected Stream<String> getKeyValueStoreIds() {
      return KeyValueEngine.getInstance().idStream();
   }

   @Override
   protected void exportStore(String id, JsonGenerator kvGenerator, JsonGenerator blobGenerator,
                              Path blobDir)
   {
      try {
         String link = Cluster.getInstance().submit(id, new ExportStorageTask(id)).get();
         File zipFile = Cluster.getInstance().getTransferFile(link);
         List<KeyValuePair<?>> pairs = new ArrayList<>();
         boolean isBlob = false;

         try(ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry entry;

            while((entry = zip.getNextEntry()) != null) {
               if(entry.getName().equals("kv.json")) {
                  JsonNode kv = objectMapper.readTree(new FilterInputStream(zip) {
                     @Override
                     public void close() {
                        // don't allow the object mapper to close the zip stream
                     }
                  });
                  isBlob = kv.get("blob").asBoolean();

                  for(JsonNode pair : kv.get("pairs")) {
                     pairs.add(objectMapper.convertValue(pair, KeyValuePair.class));
                  }
               }
               else if(!entry.isDirectory() && entry.getName().startsWith("blobs/")) {
                  Path file = blobDir.resolve(entry.getName().substring(6));

                  if(!file.toFile().exists()) {
                     Files.createDirectories(file.getParent());
                     Files.copy(zip, file);
                  }
               }
            }
         }
         finally {
            FileUtils.deleteQuietly(zipFile);
         }

         JsonGenerator generator = isBlob ? blobGenerator : kvGenerator;
         generator.writeObjectFieldStart(id);

         for(KeyValuePair<?> pair : pairs) {
            Object value = isBlob ? pair.getValue() : new Wrapper(pair.getValue());
            generator.writeObjectField(pair.getKey(), value);
         }

         generator.writeEndObject();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to export store " + id, e);
      }
   }

   @Override
   protected void putKeyValue(String id, String key, Object value) {
      Cluster.getInstance().submit(id, new PutKeyValueTask<>(id, key, (Serializable) value));
   }

   @Override
   protected void saveBlob(String id, String digest, Path file) throws IOException {
      BlobEngine.getInstance().write(id, digest, file);
   }

   private final ObjectMapper objectMapper = KeyValueEngine.createObjectMapper();
}
