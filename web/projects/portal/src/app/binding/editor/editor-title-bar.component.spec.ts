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
import { TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../common/util/component-tool";
import { AiAssistantDialogService } from "../../common/services/ai-assistant-dialog.service";
import { AiAssistantService } from "../../../../../shared/ai-assistant/ai-assistant.service";
import { BindingEditor } from "./binding-editor.component";
import { EditorTitleBar } from "./editor-title-bar.component";

function createTitleBar(): EditorTitleBar {
   TestBed.configureTestingModule({
      providers: [{ provide: NgbModal, useValue: {} }]
   });

   return TestBed.runInInjectionContext(() => new EditorTitleBar(
      { aiAssistantVisible: false } as unknown as AiAssistantDialogService,
      {} as unknown as AiAssistantService));
}

describe("EditorTitleBar way back", () => {
   afterEach(() => vi.restoreAllMocks());

   const choose = (choice: string) =>
      vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue(choice);

   it("should ask what to do with the edit, with apply first so it is the default", async () => {
      const dialog = choose("keep");
      createTitleBar().back();

      expect(dialog).toHaveBeenCalledTimes(1);
      expect(Object.keys(dialog.mock.calls[0][3])).toEqual(["apply", "discard", "keep"]);
   });

   it("should apply the edit and return, as Done does, when it is chosen", async () => {
      choose("apply");
      const bar = createTitleBar();
      const closed: boolean[] = [];
      bar.onClose.subscribe(v => closed.push(v));

      bar.back();
      await Promise.resolve();
      await Promise.resolve();

      expect(closed).toEqual([true]);
   });

   it("should discard the edit and return, as Cancel does, when it is chosen", async () => {
      choose("discard");
      const bar = createTitleBar();
      const closed: boolean[] = [];
      bar.onClose.subscribe(v => closed.push(v));

      bar.back();
      await Promise.resolve();
      await Promise.resolve();

      expect(closed).toEqual([false]);
   });

   it("should stay in the editor when keep editing is chosen", async () => {
      choose("keep");
      const bar = createTitleBar();
      const closed: boolean[] = [];
      bar.onClose.subscribe(v => closed.push(v));

      bar.back();
      await Promise.resolve();
      await Promise.resolve();

      expect(closed).toEqual([]);
   });

   it("should do nothing when a duplicate dialog is refused", async () => {
      vi.spyOn(ComponentTool, "showMessageDialog").mockRejectedValue("Duplicate message ignored");
      const bar = createTitleBar();
      const closed: boolean[] = [];
      bar.onClose.subscribe(v => closed.push(v));

      bar.back();
      await Promise.resolve();
      await Promise.resolve();

      expect(closed).toEqual([]);
   });

   it("should end with Done and Cancel the way the toolbar buttons did", () => {
      const bar = createTitleBar();
      const closed: boolean[] = [];
      bar.onClose.subscribe(v => closed.push(v));

      bar.done();
      bar.cancel();

      expect(closed).toEqual([true, false]);
   });
});

describe("BindingEditor sheet name for the breadcrumb", () => {
   const nameOf = (assetId: string) => {
      const editor = Object.create(BindingEditor.prototype);
      editor.assetId = assetId;
      return editor.sheetName;
   };

   it("should be the sheet in the asset id", () => {
      expect(nameOf("1^128^__NULL__^chart-date-drill^host-org")).toBe("chart-date-drill");
   });

   it("should drop the folder of a sheet in one", () => {
      expect(nameOf("1^128^__NULL__^Examples/Sales/Overview^host-org")).toBe("Overview");
   });

   it("should be empty when there is no asset id", () => {
      expect(nameOf(null)).toBeNull();
      expect(nameOf("")).toBeNull();
   });
});
