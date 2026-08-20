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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 3 of the layout implementation plan (2026-08-20-layout-implementation.md), Task 5:
 * {@code layout_undo}/{@code layout_redo}.
 *
 * <h2>Why this does not delegate to {@code VSLayoutControllerService.layoutUndo}/{@code
 * layoutRedo} as-is</h2>
 *
 * <p>Both bodies are already public and reachable off {@code VSLayoutControllerServiceProxy}
 * (same finding as {@link LayoutMutationService}'s class doc for its own siblings), but calling
 * them with this plugin's runtime ids would reproduce Hazard 1: they resolve the historical
 * {@code AbstractLayout} off the <b>master</b>'s own undo stack (correct -- that stack lives on
 * the master, not any clone) but then call {@code apply()} directly on the <b>master's own live
 * {@code Viewsheet}</b> (via {@code parentRvs.getViewsheet()}), exactly the corruption {@link
 * LayoutSessionService}'s class doc exists to prevent:
 *
 * <pre>{@code
 * // VSLayoutControllerService.layoutUndo
 * AbstractLayout layoutClone = (AbstractLayout) parentRvs.layoutUndo().clone();
 * this.vsLayoutService.updateVSLayouts(parentRvs, layoutClone, layoutName);
 * rvs.setViewsheet(layoutClone.apply(parentRvs.getViewsheet()));   // <-- apply() on the MASTER
 * }</pre>
 *
 * <p>The fix mirrors {@link LayoutMutationService}'s: call the master's undo-stack primitives
 * ({@code RuntimeViewsheet.layoutUndo()}/{@code layoutRedo()} -- pure bookkeeping, no {@code
 * apply()} inside them) and {@link VSLayoutService#updateVSLayouts} (a {@code LayoutInfo} write,
 * not an {@code apply()} call) directly against the master, exactly as the interactive path does
 * for those two steps -- then redirect only the {@code apply()} call itself onto {@link
 * LayoutSessionService}'s clone instead of the master.
 *
 * <h2>Why this does not go through {@link LayoutSessionService#mutateLayout}</h2>
 *
 * <p>{@code mutateLayout} unconditionally calls {@link VSLayoutService#makeUndoable} (which itself
 * calls {@code addLayoutCheckPoint}) once its mutation lambda returns -- exactly right for a new
 * edit, but wrong for undo/redo: {@code addLayoutCheckPoint} truncates anything after the current
 * pointer and appends a fresh entry there. Running undo/redo's own pointer move through {@code
 * mutateLayout} would immediately re-push a checkpoint identical to the one just moved to,
 * silently discarding the rest of the redo (for undo) or corrupting the pointer bookkeeping (for
 * redo) on every single call -- one undo would work, but a second undo, or any redo, would not.
 * This class instead uses {@link LayoutSessionService#focusLayout}, which performs the same
 * mint/reuse/switch resolution {@code mutateLayout} does but runs no mutation and takes no
 * checkpoint of its own -- the undo-stack pointer move IS this call's only state change.
 *
 * <h2>Hazard 2 -- the explicit {@code layoutName}, and why every call re-focuses first</h2>
 *
 * <p>This plugin has no {@code RuntimeViewsheetRef.getFocusedLayoutName()} equivalent (the
 * interactive client tracks which layout tab is focused and never itself causes a layout switch
 * from inside {@code layoutUndo}/{@code layoutRedo}); callers here pass {@code layoutName}
 * explicitly on every call instead. Because {@code RuntimeViewsheet.layoutPoints} is one flat
 * stack per runtime, not one per layout, this class must (re-)establish {@code layoutName} as
 * focused -- via {@link LayoutSessionService#focusLayout}, which resets the stack on a genuine
 * switch -- <em>before</em> reading {@code getLayoutPoint()}/{@code getLayoutPointsSize()} or
 * calling {@code layoutUndo()}/{@code layoutRedo()}. Skipping that and consulting the master's
 * stack first would let a caller "undo" into a checkpoint that belongs to whatever layout was
 * previously focused for this session -- the exact stale-cross-layout-entry failure Hazard 2
 * warns about -- and, when the previously-focused layout was a print layout and the requested one
 * is a device layout (or vice versa), {@code updateVSLayouts} would try to cast that stale
 * checkpoint to the wrong {@code AbstractLayout} subtype and throw {@code ClassCastException}.
 *
 * <p>Per the spec's own error-handling table, undoing/redoing past the available history for the
 * current focus is a no-op, not an error -- {@code RuntimeViewsheet.layoutUndo()}/{@code
 * layoutRedo()} do not bounds-check themselves (the interactive client instead disables the
 * button; this plugin has no such client), so this class checks {@code getLayoutPoint()} against
 * the stack bounds itself before ever calling them.
 */
@Service
public class LayoutUndoService {
   @Autowired
   public LayoutUndoService(LayoutSessionService layoutSessions,
                             ViewsheetSessionService viewsheetSessions,
                             VSLayoutService vsLayoutService)
   {
      this.layoutSessions = layoutSessions;
      this.viewsheetSessions = viewsheetSessions;
      this.vsLayoutService = vsLayoutService;
   }

   /**
    * {@code layout_undo}: reverts {@code layoutName} to its state one edit before the current
    * one, scoped to edits made since this layout was last focused in this session (Hazard 2). A
    * no-op (not an error) when there is nothing earlier to revert to for the current focus.
    */
   public Map<String, Object> layoutUndo(String sessionToken, Principal agent, String layoutName)
      throws Exception
   {
      return move(sessionToken, agent, layoutName, Direction.UNDO);
   }

   /**
    * {@code layout_redo}: reapplies the edit most recently undone for {@code layoutName}. A no-op
    * (not an error) when there is nothing later to reapply.
    */
   public Map<String, Object> layoutRedo(String sessionToken, Principal agent, String layoutName)
      throws Exception
   {
      return move(sessionToken, agent, layoutName, Direction.REDO);
   }

   private Map<String, Object> move(String sessionToken, Principal agent, String layoutName,
                                     Direction direction) throws Exception
   {
      RuntimeViewsheet master = viewsheetSessions.resolve(sessionToken, agent);

      // Re-focus BEFORE consulting the stack -- see the class doc's Hazard 2 section for why
      // this order matters: focusLayout resets the stack on a genuine switch, so only after this
      // call does master.getLayoutPoint()/getLayoutPointsSize() genuinely describe layoutName's
      // own history rather than whatever layout was previously focused for this token.
      RuntimeViewsheet clone = layoutSessions.focusLayout(sessionToken, agent, layoutName);

      if(!direction.available(master)) {
         return result(false, master);
      }

      AbstractLayout layoutClone = (AbstractLayout) direction.pop(master).clone();
      vsLayoutService.updateVSLayouts(master, layoutClone, layoutName);
      // apply() onto the CLONE's own Viewsheet only -- never the master's, per Hazard 1 (see the
      // class doc).
      clone.setViewsheet(layoutClone.apply(clone.getViewsheet()));

      return result(true, master);
   }

   private static Map<String, Object> result(boolean applied, RuntimeViewsheet master) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("applied", applied);
      result.put("layoutPoint", master.getLayoutPoint());
      result.put("layoutPoints", master.getLayoutPointsSize());
      return result;
   }

   /**
    * {@code RuntimeViewsheet.layoutUndo()}/{@code layoutRedo()} do not bounds-check the pointer
    * move themselves (see the class doc), so {@code available} must be checked first.
    */
   private enum Direction {
      UNDO {
         @Override
         boolean available(RuntimeViewsheet master) {
            return master.getLayoutPoint() > 0;
         }

         @Override
         AbstractLayout pop(RuntimeViewsheet master) {
            return master.layoutUndo();
         }
      },
      REDO {
         @Override
         boolean available(RuntimeViewsheet master) {
            return master.getLayoutPoint() < master.getLayoutPointsSize() - 1;
         }

         @Override
         AbstractLayout pop(RuntimeViewsheet master) {
            return master.layoutRedo();
         }
      };

      abstract boolean available(RuntimeViewsheet master);
      abstract AbstractLayout pop(RuntimeViewsheet master);
   }

   private final LayoutSessionService layoutSessions;
   private final ViewsheetSessionService viewsheetSessions;
   private final VSLayoutService vsLayoutService;
}
