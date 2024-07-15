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
import { TestUtils } from "../../../common/test/test-utils";
import { VSTextModel } from "../../../vsobjects/model/output/vs-text-model";
import { VSGroupContainerModel } from "../../../vsobjects/model/vs-group-container-model";
import { Viewsheet } from "../../data/vs/viewsheet";
import { ComposerObjectService } from "./composer-object.service";

describe("AssemblyContextMenuItems tests", () => {
   let composerObjectService: ComposerObjectService;

   beforeEach(() => {
      let modelService: any = { getModel: jest.fn() };
      let modalService: any = { open: jest.fn() };
      let trapService: any = { checkTrap: jest.fn() };
      let eventQueueService: any = { addMoveEvent: jest.fn() };
      let uiContextService: any = {
         isVS: jest.fn(),
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn()
      };
      let lineAnchorService: any = {};
      let debounceService: any = {
         debounce: jest.fn((key, fn, delay, args, reducer) => fn(...args))
      };
      let zone: any = {};

      composerObjectService = new ComposerObjectService(modelService, modalService,
         trapService, eventQueueService, uiContextService, lineAnchorService, debounceService, zone);
   });

   // Bug #16276 dont compare to child objects zindex when enabling layer movement
   it("should not have layer movement enabled on container assembly", () => {
      let vs: Viewsheet = new Viewsheet();
      let text1: VSTextModel = Object.assign({
         text: "foo",
         shadow: false,
         autoSize: false,
         url: false,
         hyperlinks: [],
         presenter: false,
         clickable: false
      }, TestUtils.createMockVSObjectModel("VSText", "Text1"));
      let text2: VSTextModel = Object.assign({
         text: "bar",
         shadow: false,
         autoSize: false,
         url: false,
         hyperlinks: [],
         presenter: false,
         clickable: false
      }, TestUtils.createMockVSObjectModel("VSText", "Text2"));
      let group1: VSGroupContainerModel = Object.assign({
         noImageFlag: false,
         animateGif: false,
         imageAlpha: null
      }, TestUtils.createMockVSGroupContainerModel("GroupContainer1"));

      text1.objectFormat.zIndex = 1;
      text2.objectFormat.zIndex = 3;
      text1.container = group1.absoluteName;
      text2.container = group1.absoluteName;
      group1.objectFormat.zIndex = 2;
      vs.vsObjects = [text1, text2, group1];

      composerObjectService.updateLayerMovement(vs, group1);
      expect(group1.objectFormat.bringToFrontEnabled).toBeFalsy();
      expect(group1.objectFormat.sendToBackEnabled).toBeFalsy();
   });
});
