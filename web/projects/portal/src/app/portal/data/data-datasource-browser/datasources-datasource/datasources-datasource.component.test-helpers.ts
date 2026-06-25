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
 * Shared test helpers for DatasourcesDatasourceComponent multi-pass TL specs.
 * Consumed by:
 *   datasources-datasource.component.interaction.tl.spec.ts
 *   datasources-datasource.component.risk.tl.spec.ts
 *
 * Mocking strategy: ActivatedRoute.paramMap is a controllable Subject so tests can
 * choose whether to trigger ngOnInit HTTP flows. AppInfoService and GettingStartedService
 * are mocked with vi.fn(). NgbModal is mocked to prevent real dialog opening.
 * DatasourcesDatasourceEditorComponent and DataNotificationsComponent are stubbed via
 * importOverrides; the DataNotificationsComponent stub exposes all four notification
 * methods so any danger/success/warning/info call paths in the component do not throw.
 * HTTP calls are tested in the risk spec via MSW; this file sets up no HTTP handlers.
 */

import { provideHttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output, ViewChild } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { of, Subject } from "rxjs";
import { vi } from "vitest";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { DataSourceDefinitionModel } from "../../../../../../../shared/util/model/data-source-definition-model";
import { GettingStartedService } from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { DataNotificationsComponent } from "../../data-notifications.component";
import { StubDataNotificationsComponent } from "../../data-notifications.stub";

export { StubDataNotificationsComponent as DataNotificationsStub };
import { DatasourcesDatasourceEditorComponent } from "./datasources-datasource-editor/datasources-datasource-editor.component";
import { DatasourcesDatasourceComponent } from "./datasources-datasource.component";

// ---------------------------------------------------------------------------
// Child-component stubs
// ---------------------------------------------------------------------------

@Component({ selector: "datasources-datasource-editor", template: "", standalone: true })
class DatasourcesDatasourceEditorStub {
   @Input() datasource: DataSourceDefinitionModel;
   @Input() usedNames: string[];
   @Output() datasourceChanged = new EventEmitter<DataSourceDefinitionModel>();
   @Output() datasourceValid = new EventEmitter<boolean>();
   @Output() onWarning = new EventEmitter<string>();
   @Output() onSuccess = new EventEmitter<string>();
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
      additionalConnections: null,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Shared mocks
// ---------------------------------------------------------------------------

export const ROUTER_MOCK = {
   navigate: vi.fn(),
};

// let so resetMocks() can recreate it; ROUTE_MOCK uses a getter so the
// component always subscribes to the current Subject's observable.
export let paramMap$ = new Subject<any>();

export const ROUTE_MOCK = {
   get paramMap() { return paramMap$.asObservable(); },
   snapshot: { paramMap: { get: vi.fn().mockReturnValue(null) } },
};

export const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

export const APP_INFO_MOCK = {
   isEnterprise: vi.fn().mockReturnValue(of(false)),
};

export const GETTING_STARTED_MOCK = {
   isConnectTo: vi.fn().mockReturnValue(false),
   setDataSourcePath: vi.fn(),
   continue: vi.fn(),
};

// Stable reference for afterEach cleanup: spec files call lastRenderedFixture?.destroy()
// to ensure the fixture is torn down even when a test throws before returning.
export let lastRenderedFixture: any = null;

export function resetMocks(): void {
   paramMap$ = new Subject<any>();
   Object.values(ROUTER_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   Object.values(MODAL_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   MODAL_MOCK.open.mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   Object.values(APP_INFO_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   APP_INFO_MOCK.isEnterprise.mockReturnValue(of(false));
   Object.values(GETTING_STARTED_MOCK).forEach(m => typeof m.mockClear === "function" && m.mockClear());
   GETTING_STARTED_MOCK.isConnectTo.mockReturnValue(false);
   lastRenderedFixture = null;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

export interface RenderOpts {
   enterprise?: boolean;
}

export async function renderDatasource(opts: RenderOpts = {}) {
   if(opts.enterprise !== undefined) {
      APP_INFO_MOCK.isEnterprise.mockReturnValue(of(opts.enterprise));
   }

   const { fixture } = await render(DatasourcesDatasourceComponent, {
      providers: [
         provideHttpClient(),
         { provide: Router, useValue: ROUTER_MOCK },
         { provide: ActivatedRoute, useValue: ROUTE_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: AppInfoService, useValue: APP_INFO_MOCK },
         { provide: GettingStartedService, useValue: GETTING_STARTED_MOCK },
      ],
      importOverrides: [
         { replace: DatasourcesDatasourceEditorComponent, with: DatasourcesDatasourceEditorStub },
         { replace: DataNotificationsComponent, with: StubDataNotificationsComponent },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   lastRenderedFixture = fixture;
   const comp = fixture.componentInstance as DatasourcesDatasourceComponent;
   return { comp, fixture };
}
