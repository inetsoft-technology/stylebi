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
package inetsoft.web.viewsheet.controller;

import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.*;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.TouchAssetEvent;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.io.*;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Controller
public class TouchAssetController {
   /**
    * Ripped from {@link inetsoft.analytic.composition.event.TouchAssetEvent}
    */
   @MessageMapping("/composer/touch-asset")
   public void touchAsset(@Payload TouchAssetEvent event, Principal principal,
                          CommandDispatcher commandDispatcher, @LinkUri String linkUri)
      throws Exception
   {
      try {
         RuntimeSheet rs = worksheetService.getSheet(runtimeViewsheetRef.getRuntimeId(), principal);
         boolean design = event.design();
         boolean changed = event.changed();
         boolean update = event.update();
         int width = event.width();
         int height = event.height();

         rs.access(design);

         // check if the runtime sheet is unlocked
         String lowner = rs.getLockOwner();
         String newOwner = worksheetService.getLockOwner(rs.getEntry());
         AssetEntry entry = rs.getEntry();
         boolean saved = false;

         if(!rs.isRuntime() && !rs.isPreview() && !rs.isLockProcessed() &&
            !rs.isEditable() && lowner != null &&
            !Tool.equals(newOwner, lowner))
         {
            MessageCommand command = new MessageCommand();
            command.setMessage(Catalog.getCatalog().getString(
               "common.AssetUnLockBy", lowner));
            command.setType(MessageCommand.Type.INFO);
            commandDispatcher.sendCommand(command);
            rs.setLockProcessed();
         }

         if(rs instanceof RuntimeViewsheet) {
            RuntimeViewsheet rvs = (RuntimeViewsheet) rs;
            String oid = rvs.getOriginalID();

            while(oid != null) {
               RuntimeSheet ors = worksheetService.getSheet(oid, principal);

               if(ors instanceof RuntimeViewsheet) {
                  ors.access(design);
                  oid = ((RuntimeViewsheet) ors).getOriginalID();
               }
               else {
                  break;
               }
            }

            if(rvs.getViewsheetSandbox().needRefresh.get()) {
               rvs.getViewsheetSandbox().needRefresh.set(false);
               MessageCommand command = new MessageCommand();
               command.setMessage(Catalog.getCatalog().getString("viewer.viewsheet.data.changed"));
               command.setType(MessageCommand.Type.CONFIRM);
               VSRefreshEvent refreshEvent = VSRefreshEvent.builder()
                  .tableMetaData(true)
                  .name("")
                  .confirmed(false)
                  .build();
               command.addEvent("/events/vs/refresh", refreshEvent);
               commandDispatcher.sendCommand(command);
            }

            Viewsheet vs = rvs.getViewsheet();

            if(vs == null) {
               return;
            }

            ViewsheetInfo vinfo = vs.getViewsheetInfo();

            if(rs.isRuntime()) {
               long changeTime = worksheetService.getDataChangedTime(rvs.getEntry());

               if(update && vinfo.isUpdateEnabled() && (changeTime != 0 && changeTime > rvs.getTouchTimestamp())) {
                  // refresh content
                  processRefreshEvent(principal, commandDispatcher, linkUri, width, height);
               }
            }
            else if(changed) {
               // @by ChrisSpagnoli bug1412273657631 #1 2014-10-30
               writeAutosaveFile(rs, vs, principal);
            }
         }
         else if(rs instanceof RuntimeWorksheet) {
            RuntimeWorksheet rws = (RuntimeWorksheet) rs;
            Worksheet ws = rws.getWorksheet();

            if(ws == null) {
               return;
            }

            if(!rs.isRuntime() && changed) {
               // @by ChrisSpagnoli bug1412273657631 #1 2014-10-30
               ws = (Worksheet) ws.clone();
               Worksheet.setIsTEMP(true);
               writeAutosaveFile(rs, ws, principal);
            }
         }

         if(expiredSheets.containsKey(rs.getID())) {
            expiredSheets.remove(rs.getID());
         }

         if(saved) {
            rs.setSavePoint(rs.getCurrent());
         }
      }
      catch(ExpiredSheetException e) {
         if(runtimeViewsheetRef.getRuntimeId() != null &&
            !expiredSheets.containsKey(runtimeViewsheetRef.getRuntimeId()))
         {
            expiredSheets.put(runtimeViewsheetRef.getRuntimeId(), System.currentTimeMillis());
            throw e;
         }
      }
      finally {
         Worksheet.setIsTEMP(false);
         removeClosedSheets();
      }
   }

   // @by ChrisSpagnoli bug1412273657631 #1 2014-10-30
   // Moved the functionality from viewsheet handling to a separate method,
   // so can also be invoked for worksheet.
   private void writeAutosaveFile(RuntimeSheet rs, XMLSerializable value,
                                  Principal principal) throws IOException {
      AssetEntry entry = rs.getEntry();
      File savefile = AutoSaveUtils.getAutoSavedFile(entry, principal);

      if(rs.getLastAccessed() > savefile.lastModified()) {
         ViewsheetSandbox vbox = rs instanceof RuntimeViewsheet
            ? ((RuntimeViewsheet) rs).getViewsheetSandbox() : null;

         if(vbox != null) {
            vbox.lockRead();
         }

         try {
//            XTableStorageService.setEnabled(false);

            byte[] data = AbstractIndexedStorage.encodeXMLSerializable(
               value, entry.toIdentifier());

            if(data == null) {
               return;
            }

            if(!savefile.exists()) {
               savefile.createNewFile();
            }

            try(FileOutputStream outstream = new FileOutputStream(savefile)) {
               outstream.write(data);
               outstream.flush();
            }
         }
         finally {
//            XTableStorageService.setEnabled(true);

            if(vbox != null) {
               vbox.unlockRead();
            }
         }
      }
   }

   /**
    * Remove closed sheets.
    */
   private void removeClosedSheets() {
      long now = System.currentTimeMillis();
      Map<Object, Long> expiredSheets = new ConcurrentHashMap<>(this.expiredSheets);
      List<Object> closed = expiredSheets.entrySet().stream()
         .filter(e -> now - e.getValue() > 600000)
         .map(Map.Entry::getKey)
         .collect(Collectors.toList());
      closed.forEach(id -> this.expiredSheets.remove(id));
   }

   /**
    * Refresh the viewsheet.
    */
   private void processRefreshEvent(Principal principal,
                                    CommandDispatcher commandDispatcher, String linkUri,
                                    int width, int height)
      throws Exception
   {
      VSRefreshEvent refresh = VSRefreshEvent.builder()
         .initing(false)
         .name("")
         .confirmed(false)
         .autoRefresh(true)
         .width(width)
         .height(height)
         .build();
      vsRefreshController.refreshViewsheet(
         refresh, principal, commandDispatcher, linkUri);
   }

   @Autowired
   protected void setRuntimeViewsheetRef(
      RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @Autowired
   @Qualifier("worksheetService")
   public void setWorksheetService(WorksheetService worksheetService) {
      this.worksheetService = worksheetService;
   }

   @Autowired
   protected void setVSRefreshController(
      VSRefreshController vsRefreshController)
   {
      this.vsRefreshController = vsRefreshController;
   }

   private Map<Object, Long> expiredSheets = new ConcurrentHashMap<>();
   private RuntimeViewsheetRef runtimeViewsheetRef;
   private WorksheetService worksheetService;
   private VSRefreshController vsRefreshController;
}
