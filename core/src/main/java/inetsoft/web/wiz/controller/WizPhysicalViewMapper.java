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

import java.awt.Rectangle;
import java.util.*;

/**
 * The declarative physical view of the bridge (design 3.4 and 8): the wire map of a definition
 * to a canonical in-memory {@link View}, and the Portal's {@link PhysicalModelDefinition} of a
 * stored view to the same form. The wire form drops everything that is server state (the
 * runtime id, bounds, columns, path) and keeps only what a model can declare: tables (plain,
 * alias, inline view) with the auto aliases of a plain table, joins, connection, folder,
 * description.
 *
 * <p>A table is named by the name the view references it by (what joins use): for a plain
 * table the stored qualified name, for an alias or an inline view its own name.</p>
 */
final class WizPhysicalViewMapper {
   private WizPhysicalViewMapper() {
   }

   enum Kind { PLAIN, ALIAS, VIEW }

   /**
    * An auto alias of a table: for the join between the table and {@code from} the server derives
    * an alias table {@code alias}. {@code prefix} is only meaningful, and only carried, when
    * {@code keepOutgoing} is on (the Portal's Auto Alias dialog enables the prefix input only then).
    */
   record AutoAliasDef(String from, String alias, boolean keepOutgoing, String prefix) {
      /** The comparable form: a prefix that is not used does not count. */
      AutoAliasDef canonical() {
         return new AutoAliasDef(from, alias, keepOutgoing, keepOutgoing ? prefix : null);
      }
   }

   /**
    * A table of a view. {@code oldName} is the rename marker of an alias or inline view and is
    * only ever set on a submitted definition. {@code autoAliases} is the table's auto aliases:
    * null in a submitted definition that does not mention them (the stored ones are kept), a
    * list otherwise (empty removes them all); a stored view always carries a list.
    */
   record TableDef(String name, Kind kind, String schema, String catalog, String aliasOf,
                   String sql, String oldName, boolean baseElement,
                   List<AutoAliasDef> autoAliases, Integer x, Integer y, Integer width,
                   Integer height)
   {
      /**
       * A table without a position or auto aliases. In a submitted definition {@code x} and
       * {@code y} are the position asked for (both or neither); in a stored view they are the
       * stored position, with {@code width} and {@code height}, in the units the view stores
       * (null: not placed).
       */
      TableDef(String name, Kind kind, String schema, String catalog, String aliasOf,
               String sql, String oldName, boolean baseElement)
      {
         this(name, kind, schema, catalog, aliasOf, sql, oldName, baseElement, null, null, null,
              null, null);
      }

      /** A table with auto aliases and no position. */
      TableDef(String name, Kind kind, String schema, String catalog, String aliasOf,
               String sql, String oldName, boolean baseElement, List<AutoAliasDef> autoAliases)
      {
         this(name, kind, schema, catalog, aliasOf, sql, oldName, baseElement, autoAliases, null,
              null, null, null);
      }

      /** A table with a position and no auto aliases. */
      TableDef(String name, Kind kind, String schema, String catalog, String aliasOf,
               String sql, String oldName, boolean baseElement, Integer x, Integer y,
               Integer width, Integer height)
      {
         this(name, kind, schema, catalog, aliasOf, sql, oldName, baseElement, null, x, y, width,
              height);
      }

      TableDef withNames(String newName, String newAliasOf, List<AutoAliasDef> newAutoAliases) {
         return new TableDef(newName, kind, schema, catalog, newAliasOf, sql, oldName,
                             baseElement, newAutoAliases, x, y, width, height);
      }

      TableDef withBase(boolean base) {
         return new TableDef(name, kind, schema, catalog, aliasOf, sql, oldName, base,
                             autoAliases, x, y, width, height);
      }

      boolean hasPosition() {
         return x != null && y != null;
      }
   }

   /**
    * An Auto Layout request: the Portal's "vertical" (default) or "horizontal" arrangement,
    * {@code colPriority} being the horizontal one.
    */
   record AutoLayout(boolean colPriority) {
   }

   /** The diagram size of the edit session an Auto Layout wraps to; never stored. */
   record Viewport(int width, int height) {
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
                  boolean hasTables, boolean hasJoins, AutoLayout autoLayout, Viewport viewport)
   {
      Desired(View view, boolean hasDescription, boolean hasFolder, boolean hasConnection,
              boolean hasTables, boolean hasJoins)
      {
         this(view, hasDescription, hasFolder, hasConnection, hasTables, hasJoins, null, null);
      }
   }

   /**
    * The most OWN tables and joins (not inherited from a base view) a definition may carry, one
    * write holds the database lock. {@link WizPhysicalViewDiff#plan} applies it against the stored
    * view: an update that does not grow a count is never refused for it.
    */
   static final int MAX_TABLES = 200;
   static final int MAX_JOINS = 1000;
   /** Parsing refuses a list this long outright (a stored view above the cap is far below it). */
   static final int PARSE_CEILING_TABLES = 5000;
   static final int PARSE_CEILING_JOINS = 20000;

   static final Set<String> JOIN_TYPES = Set.of(
      "EQUAL", "LEFT_OUTER", "RIGHT_OUTER", "FULL_OUTER", "GREATER", "LESS", "GREATER_EQUAL",
      "LESS_EQUAL", "NOT_EQUAL");
   static final Set<String> CARDINALITIES = Set.of(
      "ONE_TO_ONE", "ONE_TO_MANY", "MANY_TO_ONE", "MANY_TO_MANY");
   static final Set<String> MERGING = Set.of("and", "or");

   private static final Set<String> TOP_KEYS = Set.of(
      "name", "description", "folder", "connection", "parent", "tables", "joins", "autoLayout",
      "viewport");
   /** Keys of a {@code get} result that an input may carry back and that are ignored. */
   private static final Set<String> TOP_READ_ONLY = Set.of(
      "readOnly", "extended", "warnings", "errorMessage");
   private static final Set<String> TABLE_KEYS = Set.of(
      "name", "schema", "catalog", "aliasOf", "sql", "oldName", "autoAliases", "x", "y",
      "bounds");
   private static final Set<String> AUTO_ALIAS_KEYS = Set.of(
      "from", "alias", "keepOutgoing", "prefix");
   /** Natural spellings of a table position, folded into x and y. */
   private static final Set<String> TABLE_POSITION_ALIASES = Set.of("left", "top", "position");
   private static final Set<String> BOUNDS_KEYS = Set.of("x", "y", "width", "height");
   private static final Set<String> VIEWPORT_KEYS = Set.of("width", "height");
   /** Alternative spellings of the top-level autoLayout key. */
   private static final Set<String> AUTO_LAYOUT_ALIASES = Set.of("auto_layout", "autolayout");
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
         if(!TOP_KEYS.contains(key) && !TOP_READ_ONLY.contains(key) &&
            !AUTO_LAYOUT_ALIASES.contains(key))
         {
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

         if(value instanceof List<?> list && list.size() > PARSE_CEILING_TABLES) {
            errors.add(new Err("tables", "a physical view definition takes at most " +
               PARSE_CEILING_TABLES + " tables, got " + list.size(), "LIMIT_EXCEEDED"));
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

         if(value instanceof List<?> list && list.size() > PARSE_CEILING_JOINS) {
            errors.add(new Err("joins", "a physical view definition takes at most " +
               PARSE_CEILING_JOINS + " joins, got " + list.size(), "LIMIT_EXCEEDED"));
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

      AutoLayout autoLayout = parseAutoLayout(map, errors);
      Viewport viewport = parseViewport(map.get("viewport"), errors);

      if(viewport != null && autoLayout == null) {
         errors.add(new Err("definition.viewport", "viewport is the diagram size an Auto " +
            "Layout wraps to and is used only with autoLayout; it is not stored", "TYPE_INVALID"));
      }

      return new Desired(
         new View(name, description, folder, connection, parent, tables, joins),
         map.containsKey("description"), map.containsKey("folder"),
         map.containsKey("connection"), hasTables, hasJoins, autoLayout, viewport);
   }

   /**
    * {@code autoLayout}: true or "vertical" (the Portal default), "horizontal", or false/absent
    * for none. {@code auto_layout} is the same key.
    */
   private static AutoLayout parseAutoLayout(Map<String, Object> map, List<Err> errors) {
      String key = null;

      for(String k : map.keySet()) {
         if(("autoLayout".equals(k) || AUTO_LAYOUT_ALIASES.contains(k)) && map.get(k) != null) {
            if(key != null) {
               errors.add(new Err("definition." + k, "autoLayout is given twice (" + key +
                  " and " + k + ")", "TYPE_INVALID"));
            }
            else {
               key = k;
            }
         }
      }

      Object value = key == null ? null : map.get(key);

      if(value == null || Boolean.FALSE.equals(value)) {
         return null;
      }

      if(Boolean.TRUE.equals(value)) {
         return new AutoLayout(false);
      }

      if(value instanceof String text) {
         String mode = text.trim().toLowerCase(Locale.ROOT);

         if(mode.equals("vertical")) {
            return new AutoLayout(false);
         }

         if(mode.equals("horizontal")) {
            return new AutoLayout(true);
         }
      }

      errors.add(new Err("definition." + key, "autoLayout must be true, false, \"vertical\" " +
         "or \"horizontal\", got " + value, "TYPE_INVALID"));
      return null;
   }

   @SuppressWarnings("unchecked")
   private static Viewport parseViewport(Object value, List<Err> errors) {
      if(value == null) {
         return null;
      }

      if(!(value instanceof Map<?, ?>)) {
         errors.add(new Err("definition.viewport", "viewport must be {width, height}",
                            "TYPE_INVALID"));
         return null;
      }

      Map<String, Object> map = (Map<String, Object>) value;

      for(String key : map.keySet()) {
         if(!VIEWPORT_KEYS.contains(key)) {
            errors.add(unknownKey("definition.viewport." + key, VIEWPORT_KEYS));
         }
      }

      Integer width = coordinate(map.get("width"), "definition.viewport.width", 1, errors);
      Integer height = coordinate(map.get("height"), "definition.viewport.height", 1, errors);

      if(map.get("width") == null || map.get("height") == null) {
         errors.add(new Err("definition.viewport", "viewport needs both width and height",
                            "TYPE_INVALID"));
         return null;
      }

      return width == null || height == null ? null : new Viewport(width, height);
   }

   /** A whole number of at least {@code min}; null (with an error) when it is none. */
   private static Integer coordinate(Object value, String path, int min, List<Err> errors) {
      if(value == null) {
         return null;
      }

      if(value instanceof Number n && !Double.isNaN(n.doubleValue()) &&
         !Double.isInfinite(n.doubleValue()) && n.doubleValue() == Math.rint(n.doubleValue()) &&
         n.doubleValue() <= Integer.MAX_VALUE && n.doubleValue() >= min)
      {
         return n.intValue();
      }

      errors.add(new Err(path, path.substring(path.lastIndexOf('.') + 1) + " must be a whole " +
         "number of at least " + min, "TYPE_INVALID"));
      return null;
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
         if(!TABLE_KEYS.contains(key) && !TABLE_POSITION_ALIASES.contains(key) &&
            !"baseElement".equals(key))
         {
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

      List<AutoAliasDef> autoAliases = parseAutoAliases(map.get("autoAliases"),
                                                        path + ".autoAliases", errors);
      int[] position = position(map, path, errors);

      return new TableDef(name, kind, schema, catalog, aliasOf, sql, oldName,
                          Boolean.TRUE.equals(map.get("baseElement")), autoAliases,
                          position == null ? null : position[0],
                          position == null ? null : position[1], null, null);
   }

   /** Null when the key is absent (or null); a list, possibly empty, when it is sent. */
   @SuppressWarnings("unchecked")
   private static List<AutoAliasDef> parseAutoAliases(Object value, String path,
                                                      List<Err> errors)
   {
      if(value == null) {
         return null;
      }

      if(!(value instanceof List<?> list)) {
         errors.add(new Err(path, "autoAliases must be a list of {from, alias, keepOutgoing, " +
            "prefix} (an empty list removes the auto aliases of the table)", "TYPE_INVALID"));
         return null;
      }

      List<AutoAliasDef> result = new ArrayList<>();

      for(int i = 0; i < list.size(); i++) {
         String itemPath = path + "[" + i + "]";

         if(!(list.get(i) instanceof Map<?, ?>)) {
            errors.add(new Err(itemPath, "an auto alias must be an object {from, alias, " +
               "keepOutgoing, prefix}", "TYPE_INVALID"));
            continue;
         }

         Map<String, Object> map = (Map<String, Object>) list.get(i);

         for(String key : map.keySet()) {
            if(!AUTO_ALIAS_KEYS.contains(key)) {
               errors.add(unknownKey(itemPath + "." + key, AUTO_ALIAS_KEYS));
            }
         }

         String from = text(map, "from", itemPath + ".from", errors);
         String alias = text(map, "alias", itemPath + ".alias", errors);
         String prefix = text(map, "prefix", itemPath + ".prefix", errors);
         boolean keep = false;

         if(map.get("keepOutgoing") != null) {
            if(map.get("keepOutgoing") instanceof Boolean b) {
               keep = b;
            }
            else {
               errors.add(new Err(itemPath + ".keepOutgoing",
                                  "keepOutgoing must be true or false", "TYPE_INVALID"));
            }
         }

         if(from == null || from.isBlank()) {
            errors.add(new Err(itemPath + ".from", "an auto alias needs from: the name of the " +
               "table joined to this one that the alias is derived for", "AUTO_ALIAS_INVALID"));
         }

         if(alias == null || alias.isBlank()) {
            errors.add(new Err(itemPath + ".alias", "an auto alias needs an alias name",
                               "AUTO_ALIAS_INVALID"));
         }

         if(keep && (prefix == null || prefix.isBlank())) {
            errors.add(new Err(itemPath + ".prefix", "keepOutgoing needs a prefix (the prefix " +
               "of the kept outgoing columns)", "AUTO_ALIAS_INVALID"));
         }
         else if(!keep && prefix != null && !prefix.isBlank()) {
            errors.add(new Err(itemPath + ".prefix", "prefix applies only with keepOutgoing " +
               "true; remove it or set keepOutgoing", "AUTO_ALIAS_INVALID"));
         }

         result.add(new AutoAliasDef(from, alias, keep, keep ? prefix : null));
      }

      return result;
   }

   /**
    * The position a table is asked to take, in stored units: {@code x}/{@code y}, the spellings
    * {@code left}/{@code top} and {@code position: {x, y}}, or the {@code bounds} of a {@code get}
    * result fed back (its width and height are read-only and ignored). Both coordinates or none;
    * spellings that disagree are refused rather than one of them dropped.
    */
   @SuppressWarnings("unchecked")
   private static int[] position(Map<String, Object> map, String path, List<Err> errors) {
      Integer x = null;
      Integer y = null;
      boolean bad = false;
      String[][] sources = {{"x", "x"}, {"y", "y"}, {"left", "x"}, {"top", "y"}};

      for(String[] source : sources) {
         if(map.get(source[0]) == null) {
            continue;
         }

         Integer value = coordinate(map.get(source[0]), path + "." + source[0], 0, errors);

         if(value == null) {
            bad = true;
            continue;
         }

         Integer previous = source[1].equals("x") ? x : y;

         if(previous != null && !previous.equals(value)) {
            errors.add(new Err(path + "." + source[0], source[0] + " disagrees with the " +
               source[1] + " given for the same table", "TABLE_INVALID"));
            bad = true;
         }

         if(source[1].equals("x")) {
            x = value;
         }
         else {
            y = value;
         }
      }

      for(String key : List.of("position", "bounds")) {
         Object value = map.get(key);

         if(value == null) {
            continue;
         }

         if(!(value instanceof Map<?, ?>)) {
            errors.add(new Err(path + "." + key, key + " must be an object with x and y",
                               "TYPE_INVALID"));
            bad = true;
            continue;
         }

         Map<String, Object> box = (Map<String, Object>) value;
         Set<String> allowed = key.equals("position") ? Set.of("x", "y") : BOUNDS_KEYS;

         for(String k : box.keySet()) {
            if(!allowed.contains(k)) {
               errors.add(unknownKey(path + "." + key + "." + k, allowed));
               bad = true;
            }
         }

         Integer bx = coordinate(box.get("x"), path + "." + key + ".x", 0, errors);
         Integer by = coordinate(box.get("y"), path + "." + key + ".y", 0, errors);
         bad |= (box.get("x") != null && bx == null) || (box.get("y") != null && by == null);

         if((box.get("width") != null && !(box.get("width") instanceof Number)) ||
            (box.get("height") != null && !(box.get("height") instanceof Number)))
         {
            errors.add(new Err(path + "." + key, "width and height must be numbers",
                               "TYPE_INVALID"));
            bad = true;
         }

         if((bx != null && x != null && !bx.equals(x)) ||
            (by != null && y != null && !by.equals(y)))
         {
            errors.add(new Err(path + "." + key, key + " disagrees with the x/y given for the " +
               "same table", "TABLE_INVALID"));
            bad = true;
         }

         x = x == null ? bx : x;
         y = y == null ? by : y;
      }

      if(bad) {
         return null;
      }

      if((x == null) != (y == null)) {
         errors.add(new Err(path + (x == null ? ".x" : ".y"), "a position needs both x and y " +
            "(stored units, from the top left of the diagram)", "TABLE_INVALID"));
         return null;
      }

      return x == null ? null : new int[] { x, y };
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
         List<AutoAliasDef> autoAliases = selectedAutoAliases(table);

         Rectangle b = table.getBounds();
         // a table never placed is stored as -1,-1,-1,-1
         boolean placed = b != null && b.x >= 0 && b.y >= 0;
         Integer x = placed ? b.x : null;
         Integer y = placed ? b.y : null;
         Integer w = placed && b.width >= 0 ? b.width : null;
         Integer h = placed && b.height >= 0 ? b.height : null;

         if(table.getAlias() != null) {
            tables.add(new TableDef(table.getAlias(), Kind.ALIAS, null, null,
                                    table.getAliasSource(), null, null, table.isBaseTable(),
                                    autoAliases, x, y, w, h));
         }
         else if(table.getType() == TableType.VIEW) {
            tables.add(new TableDef(name, Kind.VIEW, null, null, null, table.getSql(), null,
                                    table.isBaseTable(), autoAliases, x, y, w, h));
         }
         else {
            tables.add(new TableDef(name, Kind.PLAIN, blankToNull(table.getSchema()),
                                    blankToNull(table.getCatalog()), null, null, null,
                                    table.isBaseTable(), autoAliases, x, y, w, h));
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

   /**
    * The auto aliases that are set on a table: the selected rows of the Portal's list (its
    * unselected rows are only candidates, the join neighbours), sorted by the table they are
    * derived for.
    */
   private static List<AutoAliasDef> selectedAutoAliases(PhysicalTableModel table) {
      List<AutoAliasDef> result = new ArrayList<>();

      for(AutoAliasJoinModel row : table.getAutoAliases()) {
         if(row.isSelected()) {
            result.add(new AutoAliasDef(row.getForeignTable(), row.getAlias(),
                                        row.isKeepOutgoing(),
                                        blankToNull(row.getPrefix())).canonical());
         }
      }

      result.sort(Comparator.comparing(AutoAliasDef::from));
      return result;
   }

   // -- view -> wire ---------------------------------------------------------------------------

   /**
    * The wire form: no runtime id, no columns, no path. A placed table carries its
    * {@code bounds} in stored units ({@code x}, {@code y}, {@code width}, {@code height}); the
    * diagram size is editor session state and is not in a stored view, so it is not here.
    */
   static Map<String, Object> toWire(View view) {
      return toWire(view, true);
   }

   /**
    * The form a revision is taken over: the wire form without the layout, so that moving a table
    * (in the Portal, or here) does not make an edit planned from an earlier read stale.
    */
   static Map<String, Object> toRevisionForm(View view) {
      return toWire(view, false);
   }

   private static Map<String, Object> toWire(View view, boolean layout) {
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

         if(table.autoAliases() != null && !table.autoAliases().isEmpty()) {
            List<Map<String, Object>> autoAliases = new ArrayList<>();

            for(AutoAliasDef autoAlias : table.autoAliases()) {
               Map<String, Object> a = new LinkedHashMap<>();
               a.put("from", autoAlias.from());
               a.put("alias", autoAlias.alias());
               a.put("keepOutgoing", autoAlias.keepOutgoing());
               putIf(a, "prefix", autoAlias.keepOutgoing() ? autoAlias.prefix() : null);
               autoAliases.add(a);
            }

            t.put("autoAliases", autoAliases);
         }

         if(table.baseElement()) {
            t.put("baseElement", true);
         }

         if(layout && table.hasPosition()) {
            Map<String, Object> bounds = new LinkedHashMap<>();
            bounds.put("x", table.x());
            bounds.put("y", table.y());
            putIf(bounds, "width", table.width());
            putIf(bounds, "height", table.height());
            t.put("bounds", bounds);
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
