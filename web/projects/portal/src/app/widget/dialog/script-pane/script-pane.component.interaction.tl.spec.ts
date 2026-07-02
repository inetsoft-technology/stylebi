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
 * ScriptPane - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - CodeMirror initialization and input setters
 *   Group 2 [Risk 3] - editor change and tree-click output contracts
 *   Group 3 [Risk 2] - keyboard/contextmenu event guards
 *   Group 4 [Risk 2] - tree-root and virtual-scroll input refresh
 *
 * Out of scope this pass: async timer races and display-only branch helpers.
 * Covered in script-pane.component.risk.tl.spec.ts and script-pane.component.display.tl.spec.ts.
 */

import { SimpleChange } from "@angular/core";
import { TreeTool } from "../../../common/util/tree-tool";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { createCodeMirror, createScriptPane, cleanupScriptPaneDom } from "./script-pane.component.test-helpers";

afterEach(() => {
   cleanupScriptPaneDom();
   vi.restoreAllMocks();
   vi.useRealTimers();
});

describe("ScriptPane - CodeMirror initialization and input setters [Group 1, Risk 3]", () => {
   it("should initialize CodeMirror with current expression and javascript config", () => {
      const { comp, codeMirror, codemirrorService } = createScriptPane();
      const treeSpy = vi.spyOn(TreeTool, "needUseVirtualScroll").mockReturnValue(false);
      comp.columnTreeRoot = { label: "root", children: [] } as TreeNodeModel;
      (comp as any)._expression = "return value;";

      comp.ngAfterViewInit();

      const config = (codemirrorService.createCodeMirrorInstance as any).mock.calls[0][1];
      expect(config.mode).toBe("javascript");
      expect(config.readOnly).toBe(false);
      expect(codeMirror.setValue).toHaveBeenCalledWith("return value;");
      expect(codeMirror.focus).toHaveBeenCalledTimes(1);
      expect((comp as any).initialized).toBe(true);
      expect(treeSpy).toHaveBeenCalledWith(comp.columnTreeRoot);
      expect(comp.needUseVirtualScroll).toBe(false);
   });

   it("should update CodeMirror text and preserve cursor when expression input changes", () => {
      const codeMirror = createCodeMirror("old");
      const { comp, analyzerService } = createScriptPane({ codeMirror });
      (comp as any).codemirrorInstance = codeMirror;

      comp.expression = "new expression";

      expect(codeMirror.getCursor).toHaveBeenCalledWith("from");
      expect(codeMirror.setValue).toHaveBeenCalledWith("new expression");
      expect(codeMirror.setCursor).toHaveBeenCalledWith({ line: 0, ch: 2 });
      expect(analyzerService.syntaxAnalysis).toHaveBeenCalledWith("new expression", undefined);
   });

   it("should skip CodeMirror writes when expression input already matches", () => {
      const codeMirror = createCodeMirror("same");
      const { comp } = createScriptPane({ codeMirror });
      (comp as any).codemirrorInstance = codeMirror;

      comp.expression = "same";

      expect(codeMirror.setValue).not.toHaveBeenCalled();
      expect(codeMirror.setCursor).not.toHaveBeenCalled();
   });

   it("should rebuild CodeMirror when scriptDefinitions changes after initialization", () => {
      const { comp, codeMirror, codemirrorService, ternServer } = createScriptPane();
      comp.columnTreeRoot = { label: "root", children: [] } as TreeNodeModel;
      vi.spyOn(TreeTool, "needUseVirtualScroll").mockReturnValue(true);
      comp.ngAfterViewInit();

      comp.scriptDefinitions = { "!name": "custom" };

      expect(ternServer.destroy).toHaveBeenCalled();
      expect(codeMirror.toTextArea).toHaveBeenCalled();
      expect(codemirrorService.createCodeMirrorInstance).toHaveBeenCalledTimes(2);
   });

   it("should rebuild CodeMirror with SQL mode when sql input changes after initialization", () => {
      const { comp, codemirrorService } = createScriptPane();
      comp.columnTreeRoot = { label: "root", children: [] } as TreeNodeModel;
      vi.spyOn(TreeTool, "needUseVirtualScroll").mockReturnValue(true);
      comp.ngAfterViewInit();

      comp.sql = true;

      const calls = (codemirrorService.createCodeMirrorInstance as any).mock.calls;
      expect(calls[calls.length - 1][1].mode).toBe("text/x-sql");
      expect(comp.sql).toBe(true);
   });

   it("should apply cursor input directly when CodeMirror exists", () => {
      const { comp, codeMirror } = createScriptPane();
      (comp as any).codemirrorInstance = codeMirror;

      comp.cursor = { line: 5, ch: 9 };

      expect(codeMirror.setCursor).toHaveBeenCalledWith(5, 9);
   });
});

describe("ScriptPane - editor change and itemClicked outputs [Group 2, Risk 3]", () => {
   it("should emit expressionChange and update return-token state from CodeMirror change", () => {
      const { comp, codeMirror, codemirrorService } = createScriptPane({
         hasToken: (_cm, _type, token) => token === "return"
      });
      const emitSpy = vi.spyOn(comp.expressionChange, "emit");
      codeMirror.setValue("return 1;");
      (comp as any)._expression = "return 1;";
      comp.columnTreeRoot = { label: "root", children: [] } as TreeNodeModel;
      vi.spyOn(TreeTool, "needUseVirtualScroll").mockReturnValue(true);
      comp.ngAfterViewInit();

      codeMirror.trigger("change", codeMirror, { origin: "+input" });

      expect(emitSpy).toHaveBeenCalledWith({
         expression: "return 1;",
         selection: { origin: "+input" }
      });
      expect(codemirrorService.hasToken).toHaveBeenCalledWith(codeMirror, "keyword", "return");
      expect(comp.returnError).toBe(true);
   });

   it("should emit clicked node, target, expression, and selection then refocus editor", () => {
      vi.useFakeTimers();
      const { comp, codeMirror } = createScriptPane();
      (comp as any).codemirrorInstance = codeMirror;
      codeMirror.setValue("a + b");
      const emitSpy = vi.spyOn(comp.expressionChange, "emit");
      const node = { label: "Customer" } as TreeNodeModel;

      comp.itemClicked(node, "columnTree");

      expect(emitSpy).toHaveBeenCalledWith({
         target: "columnTree",
         node,
         expression: "a + b",
         selection: {
            from: { line: 0, ch: 2 },
            to: { line: 1, ch: 4 }
         }
      });

      vi.advanceTimersByTime(200);
      expect(codeMirror.focus).toHaveBeenCalled();
   });
});

describe("ScriptPane - keyboard and context menu guards [Group 3, Risk 2]", () => {
   it("should prevent escape only when preventEscape is enabled", () => {
      const { comp } = createScriptPane();
      const event = {
         preventDefault: vi.fn(),
         stopPropagation: vi.fn()
      } as unknown as KeyboardEvent;

      comp.preventEscape = true;
      comp.onKeyUp(event);

      expect(event.preventDefault).toHaveBeenCalledTimes(1);
      expect(event.stopPropagation).toHaveBeenCalledTimes(1);
   });

   it("should stop delete and ctrl clipboard/remove shortcuts from bubbling", () => {
      const { comp } = createScriptPane();
      const deleteEvent = { keyCode: 46, ctrlKey: false, stopPropagation: vi.fn() } as unknown as KeyboardEvent;
      const copyEvent = { keyCode: 67, ctrlKey: true, stopPropagation: vi.fn() } as unknown as KeyboardEvent;
      const plainCEvent = { keyCode: 67, ctrlKey: false, stopPropagation: vi.fn() } as unknown as KeyboardEvent;

      comp.blockKeys(deleteEvent);
      comp.blockKeys(copyEvent);
      comp.blockKeys(plainCEvent);

      expect(deleteEvent.stopPropagation).toHaveBeenCalledTimes(1);
      expect(copyEvent.stopPropagation).toHaveBeenCalledTimes(1);
      expect(plainCEvent.stopPropagation).not.toHaveBeenCalled();
   });

   it("should allow native context menu while stopping propagation", () => {
      const { comp } = createScriptPane();
      const event = { stopPropagation: vi.fn() } as unknown as Event;

      expect(comp.rightClick(event)).toBe(true);
      expect(event.stopPropagation).toHaveBeenCalledTimes(1);
   });
});

describe("ScriptPane - root setters and virtual scroll refresh [Group 4, Risk 2]", () => {
   it("should reset combined function/operator tree cache when roots change", () => {
      const { comp } = createScriptPane();
      (comp as any).functionOperatorRoot = { label: "cached" };
      (comp as any).init = false;

      comp.functionTreeRoot = { label: "functions", children: [] } as TreeNodeModel;

      expect((comp as any).functionOperatorRoot).toBeNull();
      expect((comp as any).init).toBe(true);
      expect(comp.functionTreeRoot.label).toBe("functions");

      (comp as any).functionOperatorRoot = { label: "cached" };
      (comp as any).init = false;
      comp.operatorTreeRoot = { label: "operators", children: [] } as TreeNodeModel;

      expect((comp as any).functionOperatorRoot).toBeNull();
      expect((comp as any).init).toBe(true);
      expect(comp.operatorTreeRoot.label).toBe("operators");
   });

   it("should recompute virtual-scroll mode when column tree changes after initialization", () => {
      const { comp } = createScriptPane();
      const root = { label: "root", children: [] } as TreeNodeModel;
      const treeSpy = vi.spyOn(TreeTool, "needUseVirtualScroll").mockReturnValue(false);
      comp.columnTreeRoot = root;
      (comp as any).initialized = true;

      comp.ngOnChanges({ columnTreeRoot: new SimpleChange(null, root, false) });

      expect(treeSpy).toHaveBeenCalledWith(root);
      expect(comp.needUseVirtualScroll).toBe(false);
   });
});
