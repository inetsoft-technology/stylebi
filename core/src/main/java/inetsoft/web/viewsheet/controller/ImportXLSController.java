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
package inetsoft.web.viewsheet.controller;

import inetsoft.util.*;
import inetsoft.util.cachefs.CacheFS;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;

/**
 * Handles importing an excel file.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
@Controller
public class ImportXLSController {
   /**
    * Creates a new instance of <tt>ImportXLSController</tt>.
    */
   @Autowired
   public ImportXLSController(RuntimeViewsheetRef runtimeViewsheetRef,
                              ImportXLSControllerServiceProxy importXLSControllerServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.importXLSControllerServiceProxy = importXLSControllerServiceProxy;
   }

   /**
    * Upload the excel file.
    *
    * @param runtimeId The runtime viewsheet id
    * @param type      The file type (xls or xlsx)
    * @param file      The excel file to upload
    */
   @PostMapping(
      value = "/api/vs/importXLS/upload/{type}/**",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @ResponseBody
   public void processGetAssemblyImage(@RemainingPath String runtimeId,
                                       @PathVariable("type") String type,
                                       @RequestParam("file") MultipartFile file)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId).replace('/', '_');
      FileSystemService fileSystemService = FileSystemService.getInstance();

      String key = "/" + ImportXLSControllerService.class + "_" + runtimeId + "_" + type;
      Path path = CacheFS.getPath("tempStorage", key);
      fileSystemService.remove(path, 120000);

      try(OutputStream output = Files.newOutputStream(path)) {
         output.write(file.getBytes());
      }
   }

   /**
    * Process the uploaded excel file.
    * Bug #32583. add loading mask. show loading data page is better when excel too large.
    *
    * @param type       The excel file type.
    * @param principal  The user which is logged into the browser.
    * @param linkUri    The link URI.
    * @param dispatcher The command dispatcher.
    */
   @LoadingMask
   @MessageMapping("/vs/importXLS/{type}")
   public void processXLSUpload(@DestinationVariable("type") String type,
                                @LinkUri String linkUri,
                                Principal principal,
                                CommandDispatcher dispatcher)
      throws Exception
   {
      importXLSControllerServiceProxy.processXLSUpload(runtimeViewsheetRef.getRuntimeId(), type,
                                                       linkUri, principal, dispatcher);
   }


   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private ImportXLSControllerServiceProxy importXLSControllerServiceProxy;
}
