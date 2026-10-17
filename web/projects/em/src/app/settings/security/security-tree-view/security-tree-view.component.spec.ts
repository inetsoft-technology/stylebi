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
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatTreeModule } from "@angular/material/tree";
import { SecurityTreeViewComponent } from "./security-tree-view.component";
import { ScrollingModule } from "@angular/cdk/scrolling";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { SecurityTreeNode } from "./security-tree-node";
import { IdentityType } from "../../../../../../shared/data/identity-type";

describe("SecurityTreeViewComponent", () => {
   let component: SecurityTreeViewComponent;
   let fixture: ComponentFixture<SecurityTreeViewComponent>;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            MatTreeModule,
            MatButtonModule,
            MatIconModule,
            ScrollingModule,
            MatProgressBarModule,
            NoopAnimationsModule,
            SecurityTreeViewComponent],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(SecurityTreeViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});

// Bug #78732: on a Mac, Cmd-click (metaKey) must add or remove a node like Ctrl-click.
describe("SecurityTreeViewComponent modifier-key selection", () => {
   let fixture: ComponentFixture<SecurityTreeViewComponent>;

   const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));
   const rows = (): HTMLElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll(".mat-tree-node"));
   const name = (row: HTMLElement) => row.querySelector("p").textContent.trim().split(/\s/)[0];
   const active = () => rows().filter(r => r.classList.contains("mat-active")).map(name);

   async function settle(): Promise<void> {
      // cdk virtual scroll needs a few rounds before its rows render in jsdom
      for(let i = 0; i < 5; i++) {
         fixture.detectChanges();
         await fixture.whenStable();
         await sleep(20);
      }

      fixture.detectChanges();
   }

   async function click(label: string, init: MouseEventInit = {}): Promise<void> {
      const row = rows().find(r => name(r) === label);
      expect(row).toBeTruthy();
      row.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, ...init }));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
   }

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [SecurityTreeViewComponent, NoopAnimationsModule]
      }).compileComponents();

      fixture = TestBed.createComponent(SecurityTreeViewComponent);
      fixture.componentInstance.selectionChanged
         .subscribe(nodes => fixture.componentInstance.selectedNodes = nodes);
      fixture.detectChanges();

      const node = (nodeName: string, type: number, children: SecurityTreeNode[] = []) =>
         new SecurityTreeNode({ name: nodeName, orgID: "host-org" }, type, children, false);
      fixture.componentRef.setInput("treeData", [
         node("Users", IdentityType.USERS, [
            node("alice", IdentityType.USER),
            node("bob", IdentityType.USER),
            node("carol", IdentityType.USER)
         ])
      ]);
      await settle();
      fixture.nativeElement.querySelector("button[aria-label^='toggle']")
         .dispatchEvent(new MouseEvent("click", { bubbles: true }));
      await settle();
   });

   it("adds and removes a node on Cmd-click", async () => {
      await click("alice");
      await click("bob", { metaKey: true });
      expect(active()).toEqual(["alice", "bob"]);

      await click("alice", { metaKey: true });
      expect(active()).toEqual(["bob"]);
   });

   it("adds and removes a node on Ctrl-click", async () => {
      await click("alice");
      await click("bob", { ctrlKey: true });
      expect(active()).toEqual(["alice", "bob"]);

      await click("alice", { ctrlKey: true });
      expect(active()).toEqual(["bob"]);
   });

   it("keeps Shift range selection when Cmd or Ctrl is also held", async () => {
      await click("alice");
      await click("carol", { shiftKey: true });
      const range = active();
      expect(range).toEqual(["alice", "bob", "carol"]);

      await click("alice");
      await click("carol", { shiftKey: true, metaKey: true });
      expect(active()).toEqual(range);

      await click("alice");
      await click("carol", { shiftKey: true, ctrlKey: true });
      expect(active()).toEqual(range);
   });
});
