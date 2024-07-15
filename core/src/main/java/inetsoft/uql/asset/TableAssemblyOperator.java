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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.internal.ConditionUtil;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Table assembly operator, the operator between two table assemblies.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class TableAssemblyOperator implements AssetObject {
   /**
    * Join operation.
    */
   public static final int JOIN = XConstants.JOIN;
   /**
    * Inner join operation.
    */
   public static final int INNER_JOIN = XConstants.INNER_JOIN;
   /**
    * Left join operation.
    */
   public static final int LEFT_JOIN = XConstants.LEFT_JOIN;
   /**
    * Right join operation.
    */
   public static final int RIGHT_JOIN = XConstants.RIGHT_JOIN;
   /**
    * Full join operation.
    */
   public static final int FULL_JOIN = XConstants.FULL_JOIN;
   /**
    * Not equal join operation.
    */
   public static final int NOT_EQUAL_JOIN = XConstants.NOT_EQUAL_JOIN;
   /**
    * Greater join operation.
    */
   public static final int GREATER_JOIN = XConstants.GREATER_JOIN;
   /**
    * Greater equal join operation.
    */
   public static final int GREATER_EQUAL_JOIN = XConstants.GREATER_EQUAL_JOIN;
   /**
    * Less join operation.
    */
   public static final int LESS_JOIN = XConstants.LESS_JOIN;
   /**
    * Less equal join operation.
    */
   public static final int LESS_EQUAL_JOIN = XConstants.LESS_EQUAL_JOIN;
   /**
    * Merge join operation.
    */
   public static final int MERGE_JOIN = 128 | JOIN;
   /**
    * Cross join operation.
    */
   public static final int CROSS_JOIN = 256 | JOIN;
   /**
    * Concatenation operation.
    */
   public static final int CONCATENATION = 512;
   /**
    * Union operation.
    */
   public static final int UNION = 1024 | CONCATENATION;
   /**
    * Intersect operation.
    */
   public static final int INTERSECT = 2048 | CONCATENATION;
   /**
    * Minus operation.
    */
   public static final int MINUS = 4096 | CONCATENATION;

   /**
    * Constructor.
    */
   public TableAssemblyOperator() {
      super();

      operators = new ArrayList<>();
   }

   /**
    * Create an operator with a single predicate between the two attributes.
    */
   public TableAssemblyOperator(String leftTable, DataRef l,
                                String rightTable, DataRef r, int o)
   {
      this();
      Operator op = new Operator();

      op.setLeftTable(leftTable);
      op.setRightTable(rightTable);
      op.setLeftAttribute(l);
      op.setRightAttribute(r);
      op.setOperation(o);
      addOperator(op);
   }

   /**
    * Get all the operators.
    * @return all the operators.
    */
   public Operator[] getOperators() {
      Operator[] arr = new Operator[operators.size()];
      operators.toArray(arr);

      return arr;
   }

   /**
    * Get the operator at an index.
    * @param index the specified index.
    * @return the operator at the index.
    */
   public Operator getOperator(int index) {
      return operators.get(index);
   }

   /**
    * Get the operator count.
    * @return the operator count.
    */
   public int getOperatorCount() {
      return operators.size();
   }

   /**
    * Add one operator.
    * @param operator the specified operator.
    */
   public void addOperator(Operator operator) {
      operators.add(operator);
   }

   /**
    * Set the operator at an index.
    * @param index the specified index.
    * @param operator the specified operator.
    */
   public void setOperator(int index, Operator operator) {
      operators.set(index, operator);
   }

   /**
    * Remove the operator at an index.
    * @param index the specified index.
    */
   public void removeOperator(int index) {
      operators.remove(index);
   }

   /**
    * Clear the table assembly operator.
    */
   public void clear() {
      operators.clear();
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    * @param lassembly the left assembly.
    * @param rassembly the right assembly.
    */
   public void renameDepended(String oname, String nname, Assembly lassembly,
                               Assembly rassembly) {
      for(int i = 0; i < getOperatorCount(); i++) {
         Operator operator = getOperator(i);
         operator.renameDepended(oname, nname, lassembly, rassembly);
      }
   }

   private static class OperatorKey {
      OperatorKey(Operator op) {
         List<String> tables = new ArrayList<>();

         if(op.getLeftTable() != null) {
            tables.add(op.getLeftTable());
         }

         if(op.getRightTable() != null) {
            tables.add(op.getRightTable());
         }

         this.tables = tables.toArray(new String[0]);
         Arrays.sort(this.tables);
      }

      public int hashCode() {
         int hash = 11;

         for(String table : tables) {
            if(table != null) {
               hash += 7 * table.hashCode();
            }
         }

         return hash;
      }

      public boolean equals(Object obj) {
         if(obj instanceof OperatorKey) {
            OperatorKey key = (OperatorKey) obj;
            return Tool.equals(tables, key.tables);
         }

         return false;
      }

      private String[] tables;
   }

   /**
    * Check if the operator is valid.
    */
   public void checkValidity() throws Exception {
      int type = -1;
      int count = getOperatorCount();
      Map<OperatorKey, Integer> map = new HashMap<>();

      for(int i = 0; i < count; i++) {
         Operator operator = getOperator(i);
         // operation should be valid
         operator.checkValidity();

         // exclusive operation should be alone, such as merged join
         if(operator.isExclusive() && count > 1) {
            throw new Exception("Exclusive operation should be alone!");
         }

         // key operation should be the same, such as right outer join
         if(operator.isKey()) {
            int op = operator.getOperation();
            OperatorKey key = new OperatorKey(operator);
            Integer lastop = map.get(key);

            if(lastop == null) {
               lastop = op;
               map.put(key, lastop);
            }

            if(lastop != op) {
               throw new Exception(Catalog.getCatalog().getString(
                  "common.joinSameKey"));
            }
         }

         if(!operator.isConcatenation()) {
            for(int j = i + 1; j < count; j++) {
               Operator next = getOperator(j);

               if(Tool.equals(operator.getLeftTable(), next.getLeftTable()) &&
                  Tool.equals(operator.getRightTable(), next.getRightTable()) &&
                  Tool.equals(operator.getLeftAttribute(), next.getLeftAttribute()) &&
                  Tool.equals(operator.getRightAttribute(), next.getRightAttribute()) &&
                  sameDateLevel(operator.getLeftAttribute(), next.getLeftAttribute()) &&
                  sameDateLevel(operator.getRightAttribute(), next.getRightAttribute()))
               {
                  throw new Exception(Catalog.getCatalog().getString(
                     "common.joinSameColumns"));
               }

               if(Tool.equals(operator.getLeftTable(), next.getRightTable()) &&
                  Tool.equals(operator.getRightTable(), next.getLeftTable()) &&
                  Tool.equals(operator.getLeftAttribute(), next.getRightAttribute()) &&
                  Tool.equals(operator.getRightAttribute(), next.getLeftAttribute()))
               {
                  throw new Exception(Catalog.getCatalog().getString(
                     "common.joinSameColumns"));
               }
            }
         }
      }
   }

   private boolean sameDateLevel(DataRef attr, DataRef nextAttr) {
      return !(attr instanceof VSChartDimensionRef) ||
         !(nextAttr instanceof VSChartDimensionRef) ||
         ((VSChartDimensionRef) attr).getDateLevel() == ((VSChartDimensionRef)nextAttr).getDateLevel();
   }

   /**
    * Validate attribute.
    * @param ws the worksheet containing this assembly.
    */
   public void validateAttribute(Worksheet ws) throws Exception {
      validateAttribute(ws, true);
   }

   public void validateAttribute(Worksheet ws, boolean checkCrossJoins) throws Exception {
      for(int i = getOperatorCount() - 1; i >= 0; i--) {
         Operator operator = getOperator(i);
         TableAssembly left = (TableAssembly) ws.getAssembly(operator.getLeftTable());
         TableAssembly right = (TableAssembly) ws.getAssembly(operator.getRightTable());

         if(left == null || right == null) {
            continue;
         }

         ColumnSelection lselection = left.getColumnSelection(true);
         ColumnSelection rselection = right.getColumnSelection(true);

         DataRef lref = operator.getLeftAttribute();
         DataRef rref = operator.getRightAttribute();
         String lname = lref instanceof VSDataRef ? ((VSDataRef) lref).getFullName()
            : (lref != null ? lref.getName() : null);
         String rname = rref instanceof VSDataRef ? ((VSDataRef) rref).getFullName()
            : (rref != null ? rref.getName() : null);

         if(lref != null && !lselection.containsAttribute(lref) &&
            lselection.getAttribute(lname) == null)
         {
            if(getOperatorCount() == 1 && isJoin() && checkCrossJoins) {
               throw new CrossJoinException(left.getName(), right.getName(), getOperator(i));
            }

            LOG.warn("Left side attribute not found: " + lref + " in (" + lselection + ")");
            removeOperator(i);
            continue;
         }

         if(lref != null) {
            DataRef nref = lselection.findAttribute(lref);
            nref = nref == null ? lselection.getAttribute(lname): nref;
            operator.setLeftAttribute(nref);
         }

         if(rref != null && !rselection.containsAttribute(rref) &&
            rselection.getAttribute(rname) == null)
         {
            if(getOperatorCount() == 1 && isJoin() && checkCrossJoins) {
               throw new CrossJoinException(left.getName(), right.getName(), getOperator(i));
            }

            LOG.warn("Right side attribute not found: " + rref + " in " + rselection);
            removeOperator(i);
            continue;
         }

         if(rref != null) {
            DataRef nref = rselection.findAttribute(rref);
            nref = nref == null ? rselection.getAttribute(rname): nref;
            operator.setRightAttribute(nref);
         }
      }

      // switch from join to cross join
      if(getOperatorCount() == 0) {
         Operator operator = new Operator();
         operator.setOperation(CROSS_JOIN);
         addOperator(operator);
      }
   }

   /**
    * Get the key Operator.
    * @return the key Operator.
    */
   public Operator getKeyOperator() {
      int cnt = getOperatorCount();

      for(int i = 0; i < cnt; i++) {
         Operator operator = getOperator(i);

         if(operator.isKey()) {
            return operator;
         }
      }

      if(cnt <= 0) {
         Operator operator = new Operator();
         operator.setOperation(CROSS_JOIN);
         return operator;
      }
      else {
         Operator operator = new Operator();
         operator.setOperation(JOIN);
         return operator;
      }
   }

   /**
    * Check if is distinct.
    */
   public boolean isDistinct() {
      Operator op = getKeyOperator();
      return op.isDistinct();
   }

   /**
    * Get the key operation.
    * @return the key operation.
    */
   public int getKeyOperation() {
      for(int i = 0; i < getOperatorCount(); i++) {
         Operator operator = getOperator(i);

         if(operator.isKey()) {
            return operator.getOperation();
         }
      }

      return JOIN;
   }

   /**
    * Check if is join.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isJoin() {
      int op = getKeyOperation();
      return (op & JOIN) == JOIN;
   }

   /**
    * Check if is outer join.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isOuterJoin() {
      for(int i = 0; i < getOperatorCount(); i++) {
         Operator operator = getOperator(i);
         return operator.isOuterJoin();
      }

      return false;
   }

   /**
    * Check if is concatenation.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isConcatenation() {
      int op = getKeyOperation();
      return (op & CONCATENATION) == CONCATENATION;
   }

   /**
    * Check if is merge join.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isMergeJoin() {
      int op = getKeyOperation();
      return (op & MERGE_JOIN) == MERGE_JOIN;
   }

   /**
    * Check if is cross join.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isCrossJoin() {
      int op = getKeyOperation();
      return (op & CROSS_JOIN) == CROSS_JOIN;
   }

   /**
    * Check if is full join.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isFullJoin() {
      int op = getKeyOperation();
      return (op & FULL_JOIN) == FULL_JOIN;
   }

   /**
    * Check if requires column.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean requiresColumn() {
      for(int i = 0; i < getOperatorCount(); i++) {
         Operator operator = getOperator(i);

         if(operator.requiresColumn()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get operators display string.
    */
   public String getOperatorString() {
      String opString;

      if(operators.isEmpty()) {
         opString = "";
      }
      else {
         switch(operators.get(0).operation) {
         case NOT_EQUAL_JOIN:
         case GREATER_JOIN:
         case GREATER_EQUAL_JOIN:
         case LESS_JOIN:
         case LESS_EQUAL_JOIN:
            opString = operators.stream()
               .map(Operator::getName)
               .collect(Collectors.joining(","));
            break;
         default:
            // can't really mix inner/outer/full/cross/merge/etc.
            opString = operators.get(0).getName();
         }
      }

      return opString;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<tableAssemblyOperator>");

      for(int i = 0; i < operators.size(); i++) {
         Operator operator = operators.get(i);
         operator.writeXML(writer);
      }

      writer.println("</tableAssemblyOperator>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      NodeList opnodes = Tool.getChildNodesByTagName(elem, "operator");

      for(int i = 0; i < opnodes.getLength(); i++) {
         Element opnode = (Element) opnodes.item(i);
         Operator operator = new Operator();
         operator.parseXML(opnode);
         operators.add(operator);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         TableAssemblyOperator toperator = (TableAssemblyOperator) super.clone();
         toperator.operators = Tool.deepCloneCollection(operators);

         return toperator;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return operators.hashCode();
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("TOP[");

      for(int i = 0; i < operators.size(); i++) {
         Operator op = operators.get(i);

         if(i > 0) {
            writer.print(",");
         }

         op.printKey(writer);
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object, <tt>false</tt>
    * otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TableAssemblyOperator)) {
         return false;
      }

      TableAssemblyOperator top = (TableAssemblyOperator) obj;

      return operators.equals(top.operators);
   }

   public String toString() {
      return "[" + operators + "]";
   }

   /**
    * Operator represents one sub operator between two tables. It might
    * contain column information for operations which require column
    * information.
    */
   public static class Operator implements AssetObject {
      /**
       * Constructor.
       */
      public Operator() {
         super();

         distinct = true;
         operation = INNER_JOIN;
      }

      /**
       * Get the left column.
       * @return the left column of the operator.
       */
      public DataRef getLeftAttribute() {
         return lref;
      }

      /**
       * Set the left column.
       * @param ref the specified column.
       */
      public void setLeftAttribute(DataRef ref) {
         this.lref = ref;
      }

      /**
       * Get the operation.
       * @return the operation.
       */
      public int getOperation() {
         return operation;
      }

      /**
       * Set the operation.
       * @param op the specified operation.
       */
      public void setOperation(int op) {
         this.operation = op;
      }

      /**
       * Get the right column.
       * @return the right column of the operator.
       */
      public DataRef getRightAttribute() {
         return rref;
      }

      /**
       * Set the right column.
       * @param ref the specified column.
       */
      public void setRightAttribute(DataRef ref) {
         this.rref = ref;
      }

      /**
       * Get the right table.
       * @return the right table name.
       */
      public String getRightTable() {
         return rtable;
      }

      /**
       * Set the right table.
       * @param rtable the right table name.
       */
      public void setRightTable(String rtable) {
         this.rtable = rtable;
      }

      /**
       * Get the left table.
       * @return the left table name.
       */
      public String getLeftTable() {
         return ltable;
      }

      /**
       * Set the left table.
       * @param ltable the left table name.
       */
      public void setLeftTable(String ltable) {
         this.ltable = ltable;
      }

      /**
       * Rename the assemblies depended on.
       * @param oname the specified old name.
       * @param nname the specified new name.
       * @param lassembly the left assembly.
       * @param rassembly the right assembly.
       */
      public void renameDepended(String oname, String nname, Assembly lassembly,
                                  Assembly rassembly)
      {
         ColumnRef column = (ColumnRef) lref;

         if(column != null && lassembly != null) {
            ColumnRef.renameColumn(column, oname, nname);
            setLeftTable(lassembly.getName());
         }

         column = (ColumnRef) rref;

         if(column != null && rassembly != null) {
            ColumnRef.renameColumn(column, oname, nname);
            setRightTable(rassembly.getName());
         }
      }

      /**
       * Check if requires column.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean requiresColumn() {
         return operation == INNER_JOIN || operation == LEFT_JOIN ||
            operation == RIGHT_JOIN || operation == NOT_EQUAL_JOIN ||
            operation == GREATER_JOIN || operation == GREATER_EQUAL_JOIN ||
            operation == LESS_JOIN || operation == LESS_EQUAL_JOIN ||
            operation == FULL_JOIN;
      }

      /**
       * Check if is exclusive operation.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean isExclusive() {
         return isCrossJoin() || isMergeJoin() || isConcatenation();
      }

      /**
       * Check if is key operation.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean isKey() {
         return isEqualJoin() || isCrossJoin() || isMergeJoin() ||
            isConcatenation();
      }

      /**
       * Check if is join.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean isJoin() {
         return (operation & JOIN) == JOIN;
      }

      /**
       * Check if is outer join.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean isOuterJoin() {
         return operation == LEFT_JOIN || operation == RIGHT_JOIN ||
            operation == FULL_JOIN;
      }

      /**
       * Check if is equal join like <tt>INNER_JOIN<tt>, <tt>RIGHT_JOIN</tt>,
       * <tt>LEFT_JOIN</tt> etc.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean isEqualJoin() {
         return operation == INNER_JOIN || operation == LEFT_JOIN ||
            operation == RIGHT_JOIN || operation == FULL_JOIN;
      }

      /**
       * Check if is cross join.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean isCrossJoin() {
         return (operation & CROSS_JOIN) == CROSS_JOIN;
      }

      /**
       * Check if is merge join.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean isMergeJoin() {
         return (operation & MERGE_JOIN) == MERGE_JOIN;
      }

      /**
       * Check if is concatenation.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean isConcatenation() {
         return (operation & CONCATENATION) == CONCATENATION;
      }

      /**
       * Check if supports distinct option.
       * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
       */
      public boolean supportsDistinct() {
         return isConcatenation();
      }

      /**
       * Check if is distinct.
       * @return <tt>true</tt> if distinct, <tt>false</tt> otherwise.
       */
      public boolean isDistinct() {
         return distinct;
      }

      /**
       * Set the distinct option.
       * @param distinct <tt>true</tt> if distinct, <tt>false</tt> otherwise.
       */
      public void setDistinct(boolean distinct) {
         this.distinct = distinct;
      }

      /**
       * Check if the operator is valid.
       */
      public void checkValidity() throws Exception {
         if(requiresColumn()) {
            Catalog catalog = Catalog.getCatalog();

            if(lref == null) {
               MessageException ex = new MessageException(catalog.getString(
                  "common.tableOperator.leftNull"));
               throw ex;
            }
            else if(rref == null) {
               MessageException ex = new MessageException(catalog.getString(
                  "common.tableOperator.rightNull"));
               throw ex;
            }
         }
      }

      /**
       * Get a display name for the operator.
       */
      public String getName() {
         switch(operation) {
         case JOIN: return "JOIN";
         case INNER_JOIN: return "INNER_JOIN";
         case LEFT_JOIN: return "LEFT_JOIN";
         case RIGHT_JOIN: return "RIGHT_JOIN";
         case FULL_JOIN: return "FULL_JOIN";
         case NOT_EQUAL_JOIN: return "NOT_EQUAL_JOIN";
         case GREATER_JOIN: return "GREATER_JOIN";
         case GREATER_EQUAL_JOIN: return "GREATER_EQUAL_JOIN";
         case LESS_JOIN: return "LESS_JOIN";
         case LESS_EQUAL_JOIN: return "LESS_EQUAL_JOIN";
         case MERGE_JOIN: return "MERGE_JOIN";
         case CROSS_JOIN: return "CROSS_JOIN";
         case CONCATENATION: return "CONCATENATION";
         case UNION: return "UNION";
         case INTERSECT: return "INTERSECT";
         case MINUS: return "MINUS";
         }

         return "UNKNOWN";
      }

      /**
       * Get a display name for the operator.
       */
      public String getName2() {
         switch(operation) {
         case INNER_JOIN: return "=";
         case LEFT_JOIN: return "*=";
         case RIGHT_JOIN: return "=*";
         case FULL_JOIN: return "*=*";
         case NOT_EQUAL_JOIN: return "<>";
         case GREATER_JOIN: return ">";
         case GREATER_EQUAL_JOIN: return ">=";
         case LESS_JOIN: return "<";
         case LESS_EQUAL_JOIN: return "<=";
         case MERGE_JOIN: return "==";
         case CROSS_JOIN: return "*";
         case JOIN: return "JOIN";
         case CONCATENATION: return "CONCATENATION";
         case UNION: return "UNION";
         case INTERSECT: return "INTERSECT";
         case MINUS: return "MINUS";
         }

         return "UNKNOWN";
      }


      /**
       * Write the xml segment to print writer.
       * @param writer the destination print writer.
       */
      @Override
      public void writeXML(PrintWriter writer) {
         writer.println("<operator operation=\"" + operation +
                        "\" distinct=\"" + distinct +
                        "\" leftTable=\"" + Tool.escape(ltable) +
                        "\" rightTable=\"" + Tool.escape(rtable) + "\">");

         if(lref != null) {
            writer.println("<leftColumn>");
            lref.writeXML(writer);
            writer.println("</leftColumn>");
         }

         if(rref != null) {
            writer.println("<rightColumn>");
            rref.writeXML(writer);
            writer.println("</rightColumn>");
         }

         writer.println("</operator>");
      }

      /**
       * Method to parse an xml segment.
       * @param elem the specified xml element.
       */
      @Override
      public void parseXML(Element elem) throws Exception {
         operation = Integer.parseInt(Tool.getAttribute(elem, "operation"));
         distinct = "true".equals(Tool.getAttribute(elem, "distinct"));
         ltable = Tool.getAttribute(elem, "leftTable");
         rtable = Tool.getAttribute(elem, "rightTable");

         Element lnode = Tool.getChildNodeByTagName(elem, "leftColumn");

         if(lnode != null) {
            lnode = Tool.getFirstChildNode(lnode);
            lref = AbstractDataRef.createDataRef(lnode);
         }

         Element rnode = Tool.getChildNodeByTagName(elem, "rightColumn");

         if(rnode != null) {
            rnode = Tool.getFirstChildNode(rnode);
            rref = AbstractDataRef.createDataRef(rnode);
         }
      }

      /**
       * Clone this object.
       * @return the cloned object.
       */
      @Override
      public Object clone() {
         try {
            Operator op = (Operator) super.clone();
            op.lref = lref == null ? null : (DataRef) lref.clone();
            op.rref = rref == null ? null : (DataRef) rref.clone();

            return op;
         }
         catch(Exception ex) {
            // ignore it
         }

         return null;
      }

      /**
       * Get the hash code value.
       * @return the hash code value.
       */
      public int hashCode() {
         int hash = operation;

         if(distinct) {
            hash = hash ^ 17;
         }

         if(lref != null) {
            hash = hash ^ lref.hashCode();
         }

         if(rref != null) {
            hash = hash ^ rref.hashCode();
         }

         return hash;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      public boolean printKey(PrintWriter writer) throws Exception {
         writer.print("OP[");
         writer.print(operation);
         writer.print(",");
         writer.print(distinct);

         if(lref != null) {
            writer.print(",");
            ConditionUtil.printDataRefKey(lref, writer);
         }

         if(rref != null) {
            writer.print(",");
            ConditionUtil.printDataRefKey(rref, writer);
         }

         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @param obj the specified object.
       * @return <tt>true</tt> if equals the object, <tt>false</tt>
       * otherwise.
       */
      public boolean equals(Object obj) {
         if(!(obj instanceof Operator)) {
            return false;
         }

         Operator op2 = (Operator) obj;

         if(operation != op2.operation) {
            return false;
         }

         if(distinct != op2.distinct) {
            return false;
         }

         if(!ConditionUtil.equalsDataRef(lref, op2.lref)) {
            return false;
         }

         if(!ConditionUtil.equalsDataRef(rref, op2.rref)) {
            return false;
         }

         return true;
      }

      public String toString() {
         return "[" + lref + " " + getName2() + " " + rref + "]";
      }

      private int operation;
      private boolean distinct;
      private DataRef lref;
      private DataRef rref;
      private String ltable;
      private String rtable;
   }

   private ArrayList<Operator> operators;

   private static final Logger LOG = LoggerFactory.getLogger(TableAssemblyOperator.class);
}
