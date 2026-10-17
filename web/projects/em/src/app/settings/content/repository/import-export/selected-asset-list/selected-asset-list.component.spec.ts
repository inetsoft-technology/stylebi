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
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatTableModule } from "@angular/material/table";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { SelectedAssetListComponent } from "./selected-asset-list.component";

describe("SelectedAssetListComponent", () => {
   let component: SelectedAssetListComponent;
   let fixture: ComponentFixture<SelectedAssetListComponent>;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            MatTableModule,
            NoopAnimationsModule,
            SelectedAssetListComponent],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(SelectedAssetListComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});

// Bug #78732: on a Mac, Cmd-click (metaKey) must add or remove a row like Ctrl-click.
describe("SelectedAssetListComponent modifier-key selection", () => {
   let fixture: ComponentFixture<SelectedAssetListComponent>;
   let emitted: string[][];

   const asset = (path: string): any =>
      ({ label: path, path, type: 1, typeName: "VIEWSHEET", typeLabel: "" });
   const rows = (): HTMLElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll("tr.table-row"));
   const label = (row: HTMLElement) => row.querySelector("td").textContent.trim();
   const selected = () => rows().filter(r => r.classList.contains("selected-row")).map(label);

   function click(path: string, init: MouseEventInit = {}): void {
      const row = rows().find(r => label(r) === path);
      expect(row).toBeTruthy();
      row.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, ...init }));
      fixture.detectChanges();
   }

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [SelectedAssetListComponent, NoopAnimationsModule]
      }).compileComponents();

      fixture = TestBed.createComponent(SelectedAssetListComponent);
      emitted = [];
      fixture.componentInstance.selectedAssetsChange
         .subscribe(assets => emitted.push(assets.map(a => a.path)));
      fixture.componentRef.setInput("assets", [asset("Sales"), asset("Customers"), asset("Orders")]);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
   });

   it("adds a row on Cmd-click instead of replacing the selection", () => {
      click("Sales");
      click("Customers", { metaKey: true });
      expect(selected()).toEqual(["Sales", "Customers"]);
      expect(emitted[emitted.length - 1]).toEqual(["Sales", "Customers"]);
   });

   it("removes only the clicked row on Cmd-click of a selected row", () => {
      click("Sales");
      click("Customers", { metaKey: true });
      click("Sales", { metaKey: true });
      expect(selected()).toEqual(["Customers"]);
      expect(emitted[emitted.length - 1]).toEqual(["Customers"]);
   });

   it("adds and removes a row on Ctrl-click", () => {
      click("Sales");
      click("Customers", { ctrlKey: true });
      expect(selected()).toEqual(["Sales", "Customers"]);

      click("Sales", { ctrlKey: true });
      expect(selected()).toEqual(["Customers"]);
   });

   it("selects a range on Shift-click", () => {
      click("Sales");
      click("Orders", { shiftKey: true });
      expect(selected()).toEqual(["Sales", "Customers", "Orders"]);
   });

   // The toggle branches are tested before shiftKey, so Shift+Ctrl has always toggled. Shift+Cmd
   // now does the same on a Mac (before the fix Cmd was ignored and it selected a range).
   it("toggles on Shift+Cmd-click and Shift+Ctrl-click", () => {
      click("Sales");
      click("Orders", { shiftKey: true, metaKey: true });
      expect(selected()).toEqual(["Sales", "Orders"]);

      click("Orders", { shiftKey: true, ctrlKey: true });
      expect(selected()).toEqual(["Sales"]);
   });
});
