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
 * The worksheet Database Query dialog pairs (bug #80459, kind `worksheetSqlQuery`) in exactly the
 * two direct-SQL-edit editors: the simple-mode SQL box after Edit, and the advanced "SQL String"
 * tab. `agentPairing` travels dialog -> simple-query-pane / database-query -> free-form-sql-pane
 * by plain @Input bindings; the REAL dialog and panes are rendered here so one dropped hop turns
 * the matching spec red. No button for a null pairing (portal VPM / Data Modeler) or when the
 * user lacks the Free Form SQL permission.
 */
import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { of, Subject } from "rxjs";
import { BasicSqlQueryModel } from "../../../composer/data/ws/basic-sql-query-model";
import { SqlQueryDialogModel } from "../../../composer/data/ws/sql-query-dialog-model";
import { SqlAgentPairing } from "../../../composer/gui/wiz/editor-context";
import { ViewsheetCommandMessage } from "../../../common/viewsheet-client";
import { ModelService } from "../../services/model.service";
import { ParseResult } from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-sql/parse-result";
import { SQLQueryDialog } from "./sql-query-dialog.component";

const CONNECT = "Connect to Claude";

function makeSocket() {
   const commands = new Subject<ViewsheetCommandMessage>();
   const conn = { send: vi.fn(), subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) };
   return { commands, conn, whenConnected: () => of(conn) } as any;
}

function pairing(socket: any, overrides: Partial<SqlAgentPairing> = {}): SqlAgentPairing {
   return {
      runtimeId: "Worksheet1", socketConnection: socket, table: "SQL Query1", pending: true,
      ...overrides
   };
}

function simpleModel(overrides: Partial<BasicSqlQueryModel> = {}): BasicSqlQueryModel {
   return {
      tables: {}, columns: [], maxColumnCount: 100, joins: [], conditionList: [],
      sqlString: "select 1", sqlParseResult: "", sqlEdited: true, freeFormSqlEnabled: true,
      ...overrides
   } as BasicSqlQueryModel;
}

function dialogModel(overrides: Partial<SqlQueryDialogModel> = {}): SqlQueryDialogModel {
   return {
      mashUpData: false, name: "SQL Query1", runtimeId: "Worksheet1", dataSources: ["ds1"],
      dataSource: "ds1", variableNames: [], physicalTablesEnabled: true,
      freeFormSqlEnabled: true, advancedEdit: false, simpleModel: simpleModel(),
      supportsFullOuterJoin: [true],
      advancedModel: {
         name: "SQL Query1", sqlEdited: false,
         linkPaneModel: { tables: [] } as any, fieldPaneModel: {} as any,
         conditionPaneModel: {} as any, sortPaneModel: {} as any, groupingPaneModel: {} as any,
         // A failed parse with text opens the database-query on its SQL String tab.
         freeFormSQLPaneModel: {
            sqlString: "select 2", generatedSqlString: "", parseResult: ParseResult.PARSE_FAILED,
            parseSql: true, hasSqlString: true
         } as any
      },
      ...overrides
   } as SqlQueryDialogModel;
}

async function renderDialog(model: SqlQueryDialogModel, agentPairing: SqlAgentPairing | null) {
   const controller: any = {
      dataSource: "ds1", subQuery: false, sqlConditionProvider: {},
      getModel: vi.fn(() => of(model)),
      getDataSourceTree: vi.fn(() => of({ children: [] })),
      getTableColumns: vi.fn(() => of([])),
      getSQLString: vi.fn(() => of("")),
      getSqlParseResult: vi.fn(() => of("")),
      setModel: vi.fn()
   };
   const result = await render(SQLQueryDialog, {
      providers: [
         provideHttpClient(), provideHttpClientTesting(),
         { provide: ModelService, useValue: { getModel: vi.fn(() => of([])), sendModel: vi.fn() } }
      ],
      componentInputs: { controller, initTableName: "SQL Query1", tables: [], agentPairing }
   });
   result.fixture.detectChanges();
   await result.fixture.whenStable();
   result.fixture.detectChanges();
   return result;
}

function connectButtons(): HTMLElement[] {
   return screen.queryAllByText(CONNECT);
}

describe("SQL query dialog - Connect to Claude pairing (bug #80459)", () => {
   describe("simple mode, direct SQL edit", () => {
      it("shows one button once the SQL box is editable", async () => {
         await renderDialog(dialogModel(), pairing(makeSocket()));

         expect(connectButtons()).toHaveLength(1);
      });

      it("shows no button before Edit (the SQL box is read-only and generated)", async () => {
         await renderDialog(
            dialogModel({ simpleModel: simpleModel({ sqlEdited: false }) }), pairing(makeSocket()));

         expect(connectButtons()).toHaveLength(0);
      });

      it("shows no button without the Free Form SQL permission", async () => {
         const model = dialogModel({
            freeFormSqlEnabled: false,
            simpleModel: simpleModel({ freeFormSqlEnabled: false })
         });
         await renderDialog(model, pairing(makeSocket()));

         expect(connectButtons()).toHaveLength(0);
      });

      it("shows no button for a null pairing (portal VPM sub-query, other users)", async () => {
         await renderDialog(dialogModel(), null);

         expect(connectButtons()).toHaveLength(0);
      });

      it("mints for the NEW query table: pending true", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(dialogModel(), pairing(socket));
         const pane = fixture.debugElement.query(
            e => e.name === "wiz-connect-to-claude").componentInstance;

         expect(pane.editorContext).toEqual(
            { kind: "worksheetSqlQuery", assembly: "SQL Query1", pending: true });
         expect(pane.runtimeId).toBe("Worksheet1");
         expect(pane.sheetType).toBe("WORKSHEET");
         expect(pane.socketConnection).toBe(socket);
      });

      it("mints for an EXISTING query table: pending false, the table's name", async () => {
         const { fixture } = await renderDialog(
            dialogModel(), pairing(makeSocket(), { table: "Orders SQL", pending: false }));
         const pane = fixture.debugElement.query(
            e => e.name === "wiz-connect-to-claude").componentInstance;

         expect(pane.editorContext).toEqual(
            { kind: "worksheetSqlQuery", assembly: "Orders SQL", pending: false });
      });

      it("applies a push for its context to the SQL box and the model", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(dialogModel(), pairing(socket));
         const pane = fixture.debugElement.query(e => e.name === "simple-query-pane");

         socket.commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
            kind: "worksheetSqlQuery", assembly: "SQL Query1", text: "select 42"
         }));
         fixture.detectChanges();
         await fixture.whenStable();

         expect(pane.componentInstance.model.sqlString).toBe("select 42");
         // the textarea's own change handling resets the parse status
         expect(pane.componentInstance.model.sqlParseResult).toBe("_#(js:designer.qb.parseInit)");
         expect(screen.queryByText("_#(Reload)")).toBeNull();
      });

      it("ignores a push for another table", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(dialogModel(), pairing(socket));
         const pane = fixture.debugElement.query(e => e.name === "simple-query-pane");

         socket.commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
            kind: "worksheetSqlQuery", assembly: "Other", text: "select 42"
         }));

         expect(pane.componentInstance.model.sqlString).toBe("select 1");
      });

      it("parks a push behind the banner once the user typed, and Reload applies it", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(dialogModel(), pairing(socket));
         const pane = fixture.debugElement.query(e => e.name === "simple-query-pane");
         // let the agent-push helper exist (created when the button renders), then the user types
         pane.componentInstance.model.sqlString = "select typed";

         socket.commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
            kind: "worksheetSqlQuery", assembly: "SQL Query1", text: "select agent"
         }));
         fixture.detectChanges();

         expect(pane.componentInstance.model.sqlString).toBe("select typed");
         expect(screen.getByText("_#(Reload)")).toBeTruthy();

         await userEvent.click(screen.getByText("_#(Reload)"));
         fixture.detectChanges();

         expect(pane.componentInstance.model.sqlString).toBe("select agent");
         expect(screen.queryByText("_#(Reload)")).toBeNull();
      });

      it("detaches the pairing session when the pane is destroyed", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(dialogModel(), pairing(socket));
         socket.conn.send.mockClear();

         fixture.destroy();

         expect(socket.conn.send).toHaveBeenCalledWith("/events/wiz/pairing/detach", {},
            JSON.stringify({ editorContext:
               { kind: "worksheetSqlQuery", assembly: "SQL Query1", pending: true } }));
      });

      it("stops listening for pushes once destroyed", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(dialogModel(), pairing(socket));
         const model = fixture.debugElement.query(
            e => e.name === "simple-query-pane").componentInstance.model;

         fixture.destroy();
         socket.commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
            kind: "worksheetSqlQuery", assembly: "SQL Query1", text: "select late"
         }));

         expect(model.sqlString).toBe("select 1");
      });
   });

   describe("advanced mode, SQL String tab", () => {
      const advanced = (overrides: Partial<SqlQueryDialogModel> = {}) =>
         dialogModel({ advancedEdit: true, ...overrides });

      it("shows one button on the SQL String tab", async () => {
         await renderDialog(advanced(), pairing(makeSocket()));

         expect(connectButtons()).toHaveLength(1);
      });

      it("shows no SQL String tab and no button without the permission", async () => {
         await renderDialog(advanced({ freeFormSqlEnabled: false }), pairing(makeSocket()));

         expect(screen.queryByText("_#(SQL String)")).toBeNull();
         expect(connectButtons()).toHaveLength(0);
      });

      it("shows no button for a null pairing", async () => {
         await renderDialog(advanced(), null);

         expect(screen.queryByText("_#(SQL String)")).toBeTruthy();
         expect(connectButtons()).toHaveLength(0);
      });

      it("mints with the same context as the simple pane", async () => {
         const { fixture } = await renderDialog(advanced(), pairing(makeSocket()));
         const pane = fixture.debugElement.query(
            e => e.name === "wiz-connect-to-claude").componentInstance;

         expect(pane.editorContext).toEqual(
            { kind: "worksheetSqlQuery", assembly: "SQL Query1", pending: true });
      });

      it("applies a push and marks the query edited like typing does", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(advanced(), pairing(socket));
         const model = fixture.componentInstance.model.advancedModel;

         socket.commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
            kind: "worksheetSqlQuery", assembly: "SQL Query1", text: "select 99"
         }));
         fixture.detectChanges();

         expect(model.freeFormSQLPaneModel.sqlString).toBe("select 99");
         expect(model.sqlEdited).toBe(true);
      });

      it("parks a push behind the banner once the user typed, and Reload applies it", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(advanced(), pairing(socket));
         const model = fixture.componentInstance.model.advancedModel;
         model.freeFormSQLPaneModel.sqlString = "select typed";

         socket.commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
            kind: "worksheetSqlQuery", assembly: "SQL Query1", text: "select agent"
         }));
         fixture.detectChanges();

         expect(model.freeFormSQLPaneModel.sqlString).toBe("select typed");

         await userEvent.click(screen.getByText("_#(Reload)"));

         expect(model.freeFormSQLPaneModel.sqlString).toBe("select agent");
      });

      it("detaches the pairing session when the pane is destroyed", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(advanced(), pairing(socket));
         socket.conn.send.mockClear();

         fixture.destroy();

         expect(socket.conn.send).toHaveBeenCalledWith("/events/wiz/pairing/detach", {},
            expect.stringContaining("worksheetSqlQuery"));
      });

      it("stops listening for pushes once destroyed", async () => {
         const socket = makeSocket();
         const { fixture } = await renderDialog(advanced(), pairing(socket));
         const model = fixture.componentInstance.model.advancedModel;

         fixture.destroy();
         socket.commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", {
            kind: "worksheetSqlQuery", assembly: "SQL Query1", text: "select late"
         }));

         expect(model.freeFormSQLPaneModel.sqlString).toBe("select 2");
      });
   });
});
