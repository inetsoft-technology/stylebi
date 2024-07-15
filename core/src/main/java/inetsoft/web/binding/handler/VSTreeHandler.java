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
package inetsoft.web.binding.handler;

import inetsoft.analytic.composition.event.*;
import inetsoft.graph.data.DataSet;
import inetsoft.report.TableLens;
import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.web.composer.model.TreeNodeModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class VSTreeHandler {
   @Autowired
   public VSTreeHandler(VSChartHandler chartHandler) {
      this.chartHandler = chartHandler;
   }

   /**
    * RefreshChartTreeEvent.
    */
   public AssetTreeModel getChartTreeModel(AssetRepository engine,
      final RuntimeViewsheet rvs, ChartVSAssemblyInfo info, boolean isWizard, Principal principal)
      throws Exception
   {
      Viewsheet vs0 = rvs.getViewsheet();
      String aname = info.getAbsoluteName2();
      aname = aname == null ? info.getAbsoluteName() : aname;
      ChartVSAssembly chart = (ChartVSAssembly) vs0.getAssembly(aname);

      final Viewsheet vs = chart.getViewsheet();
      final String name = chart.getName();

      VSChartInfo cinfo = info.getVSChartInfo();
      SourceInfo sinfo = info.getSourceInfo();
      String prefix = null;
      String source = null;

      // fix bug1254116852399
      if(sinfo != null && (rvs.isRuntime() || chart.isEmbedded())) {
         prefix = sinfo.getPrefix();
         source = sinfo.getSource();
      }

      CubeTreeModelBuilder builder = CubeTreeModelBuilder.getBuilder(engine, principal,
         prefix, source, info, rvs, isWizard,
         new CubeTreeModelBuilder.Processor() {
            /**
             * Base worksheet changed.
             */
            @Override
            public void baseWorksheetChanged() {
               rvs.resetRuntime();
            }

            /**
             * Aggregate info is invalid.
             * @param info the assembly which is in processing's info.
             */
            @Override
            public void aggInfoInvalid(VSAssemblyInfo info) throws Exception {
               ViewsheetSandbox box = rvs.getViewsheetSandbox();

               if(box == null) {
                  return;
               }

               ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(name);
               chart.setVSAssemblyInfo(info);

               if(!isWizard) {
                  box.updateAssembly(chart.getAbsoluteName());
               }
            }

            /**
             * Check info is null.
             * @param info the assembly which is in processing's info.
             */
            @Override
            public boolean infoIsNull(VSAssemblyInfo info) {
               return info == null ||
                      ((ChartVSAssemblyInfo) info).getVSChartInfo() == null;
            }

            /**
             * Create aggregate infomation.
             * @param info the assembly which is in processing's info.
             */
            @Override
            public AggregateInfo createAggregateInfo(VSAssemblyInfo info) {
               AggregateInfo ainfo = new AggregateInfo();
               VSChartInfo ci = ((ChartVSAssemblyInfo) info).getVSChartInfo();
               ci.setAggregateInfo(ainfo);
               return ainfo;
            }

            /**
             * Clear aggregate info.
             * @param info the assembly which is in processing's info.
             */
            @Override
            public void clearAggregateInfo(VSAssemblyInfo info) {
               ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) info;
               cinfo.getVSChartInfo().setAggregateInfo(null);
            }
         });

      AggregateInfo ainfo = cinfo == null ? null : cinfo.getAggregateInfo();
      checkBaseWSChanged(vs, sinfo, builder, ainfo);

      VSChartInfo cinfo2 = ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).getVSChartInfo();
      buildGeoColumns(cinfo2, rvs, vs, chart, builder);

      return builder.getCubeTreeModel();
   }

   private void checkBaseWSChanged(Viewsheet vs,
                                   SourceInfo sinfo,
                                   CubeTreeModelBuilder builder,
                                   AggregateInfo ainfo)
   {
      AssetEntry bentry = vs.getBaseEntry();

      // base ws changed, don't use the source/aggr info
      if(bentry != null && bentry.isLogicModel() && sinfo != null &&
         !bentry.getName().equals(sinfo.getSource()))
      {
         builder.setSourceInfo(null);
         builder.setAggregateInfo(null);
      }
      else {
         builder.setSourceInfo(sinfo);
         builder.setAggregateInfo(ainfo);
      }
   }

   private void buildGeoColumns(VSChartInfo cinfo2, final RuntimeViewsheet rvs, Viewsheet vs,
                                ChartVSAssembly chart, BaseTreeModelBuilder builder) throws Exception
   {
      ColumnSelection cols = cinfo2.getGeoColumns();

      if(cols.getAttributeCount() > 0) {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();
         DataSet data = chartHandler.getChartData(box, chart);
         chartHandler.updateGeoColumns(box, vs, chart, cinfo2);
         ColumnSelection rcols = cinfo2.getRTGeoColumns();

         for(int i = 0; i < rcols.getAttributeCount(); i++) {
            String refName = rcols.getAttribute(i).getName();
            DataRef col = rcols.getAttribute(refName);
            boolean allMapped = true;

            if(col instanceof VSChartGeoRef && data != null) {
               VSChartGeoRef geoCol = (VSChartGeoRef) col;
               int index = GraphUtil.indexOfHeader(data, refName);
               FeatureMapping mapping =
                  geoCol.getGeographicOption().getMapping();

               allMapped = MapHelper.isAllMapped(data, index, mapping, cinfo2);
            }

            builder.setMappingStatus(refName, allMapped);
         }
      }
   }

   /**
    * RefreshTableTreeEvent.
    */
   public AssetTreeModel getTableTreeModel(AssetRepository engine,
      final RuntimeViewsheet rvs, TableDataVSAssemblyInfo cinfo, Principal principal)
      throws Exception
   {
      Viewsheet vs0 = rvs.getViewsheet();
      String aname = cinfo.getAbsoluteName();
      VSAssembly cass = vs0 == null ? null : (VSAssembly) vs0.getAssembly(aname);

      SourceInfo sinfo = cinfo.getSourceInfo();
      String prefix = null;
      String source = null;

      if(sinfo != null && (rvs.isRuntime() || cass.isEmbedded())) {
         prefix = sinfo.getPrefix();
         source = sinfo.getSource();
      }

      final Viewsheet vs = cass.getViewsheet();
      final String name = cass.getName();

      CubeTreeModelBuilder builder = CubeTreeModelBuilder.getBuilder(engine, principal,
         prefix, source, cinfo, rvs, false,
         new CubeTreeModelBuilder.Processor() {
            /**
             * Base worksheet changed.
             */
            @Override
            public void baseWorksheetChanged() {
               rvs.resetRuntime();
            }

            /**
             * Aggregate info is invalid.
             * @param info the assembly which is in processing's info.
             */
            @Override
            public void aggInfoInvalid(VSAssemblyInfo info) throws Exception {
               ViewsheetSandbox box = rvs.getViewsheetSandbox();

               if(box == null) {
                  return;
               }

               VSAssembly assembly = (VSAssembly) vs.getAssembly(name);
               box.updateAssembly(assembly.getAbsoluteName());
               assembly.setVSAssemblyInfo(info);
            }

            /**
             * Check info is null.
             * @param info the assembly which is in processing's info.
             */
            @Override
            public boolean infoIsNull(VSAssemblyInfo info) {
               return info == null;
            }

            /**
             * Create aggregate infomation.
             * @param info the assembly which is in processing's info.
             */
            @Override
            public AggregateInfo createAggregateInfo(VSAssemblyInfo info) {
               AggregateInfo ainfo = new AggregateInfo();

               if(info instanceof CrosstabVSAssemblyInfo) {
                  VSCrosstabInfo cinfo =
                     ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo();
                  cinfo.setAggregateInfo(ainfo);
               }
               else if(info instanceof CalcTableVSAssemblyInfo) {
                  CalcTableVSAssemblyInfo calcInfo =
                     (CalcTableVSAssemblyInfo) info;
                  calcInfo.setAggregateInfo(ainfo);
               }

               return ainfo;
            }

            /**
             * Clear aggregate info.
             * @param info the assembly which is in processing's info.
             */
            @Override
            public void clearAggregateInfo(VSAssemblyInfo info) {
               if(info instanceof CrosstabVSAssemblyInfo) {
                  VSCrosstabInfo cinfo =
                     ((CrosstabVSAssemblyInfo) info).getVSCrosstabInfo();
                  cinfo.setAggregateInfo(null);
               }
               else if(info instanceof CalcTableVSAssemblyInfo) {
                  ((CalcTableVSAssemblyInfo) info).setAggregateInfo(null);
               }
            }
         });

      AggregateInfo ainfo = null;
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      // event called from ConvertTableRefEvent?
      if(cinfo instanceof CrosstabVSAssemblyInfo) {
         ainfo = getAggregateInfo(vs, ((CrosstabVSAssemblyInfo) cinfo), box, engine);
      }
      else if(cinfo instanceof CalcTableVSAssemblyInfo) {
         ainfo = getAggregateInfo(vs, ((CalcTableVSAssemblyInfo) cinfo), box, engine);
      }

      checkBaseWSChanged(vs, sinfo, builder, ainfo);

      return builder.getCubeTreeModel(false);
   }

   /**
    * SetWSTreeModelEvent.
    */
   public AssetTreeModel getWSTreeModel(AssetRepository engine, RuntimeViewsheet rvs,
      String name, boolean isLayoutMode, Principal principal) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return null;
      }

      Worksheet ws = vs.getBaseWorksheet();
      AssetEntry entry = vs.getBaseEntry();

      if(entry != null && !vs.isDirectSource() && ws != null && !ws.isOffline()) {
         Worksheet nws = (Worksheet)
            engine.getSheet(entry, principal, false, AssetContent.ALL);

         // worksheet has changed, update the vs
         if(nws != null && nws.getLastModified() > ws.getLastModified())
         {
            rvs.resetRuntime();
         }
      }
      else if(entry != null && vs.isDirectSource()) {
         // just ignore QTL change
      }

      vs = rvs.getViewsheet();
      Properties prop = new Properties();

      if(name != null) {
         VSAssembly assembly = vs.getAssembly(name);

         if(assembly instanceof TableDataVSAssembly) {
            String table = assembly.getTableName();

            if(table != null) {
               prop.setProperty("source", table);
               prop.setProperty("filterTable", table);
            }
         }
      }

      return VSEventUtil.refreshBaseWSTree(engine, principal, vs, prop, isLayoutMode);
   }

   /**
    * Get aggregate info, if the crosstab is an old version, create a default
    * aggregate info by header rows, columns and aggregates.
    */
   private AggregateInfo getAggregateInfo(Viewsheet vs, CrosstabVSAssemblyInfo info,
      ViewsheetSandbox box, AssetRepository engine)
   {
      SourceInfo sinfo = info.getSourceInfo();
      VSCrosstabInfo cinfo = info.getVSCrosstabInfo();
      AggregateInfo ainfo = cinfo == null ? null : cinfo.getAggregateInfo();

      // exists? not old version
      if(ainfo != null && !ainfo.isEmpty()) {
         return ainfo;
      }

      DataRef[] aggs = cinfo == null ? null : cinfo.getDesignAggregates();
      aggs = aggs == null ? new DataRef[0] : aggs;
      DataRef[] cols = cinfo == null ? null : cinfo.getDesignColHeaders();
      cols = cols == null ? new DataRef[0] : cols;
      DataRef[] rows = cinfo == null ? null : cinfo.getDesignRowHeaders();
      rows = rows == null ? new DataRef[0] : rows;
      TableAssembly tbl = VSEventUtil.getTableAssembly(vs, sinfo, engine, null);

      // an empty cross table?
      if(sinfo == null || cinfo == null || tbl == null ||
         (aggs.length <= 0 && cols.length <= 0 && rows.length <= 0))
      {
         return null;
      }

      ainfo = new AggregateInfo();
      VSEventUtil.createAggregateInfo(tbl, ainfo, null, vs, true);

      cinfo.setAggregateInfo(ainfo);

      return ainfo;
   }

   /**
    * Get aggregate info, if the crosstab is an old version, create a default
    * aggregate info by header rows, columns and aggregates.
    */
   private AggregateInfo getAggregateInfo(Viewsheet vs, CalcTableVSAssemblyInfo info,
      ViewsheetSandbox box, AssetRepository engine)
   {
      SourceInfo sinfo = info.getSourceInfo();
      AggregateInfo ainfo = info.getAggregateInfo();

      // exists? not old version
      if(ainfo != null && !ainfo.isEmpty()) {
         return ainfo;
      }

      TableAssembly tbl = VSEventUtil.getTableAssembly(vs, sinfo, engine, null);

      if(sinfo == null || tbl == null) {
         return null;
      }

      ainfo = new AggregateInfo();
      VSEventUtil.createAggregateInfo(tbl, ainfo, null, vs, true);
      info.setAggregateInfo(ainfo);
      return ainfo;
   }

   public TreeNodeModel createTreeNodeModel(AssetTreeModel.Node node, Principal user) {
      List<TreeNodeModel> children = Arrays.stream(node.getNodes())
         .filter(n -> n.getEntry().getName().indexOf(Assembly.TABLE_VS_BOUND) == -1)
         .map(n -> createChildTreeNode(n, user))
         .collect(Collectors.toList());

      return TreeNodeModel.builder()
         .children(children)
         .expanded(true)
         .build();
   }

   private TreeNodeModel createChildTreeNode(AssetTreeModel.Node node, Principal user) {
      List<TreeNodeModel> children = Arrays.stream(node.getNodes())
         .map(n -> createChildTreeNode(n, user))
         .collect(Collectors.toList());
      return createNodeFromEntry(node.getEntry(), children, user);
   }

   private TreeNodeModel createNodeFromEntry(AssetEntry entry,
                                             List<TreeNodeModel> children,
                                             Principal principal)
   {
      boolean isCube = "true".equals(entry.getProperty("CUBE_TABLE"));
      String label = isCube ? entry.getProperty(LOCAL_STR) : entry.toView();
      Catalog userCatalog = Catalog.getCatalog(principal, Catalog.REPORT);

      return TreeNodeModel.builder()
         .label(entry.getType() == AssetEntry.Type.COLUMN ? Tool.localize(label)
                   : userCatalog.getString(label))
         .data(entry)
         .expanded(!isLeaf(entry) && "true".equals(entry.getProperty("expanded")))
         .dragName(entry.getType().name().toLowerCase())
         .leaf(isLeaf(entry))
         .children(children)
         .tooltip(entry.getProperty("Tooltip"))
         .disabled("notAvailable".equals(entry.getName()))
         .build();
   }

   /**
    * Method for determining whether a given AssetEntry is a leaf node/
    *
    * @param entry an AssetEntry
    * @return true if entry is a leaf, false otherwise
    */
   private boolean isLeaf(AssetEntry entry) {
      return entry.getType() == AssetEntry.Type.VIEWSHEET
         || entry.getType() == AssetEntry.Type.WORKSHEET
         || entry.getType() == AssetEntry.Type.COLUMN;
   }

   private boolean isAssemblyBindable(Assembly srcAssembly, Assembly bindAssembly, Viewsheet vs) {
      return !bindAssembly.equals(srcAssembly) && bindAssembly.isVisible() &&
         !VSUtil.isBoundTo(bindAssembly, srcAssembly, vs) &&
         bindAssembly instanceof DataVSAssembly && !(bindAssembly instanceof ChartVSAssembly);
   }

   /**
    * Append viewsheet assembly columns to the model tree.
    */
   public void appendVSAssemblyTree(RuntimeViewsheet rvs, AssetTreeModel model, Principal user,
                                    VSAssembly assembly) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());

      if(vs == null) {
         return;
      }

      boolean metadata = vs.getViewsheetInfo().isMetadata();
      AssetTreeModel.Node mroot = (AssetTreeModel.Node) model.getRoot();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.REPOSITORY_FOLDER,
                                        "/" + Catalog.getCatalog().getString("Components"),
                                        pId);
      AssetTreeModel.Node root = new AssetTreeModel.Node(entry);

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      List<Assembly> assemblies = Arrays.stream(vs.getAssemblies(true))
         .filter(a -> isAssemblyBindable(assembly, a, vs))
         .sorted(Comparator.comparing(Assembly::getAbsoluteName))
         .collect(Collectors.toList());

      AggregateInfo ainfo = null;
      SourceInfo sinfo = null;

      if(assembly instanceof ChartVSAssembly) {
         VSChartInfo cinfo = ((ChartVSAssembly) assembly).getVSChartInfo();
         ainfo = cinfo.getAggregateInfo();
         sinfo = ((ChartVSAssembly) assembly).getSourceInfo();
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         VSCrosstabInfo cinfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();
         ainfo = cinfo.getAggregateInfo();
         sinfo = ((CrosstabVSAssembly) assembly).getSourceInfo();
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         CalcTableVSAssemblyInfo cinfo =
            (CalcTableVSAssemblyInfo) assembly.getInfo();
         ainfo = cinfo.getAggregateInfo();
         sinfo = ((CalcTableVSAssembly) assembly).getSourceInfo();
      }

      for(Assembly assembly0 : assemblies) {
         if(((VSAssembly) assembly0).isWizardEditing()) {
            continue;
         }

         if(((VSAssembly) assembly0).getName().startsWith("_temp_")) {
            continue;
         }

         appendVSAssemblyTree(root, assembly, assembly0, vs, box, sinfo, ainfo, metadata, user);
      }

      if(root.getNodeCount() > 0) {
         mroot.addNode(root);
      }
   }

   private void appendVSAssemblyTree(AssetTreeModel.Node root, VSAssembly assembly,
                                     Assembly assembly0, Viewsheet vs, ViewsheetSandbox box,
                                     SourceInfo sinfo, AggregateInfo ainfo, boolean metadata,
                                     Principal user) throws Exception
   {
      final String name = assembly0.getAbsoluteName();
      List<DataRef> aggRefs = new ArrayList<>();
      List<DataRef> dimRefs = new ArrayList<>();

      if(!metadata) {
         TableLens lens;
         Boolean canCancel = VSAQuery.Q_CANCEL.get();

         try {
            // Bug #41966, shouldn't be able to cancel query initiated from the tree
            VSAQuery.Q_CANCEL.set(false);
            lens = box.getTableData(Assembly.TABLE_VS_BOUND + name);
         }
         catch(CancelledException e) {
            // Bug #41966, another request started the query execution before this thread and
            // has been cancelled, need to run it again
            lens = box.getTableData(Assembly.TABLE_VS_BOUND + name);
         }
         finally {
            if(canCancel == null) {
               VSAQuery.Q_CANCEL.remove();
            }
            else {
               VSAQuery.Q_CANCEL.set(canCancel);
            }
         }

         if(lens == null) {
            return;
         }

         Integer hcols = null;

         CrossTabFilter crosstab =
            (CrossTabFilter) Util.getNestedTable(lens, CrossTabFilter.class);

         if(crosstab != null) {
            hcols = crosstab.getHeaderColCount();
         }

         boolean aggInfoAvailable = ainfo != null && !ainfo.isEmpty() && sinfo != null &&
            sinfo.getType() == XSourceInfo.VS_ASSEMBLY && name.equals(sinfo.getSource());

         for(int c = 0; c < lens.getColCount(); c++) {
            String header = Util.getHeader(lens, c).toString();
            BaseField field = new BaseField(null, header);
            field.setDataType(Util.getDataType(lens, c));

            if(aggInfoAvailable && ainfo.containsGroup(field)) {
               dimRefs.add(field);
            }
            else if(aggInfoAvailable && ainfo.containsAggregate(field)) {
               aggRefs.add(field);
            }
            else if(hcols == null && XSchema.isNumericType(field.getDataType())) {
               aggRefs.add(field);
            }
            else if(hcols != null && c >= hcols) {
               aggRefs.add(field);
            }
            else {
               dimRefs.add(field);
            }
         }

         CalculateRef[] calcRefs = vs.getCalcFields(Assembly.TABLE_VS_BOUND + name);

         if(calcRefs != null) {
            for(CalculateRef ref : calcRefs) {
               if(aggInfoAvailable && ainfo.containsGroup(ref)) {
                  dimRefs.add(ref);
               }
               else if(aggInfoAvailable && ainfo.containsAggregate(ref)) {
                  aggRefs.add(ref);
               }
               else if(XSchema.isNumericType(ref.getDataType())) {
                  aggRefs.add(ref);
               }
               else {
                  dimRefs.add(ref);
               }
            }
         }
      }

      String path = "/components/" + Assembly.TABLE_VS_BOUND + name;
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                             AssetEntry.Type.TABLE, path, pId);
      entry.setAlias(assembly0.getName());
      AssetTreeModel.Node node = new AssetTreeModel.Node(entry);

      if(Objects.equals(entry.getAlias(), assembly0.getName())) {
         // for embedded vs
         entry.setAlias(assembly0.getAbsoluteName());
      }

      if(metadata) {
         AssetEntry notAvailableEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                                       AssetEntry.Type.COLUMN, path + "/notAvailable", null);
         notAvailableEntry.setAlias(
            Catalog.getCatalog().getString("composer.vs.binding.vsAssembly.notAvailable"));
         node.addNode(new AssetTreeModel.Node(notAvailableEntry));
         root.addNode(node);
         return;
      }
      else if(aggRefs.isEmpty() && dimRefs.isEmpty()) {
         return;
      }

      // add dimension folder
      AssetEntry dimensionEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
         node.getEntry().getPath() + "/" + Catalog.getCatalog().getString("Dimensions"), null);
      dimensionEntry.setProperty(LOCAL_STR, Catalog.getCatalog().getString("Dimensions"));
      AssetTreeModel.Node dimensionRootNode = new AssetTreeModel.Node(dimensionEntry);
      dimensionRootNode.setRequested(true);
      node.addNode(dimensionRootNode);

      // add measure folder
      AssetEntry measureEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
         node.getEntry().getPath() + "/" + Catalog.getCatalog().getString("Measures"),
                                               null);
      measureEntry.setProperty(LOCAL_STR, Catalog.getCatalog().getString("Measures"));
      AssetTreeModel.Node measureRootNode = new AssetTreeModel.Node(measureEntry);
      measureRootNode.setRequested(true);
      node.addNode(measureRootNode);

      // add the assembly node to the root
      root.addNode(node);

      // expand the nodes if applicable
      if(VSUtil.isBoundTo(assembly, assembly0, vs)) {
         root.getEntry().setProperty("expanded", "true");
         entry.setProperty("expanded", "true");
         dimensionEntry.setProperty("expanded", "true");
         measureEntry.setProperty("expanded", "true");
      }

      // create nodes for aggregates
      for(DataRef ref : aggRefs) {
         measureRootNode.addNode(new AssetTreeModel.Node(
            createAssemblyColumnEntry(ref, true, Assembly.TABLE_VS_BOUND + name, path, user)));
      }

      // create nodes for dimensions
      for(DataRef ref : dimRefs) {
         dimensionRootNode.addNode(new AssetTreeModel.Node(
            createAssemblyColumnEntry(ref, false, Assembly.TABLE_VS_BOUND + name, path, user)));
      }
   }

   private AssetEntry createAssemblyColumnEntry(DataRef ref, boolean measure, String assemblyName,
                                                String parentPath, Principal user)
   {
      String refName = ref.getName();
      String path0 = parentPath + "/" + refName;
      IdentityID pId = user == null ? null : IdentityID.getIdentityIDFromKey(user.getName());
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.COLUMN, path0, pId);
      entry.setProperty("dtype", ref.getDataType());
      entry.setProperty("assembly", assemblyName);
      entry.setProperty("attribute", refName);
      entry.setProperty("source", assemblyName);
      entry.setProperty("type", XSourceInfo.VS_ASSEMBLY + "");
      // use NONE to match regular data source tree node
      entry.setProperty("refType", measure ? DataRef.NONE + "" : DataRef.DIMENSION + "");
      entry.setProperty(AssetEntry.CUBE_COL_TYPE,
                        measure ? AssetEntry.MEASURES + "" : AssetEntry.DIMENSIONS + "");

      if(ref instanceof CalculateRef) {
         CalculateRef cref = (CalculateRef) ref;
         ExpressionRef eref = (ExpressionRef) cref.getDataRef();
         entry.setProperty("formula", "true");
         entry.setProperty("basedOnDetail", cref.isBaseOnDetail() + "");

         if(!cref.isBaseOnDetail()) {
            entry.setProperty("refType", DataRef.AGG_CALC + "");
         }

         entry.setProperty("script", eref.getExpression());
      }

      return entry;
   }

   private static final String LOCAL_STR = "localStr";
   private final VSChartHandler chartHandler;
}
