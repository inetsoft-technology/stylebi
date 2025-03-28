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

package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.HideColumnsDialogModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class HideColumnsDialogService {

   public HideColumnsDialogService(ViewsheetService viewsheetService,
                                      VSAssemblyInfoHandler assemblyInfoHandler)
   {
      this.viewsheetService = viewsheetService;
      this.assemblyInfoHandler = assemblyInfoHandler;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public HideColumnsDialogModel getColumnOptionDialogModel(@ClusterProxyKey String runtimeId,
                                                            String objectId, Principal principal) throws Exception
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

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setColumnOptionDialogModel(@ClusterProxyKey String runtimeId, String objectId,
                                          HideColumnsDialogModel model, Principal principal,
                                          CommandDispatcher dispatcher, String linkUri) throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
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

      return null;
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

   private final ViewsheetService viewsheetService;
   private final VSAssemblyInfoHandler assemblyInfoHandler;
}
