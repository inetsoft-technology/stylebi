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
import { VSAssemblyScriptPaneModel } from "./vsassembly-script-pane-model";
import { VSAssemblyScriptPane } from "./vsassembly-script-pane.component";

let event: any = {
   target: "columnTree",
   node: {
      children: [],
      collapsedIcon: null,
      cssClass: null,
      data: {
         data: "Agent",
         expression: null,
         name: "field",
         parentData: null,
         parentLabel: "Data",
         parentName: "fields",
         suffix: null,
         useragg: null
      },
      dataLabel: null,
      dragData: null,
      dragName: null,
      expanded: false,
      expandedIcon: null,
      icon: null,
      label: "Agent",
      leaf: true,
      toggleCollapsedIcon: null,
      toggleExpandedIcon: null,
      type: null
   },
   expression: "",
   selection: {
      from: {
         ch: 0,
         line: 0,
         sticky: null
      },
      to: {
         ch: 0,
         line: 0,
         sticky: null
      }
   }
};
describe("VSAssembly Script Pane Test", () => {
   const createModel: () => VSAssemblyScriptPaneModel = () => {
      return {
         expression: "",
         scriptEnabled: true
      };
   };

   let vSAssemblyScriptPane: VSAssemblyScriptPane;

   beforeEach(() => {
      vSAssemblyScriptPane = new VSAssemblyScriptPane();
      vSAssemblyScriptPane.model = createModel();
   });

   // Bug 10560 make sure VSAssemblyScriptPane can update expression.
   it("should update script pane expression when clicked", () => {
      vSAssemblyScriptPane.onExpressionChange(event);
      expect(vSAssemblyScriptPane.model.expression).toEqual("data['Agent']");
   });

});