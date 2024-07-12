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
package inetsoft.report.internal.binding;

import inetsoft.uql.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.util.XUtil;
import inetsoft.util.OrderedSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;

/**
 * LMQueryGenerator is used to generate new query after binding element to a
 * logic model.
 *
 * @version 6.0
 * @author InetSoft Technology Corp
 */
public class LMQueryGenerator extends QueryGenerator {
   /**
    * Constructor.
    *
    * @param lmodel the specified logic model
    */
   public LMQueryGenerator(XLogicalModel lmodel, Principal user) {
      super();
      this.lmodel = lmodel;
      this.user = user;
   }

   /**
    * Get the meta info of one attribute.
    * @param attribute the specified attribute, which should be a model
    * attribute.
    * @return the meta info of the attribute, <tt>null</tt> otherwise.
    */
   @Override
   protected XMetaInfo getXMetaInfo(AttributeRef attribute) {
      if(!isModelAttribute(attribute)) {
         return null;
      }

      XAttribute xattr = dereferenceAttribute(attribute);
      return xattr == null ? null : xattr.getXMetaInfo();
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
   @Override
   protected void prepareCondition(XDataSelection dsel, XSelection bsel,
                                   UniformSQL sql, VariableTable vars) {
      if(cmergeable) {
         ConditionList conds = dsel.getConditionList();

         // check whether aggregates reside in condition
         for(int i = 0; conds != null && i < conds.getSize(); i += 2) {
            ConditionItem citem = conds.getConditionItem(i);
            DataRef ref = citem.getAttribute();
            ref = normalizeExpression(ref, null);

            if(isAggregateExpression(ref)) {
               aggrs.add(((ExpressionRef) ref).getExpression());
            }
         }

         // mark whether post it
         if(aggrs.size() > 0) {
            ColumnSelection cols = dsel.getColumnSelection();

            for(int i = 0; conds != null && i < conds.getSize(); i += 2) {
               ConditionItem citem = conds.getConditionItem(i);
               DataRef ref = citem.getAttribute();

               if(isAggregateExpression(ref)) {
                  post = true;
                  break;
               }
            }
         }
      }

      super.prepareCondition(dsel, bsel, sql, vars);
   }

   /**
    * Merge condition.
    */
   @Override
   protected void mergeCondition(UniformSQL sql, ConditionList conds,
                                 VariableTable vars) {
      // generate filter node
      if(cmergeable) {
         XFilterNode bindCondition = convertCondition(conds, sql, vars);
         XFilterNode root = post ? sql.getHaving() : sql.getWhere();

         if(root == null) {
            if(post) {
               sql.setHaving(bindCondition);
            }
            else  {
               sql.setWhere(bindCondition);
            }
         }
         else {
            XSet newroot = new XSet(XSet.AND);
            newroot.addChild(root);
            newroot.addChild(bindCondition);

            if(post) {
               sql.setHaving(newroot);
            }
            else  {
               sql.setWhere(newroot);
            }
         }

         if(post) {
            XSelection sel = sql.getSelection();
            Object[] groups = sql.getGroupBy();
            List list = new ArrayList();
            boolean changed = false;

            for(int i = 0; groups != null && i < groups.length; i++) {
               list.add(groups[i]);
            }

            for(int i = 0; i < conds.getSize(); i += 2) {
               ConditionItem citem = conds.getConditionItem(i);
               DataRef ref = citem.getAttribute();
               String col;

               if(ref instanceof AttributeRef) {
                  String entity = ((AttributeRef) ref).getEntity();
                  String attr = ((AttributeRef) ref).getAttribute();
                  col = entity == null || entity.length() == 0 ?
                     attr : entity + "." + attr;
               }
               else {
                  col = ((ExpressionRef) ref).getExpression();
               }

               if(!sel.contains(col) && !list.contains(col) &&
                  !isAggregateExpression(ref))
               {
                  list.add(col);
                  changed = true;
               }
            }

            if(changed) {
               groups = new Object[list.size()];
               list.toArray(groups);
               sql.setGroupBy(groups);
            }
         }

         // clear condition list not to do condition filter in post process
         conds.removeAllItems();
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
   @Override
   protected void mergeTables(XDataSelection dsel, XSelection bsel,
                              UniformSQL sql, XDataModel model, XQuery query,
                              VariableTable vars, Principal user) {
      OrderedSet<String> tableset = new OrderedSet<>();
      OrderedSet optables = (OrderedSet) ptables.clone();

      // find new tables in column selection
      XSelection selection = sql.getSelection();

      for(int i = selection.getColumnCount() - 1; i >= 0; i--) {
         String column = selection.getColumn(i);
         String alias = selection.getAlias(i);

         // is attribute, already converted to real attribute
         if(isAttribute(column)) {
            String table = getTableName(column);

            if(table != null) {
               tableset.add(table);
            }
         }
         // is expression, contained attributes not yet converted to
         // real attributes
         else {
            boolean valid = true;

            // is a binding formula?
            if(!expressionAttributes.containsValue(column)) {
               Enumeration attrs = XUtil.findAttributes(column);

               while(attrs.hasMoreElements()) {
                  AttributeRef attr = (AttributeRef) attrs.nextElement();
                  DataRef ref = normalizeExpression(attr, null);

                  // support sum[col] as col
                  if(ref.getName().equals(alias)) {
                     ref = attr;
                  }

                  // support formula on formula
                  if(ref instanceof ExpressionRef) {
                     ExpressionRef ref2 = (ExpressionRef) ref;
                     String exp = ref2.getExpression();
                     Enumeration attrs2 = XUtil.findAttributes(exp);

                     while(attrs2.hasMoreElements()) {
                        AttributeRef attr2  = (AttributeRef) attrs2.nextElement();
                        AttributeRef attr3 = getRealAttribute(attr2);
                        attr2 = attr3 == null ? attr2 : attr3;
                        String table = attr2.getEntity();

                        if(table != null) {
                           tableset.add(table);
                        }
                     }
                  }
                  else {
                     attr = getRealAttribute(attr);

                     if(attr == null) {
                        valid = false;
                     }

                     String table = attr == null ? null : attr.getEntity();

                     if(table != null) {
                        tableset.add(table);
                     }
                  }
               }
            }

            // is a field-valid expression, convert it
            if(valid) {
               selection.setColumn(i, convertAttrInExpression(column, bsel));
               ExpressionValidater validater =
                  new SelectionExpressionValidater(i, column, bsel);
               validaters.add(validater);

               // add tables used in expression columns
               tableset.addAll(ptables);
               ptables.clear();
            }
            // isn't a field-valid expression, discard it
            else {
               selection.removeColumn(column);

               for(int j = 0; j < validaters.size(); j++) {
                  ExpressionValidater validater =
                     (ExpressionValidater) validaters.get(j);

                  validater.adjustIndex(-1);
               }
            }
         }
      }

      // add tables come from condition
      tableset.addAll(optables);

      // tables use the same order as column selection so it's more predictable.
      // reverse the order since the tables are added in reverse order above
      tableset.reverse();

      // add all tables used in the where clause of the query
      Set whereSet = JDBCUtil.getTables(sql.getWhere());
      tableset.addAll(whereSet);

      // find tables used in conditions
      // @by larryl, use ordered set so the order is deterministic
      Set tablecond = new OrderedSet();
      ConditionList conditions = dsel.getConditionList();
      SQLHelper helper = SQLHelper.getSQLHelper(sql.getDataSource());

      if(isConditionMergeable() && isConditionMergeable(conditions, helper)) {
         XFilterNode bindCondition = convertCondition(conditions, sql, vars);
         tablecond = JDBCUtil.getTables(bindCondition);
      }

      tableset.addAll(tablecond);

      // find the runtime partition associated with the query
      XPartition partition = model.getPartition(query.getPartition(), user);

      if(partition != null) {
         partition = partition.applyAutoAliases();
      }

      // move outer join to the end so 'include all rows' has the highest
      // priority and will not be inner joined with other tables
      OrderedSet outers = new OrderedSet();

      for(String tbl : tableset) {
         if(tbl != null) {
            String entity = (String) tableEntities.get(tbl);

            if(entity != null && dsel.isAllRows(entity)) {
               outers.add(tbl);
            }
         }
      }

      tableset.removeAll(outers);
      tableset.addAll(outers);
      tableset.remove(null);

      // @by larryl, use ordered set so the order is deterministic
      Set originating = new OrderedSet();
      Set others = new OrderedSet();

      // add tables to the sql (from clause)
      for(String tbl : tableset) {
         XUtil.addTable(sql, partition, tbl);

         if(originating.size() == 0) {
            originating.add(tbl);
         }
         else {
            others.add(tbl);
         }
      }

      XJoin[] joins = sql.getJoins();
      XRelationship[] rels = partition.findRelationships(originating, others);

      if(rels != null) {
         for(int k = 0; k < rels.length; k++) {
            String leftTable = rels[k].getDependentTable();
            String rightTable = rels[k].getIndependentTable();

            // if join already exists, don't add to the query again
            for(int l = 0; joins != null && l < joins.length; l++) {
               if((leftTable.equals(joins[l].getTable1(sql)) &&
                   rightTable.equals(joins[l].getTable2(sql))) ||
                  (leftTable.equals(joins[l].getTable2(sql)) &&
                   rightTable.equals(joins[l].getTable1(sql))))
               {
                  continue;
               }
            }

            if(!tableset.contains(leftTable)) {
               XUtil.addTable(sql, partition, leftTable);
               tableset.add(leftTable);
            }

            if(!tableset.contains(rightTable)) {
               XUtil.addTable(sql, partition, rightTable);
               tableset.add(rightTable);
            }

            // implement outer join
            String leftEntity = (String) tableEntities.get(leftTable);
            String rightEntity = (String) tableEntities.get(rightTable);
            String op = rels[k].getJoinType();

            // only operation equal is changeable, operations like
            // greater than or less than should not be modified
            if(op.equals("=")) {
               if(dsel.isAllRows(leftEntity) &&
                  dsel.isAllRows(rightEntity))
               {
                  op = "*=*";
               }
               else if(dsel.isAllRows(leftEntity)) {
                  op = "*=";
               }
               else if(dsel.isAllRows(rightEntity)) {
                  op = "=*";
               }
            }

            XJoin join = new XJoin(
               new XExpression(rels[k].getDependentTable() +
                               "." + rels[k].getDependentColumn(),
                               XExpression.FIELD),
               new XExpression(rels[k].getIndependentTable() + "." +
                               rels[k].getIndependentColumn(),
                               XExpression.FIELD), op);
            join.setOrder(rels[k].getOrder());
            sql.addJoin(join, rels[k].getMerging());
         }
      }

      if(others.size() > 0) {
         LOG.error("No join path found for tables: " + others);
      }
   }

   /**
    * Get real column representation of a model attribute.
    *
    * @param entity the specified attribute's entity
    * @param attr the specified attribute's attr
    * @return model column representation
    */
   @Override
   protected String getRealColumn(String entity, String attr) {
      AttributeRef ref = getRealAttribute(entity, attr);

      // not found, logic model and binding info are not sync
      if(ref == null) {
         return null;
      }

      return super.getRealColumn(ref.getEntity(), ref.getAttribute());
   }

   /**
    * Get real attribute ref representation of a model attribute.
    *
    * @param ref the specified attribute
    * @return model attribute representation
    */
   @Override
   protected AttributeRef getRealAttribute(AttributeRef ref) {
      return getRealAttribute(ref.getEntity(), ref.getAttribute());
   }

   /**
    * Get the physical table.
    * @param ref the specified attribute.
    */
   @Override
   protected String getTable(AttributeRef ref) {
      String entity = ref.getEntity();
      String attr = ref.getAttribute();
      XEntity xentity = lmodel.getEntity(entity);

      if(xentity == null) {
         return null;
      }
      else {
         XAttribute xattr = xentity.getAttribute(attr);

         if(xattr == null) {
            return null;
         }
         else {
            return xattr.getTable();
         }
      }
   }

   /**
    * Get model attribute's alias.
    *
    * @param ref the specified attribute
    * @return alias if exists, null otherwise
    */
   @Override
   protected String getAttributeAlias(AttributeRef ref) {
      String entity = ref.getEntity();
      String attr = ref.getAttribute();

      if(entity == null) {
         return null;
      }

      XEntity xentity = lmodel.getEntity(entity);

      if(xentity == null) {
         return null;
      }
      else {
         XAttribute xattr = xentity.getAttribute(attr);

         if(xattr == null) {
            return null;
         }
         else {
            return xattr.getName();
         }
      }
   }

   /**
    * Get model expression attribute's alias.
    *
    * @param ref the specified attribute
    * @return alias if exists, null otherwise
    */
   @Override
   protected String getExpressionAlias(ExpressionRef ref) {
      String entity = ref.getEntity();
      String attr = ref.getAttribute();

      if(entity == null) {
         return super.getExpressionAlias(ref);
      }

      XEntity xentity = lmodel.getEntity(entity);

      if(xentity == null) {
         return super.getExpressionAlias(ref);
      }
      else {
         XAttribute xattr = xentity.getAttribute(attr);

         if(xattr == null) {
            return super.getExpressionAlias(ref);
         }
         else {
            return entity + "." + xattr.getName();
         }
      }
   }

   /**
    * Get real attribute ref representation of a model attribute.
    *
    * @param entity the specified attribute's entity
    * @param attr the specified attribute's attr
    * @return model attribute representation
    */
   private AttributeRef getRealAttribute(String entity, String attr) {
      XAttribute xattr = dereferenceAttribute(entity, attr);

      if(xattr != null) {
         // remember the table -> entity mapping
         if(xattr.getTable() != null) {
            tableEntities.put(xattr.getTable(), entity);
         }

         return new AttributeRef(xattr.getTable(), xattr.getColumn());
      }

      return null;
   }

   /**
    * Add a column generated for the specified data ref.
    */
   @Override
   protected int addColumn(XSelection sel, String col, DataRef ref) {
      if(isAggregateExpression(ref)) {
         aggrs.add(col);
      }

      return super.addColumn(sel, col, ref);
   }

   /**
    * Check whether an attribute ref is an aggregate expression.
    */
   @Override
   public boolean isAggregateExpression(DataRef ref) {
      if(ref == null ||
         (ref.getEntity() == null && !(ref instanceof ExpressionRef)))
      {
         return false;
      }

      if(ref.getEntity() != null) {
         XAttribute xattr = dereferenceAttribute(ref.getEntity(),
                                                 ref.getAttribute());
         if(xattr instanceof ExpressionAttribute &&
            ((ExpressionAttribute) xattr).isAggregateExpression())
         {
            return true;
         }
      }

      // expression ref? check whether it's an aggregate by its expression
      if(ref instanceof ExpressionRef) {
         ExpressionRef exp = (ExpressionRef) ref;
         Enumeration enumeration = exp.getAttributes();

         while(enumeration.hasMoreElements()) {
            DataRef ref2 = (DataRef) enumeration.nextElement();

            if(isAggregateExpression(ref2)) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check whether a ref is a logical model defined expression.
    */
   @Override
   protected boolean isModelExpression(DataRef ref) {
      XAttribute xattr = dereferenceAttribute((AttributeRef) ref);
      return xattr instanceof ExpressionAttribute;
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
   @Override
   protected DataRef normalizeExpression(DataRef ref, String exp) {
      boolean modelexpr = ref instanceof AttributeRef && isModelExpression(ref);

      if(!modelexpr) {
         ref = super.normalizeExpression(ref, exp);
      }

      if(ref instanceof ExpressionRef) {
         return ref;
      }

      XAttribute xattr = dereferenceAttribute((AttributeRef) ref);

      if(!(xattr instanceof ExpressionAttribute)) {
         return ref;
      }

      ExpressionAttribute expr = (ExpressionAttribute) xattr;
      String exp2 = expr.getExpression();

      if(!expr.isParseable()) {
         sql.addExpression(exp2);
      }

      FormulaField field = new FormulaField(expr.getName(), exp2);
      field.setEntity(ref.getEntity());
      field.setType(FormulaField.SQL);
      field.setDataType(expr.getDataType());

      if(ref instanceof Field) {
         field.setOrder(((Field) ref).getOrder());
      }

      expressionAttributes.put(field, exp2);

      return field;
   }

   /**
    * Replace "field['xxx']" with "xxx" in expression.
    *
    * @param exp the specified expression
    * @param bsel the base selection
    * @return the new expression
    */
   @Override
   protected String convertAttrInExpression(String exp, XSelection bsel) {
      // this method only handles ExpressionAttribute columns; for all others,
      // the super method should be used
      if(!expressionAttributes.containsValue(exp)) {
         return super.convertAttrInExpression(exp, bsel);
      }

      String oexp = exp;
      // the remainder of this method is identical to that in the super class,
      // except where noted in comments below.
      SQLHelper helper = SQLHelper.getSQLHelper(sql);
      StringBuilder temp = new StringBuilder();
      int start, end;

      while((start = exp.indexOf("field['")) != -1) {
         end = exp.indexOf("']", start + 7);

         if(end == -1) {
            break;
         }

         temp.append(exp.substring(0, start));
         String name = exp.substring(start + 7, end);
         int index = name.lastIndexOf(".");

         // is query attribute, should find real column name to replace
         // the original name, otherwise the generated sql might be invalid
         if(index == -1) {
            name = bsel.findColumn(name);

            // an expression? bracket it
            if(!XUtil.isQualifiedName(name)) {
               temp.append('(');
               temp.append(name);
               temp.append(')');
            }
            else {
               temp.append(XUtil.quoteName(name, helper));
            }
         }
         else {
            String entity = name.substring(0, index);
            String attr = name.substring(index + 1);

            // the only difference between this method and the one in the super
            // class is - for expression attributes, we already have the
            // table/column, so we don't want to use these to look up an
            // XAttribute
            name = super.getRealColumn(entity, attr);
            name = helper.quotePath(name, true);
            temp.append(name);
            ptables.add(entity);
         }

         exp = exp.substring(end + 2);
      }

      temp.append(exp);
      String res = temp.toString();

      if(sql.containsExpression(oexp, true)) {
         sql.removeExpression(oexp);
         sql.addExpression(res);
      }

      return res;
   }

   /**
    * Gets the actual attribute object referenced by the attribute ref.
    *
    * @param ref the attribute ref to dereference.
    *
    * @return the actual attribute object or <code>null<code> if no such
    *         attribute exists.
    */
   private XAttribute dereferenceAttribute(AttributeRef ref) {
      return dereferenceAttribute(ref.getEntity(), ref.getAttribute());
   }

   /**
    * Gets the actual attribute object referenced by the attribute ref.
    *
    * @param entity the name of the entity.
    * @param attr the name of the attribute.
    *
    * @return the actual attribute object or <code>null<code> if no such
    *         attribute exists.
    */
   private XAttribute dereferenceAttribute(String entity, String attr) {
      XEntity xentity = lmodel.getEntity(entity);
      XAttribute xattr = null;

      if(xentity != null) {
         xattr = xentity.getAttribute(attr);
      }

      if(xentity == null || xattr == null) {
         LOG.debug("XAttribute not found for: [" + entity + "].[" + attr + "]");
      }

      return xattr;
   }

   /**
    * Validate query.
    */
   @Override
   protected void validateQuery() {
      super.validateQuery();

      if(aggrs.size() > 0) {
         XSelection sel = sql.getSelection();

         for(int i = 0; i < aggrs.size(); i++) {
            String col = (String) aggrs.get(i);
            col = convertAttrInExpression(col, sel);
            aggrs.set(i, col);
         }

         ArrayList groupCols = new ArrayList();

         for(int i = 0; i < sel.getColumnCount(); i++) {
            String col = sel.getColumn(i);

            if(!aggrs.contains(col)) {
               groupCols.add(col);
            }
         }

         Object[] groups = new Object[groupCols.size()];
         groups = groupCols.toArray(groups);

         if(groups.length > 0) {
            sql.setGroupBy(groups);
         }
      }
   }

   private ArrayList aggrs = new ArrayList();
   private XLogicalModel lmodel;
   private Principal user;
   private Hashtable tableEntities = new Hashtable();
   private Hashtable expressionAttributes = new Hashtable();
   private OrderedSet ptables = new OrderedSet();
   private boolean post = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(LMQueryGenerator.class);
}
