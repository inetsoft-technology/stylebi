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
import { FormattingPane } from "./formatting-pane.component";
import { FormatInfoModel } from "../../common/data/format-info-model";

const createModel = () => {
    return {
       type: "",
        color: "",
        backgroundColor: "",
        font: null,
        align: null,
        format: "",
    };
};

let pane: FormattingPane;

let model: FormatInfoModel;

describe("FormattingPane Increase Decimal Unit Tests", () => {
    beforeEach(() => {
        pane = new FormattingPane();
        model = createModel();
    });

    it("increase decimal should be enabled for number formats and disabled for the rest", () => {
        pane.formatModel = model;

        model.format = "DateFormat";
        expect(pane.increaseDecimalDisabled()).toBeTruthy();
        model.format = "MessageFormat";
        expect(pane.increaseDecimalDisabled()).toBeTruthy();

        model.format = "CurrencyFormat";
        expect(pane.increaseDecimalDisabled()).toBeFalsy();
        model.format = "PercentFormat";
        expect(pane.increaseDecimalDisabled()).toBeFalsy();
        model.format = "DecimalFormat";
        expect(pane.increaseDecimalDisabled()).toBeFalsy();
    });

   it("non-number format should do nothing", () => {
       pane.formatModel = model;
       model.format = "DateFormat";
       model.formatSpec = null;

       pane.increaseDecimal();

       expect(model.formatSpec).toBeNull();
   });

    it("currency format should increase decimal", () => {
        pane.formatModel = model;
        model.format = "CurrencyFormat";
        model.formatSpec = null;

        pane.increaseDecimal();

        expect(model.formatSpec).toMatchSnapshot();
    });

    it("percent format should increase decimal", () => {
        pane.formatModel = model;
        model.format = "PercentFormat";
        model.formatSpec = null;

        pane.increaseDecimal();

        expect(model.formatSpec).toMatchSnapshot();
    });

   it("simple decimal format should increase decimal", () => {
       pane.formatModel = model;
       model.format = "DecimalFormat";

       const increase = (before, after) => {
           model.formatSpec = before;
           pane.increaseDecimal();
           expect(model.formatSpec).toEqual(after);
       };

       increase("0.0", "0.00");
       increase("0.", "0.0");
       increase("###0.0;(###0.0)", "###0.00;(###0.00)");
       increase("###0%;(###0%)", "###0.0%;(###0.0%)");
       increase("text", ".0text");
   });

   it("empty decimal format should increase decimal", () => {
       pane.formatModel = model;
       model.format = "DecimalFormat";
       model.formatSpec = "";

       pane.increaseDecimal();

       expect(model.formatSpec).toMatchSnapshot();
   });
});

describe("FormattingPane Decrease Decimal Unit Tests", () => {
    beforeEach(() => {
        pane = new FormattingPane();
        model = createModel();
    });

    it("decrease decimal should be enabled for only currency and decimal formats", () => {
        pane.formatModel = model;
        model.formatSpec = "0.0";

        model.format = "DateFormat";
        expect(pane.decreaseDecimalDisabled()).toBeTruthy();
        model.format = "MessageFormat";
        expect(pane.decreaseDecimalDisabled()).toBeTruthy();
        model.format = "PercentFormat";
        expect(pane.decreaseDecimalDisabled()).toBeTruthy();

        model.format = "CurrencyFormat";
        expect(pane.decreaseDecimalDisabled()).toBeFalsy();
        model.format = "DecimalFormat";
        expect(pane.decreaseDecimalDisabled()).toBeFalsy();
    });

    it("decrease decimal should be disabled when the number has no decimal", () => {
        pane.formatModel = model;
        model.format = "DecimalFormat";
        model.formatSpec = "0";

        expect(pane.decreaseDecimalDisabled()).toBeTruthy();
    });

    it("decrease decimal should be enabled when the number has a decimal", () => {
        pane.formatModel = model;
        model.format = "DecimalFormat";

        const enabled = spec => {
            model.formatSpec = spec;
            expect(pane.decreaseDecimalDisabled()).toBeFalsy();
        };

        enabled("0.");
        enabled("0.;(0)");
        enabled("0;(0.)");
        enabled("0.;(0.)");
        enabled("0.0;(0)");
        enabled("0;(0.0)");
        enabled("0.0;(0.0)");
    });

    it("should decrease decimal format", () => {
        pane.formatModel = model;
        model.format = "DecimalFormat";

        const decrease = (before, after) => {
            model.formatSpec = before;
            pane.decreaseDecimal();
            expect(model.formatSpec).toEqual(after);
        };

        decrease("", "");
        decrease("0", "0");
        decrease("0.0", "0");
        decrease("0.", "0");
        decrease("0.;(0)", "0;(0)");
        decrease("0;(0.)", "0;(0)");
        decrease("0.;(0.)", "0;(0)");
        decrease("0.0;(0)", "0;(0)");
        decrease("0;(0.0)", "0;(0)");
        decrease("0.0;(0.0)", "0;(0)");
        decrease("0.0%;(0.0%)", "0%;(0%)");
        decrease("0.00;(0.00)", "0.0;(0.0)");
        decrease("0.00%;(0.00%)", "0.0%;(0.0%)");
        decrease("$0.00;($0.00)", "$0.0;($0.0)");
        decrease("0.##%;(0.##%)", "0.#%;(0.#%)");
        decrease("###0.0#;(###0.0#)", "###0.#;(###0.#)");
        decrease("0.0text", "0text");
    });

   it("non-number format should do nothing", () => {
       pane.formatModel = model;
       model.format = "DateFormat";
       model.formatSpec = null;

       pane.decreaseDecimal();

       expect(model.formatSpec).toBeNull();
   });

   it("currency format should decrease decimal", () => {
       pane.formatModel = model;
       model.format = "CurrencyFormat";
       model.formatSpec = null;

       pane.decreaseDecimal();

       expect(model.formatSpec).toMatchSnapshot();
   });

   it("percent format should do nothing", () => {
       pane.formatModel = model;
       model.format = "PercentFormat";
       model.formatSpec = null;

       pane.decreaseDecimal();

       expect(model.formatSpec).toBeNull();
   });
});
