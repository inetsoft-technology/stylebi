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

package inetsoft.web.composer.ws.assembly;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.ws.WorksheetModel;
import inetsoft.web.composer.ws.TableModeService;
import inetsoft.web.composer.ws.command.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@ClusterProxy
@Component
public class WorksheetEventService {
   public WorksheetEventService(ViewsheetService engine,
                                ObjectProvider<WorksheetEventServiceProxy> proxy)
   {
      this.engine = engine;
      this.proxy = proxy;
   }

   public String openWorksheet(Principal user, AssetEntry entry, boolean openAutoSaved,
                               boolean gettingStartedCreateQuery,
                               CommandDispatcher commandDispatcher) throws Exception
   {
      return openWorksheet(user, entry, openAutoSaved, gettingStartedCreateQuery, null,
                           commandDispatcher);
   }

   public String openWorksheet(Principal user, AssetEntry entry, boolean openAutoSaved,
                               boolean gettingStartedCreateQuery, String vsId,
                               CommandDispatcher commandDispatcher) throws Exception
   {
      String runtimeId = engine.openWorksheet(entry, user);
      WorksheetEventServiceProxy p = proxy.getIfAvailable();

      if(p != null) {
         return p.openWorksheet(
            runtimeId, user, entry, openAutoSaved, gettingStartedCreateQuery, vsId,
            commandDispatcher);
      }

      return null;
   }

   @ClusterWriteMethod
   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String openWorksheet(@ClusterProxyKey String id, Principal user, AssetEntry entry,
                               boolean openAutoSaved, boolean gettingStartedCreateQuery,
                               String vsId, CommandDispatcher commandDispatcher) throws Exception
   {
      RuntimeWorksheet rws = engine.getWorksheet(id, user);
      fixWorksheetMode(rws);
      clearGettingStatedRuntimeWs(rws, gettingStartedCreateQuery);
      linkViewsheetSandbox(rws, vsId, user);

      List errors = (List) AssetRepository.ASSET_ERRORS.get();

      String label, alias = entry.getAlias();

      if(alias != null && alias.length() > 0) {
         label = alias;
      }
      else {
         label = entry.getName();
      }

      if(openAutoSaved) {
         rws.setSavePoint(-1);
      }

      WorksheetModel worksheet = new WorksheetModel();
      worksheet.setId(entry.toIdentifier());
      worksheet.setRuntimeId(id);
      worksheet.setLabel(label);
      worksheet.setNewSheet("true".equals(entry.getProperty("openAutoSaved")));
      worksheet.setType("worksheet");
      worksheet.setCurrent(rws.getCurrent());
      worksheet.setSavePoint(rws.getSavePoint());
      worksheet.setSingleQuery(rws.getWorksheet().getWorksheetInfo().isSingleQueryMode());

      OpenWorksheetCommand command = new OpenWorksheetCommand();
      command.setWorksheet(worksheet);
      commandDispatcher.sendCommand(command);
      commandDispatcher.sendCommand(new WSInitCommand(user));

      resetTableModes(rws);
      WorksheetEventUtil.refreshWorksheet(rws, engine, commandDispatcher, user);

      Worksheet ws = rws.getWorksheet();
      String[] levels = null;

      if(ws != null) {
         WorksheetInfo wsInfo = ws.getWorksheetInfo();

         if(wsInfo != null) {
            levels = wsInfo.getMessageLevels();
         }
      }

      WSSetMessageLevelsCommand messageLevelsCommand = new WSSetMessageLevelsCommand();
      messageLevelsCommand.setMessageLevels(levels);
      commandDispatcher.sendCommand(messageLevelsCommand);

      if(!openAutoSaved && rws.getWorksheet() != null) {
         rws.replaceCheckpoint(rws.getWorksheet().prepareCheckpoint());
      }

      if(errors != null && errors.size() > 0) {
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < errors.size(); i++) {
            if(i > 0) {
               sb.append(", ");
            }

            sb.append(errors.get(i));
         }

         sb.append("(").append(entry.getDescription()).append(")");

         errors.clear();

         String msg = Catalog.getCatalog().getString(
            "common.mirrorAssemblies.updateFailed", sb.toString());
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(msg);
         messageCommand.setType(MessageCommand.Type.ERROR);
         commandDispatcher.sendCommand(messageCommand);
      }

      if(ws != null) {
         WorksheetInfo worksheetInfo = ws.getWorksheetInfo();
         Assembly[] assemblies = ws.getAssemblies();

         if(worksheetInfo.isSingleQueryMode() && assemblies != null && assemblies.length == 1) {
            Assembly assembly = assemblies[0];

            if(assembly instanceof SQLBoundTableAssembly) {
               WSEditAssemblyCommand editAssemblyCommand = WSEditAssemblyCommand.builder()
                  .assembly(WSAssemblyModelFactory.createModelFrom((WSAssembly) assembly, rws, user))
                  .build();
               commandDispatcher.sendCommand(editAssemblyCommand);
            }
         }
      }

      return id;
   }

   /**
    * Links the sandbox of the worksheet opened as its own document back to the
    * sandbox of the viewsheet it was opened from (e.g. the base worksheet link in
    * the composer's bottom status bar), so script expressions referencing viewsheet
    * assemblies (e.g. worksheet['TextInput1'].value) can resolve. No-op when the
    * worksheet was not opened from a running viewsheet (vsId is null) or that
    * viewsheet is no longer open.
    */
   private void linkViewsheetSandbox(RuntimeWorksheet rws, String vsId, Principal user) {
      if(rws == null || vsId == null) {
         return;
      }

      try {
         RuntimeViewsheet rvs = engine.getViewsheet(vsId, user);

         if(rvs == null) {
            return;
         }

         ViewsheetSandbox vsbox = rvs.getViewsheetSandbox().orElse(null);

         if(vsbox != null) {
            rws.getAssetQuerySandbox().setViewsheetSandbox(vsbox);
         }
      }
      catch(ExpiredSheetException e) {
         // the originating viewsheet is no longer open; nothing to link to
      }
      catch(Exception e) {
         LOG.debug("Unable to link worksheet sandbox to viewsheet {}", vsId, e);
      }
   }

   private static void clearGettingStatedRuntimeWs(RuntimeWorksheet rws,
                                                   boolean gettingStartedCreateQuery)
   {
      if(rws == null || !rws.isGettingStarted()) {
         return;
      }

      Worksheet worksheet = rws.getWorksheet();
      WorksheetInfo worksheetInfo = worksheet.getWorksheetInfo();
      Assembly[] assemblies = worksheet.getAssemblies();

      if(gettingStartedCreateQuery && worksheetInfo.isSingleQueryMode() && assemblies != null &&
         assemblies.length == 1)
      {
         return;
      }

      if(assemblies == null || assemblies.length == 0) {
         return;
      }

      for(Assembly assembly : assemblies) {
         worksheet.removeAssembly(assembly);
      }
   }

   private static void fixWorksheetMode(RuntimeWorksheet rws) {
      Worksheet worksheet = rws.getWorksheet();
      Assembly[] assemblies = worksheet.getAssemblies();
      WorksheetInfo worksheetInfo = worksheet.getWorksheetInfo();

      if(worksheetInfo.isSingleQueryMode()) {
         if(assemblies == null) {
            return;
         }

         if(assemblies.length > 1 ||
            !(assemblies[0] instanceof SQLBoundTableAssembly))
         {
            worksheetInfo.setMashupMode();
         }
      }
   }

   /**
    * Reset the table modes of the worksheet to design mode.
    *
    * @param rws the worksheet to reset the tables of
    */
   private static void resetTableModes(RuntimeWorksheet rws) {
      final Worksheet worksheet = rws.getWorksheet();
      final TableAssembly[] tables = Arrays.stream(worksheet.getAssemblies())
         .filter(TableAssembly.class::isInstance)
         .map(TableAssembly.class::cast)
         .toArray(TableAssembly[]::new);

      for(TableAssembly table : tables) {
         TableModeService.setDefaultTableMode(table, rws.getAssetQuerySandbox());
      }
   }

   private final ViewsheetService engine;
   private final ObjectProvider<WorksheetEventServiceProxy> proxy;
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetEventService.class);
}
