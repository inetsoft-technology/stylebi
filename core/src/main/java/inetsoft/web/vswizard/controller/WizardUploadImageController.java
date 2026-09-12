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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.ImageVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.composer.vs.dialog.ImagePreviewPaneController;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.VSObjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@Controller
public class WizardUploadImageController {
   @Autowired
   public WizardUploadImageController(WizardUploadImageServiceProxy wizardUploadImageServiceProxy)
   {
      this.wizardUploadImageServiceProxy = wizardUploadImageServiceProxy;
   }

   @PostMapping("/api/composer/vswizard/update-image/{assemblyName}/**")
   @ResponseBody
   public VSObjectModel uploadImage(@PathVariable("assemblyName") String assemblyName,
                                    @RemainingPath String runtimeId,
                                    @RequestParam("file") MultipartFile mpf,
                                    Principal principal)
      throws Exception
   {
      return wizardUploadImageServiceProxy.uploadImage(runtimeId, assemblyName, mpf, principal);
   }

   private WizardUploadImageServiceProxy wizardUploadImageServiceProxy;
}
