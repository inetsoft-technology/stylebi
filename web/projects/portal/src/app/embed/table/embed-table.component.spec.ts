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
import { TestUtils } from "../../common/test/test-utils";
import { EmbedAssemblyContextProviderFactory } from "../../vsobjects/context-provider.service";
import { EmbedTableActions } from "./embed-table-actions";
import { EmbedTableComponent } from "./embed-table.component";

/**
 * The guard itself is covered for every embed in embed-context-menu-guard.spec.ts, against a
 * stubbed actions object. This pairs it with the real EmbedTableActions instead, so the two
 * halves of the bug stay tied together: the actions class offering no menu and the component
 * declining to open one. Covers onOpenContextMenu() only, so the component is built off its
 * prototype with the three fields that method reads rather than through TestBed:
 * EmbedTableComponent is an Angular Elements custom element wired to a websocket client, and
 * standing all of that up would test the DI setup instead of the guard.
 */
describe("EmbedTableComponent.onOpenContextMenu", () => {
   let dropdownRef: any;
   let dropdownService: any;
   let miniToolbarService: any;
   let component: TestUtils.ContextMenuHost;

   beforeEach(() => {
      dropdownRef = {
         componentInstance: {},
         closeEvent: { subscribe: () => ({ unsubscribe: () => {} }) }
      };
      dropdownService = { open: vi.fn(() => dropdownRef) };
      miniToolbarService = { hiddenFreeze: vi.fn(), hiddenUnfreeze: vi.fn() };

      component = Object.create(EmbedTableComponent.prototype);
      component.vsObject = TestUtils.createMockVSTableModel("Table1");
      component.dropdownService = dropdownService;
      component.miniToolbarService = miniToolbarService;
   });

   // Bug #75951: EmbedTableActions has no menu actions, so without the guard every right click
   // opened an empty dropdown and left the mini toolbar frozen behind it.
   it("opens nothing when the assembly has no visible menu action", () => {
      component.vsObjectActions = new EmbedTableActions(component.vsObject,
         EmbedAssemblyContextProviderFactory(), false, null, null, null, null,
         () => false, () => {});
      const event: any = {
         type: "contextmenu", clientX: 10, clientY: 20, preventDefault: vi.fn()
      };

      expect(component.vsObjectActions.menuActions).toEqual([]);

      component.onOpenContextMenu(event);

      expect(dropdownService.open).not.toHaveBeenCalled();
      expect(miniToolbarService.hiddenFreeze).not.toHaveBeenCalled();
      // The browser's own context menu stays suppressed either way - having nothing of our own
      // to show is not a reason to offer the embedding page's users Reload/Save As.
      expect(event.preventDefault).toHaveBeenCalled();
   });
});
