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

import inetsoft.web.binding.model.ColorMapModel;
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
    * The fieldless frames, whose only behaviour is which visual frame they create. Mapped here
    * so {@link #describe} names them and the coverage test sees them as reachable.
    */
   private static final Map<Class<?>, String> BEHAVIOURAL = behavioural();

   /**
    * Subclasses deliberately not reachable through an alias, each with its reason. The
    * coverage test reads this list, so excluding something is a recorded decision rather than
    * an oversight.
    */
   private static final Map<Class<?>, String> EXCLUDED = Map.of(
      HSLColorModel.class, "abstract base of Brightness and Saturation, reached through those",
      TextFrameModel.class, "abstract base; text frames have no configurable variety",
      DefaultTextFrameModel.class, "the only text frame; nothing to choose",
      ColorFrameModel.class, "abstract base",
      ShapeFrameModel.class, "abstract base",
      SizeFrameModel.class, "abstract base",
      LineFrameModel.class, "abstract base",
      TextureFrameModel.class, "abstract base");

   private static Map<Class<?>, String> behavioural() {
      Map<Class<?>, String> map = new LinkedHashMap<>();
      map.put(BipolarColorModel.class, "bipolar");
      map.put(CircularColorModel.class, "circular");
      map.put(RainbowColorModel.class, "rainbow");
      map.put(HeatColorModel.class, "heat");
      map.put(FillShapeModel.class, "fill");
      map.put(OrientationShapeModel.class, "orientation");
      map.put(OvalShapeModel.class, "oval");
      map.put(PolygonShapeModel.class, "polygon");
      map.put(TriangleShapeModel.class, "triangle");
      map.put(LinearSizeModel.class, "linear");
      map.put(CategoricalSizeModel.class, "categorical");
      map.put(LinearLineModel.class, "linear");
      map.put(CategoricalLineModel.class, "categorical");
      map.put(CategoricalTextureModel.class, "categorical");
      map.put(GridTextureModel.class, "grid");
      map.put(LeftTiltTextureModel.class, "left_tilt");
      map.put(RightTiltTextureModel.class, "right_tilt");
      map.put(OrientationTextureModel.class, "orientation");
      return Collections.unmodifiableMap(map);
   }

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
            String.join(", ", typeNames(name)) + ".");
      }

      // Every frame channel now accepts a `categorical` and a `static`, so a spec meant for one
      // channel is structurally valid on another and its value keys would simply be ignored:
      // {type: "categorical", colors: [...]} on `size` would build a categorical size frame with
      // no colours and report success. So a key the chosen channel and type do not consume is
      // refused, naming both.
      requireNoUnusedKeys(name, type, spec);

      return switch(name) {
         case "color" -> colorFrame(spec, type);
         case "shape" -> shapeFrame(spec, type);
         case "size" -> sizeFrame(spec, type);
         case "line" -> lineFrame(spec, type);
         default -> textureFrame(spec, type);
      };
   }

   /** The value keys a given channel and frame type actually read. */
   private static Set<String> consumedKeys(String channel, String type) {
      Set<String> keys = new LinkedHashSet<>(List.of("type"));

      switch(channel) {
         case "color" -> {
            switch(type) {
               case "static", "brightness", "saturation" -> keys.add("color");
               case "categorical" -> keys.addAll(List.of("colors", "mapping", "useGlobal"));
               case "gradient" -> keys.addAll(List.of("from", "to"));
               case "palette" -> keys.add("palette");
               default -> { /* fieldless */ }
            }
         }
         case "shape" -> {
            switch(type) {
               case "static" -> keys.add("shape");
               case "categorical" -> keys.add("shapes");
               default -> { /* fieldless */ }
            }
         }
         case "size" -> {
            if("static".equals(type)) {
               keys.add("size");
            }
         }
         case "line" -> {
            if("static".equals(type)) {
               keys.add("line");
            }
         }
         default -> {
            if("static".equals(type)) {
               keys.add("texture");
            }
         }
      }

      return keys;
   }

   private static void requireNoUnusedKeys(String channel, String type,
                                           Map<String, Object> spec)
   {
      if(spec == null) {
         return;
      }

      Set<String> consumed = consumedKeys(channel, type);
      List<String> unused = new ArrayList<>();

      for(String key : spec.keySet()) {
         if(!consumed.contains(key)) {
            unused.add(key);
         }
      }

      if(!unused.isEmpty()) {
         throw new IllegalArgumentException(
            "A '" + type + "' frame on the " + channel + " channel does not use " + unused +
            ". It reads " + consumed + ". Passing a key it ignores would report success while " +
            "that value went nowhere — most often it means the spec was written for a " +
            "different channel.");
      }
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
         Map<String, Object> mapping = new LinkedHashMap<>();

         for(ColorMapModel map : model.getColorMaps() == null
            ? new ColorMapModel[0] : model.getColorMaps())
         {
            if(map != null && map.getOption() != null) {
               mapping.put(map.getOption(), map.getColor());
            }
         }

         out.put("mapping", mapping);
         // Reported because a true here means any mapping above is stored but not rendered.
         out.put("useGlobal", model.isUseGlobal());
         return out;
      }

      if(frame instanceof GradientColorModel model) {
         out.put("type", "gradient");
         out.put("from", model.getFromColor());
         out.put("to", model.getToColor());
         return out;
      }

      if(frame instanceof HSLColorModel model) {
         out.put("type", frame instanceof SaturationColorModel ? "saturation" : "brightness");
         out.put("color", model.getColor());
         return out;
      }

      if(frame instanceof StaticShapeModel model) {
         out.put("type", "static");
         out.put("shape", model.getShape());
         return out;
      }

      if(frame instanceof CategoricalShapeModel model) {
         out.put("type", "categorical");
         out.put("shapes", model.getShapes() == null
            ? List.of() : Arrays.asList(model.getShapes()));
         return out;
      }

      if(frame instanceof StaticSizeModel model) {
         out.put("type", "static");
         out.put("size", model.getSize());
         return out;
      }

      if(frame instanceof StaticLineModel model) {
         out.put("type", "static");
         out.put("line", model.getLine());
         return out;
      }

      if(frame instanceof StaticTextureModel model) {
         out.put("type", "static");
         out.put("texture", model.getTexture());
         return out;
      }

      String behavioural = BEHAVIOURAL.get(frame.getClass());

      if(behavioural != null) {
         out.put("type", behavioural);
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

   /** Every frame subclass the vocabulary must account for, across all five channels. */
   public static Collection<Class<?>> colorFrameSubclasses() {
      List<Class<?>> all = new ArrayList<>(PALETTES.values());
      all.addAll(COLOR_TYPES.values());
      all.addAll(BEHAVIOURAL.keySet());
      all.addAll(EXCLUDED.keySet());
      all.addAll(List.of(StaticShapeModel.class, CategoricalShapeModel.class,
                         StaticSizeModel.class, StaticLineModel.class,
                         StaticTextureModel.class, BrightnessColorModel.class,
                         SaturationColorModel.class));
      return all;
   }

   public static boolean isMapped(Class<?> subclass) {
      return COLOR_TYPES.containsValue(subclass) || PALETTES.containsValue(subclass) ||
         BEHAVIOURAL.containsKey(subclass) ||
         List.of(StaticShapeModel.class, CategoricalShapeModel.class, StaticSizeModel.class,
                 StaticLineModel.class, StaticTextureModel.class, BrightnessColorModel.class,
                 SaturationColorModel.class).contains(subclass);
   }

   public static boolean isExcluded(Class<?> subclass) {
      return EXCLUDED.containsKey(subclass);
   }

   // ── builders ──────────────────────────────────────────────────────────────

   private static VisualFrameModel colorFrame(Map<String, Object> spec, String type) {
      return switch(type) {
         case "static" -> staticColor(spec);
         case "categorical" -> categoricalColor(spec);
         case "gradient" -> gradientColor(spec);
         case "palette" -> palette(spec);
         case "brightness" -> tinted(spec, new BrightnessColorModel());
         case "saturation" -> tinted(spec, new SaturationColorModel());
         case "bipolar" -> new BipolarColorModel();
         case "circular" -> new CircularColorModel();
         case "rainbow" -> new RainbowColorModel();
         case "heat" -> new HeatColorModel();
         default -> throw unknownType("color", type);
      };
   }

   /** Brightness and saturation derive a ramp from one base colour. */
   private static VisualFrameModel tinted(Map<String, Object> spec, HSLColorModel model) {
      String color = str(spec, "color");

      if(color == null) {
         throw new IllegalArgumentException(
            "A brightness or saturation colour frame needs a 'color' to derive its ramp from.");
      }

      model.setColor(normalizeColor(color));
      return model;
   }

   private static VisualFrameModel shapeFrame(Map<String, Object> spec, String type) {
      return switch(type) {
         case "static" -> staticShape(spec);
         case "categorical" -> categoricalShape(spec);
         case "fill" -> new FillShapeModel();
         case "orientation" -> new OrientationShapeModel();
         case "oval" -> new OvalShapeModel();
         case "polygon" -> new PolygonShapeModel();
         case "triangle" -> new TriangleShapeModel();
         default -> throw unknownType("shape", type);
      };
   }

   private static VisualFrameModel staticShape(Map<String, Object> spec) {
      String shape = str(spec, "shape");

      if(shape == null) {
         throw new IllegalArgumentException(
            "A static shape frame needs a 'shape', e.g. {type: \"static\", shape: \"circle\"}.");
      }

      StaticShapeModel model = new StaticShapeModel();
      model.setShape(shape);
      return model;
   }

   private static VisualFrameModel categoricalShape(Map<String, Object> spec) {
      List<String> shapes = strList(spec, "shapes");

      if(shapes.isEmpty()) {
         throw new IllegalArgumentException(
            "A categorical shape frame needs a non-empty 'shapes' list.");
      }

      CategoricalShapeModel model = new CategoricalShapeModel();
      model.setShapes(shapes.toArray(new String[0]));
      return model;
   }

   private static VisualFrameModel sizeFrame(Map<String, Object> spec, String type) {
      return switch(type) {
         case "static" -> staticSize(spec);
         case "linear" -> new LinearSizeModel();
         case "categorical" -> new CategoricalSizeModel();
         default -> throw unknownType("size", type);
      };
   }

   private static VisualFrameModel staticSize(Map<String, Object> spec) {
      Double size = number(spec, "size");

      if(size == null) {
         throw new IllegalArgumentException(
            "A static size frame needs a numeric 'size', e.g. {type: \"static\", size: 8}.");
      }

      StaticSizeModel model = new StaticSizeModel();
      model.setSize(size);
      return model;
   }

   private static VisualFrameModel lineFrame(Map<String, Object> spec, String type) {
      return switch(type) {
         case "static" -> staticLine(spec);
         case "linear" -> new LinearLineModel();
         case "categorical" -> new CategoricalLineModel();
         default -> throw unknownType("line", type);
      };
   }

   private static VisualFrameModel staticLine(Map<String, Object> spec) {
      Double line = number(spec, "line");

      if(line == null) {
         throw new IllegalArgumentException(
            "A static line frame needs a numeric 'line' — the StyleBI line-style code.");
      }

      StaticLineModel model = new StaticLineModel();
      model.setLine(line.intValue());
      return model;
   }

   private static VisualFrameModel textureFrame(Map<String, Object> spec, String type) {
      return switch(type) {
         case "static" -> staticTexture(spec);
         case "categorical" -> new CategoricalTextureModel();
         case "grid" -> new GridTextureModel();
         case "left_tilt" -> new LeftTiltTextureModel();
         case "right_tilt" -> new RightTiltTextureModel();
         case "orientation" -> new OrientationTextureModel();
         default -> throw unknownType("texture", type);
      };
   }

   private static VisualFrameModel staticTexture(Map<String, Object> spec) {
      Double texture = number(spec, "texture");

      if(texture == null) {
         throw new IllegalArgumentException(
            "A static texture frame needs a numeric 'texture' — the StyleBI texture code.");
      }

      StaticTextureModel model = new StaticTextureModel();
      model.setTexture(texture.intValue());
      return model;
   }

   private static Double number(Map<String, Object> spec, String key) {
      String text = str(spec, key);

      if(text == null) {
         return null;
      }

      try {
         return Double.parseDouble(text);
      }
      catch(NumberFormatException e) {
         throw new IllegalArgumentException(
            "'" + key + "' must be a number, got '" + text + "'.");
      }
   }

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

   /**
    * A categorical colour frame, optionally with per-value colour mapping.
    *
    * <p><b>The {@code useGlobal} footgun lives here.</b> {@code CategoricalColorModel} defaults
    * {@code useGlobal} and {@code shareColors} to {@code true}, and while {@code useGlobal} is set
    * the automatic palette wins: an explicit per-value colour is accepted, stored, and never
    * rendered. That is a recorded defect, and it is invisible — the model round-trips perfectly
    * and the chart shows something else.
    *
    * <p>So supplying a mapping clears {@code useGlobal}, because supplying one <i>is</i> the
    * intent to override the automatic palette. Asking for {@code useGlobal: true} alongside a
    * mapping is refused rather than honoured, since that combination cannot render what was
    * asked for.
    */
   private static VisualFrameModel categoricalColor(Map<String, Object> spec) {
      List<String> colors = strList(spec, "colors");
      Map<String, Object> mapping = mapping(spec);

      if(colors.isEmpty() && mapping.isEmpty()) {
         throw new IllegalArgumentException(
            "A categorical colour frame needs a non-empty 'colors' list, or a 'mapping' of " +
            "value to colour, e.g. {type: \"categorical\", mapping: {\"East\": \"#4E79A7\"}}.");
      }

      Boolean useGlobal = bool(spec, "useGlobal");

      if(Boolean.TRUE.equals(useGlobal) && !mapping.isEmpty()) {
         throw new IllegalArgumentException(
            "'useGlobal: true' cannot be combined with a 'mapping'. While useGlobal is set the " +
            "automatic palette wins, so the mapped colours would be stored and never rendered — " +
            "the model would round-trip perfectly and the chart would show something else. Drop " +
            "useGlobal to apply the mapping, or drop the mapping to keep the automatic palette.");
      }

      CategoricalColorModel model = new CategoricalColorModel();

      if(!colors.isEmpty()) {
         model.setColors(colors.stream().map(VisualFrameAliases::normalizeColor)
                            .toArray(String[]::new));
      }

      if(!mapping.isEmpty()) {
         List<ColorMapModel> maps = new ArrayList<>();

         for(Map.Entry<String, Object> entry : mapping.entrySet()) {
            ColorMapModel map = new ColorMapModel();
            map.setOption(entry.getKey());
            map.setColor(normalizeColor(String.valueOf(entry.getValue())));
            maps.add(map);
         }

         model.setColorMaps(maps.toArray(new ColorMapModel[0]));
         // Supplying a mapping IS the intent to override the automatic palette.
         model.setUseGlobal(false);
         model.setShareColors(false);
      }
      else if(useGlobal != null) {
         model.setUseGlobal(useGlobal);
      }

      return model;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> mapping(Map<String, Object> spec) {
      Object raw = spec == null ? null : spec.get("mapping");

      if(raw == null) {
         return Map.of();
      }

      if(!(raw instanceof Map<?, ?> map)) {
         throw new IllegalArgumentException(
            "'mapping' must be an object of value to colour, e.g. {\"East\": \"#4E79A7\"}.");
      }

      Map<String, Object> out = new LinkedHashMap<>();

      for(Map.Entry<?, ?> entry : map.entrySet()) {
         if(entry.getKey() != null && entry.getValue() != null) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
         }
      }

      if(out.isEmpty()) {
         throw new IllegalArgumentException("'mapping' is empty; omit it or give it entries.");
      }

      return out;
   }

   private static Boolean bool(Map<String, Object> spec, String key) {
      Object raw = spec == null ? null : spec.get(key);

      if(raw == null) {
         return null;
      }

      if(raw instanceof Boolean value) {
         return value;
      }

      String text = String.valueOf(raw).trim();

      if("true".equalsIgnoreCase(text)) {
         return Boolean.TRUE;
      }

      if("false".equalsIgnoreCase(text)) {
         return Boolean.FALSE;
      }

      throw new IllegalArgumentException(
         "'" + key + "' must be true or false, got '" + raw + "'.");
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
      List<String> valid = typeNames(channel);
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

   /** The frame types valid for a channel, for discovery and for error messages. */
   public static List<String> typeNames(String channel) {
      List<String> names = new ArrayList<>(switch(AestheticChannels.normalize(channel)) {
         case "color" -> List.of("static", "categorical", "gradient", "palette", "brightness",
                                 "saturation", "bipolar", "circular", "rainbow", "heat");
         case "shape" -> List.of("static", "categorical", "fill", "orientation", "oval",
                                 "polygon", "triangle");
         case "size" -> List.of("static", "linear", "categorical");
         case "line" -> List.of("static", "linear", "categorical");
         case "texture" -> List.of("static", "categorical", "grid", "left_tilt", "right_tilt",
                                   "orientation");
         default -> List.<String>of();
      });
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
