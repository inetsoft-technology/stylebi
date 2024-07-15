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
import { HttpClient } from "@angular/common/http";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { SsoHeartbeatService } from "../../../../../../shared/sso/sso-heartbeat.service";
import { StompClientService } from "../../../../../../shared/stomp/stomp-client.service";
import { TestUtils } from "../../../common/test/test-utils";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { InteractService } from "../../../widget/interact/interact.service";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { ContextProvider } from "../../context-provider.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { MiniToolbar } from "../mini-toolbar/mini-toolbar.component";
import { SelectionContainerChildrenService } from "./services/selection-container-children.service";
import { VSSelectionContainer } from "./vs-selection-container.component";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";

describe("VSSelectionContainer Unit Tests", () => {
   let interactService: any;
   let fixture: ComponentFixture<VSSelectionContainer>;
   let dataTipService: any;
   let container: VSSelectionContainer;
   let dropdownService: any;
   let ssoHeartbeatService: any;

   beforeEach(async(() => {
      interactService = {
         addInteractable: jest.fn(),
         notify: jest.fn(),
         removeInteractable: jest.fn()
      };
      dataTipService = { isDataTip: jest.fn() };
      dropdownService = { };
      const contextProvider = {};
      ssoHeartbeatService = { heartbeat: jest.fn() };

      TestBed.configureTestingModule({
         imports: [
            NgbModule, FormsModule, ReactiveFormsModule, HttpClientTestingModule
         ],
         declarations: [
            VSSelectionContainer, MiniToolbar, InteractableDirective
         ],
         schemas: [ NO_ERRORS_SCHEMA ],
         providers: [
            ViewsheetClientService,
            SelectionContainerChildrenService,
            StompClientService,
            HttpClient,
            { provide: ContextProvider, useValue: contextProvider },
            { provide: InteractService, useValue: interactService },
            { provide: DataTipService, useValue: dataTipService },
            { provide: FixedDropdownService, useValue: dropdownService },
            { provide: SsoHeartbeatService, useValue: ssoHeartbeatService }
         ]
      });
      TestBed.compileComponents();
   }));

   // Bug #21218 should not display mini toolbar when selection container is invisible
   it("should not display mini toolbar when selection container is invisible", () => {
      fixture = TestBed.createComponent(VSSelectionContainer);
      container = <VSSelectionContainer>fixture.componentInstance;
      container.model = TestUtils.createMockVSSelectionContainerModel("container1");
      container.model.visible = false;
      fixture.detectChanges();

      let miniToolbar = fixture.nativeElement.querySelectorAll(
         "mini-toolbar div.mini-toolbar.btn-toolbar div.btn-group");
      expect(miniToolbar.length).toBe(0);
   });
});
