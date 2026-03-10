/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.controller;

import inetsoft.uql.XPrincipal;
import inetsoft.web.wiz.WizUtil;
import inetsoft.web.wiz.request.ExportDatabaseTableToCsvRequest;
import inetsoft.web.wiz.service.RawDataService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/wiz")
public class RawDataController {
   public RawDataController(RawDataService rawDataService) {
      this.rawDataService = rawDataService;
   }

   @PostMapping(
      value = "/profiling/rawdata/export/datasource",
      produces = "text/csv"
   )
   public void exportDataSourceTableToCsv(
      @RequestBody ExportDatabaseTableToCsvRequest data,
      XPrincipal principal,
      HttpServletResponse response) throws Exception
   {
      response.setContentType("text/csv");
      response.setBufferSize(8192);
      ServletOutputStream outputStream = response.getOutputStream();
      rawDataService.writeDataSourceTableCsvStream(data, principal, outputStream);
      outputStream.flush();
   }

   @GetMapping(
      value = "/profiling/rawdata/export/worksheet/{identifier}",
      produces = "text/csv"
   )
   public ResponseEntity<StreamingResponseBody> exportWorksheetTableToCsv(
      @PathVariable("identifier") String worksheetId,
      @RequestParam("tableName") String tableName,
      XPrincipal principal) throws Exception
   {
      StreamingResponseBody stream = outputStream -> {
         try {
            rawDataService.writeWorksheetTableCsvStream(
               WizUtil.decodeId(worksheetId),
               tableName,
               principal,
               outputStream
            );
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      };

      return ResponseEntity.ok()
         .contentType(MediaType.parseMediaType("text/csv"))
         .body(stream);
   }

   private final RawDataService rawDataService;
}
