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

import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { TestUtils } from "../../../../common/test/test-utils";
import { VSChartModel } from "../../../model/vs-chart-model";
import { VSChartActionHandler } from "./vs-chart-action-handler";

describe("VSChartActionHandler", () => {
   let model: VSChartModel;
   let modelService: any;
   let modalService: any;
   let viewsheetClient: any;
   let injector: any;
   let dataTipService: any;

   beforeEach(() => {
      model = TestUtils.createMockVSChartModel("Chart1");
      modelService = { getModel: vi.fn() };
      modalService = { open: vi.fn() };
      viewsheetClient = { sendEvent: vi.fn() };
      injector = { get: vi.fn() };
      dataTipService = {
         showDataTip: vi.fn(),
         isDataTip: vi.fn(),
         isFrozen: vi.fn(),
         hideDataTip: vi.fn()
      };
   });

   // Bug #17181
   it("should open group dialog when group event is received", () => {
      const handler = new VSChartActionHandler(modelService, viewsheetClient, modalService, injector, false, dataTipService, null);
      handler["showGroupDialog"] = vi.fn();
      handler.handleEvent(new AssemblyActionEvent<VSChartModel>("chart group", model), []);
      expect(handler["showGroupDialog"]).toHaveBeenCalled();
   });

   // Bug #17181
   it("should open rename dialog when rename event is received", () => {
      const handler = new VSChartActionHandler(modelService, viewsheetClient, modalService, injector, false, dataTipService, null);
      handler["showRenameDialog"] = vi.fn();
      handler.handleEvent(new AssemblyActionEvent<VSChartModel>("chart rename", model), []);
      expect(handler["showRenameDialog"]).toHaveBeenCalled();
   });

   // Bug #17181
   it("should call ungroup when ungroup event is received", () => {
      const handler = new VSChartActionHandler(modelService, viewsheetClient, modalService, injector, false, dataTipService, null);
      handler["ungroup"] = vi.fn();
      handler.handleEvent(new AssemblyActionEvent<VSChartModel>("chart ungroup", model), []);
      expect(handler["ungroup"]).toHaveBeenCalled();
   });
});
