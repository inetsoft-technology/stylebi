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

import inetsoft.uql.viewsheet.*;

import java.util.ArrayList;
import java.util.List;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.internal.AnnotationRectangleVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.AnnotationVSAssemblyInfo;

/**
 * An annotation is <b>three assemblies</b> — {@link AnnotationVSAssembly}, its line, and its
 * rectangle — linked by name, with the content held on the rectangle.
 *
 * <p>Two consequences the rest of the plugin has to respect:
 *
 * <ol>
 *   <li>Listed flat, the three read as unrelated assemblies with nothing to say they are one
 *       object. The read model groups them.</li>
 *   <li>Removing one part directly <b>orphans the others</b>: the surviving
 *       {@code AnnotationVSAssemblyInfo} then references a name that no longer resolves. So
 *       the structural {@code remove} op refuses on any of the three.</li>
 * </ol>
 *
 * <p>This holds whether or not the annotation tools exist yet — annotations already exist in
 * the viewsheets this plugin edits, so the orphaning hazard is present from day one.
 */
public final class AnnotationFamily {
   private AnnotationFamily() {
   }

   public static boolean isPart(VSAssembly assembly) {
      return assembly instanceof AnnotationVSAssembly ||
         assembly instanceof AnnotationLineVSAssembly ||
         assembly instanceof AnnotationRectangleVSAssembly;
   }

   /** The line and rectangle belong to an annotation; the annotation itself is the root. */
   public static boolean isSubordinatePart(VSAssembly assembly) {
      return assembly instanceof AnnotationLineVSAssembly ||
         assembly instanceof AnnotationRectangleVSAssembly;
   }

   /** The rectangle's text, which is what a reader means by "the annotation". */
   public static String contentOf(Viewsheet vs, VSAssembly annotation) {
      if(vs == null || !(annotation instanceof AnnotationVSAssembly) ||
         !(annotation.getVSAssemblyInfo() instanceof AnnotationVSAssemblyInfo info) ||
         info.getRectangle() == null)
      {
         return null;
      }

      Assembly rectangle = vs.getAssembly(info.getRectangle());

      return rectangle instanceof AnnotationRectangleVSAssembly rect &&
         rect.getVSAssemblyInfo() instanceof AnnotationRectangleVSAssemblyInfo rectInfo
         ? rectInfo.getContent() : null;
   }

   /** The names of the line and rectangle an annotation owns, for grouping in the read model. */
   public static List<String> partsOf(VSAssembly annotation) {
      if(!(annotation instanceof AnnotationVSAssembly) ||
         !(annotation.getVSAssemblyInfo() instanceof AnnotationVSAssemblyInfo info))
      {
         return List.of();
      }

      List<String> parts = new ArrayList<>();

      if(info.getLine() != null) {
         parts.add(info.getLine());
      }

      if(info.getRectangle() != null) {
         parts.add(info.getRectangle());
      }

      return parts;
   }

   /** The refusal the structural {@code remove} op raises on any annotation part. */
   public static IllegalArgumentException removeRefusal(String assemblyName) {
      return new IllegalArgumentException(
         "'" + assemblyName + "' is part of an annotation, which is three linked assemblies " +
         "— the annotation, its line, and its rectangle. Removing one directly orphans the " +
         "others: the survivors reference a name that no longer resolves. Remove the " +
         "annotation as a whole instead.");
   }
}
