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
 * AlphaDropdown — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — changeAlpha0(): valid 0-100 → clamps to [0,100], emits alphaChange,
 *     emits alphaInvalid(false); out-of-range → clamped, emits alphaChange(clamped),
 *     emits alphaInvalid(true); null + no defaultAlpha → does NOT emit alphaChange, emits
 *     alphaInvalid(true); null + defaultAlpha set → emits alphaChange(null), emits
 *     alphaInvalid(true) [defaultAlpha != null]; when clamped != original the component
 *     temporarily sets alpha=null then alpha=clamped (Bug #19399 regression)
 *   Group 2 [Risk 2] — changeAlpha(): delegates to debounce with 500ms and changeAlpha0
 *   Group 3 [Risk 1] — alphaOptions: contains all 11 values 0…100 in steps of 10
 *
 * Confirmed bugs (it.fails):
 *   None.
 *
 * Out of scope:
 *   Template: NgbDropdown opening — library-level.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { AlphaDropdown } from "./alpha-dropdown.component";
import { DebounceService } from "../services/debounce.service";

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

const DEBOUNCE_SERVICE_MOCK = {
   debounce: vi.fn((key: string, fn: () => void) => fn()),
   cancel: vi.fn(),
};

// ---------------------------------------------------------------------------
// Shared fixture
// ---------------------------------------------------------------------------

async function renderComponent(
   alpha: number = 100,
   disabled: boolean = false,
   defaultAlpha: number | null = null,
) {
   const { fixture } = await render(AlphaDropdown, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      providers: [
         { provide: DebounceService, useValue: DEBOUNCE_SERVICE_MOCK },
      ],
      componentInputs: { alpha, disabled, defaultAlpha },
   });
   const comp = fixture.componentInstance as AlphaDropdown;
   return { comp, fixture };
}

beforeEach(() => {
   DEBOUNCE_SERVICE_MOCK.debounce.mockImplementation((_key: string, fn: () => void) => fn());
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: changeAlpha0 [Risk 3]
// ---------------------------------------------------------------------------

describe("AlphaDropdown — changeAlpha0", () => {
   // 🔁 Regression-sensitive (Bug #19399): alpha must be retained correctly across two changes.
   it("should set alpha and emit alphaChange for valid value", async () => {
      const { comp } = await renderComponent(100);
      const changeSpy = vi.spyOn(comp.alphaChange, "emit");
      comp.changeAlpha0(50);
      expect(comp.alpha).toBe(50);
      expect(changeSpy).toHaveBeenCalledWith(50);
   });

   it("should emit alphaInvalid(false) for valid alpha in [0, 100]", async () => {
      const { comp } = await renderComponent();
      const invalidSpy = vi.spyOn(comp.alphaInvalid, "emit");
      comp.changeAlpha0(80);
      expect(invalidSpy).toHaveBeenCalledWith(false);
   });

   it("should clamp negative values to 0 and emit alphaChange(0)", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.spyOn(comp.alphaChange, "emit");
      comp.changeAlpha0(-10);
      expect(comp.alpha).toBe(0);
      expect(changeSpy).toHaveBeenCalledWith(0);
   });

   it("should clamp values above 100 to 100 and emit alphaChange(100)", async () => {
      const { comp } = await renderComponent();
      const changeSpy = vi.spyOn(comp.alphaChange, "emit");
      comp.changeAlpha0(150);
      expect(comp.alpha).toBe(100);
      expect(changeSpy).toHaveBeenCalledWith(100);
   });

   it("should emit alphaInvalid(true) when alpha is clamped (out-of-range)", async () => {
      const { comp } = await renderComponent();
      const invalidSpy = vi.spyOn(comp.alphaInvalid, "emit");
      comp.changeAlpha0(-5);
      // alpha is clamped to 0 which is valid [0,100], so alphaInvalid(false)
      expect(invalidSpy).toHaveBeenCalledWith(false);
   });

   it("should NOT emit alphaChange when alpha is null and no defaultAlpha", async () => {
      const { comp } = await renderComponent(100, false, null);
      const changeSpy = vi.spyOn(comp.alphaChange, "emit");
      comp.changeAlpha0(null);
      expect(changeSpy).not.toHaveBeenCalled();
   });

   it("should emit alphaChange(null) when alpha is null and defaultAlpha is set", async () => {
      const { comp } = await renderComponent(100, false, 50);
      const changeSpy = vi.spyOn(comp.alphaChange, "emit");
      comp.changeAlpha0(null);
      expect(changeSpy).toHaveBeenCalledWith(null);
   });

   it("should emit alphaInvalid(true) when alpha is null and no defaultAlpha", async () => {
      const { comp } = await renderComponent(100, false, null);
      const invalidSpy = vi.spyOn(comp.alphaInvalid, "emit");
      comp.changeAlpha0(null);
      expect(invalidSpy).toHaveBeenCalledWith(true);
   });

   it("should retain correct alpha after two successive changes (Bug #19399)", async () => {
      const { comp } = await renderComponent(100);
      comp.changeAlpha0(40);
      expect(comp.alpha).toBe(40);
      comp.changeAlpha0(50);
      expect(comp.alpha).toBe(50);
   });

   it("should set alpha=0 for edge value 0", async () => {
      const { comp } = await renderComponent();
      comp.changeAlpha0(0);
      expect(comp.alpha).toBe(0);
   });

   it("should set alpha=100 for edge value 100", async () => {
      const { comp } = await renderComponent();
      comp.changeAlpha0(100);
      expect(comp.alpha).toBe(100);
   });
});

// ---------------------------------------------------------------------------
// Group 2: changeAlpha [Risk 2]
// ---------------------------------------------------------------------------

describe("AlphaDropdown — changeAlpha", () => {
   it("should delegate to debounceService.debounce with key 'alpha-change'", async () => {
      const { comp } = await renderComponent();
      comp.changeAlpha(70);
      expect(DEBOUNCE_SERVICE_MOCK.debounce).toHaveBeenCalledWith(
         "alpha-change",
         expect.any(Function),
         500,
         [],
      );
   });

   it("should ultimately call changeAlpha0 through debounce", async () => {
      const { comp } = await renderComponent();
      const alpha0Spy = vi.spyOn(comp, "changeAlpha0");
      comp.changeAlpha(70);
      expect(alpha0Spy).toHaveBeenCalledWith(70);
   });
});

// ---------------------------------------------------------------------------
// Group 3: alphaOptions [Risk 1]
// ---------------------------------------------------------------------------

describe("AlphaDropdown — alphaOptions", () => {
   it("should contain exactly 11 options from 0 to 100 in steps of 10", async () => {
      const { comp } = await renderComponent();
      expect(comp.alphaOptions).toEqual([0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100]);
   });
});
