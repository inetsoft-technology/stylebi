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
package inetsoft.web.admin.content.repository;

import inetsoft.mv.*;
import inetsoft.mv.data.MV;
import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.*;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.Identity;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.model.NameLabelTuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.web.RecycleUtils.RECYCLE_BIN_FOLDER;

@Service
public class MVService {
   @Autowired
   public MVService(ContentRepositoryTreeService treeService, MVSupportService support) {
      this.treeService = treeService;
      this.support = support;
   }

   public MVSupportService.AnalysisResult analyze(AnalyzeMVRequest analyzeMVRequest,
                                                  Principal principal)
      throws Exception
   {
      List<ContentRepositoryTreeNode> nodesToAnalyze = analyzeMVRequest.nodes();

      // ignore ws entries from analyzing when ws mv is disabled
      if(!SreeEnv.getBooleanProperty("ws.mv.enabled")) {
         nodesToAnalyze = nodesToAnalyze.stream()
            .filter(node -> !toAssetEntry(node).isWorksheet())
            .collect(Collectors.toList());
      }

      List<String> paths = nodesToAnalyze.stream()
         .map(ContentRepositoryTreeNode::path)
         .map(treeService::getUnscopedPath)
         .collect(Collectors.toList());
      List<String> identifiers = nodesToAnalyze.stream()
         .map(node -> toAssetEntry(node).toIdentifier()).collect(Collectors.toList());

      return support.analyze(
         identifiers, paths, analyzeMVRequest.expanded(),
         analyzeMVRequest.bypass(), analyzeMVRequest.full(), principal, false);
   }

   public List<MaterializedModel> getMaterializedModel(List<MVSupportService.MVStatus> mvstatus0) {
      return getMaterializedModel(mvstatus0, false, false);
   }

   public List<MaterializedModel> getMaterializedModel(List<MVSupportService.MVStatus> mvstatus0,
                                                       boolean hideData, boolean hideExist)
   {
      List<MVSupportService.MVStatus> mvstatus = new ArrayList<>();

      for(MVSupportService.MVStatus status : mvstatus0) {
         if((!status.isExists() || !hideExist) &&
            (!status.isDataPresent() || !hideData)) {
            mvstatus.add(status);
         }
      }

      Catalog catalog = Catalog.getCatalog();
      List<MaterializedModel> modelList = new ArrayList<>();

      for(MVSupportService.MVStatus status : mvstatus) {
         MVDef def = status.getDefinition();
         MVMetaData data = def.getMetaData();
         String name = def.getName();
         String table = getTableName(def, data);

         StringBuilder buffer = new StringBuilder();

         if(Arrays.stream(data.getRegisteredSheets())
            .map(sheetId -> {
               AssetEntry entry = AssetEntry.createAssetEntry(sheetId);
               return entry == null ? "" : entry.getPath();
            })
            .anyMatch(sheetPath -> sheetPath.startsWith(RECYCLE_BIN_FOLDER)))
         {
            continue;
         }

         if(def.getUsers() != null) {
            for(Identity identity : def.getUsers()) {
               buffer.append(catalog.getString(
                  identity.getType() == Identity.GROUP ? "Group" :
                     identity.getType() == Identity.ROLE ? "Role" : "User"))
                  .append(":").append(identity.getName()).append(" ");
            }
         }

         String users = buffer.toString();
         boolean exists = status.isExists();
         boolean hasData = status.isDataPresent();
         long lm = def.getLastUpdateTime();
         String lastModifiedTime = lm <= 0 ? "" : Tool.getDateTimeFormat().format(new Date(lm));
         String cycle = def.getCycle();

         modelList.add(MaterializedModel.builder()
                          .name(name)
                          .sheets(getSheetNames(def))
                          .table(table)
                          .users(users)
                          .exists(exists)
                          .hasData(hasData)
                          .lastModifiedTime(lastModifiedTime)
                          .lastModifiedTimestamp(lm)
                          .cycle(cycle == null ? "" : cycle)
                          .build());
      }

      return modelList;
   }

   /**
    * process the optimize plan for specified mvs
    */
   public StringBuffer processPlan(List<String> mvnames, MVSupportService.AnalysisResult jobs,
                                   List<MVSupportService.MVStatus> mvstatus)
   {
      StringBuffer info = new StringBuffer();

      if(jobs == null) {
         return info;
      }

      Set<String> sheetIds = new LinkedHashSet<>();

      if(mvnames != null && !mvnames.isEmpty()) {
         for(String mv : mvnames) {
            for(MVSupportService.MVStatus status : mvstatus) {
               MVDef mvDef = status.getDefinition();
               String mvname = mvDef.getName();

               if(mvname.equals(mv)) {
                  String[] ids = mvDef.getMetaData().getRegisteredSheets();
                  sheetIds.addAll(Arrays.asList(ids));
               }
            }
         }
      }

      List<String> identifiers = jobs.getIdentifiers();
      Map<String, StringBuffer> plans = jobs.getPlans();
      int idx = 1;

      for(String id : identifiers) {
         if(!sheetIds.isEmpty() && !sheetIds.contains(id)) {
            continue;
         }

         StringBuffer plan = plans.get(id);

         if(plan != null) {
            String vs = AssetEntry.createAssetEntry(id).getPath();

            if(idx != 1) {
               info.append("\n\n\n");
            }

            info.append(idx).append(". ").append(vs).append("\n");
            idx++;
            info.append(plan);
         }
      }

      return info;
   }

   public AnalyzeMVResponse checkAnalyzeStatus(MVSupportService.AnalysisResult jobs,
                                               Principal principal)
      throws Exception
   {
      boolean completed = true;
      boolean exception = false;
      List<MaterializedModel> status = null;

      boolean onDemand = "true".equals(SreeEnv.getProperty("mv.ondemand"));
      boolean runInBackground = "true".equals(SreeEnv.getProperty("mv.run.background"));
      String defaultCycle = MVManager.getManager().getDefaultCycle();
      defaultCycle = defaultCycle == null ? "" : defaultCycle;
      List<NameLabelTuple> cycles = getDataCycles(principal);

      if(!jobs.isCompleted()) {
         completed = false;
      }
      else if(!jobs.getExceptions().isEmpty()) {
         exception = true;
      }

      if(jobs.isCompleted()) {
         status = getMaterializedModel(jobs.getStatus());
      }

      return AnalyzeMVResponse.builder()
         .completed(completed)
         .exception(exception)
         .status(status)
         .cycles(cycles)
         .onDemand(onDemand)
         .defaultCycle(defaultCycle)
         .runInBackground(runInBackground)
         .build();
   }

   public MVManagementModel getMVInfo(List<String> ids, Principal principal) throws Exception {
      Catalog catalog = Catalog.getCatalog(principal);

      if(FSService.getServer() == null) {
         throw new RuntimeException(catalog.getString("em.mv.notDataServer"));
      }

      MVManager manager = MVManager.getManager();
      boolean isShowAges = "true".equals(SreeEnv.getProperty("mvmanager.dates.ages", "false"));
      List<MaterializedModel> mvs = new ArrayList<>();
      List<NameLabelTuple> dataCycles = getDataCycles(principal);
      List<MVDef> defs = new ArrayList<>();

      if(ids != null) {
         for(MVDef def : manager.list(true, null, principal)) {
            for(String sheetId : ids) {
               if(def.getMetaData().isRegistered(sheetId)) {
                  defs.add(def);
                  break;
               }
            }
         }
      }
      else {
         defs = Arrays.asList(manager.list(true, null, principal));
      }

      for(MVDef def : defs) {
         String sheets = getSheetNames(def);

         if(sheets.length() != 0) {
            mvs.add(
               MaterializedModel.builder()
                  .name(def.getName())
                  .sheets(sheets)
                  .table(getTableName(def, def.getMetaData()))
                  .users(getUsers(def))
                  .exists(true)
                  .hasData(def.hasData())
                  .lastModifiedTime(getLastModifiedTime(def, isShowAges))
                  .lastModifiedTimestamp(def.getLastUpdateTime())
                  .cycle(def.getCycle() == null ? "" : def.getCycle())
                  .status(getStatus(def))
                  .incremental(def.isIncremental())
                  .size(getSize(def))
                  .valid(def.getMetaData().isValid())
                  .build()
            );
         }
      }

      boolean runInBackground = "true".equals(SreeEnv.getProperty("mv.run.background"));

      return MVManagementModel.builder()
         .mvs(mvs)
         .dataCycles(dataCycles)
         .showDateAsAges(isShowAges)
         .runInBackground(runInBackground)
         .build();
   }

   private String getSheetNames(MVDef def) {
      MVMetaData data = def.getMetaData();
      return Arrays.stream(data.getRegisteredSheets())
         .map(id -> AssetEntry.createAssetEntry(id).getPath())
         .filter(path -> (!path.startsWith(RECYCLE_BIN_FOLDER)))
         .collect(Collectors.joining(","));
   }

   /**
    * clean up name if bound directly to a query/lm
    */
   private String getTableName(MVDef def, MVMetaData data) {
      String table = def.isWSMV() ? getTableNames(data) : data.getWsPath() + "/" + data.getBoundTable();
      String wsId = data.getWsId();

      if(wsId != null) {
         AssetEntry entry = AssetEntry.createAssetEntry(wsId);

         if(entry != null && (entry.isQuery() || entry.isLogicModel() || entry.isPhysicalTable())) {
            if(def.isAssociationMV()) {
               table = entry.getParentPath() + "/" + data.getBoundTable();
            }
            else {
               table = data.getWsPath();
            }
         }
      }

      return table;
   }


   private String getTableNames(MVMetaData data) {
      Map<String, Set<String>> tableNameMap = data.getTableNameMap();

      if(tableNameMap.values().isEmpty()) {
         return "";
      }

      StringBuilder builder = new StringBuilder();

      for(Map.Entry<String, Set<String>> e : tableNameMap.entrySet()) {
         final String sheetId = e.getKey();
         AssetEntry entry = AssetEntry.createAssetEntry(sheetId);
         final Set<String> tableNames = e.getValue();

         for(String tableName : tableNames) {
            builder.append(entry.getPath()).append("/").append(tableName).append(",");
         }
      }

      return builder.substring(0, builder.length() - 1);
   }

   private String getUsers(MVDef def) {
      Catalog catalog = Catalog.getCatalog();
      StringBuilder usersBuilder = new StringBuilder();

      if(def.getUsers() != null) {
         for(int i = 0; i < def.getUsers().length; i++) {
            Identity identity = def.getUsers()[i];
            IdentityID identityID = IdentityID.getIdentityIDFromKey(identity.getName());
            usersBuilder.append(catalog.getString(
               identity.getType() == Identity.GROUP ? "Group" :
                  identity.getType() == Identity.ROLE ? "Role" : "User"))
               .append(":").append(identityID.getName());

            if(i < def.getUsers().length - 1) {
               usersBuilder.append(" ");
            }
         }
      }

      return usersBuilder.toString();
   }

   private String getStatus(MVDef def) {
      Catalog catalog = Catalog.getCatalog();
      Map statusMap = Cluster.getInstance().getMap("inetsoft.mv.status.map");
      String status = (String) statusMap.get(def.getName());

      if(status != null) {
         return catalog.getString(status);
      }

      return isAvailable(def) ? catalog.getString("Successful") :
         catalog.getString("Failed");
   }

   private String getLastModifiedTime(MVDef def, boolean isShowAges) {
      long time = def.getLastUpdateTime();

      if(time == -1L) {
         return "";
      }

      return formatDate(isShowAges, time);
   }

   private String getSize(MVDef def) {
      DecimalFormat format = new DecimalFormat("0.000");
      long size = 0;
      XFile xfile = FSService.getServer().getFSystem().get(def.getName());
      long blength = 0;

      if(xfile != null) {
         for(SBlock block : xfile.getBlocks()) {
            blength += block.getLength();
         }
      }

      String file = MVStorage.getFile(def.getName());
      size = MVStorage.getInstance().getLength(file) + blength;

      return format.format(size / (1024 * 1024.0));
   }

   /**
    * get dates as ages.
    */
   private String formatDate(boolean asAge, long time) {
      if(time <= 0) {
         return "";
      }

      if(!asAge) {
         return Tool.getDateTimeFormat().format(new Date(time));
      }

      Catalog catalog = Catalog.getCatalog();
      Date current = new Date();
      long ages = current.getTime() - time;
      String astr;

      // second
      if(ages < ONE_MINUTE) {
         astr = (ages / 1000) + " " + catalog.getString("Seconds");
      }
      // minute
      else if(ages < ONE_HOUR) {
         astr = (ages / ONE_MINUTE) + " " + catalog.getString("Minutes");
      }
      // hour
      else if(ages < ONE_DAY) {
         astr = (ages / ONE_HOUR) + " " + catalog.getString("Hours");
      }
      // day
      else {
         astr = (ages / ONE_DAY) + " " + catalog.getString("Days");
      }

      return astr;
   }

   public List<NameLabelTuple> getDataCycles(Principal principal) throws Exception {
      Catalog catalog = Catalog.getCatalog(principal);
      List<NameLabelTuple> dataCycles = new ArrayList<>();
      DataCycleManager dcmanager = DataCycleManager.getDataCycleManager();
      String orgId = OrganizationManager.getInstance().getCurrentOrgID(principal);

      for(Enumeration<?> cycles = dcmanager.getDataCycles(orgId); cycles.hasMoreElements(); ) {
         String cycle = (String) cycles.nextElement();

         if(SecurityEngine.getSecurity().checkPermission(principal, ResourceType.SCHEDULE_CYCLE,
                                                         cycle, ResourceAction.ACCESS))
         {
            dataCycles.add(NameLabelTuple.builder().from(cycle, catalog).build());
         }
      }

      return dataCycles;
   }

   /**
    * Check a mv is avaliable or not.
    */
   private boolean isAvailable(MVDef def) {
      MVStorage storage = MVStorage.getInstance();
      String file = MVStorage.getFile(def.getName());

      if(storage.exists(file)) {
         try {
            MV mv = storage.get(file);
            return mv.isSuccess();
         }
         catch(Exception ex) {
            // ignore it
            return false;
         }
      }

      return def.isSuccess();
   }

   public AssetEntry toAssetEntry(ContentRepositoryTreeNode node) {
      AssetEntry.Type type = node.type() == RepositoryEntry.WORKSHEET ?
                             AssetEntry.Type.WORKSHEET : AssetEntry.Type.VIEWSHEET;
      return new AssetEntry(treeService.getAssetScope(node.path()), type,
                                        treeService.getUnscopedPath(node.path()), node.owner());
   }

   private static final long ONE_MINUTE = 60 * 1000L;
   private static final long ONE_HOUR = 60 * ONE_MINUTE;
   private static final long ONE_DAY = 24 * ONE_HOUR;
   private final MVSupportService support;
   private final ContentRepositoryTreeService treeService;
}
