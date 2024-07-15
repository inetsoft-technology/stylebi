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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { BrowserAnimationsModule } from "@angular/platform-browser/animations";
import { CodemirrorService } from "../../../../../../../shared/util/codemirror/codemirror.service";
import { CustomProviderViewComponent } from "./custom-provider-view.component";

describe("CustomProviderViewComponent", () => {
   let component: CustomProviderViewComponent;
   let fixture: ComponentFixture<CustomProviderViewComponent>;

   beforeEach(() => {
      // (window.document.body as any).createTextRange = jest.fn().mockImplementation(() => ({
      //    setEnd: jest.fn(),
      //    setStart: jest.fn(),
      //    getBoundingClientRect: jest.fn(() => ({right: 0})),
      //    getClientRects: jest.fn(() => ({length: 0, left: 0, right: 0}))
      // }));
      const codemirror = {
         createTernServer: jest.fn(() => {}),
         getEcmaScriptDefs: jest.fn(() => [{"Date": {"prototype": {}}}]),
         createCodeMirrorInstance: jest.fn(() => ({
            getCursor: jest.fn(),
            setCursor: jest.fn(),
            getValue: jest.fn(() => {}),
            setValue: jest.fn(),
            refresh: jest.fn(),
            focus: jest.fn(),
            on: jest.fn(),
            toTextArea: jest.fn()
         }))
      };

      TestBed.configureTestingModule({
         imports: [
            BrowserAnimationsModule,
            FormsModule,
            ReactiveFormsModule,
            MatInputModule,
            MatIconModule
         ],
         declarations: [
            CustomProviderViewComponent
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .overrideComponent(CustomProviderViewComponent, {set: {providers: [{provide: CodemirrorService, useValue: codemirror}]}})
      .compileComponents();

      fixture = TestBed.createComponent(CustomProviderViewComponent);
      component = fixture.componentInstance;
      component.form = new FormGroup({});
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
