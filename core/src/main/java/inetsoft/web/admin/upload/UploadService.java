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
package inetsoft.web.admin.upload;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class UploadService {
   public UploadService(MavenClientService mavenClient) {
      this.mavenClient = mavenClient;
   }

   @PreDestroy
   public void removeAll() {
      for(String id : uploads.keySet()) {
         remove(id);
      }
   }

   @Scheduled(initialDelay = 300000L, fixedDelay = 300000L)
   public void removeExpired() {
      long limit = System.currentTimeMillis() - 300000L;

      for(Map.Entry<String, Upload> e : uploads.entrySet()) {
         if(e.getValue().accessed < limit) {
            remove(e.getKey());
         }
      }
   }

   public List<String> search(String query) {
      return mavenClient.search(query);
   }

   public UploadFilesResponse add(String gav) throws Exception {
      List<UploadedFile> files = mavenClient.resolve(gav);

      if(files.isEmpty()) {
         throw new FileNotFoundException(gav);
      }

      String id = add(files);
      return UploadFilesResponse.builder()
         .identifier(id)
         .files(files.stream().map(UploadedFile::fileName).collect(Collectors.toList()))
         .build();
   }

   public String add(List<UploadedFile> files) {
      String id = UUID.randomUUID().toString();
      Upload upload = new Upload();
      upload.accessed = System.currentTimeMillis();
      upload.files = files;
      uploads.put(id, upload);
      return id;
   }

   public List<UploadedFile> add(String id, List<UploadedFile> files) throws FileNotFoundException {
      Upload upload = uploads.get(id);

      if(upload == null) {
         throw new FileNotFoundException(id);
      }

      upload.accessed = System.currentTimeMillis();
      Map<String, UploadedFile> updated = new HashMap<>();

      for(UploadedFile file : upload.files) {
         updated.put(file.fileName(), file);
      }

      for(UploadedFile file : files) {
         updated.put(file.fileName(), file);
      }

      upload.files = new ArrayList<>(updated.values());
      return Collections.unmodifiableList(upload.files);
   }

   public Optional<List<UploadedFile>> get(String id) {
      Upload upload = uploads.get(id);

      if(upload == null) {
         return Optional.empty();
      }

      upload.accessed = System.currentTimeMillis();
      return Optional.of(upload.files);
   }

   public void remove(String id) {
      Upload upload = uploads.remove(id);

      if(upload != null) {
         for(UploadedFile file : upload.files) {
            try {
               Files.deleteIfExists(file.file().toPath());
            }
            catch(IOException e) {
               LOG.warn("Failed to delete temporary uploaded file", e);
            }
         }
      }
   }

   private final MavenClientService mavenClient;
   private final Map<String, Upload> uploads = new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(UploadService.class);

   private static final class Upload {
      long accessed;
      List<UploadedFile> files;
   }
}
