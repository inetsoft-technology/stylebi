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
package inetsoft.web.admin.content.dataspace;

import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.graph.aesthetic.ImageShapes;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.content.dataspace.model.DataSpaceFolderSettingsModel;
import inetsoft.web.admin.content.dataspace.model.DataSpaceFolderUploadModel;
import inetsoft.web.admin.upload.UploadService;
import inetsoft.web.admin.upload.UploadedFile;
import inetsoft.web.security.auth.ResourceExistsException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
public class DataSpaceFolderSettingsController {
   @Autowired
   public DataSpaceFolderSettingsController(
      DataSpaceContentSettingsService dataSpaceContentSettingsService,
      DataSpaceFolderSettingsService dataSpaceFolderSettingsService,
      UploadService uploadService)
   {
      this.dataSpaceContentSettingsService = dataSpaceContentSettingsService;
      this.dataSpaceFolderSettingsService = dataSpaceFolderSettingsService;
      this.uploadService = uploadService;
   }

   @GetMapping("/api/em/content/data-space/folder/model")
   public DataSpaceFolderSettingsModel getModel(
      @DecodeParam("path") String path) throws Exception
   {
      String name = dataSpaceContentSettingsService.getFileName(path);
      return DataSpaceFolderSettingsModel.builder()
         .path(path)
         .name(name)
         .newName(name)
         .build();
   }

   @PostMapping("/api/em/content/data-space/folder/apply")
   public DataSpaceFolderSettingsModel apply(@RequestBody DataSpaceFolderSettingsModel model,
                                             Principal principal)
      throws Exception
   {
      DataSpace space = DataSpace.getDataSpace();
      String newPath = model.path();
      String objectType = ActionRecord.OBJECT_TYPE_FOLDER;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), null, null,
                                                   objectType, actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_SUCCESS, null);

      if(model.newFolder()) {
         newPath = dataSpaceContentSettingsService.getPath(model.path(), model.newName());
         actionRecord.setActionName(ActionRecord.ACTION_NAME_CREATE);
         actionRecord.setObjectName(newPath);

         if(space.exists(null, newPath)) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            throw new ResourceExistsException(newPath);
         }

         space.makeDirectory(newPath);
      }
      else {
         if(!model.name().equals(model.newName())) {
            newPath = model.path().substring(0, model.path().lastIndexOf(model.name())) + model.newName();
            boolean success = space.rename(model.path(), newPath);
            actionRecord.setActionName(ActionRecord.ACTION_NAME_RENAME);
            actionRecord.setObjectName(newPath);
            actionRecord.setActionError("new name:" + newPath);

            if(!success) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);

               if(space.exists(null, newPath)) {
                  throw new ResourceExistsException(newPath);
               }
               else {
                  throw new Exception(Catalog.getCatalog().getString(
                     "adm.dataspace.rename", model.name(), model.newName()));
               }
            }
         }
      }

      Audit.getInstance().auditAction(actionRecord, principal);
      return getModel(newPath);
   }

   @DeleteMapping("/api/em/content/data-space/folder")
   public void deleteDataSpaceFolder(@DecodeParam("path") String path) {
      this.dataSpaceContentSettingsService.deleteDataSpaceNode(path, true);
   }

   @PostMapping("/api/em/content/data-space/folder/upload")
   public void uploadDataSpaceFiles(@RequestBody DataSpaceFolderUploadModel model,
                                    Principal principal)
      throws Exception
   {
      String dir = "portal/shapes".equals(model.path()) ?
         ImageShapes.getShapesDirectory() : model.path();
      boolean extract = model.extractArchives();
      String uploadId = model.files();
      List<UploadedFile> uploadedFiles = uploadService.get(uploadId)
         .orElseThrow(() -> new IllegalArgumentException("No uploaded files"));
      DataSpace space = DataSpace.getDataSpace();

      try {
         for(UploadedFile uploadedFile : uploadedFiles) {
            String format;

            if(extract &&
               (format = DataSpaceFolderSettingsService.getArchiveFormat(uploadedFile.file())) != null)
            {
               try(ArchiveAdapter archive = ArchiveAdapter.newInstance(uploadedFile.file(), format)) {
                  ArchiveEntry entry;

                  while((entry = archive.getNextEntry()) != null) {
                     dataSpaceFolderSettingsService.writeArchiveEntry(entry,
                        archive.getInputStream(entry), dir);
                  }
               }
            }
            else {
               try(InputStream input = new FileInputStream(uploadedFile.file())) {
                  space.withOutputStream(
                     dir, uploadedFile.fileName(), os -> IOUtils.copy(input, os));
                  dataSpaceContentSettingsService.updateFolder(dir);
               }
            }
         }
      }
      finally {
         uploadService.remove(uploadId);
      }
   }

   @GetMapping("/em/content/data-space/folder/download")
   public void downloadDataSpaceFolder(@RequestParam(value = "path") String path,
                                       @RequestParam(value = "name") String name,
                                       HttpServletResponse response)
      throws IOException
   {
      response.setContentType("application/zip");
      response.setHeader(
         "Content-disposition",
         "attachment; filename=\"" + Tool.cleanseCRLF(Tool.byteDecode(name)) + ".zip\"");
      path = Tool.byteDecode(path);

      DataSpace dataSpace = DataSpace.getDataSpace();
      List<String> pathList = Arrays.asList(dataSpace.list(path));

      if(!path.equals("/")) {
         String finalPath = path;
         pathList = pathList.stream().map(p -> finalPath + "/" + p).collect(Collectors.toList());
      }

      Deque<String> paths = new ArrayDeque<>(pathList);
      ZipOutputStream zip = new ZipOutputStream(response.getOutputStream());

      if(paths.isEmpty()) {
         String folder = path;

         if(path.equals("/")) {
            folder = "Storage";
         }

         zip.putNextEntry(new ZipEntry(folder + "/"));
         zip.closeEntry();
      }

      while(!paths.isEmpty()) {
         String subPath = paths.removeFirst();

         if(dataSpace.isDirectory(subPath)) {
            String[] subPaths = dataSpace.list(subPath);

            if(subPaths.length == 0) {
               zip.putNextEntry(new ZipEntry(subPath + "/"));
               zip.closeEntry();
            }

            for(String child : subPaths) {
               paths.addLast(subPath + "/" + child);
            }
         }
         else {
            int index = subPath.lastIndexOf("/");
            String dir = index < 0 ? null : subPath.substring(0, index);
            String file = index < 0 ? subPath : subPath.substring(index + 1);

            try(InputStream input = dataSpace.getInputStream(dir, file)) {
               ZipEntry entry = new ZipEntry(subPath);
               zip.putNextEntry(entry);
               IOUtils.copy(input, zip);
               zip.closeEntry();
            }
         }
      }

      zip.finish();
   }

   private final DataSpaceContentSettingsService dataSpaceContentSettingsService;
   private final DataSpaceFolderSettingsService dataSpaceFolderSettingsService;
   private final UploadService uploadService;
}
