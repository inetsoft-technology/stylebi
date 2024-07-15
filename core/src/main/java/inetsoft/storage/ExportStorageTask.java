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
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.SingletonCallableTask;
import inetsoft.util.FileSystemService;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@code ExportStorageTask} is a cluster singleton task that exports all the data in a key-value or
 * blob store.
 */
public class ExportStorageTask implements SingletonCallableTask<String> {
   /**
    * Creates a new instance of {@code ExportStorageTask}.
    *
    * @param id the unique identifier of the store.
    */
   public ExportStorageTask(String id) {
      this.id = id;
   }

   @Override
   public String call() throws Exception {
      File tempFile = FileSystemService.getInstance()
         .getCacheTempFile("storage-transfer", ".zip");

      try(ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tempFile.toPath()))) {
         exportStore(zip);
      }

      return Cluster.getInstance().addTransferFile(tempFile);
   }

   private void exportStore(ZipOutputStream zip) throws IOException {
      ObjectMapper objectMapper = KeyValueEngine.createObjectMapper();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      JsonGenerator generator = objectMapper.getFactory().createGenerator(buffer);
      generator.writeStartObject();
      generator.writeArrayFieldStart("pairs");

      Set<String> created = new HashSet<>();
      KeyValueEngine.getInstance().stream(id)
         .forEach(pair -> {
            try {
               exportPair(pair, generator, zip, created);
            }
            catch(RuntimeException e) {
               LoggerFactory.getLogger(ExportStorageTask.class).error(e.getMessage(), e);
            }
         });

      generator.writeEndArray();
      generator.writeBooleanField("blob", !created.isEmpty());
      generator.writeEndObject();
      generator.flush();

      ZipEntry entry = new ZipEntry("kv.json");
      zip.putNextEntry(entry);
      IOUtils.copy(new ByteArrayInputStream(buffer.toByteArray()), zip);
      zip.closeEntry();
   }

   private void exportPair(KeyValuePair<?> pair, JsonGenerator generator, ZipOutputStream zip,
                           Set<String> created)
   {
      try {
         generator.writeObject(pair);

         if(pair.getValue() instanceof Blob) {
            if(!created.contains("blobs/")) {
               created.add("blobs/");
               ZipEntry entry = new ZipEntry("blobs/");
               zip.putNextEntry(entry);
               zip.closeEntry();
            }

            Blob<?> blob = (Blob<?>) pair.getValue();
            String digest = blob.getDigest();

            if(digest != null) {
               String dir = "blobs/" + digest.substring(0, 2) + "/";

               if(!created.contains(dir)) {
                  created.add(dir);
                  ZipEntry entry = new ZipEntry(dir);
                  zip.putNextEntry(entry);
                  zip.closeEntry();
               }

               String path = "blobs/" + digest.substring(0, 2) + "/" + digest.substring(2);

               if(!created.contains(path)) {
                  created.add(path);
                  ZipEntry entry = new ZipEntry(path);
                  zip.putNextEntry(entry);

                  Path temp = Files.createTempFile("storage-export", ".dat");

                  try {
                     BlobEngine.getInstance().read(id, digest, temp);
                     Files.copy(temp, zip);
                     zip.closeEntry();
                  }
                  finally {
                     Files.delete(temp);
                  }
               }
            }
         }
      }
      catch(IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   private final String id;
}
