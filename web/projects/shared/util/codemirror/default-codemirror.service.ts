/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import * as CodeMirror from "codemirror";
import "codemirror/addon/comment/comment";
import "codemirror/addon/dialog/dialog";
import "codemirror/addon/edit/closebrackets";
import "codemirror/addon/edit/matchbrackets";
import "codemirror/addon/fold/brace-fold";
import "codemirror/addon/fold/comment-fold";
import "codemirror/addon/fold/foldcode";
import "codemirror/addon/fold/foldgutter";
import "codemirror/addon/hint/show-hint";
import "codemirror/addon/hint/sql-hint";
import "codemirror/addon/search/search";
import "codemirror/addon/search/searchcursor";
import "codemirror/addon/tern/tern";
import "codemirror/mode/javascript/javascript";
import "codemirror/mode/sql/sql";
import "codemirror/mode/groovy/groovy";
import * as ECMASCRIPT_DEFS from "tern/defs/ecmascript.json";
import { Injectable } from "@angular/core";
import { CodemirrorService } from "./codemirror.service";

type TokenType = "keyword" | "comment" | null;

@Injectable({
   providedIn: "root"
})
export class DefaultCodemirrorService extends CodemirrorService {
   createTernServer(options: any): any {
      return new CodeMirror.TernServer(options);
   }

   getEcmaScriptDefs(): any[] {
      return [ECMASCRIPT_DEFS["default"]];
   }

   createCodeMirrorInstance(element: any, config: any) {
      return CodeMirror.fromTextArea(element, config);
   }

   public hasToken(cm: any, tokenType: TokenType, value: string): boolean {
      const doc = cm.getDoc();

      for(let i = 0; i < doc.lineCount(); i++) {
         if(this.findReturnInLine(cm, i, tokenType, value)) {
            return true;
         }
      }

      return false;
   }

   private findReturnInLine(cm, line: number, tokenType: TokenType, value: string): boolean {
      let end = 0;

      while(true) {
         const token = cm.getTokenAt({line, ch: end + 1});

         if(token.type === tokenType && token.string && token.string.trim() === value) {
            return true;
         }

         if(end === token.end) {
            return false;
         }

         end = token.end;
      }
   }
}