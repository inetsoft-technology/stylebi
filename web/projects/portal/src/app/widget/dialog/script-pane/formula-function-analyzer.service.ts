/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable } from "@angular/core";
import { AnalysisResult } from "./analysis-result";
import { CodemirrorHighlightTextInfo } from "../codemirror-highlight-text-info";
import { ExpandStringDirective } from "../../expand-string/expand-string.directive";
import { TreeNodeModel } from "../../tree/tree-node-model";

@Injectable({
   providedIn: "root"
})
export class FormulaFunctionAnalyzerService {

   syntaxAnalysis(expression: string, functionTreeRoot: TreeNodeModel): AnalysisResult[] {
      const analysisResults: AnalysisResult[] = [];
      const globalFunctions = this.getGlobalFunctions(functionTreeRoot);
      // function missing dot.
      this.functionMissingDotAnalysis(expression, functionTreeRoot, globalFunctions,
         analysisResults);

      return analysisResults;
   }

   private functionMissingDotAnalysis(expression: string,
                                      node: TreeNodeModel,
                                      globalFunctions: string[],
                                      highLightTexts: AnalysisResult[]): void
   {
      if(!!!node || node.leaf && !node.data?.dot || !node.leaf && node.children.length < 1) {
         return;
      }

      if(node.leaf) {
         const func: string = node.data.data;

         // if the function is global then no need to check for the dot since it's valid either way
         if(globalFunctions.indexOf(this.getFunctionName(func)) >= 0) {
            return;
         }

         const searchFunc = func.endsWith(")") ? func.substring(0, func.length - 1) : func;
         let lines = expression.split(/\r?\n/);

         lines.forEach((line, i) => {
            let offset = 0;

            while(offset >= 0 && offset < line.length) {
               const index = line.indexOf(searchFunc, offset);

               if(index < 0) {
                  break;
               }

               offset = index + searchFunc.length;

               if(index === 0 || line.charAt(index - 1) !== ".") {
                  highLightTexts.push(new CodemirrorHighlightTextInfo(
                     {
                        line: i,
                        ch: index
                     },
                     FormulaFunctionAnalyzerService.findToPosition(i, index, searchFunc),
                     func,
                     ExpandStringDirective.expandString(
                        "_#(js:formula.editor.function.invalid)", [func])
                  ));
               }
            }
         });
      }
      else {
         node.children.forEach(cNode => this.functionMissingDotAnalysis(
            expression, cNode, globalFunctions, highLightTexts));
      }
   }

   private static findToPosition(startLine: number,
                                 startCh: number,
                                 matchStr: string): {line, ch}
   {
      let ch = startCh + matchStr.length;

      if(matchStr.endsWith("(")) {
         ch--;
      }

      return {
         line: startLine,
         ch: ch
      };
   }

   /**
    * Get a list of global functions that don't require a preceding dot
    */
   private getGlobalFunctions(node: TreeNodeModel): Array<string> {
      const functions = [];

      if(!!node && !node.leaf && node.children.length > 0) {
         for(let child of node.children) {
            if(child.leaf && !!child.data && !child.data.dot) {
               let func: string = this.getFunctionName(child.data.data);

               // ignore functions like Math.floor
               if(func != null && func.indexOf(".") < 0) {
                  functions.push(func);
               }
            }
            else if(!child.leaf) {
               functions.push(...this.getGlobalFunctions(child));
            }
         }
      }

      return functions;
   }

   private getFunctionName(func: string) {
      return func != null && func.indexOf("(") >= 0 ? func.substring(0, func.indexOf("(")) : func;
   }
}
