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

package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.uql.viewsheet.ImageVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.composer.vs.dialog.ImagePreviewPaneController;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.VSObjectService;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@Service
@ClusterProxy
public class WizardUploadImageService {

   public WizardUploadImageService(ViewsheetService viewsheetService,
                                   VSObjectService vsObjectService,
                                   VSObjectModelFactoryService objectModelService)
   {
      this.objectModelService = objectModelService;
      this.viewsheetService = viewsheetService;
      this.vsObjectService = vsObjectService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public VSObjectModel uploadImage(@ClusterProxyKey String runtimeId, String assemblyName,
                                    MultipartFile mpf, Principal principal) throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);

      if(rvs == null) {
         return null;
      }

      Viewsheet vs = rvs.getViewsheet();
      ImageVSAssembly assembly = (ImageVSAssembly) vs.getAssembly(assemblyName);

      try {
         if(!vsObjectService.isImage(mpf.getBytes())) {
            throw new MessageException(Catalog.getCatalog().getString("composer.uploadImageFailed"));
         }

         vs.addUploadedImage(mpf.getOriginalFilename(), mpf.getBytes());
         ((ImageVSAssemblyInfo) assembly.getVSAssemblyInfo())
            .setImageValue(ImageVSAssemblyInfo.UPLOADED_IMAGE + mpf.getOriginalFilename());
         rvs.addCheckpoint(vs.prepareCheckpoint());
      }
      catch(MessageException messageException) {
         throw messageException;
      }
      catch(Exception ex) {
         LoggerFactory.getLogger(ImagePreviewPaneController.class).debug("Failed to get uploaded file data", ex);
      }

      return objectModelService.createModel(assembly, rvs);
   }


   private VSObjectService vsObjectService;
   private ViewsheetService viewsheetService;
   private VSObjectModelFactoryService objectModelService;
}
