/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { ShuffleListComponent } from "../../../widget/shuffle-list/shuffle-list.component";
import { ViewsheetParametersDialogModel } from "../../data/vs/viewsheet-parameters-dialog-model";
import { ViewsheetParametersDialog } from "./viewsheet-parameters-dialog.component";

let createModel: () => ViewsheetParametersDialogModel = () => {
   return {
      enabledParameters: ["var1", "var2"],
      disabledParameters: ["var3", "var4"]
   };
};

describe("Viewsheet Parameters Dialog Unit Test", () => {
   let fixture: ComponentFixture<ViewsheetParametersDialog>;
   let comp: ViewsheetParametersDialog;

   beforeEach(() => {
      TestBed.configureTestingModule({
         declarations: [ViewsheetParametersDialog, EnterSubmitDirective, ShuffleListComponent, ModalHeaderComponent],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ViewsheetParametersDialog);
      comp = fixture.componentInstance;
      comp.model = createModel();
   });

   //#16983 shoule enable the buttons when select different parameter
   it("parameter dialog buttons status", () => {
      let btns = fixture.nativeElement.querySelectorAll(".shuffle-buttons button");
      let addBtn = btns[0];
      let removeBtn = btns[1];
      let addAllBtn = btns[2];
      let removeAllBtn = btns[3];

      fixture.detectChanges();
      expect(addBtn.disabled).toBeFalsy();
      expect(addAllBtn.disabled).toBeFalsy();
      expect(removeBtn.disabled).toBeFalsy();
      expect(removeAllBtn.disabled).toBeFalsy();

      comp.selectedEnabledIndexes = [0];
      fixture.detectChanges();
      expect(addBtn.hasAttribute("disabled")).toBeFalsy();
      expect(addAllBtn.hasAttribute("disabled")).toBeFalsy();

      comp.selectedDisabledIndexes = [0];
      fixture.detectChanges();
      expect(removeBtn.hasAttribute("disabled")).toBeFalsy();
      expect(removeAllBtn.hasAttribute("disabled")).toBeFalsy();
   });


   it("enable or disable parameters", () => {
      comp.selectedEnabledIndexes = [0];
      comp.disableParameter();
      fixture.detectChanges();
      expect(comp.model.enabledParameters.length).toBe(1);
      expect(comp.model.disabledParameters.length).toBe(3);

      comp.selectedDisabledIndexes = [1];
      comp.enableParameter();
      fixture.detectChanges();
      expect(comp.model.enabledParameters.length).toBe(2);
      expect(comp.model.disabledParameters.length).toBe(2);

      comp.disableAll();
      fixture.detectChanges();
      expect(comp.model.enabledParameters.length).toBe(0);
      expect(comp.model.disabledParameters.length).toBe(4);

      comp.enableAll();
      fixture.detectChanges();
      expect(comp.model.enabledParameters.length).toBe(4);
      expect(comp.model.disabledParameters.length).toBe(0);
   });

   it("should select multiple items with CTRL key", () => {
      let ctrlEvent: any = { ctrlKey: true };
      comp.select(true, 0, ctrlEvent);
      comp.select(true, 1, ctrlEvent);
      expect(comp.selectedEnabledIndexes.length).toBe(2);
   });

   it("should select multiple items with SHIFT key", () => {
      let shiftEvent: any = { shiftKey: true };
      comp.enableAll();
      fixture.detectChanges();
      comp.select(true, 1, new MouseEvent("", null));
      comp.select(true, 3, shiftEvent);
      expect(comp.selectedEnabledIndexes.length).toBe(3);
      comp.select(true, 0, shiftEvent);
      expect(comp.selectedEnabledIndexes.length).toBe(2);
   });

   it("should only select this item if it is part of the selection", () => {
      comp.selectedEnabledIndexes = [0, 1];
      comp.select(true, 1, new MouseEvent("", null));
      expect(comp.selectedEnabledIndexes.length).toBe(1);
      expect(comp.selectedEnabledIndexes[0]).toBe(1);
   });

   it("should unselect this item with CTRL key if it is part of the selection ", () => {
      let ctrlEvent: any = { ctrlKey: true };
      comp.selectedEnabledIndexes = [0];
      comp.select(true, 0, ctrlEvent);
      expect(comp.selectedEnabledIndexes.length).toBe(0);
   });
});
