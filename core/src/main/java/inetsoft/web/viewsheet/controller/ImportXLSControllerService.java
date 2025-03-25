/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.util.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class ImportXLSControllerService {

   public ImportXLSControllerService(ViewsheetService viewsheetService,
                                     CoreLifecycleService coreLifecycleService)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void processGetAssemblyImage(@ClusterProxyKey String runtimeId, String type,
                                       MultipartFile file) throws Exception
   {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File temp = fileSystemService.getCacheFile(runtimeId + "_" + type);
      fileSystemService.remove(temp, 120000);
      FileOutputStream fileOutput = new FileOutputStream(temp);
      fileOutput.write(file.getBytes());

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void processXLSUpload(@ClusterProxyKey String vsId, String type,String linkUri,
                                Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      String fileName = vsId.replace('/', '_') + "_" + type;
      File excelFile = FileSystemService.getInstance().getCacheFile(fileName);

      if(!excelFile.exists()) {
         String msg = catalog.getString("Upload Timeout");
         this.coreLifecycleService
            .sendMessage(msg, MessageCommand.Type.WARNING, dispatcher);
         return null;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return null;
      }

      Set<String> notInRange = new HashSet<>();
      List<String> assemblies = new ArrayList<>();

      try {
         ImportXLSService.getInstance().updateViewsheet(
            excelFile, type, rvs, linkUri, dispatcher, coreLifecycleService, catalog, assemblies,
            notInRange);
      }
      catch(FileNotFoundException e) {
         String msg = catalog.getString("vs.import.excel.unavailable");
         coreLifecycleService.sendMessage(msg, MessageCommand.Type.WARNING, dispatcher);
         return null;
      }

      checkError("viewer.import.notFound", assemblies, dispatcher);
      checkError("viewer.import.notInRange", notInRange, dispatcher);

      return null;
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
         this.coreLifecycleService.sendMessage(msg, MessageCommand.Type.WARNING, dispatcher);
      }
   }


   private final Catalog catalog = Catalog.getCatalog();
   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
}
