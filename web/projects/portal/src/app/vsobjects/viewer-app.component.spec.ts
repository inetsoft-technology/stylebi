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
import { ModalDismissReasons } from "@ng-bootstrap/ng-bootstrap";
import { ViewerAppComponent } from "./viewer-app.component";

/**
 * deleteBookmarkByCondition() opens the Remove Bookmarks dialog through NgbModal. Cancel and
 * the header x dismiss it with "cancel" and Escape dismisses it with ModalDismissReasons.ESC,
 * both of which reject NgbModalRef.result. The component must handle that rejection, or it
 * reaches Angular's ErrorHandler and is logged as "ERROR cancel" / "ERROR 1" (Bug #78162).
 */
describe("ViewerAppComponent — deleteBookmarkByCondition()", () => {
   let comp: any;
   let chained: Promise<any>;
   let result: Promise<any>;

   function openWith(settle: () => Promise<any>): void {
      result = settle();
      const originalThen = result.then.bind(result);

      // capture the promise the component derives from result, which is the one left
      // unhandled in the component when no rejection callback is passed
      (result as any).then = (onFulfilled?: any, onRejected?: any) => {
         chained = originalThen(onFulfilled, onRejected);
         return chained;
      };

      comp.modalService.open.mockReturnValue({ result });
   }

   beforeEach(() => {
      comp = Object.create(ViewerAppComponent.prototype);
      comp.modalService = { open: vi.fn() };
      comp.removeBookmarksDialog = {};
      comp.deleteBookMarks = vi.fn();
      chained = null;
   });

   it.each([
      ["Cancel / header x", "cancel"],
      ["Escape", ModalDismissReasons.ESC],
   ])("should handle dismissal by %s without a rejection", async (_label, reason) => {
      openWith(() => Promise.reject(reason));

      comp.deleteBookmarkByCondition();

      expect(comp.modalService.open).toHaveBeenCalledWith(comp.removeBookmarksDialog,
         expect.objectContaining({ windowClass: "remove-bookmarks-dialog" }));
      expect(chained).not.toBeNull();
      await expect(chained).resolves.toBeUndefined();
      expect(comp.deleteBookMarks).not.toHaveBeenCalled();
   });

   it("should delete bookmarks when the dialog is committed with a condition", async () => {
      const condition: any = { type: 0 };
      openWith(() => Promise.resolve(condition));

      comp.deleteBookmarkByCondition();
      await chained;

      expect(comp.deleteBookMarks).toHaveBeenCalledWith(condition);
   });

   it("should not delete bookmarks when the dialog closes with a null condition", async () => {
      openWith(() => Promise.resolve(null));

      comp.deleteBookmarkByCondition();
      await chained;

      expect(comp.deleteBookMarks).not.toHaveBeenCalled();
   });
});
