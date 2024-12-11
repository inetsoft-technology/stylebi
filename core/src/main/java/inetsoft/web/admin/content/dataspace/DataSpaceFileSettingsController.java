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
package inetsoft.web.admin.content.dataspace;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.adhoc.DecodeParam;
import inetsoft.web.admin.content.dataspace.model.*;
import inetsoft.web.security.auth.ResourceExistsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.Principal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class DataSpaceFileSettingsController {
   @Autowired
   public DataSpaceFileSettingsController(DataSpaceContentSettingsService dataSpaceContentSettingsService) {
      this.dataSpaceContentSettingsService = dataSpaceContentSettingsService;
   }

   @GetMapping("/api/em/content/data-space/file/model")
   public DataSpaceFileSettingsModel getModel(
      @DecodeParam(value = "path", required = false) String path)
   {
      DataSpace space = DataSpace.getDataSpace();
      Date time = new Date(space.getLastModified(null, path));
      SimpleDateFormat sformat = new SimpleDateFormat(SreeEnv.getProperty("format.date.time"));
      String lmt = sformat.format(time);

      //feature1377528401535 by justinRokisky - display fileSize in Dataspace
      long fSize = space.getFileLength(null, path);
      long fSizeKb = fSize / 1024;

      if(fSize % 1024 > 0) {
         fSizeKb += 1;
      }

      String fileSize = fSizeKb + " KB";
      String name = dataSpaceContentSettingsService.getFileName(path);
      String displayName = dataSpaceContentSettingsService.getDisplayName(name);
      String displayPath = dataSpaceContentSettingsService.getDisplayPath(path, name, displayName);

      return DataSpaceFileSettingsModel.builder()
         .label(displayName)
         .name(name)
         .path(path)
         .displayPath(displayPath)
         .lastModified(lmt)
         .size(fileSize)
         .build();
   }

   @GetMapping("/em/content/data-space/file/download")
   public void downloadFile(@DecodeParam("path") String path,
                            @DecodeParam("name") String name,
                            HttpServletResponse response,
                            HttpServletRequest request) throws Exception
   {
      dataSpaceContentSettingsService.downloadFile(path, name, response, request);
   }

   @PostMapping("/api/em/content/data-space/file/apply")
   public DataSpaceFileSettingsModel apply(@RequestBody ChangeDataSpaceFileRequest request,
                                           Principal principal)
      throws Exception
   {
      if(request.content() != null) {
         saveFileContent(request.content());
      }

      String path = request.path();

      DataSpace space = DataSpace.getDataSpace();
      Catalog catalog = Catalog.getCatalog();
      String objectType = ActionRecord.OBJECT_TYPE_FILE;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_EDIT, path,
                                                   objectType, actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_SUCCESS, null);

      if(request.newFile()) {
         path = dataSpaceContentSettingsService.getPath(path, request.name());
         actionRecord.setActionName(ActionRecord.ACTION_NAME_CREATE);
         actionRecord.setObjectName(path);

         if(space.exists(null, path)) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            throw new ResourceExistsException(path);
         }
      }
      else {
         if(!request.newName().equals(request.name())) {
            String npath =
               dataSpaceContentSettingsService.getNewPath(path, request.name(), request.newName());
            boolean success = space.rename(path, npath);
            actionRecord.setActionName(ActionRecord.ACTION_NAME_RENAME);
            actionRecord.setObjectName(path);
            actionRecord.setActionError("new name:" + npath);

            if(!success) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);

               if(space.exists(null, npath)) {
                  throw new ResourceExistsException(npath);
               }
               else {
                  throw new Exception(catalog.getString(
                     "adm.dataspace.rename", request.name(), request.newName()));
               }
            }
            else {
               actionRecord.setActionName(ActionRecord.ACTION_NAME_EDIT);
               path = npath;

            }
         }
      }

      if(request.fileData() != null || request.newFile()) {
         try(DataSpace.Transaction tx = space.beginTransaction();
             OutputStream out = tx.newStream(null, path))
         {
            if(request.fileData() != null) {
               byte[] data = Base64.getDecoder().decode(Objects.requireNonNull(request.fileData()));
               Tool.fileCopy(new ByteArrayInputStream(data), out);
            }

            tx.commit();
         }
         catch(Throwable e) {
            LOG.error("Failed to write data page", e);
            throw e;
         }
      }

      this.dataSpaceContentSettingsService.updateFolder(path);
      Audit.getInstance().auditAction(actionRecord, principal);

      return getModel(path);
   }

   @DeleteMapping("/api/em/content/data-space/file")
   public void deleteDataSpaceFile(@DecodeParam("path") String path) {
      this.dataSpaceContentSettingsService.deleteDataSpaceNode(path, false);
      this.dataSpaceContentSettingsService.updateFolder(path);
   }

   @PostMapping("/api/em/content/data-space/file/content")
   public void saveFileContent(@RequestBody DataSpaceFileContentModel model) {
      DataSpace dataSpace = DataSpace.getDataSpace();
      InputStream in = new ByteArrayInputStream(model.content().getBytes(StandardCharsets.UTF_8));

      try {
         dataSpace.withOutputStream(null, model.path(), out -> Tool.fileCopy(in, out));
      }
      catch(Exception e) {
         LOG.error("Error saving file: " + e);
      }
   }

   @GetMapping("/api/em/content/data-space/file/content")
   public DataSpaceFileContentModel getFileContent(@DecodeParam("path") String path,
                                                   @RequestParam(value = "preview", required = false, defaultValue = "true") boolean preview)
   {
      boolean editable = false;
      String content = "";

      if(path != null) {
         editable = getEditable(path);
      }

      if(editable) {
         content = getContentFromPath(path, preview);
      }

      return DataSpaceFileContentModel.builder()
         .content(content)
         .path(path)
         .editable(editable)
         .build();
   }

   private boolean getEditable(String path) {
      boolean fileType = false;

      try {
         fileType = probeContentType(path);
      }
      catch(Exception e) {
         LOG.error("Error getting edit property: " + e.toString());
      }

      return fileType;
   }

   private boolean probeContentType(String path) throws IOException {
      DataSpace ds = DataSpace.getDataSpace();

      if(path.endsWith("db") || path.endsWith("dat")) {
         return false;
      }

      try {
         if(!ds.exists(null, path) || ds.isDirectory(path)) {
            return false;
         }

         try(InputStream in = ds.getInputStream(null, path)) {
            Charset.availableCharsets()
               .get("UTF-8")
               .newDecoder()
               .decode(ByteBuffer.wrap(IOUtils.toByteArray(in)));
         }

         return true;
      }
      catch(CharacterCodingException e) {
         return false;
      }
   }

   private String getContentFromPath(String path, boolean preview) {
      DataSpace dataSpace = DataSpace.getDataSpace();
      String content = "";

      try(InputStream in = dataSpace.getInputStream(null, path)) {
         BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

         if(preview) {
            char[] buf = new char[512];
            br.read(buf);
            content = new String(buf);

            if(br.read() != -1) {
               int lastLine = content.lastIndexOf("\n");

               if(lastLine >= 0) {
                  content = content.substring(0, lastLine);
               }
            }
         }
         else {
            content = br.lines().collect(Collectors.joining(System.lineSeparator()));
         }
      }
      catch(IOException e) {
         LOG.error(e.toString());
      }

      return content;
   }

   private final DataSpaceContentSettingsService dataSpaceContentSettingsService;
   private static final Logger LOG = LoggerFactory.getLogger(DataSpaceFileSettingsController.class);
}
