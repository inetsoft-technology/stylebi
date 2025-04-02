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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.util.FileSystemService;
import inetsoft.util.SingletonManager;
import jakarta.annotation.PreDestroy;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class UploadService {
   public UploadService(MavenClientService mavenClient) {
      this.mavenClient = mavenClient;
      this.uploads = Cluster.getInstance().getMap(getClass().getName() + ".uploads");
   }

   @PreDestroy
   public void removeAll() {
      new ArrayList<>(uploads.keySet()) // copy to temp list before removing
         .forEach(this::remove);
   }

   @Scheduled(initialDelay = 300000L, fixedDelay = 300000L)
   public void removeExpired() {
      long limit = System.currentTimeMillis() - 300000L;
      uploads.entrySet().stream()
         .filter(e -> e.getValue().accessed < limit)
         .map(Map.Entry::getKey)
         .toList() // copy to temp list before removing
         .forEach(this::remove);
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
      upload.files = files.stream().map(f -> upload(id, f)).toList();
      uploads.put(id, upload);
      return id;
   }

   public List<UploadedFile> add(String id, List<UploadedFile> files) throws FileNotFoundException {
      Upload upload = uploads.get(id);

      if(upload == null) {
         throw new FileNotFoundException(id);
      }

      upload.accessed = System.currentTimeMillis();
      Map<String, UploadFileInfo> updated = new HashMap<>();

      for(UploadFileInfo file : upload.files) {
         updated.put(file.fileName, file);
      }

      for(UploadedFile file : files) {
         updated.put(file.fileName(), upload(id, file));
      }

      upload.files = new ArrayList<>(updated.values());
      uploads.put(id, upload);
      return upload.files.stream()
         .map(this::download)
         .toList();
   }

   public Optional<List<UploadedFile>> get(String id) {
      Upload upload = uploads.get(id);

      if(upload == null) {
         return Optional.empty();
      }

      upload.accessed = System.currentTimeMillis();

      if(upload.files == null) {
         return Optional.empty();
      }

      return Optional.of(upload.files.stream()
         .map(this::download)
         .toList());
   }

   public void remove(String id) {
      Upload upload = uploads.remove(id);

      if(upload != null) {
         for(UploadFileInfo info : upload.files) {
            try {
               processLocalFile(info, f -> {
                  if(Files.exists(f)) {
                     Files.delete(f);
                  }
               });
            }
            catch(IOException e) {
               LOG.warn("Failed to delete local file", e);
            }

            try {
               blobStorage.delete(info.blob);
            }
            catch(Exception e) {
               LOG.warn("Failed to delete blob", e);
            }
         }
      }
   }

   private UploadFileInfo upload(String id, UploadedFile file) {
      UploadFileInfo info = new UploadFileInfo();
      info.fileName = file.fileName();
      info.blob = id + "/" + file.fileName();

      if(file.multipartFile() != null) {
         upload(id, info, Objects.requireNonNull(file.multipartFile())::getInputStream);
      }
      else if(file.fileData() != null) {
         upload(id, info, () -> new ByteArrayInputStream(
            Base64.getDecoder().decode(Objects.requireNonNull(file.fileData()).content())));
      }
      else if(file.file() != null) {
         info.filePath = Objects.requireNonNull(file.file()).getAbsolutePath();

         try {
            store(info, () -> Files.newInputStream(Objects.requireNonNull(file.file()).toPath()));
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to upload file", e);
         }
      }
      else {
         throw new IllegalStateException("Either the file or multipart file must be set");
      }

      return info;
   }

   private void upload(String id, UploadFileInfo info, InputStreamSupplier input) {
      FileSystemService fsService = SingletonManager.getInstance(FileSystemService.class);
      File localFile = fsService.getCacheTempFile("upload-" + id, ".dat");

      try {
         info.filePath = fsService.getCacheFolder().toPath().toAbsolutePath()
            .relativize(localFile.toPath().toAbsolutePath()).toString();
         store(info, input);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to upload file", e);
      }
   }

   private void store(UploadFileInfo info, InputStreamSupplier input) throws IOException {
      try(InputStream in = input.get();
          BlobTransaction<Metadata> tx = blobStorage.beginTransaction();
          OutputStream out = tx.newStream(info.blob, new Metadata()))
      {
         IOUtils.copy(in, out);
         tx.commit();
      }
   }

   private UploadedFile download(UploadFileInfo info) {
      Path localFile;

      try {
         localFile = processLocalFile(info, f -> {
            if(!Files.exists(f)) {
               try(InputStream in = blobStorage.getInputStream(info.blob);
                   OutputStream out = Files.newOutputStream(f))
               {
                  IOUtils.copy(in, out);
               }
            }
         });
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to download file", e);
      }

      return UploadedFile.builder()
         .fileName(info.fileName)
         .file(localFile.toFile())
         .build();
   }

   private Path processLocalFile(UploadFileInfo info, LocalFileProcessor processor)
      throws IOException
   {
      Path localFile = Paths.get(info.filePath);

      if(!localFile.isAbsolute()) {
         localFile = FileSystemService.getInstance().getCacheFolder().toPath().resolve(localFile);
         processor.process(localFile);
      }

      return localFile;
   }

   private final MavenClientService mavenClient;
   private final DistributedMap<String, Upload> uploads;
   @SuppressWarnings("unchecked")
   private final BlobStorage<Metadata> blobStorage =
      SingletonManager.getInstance(BlobStorage.class, "fileUploads", false);
   private static final Logger LOG = LoggerFactory.getLogger(UploadService.class);

   public static final class Metadata implements Serializable {
   }

   @FunctionalInterface
   private interface LocalFileProcessor {
      void process(Path localFile) throws IOException;
   }

   @FunctionalInterface
   private interface InputStreamSupplier {
      InputStream get() throws IOException;
   }

   private static final class UploadFileInfo implements Serializable {
      String fileName;
      String filePath;
      String blob;
   }

   private static final class Upload {
      long accessed;
      List<UploadFileInfo> files;
   }
}
