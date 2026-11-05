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

import inetsoft.report.composition.graph.ChartFormatDefaults;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartRef;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Five public entry points onto a dashboard's chrome: modernize, revert, applyMark, reseed and
 * resetToModern. All five funnel into seedAll, which is what keeps them from drifting apart - each
 * just picks the targets and the mark, and seedAll does the stamping, seeding and shared-frame
 * invalidation.
 * They live in this package because seedChromeDefaults is protected.
 *
 * Nothing here is automatic - none of it runs unless somebody asks. modernize and revert are the
 * one-way pair and leave a mixed dashboard mixed: modernize touches only unmarked content, revert
 * only marked. applyMark is the per-dashboard switch and deliberately does not - it moves every
 * target that differs, so setting a mode or dark on a mixed dashboard stamps the deliberately
 * legacy assemblies too, which is what a per-dashboard control means.
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
      // unmarked(), not applyMark()'s differing predicate: an already-marked sibling (e.g. dark)
      // must survive a light-gated modernize untouched, which "differs from target mark" would not
      return mark == null ? 0 : moveMark(vs, mark, unmarked(vs));
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
      return applyMark(vs, null);
   }

   /**
    * Move every target to one mark: stamp each info whose mark differs, then re-seed it through
    * the hook creation uses. Returns how many were touched. The only route that can express a
    * MODERN_LIGHT to MODERN_DARK flip, which neither of the one-way operations above can reach.
    */
   public static int applyMark(Viewsheet vs, VizMark mark) {
      return moveMark(vs, mark, collect(vs, info -> info.getVizMark() != mark));
   }

   /**
    * Reset the sheet itself and every assembly of its own, marked or not, to what a new assembly
    * of its type gets under the sheet's mark, or the gate's when the sheet has none: author look
    * values go, the DEFAULT look is today's, and every target is stamped and seeded. Embedded
    * assets' content, shapes and images keep their look. Returns how many were touched; 0, with
    * nothing changed, when there is no mark to give.
    */
   public static int resetToModern(Viewsheet vs) {
      VizMark mark = vs.getVSAssemblyInfo() == null ? null : vs.getVSAssemblyInfo().getVizMark();
      mark = mark != null ? mark : VizMark.fromGate();

      if(mark == null) {
         return 0;
      }

      List<VSAssemblyInfo> targets = collect(vs, info -> true);

      for(VSAssemblyInfo info : targets) {
         resetChrome(info, mark);
      }

      seedAll(vs, mark, targets, true, true);
      restoreContainedSliders(targets);
      keepOnBottomTabs(vs);
      return targets.size();
   }

   /**
    * Clears the author's look on one assembly over DEFAULT tiers refreshed from a new instance.
    */
   private static void resetChrome(VSAssemblyInfo info, VizMark mark) {
      // shapes and images keep their look
      if(info.chromeSlots().isEmpty()) {
         return;
      }

      VSAssemblyInfo fresh = ChromeReset.freshDefaults(info, mark);
      ChromeReset.refreshDefaults(info, fresh);

      if(info instanceof ChartVSAssemblyInfo chart) {
         resetSlots(chart.formatSlots(), info, fresh);
         // after the object font, which the label defaults copy
         ChartFormatDefaults.fix(chart);
         resetSlots(ChartChromeSlots.of(chart), info, fresh);
      }
      else {
         resetSlots(info.chromeSlots(), info, fresh);
         ChromeReset.pinTitleFont(info, fresh);
      }
   }

   private static void resetSlots(List<ChromeSlot> slots, VSAssemblyInfo info,
                                  VSAssemblyInfo fresh)
   {
      for(ChromeSlot slot : slots) {
         if(!slot.isExpression(info)) {
            slot.reset(info, fresh);
         }
      }
   }

   /**
    * Stamp and seed the targets, after an unmarked target moving to a modern mark drops its
    * residue. A mark change re-seeds sizes and title lanes, so it re-flushes bottom-tabs children
    * as a density change does. Nothing moves when nothing was seeded.
    */
   private static int moveMark(Viewsheet vs, VizMark mark, List<VSAssemblyInfo> targets) {
      // only content leaving the classic look
      if(mark != null) {
         for(VSAssemblyInfo info : targets) {
            if(info.getVizMark() == null) {
               clearResidue(info);
            }
         }
      }

      int seeded = seedAll(vs, mark, targets, true, true);

      if(seeded > 0) {
         restoreContainedSliders(targets);
         keepOnBottomTabs(vs);
      }

      return seeded;
   }

   /**
    * Clears the author values the classic look resolves to without them; returns how many.
    */
   static int clearResidue(VSAssemblyInfo info) {
      List<ChromeSlot> slots = info.chromeSlots();
      List<Integer> objectLevel = new ArrayList<>();
      List<ChromeSlot> others = new ArrayList<>();

      for(int i = 0; i < slots.size(); i++) {
         ChromeSlot slot = slots.get(i);

         // an undecodable value matches nothing
         if(slot.residueEligible() && slot.isAuthorSet(info) && !slot.isExpression(info) &&
            slot.authorValue(info) != null)
         {
            if(slot.objectLevel()) {
               objectLevel.add(i);
            }
            else {
               others.add(slot);
            }
         }
      }

      int cleared = 0;
      List<Object> before = objectLevel.isEmpty() ? null :
         resolveAll(slots, classic(info, List.of()));

      // one at a time, as other slots inherit the object format and must resolve as before
      for(int i : objectLevel) {
         ChromeSlot slot = slots.get(i);
         List<Object> after = resolveAll(slots, classic(info, List.of(slot)));
         boolean residue = Objects.equals(slot.authorValue(info), after.get(i));

         for(int j = 0; residue && j < slots.size(); j++) {
            residue = j == i || Objects.equals(before.get(j), after.get(j));
         }

         if(residue) {
            slot.clearAuthor(info);
            // what info now resolves to
            before = after;
            cleared++;
         }
      }

      // none inherits from another, so one clone serves them all
      if(!others.isEmpty()) {
         VSAssemblyInfo shadow = classic(info, others);

         for(ChromeSlot slot : others) {
            if(Objects.equals(slot.authorValue(info), slot.resolvedValue(shadow))) {
               slot.clearAuthor(info);
               cleared++;
            }
         }
      }

      return cleared;
   }

   private static List<Object> resolveAll(List<ChromeSlot> slots, VSAssemblyInfo shadow) {
      List<Object> values = new ArrayList<>(slots.size());

      for(ChromeSlot slot : slots) {
         values.add(slot.resolvedValue(shadow));
      }

      return values;
   }

   /**
    * A clone of info as Revert and a render leave it, less the given author values.
    */
   private static VSAssemblyInfo classic(VSAssemblyInfo info, List<ChromeSlot> cleared) {
      VSAssemblyInfo shadow = info.clone();
      shadow.setVizMark(null);
      detachRuntimeAxes(shadow);

      for(ChromeSlot slot : cleared) {
         slot.clearAuthor(shadow);
      }

      shadow.seedChromeDefaults(VizContext.of(shadow));

      // seeds skip a format.css padding, which is then the classic one
      if(!shadow.isUserPadding()) {
         shadow.resetPadding(VizContext.of(shadow));
      }

      if(shadow instanceof ChartVSAssemblyInfo chart) {
         ChartFormatDefaults.fix(chart);
      }

      return shadow;
   }

   /**
    * Drops the runtime axes a clone shares with the original, which the CSS pass writes.
    */
   private static void detachRuntimeAxes(VSAssemblyInfo shadow) {
      VSChartInfo cinfo = shadow instanceof ChartVSAssemblyInfo chart ?
         chart.getVSChartInfo() : null;

      if(cinfo == null) {
         return;
      }

      cinfo.setRTAxisDescriptor(null);
      cinfo.setRTAxisDescriptor2(null);

      for(ChartRef[] refs : new ChartRef[][] { cinfo.getXFields(), cinfo.getRTXFields(),
                                              cinfo.getYFields(), cinfo.getRTYFields() })
      {
         for(ChartRef ref : refs) {
            if(ref instanceof VSChartRef vsRef) {
               vsRef.setRTAxisDescriptor(null);
            }
         }
      }
   }

   /**
    * Re-seed every target under the mark it already holds, stamping nothing. For a density change,
    * which moves no mark and so collects nothing through applyMark, but still has to re-fire the
    * density-derived control-height substitution and move a density size.
    */
   public static int reseed(Viewsheet vs) {
      List<VSAssemblyInfo> targets = collect(vs, info -> true);
      // mark is inert here: nothing is stamped, so every target keeps the one it has
      int seeded = seedAll(vs, null, targets, false, false);

      // the one re-seed that calls seedDensitySize; a restore, Modernize and Revert do not
      for(VSAssemblyInfo info : targets) {
         info.seedDensitySize(VizContext.of(info));
      }

      // an unmarked slider keeps the body it has
      restoreContainedSliders(marked(vs));
      keepOnBottomTabs(vs);
      return seeded;
   }

   /**
    * An expanded slider in a selection container stores its body, which a re-seed's padding or
    * lane moves; store it again as its next expand would. A collapsed one keeps its lane.
    */
   private static void restoreContainedSliders(List<VSAssemblyInfo> targets) {
      for(VSAssemblyInfo info : targets) {
         if(!(info instanceof TimeSliderVSAssemblyInfo slider) || slider.isHidden() ||
            !slider.isInSelectionContainer())
         {
            continue;
         }

         int height = slider.getVizMark() != null ? slider.getSliderBodyHeight() :
            slider.getListHeight() * AssetUtil.defh + slider.getTitleHeight();
         slider.setPixelSize(new Dimension(slider.getPixelSize().width, height));
      }
   }

   /**
    * A bottom-tabs strip sits under its children, and a re-seed can alter how tall a child stands
    * on it: a box a size rule resized, or a dropdown's title lane. Re-flush them all.
    */
   private static void keepOnBottomTabs(Viewsheet vs) {
      for(Assembly assembly : vs.getAssemblies(false)) {
         if(!(assembly instanceof TabVSAssembly tab) ||
            !((TabVSAssemblyInfo) tab.getVSAssemblyInfo()).getBottomTabsValue())
         {
            continue;
         }

         TabVSAssemblyInfo tabInfo = (TabVSAssemblyInfo) tab.getVSAssemblyInfo();
         String[] children = tab.getAssemblies();

         if(children == null) {
            continue;
         }

         for(String name : children) {
            if(vs.getAssembly(name) instanceof VSAssembly child) {
               TabVSAssemblyInfo.repositionChildForBottomTabs(
                  tabInfo, child.getVSAssemblyInfo(), child.getPixelSize());
            }
         }
      }
   }

   /**
    * Seeds every target, optionally stamping it first. A transition context may be built only when
    * a mark actually moves: it is what lets a seed replace the measure-to-colour ramp, a value an
    * author can reach, so a density change must not claim it.
    */
   private static int seedAll(Viewsheet vs, VizMark mark, List<VSAssemblyInfo> targets,
                              boolean stamp, boolean transition)
   {
      for(VSAssemblyInfo info : targets) {
         if(stamp) {
            info.setVizMark(mark);
         }

         info.seedChromeDefaults(transition ?
            VizContext.ofTransition(vs, info.getVizMark()) : VizContext.of(info));
      }

      if(!targets.isEmpty()) {
         // seeding rewrote chart colour frames, and a render clones the sheet's shared frame in
         // preference to an assembly's own, so the stale one has to go
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
    * seedChromeDefaults writes DEFAULT tiers and the palette, so a user format the restored state
    * legitimately carried survives untouched. A selection's seed also writes its card inset and a
    * seeded size, each guarded on the author's own value, so a selection marked before those
    * writes existed takes them here, on its first open.
    *
    * Not a transition: the mark has not changed here, so the context carries transition=false and
    * a seed reserved for Modernize and Revert - the measure-to-colour ramp, which replaces the
    * frame outright rather than rewriting defaults under it - does not run on this path.
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

   /**
    * reseedAfterRestore for an assembly in its sheet. When the seed changes how tall a bottom-tabs
    * child stands on its strip, the child shifts so its bottom edge stays put. A shift rather than
    * a re-flush, so a child off the strip keeps its distance from it, and one a script placed is
    * left alone.
    */
   public static void reseedRestored(VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      // measured only on a strip: a sheet's own size can lay it out
      boolean onStrip = info != null && !info.isPositionByScript() &&
         assembly.getViewsheet() != null && TabVSAssemblyInfo.isInBottomTabs(assembly);
      int before = onStrip ?
         TabVSAssemblyInfo.getBottomTabChildHeight(info, assembly.getPixelSize()) : 0;

      reseedAfterRestore(info);

      if(!onStrip) {
         return;
      }

      int dy = before - TabVSAssemblyInfo.getBottomTabChildHeight(info, assembly.getPixelSize());

      if(dy == 0) {
         return;
      }

      Point pos = info.getPixelOffset();

      if(pos != null) {
         info.setPixelOffset(new Point(pos.x, pos.y + dy));
      }

      if(info.getLayoutPosition() != null) {
         info.getLayoutPosition().translate(0, dy);
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
