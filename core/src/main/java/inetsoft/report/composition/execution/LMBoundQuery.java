/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.internal.DesignSession;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.ConditionListHandler;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

/**
 * Logical model bound query executes a bound table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class LMBoundQuery extends BoundQuery {
   /**
    * Create an asset query.
    */
   public LMBoundQuery(int mode, AssetQuerySandbox box, BoundTableAssembly table,
                       boolean stable, boolean metadata)
      throws Exception
   {
      super(mode, box, stable, metadata);

      this.table = table;
      Principal user = box.getUser();
      SourceInfo source = table.getSourceInfo();
      XRepository repository = XFactory.getRepository();
      this.model = repository.getDataModel(source.getPrefix());

      if(this.model == null) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.dataModelNotFound") + ": "
            + source.getPrefix());
      }

      this.lmodel = model.getLogicalModel(source.getSource(), user);

      if(this.lmodel == null) {
         throw new RuntimeException(Catalog.getCatalog().getString(
            "common.logicalModelNotFound",
            source.getSource(), source.getPrefix()));

      }

      nquery = new JDBCQuery();
      nquery.setUserQuery(true);
      nquery.setDataSource(repository.getDataSource(model.getDataSource()));
      UniformSQL sql = new UniformSQL();
      nquery.setSQLDefinition(sql);
      nquery.setPartition(lmodel.getPartition());
      nquery.setName(box.getWSName() + "." +
                     getTableDescription(table.getName()));

      aliases = new ArrayList();
      partition = model.getPartition(lmodel.getPartition(), user);

      if(partition != null) {
         partition = partition.applyAutoAliases();
      }

      this.format2 = new AttributeFormat() {
         @Override
         public String format(AttributeRef attr) {
            if(attr == null) {
               return null;
            }

            String col = getAttributeString(attr);
            colset.add(col);
            return col;
         }
      };

      colset = new HashSet();
      originalSortInfo = (SortInfo) Tool.clone(getSortInfo());
   }

   @Override
   protected Collection<?> getLogRecord() {
      String logicalModelPath = model.getDataSource() + "." + lmodel.getName();

      if(lmodel.getFolder() != null) {
         logicalModelPath = model.getDataSource() + "." + lmodel.getFolder() + "/" +
            lmodel.getName();
      }

      return Collections.singleton(LogContext.MODEL.getRecord(logicalModelPath));
   }

   /**
    * Get bound tables.
    */
   public String[] getBoundTables() {
      String[] tables = new String[aliases.size()];
      aliases.toArray(tables);
      return tables;
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
    * Get the post process base table.
    * @param vars the specified variable table.
    * @return the post process base table of the query.
    */
   @Override
   protected TableLens getPostBaseTableLens(VariableTable vars)
      throws Exception
   {
      throw new Exception("Invalid caller found!");
   }

   /**
    * Check if the source is mergeable.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSourceMergeable() throws Exception {
      return true;
   }

   @Override
   protected boolean isMergePreferred() {
      return true;
   }

   /**
    * Quote a column.
    * @param column the specified column.
    * @return the quoted column.
    */
   @Override
   protected String quoteColumn(String column) {
      if(column == null) {
         return column;
      }

      SQLHelper helper = getSQLHelper(getUniformSQL());
      int idx = column.lastIndexOf('.');

      if(idx > 0) {
         // split column can support dot in column properly
         String[] pair = splitColumn(column);
         String table = pair[0];
         String col = pair[1];
         table = isTable(table) ?
            XUtil.quoteName(table, helper) :
            XUtil.quoteAlias(table, helper);
         col = XUtil.quoteNameSegment(col, helper);
         return table + "." + col;
      }

      String txt = XUtil.quoteName(column, helper);
      return txt;
   }

   /**
    * Merge from clause.
    * @return <tt>true</tt> if fully merged, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean mergeFrom(VariableTable vars) throws Exception {
      UniformSQL nsql = getUniformSQL();
      OrderedSet originating = new OrderedSet();
      OrderedSet others = new OrderedSet();
      OrderedSet tableset = new OrderedSet();
      XJoin[] joins = nsql.getJoins();
      ColumnSelection columns = getTable().getColumnSelection();

      // maintain tables to stablize joins, otherwise the joins change heavily
      // when performing analysis
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(column.isVisible()) {
            continue;
         }

         DataRef ref = column.getDataRef();

         if(!(ref instanceof AttributeRef)) {
            continue;
         }

         XAttribute xattr = getXAttribute((AttributeRef) ref);

         if(xattr != null) {
            String[] tables = xattr.getTables();

            for(int j = 0; j < tables.length; j++) {
               if(!aliases.contains(tables[j])) {
                  aliases.add(tables[j]);
               }
            }
         }
      }

      for(int i = 0; i < aliases.size(); i++) {
         String alias = (String) aliases.get(i);
         XUtil.addTable(nsql, partition, alias);
         boolean found = false;

         for(int j = 0; joins != null && j < joins.length; j++) {
            if(joins[j].getTable1(nsql).equals(alias) ||
               joins[j].getTable2(nsql).equals(alias))
            {
               originating.add(alias);
               found = true;
               break;
            }
         }

         if(!found) {
            others.add(alias);
         }
      }

      if(originating.size() == 0 && others.size() > 0) {
         Object first = others.iterator().next();

         originating.add(first);
         others.remove(first);
      }

      tableset.addAll(originating);
      tableset.addAll(others);

      UniformSQL sql2 = new UniformSQL();
      XRelationship[] rels = partition.findRelationships(originating, others);

      OUTER:
      for(int k = 0; rels != null && k < rels.length; k++) {
         String lalias = rels[k].getDependentTable();
         String ralias = rels[k].getIndependentTable();

         for(int j2 = 0; joins != null && j2 < joins.length; j2++) {
            if((lalias.equals(joins[j2].getTable1(nsql)) &&
                ralias.equals(joins[j2].getTable2(nsql))) ||
               (lalias.equals(joins[j2].getTable2(nsql)) &&
                ralias.equals(joins[j2].getTable1(nsql))))
            {
               continue OUTER;
            }
         }

         if(!tableset.contains(lalias)) {
            XUtil.addTable(nsql, partition, lalias);
            tableset.add(lalias);
            Object table0 = partition.getRunTimeTable(lalias, true);
            String catalog = table0 instanceof String ?
               XUtil.getCatalog((String) table0) : null;

            if(catalog != null && catalog.length() > 0) {
               this.catalog = catalog;
            }
         }

         if(!tableset.contains(ralias)) {
            XUtil.addTable(nsql, partition, ralias);
            tableset.add(ralias);
            Object table0 = partition.getRunTimeTable(ralias, true);
            String catalog = table0 instanceof String ?
               XUtil.getCatalog((String) table0) : null;

            if(catalog != null && catalog.length() > 0) {
               this.catalog = catalog;
            }
         }

         String op = rels[k].getJoinType();
         String lcolumn = rels[k].getDependentColumn();
         String rcolumn = rels[k].getIndependentColumn();
         XJoin join = new XJoin(
            new XExpression(lalias + "." + lcolumn, XExpression.FIELD),
            new XExpression(ralias + "." + rcolumn, XExpression.FIELD),
            op);
         join.setOrder(rels[k].getOrder());
         sql2.addJoin(join, rels[k].getMerging());
      }

      XFilterNode node1 = sql2.getWhere();

      if(node1 != null) {
         XFilterNode node2 = nsql.getWhere();

         if(node2 != null) {
            XSet set = new XSet(XSet.AND);
            set.addChild(node1);
            set.addChild(node2);
            nsql.setWhere(set);
         }
         else {
            nsql.setWhere(node1);
         }
      }

      if(others.size() > 0) {
         LOG.warn("No join path found for tables: " + others);
      }

      return true;
   }

   /**
    * Get the default column selection.
    * @return the default column selection.
    */
   @Override
   protected ColumnSelection getDefaultColumnSelection0() {
      return box.getDefaultColumnSelection(
         table.getSourceInfo(), table.getColumnSelection());
   }

   /**
    * Validate the column selection.
    */
   @Override
   public void validateColumnSelection() {
      super.validateColumnSelection();

      ColumnSelection columns = getTable().getColumnSelection();
      Worksheet ws = getTable().getWorksheet();
      Assembly assembly = ws.getAssembly(getTable().getName());

      if(assembly instanceof TableAssembly) {
         if(table.isLiveData()) {
            for(int i = columns.getAttributeCount() - 1; i >= 0; i--) {
               if(isHiddenColumn((ColumnRef) columns.getAttribute(i))) {
                  columns.removeAttribute(i);
               }
            }
         }

         TableAssembly table = (TableAssembly) assembly;
         fixConditionColumns(table.getPreConditionList(), columns);
         fixConditionColumns(table.getPostConditionList(), columns);
         fixConditionColumns(table.getMVUpdatePreConditionList(), columns);
         fixConditionColumns(table.getMVUpdatePostConditionList(), columns);
         fixConditionColumns(table.getMVDeletePreConditionList(), columns);
         fixConditionColumns(table.getMVDeletePostConditionList(), columns);
      }
   }

   /**
    * Fix condition after validate columnselection in case the column names were changed
    * after fixing alias.(51064)
    */
   private void fixConditionColumns(ConditionListWrapper list, ColumnSelection columns) {
      if(list == null || columns == null) {
         return;
      }

      for(int i = 0; i < list.getConditionSize(); i++) {
         ConditionItem item = list.getConditionItem(i);

         if(item == null) {
            continue;
         }

         DataRef ref = item.getAttribute();

         if(!(ref instanceof ColumnRef)) {
            continue;
         }

         fixColumnAlias((ColumnRef) ref, columns);
      }
   }

   private void fixColumnAlias(ColumnRef column, ColumnSelection columns) {
      if(columns.getAttribute(column.getName(), false) != null) {
         return;
      }

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) columns.getAttribute(i);

         if(ref.getAlias() != null && ref.getDataRef() != null &&
            Objects.equals(ref.getDataRef().getName(), column.getName()))
         {
            column.setAlias(ref.getAlias());
            return;
         }
      }
   }

   /**
    * Check if is a sql expression.
    * @param ref the specified base data ref.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isSQLExpression(DataRef ref) {
      if(super.isSQLExpression(ref)) {
         return true;
      }

      if(!ref.isExpression()) {
         ref = getRealAttribute((AttributeRef) ref);
         return ref != null && ref.isExpression();
      }

      return false;
   }

   /**
    * Get the catalog.
    * @return the catalog part in sql statement if any.
    */
   @Override
   public String getCatalog() {
      return catalog;
   }

   /**
    * Check if is an qualified name.
    * @param name the specified name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isQualifiedName(String name) {
      if(super.isQualifiedName(name)) {
         return true;
      }

      if(colset.contains(name)) {
         return true;
      }

      return false;
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

      DataRef attr = getRealAttribute(column);

      if(attr == null) {
         return null;
      }

      if(attr.isExpression()) {
         // here we use format2 not to convert the attributes
         // in the expression, for they are physical attributes
         String res = getExpressionColumn((ExpressionRef) attr, format2, true);
         UniformSQL sql = getUniformSQL();

         if(sql != null) {
            String oexp = ((ExpressionRef) attr).getExpression();

            if(sql.containsExpression(oexp, true)) {
               sql.removeExpression(oexp);
               sql.addExpression(res);
            }
         }

         return res;
      }
      else {
         String col = getAttributeString(attr);
         colset.add(col);
         return col;
      }
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
      alias = attr.getAttribute();

      return requiresUpperCasedAlias(alias) ? alias.toUpperCase() : alias;
   }

   /**
    * Get the xattribute by one attribute ref.
    * @param attr the specified attribute ref.
    * @return the associated xattribute if any, <tt>null</tt> otherwise.
    */
   private XAttribute getXAttribute(AttributeRef attr) {
      String entity = attr.getEntity();
      String attribute = attr.getAttribute();
      String name = entity + "." + attribute;
      XAttribute xattr = attrmap.get(name);

      if(xattr == null) {
         XEntity xentity = lmodel.getEntity(entity);
         xattr = xentity == null ? null : xentity.getAttribute(attribute);

         // @by larryl, we need to clone and cache so we auto-drill is cleared
         // for aggregate column, it doesn't modify the original attr in the
         // model and causing all subsequence reports to lose the auto-drill
         if(xattr != null) {
            xattr = (XAttribute) xattr.clone();
            attrmap.put(name, xattr);
         }
      }

      return xattr;
   }

   /**
    * Check if is an aggregate data ref.
    */
   private boolean isAggregateExpressionAttribute(AttributeRef attr) {
      XAttribute xattr = getXAttribute(attr);

      if(!(xattr instanceof ExpressionAttribute)) {
         return false;
      }

      ExpressionAttribute eattr = (ExpressionAttribute) xattr;
      return eattr.isAggregateExpression();
   }

   /**
    * Check if contains column in the filter.
    */
   private boolean containsColumn(ColumnRef col, ConditionListWrapper wrapper) {
      ConditionList list = wrapper == null ? null : wrapper.getConditionList();

      if(list == null) {
         return false;
      }

      for(int i = 0; i < list.getConditionSize(); i += 2) {
         ConditionItem citem = wrapper.getConditionItem(i);
         DataRef ref = citem.getAttribute();

         if(ref.equals(col)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the target column is an aggregate expression column.
    */
   protected boolean isAggregateExpression(ColumnRef column) {
      return getContainedAttributes(column).stream()
               .filter(c -> isAggregateExpressionAttribute(c))
               .count() != 0;
   }

   /**
    * Validate the query.
    */
   @Override
   protected void validate() {
      ColumnSelection columns = getTable().getColumnSelection();
      ConditionListWrapper wrapper = getTable().getPreConditionList();
      ConditionListWrapper wrapper2 = getTable().getPreRuntimeConditionList();
      Set acolumns = new HashSet();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);

         if(!column.isVisible() && !containsColumn(column, wrapper) &&
            !containsColumn(column, wrapper2))
         {
            continue;
         }

         getContainedAttributes(column).stream()
            .filter(c -> isAggregateExpressionAttribute(c))
            .limit(1)
            .forEach(c -> acolumns.add(column));
      }

      if(acolumns.size() > 0 && !AssetQuerySandbox.isDesignMode(mode)) {
         originalAggregateInfo = (AggregateInfo) Tool.clone(getAggregateInfo());
         LOG.debug(
            "Replace aggregate info for aggregate expression");
         AggregateInfo ainfo =  new AggregateInfo();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) columns.getAttribute(i);

            if(!column.isVisible()) {
               boolean filtered = false;

               for(int j = 0; wrapper != null && j < wrapper.getConditionSize();
                  j += 2)
               {
                  ConditionItem citem = wrapper.getConditionItem(j);
                  DataRef ref = citem.getAttribute();

                  if(ref.equals(column)) {
                     filtered = true;
                     break;
                  }
               }

               for(int j = 0;
                  wrapper2 != null && j < wrapper2.getConditionSize(); j += 2)
               {
                  ConditionItem citem = wrapper2.getConditionItem(j);
                  DataRef ref = citem.getAttribute();

                  if(ref.equals(column)) {
                     filtered = true;
                     break;
                  }
               }

               if(!filtered) {
                  continue;
               }
            }

            if(!acolumns.contains(column)) {
               GroupRef group = new GroupRef(column);
               ainfo.addGroup(group);
            }
            else {
               AggregateRef aggregate =
                  new AggregateRef(column, AggregateFormula.NONE);
               ainfo.addAggregate(aggregate);
            }
         }

         boolean changed = false;

         for(int i = 0; wrapper != null && i < wrapper.getConditionSize();
            i += 2)
         {
            ConditionItem citem = wrapper.getConditionItem(i);
            DataRef ref = citem.getAttribute();

            if(ainfo.containsAggregate(ref)) {
               AggregateRef aref = ainfo.getAggregate(ref);
               citem.setAttribute(aref);
               changed = true;
            }
         }

         if(changed) {
            getTable().setPostConditionList(wrapper);
            getTable().setPreConditionList(new ConditionList());
         }

         boolean changed2 = false;

         for(int i = 0; wrapper2 != null && i < wrapper2.getConditionSize();
            i += 2)
         {
            ConditionItem citem = wrapper2.getConditionItem(i);
            DataRef ref = citem.getAttribute();

            if(ainfo.containsAggregate(ref)) {
               AggregateRef aref = ainfo.getAggregate(ref);
               citem.setAttribute(aref);
               changed2 = true;
            }
         }

         if(changed2) {
            getTable().setPostRuntimeConditionList(wrapper2);
            getTable().setPreRuntimeConditionList(new ConditionList());
         }

         if(getTable() instanceof BoundTableAssembly) {
            ((BoundTableAssembly) getTable()).setRuntimeAggregateInfo(ainfo);
         }
         else {
            getTable().setAggregateInfo(ainfo);
         }

         getTable().setAggregate(true);
      }

      super.validate();
   }

   @Override
   public AggregateInfo getPostAggregateInfo() {
      return originalAggregateInfo;
   }

   @Override
   public SortInfo getPostSortInfo() throws Exception {
      if(originalAggregateInfo == null || originalAggregateInfo.isEmpty()) {
         return null;
      }

      return originalSortInfo;
   }

   private boolean isHiddenColumn(ColumnRef column) {
      Enumeration<XEntity> entities = lmodel.getEntities();

      while(entities.hasMoreElements()) {
         XEntity entity = entities.nextElement();

         if(entity.isVisible() && !entity.isAttributeVisible(column.getAttribute())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the real attribute of a model attribute.
    * @param attr the specified model attribute.
    * @return the real attribute of the model attribute.
    */
   private DataRef getRealAttribute(AttributeRef attr) {
      XAttribute xattr = getXAttribute(attr);
      UniformSQL sql = getUniformSQL();

      if(sql != null && xattr instanceof ExpressionAttribute) {
         ExpressionAttribute attribute = (ExpressionAttribute) xattr;

         if(!attribute.isParseable()) {
            sql.addExpression(attribute.getExpression());
         }
      }

      // maintain tables
      String[] tables = xattr == null ? new String[0] : xattr.getTables();

      for(int i = 0; i < tables.length; i++) {
         if(!aliases.contains(tables[i])) {
            aliases.add(tables[i]);
         }
      }

      // is an expression attribute?
      if(xattr != null && xattr.isExpression()) {
         ExpressionRef exp = new ExpressionRef(null, xattr.getColumn());
         exp.setExpression(((ExpressionAttribute) xattr).getExpression());
         return exp;
      }
      // is a normal attribute?
      else if(xattr != null) {
         String entity = tables[0];
         String attribute = xattr.getColumn();
         return new AttributeRef(entity, attribute);
      }
      else {
         return null;
      }
   }

   /**
    * Get the target sql to merge into.
    * @return the target sql to merge into.
    */
   @Override
   protected UniformSQL getUniformSQL() {
      return nquery == null ? null : (UniformSQL) nquery.getSQLDefinition();
   }

   /**
    * Check if is a table or table alias.
    * @param tname the specified table name.
    * @return <tt>true</tt> if is a table, <tt>false</tt> table alias.
    */
   @Override
   protected boolean isTable(String tname) {
      return !partition.isRuntimeAlias(tname);
   }

   /**
    * If the merged columns are in a subquery, return the name
    * of the subquery (e.g. select col1 from (select ...) sub1).
    */
   @Override
   protected String getMergedTableName(ColumnRef column) {
      XEntity entity = lmodel.getEntity(column.getEntity());

      if(entity == null) {
         return null;
      }

      XAttribute attribute = entity.getAttribute(column.getAttribute());

      if(attribute == null) {
         return null;
      }

      return attribute.getTable();
   }

   /**
    * Build condition tree from condition list.
    */
   @Override
   protected XFilterNode createXFilterNode(ConditionListHandler handler,
                                           ConditionList conds,
                                           UniformSQL nsql,
                                           VariableTable vars) {
      return handler.createXFilterNode(conds, nsql, vars, partition, lmodel);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XMetaInfo getXMetaInfo(ColumnRef column, ColumnRef original) {
      DataRef attr = getRefForXMetaInfo(column);

      if(!(attr instanceof AttributeRef)) {
         return null;
      }

      XAttribute xattr = getXAttribute((AttributeRef) attr);
      XMetaInfo info = xattr == null ? null : xattr.getXMetaInfo();

      if(info != null && original.isExpression() &&
         !XSchema.areDataTypesCompatible(xattr.getDataType(), original.getDataType()))
      {
         info = null;
      }

      if(info != null) {
         info = info.clone();
         XDrillInfo dinfo = info.getXDrillInfo();

         if(dinfo != null) {
            dinfo.setColumn(attr);
         }
      }

      return fixMetaInfo(info, column, column);
   }

   /**
    * Check if the column should be added to the merged selection.
    * @param column the column to check for
    * @return <tt>true</tt> if exists, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean isColumnExists(DataRef column) {
      Enumeration entities = lmodel.getEntities();
      String entityName = column.getEntity();
      String attrName = column.getAttribute();

      // @by davidd 2009-02-09 bug1232014888564
      // iterate through all entities and attributes in the logical model
      // looking for the column argument. Need to double-check attribute
      // existence in-case user recently removed the column.
      while(entities.hasMoreElements()) {
         XEntity entity = (XEntity) entities.nextElement();

         if(entity.getName().equals(entityName)) {
            Enumeration attributes = entity.getAttributes();

            while(attributes.hasMoreElements()) {
               XAttribute attribute = (XAttribute) attributes.nextElement();

               if(attribute.getName().equals(attrName)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   protected XTypeNode getXTypeNode(ColumnSelection columns) {
      XTypeNode root = super.getXTypeNode(columns);
      DesignSession.setXMetaInfos(lmodel, columns, root);
      return root;
   }

   protected XLogicalModel lmodel; // logical model
   protected XDataModel model; // data model
   protected List aliases; // associated aliases
   protected XPartition partition; // partition
   protected AttributeFormat format2; // attribute format
   protected Set colset;  // plain columns used in the query
   protected String catalog; // catalog part

   // cached copy of XAttribute
   private Map<String, XAttribute> attrmap = new HashMap();
   private AggregateInfo originalAggregateInfo;
   private SortInfo originalSortInfo;

   private static final Logger LOG =
      LoggerFactory.getLogger(LMBoundQuery.class);
}
