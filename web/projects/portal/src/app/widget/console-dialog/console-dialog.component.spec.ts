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

import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModelService } from "../services/model.service";
import { DialogButtonsDirective } from "../standard-dialog/dialog-buttons.directive";
import { DialogContentDirective } from "../standard-dialog/dialog-content.directive";
import { StandardDialogComponent } from "../standard-dialog/standard-dialog.component";

import { ConsoleDialogComponent } from "./console-dialog.component";

describe("ConsoleDialogComponent", () => {
   let component: ConsoleDialogComponent;
   let fixture: ComponentFixture<ConsoleDialogComponent>;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            // Use HttpClientTestingModule (instead of HttpClientModule) so unmocked
            // HTTP requests don't try to hit the network; otherwise their failure
            // callbacks queue dialog opens that NG0205 after the fixture is destroyed.
            HttpClientTestingModule,
            ConsoleDialogComponent,
            StandardDialogComponent,
            DialogContentDirective,
            DialogButtonsDirective,
         ],
         providers: [
            { provide: ModelService },
            {
               // Stub NgbModal so any error dialog opened from a late HTTP error
               // (after the fixture is destroyed) is a no-op instead of NG0205.
               provide: NgbModal,
               useValue: {
                  open: () => ({
                     componentInstance: {},
                     result: new Promise<any>(() => {}),
                     close: () => {},
                     dismiss: () => {}
                  })
               }
            }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(ConsoleDialogComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
