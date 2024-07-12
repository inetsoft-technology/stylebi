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
package inetsoft.web.viewsheet.controller;


import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.viewsheet.ComboBoxVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.VSListInputSelectionEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.text.Format;
import java.util.Date;

/**
 * Controller that provides REST endpoints and message handling for Combo Box.
 *
 * @since 12.3
 */
@Controller
public class VSComboBoxController {
   /**
    * Creates a new instance of <tt>VSComboBoxController</tt>.
    */
   @Autowired
   public VSComboBoxController(VSObjectService service,
                               VSInputService inputService, RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.service = service;
      this.inputService = inputService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   /**
    * Apply selection.
    *
    * @param principal    a principal identifying the current user.
    * @param event        the apply event
    * @param dispatcher   the command dispatcher.
    *
    * @throws Exception if the selection could not be applied.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/comboBox/applySelection")
   public void applySelection(@Payload VSListInputSelectionEvent event,
                              Principal principal, CommandDispatcher dispatcher,
                              @LinkUri String linkUri)
      throws Exception
   {
      final String assemblyName = event.assemblyName();
      final RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      final Viewsheet viewsheet = rvs.getViewsheet();
      final ComboBoxVSAssembly assembly = (ComboBoxVSAssembly) viewsheet.getAssembly(assemblyName);
      final ComboBoxVSAssemblyInfo info = (ComboBoxVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Object selectedValue = event.value();

      try {
         if(info.isCalendar() && selectedValue != null && !"".equals(selectedValue)) {
            try {
               selectedValue = new Date(Long.parseLong(selectedValue.toString()));
            }
            catch(Exception ex) {
               String fmt = info.getFormat().getFormat();
               String spec = info.getFormat().getFormatExtent();
               Format format = TableFormat.getFormat(fmt, spec);

               if(format != null) {
                  selectedValue = format.parseObject(selectedValue.toString());
               }
               else {
                  throw ex;
               }
            }
         }
      }
      catch(Exception e) {
         MessageCommand command = new MessageCommand();
         command.setMessage(e.getMessage());
         command.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(command);
         return;
      }

      inputService.singleApplySelection(assemblyName, selectedValue, principal, dispatcher, linkUri);
   }

   private final VSObjectService service;
   private final VSInputService inputService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
