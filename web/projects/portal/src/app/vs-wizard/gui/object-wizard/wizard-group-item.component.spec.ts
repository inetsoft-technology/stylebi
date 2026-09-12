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

import { ViewsheetClientService } from "../../../common/viewsheet-client";

import { VSWizardGroupItem } from "./wizard-group-item.component";

describe("VSWizardGroupItem", () => {
  let component: VSWizardGroupItem;
  let stompClient: any;
  let viewsheetClientService: any;
  let dialogService: any;
  let treeService: any;
  let modelService: any;
  let examplesService: any;
  let zone: any;

  beforeEach(() => {
    stompClient = { connect: vi.fn(), subscribe: vi.fn() };
    zone = { run: vi.fn() };
    viewsheetClientService = new ViewsheetClientService(stompClient, zone);
    dialogService = {
      open: vi.fn(),
      assemblyDelete: vi.fn(),
      objectDelete: vi.fn()
    };
    treeService = {
      getTableName: vi.fn(() => "Table")
    };
     modelService = {
        sendModel: vi.fn(),
        getModel: vi.fn()
     };

    component = new VSWizardGroupItem(dialogService, viewsheetClientService,
       treeService, modelService, examplesService);
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });
});
