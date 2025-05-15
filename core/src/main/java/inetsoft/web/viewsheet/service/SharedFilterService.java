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

package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.ConfirmDataException;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.web.viewsheet.command.UpdateSharedFiltersCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;

@Service
public class SharedFilterService {
   @Autowired
   public SharedFilterService(SimpMessagingTemplate messagingTemplate,
                              ViewsheetService viewsheetService)
   {
      this.messagingTemplate = messagingTemplate;
      this.viewsheetService = viewsheetService;
   }

   public boolean processExtSharedFilters(VSAssembly assembly, int hint,
                                          RuntimeViewsheet vs, Principal principal,
                                          CommandDispatcher dispatcher)
   {
      // @by davidd bug1370506649395, Propagate View Changes as well, for
      // example: Calendar "Switch to ..." operations
      if((hint & VSAssembly.OUTPUT_DATA_CHANGED) !=
         VSAssembly.OUTPUT_DATA_CHANGED && (hint & VSAssembly.VIEW_CHANGED) !=
         VSAssembly.VIEW_CHANGED)
      {
         return false;
      }

      // for shared selection, refresh vsobject command should specified
      // process, add "SHARED_HINT" to make the RefreshVSObjectCommand not
      // equals the original RefreshVSObjectCommand, see bug1247647231402
      dispatcher.setSharedHint(assembly.getAbsoluteName());

      List<ChangedViewsheet> changed = viewsheetService.invokeOnAll(new ApplyFiltersTask(vs.getID(), assembly, principal))
         .stream()
         .flatMap(Collection::stream)
         .toList();

      for(ChangedViewsheet rvs : changed) {
         SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
         headerAccessor.setSessionId(rvs.getSocketSessionId());
         headerAccessor.setLeaveMutable(true);
         headerAccessor.setNativeHeader(
            CommandDispatcher.COMMAND_TYPE_HEADER, "UpdateSharedFiltersCommand");
         headerAccessor.setNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR, rvs.getId());
         String user = rvs.getSocketUserName();

         if(user == null) {
            user = dispatcher.getUserName();
         }

         messagingTemplate.convertAndSendToUser(
            user, CommandDispatcher.COMMANDS_TOPIC,
            new UpdateSharedFiltersCommand(), headerAccessor.getMessageHeaders());
      }

      dispatcher.setSharedHint(null);
      return false;
   }

   private final SimpMessagingTemplate messagingTemplate;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(SharedFilterService.class);

   private static final class ChangedViewsheet implements Serializable {
      public ChangedViewsheet(RuntimeViewsheet rvs) {
         this(rvs.getID(), rvs.getSocketSessionId(), rvs.getSocketUserName());
      }

      public ChangedViewsheet(String id, String socketSessionId, String socketUserName) {
         this.id = id;
         this.socketSessionId = socketSessionId;
         this.socketUserName = socketUserName;
      }

      public String getId() {
         return id;
      }

      public String getSocketSessionId() {
         return socketSessionId;
      }

      public String getSocketUserName() {
         return socketUserName;
      }

      private final String id;
      private final String socketSessionId;
      private final String socketUserName;
   }

   private static final class ApplyFiltersTask implements ViewsheetService.Task<ArrayList<ChangedViewsheet>> {
      public ApplyFiltersTask(String rid, VSAssembly assembly, Principal principal) {
         this.rid = rid;
         this.assembly = assembly;
         this.principal = principal;
      }

      @Override
      public ArrayList<ChangedViewsheet> apply(ViewsheetService service) throws Exception {
         ArrayList<ChangedViewsheet> result = new ArrayList<>();

         try {
            RuntimeViewsheet[] arr = service.getRuntimeViewsheets(principal);

            for(RuntimeViewsheet rvs : arr) {
               if(rvs.getID().equals(rid)) {
                  continue;
               }

               boolean changed = rvs.getViewsheetSandbox().processSharedFilters(
                  assembly, null, true);

               if(changed) {
                  result.add(new ChangedViewsheet(rvs));
               }
            }
         }
         catch(ConfirmDataException ignored) {
            // ignored
         }
         catch(ConfirmException cex) {
            throw cex;
         }
         catch(Exception ex) {
            LOG.warn("Failed to process the shared filters", ex);
         }

         return result;
      }

      private final String rid;
      private final VSAssembly assembly;
      private final Principal principal;
   }
}
