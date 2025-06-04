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
package inetsoft.web.composer;

import inetsoft.report.TableLens;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.uql.xmla.XMLAUtil;
import inetsoft.util.*;
import inetsoft.web.composer.model.BrowseDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

// BrowseDataEvent
public class BrowseDataController {
   /**
    * Get browsed data.
    * @param fld the data ref field.
    * @param source the source info.
    * @return the an object array, its length is 2, and obj[0] is the
    *  BrowsedData, obj[1] is an Boolean value, determine the query is
    *  a derived query.
    */
   private static Object getBrowsedData(DataRef fld, XSourceInfo source) {
      String xtype = fld == null ? XSchema.STRING : fld.getDataType();

      if(fld instanceof DataRefWrapper) {
         fld = ((DataRefWrapper) fld).getDataRef();
      }

      BrowsedData data = null;

      if(source != null && fld != null) {
         String query;
         String field;
         String table;
         String col;

         if(source.getType() == XSourceInfo.MODEL) {
            query = source.getSource() + "::" + source.getPrefix();
            String entity = fld.getEntity();
            String attr = fld.getAttribute();
            int idx = attr == null ? -1 : attr.indexOf(":");

            if(entity == null && idx != -1) {
               entity = attr.substring(0, idx);
               attr = attr.substring(idx + 1);
            }

            field = entity + "::" + attr;
            data = new BrowsedData(query, field, xtype,
                                   null, null, false);
         }
         else {
            query = source.getSource();
            field = fld.getName();

            data = new BrowsedData(query, field, xtype,
               null, null, false);
         }
      }

      return data;
   }

   /**
    * Process event.
    */
   public BrowseDataModel process(AssetQuerySandbox box) throws Exception {
      BrowseDataModel model;
      Worksheet ws = box.getWorksheet();

      if(column == null) {
         return null;
      }

      if(VSUtil.isVSAssemblyBinding(name)) {
         model = getVSAssemblyBoundData(box);
      }
      else if(dataSource != null) {
         model = executeByDataSource(box.getBrowseUser());
      }
      else {
         Assembly assembly = ws.getAssembly(name);

         if(!(assembly instanceof TableAssembly)) {
            return null;
         }

         ViewsheetSandbox viewsheetSandbox = box.getViewsheetSandbox();

         if(viewsheetSandbox != null) {
            Viewsheet viewsheet = viewsheetSandbox.getViewsheet();
            assembly = (TableAssembly) assembly.clone();
            VSAQuery.appendCalcField((TableAssembly) assembly, name, true, viewsheet);
         }

         model = executeByColumnCache(assembly);

         if(model == null) {
            model = executeByQuery(box, (TableAssembly) assembly);
         }
      }

      final UserMessage userMessage = Tool.getUserMessage();

      if(userMessage != null) {
         final String message = userMessage.getMessage();

         if(message != null && !message.isEmpty()) {
            if(isEmpty(model)) {
               // if data is returned, instead of treating as an error, just return data bug log the
               // message. otherwise the ui will show an error
               throw new MessageException(message, userMessage.getLogLevel());
            }

            LOG.warn(message);
         }
      }

      return model;
   }

   public boolean isDCDataDroup(ColumnRef ref) {
      return ref.getDataRef() instanceof NamedRangeRef &&
         ((NamedRangeRef) ref.getDataRef()).getNamedGroupInfo() instanceof DCNamedGroupInfo;
   }

   private static boolean isEmpty(BrowseDataModel data) {
      return !Optional.ofNullable(data)
         .map(BrowseDataModel::values)
         .filter(labels -> labels.length > 0)
         .isPresent();
   }

   /**
    * Execute data from column cache.
    */
   private BrowseDataModel executeByColumnCache(Assembly assembly) {
      if(sinfo == null && assembly instanceof BoundTableAssembly) {
         sinfo = ((BoundTableAssembly) assembly).getSourceInfo();
      }

      // only process model here, cause the column cache get data
      // will get all datas, but not apply conditions
      if(sinfo == null || sinfo.getSource() == null) {
         return null;
      }

      boolean model = sinfo.getType() == XSourceInfo.MODEL;

      // @by davyc, not model, but viewsheet direct source binding?
      // try to find the original logic model
      // fix bug1324611546732
      if(!model && !"true".equals(sinfo.getProperty("direct"))) {
         return null;
      }

      if(!model) {
         BoundTableAssembly original = getOriginalTableAssembly(assembly);

         if(original == null) {
            return null;
         }

         sinfo = original.getSourceInfo();
         column = findColumn(column, original.getColumnSelection(), false);

         if(sinfo.getType() != XSourceInfo.MODEL) {
            return null;
         }
      }

      BrowsedData bdata = (BrowsedData) getBrowsedData(column, sinfo);

      if(bdata == null) {
         return null;
      }

      String[][] data = bdata.getBrowsedData();
      data = data == null ? new String[0][0] : data;
      String[] values = data[0];

      if(values.length == 0) {
         return null;
      }

      String[] labels = data.length > 1 ? data[1] : data[0];
      return BrowseDataModel.builder()
         .values((Object[]) values)
         .labels((Object[]) labels)
         .build();
   }

   /**
    * Execute data from asset query sandbox.
    */
   private BrowseDataModel executeByQuery(AssetQuerySandbox box, TableAssembly table)
      throws Exception
   {
      // should not import vs class CalculateRef here, so use class name
//      if(column.getClass().getName().contains("CalculateRef")) {
//         // only get first column to browse
//         Enumeration<DataRef> refE = column.getExpAttributes();
//
//         if(refE != null && refE.hasMoreElements()) {
//            column = new ColumnRef(refE.nextElement());
//         }
//      }

      ViewsheetSandbox viewsheetSandbox = box.getViewsheetSandbox();
      ConditionList dateComparisonConds = new ConditionList();

      if(viewsheetSandbox != null && isDCDataDroup(column)) {
         dateComparisonConds = viewsheetSandbox.getDateComparisonConditions(vsAssemblyName);
      }

      box.setTimeLimited(name, false);
      table = (TableAssembly) table.clone();
      table.setPreConditionList(new ConditionList());
      table.setPreRuntimeConditionList(dateComparisonConds);
      table.setPostConditionList(new ConditionList());
      table.setPostRuntimeConditionList(new ConditionList());
      table.setRankingConditionList(new ConditionList());
      table.setAggregateInfo(new AggregateInfo());
      table.setDistinct(!isMongoDataSource(table));
      // since the result is distinct values, we should not limit the detail rows to
      // MAX_ROW_COUNT. it will be enforced when we extract the values later. (54103)
      table.setMaxRows(MAX_ROW_COUNT * 100);
      table.setMaxDisplayRows(MAX_ROW_COUNT * 100);

      if(table instanceof CubeTableAssembly && table.getName().contains(Assembly.CUBE_VS)) {
         table.setProperty("noEmpty", "false");
      }

      Worksheet base = table.getWorksheet();
      TableAssembly btable = table;
      WorksheetWrapper wrapper = null;

      if(base instanceof WorksheetWrapper) {
         wrapper = (WorksheetWrapper) base;
         btable = (TableAssembly) wrapper.getWorksheet().getAssembly(table.getName());
      }

      ColumnSelection columns = table.getColumnSelection();
      ColumnSelection bcolumns = btable != null ? btable.getColumnSelection() : columns;
      Object constant = null;
      boolean browseable = true;

      // name of freehand daterange column not contains date option prefix, it will mix up with
      // the none date group column in the columnselection and lossing date option information,
      // so here add date option prefix to avoid issue (50186).
      if(column.getDataRef() instanceof DateRangeRef &&
         ((DateRangeRef) column.getDataRef()).getDataRef() != null &&
         !(table instanceof CubeTableAssembly))
      {
         DateRangeRef range = (DateRangeRef) column.getDataRef();
         DataRef attr = range.getDataRef();
         String rangeName = DateRangeRef.getName(attr.getName(), range.getDateOption());

         if(!Objects.equals(column.getName(), rangeName)) {
            range.setName(rangeName);
         }
      }

      // @by larryl, if browse data is triggered from vs, the column ref
      // is the column ref of the V_table, which doesn't have entity value.
      // But we need to get the distinct values from the base table, so we
      // find the column by name
      ColumnRef column2 = findColumn(column, bcolumns, table instanceof CubeTableAssembly);

      if(column2 != null) {
         column = column2;
      }

      // use strict data type to browse data in condition to make sure the condtion value
      // match the final data's type.
      if(column != null && column.getDataRef() instanceof DateRangeRef) {
         column = (ColumnRef) column.clone();
         ((DateRangeRef) column.getDataRef()).setStrictDataType(true);

         if(columns.containsAttribute(column)) {
            columns.removeAttribute(column);
         }

         columns.addAttribute(column);
      }

      SortRef sort = new SortRef(column);
      SortInfo sinfo = new SortInfo();

      sort.setOrder((column.getRefType() & DataRef.CUBE_TIME_DIMENSION) ==
         DataRef.CUBE_TIME_DIMENSION ? XConstants.SORT_NONE : XConstants.SORT_ASC);
      sinfo.addSort(sort);
      table.setSortInfo(sinfo);

      if(!columns.containsAttribute(column)) {
         column = (ColumnRef) column.clone();
         column.setVisible(false);
         columns.addAttribute(0, column);

         if(wrapper != null && table instanceof MirrorTableAssembly && !column.isExpression()) {
            MirrorTableAssembly mirror = (MirrorTableAssembly) table;
            String bname = mirror.getAssemblyName();
            btable = (TableAssembly) wrapper.getWorksheet().getAssembly(bname);

            if(btable != null) {
               ColumnSelection bcols = btable.getColumnSelection();

               for(int i = 0; i < bcols.getAttributeCount(); i++) {
                  ColumnRef bcol = (ColumnRef) bcols.getAttribute(i);
                  String cname = bcol.getAlias();

                  if(cname == null || cname.length() == 0) {
                     cname = bcol.getAttribute();
                  }

                  if(column.getAttribute().equals(cname)) {
                     btable = (TableAssembly) wrapper.getAssembly(bname);
                     bcols = btable.getColumnSelection();

                     if(!bcols.containsAttribute(bcol)) {
                        bcols.addAttribute(bcol);
                        btable.setProperty("no.validate", "true");
                        btable.resetColumnSelection();
                     }

                     break;
                  }
               }
            }
         }
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef tcolumn = (ColumnRef) columns.getAttribute(i);

         if(tcolumn.equals(column)) {
            tcolumn.setVisible(true);

            if(!isBrowseable(table, tcolumn, box.getUser())) {
               browseable = false;
            }
            else if(tcolumn.isExpression()) {
               ExpressionRef exp = (ExpressionRef) column.getDataRef();
               String script = exp.getExpression();
               constant = getConstant(script);
            }
         }
         else {
            tcolumn.setVisible(false);
         }
      }

      table.setProperty("no.validate", "true");
      ArrayList<String> labels = new ArrayList<>();
      final BrowseDataModel.Builder builder = BrowseDataModel.builder();

      if(constant != null) {
         labels.add(Tool.toString(constant));
      }
      else if(browseable) {
         if(table instanceof BoundTableAssembly) {
            ((BoundTableAssembly) table).removeConditionAssemblies();
         }

         int mode = AssetQuerySandbox.BROWSE_MODE;
         VariableTable vtable = box.getVariableTable().clone();
         table.resetColumnSelection();
         box.resetDefaultColumnSelection(table.getName());
         AssetQuery query = AssetQuery.createAssetQuery(table, mode, box, false, -1L, true, false);
         TableLens data = query.getTableLens(vtable);
         data.moreRows(MAX_ROW_COUNT);
         data = AssetQuery.shuckOffFormat(data);
         box.resetDefaultColumnSelection(table.getName());
         data = AssetQuery.shuckOffFormat(data);

         if(data.getColCount() == 1) {
            if(AssetUtil.isCubeTable(table)) {
               data = new VSCubeTableLens(data, table.getColumnSelection(true));
            }

            Set added = new HashSet();

            for(int row = 1; data.moreRows(row) && labels.size() < MAX_ROW_COUNT; row++) {
               Object val = data.getObject(row, 0);

               if(val == null) {
                  continue;
               }

               String label = Tool.toString(val);

               // could be duplicates when there is named group. (61513)
               if(!added.contains(label)) {
                  labels.add(label);
                  added.add(label);
               }
            }

            builder.dataTruncated(data.moreRows(MAX_ROW_COUNT));
         }
      }

      return builder
         .values(labels.toArray(new Object[0]))
         .build();
   }

   /**
    * Find the column in the column selection.
    */
   private ColumnRef findColumn(ColumnRef column, ColumnSelection cols, boolean cube) {
      ColumnRef ref = (ColumnRef) cols.findAttribute(column);

      if(ref != null) {
         return ref;
      }

      // find by name
      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ref = (ColumnRef) cols.getAttribute(i);

         if(column.getName().equals(ref.getAlias()) ||
            column.getName().equals(ref.getAttribute()))
         {
            return ref;
         }
      }

      if(cube) {
         String cname = XMLAUtil.getHeader(column);

         for(int i = 0; i < cols.getAttributeCount(); i++) {
            ref = (ColumnRef) cols.getAttribute(i);

            if(cname.equals(XMLAUtil.getHeader(ref))) {
               return ref;
            }
         }
      }

      return null;
   }

   /**
    * Get the constant if a script statement is a constant. The method is
    * relatively weak for a constant script statement might not be recognized
    * as a constant.
    *
    * @param script the specified script statement.
    * @return the constant if the script statement is a constant.
    */
   private Object getConstant(String script) {
      if(script == null) {
         return null;
      }

      int length = script.length();

      // is a string?
      if(length > 1 && script.charAt(0) == '\'' && script.charAt(length - 1) == '\'') {
         boolean ok = true;

         for(int i = 1; i < length - 1; i++) {
            if(script.charAt(i) == '\'') {
               ok = false;
               break;
            }
         }

         if(ok) {
            return script.substring(1, length - 1);
         }
      }

      // is an integer?
      try {
         return Integer.valueOf(script);
      }
      catch(Exception ex) {
         // ignore it
      }

      // is an double
      try {
         return Double.valueOf(script);
      }
      catch(Exception ex) {
         // ignore it
      }

      return null;
   }

   /**
    * Check if browse data is enabled for the column.
    */
   private boolean isBrowseable(TableAssembly table, ColumnRef column, Principal user) {
      if(column != null && (column.getRefType() & DataRef.CUBE_MEASURE) == DataRef.CUBE_MEASURE) {
         return false;
      }

      if(table instanceof BoundTableAssembly) {
         SourceInfo srcinfo = ((BoundTableAssembly) table).getSourceInfo();

         if(srcinfo.getType() == SourceInfo.MODEL) {
            try {
               XRepository rep = XFactory.getRepository();
               XDataModel model = rep.getDataModel(srcinfo.getPrefix());
               XLogicalModel lmodel = model.getLogicalModel(srcinfo.getSource(), user);
               DataRef ref = column.getDataRef();
               XEntity entity = lmodel.getEntity(ref.getEntity());

               if(entity == null) {
                  return true;
               }

               XAttribute attr = entity.getAttribute(ref.getAttribute());

               return attr.isBrowseable();
            }
            catch(Exception ex) {
               LOG.debug("Failed to determine if model data is browseable", ex);
            }
         }
      }

      if(table instanceof MirrorTableAssembly) {
         table = ((MirrorTableAssembly) table).getTableAssembly();
         column = findBaseColumn(table, column);
         return isBrowseable(table, column, user);
      }

      if(table instanceof ComposedTableAssembly) {
         TableAssembly[] tables = ((ComposedTableAssembly) table).getTableAssemblies(false);

         String entity = column.getEntity();

         if(entity == null || "".equals(entity)) {
            String name = column.getName();
            int dot = name.indexOf(".");
            entity = dot >= 0 ? name.substring(0, dot) : null;
         }

         table = null;

         if(entity != null && !"".equals(entity)) {
            for(int i = 0; i < tables.length; i++) {
               if(entity.equals(tables[i].getName())) {
                  table = tables[i];
                  break;
               }
            }
         }

         if(table == null) {
            table = tables[0];
         }

         column = findBaseColumn(table, column);
         return isBrowseable(table, column, user);
      }

      return true;
   }

   /**
    * Find correct columns.
    */
   private ColumnRef findBaseColumn(TableAssembly table, ColumnRef column) {
      ColumnSelection columns = table.getColumnSelection();
      String name = column.getName();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef cref = (ColumnRef) columns.getAttribute(i);
         DataRef ocref = AssetUtil.getOuterAttribute(table.getName(), cref);

         if(ocref.equals(column)) {
            return cref;
         }
      }

      DataRef base = columns.getAttribute(name);

      if(base instanceof ColumnRef) {
         return (ColumnRef) base;
      }

      return column;
   }

   /**
    * Get the real data source table from the mirror table.
    */
   private BoundTableAssembly getOriginalTableAssembly(Assembly assembly) {
      if(!(assembly instanceof MirrorTableAssembly)) {
         return null;
      }

      MirrorTableAssembly mirror = (MirrorTableAssembly) assembly;
      TableAssembly original = mirror.getTableAssembly();

      if(!(original instanceof BoundTableAssembly)) {
         return null;
      }

      return (BoundTableAssembly) original;
   }

   /**
    * Get the data from the data source itself.
    */
   private BrowseDataModel executeByDataSource(Principal principal) {
      try {
         XRepository repository = XFactory.getRepository();
         JDBCDataSource xds = (JDBCDataSource) repository.getDataSource(dataSource);
         String tableName = column.getEntity();
         String columnName = column.getAttribute();
         JDBCQuery query = new JDBCQuery();
         query.setDataSource(xds);
         VariableTable vars = new VariableTable();
         vars.put("user", principal.getName());
         XDataService service = XFactory.getDataService();
         Object session = service.bind(System.getProperty("user.name"));
         XNode result = XAgent.getAgent(xds).getQueryData(query, tableName, columnName,
                                                          vars, session, principal);
         ArrayList<Object> dlist = new ArrayList<>();

         if(result instanceof XTableNode) {
            XTableNode table = (XTableNode) result;

            if(table.getColCount() > 0) {
               while(table.next()) {
                  Object data = table.getObject(0);

                  if(data != null && !dlist.contains(data)) {
                     dlist.add(data);
                  }
               }
            }
         }

         Object[] arr = dlist.toArray();
         Tool.qsort(arr, true);
         String dtype = column.getDataType();
         Object[] items = new Object[arr.length];

         for(int i = 0; i < arr.length; i++) {
            if(AssetUtil.isNumberType(dtype)) {
               items[i] = arr[i];
            }
            else {
               items[i] = AbstractCondition.getValueString(arr[i], dtype);
            }
         }

         return BrowseDataModel.builder()
            .values(items)
            .build();
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      return null;
   }

   /**
    * Get browse data for an assembly that is bound to a vs assembly
    */
   private BrowseDataModel getVSAssemblyBoundData(AssetQuerySandbox box) {
      ViewsheetSandbox vbox = box.getViewsheetSandbox();

      try {
         TableLens lens = vbox.getTableData(name);

         if(lens == null) {
            return null;
         }

         BrowseDataModel data = Util.getBrowsedData(lens, column.getName());
         final Object[] values = data != null ? data.values() : null;

         // try browsing data after grouping and aggregation is performed
         if((values == null || values.length == 0) && vsAssemblyName != null) {
            lens = vbox.getTableData(vsAssemblyName);

            if(lens == null) {
               return null;
            }

            data = Util.getBrowsedData(lens, column.getName());
         }

         return data;
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }

      return null;
   }

   private boolean isMongoDataSource(TableAssembly table) {
      String source = table.getSource();

      if(source == null) {
         return false;
      }

      try {
         XRepository repository = XFactory.getRepository();
         XDataSource xds =  repository.getDataSource(source);

         if(xds instanceof JDBCDataSource &&
            "mongodb.jdbc.MongoDriver".equals(((JDBCDataSource) xds).getDriver()))
         {
            return true;
         }
      }
      catch(RemoteException e) {
         return false;
      }

      return false;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public ColumnRef getColumn() {
      return column;
   }

   public void setColumn(ColumnRef column) {
      this.column = column;
   }

   public String getDataSource() {
      return dataSource;
   }

   public void setDataSource(String dataSource) {
      this.dataSource = dataSource;
   }

   public XSourceInfo getSourceInfo() {
      return sinfo;
   }

   public void setSourceInfo(XSourceInfo sinfo) {
      this.sinfo = sinfo;
   }

   public String getVSAssemblyName() {
      return vsAssemblyName;
   }

   public void setVSAssemblyName(String vsAssemblyName) {
      this.vsAssemblyName = vsAssemblyName;
   }

   private final Logger LOG = LoggerFactory.getLogger(BrowseDataController.class);
   private String name;
   private ColumnRef column;
   private String dataSource;
   private XSourceInfo sinfo;
   private String vsAssemblyName;

   public static final int MAX_ROW_COUNT = 1000;
}
