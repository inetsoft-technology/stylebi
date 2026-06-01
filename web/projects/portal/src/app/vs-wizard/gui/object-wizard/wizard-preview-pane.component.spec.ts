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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { Subject } from "rxjs";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../../vsobjects/context-provider.service";
import { DataTipService } from "../../../vsobjects/objects/data-tip/data-tip.service";
import { PopComponentService } from "../../../vsobjects/objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../../../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import { DefaultScaleService } from "../../../widget/services/scale/default-scale-service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { VSWizardPreviewPane } from "./wizard-preview-pane.component";

describe("VSWizardPreviewPane", () => {
  let component: VSWizardPreviewPane;
  let fixture: ComponentFixture<VSWizardPreviewPane>;

  beforeEach(waitForAsync(() => {
    const viewsheetClientService = { sendEvent: jest.fn() };

    TestBed.configureTestingModule({
      imports: [
         VSWizardPreviewPane,
         HttpClientTestingModule
      ],
      providers: [
        { provide: ViewsheetClientService, useValue: viewsheetClientService },
        { provide: ContextProvider, useValue: { viewer: false, preview: false, composer: true, binding: false } },
        { provide: DataTipService, useValue: { isDataTip: jest.fn(), showDataTip: jest.fn() } },
        { provide: PopComponentService, useValue: { isPopComponent: jest.fn(), getPopComponent: jest.fn(), componentRegistered: new Subject<any>() } },
        { provide: MiniToolbarService, useValue: { handleBodyMouseoverEvent: jest.fn(), setCurrentToolbar: jest.fn(), isMiniToolbarVisible: jest.fn() } },
        { provide: ScaleService, useClass: DefaultScaleService }
      ],
      schemas: [
         NO_ERRORS_SCHEMA
      ]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(VSWizardPreviewPane);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });
});
