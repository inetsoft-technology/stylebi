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
 * LinearColorPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit/resetEditors: capture originalFrame and emit onChangeColorFrame
 *   Group 2 [Risk 2] — setBrewerColor/get*HueModel: map frame clazz to brewer category getters
 *   Group 3 [Risk 2] — switchColorModel: gradient css preservation and heat model switch
 *   Group 4 [Risk 2] — syncColors: copy from/to colors when gradient frame selected
 *   Group 5 [Risk 1] — isSelectedFrame/gmodel: selected frame exposes live frame reference
 *   Group 6 [Risk 3] — hiddenFrames filtering: modern-mark chart hides legacy ramps but never
 *     the currently selected one (hide-never-remove)
 *   Group 7 [Risk 3] — flat house rows under a modern mark: each row checks and switches its ramp
 *
 * HTTP: MSW inline server.use() for GET ../api/composer/chart/hiddenlinearframes; a beforeEach
 *   default returns [] so Groups 1-5 (which don't care about hiding) see every ramp offered.
 *
 * Out of scope:
 *   apply output — template-only emit, no component method entry point
 */

import { provideHttpClient } from "@angular/common/http";
import { By } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, screen, waitFor } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@test-mocks/server";
import * as V from "../../../../common/data/visual-frame-model";
import { LinearColorDropdown } from "./linear-color-dropdown.component";
import { LinearColorPane } from "./linear-color-pane.component";

const RESET_DEFAULT = "_#(Reset to Default)";
const CUSTOM = "_#(Custom)";
const SINGLE_HUE = "_#(Single Hue)";
const MULTI_HUE = "_#(Multi-Hue)";
const DIVERGING = "_#(Diverging)";
const HEAT = "_#(Heat)";

const HIDDEN_LINEAR_FRAMES_URI = "*/api/composer/chart/hiddenlinearframes";

const SINGLE_HUE_HIDDEN = [
   "BluesColorModel", "GreensColorModel", "GreysColorModel",
   "OrangesColorModel", "PurplesColorModel", "RedsColorModel",
];
const DIVERGING_HIDDEN = [
   "BrBGColorModel", "PiYGColorModel", "PRGnColorModel", "PuOrColorModel",
   "RdBuColorModel", "RdGyColorModel", "RdYlGnColorModel", "SpectralColorModel", "RdYlBuColorModel",
];
const MULTI_HUE_HIDDEN = [
   "BuGnColorModel", "BuPuColorModel", "GnBuColorModel", "OrRdColorModel", "PuBuColorModel",
   "PuBuGnColorModel", "PuRdColorModel", "RdPuColorModel", "YlGnBuColorModel", "YlGnColorModel",
   "YlOrBrColorModel", "YlOrRdColorModel",
];
const ALL_HIDDEN = [
   ...SINGLE_HUE_HIDDEN, ...DIVERGING_HIDDEN, ...MULTI_HUE_HIDDEN, "HeatColorModel"
];
const TEAL = "_#(Teal)";
const AMBER = "_#(Amber)";
const VARIANCE = "_#(Variance)";

beforeEach(() => {
   server.use(http.get(HIDDEN_LINEAR_FRAMES_URI, () => HttpResponse.json([])));
});

async function renderPane(frame?: V.ColorFrameModel, props: Record<string, unknown> = {}) {
   return render(LinearColorPane, {
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: {} },
      ],
      componentProperties: { ...(frame ? { frame } : {}), ...props }
   });
}

function dropdowns(fixture: any): LinearColorDropdown[] {
   return fixture.debugElement.queryAll(By.directive(LinearColorDropdown))
      .map((de: any) => de.componentInstance as LinearColorDropdown);
}

function gradientEditorButtons(container: HTMLElement): HTMLButtonElement[] {
   return Array.from(container.querySelectorAll("gradient-color-editor static-color-editor button"));
}

function radioByLabel(label: string): HTMLInputElement {
   return screen.getByLabelText(label) as HTMLInputElement;
}

describe("LinearColorPane — ngOnInit and resetEditors [Group 1, Risk 2]", () => {
   it("should snapshot original frame and emit on init", async () => {
      const frame = new V.GradientColorModel();
      frame.fromColor = "#111111";
      frame.toColor = "#222222";
      const changed = vi.fn();
      const { fixture } = await render(LinearColorPane, {
         componentProperties: { frame },
         detectChangesOnRender: false
      });
      fixture.componentInstance.onChangeColorFrame.subscribe(changed);
      fixture.detectChanges();

      const comp = fixture.componentInstance as any;
      expect(comp.isSelectedFrame(comp.gradientModel)).toBe(true);
      expect(changed).toHaveBeenCalledWith(frame);
      expect(comp.originalFrame).toBe(frame);
   });

   it("should restore original frame and re-emit on resetEditors", async () => {
      const frame = new V.BluesColorModel();
      const changed = vi.fn();
      const { fixture, container } = await renderPane(frame);
      fixture.componentInstance.onChangeColorFrame.subscribe(changed);
      changed.mockClear();

      await userEvent.click(radioByLabel(HEAT));
      await userEvent.click(screen.getByTitle(RESET_DEFAULT));
      fixture.detectChanges();

      expect(radioByLabel(SINGLE_HUE)).toBeChecked();
      expect(changed).toHaveBeenCalled();
      expect(container.querySelector("linear-color-dropdown")).toBeTruthy();
   });
});

describe("LinearColorPane — brewer model getters [Group 2, Risk 2]", () => {
   it("should classify single-hue brewer frames", async () => {
      await renderPane(new V.BluesColorModel());

      expect(radioByLabel(SINGLE_HUE)).toBeChecked();
   });

   it("should classify multi-hue brewer frames", async () => {
      await renderPane(new V.BuGnColorModel());
      expect(radioByLabel(MULTI_HUE)).toBeChecked();
   });

   it("should classify diverging brewer frames", async () => {
      await renderPane(new V.BrBGColorModel());
      expect(radioByLabel(DIVERGING)).toBeChecked();
   });
});

describe("LinearColorPane — switchColorModel [Group 3, Risk 2]", () => {
   // 🔁 Regression-sensitive: Bug #19192 — custom gradient colors must survive model switch
   it("should preserve css gradient endpoints when switching back to gradient model", async () => {
      const frame = new V.GradientColorModel();
      frame.cssFromColor = "#d84d3f";
      frame.cssToColor = "#008000";
      const { fixture } = await renderPane(frame);

      fixture.componentInstance.switchColorModel(fixture.componentInstance.heatModel.clazz);
      fixture.componentInstance.switchColorModel(fixture.componentInstance.gradientModel.clazz);
      fixture.detectChanges();

      const gradient = fixture.componentInstance.frame as V.GradientColorModel;
      expect(gradient.cssFromColor).toBe("#d84d3f");
      expect(gradient.cssToColor).toBe("#008000");
      expect(radioByLabel(CUSTOM)).toBeChecked();
   });

   it("should switch to heat color model", async () => {
      const changed = vi.fn();
      const { fixture } = await renderPane(new V.GradientColorModel());
      fixture.componentInstance.onChangeColorFrame.subscribe(changed);

      await userEvent.click(radioByLabel(HEAT));

      expect(radioByLabel(HEAT)).toBeChecked();
      expect(changed).toHaveBeenCalled();
   });

   it("should mark a ramp model changed when the author picks it", async () => {
      const emitted: V.ColorFrameModel[] = [];
      const { fixture } = await renderPane(new V.GradientColorModel());
      fixture.componentInstance.onChangeColorFrame.subscribe((f: V.ColorFrameModel) => emitted.push(f));

      fixture.componentInstance.switchColorModel(fixture.componentInstance.TealColorModel.clazz);

      const frame = emitted[emitted.length - 1];
      expect(frame.clazz).toBe(fixture.componentInstance.TealColorModel.clazz);
      expect(frame.changed).toBe(true);
   });

   it("should not mark the gradient model changed", async () => {
      const emitted: V.ColorFrameModel[] = [];
      const { fixture } = await renderPane(new V.TealColorModel());
      fixture.componentInstance.onChangeColorFrame.subscribe((f: V.ColorFrameModel) => emitted.push(f));

      fixture.componentInstance.switchColorModel(fixture.componentInstance.gradientModel.clazz);

      const frame = emitted[emitted.length - 1];
      expect(frame.clazz).toBe(fixture.componentInstance.gradientModel.clazz);
      expect(frame.changed).toBe(false);
   });
});

describe("LinearColorPane — syncColors [Group 4, Risk 2]", () => {
   it("should copy frame endpoints into gradient editor model", async () => {
      const frame = new V.GradientColorModel();
      frame.fromColor = "#aaaaaa";
      frame.toColor = "#bbbbbb";
      const { fixture, container } = await renderPane(frame);

      fixture.componentInstance.syncColors(false);
      fixture.detectChanges();

      const buttons = gradientEditorButtons(container);
      expect(buttons[0]?.style.background).toMatch(/rgb\(170, 170, 170\)|#aaaaaa/i);
      expect(buttons[1]?.style.background).toMatch(/rgb\(187, 187, 187\)|#bbbbbb/i);
   });
});

describe("LinearColorPane — isSelectedFrame and gmodel [Group 5, Risk 1]", () => {
   it("should expose live frame through gmodel when gradient is selected", async () => {
      const { fixture } = await renderPane(new V.GradientColorModel());
      const comp = fixture.componentInstance as any;

      expect(comp.isSelectedFrame(comp.gradientModel)).toBe(true);
      expect(comp.gmodel).toBe(comp.frame);
   });

   it("should return editor gradient model when another frame type is selected", async () => {
      const { fixture } = await renderPane(new V.BluesColorModel());
      const comp = fixture.componentInstance as any;

      expect(comp.isSelectedFrame(comp.gradientModel)).toBe(false);
      expect(comp.gmodel).toBe(comp.gradientModel);
   });
});

describe("LinearColorPane — hiddenFrames filtering [Group 6, Risk 3]", () => {
   it("should list the house ramps as flat rows and drop the family rows under a modern mark", async () => {
      server.use(
         http.get(HIDDEN_LINEAR_FRAMES_URI, () => HttpResponse.json(ALL_HIDDEN))
      );
      const { fixture } = await renderPane(new V.GradientColorModel(),
         { vsId: "vs1", assemblyName: "Chart1" });

      await waitFor(() => {
         expect(fixture.componentInstance.hiddenFrames).toEqual(ALL_HIDDEN);
      });
      fixture.detectChanges();

      expect(dropdowns(fixture)).toHaveLength(0);
      expect(radioByLabel(CUSTOM)).toBeInTheDocument();
      expect(radioByLabel(TEAL)).toBeInTheDocument();
      expect(radioByLabel(AMBER)).toBeInTheDocument();
      expect(radioByLabel(VARIANCE)).toBeInTheDocument();
      expect(screen.queryByLabelText(SINGLE_HUE)).toBeNull();
      expect(screen.queryByLabelText(MULTI_HUE)).toBeNull();
      expect(screen.queryByLabelText(DIVERGING)).toBeNull();
      expect(screen.queryByLabelText(HEAT)).toBeNull();
   });

   it("should offer every ramp and render every row when nothing is hidden", async () => {
      const { fixture } = await renderPane(new V.GradientColorModel(),
         { vsId: "vs1", assemblyName: "Chart1" });

      await waitFor(() => {
         expect(fixture.componentInstance.hiddenFrames).toEqual([]);
      });
      fixture.detectChanges();

      const [singleHueDropdown, multiHueDropdown, divergingDropdown] = dropdowns(fixture);
      expect(singleHueDropdown.colorFrames).toHaveLength(8);
      expect(multiHueDropdown.colorFrames).toHaveLength(12);
      expect(divergingDropdown.colorFrames).toHaveLength(10);
      expect(screen.getByLabelText(HEAT)).toBeInTheDocument();
      expect(screen.queryByLabelText(TEAL)).toBeNull();
   });

   it("should keep a hidden ramp offered and selected when the frame is already on it", async () => {
      server.use(
         http.get(HIDDEN_LINEAR_FRAMES_URI, () => HttpResponse.json(ALL_HIDDEN))
      );
      const { fixture } = await renderPane(new V.SpectralColorModel(),
         { vsId: "vs1", assemblyName: "Chart1" });

      await waitFor(() => {
         expect(fixture.componentInstance.hiddenFrames).toEqual(ALL_HIDDEN);
      });
      fixture.detectChanges();

      // the family layout stays, minus the emptied Multi-Hue column
      const [singleHueDropdown, divergingDropdown] = dropdowns(fixture);
      expect(singleHueDropdown.colorFrames).toEqual(["AmberColorModel", "TealColorModel"]);
      expect(divergingDropdown.colorFrames).toContain("SpectralColorModel");
      expect(divergingDropdown.colorFrame).toBe("SpectralColorModel");
      expect(radioByLabel(DIVERGING)).toBeChecked();
      expect(screen.queryByLabelText(MULTI_HUE)).toBeNull();
   });

   it("should keep the Multi-Hue column and its ramp selected when the frame is already on one",
      async () => {
         server.use(
            http.get(HIDDEN_LINEAR_FRAMES_URI, () => HttpResponse.json(ALL_HIDDEN))
         );
         const { fixture } = await renderPane(new V.BuGnColorModel(),
            { vsId: "vs1", assemblyName: "Chart1" });

         await waitFor(() => {
            expect(fixture.componentInstance.hiddenFrames).toEqual(ALL_HIDDEN);
         });
         fixture.detectChanges();

         const [, multiHueDropdown] = dropdowns(fixture);
         expect(multiHueDropdown.colorFrames).toEqual(["BuGnColorModel"]);
         expect(multiHueDropdown.colorFrame).toBe("BuGnColorModel");
         expect(radioByLabel(MULTI_HUE)).toBeChecked();
      });

   // the Heat row is a radio and a fixed image rather than a dropdown entry, so it has no
   // family list to keep it in - without its own escape a chart on Heat under a modern mark
   // would leave all four radios unchecked and no route back
   it("should keep the Heat row rendered and checked when the frame is already on Heat", async () => {
      server.use(
         http.get(HIDDEN_LINEAR_FRAMES_URI, () => HttpResponse.json(ALL_HIDDEN))
      );
      const { fixture, container } = await renderPane(new V.HeatColorModel(),
         { vsId: "vs1", assemblyName: "Chart1" });

      await waitFor(() => {
         expect(fixture.componentInstance.hiddenFrames).toEqual(ALL_HIDDEN);
      });
      fixture.detectChanges();

      expect(screen.getByLabelText(HEAT)).toBeInTheDocument();
      expect(radioByLabel(HEAT)).toBeChecked();

      // the row's width toggle reads the same getter, so it cannot drift from the row itself
      const divergingHost =
         container.querySelectorAll("linear-color-dropdown")[1].parentElement;
      expect(divergingHost.classList.contains("col-4")).toBe(true);
      expect(divergingHost.classList.contains("col-10")).toBe(false);
   });
});

describe("LinearColorPane — flat house rows under a modern mark [Group 7, Risk 3]", () => {
   async function renderModern(frame: V.ColorFrameModel) {
      server.use(
         http.get(HIDDEN_LINEAR_FRAMES_URI, () => HttpResponse.json(ALL_HIDDEN))
      );
      const result = await renderPane(frame, { vsId: "vs1", assemblyName: "Chart1" });

      await waitFor(() => {
         expect(result.fixture.componentInstance.hiddenFrames).toEqual(ALL_HIDDEN);
      });
      result.fixture.detectChanges();
      return result;
   }

   it("should check the row of the house ramp the frame is on", async () => {
      await renderModern(new V.VarianceColorModel());

      expect(radioByLabel(VARIANCE)).toBeChecked();
      expect(radioByLabel(TEAL)).not.toBeChecked();
      expect(radioByLabel(AMBER)).not.toBeChecked();
   });

   it("should switch to Amber when its row is clicked", async () => {
      const { fixture } = await renderModern(new V.TealColorModel());
      expect(radioByLabel(TEAL)).toBeChecked();

      await userEvent.click(radioByLabel(AMBER));
      fixture.detectChanges();

      expect(fixture.componentInstance.frame.clazz).toMatch(/\.AmberColorModel$/);
      expect(radioByLabel(AMBER)).toBeChecked();
   });

   it("should switch to Variance when its row is clicked", async () => {
      const { fixture } = await renderModern(new V.TealColorModel());

      await userEvent.click(radioByLabel(VARIANCE));
      fixture.detectChanges();

      expect(fixture.componentInstance.frame.clazz).toMatch(/\.VarianceColorModel$/);
      expect(radioByLabel(VARIANCE)).toBeChecked();
   });

   it("should return to the flat rows once a chart on a hidden ramp picks a house ramp", async () => {
      const { fixture } = await renderModern(new V.SpectralColorModel());
      expect(dropdowns(fixture).length).toBeGreaterThan(0);

      fixture.componentInstance.switchColorModel(fixture.componentInstance.TealColorModel.clazz);
      fixture.detectChanges();
      // ngModel writes a new radio's checked state on a later tick
      await fixture.whenStable();
      fixture.detectChanges();

      expect(dropdowns(fixture)).toHaveLength(0);
      expect(radioByLabel(TEAL)).toBeChecked();
   });
});

describe("LinearColorPane — Custom ramp blend [Group 8, Risk 3]", () => {
   const SMOOTH = "_#(Smooth blend)";

   function smoothOf(fixture: any): boolean {
      return (fixture.componentInstance.frame as V.GradientColorModel).smooth;
   }

   it("should start a new Custom ramp smooth under a modern mark", async () => {
      server.use(http.get(HIDDEN_LINEAR_FRAMES_URI, () => HttpResponse.json(ALL_HIDDEN)));
      const { fixture } = await renderPane(new V.TealColorModel(),
         { vsId: "vs1", assemblyName: "Chart1" });
      await waitFor(() => {
         expect(fixture.componentInstance.hiddenFrames).toEqual(ALL_HIDDEN);
      });

      fixture.componentInstance.switchColorModel(fixture.componentInstance.gradientModel.clazz);

      expect(smoothOf(fixture)).toBe(true);
   });

   it("should start a new Custom ramp classic when nothing is hidden", async () => {
      const { fixture } = await renderPane(new V.BluesColorModel(),
         { vsId: "vs1", assemblyName: "Chart1" });

      fixture.componentInstance.switchColorModel(fixture.componentInstance.gradientModel.clazz);

      expect(smoothOf(fixture)).toBe(false);
   });

   it("should keep the blend of a Custom ramp the chart already holds", async () => {
      server.use(http.get(HIDDEN_LINEAR_FRAMES_URI, () => HttpResponse.json(ALL_HIDDEN)));
      const saved = new V.GradientColorModel();
      saved.smooth = false;
      const { fixture } = await renderPane(saved, { vsId: "vs1", assemblyName: "Chart1" });
      await waitFor(() => {
         expect(fixture.componentInstance.hiddenFrames).toEqual(ALL_HIDDEN);
      });

      fixture.componentInstance.switchColorModel(fixture.componentInstance.TealColorModel.clazz);
      fixture.componentInstance.switchColorModel(fixture.componentInstance.gradientModel.clazz);

      expect(smoothOf(fixture)).toBe(false);
   });

   it("should show the checkbox checked for a smooth ramp and emit when it is cleared", async () => {
      const saved = new V.GradientColorModel();
      saved.smooth = true;
      const emitted: V.ColorFrameModel[] = [];
      const { fixture } = await renderPane(saved);
      fixture.componentInstance.onChangeColorFrame.subscribe((f: V.ColorFrameModel) => emitted.push(f));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(radioByLabel(SMOOTH)).toBeChecked();

      await userEvent.click(radioByLabel(SMOOTH));

      expect(smoothOf(fixture)).toBe(false);
      expect((emitted[emitted.length - 1] as V.GradientColorModel).smooth).toBe(false);
   });

   it("should disable the checkbox while another ramp is selected", async () => {
      await renderPane(new V.BluesColorModel());

      expect(radioByLabel(SMOOTH)).toBeDisabled();
   });
});
