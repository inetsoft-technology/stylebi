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
 * AddParameterDialog - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnInit add/edit setup, dialog title, and edit clone conversion
 *   Group 2 [Risk 3] - ok(): duplicate parameter confirm and replace flow
 *   Group 3 [Risk 3] - ok(): create and edit parameter commits
 *   Group 4 [Risk 2] - validators, array handling, and time-value normalization
 *   Group 5 [Risk 2] - updateDynamicValue and close/cancel outputs
 *
 * Out of scope:
 *   - getScriptTester HTTP branch: direct HttpClient consumer but unrelated to the old spec and
 *     prescan-highlighted parameter create/edit paths for this pass
 *   - suspected destroy-time async leak around confirm-dialog promises
 */

import { Component, Directive, EventEmitter, Input, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";

import { AddParameterDialogModel } from "../../../../../../../shared/schedule/model/add-parameter-dialog-model";
import { ComponentTool } from "../../../../common/util/component-tool";
import { XSchema } from "../../../../common/data/xschema";
import { ValueTypes } from "../../../../vsobjects/model/dynamic-value-model";
import { DynamicValueEditorComponent } from "../../../../widget/date-type-editor/dynamic-value-editor.component";
import { DateValueEditorComponent } from "../../../../widget/date-type-editor/date-value-editor.component";
import { TimeInstantValueEditorComponent } from "../../../../widget/date-type-editor/time-instant-value-editor.component";
import { TimeValueEditorComponent } from "../../../../widget/date-type-editor/time-value-editor.component";
import { EnterSubmitDirective } from "../../../../widget/directive/enter-submit.directive";
import { ModalHeaderComponent } from "../../../../widget/modal-header/modal-header.component";
import { AddParameterDialog } from "./add-parameter-dialog.component";

@Component({
   selector: "modal-header",
   standalone: true,
   template: "<div class=\"modal-header-stub\" [attr.title]=\"title\"></div>",
})
class ModalHeaderStub {
   @Input() title = "";
   @Input() cshid = "";
   @Output() onCancel = new EventEmitter<void>();
}

@Directive({ selector: "[enterSubmit]", standalone: true })
class EnterSubmitDirectiveStub {
   @Input() enterSubmit: (() => boolean) | boolean;
   @Output() onEnter = new EventEmitter<void>();
}

@Component({ selector: "dynamic-value-editor", standalone: true, template: "" })
class DynamicValueEditorStub {
   @Input() type: string;
   @Input() valueModel: unknown;
   @Input() columnTreeRoot: unknown;
   @Input() supportVariable: boolean;
   @Input() expressionSubmitCallback: unknown;
   @Input() task: boolean;
   @Output() onValueModelChange = new EventEmitter<void>();
}

@Component({ selector: "date-value-editor", standalone: true, template: "" })
class DateValueEditorStub {
   @Input() ngModel: unknown;
}

@Component({ selector: "time-value-editor", standalone: true, template: "" })
class TimeValueEditorStub {
   @Input() model: unknown;
   @Output() timeChange = new EventEmitter<unknown>();
}

@Component({ selector: "time-instant-value-editor", standalone: true, template: "" })
class TimeInstantValueEditorStub {
   @Input() ngModel: unknown;
   @Output() timeChange = new EventEmitter<unknown>();
}

interface RenderOptions {
   index?: number;
   parameters?: AddParameterDialogModel[];
   parameterNames?: string[];
   supportDynamic?: boolean;
}

const MODAL_MOCK = { open: vi.fn() };

function makeParameter(
   overrides: Partial<AddParameterDialogModel> = {},
): AddParameterDialogModel {
   return {
      name: "a",
      type: XSchema.STRING,
      value: {
         value: "a",
         type: ValueTypes.VALUE,
         dataType: XSchema.STRING,
      },
      array: false,
      ...overrides,
   };
}

async function renderComponent(options: RenderOptions = {}) {
   const result = await render(AddParameterDialog, {
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderStub },
         { replace: EnterSubmitDirective, with: EnterSubmitDirectiveStub },
         { replace: DynamicValueEditorComponent, with: DynamicValueEditorStub },
         { replace: DateValueEditorComponent, with: DateValueEditorStub },
         { replace: TimeValueEditorComponent, with: TimeValueEditorStub },
         { replace: TimeInstantValueEditorComponent, with: TimeInstantValueEditorStub },
      ],
      componentInputs: {
         index: options.index ?? -1,
         parameters: options.parameters,
         parameterNames: options.parameterNames ?? [],
         supportDynamic: options.supportDynamic ?? false,
      },
   });

   return {
      fixture: result.fixture,
      comp: result.fixture.componentInstance,
      nativeElement: result.fixture.nativeElement as HTMLElement,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("AddParameterDialog - single pass", () => {
   describe("Group 1 - initialization and titles", () => {
      it("should initialize a blank add model and show the Add Parameter title", async () => {
         const { comp, nativeElement } = await renderComponent({
            index: -1,
            parameters: [makeParameter()],
         });

         expect(comp.title).toBe("_#(js:Add Parameter)");
         expect(comp.model).toEqual({
            name: "",
            value: {
               value: "",
               type: ValueTypes.VALUE,
               dataType: XSchema.STRING,
            },
            array: false,
            type: XSchema.STRING,
         });
         expect(
            nativeElement.querySelector(".modal-header-stub")?.getAttribute("title"),
         ).toBe("_#(js:Add Parameter)");
      });

      it("should show the Edit Parameter title when editing an existing parameter", async () => {
         const parameters = [
            makeParameter({
               name: "stamp",
               type: XSchema.TIME_INSTANT,
               array: true,
               value: {
                  value: "2024-01-01T11:12:13,2024-01-02T14:15:16",
                  type: ValueTypes.VALUE,
                  dataType: XSchema.TIME_INSTANT,
               },
            }),
         ];
         const { comp, nativeElement } = await renderComponent({
            index: 0,
            parameters,
         });

         expect(comp.title).toBe("_#(js:Edit Parameter)");
         expect(
            nativeElement.querySelector(".modal-header-stub")?.getAttribute("title"),
         ).toBe("_#(js:Edit Parameter)");
      });

      it("should clone and convert the timeInstant array value when editing", async () => {
         const parameters = [
            makeParameter({
               name: "stamp",
               type: XSchema.TIME_INSTANT,
               array: true,
               value: {
                  value: "2024-01-01T11:12:13,2024-01-02T14:15:16",
                  type: ValueTypes.VALUE,
                  dataType: XSchema.TIME_INSTANT,
               },
            }),
         ];
         const { comp } = await renderComponent({
            index: 0,
            parameters,
         });

         expect(comp.model.value.value).toBe("2024-01-01 11:12:13,2024-01-02 14:15:16");
      });
   });

   describe("Group 2 - duplicate replacement flow", () => {
      it("should show a confirm dialog and replace the existing duplicate parameter on confirm", async () => {
         const parameters = [makeParameter({ name: "a" })];
         const showConfirmDialogSpy = vi.spyOn(ComponentTool, "showConfirmDialog")
            .mockResolvedValue("ok");
         const { comp } = await renderComponent({
            index: -1,
            parameters,
         });
         const commitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.model.name = "a";
         comp.form.controls["name"].setValue("a");
         comp.model.value.value = "replaced";
         comp.form.controls["value"].setValue("replaced");
         comp.ok();
         await Promise.resolve();

         expect(showConfirmDialogSpy).toHaveBeenCalledWith(
            MODAL_MOCK,
            "_#(js:Confirm)",
            "Replace the existing parameter: a?",
         );
         expect(parameters).toEqual([
            expect.objectContaining({
               name: "a",
               value: expect.objectContaining({ value: "replaced" }),
            }),
         ]);
         expect(commitSpy).toHaveBeenCalledWith(parameters);
      });
   });

   describe("Group 3 - create and edit commit paths", () => {
      it("should create a new array parameter whose name starts with a number", async () => {
         const parameters = [makeParameter({ name: "a" })];
         const { comp } = await renderComponent({
            index: -1,
            parameters,
         });
         const commitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.model.name = "2test";
         comp.form.controls["name"].setValue("2test");
         comp.form.controls["array"].setValue(true);
         comp.model.value.value = "a,b,c";
         comp.form.controls["value"].setValue("a,b,c");

         comp.ok();

         expect(parameters).toHaveLength(2);
         expect(parameters[1]).toEqual(
            expect.objectContaining({
               name: "2test",
               array: true,
               value: expect.objectContaining({ value: "a,b,c" }),
            }),
         );
         expect(commitSpy).toHaveBeenCalledWith(parameters);
      });

      it("should update the existing parameter in place when editing without a duplicate target", async () => {
         const parameters = [makeParameter({ name: "a" })];
         const { comp } = await renderComponent({
            index: 0,
            parameters,
         });
         const commitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.model.name = "a1";
         comp.form.controls["name"].setValue("a1");
         comp.ok();

         expect(parameters).toHaveLength(1);
         expect(parameters[0].name).toBe("a1");
         expect(commitSpy).toHaveBeenCalledWith(parameters);
      });
   });

   describe("Group 4 - validation and data normalization", () => {
      it("should show the special-character validation message for an invalid parameter name", async () => {
         const { comp, fixture, nativeElement } = await renderComponent({
            index: -1,
            parameters: [makeParameter()],
         });

         comp.form.controls["name"].setValue("^!%");
         fixture.detectChanges();

         const feedback = nativeElement.querySelector(".invalid-feedback") as HTMLElement;

         expect(comp.form.controls["name"].errors).toEqual(
            expect.objectContaining({ variableSpecialCharacters: true }),
         );
         expect(feedback.textContent?.trim()).toBe("_#(parameter.name.characterValid)");
      });

      it("should normalize a time value by appending seconds before commit", async () => {
         const parameters: AddParameterDialogModel[] = [];
         const { comp } = await renderComponent({
            index: -1,
            parameters,
         });

         comp.form.controls["type"].setValue(XSchema.TIME);
         comp.changeValue({
            value: "11:11",
            type: ValueTypes.VALUE,
            dataType: XSchema.TIME,
         });
         comp.model.name = "test";
         comp.form.controls["name"].setValue("test");

         comp.ok();

         expect(parameters[0]).toEqual(
            expect.objectContaining({
               name: "test",
               type: XSchema.TIME,
               value: expect.objectContaining({ value: "11:11:00" }),
            }),
         );
      });

      it("should block invalid integer arrays and show the typed-value error dialog", async () => {
         const showMessageDialogSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockResolvedValue(undefined);
         const { comp } = await renderComponent({
            index: -1,
            parameters: [],
         });
         const commitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.form.controls["type"].setValue(XSchema.INTEGER);
         comp.form.controls["array"].setValue(true);
         comp.model.name = "numbers";
         comp.form.controls["name"].setValue("numbers");
         comp.model.value.value = "1,abc";
         comp.form.controls["value"].setValue("1,abc");

         comp.ok();

         expect(showMessageDialogSpy).toHaveBeenCalledWith(
            MODAL_MOCK,
            "_#(js:Error)",
            "Please make sure your values are correct for the selected data type.",
         );
         expect(commitSpy).not.toHaveBeenCalled();
      });
   });

   describe("Group 5 - dynamic value and cancel flows", () => {
      it("should disable the array checkbox for expression dynamic values and re-enable it for literal values", async () => {
         const { comp } = await renderComponent({
            index: -1,
            parameters: [],
         });

         comp.model.value = {
            value: "=sum(1,2)",
            type: ValueTypes.EXPRESSION,
            dataType: XSchema.STRING,
         };
         comp.updateDynamicValue();
         expect(comp.form.controls["array"].disabled).toBe(true);

         comp.model.value = {
            value: "plain",
            type: ValueTypes.VALUE,
            dataType: XSchema.STRING,
         };
         comp.updateDynamicValue();
         expect(comp.form.controls["array"].enabled).toBe(true);
      });

      it("cancelChanges should emit onCancel with no argument", async () => {
         const { comp } = await renderComponent({
            index: -1,
            parameters: [],
         });
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");

         comp.cancelChanges();

         expect(cancelSpy).toHaveBeenCalledWith();
      });

      it("close should emit onCancel with 'cancel'", async () => {
         const { comp } = await renderComponent({
            index: -1,
            parameters: [],
         });
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");

         comp.close();

         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });
   });
});
