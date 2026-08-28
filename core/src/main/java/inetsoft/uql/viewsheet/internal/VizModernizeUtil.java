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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Modernize and Revert: move a dashboard's own content between the classic and modern chrome a
 * freshly created dashboard would have. Both run through seedChromeDefaults, which is why they
 * cannot drift apart, and both live in this package because that method is protected.
 *
 * Nothing here is automatic - unmarked content is never modernized and marked content is never
 * reverted unless somebody asks. A mixed dashboard stays mixed either way.
 */
public final class VizModernizeUtil {
   private VizModernizeUtil() {
   }

   /**
    * Whether this sheet holds anything Modernize would act on: the sheet's own info, or any
    * assembly of its own, carrying no mark.
    */
   public static boolean hasUnmarked(Viewsheet vs) {
      return !unmarked(vs).isEmpty();
   }

   /**
    * Stamp and seed every unmarked assembly, and the sheet itself. Returns how many were touched.
    *
    * A no-op returning 0 when the gate is off: VizMark.fromGate() has no mark to give, and
    * modernizing into a legacy gate would produce content that reverts the moment the gate turns
    * on. Callers still gate explicitly - this is a floor, not the policy.
    */
   public static int modernize(Viewsheet vs) {
      VizMark mark = VizMark.fromGate();

      if(mark == null) {
         return 0;
      }

      VizContext ctx = VizContext.of(mark);
      List<VSAssemblyInfo> targets = unmarked(vs);

      for(VSAssemblyInfo info : targets) {
         info.setVizMark(mark);
         info.seedChromeDefaults(ctx);
      }

      if(!targets.isEmpty()) {
         // seeding rewrote chart colour frames, and a render clones the sheet's shared frame in
         // preference to an assembly's own, so the stale one has to go or the old palette survives
         vs.clearSharedFrames();
         vs.clearDimensionColors();
      }

      return targets.size();
   }

   /**
    * Whether this sheet holds anything Revert would act on: the sheet's own info, or any assembly
    * of its own, carrying a mark.
    *
    * No gate term, unlike the modernizable flag: Revert is offered under both gate states on purpose,
    * so an author in a modern org can keep one dashboard classic.
    */
   public static boolean hasMarked(Viewsheet vs) {
      return !marked(vs).isEmpty();
   }

   /**
    * Clear the mark on every marked assembly, and on the sheet itself, then re-seed each through
    * the same hook creation uses. Returns how many were touched.
    *
    * No reverser is written: with the mark cleared, seedChromeDefaults writes the legacy branch of
    * every ternary, which is the identical call a gate-off creation makes. A property added to what
    * Modernize does is therefore reverted by the same edit to the same method, or it is not added.
    *
    * No gate floor, unlike modernize(): modernizing into a closed gate produces content that
    * changes appearance the moment the gate opens, and clearing a mark has no such hazard.
    */
   public static int revert(Viewsheet vs) {
      List<VSAssemblyInfo> targets = marked(vs);
      // every target is unmarked by the time it is seeded, so one context serves them all; the
      // cast picks the VizMark overload rather than the VSAssemblyInfo one
      VizContext ctx = VizContext.of((VizMark) null);

      for(VSAssemblyInfo info : targets) {
         info.setVizMark(null);
         info.seedChromeDefaults(ctx);
      }

      if(!targets.isEmpty()) {
         // seeding rewrote chart colour frames, and a render clones the sheet's shared frame in
         // preference to an assembly's own, so the stale one has to go or the modern palette survives
         vs.clearSharedFrames();
         vs.clearDimensionColors();
      }

      return targets.size();
   }

   /**
    * Re-resolve an assembly's seeded chrome after its state has been parsed. Restore replaces stored
    * formats and descriptors without consulting the mark, so the chrome that arrives can disagree
    * with the assembly it lands on: a bookmark taken before a Revert would otherwise un-revert it,
    * and one taken before Modernize would leave legacy chrome on a marked assembly.
    *
    * Safe on the mark: most types never write it into state, and the few that serialize their
    * whole info have the mark restored by their caller before this runs, so `info` always carries
    * the live assembly's own mark by the time it gets here regardless of what the blob said.
    * seedChromeDefaults writes DEFAULT tiers and the palette only, so a user format the restored
    * state legitimately carried survives untouched.
    *
    * Per-assembly only. The sheet's shared colour frames are cleared once at the end of
    * Viewsheet.parseState, because a render prefers them over an assembly's own.
    *
    * Null-tolerant: a partially constructed assembly can have no info yet.
    */
   public static void reseedAfterRestore(VSAssemblyInfo info) {
      if(info != null) {
         info.seedChromeDefaults(VizContext.of(info));
      }
   }

   /** The unmarked half of the sheet's own content. Modernize's targets. */
   private static List<VSAssemblyInfo> unmarked(Viewsheet vs) {
      return collect(vs, info -> info.getVizMark() == null);
   }

   /** The marked half of the sheet's own content. Revert's targets - the same traversal inverted. */
   private static List<VSAssemblyInfo> marked(Viewsheet vs) {
      return collect(vs, info -> info.getVizMark() != null);
   }

   /**
    * The sheet's own info, every matching assembly of its own, and every matching
    * embedded-viewsheet container of its own. Assemblies belonging to an embedded viewsheet are
    * skipped: they are another asset's content, and this sheet has no business writing to them.
    */
   private static List<VSAssemblyInfo> collect(Viewsheet vs, Predicate<VSAssemblyInfo> test) {
      List<VSAssemblyInfo> targets = new ArrayList<>();

      addIf(targets, vs.getVSAssemblyInfo(), test);

      for(Assembly assembly : vs.getAssemblies(true)) {
         if(!(assembly instanceof VSAssembly)) {
            continue;
         }

         VSAssemblyInfo info = ((VSAssembly) assembly).getVSAssemblyInfo();

         if(info != null && !info.isEmbedded()) {
            addIf(targets, info, test);
         }
      }

      // getAssemblies(true) flattens embedded-viewsheet containers away: the collector recurses into
      // a Viewsheet-typed child and never adds it (Viewsheet.java:3246-3263). The containers are this
      // sheet's own content, so collect them from the direct children; their children belong to the
      // embedded asset and stay excluded by the isEmbedded() test above.
      for(Assembly assembly : vs.getAssemblies(false)) {
         if(assembly instanceof Viewsheet) {
            addIf(targets, ((Viewsheet) assembly).getVSAssemblyInfo(), test);
         }
      }

      return targets;
   }

   private static void addIf(List<VSAssemblyInfo> targets, VSAssemblyInfo info,
                             Predicate<VSAssemblyInfo> test)
   {
      if(info != null && test.test(info)) {
         targets.add(info);
      }
   }
}
