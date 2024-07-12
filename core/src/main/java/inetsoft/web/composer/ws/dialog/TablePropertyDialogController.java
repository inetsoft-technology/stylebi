/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuery;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.TablePropertyDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class TablePropertyDialogController extends WorksheetController {
   @RequestMapping(
      value = "api/composer/ws/dialog/table-property-dialog-model/{runtimeId}",
      method = RequestMethod.GET)
   @ResponseBody
   public TablePropertyDialogModel getModel(
      @PathVariable("runtimeId") String runtimeId,
      @RequestParam("table") String name,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      AbstractTableAssembly table = (AbstractTableAssembly) ws.getAssembly(name);

      if(table != null) {
         final AssetQuerySandbox box = rws.getAssetQuerySandbox();
         final AssetQuery query = AssetQuery.createAssetQuery(
            table, AssetQuerySandbox.RUNTIME_MODE, box, false, -1L, true, false);
         TablePropertyDialogModel model = new TablePropertyDialogModel();
         model.setOldName(table.getName());
         model.setNewName(table.getName());
         model.setDescription(table.getDescription());
         model.setReturnDistinctValues(table.isDistinct());
         model.setMaxRows(table.getMaxRows());
         model.setMergeSql(table.isSQLMergeable());
         model.setSourceMergeable(query.isSourceMergeable0());
         model.setVisibleInViewsheet(table.isVisibleTable());

         if(table instanceof EmbeddedTableAssembly) {
            EmbeddedTableAssembly etable = (EmbeddedTableAssembly) table;
            XEmbeddedTable data = etable.getEmbeddedData();
            int ocount = data.getRowCount() - 1;
            ocount = Math.max(0, ocount);
            model.setRowCount(ocount);
         }

         return model;
      }
      else {
         throw new Error("Could not find assembly " + name);
      }
   }

   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/table-property-dialog-model")
   public void saveProperties(
      @Payload TablePropertyDialogModel model,
      Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      AbstractTableAssembly assembly = (AbstractTableAssembly) ws
         .getAssembly(model.getOldName());

      if(assembly == null) {
         MessageCommand command = new MessageCommand();
         command.setMessage(catalog.getString(
            "common.table.setTablePropError"));
         command.setType(MessageCommand.Type.ERROR);
         command.setAssemblyName(model.getOldName());
      }
      else {
         assembly.setDescription(model.getDescription());
         assembly.setDistinct(model.getReturnDistinctValues());
         assembly.setMaxRows(TablePropertyDialogModel.convertRows(model.getMaxRows()));
         assembly.setSQLMergeable(model.getMergeSql());
         assembly.setVisibleTable(model.getVisibleInViewsheet());

         if(assembly instanceof EmbeddedTableAssembly) {
            int count = TablePropertyDialogModel.convertRows(model.getRowCount());
            EmbeddedTableAssembly etable = (EmbeddedTableAssembly) assembly;
            XEmbeddedTable data = etable.getEmbeddedData();
            int ocount = data.getRowCount() - 1;
            ocount = Math.max(0, ocount);

            if(count != ocount && count >= 0) {
               data.setRowCount(count + 1);
            }
         }

         WorksheetEventUtil.loadTableData(rws, assembly.getName(), true, true);
         WorksheetEventUtil
            .refreshAssembly(rws, model.getNewName(), model.getOldName(), true,
                             commandDispatcher, principal);
         WorksheetEventUtil.layout(rws, commandDispatcher);
         AssetEventUtil.refreshTableLastModified(ws, assembly.getName(), true);
      }
   }

   Catalog catalog = Catalog.getCatalog();
}
