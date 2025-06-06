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
import inetsoft.cluster.*;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class VSLifecycleControllerService {

   public VSLifecycleControllerService(ViewsheetService viewsheetService,
                                       CoreLifecycleService coreLifecycleService,
                                       VSBookmarkService vsBookmarkService) {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.vsBookmarkService = vsBookmarkService;

   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Map<String, String[]> setRuntimeParameters(@ClusterProxyKey String runtimeId,
                                                     Map<String, String[]> parameters, Principal principal) throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeId, principal);
      parameters = parameters == null ? new HashMap<>() : parameters;

      if(rvs != null && rvs.getViewsheetSandbox() != null) {
         Hyperlink.Ref hyperlinkRef = new Hyperlink.Ref();
         // get selection parameters of source rvs
         VSUtil.addSelectionParameter(hyperlinkRef,
                                      rvs.getViewsheetSandbox().getSelections());
         Enumeration<?> keys = hyperlinkRef.getParameterNames();

         while(keys.hasMoreElements()) {
            String pname = (String) keys.nextElement();

            // dont add parameters that already have values set
            if(parameters.containsKey(pname)) {
               continue;
            }

            Object paramValue = hyperlinkRef.getParameter(pname);
            String[] values = new String[0];

            if(Tool.getDataType(paramValue).equals(Tool.ARRAY)) {
               if(((Object[]) paramValue).length > 0) {
                  paramValue = ((Object[]) paramValue)[0].toString().split("\\^");
                  Object[] params =
                     (Object[]) Tool.getData(Tool.ARRAY, Tool.getDataString(paramValue));

                  if(params != null && params.length > 0) {
                     values = Arrays.stream(params)
                        .map((param) -> Tool.getData(Tool.getDataType(param),
                                                     Tool.getDataString(param)).toString())
                        .toArray(String[]::new);
                  }
               }
            }
            else {
               values = new String[] { Tool.getDataString(paramValue) };
            }

            parameters.put(pname, values);
         }
      }

      return parameters;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void openReturnedViewsheet(@ClusterProxyKey String rid, Principal principal, String linkUri,
                                      OpenViewsheetEvent event, CommandDispatcher dispatcher)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(rid, principal);

      if(rvs == null) {
         coreLifecycleService.sendMessage(
            "Viewsheet " + rid + " was expired", MessageCommand.Type.INFO, dispatcher);
         return null;
      }

      rvs.setSocketSessionId(dispatcher.getSessionId());
      rvs.setSocketUserName(dispatcher.getUserName());

      dispatcher.sendCommand(null, new SetRuntimeIdCommand(rid));
      coreLifecycleService.setExportType(rvs, dispatcher);
      coreLifecycleService.setPermission(rvs, principal, dispatcher);
      coreLifecycleService.setComposedDashboard(rvs, dispatcher);
      vsBookmarkService.processBookmark(rid, rvs, linkUri, principal, event.getBookmarkName(),
                                        IdentityID.getIdentityIDFromKey(event.getBookmarkUser()),
                                        event, dispatcher);
      ChangedAssemblyList clist = coreLifecycleService.createList(
         true, event, dispatcher, rvs, linkUri);

      // optimization, call resetRuntime() explicitly instead of passing true to
      // refreshViewsheet's resetRuntime (last parameter). otherwise the touch timestamp
      // would be updated causing cached tablelens to be invalidated after binding change
      rvs.resetRuntime();

      coreLifecycleService.refreshViewsheet(
         rvs, rid, linkUri, event.getWidth(), event.getHeight(), event.isMobile(),
         event.getUserAgent(), dispatcher, true, false, false, clist,
         event.isManualRefresh(), false);
      String url = rvs.getPreviousURL();

      if(url != null) {
         SetPreviousUrlCommand command = new SetPreviousUrlCommand();
         command.setPreviousUrl(url);
         dispatcher.sendCommand(command);
      }

      return null;
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final VSBookmarkService vsBookmarkService;
}
