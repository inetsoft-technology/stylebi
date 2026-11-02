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

import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { DrillParameterModel } from "../../../../../../../model/datasources/database/physical-model/logical-model/drill-parameter-model";
import { ModalHeaderComponent } from "../../../../../../../../../widget/modal-header/modal-header.component";
import { stubWestOfUtcDate } from "../../../../../../../../../widget/date-type-editor/west-of-utc-date.test-helpers";
import { ParameterDialog } from "./parameter-dialog.component";

@Component({ selector: "modal-header", template: "" })
class ModalHeaderStub {
   @Input() title: string;
   @Input() cshid: string;
   @Output() onCancel = new EventEmitter<any>();
}

/**
 * Bug #78730: with a Date constant, the real DateValueEditorComponent's [model] setter turned the
 * dialog's "yyyy-MM-dd" field into the previous day west of UTC and propagated it. The dialog
 * wrote it back to model.field, so each change-detection pass lost one more day and OK saved an
 * earlier date.
 *
 * Each case runs with the real Date (fails on the old code only when run west of UTC) and with a
 * Date stub that acts west of UTC in any zone, so the CI run in UTC still catches the bug.
 */
const ZONES: [string, () => void][] = [
   ["the real time zone", () => {}],
   ["a west-of-UTC Date", stubWestOfUtcDate],
];

describe.each(ZONES)("ParameterDialog date constant with %s", (_name, setUpZone) => {
   let fixture: ComponentFixture<ParameterDialog>;
   let parameters: DrillParameterModel[];
   let committed: DrillParameterModel[];

   beforeEach(() => {
      setUpZone();
      parameters = [
         { name: "a", field: "this.column", type: null },
         { name: "b", field: "this.column", type: null },
      ];
      committed = null;
      TestBed.configureTestingModule({ imports: [ParameterDialog] });
      TestBed.overrideComponent(ParameterDialog, {
         remove: { imports: [ModalHeaderComponent] },
         add: { imports: [ModalHeaderStub] }
      });
   });

   afterEach(() => {
      fixture?.destroy();
      fixture = null;
      vi.unstubAllGlobals();
   });

   const el = (): HTMLElement => fixture.nativeElement;
   const dateInput = () => el().querySelector<HTMLInputElement>("date-value-editor input");

   // let the dialog's setTimeout type handler and the inner NgModel's microtask write run
   async function settle(): Promise<void> {
      for(let i = 0; i < 3; i++) {
         fixture.detectChanges();
         await new Promise(resolve => setTimeout(resolve));
         await fixture.whenStable();
      }
   }

   // production-mode passes, as the application ticks on later events
   async function runPasses(passes: number = 4): Promise<void> {
      for(let i = 0; i < passes; i++) {
         fixture.detectChanges(false);
         await fixture.whenStable();
      }
   }

   async function create(index: number = -1): Promise<ParameterDialog> {
      fixture = TestBed.createComponent(ParameterDialog);
      fixture.componentRef.setInput("index", index);
      fixture.componentRef.setInput("parameters", parameters);
      fixture.componentInstance.onCommit.subscribe(
         (params: DrillParameterModel[]) => committed = params);
      await settle();
      return fixture.componentInstance;
   }

   function typeInto(input: HTMLInputElement, value: string): void {
      input.value = value;
      input.dispatchEvent(new Event("input"));
      input.dispatchEvent(new Event("change"));
   }

   function chooseOption(select: HTMLSelectElement, value: string): void {
      select.value = Array.from(select.options)
         .find(o => o.value === value || o.value.endsWith(": " + value)).value;
      select.dispatchEvent(new Event("change"));
   }

   function ok(): void {
      el().querySelector<HTMLButtonElement>(".modal-footer .btn-primary").click();
   }

   async function openSaved(field: string = "2026-03-04"): Promise<ParameterDialog> {
      parameters.push({ name: "day", field, type: "date" });
      return create(2);
   }

   it("keeps a saved date opened without an edit", async () => {
      const dialog = await openSaved();
      await runPasses();

      expect(dateInput().value).toBe("2026-03-04");
      expect(dialog.model.field).toBe("2026-03-04");
      ok();
      expect(committed[2]).toEqual({ name: "day", field: "2026-03-04", type: "date" });
   });

   it("keeps a retyped saved date over repeated change detection", async () => {
      const dialog = await openSaved();

      typeInto(dateInput(), "2026-05-06");
      await runPasses();

      expect(dialog.model.field).toBe("2026-05-06");
      expect(dateInput().value).toBe("2026-05-06");
      ok();
      expect(committed[2]).toEqual({ name: "day", field: "2026-05-06", type: "date" });
   });

   it("keeps a date typed into a new constant over repeated change detection", async () => {
      const dialog = await create();
      const name = el().querySelector<HTMLInputElement>("input[formcontrolname=name]");
      name.value = "day";
      name.dispatchEvent(new Event("input"));
      el().querySelectorAll<HTMLInputElement>("input[type=radio]")[1].click();
      await settle();
      chooseOption(el().querySelector<HTMLSelectElement>("select[formcontrolname=type]"), "date");
      await settle();

      typeInto(dateInput(), "2026-03-04");
      await runPasses();

      expect(dialog.model.field).toBe("2026-03-04");
      ok();
      expect(committed[2]).toEqual({ name: "day", field: "2026-03-04", type: "date" });
   });

   it("keeps a date picked in the datepicker over repeated change detection", async () => {
      const dialog = await openSaved();

      el().querySelector<HTMLButtonElement>(".date-value-editor-toggle").click();
      await settle();
      const day = el().querySelector<HTMLElement>("[aria-label='Tuesday, March 10, 2026']");
      expect(day).not.toBeNull();
      day.click();
      await runPasses();

      expect(dialog.model.field).toBe("2026-03-10");
      expect(dateInput().value).toBe("2026-03-10");
      ok();
      expect(committed[2]).toEqual({ name: "day", field: "2026-03-10", type: "date" });
   });

   it("does not move the saved date when unparseable text is typed", async () => {
      const dialog = await openSaved();

      typeInto(dateInput(), "garbage");
      await runPasses();

      expect(dialog.model.field).toBe("2026-03-04");
   });
});
