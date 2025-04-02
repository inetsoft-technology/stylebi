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

package inetsoft.web.composer.ws.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.*;
import inetsoft.uql.asset.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.WorksheetOptionPaneModel;
import inetsoft.web.composer.model.ws.WorksheetPropertyDialogModel;
import inetsoft.web.composer.ws.WorksheetControllerService;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.SetWorksheetInfoCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
@ClusterProxy
public class WorksheetPropertyDialogService extends WorksheetControllerService {

   public WorksheetPropertyDialogService(ViewsheetService viewsheetService)
   {
      super(viewsheetService);
   }


   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public WorksheetPropertyDialogModel getWorksheetInfo(@ClusterProxyKey String runtimeId, Principal principal) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine()
         .getWorksheet(Tool.byteDecode(runtimeId), principal);
      WorksheetPropertyDialogModel result = new WorksheetPropertyDialogModel();
      WorksheetOptionPaneModel worksheetOptionPaneModel = new WorksheetOptionPaneModel(
         rws);
      result.setWorksheetOptionPaneModel(worksheetOptionPaneModel);
      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void setWorksheetInfo(@ClusterProxyKey String runtimeId, WorksheetPropertyDialogModel model,
                                Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = super.getWorksheetEngine().getWorksheet(
         Tool.byteDecode(runtimeId), null);
      boolean success = process(rws, model, principal, commandDispatcher);

      if(success) {
         SetWorksheetInfoCommand command = SetWorksheetInfoCommand.builder()
            .label(rws.getEntry().toView())
            .build();
         commandDispatcher.sendCommand(command);
      }

      return null;
   }

   /**
    * Process save worksheet event.
    *
    * @return true if property was updated, false otherwise.
    */
   private boolean process(
      RuntimeWorksheet rws, WorksheetPropertyDialogModel model,
      Principal user, CommandDispatcher commandDispatcher) throws Exception
   {
      boolean reportSource = model.getWorksheetOptionPaneModel().getDataSource();
      WorksheetInfo winfo = new WorksheetInfo();
      winfo.setAlias(model.getWorksheetOptionPaneModel().getAlias());
      winfo.setDescription(model.getWorksheetOptionPaneModel().getDescription());
      AssetEntry entry = rws.getEntry();
      Worksheet ws = rws.getWorksheet();

      entry.setReportDataSource(reportSource);

      boolean refresh = ws.setWorksheetInfo(winfo);

      if(refresh) {
         rws.getAssetQuerySandbox().resetTableLens();
         WorksheetEventUtil.refreshWorksheet(
            rws, super.getWorksheetEngine(), commandDispatcher, user);
      }

      WorksheetService wengine = super.getWorksheetEngine();
      AssetRepository engine = wengine.getAssetRepository();
      String alias = model.getWorksheetOptionPaneModel().getAlias();
      String desc0 = model.getWorksheetOptionPaneModel().getDescription();

      if(engine.containsEntry(entry)) {
         entry.setAlias(alias != null ? alias : "");
         entry.setProperty("description", desc0);
         String desc = entry.getDescription();
         desc = desc.substring(0, desc.indexOf("/") + 1);
         desc += wengine.localizeAssetEntry(entry.getPath(), user,
                                            true, entry, entry
                                               .getScope() == AssetRepository.USER_SCOPE);
         entry.setProperty("_description_", desc);
         entry.setProperty("localStr",
                           desc.substring(desc.lastIndexOf("/") + 1));
         rws.setEntry(entry);
         rws.setEditable(true);

         return true;
      }

      return false;
   }
}
