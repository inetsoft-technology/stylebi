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
 * VSLoadingDisplay — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngAfterViewInit: 2 s timer sets showCancelLoading / showSwitchToMeta
 *   Group 2 [Risk 3] — ngAfterViewInit: timer skipped entirely when showIcon=false
 *   Group 3 [Risk 3] — ngOnDestroy: clears timeout so callback does not fire after destroy
 *   Group 4 [Risk 2] — cancelViewsheetLoading: sets loadingCanceled=true and emits cancelLoading
 *   Group 5 [Risk 2] — switchToMetaMode: sets switchingToMeta=true and emits switchToMeta
 *   Group 6 [Risk 2] — getBtnsPosition: "space-between" only when both visible, "center" otherwise
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { VSLoadingDisplay } from "./vs-loading-display.component";

async function renderComponent(props: Partial<VSLoadingDisplay> = {}) {
   const { fixture } = await render(VSLoadingDisplay, {
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         showIcon: true,
         autoShowCancel: true,
         autoShowMetaButton: false,
         ...props,
      },
   });
   return fixture.componentInstance as VSLoadingDisplay;
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngAfterViewInit 2 s timer [Risk 3]
// ---------------------------------------------------------------------------

describe("VSLoadingDisplay — ngAfterViewInit timer", () => {
   // 🔁 Regression-sensitive: cancel button must appear after 2 s so users can escape
   //    stuck viewsheets; regression leaves the UI with no escape for long-loading sheets.
   it("should set showCancelLoading to true after 2 s when autoShowCancel=true", async () => {
      vi.useFakeTimers();
      try {
         const comp = await renderComponent({ showIcon: true, autoShowCancel: true });
         expect(comp.showCancelLoading).toBe(false);

         vi.advanceTimersByTime(2000);

         expect(comp.showCancelLoading).toBe(true);
      } finally {
         vi.useRealTimers();
      }
   });

   it("should set showSwitchToMeta to true after 2 s when autoShowMetaButton=true", async () => {
      vi.useFakeTimers();
      try {
         const comp = await renderComponent({ showIcon: true, autoShowMetaButton: true });

         vi.advanceTimersByTime(2000);

         expect(comp.showSwitchToMeta).toBe(true);
      } finally {
         vi.useRealTimers();
      }
   });

   it("should set both flags after 2 s when both autoShowCancel and autoShowMetaButton are true", async () => {
      vi.useFakeTimers();
      try {
         const comp = await renderComponent({ showIcon: true, autoShowCancel: true, autoShowMetaButton: true });

         vi.advanceTimersByTime(2000);

         expect(comp.showCancelLoading).toBe(true);
         expect(comp.showSwitchToMeta).toBe(true);
      } finally {
         vi.useRealTimers();
      }
   });

   it("should not set showCancelLoading before the 2 s timer fires", async () => {
      vi.useFakeTimers();
      try {
         const comp = await renderComponent({ showIcon: true, autoShowCancel: true });

         vi.advanceTimersByTime(1999);

         expect(comp.showCancelLoading).toBe(false);
      } finally {
         vi.useRealTimers();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 2: timer skipped when showIcon=false [Risk 3]
// ---------------------------------------------------------------------------

describe("VSLoadingDisplay — timer skipped when showIcon=false", () => {
   // 🔁 Regression-sensitive: the icon-only mode must not show cancel/meta buttons at all;
   //    removing the showIcon guard would show buttons in the wrong context.
   it("should not set showCancelLoading after 2 s when showIcon=false", async () => {
      vi.useFakeTimers();
      try {
         const comp = await renderComponent({ showIcon: false, autoShowCancel: true });

         vi.advanceTimersByTime(2000);

         expect(comp.showCancelLoading).toBe(false);
      } finally {
         vi.useRealTimers();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnDestroy clears timeout [Risk 3]
// ---------------------------------------------------------------------------

describe("VSLoadingDisplay — ngOnDestroy memory leak", () => {
   // 🔁 Regression-sensitive: if clearTimeout is removed, the 2 s callback fires after the
   //    component is destroyed and calls detectChanges() on a destroyed ref → NG0205.
   it("should clear the timeout on destroy so the callback does not fire afterwards", async () => {
      vi.useFakeTimers();
      try {
         const clearTimeoutSpy = vi.spyOn(globalThis, "clearTimeout");
         const { fixture } = await render(VSLoadingDisplay, {
            schemas: [NO_ERRORS_SCHEMA],
            componentProperties: { showIcon: true, autoShowCancel: true },
         });
         const comp = fixture.componentInstance as VSLoadingDisplay;

         fixture.destroy();

         expect(clearTimeoutSpy).toHaveBeenCalled();
         // The timer must NOT fire after destroy
         vi.advanceTimersByTime(2000);
         expect(comp.showCancelLoading).toBe(false);
      } finally {
         vi.useRealTimers();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 4: cancelViewsheetLoading [Risk 2]
// ---------------------------------------------------------------------------

describe("VSLoadingDisplay — cancelViewsheetLoading", () => {
   // 🔁 Regression-sensitive: loadingCanceled must be set before the emit so the template
   //    hides the button immediately; missing flag means button stays clickable.
   it("should set loadingCanceled to true", async () => {
      const comp = await renderComponent();
      comp.cancelViewsheetLoading();
      expect(comp.loadingCanceled).toBe(true);
   });

   it("should emit via cancelLoading EventEmitter", async () => {
      const comp = await renderComponent();
      const emitted: null[] = [];
      comp.cancelLoading.subscribe(() => emitted.push(null));

      comp.cancelViewsheetLoading();

      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 5: switchToMetaMode [Risk 2]
// ---------------------------------------------------------------------------

describe("VSLoadingDisplay — switchToMetaMode", () => {
   it("should set switchingToMeta to true", async () => {
      const comp = await renderComponent();
      comp.switchToMetaMode();
      expect(comp.switchingToMeta).toBe(true);
   });

   it("should emit via switchToMeta EventEmitter", async () => {
      const comp = await renderComponent();
      const emitted: null[] = [];
      comp.switchToMeta.subscribe(() => emitted.push(null));

      comp.switchToMetaMode();

      expect(emitted).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 6: getBtnsPosition [Risk 2]
// ---------------------------------------------------------------------------

describe("VSLoadingDisplay — getBtnsPosition", () => {
   // 🔁 Regression-sensitive: layout changes when both buttons are present; wrong value
   //    causes buttons to overlap.
   it("should return 'space-between' when both showSwitchToMeta and showCancelLoading are true", async () => {
      const comp = await renderComponent();
      comp.showSwitchToMeta = true;
      comp.showCancelLoading = true;
      expect(comp.getBtnsPosition()).toBe("space-between");
   });

   it("should return 'center' when only showCancelLoading is true", async () => {
      const comp = await renderComponent();
      comp.showSwitchToMeta = false;
      comp.showCancelLoading = true;
      expect(comp.getBtnsPosition()).toBe("center");
   });

   it("should return 'center' when only showSwitchToMeta is true", async () => {
      const comp = await renderComponent();
      comp.showSwitchToMeta = true;
      comp.showCancelLoading = false;
      expect(comp.getBtnsPosition()).toBe("center");
   });

   it("should return 'center' when both are false", async () => {
      const comp = await renderComponent();
      comp.showSwitchToMeta = false;
      comp.showCancelLoading = false;
      expect(comp.getBtnsPosition()).toBe("center");
   });
});
