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

import { TestBed } from "@angular/core/testing";
import { TabularEditor } from "../../common/data/tabular/tabular-editor";
import { TabularView } from "../../common/data/tabular/tabular-view";
import { GooglePickerService } from "./google-picker/google-picker.service";
import { TabularViewComponent } from "./tabular-view.component";

function googlePickerView(): TabularView {
   const editor = {
      type: "GOOGLE_PICKER",
      row: 0,
      col: 1,
      enabled: true,
      visible: true,
      value: { oauthToken: "token", selectedFile: { name: "Sales", id: "abc" } },
      editorPropertyNames: [],
      editorPropertyValues: []
   } as Partial<TabularEditor> as TabularEditor;

   return {
      type: "COMPONENT",
      value: "spreadsheet",
      displayLabel: "Spreadsheet",
      row: 0,
      col: 0,
      colspan: 1,
      rowspan: 1,
      visible: true,
      required: false,
      editor,
      views: []
   } as Partial<TabularView> as TabularView;
}

describe("TabularViewComponent", () => {
   // Bug #78548: GooglePickerService lost its only provider when TabularModule was pruned,
   // so the Google Sheets "Spreadsheet" picker failed with NG0201 in every standalone host.
   it("renders the google picker editor without any extra providers", () => {
      TestBed.configureTestingModule({ imports: [TabularViewComponent] });

      const fixture = TestBed.createComponent(TabularViewComponent);
      fixture.componentRef.setInput("rootView", { views: [googlePickerView()] } as TabularView);
      fixture.detectChanges();

      const editor: HTMLElement = fixture.nativeElement.querySelector("tabular-google-picker-editor");
      expect(editor).not.toBeNull();

      const input: HTMLInputElement = editor.querySelector("input.file-input");
      expect(input).not.toBeNull();
      expect(editor.querySelector("button")).not.toBeNull();
   });

   it("shares one GooglePickerService instance so the picker script is loaded once", () => {
      TestBed.configureTestingModule({ imports: [TabularViewComponent] });
      const scriptCount = () => document.querySelectorAll("script[src='https://apis.google.com/js/api.js']").length;
      const before = scriptCount();

      const fixture = TestBed.createComponent(TabularViewComponent);
      const second = googlePickerView();
      second.row = 1;
      second.editor.row = 1;
      fixture.componentRef.setInput("rootView", { views: [googlePickerView(), second] } as TabularView);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelectorAll("tabular-google-picker-editor").length).toBe(2);
      expect(TestBed.inject(GooglePickerService)).toBeTruthy();
      expect(scriptCount() - before).toBe(1);
   });
});
