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

import inetsoft.web.wiz.model.*;
import inetsoft.web.wiz.service.WorksheetTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * REST endpoint for incremental, multi-step worksheet construction.
 * <p>
 * Each call creates a batch of table assemblies in one worksheet (loaded/created once,
 * persisted once), returning one per-table result each.
 * <ul>
 *   <li>First call (no {@code worksheetId}): creates a new worksheet, returns {@code wsId}.</li>
 *   <li>Subsequent calls: pass {@code worksheetId} from the previous response to add
 *       more tables to the same worksheet.</li>
 * </ul>
 * A single table's failure does not abort the others: its result carries {@code success:false}
 * and an {@code errorMessage}; only the tables that succeeded are persisted.
 * This endpoint is intentionally separate from {@code /ws/generate} so that
 * complex multi-step queries can be built without affecting the single-shot path.
 */
@RestController
@RequestMapping("/api/wiz")
public class WorksheetTableController {

   public WorksheetTableController(WorksheetTableService worksheetTableService) {
      this.worksheetTableService = worksheetTableService;
   }

   @PostMapping(value = "/ws/table", produces = MediaType.APPLICATION_JSON_VALUE)
   public WorksheetTablesResponse createTables(@RequestBody WorksheetTableRequest request,
                                               Principal user)
   {
      try {
         return worksheetTableService.createTables(request, user);
      }
      catch(Exception e) {
         // A batch-level failure (e.g. worksheet load) fails every requested table.
         WorksheetTablesResponse response = new WorksheetTablesResponse();
         List<WorksheetTableResponse> failures = new ArrayList<>();

         if(request.getTables() != null) {
            for(WorksheetTable t : request.getTables()) {
               WorksheetTableResponse r = new WorksheetTableResponse();
               r.setTableName(t.getTableName());
               r.setSuccess(false);
               r.setErrorMessage(e.getMessage());
               failures.add(r);
            }
         }

         response.setTables(failures);
         LOG.error("Failed to create worksheet tables", e);
         return response;
      }
   }

   /**
    * Delete one or more table assemblies from an existing worksheet.
    *
    * @param request the list of table names to remove and the worksheet identifier
    * @param user    the authenticated user
    * @return lists of deleted, not-found, and skipped table names
    */
   @DeleteMapping(value = "/ws/table", produces = MediaType.APPLICATION_JSON_VALUE)
   public DeleteWorksheetTablesResponse deleteTables(@RequestBody DeleteWorksheetTablesRequest request,
                                                     Principal user)
   {
      try {
         return worksheetTableService.deleteTables(request, user);
      }
      catch(Exception e) {
         DeleteWorksheetTablesResponse response = new DeleteWorksheetTablesResponse();
         response.setSuccess(false);
         response.setErrorMessage(e.getMessage());
         LOG.error("Failed to delete worksheet tables {}", request.getTableNames(), e);
         return response;
      }
   }

   /**
    * Get worksheet model metadata for a worksheet asset identifier.
    *
    * @param wsIdentifier the worksheet identifier
    * @param user         the authenticated user
    * @return worksheet metadata and table metadata
    */
   @GetMapping(value = "/ws/worksheet-model", produces = MediaType.APPLICATION_JSON_VALUE)
   public WorksheetModel getWorksheetModel(@RequestParam("wsIdentifier") String wsIdentifier,
                                           Principal user)
      throws Exception
   {
      return worksheetTableService.getWorksheetModel(wsIdentifier, user);
   }

   /**
    * getWorksheetModel is a pure read that does not catch its own exceptions the way
    * createTable/deleteTables do (they report failure in a 200 response body instead), so a
    * permission-denied (or any other) failure from the service would otherwise fall through to
    * an unhandled 500. Mirrors DatasourceMetaApiController's catch-all handler.
    */
   @ExceptionHandler(Exception.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleException(Exception e) {
      LOG.warn("Worksheet table API error: {}", e.getMessage(), e);
      return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
   }

   private final WorksheetTableService worksheetTableService;
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetTableController.class);
}
