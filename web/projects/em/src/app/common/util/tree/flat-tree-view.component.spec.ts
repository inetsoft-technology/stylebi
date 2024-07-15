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
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatMenuModule } from "@angular/material/menu";
import { MatTreeModule, MatTreeNode } from "@angular/material/tree";
import { By } from "@angular/platform-browser";
import { Observable, of } from "rxjs";
import { map } from "rxjs/operators";
import { FlatTreeDataSource } from "./flat-tree-data-source";
import { FlatTreeNode, TreeDataModel } from "./flat-tree-model";
import { FlatTreeViewComponent } from "./flat-tree-view.component";
import { ScrollingModule } from "@angular/cdk/scrolling";

class TestTreeDataSource extends FlatTreeDataSource<any, any> {
   public count = 0;

   constructor(dataStream$: Observable<TreeDataModel<any>>) {
      super();
      dataStream$.pipe(map((model) => this.transform(model)))
         .subscribe((nodes) => this.data = nodes);
   }

   protected getChildren(id: any): Observable<TreeDataModel<any>> {
      return null;
   }

   protected transform(model: TreeDataModel<any>, level: number = 0): any[] {
      return model.nodes.map((node) =>
         new FlatTreeNode(this.count++ + "", Math.ceil(this.count / 5), this.count < 5, node));
   }
}

@Component({
   selector: "em-test-content-tree-view",
   template: `
     <em-flat-tree-view [dataSource]="dataSource"
                        [treeControl]="treeControl"
                        [selectedNodes]="null"></em-flat-tree-view>
   `
})
class TestContentTreeView {
   public dataSource = new TestTreeDataSource(of({
      nodes: new Array(10).fill("").map((_, i) => i)
   }));

   public treeControl = this.dataSource.treeControl;
}

describe("FlatTreeViewComponent", () => {
   let component: TestContentTreeView;
   let fixture: ComponentFixture<TestContentTreeView>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            MatTreeModule,
            MatMenuModule,
            MatButtonModule,
            MatIconModule,
            ScrollingModule
         ],
         declarations: [
            TestContentTreeView,
            FlatTreeViewComponent
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      }).compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(TestContentTreeView);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create a mock tree", () => {
      expect(component).toBeTruthy();
      // using a virtual scroll which may render less elements than what the datasource provides
      // const treeNodes = fixture.debugElement.queryAll(By.directive(MatTreeNode));
      //
      // expect(treeNodes.length).toBe(10);
   });
});
