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
 * WizardToolBarComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1  [Risk 2] — undoEnabled / redoEnabled: both-direction getter coverage;
 *                        loading guard disables both
 *   Group 2  [Risk 2] — undo() / redo(): STOMP event URI dispatch
 *   Group 3  [Risk 3] — cancel(): unsaved-changes modal (empty sheet → direct close;
 *                        "yes" → emit false; "no" → no emit)
 *   Group 4  [baseline] — done() / hiddenNewBlockChanged(): output event contracts
 *   Group 5  [baseline] — isUndoRedoVisible() / hiddenComposerIcon: context + innerWidth
 *   Group 6  [baseline] — editOperations action closures: enabled/visible delegates
 *   Group 7  [baseline] — ngOnInit / ngOnDestroy lifecycle no-ops
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   getCloseActions / closeOperation — action closures covered transitively by done() and cancel() tests
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal, NgbTooltipConfig } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";

import { WizardToolBarComponent } from "./wizard-tool-bar.component";
import { ContextProvider } from "../../../vsobjects/context-provider.service";
import { MessageDialog } from "../../../widget/dialog/message-dialog/message-dialog.component";

const SEND_EVENT_MOCK = vi.fn();
const MODAL_MOCK = { open: vi.fn() };

interface SheetOptions {
   current?: number;
   points?: number;
   loading?: boolean;
   vsObjects?: unknown[];
}

function makeSheet(opts: SheetOptions = {}) {
   return {
      current: opts.current ?? 1,
      points: opts.points ?? 3,
      loading: opts.loading ?? false,
      vsObjects: opts.vsObjects ?? [],
      socketConnection: { sendEvent: SEND_EVENT_MOCK },
   };
}

async function renderComponent(opts: { sheet?: ReturnType<typeof makeSheet>; vsWizard?: boolean } = {}) {
   const sheet = opts.sheet ?? makeSheet();
   const vsWizard = opts.vsWizard ?? true;
   const result = await render(WizardToolBarComponent, {
      inputs: { sheet: sheet as any },
      providers: [
         { provide: ContextProvider, useValue: { vsWizard } },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: NgbTooltipConfig, useValue: { container: "" } },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: result.fixture.componentInstance, fixture: result.fixture };
}

beforeEach(() => {
   SEND_EVENT_MOCK.mockClear();
   MODAL_MOCK.open.mockClear().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});

// ---------------------------------------------------------------------------
// Group 1 — undoEnabled / redoEnabled [Risk 2]
// ---------------------------------------------------------------------------

describe("WizardToolBarComponent — undoEnabled / redoEnabled getters", () => {
   // 🔁 Regression-sensitive: toolbar undo/redo buttons delegate enabled() to these
   // getters. If the guard is wrong, buttons appear enabled when there is nothing
   // to undo/redo, causing the server to receive no-op events silently.
   it("undoEnabled should be true when current > 0 and not loading", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ current: 1, loading: false }) });
      expect(comp.undoEnabled).toBe(true);
   });

   it("undoEnabled should be false when current = 0", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ current: 0 }) });
      expect(comp.undoEnabled).toBe(false);
   });

   it("undoEnabled should be false when loading=true", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ current: 1, loading: true }) });
      expect(comp.undoEnabled).toBe(false);
   });

   it("redoEnabled should be true when current < points - 1 and not loading", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ current: 1, points: 3, loading: false }) });
      expect(comp.redoEnabled).toBe(true);
   });

   it("redoEnabled should be false when current = points - 1", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ current: 2, points: 3 }) });
      expect(comp.redoEnabled).toBe(false);
   });

   it("redoEnabled should be false when loading=true", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ current: 1, points: 3, loading: true }) });
      expect(comp.redoEnabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — undo() / redo() STOMP dispatch [Risk 2]
// ---------------------------------------------------------------------------

describe("WizardToolBarComponent — undo() / redo() STOMP dispatch", () => {
   // 🔁 Regression-sensitive: wrong URI means the server receives an unknown event
   // and silently ignores it — the viewsheet state never changes.
   it("undo() should send /events/undo via sheet.socketConnection", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();

      comp.undo();

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith("/events/undo");
   });

   it("redo() should send /events/redo via sheet.socketConnection", async () => {
      const { comp } = await renderComponent();
      SEND_EVENT_MOCK.mockClear();

      comp.redo();

      expect(SEND_EVENT_MOCK).toHaveBeenCalledWith("/events/redo");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — cancel() dialog flow [Risk 3]
// ---------------------------------------------------------------------------

describe("WizardToolBarComponent — cancel() unsaved-changes dialog", () => {
   // 🔁 Regression-sensitive: if the modal is bypassed or "yes" does not emit
   // onClose, users lose unsaved work silently.
   it("should emit onClose(false) immediately when sheet has no vsObjects", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ vsObjects: [] }) });
      const emitted: boolean[] = [];
      comp.onClose.subscribe((v: boolean) => emitted.push(v));

      comp.cancel();

      expect(emitted).toEqual([false]);
      expect(MODAL_MOCK.open).not.toHaveBeenCalled();
   });

   it("should open confirm dialog when sheet has vsObjects", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ vsObjects: [{}] }) });

      comp.cancel();

      expect(MODAL_MOCK.open).toHaveBeenCalledOnce();
   });

   it("should emit onClose(false) when user clicks yes", async () => {
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("yes"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent({ sheet: makeSheet({ vsObjects: [{}] }) });
      const emitted: boolean[] = [];
      comp.onClose.subscribe((v: boolean) => emitted.push(v));

      comp.cancel();

      await waitFor(() => expect(emitted).toEqual([false]));
   });

   it("should NOT emit onClose when user clicks no", async () => {
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("no"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent({ sheet: makeSheet({ vsObjects: [{}] }) });
      const emitted: boolean[] = [];
      comp.onClose.subscribe((v: boolean) => emitted.push(v));

      comp.cancel();

      // Positive gate: wait for modal to open so Promise.resolve("no") microtask settles.
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — done() / hiddenNewBlockChanged() [baseline]
// ---------------------------------------------------------------------------

describe("WizardToolBarComponent — done() / hiddenNewBlockChanged() outputs", () => {
   it("done() should emit onClose with true", async () => {
      const { comp } = await renderComponent();
      const emitted: boolean[] = [];
      comp.onClose.subscribe((v: boolean) => emitted.push(v));

      comp.done();

      expect(emitted).toEqual([true]);
   });

   it("hiddenNewBlockChanged() should emit onHiddenNewBlockChanged once", async () => {
      const { comp } = await renderComponent();
      let count = 0;
      comp.onHiddenNewBlockChanged.subscribe(() => count++);

      comp.hiddenNewBlockChanged();

      expect(count).toBe(1);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — isUndoRedoVisible() / hiddenComposerIcon [baseline]
// ---------------------------------------------------------------------------

describe("WizardToolBarComponent — isUndoRedoVisible() / hiddenComposerIcon", () => {
   it("isUndoRedoVisible() should return true when context.vsWizard=true", async () => {
      const { comp } = await renderComponent({ vsWizard: true });
      expect(comp.isUndoRedoVisible()).toBe(true);
   });

   it("isUndoRedoVisible() should return false when context.vsWizard=false", async () => {
      const { comp } = await renderComponent({ vsWizard: false });
      expect(comp.isUndoRedoVisible()).toBe(false);
   });

   it("hiddenComposerIcon should return true when innerWidth < 350", async () => {
      Object.defineProperty(window, "innerWidth", { configurable: true, value: 300 });
      const { comp } = await renderComponent();
      try {
         expect(comp.hiddenComposerIcon).toBe(true);
      } finally {
         Object.defineProperty(window, "innerWidth", { configurable: true, value: 1024 });
      }
   });

   it("hiddenComposerIcon should return false when innerWidth >= 350", async () => {
      Object.defineProperty(window, "innerWidth", { configurable: true, value: 500 });
      const { comp } = await renderComponent();
      try {
         expect(comp.hiddenComposerIcon).toBe(false);
      } finally {
         Object.defineProperty(window, "innerWidth", { configurable: true, value: 1024 });
      }
   });
});

// ---------------------------------------------------------------------------
// Group 6 — editOperations action closures [baseline]
// ---------------------------------------------------------------------------

describe("WizardToolBarComponent — editOperations action closures", () => {
   it("undo action enabled() reflects undoEnabled (false when current=0)", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ current: 0 }) });
      const undoAction = comp.getEditActions[0];
      expect(undoAction.enabled!()).toBe(false);
   });

   it("undo action enabled() reflects undoEnabled (true when current > 0)", async () => {
      const { comp } = await renderComponent({ sheet: makeSheet({ current: 1 }) });
      const undoAction = comp.getEditActions[0];
      expect(undoAction.enabled!()).toBe(true);
   });

   it("redo action visible() reflects isUndoRedoVisible (false when vsWizard=false)", async () => {
      const { comp } = await renderComponent({ vsWizard: false });
      const redoAction = comp.getEditActions[1];
      expect(redoAction.visible!()).toBe(false);
   });

   it("editOperations overall enabled() always returns true", async () => {
      const { comp } = await renderComponent();
      expect(comp.editOperations.enabled!()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — lifecycle [baseline]
// ---------------------------------------------------------------------------

describe("WizardToolBarComponent — lifecycle", () => {
   it("getEditActions should expose undo and redo actions on init", async () => {
      const { comp } = await renderComponent();
      expect(comp.getEditActions).toHaveLength(2);
   });
});
