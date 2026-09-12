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
 * Shared test helpers for VSComboBox multi-pass TL specs.
 *
 * VSComboBox has a 9-parameter constructor and issues no HTTP calls of its own (the one HTTP
 * call, FirstDayOfWeekService.getFirstDay(), is a swappable service mocked as an Observable).
 * Direct instantiation (not ATL render()) is used so every dependency can be a minimal vi.fn()
 * mock without resolving the large template's child directives (NgbDatepicker, CdkVirtualScroll,
 * NgbTypeahead, ...).
 */

import { of } from "rxjs";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetInfo } from "../../data/viewsheet-info";
import { VSComboBox } from "./vs-combo-box.component";
import { VSComboBoxModel } from "../../model/vs-combo-box-model";

export function makeMockComboBoxModel(overrides: Partial<VSComboBoxModel> = {}): VSComboBoxModel {
   const base = TestUtils.createMockVSComboBoxModel("Combo1");

   return Object.assign(base, {
      selectedLabel: "",
      selectedObject: null,
      rowCount: 5,
      editable: false,
      refresh: false,
      writeBackDirectly: false,
      dataType: "",
      calendar: false,
      labels: ["Label A", "Label B", "Label C"],
      values: ["A", "B", "C"],
   } as any, overrides) as VSComboBoxModel;
}

/** A modal ref shaped so ComponentTool.showMessageDialog() can subscribe/resolve it. */
export function makeModalMock() {
   return {
      open: vi.fn().mockImplementation(() => ({
         result: new Promise<any>(() => {}),
         componentInstance: { onCommit: { subscribe: vi.fn() } },
         close: vi.fn(),
         dismiss: vi.fn(),
      })),
   };
}

export interface ComboBoxTestOverrides {
   socket?: any;
   formDataService?: any;
   formInputService?: any;
   debounceService?: any;
   contextProvider?: any;
   dataTipService?: any;
   ngbModal?: any;
   firstDayOfWeekService?: any;
   model?: Partial<VSComboBoxModel>;
}

export interface ComboBoxTestContext {
   comp: VSComboBox;
   socket: any;
   formDataService: any;
   formInputService: any;
   debounceService: any;
   contextProvider: any;
   dataTipService: any;
   ngbModal: any;
   firstDayOfWeekService: any;
}

export function createComboBoxComponent(overrides: ComboBoxTestOverrides = {}): ComboBoxTestContext {
   const socket = overrides.socket ?? {
      sendEvent: vi.fn(),
      runtimeId: "Viewsheet1",
   };

   const formDataService = overrides.formDataService ?? {
      checkFormData: vi.fn((runtimeId: string, name: string, selection: string,
                             confirmedCallback: Function) => confirmedCallback()),
   };

   const formInputService = overrides.formInputService ?? {
      addPendingValue: vi.fn(),
   };

   const debounceService = overrides.debounceService ?? {
      debounce: vi.fn(),
   };

   const contextProvider = overrides.contextProvider ?? {
      viewer: true,
      preview: false,
      composer: false,
      binding: false,
   };

   const dataTipService = overrides.dataTipService ?? {
      isDataTip: vi.fn().mockReturnValue(false),
   };

   const ngbModal = overrides.ngbModal ?? makeModalMock();

   const firstDayOfWeekService = overrides.firstDayOfWeekService ?? {
      getFirstDay: vi.fn().mockReturnValue(of({ isoFirstDay: 1 })),
   };

   const comp = new VSComboBox(
      socket as any,
      formDataService as any,
      formInputService as any,
      debounceService as any,
      contextProvider as any,
      dataTipService as any,
      { run: (fn: any) => fn(), runOutsideAngular: (fn: any) => fn() } as any, // zone
      ngbModal as any,
      firstDayOfWeekService as any,
   );

   comp.vsInfo = new ViewsheetInfo([], "/link/", false, "Viewsheet1");
   comp.model = makeMockComboBoxModel(overrides.model ?? {});

   return {
      comp, socket, formDataService, formInputService, debounceService,
      contextProvider, dataTipService, ngbModal, firstDayOfWeekService,
   };
}
