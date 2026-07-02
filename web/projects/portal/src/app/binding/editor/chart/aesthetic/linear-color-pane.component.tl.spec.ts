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
 *
 * HTTP: no HTTP — local color frame editor only
 *
 * Out of scope:
 *   apply output — template-only emit, no component method entry point
 */

import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import * as V from "../../../../common/data/visual-frame-model";
import { LinearColorPane } from "./linear-color-pane.component";

const RESET_DEFAULT = "_#(Reset to Default)";
const CUSTOM = "_#(Custom)";
const SINGLE_HUE = "_#(Single Hue)";
const MULTI_HUE = "_#(Multi-Hue)";
const DIVERGING = "_#(Diverging)";
const HEAT = "_#(Heat)";

async function renderPane(frame?: V.ColorFrameModel) {
   return render(LinearColorPane, {
      componentProperties: frame ? { frame } : {}
   });
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
      const { fixture, container } = await render(LinearColorPane, {
         componentProperties: { frame }
      });
      fixture.componentInstance.onChangeColorFrame.subscribe(changed);
      fixture.detectChanges();

      expect(radioByLabel(CUSTOM)).toBeChecked();
      expect(changed).toHaveBeenCalledWith(frame);
      expect(gradientEditorButtons(container).length).toBeGreaterThan(0);
      expect(fixture.componentInstance.originalFrame).toBe(frame);
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

   it("should classify multi-hue and diverging brewer frames", async () => {
      await renderPane(new V.BuGnColorModel());
      expect(radioByLabel(MULTI_HUE)).toBeChecked();

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
      const { container } = await renderPane(new V.GradientColorModel());

      expect(radioByLabel(CUSTOM)).toBeChecked();
      expect(gradientEditorButtons(container).every(btn => !btn.disabled)).toBe(true);
   });

   it("should return editor gradient model when another frame type is selected", async () => {
      const { container } = await renderPane(new V.BluesColorModel());

      expect(radioByLabel(SINGLE_HUE)).toBeChecked();
      expect(gradientEditorButtons(container).every(btn => btn.disabled)).toBe(true);
   });
});
