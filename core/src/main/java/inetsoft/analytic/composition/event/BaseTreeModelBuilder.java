/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.analytic.composition.event;

import inetsoft.report.composition.AssetTreeModel;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base tree model builder.
 */
public abstract class BaseTreeModelBuilder {
   /**
    * Check if the target field is an aggregate expression
    */
   private static boolean isAggregateExpression(DataRef field, XSourceInfo sourceInfo,
                                               Principal user)
   {
      if(field == null || field.getAttribute() == null) {
         return false;
      }

      if(sourceInfo == null || sourceInfo.getType() != XSourceInfo.MODEL) {
         return false;
      }

      try {
         List<String> entities = Arrays.asList(XUtil.getEntities(sourceInfo.getPrefix(),
                                                                 sourceInfo.getSource(),
                                                                 user, true));

         String entity = field.getEntity();
         String attributeName = field.getAttribute();

         if(entity == null) {
            int idx = attributeName.indexOf(":");

            if(idx >= 0) {
               entity = attributeName.substring(0, idx);
               attributeName = attributeName.substring(idx + 1);
            }
         }

         if(!entities.contains(entity)) {
            return false;
         }

         XAttribute[] attributes = XUtil.getAttributes(
            sourceInfo.getPrefix(), sourceInfo.getSource(), entity,
            user, true, true);

         for(XAttribute attribute : attributes) {
            if(attribute instanceof ExpressionAttribute &&
               ((ExpressionAttribute) attribute).isAggregateExpression() &&
               Tool.equals(attributeName, attribute.getName()))
            {
               return true;
            }
         }
      }
      catch(Exception ignore) {
      }

      return false;
   }

   /**
    * Set aggregate info.
    */
   public void setAggregateInfo(AggregateInfo ainfo) {
      this.ainfo = ainfo;
   }

   /**
    * Set source info.
    */
   public void setSourceInfo(SourceInfo sinfo) {
      this.sinfo = sinfo;
   }

   /**
    * Get a base tree model that only has a root node.
    */
   protected AssetTreeModel getBaseModel() {
      AssetTreeModel model = new AssetTreeModel();
      AssetEntry root = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.FOLDER, "root", null);
      AssetTreeModel.Node rootNode = new AssetTreeModel.Node(root);
      rootNode.setRequested(true);
      model.setRoot(rootNode);

      return model;
   }

   /**
    * Add all base queries to the specified list assemblies.
    */
   protected void addBaseQueries(List assemblies) {
      if(baseEntry != null) {
         // check accessible
         boolean acs = VSEventUtil.checkBaseWSPermission(vs, principal,
            engine, ResourceAction.READ);

         if(baseWS != null) {
            Assembly[] wass = acs ? baseWS.getAssemblies() : new Assembly[0];

            for(int i = 0; i < wass.length; i++) {
               if(wass[i] instanceof TableAssembly) {
                  TableAssembly table = (TableAssembly) wass[i];

                  if(table.isVisibleTable()) {
                     assemblies.add(wass[i]);
                  }
               }
               else {
                  assemblies.add(wass[i]);
               }
            }

            if(!acs && sinfo != null && sinfo.getType() == XSourceInfo.ASSET) {
               Assembly assembly = baseWS.getAssembly(sinfo.getSource());

               if(assembly != null) {
                  assemblies.add(assembly);
               }
            }

            Collections.sort(assemblies, new VSEventUtil.WSAssemblyComparator(baseWS));
         }
      }
   }

   /**
    * Add current query node.
    */
   protected void addCurrentQueryNode(List assemblies, AssetTreeModel.Node rootNode) {
      if(sinfo == null || sinfo.getType() != XSourceInfo.ASSET) {
         return;
      }

      Iterator iter = assemblies.iterator();

      while(iter.hasNext()) {
         Assembly assembly = (Assembly) iter.next();

         if(!assembly.isVisible()) {
            continue;
         }

         if(baseEntry != null && baseEntry.isLogicModel() &&
            baseEntry.getProperty("pesdo2") == null)
         {
            List list = VSEventUtil.createPseudoAssemblies(engine, baseEntry,
               principal);
            baseEntry.setProperty("pesdo2", "created");
            addCurrentQueryNode(list, rootNode);
            iter.remove();
            continue;
         }

         if(assembly instanceof TableAssembly) {
            TableAssembly table = (TableAssembly) assembly;

            if(Tool.equals(sinfo.getSource(), table.getName()) ||
               baseEntry != null && baseEntry.isLogicModel())
            {
               addCurrentQueryNode(table, rootNode);
               iter.remove();
            }
         }
      }

      if(baseEntry != null) {
         baseEntry.setProperty("pesdo2", null);
      }
   }

   /**
    * Create base query nodes.
    */
   protected void addBaseQueryNodes(List assemblies, AssetTreeModel.Node rootNode) {
      if(baseEntry == null) {
         return;
      }

      Iterator iter = assemblies.iterator();

      while(iter.hasNext()) {
         Assembly assembly = (Assembly) iter.next();

         if(!assembly.isVisible()) {
            continue;
         }

         if(baseEntry.isLogicModel() && baseEntry.getProperty("pesdo") == null) {
            TableAssembly table = (TableAssembly) assembly;
            List list = VSEventUtil.createPseudoAssemblies(engine, baseEntry,
               principal);
            baseEntry.setProperty("pesdo", "created");
            addBaseQueryNodes(list, rootNode);

            continue;
         }

         if(assembly instanceof TableAssembly) {
            TableAssembly table = (TableAssembly) assembly;

            // apply bindToFirst set to false
            if(sinfo != null && sinfo.getType() == XSourceInfo.ASSET &&
               Tool.equals(sinfo.getSource(), table.getName()) ||
               baseEntry.isLogicModel())
            {
               addCurrentQueryNode(table, rootNode);
            }
            else {
               addNormalQueryNode(table, false, rootNode);
            }
         }
      }

      baseEntry.setProperty("pesdo", null);
   }

   /**
    * Apply properties for table node.
    * @param table the table assembly.
    * @param tentry the table asset entry.
    * @param current is current table.
    */
   protected void applyTableNodeProperties(TableAssembly table, AssetEntry tentry,
                                           String queryType, boolean current) {
      if(baseEntry != null && baseEntry.isLogicModel()) {
         tentry.setProperty("table", baseEntry.getName());
         tentry.setProperty("sourceType", AssetEntry.Type.LOGIC_MODEL + "");
      }

      tentry.setProperty("Tooltip", table.getProperty("Tooltip"));
      tentry.setProperty(AssetEntry.QUERY_TYPE, queryType);
      tentry.setProperty(AssetEntry.CURRENT_QUERY, current + "");
      tentry.setProperty("source", VSEventUtil.BASE_WORKSHEET);
      tentry.setProperty("embedded",
         (VSEventUtil.isEmbeddedDataSource(table) &&
         !(table instanceof SnapshotEmbeddedTableAssembly)) + "");

      // expand current binding query.
      if(sinfo != null && table.getName().equals(sinfo.getSource())) {
         tentry.setProperty("expanded", "true");
      }
   }

   /**
    * Add normal query node.
    */
   protected void addNormalQueryNode(TableAssembly table, boolean current,
                                     AssetTreeModel.Node rootNode) {
      AssetEntry tentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.TABLE, "/baseWorksheet/" + table.getName(), user);
      AssetTreeModel.Node tnode = new AssetTreeModel.Node(tentry);
      applyTableNodeProperties(table, tentry, AssetEntry.NORMAL, current);

      if(baseEntry.isWorksheet()) {
         tentry.setProperty("Tooltip", table.getDescription());
      }

      appendColumnNodes(table, tnode, current, false);

      if(rootNode.getNode(tnode.getEntry()) != null) {
         for(int i = 0; i < rootNode.getNodeCount(); i++) {
            if(Tool.equals(rootNode.getNodes()[i], tnode)) {
               rootNode.removeNode(i);
               break;
            }
         }
      }

      rootNode.addNode(tnode);
   }

   /**
    * Create query columns node.
    */
   private void createQueryColumnNode(AssetTreeModel.Node pnode, ColumnRef column, int type,
                                      TableAssembly assembly, boolean current,
                                      int colType, String ppath) {
      String name = column.getAlias();
      name = name == null || name.length() == 0 ? column.getAttribute() :
         name;

      AssetEntry centry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN,
         ppath + VSUtil.trimEntity(name, null), user);
      applyColumnNodeProperties(assembly, centry, column, name, type, colType,
         current);
      centry.setProperty("refType", "" + column.getRefType());
      centry.setProperty("formula", column.getDefaultFormula());
      centry.setProperty("Tooltip", column.getDescription());
      AssetEventUtil.appendCalcProperty(centry, column);
      AssetTreeModel.Node cnode = new AssetTreeModel.Node(centry);
      pnode.addNode(cnode);
   }

   /**
    * Apply column node properties.
    * @param tbl table assembly.
    * @param centry the column entry.
    * @param column the data column.
    * @param name the column name.
    * @param type the entry type.
    * @param colType the data column type, dimension or measure.
    * @param current is current node.
    */
   protected void applyColumnNodeProperties(TableAssembly tbl, AssetEntry centry,
                                            DataRef column, String name, int type,
                                            int colType, boolean current) {
      String tname = tbl.getName();
      centry.setProperty("dtype", column.getDataType());

      if(tbl.getAggregateInfo() != null && !tbl.getAggregateInfo().isEmpty()) {
         ColumnSelection cols = tbl.getColumnSelection();
         DataRef originalCol = cols.getAttribute(column.getAttribute());

         if(originalCol != null) {
            GroupRef group = tbl.getAggregateInfo().getGroup(originalCol.getName());

            if(group != null && group.getNamedGroupInfo() != null &&
               !group.getNamedGroupInfo().isEmpty())
            {
               centry.setProperty("dtype", XSchema.STRING);
            }
         }
      }

      centry.setProperty(AssetEntry.CURRENT_QUERY, current + "");
      centry.setProperty(AssetEntry.CUBE_COL_TYPE, type == -1 ? "" : type + "");

      DataRef baseRef = AssetUtil.getBaseAttribute(column);

      if(baseRef instanceof AttributeRef) {
         centry.setProperty("sqltype", String.valueOf(((AttributeRef) baseRef).getSqlType()));
      }

      if(info instanceof ChartVSAssemblyInfo) {
         ChartVSAssemblyInfo cinfo = (ChartVSAssemblyInfo) info;
         boolean isGeo = cinfo.getVSChartInfo().getGeoColumns().getAttribute(name) != null;
         centry.setProperty("isGeo", isGeo + "");
      }

      if(baseEntry != null && baseEntry.isLogicModel()) {
         centry.setProperty("assembly", baseEntry.getName());
         centry.setProperty("table", baseEntry.getName());
         centry.setProperty("sourceType", AssetEntry.Type.LOGIC_MODEL + "");

         SourceInfo sourceInfo = new SourceInfo(XSourceInfo.MODEL, baseEntry.getProperty("prefix"),
                                                baseEntry.getProperty("source"));
         boolean aggExpr = isAggregateExpression(column, sourceInfo,
                                                      ThreadContext.getContextPrincipal());

         if(aggExpr) {
            colType = DataRef.AGG_EXPR;
         }
      }
      else {
         centry.setProperty("assembly", tname);
      }

      centry.setProperty("attribute", name);
      centry.setProperty("source", VSEventUtil.BASE_WORKSHEET);
      centry.setProperty("type", XSourceInfo.ASSET + "");
      centry.setProperty("refType", colType + "");
      centry.setProperty("embedded",
         VSEventUtil.isEmbeddedDataSource(tbl) + "");
      centry.setProperty("mappingStatus", getMappingStatus(name, tname,
         baseEntry != null && baseEntry.isLogicModel() && current));

      if(column != null && column instanceof DataRefWrapper) {
         DataRef ref = ((DataRefWrapper) column).getDataRef();

         if(ref instanceof ColumnRef) {
            centry.setProperty("Tooltip", ((ColumnRef) ref).getDescription());
         }
      }
   }

   /*
    * Append dimensions and measures node.
    * @param addLMEntryLevel is add node to logic model.
    */
   protected void appendColumnNodes(TableAssembly assembly, AssetTreeModel.Node pnode,
                                    boolean current, boolean addLMEntryLevel) {
      ColumnSelection columns = new ColumnSelection();
      List<ColumnRef> list = new ArrayList<>();

      if(!addLMEntryLevel) {
         columns = assembly.getColumnSelection(true);
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) columns.getAttribute(i);

         if(!(ref instanceof CalculateRef)) {
            if(ref.getAttribute().length() == 0 && ref.getAlias() == null) {
               ref.setAlias("Column [" + i + "]");
            }

            ref = AssetEventUtil.isCubeType(ref.getRefType()) ? ref : VSUtil.getVSColumnRef(ref);
            list.add(ref);
         }
      }

      // internal generated field (e.g. Total@col, Range@col) should not be shown on wizard
      // tree and list, but should be on full editor so users can modify the binding.
      VSUtil.appendCalcFields(list, assembly, vs, false,
         isWizard || !(info instanceof ChartVSAssemblyInfo));

      if(list.size() == 0) {
         return;
      }

      String aname = addLMEntryLevel ? "" : (assembly.getName() + "/");
      list.sort(new VSUtil.DataRefComparator());

      if(info instanceof TableVSAssemblyInfo) {
         for(int i = 0; i < list.size(); i++) {
            String path = "/baseWorksheet/" + aname + "/";
            ColumnRef column = list.get(i);
            int reftype = VSUtil.isAggregateCalc(column) ? DataRef.AGG_CALC : DataRef.NONE;

            createQueryColumnNode(pnode, column, -1, assembly, current, reftype, path);
         }
      }
      else {
         AssetEntry dentries = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.FOLDER, "/baseWorksheet/" + aname +
            catalog.getString("Dimensions"), user);
         AssetTreeModel.Node dnodes = new AssetTreeModel.Node(dentries);
         AssetEntry mentries = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.FOLDER, "/baseWorksheet/" + aname +
            catalog.getString("Measures"), user);
         AssetTreeModel.Node mnodes = new AssetTreeModel.Node(mentries);
         pnode.addNode(dnodes);
         pnode.addNode(mnodes);
         String dimPath = "/baseWorksheet/" + aname;
         String aggPath = "/baseWorksheet/" + aname;
         dimPath += isWizard ? "/" : catalog.getString("Dimensions") + "/";
         aggPath += isWizard ? "/" : catalog.getString("Measures") + "/";

         for(int i = 0; i < list.size(); i++) {
            ColumnRef column = list.get(i);
            String type = column.getDataType();
            int reftype = VSUtil.isAggregateCalc(column) ? DataRef.AGG_CALC : DataRef.NONE;

            if(VSEventUtil.isMeasure(column)) {
               createQueryColumnNode(mnodes, column, AssetEntry.MEASURES,
                  assembly, current, reftype, aggPath);
            }
            else if(XSchema.isDateType(type)) {
               createQueryColumnNode(dnodes, column, AssetEntry.DATE_DIMENSIONS,
                  assembly, current, reftype, dimPath);
            }
            else {
               createQueryColumnNode(dnodes, column, AssetEntry.DIMENSIONS,
                  assembly, current, reftype, dimPath);
            }

            if(column.getAttribute().length() == 0 && column.getAlias().startsWith("Column [")) {
               column.setAlias(null);
            }
         }
      }
   }

   /**
    * Check if the specified column is all mapped.
    */
   protected String getMappingStatus(String name, String tname,
                                   boolean isCurrentLogicModel) {
      // if the node is current node, and it's a logic model, don't check
      // the table name and the source
      if(sinfo == null ||
         !tname.equals(sinfo.getSource()) && !isCurrentLogicModel)
      {
         return null;
      }

      return statusMap.get(name);
   }

   /**
    * Set specified column mapping status.
    */
   public void setMappingStatus(String name, boolean allMapped) {
      statusMap.put(name, allMapped + "");
   }

   /**
    * Add node to logic model.
    */
   protected void addLMEntryLevel(List assemblies, AssetTreeModel.Node rootNode) {
      Iterator iter = assemblies.iterator();

      while(iter.hasNext()) {
         Assembly assembly = (Assembly) iter.next();

         if(!assembly.isVisible()) {
            continue;
         }

         if(baseEntry.isLogicModel() && assembly instanceof TableAssembly) {
            if(ainfo != null && !ainfo.isEmpty() && sinfo != null &&
               sinfo.getSource().indexOf(Assembly.CUBE_VS) < 0)
            {
               addAggregateNode((TableAssembly) assembly, rootNode, true);
            }
            else {
               appendColumnNodes((TableAssembly) assembly, rootNode, true, true);
            }
         }
      }
   }

   /**
    * Add current query node.
    */
   protected void addCurrentQueryNode(TableAssembly table, AssetTreeModel.Node rootNode) {
      if(ainfo != null && !ainfo.isEmpty() && sinfo != null &&
         (!AssetUtil.isCubeTable(table) || AssetUtil.isWorksheetCube(table)))
      {
         addAggregateNode(table, rootNode, false);
      }
      // @by larryl, the AggregateInfo should contain a complete list
      // (checked by isValidAggregateInfo), so don't override the aggregate
      // info (user specified measure/dimension).
      else {
         addNormalQueryNode(table, true, rootNode);
      }
   }

   /**
    * Add aggregate query node, including the pre-defined aggregate info or the
    * aggregate info in chart info.
    * @param addLMEntryLevel is add node to logic model.
    */
   private void addAggregateNode(TableAssembly table, AssetTreeModel.Node rootNode,
                                 boolean addLMEntryLevel)
   {
      ColumnSelection columns = new ColumnSelection();
      boolean lmodel = baseEntry != null && baseEntry.isLogicModel();

      if(addLMEntryLevel) {
         VSUtil.appendCalcFieldsForTree(columns, baseEntry.getName(), vs);

         if(columns.getAttributeCount() == 0) {
            return;
         }

         List calcList = columns.stream().filter(a -> !a.getName().startsWith("Range@"))
            .collect(Collectors.toList());

         if(calcList.size() == 0) {
            return;
         }
      }
      else {
         columns = table.getColumnSelection(true).clone();
         columns = VSUtil.removePreparedCalcFields(columns);
         VSUtil.appendCalcFieldsForTree(columns, table.getName(), vs);
      }

      String source = lmodel ? table.getName() : sinfo.getSource();
      String queryType = AssetEntry.NORMAL;
      AssetTreeModel.Node preNode = new AssetTreeModel.Node();

      if(!addLMEntryLevel) {
         AssetEntry preEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.TABLE, "/baseWorksheet/" + source, user);
         applyTableNodeProperties(table, preEntry, queryType, true);
         preNode = new AssetTreeModel.Node(preEntry);
         rootNode.addNode(preNode);
      }

      String tablename = addLMEntryLevel ? "" : (table.getName() + "/");
      AssetEntry dentries = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.FOLDER, "/baseWorksheet/" + tablename +
         catalog.getString("Dimensions"), user);
      AssetEntry mentries = new AssetEntry(AssetRepository.QUERY_SCOPE,
         AssetEntry.Type.FOLDER, "/baseWorksheet/" + tablename +
         catalog.getString("Measures"), user);

      // expand current binding query.
      if(sinfo != null && !lmodel && table.getName().equals(sinfo.getSource())) {
         dentries.setProperty("expanded", "true");
         mentries.setProperty("expanded", "true");
      }

      AssetTreeModel.Node dnodes = new AssetTreeModel.Node(dentries);
      AssetTreeModel.Node mnodes = new AssetTreeModel.Node(mentries);
      AssetEntry dentry, mentry;
      AssetTreeModel.Node dnode, mnode;
      AggregateInfo currentInfo = ainfo;

      if(addLMEntryLevel) {
         rootNode.addNode(dnodes);
         rootNode.addNode(mnodes);
      }
      else {
         preNode.addNode(dnodes);
         preNode.addNode(mnodes);
      }

      String dimPath = "/baseWorksheet/" + tablename;
      dimPath += isWizard ? "/" : catalog.getString("Dimensions") + "/";

      for(int i = 0; i < currentInfo.getGroupCount(); i++) {
         GroupRef gref = currentInfo.getGroup(i);

         if(!isColumnsContainsRef(columns, gref.getDataRef())) {
            continue;
         }

         DataRef ref = gref.getDataRef();
         boolean isPreparedCalc = VSUtil.isPreparedCalcField(ref);

         if(isWizard && isPreparedCalc) {
            continue;
         }

         int reftype = VSUtil.isAggregateCalc(gref.getDataRef()) ?
            DataRef.AGG_CALC : DataRef.NONE;

         dentry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN,
            dimPath + VSUtil.trimEntity(gref.getName(), null), user);
         int type = XSchema.isDateType(gref.getDataType()) ?
            AssetEntry.DATE_DIMENSIONS : AssetEntry.DIMENSIONS;
         applyColumnNodeProperties(table, dentry, gref, gref.getName(),
            type, reftype, true);
         dentry.setProperty(AssetEntry.QUERY_TYPE, queryType);
         dentry.setProperty("isPreparedCalc", isPreparedCalc + "");
         ColumnRef cref = getColumnRef(columns, gref.getDataRef());
         AssetEventUtil.appendCalcProperty(dentry, cref);
         dnode = new AssetTreeModel.Node(dentry);
         dnodes.addNode(dnode);
      }

      String aggPath = "/baseWorksheet/" + tablename;
      aggPath += isWizard ? "/" : catalog.getString("Measures") + "/";

      for(int i = 0; i < currentInfo.getAggregateCount(); i++) {
         AggregateRef aref = currentInfo.getAggregate(i);
         DataRef ref = aref.getDataRef();

         if(ref instanceof CalculateRef && (((CalculateRef) ref).isDcRuntime() ||
            DateComparisonUtil.isDCCalcDatePartRef(ref.getName())))
         {
            continue;
         }

         if(!isColumnsContainsRef(columns, aref.getDataRef())) {
            continue;
         }

         int reftype = VSUtil.isAggregateCalc(aref.getDataRef()) ?
            DataRef.AGG_CALC : DataRef.NONE;

         mentry = new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN,
            aggPath + VSUtil.trimEntity(aref.getName(), null), user);
         applyColumnNodeProperties(table, mentry, aref, aref.getName(),
            AssetEntry.MEASURES, reftype, true);
         // override dtype setted in applyColumnNodeProperties,
         // get the data type from data ref, but not get it from the aggregate
         // ref which depending on formula
         mentry.setProperty("dtype",
            ((ColumnRef) aref.getDataRef()).getDataType());
         mentry.setProperty(AssetEntry.QUERY_TYPE, queryType);

         ColumnRef cref = getColumnRef(columns, aref.getDataRef());

         if(cref != null) {
            int refType = DataRef.NONE;
            boolean aggExpr = (DataRef.AGG_EXPR + "").equals(mentry.getProperty("refType"));

            if(cref.getRefType() == DataRef.DIMENSION) {
               refType = DataRef.MEASURE;
            }
            else {
               refType = cref.getRefType();
            }

            if(VSUtil.isAggregateCalc(cref)) {
               refType |= DataRef.AGG_CALC;
            }

            if(aggExpr) {
               refType |= DataRef.AGG_EXPR;
            }

            mentry.setProperty("refType", refType + "");
            mentry.setProperty("formula", cref.getDefaultFormula());
         }

         AssetEventUtil.appendCalcProperty(mentry, cref);
         mnode = new AssetTreeModel.Node(mentry);
         mnodes.addNode(mnode);
      }
   }

   /**
    * Indicates if the columnselection contains this ref.
    */
   private boolean isColumnsContainsRef(ColumnSelection columns, DataRef ref) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef dref = columns.getAttribute(i);

         if(isSameColumn(ref, dref)) {
            return true;
         }
      }

      return false;
   }

   private boolean isSameColumn(DataRef ref0, DataRef ref1) {
      if(ref0 == null || ref1 == null) {
         return false;
      }

      return Tool.equals(ref0.getName(), ref1.getName()) ||
         Tool.equals(ref0.getAttribute(), ref1.getAttribute());
   }

   /**
    * Get columnref from column selection.
    */
   private ColumnRef getColumnRef(ColumnSelection columns, DataRef ref) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef dref = columns.getAttribute(i);

         if(dref instanceof ColumnRef &&
            dref.getAttribute().equals(ref.getAttribute()))
         {
            return (ColumnRef) dref;
         }
      }

      return null;
   }

   protected IdentityID user;
   protected Viewsheet vs;
   protected SourceInfo sinfo;
   protected Worksheet baseWS;
   protected Principal principal;
   protected VSAssemblyInfo info;
   protected AssetEntry baseEntry;
   protected RuntimeViewsheet rvs;
   protected AssetRepository engine;
   protected AggregateInfo ainfo;
   protected boolean isWizard = false;
   protected Catalog catalog = Catalog.getCatalog();
   protected Map<String, String> statusMap = new HashMap();
}
