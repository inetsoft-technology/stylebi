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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.WorksheetInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.event.SaveConsoleMessageLevelsEvent;
import inetsoft.web.factory.RemainingPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class ConsoleDialogMessageController {
   @Autowired
   public ConsoleDialogMessageController(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @GetMapping("/api/composer/console-dialog/get-message-levels/**")
   public String[] getConsoleMessageLevels(@RemainingPath String runtimeId, Principal principal)
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

   @PostMapping("/api/composer/console-dialog/save-message-levels/**")
   public boolean saveConsoleMessageLevels(@RequestBody SaveConsoleMessageLevelsEvent event,
                                          @RemainingPath String runtimeId, Principal principal)
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

   private final ViewsheetService viewsheetService;
}
