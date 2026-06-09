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

import inetsoft.web.wiz.model.WorksheetTableRequest;
import inetsoft.web.wiz.model.WorksheetTableResponse;
import inetsoft.web.wiz.service.WorksheetTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * REST endpoint for incremental, multi-step worksheet construction.
 * <p>
 * Each call adds exactly one table assembly to a worksheet.
 * <ul>
 *   <li>First call (no {@code worksheetId}): creates a new worksheet, returns {@code wsId}.</li>
 *   <li>Subsequent calls: pass {@code worksheetId} from the previous response to add
 *       the next table to the same worksheet.</li>
 * </ul>
 * This endpoint is intentionally separate from {@code /ws/generate} so that
 * complex multi-step queries can be built without affecting the single-shot path.
 */
@RestController
@RequestMapping("/api/wiz")
public class WorksheetTableController {

   public WorksheetTableController(WorksheetTableService worksheetTableService) {
      this.worksheetTableService = worksheetTableService;
   }

   /**
    * Create or extend a worksheet by adding a single table assembly.
    *
    * @param request the table definition to create
    * @param user    the authenticated user
    * @return the table's column list and the worksheet identifier
    */
   @PostMapping(value = "/ws/table", produces = MediaType.APPLICATION_JSON_VALUE)
   public WorksheetTableResponse createTable(@RequestBody WorksheetTableRequest request,
                                             Principal user)
   {
      try {
         return worksheetTableService.createTable(request, user);
      }
      catch(Exception e) {
         WorksheetTableResponse response = new WorksheetTableResponse();
         response.setSuccess(false);
         response.setTableName(request.getTableName());
         response.setErrorMessage(e.getMessage());
         LOG.error("Failed to create worksheet table '{}'", request.getTableName(), e);
         return response;
      }
   }

   private final WorksheetTableService worksheetTableService;
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetTableController.class);
}
