/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * AssetTreePane keyboard Delete shortcut (Bug #77289).
 *
 * deleteEntries() is a @HostListener("keyup.delete") on the pane host. The asset tree's
 * search <input> is a descendant of the host, so a Delete typed in the search box bubbles
 * to the listener. It must be ignored there, and still open the delete confirm when the
 * key comes from the tree itself.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";

import { AssetType } from "../../../../../../shared/data/asset-type";
import { AssetConstants } from "../../../common/data/asset-constants";
import { ComponentTool } from "../../../common/util/component-tool";
import { ModelService } from "../../../widget/services/model.service";
import { DomService } from "../../../widget/dom-service/dom.service";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DragService } from "../../../widget/services/drag.service";
import { TreeView } from "../../../widget/tree/tree.component";
import { ComposerRecentService } from "../composer-recent.service";
import { AssetTreePane } from "./asset-tree-pane.component";

describe("AssetTreePane - Delete key shortcut (Bug #77289)", () => {
   let fixture: ComponentFixture<AssetTreePane>;
   let comp: AssetTreePane;
   let host: HTMLElement;

   beforeEach(() => {
      TestBed.configureTestingModule({
         providers: [
            provideHttpClient(),
            provideHttpClientTesting(),
            { provide: NgbModal, useValue: { open: vi.fn() } },
            { provide: FixedDropdownService, useValue: { open: vi.fn() } },
            { provide: DragService, useValue: { getDragData: vi.fn().mockReturnValue({}) } },
            { provide: DomService, useValue: {} },
            { provide: ModelService, useValue: { getModel: vi.fn().mockReturnValue(of([])) } },
            {
               provide: ComposerRecentService,
               useValue: {
                  addRecentlyViewed: vi.fn(),
                  removeRecentlyViewed: vi.fn(),
                  removeNonExistItems: vi.fn(),
                  recentlyViewedChange: vi.fn().mockReturnValue(of([])),
               },
            },
         ],
      });
      TestBed.overrideComponent(AssetTreePane, {
         set: { imports: [], schemas: [NO_ERRORS_SCHEMA] },
      });

      fixture = TestBed.createComponent(AssetTreePane);
      comp = fixture.componentInstance;
      host = fixture.nativeElement;
      document.body.appendChild(host);
      fixture.detectChanges();

      comp.inactive = false;
      comp.openedSheets = [];
      comp.opendTabs = [];
      comp.selectedNodes = [{
         label: "delkey_test",
         leaf: true,
         treeView: TreeView.FULL_VIEW,
         data: {
            scope: AssetConstants.GLOBAL_SCOPE,
            type: AssetType.VIEWSHEET,
            path: "Dashboard/delkey_test",
            identifier: "1^128^__NULL__^Dashboard/delkey_test",
            properties: {},
            folder: false,
         },
      } as any];

      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("cancel");
   });

   afterEach(() => {
      host.remove();
      vi.restoreAllMocks();
   });

   function pressDelete(target: HTMLElement): void {
      target.dispatchEvent(new KeyboardEvent("keyup", { key: "Delete", bubbles: true }));
   }

   function appendToHost<T extends HTMLElement>(el: T): T {
      host.appendChild(el);
      el.focus();
      return el;
   }

   it("should not show the delete confirm when Delete is pressed in the tree search input", () => {
      const input = document.createElement("input");
      input.type = "text";
      input.className = "form-control";
      input.value = "delkey_test";

      pressDelete(appendToHost(input));

      expect(ComponentTool.showMessageDialog).not.toHaveBeenCalled();
   });

   it("should not show the delete confirm when Delete is pressed in a textarea in the pane", () => {
      pressDelete(appendToHost(document.createElement("textarea")));

      expect(ComponentTool.showMessageDialog).not.toHaveBeenCalled();
   });

   it("should show the delete confirm when Delete is pressed on a focused tree element", () => {
      const treeDiv = document.createElement("div");
      treeDiv.tabIndex = 0;
      treeDiv.setAttribute("role", "tree");

      pressDelete(appendToHost(treeDiv));

      expect(ComponentTool.showMessageDialog).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Confirm)",
         "_#(js:common.tree.deleteSelected)",
         expect.anything(),
         expect.anything(),
      );
   });

   it("should show the delete confirm when Delete is pressed on the pane host", () => {
      pressDelete(host);

      expect(ComponentTool.showMessageDialog).toHaveBeenCalledTimes(1);
   });

   it("should not show the delete confirm for Backspace in the search input", () => {
      const input = appendToHost(document.createElement("input"));
      input.dispatchEvent(new KeyboardEvent("keyup", { key: "Backspace", bubbles: true }));

      expect(ComponentTool.showMessageDialog).not.toHaveBeenCalled();
   });
});
