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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatRadioModule } from "@angular/material/radio";
import {
   BrowserAnimationsModule
} from "@angular/platform-browser/animations";
import * as jsPlumb from "jsplumb";
import { CodemirrorService } from "../../../../../../../shared/util/codemirror/codemirror.service";
import { CustomSsoFormComponent } from "./custom-sso-form.component";

describe("CustomSsoFormComponent", () => {
   let component: CustomSsoFormComponent;
   let fixture: ComponentFixture<CustomSsoFormComponent>;

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

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            BrowserAnimationsModule,
            FormsModule,
            ReactiveFormsModule,
            MatInputModule,
            MatIconModule,
            MatFormFieldModule,
            MatRadioModule
         ],
         declarations: [ CustomSsoFormComponent ],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .overrideComponent(CustomSsoFormComponent, {set: {providers: [{provide: CodemirrorService, useValue: codemirror}]}})
         .compileComponents();

      fixture = TestBed.createComponent(CustomSsoFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   }));

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
