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
 * hyperlinkRegionName - the region key the Hyperlink dialog names for its pairing target
 * (bug #80459). `hyperlink-pairing-names.json` is the shared vocabulary: the server's
 * HyperlinkNamesManifestTest reads the same file, so a name produced here that the server grammar
 * rejects (or a server name this function can never produce) fails one of the two suites.
 */
import { readFileSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";
import { HyperlinkDialogModel } from "../../model/hyperlink-dialog-model";
import { hyperlinkRegionName } from "./hyperlink-pairing";

const MANIFEST: { name: string; assemblyTypes: string[] }[] = JSON.parse(
   readFileSync(join(dirname(fileURLToPath(import.meta.url)), "hyperlink-pairing-names.json"),
                "utf8"));

function model(overrides: Partial<HyperlinkDialogModel>): HyperlinkDialogModel {
   return { table: false, ...overrides } as HyperlinkDialogModel;
}

// The model the dialog would hold for a manifest name (what the server's model carries).
function modelFor(name: string): HyperlinkDialogModel {
   const cell = /^cell\[(\d+),(\d+)\]$/.exec(name);

   if(cell) {
      return model({ table: true, row: Number(cell[1]), col: Number(cell[2]) });
   }

   const column = /^(field|axis|text)\[([\s\S]*)\]$/.exec(name);

   if(column) {
      return model({ colName: column[2], axis: column[1] === "axis", text: column[1] === "text" });
   }

   return model({ titleLink: name === "title", emptyPlotLink: name === "emptyPlot" });
}

describe("hyperlinkRegionName (bug #80459)", () => {
   it("reads the manifest: every shared name is the name the dialog model derives", () => {
      expect(MANIFEST.length).toBeGreaterThan(10);

      for(const entry of MANIFEST) {
         expect(hyperlinkRegionName(modelFor(entry.name))).toBe(entry.name);
      }
   });

   it.each([
      ["a text, image or gauge: the one link", model({}), "link"],
      ["a chart title link", model({ titleLink: true }), "title"],
      ["a chart empty plot link", model({ emptyPlotLink: true }), "emptyPlot"],
      ["a table cell, 0-based", model({ table: true, row: 2, col: 1 }), "cell[2,1]"],
      ["the first cell", model({ table: true, row: 0, col: 0 }), "cell[0,0]"],
      ["a chart data field", model({ colName: "Total Sales" }), "field[Total Sales]"],
      ["a chart axis", model({ colName: "Region", axis: true }), "axis[Region]"],
      ["a chart text field", model({ colName: "Sales", text: true }), "text[Sales]"],
      ["a column with a bracket in it", model({ colName: "a]b" }), "field[a]b]"],
   ])("%s", (_label: string, m: HyperlinkDialogModel, expected: string) => {
      expect(hyperlinkRegionName(m)).toBe(expected);
   });

   it("the title and empty plot links win over a column the model still carries", () => {
      expect(hyperlinkRegionName(model({ titleLink: true, colName: "Sales" }))).toBe("title");
      expect(hyperlinkRegionName(model({ emptyPlotLink: true, colName: "Sales" })))
         .toBe("emptyPlot");
   });

   it("a table model without a numeric cell names nothing, never a half cell", () => {
      expect(hyperlinkRegionName(model({ table: true }))).toBeNull();
      expect(hyperlinkRegionName(model({ table: true, row: 1 }))).toBeNull();
   });

   it("a missing model names nothing", () => {
      expect(hyperlinkRegionName(null)).toBeNull();
      expect(hyperlinkRegionName(undefined)).toBeNull();
   });
});
