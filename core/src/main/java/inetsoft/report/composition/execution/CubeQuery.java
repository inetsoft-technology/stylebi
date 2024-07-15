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

import inetsoft.report.*;
import inetsoft.report.filter.CubeTableFilter;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.*;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCQuery;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.xmla.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Cube query executes a cube assembly.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CubeQuery extends AssetQuery {
   /**
    * Constructor.
    */
   public CubeQuery(int mode, AssetQuerySandbox box, CubeTableAssembly table,
                    boolean stable, boolean metadata) {
      super(mode, box, stable, metadata);

      this.table = table;
      this.table.update();
   }

   /**
    * Check if should ignore max rows setting.
    */
   @Override
   protected boolean ignoreMaxRows(VariableTable vars) throws Exception {
      if(!super.ignoreMaxRows(vars)) {
         return false;
      }

      XDataSource xds =
         DataSourceRegistry.getRegistry().getDataSource(table.getSource());

      return (mode & AssetQuerySandbox.RUNTIME_MODE) != 0 ||
         XDataSource.XMLA.equals(xds.getType());
   }

   /**
    * Get the post process base table lens.
    * @param vars the specified variable table.
    * @return the post process base table lens of the query.
    */
   @Override
   protected TableLens getPostBaseTableLens(VariableTable vars) throws Exception
   {
      if(SreeEnv.getProperty("olap.table.originalContent") == null &&
         XCube.SQLSERVER.equals(getCubeType()))
      {
         SreeEnv.setProperty("olap.table.originalContent", "false");
      }

      XMLAQuery query = createBaseQuery();
      fixQueryColumn(vars, query);
      addUsedColumns(query);
      List<DataRef> cols = new ArrayList<>();
      ColumnSelection columns = table.getColumnSelection(true);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         cols.add(columns.getAttribute(i));
      }

      if(!isWorksheetCube()) {
         ConditionList clist = getConditionList();
         convertCondition(query, clist);
         XNode root = ConditionListHandler.createFilterNode(clist);

         if(root != null) {
            ArrayList<XNode> nodes = new ArrayList<>();
            nodes.add(root);
            ConditionListHandler.toForest(nodes);

            // mergable condition
            if(nodes.size() == 1) {
               query.setFilterNode(nodes.get(0));
               return getCubeTableFilter(getResult(query, vars), query);
            }
            // condition not mergable, post processing is necessary
            else {
               XMLAQuery[] queries = new XMLAQuery[nodes.size()];
               TableLens union = null;
               TableLens emptyTable = null;

               for(int i = 0; i < queries.length; i++) {
                  queries[i] = createBaseQuery();

                  for(int j = 0; j < clist.getSize(); j++) {
                     HierarchyItem item = (HierarchyItem) clist.getItem(j);

                     if(item instanceof ConditionItem) {
                        DataRef ref = ((ConditionItem) item).getAttribute();

                        if(cols.contains(ref)) {
                           continue;
                        }

                        ((ColumnRef) ref).setVisible(false);
                        cols.add(ref);
                     }
                  }

                  applyColumns(queries[i], cols);
                  queries[i].setFilterNode(nodes.get(i));

                  TableLens lens = getResult(queries[i], vars);

                  if(!lens.moreRows(lens.getHeaderRowCount())) {
                     emptyTable = lens;
                     continue;
                  }

                  if(union == null) {
                     union = lens;
                  }
                  else if(union.getColCount() < lens.getColCount()) {
                     union = lens;
                  }
                  else if(union.getColCount() == lens.getColCount()) {
                     union = new UnionTableLens(union, lens);
                  }
               }

               if("true".equalsIgnoreCase(table.getProperty("showDetail"))) {
                  LOG.debug("Drill-through may not work on multiple measures.");
               }

               if("true".equalsIgnoreCase(table.getProperty("isBrush"))) {
                  LOG.debug("Brush may not work on multiple selections.");
               }

               union = union == null ? emptyTable : union;

               return getCubeTableFilter(union, query);
            }
         }
      }

      XMLAQuery[] queries = optimizeQuery(query);

      if(queries.length == 1) {
         return getCubeTableFilter(getResult(queries[0], vars), query);
      }

      TableLens mergedTable = null;

      for(int i = 0; i < queries.length; i++) {
         TableLens lens = getResult(queries[i], vars);
         mergedTable = mergedTable == null ?
            lens : new MergedJoinTableLens2(mergedTable, lens);
      }

      return getCubeTableFilter(mergedTable, query);
   }

   /**
    * Add used column to query such as expression ref and condition.
    */
   private void addUsedColumns(XMLAQuery query) throws Exception {
      if(!isWorksheetCube()) {
         return;
      }

      List<DataRef> cols = new ArrayList<>();
      ColumnSelection bcolumns = table.getColumnSelection(false);
      boolean pub = table.isRuntime() || table.isAggregate();

      if(!table.isAggregate() || !isPreConditionListMergeable() ||
         !isAggregateMergable())
      {
         for(int i = 0; i < bcolumns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) bcolumns.getAttribute(i);

            if(column.isExpression() && column.isVisible()) {
               getContainedAttributes(column, false).stream()
                  .forEach(ref -> cols.add(new ColumnRef(ref)));
            }
         }
      }

      AggregateInfo ainfo = getTable().getAggregateInfo();

      if(!ainfo.isEmpty()) {
         for(int i = 0; i < ainfo.getGroupCount(); i++) {
            GroupRef gref = ainfo.getGroup(i);

            if(gref.isExpression()) {
               getContainedAttributes(gref, false).stream()
                  .forEach(ref -> cols.add(new ColumnRef(ref)));
            }
         }
         for(int i = 0; i < ainfo.getAggregateCount(); i++) {
            AggregateRef aref = ainfo.getAggregate(i);

            if(aref.isExpression()) {
               getContainedAttributes(aref, false).stream()
                  .forEach(ref -> cols.add(new ColumnRef(ref)));
            }
         }
      }

      ConditionList clist = getPreConditionList();

      for(int i = 0; i < clist.getSize(); i += 2) {
         ConditionItem item = clist.getConditionItem(i);
         ColumnRef column = null;

         if(item.getAttribute() instanceof ColumnRef) {
            column = (ColumnRef) item.getAttribute();
         }
         else if(item.getAttribute() instanceof GroupRef){
            GroupRef gref = (GroupRef) item.getAttribute();

            if(gref.getDataRef() instanceof ColumnRef) {
               column = (ColumnRef) gref.getDataRef();
            }
         }

         if(column != null) {
            column = findColumn(column);
            cols.add(column);
         }
      }

      ConditionListWrapper wrapper = getPostConditionList();
      clist = wrapper.getConditionList();

      applyColumns(query, cols);
   }

   /**
    * Get the Cube query's MDX or sql string.
    */
   public String getCubeDefinition() throws Exception {
      VariableTable vars = box.getVariableTable();
      XMLAQuery query = createBaseQuery();
      fixQueryColumn(vars, query);
      addUsedColumns(query);
      // @by davidd, 2011-11-02 v11.3, XMLA requests can be cancelled.
      List<DataRef> cols = new ArrayList<>();
      ColumnSelection columns = table.getColumnSelection(true);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         cols.add(columns.getAttribute(i));
      }

      if(!isWorksheetCube()) {
         ConditionList clist = getConditionList();
         convertCondition(query, clist);
         XNode root = ConditionListHandler.createFilterNode(clist);

         if(root != null) {
            ArrayList<XNode> nodes = new ArrayList<>();
            nodes.add(root);
            ConditionListHandler.toForest(nodes);

           if(nodes.size() != 1) {
               XMLAQuery[] queries = new XMLAQuery[nodes.size()];
               TableLens union = null;
               TableLens emptyTable = null;
               String definition = null;

               for(int i = 0; i < queries.length; i++) {
                  queries[i] = createBaseQuery();

                  for(int j = 0; j < clist.getSize(); j++) {
                     HierarchyItem item = (HierarchyItem) clist.getItem(j);

                     if(item instanceof ConditionItem) {
                        DataRef ref = ((ConditionItem) item).getAttribute();

                        if(cols.contains(ref)) {
                           continue;
                        }

                        ((ColumnRef) ref).setVisible(false);
                        cols.add(ref);
                     }
                  }

                  applyColumns(queries[i], cols);
                  queries[i].setFilterNode(nodes.get(i));
                  definition += queries[i].getMDXDefinition();
               }

               return adjustStyle(definition);
            }
         }
      }
      else {
         StringBuilder sb = new StringBuilder();
         StringBuilder formulae = new StringBuilder();

         sb.append(getVariablesString(query, vars));
         sb.append("[");
         sb.append(query.getMDXDefinition().trim());
         sb.append("]");

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);

            if(column.isExpression() && !column.isProcessed()) {
               if(formulae.length() != 0) {
                  formulae.append(", ");
               }

               if(formulae.length() == 0) {
                  formulae.append("[");
               }

               formulae.append(column);
            }
         }

         if(formulae.length() != 0) {
            formulae.append("]");
            sb.append("\r\n");
            sb.append(getPlanPrefix());
            sb.append(catalog.getString("common.formulaMerge",
               formulae));
         }

         ConditionList conds = getConditionList();

         if(conds != null && conds.getSize() != 0) {
            sb.append("\r\n");
            sb.append(getPlanPrefix());
            sb.append(catalog.getString("common.conditionMerge"));
         }

         SortInfo sinfo = getTable().getSortInfo();

         if(!sinfo.isEmpty()) {
            sb.append("\r\n");
            sb.append(getPlanPrefix());
            sb.append(catalog.getString("common.sortMerge"));
         }

         return sb.toString();
      }

      return adjustStyle(query.getMDXDefinition());
   }

   /**
    * Cube source will not merge sorting info.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeOrderBy() throws Exception {
      return false;
   }

   private void fixQueryColumn(VariableTable vars, XMLAQuery query)
      throws Exception
   {
      // @by davidd, 2011-11-02 v11.3, XMLA requests can be cancelled.
      // @by billh, fix customer bug bug1074749189530
      // apply max rows to cube as well, for it might be basing on logical model
      int max = getMaxRows(false);

      if(max > 0) {
         vars.put(XQuery.HINT_MAX_ROWS, max + "");
      }
      else {
         vars.remove(XQuery.HINT_MAX_ROWS);
      }

      query.setProperty("queryManager", box.getQueryManager());

      List<DataRef> cols = new ArrayList<>();
      ColumnSelection columns = table.getColumnSelection(true);

      // it works if columns number is not so large even all columns involved
      if(columns.getAttributeCount() > 100 &&
         columns.equals(table.getColumnSelection()) && isRealXMLA(query) &&
         !"true".equalsIgnoreCase(table.getProperty("showDetail")))
      {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "viewer.olap.errorCubeBinding"));
      }

      if(!isWorksheetCube()) {
         for(int i = 0; i < columns.getAttributeCount(); i++) {
            cols.add(columns.getAttribute(i));
         }
      }
      else {
         ColumnSelection bcolumns = table.getColumnSelection(false);
         boolean pub = table.isRuntime() || table.isAggregate();

         if(!(table.isAggregate() && isAggregateMergable()) ||
            !isPreConditionListMergeable())
         {
            for(int i = 0; i < bcolumns.getAttributeCount(); i++) {
               ColumnRef column = (ColumnRef) bcolumns.getAttribute(i);
               DataRef base = getBaseAttribute(column);

               if(!column.isVisible()) {
                  continue;
               }

               if(base instanceof AttributeRef) {
                  cols.add(new ColumnRef(base));
               }
               else if(base instanceof ExpressionRef) {
                  addBaseCols((ExpressionRef) base, cols, bcolumns);
               }
            }
         }

         AggregateInfo ainfo = getTable().getAggregateInfo();

         if(!ainfo.isEmpty()) {
            for(int i = 0; i < ainfo.getGroupCount(); i++) {
               GroupRef gref = ainfo.getGroup(i);

               if(!gref.isExpression()) {
                  ColumnRef column =
                     (ColumnRef) bcolumns.findAttribute(gref.getDataRef());

                  DataRef ref = getBaseAttribute(column);

                  if(ref != null && ref instanceof AttributeRef &&
                     column.isVisible())
                  {
                     cols.add(new ColumnRef(ref));
                  }
               }
            }

            for(int i = 0; i < ainfo.getAggregateCount(); i++) {
               AggregateRef aref = ainfo.getAggregate(i);

               if(!aref.isExpression()) {
                  ColumnRef column =
                     (ColumnRef) bcolumns.findAttribute(aref.getDataRef());

                  DataRef ref = getBaseAttribute(column);

                  if(ref != null && ref instanceof AttributeRef &&
                     column.isVisible())
                  {
                     cols.add(new ColumnRef(ref));
                  }
               }
            }
         }
      }

      applyColumns(query, cols);
   }

   private void addBaseCols(ExpressionRef exp, List<DataRef> cols, ColumnSelection columns) {
      if(exp == null) {
         return;
      }

      Enumeration enumeration = exp.getAttributes();

      while(enumeration.hasMoreElements()) {
         Object obj = enumeration.nextElement();

         if(obj instanceof AttributeRef) {
            AttributeRef attr = (AttributeRef) obj;
            DataRef ref = columns.getAttribute(attr.getName());

            if(ref != null) {
               cols.add(ref);
            }
         }
         else if(obj instanceof ExpressionRef) {
            addBaseCols((ExpressionRef) obj, cols, columns);
         }
      }
   }

   /**
    * Get the group info of the contained table assembly.
    * @return the group info of the contained table assembly.
    */
   @Override
   public AggregateInfo getAggregateInfo() {
      if(isWorksheetCube()) {
         return super.getAggregateInfo();
      }

      return new AggregateInfo();
   }

   /**
    * Adjust the style of a string.
    */
   private String adjustStyle(String str) {
      if(str == null) {
         return "";
      }

      int index = str.indexOf("SELECT");
      int index2 = str.indexOf("SELECT NON EMPTY");

      str = index >= 0 ? index2 >= 0 ? "SELECT NON EMPTY" + "\n" +
         str.substring(index2 + 16) : "SELECT" + "\n" + str.substring(index + 6)
         : str;

      index = str.indexOf("ON COLUMNS FROM");

      str = index >= 0 ? str.substring(0, index) + "\n" +
         "ON COLUMNS FROM" + "\n" + str.substring(index + 15) : str;

      index = str.indexOf("ON ROWS FROM");

      str = index >= 0 ? str.substring(0, index) + "\n" +
         "ON ROWS FROM" + "\n" + str.substring(index + 12) : str;

      return str + "\n" +
         Catalog.getCatalog().getString("common.queryMergeMDXOK");
   }

   /**
    * Convert condtion, convert the Date to unique name of the member.
    */
   private void convertCondition(XMLAQuery query, ConditionList clist) {
      if(clist == null) {
         return;
      }

      Map<String, TableLens> map = new HashMap<>();

      for(int i = 0; i < clist.getConditionSize(); i++) {
         ConditionItem item = clist.getConditionItem(i);

         if(item == null) {
            continue;
         }

         XMetaInfo minfo = getXMetaInfo(query, item.getAttribute());

         if(minfo == null || !minfo.isAsDate() ||
            minfo.getDatePattern() == null || "".equals(minfo.getDatePattern()))
         {
            continue;
         }

         SimpleDateFormat fmt = createDateFormat(minfo);
         Condition condition = (Condition) item.getXCondition();
         int op = condition.getOperation();
         DataRef ref = item.getAttribute();
         String name = ref.getName();
         TableLens lens = map.get(name);

         if(lens == null) {
            lens = getSelectResult(ref);
            map.put(name, lens);
         }

         if(op == XCondition.LESS_THAN || op == XCondition.GREATER_THAN ||
            op == XCondition.BETWEEN)
         {
            Condition cond = null;
            boolean timeDimension =
               (ref.getRefType() & DataRef.CUBE_TIME_DIMENSION) ==
               DataRef.CUBE_TIME_DIMENSION;

            if(op == XCondition.LESS_THAN) {
               cond = convertLessThan(condition, lens, fmt, timeDimension);
            }
            else if(op == XCondition.GREATER_THAN) {
               cond = convertGreaterThan(condition, lens, fmt, timeDimension);
            }
            else {
               cond = convertBetween(condition, lens, fmt, timeDimension);
            }

            item.setXCondition(cond);
            continue;
         }

         if(condition.isConvertingType() &&
            XSchema.isDateType(condition.getType()))
         {
            condition.setConvertingType(false);
            condition.setType(XSchema.STRING);
         }

         for(int j = 0; j < condition.getValueCount(); j++) {
            Object obj = condition.getValue(j);

            if(obj instanceof Date) {
               obj = findMemberObject(lens, (Date) obj, fmt);
               obj = obj == null ? null : obj.toString();
               condition.setValue(j, obj);
            }
         }
      }
   }

   /**
    * Find MemberObject from the lens, if the date is equal to obj.
    */
   private MemberObject findMemberObject(TableLens lens, Date date,
      DateFormat fmt)
   {
      if(date instanceof CubeDate) {
         return ((CubeDate) date).getMemberObject();
      }

      for(int i = 1; lens.moreRows(i); i++) {
         Object obj = lens.getObject(i, 0);

         if(obj instanceof MemberObject &&
            Tool.equals(date, parseDate(obj, fmt)))
         {
            return (MemberObject) obj;
         }
      }

      return null;
   }

   /**
    * Convert condtion, convert "greater than" to "one of".
    */
   private Condition convertGreaterThan(Condition condition, TableLens lens,
      DateFormat fmt, boolean timeDimension)
   {
      Condition cond = new Condition(false, false);
      cond.setOperation(XCondition.ONE_OF);
      Date date = parseDate(condition.getValue(0), fmt);
      lens.moreRows(Integer.MAX_VALUE);

      for(int i = lens.getRowCount() - 1; i > 0; i--) {
         Object obj = lens.getObject(i, 0);
         int c = Tool.compare(date, parseDate(obj, fmt));

         if(c < 0 || (c == 0 && condition.isEqual())) {
            cond.addValue(obj.toString());
         }
         else if(timeDimension) {
            break;
         }
      }

      return cond;
   }

   /**
    * Convert condtion, convert "less than" to "one of".
    */
   private Condition convertLessThan(Condition condition, TableLens lens,
      DateFormat fmt, boolean timeDimension)
   {
      Condition cond = new Condition(false, false);
      cond.setOperation(XCondition.ONE_OF);
      Date date = parseDate(condition.getValue(0), fmt);

      for(int i = 1; lens.moreRows(i); i++) {
         Object obj = lens.getObject(i, 0);
         int c = Tool.compare(date, parseDate(obj, fmt));

         if(c > 0 || (c == 0 && condition.isEqual())) {
            cond.addValue(obj.toString());
         }
         else if(timeDimension) {
            break;
         }
      }

      return cond;
   }

   /**
    * Convert condtion, convert "between" to "one of".
    */
   private Condition convertBetween(Condition condition, TableLens lens,
      DateFormat fmt, boolean timeDimension)
   {
      Condition cond = new Condition(false, false);
      cond.setOperation(XCondition.ONE_OF);
      Date date0 = parseDate(condition.getValue(0), fmt);
      Date date1 = parseDate(condition.getValue(1), fmt);

      for(int i = 1; lens.moreRows(i); i++) {
         Object obj = lens.getObject(i, 0);
         Date date = parseDate(obj, fmt);
         int c0 = Tool.compare(date0, date);

         if(c0 < 0 || (c0 == 0 && condition.isEqual())) {
            int c1 = Tool.compare(date1, date);

            if(c1 > 0 || (c1 == 0 && condition.isEqual())) {
               cond.addValue(obj.toString());
               continue;
            }
         }

         if(timeDimension) {
            break;
         }
      }

      return cond;
   }

   /**
    * Parse a obj to Date with fmt.
    */
   private static Date parseDate(Object obj, DateFormat fmt) {
      if(obj == null) {
         return null;
      }

      if(obj instanceof Date) {
         return (Date) obj;
      }

      String str = obj instanceof MemberObject ?
         ((MemberObject) obj).getFullCaption() : obj.toString();

      try{
         return fmt.parse(str);
      }
      catch(Exception e) {
         LOG.warn("Failed to parse date: " + str, e);
         return null;
      }
   }

   /**
    * Get the all values of the ref.
    */
   private TableLens getSelectResult(DataRef ref) {
      XMLAQuery squery = createSelectQuery(ref);

      try {
         XDataService service = XFactory.getDataService();
         XSessionManager mgr = XSessionManager.getSessionManager();
         TableLens lens = new XNodeTableLens(service.execute(mgr.getSession(),
            squery, null, box.getUser()));
         XMLAUtil.reset();
         return lens;
      }
      catch(Exception e) {
         LOG.error("Failed to execute the select query", e);
      }

      return null;
   }

   /**
    * Get the visible table lens.
    * @param base the specified base table.
    * @param vars the specified variable table.
    * @return the visible table lens.
    */
   @Override
   protected TableLens getVisibleTableLens(TableLens base, VariableTable vars)
      throws Exception
   {
      boolean showDetail = "true".equalsIgnoreCase(
         table.getProperty("showDetail"));

      if(showDetail && (XCube.SQLSERVER.equals(getCubeType()) ||
         XCube.MONDRIAN.equals(getCubeType())))
      {
         return base;
      }

      return super.getVisibleTableLens(base, vars);
   }

   /**
    * Get visible column selection.
    */
   @Override
   protected ColumnSelection getVisibleColumnSelection() {
      ColumnSelection sel = super.getVisibleColumnSelection();

      if(!isAggregateMergable()) {
         return sel;
      }

      AggregateInfo ainfo = getTable().getAggregateInfo();
      ColumnSelection cols = new ColumnSelection();

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef gref = ainfo.getGroup(i);
         DataRef ref = sel.findAttribute(gref.getDataRef());

         if(ref == null) {
            continue;
         }

         cols.addAttribute(ref);
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aref = ainfo.getAggregate(i);
         DataRef ref = sel.findAttribute(aref.getDataRef());

         if(ref == null) {
            continue;
         }

         cols.addAttribute(ref);
      }

      return cols;
   }

   /**
    * Get the table assembly to be executed.
    * @return the table assembly.
    */
   @Override
   protected TableAssembly getTable() {
      return table;
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) {
      // do nothing
      return true;
   }

   /**
    * If the merged columns are in a subquery, return the name
    * of the subquery (e.g. select col1 from (select ...) sub1).
    */
   @Override
   protected String getMergedTableName(ColumnRef column) {
      return null;
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() {
      return false;
   }

   /**
    * Get the string representation of an attribute column.
    * @param column the specified attribute column.
    * @return the string representation of the attribute column.
    */
   @Override
   protected String getAttributeColumn(AttributeRef column) {
      return column.getAttribute();
   }

   /**
    * Get the alias of a column.
    * @param attr the specified column.
    * @return the alias of the column, <tt>null</tt> if not exists.
    */
   @Override
   protected String getAlias(ColumnRef attr) {
      return attr.getAlias();
   }

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   @Override
   public JDBCQuery getQuery0() {
      return null;
   }

   /**
    * Get the target sql to merge into.
    * @return the target sql to merge into.
    */
   @Override
   protected UniformSQL getUniformSQL() {
      return null;
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      return null;
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      return table.getColumnSelection();
   }

   /**
    * Optimize xmla query.
    */
   private XMLAQuery[] optimizeQuery(XMLAQuery query) {
      // has measure, not requesting members only
      if(query.getMeasuresCount() > 0 || query.getFilterNode() != null) {
         return new XMLAQuery[] {query};
      }

      Collection<Dimension> c = query.getSelectedDimensions();

      // no merge if only one dimension
      if(c.size() == 1) {
         return new XMLAQuery[] {query};
      }

      List<XMLAQuery> queries = new ArrayList();
      Iterator<Dimension> it = c.iterator();

      while(it.hasNext()) {
         XMLAQuery query0 = createBaseQuery();
         queries.add(query0);

         String dim = it.next().getIdentifier();
         DataRef[] refs = query.getMemberRefs(dim);

         for(int i = 0; i < refs.length; i++) {
            query0.addMemberRef(refs[i]);
         }
      }

      if(queries.size() == 0) {
         return new XMLAQuery[] {query};
      }

      XMLAQuery[] qarr = new XMLAQuery[queries.size()];
      queries.toArray(qarr);

      return qarr;
   }

   /**
    * Create base xmla query.
    */
   private XMLAQuery createBaseQuery() {
      XMLAQuery query = isAggregateMergable() ?
         new XMLAQuery2() : new XMLAQuery();

      if("true".equals(table.getProperty("richlist"))) {
         query.setProperty("richlist", "true");
      }

      query.setDataSource(
         DataSourceRegistry.getRegistry().getDataSource(table.getSource()));
      query.setProperty("showDetail", table.getProperty("showDetail"));
      query.setProperty("noEmpty", table.getProperty("noEmpty"));
      query.setExpandedPaths(table.getExpandedPaths());

      SourceInfo sinfo = table.getSourceInfo();
      String cube = sinfo.getSource();

      if(cube.startsWith(Assembly.CUBE_VS)) {
         cube = cube.substring(Assembly.CUBE_VS.length());
         int idx = cube.lastIndexOf("/");
         cube = idx >= 0 ? cube.substring(idx + 1) : cube;
      }

      query.setCube(cube);

      if(isAggregateMergable()) {
         AggregateInfo ainfo = getTable().getAggregateInfo();
         XMLAQuery2 query2 = (XMLAQuery2) query;
         query2.setAggregateInfo((AggregateInfo) ainfo.clone());
         query2.convertAggregateInfo();
      }

      return query;
   }

   /**
    * Create a query for get values of the ref.
    */
   private XMLAQuery createSelectQuery(DataRef ref) {
      XMLAQuery query = new XMLAQuery();
      query.setDataSource(
         DataSourceRegistry.getRegistry().getDataSource(table.getSource()));
      query.setProperty("noEmpty", "false");

      SourceInfo sinfo = table.getSourceInfo();
      String cube = sinfo.getSource();

      if(cube.startsWith(Assembly.CUBE_VS)) {
         cube = cube.substring(Assembly.CUBE_VS.length());
         int idx = cube.lastIndexOf("/");
         cube = idx >= 0 ? cube.substring(idx + 1) : cube;
      }

      query.setCube(cube);
      query.setProperty("queryManager", box.getQueryManager());
      query.addMemberRef(ref);

      return query;
   }

   /**
    * Check if aggregate info is mergeable.
    */
   private boolean isAggregateMergable() {
      if(!XCube.SQLSERVER.equals(getCubeType())) {
         return false;
      }

      if(!isWorksheetCube()) {
         return AssetUtil.isMergeable(getTable().getAggregateInfo());
      }

      try {
         if(!isPreConditionListMergeable()) {
            return false;
         }
      }
      catch(Exception e) {
         return false;
      }

      AggregateInfo ainfo = getTable().getAggregateInfo();
      ColumnSelection sel = getTable().getColumnSelection();

      for(int i = 0; i < ainfo.getGroupCount(); i++) {
         GroupRef gref = ainfo.getGroup(i);
         DataRef ref = sel.findAttribute(gref.getDataRef());

         if(ref != null && (!isColumnMergeable(new ColumnRef(ref)) ||
               (ref.getRefType() & DataRef.MEASURE) == DataRef.MEASURE))
         {
            return false;
         }
      }

      for(int i = 0; i < ainfo.getAggregateCount(); i++) {
         AggregateRef aref = ainfo.getAggregate(i);
         DataRef ref = sel.findAttribute(aref.getDataRef());

         if(ref != null && (!isColumnMergeable(new ColumnRef(ref)) ||
            (ref.getRefType() & DataRef.DIMENSION) == DataRef.DIMENSION))
         {
            return false;
         }
      }

      return AssetUtil.isMergeable(getTable().getAggregateInfo());
   }

   /**
    * Apply column selection.
    */
   private void applyColumns(XMLAQuery query, List<DataRef> cols) {
      XDataSource xds = query.getDataSource();
      XCube cube = XMLAUtil.getCube(xds.getFullName(), query.getCube());
      Iterator<DataRef> it = cols.iterator();

      while(it.hasNext()) {
         DataRef column = it.next();

         if((column.getRefType() & DataRef.DIMENSION) != 0) {
            query.addMemberRef(column);
         }
         else if((column.getRefType() & DataRef.MEASURE) != 0) {
            if(cube instanceof Cube) {
               // give meausre caption
               Measure measure =
                  (Measure) cube.getMeasure(XMLAUtil.getAttribute(column));

               if(measure != null) {
                  ((ColumnRef) column).setCaption(measure.getCaption());
               }
            }

            query.addMeasureRef(column);
         }
      }
   }

   /**
    * Get filters nodes.
    */
   private ConditionList getConditionList() {
      ArrayList list = new ArrayList();
      ConditionList clist = getPreConditionList();

      list.add(clist.clone());
      clist.removeAllItems();

      ConditionListWrapper wrapper = getPostConditionList();
      clist = wrapper.getConditionList();

      list.add(clist.clone());
      clist.removeAllItems();

      clist = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);

      return clist;
   }

   /**
    * Check if is to execute xmla.
    */
   private boolean isRealXMLA(XMLAQuery query) {
      XDataSource xds = query.getDataSource();
      return XDataSource.XMLA.equals(xds.getType());
   }

   /**
    * Get cube filter.
    */
   private TableLens getResult(XMLAQuery query, VariableTable vars)
      throws Exception
   {
      XDataService service = XFactory.getDataService();
      XSessionManager mgr = XSessionManager.getSessionManager();
      TableLens base = new XNodeTableLens(service.execute(mgr.getSession(),
         query, vars, box.getUser()));
      DefaultTableFilter2 result = new DefaultTableFilter2(base);
      result.setWorksheetCube(isWorksheetCube());
      result.setColumns(getColumns(query, base));
      result.setXSelection(createXSelection(query, base));
      XMLAUtil.reset();

      return result;
   }

   /**
    * Get XMetaInfo from cube member.
    */
   private XMetaInfo getXMetaInfo(XCubeMember member) {
      return member.getXMetaInfo();
   }

   /**
    * Get XCube from XMLAQuery.
    */
   private XCube getXCube(XMLAQuery query) {
      XCube cube = null;

      if(query == null) {
         return cube;
      }

      XDataSource xds = query.getDataSource();

      if(xds == null) {
         return cube;
      }

      return XMLAUtil.getCube(xds.getFullName(), query.getCube());
   }

   /**
    * Get columns from a query.
    */
   private DataRef[] getColumns(XMLAQuery query, TableLens base) {
      DataRef[] cols = new DataRef[query.getColumnCount()];
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(base, true);

      for(int i = 0; i < cols.length; i++) {
         DataRef ref = i < query.getMembersCount() ?
            query.getMemberRef(i) :
            query.getMeasureRef(i - query.getMembersCount());
         int colIdx = Util.findColumn(columnIndexMap, ref);

         if(colIdx < 0 || colIdx >= cols.length) {
            String header = XMLAUtil.getHeader(ref);
            colIdx = Util.findColumn(columnIndexMap, header);

            if(colIdx < 0 || colIdx >= cols.length) {
               continue;
            }
         }

         cols[colIdx] = ref;
      }

      return cols;
   }

   /**
    * Get cube table filter.
    */
   private TableLens getCubeTableFilter(TableLens lens, XMLAQuery query) {
      if("true".equals(SreeEnv.getProperty("olap.security.enabled"))) {
         query.setProperty("RUN_USER", box.getUser());
      }

      if(isWorksheetCube()) {
         ColumnSelection columns = table.getColumnSelection(false);
         return fixColumnIdentifiers(lens, columns);
      }

      CubeTableFilter cubeFilter = new CubeTableFilter(lens, query);

      return cubeFilter;
   }

   private TableLens fixColumnIdentifiers(TableLens cubeFilter,
      ColumnSelection cols)
   {
      int r = 0;
      boolean changed = false;

      while(cubeFilter.moreRows(r) && r < cubeFilter.getHeaderRowCount()) {
         for(int c = 0; c < cubeFilter.getColCount(); c++) {
            for(int i = 0; i < cols.getAttributeCount(); i++) {
               DataRef ref = cols.getAttribute(i);

               if(ref instanceof ColumnRef) {
                  String header = ref.getAttribute();
                  DataRef sub = ((ColumnRef) ref).getDataRef();

                  if(sub instanceof AttributeRef) {
                     String caption =  ((AttributeRef) sub).getCaption();

                     if(caption != null &&
                        caption.equals(cubeFilter.getObject(r, c)))
                     {
                        cubeFilter.setObject(r, c, header);
                        cubeFilter.setColumnIdentifier(c, caption);
                        changed = true;
                        break;
                     }
                  }
               }
            }
         }

         r++;
      }

      // refresh descriptor after header changed.
      if(changed && cubeFilter instanceof DefaultTableFilter2) {
         ((DefaultTableFilter2) cubeFilter).refreshDesciptor();
      }

      return cubeFilter;
   }

   /**
    * Create an XSelection by an XMLAQuery.
    */
   private XSelection createXSelection(XMLAQuery query, TableLens base) {
      XSelection selection = new XSelection();
      XCube cube = getXCube(query);
      DataRef[] cols = getColumns(query, base);

      for(int i = 0; i < cols.length; i++) {
         selection.addColumn(null);

         if(cols[i] == null) {
            continue;
         }

         String entity = XMLAUtil.getEntity(cols[i]);
         String attr = XMLAUtil.getAttribute(cols[i]);
         XMetaInfo minfo = null;

         if((cols[i].getRefType() &
            DataRef.CUBE_MEASURE) == DataRef.CUBE_MEASURE)
         {
            XCubeMember member = cube.getMeasure(attr);

            if(member == null) {
               continue;
            }

            minfo = getXMetaInfo(member);
         }
         else {
            XDimension dim = cube.getDimension(entity);

            if(dim == null) {
               continue;
            }

            XCubeMember member = dim.getLevelAt(dim.getScope(attr));

            if(member == null) {
               continue;
            }

            minfo = getXMetaInfo(member);
         }

         selection.setXMetaInfo(i, minfo);
      }

      return selection;
   }

   private XMetaInfo getXMetaInfo(XMLAQuery query, DataRef ref) {
      XCube cube = getXCube(query);
      String entity = XMLAUtil.getEntity(ref);
      String attr = XMLAUtil.getAttribute(ref);
      XDimension dim = cube.getDimension(entity);

      if(dim == null) {
         return null;
      }

      int level = dim.getScope(attr);

      if(level < 0) {
         return null;
      }

      XCubeMember member = dim.getLevelAt(level);

      if(member == null) {
         return null;
      }

      return getXMetaInfo(member);
   }

   private static SimpleDateFormat createDateFormat(XMetaInfo minfo) {
      if(minfo == null || !minfo.isAsDate() || minfo.getDatePattern() == null ||
         "".equals(minfo.getDatePattern()))
      {
         return null;
      }

      Locale locale = minfo.getLocale();

      if(locale == null) {
         locale = Locale.getDefault();
      }

      return new SimpleDateFormat(minfo.getDatePattern(), locale);
   }

   static class DefaultTableFilter2 extends DefaultTableFilter {
      public DefaultTableFilter2() {
         super();
      }

      public DefaultTableFilter2(TableLens table) {
         super(table);
      }

      @Override
      public Class getColType(int col) {
         if(fmts[col] == null) {
            return super.getColType(col);
         }

         return Date.class;
      }

      @Override
      public Object getObject(int r, int c) {
         Object obj = matrix.get(r, c);

         if(r < getHeaderRowCount() || fmts[c] == null) {
            obj = super.getObject(r, c);

            if(!isWSCube) {
               return obj;
            }
            else if(obj instanceof MemberObject || obj instanceof String) {
               DataRef[] columns = getColumns();
               int type = columns[c].getRefType();

               return VSCubeTableLens.getDisplayValue(obj, type);
            }
            else {
               return obj;
            }
         }

         if(obj == SparseMatrix.NULL) {
            obj = super.getObject(r, c);

            if(obj instanceof MemberObject) {
               Date date = parseDate(obj, fmts[c]);

               if(date != null) {
                  obj = new CubeDate(date, (MemberObject) obj);
               }
               else {
                  obj = date;
               }
            }

            matrix.set(r, c, obj);
         }

         if(!isWSCube) {
            return obj;
         }
         else if(obj instanceof MemberObject || obj instanceof String) {
            DataRef[] columns = getColumns();
            int type = columns[c].getRefType();

            obj = VSCubeTableLens.getDisplayValue(obj, type);
         }

         return obj;
      }

      public DataRef[] getColumns() {
         return columns;
      }

      public void setColumns(DataRef[] columns) {
         this.columns = columns;
      }

      public void setWorksheetCube(boolean isWS) {
         this.isWSCube = isWS;
      }

      @Override
      public TableDataDescriptor getDescriptor() {
         if(desc == null) {
            desc = new Descriptor(this);
         }

         return desc;
      }

      public void refreshDesciptor() {
         desc = null;
         init();
      }

      public XSelection getSelection() {
         return selection;
      }

      public void setXSelection(XSelection selection) {
         this.selection = selection;
         init();
      }

      private void init() {
         matrix = new SparseMatrix();
         fmts = new SimpleDateFormat[getColCount()];

         for(int c = 0; c < getColCount(); c++) {
            TableDataPath path = getDescriptor().getColDataPath(c);
            XMetaInfo minfo = getDescriptor().getXMetaInfo(path);
            fmts[c] = createDateFormat(minfo);
         }
      }

      class Descriptor extends DefaultTableDataDescriptor {
         public Descriptor(XTable table) {
            super(table);
            this.table = table;
         }

         @Override
         public XMetaInfo getXMetaInfo(TableDataPath path) {
            String[] s = path.getPath();
            String name = s[s.length - 1];

            if(columnIndexMap == null) {
               columnIndexMap = new ColumnIndexMap(table, true);
            }

            int idx = Util.findColumn(columnIndexMap, name);
            return selection.getXMetaInfo(idx);
         }

         @Override
         public boolean containsFormat() {
            return true;
         }

         @Override
         public boolean containsDrill() {
            return true;
         }

         private XTable table;
         private transient ColumnIndexMap columnIndexMap = null;
      }

      private boolean isWSCube = false;
      private DataRef[] columns;
      private XTable table;
      private XSelection selection;
      private TableDataDescriptor desc;
      private SimpleDateFormat[] fmts;
      private SparseMatrix matrix;
      private boolean shouldRename = false;
   }

   private static class MergedJoinTableLens2 extends MergedJoinTableLens {
      /**
       * Constructor.
       */
      public MergedJoinTableLens2() {
         super();
      }

      /**
       * Constructor.
       */
      public MergedJoinTableLens2(TableLens ltable, TableLens rtable) {
         super(ltable, rtable);
      }

      /**
       * Return the value at the specified cell.
       * @param r row number.
       * @param c column number.
       * @return the value at the location.
       */
      @Override
      public Object getObject(int r, int c) {
         TableLens ltable = getLeftTable();
         TableLens rtable = getRightTable();
         int lcols = ltable.getColCount();
         boolean lmore = ltable.moreRows(r);
         boolean rmore = rtable.moreRows(r);

         if(!lmore && !rmore) {
            return null;
         }

         TableLens table = c < lcols ? ltable : rtable;
         boolean more = c < lcols ? lmore : rmore;
         int col = c < lcols ? c : c - lcols;

         return more ? table.getObject(r, col) :
            table.getObject(table.getHeaderRowCount(), col);
      }
   }

   private CubeTableAssembly table;
   private static final Logger LOG =
      LoggerFactory.getLogger(CubeQuery.class);
}
