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

import inetsoft.uql.viewsheet.GradientColor;

import java.util.Collection;
import java.util.Objects;

/**
 * Keeps a Rectangle or Oval's solid fill and applied gradient from silently fighting inside
 * {@code set_assembly_properties} (bug #79108).
 *
 * <p>The dialog stores both, but only one is ever drawn: the gradient wins over the solid
 * background at render time, and {@code RectanglePropertyDialogService} also clears the stored
 * background whenever the gradient is applied, so a fill written under an applied gradient is
 * lost (Rectangle) or invisible (Oval) while the write reports success. The Composer UI cannot
 * hit this for a dynamic fill, and for a static fill its Gradient checkbox swaps the solid
 * picker out, so the user has to untick it before editing the solid color. The agent patch has
 * no such sequence, so it is resolved here, on the post-patch model:
 * <ul>
 *   <li>a fill write over a stored applied gradient, with no gradient write in the patch: the
 *       fill wins, the gradient is turned off (its stops are kept) and a warning says so;</li>
 *   <li>a fill write together with any gradient write that leaves the gradient applied: refused,
 *       the caller asked for two exclusive things;</li>
 *   <li>anything else (no gradient, gradient off, fill with {@code apply:false}, gradient-only)
 *       is untouched.</li>
 * </ul>
 * A whole {@code gradientColor} object without {@code apply} builds a bean whose apply is false,
 * so it is judged as off like any other merged value. A fill write is detected by comparing the
 * merged fill with the pre-patch one as well as by path, so a whole {@code fillPropPaneModel} raw
 * write cannot slip past a leaf-key check.
 */
final class ShapeFillGradientGuard {
   private ShapeFillGradientGuard() {
   }

   /** What the patch left behind, for the caller to relay. */
   record Result(Object model, String warning) {
   }

   /** The fill as it stood before the patch; {@code null} for every other assembly type. */
   record Snapshot(String prefix, String color, String colorValue) {
   }

   static Snapshot snapshot(String type, Object model) {
      String prefix = prefix(type);

      if(prefix == null) {
         return null;
      }

      return new Snapshot(prefix, (String) PropertyPath.get(model, prefix + FILL + ".color"),
                          (String) PropertyPath.get(model, prefix + FILL + ".colorValue"));
   }

   /**
    * @param before   {@link #snapshot} taken before the patch loop, may be null
    * @param model    the post-patch model
    * @param resolved the resolved dotted paths the patch wrote
    */
   static Result apply(Snapshot before, Object model, Collection<String> resolved) {
      if(before == null) {
         return new Result(model, null);
      }

      String fill = before.prefix() + FILL;
      String gradientPath = fill + ".gradientColor";
      boolean fillWritten =
         !Objects.equals(before.color(), PropertyPath.get(model, fill + ".color")) ||
         !Objects.equals(before.colorValue(), PropertyPath.get(model, fill + ".colorValue")) ||
         resolved.stream().anyMatch(path -> path.equalsIgnoreCase(fill + ".color") ||
                                            path.equalsIgnoreCase(fill + ".colorValue"));
      Object gradient = PropertyPath.get(model, gradientPath);
      boolean applied = gradient instanceof GradientColor g && g.isApply();

      if(!fillWritten || !applied) {
         return new Result(model, null);
      }

      // The path, an ancestor of it (a whole fillPropPaneModel or pane write) or anything under it.
      boolean gradientWritten = resolved.stream().anyMatch(path ->
         path.equalsIgnoreCase(gradientPath) ||
         path.toLowerCase().startsWith(gradientPath.toLowerCase() + ".") ||
         gradientPath.toLowerCase().startsWith(path.toLowerCase() + "."));

      if(gradientWritten) {
         throw new IllegalArgumentException(
            "fillColor: a solid fill (fillColor/fillColorValue) and an applied gradient " +
            "(gradientColor.apply true) were written in one patch, and they exclude each " +
            "other -- StyleBI draws the gradient and drops the solid fill, so the fill would " +
            "report success and never show. Send gradientColor.apply:false (a whole " +
            "gradientColor object needs an explicit apply) to use the solid fill, or drop the " +
            "fill write to keep the gradient. Nothing was changed.");
      }

      return new Result(
         PropertyPath.set(model, gradientPath + ".apply", false),
         "fillColor: the fill was stored and the gradient (gradientColor.apply) was turned off, " +
         "because an applied gradient hides the solid fill (and a Rectangle clears it). Its stops " +
         "are kept; set gradientColor.apply:true to show the gradient again, which hides the fill.");
   }

   private static String prefix(String type) {
      return switch(type) {
         case "rectangle" -> "rectanglePropertyPaneModel";
         case "oval" -> "ovalPropertyPaneModel";
         default -> null;
      };
   }

   private static final String FILL = ".fillPropPaneModel";
}
