/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.viewsheet.vslayout;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.AnnotationVSUtil;

/**
 * Resolves which print layout governs the export of a viewsheet.
 *
 * A print layout normally belongs to the sheet being exported. When that sheet has no print
 * layout of its own but simply wraps a single embedded viewsheet that does, the embedded
 * viewsheet's print layout is inherited, so a reusable viewsheet keeps exporting the way it
 * was designed to no matter which parent it is dropped into.
 *
 * Inheritance is deliberately narrow, because the inherited layout only covers the embedded
 * viewsheet and anything else on the wrapper's canvas would be dropped from the export. It
 * applies only when the wrapper is both unambiguous - exactly one primary embedded viewsheet
 * supplies a non-empty print layout - and thin, showing nothing else of its own.
 *
 * Visibility is a runtime value, so a viewsheet that binds it to a script or variable can be
 * judged differently here than it is at export time.
 *
 * @version 14.0
 * @author InetSoft Technology Corp
 */
public final class PrintLayoutResolver {
   /**
    * Prevent instantiation.
    */
   private PrintLayoutResolver() {
   }

   /**
    * Get the print layout that governs the export of a viewsheet, which is either its own or
    * the one inherited from a single embedded viewsheet.
    * @param vs the viewsheet being exported.
    * @return the print layout, or <tt>null</tt> if the viewsheet exports with its master layout.
    */
   public static PrintLayout getPrintLayout(Viewsheet vs) {
      PrintLayout layout = getOwnPrintLayout(vs);

      if(layout != null) {
         return layout;
      }

      Viewsheet embedded = getInheritedViewsheet(vs);

      return embedded == null ? null : getOwnPrintLayout(embedded);
   }

   /**
    * Get the name of the embedded assembly whose print layout is inherited.
    * @param vs the viewsheet being exported.
    * @return the embedded viewsheet assembly name, or <tt>null</tt> if nothing is inherited.
    */
   public static String getInheritedOwner(Viewsheet vs) {
      Viewsheet embedded = getInheritedViewsheet(vs);

      return embedded == null ? null : embedded.getName();
   }

   /**
    * Check if a viewsheet exports through a print layout, its own or an inherited one.
    * @param vs the viewsheet being exported.
    * @return <tt>true</tt> if a print layout governs the export.
    */
   public static boolean hasPrintLayout(Viewsheet vs) {
      return getPrintLayout(vs) != null;
   }

   /**
    * Get the embedded viewsheet to inherit a print layout from. Returns <tt>null</tt> when the
    * viewsheet has a print layout of its own, when no embedded viewsheet supplies one, when
    * more than one does, since there is no basis for choosing between them, or when the
    * viewsheet shows anything the inherited layout would not cover.
    */
   private static Viewsheet getInheritedViewsheet(Viewsheet vs) {
      if(vs == null || getOwnPrintLayout(vs) != null) {
         return null;
      }

      Viewsheet inherited = null;

      // only direct children are considered; a grandchild's layout would have to be composed
      // with its own parent's, which is not supported
      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof Viewsheet embedded && isCandidate(embedded)) {
            if(inherited != null) {
               return null;
            }

            inherited = embedded;
         }
         // the inherited layout knows nothing about the wrapper's own components, so anything
         // else that would show on the canvas rules inheritance out rather than being dropped
         // from the export without the user knowing
         else if(isVisibleContent((VSAssembly) assembly)) {
            return null;
         }
      }

      return inherited;
   }

   /**
    * Check if an embedded viewsheet can supply a print layout to its parent.
    */
   private static boolean isCandidate(Viewsheet embedded) {
      return embedded.isPrimary() && embedded.getVSAssemblyInfo().isVisible(true) &&
         getOwnPrintLayout(embedded) != null;
   }

   /**
    * Check if an assembly would show content of its own on the exported canvas.
    */
   private static boolean isVisibleContent(VSAssembly assembly) {
      return !AnnotationVSUtil.isAnnotation(assembly) &&
         assembly.getVSAssemblyInfo().isVisible(true);
   }

   /**
    * Get a viewsheet's own print layout, treating an empty layout as absent the same way the
    * exporter does.
    */
   private static PrintLayout getOwnPrintLayout(Viewsheet vs) {
      LayoutInfo info = vs == null ? null : vs.getLayoutInfo();
      PrintLayout layout = info == null ? null : info.getPrintLayout();

      return layout == null || layout.isEmpty() ? null : layout;
   }
}
