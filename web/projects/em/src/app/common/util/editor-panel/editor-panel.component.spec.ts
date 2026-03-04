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
import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { RouterModule } from "@angular/router";
import { MaterialTestingModule } from "../../../testing/material-testing.module";
import { EditorPanelComponent } from "./editor-panel.component";

describe("EditorPanelComponent", () => {
   let component: EditorPanelComponent;
   let fixture: ComponentFixture<EditorPanelComponent>;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            MaterialTestingModule,
            RouterModule.forRoot([])
         ],
         declarations: [
            EditorPanelComponent
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(EditorPanelComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   it("should emit unsavedChanges(true) when applyDisabled becomes false", () => {
      const emitted: boolean[] = [];
      component.unsavedChanges.subscribe((v: boolean) => emitted.push(v));

      component.applyDisabled = false;
      component.ngOnChanges({
         applyDisabled: new SimpleChange(true, false, false)
      });

      expect(emitted).toEqual([true]);
   });

   it("should emit unsavedChanges(false) when applyDisabled becomes true", () => {
      const emitted: boolean[] = [];
      component.unsavedChanges.subscribe((v: boolean) => emitted.push(v));

      component.applyDisabled = true;
      component.ngOnChanges({
         applyDisabled: new SimpleChange(false, true, false)
      });

      expect(emitted).toEqual([false]);
   });

   it("should set applyDisabled to true on handleClick", () => {
      component.applyDisabled = false;
      component.handleClick(new MouseEvent("click"));

      expect(component.applyDisabled).toBe(true);
   });
});
