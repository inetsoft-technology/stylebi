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
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.binding.command.SetGrayedOutFieldsCommand;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

@Service
@ClusterProxy
public class VSLifecycleControllerService {

   public VSLifecycleControllerService(ViewsheetService viewsheetService,
                                       AssetRepository assetRepository,
                                       CoreLifecycleService coreLifecycleService,
                                       VSBookmarkService vsBookmarkService,
                                       DataRefModelFactoryService dataRefModelFactoryService,
                                       VSCompositionService vsCompositionService) {
      this.viewsheetService = viewsheetService;
      this.assetRepository = assetRepository;
      this.coreLifecycleService = coreLifecycleService;
      this.vsBookmarkService = vsBookmarkService;
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.vsCompositionService = vsCompositionService;

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
   public Boolean processSheet(@ClusterProxyKey String runtimeId, OpenViewsheetEvent event,
                            String linkUri, boolean auditFinish, CommandDispatcher dispatcher, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      coreLifecycleService.setExportType(rvs, dispatcher);
      coreLifecycleService.setPermission(rvs, principal, dispatcher);

      if(event.getBookmarkName() != null && event.getBookmarkUser() != null) {
         IdentityID bookmarkUser = IdentityID.getIdentityIDFromKey(event.getBookmarkUser());
         VSBookmarkInfo openedBookmark = rvs.getOpenedBookmark();

         // Bug #66887, only open bookmark if it's different from the currently opened bookmark
         // prevents the default bookmark from loading twice
         if(openedBookmark == null ||
            !(Tool.equals(openedBookmark.getName(), event.getBookmarkName()) &&
               Tool.equals(openedBookmark.getOwner(), bookmarkUser)))
         {
            vsBookmarkService.processBookmark(
               runtimeId, rvs, linkUri, principal, event.getBookmarkName(),
               bookmarkUser, event, dispatcher);
         }
      }

      if(rvs != null) {
         auditFinish = shouldAuditFinish(rvs.getViewsheetSandbox());

         if(event.getPreviousUrl() != null) {
            rvs.setPreviousURL(event.getPreviousUrl());
         }
         // drill from exist? it is the previous viewsheet
         else if(event.getDrillFrom() != null) {
            RuntimeViewsheet drvs =
               viewsheetService.getViewsheet(event.getDrillFrom(), principal);
            AssetEntry dentry = drvs.getEntry();
            String didentifier = dentry.toIdentifier();
            String purl = linkUri + "app/viewer/view/" + didentifier +
               "?rvid=" + event.getDrillFrom();
            rvs.setPreviousURL(purl);
         }

         String url = rvs.getPreviousURL();

         if(url != null) {
            SetPreviousUrlCommand command = new SetPreviousUrlCommand();
            command.setPreviousUrl(url);
            dispatcher.sendCommand(command);
         }

         VSModelTrapContext context = new VSModelTrapContext(rvs, true);

         if(context.isCheckTrap()) {
            context.checkTrap(null, null);
            DataRef[] refs = context.getGrayedFields();

            if(refs.length > 0) {
               DataRefModel[] refsModel = new DataRefModel[refs.length];

               for(int i = 0; i < refs.length; i++) {
                  refsModel[i] = dataRefModelFactoryService.createDataRefModel(refs[i]);
               }

               SetGrayedOutFieldsCommand command = new SetGrayedOutFieldsCommand(refsModel);
               dispatcher.sendCommand(command);
            }
         }

         Viewsheet vs = rvs.getViewsheet();
         Assembly[] assemblies = vs.getAssemblies();

         // fix bug1309250160380, fix AggregateInfo for CrosstabVSAssembly
         for(Assembly assembly : assemblies) {
            if(assembly instanceof CrosstabVSAssembly) {
               VSEventUtil.fixAggregateInfo(
                  (CrosstabVSAssembly) assembly, rvs, assetRepository, principal);
            }
         }

         // fix z-index. flash may use a different z-index structure so we should eliminate
         // duplicate values (which may happen for group containers).
         vsCompositionService.shrinkZIndex(vs, dispatcher);
      }

      return auditFinish;
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

   private boolean shouldAuditFinish(ViewsheetSandbox viewsheetSandbox) {
      try {
         Viewsheet vs = viewsheetSandbox.getViewsheet();
         ViewsheetInfo vsInfo = vs == null ? null : vs.getViewsheetInfo();

         if(vsInfo != null && vsInfo.isDisableParameterSheet()) {
            return true;
         }

         return shouldAuditFinish0(viewsheetSandbox);
      }
      catch(Exception e) {
         // In case there are any issues/errors in checking the Variables for
         // this Viewsheet, just swallow the exception and continue on with the
         // previous logic. There is no reason to display this error to the end-user.
      }

      return true;
   }

   private boolean shouldAuditFinish0(ViewsheetSandbox viewsheetSandbox) {
      VariableTable vars = new VariableTable();
      AssetQuerySandbox abox = viewsheetSandbox.getAssetQuerySandbox();
      UserVariable[] params = abox.getAllVariables(vars);

      if(params != null && params.length > 0) {
         return false;
      }

      ViewsheetSandbox[] sandboxes = viewsheetSandbox.getSandboxes();

      if(sandboxes != null) {
         for(ViewsheetSandbox sandbox : sandboxes) {
            if(viewsheetSandbox == sandbox) {
               continue;
            }

            if(!shouldAuditFinish0(sandbox)) {
               return false;
            }
         }
      }

      return true;
   }

   private final ViewsheetService viewsheetService;
   private final AssetRepository assetRepository;
   private final CoreLifecycleService coreLifecycleService;
   private final VSBookmarkService vsBookmarkService;
   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final VSCompositionService vsCompositionService;}
