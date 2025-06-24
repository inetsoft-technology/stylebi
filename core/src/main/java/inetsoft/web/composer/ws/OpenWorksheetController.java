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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.MirrorAssemblyImpl;
import inetsoft.uql.asset.internal.MirrorTableAssemblyInfo;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.composer.model.ws.WorksheetModel;
import inetsoft.web.composer.ws.assembly.WorksheetEventService;
import inetsoft.web.composer.ws.command.OpenWorksheetCommand;
import inetsoft.web.composer.ws.command.WSInitCommand;
import inetsoft.web.composer.ws.event.OpenSheetEventValidator;
import inetsoft.web.composer.ws.event.OpenWorksheetEvent;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;

@Controller
public class OpenWorksheetController extends WorksheetController {
   @Autowired
   public OpenWorksheetController(RuntimeViewsheetManager runtimeViewsheetManager,
                                  AssetRepository assetRepository,
                                  WorksheetEventService eventService)
   {
      this.runtimeViewsheetManager = runtimeViewsheetManager;
      this.assetRepository = assetRepository;
      this.eventService = eventService;
   }

   @PostMapping("api/ws/open")
   @ResponseBody
   public OpenSheetEventValidator validateOpen(
      @RequestBody OpenWorksheetEvent event, Principal principal)
   {
      String id = event.id();
      AssetEntry entry = AssetEntry.createAssetEntry(id);
      boolean autoSaveFileExists = AutoSaveUtils.exists(entry, principal);
      AtomicBoolean notUndoable = new AtomicBoolean(false);
      String msg = getForbiddenSourcesMessage(entry, principal, notUndoable);
      return OpenSheetEventValidator.builder()
         // don't offer to restore if the assembly can't be restored. (56584)
         // 1. create a ws with snapshot and save.
         // 2. upload data into the table (don't save)
         // 3. reload composer and open the ws
         // exception if try to restore the auto-saved file.
         .autoSaveFileExists(autoSaveFileExists && !notUndoable.get())
         .forbiddenSourcesMessage(msg)
         .build();
   }

   @LoadingMask(true)
   @MessageMapping("/ws/open")
   public void openWorksheet(
      @Payload OpenWorksheetEvent event, Principal principal,
      CommandDispatcher commandDispatcher) throws Exception
   {
      ThreadContext.setPrincipal(principal);
      String id = event.id();
      AssetEntry entry = AssetEntry.createAssetEntry(id);
      String entryPath;

      if(entry == null || entry.getType() != AssetEntry.Type.WORKSHEET) {
         return;
      }

      if(entry.getScope() == AssetRepository.USER_SCOPE &&
         entry.getUser() != null && entry.getPath() != null)
      {
         String userName = entry.getUser().getName();
         entryPath = userName + "/" + entry.getPath();
      }
      else {
         entryPath = entry.getPath();
      }

      GroupedThread.withGroupedThread(groupedThread -> {
         groupedThread.addRecord(LogContext.WORKSHEET, entryPath);
      });

      if(!event.openAutoSavedFile()) {
         assetRepository.clearCache(entry);
      }

      entry.setProperty("openAutoSaved", event.openAutoSavedFile() + "");
      entry.setProperty("gettingStarted", event.gettingStartedWs() + "");
      String runtimeId = eventService.openWorksheet(
         principal, entry, event.openAutoSavedFile(), event.createQuery(),
         commandDispatcher);

      getRuntimeViewsheetRef().setRuntimeId(runtimeId);
      runtimeViewsheetManager.sheetOpened(principal, runtimeId);

      if(entry.getScope() != AssetRepository.TEMPORARY_SCOPE) {
         VSEventUtil.deleteAutoSavedFile(entry, principal);
      }

      GroupedThread.withGroupedThread(groupedThread -> {
         groupedThread.removeRecord(LogContext.WORKSHEET.getRecord(entry.getPath()));
      });
   }

   /**
    * From 12.2 NewWorksheetEvent.
    */
   @MessageMapping("/ws/new")
   public void newWorksheet(
      Principal principal, CommandDispatcher commandDispatcher) throws Exception
   {
      WorksheetService engine = getWorksheetEngine();

      String runtimeId = engine.openTemporaryWorksheet(principal, null);
      RuntimeWorksheet rws = engine.getWorksheet(runtimeId, principal);
      AssetEntry entry = rws.getEntry();

      WorksheetModel worksheet = new WorksheetModel();
      worksheet.setId(entry.toIdentifier());
      worksheet.setRuntimeId(runtimeId);
      worksheet.setLabel(rws.getEntry().getName());
      worksheet.setType("worksheet");
      worksheet.setNewSheet(true);
      worksheet.setInit(true);
      worksheet.setCurrent(rws.getCurrent());
      worksheet.setSavePoint(rws.getSavePoint());

      OpenWorksheetCommand command = new OpenWorksheetCommand();
      command.setWorksheet(worksheet);
      getRuntimeViewsheetRef().setRuntimeId(runtimeId);
      runtimeViewsheetManager.sheetOpened(principal, runtimeId);
      commandDispatcher.sendCommand(command);
      commandDispatcher.sendCommand(new WSInitCommand(principal));
   }

   private String getForbiddenSourcesMessage(AssetEntry entry, Principal user,
                                             AtomicBoolean notUndoable)
   {
      String forbiddenMsg = "";

      try {
         Worksheet sheet = (Worksheet)
            assetRepository.getSheet(entry, user, false, AssetContent.NO_DATA);

         if(sheet == null) {
            LOG.error("Trying to open non-existent worksheet: " + entry);
            return forbiddenMsg;
         }

         if(!AutoSaveUtils.isUndoable(entry, user)) {
            notUndoable.set(true);
            return "";
         }

         SecurityEngine security = SecurityEngine.getSecurity();

         for(Assembly assembly : sheet.getAssemblies()) {
            if(assembly instanceof BoundTableAssembly) {
               SourceInfo source = ((BoundTableAssembly) assembly).getSourceInfo();

               if(source.getType() == SourceInfo.QUERY || source.getType() == SourceInfo.MODEL) {
                  String resource = source.getSource() + "::" + source.getPrefix();

                  if(!SUtil.checkQueryPermission(resource, user)) {
                     forbiddenMsg = source.getType() == SourceInfo.QUERY ?
                        Catalog.getCatalog().getString("composer.ws.boundQueryForbidden") :
                        Catalog.getCatalog().getString("composer.ws.boundModelForbidden");
                     break;
                  }
               }
               else if(source.getType() == SourceInfo.DATASOURCE) {
                  if(!SUtil.checkDataSourcePermission(source.getSource(), user)) {
                     forbiddenMsg = Catalog.getCatalog().getString("composer.ws.boundDataSourcesForbidden");
                     break;
                  }
               }
               else if(source.getType() == SourceInfo.PHYSICAL_TABLE) {
                  if(!SUtil.checkDataSourcePermission(source.getPrefix(), user)) {
                     forbiddenMsg = Catalog.getCatalog().getString("composer.ws.boundDataSourcesForbidden");
                     break;
                  }

                  if(!security.checkPermission(
                     user, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS))
                  {
                     forbiddenMsg = Catalog.getCatalog().getString("composer.ws.boundPhysicalTableForbidden");
                     break;
                  }
               }
               else if(source.getType() == SourceInfo.CUBE) {
                  String resource = source.getPrefix() + "::" + source.getSource();

                  if(!security.checkPermission(
                     user, ResourceType.CUBE, resource, ResourceAction.READ))
                  {
                     forbiddenMsg = Catalog.getCatalog().getString("composer.ws.boundPhysicalTableForbidden");
                     break;
                  }
               }
            }
            else if(assembly instanceof MirrorTableAssembly) {
               MirrorTableAssemblyInfo info =
                  (MirrorTableAssemblyInfo) ((MirrorTableAssembly) assembly).getWSAssemblyInfo();
               MirrorAssemblyImpl mirror = info.getImpl();
               AssetEntry mirrorEntry = mirror.getEntry();

               if(mirrorEntry != null && !mirrorEntry.equals(entry) &&
                  mirrorEntry.getType() == AssetEntry.Type.WORKSHEET &&
                  mirrorEntry.getScope() == AssetRepository.GLOBAL_SCOPE)
               {
                  String resource = mirrorEntry.getPath();

                  if(!security.checkPermission(
                     user, ResourceType.ASSET, resource, ResourceAction.READ))
                  {
                     forbiddenMsg = Catalog.getCatalog().getString("composer.ws.boundAssetSourcesForbidden");
                     break;
                  }
               }
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to check permission on worksheet sources", e);
         forbiddenMsg = Catalog.getCatalog().getString("composer.ws.boundDataSourcesForbidden");
      }

      return forbiddenMsg;
   }

   private final RuntimeViewsheetManager runtimeViewsheetManager;
   private final AssetRepository assetRepository;
   private final WorksheetEventService eventService;
   private static final Logger LOG = LoggerFactory.getLogger(OpenWorksheetController.class);
}
