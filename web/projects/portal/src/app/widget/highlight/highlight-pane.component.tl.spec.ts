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
 * HighlightPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — deleteHighlight: splice + select adjacent highlight
 *   Group 2 [Risk 2] — isUpEnable/isDownEnable: applyRow ordering guards
 *   Group 3 [Risk 2] — up/down: swap highlight order
 *   Group 4 [Risk 1] — createNewHighlight, getFontText, color/font getters
 *   Group 5 [Risk 3] — showAddHighlightDialog: add flow via ComponentTool.showDialog
 *
 * Runtime perf note: logic tests instantiate HighlightPane directly (no FontPane /fonts HTTP).
 */

import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../common/util/component-tool";
import { FontInfo } from "../../common/data/format-info-model";
import { HighlightDialogModel } from "./highlight-dialog-model";
import { HighlightModel } from "./highlight-model";
import { HighlightPane } from "./highlight-pane.component";
import { AddHighlightDialog } from "./add-highlight-dialog.component";

function highlight(name: string, applyRow = false): HighlightModel {
   return { name, applyRow, vsConditionDialogModel: { conditionList: [] } } as HighlightModel;
}

function createModel(highlights: HighlightModel[] = []): HighlightDialogModel {
   return {
      highlights,
      tableName: "Table1",
      fields: [],
   } as HighlightDialogModel;
}

/** Direct instantiation — avoids FontPane child init and GET /api/format/fonts per test. */
function createPane(model: HighlightDialogModel, selected: HighlightModel | null = null) {
   const modalService = { open: vi.fn() };
   const comp = new HighlightPane(modalService as unknown as NgbModal);
   comp.model = model;
   comp.selectedHighlight = selected ?? model.highlights[0] ?? null;
   return { comp, modalService };
}

describe("HighlightPane — deleteHighlight — selection after delete [Group 1, Risk 3]", () => {

   it("should remove selected highlight and select next item", () => {
      const h1 = highlight("h1");
      const h2 = highlight("h2");
      const model = createModel([h1, h2]);
      const { comp } = createPane(model, h1);
      const emitSpy = vi.spyOn(comp.onSelectHighlight, "emit");

      comp.deleteHighlight();

      expect(model.highlights).toEqual([h2]);
      expect(emitSpy).toHaveBeenCalledWith(h2);
   });

   it("should select previous item when deleting last highlight", () => {
      const h1 = highlight("h1");
      const h2 = highlight("h2");
      const model = createModel([h1, h2]);
      const { comp } = createPane(model, h2);
      const emitSpy = vi.spyOn(comp.onSelectHighlight, "emit");

      comp.deleteHighlight();

      expect(model.highlights).toEqual([h1]);
      expect(emitSpy).toHaveBeenCalledWith(h1);
   });

   it("should emit null when deleting the only highlight", () => {
      const h1 = highlight("h1");
      const model = createModel([h1]);
      const { comp } = createPane(model, h1);
      const emitSpy = vi.spyOn(comp.onSelectHighlight, "emit");

      comp.deleteHighlight();

      expect(model.highlights).toEqual([]);
      expect(emitSpy).toHaveBeenCalledWith(null);
   });
});

describe("HighlightPane — isUpEnable/isDownEnable — applyRow guards [Group 2, Risk 2]", () => {

   it("should disable up when row-level highlight follows non-row highlight", () => {
      const h1 = highlight("cell", false);
      const h2 = highlight("row", true);
      const model = createModel([h1, h2]);
      const { comp } = createPane(model, h2);

      expect(comp.isUpEnable()).toBe(false);
   });

   it("should enable up for first non-row highlight", () => {
      const h1 = highlight("h1");
      const h2 = highlight("h2");
      const model = createModel([h1, h2]);
      const { comp } = createPane(model, h2);

      expect(comp.isUpEnable()).toBe(true);
   });

   it("should disable down when cell highlight precedes row-level highlight at index > 0", () => {
      const h0 = highlight("row0", true);
      const h1 = highlight("cell", false);
      const h2 = highlight("row2", true);
      const model = createModel([h0, h1, h2]);
      const { comp } = createPane(model, h1);

      expect(comp.isDownEnable()).toBe(false);
   });

   // 🔁 Documents contract: index=0 skips applyRow guard — down stays enabled even if next is row-level
   it("should keep down enabled at index 0 when next highlight is row-level", () => {
      const h0 = highlight("cell", false);
      const h1 = highlight("row", true);
      const { comp } = createPane(createModel([h0, h1]), h0);

      expect(comp.isDownEnable()).toBe(true);
   });

   it("should disable down when selected highlight is the last item", () => {
      const h1 = highlight("h1");
      const h2 = highlight("h2");
      const { comp } = createPane(createModel([h1, h2]), h2);

      expect(comp.isDownEnable()).toBe(false);
   });

   it("should disable up when selected is first item", () => {
      const h1 = highlight("h1");
      const { comp } = createPane(createModel([h1, highlight("h2")]), h1);

      expect(comp.isUpEnable()).toBe(false);
   });
});

describe("HighlightPane — up/down — reorder [Group 3, Risk 2]", () => {

   it("should swap highlights on up()", () => {
      const h1 = highlight("h1");
      const h2 = highlight("h2");
      const model = createModel([h1, h2]);
      const { comp } = createPane(model, h2);

      comp.up();

      expect(model.highlights).toEqual([h2, h1]);
   });

   it("should swap highlights on down()", () => {
      const h1 = highlight("h1");
      const h2 = highlight("h2");
      const model = createModel([h1, h2]);
      const { comp } = createPane(model, h1);

      comp.down();

      expect(model.highlights).toEqual([h2, h1]);
   });
});

describe("HighlightPane — showAddHighlightDialog — add flow [Group 5, Risk 3]", () => {

   it("should append a highlight and select it when add dialog commits", () => {
      const model = createModel([]);
      let onCommit: (result: { renameIndex: number; name: string }) => void;
      const dialogInstance: { renameIndex: number; highlights: HighlightModel[] } = {
         renameIndex: -1,
         highlights: [],
      };
      vi.spyOn(ComponentTool, "showDialog").mockImplementation(
         (_modal, dialogType, commit) => {
            onCommit = commit;
            expect(dialogType).toBe(AddHighlightDialog);
            return dialogInstance as never;
         },
      );
      const { comp } = createPane(model, null);
      const emitSpy = vi.spyOn(comp.onSelectHighlight, "emit");

      comp.showAddHighlightDialog(false);

      expect(comp.renameIndex).toBe(-1);
      expect(dialogInstance.highlights).toBe(model.highlights);

      onCommit!({ renameIndex: -1, name: "Rule A" });

      expect(model.highlights).toHaveLength(1);
      expect(model.highlights[0].name).toBe("Rule A");
      expect(emitSpy).toHaveBeenCalledWith(model.highlights[0]);
   });

   it("should set renameIndex when opening rename dialog", () => {
      const h = highlight("h1");
      const model = createModel([h]);
      const dialogInstance = { renameIndex: -1, highlights: model.highlights };
      vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogInstance as never);
      const { comp } = createPane(model, h);

      comp.showAddHighlightDialog(true);

      expect(comp.renameIndex).toBe(0);
      expect(dialogInstance.renameIndex).toBe(0);
   });
});

describe("HighlightPane — helpers and getters [Group 4, Risk 1]", () => {

   it("should create new highlight with empty condition list", () => {
      const { comp } = createPane(createModel());
      comp.model.tableName = "Orders";
      comp.model.fields = [];

      const created = comp.createNewHighlight("NewRule");

      expect(created.name).toBe("NewRule");
      expect(created.vsConditionDialogModel.tableName).toBe("Orders");
      expect(created.vsConditionDialogModel.conditionList).toEqual([]);
   });

   it("should emit onSelectHighlight from selectHighlight", () => {
      const h = highlight("h1");
      const { comp } = createPane(createModel([h]), h);
      const emitSpy = vi.spyOn(comp.onSelectHighlight, "emit");

      comp.selectHighlight(h);

      expect(emitSpy).toHaveBeenCalledWith(h);
   });

   it("should lazily create fontInfo via font getter", () => {
      const h = highlight("h1");
      const { comp } = createPane(createModel([h]), h);

      expect(h.fontInfo).toBeUndefined();
      const font = comp.font;

      expect(font).toBeInstanceOf(FontInfo);
      expect(h.fontInfo).toBe(font);
   });

   it("should build font string via getFontText", () => {
      const h = highlight("h1");
      h.fontInfo = {
         fontFamily: "Arial",
         fontSize: "12",
         fontStyle: "italic",
         fontWeight: "bold",
         fontUnderline: "underline",
         fontStrikethrough: "strikethrough",
      } as FontInfo;
      const { comp } = createPane(createModel([h]), h);

      expect(comp.getFontText()).toBe("Arial-12-italic-bold-underline-strikethrough");
   });

   it("should return empty font text when no fontInfo", () => {
      const { comp } = createPane(createModel(), null);

      expect(comp.getFontText()).toBe("");
   });

   it("should read and write foreground on selected highlight", () => {
      const h = highlight("h1");
      const { comp } = createPane(createModel([h]), h);

      comp.foreground = "#ff0000";

      expect(comp.foreground).toBe("#ff0000");
      expect(h.foreground).toBe("#ff0000");
   });
});
