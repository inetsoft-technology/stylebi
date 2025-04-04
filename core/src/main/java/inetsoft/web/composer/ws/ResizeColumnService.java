/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSResizeColumnEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
@ClusterProxy
public class ResizeColumnService extends WorksheetControllerService {

   public ResizeColumnService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void resizeColumn(@ClusterProxyKey String runtimeId, WSResizeColumnEvent event,
                            Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getRuntimeWorksheet(runtimeId, principal);
      Worksheet ws = rws.getWorksheet();
      String tname = event.getTableName();
      ColumnRef column = (ColumnRef) event.getColumnRef().createDataRef();
      int width = event.getWidth();
      TableAssembly table = (TableAssembly) ws.getAssembly(tname);
      ColumnSelection ocolumns = table.getColumnSelection(false);
      ColumnSelection ncolumns = table.getColumnSelection(true);
      int index = ocolumns.indexOfAttribute(column);

      if(index >= 0) {
         column = (ColumnRef) ocolumns.getAttribute(index);
         column.setPixelWidth(width);
      }

      index = ncolumns.indexOfAttribute(column);

      if(index >= 0) {
         column = (ColumnRef) ncolumns.getAttribute(index);
         column.setPixelWidth(width);
      }

      table.setColumnSelection(ncolumns, true);
      table.setColumnSelection(ocolumns, false);

      WorksheetEventUtil.loadTableData(rws, tname, true, false);
      WorksheetEventUtil.refreshAssembly(rws, tname, true, commandDispatcher, principal);
      WorksheetEventUtil.layout(rws, commandDispatcher);
      AssetEventUtil.refreshTableLastModified(ws, tname, true);

      return null;
   }
}
