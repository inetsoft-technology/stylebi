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
 * Shared test helpers for DatasourcesDatasourceEditorComponent multi-pass TL specs.
 * Consumed by:
 *   datasources-datasource-editor.component.interaction.tl.spec.ts
 *   datasources-datasource-editor.component.risk.tl.spec.ts
 *
 * Mocking strategy: DebounceService and OAuthAuthorizationService are provided as vi.fn()
 * mocks to avoid real debounce delays and OAuth browser flows. NgbModal is mocked to prevent
 * real dialog opening. TabularViewComponent is stubbed via importOverrides because it carries
 * its own deep DI tree (file browsers, CKEditor) unrelated to the logic under test.
 * HTTP calls are tested in the risk spec via MSW; this file provides vi.fn() stubs for
 * non-HTTP services only.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { of, Subject } from "rxjs";
import { vi } from "vitest";
import { DataSourceDefinitionModel } from "../../../../../../../../shared/util/model/data-source-definition-model";
import { TabularButton } from "../../../../../common/data/tabular/tabular-button";
import { TabularView } from "../../../../../common/data/tabular/tabular-view";
import { OAuthAuthorizationService } from "../../../../../common/services/oauth-authorization.service";
import { DebounceService } from "../../../../../widget/services/debounce.service";
import { TabularViewComponent } from "../../../../../widget/tabular/tabular-view.component";
import { DatasourcesDatasourceEditorComponent } from "./datasources-datasource-editor.component";

// ---------------------------------------------------------------------------
// Child-component stubs
// ---------------------------------------------------------------------------

@Component({
   selector: "tabular-view",
   template: "",
   standalone: true,
})
class TabularViewStub {
   @Input() rootView: TabularView;
   @Input() browseFunction: any;
   @Input() panel: boolean;
   @Input() cancelButtonExists: boolean;
   @Output() viewChange = new EventEmitter<TabularView[]>();
   @Output() validChange = new EventEmitter<boolean>();
   @Output() buttonClick = new EventEmitter<TabularButton>();
}

// ---------------------------------------------------------------------------
// Factories
// ---------------------------------------------------------------------------

export function makeDataSource(overrides: Partial<DataSourceDefinitionModel> = {}): DataSourceDefinitionModel {
   return {
      name: "TestDS",
      oldName: null,
      description: "",
      parentPath: "",
      type: "JDBC",
      deletable: true,
      tabularView: null,
      sequenceNumber: 0,
      ...overrides,
   };
}

export function makeTabularButton(overrides: Partial<TabularButton> = {}): TabularButton {
   return {
      type: "REFRESH",
      style: "",
      url: "",
      method: "",
      enabledMethod: "",
      enabled: true,
      dependsOn: [],
      oauthServiceName: "",
      oauthUser: "",
      oauthPassword: "",
      oauthClientId: "",
      oauthClientSecret: "",
      oauthScope: "",
      oauthAuthorizationUri: "",
      oauthTokenUri: "",
      oauthFlags: "",
      oauthAdditionalParameters: {},
      clicked: false,
      loading: false,
      ...overrides,
   };
}

export function makeTabularView(overrides: Partial<TabularView> = {}): TabularView {
   return {
      type: "EDITOR",
      text: "",
      color: "",
      font: "",
      value: "",
      row: 0,
      col: 0,
      rowspan: 1,
      colspan: 1,
      align: "",
      verticalAlign: "",
      paddingLeft: 0,
      paddingRight: 0,
      paddingTop: 0,
      paddingBottom: 0,
      password: false,
      displayLabel: "",
      editor: null,
      button: null,
      views: [],
      required: false,
      visible: true,
      min: 0,
      max: 0,
      pattern: [],
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

// Immediately invokes the callback so tests don't need to wait for debounce delays.
export const DEBOUNCE_MOCK = {
   debounce: vi.fn().mockImplementation((_key: string, fn: () => void) => fn()),
};

export const OAUTH_MOCK = {
   authorize: vi.fn().mockReturnValue(of({ accessToken: "test-token" })),
};

export function resetMocks(): void {
   Object.values(MODAL_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   MODAL_MOCK.open.mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   Object.values(DEBOUNCE_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   DEBOUNCE_MOCK.debounce.mockImplementation((_key: string, fn: () => void) => fn());
   Object.values(OAUTH_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   OAUTH_MOCK.authorize.mockReturnValue(of({ accessToken: "test-token" }));
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export interface RenderOpts {
   datasource?: DataSourceDefinitionModel;
   usedNames?: string[];
}

export async function renderEditor(opts: RenderOpts = {}) {
   const datasource = opts.datasource ?? makeDataSource();

   const { fixture } = await render(DatasourcesDatasourceEditorComponent, {
      componentProperties: {
         datasource,
         usedNames: opts.usedNames ?? [],
      },
      providers: [
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: DebounceService, useValue: DEBOUNCE_MOCK },
         { provide: OAuthAuthorizationService, useValue: OAUTH_MOCK },
      ],
      importOverrides: [
         { replace: TabularViewComponent, with: TabularViewStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   const comp = fixture.componentInstance as DatasourcesDatasourceEditorComponent;
   return { comp, fixture };
}
