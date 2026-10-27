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
import { HttpErrorResponse } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "./component-tool";

describe("ComponentTool.showHttpError", () => {
   const modal = {} as NgbModal;
   let dialogSpy: ReturnType<typeof vi.spyOn>;

   beforeEach(() => {
      dialogSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");
      vi.spyOn(console, "error").mockImplementation(() => {});
   });

   afterEach(() => vi.restoreAllMocks());

   it("shows the status when the response has no error body", () => {
      const error = new HttpErrorResponse({ status: 500, error: null });

      expect(() => ComponentTool.showHttpError("Failed to save", error, modal)).not.toThrow();
      expect(dialogSpy).toHaveBeenCalledWith(modal, "_#(js:Error)", "Failed to save [500]");
   });

   it("shows the message of a JSON string error body", () => {
      const error = new HttpErrorResponse({ status: 500, error: JSON.stringify({ message: "boom" }) });

      ComponentTool.showHttpError("Failed to save", error, modal);

      expect(dialogSpy).toHaveBeenCalledWith(modal, "_#(js:Error)", "Failed to save: boom");
   });

   it("shows a non-JSON string error body as is", () => {
      const error = new HttpErrorResponse({ status: 500, error: "plain text" });

      ComponentTool.showHttpError("Failed to save", error, modal);

      expect(dialogSpy).toHaveBeenCalledWith(modal, "_#(js:Error)", "plain text");
   });

   it("shows the gateway message for a 502", () => {
      const error = new HttpErrorResponse({ status: 502, error: null });

      ComponentTool.showHttpError("Failed to save", error, modal);

      expect(dialogSpy).toHaveBeenCalledWith(modal, "_#(js:Error)", "_#(js:login.error.gateway)");
   });
});
