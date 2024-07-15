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
package inetsoft.report.internal.binding;

import inetsoft.report.StyleConstants;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.ConditionListHandler;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;

/**
 * QueryGenerator is used to generate new query after binding element to a
 * base query.
 * <p>
 * It will merge the base binding attr's column info, order info, condition
 * info(if possible), etc into the base query, and the binding attr will be
 * changed accordingly. After the negotiation, the new query will be executed,
 * and the new binding attr will be applied in post process.
 *
 * @version 6.0
 * @author InetSoft Technology Corp
 */
public class QueryGenerator {

   /**
    * Get xquery to run.
    *
    * @param columns column selection.
    * @param vars the variable table
    * @param user a Principal object that identifies the user executing the
    *             query.
    *
    * @return the xquery to run, null means invalid
    */
   public static XQuery getXQuery(XDataSelection columns, VariableTable vars,
                                  Principal user) throws Exception
   {
      XRepository repository = XFactory.getRepository();
      XDataModel model = null;
      XLogicalModel lmodel = null;
      QueryGenerator gen = null;
      XQuery query = null;

      if(columns.isFromModel()) {
         String path = columns.getSource();
         int dot = path.indexOf('.');
         String prefix = null, source = null;

         if(dot >= 0) {
            prefix = path.substring(0, dot);
            source = path.substring(dot + 1);
         }
         else {
            prefix = null;
            source = path;
         }

         if(prefix != null) {
            model = repository.getDataModel(prefix);

            if(model != null) {
               lmodel = model.getLogicalModel(source, user);
            }

            if(lmodel != null) {
               gen = new LMQueryGenerator(lmodel, user);
               query = new JDBCQuery();
               ((JDBCQuery) query).setUserQuery(true);
               UniformSQL sql = new UniformSQL();
               ((JDBCQuery) query).setSQLDefinition(sql);
               query.setDataSource(repository.getDataSource(
                  lmodel.getDataSource()));
               sql.setParseResult(UniformSQL.PARSE_SUCCESS);
               sql.setDistinct(columns.isDistinct());
               query.setPartition(lmodel.getPartition());

               XLogicalModel base = lmodel.getBaseModel();
               String queryname = base != null ? "Extended Model: " +
                  lmodel.getName() + "; Logic Model: " + base.getName() :
                  "Logic Model: " + lmodel.getName();
               query.setName(queryname);
            }
         }
      }
      else {
         gen = new QueryGenerator();
         query = XUtil.getXQuery(columns.getSource());

         if(query != null) {
            query = (XQuery) query.clone();
            query.setName("Query: " + query.getName());

            if(query instanceof JDBCQuery) {
               JDBCQuery jquery = (JDBCQuery) query;
               SQLDefinition sql = jquery.getSQLDefinition();

               if(sql instanceof UniformSQL) {
                  UniformSQL usql = (UniformSQL) sql;
                  XJoin[] joins = usql.getJoins();
                  List<XJoin> list = joins == null ? null : Arrays.asList(joins);
                  usql.setOriginalJoins(list);
               }
            }
         }
      }

      if(gen != null) {
         return gen.generateQuery(columns, query, vars, model, null, user, null);
      }

      return null;
   }

   /**
    * Check if an attr is a model attr.
    *
    * @param attr the specified attr.
    * @return true if is, false otherwise
    */
   protected static boolean isModelAttribute(AttributeRef attr) {
      return attr.getEntity() != null;
   }

   /**
    * Check if sort order of an column selection and a group info is mergeable.
    *
    * @param csel the specified column selection
    */
   protected static boolean isOrderMergeable(ColumnSelection csel) {
      int colidx = 0;

      for(; colidx < csel.getAttributeCount(); colidx++) {
         DataRef ref = csel.getAttribute(colidx);

         if(!(ref instanceof Field)) {
            continue;
         }

         Field fld = (Field) ref;

         // script field contains sort order isn't sort order mergeable
         if(isScriptField(fld) && fld.getOrder() != StyleConstants.SORT_NONE) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if a field is a script formula field.
    *
    * @param fld the specified field
    * @return true if is, false otherwise
    */
   protected static boolean isScriptField(DataRef fld) {
      return fld instanceof FormulaField && !((FormulaField) fld).isSQL();
   }

   /**
    * Get sort order of a data ref.
    *
    * @param ref the specified data ref
    * @return the sort order of the data ref
    */
   protected static int getSortOrder(DataRef ref) {
      Field fld = (Field) ref;
      return fld.getOrder();
   }

   /**
    * Clear sort order of a data ref.
    *
    * @param ref the specified data ref
    */
   protected static void clearSortOrder(DataRef ref) {
      if(!(ref instanceof Field)) {
         return;
      }

      Field fld = (Field) ref;
      fld.setOrder(StyleConstants.SORT_NONE);
   }

   /**
    * Check if a condition list is mergeable in query generator.
    *
    * @param conds the specified condition list
    * @param helper the specified sqlhelper.
    * @return true if the specified condition list is mergeable, false otherwise
    */
   protected static boolean isConditionMergeable(ConditionList conds, SQLHelper helper)
   {
      for(int i = 0; i < conds.getSize(); i += 2) {
         DataRef ref = conds.getAttribute(i);

         if((ref instanceof ExpressionRef) && !((ExpressionRef) ref).isSQL()) {
            return false;
         }

         if(ref instanceof BaseField && ((BaseField) ref).getSource() != null) {
            return false;
         }

         XCondition con = conds.getXCondition(i);

         if(con != null && helper instanceof MongoHelper && con.getOperation() == XCondition.ONE_OF)
         {
            return false;
         }

         if(con instanceof DateCondition && !(con instanceof DateRange)) {
            continue;
         }

         if(!(con instanceof Condition)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Convert ConditonList to XFilterNode.
    *
    * @param conditions the condition list
    * @param sql the uniform sql
    * @param vars the variable table
    * @return the converted XFilterNode
    */
   protected static XFilterNode convertCondition(ConditionList conditions,
                                                 UniformSQL sql,
                                                 VariableTable vars) {
      ConditionListHandler handler = new ConditionListHandler();
      return handler.createXFilterNode(conditions, sql, vars);
   }

   /**
    * Constructor.
    */
   public QueryGenerator() {
      super();
   }

   /**
    * Get original type of a Field.
    */
   private static String getOriginalType(Field field) {
      Field field2 = field;

      while(field2 instanceof FormulaField) {
         field2 = ((FormulaField) field2).getOriginalField();
      }

      if(field2 != null) {
         return field2.getDataType();
      }
      else {
         return field == null ? XSchema.STRING : field.getDataType();
      }
   }

   /**
    * Clear the query generator for reuse.
    */
   public void clear() {
      expressions.clear();
   }

   /**
    * Check whether an attribute ref is an aggregate expression.
    */
   public boolean isAggregateExpression(DataRef ref) {
      return false;
   }

   /**
    * Merge the binding attr into base binding query.
    *
    * @param dsel the binding info
    * @param query the base query
    * @param vars the variable table for the query
    * @param model the data model contains the query
    * @param isDateRangeMergeable check if formula field for date range column should be merge to sql.
    * @return a new query after mergence
    */
   public XQuery generateQuery(XDataSelection dsel, XQuery query,
                               VariableTable vars, XDataModel model,
                               SourceAttr sourceAttr, Principal user,
                               Function<FormulaField, Boolean> isDateRangeMergeable)
   {
      // if query is not mergeable, ignore mergence
      if(!XUtil.isQueryMergeable(query)) {
         LOG.debug("Query is not mergeable, filter in post-processing: " + query);
         return query;
      }

      jquery = (JDBCQuery) query.clone();
      sql = (UniformSQL) jquery.getSQLDefinition();
      sql.setDataSource((JDBCDataSource) jquery.getDataSource());
      sql.setBackupSelection((XSelection) sql.getSelection().clone());
      XSelection bsel = (XSelection) sql.getSelection().clone();
      LOG.debug("Original SQL: " + sql);
      sql.clearSQLString();

      // merge distinct option
      if(dsel.isDistinct()) {
         sql.setDistinct(true);
      }

      this.dsel = dsel;

      // merge select subclause
      mergeColumnSelection(dsel, bsel, sql, sourceAttr, isDateRangeMergeable);

      // prepare where subclause
      prepareCondition(dsel, bsel, sql, vars);

      ConditionList conditions = dsel.getConditionList();
      XUtil.convertDateCondition(conditions, vars);

      // merge from subclause
      mergeTables(dsel, bsel, sql, model, query, vars, user);

      validateQuery();

      // merge where sub clause
      mergeCondition(sql, conditions, vars);

      if(groups.size() > 0) {
         String[] gcolumns = new String[groups.size()];
         XSelection sel = sql.getSelection();

         for(int i = 0; i < groups.size(); i++) {
            int idx = ((Integer) groups.get(i)).intValue();
            gcolumns[i] = sel.getColumn(idx);
         }

         sql.setGroupBy(gcolumns);
      }

      LOG.debug("Runtime SQL: " + sql);

      // @by billh, if generated result is an empty sql,
      // just return a null query for caller to handle
      if(sql.toString().length() == 0) {
         LOG.warn("Failed to generate query, the runtime SQL is empty");
         return null;
      }

      return jquery;
   }

   /**
    * Validate query.
    */
   protected void validateQuery() {
      // to quote a column in expression correctly, sql must be well defined.
      // So here we fix columns in an expression after sql is totally merged.
      for(int i = 0; i < validaters.size(); i++) {
         ExpressionValidater validater =
            (ExpressionValidater) validaters.get(i);
         validater.validate();
      }
   }

   /**
    * Merge condition.
    */
   protected void mergeCondition(UniformSQL sql, ConditionList conds,
                                 VariableTable vars) {
      // generate filter node
      if(!cmergeable) {
         return;
      }

      XFilterNode bindCondition = convertCondition(conds, sql, vars);
      XFilterNode root = sql.getWhere();

      if(root == null) {
         sql.setWhere(bindCondition);
      }
      else {
         XSet newroot = new XSet(XSet.AND);
         newroot.addChild(root);
         newroot.addChild(bindCondition);
         sql.setWhere(newroot);
      }

      // clear condition list not to do condition filter in post process
      conds.removeAllItems();
   }

   /**
    * Merge column selection info base sql.
    * <p>
    * 0. Remove all useless fields contained in base sql,
    * 1. Append all selected fields if not contained in base sql,
    * 2. Append all sql expressions user defined,
    * 3. Append all fields used in script expressions if not contains in base
    * sql,
    * 4. Merge order info if order info is mergeable.
    *
    * @param dsel the binding info
    * @param bsel the base selection
    * @param sql the base sql
    * @param sourceAttr element source attr.
    * @param isDateRangeMergeable check if formula field for date range column should be merge to sql.
    */
   protected void mergeColumnSelection(XDataSelection dsel, XSelection bsel, UniformSQL sql,
                                       SourceAttr sourceAttr,
                                       Function<FormulaField, Boolean> isDateRangeMergeable)
   {
      XSelection sel = sql.getSelection();
      ColumnSelection csel = dsel.getColumnSelection();
      ColumnSelection useless = dsel.getHiddenColumns();

      // order by is meaningless
      if(amergeable) {
         sql.removeAllOrderByFields();
      }

      boolean order = isOrderMergeable(csel);
      int index = 0;

      // remove useless attributes
      for(int i = 0; i < useless.getAttributeCount(); i++) {
         DataRef ref = useless.getAttribute(i);
         String col = bsel.findColumn(ref.getAttribute());

         // @by larryl, if an expression is used as 'order by', DB2 and
         // Informix requires the alias instead of the full expression. We
         // can't remove the column from select if its alias is used.
         if(col != null && sql.getOrderBy(ref.getAttribute()) == null) {
            boolean aliased = sel.removeAliasColumn(ref.getAttribute());

            if(!aliased) {
               sel.removeColumn(col);
            }
         }
      }

      // append model attributes and sql expressions
      for(int i = 0; i < csel.getAttributeCount(); i++) {
         Field oref = (Field) csel.getAttribute(i);

         // for fake field, don't merge to sql
         if(BindingTool.isFakeField(oref)) {
            continue;
         }

         DataRef ref = normalizeExpression(oref, null);
         int gindex = -1;

         oref.setProcessed(false);

         // is expression
         if(ref instanceof FormulaField) {
            FormulaField exp = (FormulaField) ref;
            DataRef ref2 = exp.getOriginalField();

            while(ref2 instanceof FormulaField) {
               ref2 = ((FormulaField) ref2).getOriginalField();
            }

            ref2 = ref2 == null ? oref : ref2;
            exp.setDBType(SQLHelper.getSQLHelper(sql).getSQLHelperType());

            // is sql expression
            if(exp.isSQL()) {
               String col = exp.getExpression();

               if(isDateRangeMergeable != null &&
                  !isDateRangeMergeable.apply((FormulaField) ref) && ref2 != null)
               {
                  col = "field['" + ref2.getName() + "']";
               }

               String alias = getExpressionAlias(exp);

               // expression column will be processed when merging tables,
               // and here we add it simply without converting the contained
               // attributes to real attributes
               gindex = addColumn(sel, col, ref);
               expressions.add(col);

               // maintain meta info if any
               if(ref2 instanceof AttributeRef) {
                  AttributeRef attribute = (AttributeRef) ref2;

                  if(isModelAttribute(attribute)) {
                     XMetaInfo minfo = getXMetaInfo(attribute);
                     sel.setXMetaInfo(gindex, minfo);
                  }
                  else {
                     //@by robert, if column has alias, get meta info with it's
                     //index in xselection
                     //fix bug1362451957272
                     String attr = ref2.getAttribute();
                     String col2 = bsel.findColumn(attr);
                     int idx = bsel.isAlias(attr) ? bsel.indexOfColumn(attr) : -1;
                     XMetaInfo minfo = idx < 0 ? bsel.getXMetaInfo(col2) : bsel.getXMetaInfo(idx);
                     XMetaInfo minfo0 = sel.getXMetaInfo(gindex);

                     // don't lose the default date format
                     if(minfo.isXFormatInfoEmpty()) {
                        minfo = minfo.clone();
                        minfo.setXFormatInfo(minfo0.getXFormatInfo());
                     }

                     sel.setXMetaInfo(gindex, minfo);
                  }
               }

               oref.setProcessed(true);

               if(alias != null && alias.length() > 0) {
                  sel.setAlias(gindex, alias);
               }
            }
         }
         // is attribute
         else {
            AttributeRef attr = (AttributeRef) ref;

            if(oref instanceof Field) {
               oref.setProcessed(true);
            }

            // is model attribute, append a real attribute which is already
            // converted by logic in appendAttribute method
            if(isModelAttribute(attr)) {
               gindex = appendAttribute(attr, bsel, sel, sql);
            }
         }

         boolean group = oref.isGroupField();

         if(group && amergeable) {
            gindex = gindex < 0 ? sel.indexOfColumn(oref.getAttribute()) : gindex;

            if(gindex < 0) {
               LOG.warn("Group field not found: " + oref);
            }
            else {
               groups.add(Integer.valueOf(gindex));
            }
         }

         // order is mergeable, merge it into query
         if(order) {
            int type = getSortOrder(ref);
            // @see comment in isOrderMergeable
            boolean asc = (type & StyleConstants.SORT_ASC) == StyleConstants.SORT_ASC;
            boolean desc = (type & StyleConstants.SORT_DESC) == StyleConstants.SORT_DESC;

            if(asc || desc) {
               String name = getOrderName(ref, bsel);

               if(name != null) {
                  String orderstr = asc ? "asc" : "desc";
                  sql.insertOrderBy(index++, name, orderstr);
               }
            }
         }
      }

      // order is mergeable, remove it from binding info
      if(order) {
         for(int i = 0; i < csel.getAttributeCount(); i++) {
            DataRef ref = csel.getAttribute(i);
            clearSortOrder(ref);
         }
      }
   }

   /**
    * Check if the condition list is mergeable in query generator.
    */
   public boolean isConditionMergeable() {
      return cmergeable;
   }

   /**
    * Set whether the condition list is mergeable in query generator.
    */
   public void setConditionMergeable(boolean cmergeable) {
      this.cmergeable = cmergeable;
   }

   /**
    * Check if aggregate is mergeable.
    */
   public boolean isAggregateMergeable() {
      return amergeable;
   }

   /**
    * Set whether aggregate is mergeable.
    */
   public void setAggregateMergeable(boolean amergeable) {
      this.amergeable = amergeable;
   }

   /**
    * Merge condition info into base sql.
    * <p>
    * If condition info is mergeable, it will be merged into base sql
    * and cleared not to do condition filter in post process.
    *
    * @param dsel the specified data selection
    * @param bsel the base selection
    * @param sql the specified uniform sql
    * @param vars the specified variable table
    */
   protected void prepareCondition(XDataSelection dsel, XSelection bsel,
                                   UniformSQL sql, VariableTable vars) {
      ConditionList conditions = dsel.getConditionList();
      SQLHelper helper = SQLHelper.getSQLHelper(sql.getDataSource());

      // if condition list is not mergeable, ignore mergence
      if(!cmergeable || !isConditionMergeable(conditions, helper)) {
         LOG.debug(
             "Condition is not mergeable, run as post-processing: "
             + conditions);
         cmergeable = false;
         return;
      }

      // convert attributes from "field['xxx']" to "xxx"
      for(int i = 0; i < conditions.getSize(); i += 2) {
         ConditionItem item = conditions.getConditionItem(i);
         DataRef ref = item.getAttribute();
         DataRef ref0 = normalizeExpression(ref, null);

         // do not convert date range filed
         ref = ref0 instanceof DateRangeField ? ref : ref0;

         if(ref instanceof ExpressionRef) {
            ExpressionRef exp = (ExpressionRef) ref;
            String val = exp.getExpression();
            exp.setExpression(
               convertAttrInExpression(exp.getExpression(), bsel));
            item.setAttribute(exp);
            ExpressionValidater validater =
               new ConditionExpressionValidater(exp, val, bsel);
            validaters.add(validater);
         }
         else {
            AttributeRef attr = (AttributeRef) item.getAttribute();
            convertAttributeRef(item, attr, sql, bsel, false);
         }

         // fixed bug #28399.
         // field and field to filter, should convert field value.
         if(item.getCondition().getValueCount() == 1) {
            if(item.getCondition().getValue(0) instanceof BaseField) {
               AttributeRef attr = (AttributeRef) item.getCondition().getValue(0);
               convertAttributeRef(item, attr, sql, bsel, true);
            }
         }
      }
   }

   // convert values from "Customer.Zip" to "SA.CUSTOMERS.Zip".
   private void convertAttributeRef(ConditionItem item, AttributeRef attr,
      UniformSQL sql, XSelection bsel, Boolean isConditionValue)
   {
      // is model attr?
      if(isModelAttribute(attr) && getRealAttribute(attr) != null) {
         attr = getRealAttribute(attr);
         String table = attr.getEntity();

         // if alias is defined for the table, use the alias otherwise
         // oracle bombs
         for(int j = 0; j < sql.getTableCount(); j++) {
            SelectTable tobj = sql.getSelectTable(j);

            if(table.equals(tobj.getName())) {
               String alias = tobj.getAlias();

               if(alias != null && alias.length() > 0) {
                  table = alias;
               }

               break;
            }
         }

         attr = new AttributeRef(table, attr.getAttribute());
         attr.setDataType(item.getAttribute().getDataType());

         if(!isConditionValue) {
            item.setAttribute(attr);
         }
         else {
            XCondition xCondition = item.getXCondition();

            if(xCondition != null && xCondition instanceof Condition) {
               ((Condition) xCondition).setValue(0, attr);
            }
            else {
               LOG.info("Set condition value failed.");
            }
         }
      }
      // is base query column?
      else {
         String column = bsel.findColumn(attr.getAttribute());
         String table = null;

         // @by larryl, determine whether it's an expression or column
         // by checking table instead of relying on the dot in the name
         if(column != null) {
            if(bsel instanceof JDBCSelection) {
               table = ((JDBCSelection) bsel).getTable(column);

               if(table == null) {
                  table = sql.getTable(column);
               }

               if(table != null) {
                  int index = column.lastIndexOf(".");

                  if(index > 0) {
                     column = column.substring(index + 1);
                  }
                  else {
                     table = null;
                  }
               }
            }
            else {
               int index = column.lastIndexOf(".");

               if(index >= 0) {
                  table = column.substring(0, index);
                  column = column.substring(index + 1);
               }
            }
         }

         if(table != null) {
            AttributeRef attr0 = new AttributeRef(table, column);
            attr0.setDataType(attr.getDataType());

            if(!isConditionValue) {
               item.setAttribute(attr0);
            }
            else {
               XCondition xCondition = item.getXCondition();

               if(xCondition != null && xCondition instanceof Condition) {
                  ((Condition) xCondition).setValue(0, attr0);
               }
               else {
                  LOG.info("Set condition value failed.");
               }
            }
         }
         // @by larryl, if the column is not a table column but is in
         // the selection list, it has to be an expression column. We
         // force the condition to use the expression since access does
         // not allow alias to be used in conditions
         else if(!isConditionValue && Tool.equals(bsel.getAliasColumn(attr.getAttribute()), column)) {
            ExpressionRef exp =
               new ExpressionRef("", attr.getAttribute());
            exp.setExpression(column);
            item.setAttribute(exp);
         }
      }
   }

   /**
    * Add new tables whose fields is referenced by column selection in base sql.
    * Joins between tables will also be added automatically.
    *
    * @param dsel the specified data selection
    * @param bsel the base selection
    * @param sql the specified uniform sql
    * @param model the data model contains the query which contains the sql
    */
   protected void mergeTables(XDataSelection dsel, XSelection bsel,
                              UniformSQL sql, XDataModel model, XQuery query,
                              VariableTable vars, Principal user) {
      HashSet tnames = new HashSet(); // table names from sql
      HashMap taliases = new HashMap();
      HashSet ntnames = new HashSet(); // new tables to merge in

      // collect existing tables in base sql
      for(int i = 0; i < sql.getTableCount(); i++) {
         String alias = sql.getTableAlias(i);
         String name = sql.getTableName(alias).toString();

         if(alias != null) {
            taliases.put(name, alias);
         }

         tnames.add(name);
      }

      // find new tables in column selection
      XSelection selection = sql.getSelection();

      for(int i = selection.getColumnCount() - 1; i >= 0; i--) {
         String column = selection.getColumn(i);

         // is attribute, already converted to real attribute
         if(isAttribute(column)) {
            String table = getTableName(column);

            if(table != null && !tnames.contains(table) &&
               !taliases.containsValue(table) && !ntnames.contains(table))
            {
               ntnames.add(table);
            }
         }
         // is expression, contained attributes not yet converted to
         // real attributes
         else {
            Enumeration attrs = XUtil.findAttributes(column);
            boolean valid = true;

            while(attrs.hasMoreElements()) {
               AttributeRef attr = (AttributeRef) attrs.nextElement();

               // process conversion to get a real attribute
               attr = getRealAttribute(attr);

               if(attr == null) {
                  valid = false;
               }

               String table = attr == null ? null : attr.getEntity();

               if(table != null && !tnames.contains(table) &&
                  !taliases.containsValue(table))
               {
                  ntnames.add(table);
               }
            }

            // is a field-valid expression, convert it
            if(valid) {
               selection.setColumn(i, convertAttrInExpression(column, bsel));
               ExpressionValidater validater =
                  new SelectionExpressionValidater(i, column, bsel);
               validaters.add(validater);
            }
            // isn't a field-valid expression, discard it
            else {
               selection.removeColumn(column);
            }
         }
      }

      if(model == null) {
         return;
      }

      Set tableset = null;
      // find the tables used in condition
      ConditionList conditions = dsel.getConditionList();
      SQLHelper helper = SQLHelper.getSQLHelper(sql.getDataSource());

      if(isConditionMergeable(conditions, helper)) {
         XFilterNode bindCondition = convertCondition(conditions, sql, vars);
         tableset = JDBCUtil.getTables(bindCondition);
      }
      else {
         tableset = new HashSet();
      }

      Iterator iter = tableset.iterator();

      while(iter.hasNext()) {
         String tname = (String) iter.next();

         if(!tnames.contains(tname) && !taliases.containsValue(tname)) {
            ntnames.add(tname);
         }
      }

      // find the runtime partition associated with the query
      /*
      XPartition partition = model.getPartition(query.getPartition(), user);

      if(partition != null) {
         partition = partition.applyAutoAliases();
      }

      // backward compatibility process
      if(partition == null && ntnames.size() > 0) {
         partition = findContainingPartition(model, tnames, ntnames);
      }

      XUtil.applyPartition(sql, partition, tnames, (HashSet) tnames.clone(),
                           ntnames);
      */
   }

   /**
    * Check if an expression is an attribute.
    *
    * @param exp the specified exp
    * @return true if is, false otherwise
    */
   protected boolean isAttribute(String exp) {
      return !expressions.contains(exp);
   }

   /**
    * Get real column representation of a model attribute.
    *
    * @param entity the specified attribute's entity
    * @param attr the specified attribute's attr
    * @return model column representation
    */
   protected String getRealColumn(String entity, String attr) {
      String name = entity == null ? attr : entity + "." + attr;
      return name;
   }

   /**
    * Get real attribute ref representation of a model attribute.
    *
    * @param ref the specified attribute
    * @return real attribute representation
    */
   protected AttributeRef getRealAttribute(AttributeRef ref) {
      return ref;
   }

   /**
    * Convert special expression attributes into ExpressionRef objects that
    * can be handled by the query generator.
    *
    * @param ref the attribute to be normalized.
    * @param exp the expression where the ref is found.
    *
    * @return the normalized data ref.
    */
   protected DataRef normalizeExpression(DataRef ref, String exp) {
      if(dsel == null || !(ref instanceof AttributeRef)) {
         return ref;
      }

      ColumnSelection csel = dsel.getColumnSelection();
      ColumnSelection useless = dsel.getHiddenColumns();
      int index = csel.indexOfAttribute(ref);
      DataRef ref2 = index >= 0 ? csel.getAttribute(index) : null;

      if(ref2 == null) {
         index = useless.indexOfAttribute(ref);
         ref2 = index >= 0 ? useless.getAttribute(index) : null;
      }

      // sum[col] as col
      if(ref2 instanceof ExpressionRef &&
         ((ExpressionRef) ref2).getExpression().equals(exp))
      {
         return ref;
      }

      return ref2 != null ? ref2 : ref;
   }

   /**
    * Get table name from a column.
    *
    * @param column the specified column
    * @return the table name if exists, null otherwise
    */
   protected String getTableName(String column) {
      int idx = column.lastIndexOf(".");
      return idx < 0 ? null : column.substring(0, idx);
   }

   /**
    * Get model attribute's alias.
    *
    * @param ref the specified attribute
    * @return alias if exists, null otherwise
    */
   protected String getAttributeAlias(AttributeRef ref) {
      // for physical model fields, return column to generate table.column
      // format alias, which is very vital for our new column indexing logic
      if(ref.getEntity() != null) {
         return ref.getAttribute();
      }
      else {
         return null;
      }
   }

   /**
    * Get model expression attribute's alias.
    *
    * @param ref the specified attribute
    * @return alias if exists, null otherwise
    */
   protected String getExpressionAlias(ExpressionRef ref) {
      return ref.getName();
   }

   /**
    * Get order name used in sql 'order by' of a data ref.
    *
    * @param ref the specified data ref
    * @param bsel the base selection
    * @return order name of the data ref
    */
   protected String getOrderName(DataRef ref, XSelection bsel) {
      // is attribute
      if(ref instanceof AttributeRef) {
         AttributeRef attr = (AttributeRef) ref;

         // is model attribute
         if(isModelAttribute(attr)) {
            return getRealColumn(attr.getEntity(), attr.getAttribute());
         }
         // is query attribute
         else{
            return bsel.findColumn(attr.getAttribute());
         }
      }
      // is expression
      else {
         return getExpressionAlias((ExpressionRef) ref);
      }
   }

   /**
    * Append an attribute to an XSelection.
    *
    * @param attribute the specified attribute
    * @param bsel the specified base XSelection
    * @param sel the specified destination XSelection
    */
   protected int appendAttribute(AttributeRef attribute, XSelection bsel,
                                  XSelection sel, UniformSQL sql) {
      // process conversion to get a real attribute
      AttributeRef rattribute = getRealAttribute(attribute);

      // unexpected result found for column selection is not in
      // sync with logic model, which might happen once in a while
      if(rattribute == null) {
         return -1;
      }

      String attr = rattribute.getAttribute();
      String entity = rattribute.getEntity();
      String column = null;

      if(isModelAttribute(rattribute)) {
         String tname = entity;

         // if alias is defined for the table, use the alias otherwise
         // oracle bombs
         for(int i = 0; i < sql.getTableCount(); i++) {
            SelectTable tobj = sql.getSelectTable(i);

            if(entity.equals(tobj.getName())) {
               String alias = tobj.getAlias();

               if(alias != null && alias.length() > 0) {
                  tname = alias;
               }

               break;
            }
         }

         column = tname + "." + attr;
      }
      else {
         column = bsel.findColumn(attr);
      }

      // unexpected result found for column selection is not in
      // sync with base query, which might happen once in a while
      if(column == null) {
         return -1;
      }

      String oalias = null;
      int oindex = sel.indexOf(column);

      if(oindex != -1) {
         oalias = sel.getAlias(oindex);
      }

      String nalias = null;

      // is base query column?
      if(!isModelAttribute(rattribute) && bsel.indexOf(column) != -1) {
         nalias = bsel.getAlias(bsel.indexOf(column));
      }
      // is model column?
      else {
         nalias = getAttributeAlias(attribute);
      }

      // set the alias of the column if not duplicated
      if(nalias != null && !nalias.equals(oalias)) {
         // is a normal attribute ref? only when alias equals to the attribute
         // of the attribute ref should we set alias, otherwise the column
         // header of the returned result is the alias, then the post filters
         // could not locate the proper column
         if(nalias.equals(attribute.getAttribute())) {
            nalias = attribute.getEntity() + "." + nalias;
         }
      }

      int index = -1;

      if(oindex == -1 || !Tool.equals(nalias, oalias)) {
         index = addColumn(sel, column, attribute);

         if(nalias != null) {
            sel.setAlias(index, nalias);
         }
      }

      if(attribute.getEntity() != null && bsel instanceof JDBCSelection) {
         if(nalias != null) {
            ((JDBCSelection) sel).setTable(nalias, getTable(attribute));
         }
         else {
            ((JDBCSelection) sel).setTable(column, getTable(attribute));
         }
      }

      // @by larryl, set the table for the path,
      // column quoting will not be correct if the table is not set
      if(bsel instanceof JDBCSelection) {
         String tbl = ((JDBCSelection) bsel).getTable(column);

         if(tbl == null && attr.endsWith("." + column)) {
            tbl = attr.substring(0, attr.length() - column.length() - 1);
         }

         if(tbl != null && tbl.length() > 0) {
            ((JDBCSelection) sel).setTable(column, tbl);
         }
      }

      // maintain meta info if any
      XMetaInfo minfo = getXMetaInfo(attribute);
      sel.setXMetaInfo(index, minfo);
      return index;
   }

   /**
    * Add a column generated for the specified data ref.
    */
   protected int addColumn(XSelection sel, String col, DataRef ref) {
      int idx = sel.addColumn(col);

      if(ref instanceof DateRangeField) {
         XMetaInfo minfo = sel.getXMetaInfo(idx);
         int level = ((DateRangeField) ref).getDateOption();
         String type = getOriginalType((DateRangeField) ref);

         if(XUtil.getDefaultDateFormat(level, type) != null) {
            String fmt = XUtil.getDefaultDateFormat(level, type).toPattern();
            minfo.setXFormatInfo(new XFormatInfo(TableFormat.DATE_FORMAT, fmt));
            sel.setXMetaInfo(idx, minfo);
         }
      }

      return idx;
   }

   /**
    * Get the meta info of one attribute.
    * @param attribute the specified attribute, which should be a model
    * attribute.
    * @return the meta info of the attribute, <tt>null</tt> otherwise.
    */
   protected XMetaInfo getXMetaInfo(AttributeRef attribute) {
      if(jquery != null) {
         return jquery.getSelection().getXMetaInfo(attribute.getName());
      }

      return null;
   }

   /**
    * Get the physical table.
    * @param attr the specified attribute.
    */
   protected String getTable(AttributeRef attr) {
      return attr.getEntity();
   }

   /**
    * Check whether a ref is a logical model defined expression.
    */
   protected boolean isModelExpression(DataRef ref) {
      return false;
   }

   /**
    * Replace "field['xxx']" with "xxx" in expression.
    *
    * @param exp the specified expression
    * @param bsel the base selection
    * @return the new expression
    */
   protected String convertAttrInExpression(String exp, XSelection bsel) {
      StringBuilder temp = new StringBuilder();
      int start, end;
      SQLHelper helper = SQLHelper.getSQLHelper(sql);

      while((start = exp.indexOf("field['")) != -1) {
         end = exp.indexOf("']", start + 7);

         if(end == -1) {
            break;
         }

         boolean hasParens = start > 0 && exp.charAt(start - 1) == '(' &&
            end < exp.length() - 3 && exp.charAt(end + 2) == ')';
         temp.append(exp.substring(0, start));
         String name = exp.substring(start + 7, end);
         int index = name.lastIndexOf(".");
         String cname = bsel.findColumn(name);

         // is query attribute, should find real column name to replace
         // the original name, otherwise the generated sql might be invalid
         if(index == -1 || cname != null) {
            name = cname != null ? cname: name;

            if(sql.isTableColumn(name)) {
               temp.append(helper.quotePath(name, true));
            }
            // an expression? bracket it
            else {
               DataRef ref = new AttributeRef(null, name);
               ref = normalizeExpression(ref, exp);

               if(ref instanceof ExpressionRef) {
                  String exp2 = ((ExpressionRef) ref).getExpression();
                  name = convertAttrInExpression(exp2, bsel);

                  if(!XUtil.isQualifiedName(name) && !hasParens) {
                     name = '(' + name + ')';
                  }
               }
               else if(!hasParens) {
                  name = '(' + name + ')';
               }

               temp.append(name);
            }
         }
         else {
            String entity = name.substring(0, index);
            String attr = name.substring(index + 1);
            DataRef ref = new AttributeRef(entity, attr);
            boolean modelexp = isModelExpression(ref);

            ref = normalizeExpression(ref, exp);

            // is a model formula?
            // if is aggregate mergeable, it means in most case
            // the expression ref is from an aggregate column,
            // and this expression ref should be another aggregate column
            // in this case, should not replace it.
            // except the expression ref is really an model expression.
            if(ref instanceof ExpressionRef &&
               (modelexp || !isAggregateMergeable())) {

               String exp2 = ((ExpressionRef) ref).getExpression();
               name = convertAttrInExpression(exp2, bsel);

               if(!XUtil.isQualifiedName(name) && !hasParens) {
                  name = '(' + name + ')';
               }
            }
            else {
               name = getRealColumn(entity, attr);
               name = helper.quotePath(name, true);
            }

            temp.append(name);
         }

         exp = exp.substring(end + 2);
      }

      temp.append(exp);
      return temp.toString();
   }

   /**
    * Condition expression validater.
    */
   protected class ConditionExpressionValidater extends ExpressionValidater {
      public ConditionExpressionValidater(ExpressionRef ref, String exp,
                                          XSelection selection) {
         super(exp, selection);
         this.ref = ref;
      }

      @Override
      protected void apply(String exp) {
         ref.setExpression(exp);
      }

      @Override
      protected void adjustIndex(int i) {
      }

      ExpressionRef ref;
   }

   /**
    * Seletion expression validater.
    */
   protected class SelectionExpressionValidater extends ExpressionValidater {
      public SelectionExpressionValidater(int index, String exp,
                                          XSelection selection) {
         super(exp, selection);
         this.index = index;
      }

      @Override
      protected void apply(String exp) {
         XSelection selection = sql.getSelection();
         String column = selection.getColumn(index);
         selection.setColumn(index, exp);
         Object[] groups = sql.getGroupBy();

         for(int i = 0; groups != null && i < groups.length; i++) {
            if(column.equals(groups[i])) {
               groups[i] = exp;
            }
         }

         sql.setGroupBy(groups);
      }

      @Override
      protected void adjustIndex(int i) {
         index += i;
      }

     private  int index;
   }

   /**
    * Expression validater.
    */
   protected abstract class ExpressionValidater {
      public ExpressionValidater(String exp, XSelection selection) {
         super();
         this.exp = exp;
         this.selection = selection;
      }

      protected abstract void apply(String exp);
      protected abstract void adjustIndex(int i);

      public void validate() {
         String nexp = convertAttrInExpression(exp, selection);
         apply(nexp);
      }

      protected String exp;
      protected XSelection selection;
   }

   protected JDBCQuery jquery = null;
   protected UniformSQL sql = null;
   protected List expressions = new ArrayList();
   protected List validaters = new ArrayList();
   protected List groups = new ArrayList();
   protected boolean cmergeable = true;
   protected boolean amergeable = false;
   private XDataSelection dsel = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(QueryGenerator.class);
}
