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
package inetsoft.web.composer.ws;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.event.LoadTableDataEvent;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.composer.ws.event.WSLoadTableDataCountCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WSLoadTableDataCountController extends WorksheetController {
   /**
    * From 12.2 LoadTableDataCountEvent.
    */
   @MessageMapping("/composer/worksheet/table-data-count")
   public void loadTableDataCount(
      @Payload WSAssemblyEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      AssetQuerySandbox box = rws.getAssetQuerySandbox();
      String name = event.getAssemblyName();
      TableAssembly table = (TableAssembly) ws.getAssembly(name);

      if(table == null) {
         return;
      }

      int mode = WorksheetEventUtil.getMode(table);
      TableLens lens = box.getTableLens(name, mode);
      int count = 0;
      boolean more = false;
      Exception ex = null;

      try {
         if(lens != null) {
            // force to move ahead
            count = lens.getRowCount();
            count = count < 0 ? -count - 1 : count;
            lens.moreRows(count + LoadTableDataEvent.BLOCK * 5);

            count = lens.getRowCount();
            more = count < 0;
            count = more ? -count - 1 : count;
            count = Math.max(0, count - 1); // trim header
         }
      }
      catch(ExpressionFailedException e) {
         ex = e;
      }

      WSLoadTableDataCountCommand.Builder commandBuilder = WSLoadTableDataCountCommand.builder()
         .name(name)
         .count(count)
         .completed(!more);

      // set exceeded information if completed
      if(!more) {
         String exceededMsg = AssetEventUtil.getExceededMsg(table, count);

         if(exceededMsg != null) {
            commandBuilder.exceededMsg(exceededMsg);
         }
      }

      commandDispatcher.sendCommand(commandBuilder.build());

      if(ex != null) {
         throw ex;
      }
   }
}
