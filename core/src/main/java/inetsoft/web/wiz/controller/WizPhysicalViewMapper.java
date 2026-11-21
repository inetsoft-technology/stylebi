/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.uql.erm.XPartition;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.wiz.controller.WizDataModelErrors.DmFailure;
import inetsoft.web.wiz.controller.WizDataModelWire.Err;

import java.util.*;

/**
 * The declarative physical view of the bridge (design 3.4 and 8): the wire map of a definition
 * to a canonical in-memory {@link View}, and the Portal's {@link PhysicalModelDefinition} of a
 * stored view to the same form. The wire form drops everything that is server state (the
 * runtime id, bounds, columns, path, auto aliases) and keeps only what a model can declare:
 * tables (plain, alias, inline view), joins, connection, folder, description.
 *
 * <p>A table is named by the name the view references it by (what joins use): for a plain
 * table the stored qualified name, for an alias or an inline view its own name.</p>
 */
final class WizPhysicalViewMapper {
   private WizPhysicalViewMapper() {
   }

   enum Kind { PLAIN, ALIAS, VIEW }

   /**
    * A table of a view. {@code oldName} is the rename marker of an alias or inline view and is
    * only ever set on a submitted definition.
    */
   record TableDef(String name, Kind kind, String schema, String catalog, String aliasOf,
                   String sql, String oldName, boolean baseElement)
   {
      TableDef withBase(boolean base) {
         return new TableDef(name, kind, schema, catalog, aliasOf, sql, oldName, base);
      }
   }

   /**
    * A join. In a submitted definition {@code type}, {@code mergingRule}, {@code weak},
    * {@code priority} and {@code cardinality} may be null (unspecified: a stored join keeps its
    * value, a new join gets the Portal default); in a stored view everything but the
    * cardinality of a legacy join is set.
    */
   record JoinDef(String fromTable, String fromColumn, String toTable, String toColumn,
                  String type, String mergingRule, Boolean weak, Integer priority,
                  String cardinality, boolean baseElement)
   {
      /** The unordered pair of endpoints: two joins on the same columns are the same join. */
      String key() {
         String a = fromTable + "." + fromColumn;
         String b = toTable + "." + toColumn;
         return a.compareTo(b) <= 0 ? a + " = " + b : b + " = " + a;
      }

      String label() {
         return fromTable + "." + fromColumn + " -> " + toTable + "." + toColumn;
      }
   }

   record View(String name, String description, String folder, String connection, String parent,
               List<TableDef> tables, List<JoinDef> joins)
   {
   }

   /** A submitted definition and which of its optional members were present. */
   record Desired(View view, boolean hasDescription, boolean hasFolder, boolean hasConnection,
                  boolean hasTables, boolean hasJoins)
   {
   }

   /** The most tables and joins one definition may carry (one write holds the database lock). */
   static final int MAX_TABLES = 200;
   static final int MAX_JOINS = 1000;

   static final Set<String> JOIN_TYPES = Set.of(
      "EQUAL", "LEFT_OUTER", "RIGHT_OUTER", "FULL_OUTER", "GREATER", "LESS", "GREATER_EQUAL",
      "LESS_EQUAL", "NOT_EQUAL");
   static final Set<String> CARDINALITIES = Set.of(
      "ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_ONE", "MANY_TO_MANY");
   static final Set<String> MERGING = Set.of("and", "or");

   private static final Set<String> TOP_KEYS = Set.of(
      "name", "description", "folder", "connection", "parent", "tables", "joins");
   /** Keys of a {@code get} result that an input may carry back and that are ignored. */
   private static final Set<String> TOP_READ_ONLY = Set.of(
      "readOnly", "extended", "warnings", "errorMessage");
   private static final Set<String> TABLE_KEYS = Set.of(
      "name", "schema", "catalog", "aliasOf", "sql", "oldName");
   private static final Set<String> JOIN_KEYS = Set.of(
      "from", "to", "type", "mergingRule", "weak", "priority", "cardinality");
   private static final Set<String> END_KEYS = Set.of("table", "column");

   // -- wire -> view ---------------------------------------------------------------------------

   /**
    * Parses a submitted definition. Every unknown key, wrong type or broken shape is collected
    * with its path; none is dropped.
    *
    * @throws DmFailure {@code INVALID} naming every problem found.
    */
   static Desired parse(Map<String, Object> map) {
      List<Err> errors = new ArrayList<>();
      Desired desired = parse(map, errors);

      if(!errors.isEmpty()) {
         throw invalid(errors);
      }

      return desired;
   }

   static Desired parse(Map<String, Object> map, List<Err> errors) {
      if(map == null) {
         errors.add(new Err("definition", "definition is required", "DEFINITION_REQUIRED"));
         return new Desired(new View(null, null, null, null, null, List.of(), List.of()),
                            false, false, false, false, false);
      }

      for(String key : map.keySet()) {
         if(!TOP_KEYS.contains(key) && !TOP_READ_ONLY.contains(key)) {
            errors.add(unknownKey("definition." + key, TOP_KEYS));
         }
      }

      String name = text(map, "name", "definition.name", errors);
      String description = text(map, "description", "definition.description", errors);
      String folder = text(map, "folder", "definition.folder", errors);
      String connection = text(map, "connection", "definition.connection", errors);
      String parent = text(map, "parent", "definition.parent", errors);
      List<TableDef> tables = new ArrayList<>();
      List<JoinDef> joins = new ArrayList<>();
      boolean hasTables = map.containsKey("tables") && map.get("tables") != null;
      boolean hasJoins = map.containsKey("joins") && map.get("joins") != null;

      if(hasTables) {
         Object value = map.get("tables");

         if(value instanceof List<?> list && list.size() > MAX_TABLES) {
            errors.add(new Err("tables", "a physical view definition takes at most " +
               MAX_TABLES + " tables, got " + list.size() + "; split the view", "LIMIT_EXCEEDED"));
         }
         else if(value instanceof List<?> list) {
            for(int i = 0; i < list.size(); i++) {
               TableDef table = parseTable(list.get(i), i, errors);

               if(table != null) {
                  tables.add(table);
               }
            }
         }
         else {
            errors.add(new Err("definition.tables", "tables must be a list", "TYPE_INVALID"));
         }
      }

      if(hasJoins) {
         Object value = map.get("joins");

         if(value instanceof List<?> list && list.size() > MAX_JOINS) {
            errors.add(new Err("joins", "a physical view definition takes at most " +
               MAX_JOINS + " joins, got " + list.size() + "; split the view", "LIMIT_EXCEEDED"));
         }
         else if(value instanceof List<?> list) {
            for(int i = 0; i < list.size(); i++) {
               JoinDef join = parseJoin(list.get(i), i, errors);

               if(join != null) {
                  joins.add(join);
               }
            }
         }
         else {
            errors.add(new Err("definition.joins", "joins must be a list", "TYPE_INVALID"));
         }
      }

      return new Desired(
         new View(name, description, folder, connection, parent, tables, joins),
         map.containsKey("description"), map.containsKey("folder"),
         map.containsKey("connection"), hasTables, hasJoins);
   }

   @SuppressWarnings("unchecked")
   private static TableDef parseTable(Object item, int index, List<Err> errors) {
      String path = "tables[" + index + "]";

      if(!(item instanceof Map<?, ?>)) {
         errors.add(new Err(path, "a table must be an object", "TYPE_INVALID"));
         return null;
      }

      Map<String, Object> map = (Map<String, Object>) item;
      Object rawName = map.get("name");

      if(rawName instanceof String s && !s.isBlank()) {
         path = "tables[" + s + "]";
      }

      for(String key : map.keySet()) {
         if(!TABLE_KEYS.contains(key) && !"baseElement".equals(key)) {
            errors.add(unknownKey(path + "." + key, TABLE_KEYS));
         }
      }

      String name = text(map, "name", path + ".name", errors);

      if(name == null || name.isBlank()) {
         errors.add(new Err(path + ".name", "a table needs a name", "NAME_INVALID"));
         return null;
      }

      String schema = text(map, "schema", path + ".schema", errors);
      String catalog = text(map, "catalog", path + ".catalog", errors);
      String aliasOf = text(map, "aliasOf", path + ".aliasOf", errors);
      String sql = text(map, "sql", path + ".sql", errors);
      String oldName = text(map, "oldName", path + ".oldName", errors);
      Kind kind;

      if(aliasOf != null) {
         kind = Kind.ALIAS;

         if(sql != null || schema != null || catalog != null) {
            errors.add(new Err(path, "an alias table takes only name and aliasOf (sql, schema " +
               "and catalog belong to the table it aliases)", "TABLE_INVALID"));
         }
      }
      else if(sql != null) {
         kind = Kind.VIEW;

         if(schema != null || catalog != null) {
            errors.add(new Err(path, "an inline view takes only name and sql, not schema or " +
               "catalog", "TABLE_INVALID"));
         }

         if(sql.isBlank()) {
            errors.add(new Err(path + ".sql", "the SQL of an inline view is empty",
                               "SQL_REQUIRED"));
         }
      }
      else {
         kind = Kind.PLAIN;

         if(oldName != null) {
            errors.add(new Err(path + ".oldName", "only an alias or an inline view can be " +
               "renamed; a plain table is named by the database", "TABLE_INVALID"));
         }
      }

      return new TableDef(name, kind, schema, catalog, aliasOf, sql, oldName,
                          Boolean.TRUE.equals(map.get("baseElement")));
   }

   @SuppressWarnings("unchecked")
   private static JoinDef parseJoin(Object item, int index, List<Err> errors) {
      String path = "joins[" + index + "]";

      if(!(item instanceof Map<?, ?>)) {
         errors.add(new Err(path, "a join must be an object", "TYPE_INVALID"));
         return null;
      }

      Map<String, Object> map = (Map<String, Object>) item;

      for(String key : map.keySet()) {
         if(!JOIN_KEYS.contains(key) && !"baseElement".equals(key)) {
            errors.add(unknownKey(path + "." + key, JOIN_KEYS));
         }
      }

      String[] from = endpoint(map.get("from"), path + ".from", errors);
      String[] to = endpoint(map.get("to"), path + ".to", errors);
      String type = text(map, "type", path + ".type", errors);
      String merging = text(map, "mergingRule", path + ".mergingRule", errors);
      String cardinality = text(map, "cardinality", path + ".cardinality", errors);
      Boolean weak = null;
      Integer priority = null;

      if(map.get("weak") != null) {
         if(map.get("weak") instanceof Boolean b) {
            weak = b;
         }
         else {
            errors.add(new Err(path + ".weak", "weak must be true or false", "TYPE_INVALID"));
         }
      }

      if(map.get("priority") != null) {
         if(map.get("priority") instanceof Number n && n.doubleValue() == n.intValue()) {
            priority = n.intValue();
         }
         else {
            errors.add(new Err(path + ".priority", "priority must be an integer",
                               "TYPE_INVALID"));
         }
      }

      if(type != null) {
         type = type.trim().toUpperCase(Locale.ROOT);

         if(!JOIN_TYPES.contains(type)) {
            errors.add(new Err(path + ".type", "type \"" + map.get("type") + "\" is not one of " +
               String.join(", ", new TreeSet<>(JOIN_TYPES)), "TYPE_INVALID"));
         }
      }

      if(merging != null) {
         merging = merging.trim().toLowerCase(Locale.ROOT);

         if(!MERGING.contains(merging)) {
            errors.add(new Err(path + ".mergingRule", "mergingRule \"" + map.get("mergingRule") +
               "\" is not one of and, or", "TYPE_INVALID"));
         }
      }

      if(cardinality != null) {
         cardinality = cardinality.trim().toUpperCase(Locale.ROOT);

         if(!CARDINALITIES.contains(cardinality)) {
            errors.add(new Err(path + ".cardinality", "cardinality \"" + map.get("cardinality") +
               "\" is not one of " + String.join(", ", new TreeSet<>(CARDINALITIES)),
                               "TYPE_INVALID"));
         }
      }

      if(from == null || to == null) {
         return null;
      }

      return new JoinDef(from[0], from[1], to[0], to[1], type, merging, weak, priority,
                         cardinality, Boolean.TRUE.equals(map.get("baseElement")));
   }

   @SuppressWarnings("unchecked")
   private static String[] endpoint(Object value, String path, List<Err> errors) {
      if(!(value instanceof Map<?, ?>)) {
         errors.add(new Err(path, "a join needs {table, column} for both ends",
                            "JOIN_INVALID"));
         return null;
      }

      Map<String, Object> map = (Map<String, Object>) value;

      for(String key : map.keySet()) {
         if(!END_KEYS.contains(key)) {
            errors.add(unknownKey(path + "." + key, END_KEYS));
         }
      }

      String table = text(map, "table", path + ".table", errors);
      String column = text(map, "column", path + ".column", errors);

      if(table == null || table.isBlank() || column == null || column.isBlank()) {
         errors.add(new Err(path, "a join end needs a table and a column", "JOIN_INVALID"));
         return null;
      }

      return new String[] { table, column };
   }

   private static String text(Map<String, Object> map, String key, String path,
                              List<Err> errors)
   {
      Object value = map.get(key);

      if(value == null) {
         return null;
      }

      if(!(value instanceof String)) {
         errors.add(new Err(path, key + " must be text", "TYPE_INVALID"));
         return null;
      }

      return (String) value;
   }

   private static Err unknownKey(String path, Set<String> allowed) {
      return new Err(path, "unknown key; the keys of this object are " +
         String.join(", ", new TreeSet<>(allowed)), "UNKNOWN_KEY");
   }

   static DmFailure invalid(List<Err> errors) {
      StringBuilder message = new StringBuilder("the physical view definition has ")
         .append(errors.size()).append(errors.size() == 1 ? " problem: " : " problems: ");

      for(int i = 0; i < errors.size() && i < 3; i++) {
         Err error = errors.get(i);
         message.append(i > 0 ? "; " : "").append(error.field()).append(": ")
            .append(error.message());
      }

      if(errors.size() > 3) {
         message.append("; ...");
      }

      return new DmFailure(WizDataModelWire.INVALID, errors.get(0).field(), message.toString(),
                           errors, null);
   }

   // -- stored -> view -------------------------------------------------------------------------

   /**
    * The canonical form of a stored view from the Portal's definition of it. Tables are sorted by
    * name and joins by endpoint so the output is stable.
    *
    * @param stored the stored (or runtime) partition, for the description the definition lacks.
    */
   static View fromDto(PhysicalModelDefinition dto, XPartition stored, String parent) {
      List<TableDef> tables = new ArrayList<>();
      List<JoinDef> joins = new ArrayList<>();

      for(PhysicalTableModel table : dto.getTables()) {
         String name = table.getQualifiedName() != null ? table.getQualifiedName() :
            table.getName();

         if(table.getAlias() != null) {
            tables.add(new TableDef(table.getAlias(), Kind.ALIAS, null, null,
                                    table.getAliasSource(), null, null, table.isBaseTable()));
         }
         else if(table.getType() == TableType.VIEW) {
            tables.add(new TableDef(name, Kind.VIEW, null, null, null, table.getSql(), null,
                                    table.isBaseTable()));
         }
         else {
            tables.add(new TableDef(name, Kind.PLAIN, blankToNull(table.getSchema()),
                                    blankToNull(table.getCatalog()), null, null, null,
                                    table.isBaseTable()));
         }

         if(table.getJoins() != null) {
            for(JoinModel join : table.getJoins()) {
               joins.add(new JoinDef(
                  join.getTable(), join.getColumn(), join.getForeignTable(),
                  join.getForeignColumn(), join.getType().name(),
                  join.getMergingRule().getType(), join.isWeak(), join.getOrderPriority(),
                  join.getCardinality() == null ? null : join.getCardinality().name(),
                  join.isBaseJoin()));
            }
         }
      }

      tables.sort(Comparator.comparing(TableDef::name));
      joins.sort(Comparator.comparing(JoinDef::key));
      return new View(dto.getName(), blankToNull(stored == null ? null : stored.getDescription()),
                      blankToNull(dto.getFolder()), blankToNull(dto.getConnection()),
                      blankToNull(parent), tables, joins);
   }

   // -- view -> wire ---------------------------------------------------------------------------

   /** The wire form: no runtime id, no bounds, no columns, no path, no auto aliases. */
   static Map<String, Object> toWire(View view) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("name", view.name());
      putIf(map, "description", view.description());
      putIf(map, "folder", view.folder());
      putIf(map, "connection", view.connection());
      putIf(map, "parent", view.parent());
      List<Map<String, Object>> tables = new ArrayList<>();

      for(TableDef table : view.tables()) {
         Map<String, Object> t = new LinkedHashMap<>();
         t.put("name", table.name());

         switch(table.kind()) {
         case ALIAS -> t.put("aliasOf", table.aliasOf());
         case VIEW -> t.put("sql", table.sql());
         default -> {
            putIf(t, "schema", table.schema());
            putIf(t, "catalog", table.catalog());
         }
         }

         if(table.baseElement()) {
            t.put("baseElement", true);
         }

         tables.add(t);
      }

      List<Map<String, Object>> joins = new ArrayList<>();

      for(JoinDef join : view.joins()) {
         Map<String, Object> j = new LinkedHashMap<>();
         j.put("from", new LinkedHashMap<>(Map.of(
            "table", join.fromTable(), "column", join.fromColumn())));
         j.put("to", new LinkedHashMap<>(Map.of("table", join.toTable(), "column",
                                                join.toColumn())));
         putIf(j, "type", join.type());
         putIf(j, "mergingRule", join.mergingRule());
         putIf(j, "weak", join.weak());
         putIf(j, "priority", join.priority());
         putIf(j, "cardinality", join.cardinality());

         if(join.baseElement()) {
            j.put("baseElement", true);
         }

         joins.add(j);
      }

      map.put("tables", tables);
      map.put("joins", joins);
      return map;
   }

   private static void putIf(Map<String, Object> map, String key, Object value) {
      if(value != null) {
         map.put(key, value);
      }
   }

   static String blankToNull(String value) {
      return value == null || value.isEmpty() ? null : value;
   }

   // -- view -> Portal join model --------------------------------------------------------------

   /** The Portal's join model of a join, with the defaults of a new join where unspecified. */
   static JoinModel toJoinModel(JoinDef join) {
      JoinModel model = new JoinModel();
      model.setTable(join.fromTable());
      model.setColumn(join.fromColumn());
      model.setForeignTable(join.toTable());
      model.setForeignColumn(join.toColumn());
      model.setType(JoinType.valueOf(join.type() == null ? "EQUAL" : join.type()));
      model.setMergingRule(MergingRule.forType(
         join.mergingRule() == null ? "and" : join.mergingRule()));
      model.setWeak(Boolean.TRUE.equals(join.weak()));
      model.setOrderPriority(join.priority() == null ? 0 : join.priority());
      model.setCardinality(join.cardinality() == null ? null :
                              JoinCardinality.valueOf(join.cardinality()));
      return model;
   }
}
