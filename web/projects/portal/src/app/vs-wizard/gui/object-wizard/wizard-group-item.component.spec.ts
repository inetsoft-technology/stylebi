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
  let examplesService: any;
  let zone: any;

  beforeEach(() => {
    stompClient = { connect: jest.fn(), subscribe: jest.fn() };
    zone = { run: jest.fn() };
    viewsheetClientService = new ViewsheetClientService(stompClient, zone);
    dialogService = {
      open: jest.fn(),
      assemblyDelete: jest.fn(),
      objectDelete: jest.fn()
    };
    treeService = {
      getTableName: jest.fn(() => "Table")
    };

    component = new VSWizardGroupItem(dialogService, viewsheetClientService,
       treeService, examplesService);
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });
});
