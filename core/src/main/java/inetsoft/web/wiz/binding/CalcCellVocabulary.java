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

import inetsoft.report.CellBinding;
import inetsoft.report.GroupableCellBinding;
import inetsoft.web.binding.model.table.CellBindingInfo;

import java.util.*;

/**
 * The agent-facing calc-table cell vocabulary.
 *
 * <p>Across this binding surface {@code type} now means five unrelated things:
 * the object type on {@code BindingModel}; dimension-or-measure on a field ref;
 * {@code BIND_TEXT}/{@code BIND_COLUMN}/{@code BIND_FORMULA} on a cell; sort direction on
 * {@code OrderModel}; ranking mode on {@code TopNModel}. Three of those are reachable in one
 * {@code set_cell_binding} call.
 *
 * <p>That is the {@code fieldType}-vs-{@code role} defect class at its worst — not one
 * ambiguous key but a family of them, where a plausible wrong guess lands in a valid-looking
 * field and produces a silently wrong table. <b>So this vocabulary does not expose
 * {@code type} or {@code btype} at all</b>: the keys are {@code content}, {@code grouping} and
 * {@code expand}, and supplying any of the ambiguous spellings fails loud naming the right one.
 *
 * <p>{@code role} is refused for the same reason rather than adopted as an alias: it is the
 * wrong key in the recorded {@code fieldConfigs} defect, and giving it a new meaning inside the
 * same plugin family would be its own trap.
 *
 * <p>Integer constants never appear in either direction.
 */
public final class CalcCellVocabulary {
   private static final Map<String, Integer> CONTENT = Map.of(
      "text", CellBinding.BIND_TEXT,
      "column", CellBinding.BIND_COLUMN,
      "formula", CellBinding.BIND_FORMULA);

   private static final Map<String, Integer> GROUPING = Map.of(
      "group", CellBinding.GROUP,
      "detail", CellBinding.DETAIL,
      "summary", CellBinding.SUMMARY);

   private static final Map<String, Integer> EXPAND = Map.of(
      "none", GroupableCellBinding.EXPAND_NONE,
      "vertical", GroupableCellBinding.EXPAND_V,
      "v", GroupableCellBinding.EXPAND_V,
      "horizontal", GroupableCellBinding.EXPAND_H,
      "h", GroupableCellBinding.EXPAND_H);

   /** Keys that mean something else here, mapped to the key the caller meant. */
   private static final Map<String, String> REJECTED = Map.of(
      "type", "content",
      "btype", "grouping",
      "expansion", "expand",
      "role", "content' or 'grouping");

   private CalcCellVocabulary() {
   }

   public static int content(String token) {
      return resolve(CONTENT, token, "content");
   }

   public static int grouping(String token) {
      return resolve(GROUPING, token, "grouping");
   }

   public static int expand(String token) {
      return resolve(EXPAND, token, "expand");
   }

   /**
    * Checks a cell-binding spec before anything is written.
    *
    * <p>An incomplete binding is refused rather than applied: a cell bound to nothing renders
    * blank, which reads as a data problem rather than as the binding error it is.
    */
   public static void validate(Map<String, Object> spec) {
      Map<String, Object> cell = spec == null ? Map.of() : spec;

      for(Map.Entry<String, String> rejected : REJECTED.entrySet()) {
         if(cell.containsKey(rejected.getKey())) {
            throw new IllegalArgumentException(
               "'" + rejected.getKey() + "' is not a calc-table cell key — it means something " +
               "else elsewhere in this plugin, which is exactly why it is refused here. Use '" +
               rejected.getValue() + "'. Cell keys are: content, grouping, expand, field, " +
               "value, formula.");
         }
      }

      String contentToken = str(cell, "content");

      if(contentToken == null) {
         throw new IllegalArgumentException(
            "A cell binding needs a 'content' of " + tokens(CONTENT) + ".");
      }

      int type = content(contentToken);

      if(cell.get("grouping") != null) {
         grouping(str(cell, "grouping"));
      }

      if(cell.get("expand") != null) {
         expand(str(cell, "expand"));
      }

      if(type == CellBinding.BIND_COLUMN && cell.get("field") == null) {
         throw new IllegalArgumentException(
            "A cell with content 'column' needs a 'field' — {column, type}. A cell bound to " +
            "nothing renders blank, which reads as missing data rather than a binding error.");
      }

      if(type == CellBinding.BIND_FORMULA && str(cell, "formula") == null) {
         throw new IllegalArgumentException(
            "A cell with content 'formula' needs a 'formula'. A cell bound to nothing renders " +
            "blank, which reads as missing data rather than a binding error.");
      }

      if(type == CellBinding.BIND_TEXT && str(cell, "value") == null) {
         throw new IllegalArgumentException(
            "A cell with content 'text' needs a 'value' — the literal text to show.");
      }
   }

   /** Renders a cell binding back in tokens. Never emits an integer constant or an alias key. */
   public static Map<String, Object> describe(CellBindingInfo info) {
      if(info == null) {
         return null;
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("content", tokenOf(CONTENT, info.getType()));
      out.put("grouping", tokenOf(GROUPING, info.getBtype()));
      out.put("expand", expandTokenOf(info.getExpansion()));
      out.put("value", info.getValue());
      out.put("formula", info.getFormula());
      out.put("mergeCells", info.getMergeCells());
      out.put("rowGroup", info.getRowGroup());
      out.put("colGroup", info.getColGroup());
      out.put("runtimeName", info.getRuntimeName());
      return out;
   }

   public static List<String> contentTokens() {
      return sorted(CONTENT);
   }

   public static List<String> groupingTokens() {
      return sorted(GROUPING);
   }

   public static List<String> expandTokens() {
      return List.of("none", "vertical", "horizontal");
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private static int resolve(Map<String, Integer> table, String token, String key) {
      String name = token == null ? "" : token.trim().toLowerCase();
      Integer value = table.get(name);

      if(value == null) {
         throw new IllegalArgumentException(
            "'" + token + "' is not a valid '" + key + "'. Valid values: " + tokens(table) +
            ". Integer constants are not accepted — the words are the vocabulary.");
      }

      return value;
   }

   /**
    * Reports an unrecognized constant as itself rather than guessing a token. A guess here
    * would read as fact to whoever consumed it.
    */
   private static String tokenOf(Map<String, Integer> table, int value) {
      for(Map.Entry<String, Integer> entry : table.entrySet()) {
         if(entry.getValue() == value) {
            return entry.getKey();
         }
      }

      return "unknown(" + value + ")";
   }

   private static String expandTokenOf(int value) {
      if(value == GroupableCellBinding.EXPAND_NONE) {
         return "none";
      }

      if(value == GroupableCellBinding.EXPAND_V) {
         return "vertical";
      }

      if(value == GroupableCellBinding.EXPAND_H) {
         return "horizontal";
      }

      return "unknown(" + value + ")";
   }

   private static String tokens(Map<String, Integer> table) {
      return String.join(", ", sorted(table));
   }

   private static List<String> sorted(Map<String, Integer> table) {
      List<String> names = new ArrayList<>(table.keySet());
      Collections.sort(names);
      return names;
   }

   private static String str(Map<String, Object> spec, String key) {
      Object value = spec == null ? null : spec.get(key);
      String text = value == null ? "" : String.valueOf(value).trim();
      return text.isEmpty() ? null : text;
   }
}
