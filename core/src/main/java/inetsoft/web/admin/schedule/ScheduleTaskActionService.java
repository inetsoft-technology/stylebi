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
package inetsoft.web.admin.schedule;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.cluster.*;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.content.repository.RepletRegistryManager;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ClusterProxy
public class ScheduleTaskActionService {
   @Autowired
   public ScheduleTaskActionService(AnalyticRepository analyticRepository,
                                    ScheduleManager scheduleManager,
                                    ScheduleService scheduleService,
                                    ViewsheetService viewsheetService, XRepository xRepository,
                                    SecurityEngine securityEngine)
   {
      this.analyticRepository = analyticRepository;
      this.scheduleManager = scheduleManager;
      this.scheduleService = scheduleService;
      this.viewsheetService = viewsheetService;
      this.xRepository = xRepository;
      this.securityEngine = securityEngine;
   }

   public ScheduleActionModel getTaskAction(String taskName, int index,
                                            Principal principal, boolean em)
      throws Exception
   {
      taskName = scheduleService.getTaskName(Tool.byteDecode(taskName), principal);
      Catalog catalog = Catalog.getCatalog(principal);
      ScheduleAction action = null;

      if(index < 0) {
         throw new Exception(catalog.getString(
            "em.scheduler.invalidConditionIndex"));
      }

      if(taskName == null || "".equals(taskName)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         throw new Exception(catalog.getString(
            "em.scheduler.taskNotFound", taskName));
      }

      if(action == null) {
         action = task.getAction(index);
      }

      return scheduleService.getActionModel(action, principal, em);
   }

   public void deleteTaskActions(String taskName, String taskOwner, int[] items, Principal principal)
      throws Exception
   {
      taskName = Tool.byteDecode(taskName);
      taskName = taskName.startsWith(Tool.byteDecode(taskOwner) + IdentityID.KEY_DELIMITER) ? taskName :
         scheduleService.getTaskName(taskName, principal);
      Catalog catalog = Catalog.getCatalog(principal);

      if(taskName == null || "".equals(taskName)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         throw new Exception(catalog.getString(
            "em.scheduler.taskNotFound", taskName));
      }

      for(int i = 0; i < items.length; i++) {
         int index = items[i];
         task.removeAction(index);
      }

      scheduleService.saveTask(taskName, task, principal);
   }

   public ScheduleActionModel[] saveTaskAction(String taskName, String oldTaskName, IdentityID owner,
                                               int index, ScheduleActionModel model, String linkURI,
                                               Principal principal, boolean em)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);

      if(taskName == null || "".equals(taskName)) {
         throw new Exception(catalog.getString("em.scheduler.emptyTaskName"));
      }

      taskName = scheduleService.updateTaskName(oldTaskName, taskName, owner, principal);
      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task == null) {
         throw new Exception(catalog.getString(
            "em.scheduler.taskNotFound", taskName));
      }

      ScheduleAction action = scheduleService.getActionFromModel(model, principal, linkURI);

      if(index >= task.getActionCount()) {
         task.addAction(action);
      }
      else {
         task.setAction(index, action);
      }

      scheduleService.saveTask(taskName, task, principal);

      return task.getActionStream()
         .map(a -> scheduleService.getActionModel(a, principal, em))
         .filter(Objects::nonNull)
         .toArray(ScheduleActionModel[]::new);
   }

   public List<VSBookmarkInfoModel> getBookmarks(String id, boolean em, Principal principal) {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      VSBookmarkInfo[] bookmarks = VSUtil.getBookmarks(id, pId);

      return Arrays.stream(bookmarks)
         .map(bookmark -> scheduleService.getBookmarkModel(bookmark, em))
         .collect(Collectors.toList());
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean hasPrintLayout(@ClusterProxyKey String runtimeId, Principal principal) throws Exception {
      boolean result = false;
      ViewsheetService engine = viewsheetService;

      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);

      if(rvs != null && rvs.getViewsheet() != null && rvs.getViewsheet().getLayoutInfo() != null) {
         result = rvs.getViewsheet().getLayoutInfo().getPrintLayout() != null;
      }

      return result;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public List<ScheduleAlertModel> getViewsheetHighlights(@ClusterProxyKey String runtimeId, Principal principal)
      throws Exception
   {
      ViewsheetService engine = viewsheetService;
      RuntimeViewsheet rvs = engine.getViewsheet(runtimeId, principal);
      Map<String, ScheduleAlertModel> alerts = new HashMap<>();

      for(Assembly assembly : rvs.getViewsheet().getAssemblies(true)) {
         if((assembly instanceof TextVSAssembly) ||
            (assembly instanceof ImageVSAssembly))
         {
            OutputVSAssemblyInfo info =
               (OutputVSAssemblyInfo) assembly.getInfo();
            HighlightGroup group = info.getHighlightGroup();
            addHighlights(alerts, assembly.getAbsoluteName(), group);
         }
         else if(assembly.getAssemblyType() == Viewsheet.TABLE_VIEW_ASSET) {
            TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getInfo();
            TableHighlightAttr attr = info.getHighlightAttr();

            if(attr != null) {
               Enumeration<?> e = attr.getAllHighlights();

               while(e.hasMoreElements()) {
                  HighlightGroup group = (HighlightGroup) e.nextElement();
                  addHighlights(alerts, assembly.getAbsoluteName(), group);
               }
            }
         }
         else if(assembly.getAssemblyType() == Viewsheet.CROSSTAB_ASSET) {
            CrosstabVSAssemblyInfo info =
               (CrosstabVSAssemblyInfo) assembly.getInfo();
            TableHighlightAttr attr = info.getHighlightAttr();

            if(attr != null) {
               Enumeration<?> e = attr.getAllHighlights();

               while(e.hasMoreElements()) {
                  HighlightGroup group = (HighlightGroup) e.nextElement();
                  addHighlights(alerts, assembly.getAbsoluteName(), group);
               }
            }
         }
         else if(assembly.getAssemblyType() == Viewsheet.FORMULA_TABLE_ASSET) {
            CalcTableVSAssemblyInfo info =
               (CalcTableVSAssemblyInfo) assembly.getInfo();
            TableHighlightAttr attr = info.getHighlightAttr();

            if(attr != null) {
               Enumeration<?> e = attr.getAllHighlights();

               while(e.hasMoreElements()) {
                  HighlightGroup group = (HighlightGroup) e.nextElement();
                  addHighlights(alerts, assembly.getAbsoluteName(), group);
               }
            }
         }
         else if(assembly.getAssemblyType() == Viewsheet.CHART_ASSET) {
            ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) assembly.getInfo();
            VSChartInfo chartInfo = info.getVSChartInfo();
            VSDataRef[] refs = chartInfo.getFields();
            VSDataRef[] runtimeDateComparisonRefs = chartInfo.getRuntimeDateComparisonRefs();
            refs = (VSDataRef[]) ArrayUtils.addAll(refs, runtimeDateComparisonRefs);

            for(VSDataRef dataRef : refs) {
               if(dataRef instanceof HighlightRef) {
                  ((HighlightRef) dataRef).highlights()
                     .forEach(hg -> addHighlights(alerts, assembly.getAbsoluteName(), hg));
               }
            }
         }
         else {
            VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();

            if(info instanceof RangeOutputVSAssemblyInfo) {
               RangeOutputVSAssemblyInfo range = (RangeOutputVSAssemblyInfo) info;
               Catalog catalog = Catalog.getCatalog(principal);

               for(int i = 1; i <= range.getRangeValues().length; i++) {
                  if(range.getRangeValues()[i - 1] != null) {
                     String name = "RangeOutput_Range_" + i;
                     String key = assembly.getAbsoluteName() + ":" + name;
                     String condition =
                        catalog.getString("to") + "  " + range.getRangeValues()[i - 1];
                     ScheduleAlertModel alert = ScheduleAlertModel.builder()
                        .element(assembly.getAbsoluteName())
                        .highlight(name)
                        .condition(condition)
                        .count(1)
                        .build();
                     alerts.put(key, alert);
                  }
               }
            }
         }
      }

      return alerts.values().stream()
         .sorted(Comparator.comparing(ScheduleAlertModel::element)
                    .thenComparing(ScheduleAlertModel::highlight))
         .collect(Collectors.toList());
   }

   /**
    * Adds highlight to the map.
    */
   private void addHighlights(Map<String, ScheduleAlertModel> alerts,
                              String element, HighlightGroup highlights)
   {
      if(highlights != null && !highlights.isEmpty()) {
         for(String level : highlights.getLevels()) {
            for(String name : highlights.getNames(level)) {
               Highlight highlight = highlights.getHighlight(level, name);

               if(!highlight.getConditionGroup().isEmpty()) {
                  String key = element + ":" + highlight.getName();
                  ScheduleAlertModel.Builder builder = ScheduleAlertModel.builder();
                  ScheduleAlertModel oldAlert = alerts.get(key);

                  if(oldAlert == null) {
                     builder
                        .element(element)
                        .highlight(highlight.getName())
                        .condition(highlight.getConditionGroup().toString())
                        .count(1);
                  }
                  else {
                     String cond = oldAlert.condition();

                     if(oldAlert.count() == 1) {
                        cond = "[" + cond + "]";
                     }

                     builder
                        .element(element)
                        .highlight(highlight.getName())
                        .count(oldAlert.count() + 1)
                        .condition(
                           cond + "\n or \n[" + highlight.getConditionGroup().toString() + "]");
                  }

                  alerts.put(key, builder.build());
               }
            }
         }
      }
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public List<String> getViewsheetParameters(@ClusterProxyKey String runtimeId, Principal principal)
      throws Exception
   {

      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      List<UserVariable> vars = new ArrayList<>();
      addSchedulerOnlyParameters(vars, rvs);
      VSEventUtil.refreshParameters(viewsheetService, rvs.getViewsheetSandbox(),
                                    rvs.getViewsheet(), false, null, vars);
      addSchedulerOnlyParameters(vars, rvs);
      List<String> parameters = new ArrayList<>();

      for(UserVariable var : vars) {
         parameters.add(var.getName());
      }

      return parameters;
   }

   private void addSchedulerOnlyParameters(List list, RuntimeViewsheet rvs) throws Exception {
      ViewsheetSandbox vbox = rvs.getViewsheetSandbox();
      String vsName = vbox.getSheetName();
      AssetQuerySandbox box = vbox.getAssetQuerySandbox();
      VariableTable vart = box.getVariableTable();

      UserVariable[] vars = AssetEventUtil.executeVariables(
         viewsheetService, box, vart, null, vsName, null, null, false);

      if(!"true".equals(vart.get("disableParameterSheet"))) {
         OUTER:
         for(UserVariable userVar : vars) {
            if(!SUtil.isNeedPrompt(box.getUser(), userVar)) {
               continue;
            }

            String varName = userVar.getName();

            for(Object item : list) {
               UserVariable var = (UserVariable) item;

               // avoid duplicated variables
               if(var != null && var.getName().equals(varName)) {
                  continue OUTER;
               }
            }

            list.add(userVar);
         }
      }
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public List<String> getViewsheetTableDataAssemblies(@ClusterProxyKey String runtimeId, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      List<String> tableDataAssemblies = new ArrayList<>();

      if(rvs != null && rvs.getViewsheet() != null) {
         VSUtil.getTableDataAssemblies(rvs.getViewsheet(), true)
            .stream().forEach(assembly -> {
               if(CSVUtil.needExport(assembly)) {
                  tableDataAssemblies.add(assembly.getAbsoluteName());
               }
         });
      }

      return tableDataAssemblies;
   }

   public ViewsheetTreeListModel getViewsheetTree(Principal user) throws Exception {
      ViewsheetTreeListModel.Builder builder = ViewsheetTreeListModel.builder();
      LinkedHashMap<AssetEntry, ModifiableViewsheetTreeModel> tree = new LinkedHashMap<>();
      Set<String> keys = IndexedStorage.getIndexedStorage().getKeys(this::isViewsheetTreeEntry);
      boolean myReportsAvailable = user != null &&
         securityEngine.checkPermission(user, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ);
      List<AssetEntry> entries = new ArrayList<>();
      entries.addAll(keys.stream()
         .map(AssetEntry::createAssetEntry)
         .filter(Objects::nonNull)
         .filter(AssetEntry::isViewsheet)
         .filter(e -> checkViewsheetPermission(e, user))
         .sorted(Comparator.comparing((AssetEntry e) ->
            !e.toIdentifier().startsWith(Tool.toString(AssetRepository.USER_SCOPE)))
            .thenComparing(AssetEntry::compareTo))
         .collect(Collectors.toList()));

      HashMap<String, AssetEntry[]> subEntriesMap = new HashMap<>();
      entries.forEach(e -> tree.put(e, buildTree(e, tree, myReportsAvailable, user, subEntriesMap)));

      tree.entrySet().stream()
         .filter(e -> e.getKey().isRoot())
         .map(Map.Entry::getValue)
         .flatMap(m -> m.children().stream())
         .map(this::getImmutableTree)
         .forEach(builder::addNodes);

      return builder.build();
   }

   public Map<String, String> getViewsheets(Principal user) throws Exception {
      Map<String, String> viewsheetMap = new HashMap<>();
      AssetEntry[] viewsheets = scheduleService.getViewsheets(user);

      for(AssetEntry entry : viewsheets) {
         String path = entry.getPath();
         path =
            entry.getScope() == AssetRepository.USER_SCOPE ? SUtil.MY_REPORT + "/" + path : path;

         final String viewsheetId = entry.toIdentifier();
         final String viewsheetPath = SUtil.localize(path, user, true, entry);
         viewsheetMap.put(viewsheetId, viewsheetPath);
      }

      return viewsheetMap;
   }

   public String getRepletAlias(String sheet, Principal principal) {
      return scheduleService.getRepletAlias(sheet, principal);
   }

   private boolean isViewsheetTreeEntry(String key) {
      if(key == null || key.contains(RecycleUtils.RECYCLE_BIN_FOLDER)) {
         return false;
      }

      AssetEntry entry = AssetEntry.createAssetEntry(key);
      return entry != null && (entry.isViewsheet() || entry.isRepositoryFolder());
   }

   private boolean checkViewsheetPermission(AssetEntry entry, Principal principal) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.REPORT, entry.getPath(), ResourceAction.READ);
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check viewsheet permission", e);
         return false;
      }
   }

   private boolean checkQueryPermission(String query, Principal principal) {
      try {
         return securityEngine.checkPermission(
            principal, ResourceType.QUERY, query, ResourceAction.READ);
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check query permission", e);
         return false;
      }
   }

   private ModifiableViewsheetTreeModel buildTree(AssetEntry entry,
                                                  LinkedHashMap<AssetEntry, ModifiableViewsheetTreeModel> tree,
                                                  boolean myReportsAvailable,
                                                  Principal user,
                                                  HashMap<String, AssetEntry[]> subEntriesMap)
   {
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());
      ModifiableViewsheetTreeModel model = ModifiableViewsheetTreeModel.create();
      AssetEntry parent = entry.getParent();
      model.setId(entry.toIdentifier());
      model.setFolder(entry.isRepositoryFolder());
      model.setLabel(Tool.isEmptyString(getAlias(entry, user, subEntriesMap)) ? entry.getName() :
         getAlias(entry, user, subEntriesMap));

      // create right parent node for the direct children of my report node.
      if(parent != null && parent.isRoot() && myReportsAvailable &&
         entry.getScope() == AssetRepository.USER_SCOPE &&
         !Tool.equals(entry.getPath(), Tool.MY_DASHBOARD))
      {
         boolean ownerViewsheet = entry.getUser().equals(pId);
         parent = !ownerViewsheet ? null : new AssetEntry(AssetRepository.USER_SCOPE,
            AssetEntry.Type.REPOSITORY_FOLDER, Tool.MY_DASHBOARD, pId);
      }

      if(parent != null) {
         if(!tree.containsKey(parent)) {
            tree.put(parent, buildTree(parent, tree, myReportsAvailable, user, subEntriesMap));
         }

         tree.get(parent).addChildren(model);
      }

      return model;
   }

   private String getAlias(AssetEntry entry, Principal principal,
                           HashMap<String, AssetEntry[]> subEntriesMap)
   {
      String alias = null;

      if(entry.getType() == AssetEntry.Type.VIEWSHEET) {
         try {
            entry = getAssetEntry(entry.toIdentifier(), principal, subEntriesMap);
         }
         catch(Exception e) {
            throw new RuntimeException(
               "Failed to get registry entry '" + entry.toIdentifier() + "' for " + principal, e);
         }

         if(entry != null) {
            alias = entry.getAlias();
         }
      }
      else if(entry.getType() == AssetEntry.Type.REPOSITORY_FOLDER) {
         try {
            alias = RepletRegistry.getRegistry(entry.getUser()).getFolderAlias(entry.getPath());
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to get replet registry", e);
         }
      }

      return alias;
   }

   /**
    * Gets the entry for the specified asset.
    *
    * @param identifier the asset identifier.
    * @param user       the name of the user requesting the asset.
    * @param subEntriesMap  key -> parent identifier, value -> sub entries, use this to avoid
    *                       load same folder again and again.
    *
    * @return the asset entry.
    *
    * @throws Exception if the asset entry could not be obtained.
    */
   public AssetEntry getAssetEntry(String identifier, Principal user,
                                   HashMap<String, AssetEntry[]> subEntriesMap)
      throws Exception
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);

      if(entry == null) {
         return null;
      }

      AssetEntry parent = entry.getParent();
      String key = parent + "_" + user;
      AssetEntry[] entries = null;

      if(subEntriesMap.containsKey(key)) {
         entries = subEntriesMap.get(key);
      }

      if(entries == null) {
         AssetEntry.Selector selector = new AssetEntry.Selector(
            AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET,
            AssetEntry.Type.VIEWSHEET, AssetEntry.Type.DATA,
            AssetEntry.Type.PHYSICAL, AssetEntry.Type.VIEWSHEET_SNAPSHOT);
         entries = repository.getEntries(parent, user, ResourceAction.ADMIN, selector);
         subEntriesMap.put(key, entries);
      }

      entry = null;

      for(AssetEntry e : entries) {
         if(identifier.equals(e.toIdentifier())) {
            entry = e;
            break;
         }
      }

      return entry;
   }

   private ViewsheetTreeModel getImmutableTree(ViewsheetTreeModel model) {
      ViewsheetTreeModel.Builder builder = ViewsheetTreeModel.builder();
      builder.id(model.id());
      builder.label(model.label());
      builder.folder(model.folder());

      model.children().stream()
         .map(this::getImmutableTree)
         .forEach(builder::addChildren);

      return builder.build();
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Void closeViewsheet(@ClusterProxyKey String id, Principal user) throws Exception {
      viewsheetService.closeViewsheet(id, user);
      return null;
   }

   private final AnalyticRepository analyticRepository;
   private final ScheduleManager scheduleManager;
   private final ScheduleService scheduleService;
   private final ViewsheetService viewsheetService;
   private final XRepository xRepository;
   private final SecurityEngine securityEngine;
   private final RepletRegistryManager registryManager = new RepletRegistryManager();

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskActionService.class);
}
