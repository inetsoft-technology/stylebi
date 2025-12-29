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
package inetsoft.web.composer.ws.joins;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.web.composer.model.ws.TableAssemblyOperatorModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.*;
import inetsoft.web.composer.ws.service.JoinService;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that provides an endpoint for creating and editing the inner join tables.
 */
@Controller
public class InnerJoinController extends WorksheetController {
   public InnerJoinController(JoinService joinService) {
      this.joinService = joinService;
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/edit-inner-join")
   public void editInnerJoin(
      @Payload WSInnerJoinEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final Worksheet ws = rws.getWorksheet();
      final TableAssemblyOperator noperator = new TableAssemblyOperator();

      for(TableAssemblyOperatorModel operator : event.getOperators()) {
         noperator.addOperator(WorksheetEventUtil.convertOperator(ws, operator));
      }

      if(event.getTableName() != null) {
         final RelationalJoinTableAssembly joinTableAssembly =
            (RelationalJoinTableAssembly) ws.getAssembly(event.getTableName());
         joinService.editExistingJoinTable(rws.getWorksheet(), joinTableAssembly,
                               noperator, false);
         WorksheetEventUtil.refreshColumnSelection(
            rws, joinTableAssembly.getName(), false);
         WorksheetEventUtil.loadTableData(
            rws, joinTableAssembly.getAbsoluteName(), true, true);
         WorksheetEventUtil.refreshAssembly(
            rws, joinTableAssembly.getAbsoluteName(), true, commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
         AssetEventUtil.refreshTableLastModified(
            ws, joinTableAssembly.getAbsoluteName(), true);
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/drop-table-into-join-schema")
   public void dragTableIntoJoinSchema(
      @Payload WSDropTableIntoJoinSchemaEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final String joinTable = event.getJoinTable();
      final String dragTable = event.getDroppedTable();
      joinService.checkContainsSubtable(rws, joinTable, dragTable);
      final SchemaTableInfo info = new SchemaTableInfo(event.getLeft(), event.getTop());
      final JoinService.JoinMetaInfo joinInfo = joinService.joinSourceAndTargetTables(
         dragTable, joinTable, rws, info, false, commandDispatcher);
      joinService.validateWSAndDispatchCommands(commandDispatcher, rws, joinInfo, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/dialog/inner-join-dialog/inner-join")
   public void joinTables(
      @Payload WSJoinTablesEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      rws.cloneWS();
      final String[] tables = event.getTables();

      if(tables.length < 2) {
         return;
      }

      String joinTable = tables[0];

      for(int i = 1; i < tables.length; i++) {
         joinService.checkContainsSubtable(rws, joinTable, tables[i]);
      }

      JoinService.JoinMetaInfo joinInfo = null;

      for(int i = 1; i < tables.length; i++) {
         final JoinService.JoinMetaInfo currInfo =
            joinService.joinSourceAndTargetTables(tables[i], joinTable, rws, null, false, commandDispatcher);

         if(i == 1 && currInfo != null) {
            joinInfo = currInfo;
         }

         joinTable = joinInfo == null ? null : joinInfo.getJoinTable().getAbsoluteName();
      }

      joinService.validateWSAndDispatchCommands(commandDispatcher, rws, joinInfo, principal);
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/composer/worksheet/join-tables")
   public void joinTablePair(
      @Payload WSJoinTablePairEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      rws.cloneWS();
      final String sourceTable = event.getLeftTable();
      final String targetTable = event.getRightTable();
      joinService.checkContainsSubtable(rws, targetTable, sourceTable);
      final JoinService.JoinMetaInfo joinInfo = joinService.joinSourceAndTargetTables(
         sourceTable, targetTable, rws, null, event.isJoinTarget(), commandDispatcher);
      joinService.validateWSAndDispatchCommands(commandDispatcher, rws, joinInfo, principal);
   }

   private final JoinService joinService;
}
