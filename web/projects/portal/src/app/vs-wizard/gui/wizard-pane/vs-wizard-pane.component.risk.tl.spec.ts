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

/**
 * VsWizardPane — Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — remove() / removeWizardObject(): sends RemoveVSObjectsEvent with
 *                        current grid dimensions; missing dimensions silently breaks server
 *                        layout recalculation
 *   Group 2  [Risk 3] — processRemoveVSObjectCommand: removes matching object from vsObjects;
 *                        non-matching object is NOT removed
 *   Group 3  [Risk 3] — clearFocused(): clears selection when mouse-up lands on grid cell or
 *                        wizard-add area; does NOT clear when target is another element
 *   Group 4  [Risk 3] — removeKeydownListener: keydown listener is deregistered after
 *                        ngOnDestroy so no stale events fire after the component is gone
 *   Group 5  [Risk 2] — moveable(): returns false when any selected assembly is at the boundary
 *                        in the move direction (prevents off-canvas positioning)
 *   Group 6  [Risk 2] — moveWizardObject(): key-move path (keyMove=true) sends MoveVSObjectEvent
 *                        via eventQueueService with correct name and grid dimensions
 *   Group 7  [Risk 2] — moveAssembly(): delegates to processAssemblyMoved; scrollPane is null
 *                        (no DOM) so the function returns without setTimeout (no async leak)
 *
 * Confirmed bugs (it.fails): none
 *
 * Suspected bugs (header only):
 *   Suspicion A — processAssemblyMoved: setTimeout-based auto-scroll reads from scrollContainer
 *     which is 0-height in jsdom, so autoDown/autoRight/autoLeft/autoUp are always false and the
 *     timer never fires.  In production the behaviour is correct; the test environment cannot
 *     distinguish a "nothing to scroll" correct state from an "scroll disabled" regression.
 *
 * Out of scope this pass:
 *   processAssemblyMoved() auto-scroll — scrollPane clientHeight/scrollTop are 0 in jsdom;
 *     all auto-scroll conditions evaluate to false; setTimeout never fires; untestable without
 *     a real layout engine
 *   moveAssembly() dx/dy branch logic — depends on event.interaction.pointerDelta which is a
 *     real interact.js event; the branch only affects scroll direction, not WS event content
 *   dragResizeStart with withShift=true + isDrag=true — depends on
 *     prepareBottomFollowAssemblies / prepareRightFollowAssemblies which use querySelector
 *     (returns null in jsdom for dynamic content)
 */

import {
   CLIENT_SERVICE_MOCK,
   EVENT_QUEUE_MOCK,
   resetMocks,
   makeVSObject,
   renderComponent,
} from "./vs-wizard-pane.test-fixtures";
import { RemoveVSObjectCommand } from "../../../vsobjects/command/remove-vs-object-command";
import { KeyCodeValue } from "../../../../../../shared/util/key-code-value";

beforeEach(() => {
   resetMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — remove() / removeWizardObject() [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardPane — remove() / removeWizardObject()", () => {
   // 🔁 Regression-sensitive: RemoveVSObjectsEvent must carry wizardGridRows and wizardGridCols
   // so the server can recalculate the layout after removal. If they are 0 the server uses wrong
   // dimensions and the wizard grid shrinks or grows incorrectly.
   it("should send RemoveVSObjectsEvent with current gridRowCount and gridColCount", async () => {
      const { comp } = await renderComponent();
      comp.gridRowCount = 10;
      comp.gridColCount = 8;
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.remove(["Chart1"]);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/vswizard/object/remove",
         expect.objectContaining({ wizardGridRows: 10, wizardGridCols: 8 })
      );
   });

   it("should include the given object names in the remove event", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.remove(["Chart1", "Table1"]);

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/vswizard/object/remove",
         expect.objectContaining({ objectNames: ["Chart1", "Table1"] })
      );
   });

   it("removeWizardObject() should call remove() with the given object name", async () => {
      const { comp } = await renderComponent();
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.removeWizardObject("Chart1");

      expect(CLIENT_SERVICE_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/vswizard/object/remove",
         expect.objectContaining({ objectNames: ["Chart1"] })
      );
   });
});

// ---------------------------------------------------------------------------
// Group 2 — processRemoveVSObjectCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardPane — processRemoveVSObjectCommand", () => {
   // 🔁 Regression-sensitive: if the removed object is not spliced from vsObjects, it stays
   // visible as a ghost on the wizard grid even though the server has deleted it.
   it("should remove the matching vsObject from vsObjects", async () => {
      const { comp } = await renderComponent();
      comp.viewsheet.vsObjects.push(makeVSObject({ absoluteName: "Chart1" }));
      comp.viewsheet.vsObjects.push(makeVSObject({ absoluteName: "Table1" }));

      const cmd: RemoveVSObjectCommand = { name: "Chart1" } as any;
      (comp as any)["processRemoveVSObjectCommand"](cmd);

      expect(comp.viewsheet.vsObjects.map(o => o.absoluteName)).not.toContain("Chart1");
      expect(comp.viewsheet.vsObjects.map(o => o.absoluteName)).toContain("Table1");
   });

   it("should NOT remove objects whose name does not match", async () => {
      const { comp } = await renderComponent();
      comp.viewsheet.vsObjects.push(makeVSObject({ absoluteName: "Table1" }));

      const cmd: RemoveVSObjectCommand = { name: "Chart1" } as any;
      (comp as any)["processRemoveVSObjectCommand"](cmd);

      expect(comp.viewsheet.vsObjects).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — clearFocused() / shouldClearFocused() [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardPane — clearFocused()", () => {
   // 🔁 Regression-sensitive: clicking an empty grid cell must clear the selection so the user
   // can start a fresh lasso. If clearFocused is skipped for grid cells, previously selected
   // objects appear highlighted with no way to deselect them.
   it("should clear focused assemblies when event target has class 'vs-grid-table-cell'", async () => {
      const { comp } = await renderComponent();
      const obj = makeVSObject({ absoluteName: "Chart1" });
      comp.viewsheet.vsObjects.push(obj);
      comp.viewsheet.currentFocusedAssemblies = [obj];

      const target = document.createElement("div");
      target.classList.add("vs-grid-table-cell");
      comp.clearFocused({ target });

      expect(comp.viewsheet.currentFocusedAssemblies).toHaveLength(0);
   });

   it("should clear focused assemblies when event target has class 'wizard-add'", async () => {
      const { comp } = await renderComponent();
      const obj = makeVSObject({ absoluteName: "Chart1" });
      comp.viewsheet.currentFocusedAssemblies = [obj];

      const target = document.createElement("div");
      target.classList.add("wizard-add");
      comp.clearFocused({ target });

      expect(comp.viewsheet.currentFocusedAssemblies).toHaveLength(0);
   });

   it("should NOT clear focused assemblies when event target is a regular div", async () => {
      const { comp } = await renderComponent();
      const obj = makeVSObject({ absoluteName: "Chart1" });
      comp.viewsheet.currentFocusedAssemblies = [obj];

      const target = document.createElement("div"); // no relevant class
      comp.clearFocused({ target });

      expect(comp.viewsheet.currentFocusedAssemblies).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — removeKeydownListener after ngOnDestroy [Risk 3]
// ---------------------------------------------------------------------------

describe("VsWizardPane — removeKeydownListener after ngOnDestroy", () => {
   // 🔁 Regression-sensitive: if the keydown listener is not removed on destroy, keyboard events
   // on the document still reach the destroyed component's onKeydown handler. Pressing Ctrl+Z
   // after the wizard closes would trigger an undo on a non-existent viewsheet session.
   it("should deregister the keydown listener so Ctrl+Z no longer fires sendEvent after destroy", async () => {
      const { comp, fixture } = await renderComponent();
      (comp as any)["processUpdateUndoStateCommand"]({ points: 3, current: 1, savePoint: 0 });
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      fixture.destroy(); // triggers ngOnDestroy → removeKeydownListener

      // Simulate keydown on document after destroy — should not reach the handler
      document.dispatchEvent(new KeyboardEvent("keydown", { keyCode: KeyCodeValue.Z, ctrlKey: true, bubbles: true }));

      expect(CLIENT_SERVICE_MOCK.sendEvent).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: Bug #75687 — the mouseup listener registered in ngOnInit
   // (this.renderer.listen("document", "mouseup", ...)) was never captured/removed in
   // ngOnDestroy, unlike the keydown listener above. Every wizard-pane open/close cycle left a
   // permanent listener on document bound to the destroyed instance, which fired clearFocused()
   // on stale state for every later mouseup anywhere in the app.
   it("should deregister the mouseup listener so clearFocused no longer fires after destroy", async () => {
      const { comp, fixture } = await renderComponent();
      const clearFocusedSpy = vi.spyOn(comp, "clearFocused");

      fixture.destroy(); // triggers ngOnDestroy → mouseupListener()

      document.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));

      expect(clearFocusedSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — moveable() [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardPane — moveable()", () => {
   // 🔁 Regression-sensitive: moveable() guards all arrow-key movement. Returning true when an
   // assembly is at the boundary (top=0 for UP, left=0 for LEFT) allows off-canvas positioning.
   it("should return false for LEFT when any selected assembly has left=0", async () => {
      const { comp } = await renderComponent();
      const atBoundary = makeVSObject({ absoluteName: "Chart1", objectFormat: { top: 10, left: 0, width: 100, height: 50 } as any });
      comp.viewsheet.currentFocusedAssemblies = [atBoundary];

      expect(comp.moveable(KeyCodeValue.LEFT)).toBe(false);
   });

   it("should return true for LEFT when no selected assembly has left=0", async () => {
      const { comp } = await renderComponent();
      const notAtBoundary = makeVSObject({ absoluteName: "Chart1", objectFormat: { top: 10, left: 50, width: 100, height: 50 } as any });
      comp.viewsheet.currentFocusedAssemblies = [notAtBoundary];

      expect(comp.moveable(KeyCodeValue.LEFT)).toBe(true);
   });

   it("should return false for UP when any selected assembly has top=0", async () => {
      const { comp } = await renderComponent();
      const atTop = makeVSObject({ absoluteName: "Chart1", objectFormat: { top: 0, left: 10, width: 100, height: 50 } as any });
      comp.viewsheet.currentFocusedAssemblies = [atTop];

      expect(comp.moveable(KeyCodeValue.UP)).toBe(false);
   });

   it("should return true for RIGHT regardless of position (no boundary check for right)", async () => {
      const { comp } = await renderComponent();
      const obj = makeVSObject({ absoluteName: "Chart1" });
      comp.viewsheet.currentFocusedAssemblies = [obj];

      expect(comp.moveable(KeyCodeValue.RIGHT)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — moveWizardObject() key-move path [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardPane — moveWizardObject() key-move path", () => {
   // 🔁 Regression-sensitive: when keyMove=true the handler must call eventQueueService rather
   // than the interact.js event branch. If the wrong branch fires, the server never receives the
   // move and the object snaps back to its original position.
   it("should call eventQueueService.addWizardMoveEvent with the vsObject name when keyMove=true", async () => {
      const { comp } = await renderComponent();
      const vsObj = makeVSObject({ absoluteName: "Chart1" });
      EVENT_QUEUE_MOCK.addWizardMoveEvent.mockClear();

      comp.moveWizardObject({ model: vsObj }, true);

      expect(EVENT_QUEUE_MOCK.addWizardMoveEvent).toHaveBeenCalledWith(
         CLIENT_SERVICE_MOCK,
         expect.objectContaining({ name: "Chart1" })
      );
   });

   it("should include current gridRowCount and gridColCount in the move event", async () => {
      const { comp } = await renderComponent();
      // refreshGridRows() reads offsetHeight (=0 in jsdom) and resets gridRowCount to 0.
      // moveWizardObject(keyMove=true) calls refreshGridRows() before building the event, so
      // we must suppress it here to keep the gridRowCount we set.
      const spy = vi.spyOn(comp, "refreshGridRows").mockImplementation(() => {});
      try {
         comp.gridRowCount = 12;
         comp.gridColCount = 8;
         const vsObj = makeVSObject({ absoluteName: "Chart1" });
         EVENT_QUEUE_MOCK.addWizardMoveEvent.mockClear();

         comp.moveWizardObject({ model: vsObj }, true);

         expect(EVENT_QUEUE_MOCK.addWizardMoveEvent).toHaveBeenCalledWith(
            CLIENT_SERVICE_MOCK,
            expect.objectContaining({ wizardGridRows: 12, wizardGridCols: 8 })
         );
      } finally {
         spy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 7 — moveAssembly() / processAssemblyMoved() null scrollPane path [Risk 2]
// ---------------------------------------------------------------------------

describe("VsWizardPane — moveAssembly() when scrollContainer has no layout", () => {
   // 🔁 Regression-sensitive: processAssemblyMoved reads scrollContainer.nativeElement's
   // layout properties.  In jsdom these are 0 so autoDown/autoRight etc. are always false and
   // no setTimeout fires.  The important contract is that calling moveAssembly() does NOT call
   // sendEvent — that is the moveWizardObject path, not the assembly-drag path.
   it("should not call sendEvent when moveAssembly is called with a null event", async () => {
      const { comp } = await renderComponent();
      const vsObj = makeVSObject({ absoluteName: "Chart1" });
      // changeRows() guards with `model.absoluteName == this.maxYSelectedAssembly.absoluteName`.
      // maxYSelectedAssembly is only populated via the focusedAssemblies subscription; it is
      // undefined when no assemblies have been focused yet.  Set it directly so changeRows
      // does not throw before reaching any assertions.
      (comp as any).maxYSelectedAssembly = vsObj;
      (comp as any).maxXSelectedAssembly = vsObj;
      CLIENT_SERVICE_MOCK.sendEvent.mockClear();

      comp.moveAssembly(null, vsObj);

      // moveAssembly delegates to processAssemblyMoved which never calls sendEvent;
      // sendEvent is only called from moveWizardObject() after processAssemblyMoved returns
      expect(CLIENT_SERVICE_MOCK.sendEvent).not.toHaveBeenCalled();
   });
});
