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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.ConfirmDataException;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.GroupedThread;
import inetsoft.web.viewsheet.command.UpdateSharedFiltersCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

@Service
public class SharedFilterService {
   @Autowired
   public SharedFilterService(CommandDispatcherService commandDispatcherService,
                               ViewsheetService viewsheetService)
   {
      this.commandDispatcherService = commandDispatcherService;
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

      // Resolve filterId on the source node while we still have access to the viewsheet,
      // since VSAssemblyInfo.vs is transient and will be null on remote cluster nodes.
      final String filterId = vs.getViewsheet().getViewsheetInfo().getFilterID(assembly.getName());

      // skip cluster broadcast if this assembly has no shared filter ID configured
      if(filterId == null) {
         return false;
      }

      // skip cluster broadcast if the user has fewer than 2 viewsheets open across
      // all cluster nodes — there's nothing to synchronize
      if(!viewsheetService.hasAtLeastRuntimeViewsheets(principal, 2)) {
         return false;
      }

      final String userName = dispatcher.getUserName();

      // Use a dedicated executor — invokeOnAll blocks for up to 5 minutes waiting on
      // remote nodes and must not run on the common ForkJoinPool.
      CompletableFuture.runAsync(() -> {
         List<ChangedViewsheet> changed = viewsheetService.invokeOnAll(
               new ApplyFiltersTask(vs.getID(), assembly, filterId, principal))
            .stream()
            .flatMap(Collection::stream)
            .toList();

         for(ChangedViewsheet rvs : changed) {
            try {
               SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
               headerAccessor.setSessionId(rvs.getSocketSessionId());
               headerAccessor.setLeaveMutable(true);
               headerAccessor.setNativeHeader(
                  CommandDispatcher.COMMAND_TYPE_HEADER, "UpdateSharedFiltersCommand");
               headerAccessor.setNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR, rvs.getId());
               String user = rvs.getSocketUserName();

               if(user == null) {
                  user = userName;
               }

               commandDispatcherService.convertAndSendToUser(
                  user, CommandDispatcher.COMMANDS_TOPIC,
                  new UpdateSharedFiltersCommand(), headerAccessor.getMessageHeaders());
            }
            catch(Exception ex) {
               LOG.warn("Failed to send shared filter notification for viewsheet {}", rvs.getId(), ex);
            }
         }
      }, SHARED_FILTER_EXECUTOR);

      return false;
   }

   private final CommandDispatcherService commandDispatcherService;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(SharedFilterService.class);
   private static final Executor SHARED_FILTER_EXECUTOR =
      Executors.newFixedThreadPool(5, r -> new GroupedThread(r, "SharedFilter"));

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

   private static final class ApplyFiltersTask
      implements ViewsheetService.Task<ArrayList<ChangedViewsheet>>
   {
      public ApplyFiltersTask(String rid, VSAssembly assembly, String filterId,
                              Principal principal)
      {
         this.rid = rid;
         this.assembly = assembly;
         this.filterId = filterId;
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

               Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();
               boolean changed = false;

               if(box.isPresent()) {
                  changed = box.get().processSharedFilters(assembly, filterId, null, true);
               }

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
      private final String filterId;
      private final Principal principal;
   }
}
