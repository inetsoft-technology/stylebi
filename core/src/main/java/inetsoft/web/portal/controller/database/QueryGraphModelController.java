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
package inetsoft.web.portal.controller.database;

import inetsoft.uql.jdbc.*;
import inetsoft.web.portal.model.database.JoinGraphModel;
import inetsoft.web.portal.model.database.events.*;
import inetsoft.web.portal.model.database.graph.TableDetailJoinInfo;
import inetsoft.web.portal.model.database.graph.TableJoinInfo;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;

@RestController
public class QueryGraphModelController {
   public QueryGraphModelController(RuntimeQueryService runtimeQueryService,
                                    QueryManagerService queryService,
                                    QueryGraphModelService queryGraphService)
   {
      this.runtimeQueryService = runtimeQueryService;
      this.queryService = queryService;
      this.queryGraphService = queryGraphService;
   }

   @PostMapping("/api/data/datasource/query/graph")
   public JoinGraphModel queryGraphModel(@RequestBody GetGraphModelEvent event, Principal principal)
      throws Exception
   {
      return queryGraphService.getQueryGraphModel(event.getRuntimeID(), event.getTableJoinInfo(),
         principal);
   }

   @PostMapping("/api/data/datasource/query/table/add")
   public void addTable(@RequestBody AddQueryTableEvent event, Principal principal) throws Exception {
      queryGraphService.addTables(event.getId(), event.getTables(), event.getPosition(), principal);
   }

   @PostMapping("/api/data/datasource/query/table/remove")
   public void removeTables(@RequestBody RemoveGraphTableEvent event, Principal principal) {
      queryGraphService.removeTables(event.getRuntimeId(), event.getTables(), principal);
   }

   @PutMapping("/api/data/datasource/query/table/move")
   public void moveTable(@RequestBody MoveGraphEvent event) {
      Rectangle rectangle = event.getBounds();

      if(rectangle == null || event.getTable() == null) {
         return;
      }

      queryGraphService.moveTable(event.getRuntimeId(), event.getTable(), rectangle);
   }

   @DeleteMapping("/api/data/datasource/query/table/{runtimeId}")
   public void clearTable(@PathVariable("runtimeId") String runtimeId) {
      queryGraphService.clearTable(runtimeId);
   }

   @PostMapping("api/data/datasource/query/table/properties")
   public void editTableProperties(@RequestBody EditQueryTableEvent event, Principal principal) {
      queryGraphService.editQueryTableProperties(event, principal);
   }

   @GetMapping("/api/data/datasource/query/join-edit/open/{oldRuntimeId}")
   public String openJoinEditPane(@PathVariable("oldRuntimeId") String oldRuntimeId)
      throws Exception
   {
      return this.runtimeQueryService.openNewRuntimeQuery(oldRuntimeId);
   }

   @GetMapping("/api/data/datasource/query/join-edit/close")
   public void closeJoinEditPane(@RequestParam("originRuntimeId") String originRuntimeId,
                                 @RequestParam("newRuntimeId") String newRuntimeId,
                                 @RequestParam(value="save", required = false) boolean save)
   {
      this.runtimeQueryService.closeRuntimeQuery(originRuntimeId, newRuntimeId, save);
   }

   @PostMapping("/api/data/datasource/query/join")
   public void createJoin(@RequestBody TableDetailJoinInfo joinInfo) throws Exception {
      queryGraphService.createJoin(joinInfo);
   }

   @PutMapping("/api/data/datasource/query/join")
   public void editJoin(@RequestBody EditJoinEvent event) {
      queryGraphService.editJoin(event);
   }

   @PostMapping("/api/data/datasource/query/join/delete")
   public void deleteJoin(@RequestBody TableDetailJoinInfo joinInfo) throws Exception {
      deleteJoins(joinInfo);
   }

   @PostMapping("/api/data/datasource/query/joins/delete")
   public void deleteJoins(@RequestBody TableJoinInfo joinInfo) {
      queryGraphService.deleteJoins(joinInfo);
   }

   @DeleteMapping("/api/data/datasource/query/joins/{runtimeId}")
   public void clearJoins(@PathVariable("runtimeId") String runtimeId) {
      queryGraphService.clearJoins(runtimeId);
   }

   @GetMapping("/api/data/datasource/query/table/alias/check")
   public boolean checkTableAlias(@RequestParam("runtimeId") String runtimeId,
                                  @RequestParam("tableAlias") String tableAlias)
   {
      JDBCQuery query = queryService.getQuery(runtimeId);

      if(query != null && query.getDataSource() != null) {
         UniformSQL sql = (UniformSQL) query.getSQLDefinition();
         SQLHelper provider = SQLHelper.getSQLHelper(query.getDataSource());
         String quote = provider.getQuote();
         Object name = sql.getTableName(tableAlias);

         if(tableAlias.contains(quote) && (name == null || !name.equals(tableAlias))) {
            return false;
         }
      }

      return true;
   }

   private final RuntimeQueryService runtimeQueryService;
   private final QueryManagerService queryService;
   private final QueryGraphModelService queryGraphService;
}
