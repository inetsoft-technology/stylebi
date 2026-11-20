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

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XFilterNode;
import inetsoft.uql.jdbc.XTrinaryCondition;
import inetsoft.uql.jdbc.XUnaryCondition;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static inetsoft.web.wiz.controller.WizVpmMapper.*;

/**
 * Validates a VPM wire definition before anything is written (design 3.4, 7). {@code createVPM}
 * dereferences operands and the operator unguarded (an unknown symbol is an NPE deep in
 * {@code JDBCUtil}), so every shape and arity rule is checked here and named by field.
 *
 * <p>Field paths are name-keyed ({@code conditions[EastOnly].tree.items[1].right.type}), with an
 * index fallback for an unnamed element. All errors are collected, at most {@value #MAX_ERRORS}.
 * Scripts ({@code lookup}, condition and hidden-columns scripts) are never inspected.</p>
 */
final class WizVpmValidator {
   /** What the validation is for: a create, an update of {@code stored}, or a test draft. */
   enum Mode {
      CREATE, UPDATE, DRAFT
   }

   record Result(List<WizDataModelWire.Err> errors, List<String> warnings) {
   }

   /**
    * @param subqueryCheck returns an error text for a subquery's SQL, or null if it is usable;
    *                      null skips the check
    */
   WizVpmValidator(Function<String, String> subqueryCheck) {
      this.subqueryCheck = subqueryCheck;
   }

   /**
    * @param dataModel  the data source's data model (physical view targets are looked up in it)
    * @param definition the wire definition, canonical keys only
    * @param stored     the model being updated, else null
    * @param roleExists whether a role name exists; null skips the (warning-only) role check
    */
   Result validate(XDataModel dataModel, Map<String, Object> definition, Mode mode,
                   VirtualPrivateModel stored, Predicate<String> roleExists)
   {
      Run run = new Run(dataModel, roleExists);

      if(definition == null) {
         run.error("definition", "definition is required", "REQUIRED");
         return run.result();
      }

      run.unknownKeys("definition", definition, TOP_KEYS);

      if(mode != Mode.DRAFT && isBlank(definition.get("name"))) {
         run.error("name", "name is required", "REQUIRED");
      }

      run.optionalString("description", definition.get("description"));
      run.optionalString("lookup", definition.get("lookup"));
      run.conditions(definition.get("conditions"));
      run.hidden(definition.get("hidden"), stored);
      return run.result();
   }

   private final class Run {
      Run(XDataModel dataModel, Predicate<String> roleExists) {
         this.dataModel = dataModel;
         this.roleExists = roleExists;
      }

      Result result() {
         return new Result(errors, warnings);
      }

      void error(String field, String message, String code) {
         if(errors.size() < MAX_ERRORS) {
            errors.add(new WizDataModelWire.Err(field, message, code));
         }
      }

      void unknownKeys(String path, Map<String, Object> map, Set<String> valid) {
         for(String key : map.keySet()) {
            if(!valid.contains(key)) {
               error(path + "." + key, path + ": unknown key \"" + key + "\"; valid keys: " +
                     String.join(", ", valid), "UNKNOWN_KEY");
            }
         }
      }

      void optionalString(String path, Object value) {
         if(value != null && !(value instanceof String)) {
            error(path, path + " must be a string or null", "TYPE_INVALID");
         }
      }

      // -- conditions ----------------------------------------------------------------------

      void conditions(Object value) {
         if(value == null) {
            return;
         }

         if(!(value instanceof List)) {
            error("conditions", "conditions must be a list", "TYPE_INVALID");
            return;
         }

         Set<String> names = new HashSet<>();
         List<Object> list = list(value);

         for(int i = 0; i < list.size(); i++) {
            Map<String, Object> condition = map(list.get(i));
            String label = "conditions[" + conditionLabel(condition, i) + "]";

            if(condition == null) {
               error(label, label + " must be an object", "TYPE_INVALID");
               continue;
            }

            condition(label, condition, names);
         }
      }

      private String conditionLabel(Map<String, Object> condition, int index) {
         Object name = condition == null ? null : condition.get("name");
         return name instanceof String && !((String) name).isBlank() ? (String) name :
            String.valueOf(index);
      }

      void condition(String path, Map<String, Object> condition, Set<String> names) {
         unknownKeys(path, condition, CONDITION_KEYS);
         Object name = condition.get("name");

         if(!(name instanceof String) || ((String) name).isBlank()) {
            error(path + ".name", "a condition needs a name", "REQUIRED");
         }
         else if(!names.add((String) name)) {
            error(path + ".name", "two conditions are named \"" + name + "\"", "CONDITION_DUPLICATE");
         }

         target(path + ".target", condition.get("target"));
         optionalString(path + ".script", condition.get("script"));
         Object tree = condition.get("tree");
         boolean hasScript = condition.get("script") instanceof String &&
            !((String) condition.get("script")).isBlank();

         if(tree == null) {
            if(!hasScript) {
               error(path + ".tree", "a condition needs a tree or a script", "TREE_SHAPE_INVALID");
            }
         }
         else {
            node(path + ".tree", tree, 0);
         }
      }

      void target(String path, Object value) {
         Map<String, Object> target = map(value);

         if(target == null) {
            error(path, path + " is required: {kind: \"table\"|\"physicalView\", name}", "REQUIRED");
            return;
         }

         unknownKeys(path, target, TARGET_KEYS);
         Object kind = target.get("kind");
         Object name = target.get("name");

         if(!KIND_TABLE.equals(kind) && !KIND_PHYSICAL_VIEW.equals(kind)) {
            error(path + ".kind", path + ".kind: expected table or physicalView, got \"" + kind +
                  "\"", "TARGET_INVALID");
         }

         if(!(name instanceof String) || ((String) name).isBlank()) {
            error(path + ".name", path + ".name is required", "REQUIRED");
            return;
         }

         if(KIND_PHYSICAL_VIEW.equals(kind)) {
            if(dataModel == null || dataModel.getPartition((String) name) == null) {
               error(path + ".name", path + ".name: no physical view \"" + name + "\" in this " +
                     "data source", "TARGET_NOT_FOUND");
            }
         }
         else if(KIND_TABLE.equals(kind)) {
            warn("TARGET_UNVERIFIED: " + path + ".name: table \"" + name +
                 "\" cannot be verified without reading the database metadata");
         }
      }

      // -- tree ----------------------------------------------------------------------------

      void node(String path, Object value, int depth) {
         Map<String, Object> node = map(value);

         if(node == null) {
            error(path, path + " must be a group {op, items} or a clause {left, op, ...}",
                  "TREE_SHAPE_INVALID");
            return;
         }

         if(depth > MAX_DEPTH) {
            error(path, "conditions are nested deeper than " + MAX_DEPTH + " groups",
                  "TREE_SHAPE_INVALID");
            return;
         }

         if(node.containsKey("items")) {
            group(path, node, depth);
         }
         else {
            clause(path, node);
         }
      }

      void group(String path, Map<String, Object> group, int depth) {
         unknownKeys(path, group, GROUP_KEYS);
         Object op = group.get("op");

         if(!(op instanceof String) || (!"and".equalsIgnoreCase((String) op) &&
            !"or".equalsIgnoreCase((String) op)))
         {
            error(path + ".op", path + ".op: expected and or or, got \"" + op + "\"",
                  "OPERATION_INVALID");
         }

         notFlag(path, group.get("not"));

         if(!(group.get("items") instanceof List) || list(group.get("items")).size() < 2) {
            error(path + ".items", path + ".items: a group needs at least 2 items (a single " +
                  "item is the item itself; a negated single clause is {not:true} on the clause)",
                  "TREE_SHAPE_INVALID");
            return;
         }

         List<Object> items = list(group.get("items"));

         for(int i = 0; i < items.size(); i++) {
            node(path + ".items[" + i + "]", items.get(i), depth + 1);
         }
      }

      void notFlag(String path, Object value) {
         if(value != null && !(value instanceof Boolean)) {
            error(path + ".not", path + ".not must be true or false", "TYPE_INVALID");
         }
      }

      void clause(String path, Map<String, Object> clause) {
         unknownKeys(path, clause, CLAUSE_KEYS);
         notFlag(path, clause.get("not"));
         Object opValue = clause.get("op");
         String symbol = null;
         int arity = 0;

         if(!(opValue instanceof String) || ((String) opValue).isBlank()) {
            error(path + ".op", path + ".op is required; valid operators: " + OPERATORS,
                  "OPERATION_INVALID");
         }
         else {
            symbol = canonicalOp((String) opValue);
            arity = arity(symbol);

            if(arity == 0) {
               error(path + ".op", path + ".op: \"" + opValue + "\" is not a condition operator " +
                     "(symbols are matched ignoring case, localized names are not accepted); " +
                     "valid operators: " + OPERATORS, "OPERATION_INVALID");
            }
         }

         // operand presence follows the operator's arity; extras are refused, never dropped
         boolean left = clause.get("left") != null;
         boolean right = clause.get("right") != null;
         boolean right2 = clause.get("right2") != null;

         if(!left) {
            error(path + ".left", path + ".left is required", "REQUIRED");
         }
         else {
            operand(path + ".left", clause.get("left"), "EXISTS".equals(symbol) ||
                    "UNIQUE".equals(symbol));
         }

         if(arity >= 2) {
            if(right) {
               operand(path + ".right", clause.get("right"), false);
            }
            else {
               error(path + ".right", path + ".right is required for " + symbol, "OPERAND_ARITY");
            }
         }
         else if(arity == 1 && right) {
            error(path + ".right", path + ".right is not allowed for " + symbol +
                  " (it takes one operand)", "OPERAND_ARITY");
         }

         if(arity == 3) {
            if(right2) {
               operand(path + ".right2", clause.get("right2"), false);
            }
            else {
               error(path + ".right2", path + ".right2 is required for " + symbol,
                     "OPERAND_ARITY");
            }
         }
         else if(arity > 0 && right2) {
            error(path + ".right2", path + ".right2 is not allowed for " + symbol,
                  "OPERAND_ARITY");
         }

         if(arity == 1 && ("EXISTS".equals(symbol) || "UNIQUE".equals(symbol)) && left) {
            Map<String, Object> operand = map(clause.get("left"));

            if(operand != null && !"subquery".equalsIgnoreCase(string(operand.get("type")))) {
               error(path + ".left.type", path + ".left.type: " + symbol +
                     " needs a subquery operand", "OPERAND_TYPE_INVALID");
            }
         }
      }

      void operand(String path, Object value, boolean subqueryOnly) {
         Map<String, Object> operand = map(value);

         if(operand == null) {
            error(path, path + " must be {type, expression}", "TYPE_INVALID");
            return;
         }

         unknownKeys(path, operand, OPERAND_KEYS);
         Object typeValue = operand.get("type");
         String type = typeValue instanceof String ? ((String) typeValue).toLowerCase() : null;

         if(type == null || !OPERAND_TYPES.contains(type)) {
            error(path + ".type", path + ".type: expected one of " +
                  String.join(", ", OPERAND_TYPES) + ", got \"" + typeValue + "\"",
                  "OPERAND_TYPE_INVALID");
            return;
         }

         if("subquery".equals(type)) {
            subquery(path, operand);
            return;
         }

         if(operand.containsKey("sql")) {
            error(path + ".sql", path + ".sql is only for type subquery", "UNKNOWN_KEY");
         }

         Object expression = operand.get("expression");

         if(!(expression instanceof String)) {
            error(path + ".expression", path + ".expression is required (text)", "REQUIRED");
            return;
         }

         String text = (String) expression;

         switch(type) {
         case "value":
            if(text.startsWith("$(")) {
               error(path + ".expression", path + ".expression: a value starting with \"$(\" " +
                     "is a variable; use type variable or session", "OPERAND_TYPE_INVALID");
            }

            break;
         case "expression":
            if(text.isBlank()) {
               error(path + ".expression", path + ".expression must not be blank", "REQUIRED");
            }
            else if(text.startsWith("$(")) {
               error(path + ".expression", path + ".expression: text starting with \"$(\" is " +
                     "read back as a variable; use type variable or session",
                     "OPERAND_TYPE_INVALID");
            }

            break;
         case "field":
            if(text.isBlank()) {
               error(path + ".expression", path + ".expression must name a column, e.g. " +
                     "ORDERS.REGION", "REQUIRED");
            }

            break;
         case "variable":
            if(text.isBlank() || text.contains("$(") || text.contains(")")) {
               error(path + ".expression", path + ".expression: give the bare variable name, " +
                     "without $( )", "OPERAND_TYPE_INVALID");
            }
            else if(SESSION_NAMES.contains(text)) {
               error(path + ".expression", path + ".expression: " + text + " is session data; " +
                     "use type session", "OPERAND_TYPE_INVALID");
            }

            break;
         case "session":
            if(!SESSION_NAMES.contains(text)) {
               error(path + ".expression", path + ".expression: expected one of " +
                     String.join(", ", SESSION_NAMES) + ", got \"" + text + "\"",
                     "OPERAND_TYPE_INVALID");
            }

            break;
         default:
         }
      }

      void subquery(String path, Map<String, Object> operand) {
         Object sql = operand.get("sql");

         if(operand.containsKey("expression")) {
            error(path + ".expression", path + ".expression is not for type subquery; give sql",
                  "OPERAND_TYPE_INVALID");
         }

         if(!(sql instanceof String) || ((String) sql).isBlank()) {
            error(path + ".sql", path + ".sql is required for a subquery", "REQUIRED");
            return;
         }

         if(subqueryCheck == null) {
            return;
         }

         String problem = subqueryCheck.apply((String) sql);

         if(problem != null) {
            error(path + ".sql", path + ".sql: " + problem, "OPERAND_TYPE_INVALID");
         }
      }

      // -- hidden columns ------------------------------------------------------------------

      void hidden(Object value, VirtualPrivateModel stored) {
         Set<String> keep = storedUnsupported(stored);

         if(value == null) {
            for(String name : keep) {
               unsupportedError("hidden", name, "dropping it by clearing hidden");
            }

            return;
         }

         Map<String, Object> hidden = map(value);

         if(hidden == null) {
            error("hidden", "hidden must be an object or null", "TYPE_INVALID");
            return;
         }

         unknownKeys("hidden", hidden, HIDDEN_KEYS);
         optionalString("hidden.script", hidden.get("script"));
         Object roles = hidden.get("roles");

         if(roles != null && !(roles instanceof List)) {
            error("hidden.roles", "hidden.roles must be a list of role names", "TYPE_INVALID");
         }
         else {
            List<Object> list = list(roles);

            for(int i = 0; i < list.size(); i++) {
               Object role = list.get(i);
               String path = "hidden.roles[" + i + "]";

               if(!(role instanceof String) || ((String) role).isBlank()) {
                  error(path, path + " must be a role name", "TYPE_INVALID");
               }
               else if(roleExists != null && !roleExists.test((String) role)) {
                  warn("ROLE_UNKNOWN: " + path + ": no role \"" + role + "\" exists yet");
               }
            }
         }

         Object columns = hidden.get("columns");
         Set<String> seen = new HashSet<>();
         Set<String> keptUnsupported = new HashSet<>();

         if(columns != null && !(columns instanceof List)) {
            error("hidden.columns", "hidden.columns must be a list of {table, column}",
                  "TYPE_INVALID");
            return;
         }

         List<Object> list = list(columns);

         for(int i = 0; i < list.size(); i++) {
            String path = "hidden.columns[" + i + "]";
            Map<String, Object> column = map(list.get(i));

            if(column == null) {
               error(path, path + " must be {table, column}", "HIDDEN_COLUMN_INVALID");
               continue;
            }

            unknownKeys(path, column, COLUMN_KEYS);
            Object name = column.get("column");
            Object table = column.get("table");

            if(!(name instanceof String) || ((String) name).isBlank()) {
               error(path + ".column", path + ".column is required", "HIDDEN_COLUMN_INVALID");
               continue;
            }

            if(table != null && !(table instanceof String)) {
               error(path + ".table", path + ".table must be a table name or null",
                     "HIDDEN_COLUMN_INVALID");
               continue;
            }

            if(Boolean.TRUE.equals(column.get("unsupported"))) {
               if(!keep.contains(name)) {
                  error(path, path + ": \"" + name + "\" is not a plain table.column reference " +
                        "of the stored model, so it cannot be written; remove the entry",
                        "HIDDEN_COLUMN_INVALID");
               }
               else {
                  keptUnsupported.add((String) name);
               }

               continue;
            }

            if(!seen.add(table + "\u0000" + name)) {
               error(path, path + ": " + (table == null ? "" : table + ".") + name +
                     " is listed twice", "HIDDEN_COLUMN_INVALID");
            }
         }

         for(String name : keep) {
            if(!keptUnsupported.contains(name)) {
               unsupportedError("hidden.columns", name, "changing or dropping it");
            }
         }
      }

      private void unsupportedError(String path, String name, String what) {
         error(path, path + ": the stored hidden column \"" + name + "\" is not a plain " +
               "table.column reference; this tool cannot change it, so " + what + " is refused " +
               "(keep the entry as read, with unsupported:true)", "HIDDEN_COLUMN_INVALID");
      }

      private Set<String> storedUnsupported(VirtualPrivateModel stored) {
         Set<String> names = new LinkedHashSet<>();

         if(stored == null || stored.getHiddenColumns() == null) {
            return names;
         }

         for(Enumeration<DataRef> e = stored.getHiddenColumns().getHiddenColumns();
             e.hasMoreElements();)
         {
            DataRef ref = e.nextElement();

            if(ref != null && plainAttribute(ref) == null) {
               names.add(ref.getName());
            }
         }

         return names;
      }

      private void warn(String warning) {
         if(!warnings.contains(warning)) {
            warnings.add(warning);
         }
      }

      private final XDataModel dataModel;
      private final Predicate<String> roleExists;
      private final List<WizDataModelWire.Err> errors = new ArrayList<>();
      private final List<String> warnings = new ArrayList<>();
   }

   /** 1 for a unary operator, 2 binary, 3 trinary, 0 for anything else. */
   static int arity(String symbol) {
      // isOperator also accepts the localized names; only symbols are valid on the wire
      if(symbol == null || !operatorSymbols().contains(symbol)) {
         return 0;
      }

      if(XUnaryCondition.isOperator(symbol)) {
         return 1;
      }

      if(XBinaryCondition.isOperator(symbol)) {
         return 2;
      }

      return XTrinaryCondition.isOperator(symbol) ? 3 : 0;
   }

   private static boolean isBlank(Object value) {
      return !(value instanceof String) || ((String) value).isBlank();
   }

   /** The operator symbols the server accepts, in the order unary, binary, trinary. */
   static String operators() {
      return String.join(", ", operatorSymbols());
   }

   static final int MAX_ERRORS = 50;
   static final int MAX_DEPTH = 20;
   private static final String OPERATORS = operators();
   private static final List<String> OPERAND_TYPES =
      List.of("field", "value", "expression", "variable", "session", "subquery");
   private static final List<String> SESSION_NAMES = List.of("_USER_", "_GROUPS_", "_ROLES_");
   private static final Set<String> TOP_KEYS = keys("name", "description", "lookup", "conditions",
                                                    "hidden");
   private static final Set<String> CONDITION_KEYS = keys("name", "target", "script", "tree");
   private static final Set<String> TARGET_KEYS = keys("kind", "name");
   private static final Set<String> GROUP_KEYS = keys("op", "not", "items");
   private static final Set<String> CLAUSE_KEYS = keys("left", "op", "right", "right2", "not");
   private static final Set<String> OPERAND_KEYS = keys("type", "expression", "sql");
   private static final Set<String> HIDDEN_KEYS = keys("roles", "script", "columns");
   private static final Set<String> COLUMN_KEYS = keys("table", "column", "unsupported");

   private static Set<String> keys(String... keys) {
      return new LinkedHashSet<>(Arrays.asList(keys));
   }

   private final Function<String, String> subqueryCheck;
}
