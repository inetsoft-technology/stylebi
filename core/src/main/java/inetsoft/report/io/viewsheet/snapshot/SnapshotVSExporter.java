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
package inetsoft.report.io.viewsheet.snapshot;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.ExportRecord;
import inetsoft.util.dep.*;
import inetsoft.web.admin.deploy.DeployUtil;
import inetsoft.web.admin.deploy.PartialDeploymentJarInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Export viewsheet as snapshot.
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SnapshotVSExporter {
   /**
    * Constructor.
    * @param rvs the viewsheet entry.
    */
   public SnapshotVSExporter(RuntimeViewsheet rvs) {
      this.rvs = rvs;
   }

   /**
    * Write viewsheet info and its content to OutputStream.
    */
   public void write(OutputStream out) throws Exception {
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null) {
         return;
      }

      vs = (Viewsheet) vs.clone();
      AssetEntry entry = rvs.getEntry();
      asset = new ViewsheetAsset2(entry);
      asset.setSheet(vs);

      if(vs.getBaseEntry() == null) {
         LOG.warn(
            "Offline viewsheet is not applicable to cube source");
      }

       // log export action in audit
      if(logExport) {
         IdentityID userID = IdentityID.getIdentityIDFromKey(box.getUser().getName());
         Timestamp exportTimestamp = new Timestamp(System.currentTimeMillis());
         ExportRecord exportRecord = new ExportRecord(userID.getName(), null,
            ExportRecord.OBJECT_TYPE_VIEWSHEET, null, exportTimestamp, Tool.getHost());
         String objectName = entry.getDescription(false);
         exportRecord.setExportType("vso");
         exportRecord.setObjectName(objectName);
         Audit.getInstance().auditExport(exportRecord, box.getUser());

         setLogExport(false);
      }

      List<XAssetDependency> list = new ArrayList<>();
      Map<XAsset, String[]> depAssetsMap = new HashMap<>();
      getDependencies(list, asset, depAssetsMap);

      List<XAsset> assets = new ArrayList<>();
      assets.add(asset);
      depAssetsMap.keySet().stream().forEach((depAsset) -> {
         boolean same =
            assets.stream().anyMatch((xAsset) -> {
               String identifier = xAsset instanceof AbstractSheetAsset ?
                  ((AbstractSheetAsset) xAsset).getAssetEntry().toIdentifier() : xAsset.toIdentifier();
               String depIdentifier = depAsset instanceof AbstractSheetAsset ?
                  ((AbstractSheetAsset) depAsset).getAssetEntry().toIdentifier() : depAsset.toIdentifier();
               return Tool.equals(identifier, depIdentifier);
            });

         if(!same) {
            assets.add(depAsset);
         }
      });

      assets.sort(new XAssetComparator(list));

      JarOutputStream jar = new JarOutputStream(out);
      List<PartialDeploymentJarInfo.SelectedAsset> selectedEntries = new ArrayList<>();
      List<PartialDeploymentJarInfo.RequiredAsset> dependentAssets = new ArrayList<>();
      PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();

      for(XAsset asset0 : assets) {
         if(asset0 instanceof WorksheetAsset) {
            ((WorksheetAsset) asset0).setSnapshot(true);
         }

         asset0.writeContent(jar);

         if(asset0 != asset) {
            String[] temp = depAssetsMap.get(asset0);
            PartialDeploymentJarInfo.RequiredAsset dep = new PartialDeploymentJarInfo.RequiredAsset();
            dep.setPath(asset0.getPath());
            dep.setType(asset0.getType());
            dep.setUser(asset0.getUser());
            dep.setTypeDescription(asset0.toString());
            dep.setRequiredBy(temp[0]);
            dep.setDetailDescription(temp[1]);
            dep.setLastModifiedTime(asset0.getLastModifiedTime());
            dependentAssets.add(dep);
         }
         else {
            PartialDeploymentJarInfo.SelectedAsset sel = new PartialDeploymentJarInfo.SelectedAsset();
            sel.setType(asset0.getType());
            sel.setPath(asset0.getPath());

            if(asset0.getUser() == null) {
               sel.setUser(new IdentityID(XAsset.NULL, OrganizationManager.getInstance().getCurrentOrgID()));
            }
            else {
               sel.setUser(asset0.getUser());
            }

            sel.setIcon("/inetsoft/sree/web/images/viewsheet.gif");
            sel.setLastModifiedTime(asset0.getLastModifiedTime());
            selectedEntries.add(sel);
         }

         DeployUtil.setAssetAlias(asset0, info);
      }

      info.setDependentAssets(dependentAssets);
      info.setSelectedEntries(selectedEntries);
      info.setOverwriting(true);
      info.setDeploymentDate(new Timestamp(System.currentTimeMillis()));
      info.setName(entry.getName() + ".vso");
      jar.putNextEntry(new JarEntry("JarFileInfo.xml"));
      info.save(jar);

      out.flush();
      out.close();
   }

   /**
    * Get the dependencies of the specified asset.
    */
   private void getDependencies(List<XAssetDependency> list,
      XAssetDependency dependency, Map<XAsset, String[]> depAssetsMap)
      throws Exception
   {
      XAsset newDAsset = dependency.getDependedXAsset();

      if(newDAsset instanceof WorksheetAsset) {
         XAsset asset0 = dependency.getDependingXAsset();
         ViewsheetAsset vasset = asset0 instanceof ViewsheetAsset ?
            (ViewsheetAsset) asset0 : null;
         convertEmbeddedTable((WorksheetAsset) newDAsset, vasset);
      }

      if(depAssetsMap.containsKey(newDAsset)) {
         String[] temp = depAssetsMap.get(newDAsset);
         temp[0] += ", " + dependency.getDependingXAsset().getPath();
         temp[1] += " " + dependency.toString();
      }
      else {
         boolean invisible =
            !dependency.getDependingXAsset().isVisible();
         String[] temp = new String[2];
         temp[0] = invisible ? "" :
            dependency.getDependingXAsset().getPath();
         temp[1] = invisible ? "" : dependency.toString();
         depAssetsMap.put(newDAsset, temp);
      }

      getDependencies(list, newDAsset, depAssetsMap);
   }

   /**
    * Get the dependencies of the specified asset.
    */
   private void getDependencies(List<XAssetDependency> list, XAsset asset,
      Map<XAsset, String[]> depAssetsMap) throws Exception
   {
      XAssetDependency[] dependencies = asset.getDependencies();

      for(XAssetDependency adependency : dependencies) {
         if(!list.contains(adependency)) {
            list.add(adependency);
            getDependencies(list, adependency, depAssetsMap);
         }
      }

      String[] value = null;

      if(depAssetsMap.containsKey(asset)) {
         value = depAssetsMap.get(asset);
         depAssetsMap.remove(asset);
      }

      fix(asset);

      if(value != null) {
         depAssetsMap.put(asset, value);
      }
   }

   private void fix(XAsset asset) throws Exception {
      if(asset instanceof AbstractSheetAsset) {
         AbstractSheetAsset nasset = (AbstractSheetAsset) asset;
         AssetEntry entry = nasset.getAssetEntry();

         if(nasset instanceof ViewsheetAsset) {
            Viewsheet vs = (Viewsheet) nasset.getSheet(false);

            if(vs == null) {
               AssetRepository engine = rvs.getAssetRepository();
               vs = (Viewsheet)
                  engine.getSheet(entry, null, false, AssetContent.ALL);
               vs = (Viewsheet) vs.clone();
               vs.setEntry(fixEntry(vs.getEntry(), false));
               vs.setBaseEntry(fixEntry(vs.getBaseEntry(), false));
               nasset.setSheet(vs);
            }
            else {
               vs.setEntry(fixEntry(vs.getEntry(), false));
               vs.setBaseEntry(fixEntry(vs.getBaseEntry(), false));
            }
         }

         nasset.setAssetEntry(fixEntry(entry, asset == this.asset));
      }
   }

   private AssetEntry fixEntry(AssetEntry entry, boolean fixpath) {
      if(entry == null) {
         return entry;
      }

      if(entry.getScope() == AssetRepository.USER_SCOPE || fixpath) {
         return new AssetEntry(AssetRepository.GLOBAL_SCOPE, entry.getType(),
               fixpath ? entry.getName() : entry.getPath(), null);
      }

      return entry;
   }

   /**
    * Convert bound table to embedded table.
    */
   private void convertEmbeddedTable(WorksheetAsset wasset,
                                     ViewsheetAsset vasset) throws Exception
   {
      AssetRepository engine = rvs.getAssetRepository();
      Viewsheet vs = vasset != null ? (Viewsheet) vasset.getCurrentSheet(engine) : null;
      Worksheet originalSheet;
      boolean direct = false;

      if(vs == null) {
         originalSheet = (Worksheet) wasset.getCurrentSheet(engine);
      }
      else if(vs.isDirectSource()) {
         originalSheet = vs.getBaseWorksheet();
         direct = true;
      }
      else {
         originalSheet = (Worksheet) engine.getSheet(
            vs.getBaseEntry(), null, false, AssetContent.ALL);
      }

      ViewsheetSandbox vbox = rvs.getViewsheetSandbox();

      if(vbox == null) {
         return;
      }

      AssetQuerySandbox box = vbox.getAssetQuerySandbox();
      Principal user = box.getUser();
      VariableTable table = box.getVariableTable();
      originalSheet = new WorksheetWrapper(originalSheet);
      VSUtil.shrinkTable(vs, originalSheet);
      fixColumnSelection(originalSheet);
      box = new AssetQuerySandbox(originalSheet);
      box.setWSName(box.getWSName());
      box.setBaseUser(user);
      box.refreshVariableTable(table);
      box.getVariableTable().put("__exporting_snapshot__", "true");

      if(vs != null && vs.isDirectSource()) {
         vs.setBaseEntry(wasset.getAssetEntry());
      }

      Worksheet ws = (Worksheet) wasset.getCurrentSheet(engine);

      if(ws == null) {
         return;
      }

      ws = (Worksheet) ws.clone();
      ws.setOffline(true);
      wasset.setSheet(ws);
      Assembly[] assemblies = ws.getAssemblies(true);
      List<Assembly> mirrors = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         TableAssembly base = getBaseTable((TableAssembly) assembly);

         if(!(base instanceof EmbeddedTableAssembly)) {
            mirrors.add(assembly);
         }
      }

      for(Assembly assembly : assemblies) {
         if(requiresConverting(assembly)) {
            TableAssembly tassembly = (TableAssembly) assembly;
            final EmbeddedTableAssembly nassembly = convertEmbeddedTable(box, tassembly, direct);

            if(nassembly == null) {
               continue;
            }

            final String mirrorName = processMirrorTableAssembly(ws, nassembly);
            ws.addAssembly(nassembly);

            if(mirrorName != null) {
               ws.renameAssembly(nassembly.getName(), mirrorName + SNAPSHOT_TABLE_SUFFIX, false);
            }
         }
      }

      // add auto.generate flag to let embed table to generate mirror table
      // when call Viewsheet.resetWS to let mv work proper
      for(Assembly mirror : mirrors) {
         mirror = ws.getAssembly(mirror.getName());
         TableAssembly base = getBaseTable((TableAssembly) mirror);

         if(base == null) {
            continue;
         }

         base = (TableAssembly) ws.getAssembly(base.getName());

         if(base instanceof EmbeddedTableAssembly) {
            base.setProperty("auto.generate", "true");
         }
      }
   }

   private String processMirrorTableAssembly(Worksheet ws, EmbeddedTableAssembly nassembly) {
      if(ws == null || nassembly == null) {
         return null;
      }

      // update mirror table
      String name = nassembly.getName();
      int idx = name.indexOf("_O");

      if(idx < 0) {
         return null;
      }

      String nname = name.substring(0, idx);
      Assembly ass0 = ws.getAssembly(nname);

      if(!(ass0 instanceof MirrorTableAssembly)) {
         return null;
      }

      MirrorTableAssembly mirror0 = (MirrorTableAssembly) ass0;
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, nname, nassembly);
      mirror.setVisible(mirror0.isVisible());

      // update columns
      ColumnSelection mcols = mirror.getColumnSelection();
      ColumnSelection tcols = nassembly.getColumnSelection();
      ColumnSelection ncols = new ColumnSelection();

      for(int i = 0; i < tcols.getAttributeCount(); i++) {
         ColumnRef tcolumn = (ColumnRef) tcols.getAttribute(i);
         DataRef mcolumn = mcols.getAttribute(tcolumn.getName());

         if(mcolumn == null) {
            mcolumn = mcols.getAttribute(tcolumn.getAttribute());
         }

         if(mcolumn != null) {
            ncols.addAttribute(mcolumn);
         }
      }

      if(!ncols.isEmpty()) {
         mirror.setColumnSelection(ncols);
      }

      ws.addAssembly(mirror);
      return nname;
   }

   /**
    * Check if the specified table assembly requires converting.
    */
   private boolean requiresConverting(Assembly table) {
      if(table instanceof BoundTableAssembly) {
         return true;
      }

      if(!isBoundTable(table)) {
         return false;
      }

      ColumnSelection cols = ((TableAssembly) table).getColumnSelection();

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef col = (ColumnRef) cols.getAttribute(i);

         // sql expression could not be executed properly in post process,
         // so here we convert this composed table into embedded table as well
         if(!(col instanceof CalculateRef) && col.isExpression() && col.isSQL())
         {
            LOG.warn(
               "Table " + table.getName() + " has SQL expression, " +
               "which will be converted to an embedded table. " +
               "This structual change might affect dashboard interactivies.");
            return true;
         }
      }

      return false;
   }

   /**
    * Check if is bound table. A bound table is a bound table, and if a
    * composed table based on bound tables (including sub composed tables),
    * it's also a bound table.
    */
   private boolean isBoundTable(Assembly table) {
      if(table instanceof BoundTableAssembly ||
         table instanceof SnapshotEmbeddedTableAssembly)
      {
         return true;
      }
      else if(!(table instanceof ComposedTableAssembly)) {
         return false;
      }

      // for composite table, we cannot convert it to snapshot embedded
      // table assembly by using AssetEventUtil.convertEmbeddedTable,
      // because it has two XNodeTable, at the same time, if there exist
      // sql formula in the composite table, it is also not valid
      // fix bug1333935758061
      if(table instanceof CompositeTableAssembly || table instanceof MirrorTableAssembly) {
         return false;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tarr = ctable.getTableAssemblies(false);

      for(int i = 0; tarr != null && i < tarr.length; i++) {
         if(!isBoundTable(tarr[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get base table.
    */
   private TableAssembly getBaseTable(TableAssembly assembly) {
      if(!(assembly instanceof MirrorTableAssembly)) {
         return null;
      }

      TableAssembly base = assembly;

      while(base instanceof MirrorTableAssembly) {
         base = (TableAssembly) ((MirrorTableAssembly) base).getAssembly();
      }

      return base;
   }

   /**
    * Convert table to embedded table.
    */
   private EmbeddedTableAssembly convertEmbeddedTable(AssetQuerySandbox box,
      TableAssembly tab, boolean direct) throws Exception
   {
      // reset column selection, in AssetEventUtil.convertEmbeddedTable,
      // the column selection has set to embedded table's column selection,
      // but for composite table, its sub tables are also in original status,
      // not change to use the converted embedded table assembly, so here should
      // reset column selection to make sure default column selection in sync
      // fix bug1305007731617
      box.resetDefaultColumnSelection();
      EmbeddedTableAssembly assembly =
         AssetEventUtil.convertEmbeddedTable(box, tab, true, true, direct);

      try {
         copyInfo(assembly, tab, box);
      }
      catch(Exception ex) {
         LOG.error("Failed to copy embedded table assembly info", ex);
      }

      return assembly;
   }

   /**
    * Copy nessary infomations from original table to target table.
    */
   private void copyInfo(TableAssembly target, TableAssembly source,
                         AssetQuerySandbox box)
      throws Exception
   {
      AssetQuery query = AssetQuery.createAssetQuery(
         source, AssetQuerySandbox.RUNTIME_MODE, box, false, -1L, true, true);

      if(!query.isPreConditionListMergeable()) {
         target.setPreConditionList(source.getPreConditionList());
      }

      if(!query.isPostConditionListMergeable()) {
         target.setPostConditionList(source.getPostConditionList());
      }

      if(!query.isRankingMergeable()) {
         target.setRankingConditionList(source.getRankingConditionList());
      }

      if(!query.isSortInfoMergeable()) {
         target.setSortInfo(source.getSortInfo());
      }

      if(!((AbstractTableAssembly) target).isCrosstab()) {
         if(query.isAggregateInfoMergeable()) {
            target.setAggregateInfo(new AggregateInfo());
         }
         else {
            target.setAggregateInfo(
               (AggregateInfo) source.getAggregateInfo().clone());
         }
      }
   }

   /**
    * Fix column selection of asset query to make all used columns visible.
    */
   private void fixColumnSelection(Worksheet ws) {
      if(ws == null) {
         return;
      }

      Assembly[] arr = ws.getAssemblies();

      for(Assembly assembly : arr) {
         if(assembly instanceof TableAssembly) {
            TableAssembly tassembly = (TableAssembly) assembly;
            ColumnSelection cols = tassembly.getColumnSelection();

            for(int i = 0; i < cols.getAttributeCount(); i++) {
               if(cols.getAttribute(i) instanceof ColumnRef) {
                  ((ColumnRef) cols.getAttribute(i)).setHiddenParameter(false);
               }
            }
         }
      }
   }

   /**
    * Check if should log export.
    * @return <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   public boolean isLogExport() {
      return logExport;
   }

   /**
    * Set whether should log export.
    * @param log <tt>true</tt> if should log, <tt>false</tt> otherwise.
    */
   public void setLogExport(boolean log) {
      this.logExport = log;
   }

   private static final Logger LOG = LoggerFactory.getLogger(SnapshotVSExporter.class);
   private RuntimeViewsheet rvs;
   private ViewsheetAsset asset;
   private boolean logExport = false;
   private static final String SNAPSHOT_TABLE_SUFFIX = "_VSO";
}
