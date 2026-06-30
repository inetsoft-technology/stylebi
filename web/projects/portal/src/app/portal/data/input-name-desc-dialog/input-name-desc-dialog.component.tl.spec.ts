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
 * InputNameDescDialog - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - form initialization and change handling: ngOnInit/ngOnChanges rebuild the
 *                      control from value + validators; formValid/errorMessage contracts
 *   Group 2 [Risk 3] - ok(): direct commit path and duplicate-check=false commit path
 *   Group 3 [Risk 3] - ok(): duplicate-check=true shows dialog and reattaches change detector
 *   Group 4 [Risk 3] - ok(): duplicate-check error maps 403 vs non-403 messages and emits cancel
 *   Group 5 [Risk 1] - cancel(): emits cancel token
 *
 * Confirmed bugs (it.fails): none
 */

import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { HttpErrorResponse } from "@angular/common/http";
import { Validators } from "@angular/forms";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, throwError } from "rxjs";

import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ComponentTool } from "../../../common/util/component-tool";
import { InputNameDescDialog, ValidatorMessageInfo } from "./input-name-desc-dialog.component";

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: {},
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

function makeValidatorMessages(): ValidatorMessageInfo[] {
   return [
      { validatorName: "required", message: "required message" },
      { validatorName: "pattern", message: "pattern message" },
   ];
}

interface RenderOpts {
   value?: string;
   validators?: any[];
   validatorMessages?: ValidatorMessageInfo[];
   hasDuplicateCheck?: (value: string) => any;
   description?: string;
}

async function renderComp(opts: RenderOpts = {}) {
   const { fixture } = await render(InputNameDescDialog, {
      providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         value: opts.value ?? "Initial",
         description: opts.description ?? "Desc",
         validators: opts.validators ?? [FormValidators.required],
         validatorMessages: opts.validatorMessages ?? makeValidatorMessages(),
         hasDuplicateCheck: opts.hasDuplicateCheck ?? null,
      },
   });

   return { comp: fixture.componentInstance as InputNameDescDialog, fixture };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("Group 1 - form initialization and validation display", () => {
   it("should initialize the control from value on ngOnInit", async () => {
      const { comp } = await renderComp({ value: "Original" });

      expect(comp.control.value).toBe("Original");
      expect(comp.formValid()).toBe(true);
   });

   it("should rebuild the control when ngOnChanges receives a new value", async () => {
      const { comp } = await renderComp({ value: "Old" });
      comp.value = "New";

      comp.ngOnChanges({
         value: new SimpleChange("Old", "New", false),
      });

      expect(comp.control.value).toBe("New");
   });

   it("should expose the first matching validator message and report invalid form state", async () => {
      const { comp } = await renderComp({
         value: "",
         validators: [FormValidators.required, Validators.pattern(/^[A-Z]+$/)],
      });

      comp.control.setValue("");
      comp.control.markAsTouched();
      comp.control.updateValueAndValidity();

      expect(comp.formValid()).toBe(false);
      expect(comp.errorMessage).toBe("required message");
   });
});

describe("Group 2 - ok(): commit paths", () => {
   it("should emit onCommit immediately when duplicate check is absent", async () => {
      const { comp } = await renderComp({ value: "Alpha", description: "Meaningful" });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.control.setValue("Alpha");
      comp.ok();

      expect(emitSpy).toHaveBeenCalledWith({
         name: "Alpha",
         description: "Meaningful",
      });
   });

   it("should call hasDuplicateCheck and emit onCommit when duplicate=false", async () => {
      const duplicateCheck = vi.fn().mockReturnValue(of(false));
      const { comp } = await renderComp({
         value: "OldName",
         description: "Desc",
         hasDuplicateCheck: duplicateCheck,
      });
      const emitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.control.setValue("NewName");
      comp.ok();

      expect(duplicateCheck).toHaveBeenCalledWith("NewName");
      expect(emitSpy).toHaveBeenCalledWith({
         name: "NewName",
         description: "Desc",
      });
   });
});

describe("Group 3 - ok(): duplicate=true dialog branch", () => {
   it("should show the duplicate dialog, detach first, then reattach after the dialog resolves", async () => {
      const duplicateCheck = vi.fn().mockReturnValue(of(true));
      const showDialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComp({
         value: "OldName",
         hasDuplicateCheck: duplicateCheck,
      });
      const detachSpy = vi.spyOn((comp as any).changeDetectorRef, "detach");
      const reattachSpy = vi.spyOn((comp as any).changeDetectorRef, "reattach");
      const commitSpy = vi.spyOn(comp.onCommit, "emit");

      comp.control.setValue("NewName");
      comp.ok();

      expect(detachSpy).toHaveBeenCalledOnce();
      await waitFor(() => expect(showDialogSpy).toHaveBeenCalled());
      await waitFor(() => expect(reattachSpy).toHaveBeenCalledOnce());
      expect(commitSpy).not.toHaveBeenCalled();
   });
});

describe("Group 4 - ok(): duplicate-check error mapping", () => {
   it("should show the unauthorized message on a 403 error and emit cancel after dialog close", async () => {
      const duplicateCheck = vi.fn().mockReturnValue(
         throwError(() => new HttpErrorResponse({ status: 403 })),
      );
      const showDialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComp({
         value: "OldName",
         hasDuplicateCheck: duplicateCheck,
      });
      const cancelSpy = vi.spyOn(comp.onCancel, "emit");

      comp.control.setValue("NewName");
      comp.ok();

      await waitFor(() =>
         expect(showDialogSpy).toHaveBeenCalledWith(
            MODAL_MOCK,
            "_#(js:Error)",
            "_#(js:data.datasets.unauthorized)",
         ),
      );
      await waitFor(() => expect(cancelSpy).toHaveBeenCalledWith("cancel"));
   });

   it("should show the internal-error message on a non-403 error", async () => {
      const duplicateCheck = vi.fn().mockReturnValue(
         throwError(() => new HttpErrorResponse({ status: 500 })),
      );
      const showDialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      const { comp } = await renderComp({
         value: "OldName",
         hasDuplicateCheck: duplicateCheck,
      });

      comp.control.setValue("NewName");
      comp.ok();

      await waitFor(() =>
         expect(showDialogSpy).toHaveBeenCalledWith(
            MODAL_MOCK,
            "_#(js:Error)",
            "_#(js:internal.error)",
         ),
      );
   });
});

describe("Group 5 - cancel()", () => {
   it("should emit cancel", async () => {
      const { comp } = await renderComp();
      const cancelSpy = vi.spyOn(comp.onCancel, "emit");

      comp.cancel();

      expect(cancelSpy).toHaveBeenCalledWith("cancel");
   });
});
