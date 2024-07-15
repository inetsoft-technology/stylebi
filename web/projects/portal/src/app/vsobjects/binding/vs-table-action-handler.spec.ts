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
import { AssemblyActionEvent } from "../../common/action/assembly-action-event";
import { TestUtils } from "../../common/test/test-utils";
import { VSCrosstabModel } from "../model/vs-crosstab-model";
import { VSTableModel } from "../model/vs-table-model";
import { VSTableActionHandler } from "./vs-table-action-handler";

describe("VS Table Action Handler Unit Test", () => {
   let vsCrosstabModel: VSCrosstabModel;
   let vsTableModel: VSTableModel;
   let modelService: any;
   let modalService: any;
   let injector: any;
   let viewsheetClientService: any;

   beforeEach(() => {
      vsCrosstabModel = TestUtils.createMockVSCrosstabModel("Crosstab1");
      modelService = { getModel: jest.fn() };
      modalService = { open: jest.fn() };
      injector = { get: jest.fn() };
      viewsheetClientService = { sendEvent: jest.fn() };
   });

   //for Bug #17196, delete column(s) can not work
   it("should call removeTableColumns when delete column event is received", () => {
      const handler = new VSTableActionHandler(modelService, viewsheetClientService, modalService, injector, null);
      handler["removeTableColumns"] = jest.fn();
      handler.handleEvent(new AssemblyActionEvent<VSTableModel>("table delete-columns", vsTableModel), []);
      expect(handler["removeTableColumns"]).toHaveBeenCalled();
   });

   //for Bug #17195, properties dialog can not open
   it("should open properties dialog wehn properties event is received", () => {
      const handler = new VSTableActionHandler(modelService, viewsheetClientService, modalService, injector, null);
      handler["showTablePropertiesDialog"] = jest.fn();
      handler.handleEvent(new AssemblyActionEvent<VSTableModel>("table properties", vsTableModel), []);
      expect(handler["showTablePropertiesDialog"]).toHaveBeenCalled();
   });

});
