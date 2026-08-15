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
import inetsoft.uql.XConstants;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.model.table.CrosstabOptionInfo;
import inetsoft.web.binding.model.table.TableOptionInfo;
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
            ref.setDateLevel(DateLevels.normalize(field.dateLevel()));
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

   // ── sorting and ranking (2d Phase 2) ──────────────────────────────────────

   /**
    * Applies a sort to one dimension on a shelf.
    *
    * <p>The dimension is found by column name, not by index, for the same reason the sort's own
    * column reference is a name — see {@link DimensionSortRanking}.
    */
   public static void setSort(BaseTableBindingModel model, String shelf, String column,
                              Integer index, DimensionSortRanking.Sort sort)
   {
      DimensionSortRanking.applySort(requireDimension(model, shelf, column, index), sort);
   }

   public static void setRanking(BaseTableBindingModel model, String shelf, String column,
                                 Integer index, DimensionSortRanking.Ranking ranking)
   {
      DimensionSortRanking.applyRanking(requireDimension(model, shelf, column, index), ranking);
   }

   /** The sort and ranking on every dimension of a shelf. */
   public static Map<String, Object> describeSorts(BaseTableBindingModel model, String shelf) {
      String name = requireShelf(model, shelf);
      Map<String, Object> out = new LinkedHashMap<>();

      List<BDimensionRefModel> dimensions = dimensionsOf(model, name);

      for(int i = 0; i < dimensions.size(); i++) {
         BDimensionRefModel dimension = dimensions.get(i);
         String column = dimension.getColumnValue() == null
            ? dimension.getName() : dimension.getColumnValue();

         // A crosstab binds the same column twice on purpose (Year then Quarter), and keying by
         // name alone let the second overwrite the first, so one of the two was simply invisible.
         // The index is appended only when it is needed, to keep the common shape unchanged.
         out.put(occurrences(dimensions, column) > 1 ? column + " [" + i + "]" : column,
                 DimensionSortRanking.describe(dimension));
      }

      return out;
   }

   private static long occurrences(List<BDimensionRefModel> dimensions, String column) {
      return dimensions.stream()
         .map(d -> d.getColumnValue() == null ? d.getName() : d.getColumnValue())
         .filter(value -> value != null && value.equalsIgnoreCase(column))
         .count();
   }

   /**
    * The dimension a call means.
    *
    * <p>A crosstab binds the same column more than once by design — dropping a date column twice
    * is how a Year › Quarter drill is built, and the Composer auto-advances the level on the
    * second drop. Sort and ranking are fields on each ref, so the product itself never addresses
    * a dimension by name; this layer does, and it used to return the FIRST match. Sorting "the
    * quarter one" then quietly sorted the year one and reported success.
    *
    * <p>So a name matching several refs now requires {@code index} — the position on the shelf,
    * which is what {@code suppressGroupTotal} already keys on ({@code ORDER_DATE:rows0}). A name
    * matching exactly one still needs nothing, so the common call is unchanged.
    */
   private static BDimensionRefModel requireDimension(BaseTableBindingModel model, String shelf,
                                                      String column, Integer index)
   {
      String name = requireShelf(model, shelf);

      if("aggregates".equals(name) || "details".equals(name)) {
         throw new IllegalArgumentException(
            "Sorting and ranking apply to dimension shelves. The " + name + " shelf holds " +
            (("aggregates".equals(name)) ? "measures" : "ungrouped columns") + ".");
      }

      List<String> present = new ArrayList<>();
      List<BDimensionRefModel> dimensions = dimensionsOf(model, name);
      Map<Integer, BDimensionRefModel> matches = new LinkedHashMap<>();

      for(int i = 0; i < dimensions.size(); i++) {
         BDimensionRefModel dimension = dimensions.get(i);
         String value = dimension.getColumnValue() == null
            ? dimension.getName() : dimension.getColumnValue();
         present.add(value);

         if(value != null && value.equalsIgnoreCase(column)) {
            matches.put(i, dimension);
         }
      }

      if(index != null) {
         BDimensionRefModel chosen = matches.get(index);

         if(chosen == null) {
            throw new IllegalArgumentException(
               "index " + index + " is not a position of '" + column + "' on the " + name +
               " shelf. It is bound at: " + matches.keySet() + ".");
         }

         return chosen;
      }

      if(matches.size() > 1) {
         StringBuilder candidates = new StringBuilder();

         for(Map.Entry<Integer, BDimensionRefModel> match : matches.entrySet()) {
            String level = match.getValue().getDateLevel();
            candidates.append(candidates.isEmpty() ? "" : ", ")
               .append("index ").append(match.getKey())
               .append(level == null || level.isBlank() ? "" : " (date level " + level + ")");
         }

         throw new IllegalArgumentException(
            "'" + column + "' is bound " + matches.size() + " times on the " + name +
            " shelf, so this call is ambiguous. Pass 'index' to say which: " + candidates + ".");
      }

      if(matches.size() == 1) {
         return matches.values().iterator().next();
      }

      throw new IllegalArgumentException(
         "'" + column + "' is not on the " + name + " shelf. It holds: " +
         (present.isEmpty() ? "(nothing)" : String.join(", ", present)) + ".");
   }

   private static List<BDimensionRefModel> dimensionsOf(BaseTableBindingModel model,
                                                        String shelf)
   {
      List<BDimensionRefModel> dimensions = switch(shelf) {
         case "rows" -> ((CrosstabBindingModel) model).getRows();
         case "cols" -> ((CrosstabBindingModel) model).getCols();
         case "groups" -> ((TableBindingModel) model).getGroups();
         default -> List.of();
      };

      return dimensions == null ? List.of() : dimensions;
   }

   // ── column labels (2d Phase 2) ────────────────────────────────────────────

   /**
    * Header aliases. A label for a column that is not bound would sit in {@code name2Labels}
    * doing nothing, so it is refused with the bound columns listed.
    */
   public static void setColumnLabels(BaseTableBindingModel model, Map<String, String> labels) {
      if(labels == null || labels.isEmpty()) {
         throw new IllegalArgumentException(
            "set_column_labels needs at least one label. To remove one, pass it with an empty " +
            "string.");
      }

      Set<String> bound = new LinkedHashSet<>();

      for(String shelf : shelvesOf(model)) {
         for(FieldRef field : read(model, shelf)) {
            if(field.column() != null) {
               bound.add(field.column());
            }
         }
      }

      for(Map.Entry<String, String> label : labels.entrySet()) {
         if(bound.stream().noneMatch(column -> column.equalsIgnoreCase(label.getKey()))) {
            throw new IllegalArgumentException(
               "'" + label.getKey() + "' is not bound on this assembly, so a label for it would " +
               "never be shown. Bound columns: " +
               (bound.isEmpty() ? "(none)" : String.join(", ", bound)) + ".");
         }
      }

      // name2Labels is read and written by BaseTableBindingModel and by nothing else in the
      // product. Writing here landed nowhere: the header kept its original text, columnLabels
      // read back empty, and the tool still reported success -- the worst of the three possible
      // outcomes, because the caller has no way to tell.
      //
      // A header rename is really a TableDataPath cell override, which needs the rendered table
      // lens to find the header cell and differs between a crosstab and a table. Until that is
      // built, refusing is the honest answer: an agent can then surface a missing capability
      // rather than report a rename that did not happen.
      throw new UnsupportedOperationException(
         "Renaming column headers is not supported yet. The label would be stored somewhere " +
         "nothing reads, so the header would keep its current text while this call reported " +
         "success. Ask for it as a gap rather than working around it.");
   }

   // ── options (2d Phase 3) ──────────────────────────────────────────────────

   /**
    * Crosstab and table options.
    *
    * <p><b>The crosstab totals are string-typed booleans</b> — {@code rowTotalVisibleValue} and
    * friends are dynamic-value strings, not booleans, and StyleBI reads anything that is not
    * {@code "true"} as false. So a caller passing {@code "yes"} would silently turn the total
    * <i>off</i>. Real booleans are normalized to the string form, and any other spelling is
    * refused rather than coerced.
    */
   public static void setOptions(BaseTableBindingModel model, Map<String, Object> options) {
      if(options == null || options.isEmpty()) {
         throw new IllegalArgumentException("set_table_options needs at least one option.");
      }

      if(model instanceof CrosstabBindingModel crosstab) {
         setCrosstabOptions(crosstab, options);
      }
      else if(model instanceof TableBindingModel table) {
         setTableOptions(table, options);
      }
   }

   private static void setCrosstabOptions(CrosstabBindingModel model,
                                          Map<String, Object> options)
   {
      CrosstabOptionInfo info = model.getOption();

      if(info == null) {
         info = new CrosstabOptionInfo();
         model.setOption(info);
      }

      requireKnown(options, List.of("rowTotals", "colTotals", "percentageBy",
                                    "summarySideBySide", "suppressGroupTotal"));

      if(options.containsKey("rowTotals")) {
         info.setRowTotalVisibleValue(stringBoolean(options.get("rowTotals"), "rowTotals"));
      }

      if(options.containsKey("colTotals")) {
         info.setColTotalVisibleValue(stringBoolean(options.get("colTotals"), "colTotals"));
      }

      if(options.containsKey("percentageBy")) {
         info.setPercentageByValue(percentageBy(options.get("percentageBy"), model));
      }

      if(options.containsKey("summarySideBySide")) {
         info.setSummarySideBySide(
            Boolean.parseBoolean(stringBoolean(options.get("summarySideBySide"),
                                               "summarySideBySide")));
      }

      if(options.get("suppressGroupTotal") instanceof Map<?, ?> suppression) {
         for(Map.Entry<?, ?> entry : suppression.entrySet()) {
            model.getSuppressGroupTotal().put(
               String.valueOf(entry.getKey()),
               Boolean.parseBoolean(stringBoolean(entry.getValue(), "suppressGroupTotal")));
         }

         pruneOrphanedSuppression(model);
      }
   }

   private static void setTableOptions(TableBindingModel model, Map<String, Object> options) {
      TableOptionInfo info = model.getOption();

      if(info == null) {
         info = new TableOptionInfo();
         model.setOption(info);
      }

      requireKnown(options, List.of("grandTotal", "distinct"));

      if(options.containsKey("grandTotal")) {
         info.setGrandTotal(
            Boolean.parseBoolean(stringBoolean(options.get("grandTotal"), "grandTotal")));
      }

      if(options.containsKey("distinct")) {
         info.setDistinct(
            Boolean.parseBoolean(stringBoolean(options.get("distinct"), "distinct")));
      }
   }

   /**
    * Percentage-by is positional and only meaningful when the shelf it refers to is populated.
    * By-column with no columns bound renders zeros rather than failing.
    */
   private static String percentageBy(Object raw, CrosstabBindingModel model) {
      String token = raw == null ? "" : String.valueOf(raw).trim().toLowerCase();
      int value = switch(token) {
         case "none" -> XConstants.PERCENTAGE_NONE;
         case "col", "column", "columns" -> XConstants.PERCENTAGE_BY_COL;
         case "row", "rows" -> XConstants.PERCENTAGE_BY_ROW;
         default -> throw new IllegalArgumentException(
            "'percentageBy' must be none, row or col, got '" + raw + "'.");
      };

      if(value == XConstants.PERCENTAGE_BY_COL &&
         (model.getCols() == null || model.getCols().isEmpty()))
      {
         throw new IllegalArgumentException(
            "'percentageBy: col' needs at least one field on the cols shelf. With none bound the " +
            "crosstab renders zeros rather than failing.");
      }

      if(value == XConstants.PERCENTAGE_BY_ROW &&
         (model.getRows() == null || model.getRows().isEmpty()))
      {
         throw new IllegalArgumentException(
            "'percentageBy: row' needs at least one field on the rows shelf. With none bound the " +
            "crosstab renders zeros rather than failing.");
      }

      return String.valueOf(value);
   }

   /**
    * A dynamic-value string that StyleBI reads as a boolean. Anything but "true" reads as false,
    * so "yes" would silently turn a total off — refused rather than coerced.
    */
   private static String stringBoolean(Object raw, String key) {
      if(raw instanceof Boolean value) {
         return String.valueOf(value);
      }

      String text = raw == null ? "" : String.valueOf(raw).trim();

      if("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
         return text.toLowerCase();
      }

      throw new IllegalArgumentException(
         "'" + key + "' must be true or false, got '" + raw + "'. This setting is stored as a " +
         "string that StyleBI reads as a boolean, so a spelling like \"yes\" would read as " +
         "false and silently turn the setting off.");
   }

   private static void requireKnown(Map<String, Object> options, List<String> known) {
      List<String> unknown = new ArrayList<>();

      for(String key : options.keySet()) {
         if(!known.contains(key)) {
            unknown.add(key);
         }
      }

      if(!unknown.isEmpty()) {
         throw new IllegalArgumentException(
            "Unknown option(s) " + unknown + " for this object type. Valid options: " + known +
            ". An unknown option would be accepted and do nothing.");
      }
   }

   /** The option vocabulary, so a caller does not guess which type takes which. */
   public static Map<String, Object> optionVocabulary() {
      return Map.of(
         "crosstab", List.of("rowTotals", "colTotals", "percentageBy", "summarySideBySide",
                             "suppressGroupTotal"),
         "table", List.of("grandTotal", "distinct"),
         "percentageBy", List.of("none", "row", "col"),
         "sortDirections", DimensionSortRanking.sortTokens(),
         "rankingModes", DimensionSortRanking.rankingTokens());
   }
}
