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
package inetsoft.analytic.composition.event;

import inetsoft.report.composition.*;
import inetsoft.report.composition.AssetTreeModel.Node;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;

import java.security.Principal;
import java.util.*;

/**
 * CubeTreeModelBuilder to build a tree model show as cube, which means the tree
 * model has dimensions and measures. If a table is an normal table, and without
 * defined aggregate infomations, treat number type of column as measure,
 * other types of column as dimension, if defined aggregate infomations, create
 * dimensions and measures by the infomation. If a table is cube table, the
 * dimensions and aggregates is defined by the table self.
 */
public class CubeTreeModelBuilder extends BaseTreeModelBuilder {
   /**
    * Default constructor.
    */
   private CubeTreeModelBuilder() {
      super();
   }

   /**
    * Create Info by event and viewsheet.
    */
   public static CubeTreeModelBuilder getBuilder(AssetEvent event,
                                                 RuntimeViewsheet rvs,
                                                 Processor processor) {
      CubeTreeModelBuilder model = new CubeTreeModelBuilder();
      model.engine = event.getWorksheetEngine().getAssetRepository();
      model.processor = processor;
      model.rvs = rvs;
      model.runtime = rvs.isRuntime();
      model.principal = event.getUser();
      model.user = model.principal == null ? null : IdentityID.getIdentityIDFromKey(model.principal.getName());

      String prefix = (String) event.get("prefix");
      String source = (String) event.get("source");
      // get properties for cube table
      Properties props = new Properties();

      if(prefix != null) {
         props.setProperty("prefix", prefix);
      }

      if(source != null) {
         props.setProperty("source", source);
      }

      model.props = props;
      model.cube = source != null && source.indexOf(Assembly.CUBE_VS) >= 0;

      // get viewsheet
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return model;
      }

      VSAssemblyInfo info = (VSAssemblyInfo) event.get("assemblyInfo");
      model.info = info;
      String aname = info.getAbsoluteName2();
      aname = aname == null ? info.getAbsoluteName() : aname;
      int idx = aname.lastIndexOf(".");

      if(idx != -1) {
         vs = (Viewsheet) vs.getAssembly(aname.substring(0, idx));
      }

      model.vs = vs;
      model.baseWS = vs.getBaseWorksheet();
      model.baseEntry = vs.getBaseEntry();
      return model;
   }

   public static CubeTreeModelBuilder getBuilder(AssetRepository engine,
      Principal principal, String prefix, String source, VSAssemblyInfo info,
      RuntimeViewsheet rvs, boolean isVSWizard, Processor processor)
   {
      CubeTreeModelBuilder model = new CubeTreeModelBuilder();
      model.engine = engine;
      model.processor = processor;
      model.rvs = rvs;
      model.runtime = rvs.isRuntime();
      model.principal = principal;
      model.user = model.principal == null ? null : IdentityID.getIdentityIDFromKey(model.principal.getName());
      // get properties for cube table
      Properties props = new Properties();

      if(prefix != null) {
         props.setProperty("prefix", prefix);
      }

      if(source != null) {
         props.setProperty("source", source);
      }

      model.props = props;
      model.cube = source != null && source.indexOf(Assembly.CUBE_VS) >= 0 && !isVSWizard;
      model.isWizard = isVSWizard;

      // get viewsheet
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return model;
      }

      model.info = info;
      String aname = info.getAbsoluteName2();
      aname = aname == null ? info.getAbsoluteName() : aname;
      int idx = aname.lastIndexOf(".");

      if(idx != -1) {
         vs = (Viewsheet) vs.getAssembly(aname.substring(0, idx));
      }

      model.vs = vs;
      model.baseWS = vs.getBaseWorksheet();
      model.baseEntry = vs.getBaseEntry();
      return model;
   }

   /**
    * Get cube tree mode, equals call getCubeTreeModel(true).
    */
   public AssetTreeModel getCubeTreeModel() throws Exception {
      return getCubeTreeModel(true);
   }

   /**
    * Get cube tree model.
    * @param bindToFirst identify to move the table in binding to first.
    */
   public AssetTreeModel getCubeTreeModel(boolean bindToFirst) throws Exception
   {
      if(rvs == null || info == null || vs == null || engine == null) {
         return null;
      }

      if(baseEntry != null && baseWS != null && baseEntry.isWorksheet()) {
         IndexedStorage store = engine.getStorage(baseEntry);

         // optimization, loading a ws may be time consuming
         if(store != null && store.lastModified() > rvs.getLastReset()) {
            Worksheet nw = (Worksheet) engine.getSheet(baseEntry, principal,
                                                       false, AssetContent.ALL);

            // worksheet has changed
            if(nw != null && nw.getLastModified() > baseWS.getLastModified()) {
               processor.baseWorksheetChanged();
            }
         }
      }

      if(!isValidAggregateInfo()) {
         processor.aggInfoInvalid(info);
      }

      AssetTreeModel model = runtime && cube ? getBaseModel() :
                                               getModel(bindToFirst);

      if((!runtime || (runtime && (cube || sinfo == null))) &&
         !(info instanceof TableVSAssemblyInfo) &&
         !(info instanceof CalcTableVSAssemblyInfo) && !isWizard)
      {
         Node rootNode = (Node) model.getRoot();
         Node cubeNode = VSEventUtil.getCubeTree(principal, props, vs);
         fixCubeNode(cubeNode);
         expandSourceCube(cubeNode);

         if(cubeNode.getNodeCount() > 0) {
            rootNode.addNode(cubeNode);
         }
      }

      return model;
   }

   private void expandSourceCube(Node cubes) {
      String source = sinfo == null ? null : sinfo.getSource();
      Node[] nodes = cubes == null ? null : cubes.getNodes();

      if(nodes == null || source == null || !source.startsWith(Assembly.CUBE_VS)) {
         return;
      }

      AssetEntry cubesEntry = cubes.getEntry();
      cubesEntry.setProperty("expanded", "true");

      source = source.substring(Assembly.CUBE_VS.length());

      for(int i = 0; i < nodes.length; i++) {
         Node node = nodes[i];
         AssetEntry entry = node.getEntry();

         if(entry != null && Tool.equals(entry.getPath(), source)) {
            entry.setProperty("expanded", "true");
            Node[] subNodes = node.getNodes();

            for(int j = 0; subNodes != null && j < subNodes.length; j++) {
               Node subNode = subNodes[j];
               AssetEntry subEntry = subNode.getEntry();

               if(subEntry != null) {
                  subEntry.setProperty("expanded", "true");
               }
            }

            break;
         }
      }
   }

   /**
    * Create measures.
    */
   public static void addExpressionMeasureNode(Node cubeNode, Viewsheet vs) {
      CalculateRef[] calcs = vs == null ? null :
         vs.getCalcFields(cubeNode.getEntry().getName());

      if(calcs == null) {
         return;
      }

      for(int i = 0; i < calcs.length; i++) {
         CalculateRef gref = calcs[i];

         // cube only support create measure
         if(gref.isBaseOnDetail()) {
            continue;
         }

         ExpressionRef eref = (ExpressionRef) gref.getDataRef();
         String expression = eref.getExpression();

         if(expression == null || expression.trim().equals("")) {
            continue;
         }

         String name = eref.getName();
         String path = eref.getDataSource();

         AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.FOLDER, path, null);
         Node tNode = cubeNode.getNodeByEntry(entry);

         if(tNode != null) {
            int idx = path.lastIndexOf("/");
            String path0 = path.substring(0, idx);
            path0 = Assembly.CUBE_VS + path0;

            path = path + "/" + name;
            AssetEntry entry0 = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
               AssetEntry.Type.COLUMN, path, null);

            entry0.setProperty("refType",
               (DataRef.CUBE_MEASURE | DataRef.AGG_CALC)+ "");
            entry0.setProperty(AssetEntry.CUBE_COL_TYPE + "",
               AssetEntry.MEASURES + "");
            entry0.setProperty("table", path0);
            entry0.setProperty("assembly", path0);
            entry0.setProperty("attribute", name);
            entry0.setProperty("sqlServer", "true");
            entry0.setProperty("expression", expression);
            AssetEventUtil.appendCalcProperty(entry0, gref);
            Node nNode = new Node(entry0);
            nNode.setOneOff(true);
            tNode.addNode(nNode);
         }
      }
   }

   /**
    * Fix the cube tree node add mapping status property.
    */
   private void fixCubeNode(Node cubeNode) {
      AssetEntry[] entrys = cubeNode.getEntrys();

      for(Map.Entry<String, String> mentry : statusMap.entrySet()) {
         String name = mentry.getKey();

         for(AssetEntry entry : entrys) {
            if(!name.equals(entry.getProperty("alias"))){
               continue;
            }

            String tname = entry.getProperty("table");
            entry.setProperty("mappingStatus",
                              getMappingStatus(name, tname, false));

            if(getMappingStatus(name, tname, false) != null) {
               entry.setProperty("isGeo", true + "");
            }
         }
      }
   }

   /**
    * Check if the aggregate info is up-to-date one. If the base query is
    * changed, the aggregate info should be updated.
    */
   private boolean isValidAggregateInfo() {
      if(processor.infoIsNull(info)) {
         return true;
      }

      if(sinfo == null || ainfo == null || ainfo.isEmpty()) {
         return true;
      }

      TableAssembly table = getTableAssembly();

      if(table == null || AssetUtil.isCubeTable(table) &&
         !AssetUtil.isWorksheetCube(table))
      {
         return true;
      }

      ColumnSelection columns = table.getColumnSelection(true).clone();
      VSUtil.appendCalcFields(columns, table.getName(), vs, false);
      List<String> allcols = new ArrayList<>();
      ArrayList<String> allColTypes = new ArrayList<>();
      boolean changed = (ainfo.getGroupCount() + ainfo.getAggregateCount()) !=
         columns.getAttributeCount();

      for(int i = 0; i < columns.getAttributeCount() && !changed; i++) {
         ColumnRef cref = (ColumnRef) columns.getAttribute(i);
         allcols.add(VSUtil.getVSColumnRef(cref).getName());
         allColTypes.add(cref.getDataType());
      }

      String colName;
      String dtype;

      for(int i = 0; i < ainfo.getGroupCount() && !changed; i++) {
         colName = ainfo.getGroup(i).getName();
         dtype = ainfo.getGroup(i).getDataRef() != null ?
            ainfo.getGroup(i).getDataRef().getDataType() : null;
         int index = allcols.indexOf(colName);

         if(index < 0 || !Tool.equals(dtype, allColTypes.get(index))) {
            changed = true;
            break;
         }
      }

      for(int i = 0; i < ainfo.getAggregateCount() && !changed; i++) {
         colName = ainfo.getAggregate(i).getName();
         dtype = ainfo.getAggregate(i).getDataRef() != null ?
            ainfo.getAggregate(i).getDataRef().getDataType() : null;
         int index = allcols.indexOf(colName);

         if(index < 0 || !Tool.equals(dtype, allColTypes.get(index))) {
            changed = true;
            break;
         }
      }

      if(changed) {
         processor.clearAggregateInfo(info);
         fixAggregateInfo(table);
         return false;
      }

      return true;
   }

   /**
    * Get table assembly by source info.
    */
   private TableAssembly getTableAssembly() {
      return VSEventUtil.getTableAssembly(vs, sinfo, engine, null);
   }

   /**
    * Source info changed, fix the aggregate info of the chart info.
    */
   private void fixAggregateInfo(TableAssembly table) {
      AggregateInfo oainfo = ainfo;
      ainfo = processor.createAggregateInfo(info);

      // internal generated field (e.g. Total@col, Range@col) should not be shown on wizard
      // tree and list, but should be on full editor so users can modify the binding.
      boolean includePreparedCalc = !isWizard && info instanceof ChartVSAssemblyInfo &&
         Objects.equals(((ChartVSAssemblyInfo) info).getSourceInfo().getSource(), table.getName());
      VSEventUtil.createAggregateInfo(table, ainfo, oainfo, vs, !includePreparedCalc);
   }

   /*
    * Refresh chart tree.
    */
   private AssetTreeModel getModel(boolean bindToFirst) {
      AssetTreeModel model = getBaseModel();
      ArrayList assemblies = new ArrayList();

      if(runtime) {
         addBaseQueries(assemblies);
         addLMEntryLevel(assemblies, (Node) model.getRoot());
         addCurrentQueryNode(assemblies, (Node) model.getRoot());
      }
      else {
         addBaseQueries(assemblies);
         addLMEntryLevel(assemblies, (Node) model.getRoot());

         if(bindToFirst) {
            addCurrentQueryNode(assemblies, (Node) model.getRoot());
         }

         String src = props.getProperty("source");

         if(src == null || src.length() == 0) {
            addBaseQueryNodes(assemblies, (Node) model.getRoot());
         }
      }

      return model;
   }

   /**
    * Processor interface, to regist some functions for specified processing
    * for different event.
    */
   public static interface Processor {
      /**
       * Base worksheet changed.
       */
      public void baseWorksheetChanged();

      /**
       * Aggregate info is invalid.
       * @param info the assembly which is in processing's info.
       */
      public void aggInfoInvalid(VSAssemblyInfo info) throws Exception;

      /**
       * Check info is null.
       * @param info the assembly which is in processing's info.
       */
      public boolean infoIsNull(VSAssemblyInfo info);

      /**
       * Create aggregate infomation.
       * @param info the assembly which is in processing's info.
       */
      public AggregateInfo createAggregateInfo(VSAssemblyInfo info);

      /**
       * Clear aggregate info.
       * @param info the assembly which is in processing's info.
       */
      public void clearAggregateInfo(VSAssemblyInfo info);
   }

   private Processor processor;
   private boolean runtime; // runtime mode?
   private Properties props; // properties for cube
   private boolean cube; // is cube?
}
