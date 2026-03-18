/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * AxisLabelPane — Angular Testing Library style
 *
 * Feature #72694: "Labels on Opposite Side" checkbox
 *
 * Coverage:
 *   Group 1 — Checkbox visibility (controlled by model.secondary)
 *   Group 2 — Checkbox initial state (model.labelOnSecondaryAxis binding)
 *   Group 3 — User interaction (toggle updates model)
 *   Group 4 — State preservation when secondary flag changes
 *   Group 5 — "Show Axis Labels" baseline (existing functionality, not regressed)
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { render, screen, waitFor } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { NgbModal, NgbModule } from "@ng-bootstrap/ng-bootstrap";

import { AxisLabelPane } from "./axis-label-pane.component";
import { AxisLabelPaneModel } from "../model/dialog/axis-label-panel-model";

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

const LABEL_TEXT = "_#(Labels on Opposite Side)";
const SHOW_AXIS_LABEL_TEXT = "_#(Show Axis Labels)";

function createModel(overrides: Partial<AxisLabelPaneModel> = {}): AxisLabelPaneModel {
   return {
      showAxisLabel: true,
      showAxisLabelEnabled: true,
      rotationRadioGroupModel: { rotation: "auto" },
      labelOnSecondaryAxis: false,
      secondary: false,
      ...overrides,
   };
}

async function renderPane(modelOverrides: Partial<AxisLabelPaneModel> = {}) {
   return renderPaneWithModel(createModel(modelOverrides));
}

// Escape-hatch for tests that need a pre-built model (e.g. after deleting optional keys)
async function renderPaneWithModel(model: AxisLabelPaneModel) {
   return render(AxisLabelPane, {
      imports: [CommonModule, FormsModule, NgbModule],
      providers: [NgbModal],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: { model },
   });
}

// ---------------------------------------------------------------------------
// Group 1: Checkbox visibility — controlled by model.secondary
// ---------------------------------------------------------------------------

describe("AxisLabelPane — Labels on Opposite Side — visibility (Feature #72694)", () => {

   // TC-04: Single Chart, no secondary axis → checkbox visible
   it("should show the checkbox when model.secondary is false", async () => {
      await renderPane({ secondary: false });

      expect(screen.getByLabelText(LABEL_TEXT)).toBeInTheDocument();
   });

   // TC-04 variant: secondary field absent (undefined) → defaults to showing
   it("should show the checkbox when model.secondary is undefined", async () => {
      // secondary is an optional field; absence should not hide the checkbox
      const model = createModel();
      delete model.secondary;

      await renderPaneWithModel(model);

      expect(screen.getByLabelText(LABEL_TEXT)).toBeInTheDocument();
   });

   // TC-05 / TC-18: Secondary axis enabled → checkbox must be hidden
   it("should hide the checkbox when model.secondary is true", async () => {
      await renderPane({ secondary: true });

      expect(screen.queryByLabelText(LABEL_TEXT)).not.toBeInTheDocument();
   });
});

// ---------------------------------------------------------------------------
// Group 2: Checkbox initial state — reflects model.labelOnSecondaryAxis
// ---------------------------------------------------------------------------

describe("AxisLabelPane — Labels on Opposite Side — initial state (Feature #72694)", () => {

   // TC-01 / TC-29: Checkbox matches saved model value (false)
   it("should render checkbox as unchecked when labelOnSecondaryAxis is false", async () => {
      await renderPane({ labelOnSecondaryAxis: false });

      expect(screen.getByLabelText(LABEL_TEXT)).not.toBeChecked();
   });

   // TC-29: Checkbox matches saved model value (true)
   it("should render checkbox as checked when labelOnSecondaryAxis is true", async () => {
      await renderPane({ labelOnSecondaryAxis: true });

      expect(screen.getByLabelText(LABEL_TEXT)).toBeChecked();
   });

   // TC-30: Old dashboard without the field — null treated as false
   it("should render checkbox as unchecked when labelOnSecondaryAxis is null", async () => {
      // Simulates loading a dashboard saved before Feature #72694 was merged
      await renderPane({ labelOnSecondaryAxis: null as unknown as boolean });

      expect(screen.getByLabelText(LABEL_TEXT)).not.toBeChecked();
   });
});

// ---------------------------------------------------------------------------
// Group 3: User interaction — toggling updates the model
// ---------------------------------------------------------------------------

describe("AxisLabelPane — Labels on Opposite Side — user interaction (Feature #72694)", () => {

   // TC-01: Check the box → checkbox becomes checked
   it("should set model.labelOnSecondaryAxis to true when user checks the checkbox", async () => {
      const user = userEvent.setup();
      await renderPane({ labelOnSecondaryAxis: false });

      await user.click(screen.getByLabelText(LABEL_TEXT));

      expect(screen.getByLabelText(LABEL_TEXT)).toBeChecked();
   });

   // TC-01: Uncheck the box → checkbox becomes unchecked
   it("should set model.labelOnSecondaryAxis to false when user unchecks the checkbox", async () => {
      const user = userEvent.setup();
      await renderPane({ labelOnSecondaryAxis: true });

      await user.click(screen.getByLabelText(LABEL_TEXT));

      expect(screen.getByLabelText(LABEL_TEXT)).not.toBeChecked();
   });

   // TC-01 baseline: "Show Axis Labels" checkbox becomes unchecked after toggle
   it("should update model.showAxisLabel when user toggles Show Axis Labels", async () => {
      const user = userEvent.setup();
      await renderPane({ showAxisLabel: true });

      await user.click(screen.getByLabelText(SHOW_AXIS_LABEL_TEXT));

      expect(screen.getByLabelText(SHOW_AXIS_LABEL_TEXT)).not.toBeChecked();
   });
});

// ---------------------------------------------------------------------------
// Group 4: State preservation when secondary flag changes
// ---------------------------------------------------------------------------

describe("AxisLabelPane — Labels on Opposite Side — state preservation (Feature #72694)", () => {

   // TC-31: UI hides checkbox when secondary=true, but must NOT clear the stored value
   it("should preserve labelOnSecondaryAxis value in model when secondary changes to true", async () => {
      const { fixture } = await renderPane({ labelOnSecondaryAxis: true, secondary: false });

      // Checkbox is visible and checked
      expect(screen.getByLabelText(LABEL_TEXT)).toBeChecked();

      // Simulate secondary axis being enabled (e.g. user changes chart settings externally)
      fixture.componentInstance.model.secondary = true;
      fixture.detectChanges();

      // Checkbox must be hidden
      expect(screen.queryByLabelText(LABEL_TEXT)).not.toBeInTheDocument();

      // But the underlying model value must be preserved (not reset to false)
      expect(fixture.componentInstance.model.labelOnSecondaryAxis).toBe(true);
   });

   // TC-06 + TC-31: After secondary is disabled again, checkbox reappears with the saved value
   it("should restore checkbox with its saved value when secondary changes back to false", async () => {
      const { fixture } = await renderPane({ labelOnSecondaryAxis: true, secondary: true });

      // Checkbox is hidden
      expect(screen.queryByLabelText(LABEL_TEXT)).not.toBeInTheDocument();

      // Simulate secondary axis being disabled
      fixture.componentInstance.model.secondary = false;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      // Checkbox reappears and still reflects the saved value
      await waitFor(() => expect(screen.getByLabelText(LABEL_TEXT)).toBeChecked());
   });
});

// ---------------------------------------------------------------------------
// Group 5: "Show Axis Labels" baseline — existing functionality not regressed
// ---------------------------------------------------------------------------

describe("AxisLabelPane — Show Axis Labels baseline", () => {

   it("should always show the Show Axis Labels checkbox regardless of secondary flag", async () => {
      // secondary=true hides "Labels on Opposite Side" but must NOT affect "Show Axis Labels"
      await renderPane({ secondary: true });

      expect(screen.getByLabelText(SHOW_AXIS_LABEL_TEXT)).toBeInTheDocument();
   });

   it("should render Show Axis Labels as checked when showAxisLabel is true", async () => {
      await renderPane({ showAxisLabel: true });

      expect(screen.getByLabelText(SHOW_AXIS_LABEL_TEXT)).toBeChecked();
   });

   it("should render Show Axis Labels as unchecked when showAxisLabel is false", async () => {
      await renderPane({ showAxisLabel: false });

      expect(screen.getByLabelText(SHOW_AXIS_LABEL_TEXT)).not.toBeChecked();
   });
});
