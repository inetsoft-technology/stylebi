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
import { LayoutOptionDialogModel } from "../../data/vs/layout-option-dialog-model";
import { LayoutOptionDialog } from "./layout-option-dialog.component";

describe("Layout Option Dialog Test", () => {
   const createModel: () => LayoutOptionDialogModel = () => {
      return {
         selectedValue: 2,
         object: "VSGauge",
         target: "VSSelectionList",
         showSelectionContainerOption: false,
         vsEntry: null
      };
   };

   let layoutOptionDialog: LayoutOptionDialog;
   let socket: any;

   beforeEach(() => {
      socket = { sendEvent: jest.fn() };

      layoutOptionDialog = new LayoutOptionDialog(socket);
      layoutOptionDialog.model = createModel();
   });

   // Bug #9817 placing new object in tab should work
   it("should send event with correct uri/arguments when closing", () => {
      layoutOptionDialog.saveChanges();
      expect(socket.sendEvent).toHaveBeenCalled();
      expect(socket.sendEvent.mock.calls[0][0]).toEqual("/events/composer/vs/layout-option-dialog-model/");
      expect(socket.sendEvent.mock.calls[0][1]).toBeTruthy();
   });
});