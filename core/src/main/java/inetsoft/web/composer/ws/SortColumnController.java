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
package inetsoft.web.composer.ws;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSSortColumnEvent;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class SortColumnController extends WorksheetController {
   @Undoable
   @LoadingMask
   @InitWSExecution
   @MessageMapping("/composer/worksheet/sort-column")
   public void sortColumn(@Payload WSSortColumnEvent event, Principal principal,
                          CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.getTableName();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);

      if(table != null) {
         boolean tableMode = table.isRuntime();
         ColumnSelection columns = table.getColumnSelection();
         ColumnRef column = (ColumnRef) event.getInfo().getRef().createDataRef();
         int index = columns.indexOfAttribute(column);

         // For cube table, find column by alias first, then attribute.
         if(index < 0 && table instanceof CubeTableAssembly) {
            String name = column.getAttribute();

            for(int i = 0; i < columns.getAttributeCount(); i++) {
               if(name.equals(((ColumnRef) columns.getAttribute(i)).getAlias())) {
                  index = i;
                  break;
               }
            }

            if(index < 0) {
               for(int i = 0; i < columns.getAttributeCount(); i++) {
                  if(name.equals(columns.getAttribute(i).getAttribute())) {
                     index = i;
                     break;
                  }
               }
            }
         }

         if(index < 0) {
            return;
         }

         column = (ColumnRef) columns.getAttribute(index);
         int type = event.getInfo().getSortType();
         SortInfo sinfo = table.getSortInfo();
         sinfo.clear();

         if(type == XConstants.SORT_NONE) {
            type = XConstants.SORT_ASC;
         }
         else if(type == XConstants.SORT_ASC) {
            type = XConstants.SORT_DESC;
         }
         else {
            type = XConstants.SORT_NONE;
         }

         // fix customer bug bug1306510577811, always sort rather than original
         if(type != XConstants.SORT_NONE) {
            SortRef sort = new SortRef(column);
            sort.setOrder(type);
            sinfo.addSort(sort);
         }

         sinfo.setTempSort(true);
         WorksheetEventUtil.refreshColumnSelection(rws, tname, true);
         WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);
         table.setRuntime(tableMode);
         AssetEventUtil.refreshTableLastModified(ws, tname, true);
      }
   }
}
