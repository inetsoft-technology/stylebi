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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.HideColumnsDialogModel;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

/**
 * Controller that provides the endpoints for the hide columns dialog.
 *
 * @since 12.3
 */
@Controller
public class HideColumnsDialogController {
   /**
    * Creates a new instance of <tt>HideColumnsDialogController</tt>.
    *
    * @param runtimeViewsheetRef RuntimeViewsheetRef instance
    * @param placeholderService  PlaceholderService instance
    * @param viewsheetService    Viewsheet engine
    */
   @Autowired
   public HideColumnsDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                      PlaceholderService placeholderService,
                                      ViewsheetService viewsheetService,
                                      VSInputService vsInputService,
                                      VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
      this.vsInputService = vsInputService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   /**
    * Gets the model for the hide column dialog.
    *
    * @param objectId  the object identifier.
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the user information.
    *
    * @return the model.
    */
   @RequestMapping(
      value = "/api/composer/vs/hide-columns-dialog-model",
      method = RequestMethod.GET
   )
   @ResponseBody
   public HideColumnsDialogModel getColumnOptionDialogModel(@RequestParam("objectId") String objectId,
                                                             @RequestParam("runtimeId") String runtimeId,
                                                             Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      TableVSAssembly assembly = (TableVSAssembly) viewsheet.getAssembly(objectId);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();

      List<String> availableColumns = new ArrayList<>();
      ColumnSelection columns = info.getColumnSelection();
      Enumeration<DataRef> refs = columns.getAttributes();

      while(refs.hasMoreElements()) {
         availableColumns.add(refs.nextElement().getAttribute());
      }

      List<String> hiddenColumnsList = new ArrayList<>();
      ColumnSelection hiddenColumns = info.getHiddenColumns();
      refs = hiddenColumns.getAttributes();

      while(refs.hasMoreElements()) {
         hiddenColumnsList.add(refs.nextElement().getAttribute());
      }

      return HideColumnsDialogModel.builder()
         .availableColumns(availableColumns)
         .hiddenColumns(hiddenColumnsList)
         .build();
   }

   /**
    * Sets information gathered from the hide column dialog.
    *
    * @param objectId   the object id.
    * @param model      the hyperlink dialog model.
    * @param principal  the user information.
    * @param dispatcher the command dispatcher.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/composer/vs/hide-columns-dialog-model/{objectId}")
   public void setColumnOptionDialogModel(@DestinationVariable("objectId") String objectId,
                                          @Payload HideColumnsDialogModel model,
                                          Principal principal,
                                          CommandDispatcher dispatcher,
                                          @LinkUri String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      TableVSAssembly assembly = (TableVSAssembly) viewsheet.getAssembly(objectId);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();
      TableVSAssemblyInfo clone = (TableVSAssemblyInfo)info.clone();
      ColumnSelection oavailable = info.getColumnSelection();
      ColumnSelection ohidden = info.getHiddenColumns();
      List<DataRef> columns = getAllColumns(oavailable, ohidden);
      ColumnSelection availableColumns = new ColumnSelection();
      ColumnSelection hiddenColumns = new ColumnSelection();

      for(int i = 0; i < columns.size(); i++) {
         DataRef ref = columns.get(i);

         if(model.availableColumns().contains(ref.getAttribute())) {
            availableColumns.addAttribute(ref);
         }

         if(model.hiddenColumns().contains(ref.getAttribute())) {
            hiddenColumns.addAttribute(ref);
         }
      }

      clone.setColumnSelection(availableColumns);
      clone.setHiddenColumns(hiddenColumns);
      assemblyInfoHandler.apply(rvs, clone, viewsheetService,
         false, false, false, false, dispatcher, null, null, linkUri, null);
   }

   private List<DataRef> getAllColumns(ColumnSelection show, ColumnSelection hide) {
      List<DataRef> cols = new ArrayList<>();
      addColumns(cols, show);
      addColumns(cols, hide);
      return cols;
   }

   private void addColumns(List<DataRef> all, ColumnSelection part) {
      Enumeration<DataRef> refs = part.getAttributes();
      DataRef currentRef = null;

      while(refs.hasMoreElements()) {
         currentRef = refs.nextElement();
         all.add(currentRef);
      }
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private final VSInputService vsInputService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
}
