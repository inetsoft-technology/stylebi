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

import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { of } from "rxjs";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";
import { SelectedAssetModel } from "../../../content/repository/import-export/selected-asset-model";
import { BackupFileComponent } from "./backup-file.component";

const TREE_URI = "../api/em/content/repository/tree";
const CHECK_URI = "../api/em/content/repository/export/check-permission";

function repositoryNode(label: string, path: string, type: number, children: any[] = null): any {
   return {
      label, path, owner: null, type, readOnly: false, builtIn: false, description: "",
      icon: null, visible: true, children
   };
}

function selectedAsset(path: string): SelectedAssetModel {
   return <SelectedAssetModel> <any> {
      label: path, path, type: RepositoryEntryType.VIEWSHEET, typeName: "", typeLabel: ""
   };
}

// Bug #78732: on a Mac, Cmd-click (metaKey) must add or remove an asset like Ctrl-click, and a
// repeated Ctrl/Cmd-click on a selected asset must remove it instead of appending a duplicate.
describe("BackupFileComponent modifier-key selection", () => {
   let fixture: ComponentFixture<BackupFileComponent>;
   let http: HttpTestingController;

   const treeRows = (): HTMLElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll(".flat-tree-node"));
   const highlighted = () =>
      treeRows().filter(r => r.classList.contains("selected")).map(r => r.title);
   const selectedNodes = () => fixture.componentInstance.selectedNodes.map(n => n.data.path);
   const listItems = (): HTMLElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll("mat-list-item"));
   const itemLabel = (item: HTMLElement) => item.textContent.trim();
   const selectedItems = () =>
      listItems().filter(i => i.classList.contains("selected")).map(itemLabel);
   const button = (label: string): HTMLButtonElement =>
      fixture.nativeElement.querySelector(`button[aria-label="${label}"]`);

   function dispatchClick(element: Element, init: MouseEventInit = {}): void {
      expect(element).toBeTruthy();
      element.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, ...init }));
   }

   async function clickNode(label: string, init: MouseEventInit = {}): Promise<void> {
      dispatchClick(treeRows().find(r => r.title === label), init);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
   }

   function clickItem(label: string, init: MouseEventInit = {}): void {
      dispatchClick(listItems().find(i => itemLabel(i) === label), init);
      fixture.detectChanges();
   }

   beforeEach(async () => {
      const connection = { subscribe: () => {}, disconnect: () => {} };

      await TestBed.configureTestingModule({
         imports: [BackupFileComponent, NoopAnimationsModule],
         providers: [
            provideHttpClient(),
            provideHttpClientTesting(),
            { provide: StompClientService, useValue: { connect: () => of(connection) } }
         ]
      }).compileComponents();

      http = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(BackupFileComponent);
      fixture.componentRef.setInput("enabled", true);
      fixture.detectChanges();
      http.expectOne(TREE_URI).flush({
         nodes: [
            repositoryNode("Reports", "Reports", RepositoryEntryType.FOLDER,
               [repositoryNode("Sales", "Reports/Sales", RepositoryEntryType.VIEWSHEET)]),
            repositoryNode("Dashboard", "Dashboard", RepositoryEntryType.VIEWSHEET),
            repositoryNode("Orders", "Orders", RepositoryEntryType.VIEWSHEET)
         ]
      });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
   });

   describe("repository tree", () => {
      it("adds and removes a node on Cmd-click", async () => {
         await clickNode("Dashboard");
         await clickNode("Orders", { metaKey: true });
         expect(selectedNodes()).toEqual(["Dashboard", "Orders"]);
         expect(highlighted()).toEqual(["Dashboard", "Orders"]);

         await clickNode("Orders", { metaKey: true });
         expect(selectedNodes()).toEqual(["Dashboard"]);
         expect(highlighted()).toEqual(["Dashboard"]);
      });

      it("removes the node on a repeated Ctrl-click instead of adding a duplicate", async () => {
         await clickNode("Dashboard");
         await clickNode("Orders", { ctrlKey: true });
         expect(selectedNodes()).toEqual(["Dashboard", "Orders"]);

         await clickNode("Dashboard", { ctrlKey: true });
         expect(selectedNodes()).toEqual(["Orders"]);

         await clickNode("Orders", { ctrlKey: true });
         expect(selectedNodes()).toEqual([]);
         expect(button("_#(Add)").disabled).toBe(true);
      });

      it("sends each asset once when Add follows repeated Ctrl/Cmd-clicks", async () => {
         await clickNode("Orders");
         await clickNode("Dashboard", { ctrlKey: true });
         await clickNode("Dashboard", { metaKey: true });
         await clickNode("Dashboard", { ctrlKey: true });
         expect(selectedNodes()).toEqual(["Orders", "Dashboard"]);

         // The check-permission POST is followed by a status poll on a timer. Fake timers keep
         // that poll from starting, so it can't outlive this test.
         vi.useFakeTimers();

         try {
            dispatchClick(button("_#(Add)"));
            const request = http.expectOne(CHECK_URI);
            expect(request.request.body.selectedAssets.map((a: any) => a.path))
               .toEqual(["Orders", "Dashboard"]);
            request.flush({});
            http.verify();
         }
         finally {
            vi.useRealTimers();
         }
      });
   });

   describe("selected asset list", () => {
      beforeEach(() => {
         fixture.componentInstance.selectedEntities =
            [selectedAsset("Dashboard"), selectedAsset("Reports/Sales"), selectedAsset("Orders")];
         fixture.detectChanges();
      });

      it("adds and removes an asset on Cmd-click", () => {
         clickItem("Dashboard");
         clickItem("Reports/Sales", { metaKey: true });
         expect(selectedItems()).toEqual(["Dashboard", "Reports/Sales"]);

         clickItem("Reports/Sales", { metaKey: true });
         expect(selectedItems()).toEqual(["Dashboard"]);
         expect(fixture.componentInstance.entitySelection.map(e => e.path)).toEqual(["Dashboard"]);
      });

      it("removes the asset on a repeated Ctrl-click instead of adding a duplicate", () => {
         clickItem("Dashboard");
         clickItem("Orders", { ctrlKey: true });
         clickItem("Orders", { ctrlKey: true });
         expect(fixture.componentInstance.entitySelection.map(e => e.path)).toEqual(["Dashboard"]);
         expect(selectedItems()).toEqual(["Dashboard"]);

         dispatchClick(button("_#(Remove)"));
         fixture.detectChanges();
         expect(listItems().map(itemLabel)).toEqual(["Reports/Sales", "Orders"]);
      });
   });
});
