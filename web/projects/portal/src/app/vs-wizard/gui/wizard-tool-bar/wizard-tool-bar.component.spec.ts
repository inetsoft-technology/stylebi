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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { WizardToolBarComponent } from "./wizard-tool-bar.component";
import {
   ContextProvider,
   VSWizardContextProviderFactory
} from "../../../vsobjects/context-provider.service";

describe("WizardToolBarComponent", () => {
   let component: WizardToolBarComponent;
   let fixture: ComponentFixture<WizardToolBarComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            NgbModule
         ],
         declarations: [WizardToolBarComponent],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            {
               provide: ContextProvider,
               useFactory: VSWizardContextProviderFactory
            }
         ]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(WizardToolBarComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
