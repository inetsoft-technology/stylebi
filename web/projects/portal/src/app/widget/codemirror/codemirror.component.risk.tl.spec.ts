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
 * CodemirrorComponent — Pass 2: risk / memory leaks
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — 3000ms timer set in the "change" handler is not cleared on destroy
 *   Group 2 [Risk 2] — ngOnInit HTTP subscription (scriptDefinition GET) is not stored on destroy
 *
 * Fixed bugs (Bug #75599):
 *   Bug #75599 — checkSyntax timer leak (Group 1), FIXED: the codemirror "change" event handler
 *     sets this.timer = setTimeout(() => this.checkSyntax(expr), 3000). destroyCodeMirror()
 *     previously cleared the ternServer, codemirrorInstance, and cancelAutocomplete, but did NOT
 *     call clearTimeout(this.timer), so after ngOnDestroy the timer could fire on the dead
 *     component and call checkSyntax(), sending an HTTP POST to the backend. Fixed by adding
 *     clearTimeout(this.timer) inside destroyCodeMirror().
 *
 *   Bug #75599 — ngOnInit HTTP subscription leak (Group 2), FIXED: ngOnInit previously subscribed
 *     to this.http.get(URI_SCRIPT_SCRIPTDEFINITIPN) without storing the subscription. After
 *     ngOnDestroy, if the HTTP response arrived it would invoke the scriptDefinitions setter,
 *     which calls destroyCodeMirror() + initCodeMirror() on the dead component (scriptEditor
 *     ViewChild is null at that point, causing a null-reference crash). Fixed by storing the
 *     subscription in a field (scriptDefinitionsSubscription) and unsubscribing it in
 *     ngOnDestroy.
 *
 * Out of scope: all interaction paths — covered in codemirror.component.interaction.tl.spec.ts.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { Subject, of } from "rxjs";
import { HttpClient } from "@angular/common/http";
import { CodemirrorComponent } from "./codemirror.component";
import { CodemirrorService } from "../../../../../shared/util/codemirror/codemirror.service";
import { FormulaFunctionAnalyzerService } from "../dialog/script-pane/formula-function-analyzer.service";
import { HelpUrlService } from "../help-link/help-url.service";

// ---------------------------------------------------------------------------
// Shared mock infrastructure (mirrors interaction spec)
// ---------------------------------------------------------------------------

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

function makeHttpMock(getObservable: any = of(null)) {
   return {
      get: vi.fn().mockReturnValue(getObservable),
      post: vi.fn().mockReturnValue(of(null)),
   };
}

async function renderComponent(httpMock = makeHttpMock()) {
   cmHandlers = {};
   const { fixture } = await render(CodemirrorComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: CodemirrorService, useValue: codemirrorServiceMock },
         { provide: FormulaFunctionAnalyzerService, useValue: analyzerServiceMock },
         { provide: HelpUrlService, useValue: {} },
         { provide: HttpClient, useValue: httpMock },
      ],
   });
   return { comp: fixture.componentInstance as CodemirrorComponent, fixture, httpMock };
}

beforeEach(() => {
   cmHandlers = {};
   vi.clearAllMocks();
   // ngAfterViewInit schedules a 0ms timer (setTimeout(() => codemirrorInstance.refresh(), 0))
   // when the editor element is hidden (always in jsdom). Fake timers prevent this timer from
   // firing after ngOnDestroy sets codemirrorInstance=null, which would otherwise throw.
   vi.useFakeTimers();
   codemirrorServiceMock.getEcmaScriptDefs.mockReturnValue([{ Date: { prototype: { toJSON: {} } } }]);
   codemirrorServiceMock.createTernServer.mockReturnValue(mockTernServer);
   codemirrorServiceMock.createCodeMirrorInstance.mockReturnValue(mockCmInstance);
   codemirrorServiceMock.hasToken.mockReturnValue(false);
   analyzerServiceMock.syntaxAnalysis.mockReturnValue([]);
   mockCmInstance.on.mockImplementation((event: string, cb: Function) => {
      cmHandlers[event] = cb;
   });
   mockTernServer.options.typeTip = null;
});

afterEach(() => {
   vi.restoreAllMocks();
   vi.useRealTimers();
});

// ---------------------------------------------------------------------------
// Group 1: 3000ms checkSyntax timer leak [Risk 3]
// ---------------------------------------------------------------------------

describe("CodemirrorComponent — checkSyntax 3000ms timer leak", () => {
   it("should schedule a 3000ms timer when the codemirror change event fires", async () => {
      const { comp } = await renderComponent();
      const checkSyntaxSpy = vi.spyOn(comp, "checkSyntax").mockImplementation(() => {});

      vi.useFakeTimers();
      const changeHandler = cmHandlers["change"];
      expect(changeHandler).toBeDefined();
      changeHandler(mockCmInstance, { text: ["x"], origin: "+input" });

      expect(checkSyntaxSpy).not.toHaveBeenCalled(); // timer hasn't fired yet
      vi.advanceTimersByTime(3000);
      expect(checkSyntaxSpy).toHaveBeenCalledOnce();
      vi.useRealTimers();
   });

   it("should cancel the previous timer when the change event fires again before 3000ms", async () => {
      const { comp } = await renderComponent();
      const checkSyntaxSpy = vi.spyOn(comp, "checkSyntax").mockImplementation(() => {});

      vi.useFakeTimers();
      const changeHandler = cmHandlers["change"];
      changeHandler(mockCmInstance, { text: ["a"], origin: "+input" });
      vi.advanceTimersByTime(1000); // partial advance
      changeHandler(mockCmInstance, { text: ["b"], origin: "+input" }); // resets timer
      vi.advanceTimersByTime(3000); // only the second timer should fire

      expect(checkSyntaxSpy).toHaveBeenCalledOnce(); // not twice
      vi.useRealTimers();
   });

   // Bug #75599 (FIXED): destroyCodeMirror() now calls clearTimeout(this.timer). After
   // ngOnDestroy the pending 3000ms timer no longer fires, so checkSyntax() is not called on
   // the dead component and no HTTP POST request is sent to the backend.
   it("should not call checkSyntax after component is destroyed (3000ms timer leak)", async () => {
      const { comp, fixture } = await renderComponent();
      const checkSyntaxSpy = vi.spyOn(comp, "checkSyntax").mockImplementation(() => {});

      vi.useFakeTimers();
      const changeHandler = cmHandlers["change"];
      changeHandler(mockCmInstance, { text: ["x"], origin: "+input" }); // sets this.timer

      fixture.destroy(); // ngOnDestroy → destroyCodeMirror(); this.timer cleared

      vi.advanceTimersByTime(3001); // timer would have fired here if not cleared

      expect(checkSyntaxSpy).not.toHaveBeenCalled();
      vi.useRealTimers();
   });
});

// ---------------------------------------------------------------------------
// Group 2: ngOnInit HTTP subscription leak [Risk 2]
// ---------------------------------------------------------------------------

describe("CodemirrorComponent — ngOnInit scriptDefinition HTTP subscription leak", () => {
   it("should set scriptDefinitions from the HTTP response during ngOnInit", async () => {
      const defs = { myFn: {} };
      const httpMock = makeHttpMock(of(defs));
      const { comp } = await renderComponent(httpMock);
      expect(comp.scriptDefinitions).toBe(defs);
   });

   // Bug #75599 (FIXED): ngOnInit now stores the http.get() subscription in
   // scriptDefinitionsSubscription, and ngOnDestroy unsubscribes it. After ngOnDestroy, a late
   // HTTP response no longer triggers the scriptDefinitions setter, so destroyCodeMirror() +
   // initCodeMirror() are not invoked on the dead component and no null-reference crash occurs.
   it("should not invoke the scriptDefinitions setter after component is destroyed (ngOnInit HTTP leak)", async () => {
      const lateSource = new Subject<any>();
      const httpMock = makeHttpMock(lateSource.asObservable());
      const { comp, fixture } = await renderComponent(httpMock);

      // Prevent initCodeMirror from crashing on null ViewChild after destroy,
      // so the assertion (not the crash) is the observable proof of the fix.
      vi.spyOn(comp as any, "destroyCodeMirror").mockImplementation(() => {});
      vi.spyOn(comp as any, "initCodeMirror").mockImplementation(() => {});

      const initialDefs = (comp as any)._scriptDefinitions;
      fixture.destroy(); // ngOnDestroy — subscription unsubscribed

      lateSource.next({ newDef: true }); // HTTP response arrives after destroy

      // _scriptDefinitions stays at its pre-destroy value because the subscription was
      // torn down, proving the leak is fixed.
      expect((comp as any)._scriptDefinitions).toBe(initialDefs);
   });
});
