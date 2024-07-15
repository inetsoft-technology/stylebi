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
import { Component } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";

import { ActionsContextmenuComponent } from "./actions-contextmenu.component";

const visibleAndInvisibleActions = [
   new AssemblyActionGroup([
      {
         id: () => "1",
         label: () => "label",
         icon: () => "icon",
         enabled: () => true,
         visible: () => true,
         action: () => {}
      },
      {
         id: () => "2",
         label: () => "label",
         icon: () => "icon",
         enabled: () => true,
         visible: () => false,
         action: () => {}
      },
   ])];
const disabledActions = [
   new AssemblyActionGroup([
      {
         id: () => "1",
         label: () => "label",
         icon: () => "icon",
         enabled: () => false,
         visible: () => true,
         action: () => {}
      }
   ])];
const multipleActionGroups = [
   new AssemblyActionGroup([
      {
         id: () => "1",
         label: () => "label",
         icon: () => "icon",
         enabled: () => true,
         visible: () => true,
         action: () => {}
      }
   ]),
   new AssemblyActionGroup([
      {
         id: () => "2",
         label: () => "label",
         icon: () => "icon",
         enabled: () => true,
         visible: () => true,
         action: () => {}
      }
   ])];
const invisibleActions = [
   new AssemblyActionGroup([
      {
         id: () => "1",
         label: () => "label",
         icon: () => "icon",
         enabled: () => true,
         visible: () => false,
         action: () => {}
      }
   ])];

@Component({
   selector: "test-app",
   template: `<actions-contextmenu [actions]="actions"></actions-contextmenu>`
})
class TestApp {
   actions: AssemblyActionGroup[];
}

describe("Actions Contextmenu Component Tests", () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         declarations: [TestApp, ActionsContextmenuComponent],
      }).compileComponents();
   });

   it("should display only visible actions", () => {
      let fixture = TestBed.createComponent(TestApp);
      let app = fixture.componentInstance;
      app.actions = visibleAndInvisibleActions;
      fixture.detectChanges(true);

      expect(fixture.debugElement.queryAll(By.css("a")).length).toBe(1);
   });

   it("should apply a css class to disabled actions", () => {
      let fixture = TestBed.createComponent(TestApp);
      let app = fixture.componentInstance;
      app.actions = disabledActions;
      fixture.detectChanges(true);

      expect(fixture.debugElement.query(By.css("a.disable-link"))).toBeTruthy();
   });

   it("should create a divider to separate action groups", () => {
      let fixture = TestBed.createComponent(TestApp);
      let app = fixture.componentInstance;
      app.actions = multipleActionGroups;
      fixture.detectChanges(true);

      expect(fixture.debugElement.queryAll(By.css(".dropdown-divider")).length).toBe(1);
   });

   it("should not create a dropdown when there are no visible actions", () => {
      let fixture = TestBed.createComponent(TestApp);
      let app = fixture.componentInstance;
      app.actions = invisibleActions;
      fixture.detectChanges(true);

      expect(fixture.debugElement.query(By.css(".show"))).toBeFalsy();
   });
});
