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
import { CommonModule } from "@angular/common";
import { NO_ERRORS_SCHEMA, Optional } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { SsoHeartbeatService } from "../../../../../../shared/sso/sso-heartbeat.service";
import { TestUtils } from "../../../common/test/test-utils";
import { StompClientService, ViewsheetClientService } from "../../../common/viewsheet-client";
import { ActionsContextmenuAnchorDirective } from "../../../widget/fixed-dropdown/actions-contextmenu-anchor.directive";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { InteractService } from "../../../widget/interact/interact.service";
import { InteractableDirective } from "../../../widget/interact/interactable.directive";
import { ComposerToken, ContextProvider, ViewerContextProviderFactory } from "../../context-provider.service";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSSelectionContainerModel } from "../../model/vs-selection-container-model";
import { MiniToolbar } from "../mini-toolbar/mini-toolbar.component";
import { TitleCell } from "../title-cell/title-cell.component";
import { CurrentSelection } from "./current-selection.component";

describe("CurrentSelection Unit Tests", () => {
   let dropdownService: any;
   let interactService: any;
   let ssoHeartbeatService: any;

   beforeEach(async(() => {
      dropdownService = {};
      interactService = {
         addInteractable: jest.fn(),
         notify: jest.fn(),
         removeInteractable: jest.fn()
      };
      ssoHeartbeatService = {
         heartbeat: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            NgbModule,
            FormsModule,
            ReactiveFormsModule
         ],
         declarations: [
            CurrentSelection,
            MiniToolbar,
            TitleCell,
            ActionsContextmenuAnchorDirective,
            InteractableDirective
         ],
         providers: [
            ViewsheetClientService,
            StompClientService,
            { provide: FixedDropdownService, useValue: dropdownService },
            { provide: InteractService, useValue: interactService },
            {
               provide: ContextProvider,
               useFactory: ViewerContextProviderFactory,
               deps: [[new Optional(), ComposerToken]]
            },
            { provide: SsoHeartbeatService, useValue: ssoHeartbeatService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   }));

   // Bug #10087 Truncate variable does not update
   // Bug #19688
   it("should toggle truncate variable", (done) => {
      let fixture: ComponentFixture<CurrentSelection> = TestBed.createComponent(CurrentSelection);
      let formatModel: VSFormatModel = TestUtils.createMockVSFormatModel();
      formatModel.decoration = "underline line-through";
      const model = TestUtils.createMockVSObjectModel("VSSelectionContainer", "CurrentSelection1");
      fixture.componentInstance.model = <VSSelectionContainerModel> model;
      fixture.componentInstance.titleFormat = formatModel;
      fixture.componentInstance.selection = {
         title: "outerTitle",
         value: "outerValue",
         name: "outerName"
      };
      fixture.detectChanges();

      fixture.whenStable().then(() => {
         let currentSelectionText: any = fixture.nativeElement.querySelectorAll(".current-selection-selected-text")[0];
         expect(currentSelectionText.style["text-decoration"]).toEqual("underline line-through");
         done();
      });
   });
});