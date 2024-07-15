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
package inetsoft.web.admin.upload;

import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.util.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class UploadController {
   @Autowired
   public UploadController(UploadService service, SecurityEngine securityEngine) {
      this.service = service;
      this.securityEngine = securityEngine;
   }

   @GetMapping("/api/em/upload/maven-search")
   public MavenSearchResponse searchMaven(@RequestParam("q") String query) {
      List<String> results = service.search(query);
      return MavenSearchResponse.builder()
         .results(results)
         .build();
   }

   @PostMapping("/api/em/upload/maven")
   public UploadFilesResponse uploadMaven(@RequestBody MavenUploadRequest request,
                                          Principal principal) throws Exception
   {
      if(!checkPermission(principal)) {
         throw new SecurityException("You do not have permission to upload files.");
      }

      return service.add(request.gav());
   }

   @PostMapping("/api/em/upload")
   public UploadFilesResponse uploadFiles(
      @RequestParam("uploadedFiles") MultipartFile[] uploadedFiles,
      @RequestParam(name = "id", required = false) String id,
      Principal principal) throws Exception
   {
      if(!checkPermission(principal)) {
         throw new SecurityException("You do not have permission to upload files.");
      }

      List<UploadedFile> files = Arrays.stream(uploadedFiles)
         .map(this::uploadFile)
         .collect(Collectors.toList());

      if(id == null) {
         id = service.add(files);
         List<String> names = files.stream()
            .map(UploadedFile::fileName)
            .collect(Collectors.toList());
         return UploadFilesResponse.builder()
            .identifier(id)
            .files(names)
            .build();
      }
      else {
         List<String> names = service.add(id, files).stream()
            .map(UploadedFile::fileName)
            .collect(Collectors.toList());
         return UploadFilesResponse.builder()
            .identifier(id)
            .files(names)
            .build();
      }
   }

   private boolean checkPermission(Principal principal) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.EM, "*", ResourceAction.ACCESS) ||
            securityEngine.checkPermission(
               principal, ResourceType.UPLOAD_DRIVERS, "*", ResourceAction.ACCESS);
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check authorization", e);
         return false;
      }
   }

   private UploadedFile uploadFile(MultipartFile upload) {
      File file = FileSystemService.getInstance().getCacheTempFile("upload", ".dat");
      file.deleteOnExit();

      try(FileOutputStream out = new FileOutputStream(file)) {
         out.write(upload.getBytes());
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to copy upload to local file system", e);
      }

      return UploadedFile.builder()
         .fileName(Objects.requireNonNull(upload.getOriginalFilename()))
         .file(file)
         .build();
   }

   private final UploadService service;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(UploadController.class);
}
