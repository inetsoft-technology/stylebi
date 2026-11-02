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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { Observable, of } from "rxjs";
import { FlatTreeDataSource } from "./flat-tree-data-source";
import { FlatTreeNode, TreeDataModel } from "./flat-tree-model";
import { FlatTreeViewComponent } from "./flat-tree-view.component";
import { MultiSelectTreeNodeDirective } from "./multi-select-tree-node.directive";

class FourNodeDataSource extends FlatTreeDataSource<FlatTreeNode<any>, any> {
   constructor() {
      super();
      this.data = ["zero", "one", "two", "three"]
         .map(name => new FlatTreeNode<any>(name, 0, false, { name }));
   }

   protected getChildren(): Observable<TreeDataModel<any>> {
      return of({ nodes: [] });
   }

   protected transform(): FlatTreeNode<any>[] {
      return [];
   }
}

@Component({
   selector: "em-multi-select-tree-test-host",
   imports: [FlatTreeViewComponent, MultiSelectTreeNodeDirective],
   template: `
      <em-flat-tree-view emMultiSelectTree [tree]="dataSource" [currentSelection]="selection"
                         (nodesSelected)="selection = $event"
                         [dataSource]="dataSource" [treeControl]="dataSource.treeControl"
                         [selectedNodes]="selection"></em-flat-tree-view>`
})
class MultiSelectTreeHostComponent {
   dataSource = new FourNodeDataSource();
   selection: FlatTreeNode[] = [];
}

// Bug #78732: on a Mac, Cmd-click (metaKey) must add or remove a node like Ctrl-click.
describe("MultiSelectTreeNodeDirective modifier-key selection", () => {
   let fixture: ComponentFixture<MultiSelectTreeHostComponent>;

   const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
   const rows = (): HTMLElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll(".flat-tree-node"));
   const selection = () => fixture.componentInstance.selection.map(n => n.label);
   const highlighted = () =>
      rows().filter(r => r.classList.contains("selected")).map(r => r.title);

   function click(label: string, init: MouseEventInit = {}): void {
      const row = rows().find(r => r.title === label);
      expect(row).toBeTruthy();
      row.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, ...init }));
      fixture.detectChanges();
   }

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [MultiSelectTreeHostComponent, NoopAnimationsModule]
      }).compileComponents();

      fixture = TestBed.createComponent(MultiSelectTreeHostComponent);

      // cdk virtual scroll needs a few rounds before its rows render in jsdom
      for(let i = 0; i < 5; i++) {
         fixture.detectChanges();
         await fixture.whenStable();
         await sleep(20);
      }

      fixture.detectChanges();
   });

   it("adds and removes a node on Cmd-click", () => {
      click("zero");
      click("two", { metaKey: true });
      expect(selection()).toEqual(["zero", "two"]);
      expect(highlighted()).toEqual(["zero", "two"]);

      click("zero", { metaKey: true });
      expect(selection()).toEqual(["two"]);
   });

   it("adds and removes a node on Ctrl-click", () => {
      click("zero");
      click("two", { ctrlKey: true });
      expect(selection()).toEqual(["zero", "two"]);

      click("zero", { ctrlKey: true });
      expect(selection()).toEqual(["two"]);
   });

   it("keeps Shift range selection when Cmd or Ctrl is also held", () => {
      click("zero");
      click("three", { shiftKey: true });
      const range = [...selection()].sort();
      expect(range).toEqual(["one", "three", "two", "zero"]);

      click("zero");
      click("three", { shiftKey: true, metaKey: true });
      expect([...selection()].sort()).toEqual(range);

      click("zero");
      click("three", { shiftKey: true, ctrlKey: true });
      expect([...selection()].sort()).toEqual(range);
   });
});
