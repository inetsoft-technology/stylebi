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
package inetsoft.web.wiz.pairing;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The parsed {@code name} of a {@code dynamicValue} context ({@link EditorContext#DYNAMIC_VALUE}):
 * which dynamic value of an assembly's dialog the session is paired at, in the plugin's own
 * vocabulary.
 *
 * <p>Grammar: a bare {@code key} is a {@code set_assembly_properties} key (namespace
 * {@link #PROP}); anything else is {@code namespace:key}, the namespace naming the plugin tool
 * whose parameter the key is. One spelling per target: the grant and every target are compared by
 * exact string, so the explicit {@code prop:} prefix is refused. Per-field and per-cell values are
 * addressed by 0-based POSITION ({@code x[0].column}, {@code customPeriods[1].start}), never by
 * column name, because the field being switched to Expression is the thing that names the column.
 *
 * <p>This class decides only whether a name is well formed. Whether the key exists for the
 * concrete assembly type is {@link DynamicValueVocabulary}'s job. Pure and static, so it needs no
 * Spring bean (the deployed instance runs AOT: a new bean needs the {@code server} module rebuilt).
 */
public record DynamicValueName(String namespace, String key) {
   /** The default namespace: a {@code set_assembly_properties} alias or raw dotted path. */
   public static final String PROP = "prop";
   public static final String SOURCE = "source";
   public static final String BINDING = "binding";
   public static final String TABLE_OPTION = "tableOption";
   public static final String DATE_COMPARISON = "dateComparison";
   public static final String CHART_TARGET = "chartTarget";
   public static final String CHART_REGION = "chartRegion";

   /** Reserved until a format batch defines it: refused rather than treated as unknown. */
   private static final String FORMAT = "format";

   public static final int MAX_LENGTH = 200;

   /** The name as it travels on the wire: the bare key for {@link #PROP}, else namespace:key. */
   public String wire() {
      return PROP.equals(namespace) ? key : namespace + ":" + key;
   }

   /** The parsed name, or {@code null} when {@link #problem} says it is malformed. */
   public static DynamicValueName parse(String name) {
      if(problem(name) != null) {
         return null;
      }

      int colon = name.indexOf(':');

      return colon < 0 ? new DynamicValueName(PROP, name)
         : new DynamicValueName(name.substring(0, colon), name.substring(colon + 1));
   }

   /** Why {@code name} is not a well-formed dynamic value name, or {@code null} when it is. */
   public static String problem(String name) {
      if(name == null || name.isBlank()) {
         return "name is required";
      }

      if(name.length() > MAX_LENGTH) {
         return "name is longer than " + MAX_LENGTH + " characters";
      }

      for(int i = 0; i < name.length(); i++) {
         char c = name.charAt(i);

         if(Character.isWhitespace(c)) {
            return "name must not contain whitespace";
         }

         if(Character.isISOControl(c)) {
            return "name must not contain control characters";
         }
      }

      int colon = name.indexOf(':');

      if(colon < 0) {
         return PROP_KEY.matcher(name).matches() ? null : malformed(name, PROP, PROP_GRAMMAR);
      }

      String namespace = name.substring(0, colon);
      String key = name.substring(colon + 1);

      if(PROP.equals(namespace)) {
         return "one spelling per target: drop the 'prop:' prefix, a bare name is a " +
            "set_assembly_properties key";
      }

      if(FORMAT.equals(namespace)) {
         return "namespace 'format' is not available yet";
      }

      Grammar grammar = GRAMMARS.get(namespace);

      if(grammar == null) {
         return "unknown namespace '" + namespace + "' in '" + name + "'; expected a bare " +
            "set_assembly_properties key or one of " + String.join(", ", GRAMMARS.keySet()) + ":";
      }

      return grammar.pattern().matcher(key).matches() ? null
         : malformed(name, namespace, grammar.description());
   }

   private static String malformed(String name, String namespace, String expected) {
      return "'" + name + "' is not a valid " + namespace + " name: expected " + expected;
   }

   private record Grammar(Pattern pattern, String description) {
   }

   private static final String INDEX = "(?:0|[1-9][0-9]{0,3})";
   private static final String IDENT = "[A-Za-z][A-Za-z0-9_]*";

   private static final Pattern PROP_KEY = Pattern.compile(
      IDENT + "(?:\\." + IDENT + ")*|rangeValues\\[" + INDEX + "\\]");
   private static final String PROP_GRAMMAR =
      "an alias, a dotted raw path, or rangeValues[<index>]";

   private static String alternatives(List<String> keys) {
      return String.join("|", keys.stream().map(Pattern::quote).toList());
   }

   private static Grammar oneOf(List<String> keys) {
      return new Grammar(Pattern.compile(alternatives(keys)), "one of " + String.join(", ", keys));
   }

   /** Namespace -> key grammar; ordered so the "expected one of" list is stable. */
   private static final Map<String, Grammar> GRAMMARS = grammars();

   private static Map<String, Grammar> grammars() {
      Map<String, Grammar> m = new java.util.LinkedHashMap<>();

      m.put(SOURCE, oneOf(List.of("measure", "parentIdColumn", "idColumn", "labelColumn")));

      List<String> bindingFields = List.of(
         "column", "dateLevel", "aggregate", "secondaryColumn", "n", "sort.sortByField",
         "ranking.mode", "ranking.n", "ranking.measure", "percentage",
         "calculateInfo.percentageDirection");
      m.put(BINDING, new Grammar(
         Pattern.compile("[A-Za-z][A-Za-z0-9]*\\[" + INDEX + "\\]\\.(?:" +
                         alternatives(bindingFields) + ")"),
         "<shelf>[<index>].<field> with field one of " + String.join(", ", bindingFields)));

      m.put(TABLE_OPTION, oneOf(List.of("rowTotals", "colTotals")));

      List<String> dateKeys = List.of(
         "periodsBack", "level", "endDate", "interval", "intervalGranularity",
         "intervalContextLevel", "intervalEndDate");
      m.put(DATE_COMPARISON, new Grammar(
         Pattern.compile(alternatives(dateKeys) + "|customPeriods\\[" + INDEX +
                         "\\]\\.(?:start|end)"),
         "one of " + String.join(", ", dateKeys) +
            ", customPeriods[<index>].start, customPeriods[<index>].end"));

      m.put(CHART_TARGET, oneOf(List.of(
         "line.value", "line.label", "band.from", "band.to", "band.fromLabel", "band.toLabel",
         "statistics.statisticValue", "statistics.percentageOf", "statistics.label")));

      m.put(CHART_REGION, new Grammar(
         Pattern.compile("title/(?:x|x2|y|y2)/title|legend/" + INDEX + "/title"),
         "title/<x|x2|y|y2>/title or legend/<index>/title"));

      return java.util.Collections.unmodifiableMap(m);
   }
}
