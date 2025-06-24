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
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.binding.command.SetGrayedOutFieldsCommand;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.embed.EmbedAssemblyInfo;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class CoreLifecycleControllerService {

   public CoreLifecycleControllerService(ViewsheetService viewsheetService,
                                         AssetRepository assetRepository,
                                         DataRefModelFactoryService dataRefModelFactoryService,
                                         VSCompositionService vsCompositionService,
                                         CoreLifecycleService coreLifecycleService,
                                         VSBookmarkService vsBookmarkService,
                                         RuntimeViewsheetRef runtimeViewsheetRef)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.assetRepository = assetRepository;
      this.dataRefModelFactoryService = dataRefModelFactoryService;
      this.vsCompositionService = vsCompositionService;
      this.vsBookmarkService = vsBookmarkService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public ProcessSheetResult handleOpenedSheet(@ClusterProxyKey String id, String eid, String execSessionId, String vsID,
                                  String bookmarkIndex, String drillFrom, AssetEntry entry, boolean viewer, String uri, VariableTable variables,
                                  OpenViewsheetEvent event, CommandDispatcher dispatcher, Principal user) throws Exception
   {
      RuntimeViewsheet rvs2 = null;
      RuntimeViewsheet rvs = null;

      try {
         rvs = viewsheetService.getViewsheet(id, user);
      }
      catch(Exception e) {
         LoggerFactory.getLogger(getClass()).warn("Missing viewsheet {}", id, new Exception("Stack trace"));
         id = viewsheetService.openViewsheet(entry, user, viewer);
         rvs = viewsheetService.getViewsheet(id, user);
      }

      //manually add to header inside proxy
      final MessageAttributes messageAttributes = MessageContextHolder.getMessageAttributes();
      final StompHeaderAccessor headerAccessor = messageAttributes.getHeaderAccessor();

      if(headerAccessor.getNativeHeader("sheetRuntimeId") == null) {
         headerAccessor.setNativeHeader("sheetRuntimeId", id);
      }

      if(runtimeViewsheetRef != null) {
         runtimeViewsheetRef.setRuntimeId(id);
      }

      if(vsID != null) {
         rvs2 = viewsheetService.getViewsheet(vsID, user);
      }

      ChangedAssemblyList clist = coreLifecycleService.createList(true, event, dispatcher, null, uri);

      rvs.getEntry().setProperty("bookmarkIndex", bookmarkIndex);
      // optimization, this shouldn't be needed for new vs since the
      // RuntimeViewsheet cstr calls updateVSBookmark through setEntry
      rvs.updateVSBookmark();
      // @by davyc, if full screen viewsheet, keep its variables
      // fix bug1366833082660
      VariableTable temp = rvs.getViewsheetSandbox().getVariableTable();

      if(temp != null) {
         temp.addAll(variables);
      }

      variables = temp;

      VSEventUtil.syncEmbeddedTableVSAssembly(rvs.getViewsheet());
      rvs.setEmbeddedID(eid);
      rvs.setExecSessionID(execSessionId);

      // if opened for editing from a viewer vs, copy the current viewer vs so
      // editing starts from the same state as the viewer
      if(rvs2 != null) {
         Viewsheet sheet = rvs2.restoreViewsheet();
         //if opened for editing from viewer vs, clear layout position and size
         sheet.clearLayoutState();
         rvs.setViewsheet(sheet);
      }

      // @by changhongyang 2017-10-12, when opening the autosaved copy, set the save point so that
      // the viewsheet is indicated to be modified
      if(event.isOpenAutoSaved() || event.isConfirmed()) {
         rvs.setSavePoint(-1);
      }

      ChangedAssemblyList.ReadyListener rlistener = clist.getReadyListener();
      rvs.setSocketSessionId(dispatcher.getSessionId());
      rvs.setSocketUserName(dispatcher.getUserName());
      dispatcher.sendCommand(null, new SetRuntimeIdCommand(id, coreLifecycleService.getPermissions(rvs, user)));
      coreLifecycleService.setExportType(rvs, dispatcher);
      coreLifecycleService.setComposedDashboard(rvs, dispatcher);

      // embed web component
      if(event.getEmbedAssemblyName() != null) {
         Viewsheet vs = rvs.getViewsheet();

         if(vs != null && !vs.containsAssembly(event.getEmbedAssemblyName())) {
            throw new RuntimeException("Assembly does not exist: " + event.getEmbedAssemblyName());
         }

         EmbedAssemblyInfo embedAssemblyInfo = new EmbedAssemblyInfo();
         embedAssemblyInfo.setAssemblyName(event.getEmbedAssemblyName());
         embedAssemblyInfo.setAssemblySize(event.getEmbedAssemblySize());
         rvs.setEmbedAssemblyInfo(embedAssemblyInfo);
      }

      if(rlistener != null) {
         rlistener.setRuntimeSheet(rvs);
         rlistener.setID(id);
      }

      Set<String> scopied = new HashSet<>();
      ViewsheetSandbox vbox = rvs.getViewsheetSandbox();
      AssetQuerySandbox box = vbox.getAssetQuerySandbox();
      executeVariablesQuery(rvs, vbox);

      // drilldown vs inherit selections from source
      if(drillFrom != null) {
         RuntimeViewsheet ovs = viewsheetService.getViewsheet(drillFrom, user);

         if(ovs != null) {
            ViewsheetSandbox ovbox = ovs.getViewsheetSandbox();
            VSEventUtil.copySelections(ovs.getViewsheet(), rvs.getViewsheet(), scopied);

            if(!scopied.isEmpty()) {
               // in case calendar changed from single to double
               rvs.getViewsheet().layout();
            }

            if(box != null) {
               vbox.setPViewsheet(ovbox.getScope());
            }
         }

         if(box != null) {
            // replace all drilldown variables so they don't accumulate
            box.getVariableTable().clear();
         }
      }

      if(variables != null && box != null) {
         Enumeration iter = variables.keys();

         while(iter.hasMoreElements()) {
            String key = (String) iter.nextElement();
            Object val = variables.get(key);
            box.getVariableTable().put(key, val);
         }

         vbox.resetRuntime();
      }

      List<String> ids = null;

      if(eid != null) {
         RuntimeViewsheet prvs = viewsheetService.getViewsheet(eid, user);

         if(prvs != null) {
            Viewsheet parent = prvs.getViewsheet();
            parent.addChildId(id);

            ids = parent.getChildrenIds();

            // use parent viewsheet to refresh viewsheet
            RuntimeViewsheet crvs = viewsheetService.getViewsheet(id, user);
            Viewsheet embed = VSEventUtil.getViewsheet(parent,
                                                       crvs.getEntry());
            updateViewsheet(embed, crvs, dispatcher);
         }
      }

      try {
         coreLifecycleService.refreshViewsheet(rvs, id, uri, event.getWidth(), event.getHeight(),
                          event.isMobile(),
                          event.getUserAgent(), event.isDisableParameterSheet(), dispatcher,
                          true, false, false, clist, scopied, variables, event.isManualRefresh(),
                          false, true);
      }
      catch(ConfirmException e) {
         if(!coreLifecycleService.waitForMV(e, rvs, dispatcher)) {
            throw e;
         }
      }

      if(ids != null) {
         SetVSEmbedCommand command = new SetVSEmbedCommand();
         command.setIds(ids);
         dispatcher.sendCommand(command);
      }

      boolean auditFinish = true;

      if(id != null) {
         auditFinish = processSheet(rvs, event, uri, dispatcher, rvs.getAssetRepository(), user);
      }

      return new ProcessSheetResult(id, auditFinish, coreLifecycleService.getPermissions(rvs, user));
   }

   private boolean processSheet(RuntimeViewsheet rvs, OpenViewsheetEvent event, String linkUri,
                                CommandDispatcher dispatcher, AssetRepository assetRepository, Principal principal) throws Exception {
      boolean auditFinish = true;
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
               rvs.getID(), rvs, linkUri, principal, event.getBookmarkName(),
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

   private void executeVariablesQuery(RuntimeViewsheet rvs, ViewsheetSandbox vbox)
      throws Exception
   {
      Viewsheet vs = vbox.getViewsheet();
      List<DataVSAssembly> dataObjs = getDataVSAssemblies(vs);

      for(DataVSAssembly dassembly : dataObjs) {
         String tName = dassembly.getTableName();
         UserVariable[] vars = dassembly.getAllVariables();

         if(tName == null) {
            continue;
         }

         RuntimeWorksheet rws = dassembly.isEmbedded() ?
            VSUtil.getRuntimeWorksheet(dassembly.getViewsheet(), vbox) :
            rvs.getRuntimeWorksheet();

         if(rws == null) {
            continue;
         }

         for(UserVariable uuvar : vars) {
            if(uuvar.getChoiceQuery() == null) {
               continue;
            }

            String cname = uuvar.getChoiceQuery();
            String[] pair = Tool.split(uuvar.getChoiceQuery(), "]:[", false);

            if(pair != null && pair.length > 1) {
               tName = pair[0];
               cname = pair[pair.length - 1];
            }

            ColumnRef dataRef;

            // for calc table, map cell name to column name
            if(dassembly instanceof CalcTableVSAssembly) {
               dataRef = new ColumnRef(new AttributeRef(
                  VSUtil.getCalcTableColumnNameFromCellName(cname, (CalcTableVSAssembly) dassembly)));
            }
            else {
               dataRef = new ColumnRef(new AttributeRef(cname));
            }

            BrowseDataController browseDataCtrl = new BrowseDataController();
            browseDataCtrl.setColumn(dataRef);
            browseDataCtrl.setName(tName);

            final BrowseDataModel data = browseDataCtrl.process(rws.getAssetQuerySandbox());
            uuvar.setValues(data.values());
            uuvar.setChoices(data.values());
            uuvar.setDataTruncated(data.dataTruncated());
            uuvar.setExecuted(true);
         }
      }
   }

   private void updateViewsheet(Viewsheet source, RuntimeViewsheet rtarget,
                                CommandDispatcher dispatcher) throws Exception
   {
      Viewsheet target = rtarget.getViewsheet();

      if(target == null) {
         return;
      }

      Assembly[] assemblies = target.getAssemblies();
      // sort assemblies, first copy container, then copy child, so
      // the container's selection and child visible will be correct
      // fix bug1257130820064
      Arrays.sort(assemblies, new Comparator<Assembly>() {
         @Override
         public int compare(Assembly obj1, Assembly obj2) {
            if(!(obj1 instanceof VSAssembly) || !(obj2 instanceof VSAssembly)) {
               return 0;
            }

            VSAssembly ass1 = (VSAssembly) obj1;
            VSAssembly ass2 = (VSAssembly) obj2;

            if(isParent(ass1, ass2)) {
               return 1;
            }
            else if(isParent(ass2, ass1)) {
               return -1;
            }

            return 0;
         }

         private boolean isParent(VSAssembly child, VSAssembly parent) {
            VSAssembly p = child.getContainer();

            while(p != null) {
               if(p == parent) {
                  return true;
               }

               p = p.getContainer();
            }

            return false;
         }
      });

      for(Assembly assemblyObj : assemblies) {
         VSAssembly assembly = (VSAssembly) assemblyObj;
         String name = assembly.getName();
         VSAssembly nassembly = source == null ? null :
            (VSAssembly) source.getAssembly(name);

         target.removeAssembly(assembly.getAbsoluteName(), false, true);

         if(nassembly != null) {
            VSAssembly cassembly = (VSAssembly) nassembly.clone();

            // @by cehnw, fix bug1258511736132.
            // Embedd viewsheet's assemblies visible property is runtime's.
            // When the embedd viewsheet is drilled, the opened viewsheet's
            // component is cloned from the parent, it could be wrong.
            // I fixed visible property according to the original viewsheet.
            VSAssemblyInfo info = assembly.getVSAssemblyInfo();

            if((info.getVisibleValue().equals("" + VSAssembly.ALWAYS_SHOW) ||
               "show".equals(info.getVisibleValue())) && info.isVisible()) {
               cassembly.getVSAssemblyInfo().setVisible(true);
            }

            target.addAssembly(cassembly, false, false);

            if(!assembly.getVSAssemblyInfo().equals(
               cassembly.getVSAssemblyInfo())) {
               coreLifecycleService.refreshVSAssembly(rtarget, cassembly, dispatcher);
            }
         }
      }
   }

   private List<DataVSAssembly> getDataVSAssemblies(Viewsheet vs) {
      List<DataVSAssembly> dataObjs = new ArrayList<>();

      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof Viewsheet) {
            dataObjs.addAll(getDataVSAssemblies((Viewsheet) assembly));
         }

         if(assembly instanceof DataVSAssembly) {
            dataObjs.add((DataVSAssembly) assembly);
         }
      }

      return dataObjs;
   }

   public static final class ProcessSheetResult implements Serializable {
      private String id;
      private boolean auditFinish;
      private Set<String> dispatchPermissions;

      public ProcessSheetResult(String id, boolean auditFinish, Set<String> dispatchPermissions) {
         this.id = id;
         this.auditFinish = auditFinish;
         this.dispatchPermissions = dispatchPermissions;
      }

      public ProcessSheetResult(String id, boolean auditFinish) {
         this(id, auditFinish, null);
      }

      public String getId() {
         return id;
      }

      public void setId(String id) {
         this.id = id;
      }

      public boolean getAuditFinish() {
         return auditFinish;
      }

      public void setAuditFinish(boolean auditFinish) {
         this.auditFinish = auditFinish;
      }

      public Set<String> getDispatchPermissions() {
         return dispatchPermissions;
      }

      public void setDispatchPermissions(Set<String> dispatchPermissions) {
         this.dispatchPermissions = dispatchPermissions;
      }
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final AssetRepository assetRepository;
   private final DataRefModelFactoryService dataRefModelFactoryService;
   private final VSCompositionService vsCompositionService;
   private final VSBookmarkService vsBookmarkService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
}
