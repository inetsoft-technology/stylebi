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
package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.io.csv.CSVConfig;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.portal.model.CSVConfigModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.dialog.ExportDialogModel;
import inetsoft.web.viewsheet.model.dialog.FileFormatPaneModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@Controller
public class ExportDialogController {
   /**
    * Creates a new instance of ExportDialogController
    * @param runtimeViewsheetRef    RuntimeViewsheetRef instance
    */
   @Autowired
   public ExportDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                 ExportDialogServiceProxy exportDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.exportDialogServiceProxy = exportDialogServiceProxy;
   }

   /**
    * Gets the export dialog model.
    * @param runtimeId  the runetime id
    * @param principal  the principal user
    * @return  the export dialog model
    * @throws Exception if could not create the export dialog model
    */
   @RequestMapping(value="/api/vs/export-dialog-model/**", method = RequestMethod.GET)
   @ResponseBody
   public ExportDialogModel getExportDialogModel(@RemainingPath String runtimeId,
                                                 Principal principal)
      throws Exception
   {
      IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
      runtimeId = Tool.byteDecode(runtimeId);

      return exportDialogServiceProxy.getExportDialogModel(runtimeId, principal);
   }



   /**
    * Check if exporting a viewsheet is valid.
    * @param runtimeId     the runtime id of the vs
    * @param folderPath    the folder to export to
    * @param principal     the principal user
    * @return  A Message command with type OK for valid export params
    * @throws Exception if could not check if the export is valid
    */
   @RequestMapping(value="/api/vs/check-export-valid/**", method = RequestMethod.GET)
   @ResponseBody
   public MessageCommand checkExportValid(@RemainingPath String runtimeId,
                                          @RequestParam("folderPath") String folderPath,
                                          Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);

      return exportDialogServiceProxy.checkExportValid(runtimeId, folderPath, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private ExportDialogServiceProxy exportDialogServiceProxy;
}
