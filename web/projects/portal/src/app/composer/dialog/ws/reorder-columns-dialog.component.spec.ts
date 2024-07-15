/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { DebugElement, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf } from "rxjs";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { LargeFormFieldComponent } from "../../../widget/large-form-field/large-form-field.component";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { ModelService } from "../../../widget/services/model.service";
import { TooltipDirective } from "../../../widget/tooltip/tooltip.directive";
import { TooltipService } from "../../../widget/tooltip/tooltip.service";
import { ReorderColumnsDialog } from "./reorder-columns-dialog.component";
import { FeatureFlagsService } from "../../../../../../shared/feature-flags/feature-flags.service";

let createModel: () => Observable<any> = () => {
   return observableOf({
      columns: [
         {dataRef: null, alias: "", width: 1, visible: false, valid: false, sql: false},
         {dataRef: null, alias: "", width: 1, visible: false, valid: false, sql: false},
         {dataRef: null, alias: "", width: 1, visible: false, valid: false, sql: false}
         ],
      indexes: [0, 1, 2]
   });
};

describe("Reorder Columns Dialog Test", () => {
   let fixture: ComponentFixture<ReorderColumnsDialog>;
   let modelService: any;
   let tooltipService: any;
   let featureFlagsService: any;
   let deUp: DebugElement;
   let deDown: DebugElement;
   let elUp: HTMLElement;
   let elDown: HTMLElement;

   beforeEach(async(() => {
      modelService = { getModel: jest.fn(() => createModel()) };
      tooltipService = { createToolTip: jest.fn() };
      featureFlagsService = { isFeatureEnabled: jest.fn() };

      TestBed.configureTestingModule({
         imports: [ NgbModule ],
         declarations: [
            ReorderColumnsDialog, EnterSubmitDirective, LargeFormFieldComponent, TooltipDirective, ModalHeaderComponent
         ],
         providers: [
            { provide: ModelService, useValue: modelService },
            { provide: TooltipService, useValue: tooltipService },
            { provide: FeatureFlagsService, useValue: featureFlagsService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();

      fixture = TestBed.createComponent(ReorderColumnsDialog);
      TestBed.inject(ModelService);
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         deUp = fixture.debugElement.query(By.css("button.btn-up"));
         elUp = <HTMLElement>deUp.nativeElement;
         deDown = fixture.debugElement.query(By.css("button.btn-up"));
         elDown = <HTMLElement>deUp.nativeElement;
      });
   }));

   it("should disable up button before a column name is selected", async(() => {
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         expect(elUp.hasAttribute("disabled")).toBe(true);
         expect(elDown.hasAttribute("disabled")).toBe(true);
      });
   }));

   it("should enable up button after a column name is selected", async(() => {
      fixture.componentInstance.selectItem(new MouseEvent("click"), 1);
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         expect(elUp.hasAttribute("disabled")).toBe(false);
         expect(elDown.hasAttribute("disabled")).toBe(false);
      });
   }));

});
