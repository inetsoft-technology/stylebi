/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.uql.viewsheet.UploadVSAssembly;
import inetsoft.uql.viewsheet.internal.UploadVSAssemblyInfo;
import inetsoft.util.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.event.UploadCompleteEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.DefaultMultipartHttpServletRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.security.Principal;

@Controller
public class VSUploadController {
   /**
    * Creates a new instance of <tt>VSUploadController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSUploadController(RuntimeViewsheetRef runtimeViewsheetRef,
                             ViewsheetService viewsheetService,
                             VSObjectPropertyService vsObjectPropertyService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.viewsheetService = viewsheetService;
      this.vsObjectPropertyService = vsObjectPropertyService;
   }

   @PostMapping(value = "/api/upload/uploadFile/", headers = "content-type=multipart/form-data")
   @ResponseBody
   public boolean processUploadFile(HttpServletRequest request, @LinkUri String linkUri,
                                    Principal principal) {
      if(LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM)) {
         MultipartFile mpf = ((MultipartHttpServletRequest) request)
            .getFileMap().get("uploads[]");
         String fileName = mpf.getOriginalFilename();
         File upload = FileSystemService.getInstance().getCacheFile(fileName);

         try(FileOutputStream out = new FileOutputStream(upload)) {
            out.write(mpf.getBytes());
         }
         catch(Exception ex) {
            LOG.debug("Failed to get uploaded file data", ex);
         }

         return true;
      }
      else {
         throw new MessageException(Catalog.getCatalog().getString("viewer.viewsheet.needFormLicense"));
      }
   }

   @MessageMapping("/composer/viewsheet/uploadComplete")
   public void onUploadComplete(@Payload UploadCompleteEvent event, Principal principal,
                           CommandDispatcher dispatcher, @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      UploadVSAssembly uploadAssembly = (UploadVSAssembly) rvs.getViewsheet().getAssembly(event.getName());
      UploadVSAssemblyInfo uploadAssemblyInfo = (UploadVSAssemblyInfo) Tool.clone(uploadAssembly.getVSAssemblyInfo());
      uploadAssemblyInfo.setLoaded(true);
      uploadAssemblyInfo.setFileName(event.getFileName());
      this.vsObjectPropertyService.editObjectProperty(
         rvs, uploadAssemblyInfo, event.getName(), event.getName(), linkUri,
         principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final ViewsheetService viewsheetService;
   private final VSObjectPropertyService vsObjectPropertyService;
   private static final Logger LOG = LoggerFactory.getLogger(VSUploadController.class);
}
