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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { DataQueryModelService } from "../data-query-model.service";
import { SqlQueryPreviewPaneComponent } from "./sql-query-preview-pane.component";

const VARIABLES_URL = "../api/data/datasource/query/variables";
const LOAD_DATA_URL = "../api/data/datasource/query/load/data";

/**
 * Bug #77870: a failed preview shows the server's message whatever the shape of the error body,
 * the database message string of a SQL error, the {error, message} object of an expired query
 * session, or no usable body at all.
 */
describe("SqlQueryPreviewPaneComponent error message", () => {
   let fixture: ComponentFixture<SqlQueryPreviewPaneComponent>;
   let component: SqlQueryPreviewPaneComponent;
   let httpMock: HttpTestingController;
   let showMessageDialog: any;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, SqlQueryPreviewPaneComponent],
         providers: [
            DataQueryModelService,
            { provide: NgbModal, useValue: { open: vi.fn() } }
         ]
      });
      TestBed.overrideComponent(SqlQueryPreviewPaneComponent,
         { set: { template: "", imports: [] } });

      showMessageDialog = vi.spyOn(ComponentTool, "showMessageDialog")
         .mockReturnValue(Promise.resolve("ok"));
      httpMock = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(SqlQueryPreviewPaneComponent);
      component = fixture.componentInstance;
      component.runtimeId = "rq-77870";
      component.sqlString = "select 1/0 from EMP";
      component.sqlEdited = true;
   });

   afterEach(() => {
      httpMock.verify();
      vi.restoreAllMocks();
   });

   // runs the real flow: variables (none) through DataQueryModelService, then load/data
   function preview(): ReturnType<HttpTestingController["expectOne"]> {
      fixture.detectChanges();
      httpMock.expectOne(req => req.url === VARIABLES_URL).flush(null);
      const req = httpMock.expectOne(req => req.url === LOAD_DATA_URL);
      expect(req.request.params.get("runtimeId")).toBe("rq-77870");
      expect(req.request.params.get("sqlString")).toBe("select 1/0 from EMP");
      return req;
   }

   function shownMessage(): string {
      expect(showMessageDialog).toHaveBeenCalledTimes(1);
      expect(component.previewPending).toBe(false);
      return showMessageDialog.mock.calls[0][2];
   }

   it("shows the database message of a string body", () => {
      preview().flush("java.sql.SQLDataException: Attempt to divide by zero.",
         { status: 400, statusText: "Bad Request" });

      expect(shownMessage()).toBe("java.sql.SQLDataException: Attempt to divide by zero.");
   });

   it("prefixes the syntax error caption for a syntax error", () => {
      preview().flush("java.sql.SQLSyntaxErrorException: Table 'X' does not exist.",
         { status: 400, statusText: "Bad Request" });

      expect(shownMessage()).toBe("_#(js:common.sqlquery.syntaxError)\n" +
         "java.sql.SQLSyntaxErrorException: Table 'X' does not exist.");
   });

   it("shows the message of an {error, message} body", () => {
      const message = "The query session has expired. Please close and reopen the query editor.";
      preview().flush({ error: "messageException", message },
         { status: 500, statusText: "Internal Server Error" });

      expect(shownMessage()).toBe(message);
   });

   it("shows a generic message for a null body", () => {
      preview().flush(null, { status: 500, statusText: "Internal Server Error" });

      expect(shownMessage()).toBe("_#(js:internal.error)");
   });

   it("shows a generic message for an empty string body", () => {
      preview().flush("", { status: 500, statusText: "Internal Server Error" });

      expect(shownMessage()).toBe("_#(js:internal.error)");
   });

   it("shows a generic message when the server can't be reached", () => {
      preview().error(new ProgressEvent("error"), { status: 0, statusText: "Unknown Error" });

      expect(shownMessage()).toBe("_#(js:internal.error)");
   });

   it("shows the rows of a successful preview without a message", () => {
      preview().flush([["ID"], ["1"]]);

      expect(showMessageDialog).not.toHaveBeenCalled();
      expect(component.tableData).toEqual([["ID"], ["1"]]);
      expect(component.previewPending).toBe(false);
   });
});
