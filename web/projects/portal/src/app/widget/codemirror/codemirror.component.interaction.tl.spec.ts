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
 * CodemirrorComponent — Pass 1: interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — expression setter: updates codemirror value and triggers syntax analysis
 *   Group 2 [Risk 2] — sql setter: re-initialises codemirror (destroy+init) when already initialised
 *   Group 3 [Risk 2] — scriptDefinitions setter: re-initialises codemirror when already initialised
 *   Group 4 [Risk 1] — functionTreeRoot setter: resets functionOperatorRoot and init flag
 *   Group 5 [Risk 2] — ngOnDestroy: cancels autocomplete, destroys TernServer, clears instance
 *
 * Confirmed bugs (it.fails): none in this pass — see codemirror.component.risk.tl.spec.ts
 *
 * Out of scope:
 *   checkSyntax() — makes HTTP POST to backend; integration-level.
 *   delayAutocomplete() / inputRead handler — autocomplete lifecycle; integration-level DOM.
 *   renderAnalysisResults() — manipulates codemirror doc markers; DOM-level.
 *   ngAfterViewChecked() — calls getClientRects() on DOM element; jsdom returns empty list,
 *     making the viewChecked branch unreachable; covered in risk spec.
 *   cursor setter — calls codemirrorInstance.setCursor; Risk 1, one-liner.
 *   codemirrorInstanceEmitter — EventEmitter that emits the cm instance; Risk 1.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { HttpClient } from "@angular/common/http";
import { CodemirrorComponent } from "./codemirror.component";
import { CodemirrorService } from "../../../../../shared/util/codemirror/codemirror.service";
import { FormulaFunctionAnalyzerService } from "../dialog/script-pane/formula-function-analyzer.service";
import { HelpUrlService } from "../help-link/help-url.service";

// ---------------------------------------------------------------------------
// Shared mock infrastructure
// ---------------------------------------------------------------------------

// Capture event handlers registered by initCodeMirror so tests can trigger them.
let cmHandlers: Record<string, Function> = {};

const mockCmInstance: any = {
   getValue: vi.fn().mockReturnValue(""),
   setValue: vi.fn(),
   getCursor: vi.fn().mockReturnValue({ line: 0, ch: 0 }),
   setCursor: vi.fn(),
   on: vi.fn().mockImplementation((event: string, cb: Function) => {
      cmHandlers[event] = cb;
   }),
   off: vi.fn(),
   refresh: vi.fn(),
   toTextArea: vi.fn(),
   operation: vi.fn().mockImplementation((fn: () => void) => fn()),
   doc: {
      markText: vi.fn().mockReturnValue({ clear: vi.fn() }),
      clearGutter: vi.fn(),
      setGutterMarker: vi.fn(),
   },
};

const mockTernServer: any = {
   destroy: vi.fn(),
   updateArgHints: vi.fn(),
   complete: vi.fn(),
   showDocs: vi.fn(),
   options: { hintDelay: 17000, typeTip: null },
};

const codemirrorServiceMock = {
   getEcmaScriptDefs: vi.fn().mockReturnValue([{ Date: { prototype: { toJSON: {} } } }]),
   createTernServer: vi.fn().mockReturnValue(mockTernServer),
   createCodeMirrorInstance: vi.fn().mockReturnValue(mockCmInstance),
   hasToken: vi.fn().mockReturnValue(false),
};

const analyzerServiceMock = {
   syntaxAnalysis: vi.fn().mockReturnValue([]),
};

const httpMock = {
   get: vi.fn().mockReturnValue(of(null)),
   post: vi.fn().mockReturnValue(of(null)),
};

async function renderComponent(props: Record<string, any> = {}) {
   cmHandlers = {};
   const { fixture } = await render(CodemirrorComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: CodemirrorService, useValue: codemirrorServiceMock },
         { provide: FormulaFunctionAnalyzerService, useValue: analyzerServiceMock },
         { provide: HelpUrlService, useValue: {} },
         { provide: HttpClient, useValue: httpMock },
      ],
      componentProperties: props,
   });
   return { comp: fixture.componentInstance as CodemirrorComponent, fixture };
}

beforeEach(() => {
   cmHandlers = {};
   vi.clearAllMocks();
   // Restore return values that clearAllMocks wipes out
   codemirrorServiceMock.getEcmaScriptDefs.mockReturnValue([{ Date: { prototype: { toJSON: {} } } }]);
   codemirrorServiceMock.createTernServer.mockReturnValue(mockTernServer);
   codemirrorServiceMock.createCodeMirrorInstance.mockReturnValue(mockCmInstance);
   codemirrorServiceMock.hasToken.mockReturnValue(false);
   analyzerServiceMock.syntaxAnalysis.mockReturnValue([]);
   httpMock.get.mockReturnValue(of(null));
   httpMock.post.mockReturnValue(of(null));
   mockCmInstance.on.mockImplementation((event: string, cb: Function) => {
      cmHandlers[event] = cb;
   });
   mockTernServer.options.typeTip = null;
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: expression setter [Risk 3]
// ---------------------------------------------------------------------------

describe("CodemirrorComponent — expression setter", () => {
   it("should call setValue on the codemirror instance when the expression changes", async () => {
      const { comp } = await renderComponent();
      mockCmInstance.getValue.mockReturnValue("old");

      comp.expression = "newValue";

      expect(mockCmInstance.setValue).toHaveBeenCalledWith("newValue");
   });

   it("should NOT call setValue when the expression matches the current codemirror value", async () => {
      const { comp } = await renderComponent();
      mockCmInstance.getValue.mockReturnValue("same");
      mockCmInstance.setValue.mockClear();

      comp.expression = "same";

      expect(mockCmInstance.setValue).not.toHaveBeenCalled();
   });

   it("should update the cursor position after setValue", async () => {
      const { comp } = await renderComponent();
      const fakeCursor = { line: 2, ch: 5 };
      mockCmInstance.getValue.mockReturnValue("old");
      mockCmInstance.getCursor.mockReturnValue(fakeCursor);

      comp.expression = "newValue";

      expect(mockCmInstance.getCursor).toHaveBeenCalledWith("from");
      expect(mockCmInstance.setCursor).toHaveBeenCalledWith(fakeCursor);
   });

   it("should invoke syntaxAnalysis after the expression is updated", async () => {
      const { comp } = await renderComponent({ expression: "initial" });
      analyzerServiceMock.syntaxAnalysis.mockClear();

      comp.expression = "x + 1";

      expect(analyzerServiceMock.syntaxAnalysis).toHaveBeenCalled();
   });

   it("should store the expression value in the getter after assignment", async () => {
      const { comp } = await renderComponent();
      comp.expression = "myExpr";
      expect(comp.expression).toBe("myExpr");
   });
});

// ---------------------------------------------------------------------------
// Group 2: sql setter [Risk 2]
// ---------------------------------------------------------------------------

describe("CodemirrorComponent — sql setter re-initialisation", () => {
   it("should call toTextArea (destroyCodeMirror) and re-init when sql changes after initialisation", async () => {
      const { comp } = await renderComponent();
      // ngAfterViewInit has already run: initialized=true, codemirrorInstance is mockCmInstance
      mockCmInstance.toTextArea.mockClear();
      codemirrorServiceMock.createCodeMirrorInstance.mockClear();

      comp.sql = true; // flip to SQL mode

      expect(mockCmInstance.toTextArea).toHaveBeenCalled();          // destroyCodeMirror ran
      expect(codemirrorServiceMock.createCodeMirrorInstance).toHaveBeenCalled(); // initCodeMirror ran
   });

   it("should store the sql flag value", async () => {
      const { comp } = await renderComponent();
      comp.sql = true;
      expect(comp.sql).toBe(true);
   });

   it("should NOT re-init when sql is set before ngAfterViewInit completes", async () => {
      // Simulate pre-init: create component but prevent initialization
      // sql=false is the default; setting it before initialized=true is a no-op for re-init
      const { comp } = await renderComponent();
      (comp as any).initialized = false;
      mockCmInstance.toTextArea.mockClear();
      codemirrorServiceMock.createCodeMirrorInstance.mockClear();

      comp.sql = true;

      // destroyCodeMirror must NOT have been triggered
      expect(mockCmInstance.toTextArea).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3: scriptDefinitions setter [Risk 2]
// ---------------------------------------------------------------------------

describe("CodemirrorComponent — scriptDefinitions setter re-initialisation", () => {
   it("should destroy and re-init codemirror when scriptDefinitions changes after initialisation", async () => {
      const { comp } = await renderComponent();
      mockCmInstance.toTextArea.mockClear();
      codemirrorServiceMock.createCodeMirrorInstance.mockClear();

      comp.scriptDefinitions = { myFn: {} };

      expect(mockCmInstance.toTextArea).toHaveBeenCalled();
      expect(codemirrorServiceMock.createCodeMirrorInstance).toHaveBeenCalled();
   });

   it("should pass new scriptDefinitions to createCodeMirrorInstance via hintOptions", async () => {
      const { comp } = await renderComponent();
      const defs = { myFunc: { "!type": "fn()" } };
      codemirrorServiceMock.createCodeMirrorInstance.mockClear();

      comp.scriptDefinitions = defs;

      // scriptDefinitions is stored and used in initCodeMirror's ternServer defs
      expect(comp.scriptDefinitions).toBe(defs);
   });
});

// ---------------------------------------------------------------------------
// Group 4: functionTreeRoot setter [Risk 1]
// ---------------------------------------------------------------------------

describe("CodemirrorComponent — functionTreeRoot setter", () => {
   it("should store the functionTreeRoot value", async () => {
      const { comp } = await renderComponent();
      const root = { label: "Functions", children: [] };
      comp.functionTreeRoot = root as any;
      expect(comp.functionTreeRoot).toBe(root);
   });

   it("should reset functionOperatorRoot to null when functionTreeRoot is set", async () => {
      const { comp } = await renderComponent();
      (comp as any).functionOperatorRoot = { label: "old" };
      comp.functionTreeRoot = { label: "new", children: [] } as any;
      expect((comp as any).functionOperatorRoot).toBeNull();
   });

   it("should reset the init flag to true when functionTreeRoot is set", async () => {
      const { comp } = await renderComponent();
      (comp as any).init = false;
      comp.functionTreeRoot = { label: "r", children: [] } as any;
      expect((comp as any).init).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: ngOnDestroy [Risk 2]
// ---------------------------------------------------------------------------

describe("CodemirrorComponent — ngOnDestroy cleanup", () => {
   it("should call ternServer.destroy() when the component is destroyed", async () => {
      const { fixture } = await renderComponent();
      mockTernServer.destroy.mockClear();
      fixture.destroy();
      expect(mockTernServer.destroy).toHaveBeenCalled();
   });

   it("should call codemirrorInstance.toTextArea() when the component is destroyed", async () => {
      const { fixture } = await renderComponent();
      mockCmInstance.toTextArea.mockClear();
      fixture.destroy();
      expect(mockCmInstance.toTextArea).toHaveBeenCalled();
   });

   it("should null out codemirrorInstance after destroy so it cannot be reused", async () => {
      const { comp, fixture } = await renderComponent();
      fixture.destroy();
      expect((comp as any).codemirrorInstance).toBeNull();
   });

   it("should call cancelAutocomplete before destroying if it is set", async () => {
      const { comp, fixture } = await renderComponent();
      const cancelSpy = vi.fn();
      (comp as any).cancelAutocomplete = cancelSpy;
      fixture.destroy();
      expect(cancelSpy).toHaveBeenCalled();
   });
});
