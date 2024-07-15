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

import inetsoft.report.composition.*;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import inetsoft.web.composer.ws.TableModeController;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.AllowCrossJoinEvent;
import inetsoft.web.composer.ws.event.WSCrossJoinEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller that provides a REST endpoint for creating and fetching the assemblies of cross joins.
 */
@Controller
public class CrossJoinController extends WorksheetController {
   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/inner-join-dialog/cross-join")
   public void doCrossJoin(
      @Payload WSCrossJoinEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(Tool.byteDecode(getRuntimeId()), principal);
      final CrossJoinMetaInfo joinInfo = process(rws, event.getTableNames(), false);

      if(joinInfo == null || joinInfo.joinTable == null) {
         return;
      }

      rws.getWorksheet().checkDependencies();
      final RelationalJoinTableAssembly jTable = joinInfo.joinTable;
      AssetEventUtil.initColumnSelection(rws, jTable);
      TableModeController.setDefaultTableMode(jTable, rws.getAssetQuerySandbox());
      WorksheetEventUtil.loadTableData(rws, jTable.getName(), true, false);
      WorksheetEventUtil.layout(rws, commandDispatcher);

      if(joinInfo.newTable) {
         WorksheetEventUtil.createAssembly(rws, jTable, commandDispatcher, principal);
         WorksheetEventUtil.focusAssembly(jTable.getName(), commandDispatcher);
      }
      else {
         WorksheetEventUtil.refreshAssembly(
            rws, jTable.getName(), true, commandDispatcher, principal);
      }
   }

   @MessageMapping("/ws/crossjoin/confirm")
   public void allowCrossJoin(@Payload AllowCrossJoinEvent event,
                              Principal principal,
                              CommandDispatcher dispatcher,
                              @LinkUri String linkUri) throws Exception
   {
      final WorksheetService engine = getWorksheetEngine();
      final String id = Tool.byteDecode(getRuntimeId());
      final RuntimeSheet sheet = engine.getSheet(id, principal);
      final String tableName = event.tableName();
      crossJoinService.executeCrossjoinAssemblies(principal, dispatcher, linkUri, sheet, tableName);
   }

   /**
    * Process this worksheet event.
    * Ripped from 12.2 MergeCrossEvent.
    *
    * @param rws the specified runtime worksheet as the context.
    */
   public static CrossJoinMetaInfo process(
      RuntimeWorksheet rws, String[] assemblies, boolean newTable)
      throws Exception
   {
      CrossJoinMetaInfo joinInfo = null;

      for(int i = 1; i < assemblies.length; i++) {
         String fromTable = joinInfo == null ? assemblies[i - 1] : joinInfo.joinTable.getName();
         CrossJoinMetaInfo currInfo = concatenateTableCross(
            fromTable, assemblies[i], rws, newTable);

         if(currInfo == null || currInfo.joinTable == null) {
            return null;
         }

         if(i == 1) {
            joinInfo = currInfo;
         }
      }

      return joinInfo;
   }

   public static CrossJoinMetaInfo concatenateTableCross(
      String fromName, String toName,
      RuntimeWorksheet rws, boolean newTable) throws Exception
   {
      Worksheet ws = rws.getWorksheet();
      WSAssembly from = (WSAssembly) ws.getAssembly(fromName);
      WSAssembly to = (WSAssembly) ws.getAssembly(toName);

      if(!(from instanceof TableAssembly && to instanceof TableAssembly)) {
         return null;
      }

      final String nname = AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET);
      TableAssemblyOperator operator = new TableAssemblyOperator();
      TableAssemblyOperator.Operator op = new TableAssemblyOperator.Operator();
      RelationalJoinTableAssembly jointbl;
      boolean newtbl = false;

      op.setOperation(TableAssemblyOperator.CROSS_JOIN);
      operator.addOperator(op);

      if(!newTable && from instanceof RelationalJoinTableAssembly) {
         jointbl = (RelationalJoinTableAssembly) from;
         checkContainsSubtable(rws, jointbl, to);
      }
      else if(!newTable && to instanceof RelationalJoinTableAssembly) {
         jointbl = (RelationalJoinTableAssembly) to;
         checkContainsSubtable(rws, jointbl, from);
      }
      else {
         newtbl = true;
         jointbl = new RelationalJoinTableAssembly(ws, nname,
                                                 new TableAssembly[]{ (TableAssembly) from, (TableAssembly) to },
                                                 new TableAssemblyOperator[]{ operator });

         ws.addAssembly(jointbl);
         AssetEventUtil.layoutResultantTable(from, to, jointbl);
      }

      if(!newtbl) {
         TableAssembly[] arr = jointbl.getTableAssemblies(true);
         TableAssembly[] narr = new TableAssembly[arr.length + 1];

         if(jointbl == from) {
            System.arraycopy(arr, 0, narr, 0, arr.length);
            narr[arr.length] = (TableAssembly) to;
            jointbl.setOperator(narr[narr.length - 2].getName(),
                                narr[narr.length - 1].getName(), operator);
         }
         else {
            System.arraycopy(arr, 0, narr, 1, arr.length);
            narr[0] = (TableAssembly) from;
            jointbl.setOperator(narr[0].getName(), narr[1].getName(),
                                operator);
         }

         jointbl.setTableAssemblies(narr);
         ws.removeAssembly(jointbl);
         ws.addAssembly(jointbl);
      }

      return new CrossJoinMetaInfo(jointbl, newtbl);
   }

   private static void checkContainsSubtable(
      RuntimeWorksheet rws, RelationalJoinTableAssembly jtable, WSAssembly subtable)
   {
      if(JoinUtil.tableContainsSubtable(jtable, subtable.getAbsoluteName())) {
         rws.rollback();
         throw new MessageException(catalog.getString(
            "common.table.recursiveJoin"));
      }
   }

   protected static class CrossJoinMetaInfo {
      private CrossJoinMetaInfo(RelationalJoinTableAssembly joinTable, boolean newTable) {
         this.joinTable = joinTable;
         this.newTable = newTable;
      }

      protected final RelationalJoinTableAssembly joinTable;
      protected final boolean newTable;
   }

   @Autowired
   private CrossJoinService crossJoinService;
   private static final Catalog catalog = Catalog.getCatalog();
}
