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
 * ScriptPane - Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnInit subscriptions and cursor-position ordering
 *   Group 2 [Risk 3] - display-refresh timers in view lifecycle hooks
 *   Group 3 [Risk 3] - delayAutocomplete cancellation and listener cleanup
 *   Group 4 [Risk 3] - analysis-result render/clear and destroy cleanup
 *
 * Out of scope this pass: direct user interaction and pure display helpers.
 */

import { TreeTool } from "../../../common/util/tree-tool";
import { CodemirrorHighlightTextInfo } from "../codemirror-highlight-text-info";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { createCodeMirror, createDomRectList, createScriptPane, cleanupScriptPaneDom } from "./script-pane.component.test-helpers";

afterEach(() => {
   cleanupScriptPaneDom();
   vi.restoreAllMocks();
   vi.useRealTimers();
});

describe("ScriptPane - ngOnInit subscriptions and cursor ordering [Group 1, Risk 3]", () => {
   it("should bind help URL and move cursor to top when cursor-top setting arrives true", () => {
      const { comp, codeMirror, helpUrl$, cursorTop$ } = createScriptPane();
      (comp as any).codemirrorInstance = codeMirror;

      comp.ngOnInit();
      helpUrl$.next("https://help.example/script");
      cursorTop$.next(true);

      expect(comp.helpURL).toBe("https://help.example/script");
      expect(codeMirror.setCursor).toHaveBeenCalledWith({ line: 0, ch: 0 });
      expect((comp as any).cursorTopLoaded).toBe(true);
      expect((comp as any).cursorPositionApplied).toBe(true);
   });

   it("should apply bottom cursor once when cursor-top setting arrives false", () => {
      const { comp, codeMirror, cursorTop$ } = createScriptPane();
      (comp as any).codemirrorInstance = codeMirror;

      comp.ngOnInit();
      cursorTop$.next(false);
      cursorTop$.next(false);

      expect(codeMirror.setCursor).toHaveBeenCalledTimes(1);
      expect(codeMirror.setCursor).toHaveBeenCalledWith({ line: 3, ch: 4 });
   });
});

describe("ScriptPane - lifecycle refresh timers [Group 2, Risk 3]", () => {
   it("should refresh CodeMirror after hidden editor becomes initialized", () => {
      vi.useFakeTimers();
      const { comp, codeMirror } = createScriptPane({ displayed: false });
      comp.columnTreeRoot = { label: "root", children: [] } as TreeNodeModel;
      vi.spyOn(TreeTool, "needUseVirtualScroll").mockReturnValue(true);

      comp.ngAfterViewInit();
      vi.runOnlyPendingTimers();

      expect(codeMirror.refresh).toHaveBeenCalledTimes(1);
   });

   it("should refresh once after view check when editor is displayed", () => {
      vi.useFakeTimers();
      const { comp, codeMirror } = createScriptPane();
      (comp as any).codemirrorInstance = codeMirror;

      comp.ngAfterViewChecked();
      vi.runOnlyPendingTimers();

      expect(codeMirror.refresh).toHaveBeenCalledTimes(1);
      expect((comp as any).viewChecked).toBe(true);
   });

   it("should reset viewChecked when a previously displayed editor becomes hidden", () => {
      const { comp, container } = createScriptPane();
      (comp as any).viewChecked = true;
      (container.getClientRects as any).mockReturnValue(createDomRectList(false));

      comp.ngAfterViewChecked();

      expect((comp as any).viewChecked).toBe(false);
   });
});

describe("ScriptPane - delayAutocomplete cancellation [Group 3, Risk 3]", () => {
   it("should cancel the previous autocomplete timer and listeners before scheduling the next one", () => {
      vi.useFakeTimers();
      const { comp, codeMirror, renderer } = createScriptPane();
      const first = vi.fn();
      const second = vi.fn();
      (comp as any).codemirrorInstance = codeMirror;
      (comp as any).ternServer = { options: { hintDelay: 25 } };

      (comp as any).delayAutocomplete(first);
      const firstCleanups = (renderer.listen as any).mock.results.map((result: any) => result.value);
      (comp as any).delayAutocomplete(second);
      vi.advanceTimersByTime(25);

      expect(first).not.toHaveBeenCalled();
      expect(second).toHaveBeenCalledTimes(1);
      expect(codeMirror.off).toHaveBeenCalled();
      expect(firstCleanups.every((cleanup: any) => cleanup.mock.calls.length > 0)).toBe(true);
      expect((comp as any).cancelAutocomplete).toBeNull();
   });

   it("should cancel pending autocomplete when registered activity listener fires", () => {
      vi.useFakeTimers();
      const { comp, codeMirror } = createScriptPane();
      const fn = vi.fn();
      (comp as any).codemirrorInstance = codeMirror;
      (comp as any).ternServer = { options: { hintDelay: 25 } };

      (comp as any).delayAutocomplete(fn);
      codeMirror.trigger("cursorActivity");
      vi.advanceTimersByTime(25);

      expect(fn).not.toHaveBeenCalled();
      expect((comp as any).cancelAutocomplete).toBeNull();
   });
});

describe("ScriptPane - analysis results and destroy cleanup [Group 4, Risk 3]", () => {
   it("should clear old markers, emit new results, and render CodeMirror gutter markers", () => {
      const oldMarker = { clear: vi.fn() };
      const result = new CodemirrorHighlightTextInfo(
         { line: 0, ch: 1 },
         { line: 0, ch: 4 },
         "bad",
         "Bad function"
      );
      const { comp, codeMirror, analyzerService } = createScriptPane({ analysisResults: [result] });
      const emitSpy = vi.spyOn(comp.analysisResultsChange, "emit");
      (comp as any).codemirrorInstance = codeMirror;
      (comp as any)._analysisResults = [
         new CodemirrorHighlightTextInfo({ line: 0, ch: 0 }, { line: 0, ch: 1 }, "old", "Old", oldMarker)
      ];

      comp.expression = "bad()";

      expect(analyzerService.syntaxAnalysis).toHaveBeenCalledWith("bad()", undefined);
      expect(oldMarker.clear).toHaveBeenCalledTimes(1);
      expect(codeMirror.doc.clearGutter).toHaveBeenCalledWith("CodeMirror-lint-markers");
      expect(emitSpy).toHaveBeenCalledWith([result]);
      expect(codeMirror.doc.markText).toHaveBeenCalledWith(
         { line: 0, ch: 1 },
         { line: 0, ch: 4 },
         expect.objectContaining({ className: "alert-danger cm-error" })
      );
      expect(codeMirror.doc.setGutterMarker).toHaveBeenCalledWith(
         0,
         "CodeMirror-lint-markers",
         expect.any(HTMLDivElement)
      );
   });

   it("should destroy tern server, text area, and pending autocomplete on ngOnDestroy", () => {
      const { comp, codeMirror, ternServer } = createScriptPane();
      const cancelAutocomplete = vi.fn();
      (comp as any).codemirrorInstance = codeMirror;
      (comp as any).ternServer = ternServer;
      (comp as any).cancelAutocomplete = cancelAutocomplete;

      comp.ngOnDestroy();

      expect(cancelAutocomplete).toHaveBeenCalledTimes(1);
      expect(ternServer.destroy).toHaveBeenCalledTimes(1);
      expect(codeMirror.toTextArea).toHaveBeenCalledTimes(1);
      expect((comp as any).ternServer).toBeNull();
      expect((comp as any).codemirrorInstance).toBeNull();
   });
});
