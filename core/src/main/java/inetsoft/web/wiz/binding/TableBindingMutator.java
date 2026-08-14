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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.*;

/**
 * Read-modify-write over a crosstab's or table's shelves.
 *
 * <p>Five shelves against the chart's fifteen, so this is the simplest of the binding
 * mutators — but the shelves are <b>not interchangeable between the two object types</b>, and
 * the refusals say which type has which rather than just "unknown shelf".
 *
 * <p>Callers must pass the model {@code VSBindingService.createModel} returned and mutate it
 * in place: {@code setbinding} carries the whole {@code BindingModel}, so a fresh one would
 * drop {@code source}, {@code allRows}, {@code name2Labels}, the option info, and everything
 * else no tool here touches.
 */
public final class TableBindingMutator {
   /** Shelves holding dimensions, per object type. */
   private static final List<String> CROSSTAB_DIMENSION_SHELVES = List.of("rows", "cols");
   private static final List<String> TABLE_DIMENSION_SHELVES = List.of("groups");

   /**
    * Natural spellings an agent is likely to reach for. The canonical names match
    * {@code CrosstabConstants} rather than inventing new ones.
    */
   private static final Map<String, String> ALIASES = Map.of(
      "rowheaders", "rows",
      "row", "rows",
      "columnheaders", "cols",
      "columns", "cols",
      "col", "cols",
      "column", "cols",
      "aggregate", "aggregates",
      "measures", "aggregates",
      "group", "groups",
      "detail", "details");

   private TableBindingMutator() {
   }

   /** Canonical shelf names valid for the given model, in the order the UI presents them. */
   public static List<String> shelvesOf(BaseTableBindingModel model) {
      if(model instanceof CrosstabBindingModel) {
         return List.of("rows", "cols", "aggregates");
      }

      if(model instanceof TableBindingModel) {
         return List.of("groups", "details", "aggregates");
      }

      throw new IllegalArgumentException(
         "Not a table or crosstab binding: " +
         (model == null ? "null" : model.getClass().getSimpleName()) + ".");
   }

   public static String requireShelf(BaseTableBindingModel model, String shelf) {
      String raw = shelf == null ? "" : shelf.trim().toLowerCase();
      String name = ALIASES.getOrDefault(raw, raw);
      List<String> valid = shelvesOf(model);

      if(!valid.contains(name)) {
         String type = model instanceof CrosstabBindingModel ? "crosstab" : "table";
         throw new IllegalArgumentException(
            "Unknown shelf '" + shelf + "' for a " + type + ". Its shelves are: " +
            String.join(", ", valid) + ".");
      }

      return name;
   }

   public static void setShelf(BaseTableBindingModel model, String shelf, List<FieldRef> fields) {
      String name = requireShelf(model, shelf);
      List<FieldRef> refs = fields == null ? List.of() : fields;

      for(FieldRef field : refs) {
         requireCompatible(name, field);
      }

      switch(name) {
         case "rows" -> ((CrosstabBindingModel) model).setRows(dimensions(refs));
         case "cols" -> ((CrosstabBindingModel) model).setCols(dimensions(refs));
         case "groups" -> ((TableBindingModel) model).setGroups(dimensions(refs));
         case "details" -> ((TableBindingModel) model).setDetails(details(refs));
         default -> model.setAggregates(aggregates(refs));
      }

      pruneOrphanedSuppression(model);
   }

   public static void addField(BaseTableBindingModel model, String shelf, FieldRef field,
                               Integer position)
   {
      String name = requireShelf(model, shelf);
      requireCompatible(name, field);
      List<FieldRef> current = new ArrayList<>(read(model, name));
      int index = position == null ? current.size() : position;

      if(index < 0 || index > current.size()) {
         throw new IllegalArgumentException(
            "Position " + position + " is outside the " + name + " shelf, which holds " +
            current.size() + " field(s). Valid positions are 0 to " + current.size() + ".");
      }

      current.add(index, field);
      setShelf(model, name, current);
   }

   public static void removeField(BaseTableBindingModel model, String shelf, String column) {
      String name = requireShelf(model, shelf);
      List<FieldRef> current = new ArrayList<>(read(model, name));
      boolean removed = current.removeIf(
         field -> field.column() != null && field.column().equalsIgnoreCase(column));

      if(!removed) {
         throw new IllegalArgumentException(
            "'" + column + "' is not on the " + name + " shelf. It holds: " +
            (current.isEmpty() ? "(nothing)" : columns(current)) + ".");
      }

      setShelf(model, name, current);
   }

   /**
    * Moves a field between shelves in one write.
    *
    * <p>Its own operation rather than remove-then-add because pivoting a crosstab is the most
    * common table edit, and two calls would mean two checkpoints and an intermediate state
    * the browser renders. Validation happens before either side is touched, so a rejected
    * move leaves the field where it was rather than dropping it.
    */
   public static void moveField(BaseTableBindingModel model, String fromShelf, String toShelf,
                                String column, Integer position)
   {
      String from = requireShelf(model, fromShelf);
      String to = requireShelf(model, toShelf);
      FieldRef field = read(model, from).stream()
         .filter(ref -> ref.column() != null && ref.column().equalsIgnoreCase(column))
         .findFirst()
         .orElseThrow(() -> new IllegalArgumentException(
            "'" + column + "' is not on the " + from + " shelf."));

      requireCompatible(to, field);

      List<FieldRef> source = new ArrayList<>(read(model, from));
      source.removeIf(ref -> ref.column() != null && ref.column().equalsIgnoreCase(column));
      List<FieldRef> target = new ArrayList<>(read(model, to));
      int index = position == null ? target.size() : position;

      if(index < 0 || index > target.size()) {
         throw new IllegalArgumentException(
            "Position " + position + " is outside the " + to + " shelf, which holds " +
            target.size() + " field(s).");
      }

      target.add(index, field);
      setShelf(model, from, source);
      setShelf(model, to, target);
   }

   /** What a shelf currently holds, in the shared vocabulary. */
   public static List<FieldRef> read(BaseTableBindingModel model, String shelf) {
      String name = requireShelf(model, shelf);
      List<? extends DataRefModel> refs = switch(name) {
         case "rows" -> ((CrosstabBindingModel) model).getRows();
         case "cols" -> ((CrosstabBindingModel) model).getCols();
         case "groups" -> ((TableBindingModel) model).getGroups();
         case "details" -> ((TableBindingModel) model).getDetails();
         default -> model.getAggregates();
      };

      List<FieldRef> out = new ArrayList<>();

      if(refs != null) {
         for(DataRefModel ref : refs) {
            out.add(fieldOf(ref, name));
         }
      }

      return out;
   }

   // ── validation ────────────────────────────────────────────────────────────

   /**
    * A shelf holds one kind of thing, and putting the wrong kind on it is the mistake that
    * renders plausibly wrong rather than failing — a measure dropped into rows becomes a
    * grouping column, which produces a real-looking table of the wrong shape.
    */
   private static void requireCompatible(String shelf, FieldRef field) {
      FieldRefFactory.requireType(field);
      boolean measure = FieldRefFactory.MEASURE.equalsIgnoreCase(field.type());

      if("aggregates".equals(shelf) && !measure) {
         throw new IllegalArgumentException(
            "Field '" + field.column() + "' is a dimension, and the aggregates shelf holds " +
            "measures. Put it on a dimension shelf, or set its type to measure with an " +
            "aggregate.");
      }

      if(!"aggregates".equals(shelf) && measure) {
         throw new IllegalArgumentException(
            "Field '" + field.column() + "' is a measure, and the " + shelf + " shelf holds " +
            "dimensions. Measures belong on the aggregates shelf.");
      }

      // A detail row is ungrouped raw data, so an aggregate on it is a category error rather
      // than a value to drop. See docs/superpowers/plans/2026-08-13-needs-your-input.md.
      if("details".equals(shelf) && field.aggregate() != null) {
         throw new IllegalArgumentException(
            "Field '" + field.column() + "' carries an aggregate, and the details shelf holds " +
            "ungrouped columns. Put it on the aggregates shelf instead.");
      }
   }

   // ── conversions ───────────────────────────────────────────────────────────

   private static List<BDimensionRefModel> dimensions(List<FieldRef> fields) {
      List<BDimensionRefModel> out = new ArrayList<>();

      for(FieldRef field : fields) {
         BDimensionRefModel ref = new BDimensionRefModel();
         ref.setName(field.column());
         ref.setColumnValue(field.column());

         if(field.dateLevel() != null) {
            ref.setDateLevel(field.dateLevel());
         }

         out.add(ref);
      }

      return out;
   }

   private static List<BAggregateRefModel> aggregates(List<FieldRef> fields) {
      List<BAggregateRefModel> out = new ArrayList<>();

      for(FieldRef field : fields) {
         BAggregateRefModel ref = new BAggregateRefModel();
         ref.setName(field.column());
         ref.setColumnValue(field.column());

         if(field.aggregate() != null) {
            ref.setFormula(field.aggregate());
         }

         out.add(ref);
      }

      return out;
   }

   /**
    * Detail columns are {@code ColumnRefModel} wrapping an attribute ref — a plainer shape
    * than every other shelf in the plugin.
    */
   private static List<DataRefModel> details(List<FieldRef> fields) {
      List<DataRefModel> out = new ArrayList<>();

      for(FieldRef field : fields) {
         AttributeRefModel attribute = new AttributeRefModel();
         attribute.setName(field.column());
         attribute.setAttribute(field.column());

         ColumnRefModel column = new ColumnRefModel();
         column.setName(field.column());
         column.setAttribute(field.column());
         column.setDataRefModel(attribute);
         column.setVisible(true);
         out.add(column);
      }

      return out;
   }

   private static FieldRef fieldOf(DataRefModel ref, String shelf) {
      FieldRef field = FieldRefFactory.from(ref);

      // A detail column is neither dimension nor measure on the wire, but the shared
      // vocabulary requires a type, so it reads back as the dimension it behaves like.
      if("details".equals(shelf) && field.type() == null) {
         return new FieldRef(field.column(), FieldRefFactory.DIMENSION, null, null, null);
      }

      return field;
   }

   private static String columns(List<FieldRef> fields) {
      List<String> names = new ArrayList<>();

      for(FieldRef field : fields) {
         names.add(field.column());
      }

      return String.join(", ", names);
   }

   /**
    * {@code suppressGroupTotal} is keyed by field name and nothing prunes it, so a removed
    * field leaves an entry that grows the map unboundedly and can resurrect suppression if
    * the name is ever reused. Every write prunes what is no longer bound.
    */
   private static void pruneOrphanedSuppression(BaseTableBindingModel model) {
      if(!(model instanceof CrosstabBindingModel crosstab)) {
         return;
      }

      Hashtable<String, Boolean> suppression = crosstab.getSuppressGroupTotal();

      if(suppression == null || suppression.isEmpty()) {
         return;
      }

      Set<String> bound = new HashSet<>();

      for(String shelf : shelvesOf(model)) {
         for(FieldRef field : read(model, shelf)) {
            if(field.column() != null) {
               bound.add(field.column());
            }
         }
      }

      suppression.keySet().removeIf(key -> !bound.contains(key));
   }
}
