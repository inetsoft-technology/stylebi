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
 * Testing Library migration example
 *
 * Corresponding original test: vs-submit.spec.ts
 * Demonstrates how to migrate from fixture.nativeElement.querySelector('.submit-button')
 * to screen.getByRole('button', { name: 'Submit' })
 *
 * Main changes:
 *  - render() replaces TestBed.configureTestingModule + createComponent
 *  - screen.getByRole() replaces querySelector — no dependency on CSS class names
 *  - toHaveStyle / toHaveClass replaces original style/getAttribute assertions
 *  - componentProperties replaces manual assignment + detectChanges
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { render, screen } from "@testing-library/angular";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { HttpClientTestingModule } from "@angular/common/http/testing";

import { SsoHeartbeatService } from "../../../../../../shared/sso/sso-heartbeat.service";
import { TestUtils } from "../../../common/test/test-utils";
import { StompClientService, ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { SafeFontDirective } from "../../directives/safe-font.directive";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { TimerService } from "../data-tip/timer.service";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSSubmit } from "./vs-submit.component";
import { GlobalSubmitService } from "../../util/global-submit.service";

/**
 * Common render helper function — replaces the verbose TestBed configuration in beforeEach
 * Only pass the model properties to override, others remain default
 */
async function renderSubmit(
   modelOverrides: Partial<ReturnType<typeof TestUtils.createMockVSSubmitModel>> = {},
   componentOverrides: { viewer?: boolean } = {}
) {
   const model = {
      ...TestUtils.createMockVSSubmitModel("submit1"),
      ...modelOverrides,
   };

   return render(VSSubmit, {
      imports: [ReactiveFormsModule, FormsModule, NgbModule, HttpClientTestingModule],
      declarations: [VSPopComponentDirective, SafeFontDirective],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         FormInputService,
         ViewsheetClientService,
         StompClientService,
         PopComponentService,
         GlobalSubmitService,
         { provide: ContextProvider, useValue: {} },
         { provide: DataTipService, useValue: { isDataTip: jest.fn() } },
         { provide: DebounceService, useValue: { debounce: jest.fn((key, fn, delay, args) => fn(...args)) } },
         { provide: SsoHeartbeatService, useValue: { heartbeat: jest.fn() } },
         { provide: TimerService, useValue: { defer: jest.fn((fn) => fn()) } },
      ],
      componentProperties: { model, ...componentOverrides },
   });
}

describe("VSSubmit — Testing Library style", () => {

   // Corresponds to the original Bug #18438 test
   // Original: fixture.nativeElement.querySelector('button.submit-button')
   // New: screen.getByRole('button', { name: 'Submit' }) — locate by ARIA role + text
   it("should apply horizontal alignment style", async () => {
      await renderSubmit({
         objectFormat: {
            ...TestUtils.createMockVSSubmitModel("submit1").objectFormat,
            hAlign: "right",
            vAlign: "top",
            width: 146,
            height: 65,
         },
      });

      // Locate by ARIA button role + button text, no dependency on CSS class names
      const button = screen.getByRole("button", { name: "Submit" });

      // jest-dom matcher, more readable than style['text-align']
      expect(button).toHaveStyle({ textAlign: "right" });

      // padding-bottom should not be empty when vAlign='top'
      expect(button.style.paddingBottom).not.toBe("");
   });

   // Corresponds to the original Bug #20253 test
   // Original: sub.getAttribute('class').toMatch(/.*fade-assembly.*/)
   // New: toHaveClass('fade-assembly') — clearer semantics
   it("should apply fade-assembly class when disabled", async () => {
      await renderSubmit({ enabled: false });

      const button = screen.getByRole("button", { name: "Submit" });

      // Directly assert class existence, no need for regex
      expect(button).toHaveClass("fade-assembly");
   });

   // New: Testing Library specific capability — test click interaction
   // Template condition: [disabled]="viewer && !model.enabled"
   // Button is only truly disabled when viewer=true and enabled=false
   it("should disable button when viewer mode and not enabled", async () => {
      // Template: [disabled]="viewer && !model.enabled"
      // Requires viewer=true and enabled=false to be disabled
      await renderSubmit({ enabled: false }, { viewer: true });

      // Use jest-dom toBeDisabled() to directly assert disabled state — clearer than getAttribute('disabled')
      const button = screen.getByRole("button", { name: "Submit" });
      expect(button).toBeDisabled();
   });
});