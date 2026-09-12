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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.table.ReportLayoutTool;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.CalcAggregate;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.command.*;
import inetsoft.web.binding.drm.AggregateRefModel;
import inetsoft.web.binding.event.*;
import inetsoft.web.binding.handler.*;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.binding.model.table.*;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.condition.ConditionExpression;
import inetsoft.web.composer.vs.objects.event.ResizeCalcTableCellEvent;
import inetsoft.web.viewsheet.command.AssemblyLoadingCommand;
import inetsoft.web.viewsheet.command.ClearAssemblyLoadingCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class VSTableLayoutController {
   /**
    * Creates a new instance of <tt>ViewsheetBindingController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public VSTableLayoutController(
      RuntimeViewsheetRef runtimeViewsheetRef,
      VSTableLayoutServiceProxy vsTableLayoutService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsTableLayoutService = vsTableLayoutService;
   }

   @MessageMapping("/vs/calctable/tablelayout/getlayout")
   public void getLayout(@Payload GetTableLayoutEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsTableLayoutService.getLayout(id, event, principal, dispatcher);
   }

   @MessageMapping("/vs/calctable/tablelayout/getcellbinding")
   public void getCellBinding(@Payload GetCellBindingEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsTableLayoutService.getCellBinding(id, event, principal, dispatcher);
   }

   @MessageMapping("/vs/calctable/tablelayout/changeColumnValue")
   public void changeColumnValue(@Payload ChangeColumnValueEvent event,
                                 @LinkUri String linkUri,
                                 Principal principal, CommandDispatcher dispatcher)
      throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsTableLayoutService.changeColumnValue(id, event, linkUri, principal, dispatcher);
   }

   private boolean isCalcAggrgate(String table, String name, Viewsheet vs) {
      CalculateRef ref = vs == null ? null : vs.getCalcField(table, name);
      return ref != null && !ref.isBaseOnDetail();
   }

   @MessageMapping("/vs/calctable/tablelayout/setcellbinding")
   public void setCellBinding(@Payload SetCellBindingEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsTableLayoutService.setCellBinding(id, event, principal, dispatcher);
   }

   @PutMapping("/api/vs/calctable/tablelayout/checktrap")
   @ResponseBody
   public ResponseEntity<CellBindingInfo> checkTrapForCalc(@RequestParam("vsId") String vsId,
      @RequestParam("assemblyName") String name,
      @RequestParam("row") int row, @RequestParam("col") int col,
      @RequestBody CellBindingInfo binding,
      Principal principal) throws Exception
   {
      CellBindingInfo cellBinding = vsTableLayoutService
         .checkTrapForCalc(vsId, name, row, col, binding, principal);

      HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.setContentType(MediaType.APPLICATION_JSON);
      return new ResponseEntity<>(cellBinding, httpHeaders, HttpStatus.OK);
   }

   @MessageMapping("/vs/calctable/tablelayout/modifylayout")
   public void modifyLayout(@Payload ModifyTableLayoutEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsTableLayoutService.modifyLayout(id, event, principal, dispatcher);
   }

   @MessageMapping("/vs/calctable/tablelayout/copycutcell")
   public void copyCut(@Payload CopyCutCalcCellEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsTableLayoutService.copyCut(id, event, principal, dispatcher);
   }

   @MessageMapping("/vs/calctable/tablelayout/getcellscript")
   public void getCellScript(@Payload GetCellScriptEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsTableLayoutService.getCellScript(id, event, principal, dispatcher);
   }

   @MessageMapping("/vs/calctable/tablelayout/getnamedgroup")
   public void getNamedGroup(@Payload GetPredefinedNamedGroupEvent event,
      Principal principal, CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsTableLayoutService.getNamedGroup(id, event, principal, dispatcher);
   }

   @MessageMapping("/vs/calctable/tablelayout/resize")
   public void resizeCalcTableCell(@Payload ResizeCalcTableCellEvent event, Principal principal,
                                CommandDispatcher dispatcher) throws Exception
   {
      String id = runtimeViewsheetRef.getRuntimeId();

      if(id == null) {
         return;
      }

      vsTableLayoutService.resizeCalcTableCell(id, event, principal, dispatcher);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final VSTableLayoutServiceProxy vsTableLayoutService;
}
