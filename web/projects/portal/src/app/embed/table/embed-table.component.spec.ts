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
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { TestUtils } from "../../common/test/test-utils";
import { EmbedAssemblyContextProviderFactory } from "../../vsobjects/context-provider.service";
import { AddVsObjectMode } from "../../vsobjects/command/add-vs-object-command";
import { VSTableModel } from "../../vsobjects/model/vs-table-model";
import { EmbedTableActions } from "./embed-table-actions";
import { EmbedTableComponent } from "./embed-table.component";

/**
 * The guard itself is covered for every embed in embed-context-menu-guard.spec.ts, against a
 * stubbed actions object. This pairs it with the real EmbedTableActions instead, so the two
 * halves of the bug stay tied together: the actions class offering nothing visible and the
 * component declining to open a menu. Covers onOpenContextMenu() only, so the component is built off its
 * prototype with the three fields that method reads rather than through TestBed:
 * EmbedTableComponent is an Angular Elements custom element wired to a websocket client, and
 * standing all of that up would test the DI setup instead of the guard.
 */
describe("EmbedTableComponent.onOpenContextMenu", () => {
   let dropdownRef: any;
   let dropdownService: any;
   let miniToolbarService: any;
   let component: TestUtils.ContextMenuHost;
   let table: VSTableModel;

   beforeEach(() => {
      dropdownRef = {
         componentInstance: {},
         closeEvent: { subscribe: () => ({ unsubscribe: () => {} }) }
      };
      dropdownService = { open: vi.fn(() => dropdownRef) };
      miniToolbarService = { hiddenFreeze: vi.fn(), hiddenUnfreeze: vi.fn() };

      table = TestUtils.createMockVSTableModel("Table1");
      component = Object.create(EmbedTableComponent.prototype);
      component.vsObject = table;
      component.dropdownService = dropdownService;
      component.miniToolbarService = miniToolbarService;
   });

   // Bug #75951: every EmbedTableActions menu entry is selection-gated (Bug #75961 added the only
   // ones there are), so on an untouched table nothing is visible - and without the guard every
   // right click opened an empty dropdown and left the mini toolbar frozen behind it.
   it("opens nothing when the assembly has no visible menu action", () => {
      component.vsObjectActions = new EmbedTableActions(table,
         EmbedAssemblyContextProviderFactory(), false, null, null, null, null,
         () => false, () => {});
      const event: any = {
         type: "contextmenu", clientX: 10, clientY: 20, preventDefault: vi.fn()
      };

      expect(AssemblyActionGroup.anyVisible(component.vsObjectActions.menuActions)).toBeFalsy();

      component.onOpenContextMenu(event);

      expect(dropdownService.open).not.toHaveBeenCalled();
      expect(miniToolbarService.hiddenFreeze).not.toHaveBeenCalled();
      // The browser's own context menu stays suppressed either way - having nothing of our own
      // to show is not a reason to offer the embedding page's users Reload/Save As.
      expect(event.preventDefault).toHaveBeenCalled();
   });
});

/**
 * Regression coverage for the stylebi-side half of bug-75975: <inetsoft-table> (like every other
 * embed element) never dispatched any DOM event a consumer could use to learn when it actually
 * finished rendering - a plain custom-element consumer had nothing but a fixed client-side
 * timeout to fall back on. A table has no async per-tile image loading of its own (its cells are
 * plain DOM rendered synchronously from the model by Angular change detection), so "rendered"
 * here is just "the model this command carried has been applied and change detection has run".
 * Built off the prototype for the same reason as the suite above: standing up the full DI graph
 * would test the websocket wiring instead of the dispatch.
 */
describe("EmbedTableComponent.dispatchLoaded", () => {
   let component: any;
   let host: HTMLElement;
   let table: VSTableModel;

   beforeEach(() => {
      table = TestUtils.createMockVSTableModel("Table1");
      host = document.createElement("div");
      component = Object.create(EmbedTableComponent.prototype);
      component.assemblyName = "Table1";
      component.contextProvider = EmbedAssemblyContextProviderFactory();
      component.miniToolbarService = { hiddenFreeze: vi.fn(), hiddenUnfreeze: vi.fn() };
      component.cdRef = { detectChanges: vi.fn() };
      component.elementRef = { nativeElement: host };
      component.updateVSInfo = () => {};
   });

   it("dispatches a bubbling, composed 'loaded' CustomEvent on the host element", () => {
      const listener = vi.fn();
      host.addEventListener("loaded", listener);

      component.dispatchLoaded();

      expect(listener).toHaveBeenCalledTimes(1);
      const event: CustomEvent = listener.mock.calls[0][0];
      expect(event.bubbles).toBe(true);
      expect(event.composed).toBe(true);
   });

   it("dispatches 'loaded' after applying a fresh AddVSObjectCommand", () => {
      const listener = vi.fn();
      host.addEventListener("loaded", listener);

      component.processAddVSObjectCommand({
         name: "Table1", mode: AddVsObjectMode.RUNTIME_MODE, model: table, parent: null
      });

      expect(component.cdRef.detectChanges).toHaveBeenCalled();
      expect(listener).toHaveBeenCalledTimes(1);
   });

   it("dispatches 'loaded' after applying a RefreshVSObjectCommand", () => {
      const listener = vi.fn();
      host.addEventListener("loaded", listener);
      component.vsObject = table;

      component.processRefreshVSObjectCommand({ info: table });

      expect(component.cdRef.detectChanges).toHaveBeenCalled();
      expect(listener).toHaveBeenCalledTimes(1);
   });

   it("does not dispatch 'loaded' for a command addressed to a different assembly", () => {
      const listener = vi.fn();
      host.addEventListener("loaded", listener);

      component.processAddVSObjectCommand({
         name: "SomeOtherTable", mode: AddVsObjectMode.RUNTIME_MODE, model: table, parent: null
      });

      expect(listener).not.toHaveBeenCalled();
   });
});
