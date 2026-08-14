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

import inetsoft.web.binding.model.graph.aesthetic.*;

import java.util.*;

/**
 * The agent-facing visual-frame vocabulary.
 *
 * <p>{@code VisualFrameModel} discriminates its subtypes by fully-qualified Java class name:
 *
 * <pre>{@code @JsonTypeInfo(use = Id.CLASS, ..., property = "clazz")}</pre>
 *
 * <p>No agent should be asked to produce {@code
 * inetsoft.web.binding.model.graph.aesthetic.CategoricalColorModel}, and any that guesses will
 * guess wrong. This table is the only way in, and {@link #describe} is the only way out — an
 * FQCN never reaches the caller in either direction.
 *
 * <p>The 64 classes in {@code model/graph/aesthetic} are mostly not distinct behaviour: 27 of
 * them are named colour ramps that declare no fields at all and differ only in
 * {@code createVisualFrame()}. Those collapse to one alias — {@code {type: "palette", palette:
 * "<name>"}} — leaving a handful of real frame types.
 *
 * <p><b>Classes are referenced by class literal, never by name string</b>, so an upstream
 * rename or repackage breaks the build rather than surfacing as a runtime resolution failure
 * against a live viewsheet.
 */
public final class VisualFrameAliases {
   /**
    * The 27 named colour ramps. Each is a fieldless subclass, so the whole family needs one
    * alias plus this registry to make the names discoverable rather than guessable.
    */
   public static final Map<String, Class<? extends ColorFrameModel>> PALETTES = palettes();

   /**
    * Behavioural colour frames this phase builds. The remaining ones — HSL, brightness,
    * saturation, bipolar, circular, rainbow, heat — arrive in Phase 2.
    */
   private static final Map<String, Class<? extends ColorFrameModel>> COLOR_TYPES = Map.of(
      "static", StaticColorModel.class,
      "categorical", CategoricalColorModel.class,
      "gradient", GradientColorModel.class);

   /**
    * Subclasses deliberately not reachable through an alias, each with its reason. The
    * coverage test reads this list, so excluding something is a recorded decision rather than
    * an oversight.
    */
   private static final Map<Class<?>, String> EXCLUDED = Map.of(
      HSLColorModel.class, "abstract base of Brightness and Saturation",
      BrightnessColorModel.class, "Phase 2 — behavioural colour frames",
      SaturationColorModel.class, "Phase 2 — behavioural colour frames",
      BipolarColorModel.class, "Phase 2 — behavioural colour frames",
      CircularColorModel.class, "Phase 2 — behavioural colour frames",
      RainbowColorModel.class, "Phase 2 — behavioural colour frames",
      HeatColorModel.class, "Phase 2 — behavioural colour frames");

   private VisualFrameAliases() {
   }

   /**
    * Builds a frame from the agent vocabulary.
    *
    * @param channel the aesthetic channel the frame is destined for; a colour frame on the
    *                size channel is a caller error worth naming, not something to coerce
    * @param spec    {@code {type, ...}} in the alias vocabulary
    */
   public static VisualFrameModel create(String channel, Map<String, Object> spec) {
      // The type is read before the channel is validated so a mismatch can name both. Being
      // told "the size channel is not supported" when you asked for a categorical colour
      // frame leaves you guessing which half of the call was wrong.
      String type = str(spec, "type");
      String name = requireFrameChannelFor(channel, type);

      if(type == null) {
         throw new IllegalArgumentException(
            "A visual frame needs a 'type'. Valid types for the " + name + " channel: " +
            String.join(", ", colorTypeNames()) + ".");
      }

      // Only the colour channel is supported this phase, so any frame reaching here is a
      // colour frame; requireFrameChannel has already refused the others.
      return switch(type) {
         case "static" -> staticColor(spec);
         case "categorical" -> categoricalColor(spec);
         case "gradient" -> gradientColor(spec);
         case "palette" -> palette(spec);
         default -> throw unknownType(name, type);
      };
   }

   /** Renders a frame back in the agent vocabulary. Never emits {@code clazz}. */
   public static Map<String, Object> describe(VisualFrameModel frame) {
      if(frame == null) {
         return null;
      }

      Map<String, Object> out = new LinkedHashMap<>();

      if(frame instanceof StaticColorModel model) {
         out.put("type", "static");
         out.put("color", model.getColor());
         return out;
      }

      if(frame instanceof CategoricalColorModel model) {
         out.put("type", "categorical");
         out.put("colors", model.getColors() == null
            ? List.of() : Arrays.asList(model.getColors()));
         return out;
      }

      if(frame instanceof GradientColorModel model) {
         out.put("type", "gradient");
         out.put("from", model.getFromColor());
         out.put("to", model.getToColor());
         return out;
      }

      String paletteName = paletteNameOf(frame.getClass());

      if(paletteName != null) {
         out.put("type", "palette");
         out.put("palette", paletteName);
         return out;
      }

      // A frame outside the vocabulary is reported as what it is rather than silently
      // flattened to something wrong. The caller can still see it; they just cannot rebuild
      // it with set_visual_frame yet.
      out.put("type", "unsupported");
      out.put("detail", frame.getClass().getSimpleName());
      return out;
   }

   /** Normalizes {@code #RGB} / {@code #RRGGBB}, with or without {@code #}, to {@code #RRGGBB}. */
   public static String normalizeColor(String color) {
      String value = color == null ? "" : color.trim();

      if(value.startsWith("#")) {
         value = value.substring(1);
      }

      if(!value.matches("(?i)[0-9a-f]{3}|[0-9a-f]{6}")) {
         throw new IllegalArgumentException(
            "'" + color + "' is not a colour this tool accepts. Use #RRGGBB or #RGB (the '#' " +
            "is optional). Named CSS colours are not supported — half-supporting them would " +
            "mean some names silently rendering as something else.");
      }

      value = value.toUpperCase();

      if(value.length() == 3) {
         StringBuilder expanded = new StringBuilder();

         for(char c : value.toCharArray()) {
            expanded.append(c).append(c);
         }

         value = expanded.toString();
      }

      return "#" + value;
   }

   // ── the coverage test's view ──────────────────────────────────────────────

   /** Every concrete {@code ColorFrameModel} subclass the vocabulary must account for. */
   public static Collection<Class<?>> colorFrameSubclasses() {
      List<Class<?>> all = new ArrayList<>(PALETTES.values());
      all.addAll(COLOR_TYPES.values());
      all.addAll(EXCLUDED.keySet());
      return all;
   }

   public static boolean isMapped(Class<?> subclass) {
      return COLOR_TYPES.containsValue(subclass) || PALETTES.containsValue(subclass);
   }

   public static boolean isExcluded(Class<?> subclass) {
      return EXCLUDED.containsKey(subclass);
   }

   // ── builders ──────────────────────────────────────────────────────────────

   private static VisualFrameModel staticColor(Map<String, Object> spec) {
      String color = str(spec, "color");

      if(color == null) {
         throw new IllegalArgumentException(
            "A static colour frame needs a 'color', e.g. {type: \"static\", color: \"#4E79A7\"}.");
      }

      StaticColorModel model = new StaticColorModel();
      model.setColor(normalizeColor(color));
      return model;
   }

   private static VisualFrameModel categoricalColor(Map<String, Object> spec) {
      List<String> colors = strList(spec, "colors");

      if(colors.isEmpty()) {
         throw new IllegalArgumentException(
            "A categorical colour frame needs a non-empty 'colors' list, e.g. " +
            "{type: \"categorical\", colors: [\"#4E79A7\", \"#F28E2C\"]}.");
      }

      CategoricalColorModel model = new CategoricalColorModel();
      model.setColors(colors.stream().map(VisualFrameAliases::normalizeColor)
                         .toArray(String[]::new));
      return model;
   }

   private static VisualFrameModel gradientColor(Map<String, Object> spec) {
      String from = str(spec, "from");
      String to = str(spec, "to");

      if(from == null || to == null) {
         throw new IllegalArgumentException(
            "A gradient colour frame needs both 'from' and 'to', e.g. " +
            "{type: \"gradient\", from: \"#EEEEFF\", to: \"#005599\"}.");
      }

      GradientColorModel model = new GradientColorModel();
      model.setFromColor(normalizeColor(from));
      model.setToColor(normalizeColor(to));
      return model;
   }

   private static VisualFrameModel palette(Map<String, Object> spec) {
      String requested = str(spec, "palette");

      if(requested == null) {
         throw new IllegalArgumentException(
            "A palette frame needs a 'palette' name. Available: " + paletteNames() + ".");
      }

      for(Map.Entry<String, Class<? extends ColorFrameModel>> entry : PALETTES.entrySet()) {
         if(entry.getKey().equalsIgnoreCase(requested)) {
            try {
               return entry.getValue().getDeclaredConstructor().newInstance();
            }
            catch(ReflectiveOperationException e) {
               throw new IllegalStateException(
                  "Palette '" + entry.getKey() + "' could not be instantiated.", e);
            }
         }
      }

      throw new IllegalArgumentException(
         "Unknown palette '" + requested + "'. Available: " + paletteNames() + ".");
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private static String requireFrameChannelFor(String channel, String type) {
      try {
         return AestheticChannels.requireFrameChannel(channel);
      }
      catch(IllegalArgumentException e) {
         if(type == null) {
            throw e;
         }

         throw new IllegalArgumentException(
            e.getMessage() + " (the requested frame type was '" + type + "')", e);
      }
   }

   private static IllegalArgumentException unknownType(String channel, String type) {
      List<String> valid = colorTypeNames();
      String nearest = nearestMatch(type, valid);
      String hint = nearest == null ? "" : " Did you mean '" + nearest + "'?";

      return new IllegalArgumentException(
         "Unknown frame type '" + type + "' for the " + channel + " channel." + hint +
         " Valid types: " + String.join(", ", valid) + ". A frame is never substituted with a " +
         "default — that would look like the tool worked.");
   }

   /**
    * Cheap edit-distance-1-ish match: good enough to catch a typo like "categorial", and it
    * never guesses on the caller's behalf — the near match is only ever a suggestion in an
    * error message.
    */
   private static String nearestMatch(String typed, List<String> candidates) {
      String best = null;
      int bestDistance = Integer.MAX_VALUE;

      for(String candidate : candidates) {
         int distance = editDistance(typed.toLowerCase(), candidate);

         if(distance < bestDistance) {
            bestDistance = distance;
            best = candidate;
         }
      }

      return bestDistance <= Math.max(2, typed.length() / 3) ? best : null;
   }

   private static int editDistance(String a, String b) {
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

   private static List<String> colorTypeNames() {
      List<String> names = new ArrayList<>(COLOR_TYPES.keySet());
      names.add("palette");
      Collections.sort(names);
      return names;
   }

   private static String paletteNames() {
      return String.join(", ", PALETTES.keySet());
   }

   private static String paletteNameOf(Class<?> type) {
      for(Map.Entry<String, Class<? extends ColorFrameModel>> entry : PALETTES.entrySet()) {
         if(entry.getValue().equals(type)) {
            return entry.getKey();
         }
      }

      return null;
   }

   private static String str(Map<String, Object> spec, String key) {
      Object value = spec == null ? null : spec.get(key);
      String text = value == null ? "" : String.valueOf(value).trim();
      return text.isEmpty() ? null : text;
   }

   private static List<String> strList(Map<String, Object> spec, String key) {
      Object value = spec == null ? null : spec.get(key);

      if(!(value instanceof Collection<?> collection)) {
         return List.of();
      }

      List<String> out = new ArrayList<>();

      for(Object item : collection) {
         if(item != null) {
            out.add(String.valueOf(item));
         }
      }

      return out;
   }

   private static Map<String, Class<? extends ColorFrameModel>> palettes() {
      Map<String, Class<? extends ColorFrameModel>> map = new LinkedHashMap<>();
      map.put("Blues", BluesColorModel.class);
      map.put("BrBG", BrBGColorModel.class);
      map.put("BuGn", BuGnColorModel.class);
      map.put("BuPu", BuPuColorModel.class);
      map.put("GnBu", GnBuColorModel.class);
      map.put("Greens", GreensColorModel.class);
      map.put("Greys", GreysColorModel.class);
      map.put("OrRd", OrRdColorModel.class);
      map.put("Oranges", OrangesColorModel.class);
      map.put("PRGn", PRGnColorModel.class);
      map.put("PiYG", PiYGColorModel.class);
      map.put("PuBu", PuBuColorModel.class);
      map.put("PuBuGn", PuBuGnColorModel.class);
      map.put("PuOr", PuOrColorModel.class);
      map.put("PuRd", PuRdColorModel.class);
      map.put("Purples", PurplesColorModel.class);
      map.put("RdBu", RdBuColorModel.class);
      map.put("RdGy", RdGyColorModel.class);
      map.put("RdPu", RdPuColorModel.class);
      map.put("RdYlBu", RdYlBuColorModel.class);
      map.put("RdYlGn", RdYlGnColorModel.class);
      map.put("Reds", RedsColorModel.class);
      map.put("Spectral", SpectralColorModel.class);
      map.put("YlGn", YlGnColorModel.class);
      map.put("YlGnBu", YlGnBuColorModel.class);
      map.put("YlOrBr", YlOrBrColorModel.class);
      map.put("YlOrRd", YlOrRdColorModel.class);
      return Collections.unmodifiableMap(map);
   }
}
