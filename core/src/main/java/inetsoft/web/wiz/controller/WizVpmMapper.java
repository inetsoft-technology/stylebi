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
package inetsoft.web.wiz.controller;

import inetsoft.uql.XDataSource;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.HiddenColumns;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.portal.model.database.Condition;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.composer.model.ws.BasicSQLQueryModel;
import inetsoft.web.composer.model.ws.SQLQueryDialogModel;

import java.util.*;

/**
 * Converts between a stored virtual private model and the wire definition of the data model bridge
 * (design 3.4, 7), and canonicalizes an incoming definition so a write can be compared with its
 * read-back.
 *
 * <p>The condition tree on the wire is {@code Group {op, not?, items}} or
 * {@code Clause {left, op, right?, right2?, not?}}. The stored form is an {@code XFilterNode}
 * tree, read by a direct walk. A write flattens the tree into the Portal's own flat list of
 * clauses and conjunctions (each with a level), which {@code VpmService.fromModel} builds exactly
 * as the Portal does ({@code HierarchyListModel.fixConditions} then
 * {@code ConditionListHandler.createXFilterNode}). Siblings of a group are joined by conjunctions
 * of the group's relation at the group's level and a nested group sits one level deeper, so the
 * precedence rule of the builder never regroups anything.</p>
 *
 * <p>Scripts ({@code lookup}, a condition's {@code script}, the hidden-columns {@code script}) are
 * strings stored as written; nothing here rewrites them.</p>
 */
final class WizVpmMapper {
   WizVpmMapper(DataRefModelFactoryService dataRefModelFactoryService) {
      this.dataRefModelFactoryService = dataRefModelFactoryService;
   }

   // -- stored -> wire ----------------------------------------------------------------------

   /** The wire definition of a stored model. */
   Map<String, Object> toWire(VirtualPrivateModel vpm, XDataSource dataSource) {
      Map<String, Object> wire = new LinkedHashMap<>();
      wire.put("name", vpm.getName());
      wire.put("description", blankToNull(vpm.getDescription()));
      wire.put("lookup", blankToNull(vpm.getScript()));
      List<Object> conditions = new ArrayList<>();

      for(Enumeration<?> e = vpm.getConditions(); e.hasMoreElements();) {
         conditions.add(conditionToWire((VpmCondition) e.nextElement(), dataSource));
      }

      wire.put("conditions", conditions);
      wire.put("hidden", hiddenToWire(vpm.getHiddenColumns()));
      return wire;
   }

   private Map<String, Object> conditionToWire(VpmCondition condition, XDataSource dataSource) {
      Map<String, Object> wire = new LinkedHashMap<>();
      wire.put("name", condition.getName());
      Map<String, Object> target = new LinkedHashMap<>();
      target.put("kind", condition.getType() == VpmCondition.PHYSICMODEL ? KIND_PHYSICAL_VIEW :
         KIND_TABLE);
      target.put("name", condition.getTable());
      wire.put("target", target);

      if(!isBlank(condition.getScript())) {
         wire.put("script", condition.getScript());
      }

      Object tree = nodeToWire(condition.getCondition(), dataSource);

      if(tree != null) {
         wire.put("tree", tree);
      }

      return wire;
   }

   /** A stored node as a wire tree; {@code null} for an empty node. */
   private Object nodeToWire(XFilterNode node, XDataSource dataSource) {
      if(node == null) {
         return null;
      }

      if(node instanceof XSet set) {
         List<Object> items = new ArrayList<>();

         for(int i = 0; i < set.getChildCount(); i++) {
            Object child = nodeToWire((XFilterNode) set.getChild(i), dataSource);

            if(child != null) {
               items.add(child);
            }
         }

         if(items.isEmpty()) {
            return null;
         }

         if(items.size() == 1) {
            // the server never stores a one-child set; a negated one folds into its child
            Object only = items.get(0);
            return set.isIsNot() ? negate(only) : only;
         }

         Map<String, Object> group = new LinkedHashMap<>();
         group.put("op", set.getRelation() == null ? XSet.AND : set.getRelation().toLowerCase());

         if(set.isIsNot()) {
            group.put("not", true);
         }

         group.put("items", items);
         return group;
      }

      return clauseToWire(node, dataSource);
   }

   @SuppressWarnings("unchecked")
   private static Object negate(Object node) {
      Map<String, Object> map = (Map<String, Object>) node;

      if(Boolean.TRUE.equals(map.get("not"))) {
         map.remove("not");
      }
      else {
         // keep "not" right after the operator for a readable key order
         Map<String, Object> copy = new LinkedHashMap<>();

         for(Map.Entry<String, Object> entry : map.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());

            if(entry.getKey().equals("op")) {
               copy.put("not", true);
            }
         }

         map.clear();
         map.putAll(copy);
      }

      return map;
   }

   private Map<String, Object> clauseToWire(XFilterNode leaf, XDataSource dataSource) {
      if(!(leaf instanceof XUnaryCondition) && !(leaf instanceof XBinaryCondition) &&
         !(leaf instanceof XTrinaryCondition))
      {
         throw new IllegalStateException(
            "a condition contains a " + leaf.getClass().getSimpleName() +
            " node, which this tool cannot represent");
      }

      Clause clause = JDBCUtil.createCondition(leaf, dataSource);
      Map<String, Object> wire = new LinkedHashMap<>();
      wire.put("left", operandToWire(clause.getValue1()));
      wire.put("op", clause.getOperation().getSymbol());

      if(leaf instanceof XBinaryCondition || leaf instanceof XTrinaryCondition) {
         wire.put("right", operandToWire(clause.getValue2()));
      }

      if(leaf instanceof XTrinaryCondition) {
         wire.put("right2", operandToWire(clause.getValue3()));
      }

      if(clause.isNegated()) {
         wire.put("not", true);
      }

      return wire;
   }

   private Map<String, Object> operandToWire(ClauseValue value) {
      Map<String, Object> operand = new LinkedHashMap<>();
      String type = value.getType() == null ? XExpression.VALUE : value.getType();
      String expression = value.getExpression();

      switch(type) {
      case XExpression.FIELD:
         operand.put("type", "field");
         operand.put("expression", expression == null ? "" : expression);
         break;
      case XExpression.EXPRESSION:
         operand.put("type", "expression");
         operand.put("expression", expression == null ? "" : expression);
         break;
      case XExpression.VARIABLE:
         operand.put("type", "variable");
         operand.put("expression", unwrap(expression));
         break;
      case "Session Data":
         operand.put("type", "session");
         operand.put("expression", unwrap(expression));
         break;
      case XExpression.SUBQUERY:
         operand.put("type", "subquery");
         SQLQueryDialogModel query = value.getQuery();
         String sql = query == null || query.getSimpleModel() == null ? null :
            query.getSimpleModel().getSqlString();
         operand.put("sql", sql == null ? "" : sql);
         break;
      default:
         operand.put("type", "value");
         operand.put("expression", expression == null ? "" : expression);
      }

      return operand;
   }

   private Map<String, Object> hiddenToWire(HiddenColumns hidden) {
      if(hidden == null) {
         return null;
      }

      List<Object> roles = new ArrayList<>();

      for(Enumeration<String> e = hidden.getRoles(); e.hasMoreElements();) {
         roles.add(String.valueOf(e.nextElement()));
      }

      List<Object> columns = new ArrayList<>();

      for(Enumeration<DataRef> e = hidden.getHiddenColumns(); e.hasMoreElements();) {
         DataRef ref = e.nextElement();
         AttributeRef plain = plainAttribute(ref);
         Map<String, Object> column = new LinkedHashMap<>();

         if(plain != null) {
            column.put("table", blankToNull(plain.getEntity()));
            column.put("column", plain.getAttribute());
         }
         else {
            column.put("table", null);
            column.put("column", ref == null ? null : ref.getName());
            column.put("unsupported", true);
         }

         columns.add(column);
      }

      String script = hidden.getScript();

      if(roles.isEmpty() && columns.isEmpty() && isBlank(script)) {
         return null;
      }

      Map<String, Object> wire = new LinkedHashMap<>();
      wire.put("roles", roles);

      if(!isBlank(script)) {
         wire.put("script", script);
      }

      wire.put("columns", columns);
      return wire;
   }

   /** The plain table.column reference behind a hidden item, or null if it is anything else. */
   static AttributeRef plainAttribute(DataRef ref) {
      DataRef current = ref;

      while(current instanceof DataRefWrapper wrapper) {
         current = wrapper.getDataRef();
      }

      return current != null && current.getClass() == AttributeRef.class ?
         (AttributeRef) current : null;
   }

   // -- wire -> Portal DTO ------------------------------------------------------------------

   /**
    * The Portal definition for a validated wire definition. {@code stored} (null on create) is the
    * model being updated: a hidden column read as {@code unsupported} is rebuilt from it, so an
    * update that leaves it alone writes it back untouched.
    */
   VPMDefinition toDefinition(Map<String, Object> wire, String database,
                              VirtualPrivateModel stored)
   {
      VPMDefinition definition = new VPMDefinition();
      definition.setName(string(wire.get("name")));
      definition.setDescription(string(wire.get("description")));
      definition.setLookup(string(wire.get("lookup")));
      List<Condition> conditions = new ArrayList<>();

      for(Object item : list(wire.get("conditions"))) {
         conditions.add(conditionToDefinition(map(item), database));
      }

      definition.setConditions(conditions);
      Map<String, Object> hidden = map(wire.get("hidden"));

      if(hidden != null && !isEmptyHidden(hidden)) {
         definition.setHidden(hiddenToDefinition(hidden, stored));
      }

      return definition;
   }

   private Condition conditionToDefinition(Map<String, Object> wire, String database) {
      Condition condition = new Condition();
      condition.setName(string(wire.get("name")));
      Map<String, Object> target = map(wire.get("target"));
      condition.setType(KIND_PHYSICAL_VIEW.equals(string(target.get("kind"))) ?
                           VpmCondition.PHYSICMODEL : VpmCondition.TABLE);
      condition.setTableName(string(target.get("name")));
      condition.setScript(string(wire.get("script")));
      List<DataConditionItem> clauses = new ArrayList<>();
      Object tree = wire.get("tree");

      if(tree != null) {
         flatten(map(tree), 0, clauses, database);
      }

      condition.setClauses(clauses);
      return condition;
   }

   /**
    * Flattens a wire tree into the Portal's list: siblings are joined by a conjunction of the
    * group's relation at the group's level, a nested group sits one level deeper.
    */
   private void flatten(Map<String, Object> node, int level, List<DataConditionItem> out,
                        String database)
   {
      if(node.containsKey("items")) {
         List<Object> items = list(node.get("items"));
         String relation = string(node.get("op")).toLowerCase();
         boolean not = Boolean.TRUE.equals(node.get("not"));

         for(int i = 0; i < items.size(); i++) {
            if(i > 0) {
               Conjunction conjunction = new Conjunction();
               conjunction.setLevel(level);
               conjunction.setConjunction(relation);
               conjunction.setIsNot(not);
               conjunction.setJunc(true);
               out.add(conjunction);
            }

            Map<String, Object> child = map(items.get(i));

            if(child.containsKey("items")) {
               flatten(child, level + 1, out, database);
            }
            else {
               out.add(clause(child, level, database));
            }
         }
      }
      else {
         out.add(clause(node, level, database));
      }
   }

   private Clause clause(Map<String, Object> node, int level, String database) {
      Clause clause = new Clause();
      clause.setLevel(level);
      Operation operation = new Operation();
      operation.setSymbol(canonicalOp(string(node.get("op"))));
      clause.setOperation(operation);
      clause.setNegated(Boolean.TRUE.equals(node.get("not")));
      clause.setValue1(operand(map(node.get("left")), database));

      if(node.get("right") != null) {
         clause.setValue2(operand(map(node.get("right")), database));
      }

      if(node.get("right2") != null) {
         clause.setValue3(operand(map(node.get("right2")), database));
      }

      return clause;
   }

   private ClauseValue operand(Map<String, Object> node, String database) {
      String type = string(node.get("type")).toLowerCase();
      String expression = string(node.get("expression"));

      switch(type) {
      case "field":
         return new ClauseValue(XExpression.FIELD, expression);
      case "expression":
         return new ClauseValue(XExpression.EXPRESSION, expression);
      case "variable":
         return new ClauseValue(XExpression.VARIABLE, "$(" + expression + ")");
      case "session":
         return new ClauseValue("Session Data", "$(" + expression + ")");
      case "subquery":
         ClauseValue value = new ClauseValue(XExpression.SUBQUERY);
         BasicSQLQueryModel simple = new BasicSQLQueryModel();
         simple.setSqlEdited(true);
         simple.setSqlString(string(node.get("sql")));
         SQLQueryDialogModel query = new SQLQueryDialogModel();
         query.setDataSource(database);
         query.setSimpleModel(simple);
         value.setQuery(query);
         return value;
      default:
         return new ClauseValue(XExpression.VALUE, expression);
      }
   }

   private HiddenColumnsModel hiddenToDefinition(Map<String, Object> hidden,
                                                 VirtualPrivateModel stored)
   {
      HiddenColumnsModel model = new HiddenColumnsModel();
      List<String> roles = new ArrayList<>();

      for(Object role : list(hidden.get("roles"))) {
         roles.add(String.valueOf(role));
      }

      model.setRoles(roles);
      model.setScript(string(hidden.get("script")));
      List<DataRefModel> refs = new ArrayList<>();

      for(Object item : list(hidden.get("columns"))) {
         Map<String, Object> column = map(item);
         String name = string(column.get("column"));

         if(Boolean.TRUE.equals(column.get("unsupported"))) {
            DataRef original = storedUnsupported(stored, name);

            if(original != null) {
               refs.add(dataRefModelFactoryService.createDataRefModel(original));
            }

            continue;
         }

         String table = string(column.get("table"));
         AttributeRefModel ref = new AttributeRefModel();
         ref.setEntity(table == null ? "" : table);
         ref.setAttribute(name);
         String qualified = table == null || table.isEmpty() ? name : table + "." + name;
         ref.setName(qualified);
         ref.setCaption(qualified);
         refs.add(ref);
      }

      model.setHiddens(refs);
      return model;
   }

   private static DataRef storedUnsupported(VirtualPrivateModel stored, String name) {
      if(stored == null || stored.getHiddenColumns() == null) {
         return null;
      }

      for(Enumeration<DataRef> e = stored.getHiddenColumns().getHiddenColumns();
          e.hasMoreElements();)
      {
         DataRef ref = e.nextElement();

         if(plainAttribute(ref) == null && ref != null && Objects.equals(ref.getName(), name)) {
            return ref;
         }
      }

      return null;
   }

   // -- canonical form ----------------------------------------------------------------------

   /**
    * The form a definition has once it is stored and read back: operators and operand types
    * canonicalized, {@code not:false} and empty strings dropped, hidden columns without a table
    * normalized, an empty {@code hidden} object read as {@code null}. Compared with
    * {@link #toWire} after a write (decision C2).
    */
   @SuppressWarnings("unchecked")
   Map<String, Object> normalize(Map<String, Object> wire) {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("name", wire.get("name"));
      out.put("description", blankToNull(string(wire.get("description"))));
      out.put("lookup", blankToNull(string(wire.get("lookup"))));
      List<Object> conditions = new ArrayList<>();

      for(Object item : list(wire.get("conditions"))) {
         Map<String, Object> condition = map(item);
         Map<String, Object> c = new LinkedHashMap<>();
         c.put("name", condition.get("name"));
         Map<String, Object> target = map(condition.get("target"));
         Map<String, Object> t = new LinkedHashMap<>();
         t.put("kind", target.get("kind"));
         t.put("name", target.get("name"));
         c.put("target", t);

         if(!isBlank(string(condition.get("script")))) {
            c.put("script", condition.get("script"));
         }

         if(condition.get("tree") != null) {
            c.put("tree", normalizeNode(map(condition.get("tree"))));
         }

         conditions.add(c);
      }

      out.put("conditions", conditions);
      Map<String, Object> hidden = map(wire.get("hidden"));

      if(hidden == null || isEmptyHidden(hidden)) {
         out.put("hidden", null);
      }
      else {
         Map<String, Object> h = new LinkedHashMap<>();
         h.put("roles", new ArrayList<>(list(hidden.get("roles"))));

         if(!isBlank(string(hidden.get("script")))) {
            h.put("script", hidden.get("script"));
         }

         List<Object> columns = new ArrayList<>();

         for(Object item : list(hidden.get("columns"))) {
            Map<String, Object> column = map(item);
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("table", blankToNull(string(column.get("table"))));
            c.put("column", column.get("column"));

            if(Boolean.TRUE.equals(column.get("unsupported"))) {
               c.put("unsupported", true);
            }

            columns.add(c);
         }

         h.put("columns", columns);
         out.put("hidden", h);
      }

      return out;
   }

   private Map<String, Object> normalizeNode(Map<String, Object> node) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(node.containsKey("items")) {
         out.put("op", string(node.get("op")).toLowerCase());

         if(Boolean.TRUE.equals(node.get("not"))) {
            out.put("not", true);
         }

         List<Object> items = new ArrayList<>();

         for(Object item : list(node.get("items"))) {
            items.add(normalizeNode(map(item)));
         }

         out.put("items", items);
         return out;
      }

      out.put("left", normalizeOperand(map(node.get("left"))));
      out.put("op", canonicalOp(string(node.get("op"))));

      if(node.get("right") != null) {
         out.put("right", normalizeOperand(map(node.get("right"))));
      }

      if(node.get("right2") != null) {
         out.put("right2", normalizeOperand(map(node.get("right2"))));
      }

      if(Boolean.TRUE.equals(node.get("not"))) {
         out.put("not", true);
      }

      return out;
   }

   private Map<String, Object> normalizeOperand(Map<String, Object> operand) {
      Map<String, Object> out = new LinkedHashMap<>();
      String type = string(operand.get("type")).toLowerCase();
      out.put("type", type);

      if("subquery".equals(type)) {
         out.put("sql", string(operand.get("sql")));
      }
      else {
         out.put("expression", operand.get("expression") == null ? "" : operand.get("expression"));
      }

      return out;
   }

   /**
    * The canonical symbol of an operator spelled in any case ({@code !=} is {@code <>}). Only
    * symbols match: a localized operator name ("equal to") is returned unchanged, so it fails
    * the operator check instead of being guessed at.
    */
   static String canonicalOp(String op) {
      if(op == null) {
         return null;
      }

      String trimmed = op.trim();

      if("!=".equals(trimmed)) {
         return "<>";
      }

      for(String symbol : operatorSymbols()) {
         if(symbol.equalsIgnoreCase(trimmed)) {
            return symbol;
         }
      }

      return trimmed;
   }

   /** Every operator symbol of the server's vocabulary (unary, binary, trinary). */
   static List<String> operatorSymbols() {
      List<String> symbols = new ArrayList<>();

      for(String name : XFilterNode.getAllOperators()) {
         String symbol = XFilterNode.getOpSymbol(name);

         if(symbol != null && !symbols.contains(symbol)) {
            symbols.add(symbol);
         }
      }

      return symbols;
   }

   private static boolean isEmptyHidden(Map<String, Object> hidden) {
      return list(hidden.get("roles")).isEmpty() && list(hidden.get("columns")).isEmpty() &&
         isBlank(string(hidden.get("script")));
   }

   private static String unwrap(String expression) {
      if(expression != null && expression.startsWith("$(") && expression.endsWith(")")) {
         return expression.substring(2, expression.length() - 1);
      }

      return expression == null ? "" : expression;
   }

   @SuppressWarnings("unchecked")
   static Map<String, Object> map(Object value) {
      return value instanceof Map ? (Map<String, Object>) value : null;
   }

   @SuppressWarnings("unchecked")
   static List<Object> list(Object value) {
      return value instanceof List ? (List<Object>) value : List.of();
   }

   static String string(Object value) {
      return value == null ? null : String.valueOf(value);
   }

   private static boolean isBlank(String value) {
      return value == null || value.isEmpty();
   }

   private static String blankToNull(String value) {
      return isBlank(value) ? null : value;
   }

   static final String KIND_TABLE = "table";
   static final String KIND_PHYSICAL_VIEW = "physicalView";
   private final DataRefModelFactoryService dataRefModelFactoryService;
}
