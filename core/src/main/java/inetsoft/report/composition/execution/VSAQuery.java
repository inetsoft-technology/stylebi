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
package inetsoft.report.composition.execution;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.TableLens;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.WorksheetWrapper;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.lens.MaxRowsTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCQueryFilter;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.ScriptException;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * VSAQuery, the viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class VSAQuery {
   /**
    * Create the associated viewsheet assembly query.
    * @param box the specified viewsheet sandbox as the context.
    * @param assembly the specified viewsheet assembly.
    * @param type result type defined in DataMap.
    * @return the created viewsheet assembly query.
    */
   public static VSAQuery createVSAQuery(ViewsheetSandbox box, VSAssembly assembly, int type)
      throws Exception
   {
      boolean detail = (type & DataMap.DETAIL) != 0;

      if(assembly instanceof OutputVSAssembly) {
         return new OutputVSAQuery(box, assembly.getName());
      }
      else if(assembly instanceof InputVSAssembly) {
         return new InputVSAQuery(box, assembly.getName());
      }
      else if(assembly instanceof EmbeddedTableVSAssembly) {
         return new EmbeddedTableVSAQuery(box, assembly.getName(), detail);
      }
      else if(assembly instanceof TableVSAssembly) {
         return new TableVSAQuery(box, assembly.getName(), detail);
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         return new CrosstabVSAQuery(box, assembly.getName(), detail);
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         return new CalcTableVSAQuery(box, assembly.getName(), detail);
      }
      else if(assembly instanceof ChartVSAssembly) {
         ChartVSAQuery qry = new ChartVSAQuery(box, assembly.getName(), detail);
         qry.setBrush((type & DataMap.BRUSH) != 0);
         qry.setIgnoreRuntimeCondition(type == DataMap.NO_FILTER);
         return qry;
      }
      else if(assembly instanceof SelectionListVSAssembly) {
         return new SelectionListVSAQuery(box, assembly.getName());
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         return SelectionTreeVSAQuery.createVSAQuery(box, assembly.getName());
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         return new TimeSliderVSAQuery(box, assembly.getName());
      }
      else if(assembly instanceof CalendarVSAssembly) {
         return new CalendarVSAQuery(box, assembly.getName());
      }
      else if(assembly instanceof SelectionVSAssembly) {
         return new AbstractSelectionVSAQuery(box, assembly.getName());
      }
      else {
         throw new RuntimeException("Unsupported assembly found: " + assembly);
      }
   }

   /**
    * Normalize a table assembly created in a VSAQuery.
    * @param table the specified table assembly to normalize.
    */
   public static final void normalizeTable(TableAssembly table) {
      if(table == null) {
         return;
      }

      if(table instanceof ComposedTableAssembly) {
         ((ComposedTableAssembly) table).setHierarchical(false);
      }

      // the created table is always invisible
      table.setVisible(false);
   }

   /**
    * Fix condition list.
    * @param conds the specified condition list to be fixed.
    * @param cols the specified column selection of table assembly.
    */
   public static void fixConditionList(ConditionList conds, ColumnSelection cols) {
      fixConditionList(conds, cols, false);
   }

   private static void fixConditionList(ConditionList conds, ColumnSelection cols, boolean fuzzy) {
      if(conds == null) {
         return;
      }

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);

         if(citem.getAttribute() instanceof ColumnRef) {
            ColumnRef column = (ColumnRef) citem.getAttribute();
            fixConditionColumn(column, cols, fuzzy);
         }
      }
   }

   private static void fixConditionColumn(ColumnRef column, ColumnSelection cols, boolean fuzzy) {
      if(column == null || cols == null || !(column.getDataRef() instanceof DateRangeRef)) {
         return;
      }

      ColumnRef ncolumn = column.clone();
      DateRangeRef dref = (DateRangeRef) ncolumn.getDataRef();
      DataRef ref = dref.getDataRef();
      DataRef baseCol = ref == null ? null : cols.getAttribute(ref.getName(), true);

      if(baseCol == null) {
         baseCol = new ColumnRef((DataRef) ref.clone());
         cols.addAttribute(baseCol);
      }

      if(cols.getAttribute(ncolumn.getName(), fuzzy) != null) {
         return;
      }

      String originalType = dref.getOriginalType();
      // DateRangeRef expects to point to the base ref (AttributeRef), not ColumnRef.
      // need to real base column (may be quanlified, e.g. Query2.orderdate), or the
      // group may be dropped in AggregateInfo.validate(). (60722)
      DataRef baseRef = baseCol instanceof ColumnRef ? ((ColumnRef) baseCol).getDataRef() : baseCol;
      dref = new DateRangeRef(dref.getAttribute(), baseRef, dref.getDateOption());
      dref.setOriginalType(originalType);
      ncolumn.setDataRef(dref);
      ncolumn.setVisible(false);
      cols.addAttribute(ncolumn);
   }

   /**
    * Create a viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public VSAQuery(ViewsheetSandbox box, String vname) {
      super();

      this.box = box;
      this.vname = vname;
      this.createdTime = System.currentTimeMillis();
      locale = Catalog.getCatalog().getLocale();

      if(locale == null) {
         locale = Locale.getDefault();
      }
   }

   /**
    * Get table values and default cell formats.
    * @param table the specifield table.
    * @param index the binding data ref's index.
    * @param ingoreEmtpyString convert "" to null if true.
    * @return an array, index 0 is values which type is Object,
    *  index 1 is default format for the specified column.
    */
   public static Object[] getValueFormatPairs(XTable table, int index,
                                              boolean ingoreEmtpyString)
   {
      ArrayList vals = new ArrayList();
      // default cell format should same for all the value in one column
      Format dfmt = null;
      table.moreRows(Integer.MAX_VALUE);
      Object obj = null;

      for(int i = table.getHeaderRowCount(); i < table.getRowCount(); i++) {
         obj = table.getObject(i, index);

         if(ingoreEmtpyString && "".equals(obj)) {
            obj = null;
         }

         vals.add(obj);
         Format fmt = table instanceof TableLens ?
            ((TableLens) table).getDefaultFormat(i, index) : null;

         if(fmt != null) {
            dfmt = fmt;
         }
      }

      Object[] values = new Object[vals.size()];
      vals.toArray(values);
      return new Object[] {values, dfmt};
   }

   /**
    * Get the data.
    * @return the data of the query.
    */
   public abstract Object getData() throws Exception;

   /**
    * Check if this VSAQuery is for detail data.
    */
   public boolean isDetail() {
      return false;
   }

   protected TableAssembly mergePostCondition(TableAssembly table) {
      ConditionList pcond = createPostConds();

      if(pcond == null || pcond.isEmpty()) {
         return table;
      }

      List<ConditionListWrapper> conds = new ArrayList<>();
      AggregateInfo ainfo = table.getAggregateInfo();

      if(ainfo != null && !ainfo.isEmpty()) {
         ColumnSelection columns = new ColumnSelection();

         for(int i = 0; i < ainfo.getAggregateCount(); i++) {
            AggregateRef ref = ainfo.getAggregate(i);
            columns.addAttribute(ref.getDataRef());
         }

         VSAssembly vassembly = getAssembly();

         if(vassembly instanceof ChartVSAssembly) {
            ChartVSAssembly chart = (ChartVSAssembly) vassembly;
            VSChartInfo cinfo = chart.getVSChartInfo();

            if(GraphTypes.isGeo(cinfo.getChartType())) {
               for(int i = 0; i < ainfo.getGroupCount(); i++) {
                  GroupRef ref = ainfo.getGroup(i);
                  columns.addAttribute(ref.getDataRef());
               }
            }
         }

         // replace with valid columns
         HierarchyListModel model = new HierarchyListModel(pcond);

         for(int i = model.getSize() - 1; i >= 0; i--) {
            if(model.isConditionItem(i)) {
               ConditionItem item = (ConditionItem) model.getElementAt(i);
               DataRef attr = item.getAttribute();
               attr = columns.getAttribute(attr.getName());

               if(attr != null) {
                  item.setAttribute(attr);
               }
            }
         }

         pcond.validate(columns);
      }

      conds.add(pcond);
      Worksheet ws = table.getWorksheet();

      if(ws != null) {
         String mname = table.getName() + "_mirror";
         MirrorTableAssembly mirror = new MirrorTableAssembly(ws, mname, table);
         ws.addAssembly(mirror); // need to add to ws for MV transformation. (54096)

         if(ainfo != null && ainfo.getGroupCount() > 0) {
            boolean existDCMerge = Arrays.stream(ainfo.getGroups())
               .filter(group -> group.getDcMergeGroup() != null)
               .findAny().isPresent();

            if(existDCMerge) {
               mirror.setAggregateInfo(ainfo);
            }
         }

         table = mirror;
      }

      ainfo = table.getAggregateInfo();

      if(ainfo != null && ainfo.isAggregated()) {
         table.setAggregate(true);
      }

      boolean hasCubeMeasure = false;

      if(isCube() && conds != null) {
         ColumnSelection columnSelection = table.getColumnSelection(true);

         for(ConditionListWrapper cond : conds) {
            for(int i = 0; i < cond.getConditionSize(); i++) {
               ConditionItem conditionItem = cond.getConditionItem(i);

               if(conditionItem == null) {
                  continue;
               }

               DataRef attribute = conditionItem.getAttribute();

               if(attribute == null) {
                  continue;
               }

               DataRef col = columnSelection.getAttribute(attribute.getAttribute());

               if(col instanceof ColumnRef && VSEventUtil.isMeasure((ColumnRef) col)) {
                  hasCubeMeasure = true;
                  break;
               }
            }

            if(hasCubeMeasure) {
               break;
            }
         }
      }

      if(table.isAggregate() || hasCubeMeasure) {
         ConditionListWrapper c = table.getPostRuntimeConditionList();

         if(c != null && !c.isEmpty()) {
            conds.add(c);
         }

         ConditionList list = ConditionUtil.mergeConditionList(conds, JunctionOperator.AND);
         table.setPostRuntimeConditionList(list);
         table.setProperty("Post_Condition_HasCubeMeasure", "" + hasCubeMeasure);
      }
      else {
         ConditionListWrapper c = table.getPreRuntimeConditionList();

         if(c != null && !c.isEmpty()) {
            conds.add(c);
         }

         ConditionList list = ConditionUtil.mergeConditionList(conds, JunctionOperator.AND);
         table.setPreRuntimeConditionList(list);
      }

      return table;
   }

   protected ConditionList createPostConds() {
      Viewsheet vs = getViewsheet();
      Assembly[] arr = vs.getAssemblies();
      List<ConditionListWrapper> conds = new ArrayList<>();

      for(Assembly assembly : arr) {
         if(assembly instanceof TimeSliderVSAssembly) {
            TimeSliderVSAssembly slider = (TimeSliderVSAssembly) assembly;

            if(slider.getSourceType() == XSourceInfo.VS_ASSEMBLY) {
               String source = slider.getTableName();

               if(getAssembly().getName().equals(source)) {
                  ConditionList cond = slider.getConditionList();

                  if(cond != null && !cond.isEmpty()) {
                     conds.add(cond);
                  }
               }
            }
         }
      }

      if(conds.size() <= 0) {
         return null;
      }

      return ConditionUtil.mergeConditionList(conds, JunctionOperator.AND);
   }

   /**
    * Check if is drill through operation.
    */
   private boolean isCube() {
      return getCubeType(getAssembly()) != null;
   }

   /**
    * Get the asset query sandbox.
    * @return the asset query sandbox.
    */
   protected AssetQuerySandbox getAssetQuerySandbox() {
      return box.getAssetQuerySandbox();
   }

   /**
    * Get the worksheet object in context.
    * @return the worksheet object in context.
    */
   protected Worksheet getWorksheet() {
      if(ws != null) {
         return ws;
      }

      VSAssembly vassembly = getAssembly();
      boolean dynamic = false;
      boolean sqlCube = XCube.SQLSERVER.equals(getCubeType(getAssembly()));

      if(vassembly instanceof DynamicBindableVSAssembly) {
         DynamicBindableVSAssembly dassembly =
            (DynamicBindableVSAssembly) vassembly;
         ConditionList conds = dassembly.getPreConditionList();

         if(conds != null && !conds.isEmpty()) {
            dynamic = true;
         }
      }

      // if contains condition list defined in viewsheet, wrap worksheet to
      // avoid it being polluted, for the table assembly in the worksheet will
      // be changed by merging condition lists
      if(VSUtil.createWsWrapper(getViewsheet(), getAssembly())) {
         return ws = new WorksheetWrapper(box.getWorksheet());
      }

      return ws = box.getWorksheet();
   }

   /**
    * Get the viewsheet assembly in context.
    * @return the viewsheet assembly in context.
    */
   protected VSAssembly getAssembly() {
      return (VSAssembly) getViewsheet().getAssembly(vname);
   }

   /**
    * Get the VSTableAssembly.
    */
   protected TableAssembly getVSTableAssembly(String name) {
      return getVSTableAssembly(name, false);
   }

   /**
    * Get the VSTableAssembly, do not filter the table columns if not analysis.
    */
   protected TableAssembly getVSTableAssembly(String name, boolean analysis) {
      return VSAQuery.getVSTableAssembly(name, shrink, getViewsheet(), getWorksheet());
   }

   /**
    * Get the VSTableAssembly, do not filter the table columns if not analysis.
    */
   public static TableAssembly getVSTableAssembly(String name, boolean shrink,
                                                  Viewsheet vs, Worksheet ws)
   {
      if(ws == null) {
         return null;
      }

      if(shrink) {
         VSUtil.shrinkTable(vs, ws);
      }

      TableAssembly base = (TableAssembly) ws.getAssembly(name);
      VSAQuery.appendCalcField(base, name, true, vs);

      TableAssembly table = ws.getVSTableAssembly(name);

      if(table instanceof MirrorTableAssembly) {
         ((MirrorTableAssembly) table).updateColumnSelection();
      }

      return table;
   }

   /**
    * Get the TableAssembly.
    */
   protected TableAssembly getAssembly(String name) {
      Worksheet ws = getWorksheet();

      if(ws == null) {
         return null;
      }

      if(shrink) {
         VSUtil.shrinkTable(getViewsheet(), ws);
      }

      return (TableAssembly) ws.getAssembly(name);
   }

   /**
    * Get the viewsheet object in context.
    * @return the viewsheet object in context.
    */
   protected Viewsheet getViewsheet() {
      return box.getViewsheet();
   }

   /**
    * Get the viewsheet object in context.
    * @return the viewsheet object in context.
    */
   protected String getID() {
      return box.getID();
   }

   /**
    * Set the brush/zoom selection from chart to the specified table.
    * @param table the specified table.
    */
   protected void setSharedCondition(ChartVSAssembly chart, TableAssembly table) {
      setSharedCondition(chart, table, true);
   }

   /**
    * Set the brush/zoom/drill selection from chart to the specified table.
    * @param table the specified table.
    */
   protected void setSharedCondition(ChartVSAssembly chart, TableAssembly table, boolean brush) {
      VSAssembly vsAssembly = getAssembly();

      if(table == null || vsAssembly == null) {
         return;
      }

      ColumnSelection cols = table.getColumnSelection();

      if(chart != null) {
         try {
            box.updateAssembly(chart);
         }
         catch(Exception e) {
            LOG.warn("Failed to update chart assembly: " + chart.getAssemblyEntry(), e);
         }
      }

      ConditionList conds = chart == null ? null
         : (brush ? chart.getBrushConditionList(cols, true) : null);

      if(conds != null && !conds.isEmpty()) {
         String tip = chart.getRuntimeTipView();

         if(vname.equals(tip)) {
            conds = null;
         }
      }

      ConditionListWrapper twrapper = vsAssembly.getTipConditionList();
      ConditionList tconds = twrapper == null ? null : twrapper.getConditionList();

      if(chart != null) {
         conds = VSAQuery.replaceGroupValues(conds, chart);
         tconds = VSAQuery.replaceGroupValues(tconds, chart);
      }

      // include tip condition
      if(tconds != null && !tconds.isEmpty()) {
         if(conds == null || conds.isEmpty()) {
            conds = tconds;
         }
         else {
            List<ConditionList> list = new ArrayList<>();
            list.add(conds);
            list.add(tconds);
            conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
         }
      }

      // process shared drillFilter.
      ConditionList drillFilterConds;

      if(DateComparisonUtil.appliedDateComparison(vsAssembly.getVSAssemblyInfo())) {
         drillFilterConds = null;
      }
      else {
         drillFilterConds = ConditionUtil.getMergedDrillFilterCondition(getViewsheet(),
                                                                                                    ConditionUtil.getAssemblySource(vsAssembly));
      }

      // execute will change field name of condition list, so need clone
      drillFilterConds = ConditionUtil.isEmpty(drillFilterConds) ? null : drillFilterConds.clone();

      if(ConditionUtil.isEmpty(conds)) {
         conds = drillFilterConds;
      }
      else if(!ConditionUtil.isEmpty(drillFilterConds)) {
         List<ConditionList> list = new ArrayList<>();
         list.add(conds.getConditionList());
         list.add(drillFilterConds);
         conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
      }

      if(ConditionUtil.isEmpty(conds)) {
         return;
      }

      table.setPreRuntimeConditionList(conds);
      // add date range ref to column selection
      fixConditionList(conds, cols, true);
      fixConditionList(conds, table.getColumnSelection(true), true);
   }

   /**
    * Get cube type of source info.
    */
   public static String getCubeType(VSAssembly assembly) {
      SourceInfo sinfo = VSUtil.getCubeSource(assembly);
      return sinfo == null ? null : VSUtil.getCubeType(sinfo.getPrefix(), sinfo.getSource());
   }

   /**
    * Replace the condition against group name with group values.
    */
   public static ConditionList replaceGroupValues(ConditionList conds,
                                                  ChartVSAssembly chart) {
      return replaceGroupValues(conds, chart, false);
   }

   /**
    * Replace the condition against group name with group values.
    */
   public static ConditionList replaceGroupValues(ConditionList conds,
      ChartVSAssembly chart, boolean detail)
   {
      VSChartInfo cinfo = chart.getVSChartInfo();
      DataRef[] arr = cinfo.getRTFields();

      return replaceGroupValues(conds, chart, detail, arr);
   }

   /**
    * Replace the condition against group name with group values.
    */
   public static ConditionList replaceGroupValues(ConditionList conds,
      CrosstabVSAssembly crosstab, boolean detail)
   {
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      DataRef[] cols = cinfo == null ? null : cinfo.getRuntimeColHeaders();
      cols = cols == null ? new DataRef[0] : cols;
      DataRef[] rows = cinfo == null ? null : cinfo.getRuntimeRowHeaders();
      rows = rows == null ? new DataRef[0] : rows;
      DataRef[] groups = new DataRef[cols.length + rows.length];
      System.arraycopy(cols, 0, groups, 0, cols.length);
      System.arraycopy(rows, 0, groups, cols.length, rows.length);

      return replaceGroupValues(conds, crosstab, detail, groups);
   }

   /**
    * Replace the condition against group name with group values.
    */
   private static ConditionList replaceGroupValues(ConditionList conds,
      DataVSAssembly assembly, boolean detail, DataRef[] arr)
   {
      String ctype = getCubeType(assembly);
      boolean drillThrough = detail && (XCube.SQLSERVER.equals(ctype) ||
                                        XCube.MONDRIAN.equals(ctype));
      ConditionList nconds = new ConditionList();
      int indent = 0;
      Map<Integer, List<ConditionItem>> map = new HashMap<>();

      for(int i = 0; conds != null && i < conds.getSize(); i++) {
         HierarchyItem item = conds.getItem(i);

         if(indent > 0) {
            item.setLevel(item.getLevel() + indent);
         }

         if(!(item instanceof ConditionItem)) {
            nconds.append(item);
            continue;
         }

         ConditionItem citem = (ConditionItem) item;
         Condition cexpr = citem.getCondition();

         if(cexpr.getValueCount() > 1 && cexpr.getOperation() != XCondition.ONE_OF) {
            nconds.append(item);
            continue;
         }

         ConditionList gconds = null;
         boolean grouped = false;
         VSDimensionRef dim = null;
         SNamedGroupInfo groups = null;

         for(DataRef ref : arr) {
            if(!(ref instanceof VSDimensionRef)) {
               continue;
            }

            dim = (VSDimensionRef) ref;
            String fullname = dim.getFullName();
            String name = citem.getAttribute().getName();

            if(!(name.equals(fullname) || ("DataGroup(" + name + ")").equals(fullname))) {
               continue;
            }

            if(dim.isNameGroup()) {
               grouped = true;
               groups = (SNamedGroupInfo) dim.getNamedGroupInfo();
               break;
            }
         }

         ColumnRef cref = null;

         if(grouped) {
            ColumnRef acref = (ColumnRef) dim.getDataRef();
            DataRef ref2 = new AttributeRef(null, acref.getAttribute());
            cref = new ColumnRef(ref2);
         }

         boolean isNull = (cexpr.getOperation() == Condition.NULL);
         boolean negated = cexpr.isNegated();

         if(grouped && cexpr.getOperation() == XCondition.ONE_OF) {
            HashSet values = new HashSet<>();
            String dtype = groups.getDataRef().getDataType();

            for(int j = 0; j < cexpr.getValueCount(); j++) {
               Object val = cexpr.getValue(j);
               String nval = getGroupName(val);
               ConditionList agconds = groups.getGroupCondition(nval);

               if(agconds != null && agconds.getSize() != 0) {
                  if(negated) {
                     agconds = (ConditionList) agconds.clone();
                     agconds.negate();
                  }

                  for(int k = 0; k < agconds.getSize(); k++) {
                     HierarchyItem gitem = agconds.getItem(k);

                     if(gitem instanceof ConditionItem) {
                        ConditionItem gcond = (ConditionItem) gitem;
                        values.add(gcond.getCondition().getValue(0));
                     }
                  }
               }
               else {
                  if(val instanceof String && !XSchema.STRING.equals(dtype)) {
                     val = Tool.getData(dtype, val);
                  }

                  values.add(val);
               }
            }

            cexpr.removeAllValues();
            int count = 0;
            int lvl = citem.getLevel() + indent + 1;
            boolean haveNull = false;

            for(Object val : values) {
               count++;

               if(val == null) {
                  haveNull = true;
                  Condition ncond = new Condition(dtype);
                  ncond.setOperation(XCondition.NULL);
                  ncond.setNegated(negated);
                  ConditionItem nitem = new ConditionItem(cref, ncond, lvl);
                  nconds.append(nitem);

                  if(count < values.size()) {
                     nconds.append(new JunctionOperator(JunctionOperator.AND, lvl));
                  }
               }
               else {
                  cexpr.addValue(val);
               }
            }

            if(haveNull || indent > 0) {
               citem.setLevel(lvl);
               indent++;
            }

            citem.setAttribute(cref);
            nconds.append(item);
            continue;
         }

         String nval = getGroupName(cexpr.getValue(0));
         gconds = grouped && !isNull ? groups.getGroupCondition(nval) : null;

         if(gconds != null && !gconds.isEmpty() && negated) {
            gconds = (ConditionList) gconds.clone();
            gconds.negate();
         }

         if(gconds == null || gconds.getSize() == 0) {
            if(grouped) {
               citem.setAttribute(cref);

               if(!isNull) {
                  String dtype = groups.getDataRef().getDataType();

                  if(!XSchema.STRING.equals(dtype)) {
                     cexpr.setType(dtype);
                     HashSet values = new HashSet<>();

                     for(int j = 0; j < cexpr.getValueCount(); j++) {
                        Object val = cexpr.getValue(j);

                        if(val instanceof String) {
                           values.add(Tool.getData(dtype, val));
                        }
                        else {
                           values.add(val);
                        }
                     }

                     cexpr.removeAllValues();

                     for(Object val : values) {
                        cexpr.addValue(val);
                     }
                  }
               }
            }

            nconds.append(item);
            continue;
         }

         // replace condition with new items
         if(drillThrough) {
            nconds.append(item);

            for(int k = 0; k < gconds.getSize(); k++) {
               HierarchyItem gitem = gconds.getItem(k);

               if(gitem instanceof ConditionItem) {
                  ConditionItem gcond = (ConditionItem) gitem;
                  gcond.setAttribute(cref);
                  gcond.setLevel(item.getLevel());

                  ConditionItem gcond0 = (ConditionItem) gcond.clone();
                  List<ConditionItem> list = map.get(Integer.valueOf(i));

                  if(list == null) {
                     list = new ArrayList();
                  }

                  map.put(Integer.valueOf(nconds.getSize() - 1), list);
                  list.add(gcond0);
               }
            }
         }
         else {
            for(int k = 0; k < gconds.getSize(); k++) {
               HierarchyItem gitem = gconds.getItem(k);

               if(gitem instanceof ConditionItem) {
                  ConditionItem gcond = (ConditionItem) gitem;
                  gcond.setAttribute(cref);
               }

               gitem.setLevel(gitem.getLevel() + citem.getLevel() + 1);
               nconds.append(gitem);
            }
         }
      }

      if(drillThrough) {
         List<ConditionList> nlist = new ArrayList();
         List<ConditionList> list =
            ConditionUtil.separateConditionList(nconds, JunctionOperator.OR, true);

         for(int i = 0, colIdx = 0; i < list.size(); i++) {
            ConditionList clist0 = list.get(i);
            nlist.addAll(getGroupConditions(clist0, colIdx, map, 0));
            colIdx += clist0.getSize() + 1;
         }

         if(nlist.size() > 0) {
            nconds = ConditionUtil.mergeConditionList(nlist, JunctionOperator.OR);
         }
      }

      nconds.validate(false);
      return nconds;
   }

   private static String getGroupName(Object value) {
      String groupName = (value + "").trim();
      int idx1 = groupName.lastIndexOf("[");
      int idx2 = groupName.lastIndexOf("]");

      return idx1 >= 0 && idx2 >= 0 ?
         groupName.substring(idx1 + 1, idx2) : groupName;
   }

   private static List<ConditionList> getGroupConditions(ConditionList clist,
      int startIndex, Map<Integer, List<ConditionItem>> map, int current)
   {
      List<ConditionList> list = new ArrayList();

      for(int i = current; i < clist.getSize(); i++) {
         List<ConditionItem> citems = map.get(Integer.valueOf(startIndex + i));

         if(citems == null) {
            continue;
         }

         for(int j = 0; j < citems.size(); j++) {
            ConditionList clist0 = (ConditionList) clist.clone();
            ConditionItem citem = citems.get(j);
            citem.setLevel(clist0.getItem(i).getLevel());
            clist0.setItem(i, citem);
            list.addAll(getGroupConditions(clist0, startIndex, map, i + 1));
         }

         break;
      }

      if(list.size() == 0) {
         list.add(clist);
      }

      return list;
   }

   /**
    * Execute a table assembly and return a table lens.
    * @param table the specified table assembly.
    * @return the table lens as the result.
    */
   protected TableLens getTableLens(TableAssembly table) throws Exception {
      AssetQuerySandbox wbox = box.getAssetQuerySandbox();

      if(wbox == null) {
         return null;
      }

      removeUnusedCalcFields(table);
      replaceConditionRef(table);

      // analyze mode should limit rows so it doesn't take a long time
      int mode;
      TableAssembly table2 = null;
      boolean cruntime = AssetUtil.containsRuntimeCondition(table, true);
      boolean limited = box.isTimeLimited(vname);
      boolean vsMeta = false;

      if(isDetail()) {
         table.setProperty("isDetail", "true");
      }

      if(isMetadata()) {
         VSAssembly vsobj = getAssembly();
         boolean vsSrc = false;

         if(vsobj instanceof DataVSAssembly) {
            SourceInfo sourceInfo = ((DataVSAssembly) vsobj).getSourceInfo();
            vsSrc = sourceInfo != null && sourceInfo.getType() == SourceInfo.VS_ASSEMBLY;
         }

         // if bound to vs-assembly, need to use live data for meta
         if(vsSrc) {
            mode = AssetQuerySandbox.LIVE_MODE;
            vsMeta = true;
         }
         else {
            mode = AssetQuerySandbox.DESIGN_MODE;
         }

         if(getViewsheet().getViewsheetInfo().isMetadata()) {
            table.setProperty("metadata", "true");
         }
      }
      else if(box.getMode() == AbstractSheet.SHEET_RUNTIME_MODE) {
         mode = AssetQuerySandbox.RUNTIME_MODE;
      }
      else if(getViewsheet().getViewsheetInfo().isMetadata()) {
         mode = AssetQuerySandbox.DESIGN_MODE;
         table.setProperty("metadata", "true");
      }
      else {
         mode = AssetQuerySandbox.LIVE_MODE;
      }

      if(!wbox.isIgnoreFiltering() && !cruntime && mode == AssetQuerySandbox.LIVE_MODE) {
         table2 = (TableAssembly) table.clone();
      }

      // @by billh, since v10.3, this function is removed per suggestion
      // from US team, for metadata mode is available for a given viewshseet
      boolean tryIgnoring = false;
         // "true".equals(SreeEnv.getProperty("asset.ignore.filtering"));
      // cancel the pending query if any
      QueryManager qmgr = box.getQueryManager(vname);

      if(Q_CANCEL.get() != Boolean.FALSE) {
         qmgr.cancel();
      }

      wbox.setQueryManager(qmgr); // was box.getQueryManager()
      wbox.setTimeLimited(table.getName(), limited);

      table.setProperty("assetName", box.getSheetName());

      // execute script here instead of waiting for MVQueryBuilder. Otherwise
      // the VS objects are not accessible in the script.
      executeExpressions(table, box);
      Set ignored = markVariables(table, wbox, mode, limited,
                                  box.getTouchTimestamp(), qmgr);

      XUtil.VS_ASSEMBLY.set(getAssembly().getName());
      String vassembly = getAssembly().getAbsoluteName();
      Matcher matcher = TEMP_CALC_CROSSTAB_PATTERN.matcher(vassembly);

      if(matcher.matches() && matcher.groupCount() == 1) {
         vassembly = matcher.group(1);
      }

      try {
         TableLens lens = AssetDataCache.getData(
            box.getID(), table, wbox, ignored, mode,
            limited, box.getTouchTimestamp(), qmgr);

         List<Exception> exs = WorksheetService.ASSET_EXCEPTIONS.get();

         if(table2 != null && lens != null && lens.moreRows(0) && !lens.moreRows(1) &&
            tryIgnoring)
         {
            wbox.setIgnoreFiltering(true);
            lens = AssetDataCache.getData(box.getID(), table, wbox, null, mode,
                                          true, box.getTouchTimestamp(), qmgr);
            exs = WorksheetService.ASSET_EXCEPTIONS.get();

            if(exs != null) {
               String msg = Catalog.getCatalog().
                  getString("common.ignore.filtering");
               exs.add(new MessageException(msg, LogLevel.INFO, true, MessageCommand.INFO));
            }
         }
         else if(mode == AssetQuerySandbox.LIVE_MODE &&
                 exs != null && !exs.isEmpty() && lens instanceof TableFilter2 &&
                 ((TableFilter2) lens).getTable() instanceof XNodeMetaTable)
         {
            String msg = Catalog.getCatalog().
               getString("common.viewsheet.failoverMetadata");
            exs.add(new MessageException(msg));
            setMetadata(true);
            getViewsheet().getViewsheetInfo().setMetadata(true);
         }

         // using live data as meta data for vs-assembly as source
         // it's used during crosstab conversion to calc
         if(vsMeta) {
            lens = new MaxRowsTableLens(lens, 1);
         }

         return lens;
      }
      finally {
         XUtil.VS_ASSEMBLY.set(null);
      }
   }

   /**
    * Mark a variable is not used for this table execution,
    * if a variable is not in use, then it will not add to cache key.
    * @return the set of ignored variables
    */
   private Set markVariables(
      TableAssembly table, AssetQuerySandbox wbox, int mode,
      boolean limited, long ts, QueryManager qmgr)
   {
      VariableTable vars = wbox.getVariableTable();
      Set<String> notTouched = new HashSet<>();

      if(vars == null) {
         return notTouched;
      }

      try {
         table = (TableAssembly) table.clone();

         AssetQuery query = AssetQuery.createAssetQuery(table, mode, wbox,
                                                        false, ts, true, false);
         query.setTimeLimited(limited);
         query.setQueryManager(qmgr);
         StringBuilder buf = new StringBuilder();
         query.getQueryPlan().writeContent(buf);

         VSAssembly vsobj = getAssembly();

         // for MV query, the query plan doesn't include detailed
         // information so parameters defined in VS will be missing
         if(vsobj instanceof DynamicBindableVSAssembly) {
            buf.append(((DynamicBindableVSAssembly) vsobj).getPreConditionList());
         }

         Set<String> cvars = new HashSet<>();
         // composed table? recursive has been maintained
         UserVariable[] uvars = table.getAllVariables();

         for(UserVariable uvar : uvars) {
            cvars.add(uvar.getName());
         }

         Enumeration<String> keys = vars.keys();

         while(keys.hasMoreElements()) {
            String key = keys.nextElement();

            if(VariableTable.isBuiltinVariable(key) ||
               VariableTable.isContextVariable(key) ||
               vars.isInternalParameter(key) || cvars.contains(key))
            {
               continue;
            }

            // a very simple mechanism, can be enhanced
            if(buf.indexOf(key) >= 0) {
               continue;
            }

            notTouched.add(key);
         }

         String filters = SreeEnv.getProperty("jdbc.query.filter");

         if(filters != null && !filters.trim().isEmpty()) {
            for(String filterClass : filters.trim().split(",")) {
               try {
                  JDBCQueryFilter filter = (JDBCQueryFilter)
                     Class.forName(
                        Tool.convertUserClassName(filterClass)).newInstance();
                  notTouched.removeAll(filter.getIncludedParameters(vars));
               }
               catch(Exception exc) {
                  LOG.warn("Failed to instantiate query filter class: " + filterClass,
                     exc);
               }
            }
         }
      }
      catch(Throwable e) {
         LOG.warn("Failed to search variables: " + table.getName(), e);
      }

      // add calculate field parameter
      while(table != null) {
         ColumnSelection sel = table.getColumnSelection(true);

         for(int i = 0; i < sel.getAttributeCount(); i++) {
            DataRef ref = sel.getAttribute(i);
            checkVariable(ref, notTouched);
         }

         AggregateInfo ainfo = table.getAggregateInfo();

         if(ainfo != null) {
            for(int i = 0; i < ainfo.getGroupCount(); i++) {
               checkVariable(ainfo.getGroup(i), notTouched);
            }

            for(int i = 0; i < ainfo.getAggregateCount(); i++) {
               checkVariable(ainfo.getAggregate(i), notTouched);
            }
         }

         if(table instanceof MirrorTableAssembly) {
            table = ((MirrorTableAssembly) table).getTableAssembly();
         }
         else {
            break;
         }
      }

      return notTouched;
   }

   private static void checkVariable(DataRef ref, Set<String> notTouched) {
      ExpressionRef eref = null;

      while(ref != null) {
         if(ref instanceof ExpressionRef && !(ref instanceof AliasDataRef)) {
            eref = (ExpressionRef) ref;
            break;
         }

         if(ref instanceof DataRefWrapper) {
            ref = ((DataRefWrapper) ref).getDataRef();
         }
         else {
            break;
         }
      }

      if(eref != null) {
         String exp = eref.getExpression();

         if(exp != null) {
            Iterator<String> it = notTouched.iterator();

            while(it.hasNext()) {
               String notKey = it.next();

               if(exp.contains(notKey)) {
                  it.remove();
               }
            }
         }
      }
   }

   /**
    * Execute the condition expressions and replace with value.
    */
   private static void executeExpressions(TableAssembly table,
                                          ViewsheetSandbox box)
   {
      executeExpressions(table.getPreRuntimeConditionList(), box);

      if(table instanceof ComposedTableAssembly) {
         TableAssembly[] tbls =
            ((ComposedTableAssembly) table).getTableAssemblies(false);

         if(tbls != null) {
            for(TableAssembly tbl : tbls) {
               executeExpressions(tbl, box);
            }
         }
      }
   }

   /**
    * Execute the condition expressions and replace with value.
    */
   private static void executeExpressions(ConditionListWrapper wrapper,
                                          ViewsheetSandbox box)
   {
      if(wrapper == null || box == null) {
         return;
      }

      ConditionList condlist = wrapper.getConditionList();

      for(int i = 0; i < condlist.getSize(); i++) {
         XCondition xcond = condlist.getXCondition(i);

         if(xcond != null && xcond instanceof Condition) {
            Condition cond = (Condition) xcond;

            for(int k = 0; k < cond.getValueCount(); k++) {
               Object val = cond.getValue(k);

               if(val instanceof ExpressionValue) {
                  String type = ((ExpressionValue) val).getType();

                  try {
                     val = executeScript(cond, (ExpressionValue) val, box);
                     cond.setValue(k, val);
                  }
                  catch(Exception e) {
                     if(ExpressionValue.JAVASCRIPT.equals(type)) {
                        throw e;
                     }
                     else {
                        throw new RuntimeException(
                           "SQL Expressions are not currently supported on viewsheet assemblies");
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Execute the mv condition.
    */
   private static Object executeScript(Condition cond, ExpressionValue eval,
                                       ViewsheetSandbox vbox)
   {
      Object val = null;
      String exp = eval.getExpression();
      AssetQuerySandbox box = vbox.getAssetQuerySandbox();
      Viewsheet vs = vbox == null ? null : vbox.getViewsheet();
      ScriptEnv senv = box.getScriptEnv();
      Scriptable scope = null;

      try {
         val = senv.exec(senv.compile(exp), scope = box.getScope(), null, vs);
      }
      catch(Exception ex) {
         String suggestion = senv.getSuggestion(ex, null, scope);
         String msg = "Script error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + XUtil.numbering(exp);

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.warn(msg);
         }

         throw new ScriptException(msg);
      }

      return PreAssetQuery.getScriptValue(val, cond);
   }

   /**
    * Append detail calc field to table assembly.
    */
   protected void appendDetailCalcField(TableAssembly table, String tname) {
      appendCalcField(table, tname, true, getViewsheet());
   }

   /**
    * Append detail calc field to table assembly.
    */
   protected void appendAggCalcField(TableAssembly table, String tname) {
      appendCalcField(table, tname, false, getViewsheet());
   }

   /**
    * Append calc field to table assembly.
    */
   public static void appendCalcField(TableAssembly table, String tname,
                                      boolean detail, Viewsheet vs)
   {
      if(table == null) {
         return;
      }

      CalculateRef[] calcs = vs.getCalcFields(tname);
      boolean isCube = table.getName().startsWith(Assembly.CUBE_VS);

      if(calcs != null) {
         ColumnSelection columns = table.getColumnSelection(false);
         boolean changed = false;

         for(int i = 0; i < calcs.length; i++) {
            boolean valid = isCube ? true : (detail == calcs[i].isBaseOnDetail());

            if(!valid) {
               continue;
            }

            if(!calcs[i].isBaseOnDetail()) {
               // clear the mirror table aggregate entity.calc_field,
               // only keep the calc_field
               DataRef old = columns.getAttribute(calcs[i].getName());

               if(old != null) {
                  columns.removeAttribute(old);
               }
            }

            calcs[i].setVisible(true);
            columns.addAttribute((CalculateRef) calcs[i].clone());
            changed = true;
         }

         changed = VSUtil.addCalcBaseRefs(columns, null, Arrays.asList(calcs)) || changed;

         if(changed) {
            table.resetColumnSelection();
         }
      }
   }

   /**
    * Fix user create aggregate ref's second dataref with columnselection.
    */
   protected void fixUserAggregateRef(AggregateRef aref, ColumnSelection columns) {
      DataRef ref2 = aref.getSecondaryColumn();

      if(ref2 != null) {
         ref2 = (ColumnRef) columns.getAttribute(ref2.getName());

         if(ref2 == null) {
            LOG.warn("Column not found: " + aref.getSecondaryColumn());
            throw new ColumnNotFoundException(Catalog.getCatalog().getString
               ("common.invalidTableColumn",
               aref.getSecondaryColumn()));
         }

         aref.setSecondaryColumn(ref2);
      }
   }

   // remove unused calc fields
   // since all calc fields are added to table in appendCalcField, regardless of whether
   // it's used. that will result the calc field being executed (in FormulaTableLens)
   // regardless of whether it's actually used. this function removes any calc field
   // that is not referenced anywhere in the output (column list or aggregate info).
   private void removeUnusedCalcFields(TableAssembly table) {
      // need to run in a look since calc field referenced by removed calc field can only
      // be removed after the calc field has been removed.
      while(removeUnusedCalcField(table)) {
         // continue until no more to remove
      }
   }

   private boolean removeUnusedCalcField(TableAssembly table) {
      // the structure of the table assembly is
      //   table - aggragetInfo and public columnSelection reflects whether field is used
      //   child (mirror) - column selection contains calc field (no aggregate)

      if(!(table instanceof MirrorTableAssembly)) {
         return false;
      }

      TableAssembly child = ((MirrorTableAssembly) table).getTableAssembly();
      ColumnSelection childPub = child.getColumnSelection(true);
      ColumnSelection childPriv = child.getColumnSelection(false);

      if(!child.getAggregateInfo().isEmpty()) {
         return false;
      }

      // should not have any selection
      if(!childPriv.equals(childPub)) {
         return false;
      }

      ColumnSelection pubcols = table.getColumnSelection(true);
      ColumnSelection privcols = table.getColumnSelection(false);
      AggregateInfo ainfo = table.getAggregateInfo();
      // all columns used in table, including columns referenced from expression
      String allCols = Stream.concat(childPriv.stream(), privcols.stream())
         .filter(ref -> ref instanceof ColumnRef)
         .map(ref -> ((ColumnRef) ref).getDataRef())
         .filter(Objects::nonNull)
         .map(ref -> ref instanceof ExpressionRef ? ((ExpressionRef) ref).getExpression()
               : ref.getAttribute())
         .collect(Collectors.joining(";"));

      ConditionListWrapper conds = table.getPreRuntimeConditionList();

      if(conds != null) {
         for(int i = 0; i < conds.getConditionSize(); i += 2) {
            ConditionItem item = conds.getConditionItem(i);
            DataRef ref = AssetUtil.getBaseAttribute(item.getAttribute());

            if(ref instanceof DateRangeRef) {
               ref = ((DateRangeRef) ref).getDataRef();

               if(ref != null) {
                  allCols += ";" + ref.getAttribute();
               }
            }
         }
      }

      for(int i = childPriv.getAttributeCount() - 1; i >= 0; i--) {
         DataRef ref = childPriv.getAttribute(i);

         if(ref instanceof CalculateRef && ((CalculateRef) ref).isBaseOnDetail()) {
            CalculateRef calc = (CalculateRef) ref;

            if(!usedInSelection(pubcols, calc) && !usedInGroup(ainfo, calc) &&
               !usedInAggregate(ainfo, calc) && !allCols.contains(ref.getName()) &&
               !usedInSelection(child.getName(), calc))
            {
               pubcols.getAttribute(ref.getName());
               childPriv.removeAttribute(i);
               childPub.removeAttribute(i);
               return true;
            }
         }
      }

      return false;
   }

   private boolean usedInSelection(ColumnSelection cols, CalculateRef calc) {
      final String name = calc.getName();

      if(cols.getAttribute(name) != null) {
         return true;
      }

      return cols.stream().anyMatch(col -> Objects.equals(col.getAttribute(), name));
   }

   private boolean usedInGroup(AggregateInfo ainfo, CalculateRef calc) {
      if(ainfo.containsGroup(calc)) {
         return true;
      }

      return Arrays.asList(ainfo.getGroups()).stream().anyMatch(group -> {
         if(!(group.getDataRef() instanceof ColumnRef) ||
            !(((ColumnRef) group.getDataRef()).getDataRef() instanceof DateRangeRef))
         {
            return false;
         }

         DateRangeRef range = (DateRangeRef) ((ColumnRef) group.getDataRef()).getDataRef();
         DataRef ref = range.getDataRef();

         if(ref != null && Tool.equals(ref.getAttribute(), calc.getName())) {
            return true;
         }

         return false;
      });
   }

   // Check if calc is used in any selection assembly.
   private boolean usedInSelection(String tblname, CalculateRef calc) {
      return getViewsheet().getSelectionAssemblyStream()
         .filter(vsobj -> vsobj.getTableNames().contains(tblname))
         .anyMatch(vsobj -> Arrays.stream(vsobj.getDataRefs())
            .anyMatch(ref -> Objects.equals(ref.getName(), calc.getName())));
   }

   private boolean usedInAggregate(AggregateInfo ainfo, CalculateRef calc) {
      if(ainfo.containsAggregate(calc)) {
         return true;
      }

      return Arrays.asList(ainfo.getAggregates()).stream().anyMatch(aref -> {
         if(aref == null || aref.getFormula() == null ||
            !aref.getFormula().isTwoColumns())
         {
            return false;
         }

         DataRef secondRef = aref.getSecondaryColumn();

         if(secondRef != null &&Tool.equals(secondRef.getAttribute(), calc.getName())) {
            return true;
         }

         return false;
      });
   }

   /**
    * Check if is shrink table.
    */
   public boolean isShrinkTable() {
      return shrink;
   }

   /**
    * Set whether shrink table.
    */
   public void setShrinkTable(boolean shrink) {
      this.shrink = shrink;
   }

   /**
    * Determines the cancelled state of the query.
    * @return  <tt>true</tt> if cancelled, <tt>false</tt> otherwise
    */
   protected boolean isCancelled() {
      QueryManager queryManager = box.getQueryManager(vname);
      return queryManager != null && queryManager.lastCancelled() > createdTime;
   }

   /**
    * Replace all condition ref to self data ref so that mv can hit.
    */
   private void replaceConditionRef(TableAssembly table) {
      while(table instanceof MirrorTableAssembly) {
         String name = table.getName();

         // viewsheet created table?
         if(name.startsWith(Assembly.TABLE_VS)) {
            ColumnSelection sel = table.getColumnSelection();
            ConditionListWrapper wrapper = table.getPreRuntimeConditionList();
            wrapper = replaceRef(wrapper, sel);
            table.setPreRuntimeConditionList(wrapper);
            table = ((MirrorTableAssembly) table).getTableAssembly();
            continue;
         }

         break;
      }
   }

   private ConditionListWrapper replaceRef(ConditionListWrapper wrapper,
                                           ColumnSelection sel)
   {
      if(wrapper == null || wrapper.isEmpty()) {
         return wrapper;
      }

      ConditionList conds = wrapper.getConditionList();

      for(int i = 0; i < conds.getSize(); i += 2) {
         ConditionItem citem = conds.getConditionItem(i);
         DataRef ref = citem.getAttribute();
         DataRef ref2 = sel.findAttribute(ref);

         if(ref2 == null) {
            ref2 = sel.getAttribute(ref.getName());
         }

         if(ref2 != null) {
            citem.setAttribute(ref2);
         }
      }

      return wrapper;
   }

   /**
    * Validate calculate ref.
    */
   protected void validateCalculateRef(Worksheet ws, String name) {
      TableAssembly table = (TableAssembly) ws.getAssembly(name);

      if(table == null) {
         return;
      }

      ColumnSelection cols = table.getColumnSelection();
      cols.stream().filter(CalculateRef.class::isInstance)
         .forEach(c -> validateCalculateRef((CalculateRef) c));
   }

   /**
    * Validate calculate ref.
    */
   protected void validateCalculateRef(CalculateRef ref) {
      ExpressionRef eref = (ExpressionRef) ref.getDataRef();
      String exp = eref.getExpression();

      if(exp == null || "".equals(exp.trim())) {
         return;
      }

      String exp0 = this instanceof ChartVSAQuery ? ".data" : ".value";
      exp = exp.contains(vname + exp0) ? "" : exp;
      eref.setExpression(exp);
   }

   /**
    * Check if is worksheet cube.
    */
   protected boolean isWorksheetCube() {
      return VSUtil.isWorksheetCube(getAssembly());
   }

   /**
    * Check whether to use metadata for editing.
    */
   public boolean isMetadata() {
      return meta;
   }

   /**
    * Set whether to use metadata for editing.
    */
   public void setMetadata(boolean meta) {
      this.meta = meta;
   }

   /**
    * Creates a table assembly for a table that is bound to a vs assembly
    *
    * @param assemblyName source assembly name
    */
   public TableAssembly createAssemblyTable(String assemblyName) throws Exception {
      Viewsheet vs = getViewsheet();

      if(assemblyName == null) {
         return null;
      }

      TableLens lens = box.getTableData(assemblyName);

      if(lens == null) {
         return null;
      }

      // meta data doesn't require all rows, which would cause conversion to calc problem
      if(meta) {
         lens = new MaxRowsTableLens(lens, 1);
      }

      Worksheet ws = box.getWorksheet();
      DataTableAssembly table = new DataTableAssembly(ws, assemblyName);
      table.setVisible(false);
      table.setData(lens);
      vs.getBaseWorksheet().addAssembly(table);

      // name should start with V_, otherwise PreAssetQuery.getAggregateInfo
      // will return empty
      String mname = Assembly.TABLE_VS + getAssembly().getName() + "_" + table.getName();
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, mname, table);
      // append calc fields
      VSAQuery.appendCalcField(mirror, table.getName(), true, vs);
      DynamicBindableVSAssembly dassembly = (DynamicBindableVSAssembly) getAssembly();

      // append conditions
      if(!"true".equals(mirror.getProperty("vs.cond"))) {
         ConditionList conds = dassembly.getPreConditionList();

         if(conds != null && !conds.isEmpty()) {
            ColumnSelection cols = mirror.getColumnSelection(false);
            conds = VSUtil.normalizeConditionList(cols, conds);
            conds.replaceVariables(box.getVariableTable());
            ConditionListWrapper pwrapper = mirror.getPreRuntimeConditionList();

            if(pwrapper != null && !pwrapper.isEmpty()) {
               List<ConditionList> list = new ArrayList<>();
               list.add(pwrapper.getConditionList());
               list.add(conds);
               conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);
            }

            mirror.setPreRuntimeConditionList(conds);
         }

         box.appendDateComparisonConditions(vname, mirror);
         mirror.setProperty("vs.cond", "true");
      }

      return mirror;
   }

   protected void checkMaxRowLimit(XTable data) {
      // add max row limit user message.
      if(data != null && box.getMode() == Viewsheet.SHEET_RUNTIME_MODE && getAssembly() != null) {
         VSEventUtil.addWarningText(data, box, getAssembly().getAbsoluteName(), true);
      }
   }

   /**
    * Check if the format contains dynamic value.
    */
   protected final boolean isDynamic(VSFormat vfmt) {
      List<DynamicValue> dvals = vfmt.getDynamicValues();

      for(DynamicValue dval : dvals) {
         if(VSUtil.isVariableValue(dval.getDValue()) ||
            VSUtil.isScriptValue(dval.getDValue()))
         {
            return true;
         }
      }

      return false;
   }

   public static final ThreadLocal<Boolean> Q_CANCEL = new ThreadLocal<>();
   protected ViewsheetSandbox box;
   protected String vname;
   protected long createdTime;
   protected Locale locale;
   private Worksheet ws = null;
   private boolean shrink = true;
   private boolean meta = false;

   private static final Pattern TEMP_CALC_CROSSTAB_PATTERN = Pattern.compile("^" +
      CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX + "([\\s\\S]+)_Crosstab_[\\d]+$");
   private static final Logger LOG = LoggerFactory.getLogger(VSAQuery.class);
}
