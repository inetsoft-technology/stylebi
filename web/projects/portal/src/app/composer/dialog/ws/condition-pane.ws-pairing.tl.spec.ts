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

/**
 * Worksheet condition pairing (bug #80459, batch 1, H2). The server resolves a worksheet
 * condition target from the table's PRE-aggregate list only, so the Connect to Claude button
 * (which `condition-list` shows by forwarding `runtimeId`/`socketConnection` down to the
 * expression editor) may only be offered for that list. The Post-aggregate list and the four MV
 * append/delete lists must receive no socket; the Ranking list never did. `runtimeId` itself
 * stays on both panes, where the item-pane providers use it to fetch data.
 */
// The recording stub binds many inputs of the component it replaces; listing them in `inputs` is
// far shorter than one @Input() per property.
/* eslint-disable @angular-eslint/no-inputs-metadata-property */
import { HttpClient } from "@angular/common/http";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { render } from "@testing-library/angular";
import { AdvancedConditionPane } from "./advanced-condition-pane.component";
import { MVConditionPane } from "./mv-condition-pane.component";

const SOCKET = { commands: null } as any;

@Component({ selector: "condition-list", template: "",
             inputs: ["simplePane", "provider", "fields", "subqueryTables", "conditionList",
                      "table", "runtimeId", "socketConnection", "isVSContext",
                      "showOriginalName"] })
class StubConditionList {
   static instances: StubConditionList[] = [];
   conditionList: any[];
   runtimeId: string;
   socketConnection: any;
   constructor() { StubConditionList.instances.push(this); }
}

afterEach(() => StubConditionList.instances.length = 0);

describe("worksheet condition pairing is offered only for the Pre-aggregate list (bug #80459)", () => {
   const pre = [{ pre: 1 }];
   const post = [{ post: 1 }];

   it("advanced pane: Pre-aggregate list keeps runtime and socket, Post-aggregate and Ranking get none",
      async () =>
   {
      await render(AdvancedConditionPane, {
         componentImports: [StubConditionList],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: HttpClient, useValue: {} }],
         componentProperties: {
            runtimeId: "ws-1", socketConnection: SOCKET, assemblyName: "Query1",
            preAggregateConditionList: pre, postAggregateConditionList: post,
            rankingConditionList: [], preAggregateFields: [], postAggregateFields: [],
            expressionFields: [], variableNames: [],
         } as any,
      });

      const lists = StubConditionList.instances;
      expect(lists.length).toBe(3);

      const preList = lists.find(l => l.conditionList === pre);
      const postList = lists.find(l => l.conditionList === post);
      expect(preList.runtimeId).toBe("ws-1");
      expect(preList.socketConnection).toBe(SOCKET);
      expect(postList.socketConnection).toBeFalsy();
      expect(postList.runtimeId).toBeFalsy();
      expect(lists.filter(l => !!l.socketConnection).length).toBe(1);
   });

   it("MV pane: none of the four append/delete lists gets a socket, providers keep the runtime",
      async () =>
   {
      const { fixture } = await render(MVConditionPane, {
         componentImports: [StubConditionList, FormsModule],
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: HttpClient, useValue: {} }],
         componentProperties: {
            runtimeId: "ws-1", assemblyName: "Query1",
            model: { preAggregateFields: [], postAggregateFields: [],
                     appendPreAggregateConditionList: [], appendPostAggregateConditionList: [],
                     deletePreAggregateConditionList: [], deletePostAggregateConditionList: [] },
            expressionFields: [], variableNames: [],
         } as any,
      });

      const lists = StubConditionList.instances;
      expect(lists.length).toBe(4);
      expect(lists.filter(l => !!l.socketConnection).length).toBe(0);
      expect(lists.filter(l => !!l.runtimeId).length).toBe(0);

      const pane = fixture.componentInstance as any;
      expect(pane.appendProvider.runtimeId).toBe("ws-1");
      expect(pane.deleteProvider.runtimeId).toBe("ws-1");
   });
});
