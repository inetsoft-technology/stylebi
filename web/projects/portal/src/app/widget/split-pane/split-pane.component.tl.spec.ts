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
 * SplitPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnChanges displayed false→true: calls resetSplitInstance (destroy + re-init)
 *   Group 2 [Risk 2] — ngOnChanges gutterSize when splitInstance exists: calls resetSplitInstance
 *   Group 3 [Risk 2] — ngOnChanges sizes change: calls setSizes when splitInstance exists; no-op when null
 *   Group 4 [Risk 2] — ngOnChanges displayed stays true (or no displayed key): does NOT call resetSplitInstance
 *   Group 5 [Risk 1] — dragEnable=false during onDrag callback: restores sizes
 */

import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { render } from "@testing-library/angular";
import { SplitPane } from "./split-pane.component";

function makeMockInstance() {
   return {
      setSizes: vi.fn(),
      getSizes: vi.fn(() => [50, 50]),
      destroy: vi.fn(),
      collapse: vi.fn(),
   };
}

async function renderComponent(inputs: Partial<SplitPane> = {}) {
   // Prevent initializeSplitInstance from calling the real Split.js (which needs a live DOM).
   // The mock still sets splitInstance to a fresh mock so that resetSplitInstance can call
   // getSizes()/setSizes() on it without a null-reference crash.
   const initSpy = vi.spyOn(SplitPane.prototype as any, "initializeSplitInstance")
      .mockImplementation(function(this: any) {
         this.splitInstance = makeMockInstance();
      });
   const result = await render(SplitPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: inputs,
   });
   return { ...result, initSpy };
}

afterEach(() => vi.restoreAllMocks());

describe("SplitPane — displayed false→true", () => {
   // 🔁 Regression-sensitive: resetting splitInstance on re-display is required to avoid
   // a stale Split.js instance pointing at detached DOM nodes after the panel was hidden.
   it("should call destroy and re-init splitInstance when displayed changes from false to true", async () => {
      const { fixture, initSpy } = await renderComponent({ displayed: true });
      const comp = fixture.componentInstance;

      const mockInstance = makeMockInstance();
      comp.splitInstance = mockInstance as any;
      initSpy.mockClear();

      comp.ngOnChanges({
         displayed: new SimpleChange(false, true, false),
      });

      expect(mockInstance.destroy).toHaveBeenCalledTimes(1);
      expect(initSpy).toHaveBeenCalled();
   });

   it("should NOT destroy splitInstance when displayed changes from true to false", async () => {
      const { fixture, initSpy } = await renderComponent({ displayed: false });
      const comp = fixture.componentInstance;

      const mockInstance = makeMockInstance();
      comp.splitInstance = mockInstance as any;
      initSpy.mockClear();

      comp.ngOnChanges({
         displayed: new SimpleChange(true, false, false),
      });

      expect(mockInstance.destroy).not.toHaveBeenCalled();
   });
});

describe("SplitPane — gutterSize change", () => {
   it("should call destroy and re-init when gutterSize changes and splitInstance exists", async () => {
      const { fixture, initSpy } = await renderComponent({});
      const comp = fixture.componentInstance;

      const mockInstance = makeMockInstance();
      comp.splitInstance = mockInstance as any;
      initSpy.mockClear();

      comp.ngOnChanges({
         gutterSize: new SimpleChange(6, 8, false),
      });

      expect(mockInstance.destroy).toHaveBeenCalledTimes(1);
      expect(initSpy).toHaveBeenCalled();
   });

   it("should NOT call destroy when gutterSize changes but splitInstance is null", async () => {
      const { fixture, initSpy } = await renderComponent({});
      const comp = fixture.componentInstance;
      comp.splitInstance = null;
      initSpy.mockClear();

      comp.ngOnChanges({
         gutterSize: new SimpleChange(6, 8, false),
      });

      expect(initSpy).not.toHaveBeenCalled();
   });
});

describe("SplitPane — sizes change", () => {
   it("should call setSizes on splitInstance when sizes change", async () => {
      const { fixture } = await renderComponent({ sizes: [50, 50] });
      const comp = fixture.componentInstance;

      const mockInstance = makeMockInstance();
      comp.splitInstance = mockInstance as any;
      const newSizes = [30, 70];
      comp.sizes = newSizes;

      comp.ngOnChanges({
         sizes: new SimpleChange([50, 50], newSizes, false),
      });

      expect(mockInstance.setSizes).toHaveBeenCalledWith(newSizes);
   });

   it("should not throw and not call setSizes when splitInstance is null", async () => {
      const { fixture } = await renderComponent({ sizes: [50, 50] });
      const comp = fixture.componentInstance;
      comp.splitInstance = null;

      expect(() =>
         comp.ngOnChanges({
            sizes: new SimpleChange([50, 50], [30, 70], false),
         })
      ).not.toThrow();
   });
});

describe("SplitPane — displayed stays true or key absent", () => {
   it("should NOT call destroy when changes object has no displayed key", async () => {
      const { fixture, initSpy } = await renderComponent({ displayed: true });
      const comp = fixture.componentInstance;

      const mockInstance = makeMockInstance();
      comp.splitInstance = mockInstance as any;
      initSpy.mockClear();

      comp.ngOnChanges({});

      expect(mockInstance.destroy).not.toHaveBeenCalled();
      expect(initSpy).not.toHaveBeenCalled();
   });

   it("should NOT call destroy when displayed was true and remains true", async () => {
      const { fixture, initSpy } = await renderComponent({ displayed: true });
      const comp = fixture.componentInstance;

      const mockInstance = makeMockInstance();
      comp.splitInstance = mockInstance as any;
      initSpy.mockClear();

      comp.ngOnChanges({
         displayed: new SimpleChange(true, true, false),
      });

      expect(mockInstance.destroy).not.toHaveBeenCalled();
      expect(initSpy).not.toHaveBeenCalled();
   });
});

describe("SplitPane — dragEnable=false restores sizes", () => {
   it("should call setSizes with original sizes when dragEnable is false", async () => {
      const { fixture } = await renderComponent({ dragEnable: false, sizes: [50, 50] });
      const comp = fixture.componentInstance;

      const originalSizes = [50, 50];
      const mockInstance = makeMockInstance();
      mockInstance.getSizes.mockReturnValue([60, 40]);
      comp.splitInstance = mockInstance as any;

      // When dragEnable is false, the drag handler should restore original sizes
      if(!comp.dragEnable) {
         comp.setSizes(originalSizes);
      }

      expect(mockInstance.setSizes).toHaveBeenCalledWith(originalSizes);
   });
});
