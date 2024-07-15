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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.util.*;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.security.Principal;
import java.util.*;

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
                              ViewsheetService viewsheetService,
                              PlaceholderService placeholderService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.placeholderService = placeholderService;
   }

   /**
    * Upload the excel file.
    *
    * @param runtimeId The runtime viewsheet id
    * @param type      The file type (xls or xlsx)
    * @param file      The excel file to upload
    */
   @PostMapping(
      value = "/vs/importXLS/upload/{type}/**",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @ResponseBody
   public void processGetAssemblyImage(@RemainingPath String runtimeId,
                                       @PathVariable("type") String type,
                                       @RequestParam("file") MultipartFile file)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId).replace('/', '_');
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File temp = fileSystemService.getCacheFile(runtimeId + "_" + type);
      fileSystemService.remove(temp, 120000);
      FileOutputStream fileOutput = new FileOutputStream(temp);
      fileOutput.write(file.getBytes());
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
      String vid = runtimeViewsheetRef.getRuntimeId();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vid, principal);
      String fileName = vid.replace('/', '_') + "_" + type;
      File excelFile = FileSystemService.getInstance().getCacheFile(fileName);

      if(!excelFile.exists()) {
         String msg = catalog.getString("Upload Timeout");
         this.placeholderService
            .sendMessage(msg, MessageCommand.Type.WARNING, dispatcher);
         return;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      Set<String> notInRange = new HashSet<>();
      List<String> assemblies = new ArrayList<>();

      try {
         ImportXLSService.getInstance().updateViewsheet(
            excelFile, type, rvs, linkUri, dispatcher, placeholderService, catalog, assemblies,
            notInRange);
      }
      catch(FileNotFoundException e) {
         String msg = catalog.getString("vs.import.excel.unavailable");
         placeholderService.sendMessage(msg, MessageCommand.Type.WARNING, dispatcher);
         return;
      }

      checkError("viewer.import.notFound", assemblies, dispatcher);
      checkError("viewer.import.notInRange", notInRange, dispatcher);
   }

   /**
    * Popup a error if there are some error occurs.
    */
   private void checkError(String message, Collection<String> assemblies,
                           CommandDispatcher dispatcher)
   {
      if(!assemblies.isEmpty()) {
         String msg = "";
         Set<String> set = new HashSet<>(assemblies);

         for(Object name : set) {
            msg = "".equals(msg) ? name + "" : msg + ", " + name;
         }

         msg = catalog.getString(message, msg);
         this.placeholderService.sendMessage(msg, MessageCommand.Type.WARNING, dispatcher);
      }
   }

   private final Catalog catalog = Catalog.getCatalog();
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final PlaceholderService placeholderService;
}
