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

import java.util.*;

/**
 * Cross-field rules for {@code set_assembly_properties} that the Composer enforces only with
 * {@code [disabled]} bindings on the dialog fields, so a raw write reaches storage through a
 * path the UI cannot take (bugs #79259, #79260). The axis is "a control greyed out by a sibling
 * flag or mode, with no rule on the model":
 *
 * <ul>
 *   <li>Text: "Pop Component" is disabled while "Embed content from URL" is on.</li>
 *   <li>Image: "Scale Image" and "Tile" disable each other, and the 9-cell insets are enabled
 *       only with Scale Image on and Maintain Aspect Ratio off. An SVG has no "Scale Cell Grid"
 *       (it is forced to keep its ratio) and an animated GIF has no insets (Redmine #80464).</li>
 *   <li>TextInput: "Multiple Lines" is enabled only for the Text input control type (Redmine
 *       #80464).</li>
 * </ul>
 *
 * <p>Every rule is judged on the post-patch model and fires only when the patch changes one of
 * the fields it is about, so legacy content that is already in the conflicting state stays
 * editable by unrelated patches and an unchanged echo of a read is never refused. A refusal is
 * thrown before anything is written, so the stored values stay as they were.
 */
final class PatchGuards {
   private PatchGuards() {
   }

   private static final String TEXT_EMBED = "textGeneralPaneModel.textPaneModel.url";
   private static final String TEXT_POP = "textGeneralPaneModel.popComponent";
   private static final String IMAGE_SCALE_PANE = "imageAdvancedPaneModel.imageScalePaneModel.";
   private static final String GROUP_SCALE_PANE = "imageScalePane.";
   private static final String SCALE = "scaleImageChecked";
   private static final String TILE = "tile";
   private static final String ASPECT = "maintainAspectRatio";
   private static final String[] INSETS = { "top", "left", "bottom", "right" };
   private static final String IMAGE_SELECTED =
      "imageGeneralPaneModel.staticImagePaneModel.imagePreviewPaneModel.selectedImage";
   private static final String IMAGE_GIF =
      "imageGeneralPaneModel.staticImagePaneModel.imagePreviewPaneModel.animateGifImage";
   private static final String IMAGE_DYNAMIC_SELECTED =
      "imageAdvancedPaneModel.dynamicImagePaneModel.dynamicImageSelected";
   private static final String IMAGE_DYNAMIC_VALUE =
      "imageAdvancedPaneModel.dynamicImagePaneModel.dynamicImageValue";
   private static final String TEXT_INPUT_MULTI_LINE = "textInputGeneralPaneModel.multiLine";
   private static final String TEXT_INPUT_TYPE = "textInputColumnOptionPaneModel.type";
   private static final String TEXT_INPUT_OPTION = "textInputColumnOptionPaneModel.";

   /**
    * Input Control type -> the sub-editor of {@code textInputColumnOptionPaneModel} the Composer
    * shows for it and {@code VSInputService} applies and reads back, with that editor's fields.
    * The other editors are neither shown nor applied, so a write to one of their fields is lost.
    */
   private static final Map<String, Map.Entry<String, List<String>>> TEXT_INPUT_EDITORS =
      new java.util.LinkedHashMap<>();

   static {
      List<String> pattern = List.of("pattern", "errorMessage");
      List<String> range = List.of("minimum", "maximum", "errorMessage");
      TEXT_INPUT_EDITORS.put("Text", Map.entry("textEditorModel", pattern));
      TEXT_INPUT_EDITORS.put("Password", Map.entry("passwordEditorModel", pattern));
      TEXT_INPUT_EDITORS.put("Date", Map.entry("dateEditorModel", range));
      TEXT_INPUT_EDITORS.put("Integer", Map.entry("integerEditorModel", range));
      TEXT_INPUT_EDITORS.put("Float", Map.entry("floatEditorModel", range));
   }

   /**
    * The values of the guarded paths before the patch is applied, read as values because
    * {@code PropertyPath.set} mutates mutable holders in place. Empty unless the type has
    * guarded paths and the patch writes one of them.
    */
   static Map<String, Object> snapshot(String type, Object model,
                                       Collection<String> resolvedPaths)
   {
      Map<String, Object> values = new HashMap<>();
      List<String> paths = paths(type);

      if(paths.stream().anyMatch(path -> touches(resolvedPaths, path))) {
         // the rules compare siblings, so the whole group is read once any of it is written
         for(String path : paths) {
            values.put(path, PropertyPath.get(model, path));
         }
      }

      return values;
   }

   /**
    * @param model the root returned by the last {@code PropertyPath.set}, not a held reference
    *
    * @throws IllegalArgumentException naming every field involved, when the patch produced a
    *         state the Composer cannot.
    */
   static void require(String type, Map<String, Object> before, Object model) {
      if(before.isEmpty()) {
         return;
      }

      if("text".equals(type)) {
         requireEmbedOrPop(before, model);
      }
      else if("textinput".equals(type)) {
         requireMultiLineForText(before, model);
         requireEditorOfTheSelectedType(before, model);
      }
      else {
         String prefix = scalePrefix(type);

         if(prefix != null) {
            requireScalePane(prefix, before, model);

            if("image".equals(type)) {
               requireSvgAndGif(prefix, before, model);
            }
         }
      }
   }

   private static List<String> paths(String type) {
      if("text".equals(type)) {
         return List.of(TEXT_EMBED, TEXT_POP);
      }

      if("textinput".equals(type)) {
         List<String> paths = new ArrayList<>(List.of(TEXT_INPUT_MULTI_LINE, TEXT_INPUT_TYPE));

         for(Map.Entry<String, List<String>> editor : TEXT_INPUT_EDITORS.values()) {
            for(String leaf : editor.getValue()) {
               paths.add(TEXT_INPUT_OPTION + editor.getKey() + "." + leaf);
            }
         }

         return paths;
      }

      String prefix = scalePrefix(type);

      if(prefix == null) {
         return List.of();
      }

      List<String> paths = new ArrayList<>(List.of(prefix + SCALE, prefix + TILE, prefix + ASPECT));

      for(String leaf : INSETS) {
         paths.add(prefix + leaf);
      }

      if("image".equals(type)) {
         paths.addAll(List.of(IMAGE_SELECTED, IMAGE_GIF, IMAGE_DYNAMIC_SELECTED,
                              IMAGE_DYNAMIC_VALUE));
      }

      return paths;
   }

   private static String scalePrefix(String type) {
      if("image".equals(type)) {
         return IMAGE_SCALE_PANE;
      }

      return "groupcontainer".equals(type) ? GROUP_SCALE_PANE : null;
   }

   private static boolean touches(Collection<String> resolvedPaths, String path) {
      return resolvedPaths.stream().anyMatch(p -> p.equalsIgnoreCase(path));
   }

   private static boolean changed(Map<String, Object> before, Object model, String path) {
      return !Objects.equals(before.get(path), PropertyPath.get(model, path));
   }

   private static void requireEmbedOrPop(Map<String, Object> before, Object model) {
      if(!changed(before, model, TEXT_EMBED) && !changed(before, model, TEXT_POP)) {
         return;
      }

      Object pop = PropertyPath.get(model, TEXT_POP);
      String popName = pop == null ? "" : pop.toString().trim();

      if(Boolean.TRUE.equals(PropertyPath.get(model, TEXT_EMBED)) && !popName.isEmpty()) {
         throw new IllegalArgumentException(
            "embedUrl and popComponent: a Text that embeds a URL (embedUrl:true) is rendered " +
            "as an iframe in the viewer, so its popComponent '" + popName + "' could not be " +
            "clicked open; the Composer disables Pop Component while \"Embed content from " +
            "URL\" is on. Nothing was changed. In the same call set popComponent:\"\" to " +
            "embed the URL, or embedUrl:false to keep the pop component.");
      }
   }

   private static void requireScalePane(String prefix, Map<String, Object> before, Object model) {
      boolean scale = Boolean.TRUE.equals(PropertyPath.get(model, prefix + SCALE));
      boolean tile = Boolean.TRUE.equals(PropertyPath.get(model, prefix + TILE));
      boolean aspect = Boolean.TRUE.equals(PropertyPath.get(model, prefix + ASPECT));

      if(scale && tile &&
         (changed(before, model, prefix + SCALE) || changed(before, model, prefix + TILE)))
      {
         throw new IllegalArgumentException(
            prefix + SCALE + " and " + prefix + TILE + ": Scale Image and Tile are mutually " +
            "exclusive in the Composer (each is disabled while the other is on). Set " +
            SCALE + ":false to tile, or " + TILE + ":false to scale. Nothing was changed.");
      }

      // Bug #80036: sibling of the insets rule below -- maintainAspectRatio itself is stored
      // only while Scale Image is on; the Composer disables "Maintain Aspect Ratio" the same
      // way it disables the 9-cell insets fieldset while Scale is off, but the write still
      // reported ok:true with a generic "may not apply to that assembly type" note and read
      // back unchanged.
      if(!scale && changed(before, model, prefix + ASPECT)) {
         throw new IllegalArgumentException(
            prefix + ASPECT + ": " + ASPECT + " is applied only while " + SCALE + " is true " +
            "(\"Scale Image\"); the Composer disables Maintain Aspect Ratio while Scale Image " +
            "is off, so the write would report success and read back unchanged. With this " +
            "patch " + SCALE + " would be " + scale + ". Set " + SCALE + ":true in the same " +
            "call to change " + ASPECT + ". Nothing was changed.");
      }

      for(String leaf : INSETS) {
         String path = prefix + leaf;

         if(!changed(before, model, path)) {
            continue;
         }

         Object value = PropertyPath.get(model, path);

         if(value instanceof Number && ((Number) value).intValue() < 0) {
            throw new IllegalArgumentException(
               path + ": " + value + " is negative; the 9-cell insets (top, left, bottom, " +
               "right) are pixel widths, and a negative one makes the image fall back to a " +
               "plain stretch. Use 0 or more. Nothing was changed.");
         }

         if(!scale || aspect) {
            throw new IllegalArgumentException(
               path + ": the 9-cell insets (top, left, bottom, right) are stored only when " +
               SCALE + " is true and " + ASPECT + " is false (\"Scale Cell Grid\"); " +
               "otherwise the image dialog discards them, so the write would report success " +
               "and read back unchanged. With this patch " + SCALE + " would be " + scale +
               " and " + ASPECT + " would be " + aspect + ". Set " + SCALE + ":true and " +
               ASPECT + ":false in the same call. Nothing was changed.");
         }
      }
   }

   /**
    * {@code textinput-general-pane} disables "Multiple Lines" unless the Input Control type is
    * Text (a blank type is the dialog's default, Text), but the setter stores the flag for any
    * type, so a write for a Date/Integer/Float/Password input reported success and changed
    * nothing the viewer shows. Fires only when the patch turns multiLine itself on, judged on
    * the post-patch type (so inputControl:"Text" in the same call is fine).
    */
   private static void requireMultiLineForText(Map<String, Object> before, Object model) {
      boolean multi = Boolean.TRUE.equals(PropertyPath.get(model, TEXT_INPUT_MULTI_LINE));
      Object type = PropertyPath.get(model, TEXT_INPUT_TYPE);
      String typeName = type == null ? "" : type.toString().trim();

      // Only the write of multiLine itself: moving the type off Text while a stored true stays
      // is what the Composer does too, and the viewer honors the flag for Text only.
      if(multi && !typeName.isEmpty() && !"Text".equalsIgnoreCase(typeName) &&
         changed(before, model, TEXT_INPUT_MULTI_LINE))
      {
         throw new IllegalArgumentException(
            TEXT_INPUT_MULTI_LINE + ": Multiple Lines is available only while the input control " +
            "type (" + TEXT_INPUT_TYPE + ") is Text; the Composer disables it for " + typeName +
            ", so the write would report success and have no effect. Set multiLine:false, or " +
            "inputControl:\"Text\" in the same call. Nothing was changed.");
      }
   }

   /**
    * {@code textinput-column-option-pane} shows only the sub-editor of the selected Input Control
    * type (Text and Password share the pattern editor's shape, Date/Integer/Float the range
    * editor's), and {@code VSInputService} builds the column option from, and reads back, that
    * one editor. A write to a field of any other editor is accepted and then dropped, so it
    * reported success and read back as the default. Fires only for a field the patch changes,
    * judged on the post-patch type (so a type switch and its editor's fields in one call pass).
    */
   private static void requireEditorOfTheSelectedType(Map<String, Object> before, Object model) {
      Object type = PropertyPath.get(model, TEXT_INPUT_TYPE);
      String typeName = type == null || type.toString().isBlank() ? "Text" : type.toString().trim();

      for(Map.Entry<String, Map.Entry<String, List<String>>> entry : TEXT_INPUT_EDITORS.entrySet()) {
         if(entry.getKey().equalsIgnoreCase(typeName)) {
            continue;
         }

         for(String leaf : entry.getValue().getValue()) {
            String path = TEXT_INPUT_OPTION + entry.getValue().getKey() + "." + leaf;

            if(changed(before, model, path)) {
               throw new IllegalArgumentException(
                  path + ": this is the " + entry.getKey() + " Input Control's field, but the " +
                  "input control type (" + TEXT_INPUT_TYPE + ") is " + typeName + ", so the " +
                  "Composer does not show it and the write would be dropped (reported success, " +
                  "read back unchanged). Set inputControl:\"" + entry.getKey() + "\" in the " +
                  "same call, or write the " + typeName + " editor's field instead. Nothing " +
                  "was changed.");
            }
         }
      }
   }

   /**
    * {@code image-scale-pane} forces Maintain Aspect Ratio on and disables "Scale Cell Grid" for
    * an SVG (the picked image, or a plain dynamic image path, ends in {@code .svg}), and disables
    * the four insets for an animated GIF. Fires only when the patch changes one of the fields
    * involved.
    */
   private static void requireSvgAndGif(String prefix, Map<String, Object> before, Object model) {
      boolean scale = Boolean.TRUE.equals(PropertyPath.get(model, prefix + SCALE));
      boolean aspect = Boolean.TRUE.equals(PropertyPath.get(model, prefix + ASPECT));
      Object dynamicValue = PropertyPath.get(model, IMAGE_DYNAMIC_VALUE);
      String dynamic = dynamicValue == null ? "" : dynamicValue.toString().trim();
      Object picked = PropertyPath.get(model, IMAGE_SELECTED);
      String image = Boolean.TRUE.equals(PropertyPath.get(model, IMAGE_DYNAMIC_SELECTED)) &&
         !dynamic.isEmpty() && !dynamic.startsWith("$(") && !dynamic.startsWith("=") ?
         dynamic : (picked == null ? "" : picked.toString());

      if(scale && !aspect && image.endsWith(".svg") &&
         (changed(before, model, prefix + SCALE) || changed(before, model, prefix + ASPECT) ||
          changed(before, model, IMAGE_SELECTED) || changed(before, model, IMAGE_DYNAMIC_SELECTED) ||
          changed(before, model, IMAGE_DYNAMIC_VALUE)))
      {
         throw new IllegalArgumentException(
            prefix + ASPECT + ": the image '" + image + "' is an SVG, which the Composer forces " +
            "to keep its aspect ratio (\"Scale Cell Grid\" is disabled for it), so scaling it " +
            "with " + ASPECT + ":false would not give a cell grid. Set " + ASPECT +
            ":true, or pick a raster image. Nothing was changed.");
      }

      if(Boolean.TRUE.equals(PropertyPath.get(model, IMAGE_GIF))) {
         for(String leaf : INSETS) {
            if(changed(before, model, prefix + leaf)) {
               throw new IllegalArgumentException(
                  prefix + leaf + ": the image is an animated GIF (" + IMAGE_GIF + " is true); " +
                  "the Composer disables the 9-cell insets for it. Turn the animation off in " +
                  "the same call (" + IMAGE_GIF + ":false) or leave the insets alone. Nothing " +
                  "was changed.");
            }
         }
      }
   }
}
