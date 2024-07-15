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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.QueryTreeModel.QueryNode;
import inetsoft.report.internal.table.FilledTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Mirror query executes a mirror table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MirrorQuery extends AssetQuery {
   /**
    * Create an asset query.
    */
   public MirrorQuery(int mode, AssetQuerySandbox box, MirrorTableAssembly table,
                      boolean stable, boolean metadata, long ts)
      throws Exception
   {
      super(mode, box, stable, metadata);
      this.table = table;
      this.table.update();
      TableAssembly[] tables = table.getTableAssemblies(true);

      if(tables.length == 0) {
         throw new RuntimeException(catalog.getString("Mirrored table not found: " + table));
      }

      TableAssembly mirror = tables[0];

      if(isCube() && !isWorksheetCube()) {
         mirror = (TableAssembly)
            mirror.copyAssembly("___mirror___" + mirror.getName());
         ColumnSelection columns = mirror.getColumnSelection();

         if(!isAggregateMergable(mirror) && !baseOnVSComponent()) {
            String tname = tables[0].getName();

            for(int i = columns.getAttributeCount() - 1; i >= 0; i--) {
               ColumnRef column = (ColumnRef) columns.getAttribute(i);
               DataRef attr = AssetUtil.getOuterAttribute(tname, column);
               ColumnRef column2 = new ColumnRef(attr);

               if(isVisibleColumn(column2)) {
                  continue;
               }

               column.setVisible(false);
            }

            // keep columns even update table
            mirror.setColumnSelection(columns);
         }

         // show details
         if("true".equalsIgnoreCase(table.getProperty("showDetail"))) {
            mirror.setProperty("showDetail", "true");
         }

         if("true".equalsIgnoreCase(table.getProperty("isBrush"))) {
            mirror.setProperty("isBrush", "true");
         }

         if("false".equalsIgnoreCase(table.getProperty("noEmpty"))) {
            mirror.setProperty("noEmpty", "false");
         }

         if(table.getPreRuntimeConditionList() != null) {
            ConditionList clist = table.getPreRuntimeConditionList().getConditionList();

            if(clist != null) {
               for(int i = 0; i < clist.getSize(); i++) {
                  ConditionItem item = clist.getConditionItem(i);

                  if(item == null) {
                     continue;
                  }

                  DataRef ref = item.getAttribute();
                  item.setAttribute(getBaseColumn(ref, columns));
               }

               // fix bug1243851006886, merge conditions into cube table
               List<ConditionList> list = new ArrayList<>();
               list.add((ConditionList) clist.clone());
               ConditionListWrapper rwrapper = mirror.getPreRuntimeConditionList();
               list.add(rwrapper == null ? null : rwrapper.getConditionList());
               ConditionList conds = ConditionUtil.mergeConditionList(list, JunctionOperator.AND);

               mirror.setPreRuntimeConditionList(conds);
            }
         }
      }

      this.query = AssetQuery.createAssetQuery(mirror, fixSubQueryMode(mode),
                                               box, true, ts, false, metadata);
      this.query.setSubQuery(true);
      this.query.plan = this.plan;
      this.query.parentSQLExpressions.addAll(getSQLExpressions());

      colset = new HashSet<>();
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
    * Get all the table assemblies of the query.
    * @return all the table assemblies of the composite table assembly.
    */
   private TableAssembly[] getTableAssemblies() {
      if(this.query == null || isCube() && !isWorksheetCube()) {
         return this.table.getTableAssemblies(true);
      }

      TableAssembly assembly = this.query.getTable();

      if(assembly == null) {
         return new TableAssembly[0];
      }

      return new TableAssembly[] {assembly};
   }

   /**
    * Get pre runtime condition list.
    * @return the pre runtime condition list.
    */
   @Override
   protected ConditionList getPreRuntimeConditionList() {
      if(isCube() && !isWorksheetCube()) {
         return null;
      }

      return super.getPreRuntimeConditionList();
   }

   @Override
   protected Collection<?> getLogRecord() {
      if(query == null) {
         return Collections.emptySet();
      }

      return query.getLogRecord();
   }

   /**
    * Get the query time out.
    */
   @Override
   protected int getQueryTimeOut() {
      return query == null ? 0 : query.getQueryTimeOut();
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
      if(isCube() && !isWorksheetCube() && !baseOnVSComponent()) {
         return base;
      }

      return super.getVisibleTableLens(base, vars);
   }

   /**
    * Get the post process base table.
    * @param vars the specified variable table.
    * @return the post process base table of the query.
    */
   @Override
   protected TableLens getPostBaseTableLens(VariableTable vars) throws Exception {
      TableAssembly table2 = this.table.getTableAssembly();

      if(table2 == null) {
         return null;
      }

      query.setSubQuery(false);
      query.setTimeLimited(isTimeLimited());
      query.setQueryManager(getQueryManager());
      TableLens table = query.getTableLens(vars);

      // clear the alert message
      // Tool.getUserMessage();
      table = AssetQuery.shuckOffFormat(table);
      String tablePrefix = table2.getName() + ".";

      if(table == null) {
         return null;
      }

      for(int i = 0; i < table.getColCount(); i++) {
         String header = AssetUtil.format(XUtil.getHeader(table, i));
         String originalIdentifier = table.getColumnIdentifier(i);

         if(Tool.equals(XUtil.getDefaultColumnName(i), header) &&
            (StringUtils.isEmpty(originalIdentifier) || originalIdentifier.endsWith(".")))
         {
            header = tablePrefix;
         }
         else if(header != null && !header.startsWith(tablePrefix)) {
            header = tablePrefix + header;
         }

         table.setColumnIdentifier(i, header);
      }

      ColumnSelection cols = getTable().getColumnSelection(true);
      boolean nullExp = false;
      List<String> headers = new ArrayList<>();
      ColumnIndexMap columnIndexMap = new ColumnIndexMap(table);

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) cols.getAttribute(i);
         String header = isCube() && column.getCaption() != null ? column.getCaption() :
            column.getAttribute();

         if(AssetUtil.isNullExpression(column)) {
            headers.add(header);
            nullExp = true;
         }

         if(AssetUtil.findColumn(table, column, columnIndexMap) >= 0) {
            headers.add(header);
         }
      }

      if(nullExp) {
         table = new FilledTableLens(table, headers.toArray(new String[0]),
                                     null, null);
      }

      return table;
   }

   /**
    * gets the aggregate info to apply date range format
    */
   protected AggregateInfo getBasedAggregateInfo() {
      if(!this.ginfo.isEmpty()) {
         return this.ginfo;
      }

      AssetQuery baseQuery = this.query;
      AggregateInfo aInfo = baseQuery.ginfo.isEmpty() ? baseQuery.getAggregateInfo() : baseQuery.ginfo;

      while(aInfo.isEmpty() &&
         baseQuery instanceof MirrorQuery &&
         baseQuery.getChild(0) != null)
      {
         baseQuery = baseQuery.getChild(0);
         aInfo = baseQuery.ginfo.isEmpty() ? baseQuery.getAggregateInfo() : baseQuery.ginfo;
      }

      return aInfo;
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() throws Exception {
      if(!super.isSourceMergeable()) {
         return false;
      }

      boolean mergeable = query.isQueryMergeable(true);

      if(!mergeable) {
         return false;
      }

      UniformSQL sql = query.getUniformSQL();
      SQLHelper helper = getSQLHelper(sql);

      if(!helper.supportsOperation(SQLHelper.MIRROR_TABLE)) {
         return false;
      }

      final boolean maxRowsSupported = helper.supportsOperation(SQLHelper.MAXROWS);
      int maxrows = query.getDefinedMaxRows(maxRowsSupported);

      if(!helper.supportsOperation(SQLHelper.FROM_SUBQUERY)) {
         return false;
      }

      boolean runtime = (mode & AssetQuerySandbox.RUNTIME_MODE) != 0;
      boolean userMaxRows = query.getTable().getMaxRows() > 0;

      // maxrows in live mode is not defined by user, and can be ignored if the
      // pushdown should happen (e.g. contains sql expressions)
      if(maxrows > 0 && (runtime || userMaxRows) && !maxRowsSupported) {
         return false;
      }

      ColumnSelection cols = getTable().getColumnSelection(true);

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) cols.getAttribute(i);

         if(AssetUtil.isNullExpression(column)) {
            return false;
         }
      }

      if(query.getPostAggregateInfo() != null && !query.getPostAggregateInfo().isEmpty()) {
         return false;
      }

      return true;
   }

   /**
    * Get the string representation of an attribute column.
    * @param column the specified attribute column.
    * @return the string representation of the attribute column.
    */
   @Override
   protected String getAttributeColumn(AttributeRef column) {
      if(column == null) {
         return null;
      }

      // if a column is renamed to an alias, the column should be referred to using the
      // alias defined in sub-query. otherwise there will be a column not fould.
      // for example, a column col1 is aliased in sub-query (45140):
      // select col1 as ALIAS_1
      // if it's still referred to as col1 in this query, it would generate an invalid sql:
      // select col1 from (select col1 as ALIAS_1 ...)
      String subAlias = getColumnSubAlias(column.getAttribute());

      if(subAlias != null) {
         column = new AttributeRef(column.getEntity(), subAlias);
      }

      String col = getAttributeString(column);
      colset.add(col);

      return col;
   }

   /**
    * For a column, if it's aliased to a different name in sub-query, return the alias
    * used by the sub-query.
    */
   private String getColumnSubAlias(String col) {
      if(subAliases.containsKey(col)) {
         return subAliases.get(col);
      }

      if(nquery != null) {
         if(parsedQuery == null ||
            !((UniformSQL) parsedQuery.getSQLDefinition()).equalsStructure(nquery.getSQLDefinition()))
         {
            // Bug #45187, create copy of query before parsing to isolate side effects
            parsedQuery = (JDBCQuery) nquery.clone();
            parsedQuery.getSQLAsString();
         }

         UniformSQL uniformSQL = (UniformSQL) parsedQuery.getSQLDefinition();
         JDBCSelection selection = (JDBCSelection) uniformSQL.getSelection();
         String subAlias = selection.getColumnAlias(col);

         // optimization, pre populate the subAliases so we don't need to clone the
         // query every time. (53160)
         prepareSubAliases(selection);

         if(subAlias != null) {
            return subAlias;
         }
      }

      if(query instanceof MirrorQuery) {
         return ((MirrorQuery) query).getColumnSubAlias(col);
      }

      return null;
   }

   private void prepareSubAliases(JDBCSelection selection) {
      AggregateInfo info = getAggregateInfo();
      GroupRef[] groups = info.getGroups();

      // called from isGroupMergeable
      for(int i = 0; i < groups.length; i++) {
         DataRef base = getBaseAttribute(groups[i]);
         subAliases.put(base.getAttribute(), selection.getColumnAlias(base.getAttribute()));
      }

      // called from mergeSelect->mergeColumn
      for(int i = 0; i < used.getAttributeCount(); i++) {
         DataRef base = getBaseAttribute(used.getAttribute(i));
         subAliases.put(base.getAttribute(), selection.getColumnAlias(base.getAttribute()));
      }
   }

   /**
    * Check if a column is part of a selection.
    */
   @Override
   protected boolean isColumnOnSelection(DataRef column, XSelection sel) {
      String attr = column.getAttribute();

      if(attr == null) {
         return true;
      }

      attr = attr.toLowerCase();

      for(int j = 0; j < sel.getColumnCount(); j++) {
         String col = sel.getLowerCaseColumn(j);
         String alias = sel.getAlias(j);

         if(col.equals(attr) || col.endsWith("." + attr) || alias != null &&
            alias.equalsIgnoreCase(attr) || getColumnSubAlias(column.getAttribute()) != null)
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the alias of a column.
    * @param column the specified column.
    * @return the alias of the column, <tt>null</tt> if not exists.
    */
   @Override
   protected String getAlias(ColumnRef column) {
      String alias = column.getAlias();

      if(alias != null) {
         return requiresUpperCasedAlias(alias) ? alias.toUpperCase() : alias;
      }

      DataRef attr = getBaseAttribute(column);

      if(attr.isExpression()) {
         String name = attr.getName();
         return requiresUpperCasedAlias(name) ? name.toUpperCase() : name;
      }

      return null;
   }

   /**
    * Get the target jdbc query to merge into.
    * @return the target jdbc query to merge into.
    */
   @Override
   public JDBCQuery getQuery0() throws Exception {
      if(nquery != null) {
         return nquery;
      }

      if(!isSourceMergeable()) {
         return null;
      }

      if(nquery == null) {
         JDBCQuery query0 = query.getQuery();
         nquery = (JDBCQuery) query0.clone();
         nquery.setSQLDefinition(new UniformSQL());
         nquery.setName(box.getWSName() + "." + getTableDescription(table.getName()));
         parsedQuery = null;
      }

      return nquery;
   }

   /**
    * Get the target sql to merge into.
    * @return the target sql to merge into.
    */
   @Override
   protected UniformSQL getUniformSQL() {
      try {
         getQuery();
      }
      catch(Exception ex) {
         LOG.warn("Failed to get query for uniform SQL", ex);
      }

      return nquery == null ? null : (UniformSQL) nquery.getSQLDefinition();
   }

   /**
    * Merge the query.
    */
   @Override
   public void merge(VariableTable vars) throws Exception {
      if(!isSourceMergeable() || !isMergePreferred()) {
         ColumnSelection columns = getTable().getColumnSelection();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);
            column.setProcessed(false);
         }

         this.ginfo = (AggregateInfo) getAggregateInfo().clone();
         mergeOrderBy();

         return;
      }

      query.merge(vars);
      super.merge(vars);
   }

   /**
    * Get the sorting column of table.
    */
   @Override
   protected SortInfo getSortInfo() throws Exception {
      SortInfo sinfo = super.getSortInfo();

      // if no sorting defined on mirror, get the sorting from the base since
      // the subquery sorting will be discarded in sql
      if(sinfo.isEmpty() && isSourceMergeable()) {
         sinfo = new SortInfo();
         TableAssembly base = table.getTableAssembly();
         ColumnSelection cols = table.getColumnSelection(true);
         ColumnSelection bcols = base.getColumnSelection(true);
         SortInfo binfo = base.getSortInfo();

         if(binfo.isEmpty()) {
            binfo = query.getSortInfo();
         }

         for(int i = 0; i < binfo.getSortCount(); i++) {
            SortRef col = binfo.getSort(i);
            ColumnRef ref = AssetUtil.getColumnRefFromAttribute(bcols, col);

            if(ref == null) {
               continue;
            }

            DataRef nref = AssetUtil.getOuterAttribute(base.getName(), ref);
            ref = new ColumnRef(nref);
            ColumnRef ref2 = AssetUtil.getColumnRefFromAttribute(cols, ref);

            if(ref2 == null) {
               continue;
            }

            SortRef col2 = new SortRef(ref2);
            col2.setOrder(col.getOrder());
            sinfo.addSort(col2);
         }

         copyQueryOrderBy(sinfo);
      }

      return sinfo;
   }

   /**
    * fix Bug #21541, orderby fields be lost when create new uniformsql for
    * source mergeable mirror query. So here copy the orderbys to fix this bug.
    *
    * @param sinfo the new SoftInfo which need to add sorts from old uniformSQL.
    */
   private void copyQueryOrderBy(SortInfo sinfo) {
      if(!sinfo.isEmpty()) {
         return;
      }

      UniformSQL osql = query.getUniformSQL();

      if(osql == null || osql.getOrderByFields() == null ||
         osql.getOrderByFields().length == 0)
      {
         return;
      }

      TableAssembly base = table.getTableAssembly();
      ColumnSelection cols = table.getColumnSelection(true);
      // this has side effects, so it needs to be kept
      base.getColumnSelection(true);
      Object[] fields = osql.getOrderByFields();

      for(Object fieldObj : fields) {
         String field = fieldObj == null ? null : fieldObj + "";
         ColumnRef ref = getColumnRefFromAttribute(cols, field);

         if(ref == null) {
            continue;
         }

         DataRef nref = AssetUtil.getOuterAttribute(base.getName(), ref);
         ref = new ColumnRef(nref);
         ColumnRef ref2 = AssetUtil.getColumnRefFromAttribute(cols, ref);

         if(ref2 == null) {
            continue;
         }

         SortRef col2 = new SortRef(ref2);
         String sort = osql.getOrderBy(fieldObj);
         int order = "asc".equals(sort) ? XConstants.SORT_ASC :
            "desc".equals(sort) ? XConstants.SORT_DESC : XConstants.SORT_NONE;
         col2.setOrder(order);
         sinfo.addSort(col2);
      }
   }

   private ColumnRef getColumnRefFromAttribute(ColumnSelection columns, String field) {
      Enumeration iter = columns.getAttributes();

      while(iter.hasMoreElements()) {
         ColumnRef dref = (ColumnRef) iter.nextElement();
         String alias = dref.getAlias();
         int idx = field.lastIndexOf(".");
         field = idx != -1 ? field.substring(idx + 1) : field;

         if(Tool.equals(alias, field) || Tool.equals(dref.getAttribute(), field)) {
            return dref;
         }
      }

      return null;
   }

   /**
    * Check if the column should be added to the merged selection.
    */
   @Override
   protected boolean isColumnExists(DataRef column) {
      XSelection sel = query.getUniformSQL().getSelection();
      return isColumnOnSelection(column, sel);
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) throws Exception {
      // for mirror/join/concatenate query, the tables are sub queries
      UniformSQL sql = query.getUniformSQL();
      TableAssembly table = this.table.getTableAssembly();

      if(table == null) {
         throw new Exception("Mirror table not found: " + this.table);
      }

      JDBCDataSource ds = sql.getDataSource();

      // Bug #11389, sub sql should not remove order by fields. otherwise when table
      // set max row and change sort, the mirror table data is error.
      // @by stephenwebster, For Bug #9482, create compromise for #11389.
      // It should be ok to remove the order by fields if there is no row limit.
      // Some databases (like Intersystems Caché) will treat an order by clause
      // in a sub-select as invalid SQL, and it doesn't make sense to keep
      // them in cases where a row limit does not exist.
      if(query.getDefinedMaxRows() == 0 && Tool.equals(ds.getDriver(), "com.intersys.jdbc.CacheDriver")) {
         sql.removeAllOrderByFields();
      }

      sql.setSubQuery(true);
      sql.setCacheable(true);

      UniformSQL sql0 = getUniformSQL();
      sql0.addTable(table.getName(), sql);

      return true;
   }

   /**
    * If the merged columns are in a subquery, return the name
    * of the subquery (e.g. select col1 from (select ...) sub1).
    */
   @Override
   protected String getMergedTableName(ColumnRef column) {
      TableAssembly table = this.table.getTableAssembly();
      return table.getName();
   }

   /**
    * Validate the column selection.
    */
   @Override
   public void validateColumnSelection() {
      final ColumnSelection defcols = getDefaultColumnSelection();
      // basecols may be modified in validateColumnSelection(). it shouldn't affect
      // the base table so we make a copy (39825).
      final ColumnSelection basecols = getTable().getColumnSelection().clone();

      validateColumnSelection(defcols, basecols, true, true, false, true);
      boolean visible = false;

      for(int i = 0; i < basecols.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) basecols.getAttribute(i);

         if(column.isVisible()) {
            visible = true;
            break;
         }
      }

      if(!visible) {
         for(int i = 0; i < basecols.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) basecols.getAttribute(i);
            column.setVisible(true);
         }
      }

      getTable().setColumnSelection(basecols);
   }

   /**
    * Check if is an qualified name.
    * @param name the specified name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isQualifiedName(String name) {
      return super.isQualifiedName(name) || colset.contains(name);
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      // Bug #38117, use the cloned, cached child table assembly. When invoked due to
      // ViewsheetSandbox.reset(), this.table.getTableAssembly() won't have the columns validated
      // yet. In the constructor, an asset query is created using this cloned copy. That copy will
      // have the hidden columns applied.
      TableAssembly[] tables = getTableAssemblies();

      if(tables == null || tables.length == 0) {
         return null;
      }

      TableAssembly table = tables[0];

      if(table == null) {
         return null;
      }

      String tname = table.getName();
      ColumnSelection columns = new ColumnSelection();
      query.validateColumnSelection();
      ColumnSelection tcolumns = table.getColumnSelection(true);
      AggregateInfo aggregateInfo = table.getAggregateInfo();

      for(int i = 0; i < tcolumns.getAttributeCount(); i++) {
         ColumnRef tcolumn = (ColumnRef) tcolumns.getAttribute(i);
         DataRef attr = AssetUtil.getOuterAttribute(tname, tcolumn);
         String dtype = tcolumn.getDataType();
         ColumnRef column = new ColumnRef(attr);

         if(aggregateInfo != null) {
            GroupRef group = aggregateInfo.getGroup(tcolumn);

            if(group != null && group.getNamedGroupInfo() != null &&
               !group.getNamedGroupInfo().isEmpty())
            {
               dtype = XSchema.STRING;
            }
         }

         column.setDataType(dtype);
         columns.addAttribute(column, false);
      }

      return columns;
   }

   /**
    * Set the query level.
    * @param level the specified query level.
    */
   @Override
   public void setLevel(int level) {
      super.setLevel(level);
      query.setLevel(level + 1);
   }

   /**
    * Get a text description of how the query will be executed.
    * @return the text description.
    */
   @Override
   public QueryNode getQueryPlan() throws Exception {
      QueryNode plan = super.getQueryPlan();
      StringBuilder buffer = new StringBuilder();
      String desc = catalog.getString("common.mirrorMerge");
      buffer.append(getVariablesString(null));
      buffer.append(desc);
      QueryNode qnode = (plan != null) ? plan : getQueryNode(buffer.toString());
      qnode.setRelation("Mirror");
      qnode.addNode(query.getQueryPlan());

      return qnode;
   }

   /**
    * Set the plan flag.
    * @param plan <tt>true</tt> if for plan only, <tt>false</tt> otherwise.
    */
   @Override
   public void setPlan(boolean plan) {
      super.setPlan(plan);
      query.setPlan(plan);
   }

   /**
    * Get the number of child queries.
    */
   @Override
   public int getChildCount() {
      return 1;
   }

   /**
    * Get the specified child query.
    */
   @Override
   public AssetQuery getChild(int idx) {
      return query;
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      return query.getCatalog();
   }

   /**
    * Get query icon.
    */
   @Override
   protected String getIconPath() {
      return "/inetsoft/report/gui/composition/images/mirror.png";
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XMetaInfo getXMetaInfo(ColumnRef column, ColumnRef original) {
      DataRef ref = getRefForXMetaInfo(column);

      if(!(ref instanceof AttributeRef)) {
         return null;
      }

      AttributeRef attr = (AttributeRef) ref;
      ColumnSelection cols = query.getTable().getColumnSelection(true);
      ColumnRef column1 = null;
      ColumnRef column2 = null;

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         ColumnRef tcolumn = (ColumnRef) cols.getAttribute(i);

         if(attr.getAttribute().equals(tcolumn.getAlias())) {
            column1 = tcolumn;
            break;
         }

         if(tcolumn.getAlias() == null &&
            attr.getAttribute().equals(tcolumn.getAttribute()))
         {
            column2 = tcolumn;
         }
      }

      ColumnRef tcolumn = column1 != null ?  column1 : column2;
      XMetaInfo info = tcolumn == null ? null : query.getXMetaInfo(tcolumn, original);
      return fixMetaInfo(info, tcolumn, column, query);
   }

   /**
    * Get original data ref.
    */
   private DataRef getOriginalDataRef(DataRef ref0) {
      if(ref0.isExpression()) {
         if(ref0 instanceof ColumnRef) {
            ref0 = ((ColumnRef) ref0).getDataRef();

            if(ref0 instanceof NamedRangeRef) {
               return ((NamedRangeRef) ref0).getDataRef();
            }
            else if(ref0 instanceof AliasDataRef) {
               return ((AliasDataRef) ref0).getDataRef();
            }
         }
      }

      return ref0;
   }

   /**
    * Check if a column is used in mirror table.
    */
   private boolean isVisibleColumn(ColumnRef column) {
      ColumnSelection columns = getTable().getColumnSelection(true);

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         DataRef ref0 = columns.getAttribute(i);

         if(Tool.equals(column, ref0)) {
            return true;
         }
         else if(Tool.equals(column, getOriginalDataRef(ref0))) {
            return true;
         }
      }

      ConditionList conditions = getPreConditionList();

      if(conditions != null && conditions.getConditionItem(column) != null) {
         return true;
      }

      ConditionListWrapper wrapper = getPostConditionList();

      if(wrapper != null) {
         conditions = wrapper.getConditionList();
         return conditions != null && conditions.getConditionItem(column) != null;
      }

      return false;
   }

   /**
    * Get base column form base table.
    */
   private DataRef getBaseColumn(DataRef ref, ColumnSelection columns) {
      if(columns.containsAttribute(ref)) {
         return ref;
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         String name = column.getAlias();
         name = name == null || name.length() == 0 ?
            column.getAttribute() : name;

         if(Tool.equals(ref.getAttribute(), name)) {
            return column;
         }
         else if(Tool.equals(getOriginalDataRef(ref).getAttribute(), name)) {
            return column;
         }
      }

      return ref;
   }

   /**
    * Check is aggregate info should be merged into MDX.
    */
   private boolean isAggregateMergable(TableAssembly mirror) {
      return XCube.SQLSERVER.equals(getCubeType()) &&
         AssetUtil.isMergeable(mirror.getAggregateInfo());
   }

   /**
    * Check if is drill through operation.
    */
   private boolean isCube() {
      return getCubeType() != null;
   }

   /**
    * Check if is cube worksheet.
    */
   @Override
   protected boolean isWorksheetCube() {
      if(!isCube()) {
         return false;
      }

      return super.isWorksheetCube();
   }

   /**
    * Quote a column.
    * @param column the specified column.
    * @return the quoted column.
    */
   @Override
   protected String quoteColumn(String column) {
      SQLHelper helper = getSQLHelper(getUniformSQL());
      int idx = column.lastIndexOf('.');

      if(idx > 0) {
         String[] pair = splitColumn(column);
         assert pair != null;
         String table = pair[0];
         String col = pair[1];
         table = XUtil.quoteAlias(table, helper);
         col = XUtil.quoteNameSegment(col, helper);
         return table + "." + col;
      }

      return XUtil.quoteName(column, helper);
   }

   @Override
   protected Object getQueryProperty(String name) {
      Object val = super.getQueryProperty(name);

      if(val == null) {
         val = query.getQueryProperty(name);
      }

      return val;
   }

   private boolean baseOnVSComponent() {
      TableAssembly table = getTable();

      if(table instanceof MirrorTableAssembly) {
         TableAssembly tableAssembly = ((MirrorTableAssembly) table).getTableAssembly();

         return tableAssembly != null &&
            "true".equals(tableAssembly.getProperty("Component_Binding_Table"));
      }

      return false;
   }

   @Override
   protected boolean validColSel(ColumnSelection cols) {
      return cols != null && !(cols instanceof NullColumnSelection);
   }

   private MirrorTableAssembly table; // mirror table assembly
   private AssetQuery query; // base query
   private JDBCQuery nquery; // new query
   private JDBCQuery parsedQuery;
   private Set<String> colset; // columns
   private final Map<String, String> subAliases = new HashMap<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(MirrorQuery.class);
}
