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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.pairing.PairingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves spec #11's Hazard 1: a layout edit must never mutate the paired session's own
 * (Master) {@code Viewsheet} as a side effect.
 *
 * <p>{@code AbstractLayout.apply(Viewsheet)} deep-copies the {@code Viewsheet} argument's own
 * container object, but that copy is shallow ({@code AbstractSheet.clone()} is
 * {@code super.clone()}) -- the argument's assemblies, their {@code VSAssemblyInfo}, and their
 * {@code FormatInfo}/{@code VSCompositeFormat} objects are the *same* instances, not copies.
 * {@code apply()} then mutates those shared objects in place (layout position/size/visibility via
 * {@code applyBaseAssembly}, per-format {@code RScaleFont} via {@code applyScaleFont}) before
 * returning the new top-level container. Every layout service method that calls it
 * ({@code moveResizeLayoutObjects}, {@code layoutUndo}, {@code layoutRedo},
 * {@code changeViewsheetLayout}) then installs the result via {@code setViewsheet(...)}. Passing
 * the paired session's own master {@code Viewsheet} as that argument -- even if the *result* is
 * only ever installed onto a clone -- silently rewrites the master's own assemblies' layout
 * position and scale font as a side effect of computing that result.
 *
 * <p>The interactive Composer tolerates this because there is exactly one browser tab and one
 * runtime id shared between "editing Master" and "editing a layout": the human's own view is
 * expected to show layout-preview content while a layout tab is focused, and
 * {@code coreLifecycleService.layoutViewsheet(...)} restores true Master state when they switch
 * back. This plugin has no such mode switch -- the human's Composer must show the true Master at
 * all times while the agent works a layout in the background -- so this service deliberately
 * deviates from {@code changeViewsheetLayout}'s literal argument choice: every {@code apply()}
 * call here targets the <b>clone's own</b>, independently-loaded {@code Viewsheet} (obtained via
 * {@link ViewsheetService#openTemporaryViewsheet}, which reloads the persisted asset fresh and
 * therefore shares no object graph with the master), never the master's. The master's
 * {@code Viewsheet} is read only (via {@code VSLayoutService.findViewsheetLayout}) to locate the
 * layout definition to clone -- it is never handed to {@code apply()}.
 *
 * <p>One clone runtime is cached per {@code (sessionToken, layoutName)} (Global Constraint /
 * design decision: Option A in the layout implementation plan) -- reused across calls for the same
 * layout, flushed and re-minted on a genuine switch to a different layout for the same token. This
 * mirrors {@code changeViewsheetLayout}'s own reset rule
 * ({@code if(!layoutName.equals(currLayout)) { rvs.resetLayoutUndoRedo(); ... }}), which already
 * depends on remembering the previous layout -- a fresh mint on every call would have to reinvent
 * that bookkeeping to avoid resetting the layout undo stack on every single mutating call.
 *
 * <p>Per Global Constraint 4, the layout undo/redo checkpoint for a mutation goes through
 * {@link VSLayoutService#makeUndoable}, never {@code master.addCheckpoint(...)} -- that would
 * checkpoint a layout edit onto the viewsheet's main (Master) undo stack, corrupting it exactly as
 * spec #11's Hazard 2 describes.
 */
@Service
public class LayoutSessionService {
   @Autowired
   public LayoutSessionService(ViewsheetSessionService viewsheetSessions,
                                ViewsheetService viewsheetService,
                                VSLayoutService vsLayoutService)
   {
      this.viewsheetSessions = viewsheetSessions;
      this.viewsheetService = viewsheetService;
      this.vsLayoutService = vsLayoutService;
   }

   /**
    * Resolves the preview-clone runtime for {@code layoutName}, minting or reusing it exactly as
    * {@link #mutateLayout} would. A read never seeds a new baseline checkpoint itself (that would
    * make an undo step "appear from nowhere" for a caller that only ever calls {@code get_layout}),
    * but if resolving this read causes a genuine switch away from whatever layout (or lack of one)
    * was previously focused for this token, the master's layout undo/redo stack is still reset to
    * empty first -- see {@link #resolveClone}'s doc for why a read-triggered switch must reset the
    * stack just as a mutation-triggered one does, even though it must not go on to seed it.
    */
   public RuntimeViewsheet resolveForRead(String sessionToken, Principal agent, String layoutName)
      throws PairingException
   {
      RuntimeViewsheet master = viewsheetSessions.resolve(sessionToken, agent);

      try {
         return resolveClone(sessionToken, agent, master, layoutName, false).cloneRvs();
      }
      catch(PairingException | RuntimeException e) {
         throw e;
      }
      catch(Exception e) {
         throw new IllegalStateException(
            "Failed to resolve layout \"" + layoutName + "\" for reading", e);
      }
   }

   /**
    * Focuses the preview-clone runtime for {@code layoutName} -- mints, reuses, or switches
    * exactly as {@link #mutateLayout} does before running its mutation, including the
    * reset-and-seed-baseline effect a genuine switch has on the master's layout undo/redo stack --
    * without running any mutation of its own.
    *
    * <p>This plugin has no {@code RuntimeViewsheetRef.getFocusedLayoutName()} equivalent: the
    * interactive Composer's {@code layoutUndo}/{@code layoutRedo} trust the client's own focused
    * tab and never themselves cause a layout switch, but this plugin's {@code layout_undo}/
    * {@code layout_redo} tools take an explicit {@code layoutName} argument on every call instead,
    * so they must (re-)establish focus on that layout -- via this method -- before consulting the
    * master's undo stack, rather than trusting whatever layout that stack happens to still reflect
    * from a previous, unrelated call. Calling this when {@code layoutName} is already focused is a
    * pure no-op read (the early-return branch in {@link #resolveClone} short-circuits before the
    * reset/seed logic runs at all), so a normal "undo the edit I just made" call pays no extra cost.
    */
   public RuntimeViewsheet focusLayout(String sessionToken, Principal agent, String layoutName)
      throws Exception
   {
      RuntimeViewsheet master = viewsheetSessions.resolve(sessionToken, agent);
      return resolveClone(sessionToken, agent, master, layoutName, true).cloneRvs();
   }

   /**
    * Resolves the clone runtime for {@code layoutName} (minting/reusing/switching per the class
    * doc), runs {@code mutation} against it, then checkpoints via
    * {@link VSLayoutService#makeUndoable} -- never {@code master.addCheckpoint(...)}, per Global
    * Constraint 4.
    */
   public void mutateLayout(String sessionToken, Principal agent, String layoutName,
                             LayoutMutation mutation) throws Exception
   {
      RuntimeViewsheet master = viewsheetSessions.resolve(sessionToken, agent);
      Resolved resolved = resolveClone(sessionToken, agent, master, layoutName, true);

      CapturingCommandDispatcher.withCapturingDispatcher(agent, dispatcher -> {
         mutation.run(resolved.cloneRvs(), master, resolved.cloneRuntimeId(), dispatcher);
         vsLayoutService.makeUndoable(master, dispatcher, layoutName);
         return null;
      });
   }

   /**
    * Flushes the cached clone for {@code sessionToken}, if any, without touching any other
    * token's clone. Called from {@code ViewsheetAssemblyAgentController.detach} (the endpoint
    * every wiz viewsheet session already closes through -- see {@code detach_sheet}) so a layout
    * clone never outlives the session it was minted for.
    */
   public void disposeAll(String sessionToken) {
      ActiveClone active = clones.remove(sessionToken);

      if(active != null) {
         viewsheetService.flushRuntimeSheet(active.cloneRuntimeId());
      }
   }

   /**
    * Reuses the cached clone for {@code (sessionToken, layoutName)} if one is already active;
    * otherwise flushes whatever clone WAS active for this token (a genuine switch, including the
    * very first mint, which "switches" away from no clone at all) and mints a fresh one.
    *
    * <p>Every genuine switch resets the master's layout undo/redo stack unconditionally --
    * {@code layoutPoints} is one flat list per runtime, not one per layout (Hazard 2), so leaving
    * it as-is across a switch would let a caller "undo" into a checkpoint that belongs to whatever
    * layout was previously focused, or -- worse -- hand a {@code PrintLayout} checkpoint to
    * {@code updateVSLayouts} for a {@code ViewsheetLayout} name and blow up with a
    * {@code ClassCastException}. {@code checkpoint} only controls whether a fresh <em>baseline</em>
    * is then seeded ({@code addLayoutCheckPoint}): {@link #resolveForRead} (checkpoint {@code
    * false}) must not create an undo step of its own (a caller that only ever calls
    * {@code get_layout} should never see one appear from nowhere), so after the reset it leaves the
    * stack genuinely empty ({@code getLayoutPointsSize() == 0}) -- which is still exactly right for
    * {@code layout_undo}/{@code layout_redo} to treat as "nothing to undo yet" the next time this
    * layout is focused for editing.
    */
   private Resolved resolveClone(String sessionToken, Principal agent, RuntimeViewsheet master,
                                  String layoutName, boolean checkpoint) throws Exception
   {
      ActiveClone active = clones.get(sessionToken);

      if(active != null && active.layoutName().equals(layoutName)) {
         return new Resolved(viewsheetService.getViewsheet(active.cloneRuntimeId(), agent),
                              active.cloneRuntimeId());
      }

      if(active != null) {
         viewsheetService.flushRuntimeSheet(active.cloneRuntimeId());
         clones.remove(sessionToken);
      }

      String cloneRuntimeId = viewsheetService.openTemporaryViewsheet(
         master.getID(), (AssetEntry) master.getEntry().clone(), agent);
      RuntimeViewsheet clone = viewsheetService.getViewsheet(cloneRuntimeId, agent);
      clone.setOriginalID(master.getID());

      AbstractLayout found = vsLayoutService.findViewsheetLayout(master.getViewsheet(), layoutName)
         .orElseThrow(() -> new IllegalArgumentException(
            "Unknown layout \"" + layoutName + "\" -- call list_layouts to see the print and " +
            "device layouts defined on this viewsheet."));

      AbstractLayout layoutClone = found.clone();
      // Use scale font 1 when editing layouts, mirroring changeViewsheetLayout /
      // moveResizeLayoutObjects. Applied onto the CLONE's own Viewsheet, never the master's --
      // see the class doc for why that argument choice is the whole point of this service.
      layoutClone.setScaleFont(1);
      clone.setViewsheet(layoutClone.apply(clone.getViewsheet()));

      // Unconditional: see the class doc above for why a read-triggered switch must reset the
      // stack too, even though (per checkpoint) it must not go on to seed a baseline.
      master.resetLayoutUndoRedo();

      if(checkpoint) {
         master.addLayoutCheckPoint(layoutClone);
      }

      clones.put(sessionToken, new ActiveClone(layoutName, cloneRuntimeId));
      return new Resolved(clone, cloneRuntimeId);
   }

   /** One mutation against a layout's preview-clone runtime. */
   @FunctionalInterface
   public interface LayoutMutation {
      void run(RuntimeViewsheet clone, RuntimeViewsheet master, String cloneRuntimeId,
               CommandDispatcher dispatcher) throws Exception;
   }

   private record ActiveClone(String layoutName, String cloneRuntimeId) {
   }

   private record Resolved(RuntimeViewsheet cloneRvs, String cloneRuntimeId) {
   }

   private final ViewsheetSessionService viewsheetSessions;
   private final ViewsheetService viewsheetService;
   private final VSLayoutService vsLayoutService;
   private final Map<String, ActiveClone> clones = new ConcurrentHashMap<>();
}
