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
import inetsoft.report.StyleConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.*;
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
 * Controller that provides the endpoints for the vs sorting dialog.
 *
 * @since 12.3
 */
@Controller
public class VSSortingDialogController {
   /**
    * Creates a new instance of <tt>VSSortingDialogController</tt>.
    */
   @Autowired
   public VSSortingDialogController(
      VSObjectService service, VSInputService vsInputService, ViewsheetService viewsheetService,
      RuntimeViewsheetRef runtimeViewsheetRef, VSAssemblyInfoHandler vsAssemblyInfoHandler)
   {
      this.service = service;
      this.vsInputService = vsInputService;
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.vsAssemblyInfoHandler = vsAssemblyInfoHandler;
   }

   /**
    * Gets the model for the vs sorting dialog.
    *
    * @param objectId  the object identifier.
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the user information.
    *
    * @return the model.
    */
   @RequestMapping(
      value = "/api/composer/vs/vs-sorting-dialog-model",
      method = RequestMethod.GET
   )
   @ResponseBody
   public VSSortingDialogModel getVSSortingDialogModel(@RequestParam("objectId") String objectId,
                                                       @RequestParam("runtimeId") String runtimeId, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalcTableVSAssembly assembly = (CalcTableVSAssembly) viewsheet.getAssembly(objectId);
      CalcTableVSAssemblyInfo assemblyInfo = (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();
      SourceInfo sourceInfo = assemblyInfo.getSourceInfo();
      SortInfo sortInfo = assemblyInfo.getSortInfo();
      ColumnSelection columnSelection = null;
      SortRef[] sortRefs = sortInfo == null ? new SortRef[0] : sortInfo.getSorts();
      List<VSSortRefModel> columnSortList = new ArrayList<>();
      List<VSSortRefModel> columnNoneList = new ArrayList<>();
      DataRef[] allColumns = new DataRef[0];

      if(sourceInfo != null && sourceInfo.getSource() != null) {
         columnSelection =
            vsInputService.getTableColumns(rvs, sourceInfo.getSource(), null, null, null,
                                           false, false, true, false, false, true, principal);
         allColumns = (DataRef[]) Collections.list(columnSelection.getAttributes()).toArray(new DataRef[0]);
      }

      if(columnSelection != null) {
         for(SortRef sortRef : sortRefs) {
            if(!containsColumn(sortRef.getDataRef(), allColumns)) {
               continue;
            }

            VSSortRefModel sortRefModel = new VSSortRefModel();
            sortRefModel.setName(sortRef.getDataRef().getName());
            sortRefModel.setView(sortRef.getDataRef().toView());
            sortRefModel.setOrder(sortRef.getOrder());
            columnSortList.add(sortRefModel);
         }

         for(int i = 0; i < columnSelection.getAttributeCount(); i++) {
            DataRef ref = columnSelection.getAttribute(i);

            if(containsColumn(ref, sortRefs)) {
               continue;
            }

            VSSortRefModel sortRefModel = new VSSortRefModel();
            sortRefModel.setName(ref.getName());
            sortRefModel.setView(ref.toView());
            sortRefModel.setOrder(StyleConstants.SORT_NONE);
            columnNoneList.add(sortRefModel);
         }
      }

      VSSortingDialogModel vsSortingDialogModel = new VSSortingDialogModel();
      VSSortingPaneModel vsSortingPaneModel = vsSortingDialogModel.getVsSortingPaneModel();
      vsSortingPaneModel.setColumnSortList(columnSortList.toArray(new VSSortRefModel[0]));
      vsSortingPaneModel.setColumnNoneList(columnNoneList.toArray(new VSSortRefModel[0]));

      return vsSortingDialogModel;
   }

   @Undoable
   @MessageMapping("/composer/vs/vs-sorting-dialog-model/{objectId}")
   public void setVSSortingDialogModel(@DestinationVariable("objectId") String objectId,
                                       @Payload VSSortingDialogModel model,
                                       Principal principal,
                                       CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs =
         service.getRuntimeViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      CalcTableVSAssembly assembly = (CalcTableVSAssembly) viewsheet.getAssembly(objectId);
      CalcTableVSAssemblyInfo assemblyInfo = (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();
      SourceInfo sourceInfo = assemblyInfo.getSourceInfo();
      ColumnSelection columnSelection = sourceInfo == null ? null :
         vsInputService.getTableColumns(rvs, sourceInfo.getSource(), null, null, null,
                                        false, false, true, false, false, true, principal);
      SortInfo sortInfo = new SortInfo();

      if(columnSelection != null) {
         for(VSSortRefModel vsSortRefModel : model.getVsSortingPaneModel().getColumnSortList()) {
            SortRef sortRef = new SortRef();
            DataRef ref = columnSelection.getAttribute(vsSortRefModel.getName());
            sortRef.setDataRef(ref);
            sortRef.setOrder(vsSortRefModel.getOrder());
            sortInfo.addSort(sortRef);
         }
      }

      assemblyInfo.setSortInfo(sortInfo);
      this.vsAssemblyInfoHandler.apply(rvs, assemblyInfo, viewsheetService, false, false, true, false, dispatcher);
   }

   private boolean containsColumn(DataRef ref, DataRef[] refArray) {
      for(DataRef ref0 : refArray) {
         if(ref0 instanceof SortRef) {
            ref0 = ((SortRef) ref0).getDataRef();
         }

         if(Tool.equals(ref, ref0)) {
            return true;
         }
      }

      return false;
   }

   private final VSObjectService service;
   private final VSInputService vsInputService;
   private final ViewsheetService viewsheetService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private VSAssemblyInfoHandler vsAssemblyInfoHandler;
}
