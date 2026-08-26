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
      return create(channel, spec, false);
   }

   /** @param relationChart see {@link AestheticChannels#requireFieldChannel(String, boolean)}. */
   public static VisualFrameModel create(String channel, Map<String, Object> spec,
                                         boolean relationChart)
   {
      // The type is read before the channel is validated so a mismatch can name both. Being
      // told "the size channel is not supported" when you asked for a categorical colour
      // frame leaves you guessing which half of the call was wrong.
      String type = str(spec, "type");
      String name = requireFrameChannelFor(channel, type, relationChart);

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

      // node-color/node-size reuse color/size's frame-type taxonomy verbatim: the model holds
      // the identical ColorFrameModel/SizeFrameModel types, just on a second property pair.
      return switch(frameFamily(name)) {
         case "color" -> colorFrame(spec, type);
         case "shape" -> shapeFrame(spec, type);
         case "size" -> sizeFrame(spec, type);
         case "line" -> lineFrame(spec, type);
         default -> textureFrame(spec, type);
      };
   }

   /**
    * {@code node-color}/{@code node-size} map to {@code color}/{@code size} — same frame-type
    * taxonomy, same model classes, just a second target property on {@code ChartBindingModel}.
    */
   private static String frameFamily(String channel) {
      return switch(channel) {
         case "node-color" -> "color";
         case "node-size" -> "size";
         default -> channel;
      };
   }

   /** The value keys a given channel and frame type actually read. */
   private static Set<String> consumedKeys(String channel, String type) {
      Set<String> keys = new LinkedHashSet<>(List.of("type"));

      switch(frameFamily(channel)) {
         case "color" -> {
            switch(type) {
               case "static", "brightness", "saturation" -> keys.add("color");
               case "categorical" ->
                  keys.addAll(List.of("colors", "mapping", "shareColors", "colorValueFrame"));
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
            switch(type) {
               case "static" -> keys.add("size");
               // Both graduated size frames map their values onto the same smallest..largest
               // range -- the two-handle slider in the Composer's binding-size pane. It lives on
               // SizeFrameModel, so it is the same pair for linear and categorical alike; there
               // is no per-category size list on the model to expose.
               case "linear", "categorical" -> keys.addAll(List.of("smallest", "largest"));
               default -> { /* fieldless */ }
            }
         }
         case "line" -> {
            switch(type) {
               case "static" -> keys.add("line");
               case "categorical" -> keys.add("lines");
               default -> { /* fieldless */ }
            }
         }
         default -> {
            switch(type) {
               case "static" -> keys.add("texture");
               case "categorical" -> keys.add("textures");
               default -> { /* fieldless */ }
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

   /**
    * A frame's {@code type} in the agent vocabulary, for naming it in an error message. Answers
    * the class's simple name for a model {@link #describe} has no branch for, so a diagnostic
    * never becomes a second, more confusing failure than the one it is reporting.
    */
   public static String typeName(VisualFrameModel frame) {
      Map<String, Object> described = describe(frame);
      Object type = described == null ? null : described.get("type");

      if(type != null) {
         return String.valueOf(type);
      }

      return frame == null ? "null" : frame.getClass().getSimpleName();
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
         // The same selection ColorFrameModelFactory.CategoricalColorFactory makes on the way in:
         // with sharing on the pins live on the viewsheet, off they live on this frame. Reading
         // the other one would report mapping: {} for a chart that visibly has pinned colours.
         ColorMapModel[] pins = model.isUseGlobal()
            ? model.getGlobalColorMaps() : model.getColorMaps();

         for(ColorMapModel map : pins == null ? new ColorMapModel[0] : pins) {
            if(map != null && map.getOption() != null) {
               mapping.put(map.getOption(), readableColor(map.getColor()));
            }
         }

         out.put("mapping", mapping);
         // One key, matching the one checkbox and the one key the write side takes. The frame's
         // isUseGlobal() is reported through it rather than beside it: it is the flag the render
         // path actually consults for the pins above, and a legacy frame where the two disagree
         // is a state the Composer can neither produce nor repair -- so naming it here would offer
         // the agent a distinction it has no way to act on. Writes leave such a frame's pair
         // untouched instead (ChartAestheticMutator.carryShareColorState).
         out.put("shareColors", model.isUseGlobal());
         out.put("colorValueFrame", model.isColorValueFrame());
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

      // These three fall through to BEHAVIOURAL below, which knows only their type name. That
      // was correct while create() built them empty; now that they carry values, reporting the
      // name alone would leave the read unable to see what the write set -- a frame that
      // round-trips as {type: "categorical"} whatever it holds.
      if(frame instanceof CategoricalLineModel model) {
         out.put("type", "categorical");
         out.put("lines", boxed(model.getLines()));
         return out;
      }

      if(frame instanceof CategoricalTextureModel model) {
         out.put("type", "categorical");
         out.put("textures", boxed(model.getTextures()));
         return out;
      }

      if(frame instanceof LinearSizeModel || frame instanceof CategoricalSizeModel) {
         SizeFrameModel model = (SizeFrameModel) frame;
         out.put("type", frame instanceof CategoricalSizeModel ? "categorical" : "linear");
         out.put("smallest", model.getSmallest());
         out.put("largest", model.getLargest());
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

   private static List<Integer> boxed(int[] values) {
      if(values == null) {
         return List.of();
      }

      List<Integer> out = new ArrayList<>(values.length);

      for(int value : values) {
         out.add(value);
      }

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

   /**
    * A colour on the way out, in the one spelling {@link #normalizeColor} takes on the way in.
    *
    * <p>The two arrays a categorical colour frame reports from are filled by different formatters.
    * {@code CategoricalColorModel(wrapper)} uses {@code Tool.toString(Color)} for {@code colors}
    * and {@code colorMaps}, which writes {@code #RRGGBB}; {@code
    * VSChartBindingFactory.applyColorsToFrame} refills {@code globalColorMaps} from the viewsheet
    * with {@code Tool.colorToHTMLString}, which returns six hex digits and no {@code #}. So a
    * shared pin read back as {@code "000000"} — a value this tool's own {@code set_visual_frame}
    * refuses, making the read impossible to feed back to the write.
    *
    * <p>Left alone rather than corrected if it is neither spelling: a diagnostic must not become a
    * second failure, and reporting the raw value is more use than an exception when something
    * upstream has put an unexpected string there.
    */
   private static String readableColor(String color) {
      try {
         return normalizeColor(color);
      }
      catch(IllegalArgumentException ex) {
         return color;
      }
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
      // CategoricalShapeFrameModelFactory ends its update with setChanged(model.isChanged()),
      // which overwrites the flag setShape(i, ...) had just raised. A model left at the default
      // false therefore stores the shapes and then reports the frame as untouched, so a chart
      // type change discards them. An agent write is by definition a deliberate change.
      model.setChanged(true);
      return model;
   }

   private static VisualFrameModel sizeFrame(Map<String, Object> spec, String type) {
      return switch(type) {
         case "static" -> staticSize(spec);
         case "linear" -> sizeRange(spec, new LinearSizeModel());
         case "categorical" -> sizeRange(spec, new CategoricalSizeModel());
         default -> throw unknownType("size", type);
      };
   }

   /**
    * The range a graduated size frame maps its values onto, from {@code SizeFrameModel}'s own
    * {@code smallest}/{@code largest} — the two-handle slider the Composer shows once a field is
    * bound to the size channel. Both are optional and default to 1 and 30, the same values the
    * slider opens with.
    *
    * <p>{@code setChanged(true)} is not decoration here.
    * {@code SizeFrameModelFactory.updateVisualFrameWrapper0} returns {@code null} — discarding
    * the whole wrapper — when the model reports itself unchanged, so a linear or categorical
    * size frame built without it was accepted, reported as success, and then dropped before it
    * reached the chart. That is the same silent discard {@link #staticSize} already had to work
    * around, one class up the hierarchy.
    */
   private static VisualFrameModel sizeRange(Map<String, Object> spec, SizeFrameModel model) {
      Double smallest = number(spec, "smallest");
      Double largest = number(spec, "largest");

      if(smallest != null) {
         model.setSmallest(smallest);
      }

      if(largest != null) {
         model.setLargest(largest);
      }

      if(model.getSmallest() > model.getLargest()) {
         throw new IllegalArgumentException(
            "'smallest' (" + model.getSmallest() + ") cannot be greater than 'largest' (" +
            model.getLargest() + ").");
      }

      model.setChanged(true);
      return model;
   }

   private static VisualFrameModel staticSize(Map<String, Object> spec) {
      Double size = number(spec, "size");

      if(size == null) {
         throw new IllegalArgumentException(
            "A static size frame needs a numeric 'size', e.g. {type: \"static\", size: 8}.");
      }

      StaticSizeModel model = new StaticSizeModel();
      model.setSize(size);
      // StaticSizeFrameModelFactory is the only static factory that gates on isChanged():
      // when false it resets the USER-tier composite value instead of applying the size,
      // so this write must mark itself as a deliberate change or it is silently discarded.
      model.setChanged(true);
      return model;
   }

   private static VisualFrameModel lineFrame(Map<String, Object> spec, String type) {
      return switch(type) {
         case "static" -> staticLine(spec);
         case "linear" -> new LinearLineModel();
         case "categorical" -> categoricalLine(spec);
         default -> throw unknownType("line", type);
      };
   }

   /**
    * A line style per category — the {@code line-combo-box} row the Composer's categorical pane
    * shows when the shape channel of a line chart carries a field.
    *
    * <p>{@code lines} is optional: an empty categorical frame is a meaningful request ("vary the
    * line style by category, using the defaults"), and it is what binding a field produces
    * before anything is picked. {@code CategoricalLineFrameModelFactory} returns the wrapper
    * untouched for an empty array, so the defaults survive.
    */
   private static VisualFrameModel categoricalLine(Map<String, Object> spec) {
      CategoricalLineModel model = new CategoricalLineModel();
      model.setLines(intArray(spec, "lines", "line-style code"));
      model.setChanged(true);
      return model;
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
         case "categorical" -> categoricalTexture(spec);
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

   /** A texture per category. {@code textures} is optional, for the reason in {@link #categoricalLine}. */
   private static VisualFrameModel categoricalTexture(Map<String, Object> spec) {
      CategoricalTextureModel model = new CategoricalTextureModel();
      model.setTextures(intArray(spec, "textures", "texture code"));
      model.setChanged(true);
      return model;
   }

   /**
    * Reads a list of integer codes. A present-but-not-a-list value is refused rather than read
    * as an empty list: {@code lines: 4097} means one line style, and silently building an empty
    * categorical frame from it would report success and change nothing.
    */
   private static int[] intArray(Map<String, Object> spec, String key, String what) {
      Object raw = spec == null ? null : spec.get(key);

      if(raw == null) {
         return new int[0];
      }

      if(!(raw instanceof Collection<?>)) {
         throw new IllegalArgumentException(
            "'" + key + "' must be a list of " + what + "s, e.g. \"" + key + "\": [4097, 4113], " +
            "got '" + raw + "'.");
      }

      List<String> values = strList(spec, key);
      int[] out = new int[values.size()];

      for(int i = 0; i < out.length; i++) {
         try {
            out[i] = (int) Double.parseDouble(values.get(i));
         }
         catch(NumberFormatException e) {
            throw new IllegalArgumentException(
               "'" + key + "[" + i + "]' must be a " + what + ", got '" + values.get(i) + "'.");
         }
      }

      return out;
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
    * A categorical colour frame, optionally with per-value colour pins.
    *
    * <p>{@code shareColors} is the categorical pane's <b>"Share Colors"</b> checkbox. That one
    * checkbox drives two different flags on {@code CategoricalColorFrame}
    * ({@code categorical-color-pane.shareColorsChange} sets both), and they are not the same
    * mechanism:
    *
    * <ul>
    *   <li>{@code shareColors} makes {@code VSFrameVisitor.createFrame} replace this frame
    *       wholesale with a clone of whichever frame the viewsheet has already cached for the same
    *       (column, date level). <b>That</b> is what makes one dimension render in the same colours
    *       on every chart of the viewsheet, and it carries the ordinary {@code colors} palette, so
    *       it needs nothing else set up first.</li>
    *   <li>{@code useGlobal} makes {@code VSFrameVisitor.applyGlobalColors} stamp the viewsheet's
    *       fixed value-to-colour pins over the result, and makes
    *       {@code ColorFrameModelFactory.CategoricalColorFactory} read {@code globalColorMaps}
    *       instead of {@code colorMaps}.</li>
    * </ul>
    *
    * <p>Set together here, as the checkbox sets them. An omitted {@code shareColors} is left at a
    * definite {@code false} and resolved against the channel's current frame by
    * {@code ChartAestheticMutator.carryShareColorState}, which is the only place with the frame to
    * leave alone — a spec that does not mention the checkbox is not asking to move it.
    *
    * <p>A {@code mapping} works either way; the flag decides where the pins land, exactly as
    * {@code openColorMappingDialog}'s callback does ({@code if(useGlobal) globalColorMaps = maps;
    * else colorMaps = maps;}). Off, they pin values on this chart. On, they pin them on the
    * viewsheet, so every chart colouring by that column shows the same colour for that value —
    * which is what "Assign Fixed Mapping" with "Share Colors" checked does in the Composer, and
    * the more useful of the two. {@code ColorFrameModelFactory.CategoricalColorFactory} reads
    * whichever array the flag selects, so writing to the other one is what would silently lose
    * them.
    *
    * <p>{@code colorValueFrame} is the pane's other checkbox, "Use Column Values as Colors": the
    * bound column's own values are read as colours rather than mapped to a palette, which
    * {@code CategoricalColorFrameWrapper.getVisualFrame} implements by handing back a
    * {@code ColorValueColorFrame} instead of the categorical one. The Composer only shows that
    * checkbox where the deployment lists {@code ColorValueColorFrame} in the
    * {@code custom.chart.frames} property, which is empty by default; the backend honours the flag
    * either way, so it is accepted here and the visibility rule is documented rather than
    * enforced — a deployment that has not enabled the checkbox has not disabled the feature.
    */
   private static VisualFrameModel categoricalColor(Map<String, Object> spec) {
      List<String> colors = strList(spec, "colors");
      Map<String, Object> mapping = mapping(spec);
      Boolean colorValueFrame = bool(spec, "colorValueFrame");

      if(colors.isEmpty() && mapping.isEmpty() && colorValueFrame == null) {
         throw new IllegalArgumentException(
            "A categorical colour frame needs a non-empty 'colors' list, a 'mapping' of value to " +
            "colour, or 'colorValueFrame', e.g. " +
            "{type: \"categorical\", mapping: {\"East\": \"#4E79A7\"}}.");
      }

      Boolean share = shareColors(spec);
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

         // The flag picks the destination, the way the Color Mapping dialog's callback does.
         // Writing to the array the factory is not reading is what loses the pins silently.
         if(Boolean.TRUE.equals(share)) {
            model.setGlobalColorMaps(maps.toArray(new ColorMapModel[0]));
         }
         else {
            model.setColorMaps(maps.toArray(new ColorMapModel[0]));
         }
      }

      // Both flags together, the way the "Share Colors" checkbox sets them. An omitted
      // shareColors leaves them here at a definite false and is resolved against the channel's
      // current frame by ChartAestheticMutator.carryShareColorState, which is the only place that
      // has the frame to leave alone.
      boolean shared = Boolean.TRUE.equals(share);
      model.setUseGlobal(shared);
      model.setShareColors(shared);

      if(colorValueFrame != null) {
         model.setColorValueFrame(colorValueFrame);
      }

      return model;
   }

   /**
    * The "Share Colors" checkbox as the spec asked for it, or {@code null} when the spec did not
    * mention it — which means "leave the checkbox where it is", the way the Composer's Apply does.
    * Resolving that against the channel's current frame needs the frame, so it happens in
    * {@code ChartAestheticMutator}; this only reports what was asked.
    *
    * <p>Deliberately one key, not two. The underlying frame carries {@code useGlobal} and
    * {@code shareColors} separately for historical reasons — {@code useGlobal} came first, for the
    * Color Mapping dialog, and {@code shareColors} was added later for frame sharing, which is why
    * {@code CategoricalColorFrameWrapper.parseContents} has a legacy default for one and not the
    * other. The Composer has driven them from a single checkbox ever since; the dialog's own
    * {@code toggleGlobal()} is left over from before that and is wired to nothing. Exposing both
    * would offer a distinction the product does not have and no caller could use correctly, so
    * the agent vocabulary has one name and the pair is derived from it.
    */
   static Boolean shareColors(Map<String, Object> spec) {
      return bool(spec, "shareColors");
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

   private static String requireFrameChannelFor(String channel, String type,
                                                boolean relationChart)
   {
      try {
         return AestheticChannels.requireFrameChannel(channel, relationChart);
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
      List<String> names = new ArrayList<>(switch(frameFamily(AestheticChannels.normalize(channel))) {
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
