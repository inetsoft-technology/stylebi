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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.script.viewsheet.ScriptEvent;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.filereader.TextUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.controller.ComposerVSTableController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.table.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Controller that processes vs form events.
 */
@Controller
public class VSFormTableController {
   /**
    * Creates a new instance of <tt>VSFormTableController</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public VSFormTableController(VSFormTableServiceProxy vsFormTableServiceProxy,
                                RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.vsFormTableServiceProxy = vsFormTableServiceProxy;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   /**
      * Insert/Append row in form table.
      *
      * @param event      the event parameters.
      * @param principal  a principal identifying the current user.
      * @param dispatcher the command dispatcher.
      *
      * @throws Exception if unable to retrieve/edit object.
      */
   @Undoable
   @LoadingMask
   @MessageMapping("/formTable/addRow")
   public void addRow(@Payload InsertTableRowEvent event, @LinkUri String linkUri,
                      CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      vsFormTableServiceProxy.addRow(this.runtimeViewsheetRef.getRuntimeId(), event, linkUri, dispatcher, principal);
   }

   /**
    * Remove rows in form table.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/formTable/deleteRows")
   public void deleteRows(@Payload DeleteTableRowsEvent event, @LinkUri String linkUri,
                          CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      final String runtimeId = runtimeViewsheetRef.getRuntimeId();
      vsFormTableServiceProxy.deleteRows(runtimeId, event, linkUri, dispatcher, principal);
   }

   /**
    * Change form table input.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/formTable/edit")
   public void changeFormInput(@Payload ChangeFormTableCellInputEvent event,
                               @LinkUri String linkUri, CommandDispatcher dispatcher,
                               Principal principal)
      throws Exception
   {
      vsFormTableServiceProxy.changeFormInput(this.runtimeViewsheetRef.getRuntimeId(), event, linkUri, dispatcher, principal);
   }

   /**
    * Change form table input.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/formTable/apply")
   public void applyChanges(@Payload ApplyFormChangesEvent event,
                            @LinkUri String linkUri, CommandDispatcher dispatcher,
                            Principal principal)
      throws Exception
   {
      vsFormTableServiceProxy.applyChanges(runtimeViewsheetRef.getRuntimeId(), event, linkUri, dispatcher, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSFormTableServiceProxy vsFormTableServiceProxy;
}
