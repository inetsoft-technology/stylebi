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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.VSCSSUtil;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.CheckMissingMVEvent;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.script.viewsheet.ScriptEvent;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.ViewsheetLayout;
import inetsoft.util.*;
import inetsoft.util.script.ScriptException;
import inetsoft.web.composer.BrowseDataController;
import inetsoft.web.composer.model.BrowseDataModel;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.embed.EmbedAssemblyInfo;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.controller.table.BaseTableController;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.Point2D;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility methods copied from VSEventUtil. The contents of this class should be moved to
 * appropriate controller methods and this class removed.
 *
 * @since 12.3
 */
@Service
public class CoreLifecycleService {
   @Autowired
   public CoreLifecycleService(
      VSObjectModelFactoryService objectModelService, ViewsheetService viewsheetService,
      VSLayoutService vsLayoutService, ParameterService parameterService)
   {
      this.objectModelService = objectModelService;
      this.viewsheetService = viewsheetService;
      this.vsLayoutService = vsLayoutService;
      this.parameterService = parameterService;
   }

   public String openViewsheet(ViewsheetService engine, OpenViewsheetEvent event,
                               Principal user, String uri, String eid, AssetEntry entry,
                               CommandDispatcher dispatcher,
                               RuntimeViewsheetRef runtimeViewsheetRef,
                               RuntimeViewsheetManager runtimeViewsheetManager,
                               boolean viewer, String drillFrom, VariableTable variables,
                               String fullScreenId, String execSessionId)
      throws Exception
   {
      ChangedAssemblyList clist = createList(true, event, dispatcher, null, uri);
      String vsID = entry.getProperty("vsID");
      String id = Tool.byteDecode(fullScreenId);
      String bookmarkIndex =
         Tool.byteDecode(entry.getProperty("bookmarkIndex"));
      entry.setProperty("drillfrom", drillFrom);
      entry.setProperty("vsID", null);
      entry.setProperty("bookmarkIndex", null);
      RuntimeViewsheet rvs;
      RuntimeViewsheet rvs2 = null;

      if(id != null && id.length() > 0) {
         rvs = engine.getViewsheet(id, user);
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
      }
      else {
         if(vsID != null) {
            rvs2 = engine.getViewsheet(vsID, user);
         }

         id = engine.openViewsheet(entry, user, viewer);
         rvs = engine.getViewsheet(id, user);
      }

      if(runtimeViewsheetRef != null) {
         runtimeViewsheetRef.setRuntimeId(id);
      }

      if(runtimeViewsheetManager != null) {
         runtimeViewsheetManager.sheetOpened(id);
      }

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
      dispatcher.sendCommand(null, new SetRuntimeIdCommand(id, getPermissions(rvs, user)));
      setExportType(rvs, dispatcher);
      setComposedDashboard(rvs, dispatcher);

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
         RuntimeViewsheet ovs = engine.getViewsheet(drillFrom, user);

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
         RuntimeViewsheet prvs = engine.getViewsheet(eid, user);

         if(prvs != null) {
            Viewsheet parent = prvs.getViewsheet();
            parent.addChildId(id);

            ids = parent.getChildrenIds();

            // use parent viewsheet to refresh viewsheet
            RuntimeViewsheet crvs = engine.getViewsheet(id, user);
            Viewsheet embed = VSEventUtil.getViewsheet(parent,
                                                       crvs.getEntry());
            updateViewsheet(embed, crvs, dispatcher);
         }
      }

      try {
         refreshViewsheet(rvs, id, uri, event.getWidth(), event.getHeight(),
            event.isMobile(),
            event.getUserAgent(), event.isDisableParameterSheet(), dispatcher,
            true, false, false, clist, scopied, variables, event.isManualRefresh(),
            false, true);
      }
      catch(ConfirmException e) {
         if(!waitForMV(e, rvs, dispatcher)) {
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

   public boolean waitForMV(ConfirmException e, RuntimeViewsheet rvs,
                            CommandDispatcher commandDispatcher)
   {
      if(e.getLevel() != ConfirmException.PROGRESS || !(e.getEvent() instanceof CheckMissingMVEvent)) {
         return false;
      }

      boolean checkMVHandled = commandDispatcher.stream()
         .filter(c -> c.getCommand() instanceof MessageCommand)
         .map(c -> (MessageCommand) c.getCommand())
         .flatMap(c -> c.getEvents().values().stream())
         .anyMatch(evt -> evt instanceof inetsoft.web.viewsheet.event.CheckMVEvent);

      if(!checkMVHandled) {
         CheckMissingMVEvent event = (CheckMissingMVEvent) e.getEvent();
         AssetEntry entry = event.getEntry();

         MessageCommand cmd = new MessageCommand();
         cmd.setMessage(e.getMessage());
         cmd.setType(MessageCommand.Type.PROGRESS);

         inetsoft.web.viewsheet.event.CheckMVEvent checkEvent =
            new inetsoft.web.viewsheet.event.CheckMVEvent();
         checkEvent.setEntryId(entry.toIdentifier());
         checkEvent.setWaitFor(false);
         checkEvent.setBackground(event.isBackground());
         checkEvent.setConfirmed(false);
         checkEvent.setRefreshDirectly(event.isRefreshDirectly());

         cmd.addEvent("/events/composer/viewsheet/checkmv", checkEvent);
         commandDispatcher.sendCommand(cmd);

         if(rvs != null) {
            rvs.addCheckpoint(rvs.getSheet().prepareCheckpoint());
            UpdateUndoStateCommand pointCommand = new UpdateUndoStateCommand();
            pointCommand.setPoints(rvs.size());
            pointCommand.setCurrent(rvs.getCurrent());
            pointCommand.setSavePoint(rvs.getSavePoint());
            commandDispatcher.sendCommand(pointCommand);
         }
      }

      return true;
   }

   public ChangedAssemblyList createList(boolean breakable,
                                                OpenViewsheetEvent event,
                                                CommandDispatcher dispatcher,
                                                RuntimeViewsheet rvs, String uri)
   {
      ChangedAssemblyList clist = new ChangedAssemblyList(breakable);
      clist.setReadyListener(new ReadyListener(
         clist, event.getWidth(), event.getHeight(), dispatcher.detach(), rvs, uri));
      return clist;
   }

   public ChangedAssemblyList createList(boolean breakable,
                                                CommandDispatcher dispatcher,
                                                RuntimeViewsheet rvs, String uri)
   {
      ChangedAssemblyList clist = new ChangedAssemblyList(breakable);
      clist.setReadyListener(new ReadyListener(clist, dispatcher.detach(), rvs, uri));
      return clist;
   }

   public void setViewsheetInfo(RuntimeViewsheet rvs, String linkUri,
                                CommandDispatcher dispatcher)
   {
      SetViewsheetInfoCommand command = new SetViewsheetInfoCommand();

      if(rvs.getViewsheet() != null) {
         Map<String, Object> info = new HashMap<>();
         info.put("name", rvs.getEntry().toView());

         Viewsheet vs = rvs.getViewsheet();
         ViewsheetInfo vsInfo = vs.getViewsheetInfo();
         // TODO populate info with values from vs.getViewsheetInfo() when it is
         // determined what is needed
         HashMap<String, Object> infoMap = new HashMap<>();
         infoMap.put("updateEnabled", vsInfo.isUpdateEnabled());
         infoMap.put("touchInterval", vsInfo.getTouchInterval());
         infoMap.put("scaleToScreen", vsInfo.isScaleToScreen());
         infoMap.put("fitToWidth", vsInfo.isFitToWidth());
         infoMap.put("balancePadding", vsInfo.isBalancePadding());
         infoMap.put("isMetadata", vsInfo.isMetadata());
         infoMap.put("snapGrid", vsInfo.getSnapGrid());

         TableDataPath dataPath = new TableDataPath(-1, TableDataPath.OBJECT);
         VSCompositeFormat format = vs.getFormatInfo().getFormat(dataPath);
         String color = VSCSSUtil.getBackgroundRGBA(format);

         if(!color.isEmpty()) {
            infoMap.put("viewsheetBackground", color);
         }
         else {
            infoMap.put("viewsheetBackground", Tool.getVSCSSBgColorHexString());
         }

         LicenseManager licenseManager = LicenseManager.getInstance();

         if(licenseManager.isElasticLicense() && licenseManager.getElasticRemainingHours() == 0) {
            infoMap.put("hasWatermark", true);
         }
         else if(licenseManager.isHostedLicense()) {
            Principal user = ThreadContext.getContextPrincipal();

            if(user instanceof SRPrincipal principal) {
               String orgId = principal.getOrgId();
               String username = principal.getName();

               if(licenseManager.getHostedRemainingHours(orgId, username) == 0) {
                  infoMap.put("hasWatermark", true);
               }
            }
         }

         infoMap.put("statusText", rvs.getEntry().getDescription() + " ");
         infoMap.put("lastModifiedTime", vs.getLastModified());
         infoMap.put("dateFormat", Tool.getDateFormatPattern());
         infoMap.put("templateHeight", vsInfo.getTemplateHeight());
         infoMap.put("templateEnabled", vsInfo.isTemplateEnabled());
         infoMap.put("metadata", vsInfo.isMetadata());

         boolean accessible =
            Boolean.parseBoolean((SreeEnv.getProperty(" accessibility.enabled", "false")));

         infoMap.put("accessible", accessible);
         infoMap.put("messageLevels", vsInfo.getMessageLevels());
         infoMap.put("virtualScroll", "true".equals(SreeEnv.getProperty("viewsheet.virtual.scroll")));

         command.setInfo(infoMap);
         // TODO populate assemblyInfo with values from vs.getViewsheetInfo()
         // when it is determined what is needed
         command.setAssemblyInfo(info);
         command.setBaseEntry(vs.getBaseEntry());

         if(!UserEnv.supportedUser(rvs.getUser())) {
            command.setAnnotation(rvs.getViewsheet().getAnnotationsVisible());
         }
         else {
            command.setAnnotation(
               "true".equals(UserEnv.getProperty(rvs.getUser(), "annotation", "true")));
         }

         command.setAnnotated(AnnotationVSUtil.isAnnotated(vs));
         command.setFormTable(FormUtil.containsForm(vs, true));
         command.setHasScript(vsInfo.isScriptEnabled() &&
                                 (!StringUtils.isEmpty(vsInfo.getOnInit()) ||
                                    !StringUtils.isEmpty(vsInfo.getOnLoad())));

         List<String> layouts = vs.getLayoutInfo()
            .getViewsheetLayouts()
            .stream()
            .map(ViewsheetLayout::getName)
            .collect(Collectors.toList());
         layouts.add(0, Catalog.getCatalog().getString("Master"));

         if(vs.getLayoutInfo().getPrintLayout() != null) {
            layouts.add(Catalog.getCatalog().getString("Print Layout"));
         }

         command.setLayouts(layouts);
         command.setLinkUri(linkUri);
      }

      dispatcher.sendCommand(command);
   }

   public void sendMessage(String message, MessageCommand.Type type,
                           CommandDispatcher dispatcher)
   {
      MessageCommand command = new MessageCommand();
      command.setMessage(message);
      command.setType(type);
      dispatcher.sendCommand(command);
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
               refreshVSAssembly(rtarget, cassembly, dispatcher);
            }
         }
      }
   }

   public boolean layoutViewsheet(RuntimeViewsheet rvs, String id, String uri,
                                  CommandDispatcher dispatcher) throws Exception
   {
      return layoutViewsheet(
         rvs, id, uri, dispatcher, new String[0], new ChangedAssemblyList());
   }

   public boolean layoutViewsheet(RuntimeViewsheet rvs, String id, String uri,
                                         CommandDispatcher dispatcher, String name,
                                         ChangedAssemblyList clist) throws Exception
   {
      String[] names = name == null ? new String[0] : new String[] {name};
      return layoutViewsheet(rvs, id, uri, dispatcher, names, clist);
   }

   public boolean layoutViewsheet(RuntimeViewsheet rvs, String id, String uri,
                                  CommandDispatcher dispatcher, String[] names,
                                  ChangedAssemblyList clist) throws Exception
   {
      // fix assembly size
      List rlist = VSEventUtil.fixAssemblySize(rvs);
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return false;
      }

      java.awt.Dimension osize = vs.getLastSize();
      Assembly[] assemblies = vs.layout(names);
      java.awt.Dimension nsize = vs.getPixelSize();

      if(nsize.width > osize.width || nsize.height > osize.height) {
         refreshViewsheet(rvs, id, uri, dispatcher, false, false, false, clist);
         return true;
      }

      // resized assembly
      for(Object item : rlist) {
         VSAssembly assembly = (VSAssembly) item;
         refreshVSAssembly(rvs, assembly, dispatcher);
      }

      // manually moved assembly
      List<String> list = Arrays.asList(names);

      for(String name : list) {
         VSAssembly assembly = (VSAssembly) vs.getAssembly(name);

         if(!rlist.contains(assembly)) {
            refreshVSAssembly(rvs, assembly, dispatcher);
         }
      }

      // automatically moved assembly
      for(Assembly assembly : assemblies) {
         if(rlist.contains(assembly)) {
            continue;
         }

         String name = assembly.getName();

         if(!list.contains(name)) {
            refreshVSAssembly(rvs, (VSAssembly) assembly, dispatcher);
         }
      }

      return false;
   }

   public void refreshViewsheet(RuntimeViewsheet rvs, String id, String uri,
                                CommandDispatcher dispatcher, boolean initing,
                                boolean component, boolean reset,
                                ChangedAssemblyList clist) throws Exception
   {
      refreshViewsheet(
         rvs, id, uri, 0, 0, false, null, dispatcher, initing, component, reset, clist);
   }

   public void refreshViewsheet(RuntimeViewsheet rvs, String id, String uri,
                                CommandDispatcher dispatcher, boolean initing,
                                boolean component, boolean reset,
                                ChangedAssemblyList clist,
                                boolean resetRuntime) throws Exception
   {
      refreshViewsheet(
         rvs, id, uri, 0, 0, false, null, dispatcher, initing, component, reset,
         clist, false, resetRuntime);
   }

   public void refreshViewsheet(RuntimeViewsheet rvs, String id, String uri,
                                int width, int height, boolean mobile,
                                String userAgent, CommandDispatcher dispatcher,
                                boolean initing, boolean component, boolean reset,
                                ChangedAssemblyList clist) throws Exception
   {
      refreshViewsheet(
         rvs, id, uri, width, height, mobile, userAgent, false, dispatcher, initing,
         component, reset, clist, null, null, false, false);
   }

   public void refreshViewsheet(RuntimeViewsheet rvs, String id, String uri,
      int width, int height, boolean mobile, String userAgent,
      CommandDispatcher dispatcher, boolean initing, boolean component,
      boolean reset, ChangedAssemblyList clist, boolean manualRefresh,
      boolean resetRuntime) throws Exception
   {
      refreshViewsheet(
         rvs, id, uri, width, height, mobile, userAgent, false, dispatcher,
         initing, component, reset, clist, null, null, manualRefresh, resetRuntime);
   }

   public void refreshViewsheet(RuntimeViewsheet rvs, String id, String uri,
      int width, int height, boolean mobile, String userAgent,
      boolean disableParameterSheet, CommandDispatcher dispatcher,
      boolean initing, boolean component, boolean reset,
      ChangedAssemblyList clist, Set<String> copiedSelections, VariableTable initvars,
      boolean manualRefresh, boolean resetRuntime) throws Exception
   {
      refreshViewsheet(
         rvs, id, uri, width, height, mobile, userAgent, false, dispatcher,
         initing, component, reset, clist, copiedSelections, initvars, manualRefresh,
         resetRuntime, false);
   }

   public void refreshViewsheet(RuntimeViewsheet rvs, String id, String uri, int width,
      int height, boolean mobile, String userAgent,
      boolean disableParameterSheet, CommandDispatcher dispatcher,
      boolean initing, boolean component, boolean reset,
      ChangedAssemblyList clist, Set<String> copiedSelections, VariableTable initvars,
      boolean manualRefresh, boolean resetRuntime, boolean isOpenVS) throws Exception
   {
      refreshViewsheet(
         rvs, id, uri, width, height, mobile, userAgent, disableParameterSheet, dispatcher,
         initing, component, reset, clist, copiedSelections, initvars, manualRefresh,
         resetRuntime, isOpenVS, false);
   }

   public void refreshViewsheet(RuntimeViewsheet rvs, String id, String uri, int width,
      int height, boolean mobile, String userAgent,
      boolean disableParameterSheet, CommandDispatcher dispatcher,
      boolean initing, boolean component, boolean reset,
      ChangedAssemblyList clist, Set<String> copiedSelections, VariableTable initvars,
      boolean manualRefresh, boolean resetRuntime,
      boolean isOpenVS, boolean toggleMaxMode) throws Exception
   {
      Viewsheet sheet = rvs.getViewsheet();
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      boolean ignoreRefreshTempAssembly = WizardRecommenderUtil.ignoreRefreshTempAssembly();
      boolean loadTablesInLock = Boolean.TRUE.equals(VSUtil.OPEN_VIEWSHEET.get());

      if(box == null || sheet == null) {
         return;
      }

      boolean inited = false;

      if(!disableParameterSheet) {
         disableParameterSheet = sheet.getViewsheetInfo().isDisableParameterSheet();
      }

      for(CommandDispatcher.Command command : dispatcher) {
         if("InitGridCommand".equals(command.getType())) {
            inited = true;
            break;
         }
      }

      long ts = System.currentTimeMillis();
      ViewsheetInfo vsinfo = sheet.getViewsheetInfo();

      if(vsinfo != null && vsinfo.isScaleToScreen() && rvs.isRuntime() && height != 0 && width != 0)
      {
         // if scaling is to be applied, clear any previous scaling prior to calculating the view
         // size
         VSEventUtil.clearScale(sheet);
      }

      if(!"true".equals(rvs.getProperty("__EXPORTING__"))) {
         sheet.updateCSSFormat(null, null);
      }

      updateAssembliesTittleDefaultFormat(sheet);

      // Include invisible assemblies when calculating the view size because
      // they may be made visible at a later time, e.g. with a script.
      // Assemblies that are not included in the layout have already been
      // explicitly positioned and sized to zero so that they don't affect
      // anything else.
      Dimension viewSize = sheet.getPreferredSize(true, false);
      Assembly[] assemblies = {};
      box.lockWrite();

      try {
         viewsheetService.addExecution(id);
         // reset the runtime values before onLoad/onInit, so we don't
         // clear out values set in onLoad/onInit
         // @by stephenwebster, For Bug #7758, I moved resetRuntimeValues before
         // scale to screen operations so scaled column widths would not get reset.
         VSUtil.resetRuntimeValues(sheet, false);

         // don't scale viewsheet in design mode or if height or width is set to 0
         if(vsinfo != null && vsinfo.isScaleToScreen() && rvs.isRuntime() &&
            (height != 0 || vsinfo.isFitToWidth()) && width != 0 &&
            rvs.getEmbedAssemblyInfo() == null)
         {
            // if not initializing a viewsheet then always apply scale
            boolean applyScale = !initing || vsinfo.isDisableParameterSheet();

            if(!applyScale) {
               // Bug #69536, delay scaling until parameters are submitted and prevent
               // an early execution of the viewsheet.
               List<UserVariable> vars = parameterService.getPromptParameters(sheet, box, initvars);
               applyScale = vars.isEmpty();
            }

            if(applyScale) {
               // applyScale may trigger execution, which in turn may depend on variables
               // set in onInit. since viewsheetsandbox tracks whether onInit needs to be executed
               // again, calling it here should be safe
               box.processOnInit();
               // variables in onLoad should also be accessible by assembly scripts. (56119)
               box.processOnLoadIf();
               Assembly[] allAssemblies = sheet.getAssemblies();

               if(allAssemblies != null) {
                  // execute script, because it maybe set the pop component by script.
                  for(Assembly assembly : allAssemblies) {
                     try {
                        box.executeScript((VSAssembly) assembly);
                     }
                     catch(MessageException ex) {
                        // During script execution, exception may be thrown because of permission control
                        // of chart type, so catch it to make sure that other assemblies display correct.
                        if(!"INVALID_CHART_TYPE".equals(ex.getKeywords())) {
                           throw ex;
                        }
                     }
                  }
               }

               viewSize = sheet.getPreferredSize(true, false);
               Point2D.Double scaleRatio = VSEventUtil.calcScalingRatio(
                  sheet, viewSize, width, height, mobile);
               VSEventUtil.applyScale(sheet, scaleRatio, mobile, userAgent, width, height, box);

               // remember the current bounds
               rvs.setProperty("viewsheet.init.bounds", sheet.getPreferredBounds());
               rvs.setProperty("viewsheet.appliedScale", new Dimension(width, height));
               rvs.setProperty("viewsheet.scaleRatio", new Point2D.Double(scaleRatio.x, scaleRatio.y));
            }
         }

         if(!component && !inited) {
            sheet.setLastSize(sheet.getPixelSize());
            VSEventUtil.setToolbar(sheet);
         }

         try {
            box.setRefreshing(true);

            if(resetRuntime || manualRefresh) {
               rvs.resetRuntime();
               rvs.setTouchTimestamp(System.currentTimeMillis());
               box.setTouchTimestamp(rvs.getTouchTimestamp());
            }

            if(manualRefresh) {
               List<UserVariable> vars = new ArrayList<>();
               VSEventUtil.refreshParameters(viewsheetService, box, sheet, true, initvars, vars);

               if(!vars.isEmpty() && !disableParameterSheet) {
                  setViewsheetInfo(rvs, uri, dispatcher);

                  UserVariable[] vtable = vars.toArray(new UserVariable[0]);
                  parameterService.collectParameters(rvs.getViewsheet(), vtable,
                                                     disableParameterSheet, dispatcher);

                  return;
               }
            }

            if(isOpenVS) {
               refreshInputValues(box);
            }

            // viewsheetsandbox tracks whether onInit needs to be executed
            box.processOnInit();

            if(!component && !inited) {
               // send init after onInit/onLoad so changes in toolbar visibility is picked up
               InitGridCommand command = new InitGridCommand();
               command.setViewsheetId(id);
               command.setEntry(rvs.getEntry());
               long modified = sheet.getLastModified();
               Date date = new Date(modified);
               command.setIniting(initing);
               command.setViewSize(viewSize);
               command.setEditable(rvs.isEditable());
               command.setLockOwner(rvs.getLockOwner());
               command.setEmbeddedId(rvs.getEmbeddedID());
               command.setToolbarVisible(
                  sheet.getVSAssemblyInfo().isActionVisible("Toolbar") &&
                  !Boolean.valueOf(SreeEnv.getProperty("Viewsheet Toolbar Hidden")));

               if(initing) {
                  command.setLastModified(date);
               }

               command.setScope(rvs.getEntry().getScope());
               command.setRuntimeFontScale(sheet.getRScaleFont());
               command.setHasSharedFilters(vsinfo != null && !vsinfo.getFilterIDs().isEmpty());
               dispatcher.sendCommand(command);
            }

            if(initing) {
               // set infos so the isScaleToScreen() is correct when vsobject
               // is refreshed
               setViewsheetInfo(rvs, uri, dispatcher);

               // make sure containers are created before the children
               // so the check for whether a component is in container is correct
               refreshContainer(rvs);

               // make sure embedded viewsheet is created before the children so
               // the position is available in VSObject.getPixelPosition
               refreshEmbeddedViewsheet(rvs, rvs.getViewsheet(), uri, dispatcher,
                                        manualRefresh, manualRefresh && !resetRuntime);
               // @by mikec, collect parameters before reset the sandbox seems
               // more reasonable, otherwise if during reset box the code need
               // parameters to access database, error will be thrown.
               // @see bug1234937851455
               List<UserVariable> vars = parameterService.getPromptParameters(sheet, box, initvars);

               if(!vars.isEmpty() && !disableParameterSheet) {
                  setViewsheetInfo(rvs, uri, dispatcher);
                  UserVariable[] vtable = vars.toArray(new UserVariable[0]);

                  parameterService.collectParameters(rvs.getViewsheet(), vtable,
                                                     disableParameterSheet, dispatcher, isOpenVS);

                  return;
               }

               assemblies = sheet.getAssemblies(false, true);
               Arrays.sort(assemblies, new TabAnnotationComparator());

               // make the assemblies paint before the data is fetched so the
               // user could see the vs without a long wait
               for(Assembly assembly : assemblies) {
                  if(box.isCancelled(ts)) {
                     return;
                  }

                  VSAssembly vsassembly = (VSAssembly) assembly;

                  // child assemblies created by container
                  if(vsassembly.getContainer() != null) {
                     continue;
                  }

                  // bug1390407468587, avoid triggering a query (e.g. chart)
                  // before condition is set (in box.reset later in this method)
                  if(vsassembly instanceof DataVSAssembly ||
                     vsassembly instanceof OutputVSAssembly ||
                     vsassembly instanceof ContainerVSAssembly ||
                     vsassembly instanceof Viewsheet ||
                     !VSEventUtil.isVisibleInTab(vsassembly.getVSAssemblyInfo()))
                  {
                     //bug1395797472256, chart need update for onload script.
                     if(vsassembly instanceof ChartVSAssembly) {
                        try {
                           box.updateAssembly(vsassembly.getAbsoluteName());
                        }
                        catch(ScriptException scriptException) {
                           // ScriptException should be logged at appropriate level when created
                        }
                        catch(Exception ex) {
                           LOG.warn("Failed to update chart assembly during initialization", ex);
                        }
                     }

                     continue;
                  }

                  VSAssemblyInfo info = (VSAssemblyInfo) vsassembly.getInfo();
                  boolean enabled = info.isEnabled();

                  info.setEnabled(false);
                  addDeleteVSObject(rvs, vsassembly, dispatcher);
                  info.setEnabled(enabled);

                  if (info instanceof TextInputVSAssemblyInfo) {
                     if(((TextInputVSAssemblyInfo) info).getValue() == null || "".equals(((TextInputVSAssemblyInfo) info).getValue())) {
                        ((TextInputVSAssemblyInfo) info).setValue(((TextInputVSAssemblyInfo) info).getDefaultText());
                     }
                  }
               }
            }

            Assembly[] sheetAssemblies =
               sheet.getAssemblies(false, false, !ignoreRefreshTempAssembly, false);

            // need process onload every time when refresh the viewsheet, since
            // onload script will effect without delay
            if(box.isCancelled(ts)) {
               return;
            }
            else if(reset) {
               box.reset(null, sheetAssemblies, clist, true, true, null, toggleMaxMode);
               rvs.refreshAllTipViewOrPopComponentTable();
            }
            else if(initing) {
               box.reset(null, sheetAssemblies, clist, true, true, copiedSelections);
               rvs.refreshAllTipViewOrPopComponentTable();
            }

            if(!box.getDelayedVisibilityAssemblies().isEmpty()) {
               for(Map.Entry<Integer, Set<String>> e :
                  box.getDelayedVisibilityAssemblies().entrySet())
               {
                  DelayVisibilityCommand cmd =
                     new DelayVisibilityCommand(e.getKey(), new ArrayList<>(e.getValue()));
                  dispatcher.sendCommand(cmd);
               }
            }

            if(initing) {
               sheet = rvs.getViewsheet();

               if(sheet == null) {
                  return;
               }

               rvs.replaceCheckpoint(sheet.prepareCheckpoint());
               // visibility may be changed in reset
               sheet.layout();
            }
            // @by davyc, InitGridCommand may removed all objects, so here
            // should refresh container to make sure the child get container is
            // correct in client side, see bug1239603135453
            else if(inited) {
               Viewsheet vs = rvs.getViewsheet();

               if(vs == null) {
                  return;
               }

               Assembly[] assemblies0 = vs.getAssemblies(false, true, false, !ignoreRefreshTempAssembly);

               for(Assembly assembly : assemblies0) {
                  if(box.isCancelled(ts)) {
                     return;
                  }

                  VSAssembly vsassembly = (VSAssembly) assembly;

                  if(vsassembly instanceof ContainerVSAssembly &&
                     !(vsassembly instanceof TabVSAssembly) ||
                     vsassembly instanceof SelectionVSAssembly)
                  {
                     refreshVSAssembly(rvs, vsassembly.getAbsoluteName(), dispatcher);
                  }
               }
            }

            Viewsheet vs = rvs.getViewsheet();

            if(vs == null) {
               return;
            }

            assemblies = vs.getAssemblies(false, true, !ignoreRefreshTempAssembly, false);
            Arrays.sort(assemblies, new TabAnnotationComparator());

            for(Assembly assembly : assemblies) {
               if(box.isCancelled(ts)) {
                  return;
               }

               VSAssembly vsassembly = (VSAssembly) assembly;

               // already processed? ignore it
               if(clist.getProcessedList().contains(vsassembly.getAssemblyEntry())) {
                  continue;
               }

               if(vsassembly instanceof Viewsheet) {
                  VSEventUtil.setLinkURI(uri,
                                         ((Viewsheet) vsassembly).getAssemblies(true, false));
               }
               else if(vsassembly instanceof OutputVSAssembly) {
                  VSEventUtil.setLinkURI(uri, vsassembly);
               }

               addDeleteVSObject(rvs, vsassembly, dispatcher);
            }

            // Bug #70029, prevent race conditions between CollectParametersOverEvent and bookmark
            // being processed on viewsheet open. Load tables in lock so that they don't cause
            // issues for each other such as clearing column widths.
            if(loadTablesInLock) {
               initTables(rvs, dispatcher, uri, assemblies);
            }
         }
         finally {
            box.setRefreshing(false);
         }
      }
      finally {
         box.clearDelayedVisibilityAssemblies();
         viewsheetService.removeExecution(id);
         box.unlockWrite();
      }

      // loading table can take a long time, move it out of the locked block
      if(!loadTablesInLock) {
         initTables(rvs, dispatcher, uri, assemblies);
      }

      List errors = (List) AssetRepository.ASSET_ERRORS.get();

      if(errors != null && !errors.isEmpty()) {
         StringBuilder sb = new StringBuilder();
         AssetEntry entry = rvs.getEntry();

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
         sendMessage(msg, MessageCommand.Type.WARNING, dispatcher);
      }

      // set info after script is executed
      setViewsheetInfo(rvs, uri, dispatcher);

      UpdateUndoStateCommand pointCommand = new UpdateUndoStateCommand();
      pointCommand.setPoints(rvs.size());
      pointCommand.setCurrent(rvs.getCurrent());
      pointCommand.setSavePoint(rvs.getSavePoint());
      dispatcher.sendCommand(pointCommand);
   }

   /**
    * Update the assembly tittle default font.
    */
   private void updateAssembliesTittleDefaultFormat(Viewsheet vs) {
      if(vs == null) {
         return;
      }

      Assembly[] allAssemblies = vs.getAssemblies();

      if(allAssemblies == null) {
         return;
      }

      for(Assembly assembly : allAssemblies) {
         if(!(assembly instanceof VSAssembly)) {
            continue;
         }

         VSAssembly vSAssembly = (VSAssembly) assembly;

         if(vSAssembly.getVSAssemblyInfo() instanceof TitledVSAssemblyInfo) {
            vSAssembly.getVSAssemblyInfo().updateTitleDefaultFontSize();
         }
      }
   }

   // if opening a new vs, we should load the value from worksheet if it
   // is bound to a cell in an embedded table. (44162)
   private void refreshInputValues(ViewsheetSandbox box) throws Exception {
      Viewsheet vs = box.getViewsheet();
      Assembly[] assemblies = vs.getAssemblies(false, true);
      long ts = System.currentTimeMillis();

      // make the assemblies paint before the data is fetched so the
      // user could see the vs without a long wait
      for(Assembly assembly : assemblies) {
         if(box.isCancelled(ts)) {
            return;
         }

         VSAssembly vsassembly = (VSAssembly) assembly;

         // child assemblies created by container
         if(vsassembly.getContainer() != null || !(vsassembly instanceof SingleInputVSAssembly)) {
            continue;
         }

         InputVSAssembly input = (InputVSAssembly) vsassembly;

         if(input.getRow() >= 0 && input.getColumn() != null) {
            Object value = new InputVSAQuery(box, assembly.getAbsoluteName()).getData();

            if(value instanceof ListData) {
               Object[] values = ((ListData) value).getValues();
               value = values == null || values.length == 0 ? null : values[0];
            }

            if(value != null) {
               ((SingleInputVSAssembly) vsassembly).setSelectedObject(value);
            }
         }
      }
   }

   private void initTables(RuntimeViewsheet rvs, CommandDispatcher dispatcher,
                           String uri, Assembly[] assemblies) throws Exception
   {
      for(Assembly assembly : assemblies) {
         if(assembly instanceof Viewsheet) {
            initTable(rvs, dispatcher, uri, ((Viewsheet) assembly).getAssemblies(true, false));
         }
         else {
            initTable(rvs, dispatcher, uri, assembly);
         }
      }
   }

   public void initTable(RuntimeViewsheet rvs, CommandDispatcher dispatcher,
                         String uri, Assembly... vsobjs) throws Exception
   {
      for(Assembly obj : vsobjs) {
         VSAssembly assembly = (VSAssembly) obj;
         boolean refreshed = false;

         if(assembly instanceof TableDataVSAssembly &&
            ((VSEventUtil.isVisible(rvs, assembly) &&
            VSEventUtil.isVisibleTabVS(assembly, rvs.isRuntime())) ||
            rvs.isRuntime() &&
            (rvs.isTipView(assembly.getAbsoluteName()) ||
            rvs.isPopComponent(assembly.getAbsoluteName()))))
         {
            int nrows = Math.max(assembly.getPixelSize().height / 16, 100);
            BaseTableController.loadTableData(rvs, assembly.getAbsoluteName(), 0, 0, nrows,
                                              uri, dispatcher);
            // Bug #17514 Initing a table can cause the info to have changes, like its
            // script being executed. 12.2 LoadTableLensEvent was called here which
            // not only loads data but also sends an updated assembly info to front end.
            // Refresh assembly here so info is correct.
            refreshVSAssembly(rvs, assembly, dispatcher);
            refreshed = true;
         }

         // @by billh, performance optimization for metlife
         // no need to refresh again if it was refreshed above
         if(!refreshed &&
            VSEventUtil.isVisibleTabVS(assembly) && (assembly instanceof CubeVSAssembly))
         {
            refreshVSAssembly(rvs, assembly, dispatcher);
         }
      }
   }

   public void refreshEmbeddedViewsheet(RuntimeViewsheet rvs,
                                               String uri,
                                               CommandDispatcher dispatcher)
      throws Exception
   {
      refreshEmbeddedViewsheet(rvs, rvs.getViewsheet(), uri, dispatcher, false);
   }

   private void refreshEmbeddedViewsheet(RuntimeViewsheet rvs, Viewsheet sheet, String uri,
                                         CommandDispatcher dispatcher, boolean manualRefresh)
      throws Exception
   {
      refreshEmbeddedViewsheet(rvs, sheet, uri, dispatcher, manualRefresh, true);
   }

   private void refreshEmbeddedViewsheet(RuntimeViewsheet rvs, Viewsheet sheet, String uri,
                                         CommandDispatcher dispatcher, boolean manualRefresh,
                                         boolean refreshParent)
      throws Exception
   {
      if(sheet == null || !sheet.isPrimary()) {
         return;
      }

      if(manualRefresh && refreshParent) {
         // make sure that any embedded viewsheets have their contents updated
         sheet.update(rvs.getAssetRepository(), null, rvs.getUser());
         ViewsheetSandbox box = rvs.getViewsheetSandbox();

         if(box != null) {
            box.disposeSandbox();
         }
      }

      Assembly[] assemblies = sheet.getAssemblies(false, true);

      for(Assembly assembly : assemblies) {
         VSAssembly vsassembly = (VSAssembly) assembly;

         if(vsassembly instanceof Viewsheet) {
            addDeleteVSObject(rvs, vsassembly, dispatcher, false);
            refreshEmbeddedViewsheet(
               rvs, (Viewsheet) vsassembly, uri, dispatcher, manualRefresh);
         }
      }
   }

   private void refreshContainer(RuntimeViewsheet rvs) {
      refreshContainer(rvs.getViewsheet());
   }

   private void refreshContainer(Viewsheet vs) {
      if(vs == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies(false, true);
      List<VSAssembly> containers = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         VSAssembly vsassembly = (VSAssembly) assembly;

         if(vsassembly instanceof ContainerVSAssembly) {
            // @by stephenwebster, fix bug1395088797566
            // By calling updateZIndex here, we ensure that elements
            // within the container have a ZIndex of at least the ZIndex
            // of its container at runtime.  Otherwise, elements in the
            // container may have a ZIndex as the first level of the
            // viewsheet. This causes tabbed elements to show below a
            // shape object, for example, even though the tabbed element
            // has a higher ZIndex.  This is only a runtime modification.
            // The design time ZIndex is controlled by
            // VSUtil.calcChildZIndex.
            VSEventUtil.updateZIndex(vs, assembly);
            containers.add(vsassembly);
         }
      }

      containers.sort(new Comparator<VSAssembly>() {
         @Override
         public int compare(VSAssembly v1, VSAssembly v2) {
            if(v1 == null || v2 == null) {
               return 0;
            }

            // v1 contains v2?
            if(contains(v1, v2)) {
               return -1;
            }

            // v2 contains v1?
            if(contains(v2, v1)) {
               return 1;
            }

            return 0;
         }

         private boolean contains(VSAssembly v1, VSAssembly v2) {
            VSAssembly p = v2.getContainer();

            while(p != null) {
               if(p == v1) {
                  return true;
               }

               p = p.getContainer();
            }

            return false;
         }
      });

      for(VSAssembly vsAssembly : containers) {
         ContainerVSAssembly container = (ContainerVSAssembly) vsAssembly;
         String[] children = container.getAbsoluteAssemblies();

         if(children != null && children.length > 0) {
            for(String child : children) {
               if(vs.getAssembly(child) == null) {
                  container.removeAssembly(child);
               }
            }
         }

         if(container instanceof CurrentSelectionVSAssembly) {
            continue;
         }

         children = container.getAbsoluteAssemblies();

         if(children.length <= 1) {
            vs.removeAssembly(container);

            if(children.length > 0) {
               VSAssembly comp = (VSAssembly) vs.getAssembly(children[0]);
               comp.setZIndex(container.getZIndex());
            }

            continue;
         }

         if(container instanceof Viewsheet) {
            refreshContainer((Viewsheet) container);
         }
      }
   }

   public void refreshVSAssembly(RuntimeViewsheet rvs, String aname,
                                 CommandDispatcher dispatcher)
         throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      refreshVSAssembly(rvs, vs.getAssembly(aname), dispatcher);
   }

   public void refreshVSAssembly(RuntimeViewsheet rvs, VSAssembly assembly,
                                 CommandDispatcher dispatcher)
      throws Exception
   {
      String shared = dispatcher.getSharedHint();

      if(assembly == null || (!VSEventUtil.isVisible(rvs, assembly) &&
         !AnnotationVSUtil.isAnnotation(assembly) &&
         !rvs.isTipView(assembly.getAbsoluteName()) &&
         !rvs.isPopComponent(assembly.getAbsoluteName())))
      {
         return;
      }

      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      if(assembly instanceof Viewsheet) {
         ViewsheetVSAssemblyInfo info =
            (ViewsheetVSAssemblyInfo) VSEventUtil.getAssemblyInfo(rvs, assembly);
         assembly.setVSAssemblyInfo(info);

         refreshVSObject(assembly, rvs, shared, box, dispatcher);

         for(Object infoObj : info.getChildAssemblies()) {
            VSAssemblyInfo childInfo =
               VSEventUtil.getAssemblyInfo(rvs, (VSAssembly) infoObj);
            ((VSAssembly) infoObj).setVSAssemblyInfo(childInfo);
            addDeleteVSObject(rvs, (VSAssembly) infoObj, dispatcher);
         }

         initTable(rvs, dispatcher, "", ((Viewsheet) assembly).getAssemblies(false, false));
      }
      else {
         if(assembly.isEmbedded()) {
            Viewsheet vs2 = assembly.getViewsheet();

            if(vs2 == null) {
               return;
            }

            String name2 = vs2.getAbsoluteName();

            // visible embedded viewsheet?
            if(!name2.contains(".")) {
               VSAssemblyInfo info2 = VSEventUtil.getAssemblyInfo(rvs, vs2);
               vs2.setVSAssemblyInfo(info2);
               refreshVSObject(vs2, rvs, shared, box, dispatcher);
            }
         }

         box.lockWrite();

         try {
            VSAssemblyInfo info = VSEventUtil.getAssemblyInfo(rvs, assembly);
            assembly.setVSAssemblyInfo(info);

            if(rvs.isRuntime() && AnnotationVSUtil.needRefreshAnnotation(info, dispatcher)) {
               AnnotationVSUtil.refreshAllAnnotations(rvs, assembly, dispatcher, this);
            }

            refreshVSObject(assembly, rvs, shared, box, dispatcher);

            if((info instanceof SelectionListVSAssemblyInfo) &&
               ((SelectionListVSAssemblyInfo) info).isAdhocFilter())
            {
               FormatInfo fmtInfo = info.getFormatInfo();
               VSCompositeFormat ofmt = info.getFormat();
               VSCompositeFormat tfmt =
                  fmtInfo.getFormat(VSAssemblyInfo.TITLEPATH);
               ofmt.getDefaultFormat().setBackgroundValue(Color.WHITE.getRGB() + "");
               tfmt.getDefaultFormat().setBackgroundValue(Color.WHITE.getRGB() + "");
            }

            // when a group container's visibility is changed at runtime (e.g. in a script),
            // the children have not been added in the client. need to call addDeleteVSObject
            // to make sure they are added to the client app if necessary.
            if(info instanceof GroupContainerVSAssemblyInfo ||
               info instanceof TabVSAssemblyInfo)
            {
               Object linkUri = box.getVariableTable().get("__LINK_URI__");
               addContainerVSObject(rvs, assembly, linkUri == null ? "" : linkUri.toString(),
                                    dispatcher, new ArrayList<>());
            }
         }
         finally {
            box.unlockWrite();
         }
      }
   }

   public void refreshVSAssembly(RuntimeViewsheet rvs, String name,
                                        CommandDispatcher dispatcher, boolean recursive)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly assembly = vs == null ?
         null : (VSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         return;
      }

      if(recursive && assembly instanceof ContainerVSAssembly) {
         ContainerVSAssembly cassembly = (ContainerVSAssembly) assembly;
         // use absolute name
         String[] names = cassembly.getAbsoluteAssemblies();

         for(String assemblyName : names) {
            refreshVSAssembly(rvs, assemblyName, dispatcher);
         }
      }

      refreshVSAssembly(rvs, assembly, dispatcher);
   }

   public void refreshVSObject(VSAssembly assembly, RuntimeViewsheet rvs, String sharedHint,
                               ViewsheetSandbox box, CommandDispatcher dispatcher)
   {
      if(assembly instanceof OutputVSAssembly && box != null) {
         OutputVSAssemblyInfo info = (OutputVSAssemblyInfo) assembly.getVSAssemblyInfo();
         box = getSandbox(box, info.getAbsoluteName());
         info.setLinkVarTable(box.getAllVariables());
         info.setLinkSelections(box.getSelections());
         parameterService.refreshTextParameters(rvs, assembly);
      }

      RefreshVSObjectCommand command = new RefreshVSObjectCommand();

      try {
         command.setInfo(objectModelService.createModel(assembly, rvs));
         command.setShared(sharedHint);
         command.setWizardTemporary(assembly.isWizardTemporary());
      }
      catch(MessageException ex) {
         MessageCommand msgCom = new MessageCommand();
         msgCom.setMessage(ex.getMessage());
         msgCom.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(msgCom);
      }

      if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
         // If assembly is in selection container, send event to selection container to
         // refresh child object
         dispatcher.sendCommand(assembly.getContainer().getAbsoluteName(), command);
      }
      else if(assembly.isEmbedded()) {
         dispatcher.sendCommand(assembly.getViewsheet().getAbsoluteName(), command);
      }
      else {
         dispatcher.sendCommand(command);
      }
   }

   public ViewsheetSandbox getSandbox(ViewsheetSandbox box, String name) {
      int index = name.lastIndexOf('.');

      if(index >= 0) {
         ViewsheetSandbox box0 = box.getSandbox(name.substring(0, index));
         name = name.substring(index + 1);
         return getSandbox(box0, name);
      }

      return box;
   }

   public void addDeleteVSObject(RuntimeViewsheet rvs, VSAssembly assembly,
                                 CommandDispatcher dispatcher) throws Exception
   {
      String assemblyName = assembly != null ? assembly.getAbsoluteName() : null;

      try {
         if(assemblyName != null) {
            dispatcher.sendCommand(assemblyName, new AssemblyLoadingCommand());
         }

         addDeleteVSObject(rvs, assembly, dispatcher, true);
      }
      finally {
         if(assemblyName != null) {
            dispatcher.sendCommand(assemblyName, new ClearAssemblyLoadingCommand());
         }
      }
   }

   public void addDeleteVSObject(RuntimeViewsheet rvs, VSAssembly assembly,
                                 CommandDispatcher dispatcher, boolean sub)
      throws Exception
   {
      if(assembly == null) {
         throw new ConfirmException();
      }

      String name = assembly.getAbsoluteName();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      if(rvs.getEmbedAssemblyInfo() != null && name != null) {
         EmbedAssemblyInfo embedAssemblyInfo = rvs.getEmbedAssemblyInfo();

         if(!Tool.equals(name, embedAssemblyInfo.getAssemblyName())) {
            return;
         }

         AssemblyInfo info = assembly.getInfo();

         if(info instanceof ChartVSAssemblyInfo) {
            // open with max mode
            ((ChartVSAssemblyInfo) info).setMaxSize(
               embedAssemblyInfo.getAssemblySize());
            info.setPixelSize(embedAssemblyInfo.getAssemblySize());
         }
      }

      // @by yanie: bug1422049637079
      // The runtime dynamic values might be reset, so herein execute them to
      // make sure the assembly's visibility state is correct
      if(assembly instanceof SelectionVSAssembly) {
         try {
            box.executeDynamicValues(name, assembly.getViewDynamicValues(true));
         }
         catch(ScriptException ex) {
            Tool.addUserMessage(ex.getMessage(), ConfirmException.ERROR);
         }
      }

      if(assembly instanceof SelectionTreeVSAssembly) {
         VSAQuery query = VSAQuery.createVSAQuery(getSandbox(box, name), assembly, DataMap.NORMAL);
         ((SelectionVSAQuery) query).refreshViewSelectionValue();
      }

      // VSEventUtil.getAssemblyInfo returns a cloned info. Since we create the object model
      // by passing in the assembly, we need to make sure the assembly has the correct info.
      // So clone assembly here, fix the info, and use cloned assembly to build models.
      VSAssembly vsAssembly = (VSAssembly) assembly.clone();

      box.lockWrite();

      try {
         vsAssembly.setVSAssemblyInfo((VSAssemblyInfo) assembly.getVSAssemblyInfo().clone(true));
      }
      finally {
         box.unlockWrite();
      }

      VSAssemblyInfo info = vsAssembly.getVSAssemblyInfo();
      VSEventUtil.fixAssemblyInfo(info, assembly, rvs);

      if(!VSEventUtil.isVisible(rvs, assembly) && !rvs.isTipView(name) &&
         !rvs.isPopComponent(name) && !VSEventUtil.isInTab(assembly) &&
         !info.isControlByScript() && !AnnotationVSUtil.isAnnotation(assembly))
      {
         RemoveVSObjectCommand command = new RemoveVSObjectCommand();
         command.setName(name);

         if(assembly.isEmbedded() && assembly.getViewsheet().isEmbedded()) {
            dispatcher.sendCommand(assembly.getViewsheet().getAbsoluteName(), command);
         }
         else if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
            // If assembly is in selection container, send event to selection container
            // to refresh child object
            dispatcher.sendCommand(assembly.getContainer().getAbsoluteName(), command);
         }
         else {
            dispatcher.sendCommand(command);
         }

         deleteEmbeddedViewsheetChildren(assembly, dispatcher);

         return;
      }

      AddVSObjectCommand.Mode mode = AddVSObjectCommand.Mode.DESIGN_MODE;

      if(AnnotationVSUtil.isAnnotation(assembly) && (!rvs.isRuntime() || name.contains("."))) {
         return;
      }

      try {
         box.updateAssembly(assembly.getAbsoluteName());
      }
      catch(ConfirmException | ScriptException ex) {
         // ScriptException should be logged at appropriate level when created
      }
      catch(Exception ex) {
         LOG.warn("Failed to update a viewsheet assembly after deleting children", ex);
      }

      if(assembly instanceof Viewsheet) {
         ViewsheetVSAssemblyInfo vinfo = (ViewsheetVSAssemblyInfo) info;
         AddVSObjectCommand command = new AddVSObjectCommand();
         command.setName(assembly.getAbsoluteName());
         command.setMode(mode);
         // use cloned vsAssembly because it contains correct updated info
         command.setModel(objectModelService.createModel(vsAssembly, rvs));
         command.setWizardTemporary(assembly.isWizardTemporary());

         if(assembly.isEmbedded() && assembly.getViewsheet().isEmbedded()) {
            dispatcher.sendCommand(assembly.getViewsheet().getAbsoluteName(), command);
         }
         else {
            dispatcher.sendCommand(command);
         }

         if(sub) {
            List objList = vinfo.getChildAssemblies();

            dispatcher.sendCommand(info.getAbsoluteName(), new RefreshEmbeddedVSCommand(objList));

            for(Object assemblyObj : objList) {
               VSAssembly sassembly = (VSAssembly) assemblyObj;
               VSAssemblyInfo cinfo = VSEventUtil.getAssemblyInfo(rvs, sassembly);

               if(VSEventUtil.isVisible(rvs, sassembly) ||
                  rvs.isRuntime() && (rvs.isTipView(cinfo.getAbsoluteName()) ||
                                      rvs.isPopComponent(cinfo.getAbsoluteName())))
               {
                  addDeleteVSObject(rvs, sassembly, dispatcher);
               }
            }
         }
      }
      else {
         if(info instanceof OutputVSAssemblyInfo && info.isEnabled()) {
            Object data = null;

            try {
               data = box.getData(assembly.getName());
            }
            catch(ScriptException e) {
               // ScriptException should be logged at appropriate level when created
            }
            catch(Exception e) {
               LOG.warn("Failed to get data", e);
            }

            OutputVSAssemblyInfo outputInfo = (OutputVSAssemblyInfo) info;

            if(data == null) {
               data = ((OutputVSAssembly) assembly).getValue();
            }
            // set the newest value again in case the newly calculated result has changed
            // due to sequencing (e.g. input assembly value applied in parameter after the
            // previous execution of output value). (68371)
            else {
               outputInfo.setValue(data);
               outputInfo.updateHighlight(box.getAllVariables(),
                                          box.getConditionAssetQuerySandbox(assembly.getViewsheet()));
            }

            BindingInfo binding = outputInfo.getBindingInfo();
            // it is not reasonable to disable text, this will cause
            // format problem, fix bug1343748782524
            outputInfo.setOutputEnabled(data != null || !box.isRuntime() ||
                                           binding == null || binding.isEmpty() ||
                                           outputInfo instanceof TextVSAssemblyInfo);
         }

         // first refresh, draw with mask
         if(!info.isEnabled()) {
            info = (VSAssemblyInfo) info.clone();
         }

         if(info instanceof OutputVSAssemblyInfo) {
            box = getSandbox(box, info.getAbsoluteName());
            ((OutputVSAssemblyInfo) info).setLinkVarTable(box.getAllVariables());
            ((OutputVSAssemblyInfo) info).setLinkSelections(box.getSelections());
            parameterService.refreshTextParameters(rvs, vsAssembly);
         }

         if(rvs.isRuntime() && AnnotationVSUtil.needRefreshAnnotation(info, dispatcher)) {
            AnnotationVSUtil.refreshAllAnnotations(rvs, assembly, dispatcher, this);
         }

         if(rvs.getViewsheetSandbox() == null) {
            return;
         }

         AddVSObjectCommand command = new AddVSObjectCommand();
         command.setName(assembly.getAbsoluteName());
         command.setMode(mode);
         command.setWizardTemporary(assembly.isWizardTemporary());

         try {
            // use cloned vsAssembly because it contains correct updated info
            command.setModel(objectModelService.createModel(vsAssembly, rvs));
         }
         catch(MessageException ex) {
            MessageCommand msgCom = new MessageCommand();
            msgCom.setMessage(ex.getMessage());
            msgCom.setType(MessageCommand.Type.ERROR);
            dispatcher.sendCommand(msgCom);
         }

         Assembly container = assembly.getContainer();

         // if assembly is in selection container, send event to selection container
         // to add child object
         if(container instanceof CurrentSelectionVSAssembly) {
            dispatcher.sendCommand(container.getAbsoluteName(), command);
         }
         else if(assembly.isEmbedded()) {
            dispatcher.sendCommand(assembly.getViewsheet().getAbsoluteName(), command);
         }
         else {
            dispatcher.sendCommand(command);
         }
      }
   }

   public void removeVSAssembly(RuntimeViewsheet rvs, String uri,
                                VSAssembly assembly, CommandDispatcher dispatcher,
                                boolean replace, boolean layout) throws Exception
   {
      removeVSAssemblies(rvs, uri, dispatcher, replace, layout, true, assembly);
   }

   public void removeVSAssemblies(RuntimeViewsheet rvs, String uri, CommandDispatcher dispatcher,
                                  boolean replace, boolean layout, boolean fireEvent,
                                  VSAssembly ...assemblies)
      throws Exception
   {
      removeVSAssemblies(rvs, uri, dispatcher, replace, layout, fireEvent, true, assemblies);
   }

   public void removeVSAssemblies(RuntimeViewsheet rvs, String uri, CommandDispatcher dispatcher,
                                  boolean replace, boolean layout, boolean fireEvent,
                                  boolean refreshData, VSAssembly ...assemblies)
      throws Exception
   {
      final String id = rvs.getID();
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null || assemblies.length == 0) {
         return;
      }

      final Viewsheet gvs = vs; // global viewsheet
      String name0 = assemblies[0].getAbsoluteName();
      final int dot = name0.lastIndexOf('.');

      if(dot >= 0) {
         String bname = name0.substring(0, dot);
         Assembly vsc = vs.getAssembly(bname);
         ViewsheetSandbox box2 = box.getSandbox(bname);

         if(vsc instanceof Viewsheet && box2 != null) {
            vs = (Viewsheet) vsc;
            box = box2;
         }
      }

      final List<String> names = Arrays.stream(assemblies).map(Assembly::getName)
         .collect(Collectors.toList());
      final List<String> fnames = Arrays.stream(assemblies).map(Assembly::getAbsoluteName)
         .collect(Collectors.toList());

      // remove assembly in layouts
      Viewsheet finalVs = vs;
      names.forEach(name -> vsLayoutService.removeLayoutAssembly(finalVs, name));

      final Worksheet ws = vs.getBaseWorksheet();
      final List<AssemblyEntry> assemblyEntries = Arrays.stream(assemblies)
         .map(a -> a.getAssemblyEntry()).collect(Collectors.toList());
      final Set<AssemblyRef> refs = new HashSet<>();
      final Set<String> containsDepNames = new HashSet<>();

      for(AssemblyEntry entry : assemblyEntries) {
         if(entry == null) {
            continue;
         }

         Set<AssemblyRef> depSet = Arrays.asList(finalVs.getDependings(entry))
            .stream()
            .filter(ref -> !assemblyEntries.contains(ref.getEntry()))
            .collect(Collectors.toSet());

         if(depSet.size() > 0) {
            containsDepNames.add(entry.getName());
         }

         refs.addAll(depSet);
      }

      final List<Assembly> rlist = new ArrayList<>();
      final Set<SelectionVSAssembly> relatedSelections = new HashSet<>();
      final Set<String> refreshAssemblies = new HashSet<>();
      boolean needRefresh = false;

      if(!replace) {
         for(Assembly assembly : assemblies) {
            if(assembly instanceof SelectionVSAssembly) {
               final SelectionVSAssembly sassembly = (SelectionVSAssembly) assembly;
               final List<String> tableNames = sassembly.getTableNames();

               for(String tname : tableNames) {
                  final SelectionVSAssembly[] sarr = box.getSelectionVSAssemblies(tname);

                  final Optional<SelectionVSAssembly> relatedSelection = Arrays.stream(sarr)
                     .filter(item -> item != assembly)
                     .findFirst();

                  // one selection is sufficient because other related selections will be updated
                  // during processing in ViewsheetSandbox.
                  relatedSelection.ifPresent(relatedSelections::add);
               }
            }
            else if(assembly instanceof EmbeddedTableVSAssembly) {
               new EmbeddedTableVSAQuery(box, assembly.getName(), false).resetEmbeddedData();
            }
            else if(assembly instanceof InputVSAssembly) {
               InputVSAssembly vassembly = (InputVSAssembly) assembly;
               new InputVSAQuery(box, assembly.getName()).resetEmbeddedData(false);

               if(vassembly.isVariable()) {
                  String tname = vassembly.getTableName();

                  if(tname != null && tname.startsWith("$(")) {
                     tname = tname.substring(2, tname.length() - 1);
                  }

                  VariableAssembly vass = (VariableAssembly) ws.getAssembly(tname);
                  Object dvalue = null;

                  if(vass != null) {
                     dvalue = vass.getVariable().getValueNode() == null ?
                        null : vass.getVariable().getValueNode().getValue();
                  }

                  VSAssemblyInfo info = vassembly.getVSAssemblyInfo();

                  if(dvalue != null) {
                     if(info instanceof SliderVSAssemblyInfo) {
                        ((SliderVSAssemblyInfo) info).setSelectedObject(dvalue);
                     }
                     else if(info instanceof SpinnerVSAssemblyInfo) {
                        ((SpinnerVSAssemblyInfo) info).setSelectedObject(dvalue);
                     }
                     else if(info instanceof CheckBoxVSAssemblyInfo) {
                        Object[] objs = new Object[]{ dvalue };
                        ((CheckBoxVSAssemblyInfo) info).setSelectedObjects(objs);
                     }
                     else if(info instanceof RadioButtonVSAssemblyInfo) {
                        ((RadioButtonVSAssemblyInfo) info).setSelectedObject(dvalue);
                     }
                     else if(info instanceof ComboBoxVSAssemblyInfo) {
                        ((ComboBoxVSAssemblyInfo) info).setSelectedObject(dvalue);
                     }
                  }
                  else {
                     if(vassembly instanceof NumericRangeVSAssembly) {
                        ((NumericRangeVSAssembly) vassembly).clearSelectedObject();
                     }
                     else if(vassembly instanceof ListInputVSAssembly) {
                        ((ListInputVSAssembly) vassembly).clearSelectedObjects();
                     }
                  }

                  needRefresh = true;
               }
            }
            else if(assembly instanceof AnnotationVSAssembly) {
               Assembly base = AnnotationVSUtil.getBaseAssembly(gvs, assembly.getAbsoluteName());

               if(base != null && base.getInfo() instanceof BaseAnnotationVSAssemblyInfo) {
                  BaseAnnotationVSAssemblyInfo baseInfo =
                     (BaseAnnotationVSAssemblyInfo) base.getInfo();
                  baseInfo.removeAnnotation(assembly.getAbsoluteName());

                  if(!assemblyEntries.contains(base.getAssemblyEntry())) {
                     refreshAssemblies.add(base.getAbsoluteName());
                  }
               }
            }
         }

         for(AssemblyRef ref : refs) {
            AssemblyEntry entry = ref.getEntry();
            Assembly tassembly = null;

            if(entry.isWSAssembly()) {
               tassembly = ws != null ? ws.getAssembly(entry) : null;

               // reexecute runtime condition list
               if(tassembly instanceof TableAssembly) {
                  //!(assembly instanceof EmbeddedTableVSAssembly))
                  ((TableAssembly) tassembly).setPreRuntimeConditionList(null);
                  ((TableAssembly) tassembly).setPostRuntimeConditionList(null);
                  AssemblyRef[] refs2 = vs.getDependeds(entry);

                  for(int j = 0; refs2 != null && j < refs2.length; j++) {
                     Assembly assembly2 = vs.getAssembly(refs2[j].getEntry());

                     if(assembly2 instanceof SelectionVSAssembly) {
                        rlist.add(assembly2);
                     }
                  }
               }
            }
            else {
               tassembly = vs.getAssembly(entry);
            }

            if(tassembly != null) {
               rlist.add(tassembly);
            }
         }
      }

      Assembly[] rarr = new Assembly[rlist.size()];
      rlist.toArray(rarr);
      ArrayList<AssemblyRef> vrefs = new ArrayList<>(
         assemblyEntries.stream()
            .map(e -> new HashSet<>(Arrays.asList(finalVs.getViewDependings(e))))
            .reduce(new HashSet<>(), (set, a) -> {
               set.addAll(a);
               return set;
            }));
      Set<String> removed = new HashSet<>();

      for(int i = 0; i < names.size(); i++) {
         String name = names.get(i);
         String fname = fnames.get(i);
         VSAssembly assembly = assemblies[i];

         // there may be duplicates when grouped container is involved
         if(removed.contains(fname)) {
            continue;
         }

         removed.add(fname);

         if(vs.removeAssembly(name, fireEvent)) {
            ViewsheetInfo vinfo = vs.getViewsheetInfo();
            vinfo.setFilterID(assembly.getAbsoluteName(), null);
            vinfo.removeLocalID(name, true);

            RemoveVSObjectCommand command = new RemoveVSObjectCommand();
            command.setName(fname);

            if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
               //If assembly is in selection container, send event to selection container
               // to refresh child object
               dispatcher.sendCommand(assembly.getContainer().getAbsoluteName(), command);
            }
            // remove in embedded vs
            else if(!fname.equals(name)) {
               dispatcher.sendCommand(fname.substring(0, fname.length() - name.length() - 1),
                                      command);
            }
            else {
               dispatcher.sendCommand(command);
            }

            // reexecute depending assemblies
            // if there are related selections, an execution will be triggered later.
            // dont run reset/execute here otherwise the selection condition will be missing
            // and the assemblies will be run twice.
            if(rarr.length > 0 && relatedSelections.isEmpty() && containsDepNames.contains(name)) {
               ChangedAssemblyList clist = new ChangedAssemblyList();
               box.reset(null, rarr, clist, false, false, null);
               execute(rvs, fname, uri, clist, dispatcher, false, true, names);
            }

            if(!replace) {
               // current selection supports remove child
               // when in embedded viewsheet
               if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
                  ContainerVSAssembly cass = (ContainerVSAssembly) assembly.getContainer();

                  // do not use absolute name
                  if(cass.removeAssembly(assembly.getName())) {
                     refreshAssemblies.add(cass.getAbsoluteName());
                  }
               }
               else {
                  for(Assembly assemblyItem : vs.getAssemblies()) {
                     if(!(assemblyItem instanceof ContainerVSAssembly)) {
                        continue;
                     }

                     ContainerVSAssembly container = (ContainerVSAssembly) assemblyItem;

                     if(!container.removeAssembly(name)) {
                        continue;
                     }

                     refreshAssemblies.add(container.getAbsoluteName());

                     if(assembly instanceof ContainerVSAssembly) {
                        ContainerVSAssembly removedContainer = (ContainerVSAssembly) assembly;

                        if(removedContainer.getAssemblies().length > 0) {
                           String firstChildName = removedContainer.getAssemblies()[0];
                           List<String> children = new ArrayList<>();

                           Collections.addAll(children, container.getAssemblies());

                           children.add(firstChildName);
                           container.setAssemblies(children.toArray(new String[0]));
                        }
                     }

                     if(!(assemblyItem instanceof TabVSAssembly)) {
                        continue;
                     }

                     TabVSAssembly tab = (TabVSAssembly) assemblyItem;
                     String[] tabAssemblies = tab.getAssemblies();

                     // for viewsheet performance, we don't refresh the assembly
                     // which is not the selected tab in tab assembly,
                     // so if we remove the selected assembly in tab, we should
                     // refresh the next selected assembly.
                     // see bug1302191571448
                     tab.setSelectedValue(tabAssemblies[0]);
                     loadTableLens(rvs, tabAssemblies[0], uri, dispatcher);

                     if(tabAssemblies.length <= 1) {
                        removeVSAssembly(rvs, uri, tab, dispatcher, false, true);

                        VSAssembly comp = (VSAssembly)
                           tab.getViewsheet().getAssembly(tabAssemblies[0]);
                        comp.setZIndex(tab.getZIndex());
                        refreshAssemblies.add(comp.getAbsoluteName());
                     }
                     else {
                        refreshAssemblies.add(tab.getAbsoluteName());
                        VSAssembly comp = (VSAssembly)
                           tab.getViewsheet().getAssembly(tabAssemblies[0]);
                        refreshAssemblies.add(comp.getAbsoluteName());
                     }
                  }
               }
            }

            if(assembly instanceof Viewsheet) {
               List<Assembly> objList = new ArrayList<>();
               VSEventUtil.appendEmbeddedChild((Viewsheet) assembly, objList, true, rvs);

               for(Assembly obj : objList) {
                  VSAssembly childAssembly = (VSAssembly) obj;
                  command = new RemoveVSObjectCommand();
                  command.setName(childAssembly.getAbsoluteName());
                  dispatcher.sendCommand(command);
               }
            }
            else if(assembly instanceof TabVSAssembly && layout) {
               layoutViewsheet(rvs, id, uri, dispatcher);

               TabVSAssembly tab = (TabVSAssembly) assembly;
               String[] tabAssemblies = tab.getAssemblies();

               // move floatable children apart since layout would not move them
               for(int k = 0; k < tabAssemblies.length; k++) {
                  VSAssembly comp = (VSAssembly)
                     tab.getViewsheet().getAssembly(tabAssemblies[k]);

                  if(comp instanceof FloatableVSAssembly) {
                     Point pos = comp.getPixelOffset();
                     comp.setPixelOffset(new Point(pos.x + (k * AssetUtil.defw), pos.y));
                  }

                  refreshAssemblies.add(tabAssemblies[k]);
                  loadTableLens(rvs, tabAssemblies[k], uri, dispatcher);
               }
            }
            else if(assembly instanceof GroupContainerVSAssembly && layout) {
               String[] children = ((GroupContainerVSAssembly) assembly).getAssemblies();

               for(String child : children) {
                  refreshAssemblies.add(child);
               }
            }
            else if(assembly instanceof DrillFilterVSAssembly && !replace) {
               needRefresh = true;
            }
         }
         else {
            MessageCommand command = new MessageCommand();
            command.setMessage(
               Catalog.getCatalog().getString("common.removeAssemblyFailed"));
            command.setType(MessageCommand.Type.WARNING);
            dispatcher.sendCommand(command);
         }
      }

      // reprocess associated assemblies
      if(refreshData && !relatedSelections.isEmpty()) {
         try {
            int hint = VSAssembly.OUTPUT_DATA_CHANGED;

            for(SelectionVSAssembly relatedSelection : relatedSelections) {
               execute(rvs, relatedSelection.getAbsoluteName(), uri, hint, dispatcher);
            }
         }
         catch(ConfirmDataException ex) {
            // do nothing, it doesn't need the confirm data exception
            // when remove assembly
         }
         catch(ScriptException ex) {
            List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

            if(exs != null) {
               exs.add(ex);
            }
         }
      }

      // refresh views dependings
      if(!replace) {
         for(int i = 0; i < vrefs.size(); i++) {
            AssemblyEntry entry = vrefs.get(i).getEntry();

            if(entry.isVSAssembly()) {
               try {
                  box.executeView(entry.getName(), true);
               }
               catch(ConfirmDataException ex) {
                  // do nothing, it doesn't need the confirm data exception
                  // when remove assembly
               }
               catch(ScriptException ex) {
                  List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

                  if(exs != null) {
                     exs.add(ex);
                  }
               }

               refreshAssemblies.add(entry.getAbsoluteName());
            }
         }
      }

      if(needRefresh) {
         AssetQuerySandbox wbox = box.getAssetQuerySandbox();
         wbox.setIgnoreFiltering(false);
         ChangedAssemblyList clist = createList(true, dispatcher, rvs, uri);
         refreshEmbeddedViewsheet(rvs, uri, dispatcher);
         box.resetRuntime();
         refreshViewsheet(rvs, id, uri, dispatcher, false, true, true, clist);
      }
      else {
         for(String name : refreshAssemblies) {
            refreshVSAssembly(rvs, name, dispatcher, true);
            VSAssembly assembly = vs.getAssembly(name);

            if(assembly != null && !assembly.isVisible() && assembly.getContainer() instanceof TabVSAssembly) {
               refreshVSAssembly(rvs, assembly.getContainer(), dispatcher);

               for(String child : ((TabVSAssembly) assembly.getContainer()).getAbsoluteAssemblies()) {
                  VSAssembly childAssembly = vs.getAssembly(child);

                  if(assembly.isEmbedded() && childAssembly != null) {
                     addDeleteVSObject(rvs, childAssembly, dispatcher);
                  }
                  else {
                     refreshVSAssembly(rvs, child, dispatcher);
                  }
               }
            }
         }
      }
   }

   public void execute(RuntimeViewsheet rvs, String name, String uri, int hint,
                       CommandDispatcher dispatcher)
      throws Exception
   {
      execute(rvs, name, uri, hint, true, dispatcher);
   }

   public void execute(RuntimeViewsheet rvs, String name, String uri, int hint,
                       boolean refreshData, CommandDispatcher dispatcher)
      throws Exception
   {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return;
      }

      ChangedAssemblyList clist = new ChangedAssemblyList();

      try {
         box.processChange(name, hint, clist);
      }
      catch(ConfirmException e) {
         if(!waitForMV(e, rvs, dispatcher)) {
            throw e;
         }
      }

      try {
         execute(rvs, name, uri, clist, dispatcher, true, refreshData);
      }
      catch(ConfirmException e) {
         if(!waitForMV(e, rvs, dispatcher)) {
            throw e;
         }
      }
   }

   /**
    * Execute the changed assemblies and refresh them if necessary.
    */
   public void execute(RuntimeViewsheet rvs, String name, String uri,
                       ChangedAssemblyList clist, CommandDispatcher dispatcher,
                       boolean included) throws Exception
   {
      execute(rvs, name, uri, clist, dispatcher, included, true);
   }

   public void execute(RuntimeViewsheet rvs, String name, String uri,
                       ChangedAssemblyList clist, CommandDispatcher dispatcher,
                       boolean included, boolean refreshData) throws Exception
   {
      execute(rvs, name, uri, clist, dispatcher, included, refreshData, null);
   }

   /**
    * Execute the changed assemblies and refresh them if necessary.
    */
   public void execute(RuntimeViewsheet rvs, String name, String uri,
                       ChangedAssemblyList clist, CommandDispatcher dispatcher,
                       boolean included, boolean refreshData, List<String> removed)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      List<Object> processed = new ArrayList<>();
      // refreshData before executeView so the highlight uses the correct value
      List<AssemblyEntry> dataList = clist.getDataList();
      List<AssemblyEntry> viewList = clist.getViewList();
      Set<String> tableDataAssemblies = new HashSet<>();

      for(AssemblyEntry entry : dataList) {
         String vname = entry.getAbsoluteName();
         VSAssembly assembly = vs.getAssembly(vname);

         // already processed? ignore it
         // if the assembly will be removed, do not fix its relation data.
         if(clist.getProcessedList().contains(entry) ||
            AnnotationVSUtil.isAnnotation(assembly) ||
            removed != null && removed.contains(vname))
         {
            continue;
         }

         if(refreshData) {
            refreshData(rvs, dispatcher, entry, uri);
         }

         if(assembly == null) {
            continue;
         }

         if((included || !vname.equals(name)) && !processed.contains(vname)) {
            // if it will be refreshed in the view loop, don't refresh it twice
            if(!viewList.contains(entry)) {
               addDeleteVSObject(rvs, assembly, dispatcher);
            }

            tableDataAssemblies.add(vname);
            processed.add(vname);
         }
      }

      Tool.mergeSort(viewList, new DependencyComparator(vs, true));

      for(AssemblyEntry entry : viewList) {
         // already processed? ignore it
         if(clist.getProcessedList().contains(entry) ||
            removed != null && removed.contains(entry.getAbsoluteName()))
         {
            continue;
         }

         String vname = entry.getAbsoluteName();
         VSAssembly assembly = (VSAssembly) vs.getAssembly(vname);

         if(assembly == null || AnnotationVSUtil.isAnnotation(assembly)) {
            continue;
         }

         // Fix Bug #22513. Because the call to refreshData() in the previous loop calls
         // refreshVSAssembly() and causes a VGraphPair to be created, we need to clear the cached
         // VGraphPair before calling executeView() so that a VGraphPair with the dynamic values
         // will be placed in the cache. If we can perform the refreshData() without generating a
         // VGraphPair, we should be able to remove this.
         box.clearGraph(entry.getAbsoluteName());

         box.executeView(entry.getAbsoluteName(), false);

         if(included || !vname.equals(name)) {
            if(refreshData) {
               addDeleteVSObject(rvs, assembly, dispatcher);
            }

            // @by davyc, for view, should also refresh children,
            // to make sure in client the enable is correct
            if(assembly instanceof ContainerVSAssembly) {
               addContainerVSObject(rvs, assembly, uri, dispatcher, processed);
            }
            else if(assembly instanceof Viewsheet) {
               Assembly[] assems = ((Viewsheet) assembly).getAssemblies(true);

               for(Assembly assem : assems) {
                  if(assem != null) {
                     addDeleteVSObject(rvs, (VSAssembly) assem, dispatcher);

                     if(!processed.contains(assem.getAbsoluteName())) {
                        tableDataAssemblies.add(assem.getAbsoluteName());
                        processed.add(assem.getAbsoluteName());
                     }
                  }
               }
            }

            if(!processed.contains(vname)) {
               tableDataAssemblies.add(vname);
               processed.add(vname);
            }
         }
      }

      // load table data after execute() so the dynamic values would be
      // updated already
      for(String vname : tableDataAssemblies) {
         if(!WizardRecommenderUtil.isWizardTempBindingAssembly(vname)) {
            // Bug 19946, dynamic format properties not applied on cached table
            // need to clear and re-create VSTableLens
            box.resetDataMap(vname);
            loadTableLens(rvs, vname, uri, dispatcher, refreshData);
         }
      }

      // the setActionVisible may be called in script so we refresh the info.
      // in the future, we may consider adding the dependency to clist to
      // refresh only necessary
      setPermission(rvs, rvs.getUser(), dispatcher);

      final UserMessage msg = Tool.getUserMessage();

      if(msg != null) {
         dispatcher.sendCommand(MessageCommand.fromUserMessage(msg));
      }
   }

   public void executeInfluencedHyperlinkAssemblies(Viewsheet vs,
                                                    CommandDispatcher dispatcher,
                                                    RuntimeViewsheet rvs,
                                                    String uri, List<String> group)
      throws Exception
   {
      Assembly[] assemblies = vs.getAssemblies();
      ChangedAssemblyList clist = createList(true, dispatcher, rvs, uri);

      // Iterate over all assemblies and for add to view list if they have
      // hyperlinks that "send selection parameters"
      for(Assembly ass : assemblies) {
         VSAssembly vass = (VSAssembly) ass;
         VSAssemblyInfo info = vass.getVSAssemblyInfo();
         Hyperlink[] links = VSUtil.getAllLinks(info, true);

         for(Hyperlink link : links) {
            if(link != null && link.isSendSelectionParameters()) {
               // bindable table exist in the processed selections?
               // ignore it, because the selection will handle it
               if(group != null && vass instanceof BindableVSAssembly) {
                  String tname = vass.getTableName();

                  if(tname != null && group.contains(tname)) {
                     break;
                  }
               }

               AssemblyEntry vref = vass.getAssemblyEntry();

               // clear the graph, because it is invalid(cancelled)
               if(vass instanceof ChartVSAssembly) {
                  rvs.getViewsheetSandbox().clearGraph(info.getAbsoluteName());
               }

               if(!clist.getViewList().contains(vref)) {
                  clist.getViewList().add(vref);
               }

               break;
            }
         }
      }

      if(!clist.getViewList().isEmpty()) {
         execute(rvs, null, uri, clist, dispatcher, true);
      }
   }

   private void addContainerVSObject(RuntimeViewsheet rvs, VSAssembly assembly,
                                     String uri, CommandDispatcher dispatcher,
                                     List<Object> processed) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      List<Assembly> list = new ArrayList<>();
      Set<String> vsNames = new HashSet<>();
      VSEventUtil.getAssembliesInContainer(vs, assembly, list);

      for(Assembly assemblyItem : list) {
         VSAssembly child = (VSAssembly) assemblyItem;

         // Ensure that parent viewsheet assemblies are added before their children
         addContainerObjectParent(child, list, processed, vsNames, rvs, uri, dispatcher);
         addDeleteVSObject(rvs, child, dispatcher);

         if(!processed.contains(child.getAbsoluteName())) {
            loadTableLens(rvs, child.getAbsoluteName(), uri, dispatcher);
            processed.add(child);
         }
      }
   }

   private void addContainerObjectParent(VSAssembly child, List<Assembly> list,
                                         List<Object> processed, Set<String> vsNames,
                                         RuntimeViewsheet rvs, String uri,
                                         CommandDispatcher dispatcher) throws Exception
   {
      String vsName = child.getViewsheet().getVSAssemblyInfo().getAbsoluteName();

      if(vsName != null && !vsNames.contains(vsName)) {
         vsNames.add(vsName);

         for(Assembly assemblyItem : list) {
            VSAssembly assemblyItem0 = (VSAssembly) assemblyItem;

            if(assemblyItem0.getName().equals(vsName)) {
               addContainerObjectParent(assemblyItem0, list, processed, vsNames, rvs, uri, dispatcher);
               addDeleteVSObject(rvs, assemblyItem0, dispatcher);

               if(!processed.contains(assemblyItem0.getAbsoluteName())) {
                  loadTableLens(rvs, assemblyItem0.getAbsoluteName(), uri, dispatcher);
                  processed.add(assemblyItem0);
               }

               break;
            }
         }
      }

      if(child instanceof Viewsheet) {
         vsNames.add(child.getAbsoluteName());
      }
   }

   /**
    * Check the removed assemblies of new viewsheet and
    * update client side components for consistency.
    *
    * @param ovs old viewsheet
    * @param nvs new viewsheet
    * @param dispatcher command dispatcher
    */
   public void checkAndRemoveAssemblies(Viewsheet ovs,
                                        Viewsheet nvs,
                                        CommandDispatcher dispatcher)
   {
      final List<Assembly> newAssemblies = Arrays.asList(nvs.getAssemblies());
      final Assembly[] oldAssemblies = ovs.getAssemblies();

      for(Assembly oassembly : oldAssemblies) {
         final VSAssembly oldAssembly = (VSAssembly) oassembly;
         final VSAssembly newAssembly = (VSAssembly) nvs.getAssembly(oassembly.getName());
         final VSAssembly oldAssemblyContainer = oldAssembly.getContainer();

         if(newAssembly == null ||
            !newAssemblies.contains(oassembly) ||
            !Objects.equals(oldAssemblyContainer, newAssembly.getContainer()))
         {
            final RemoveVSObjectCommand command = new RemoveVSObjectCommand();
            command.setName(oldAssembly.getName());

            if(oldAssemblyContainer instanceof CurrentSelectionVSAssembly) {
               dispatcher.sendCommand(oldAssemblyContainer.getAbsoluteName(), command);
            }
            else {
               dispatcher.sendCommand(command);
            }

            // this probably isn't necessary since the embedded viewsheet itself will be removed
            // so nothing is listening to the remove commands.
            deleteEmbeddedViewsheetChildren(oldAssembly, dispatcher);
         }
      }
   }

   private void deleteEmbeddedViewsheetChildren(VSAssembly assembly,
                                                CommandDispatcher dispatcher)
   {
      if(!assembly.isEmbedded() || !(assembly instanceof Viewsheet)) {
         return;
      }

      List<Assembly> assemblies = new ArrayList<>();
      VSEventUtil.listEmbeddedAssemblies((Viewsheet) assembly, assemblies);

      for(Assembly embeddedAssembly : assemblies) {
         String name = embeddedAssembly.getAbsoluteName();
         RemoveVSObjectCommand command = new RemoveVSObjectCommand();
         command.setName(name);
         dispatcher.sendCommand(command);
      }
   }

   public void setPermission(RuntimeViewsheet rvs, Principal user,
                             CommandDispatcher dispatcher) throws Exception
   {
      SetPermissionsCommand command = new SetPermissionsCommand();
      command.setPermissions(getPermissions(rvs, user));
      dispatcher.sendCommand(command);
   }

   public Set<String> getPermissions(RuntimeViewsheet rvs, Principal user) throws Exception {
      Set<String> permissions = new HashSet<>();

      if(rvs.isRuntime()) {
         final SecurityEngine engine = SecurityEngine.getSecurity();

         if(Boolean.parseBoolean(SreeEnv.getProperty("Viewsheet Toolbar Hidden"))) {
            permissions.add("Toolbar");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "AddBookmark", ResourceAction.READ)) {
            permissions.add("AddBookmark");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "BrowserBookmark", ResourceAction.READ)) {
            permissions.add("BrowserBookmark");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Print", ResourceAction.READ)) {
            permissions.add("PrintVS");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "SaveSnapshot", ResourceAction.READ)) {
            permissions.add("SaveSnapshot");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "HOME", ResourceAction.READ)) {
            permissions.add("Home");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "PageNavigation", ResourceAction.READ)) {
            permissions.add("PageNavigation");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Edit", ResourceAction.READ) ||
            !engine.checkPermission(user, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS))
         {
            permissions.add("Edit");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Export", ResourceAction.READ)) {
            permissions.add("ExportVS");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Import", ResourceAction.READ) ||
            !FormUtil.containsForm(rvs.getViewsheet(), true))
         {
            permissions.add("ImportXLS");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Zoom In", ResourceAction.READ)) {
            permissions.add("Zoom In");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Zoom Out", ResourceAction.READ)) {
            permissions.add("Zoom Out");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Email", ResourceAction.READ)) {
            permissions.add("Email");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Schedule", ResourceAction.READ) ||
            !engine.checkPermission(user, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS) ||
            !engine.checkPermission(user, ResourceType.SCHEDULE_TASK_FOLDER, "/", ResourceAction.READ) ||
            VSUtil.hideActionsForHostAssets(rvs.getEntry(), user))
         {
            permissions.add("Schedule");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Refresh", ResourceAction.READ)) {
            permissions.add("Refresh");
         }

         String entryPath = rvs.getEntry().getScope() == AssetRepository.USER_SCOPE ?
            Tool.MY_DASHBOARD + "/" + rvs.getEntry().getPath() : rvs.getEntry().getPath();

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_TOOLBAR_ACTION, "Social Sharing", ResourceAction.READ) ||
            !(engine.checkPermission(user, ResourceType.REPORT, entryPath, ResourceAction.SHARE) ||
               VSUtil.isDefaultVSGloballyViewsheet(rvs.getEntry(), user)))
         {
            permissions.add("Social Sharing");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_ACTION, "Bookmark", ResourceAction.READ)) {
            permissions.add("AllBookmark");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_ACTION, "OpenBookmark", ResourceAction.READ)) {
            permissions.add("OpenBookmark");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_ACTION, "ShareBookmark", ResourceAction.READ)) {
            permissions.add("ShareBookmark");
         }

         if(!engine.checkPermission(user, ResourceType.VIEWSHEET_ACTION, "ShareToAll", ResourceAction.READ)) {
            permissions.add("ShareToAll");
         }

         if(!engine.checkPermission(user, ResourceType.PORTAL_TAB, "Report", ResourceAction.READ)) {
            permissions.add("PortalRepository");
         }

         if(engine.checkPermission(user, ResourceType.REPORT, rvs.getEntry().getPath(),
                                   ResourceAction.WRITE))
         {
            permissions.add("Viewsheet_Write");
         }

         boolean profiling = ((XPrincipal) user).isProfiling() &&
            SecurityEngine.getSecurity().checkPermission(user, ResourceType.PROFILE, "*", ResourceAction.ACCESS);

         if(profiling) {
            permissions.add("Profiling");
         }

         final Viewsheet vs = rvs.getViewsheet();

         if(vs != null) {
            final VSAssemblyInfo vsAssemblyInfo = vs.getVSAssemblyInfo();
            permissions.addAll(vsAssemblyInfo.getActionNames());
         }
      }

      return permissions;
   }

   public void setExportType(RuntimeViewsheet rvs, CommandDispatcher dispatcher) {
      Viewsheet vs = rvs.getViewsheet();
      VSAssemblyInfo vsAssemblyInfo = vs.getVSAssemblyInfo();
      String[] allOptions = VSUtil.getExportOptions();
      ArrayList<String> options = new ArrayList<>();

      for(int i = 0; i < allOptions.length; i++) {
         String eoption = allOptions[i];

         if(vsAssemblyInfo.isActionVisible(eoption)) {
            options.add(eoption);
         }
      }

      SetExportTypesCommand command = new SetExportTypesCommand();
      command.setExportTypes(options.toArray(new String[options.size()]));
      dispatcher.sendCommand(command);
   }

   public void setComposedDashboard(RuntimeViewsheet rvs, CommandDispatcher dispatcher) {
      Viewsheet vs = rvs.getViewsheet();

      if(vs.getViewsheetInfo().isComposedDashboard()) {
         dispatcher.sendCommand(new SetComposedDashboardCommand());
      }
   }

   private void refreshData(RuntimeViewsheet rvs, CommandDispatcher dispatcher,
                           AssemblyEntry entry, String uri)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null) {
         return;
      }

      VSAssembly assembly = vs.getAssembly(entry.getAbsoluteName());

      if(assembly instanceof OutputVSAssembly) {
         // set link uri to output assembly info
         OutputVSAssemblyInfo oinfo = (OutputVSAssemblyInfo) assembly.getInfo();
         oinfo.setLinkURI(uri);
         box.executeOutput(entry);
         Object data = oinfo.getValue();

         if(data != null) {
            ((OutputVSAssembly) assembly).updateHighlight(
               box.getAllVariables(),
               box.getConditionAssetQuerySandbox(assembly.getViewsheet()));
         }
      }

      refreshVSAssembly(rvs, entry.getAbsoluteName(), dispatcher);
   }

   public void loadTableLens(RuntimeViewsheet rvs, String name, String uri,
                             CommandDispatcher dispatcher)
   {
      loadTableLens(rvs, name, uri, dispatcher, true);
   }

   private void loadTableLens(RuntimeViewsheet rvs, String name, String uri,
                             CommandDispatcher dispatcher, boolean refreshData)
   {
      try {
         Viewsheet vs = rvs.getViewsheet();
         VSAssembly assembly = vs == null ? null : (VSAssembly) vs.getAssembly(name);

         if(!(assembly instanceof TableDataVSAssembly)) {
            return;
         }

         VSAssemblyInfo info = assembly.getVSAssemblyInfo();

         if(!VSEventUtil.isVisible(rvs, assembly) && !info.isControlByScript() &&
            !(rvs.isRuntime() && (rvs.isTipView(name) ||
            rvs.isPopComponent(name))))
         {
            return;
         }

         int mode = 0;
         int num = 100;
         int start = ((TableDataVSAssembly) assembly).getLastStartRow();
         BaseTableController.loadTableData(rvs, name, mode, start, num, uri, dispatcher, refreshData);
      }
      catch(ExpiredSheetException | CancelledException | ConfirmException | MessageException |
         ColumnNotFoundException e)
      {
         throw e;
      }
      catch(ScriptException e) {
         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(exs != null) {
            exs.add(e);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to load the table data", ex);
         sendMessage(ex.getMessage(), MessageCommand.Type.ERROR, dispatcher);
      }
   }

   private void updateExternalUrl(RuntimeViewsheet rvs, CommandDispatcher dispatcher,
                                  AssemblyEntry entry, String linkUri)
   {
      List<TextVSAssembly> targets = Arrays.stream(rvs.getViewsheet().getAssemblies(true))
         .filter(TextVSAssembly.class::isInstance)
         .map(TextVSAssembly.class::cast)
         .filter(a -> ((TextVSAssemblyInfo) a.getVSAssemblyInfo()).isUrl())
         .collect(Collectors.toList());

      if(!targets.isEmpty()) {
         try {
            UpdateExternalUrlCommand command = new UpdateExternalUrlCommand();
            command.setName(entry.getAbsoluteName());
            command.setUrl(
               linkUri + "api/vs/external?vs=" + URLEncoder.encode(rvs.getID(), "UTF-8") +
               "&assembly=" + URLEncoder.encode(entry.getAbsoluteName(), "UTF-8"));

            for(TextVSAssembly target : targets) {
               dispatcher.sendCommand(target.getAbsoluteName(), command);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to send update external URL command", e);
         }
      }
   }

   /**
    * Dispatch event to trigger ViewsheetScope script execution.
    * @param event event provide event source, type and other properties.
    */
   public void dispatchEvent(ScriptEvent event, CommandDispatcher dispatcher, RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = box.getViewsheet();
      ViewsheetScope vsscope = box.getScope();
      vsscope.addVariable("event" , event);
      String onload = vs.getViewsheetInfo().getOnLoad();

      if(vs.getViewsheetInfo().isScriptEnabled() &&
         onload != null && onload.trim().length() > 0)
      {
         try {
            vsscope.execute(onload, ViewsheetScope.VIEWSHEET_SCRIPTABLE);
         }
         catch(Exception ex) {
            if(LOG.isDebugEnabled()) {
               LOG.debug("Failed to execute viewsheet script", ex);
            }
            else {
               LOG.warn("Failed to execute viewsheet script: {}", ex.getMessage());
            }

            CoreTool.addUserMessage(ex.getMessage());
         }

         Set<AssemblyRef> refs = new HashSet<>();
         VSUtil.getReferencedAssets(onload, refs, vs, null);

         // refresh modified components
         for(AssemblyRef ref : refs) {
            String name = ref.getEntry().getName();
            boolean contained = vs.containsAssembly(name);

            if(contained) {
               VSAssembly assembly = vs.getAssembly(name);

               try {
                  parameterService.refreshTextParameters(rvs, assembly);
                  RefreshVSObjectCommand command = new RefreshVSObjectCommand();
                  command.setInfo(objectModelService.createModel(assembly, rvs));
                  command.setWizardTemporary(assembly.isWizardTemporary());
                  dispatcher.sendCommand(command);
               }
               catch(Exception exception) {
                  LOG.warn(
                              "Unable to to trigger script execution", exception);
               }
            }
         }
      }

      vsscope.removeVariable("event");
   }

   private boolean shouldApplyTheSelection(boolean canApplySelectFirst, VSAssembly assembly) {
      boolean openVS = Boolean.TRUE.equals(VSUtil.OPEN_VIEWSHEET.get());

      if(assembly == null || !canApplySelectFirst || !openVS) {
         return false;
      }

      if(assembly.getVSAssemblyInfo() instanceof SelectionVSAssemblyInfo) {
         if(!((SelectionVSAssemblyInfo) assembly.getVSAssemblyInfo()).isSelectFirstItem()) {
            return false;
         }
      }

      if(assembly instanceof SelectionListVSAssembly) {
         SelectionListVSAssembly selectionList = (SelectionListVSAssembly) assembly;
         SelectionListVSAssemblyInfo selectionListInfo = selectionList.getSelectionListInfo();

         return (selectionList.getStateSelectionList() == null ||
            selectionList.getStateSelectionList().getSelectionValueCount() == 0) &&
            selectionListInfo.getSelectionList() != null &&
            selectionListInfo.getSelectionList().getSelectionValueCount() > 0;
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         SelectionTreeVSAssembly selectionTree = (SelectionTreeVSAssembly) assembly;
         SelectionTreeVSAssemblyInfo selectionTreeInfo = selectionTree.getSelectionTreeInfo();

         return (selectionTree.getStateSelectionList() == null ||
            selectionTree.getStateSelectionList().getSelectionValueCount() == 0) &&
            selectionTreeInfo.getCompositeSelectionValue() != null &&
            selectionTreeInfo.getCompositeSelectionValue().getSelectionList() != null &&
            selectionTreeInfo.getCompositeSelectionValue().getSelectionList().getSelectionValueCount() > 0;
      }

      return false;
   }

   private final VSObjectModelFactoryService objectModelService;
   private final ViewsheetService viewsheetService;
   private final VSLayoutService vsLayoutService;
   private final ParameterService parameterService;
   private static final Logger LOG = LoggerFactory.getLogger(CoreLifecycleService.class);

   /**
    * Comparator for sorting tabs before other assemblies but after annotations
    */
   protected static final class TabAnnotationComparator extends AnnotationComparator {
      TabAnnotationComparator() {
         super(true);
         this.asc = true;
      }

      @Override
      public int compare(Assembly a, Assembly b) {
         int comp = super.compare(a, b);

         if(comp == 0 && a != null && b != null) {
            AssemblyInfo ainfo = a.getInfo();
            AssemblyInfo binfo = b.getInfo();

            if(ainfo instanceof TabVSAssemblyInfo) {
               comp = asc ? -1 : 1;
            }
            else if(binfo instanceof TabVSAssemblyInfo) {
               comp = asc ? 1 : -1;
            }
         }

         return comp;
      }

      private boolean asc;
   }

   private class ReadyListener
      implements ChangedAssemblyList.ReadyListener
   {
      ReadyListener(ChangedAssemblyList clist, CommandDispatcher dispatcher,
                    RuntimeViewsheet rvs, String uri)
      {
         this(clist, 0, 0, dispatcher, rvs, uri);
      }
      /**
       * Create a ready listener.
       */
      ReadyListener(ChangedAssemblyList clist, int width, int height,
                    CommandDispatcher dispatcher, RuntimeViewsheet rvs, String uri)
      {
         super();
         this.clist = clist;
         this.width = width;
         this.height = height;
         this.dispatcher = dispatcher;
         this.rvs = rvs;
         this.uri = uri;
      }

      /**
       * Get the changed assembly list.
       * @return the changed assembly list.
       */
      public ChangedAssemblyList getList() {
         return clist;
      }

      /**
       * Get the asset command.
       * @return the asset command.
       */
      public CommandDispatcher getDispatcher() {
         return dispatcher;
      }

      /**
       * Set the runtime sheet.
       * @param rs the specified runtime sheet.
       */
      @Override
      public void setRuntimeSheet(RuntimeSheet rs) {
         this.rvs = (RuntimeViewsheet) rs;
      }

      /**
       * Get the runtime sheet.
       * @return the runtime sheet if any, <tt>null</tt> otherwise.
       */
      @Override
      public RuntimeSheet getRuntimeSheet() {
         return this.rvs;
      }

      /**
       * Set whether to initialize grid.
       * @param grid <tt>true</tt> to initialize grid, <tt>false</tt> otherwise.
       */
      @Override
      public void setInitingGrid(boolean grid) {
         this.grid = grid;
      }

      /**
       * Check if should initing grid.
       * @return <tt>true</tt> if should initing grid, <tt>false</tt> otherwise.
       */
      @Override
      public boolean isInitingGrid() {
         return grid;
      }

      /**
       * Get the viewsheet id.
       * @return the viewsheet id of the list.
       */
      @Override
      public String getID() {
         return id;
      }

      /**
       * Set the viewsheet id to the list.
       * @param id the specified viewsheet id.
       */
      @Override
      public void setID(String id) {
         this.id = id;
      }

      /**
       * Triggered when more assembly gets ready.
       */
      @Override
      public void onReady() throws Exception {
         try {
            onReady0();
         }
         catch(Exception ex) {
            // ignore if the runtime viewsheet has been disposed
            if(rvs != null && rvs.getViewsheet() != null) {
               throw ex;
            }
            else {
               LOG.debug("Runtime viewsheet disposed", ex);
            }
         }
      }

      private void onReady0() throws Exception {
         if(!inited) {
            Viewsheet sheet = rvs.getViewsheet();

            if(grid && sheet != null) {
               sheet.setLastSize(sheet.getPixelSize());
               InitGridCommand command = new InitGridCommand();
               command.setViewsheetId(id);
               command.setEntry(rvs.getEntry());
               command.setIniting(false);
               command.setEditable(rvs.isEditable());
               command.setLockOwner(rvs.getLockOwner());
               command.setViewSize(new Dimension(width, height));
               command.setHasSharedFilters(!sheet.getViewsheetInfo().getFilterIDs().isEmpty());
               dispatcher.sendCommand(command);
            }

            inited = true;
         }

         List<AssemblyEntry> ready = clist.getReadyList();
         List<AssemblyEntry> processed = clist.getProcessedList();
         List<AssemblyEntry> pending = clist.getPendingList();

         while(!ready.isEmpty()) {
            AssemblyEntry entry = ready.remove(0);
            process(entry);

            if(!processed.contains(entry)) {
               processed.add(entry);
            }

            pending.remove(entry);
         }

         while(!pending.isEmpty()) {
            AssemblyEntry entry = pending.remove(0);
            String vname = entry.getAbsoluteName();
            Viewsheet vs = rvs.getViewsheet();
            VSAssembly assembly = (VSAssembly) vs.getAssembly(vname);
            // @by: ChrisSpagnoli bug1412261632374 #2 2014-10-10
            // It is possible for client to send VSLayoutEvent with an assembly
            // which has since been deleted.  So, check for null before proceeding.
            if(assembly != null) {
               addDeleteVSObject(rvs, assembly, dispatcher);
            }
         }
      }

      /**
       * Process an assembly.
       * @param entry the specified assembly.
       */
      private void process(AssemblyEntry entry) throws Exception {
         if(rvs == null || rvs.isDisposed()) {
            return;
         }

         Viewsheet vs = rvs.getViewsheet();
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         String vname = entry.getAbsoluteName();

         box.lockRead();

         try {
            VSAssembly assembly = (VSAssembly) vs.getAssembly(vname);

            if(assembly == null) {
               return;
            }

            boolean canApplySelectFirst = false;

            if(assembly.getVSAssemblyInfo() instanceof SelectionVSAssemblyInfo) {
               if(!((SelectionVSAssemblyInfo) assembly.getVSAssemblyInfo()).isSelectFirstItem()) {
                  canApplySelectFirst = true;
               }
            }

            box.executeView(entry.getAbsoluteName(), false);

            // execute view will execute the selection script,
            // process selected states change after set select the first item.
            if(shouldApplyTheSelection(canApplySelectFirst, assembly)) {
               ChangedAssemblyList list = createList(true, dispatcher, rvs, uri);
               box.processChange(assembly.getAbsoluteName(), VSAssembly.OUTPUT_DATA_CHANGED, list);
               execute(rvs, assembly.getName(), uri, list, dispatcher, false);
            }

            // @by billh, performance optimization, make sure that
            // AddVSObjectCommand and RefreshVSObjectCommand are bundled
            // together, so that RefreshVSObjectCommand could be removed
            // when compacting this AssetCommand to reduce traffic load
            refreshData(rvs, dispatcher, entry, uri);
            addDeleteVSObject(rvs, assembly, dispatcher);
            loadTableLens(rvs, vname, uri, dispatcher);
            updateExternalUrl(rvs, dispatcher, entry, uri);
         }
         finally {
            box.unlockRead();
         }
      }

      private ChangedAssemblyList clist;
      private CommandDispatcher dispatcher;
      private RuntimeViewsheet rvs;
      private boolean grid;
      private boolean inited;
      private String id;
      private String uri;
      private int width = 0;
      private int height = 0;
   }
}
