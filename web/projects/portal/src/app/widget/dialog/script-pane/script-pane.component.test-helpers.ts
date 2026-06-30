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

import { Subject } from "rxjs";
import { CodemirrorService } from "../../../../../../shared/util/codemirror/codemirror.service";
import { ScriptPane } from "./script-pane.component";

export interface MockCodeMirror {
   setValue: any;
   getValue: any;
   focus: any;
   setCursor: any;
   lineCount: any;
   lastLine: any;
   getLine: any;
   refresh: any;
   getCursor: any;
   on: any;
   off: any;
   operation: any;
   showHint: any;
   replaceRange: any;
   toTextArea: any;
   doc: {
      clearGutter: any;
      markText: any;
      setGutterMarker: any;
   };
   state: any;
   trigger: (event: string, ...args: any[]) => void;
}

export function createDomRectList(displayed: boolean = true): DOMRectList {
   const rect = new DOMRect(0, 0, 100, 30);

   return {
      length: displayed ? 1 : 0,
      0: displayed ? rect : undefined,
      item: (index: number) => displayed && index === 0 ? rect : null,
      [Symbol.iterator]: function*() {
         if(displayed) {
            yield rect;
         }
      }
   } as DOMRectList;
}

export function createCodeMirror(initialValue: string = ""): MockCodeMirror {
   let value = initialValue;
   const handlers = new Map<string, Function[]>();
   const textMarker = { clear: vi.fn() };

   const cm: MockCodeMirror = {
      setValue: vi.fn((next: string) => value = next),
      getValue: vi.fn(() => value),
      focus: vi.fn(),
      setCursor: vi.fn(),
      lineCount: vi.fn(() => 3),
      lastLine: vi.fn(() => 2),
      getLine: vi.fn(() => "tail"),
      refresh: vi.fn(),
      getCursor: vi.fn((which?: string) => {
         return which === "to" ? { line: 1, ch: 4 } : { line: 0, ch: 2 };
      }),
      on: vi.fn((event: string, cb: Function) => {
         handlers.set(event, [...(handlers.get(event) || []), cb]);
      }),
      off: vi.fn((event: string, cb: Function) => {
         handlers.set(event, (handlers.get(event) || []).filter(handler => handler !== cb));
      }),
      operation: vi.fn((cb: Function) => cb()),
      showHint: vi.fn(),
      replaceRange: vi.fn(),
      toTextArea: vi.fn(),
      doc: {
         clearGutter: vi.fn(),
         markText: vi.fn(() => textMarker),
         setGutterMarker: vi.fn()
      },
      state: {},
      trigger: (event: string, ...args: any[]) => {
         (handlers.get(event) || []).forEach(handler => handler(...args));
      }
   };

   return cm;
}

export function createTernServer() {
   return {
      options: {},
      complete: vi.fn(),
      showDocs: vi.fn(),
      updateArgHints: vi.fn(),
      destroy: vi.fn()
   };
}

export function createScriptPane(options: {
   codeMirror?: MockCodeMirror;
   defs?: any[] | null;
   displayed?: boolean;
   hasToken?: (instance: any, type: string, token: string) => boolean;
   analysisResults?: any[];
} = {}) {
   const codeMirror = options.codeMirror || createCodeMirror();
   const ternServer = createTernServer();
   const helpUrl$ = new Subject<string>();
   const cursorTop$ = new Subject<boolean>();
   const rendererCleanups: ReturnType<typeof vi.fn>[] = [];
   const container = document.createElement("div");
   const textarea = document.createElement("textarea");

   container.getClientRects = vi.fn(() => createDomRectList(options.displayed ?? true));

   const codemirrorService = {
      getEcmaScriptDefs: vi.fn(() => {
         if(options.defs === null) {
            return null;
         }

         return options.defs || [{ Date: { prototype: { toJSON: true } } }];
      }),
      createTernServer: vi.fn(() => ternServer),
      createCodeMirrorInstance: vi.fn(() => codeMirror),
      hasToken: vi.fn(options.hasToken || (() => false))
   } as unknown as CodemirrorService;

   const zone = {
      runOutsideAngular: vi.fn((fn: Function) => fn()),
      run: vi.fn((fn: Function) => fn())
   } as any;

   const analyzerService = {
      syntaxAnalysis: vi.fn(() => options.analysisResults || [])
   } as any;

   const helpService = {
      getScriptHelpUrl: vi.fn(() => helpUrl$.asObservable())
   } as any;

   const scriptSettingsService = {
      isCursorTop: vi.fn(() => cursorTop$.asObservable())
   } as any;

   const renderer = {
      listen: vi.fn(() => {
         const cleanup = vi.fn();
         rendererCleanups.push(cleanup);
         return cleanup;
      })
   } as any;

   const host = { nativeElement: document.createElement("div") } as any;
   const comp = new ScriptPane(
      codemirrorService,
      zone,
      analyzerService,
      helpService,
      scriptSettingsService,
      renderer,
      host
   );

   (comp as any).scriptEditor = { nativeElement: textarea };
   (comp as any).scriptEditorContainer = { nativeElement: container };

   return {
      comp,
      codeMirror,
      ternServer,
      codemirrorService,
      zone,
      analyzerService,
      helpService,
      scriptSettingsService,
      renderer,
      rendererCleanups,
      helpUrl$,
      cursorTop$,
      container,
      textarea
   };
}
