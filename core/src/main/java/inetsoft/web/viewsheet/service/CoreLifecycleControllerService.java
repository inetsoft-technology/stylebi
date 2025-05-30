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
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.embed.EmbedAssemblyInfo;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.List;

@Service
@ClusterProxy
public class CoreLifecycleControllerService {

   public CoreLifecycleControllerService(ViewsheetService viewsheetService,
                                         CoreLifecycleService coreLifecycleService)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public String handleOpenedSheet(@ClusterProxyKey String id, String eid, String execSessionId, String vsID,
                                 AssetEntry entry, boolean viewer, String bookmarkIndex, String drillFrom, String uri, VariableTable variables,
                                 OpenViewsheetEvent event, CommandDispatcher dispatcher, Principal user) throws Exception
   {
      ChangedAssemblyList clist = coreLifecycleService.createList(true, event, dispatcher, null, uri);
      RuntimeViewsheet rvs2 = null;
      RuntimeViewsheet rvs = null;

      try {
         rvs = viewsheetService.getViewsheet(id, user);
      }
      catch(Exception e) {
         //if getViewsheet fails, return null to open and call proxy with correct id
         return null;
      }

      if(vsID != null) {
         rvs2 = viewsheetService.getViewsheet(vsID, user);
      }

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

      return id;
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

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
}
