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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;
import java.sql.Types;
import java.util.*;

/**
 * ConditionListHandler translates a selection from a data model into
 * a XFilterNode format.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 * @see XFilterNode
 */
public class ConditionListHandler {
   /**
    * Create a ConditionListHandler that translate the specified
    * condition list into XFilterNode format.
    */
   public ConditionListHandler() {
      super();
      prefix = "dataselcond";
   }

   /**
    * Create a ConditionListHandler that translate the specified
    * condition list into XFilterNode format.
    * @param replaceVariables determines whether variables will be replaced
    */
   public ConditionListHandler(boolean replaceVariables) {
      this();
      this.replaceVariables = replaceVariables;
   }

   /**
    * Generates an <code>XFilterNode</code> object that can be used by
    * <code>JDBCModelHandler</code>.
    * @param conditions conditionlist object
    * @param sql a uniformed sql.
    * @param partition the data model from which the attributes were taken.
    * @param vars variable values for the query.
    * @return a XFilterNode object.
    */
   public XFilterNode createXFilterNode(ConditionList conditions,
                                        UniformSQL sql, VariableTable vars,
                                        XPartition partition,
                                        XLogicalModel lmodel) {
      this.sql = sql;
      this.lmodel = lmodel;
      this.partition = partition;
      Queue queue = new Queue();
      conditions.validate();

      for(int i = 0, varcnt = 1; i < conditions.getSize(); i++) {
         HierarchyItem item = (HierarchyItem) conditions.getItem(i);

         if(item instanceof ConditionItem) {
            final ConditionItem citem = (ConditionItem) item;
            queue.enqueue(createItem(citem, vars));
         }
         else if(item instanceof JunctionOperator) {
            final JunctionOperator jitem = (JunctionOperator) item;
            queue.enqueue(createItem(jitem));
         }
      }

      XFilterNode root = walk(queue);
      return root;
   }

   /**
    * Generates an <code>XFilterNode</code> object
    * @param conditions conditionlist object
    * @param sql a uniformed sql.
    * @param vars variable values for the query.
    * @return a XFilterNode object.
    */
   public XFilterNode createXFilterNode(ConditionList conditions,
                                        UniformSQL sql, VariableTable vars) {
      this.sql = sql;
      // @by davyc, the original logic for enhance between is wrong
      conditions = ConditionUtil.expandBetween(conditions, vars);
      Queue queue = new Queue();

      for(int i = 0, varcnt = 1; i < conditions.getSize(); i++) {
         HierarchyItem item = (HierarchyItem) conditions.getItem(i);

         if(item instanceof ConditionItem) {
            final ConditionItem citem = (ConditionItem) item;
            final XFilterNodeItem xfni = createItem(citem, vars);
            queue.enqueue(xfni);
         }
         else if(item instanceof JunctionOperator) {
            final JunctionOperator jitem = (JunctionOperator) item;
            queue.enqueue(createItem(jitem));
         }
      }

      XFilterNode root = walk(queue);
      return root;
   }

   /**
    * Generates an <code>XFilterNode</code> object
    *
    * @param list a list of HierarchyItem objects.
    *
    * @return a XFilterNode object.
    */
   public XFilterNode createXFilterNode(HierarchyList list) {
      /* @by jasons createXFilterNode needs to take the precedence of junction
         operators into account (AND should be evaluated before OR). To ensure
         this, the following algorithm is used, using the parseClause() function
         recursively.

         1  find indices of OR's at current level
         2  if clause has OR's at current level
            3  create OR XSet
            4  split clause into subclauses at OR junctions
            5  for each subclause
               6  call step 1 for subclause
               7  append result to XSet
            8  return XSet
         9  else (clause has no OR's at current level)
            10 find indices of AND's at current level
            11 if clause has AND's at current level
               12 create AND XSet
               13 split clause into subclauses at AND junctions
               14 for each subclause
                  15 call step 1 for subclause
                  16 append result to XSet
               17 return XSet
            18 else (clause has no AND's at current level)
               19 if clause has only one item
                  20 return XFilterNode of item
               21 else (clause has more than one item)
                  22 increment the current level
                  23 call step 1 for clause
                  24 return result

         the clause (A and B or C and D) will result in the tree:
            or
            /\
         and  and
         / \  / \
         A B  C D

         and the clause ((A or B) and (C or D)) will result in the tree:
            and
            / \
          or   or
          /\   /\
         A  B C  D

         which are correct, based on the grouping and precedence of the junction
         operators.
      */
      HierarchyItem[] clause = new HierarchyItem[list.getSize()];

      for(int i = 0; i < clause.length; i++) {
         clause[i] = list.getItem(i);
      }

      return parseClause(clause, 0);
   }

   /**
    * Create XFilterNode Item.
    */
   protected XFilterNodeItem createItem(ConditionItem item, VariableTable vars)
   {
      Condition cond = item.getCondition();
      DataRef dref = item.getAttribute();
      String ent = dref.getEntity();
      XExpression field;

      if(replaceVariables) {
         cond.replaceVariable(vars);
      }

      // process the condition defined on an expressionRef
      if(ent == null || ent.equals("") || dref instanceof ExpressionRef) {
         field = attributeToExpression(dref);
      }
      else {
         field = dataRefToExpression(dref);
      }

      XFilterNode condnode = createCondNode(cond, field, vars);
      return new XFilterNodeItem(condnode, item.getLevel());
   }

   /**
    * Convert an attribute to an XExpression.
    */
   protected XExpression attributeToExpression(DataRef dref) {
      String attrName = dref instanceof ExpressionRef ?
         ((ExpressionRef) dref).getExpression() : dref.getAttribute();
      String ftype = dref instanceof ExpressionRef ?
         XExpression.EXPRESSION : XExpression.FIELD;
      return new XExpression(attrName, ftype);
   }

   /**
    * Convert a DataRef to an XExpression.
    */
   protected XExpression dataRefToExpression(DataRef dref) {
      if(lmodel != null && partition != null) {
         return attributeToExpression0(dref);
      }
      else {
         return columnToExpression(dref);
      }
   }

   /**
    * Convert an entity attribute to a field expression.
    * The corresponding table is added to the UniformSQL.
    */
   private XExpression attributeToExpression0(DataRef dref) {
      String ent = dref.getEntity();
      XEntity entity = lmodel.getEntity(ent);

      if(entity == null) {
         throw new RuntimeException("Entity not found in logical model: " +
                                    dref.getEntity());
      }

      XAttribute xattr = entity.getAttribute(dref.getAttribute());

      if(xattr == null) {
         throw new RuntimeException("Attribute not found in entity[" +
                                    entity.getName() + "]: " +
                                    dref.getAttribute());
      }

      String tblName = xattr.getTable();
      Object alias = partition.getRunTimeTable(tblName, true);

      if(alias != null) {
         sql.addTable(tblName, alias);
      }
      else {
         sql.addTable(tblName);
      }

      String colName = xattr.getColumn();
      return new XExpression(tblName + "." + colName, XExpression.FIELD);
   }

   /**
    * Convert a DataRef to an XExpression.
    */
   private XExpression columnToExpression(DataRef dref) {
      String tblName = dref.getEntity();
      String colName = dref.getAttribute();
      String name = tblName == null ? colName : tblName + "." + colName;
      return new XExpression(name, XExpression.FIELD);
   }

   /**
    * Get an XExpression. If the value is a DataRef, a field is created.
    * Otherwise the value is added to the VariableTable and a variable
    * expression is returned.
    */
   protected XExpression getExpression(Condition con, Object val,
                                       VariableTable vars)
   {
      return getExpression(con, val, vars, false);
   }

   /**
    * Get an XExpression. If the value is a DataRef, a field is created.
    * Otherwise the value is added to the VariableTable and a variable
    * expression is returned.
    */
   protected XExpression getExpression(Condition con, Object val,
      VariableTable vars, boolean forCondition)
   {
      return getExpression(val, vars, forCondition);
   }

   /**
    * Get an XExpression. If the value is a DataRef, a field is created.
    * Otherwise the value is added to the VariableTable and a variable
    * expression is returned.
    */
   protected XExpression getExpression(Object val, VariableTable vars) {
      return getExpression(val, vars, false);
   }

   /**
    * Get an XExpression. If the value is a DataRef, a field is created.
    * Otherwise the value is added to the VariableTable and a variable
    * expression is returned.
    */
   protected XExpression getExpression(Object val, VariableTable vars, boolean forCondition) {
      if(val instanceof DataRef) {
         return dataRefToExpression((DataRef) val);
      }
      else {
         String varname = prefix + (varcnt++);
         vars.put(varname, val);
         String expressionValue = "$(" + varname + ")";

         // for mysql sql like 'where time(expression) = $(variable)', it will do not correct filter when
         // variable is java.sql.Time
         if(forCondition && val instanceof Time && sql != null && sql.getDataSource() != null &&
            sql.getDataSource().getDatabaseType() == JDBCDataSource.JDBC_MYSQL)
         {
            return new XExpression("time(" + expressionValue + ")", XExpression.EXPRESSION);
         }

         return new XExpression(expressionValue, XExpression.EXPRESSION);
      }
   }

   /**
    * Get an strip quotes string. Strip the quotes of a string value.
    * @param sqlTypes use the sqlTypes to strip the quotes.
    * @param value the value to be striped.
    * @return the value striped quotes.
    */
   protected String getStripQuotesString(SQLTypes sqlTypes, Object value) {
      return sqlTypes.stripQuotes(value);
   }

   /**
    * Create XFilterNode.
    * The logical model and partition should be supplied if the query is
    * from a data model binding. Otherwise they can be passed in as null.
    */
   protected XFilterNode createCondNode(Condition cond, XExpression field,
                                        VariableTable vars) {
      XFilterNode condnode;
      SQLTypes sqlTypes = SQLTypes.getSQLTypes(sql.getDataSource());
      List values = cond.getValues();

      for(int i = 0; i < cond.getValueCount(); i++) {
         Object val = values.get(i);

         if((val instanceof String) &&
            val.equals(XConstants.CONDITION_NULL_VALUE))
         {
            cond.setOperation(XCondition.NULL);
         }
         else if((val instanceof String) &&
            val.equals(XConstants.CONDITION_EMPTY_STRING))
         {
            cond.setValue(i, "");
         }
         else if((val instanceof String) &&
            val.equals(XConstants.CONDITION_NULL_STRING))
         {
            cond.setValue(i, "null");
         }
         else if(val instanceof Object[]) {
            Object[] objs = (Object[]) val;

            if(objs.length == 1) {
               if(XConstants.CONDITION_NULL_VALUE.equals(objs[0])) {
                  cond.setOperation(XCondition.NULL);
                  break;
               }
               else if(XConstants.CONDITION_EMPTY_STRING.equals(objs[0])) {
                  objs[0] = "";
               }
               else if(XConstants.CONDITION_NULL_STRING.equals(objs[0]))
               {
                  objs[0] = "null";
               }
            }

            for(int j = 0; j < objs.length && objs.length > 1; j++) {
               if(XConstants.CONDITION_NULL_VALUE.equals(objs[j]) ||
                  XConstants.CONDITION_EMPTY_STRING.equals(objs[j]))
               {
                  LOG.warn(
                     "Ignore the NULL_VALUE or EMPTY_STRING condition");
               }
            }
         }
      }

      switch(cond.getOperation()) {
      case XCondition.ONE_OF:
         StringBuilder value = new StringBuilder();
         value.append('(');

         for(int j = 0; j < cond.getValueCount(); j++) {
            if(j > 0) {
               value.append(',');
            }

            value.append(getExpression(cond, cond.getValue(j), vars));
         }

         value.append(')');
         condnode = new XBinaryCondition(
            field, new XExpression(value.toString(), XExpression.EXPRESSION),
            "IN");
         break;
      case XCondition.BETWEEN:
         condnode = new XTrinaryCondition(field,
                                          getExpression(cond, cond.getValue(0), vars),
                                          getExpression(cond, cond.getValue(1), vars),
                                          "BETWEEN");
         break;
      case XCondition.STARTING_WITH:
         XExpression xexpression = getExpression(cond, cond.getValue(0), vars);
         String startStr = getStripQuotesString(sqlTypes, xexpression.getValue());
         condnode = new XBinaryCondition(
            field,
            new XExpression("'" + startStr + "%'", XExpression.EXPRESSION),
            "LIKE");
         break;
      case XCondition.CONTAINS:
         XExpression containsExpression = getExpression(cond, cond.getValue(0), vars);
         String containsStr = getStripQuotesString(sqlTypes, containsExpression.getValue());
         condnode = new XBinaryCondition(
            field,
            new XExpression("'%" + containsStr + "%'", XExpression.EXPRESSION),
            "LIKE");
         break;
      case XCondition.LIKE:
         XExpression likeExpression = getExpression(cond, cond.getValue(0), vars);
         String likeStr = getStripQuotesString(sqlTypes, likeExpression.getValue());
         condnode = new XBinaryCondition(
            field,
            new XExpression("'" + likeStr + "'", XExpression.EXPRESSION),
            "LIKE");
         break;
      case XCondition.NULL:
         condnode = new XUnaryCondition(field, "IS NULL");
         break;
      case XCondition.LESS_THAN:
         condnode = new XBinaryCondition(field,
                                         getExpression(cond, cond.getValue(0), vars),
                                         cond.isEqual() ? "<=" : "<");
         break;
      case XCondition.GREATER_THAN:
         condnode = new XBinaryCondition(field,
                                         getExpression(cond, cond.getValue(0), vars),
                                         cond.isEqual() ? ">=" : ">");
         break;
      default: // EQUAL_TO
         Object condValue = cond.getValue(0);
         condValue = fixConditionValue(field, condValue);
         condnode = new XBinaryCondition(field, getExpression(cond, condValue, vars, true), "=");
         break;
      }

      condnode.setIsNot(cond.isNegated());
      return condnode;
   }

   private Object fixConditionValue(XExpression field, Object condValue) {
      if(field == null || condValue == null || !field.isSqlTypeSet()) {
         return condValue;
      }

      if(!(condValue instanceof String || condValue instanceof Number || condValue instanceof Boolean)) {
         return condValue;
      }

      int sql_type = field.getSqlType();

      if(sql_type == Types.BOOLEAN || sql_type == Types.VARCHAR || sql_type == Types.INTEGER) {
         String sqlType = SQLTypes.getSQLTypes(null).convertToXType(sql_type);
         return CoreTool.getData(sqlType, condValue);
      }

      return condValue;
   }

   /**
    * Create XSet Item.
    */
   private XSetItem createItem(JunctionOperator item) {
      XSet result = new XSet(XSet.AND);

      if(item.getJunction() == JunctionOperator.OR) {
         result = new XSet(XSet.OR);
      }

      return new XSetItem(result, item.getLevel());
   }

   /**
    * Calculate the condition group.
    */
   private XFilterNode walk(Queue queue) {
      Stack stack = new Stack();
      Set created = new HashSet();

      while(queue.peek() != null) {
         if(stack.empty()) {
            Object obj = queue.dequeue();
            stack.push(obj);
            continue;
         }

         HierarchyItem istack = (HierarchyItem) stack.peek();
         HierarchyItem iqueue = (HierarchyItem) queue.peek();

         if(iqueue == null) {
            break;
         }
         else if(iqueue.getLevel() > istack.getLevel()) {
            Object obj = queue.dequeue();
            stack.push(obj);
         }
         else if(iqueue.getLevel() == istack.getLevel()) {
            if(iqueue instanceof XSetItem && !created.contains(iqueue)) {
               Object obj = queue.dequeue();
               stack.push(obj);
            }
            else {
               XSetItem op = (XSetItem) istack;

               if(op.getXSet().getRelation().equals(XSet.AND)) {
                  calcQueueStack(queue, stack, created);
               }
               else {
                  stack.push(queue.dequeue());
               }
            }
         }
         else {
            calcStack(queue, stack, created);
         }
      }

      while(!stack.empty() && stack.size() > 2) {
         calcStackStack(queue, stack, created);
      }

      return stack.empty() ? null : ((XFilterNodeItem) stack.pop()).getNode();
   }

   /**
    * Calculate conditions from stack and put back to stack.
    */
   private void calcStackStack(Queue queue, Stack stack, Set created) {
      XFilterNodeItem i1 = (XFilterNodeItem) stack.pop();
      XSetItem op = (XSetItem) stack.pop();
      XFilterNodeItem i2 = (XFilterNodeItem) stack.pop();
      XFilterNodeItem result = calc(i1, op, i2, queue, stack);
      created.add(result);
      stack.push(result);
   }

   /**
    * Calculate conditions from queue and stack and put to queue.
    */
   private void calcQueueStack(Queue queue, Stack stack, Set created) {
      XFilterNodeItem i2 = (XFilterNodeItem) queue.dequeue();
      XSetItem op = (XSetItem) stack.pop();
      XFilterNodeItem i1 = (XFilterNodeItem) stack.pop();
      XFilterNodeItem result = calc(i1, op, i2, queue, stack);
      created.add(result);
      queue.add(0, result);
   }

   /**
    * Calculate conditions from stack and put to queue.
    */
   private void calcStack(Queue queue, Stack stack, Set created) {
      XFilterNodeItem i2 = (XFilterNodeItem) stack.pop();
      XSetItem op = (XSetItem) stack.pop();
      XFilterNodeItem i1 = (XFilterNodeItem) stack.pop();
      XFilterNodeItem result = calc(i1, op, i2, queue, stack);
      created.add(result);
      queue.add(0, result);
   }

   /**
    * Check the result item level.
    */
   private int checkLevel(int original, Queue queue, Stack stack) {
      int lvl1 = queue.peek() == null ?
         -1 :
         ((HierarchyItem) queue.peek()).getLevel();
      int lvl2 = stack.empty() ?
         -1 :
         ((HierarchyItem) stack.peek()).getLevel();
      int lvl = Math.max(lvl1, lvl2);

      return (lvl == -1) ? original : lvl;
   }

   /**
    * Calculate two boolean values.
    */
   private XFilterNodeItem calc(XFilterNodeItem i1, XSetItem op,
                                XFilterNodeItem i2, Queue queue, Stack stack) {
      final int lvl = checkLevel(op.getLevel(), queue, stack);
      XSet set = op.getXSet();

      set.addChild(i1.getNode());
      set.addChild(i2.getNode());
      op.setLevel(lvl);

      return op;
   }

   /**
    * Recursively builds a filter node subtree from a subclause. Ensures that
    * the nodes are ordered properly. Nodes are ordered by grouping, then
    * junction operator (AND has precedence over OR). Expressions at the bottom
    * of the tree are evaluated before the expressions above them.
    *
    * @param clause a list of HierarchyItem objects that represent the clause
    *               being parsed.
    * @param currlvl the current grouping level being parsed.
    *
    * @return the filter node subtree that represents the specified clause.
    */
   private XFilterNode parseClause(HierarchyItem[] clause, int currlvl) {
      if(clause == null || clause.length == 0) {
         return null;
      }

      XFilterNode result = splitAndParseClause(clause, currlvl, XSet.OR);

      if(result != null) { // have OR's at current level
         return result;
      }
      else { // no OR's at current level
         result = splitAndParseClause(clause, currlvl, XSet.AND);

         if(result != null) { // have AND's at current level
            return result;
         }
         else { // no AND's at current level
            // @by vincentx, 2004-09-15, fix bug1095061780997
            // handles NOT AND and NOT OR.
            result = splitAndParseClause(clause, currlvl, "not and");

            if(result != null) { // have NOT AND's at current level
               return result;
            }
            else { // no NOT AND's at current level
               result = splitAndParseClause(clause, currlvl, "not or");

               if(result != null) { // have NOT OR's at current level
                  return result;
               }
               else { // no NOT OR's at current level
                  if(clause.length == 1) { // only have one item left, return it
                     return (XFilterNode)
                        ((XFilterNodeItem) clause[0]).getNode().clone();
                  }
                  else { // have more items at higher level
                     return parseClause(clause, currlvl + 1);
                  }
               }
            }
         }
      }
   }

   /**
    * Splits a clause into subclauses by the joins of the specified type at the
    * current level.
    *
    * @param clause the clause to split.
    * @param currlvl the current level being parsed.
    * @param type either XSet.OR or XSet.AND.
    *
    * @return an XFilterNode tree representing the the clause, with an XSet of
    *         the specified type at the root.
    */
   private XFilterNode splitAndParseClause(HierarchyItem[] clause, int currlvl,
                                           String type) {
      ArrayList junctions = new ArrayList();

      if(!type.startsWith("not")) { // handles AND/OR
         for(int i = 0; i < clause.length; i++) {
            if(clause[i] instanceof XSetItem &&
               ((XSetItem) clause[i]).getXSet().getRelation().equals(type) &&
               clause[i].getLevel() == currlvl &&
               !validateJunction(((XSetItem) clause[i]).toString())
               .startsWith("not"))
            {
               junctions.add(Integer.valueOf(i));
            }
         }
      }
      else if(type.endsWith("and")) { // handles NOT AND
         for(int i = 0; i < clause.length; i++) {
            if(clause[i] instanceof XSetItem &&
               clause[i].getLevel() == currlvl &&
               validateJunction(((XSetItem) clause[i]).toString()).
               endsWith("and")) {

               junctions.add(Integer.valueOf(i));
            }
         }
      }
      else if(type.endsWith("or")) { // handles NOT OR
         for(int i = 0; i < clause.length; i++) {
            if(clause[i] instanceof XSetItem &&
               clause[i].getLevel() == currlvl &&
               validateJunction(((XSetItem) clause[i]).toString())
               .endsWith("or")) {

               junctions.add(Integer.valueOf(i));
            }
         }
      }

      if(junctions.size() > 0) {
         XSet set = new XSet();

         // @by vincentx, 2004-09-15
         // set IsNot when the junctions are NOT AND or NOT OR.
         if(type.equals("not and")) {
            set.setRelation(XSet.AND);
            set.setIsNot(true);
         }
         else if(type.equals("not or")) {
            set.setRelation(XSet.OR);
            set.setIsNot(true);
         }
         else {
            set.setRelation(type);
         }

         HierarchyItem[] subclause = null;
         int startidx = 0, curridx = 0;

         for(int i = 0; i < junctions.size(); i++) {
            curridx = ((Integer) junctions.get(i)).intValue();
            subclause = new HierarchyItem[curridx - startidx];
            System.arraycopy(clause, startidx, subclause, 0, subclause.length);
            set.addChild(parseClause(subclause, currlvl));
            startidx = curridx + 1;
         }

         subclause = new HierarchyItem[clause.length - startidx];
         System.arraycopy(clause, startidx, subclause, 0, subclause.length);
         set.addChild(parseClause(subclause, currlvl));
         set.setGroup(true);
         return set;
      }

      return null;
   }

   /**
    * Remove the meaningless part from an XSetItem.
    * Note: only handles junctions.
    * e.g, ......not  and --> not  and
    */
   private String validateJunction(String junction) {
      String str = junction.trim();
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < str.length() ; i++) {
         char ch = junction.charAt(i);

         if(Character.isLetter(ch) || (ch == ' ')) {
            sb.append(ch);
         }
      }

      return sb.toString().trim();
   }

   /**
    * Check if two xsets are equal.
    */
   private boolean isEqualXSet(XSet xset1, XSet xset2) {
      if(xset1.getRelation().equals(xset2.getRelation()) &&
         xset1.isIsNot() == xset2.isIsNot())
      {
         return true;
      }

      return false;
   }

   protected transient int varcnt = 1;
   protected transient String prefix;
   private UniformSQL sql; // the query the handler is creating
   private XLogicalModel lmodel; // the model if query is from datamodel binding
   private XPartition partition;
   private boolean replaceVariables = true;

   private static final Logger LOG =
      LoggerFactory.getLogger(ConditionListHandler.class);
}
