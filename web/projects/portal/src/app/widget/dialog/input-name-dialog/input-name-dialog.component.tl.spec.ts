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
 * InputNameDialog - single-pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - form control initialization, ngOnChanges refresh, and inline validation UI
 *   Group 2 [Risk 3] - ok(): sanitized commit, unchanged-trim duplicate bypass, duplicate conflict
 *   Group 3 [Risk 3] - ok(): duplicate-check error mapping for 403 and generic failures
 *   Group 4 [Risk 2] - cancel() and ngAfterViewInit focus/select behavior
 *   Group 5 [Risk 3] - destroy-time duplicate-check teardown
 *
 * Fixed bugs:
 *   - Bug #75599 (FIXED): late duplicate-check emissions no longer trigger side effects after
 *     fixture.destroy(), now that InputNameDialog implements OnDestroy and unsubscribes the
 *     stored duplicate-check Subscription.
 *
 * Mocking strategy:
 *   - ModalHeaderComponent and EnterSubmitDirective are stubbed via importOverrides because this
 *     file only verifies the dialog's own control/error/submit contracts.
 *   - ComponentTool.showMessageDialog is spied per-test; no real NgbModal flow is needed.
 */

import { ChangeDetectorRef, Component, Directive, EventEmitter, Input, Output, SimpleChange } from "@angular/core";
import { HttpErrorResponse } from "@angular/common/http";
import { ValidatorFn } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { Observable, Subject, throwError } from "rxjs";

import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ComponentTool } from "../../../common/util/component-tool";
import { EnterSubmitDirective } from "../../directive/enter-submit.directive";
import { ModalHeaderComponent } from "../../modal-header/modal-header.component";
import {
   InputNameDialog,
   ValidatorMessageInfo,
} from "./input-name-dialog.component";

@Component({ selector: "modal-header", standalone: true, template: "" })
class ModalHeaderStub {
   @Input() title = "";
   @Input() cshid = "";
   @Output() onCancel = new EventEmitter<void>();
}

@Directive({ selector: "[enterSubmit]", standalone: true })
class EnterSubmitDirectiveStub {
   @Input() enterSubmit: (() => boolean) | boolean;
   @Output() onEsc = new EventEmitter<void>();
   @Output() onEnter = new EventEmitter<void>();
}

interface InputNameDialogPrivateApi {
   changeDetectorRef: ChangeDetectorRef;
}

interface RenderOptions {
   validators?: ValidatorFn[];
   validatorMessages?: ValidatorMessageInfo[];
   value?: string;
   hasDuplicateCheck?: (value: string) => Observable<boolean>;
   duplicateMessage?: string;
}

const MODAL_MOCK = { open: vi.fn() };

function makeValidatorMessages(
   overrides: Partial<ValidatorMessageInfo>[] = [],
): ValidatorMessageInfo[] {
   const base: ValidatorMessageInfo[] = [
      { validatorName: "required", message: "Name is required" },
      { validatorName: "alphanumericalCharacters", message: "Name must contain alphanumeric characters" },
   ];

   return base.map((item, index) => ({ ...item, ...(overrides[index] ?? {}) }));
}

async function renderComponent(options: RenderOptions = {}) {
   const result = await render(InputNameDialog, {
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderStub },
         { replace: EnterSubmitDirective, with: EnterSubmitDirectiveStub },
      ],
      providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
      componentInputs: {
         validators: options.validators ?? [
            FormValidators.required,
            FormValidators.alphanumericalCharacters,
         ],
         validatorMessages: options.validatorMessages ?? makeValidatorMessages(),
         title: "Input",
         label: "Name",
         value: options.value ?? "Alpha",
         hasDuplicateCheck: options.hasDuplicateCheck,
         duplicateMessage: options.duplicateMessage ?? "_#(js:Duplicate Name)",
      },
   });

   return {
      fixture: result.fixture,
      comp: result.fixture.componentInstance,
      input: result.fixture.nativeElement.querySelector("#inputNameField") as HTMLInputElement,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("InputNameDialog - single pass", () => {
   describe("Group 1 - control initialization and validation UI", () => {
      it("should initialize from the input value and rebuild the control on ngOnChanges", async () => {
         const { comp } = await renderComponent({ value: "Alpha" });

         expect(comp.control.value).toBe("Alpha");

         comp.value = "Beta";
         comp.ngOnChanges({
            value: new SimpleChange("Alpha", "Beta", false),
         });

         expect(comp.control.value).toBe("Beta");
      });

      it("should show the required error message on the input and disable OK when the field is empty", async () => {
         const { fixture, comp, input } = await renderComponent();

         comp.control.setValue("");
         fixture.detectChanges();

         const feedback = fixture.nativeElement.querySelector(".invalid-feedback") as HTMLElement;
         const okButton = fixture.nativeElement.querySelector(".btn-primary") as HTMLButtonElement;

         expect(input.classList.contains("is-invalid")).toBe(true);
         expect(feedback.textContent?.trim()).toBe("Name is required");
         expect(okButton.disabled).toBe(true);
      });
   });

   describe("Group 2 - ok success and duplicate handling", () => {
      it("should strip control characters and emit the sanitized name when duplicate checking is not configured", async () => {
         const { comp } = await renderComponent({ value: "Alpha" });
         const commitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.control.setValue("A\u0000lpha\u007f");
         comp.ok();

         expect(commitSpy).toHaveBeenCalledWith("Alpha");
      });

      it("should bypass duplicate checking when the trimmed value matches the original name", async () => {
         const duplicateCheck = vi.fn(() => new Subject<boolean>().asObservable());
         const { comp } = await renderComponent({
            value: "Alpha",
            hasDuplicateCheck: duplicateCheck,
         });
         const commitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.control.setValue(" Alpha ");
         comp.ok();

         expect(duplicateCheck).not.toHaveBeenCalled();
         expect(commitSpy).toHaveBeenCalledWith(" Alpha ");
      });

      it("should show the duplicate dialog and avoid commit when duplicate checking reports a conflict", async () => {
         const duplicate$ = new Subject<boolean>();
         const showMessageDialogSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockResolvedValue(undefined);
         const { comp } = await renderComponent({
            value: "Alpha",
            hasDuplicateCheck: vi.fn(() => duplicate$.asObservable()),
            duplicateMessage: "Already exists",
         });
         const detachSpy = vi.spyOn(
            (comp as unknown as InputNameDialogPrivateApi).changeDetectorRef,
            "detach",
         );
         const reattachSpy = vi.spyOn(
            (comp as unknown as InputNameDialogPrivateApi).changeDetectorRef,
            "reattach",
         );
         const commitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.control.setValue("Beta");
         comp.ok();
         duplicate$.next(true);
         await Promise.resolve();

         expect(showMessageDialogSpy).toHaveBeenCalledWith(
            MODAL_MOCK,
            "_#(js:Error)",
            "Already exists",
         );
         expect(detachSpy).toHaveBeenCalled();
         expect(reattachSpy).toHaveBeenCalled();
         expect(commitSpy).not.toHaveBeenCalled();
      });

      it("should emit the sanitized name when duplicate checking succeeds", async () => {
         const duplicateCheck = vi.fn(() => new Subject<boolean>().asObservable());
         const duplicate$ = new Subject<boolean>();
         duplicateCheck.mockReturnValue(duplicate$.asObservable());
         const { comp } = await renderComponent({
            value: "Alpha",
            hasDuplicateCheck: duplicateCheck,
         });
         const commitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.control.setValue("Be\u0000ta");
         comp.ok();
         duplicate$.next(false);

         expect(duplicateCheck).toHaveBeenCalledWith("Beta");
         expect(commitSpy).toHaveBeenCalledWith("Beta");
      });
   });

   describe("Group 3 - duplicate-check error mapping", () => {
      it("should show the unauthorized message and emit cancel after a 403 duplicate-check error", async () => {
         const showMessageDialogSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockResolvedValue(undefined);
         const { comp } = await renderComponent({
            value: "Alpha",
            hasDuplicateCheck: () => throwError(() => new HttpErrorResponse({ status: 403 })),
         });
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");

         comp.control.setValue("Beta");
         comp.ok();
         await Promise.resolve();

         expect(showMessageDialogSpy).toHaveBeenCalledWith(
            MODAL_MOCK,
            "_#(js:Error)",
            "_#(js:data.datasets.unauthorized)",
         );
         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });

      it("should show the internal error message and emit cancel after a non-403 duplicate-check error", async () => {
         const showMessageDialogSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockResolvedValue(undefined);
         const { comp } = await renderComponent({
            value: "Alpha",
            hasDuplicateCheck: () => throwError(() => new HttpErrorResponse({ status: 500 })),
         });
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");

         comp.control.setValue("Beta");
         comp.ok();
         await Promise.resolve();

         expect(showMessageDialogSpy).toHaveBeenCalledWith(
            MODAL_MOCK,
            "_#(js:Error)",
            "_#(js:internal.error)",
         );
         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });
   });

   describe("Group 4 - cancel and view focus", () => {
      it("should emit cancel when cancel() is called", async () => {
         const { comp } = await renderComponent();
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");

         comp.cancel();

         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });

      it("should focus and select the input after view init", async () => {
         const focusSpy = vi.spyOn(HTMLInputElement.prototype, "focus");
         const selectSpy = vi.spyOn(HTMLInputElement.prototype, "select");

         await renderComponent();

         expect(focusSpy).toHaveBeenCalled();
         expect(selectSpy).toHaveBeenCalled();
      });
   });

   describe("Group 5 - destroy-time teardown", () => {
      // Bug #75599 (FIXED): ok() previously kept the duplicate-check subscription alive after
      // fixture.destroy(), so a late duplicate result still ran dialog side effects on the dead
      // component. The component now implements OnDestroy and unsubscribes the stored
      // Subscription in ngOnDestroy(), so a post-destroy emission is correctly ignored.
      it("should ignore late duplicate-check emissions after destroy", async () => {
         const duplicate$ = new Subject<boolean>();
         const showMessageDialogSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockResolvedValue(undefined);
         const { comp, fixture } = await renderComponent({
            value: "Alpha",
            hasDuplicateCheck: () => duplicate$.asObservable(),
         });

         comp.control.setValue("Beta");
         comp.ok();
         fixture.destroy();

         expect(showMessageDialogSpy).not.toHaveBeenCalled();

         duplicate$.next(true);

         expect(showMessageDialogSpy).not.toHaveBeenCalled();
      });
   });
});
