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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

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
 * The embedded viewsheet may itself be a thin wrapper, so inheritance passes down a chain of
 * thin wrappers to the first viewsheet that has a print layout of its own. Every level of the
 * chain has to be unambiguous and thin. (#78132)
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
    * @return the absolute name of the embedded viewsheet assembly, such as
    * <tt>Wrapper.Child</tt> when the layout is inherited through a thin intermediate wrapper,
    * or <tt>null</tt> if nothing is inherited. For a direct child it is the assembly name.
    */
   public static String getInheritedOwner(Viewsheet vs) {
      Viewsheet embedded = getInheritedViewsheet(vs);

      return embedded == null ? null : embedded.getAbsoluteName();
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
      return getInheritedViewsheet(vs, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
   }

   /**
    * Get the embedded viewsheet to inherit a print layout from, looking through embedded
    * viewsheets that are thin wrappers themselves.
    * @param vs the viewsheet to resolve.
    * @param depth the number of embedding levels above <tt>vs</tt>.
    * @param visited the viewsheets already resolved, which guards against a cycle.
    */
   private static Viewsheet getInheritedViewsheet(Viewsheet vs, int depth, Set<Viewsheet> visited) {
      if(vs == null || getOwnPrintLayout(vs) != null) {
         return null;
      }

      if(depth >= MAX_DEPTH || !visited.add(vs)) {
         LOG.debug("Print layout inheritance stopped at embedded viewsheet {}, the chain is " +
                      "too deep or cyclic.", vs.getName());
         return null;
      }

      Viewsheet inherited = null;

      for(Assembly assembly : vs.getAssemblies()) {
         Viewsheet owner = assembly instanceof Viewsheet embedded && isCandidate(embedded) ?
            getLayoutOwner(embedded, depth + 1, visited) : null;

         if(owner != null) {
            if(inherited != null) {
               return null;
            }

            inherited = owner;
         }
         // the inherited layout knows nothing about the wrapper's own components, so anything
         // else that would show on the canvas rules inheritance out rather than being dropped
         // from the export without the user knowing. That includes a visible embedded
         // viewsheet that supplies no print layout.
         else if(isVisibleContent((VSAssembly) assembly)) {
            return null;
         }
      }

      return inherited;
   }

   /**
    * Get the viewsheet whose print layout governs an embedded viewsheet, which is either the
    * embedded viewsheet itself or one it inherits from through a chain of thin wrappers.
    */
   private static Viewsheet getLayoutOwner(Viewsheet embedded, int depth, Set<Viewsheet> visited) {
      return getOwnPrintLayout(embedded) != null ?
         embedded : getInheritedViewsheet(embedded, depth, visited);
   }

   /**
    * Check if an embedded viewsheet may supply a print layout to its parent. It supplies one
    * only if it also has a print layout of its own or inherits one.
    */
   private static boolean isCandidate(Viewsheet embedded) {
      return embedded.isPrimary() && embedded.getVSAssemblyInfo().isVisible(true);
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

   /**
    * The number of embedding levels a print layout is inherited through.
    */
   private static final int MAX_DEPTH = 8;
   private static final Logger LOG = LoggerFactory.getLogger(PrintLayoutResolver.class);
}
