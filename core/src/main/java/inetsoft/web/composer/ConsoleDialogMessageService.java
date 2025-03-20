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

package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.WorksheetInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.event.SaveConsoleMessageLevelsEvent;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@ClusterProxy
public class ConsoleDialogMessageService {

   public ConsoleDialogMessageService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String[] getConsoleMessageLevels(@ClusterProxyKey String runtimeId, Principal principal)
   {
      RuntimeSheet sheet = viewsheetService.getSheet(Tool.byteDecode(runtimeId), principal);

      if(sheet instanceof RuntimeViewsheet) {
         Viewsheet vs = ((RuntimeViewsheet) sheet).getViewsheet();

         if(vs != null) {
            ViewsheetInfo vsInfo = vs.getViewsheetInfo();

            if(vsInfo != null) {
               return vsInfo.getMessageLevels();
            }
         }
      }

      return null;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public boolean saveConsoleMessageLevels(@ClusterProxyKey String runtimeId, SaveConsoleMessageLevelsEvent event,
                                                                              Principal principal)
   {
      RuntimeSheet sheet = viewsheetService.getSheet(Tool.byteDecode(runtimeId), principal);

      if(sheet instanceof RuntimeViewsheet) {
         Viewsheet vs = ((RuntimeViewsheet) sheet).getViewsheet();

         if(vs != null) {
            ViewsheetInfo vsInfo = vs.getViewsheetInfo();

            if(vsInfo != null) {
               vsInfo.setMessageLevels(event.getMessageLevels());
               return true;
            }
         }
      }
      else if(sheet instanceof RuntimeWorksheet) {
         Worksheet ws = ((RuntimeWorksheet) sheet).getWorksheet();

         if(ws != null) {
            WorksheetInfo wsInfo = ws.getWorksheetInfo();

            if(wsInfo != null) {
               wsInfo.setMessageLevels(event.getMessageLevels());
               return true;
            }
         }
      }

      return false;
   }

   private ViewsheetService viewsheetService;
}
