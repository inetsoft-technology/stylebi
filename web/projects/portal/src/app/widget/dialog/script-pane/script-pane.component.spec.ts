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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { of } from "rxjs";
import { CodemirrorService } from "../../../../../../shared/util/codemirror/codemirror.service";
import { FormulaFunctionAnalyzerService } from "./formula-function-analyzer.service";
import { HelpUrlService } from "../../help-link/help-url.service";
import { ScriptSettingsService } from "./script-settings.service";
import { ScriptPane } from "./script-pane.component";

describe("ScriptPane", () => {
   let fixture: ComponentFixture<ScriptPane>;

   const makeCodemirrorService = (defs: object[] | null) => ({
      getEcmaScriptDefs: vi.fn(() => defs),
      createTernServer: vi.fn(() => ({ options: { typeTip: "", hintDelay: 0 }, destroy: vi.fn() })),
      createCodeMirrorInstance: vi.fn(() => ({
         setValue: vi.fn(),
         getValue: vi.fn(() => ""),
         focus: vi.fn(),
         setCursor: vi.fn(),
         lineCount: vi.fn(() => 0),
         lastLine: vi.fn(() => ""),
         on: vi.fn(),
         toTextArea: vi.fn(),
         getLine: vi.fn(() => ""),
         refresh: vi.fn(),
         getCursor: vi.fn(() => ({ line: 0, ch: 0 }))
      })),
      hasToken: vi.fn(() => false)
   });

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ScriptPane],
         providers: [
            FormulaFunctionAnalyzerService,
            { provide: HelpUrlService, useValue: { getScriptHelpUrl: () => of(""), getHelpUrl: () => of("") } },
            { provide: ScriptSettingsService, useValue: { isCursorTop: () => of(false) } }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
   });

   it("should initialize without error when CodemirrorService returns null defs", () => {
      TestBed.overrideComponent(ScriptPane, {
         set: { providers: [{ provide: CodemirrorService, useValue: makeCodemirrorService(null) }] }
      });

      vi.spyOn(Element.prototype, "getClientRects").mockReturnValue({
         length: 1,
         0: new DOMRect(0, 0, 100, 100),
         item: () => new DOMRect(0, 0, 100, 100),
         [Symbol.iterator]: function*() { yield new DOMRect(0, 0, 100, 100); }
      } as any);

      fixture = TestBed.createComponent(ScriptPane);
      expect(() => fixture.detectChanges()).not.toThrow();
   });
});
