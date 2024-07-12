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

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.RelationalJoinTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.CompositeTableAssemblyInfo;
import inetsoft.uql.asset.internal.SchemaTableInfo;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSResizeSchemaTableEvent;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WSResizeSchemaTableController extends WorksheetController {
   @Undoable
   @MessageMapping("/composer/worksheet/resize-schema-table")
   public void resizeSchemaTable(
      @Payload WSResizeSchemaTableEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final Worksheet ws = rws.getWorksheet();
      final RelationalJoinTableAssembly table =
         (RelationalJoinTableAssembly) ws.getAssembly(event.joinTableName());

      if(table != null) {
         final CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo) table.getTableInfo();
         final SchemaTableInfo schemaTableInfo = info.getSchemaTableInfo(event.schemaTableName());

         if(schemaTableInfo != null) {
            double width = Math.max(event.width(), SchemaTableInfo.MIN_SCHEMA_TABLE_WIDTH);
            double x = schemaTableInfo.getLeft();

            if(event.offsetLocation()) {
               x += schemaTableInfo.getWidth() - width;

               if(x < 0) {
                  width += x;
                  x = 0;
               }
            }

            info.setSchemaTableInfo(event.schemaTableName(),
                                    new SchemaTableInfo(x, schemaTableInfo.getTop(), width));
            WorksheetEventUtil.refreshAssembly(
               rws, table.getName(), false, commandDispatcher, principal);
         }
      }
   }
}
