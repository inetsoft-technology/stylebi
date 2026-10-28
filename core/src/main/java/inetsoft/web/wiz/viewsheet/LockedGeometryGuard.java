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

import java.util.Map;
import java.util.Set;

/**
 * Refuses a {@code set_assembly_properties} patch that moves or resizes a locked Image, Line,
 * Oval or Rectangle (bug #79615). The lock is enforced by the Composer only by disabling the
 * top/left/width/height fields of the size-and-position pane, and the server-side dialog setters
 * never read it, so a raw write changed the geometry while {@code edit} move/resize was refused.
 *
 * <p>Judged like {@link PatchGuards}: on the post-patch model, firing only when the patch changes
 * one of the four fields, so an unchanged echo of a read is never refused and a refusal is thrown
 * before anything is written. {@code locked} itself is read-only on this path and is not guarded.
 */
final class LockedGeometryGuard {
   private LockedGeometryGuard() {
   }

   private static final String[] LEAVES = { "top", "left", "width", "height" };

   /** The geometry before the patch; {@code null} unless the assembly is a locked lockable one. */
   record Snapshot(String pane, Object[] values) {
   }

   static Snapshot snapshot(String type, Object model) {
      String pane = pane(type);

      if(pane == null || !Boolean.TRUE.equals(PropertyPath.get(model, pane + ".locked"))) {
         return null;
      }

      Object[] values = new Object[LEAVES.length];

      for(int i = 0; i < LEAVES.length; i++) {
         values[i] = PropertyPath.get(model, pane + "." + LEAVES[i]);
      }

      return new Snapshot(pane, values);
   }

   /**
    * @param resolved the caller's key to the path it resolved to, used to name the caller's own key
    * @param model    the root returned by the last {@code PropertyPath.set}, not a held reference
    *
    * @throws IllegalArgumentException when the patch changed the geometry of a locked assembly.
    */
   static void require(Snapshot before, String assemblyName, Map<String, String> resolved,
                       Object model)
   {
      if(before == null) {
         return;
      }

      for(int i = 0; i < LEAVES.length; i++) {
         Object after = PropertyPath.get(model, before.pane() + "." + LEAVES[i]);

         if(!java.util.Objects.equals(before.values()[i], after)) {
            throw new IllegalArgumentException(
               "Property '" + key(before.pane(), LEAVES[i], resolved) + "' cannot be applied to '" +
               assemblyName + "' - it is locked. Use edit(op:'set_lock', locked:false) first. " +
               "Nothing was changed.");
         }
      }
   }

   private static String key(String pane, String leaf, Map<String, String> resolved) {
      String path = pane + "." + leaf;

      for(Map.Entry<String, String> entry : resolved.entrySet()) {
         if(path.equalsIgnoreCase(entry.getValue())) {
            return entry.getKey();
         }
      }

      return leaf;
   }

   private static String pane(String type) {
      return switch(type) {
         case "rectangle", "oval", "line" -> "shapeGeneralPaneModel.sizePositionPaneModel";
         case "image" -> "imageGeneralPaneModel.sizePositionPaneModel";
         default -> null;
      };
   }
}
