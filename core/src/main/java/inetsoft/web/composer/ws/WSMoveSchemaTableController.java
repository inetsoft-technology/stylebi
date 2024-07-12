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
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSMoveSchemaTablesEvent;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.awt.geom.Point2D;
import java.security.Principal;

@Controller
public class WSMoveSchemaTableController extends WorksheetController {
   @Undoable
   @MessageMapping("/composer/worksheet/move-schemas")
   public void moveSchemas(
      @Payload WSMoveSchemaTablesEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final Worksheet ws = rws.getWorksheet();
      final RelationalJoinTableAssembly joinTable = (RelationalJoinTableAssembly) ws
         .getAssembly(event.getJoinTableName());
      final CompositeTableAssemblyInfo info = (CompositeTableAssemblyInfo) joinTable
         .getTableInfo();
      final String[] names = event.getAssemblyNames();
      final int x = event.getOffsetLeft();
      final int y = event.getOffsetTop();

      for(String name : names) {
         Point2D.Double pos = info.getSchemaPixelPosition(name);

         if(pos != null) {
            pos = new Point2D.Double(pos.x + x, pos.y + y);
            info.setSchemaPixelPosition(name, pos);
         }
      }

      WorksheetEventUtil.layoutSchema(joinTable, names, commandDispatcher);
   }
}
