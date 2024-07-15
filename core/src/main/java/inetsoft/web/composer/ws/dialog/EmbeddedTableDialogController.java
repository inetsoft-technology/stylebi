/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.Util;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.EmbeddedTableDialogModel;
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

import java.awt.*;
import java.security.Principal;

@Controller
public class EmbeddedTableDialogController extends WorksheetController {
   @RequestMapping(
      value = "api/composer/ws/dialog/embedded-table-dialog-model/{runtimeId}",
      method = RequestMethod.GET)
   @ResponseBody
   public EmbeddedTableDialogModel getEmbeddedTableDialogModel(
      @PathVariable("runtimeId") String runtimeId, Principal principal) throws Exception
   {
      final Worksheet ws = getWorksheetEngine().getWorksheet(Tool.byteDecode(runtimeId), principal)
         .getWorksheet();
      final EmbeddedTableDialogModel model = new EmbeddedTableDialogModel();
      model.setName(AssetUtil.getNextName(ws, AbstractSheet.TABLE_ASSET));
      model.setRows(1);
      model.setCols(1);
      return model;
   }

   /**
    * From 12.2 NewEmbeddedTableEvent.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/ws/dialog/embedded-table-dialog-model")
   public void createEmbeddedTable(
      @Payload EmbeddedTableDialogModel model,
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final Worksheet ws = rws.getWorksheet();
      final EmbeddedTableAssembly assembly = new EmbeddedTableAssembly(ws, model.getName());
      initEmbeddedTablePosition(ws, assembly);

      if(model.getCols() > Util.getOrganizationMaxColumn()) {
         model.setCols(Util.getOrganizationMaxColumn());
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(Catalog.getCatalog().getString("common.columnexceed",
            "" + model.getName() + "", "" + Util.getOrganizationMaxColumn()));
         messageCommand.setType(MessageCommand.Type.WARNING);
         commandDispatcher.sendCommand(messageCommand);
      }

      final int twidth = model.getCols();
      final int theight = model.getRows() + 1;
      assembly.setPixelSize(new Dimension(AssetUtil.defw, theight));
      final String[] types = new String[twidth];
      final Object[][] data = new Object[theight][twidth];

      for(int i = 0; i < data[0].length; i++) {
         data[0][i] = "col" + i;
      }

      final XEmbeddedTable table = new XEmbeddedTable(types, data);
      assembly.setEmbeddedData(table);
      assembly.setEditMode(true);
      ws.addAssembly(assembly);

      AssetEventUtil.initColumnSelection(rws, assembly);
      WorksheetEventUtil.createAssembly(rws, assembly, commandDispatcher, principal);
      WorksheetEventUtil.loadTableData(rws, model.getName(), false, false);
      WorksheetEventUtil.refreshAssembly(rws, model.getName(), false, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      WorksheetEventUtil.focusAssembly(model.getName(), commandDispatcher);
   }

   private void initEmbeddedTablePosition(Worksheet ws, EmbeddedTableAssembly assembly0) {
      Assembly[] assemblies = ws.getAssemblies();
      int maxTop = 0;
      int minTop = 0;

      for(int i = 0; i < assemblies.length; i++) {
         AbstractWSAssembly assembly = (AbstractWSAssembly) ws.getAssembly(assemblies[i].getName());

         if(i == 0) {
            maxTop = minTop = assembly.getPixelOffset().y;
         }

         maxTop = Math.max(maxTop, assembly.getPixelOffset().y);
         minTop = Math.min(minTop, assembly.getPixelOffset().y);
      }

      assembly0.setPixelOffset(new Point(10, minTop > 71 ? 10 : maxTop + 71));
   }
}
