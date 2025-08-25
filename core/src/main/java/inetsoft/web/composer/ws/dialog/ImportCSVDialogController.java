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
package inetsoft.web.composer.ws.dialog;

import inetsoft.util.Tool;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.composer.model.ws.ImportCSVDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.HashMap;

/**
 * Controller that provides the endpoints for the importing a data file into the worksheet.
 *
 * @since 12.3
 */
@Controller
public class ImportCSVDialogController extends WorksheetController {
   public ImportCSVDialogController(ImportCSVDialogServiceProxy dialogService,
                                    BinaryTransferService binaryTransferService)
   {
      this.dialogService = dialogService;
      this.binaryTransferService = binaryTransferService;
   }

   /**
    * Gets the top-level descriptor of the rectangle.
    *
    * @param runtimeId the runtime identifier of the rectangle.
    *
    * @return the rectangle descriptor.
    */
   @GetMapping("/api/composer/ws/import-csv-dialog-model/{runtimeId}")
   @ResponseBody
   public ImportCSVDialogModel getImportCSVDialogModel(@PathVariable("runtimeId") String runtimeId)
   {
      return ImportCSVDialogModel.builder()
         .encodingSelected("UTF-8")
         .delimiter(',')
         .delimiterTab(false)
         .unpivotCB(false)
         .headerCols(1)
         .firstRowCB(true)
         .removeQuotesCB(true)
         .build();
   }

   @PostMapping(
      value = "/api/composer/ws/import-csv-dialog-model/upload/{runtimeId}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @ResponseBody
   public HashMap<String, Object> getUploadFile(
      @RequestParam("uploads[]") MultipartFile mpf,
      @PathVariable("runtimeId") String runtimeId) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);

      String key = "/" + ImportCSVDialogController.class.getName() + "_" + runtimeId;
      BinaryTransfer data = binaryTransferService.createBinaryTransfer(key);
      DeferredFileOutputStream output = binaryTransferService.createOutputStream(data);

      try(InputStream input = mpf.getInputStream()) {
         IOUtils.copy(input, output);
      }
      finally {
         binaryTransferService.closeOutputStream(data, output);
      }

      return dialogService.getUploadFile(runtimeId, data);
   }

   @PostMapping("/api/composer/ws/import-csv-dialog-model/preview/{runtimeId}")
   @ResponseBody
   public HashMap<String, Object> getPreviewTable(
      @RequestBody ImportCSVDialogModel model,
      @PathVariable("runtimeId") String runtimeId) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return dialogService.getPreviewTable(runtimeId, model);
   }

   @PutMapping("/api/composer/ws/import-csv-dialog-model/touch-file/{runtimeId}")
   @ResponseBody
   public void touchFile(@PathVariable("runtimeId") String runtimeId) throws IOException {
      dialogService.touchFile(runtimeId);
   }

   @MessageMapping("/ws/dialog/import-csv-dialog-model")
   public void setImportCSVDialogModel(
      @Payload ImportCSVDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      dialogService.setImportCSVDialogModel(getRuntimeId(), model, principal, commandDispatcher);
   }

   private final ImportCSVDialogServiceProxy dialogService;
   private final BinaryTransferService binaryTransferService;

   private static final Logger LOG = LoggerFactory.getLogger(ImportCSVDialogController.class);
}
