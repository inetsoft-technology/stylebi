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
package inetsoft.web.wiz.viewsheet;

import inetsoft.web.composer.model.vs.*;

import java.util.*;

/**
 * Short names for the deep dialog-model paths.
 *
 * <p>Setting one boolean on a gauge otherwise means writing
 * {@code gaugeGeneralPaneModel.outputGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.visible}.
 * That depth is the entire reason this layer exists.
 *
 * <p>Two invariants hold this honest:
 *
 * <ol>
 *   <li><b>Every declared alias resolves.</b> {@code PropertyAliasesTest} reflects over this
 *       registry and asserts each path exists on its model class. When the composer renames a
 *       pane field the build breaks rather than production.</li>
 *   <li><b>Aliases never widen the surface.</b> An alias is only ever a shorter name for a
 *       path that already exists — nothing here computes, combines, or defaults a value. That
 *       would put property semantics in two places, which is the drift this design prevents.</li>
 * </ol>
 *
 * <p>Resolution order is exact alias → raw dotted path → error. A key that is neither is never
 * dropped.
 */
public final class PropertyAliases {
   /** One assembly type's dialog model and its alias vocabulary. */
   public record TypeAliases(String assemblyType, Class<?> modelClass,
                             Map<String, String> aliases) {}

   private static final Map<String, TypeAliases> REGISTRY = registry();

   private PropertyAliases() {
   }

   public static TypeAliases forType(String assemblyType) {
      TypeAliases entry = REGISTRY.get(normalize(assemblyType));

      if(entry == null) {
         throw new IllegalArgumentException(
            "No property vocabulary for assembly type '" + assemblyType + "' yet. Types " +
            "covered: " + String.join(", ", new TreeSet<>(REGISTRY.keySet())) + ". Raw dotted " +
            "paths are not available for uncovered types either, because the dialog model is " +
            "not known.");
      }

      return entry;
   }

   public static boolean covers(String assemblyType) {
      return REGISTRY.containsKey(normalize(assemblyType));
   }

   public static Set<String> coveredTypes() {
      return Collections.unmodifiableSet(REGISTRY.keySet());
   }

   /**
    * Resolves an agent-supplied key to a model path: exact alias first, then the key as a raw
    * dotted path. A key that is neither throws with near-matches — it is never dropped.
    */
   public static String resolve(String assemblyType, String key) {
      TypeAliases entry = forType(assemblyType);
      String alias = entry.aliases().get(key);

      if(alias != null) {
         return alias;
      }

      if(key != null && key.contains(".")) {
         // A dotted key is the documented raw escape hatch. PropertyPath validates it and
         // fails loud if it does not resolve, so nothing silently no-ops here.
         return key;
      }

      throw new IllegalArgumentException(
         "'" + key + "' is not a property of " + entry.assemblyType() + "." +
         nearest(key, entry.aliases().keySet()) +
         " Known names: " + String.join(", ", new TreeSet<>(entry.aliases().keySet())) +
         ". A raw model path (containing a '.') is also accepted.");
   }

   private static String nearest(String key, Set<String> candidates) {
      String best = null;
      int bestDistance = Integer.MAX_VALUE;

      for(String candidate : candidates) {
         int distance = distance(key == null ? "" : key.toLowerCase(), candidate.toLowerCase());

         if(distance < bestDistance) {
            bestDistance = distance;
            best = candidate;
         }
      }

      return best != null && bestDistance <= 3 ? " Did you mean '" + best + "'?" : "";
   }

   private static int distance(String a, String b) {
      int[] previous = new int[b.length() + 1];
      int[] current = new int[b.length() + 1];

      for(int j = 0; j <= b.length(); j++) {
         previous[j] = j;
      }

      for(int i = 1; i <= a.length(); i++) {
         current[0] = i;

         for(int j = 1; j <= b.length(); j++) {
            int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
            current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                                  previous[j - 1] + cost);
         }

         int[] swap = previous;
         previous = current;
         current = swap;
      }

      return previous[b.length()];
   }

   private static String normalize(String assemblyType) {
      return assemblyType == null ? "" : assemblyType.trim().toLowerCase();
   }

   // ── the registry ──────────────────────────────────────────────────────────

   private static Map<String, TypeAliases> registry() {
      Map<String, TypeAliases> registry = new LinkedHashMap<>();
      register(registry, "gauge", GaugePropertyDialogModel.class, gauge());
      register(registry, "text", TextPropertyDialogModel.class, text());
      return Collections.unmodifiableMap(registry);
   }

   private static void register(Map<String, TypeAliases> registry, String type,
                                Class<?> modelClass, Map<String, String> aliases)
   {
      registry.put(type, new TypeAliases(type, modelClass, Collections.unmodifiableMap(aliases)));
   }

   /** The general pane every output assembly shares, under a given prefix. */
   private static void outputGeneral(Map<String, String> aliases, String prefix) {
      String basic = prefix + ".outputGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel";
      aliases.put("name", basic + ".name");
      aliases.put("visible", basic + ".visible");
      aliases.put("enabled", basic + ".enabled");
      aliases.put("shadow", basic + ".shadow");
      aliases.put("primary", basic + ".primary");
      aliases.put("refresh", basic + ".refresh");
   }

   private static void sizePosition(Map<String, String> aliases, String prefix) {
      String size = prefix + ".sizePositionPaneModel";
      aliases.put("top", size + ".top");
      aliases.put("left", size + ".left");
      aliases.put("width", size + ".width");
      aliases.put("height", size + ".height");
      aliases.put("locked", size + ".locked");
   }

   private static Map<String, String> gauge() {
      Map<String, String> aliases = new LinkedHashMap<>();
      outputGeneral(aliases, "gaugeGeneralPaneModel");
      sizePosition(aliases, "gaugeGeneralPaneModel");
      aliases.put("min", "gaugeGeneralPaneModel.numberRangePaneModel.min");
      aliases.put("max", "gaugeGeneralPaneModel.numberRangePaneModel.max");
      aliases.put("majorIncrement", "gaugeGeneralPaneModel.numberRangePaneModel.majorIncrement");
      aliases.put("minorIncrement", "gaugeGeneralPaneModel.numberRangePaneModel.minorIncrement");
      aliases.put("showValue", "gaugeAdvancedPaneModel.showValue");
      return aliases;
   }

   private static Map<String, String> text() {
      Map<String, String> aliases = new LinkedHashMap<>();
      outputGeneral(aliases, "textGeneralPaneModel");
      sizePosition(aliases, "textGeneralPaneModel");
      aliases.put("alpha", "textGeneralPaneModel.alpha");
      aliases.put("popComponent", "textGeneralPaneModel.popComponent");
      return aliases;
   }
}
