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
package inetsoft.web.composer.controller;

import inetsoft.report.composition.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.*;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class HeartBeatController {

   @RequestMapping(value="/test/vs/touch-asset", method = RequestMethod.POST)
   @ResponseBody
   public String touchAsset(@RequestParam("viewsheetId") String vsId,
                          @RequestParam(value = "design", defaultValue = "false") boolean design,
                          @RequestParam(value = "changed", defaultValue = "false") boolean changed,
                          @RequestParam(value = "update", defaultValue = "false") boolean update,
                          Principal principal,
                          @LinkUri String linkUri)
      throws Exception
   {
      try {
         RuntimeSheet rs = worksheetService.getSheet(vsId, principal);
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
            /*
            MessageCommand command = new MessageCommand();
            command.setMessage(Catalog.getCatalog().getString(
               "common.AssetUnLockBy", lowner));
            command.setType(MessageCommand.Type.INFO);
            commandDispatcher.sendCommand(command);
            */

            rs.setLockProcessed();

            return "AssetUnLockBy";
         }

         if(rs instanceof RuntimeViewsheet) {
            RuntimeViewsheet rvs = (RuntimeViewsheet) rs;

            if(rvs.getViewsheetSandbox().needRefresh.get()) {
               rvs.getViewsheetSandbox().needRefresh.set(false);
               /*
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
               */

               return "changed";
            }

            Viewsheet vs = rvs.getViewsheet();

            if(vs == null) {
               return "vs is null";
            }

            ViewsheetInfo vinfo = vs.getViewsheetInfo();

            if(rs.isRuntime()) {
               long changeTime = worksheetService.getDataChangedTime(rvs.getEntry());
               boolean monitorEnabled = "true".equalsIgnoreCase(
                  SreeEnv.getProperty("assetMonitor.enabled"));

               if(update && vinfo.isUpdateEnabled() && (!monitorEnabled ||
                  (changeTime != 0 && changeTime > rvs.getTouchTimestamp())))
               {
//                  VSEventUtil.changeAdhocFilterStatus(rvs, command);
                  // refresh content
                  // processRefreshEvent(principal, commandDispatcher, linkUri);
                  return "refresh";
               }
            }
            else if(changed &&
               entry.getScope() != AssetRepository.TEMPORARY_SCOPE) {
               writeAutosaveFile(rs, vs, principal);
            }
         }
         else if(rs instanceof RuntimeWorksheet) {
            RuntimeWorksheet rws = (RuntimeWorksheet) rs;
            Worksheet ws = rws.getWorksheet();

            if(ws == null) {
               return "ws is null";
            }

            if(!rs.isRuntime() && changed) {
               // @by ChrisSpagnoli bug1412273657631 #1 2014-10-30
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
         e.printStackTrace();

         if(!expiredSheets.containsKey(vsId)) {
            expiredSheets.put(vsId, System.currentTimeMillis());
            throw e;
         }
      }
      finally {
         removeClosedSheets();
      }

      return "success";
   }

   private void writeAutosaveFile(RuntimeSheet rs, XMLSerializable value,
                                  Principal principal) throws IOException
   {
      AssetEntry entry = rs.getEntry();
      byte[] data = AbstractIndexedStorage.encodeXMLSerializable(
         value, entry.toIdentifier());

      if(rs.getLastAccessed() > AutoSaveUtils.getLastModified(entry, principal)) {
         AutoSaveUtils.writeAutoSaveFile(data, entry, principal);
      }
   }

   private void removeClosedSheets() {
      long now = System.currentTimeMillis();
      List<Object> closed = expiredSheets.entrySet().stream()
         .filter((e) -> now - e.getValue() > 600000)
         .map(Map.Entry::getKey)
         .collect(Collectors.toList());
      closed.forEach((id) -> expiredSheets.remove(id));
   }

   @Autowired
   @Qualifier("worksheetService")
   public void setWorksheetService(WorksheetService worksheetService) {
      this.worksheetService = worksheetService;
   }

   private Map<Object, Long> expiredSheets = new HashMap<>();
   private WorksheetService worksheetService;
}
