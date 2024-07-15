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
package inetsoft.web.composer.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSInsertDataEvent;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class InsertDataController extends WorksheetController {
   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("/composer/worksheet/insert-data")
   public void insertData(
      @Payload WSInsertDataEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String name = event.tableName();
      String type = event.type();
      int index = event.index();
      boolean insert = event.insert();
      EmbeddedTableAssembly assembly =
         (EmbeddedTableAssembly) ws.getAssembly(name);
      XEmbeddedTable data = assembly.getEmbeddedData();

      if(data.getColCount() >= Util.getOrganizationMaxColumn()) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(Util.getColumnLimitMessage());
         messageCommand.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(messageCommand);

         return;
      }

      if("column".equals(type)) {
         ColumnSelection columns = assembly.getColumnSelection();
         List<String> identifiers = new ArrayList<>();

         // get identifiers before
         for(int c = 0; c < data.getColCount(); c++) {
            identifiers.add(data.getColumnIdentifier(c));
         }

         String colname;
         int i = 1;

         while(true) {
            colname = "col" + i;

            if(columns.getAttribute(colname) == null &&
               AssetUtil.findColumnConflictingWithAlias(columns, null, colname, true) == null)
            {
               break;
            }

            i++;
         }

         // find the index in the embeded data table
         int index2 = findIndex(data, columns, index, insert);
         index = insert ? index : index + 1;
         data.insertCol(index2);
         data.setObject(0, index2, colname);
         identifiers.add(index2, colname);

         // set the new identifiers after adding the column
         for(int c = 0; c < data.getColCount(); c++) {
            data.setColumnIdentifier(c, identifiers.get(c));
         }

         AttributeRef attr = new AttributeRef(null, colname);
         ColumnRef column = new ColumnRef(attr);
         String alias = AssetUtil.findAlias(columns, column);
         column.setAlias(alias);
         columns.addAttribute(index, column);
         assembly.setColumnSelection(columns);
         WorksheetEventUtil.refreshColumnSelection(rws, name, true);
      }
      else {
         index = insert ? index : index + 1;
         data.insertRow(index + 1);
      }

      WorksheetEventUtil.loadTableData(rws, name, true, true);
      WorksheetEventUtil.refreshAssembly(rws, name, true, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      AssetEventUtil.refreshTableLastModified(ws, name, true);
   }

   private int findIndex(
      XEmbeddedTable data, ColumnSelection columns,
      int index, boolean insert)
   {
      if(insert) {
         while(index < columns.getAttributeCount()) {
            DataRef ref = columns.getAttribute(index);

            if(!ref.isExpression()) {
               return AssetUtil.findColumn(data, ref);
            }

            index++;
         }

         return data.getColCount();
      }

      while(index > 0) {
         DataRef ref = columns.getAttribute(index);

         if(!ref.isExpression()) {
            return AssetUtil.findColumn(data, ref) + 1;
         }

         index--;
      }

      return 0;
   }
}