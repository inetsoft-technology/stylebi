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
import { TestBed } from "@angular/core/testing";
import { DateTypeFormatter } from "./date-type-formatter";

describe('date-type-formatter', () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [],
         providers: []
      })
   });

   it("transformValue", () => {
      expect(DateTypeFormatter.transformValue("2020-07-10 15:00:00",
         "YYYY-MM-DDTHH:mm:ss", "YYYY-MM-DD"))
         .toBe("2020-07-10");
      expect(DateTypeFormatter.transformValue("2020-07-10",
         "YYYY-MM-DD", "YYYY/MM/DD"))
         .toBe("2020/07/10");
      expect(DateTypeFormatter.transformValue("2020-07-10",
         "YYYY-MM-DD", "DD/MM/YYYY"))
         .toBe("10/07/2020");

      expect(DateTypeFormatter.transformValue("2020-07-10 15:00:00",
         "YYYY-MM-DDTHH:mm:ss", "[{ts ']YYYY-MM-DD HH:mm:ss['}]"))
         .toBe("{ts '2020-07-10 15:00:00'}");
      expect(DateTypeFormatter.transformValue("2023-10-08 07:30:20",
         "YYYY-MM-DDTHH:mm:ss", "[{t ']HH:mm:ss['}]"))
         .toBe("{t '07:30:20'}");
      expect(DateTypeFormatter.transformValue("2023-10-08 07:30:20",
         "YYYY-MM-DDTHH:mm:ss", "[{d ']YYYY-MM-DD['}]"))
         .toBe("{d '2023-10-08'}");

      expect(DateTypeFormatter.transformValue("2023-10-08 07:30:20",
         "YYYY-MM-DDTHH:mm:ss", "YYYY-MM-DDTHH:mm:ss"))
         .toBe("2023-10-08T07:30:20");

      // date can be parsed with new format
      expect(DateTypeFormatter.transformValue("2017-01-06 00:00:00",
         "[{ts ']YYYY-MM-DD HH:mm:ss['}]", "YYYY-MM-DDTHH:mm:ss"))
         .toBe("2017-01-06T00:00:00");

      // Bug #66449
      // a PM
      expect(DateTypeFormatter.transformValue("2024-06-12 11:59:59 PM",
         "yyyy-MM-dd hh:mm:ss a", "hh:mm:ss a YYYY-MM-DD"))
         .toBe("11:59:59 PM 2024-06-12");
      // A PM
      expect(DateTypeFormatter.transformValue("2024-06-12 11:59:59 PM",
         "yyyy-MM-dd hh:mm:ss A", "hh:mm:ss a YYYY-MM-DD"))
         .toBe("11:59:59 PM 2024-06-12");
      // a pm
      expect(DateTypeFormatter.transformValue("2024-06-12 11:59:59 pm",
         "yyyy-MM-dd hh:mm:ss a", "hh:mm:ss a YYYY-MM-DD"))
         .toBe("11:59:59 PM 2024-06-12");
      // A pm
      expect(DateTypeFormatter.transformValue("2024-06-12 11:59:59 pm",
         "yyyy-MM-dd hh:mm:ss A", "hh:mm:ss a YYYY-MM-DD"))
         .toBe("11:59:59 PM 2024-06-12");
   });

   it("toTimeInstant", () => {
      let expected = {
         "date": 8,
         "hours": 7,
         "milliseconds": 0,
         "minutes": 30,
         "months": 9,
         "seconds": 20,
         "years": 2023
      };
      expect(DateTypeFormatter.toTimeInstant("2023-10-08 07:30:20",
         "YYYY-MM-DDTHH:mm:ss"))
         .toStrictEqual(expected);

      expected = {
         "date": 11,
         "hours": 0,
         "milliseconds": 0,
         "minutes": 0,
         "months": 10,
         "seconds": 0,
         "years": 2023
      }
      expect(DateTypeFormatter.toTimeInstant("2023/11/11",
         "YYYY/MM/DD"))
         .toStrictEqual(expected);

      expected = {
         "date": 0,
         "hours": 15,
         "milliseconds": 0,
         "minutes": 30,
         "months": 0,
         "seconds": 20,
         "years": 0
      }
      let timeInstant = DateTypeFormatter.toTimeInstant("15:30:20",
         "HH:mm:ss");
      expect(timeInstant.hours).toBe(expected.hours);
      expect(timeInstant.minutes).toBe(expected.minutes);
      expect(timeInstant.seconds).toBe(expected.seconds);

      // Bug #66449
      expected = {
         "date": 12,
         "hours": 23,
         "milliseconds": 0,
         "minutes": 59,
         "months": 5,
         "seconds": 59,
         "years": 2024
      };
      expect(DateTypeFormatter.toTimeInstant("2024-06-12 11:59:59 PM",
         "yyyy-MM-dd hh:mm:ss a"))
         .toStrictEqual(expected);
   });

   it("currentTimeInstantInFormat", () => {
      let formatted = DateTypeFormatter.currentTimeInstantInFormat("YYYY-MM-DDTHH:mm:ss");
      expect(formatted.split("-").length).toBe(3);
      expect(formatted.split("T").length).toBe(2);
      expect(formatted.split(":").length).toBe(3);

      formatted = DateTypeFormatter.currentTimeInstantInFormat("YYYY/MM/DD");
      expect(formatted.split("/").length).toBe(3);
      expect(formatted.includes("-")).toBeFalsy();
      expect(formatted.includes("T")).toBeFalsy();
      expect(formatted.includes(":")).toBeFalsy();

      formatted = DateTypeFormatter.currentTimeInstantInFormat("HH:mm:ss");
      expect(formatted.split(":").length).toBe(3);
      expect(formatted.includes("-")).toBeFalsy();
      expect(formatted.includes("T")).toBeFalsy();
      expect(formatted.includes("/")).toBeFalsy();
   });

   it("format", () => {
      let formatted = DateTypeFormatter.format(new Date(), "YYYY-MM-DDTHH:mm:ss");
      expect(formatted.split("-").length).toBe(3);
      expect(formatted.split("T").length).toBe(2);
      expect(formatted.split(":").length).toBe(3);

      formatted = DateTypeFormatter.format(new Date(), "YYYY/MM/DD");
      expect(formatted.split("/").length).toBe(3);
      expect(formatted.includes("-")).toBeFalsy();
      expect(formatted.includes("T")).toBeFalsy();
      expect(formatted.includes(":")).toBeFalsy();

      formatted = DateTypeFormatter.format(new Date(), "HH:mm:ss");
      expect(formatted.split(":").length).toBe(3);
      expect(formatted.includes("-")).toBeFalsy();
      expect(formatted.includes("T")).toBeFalsy();
      expect(formatted.includes("/")).toBeFalsy();
   });

   it("formatInTimeZone", () => {
      let timeGreenwich = DateTypeFormatter.formatInTimeZone(new Date(), "Etc/Greenwich", "HH");
      let timeSeoul = DateTypeFormatter.formatInTimeZone(new Date(), "Asia/Seoul", "HH");
      expect((((parseInt(timeSeoul, 10) - parseInt(timeGreenwich, 10)) % 24) + 24) % 24).toBe(9);
   });

   it("formatDuration", () => {
      expect(DateTypeFormatter.formatDuration(1697468075673, "H:mm:ss"))
         .toBe("14:54:35");
   });

   it("timeInstantToDate", () => {
      let timeInstant = {
         "date": 11,
         "hours": 5,
         "milliseconds": 0,
         "minutes": 30,
         "months": 10,
         "seconds": 30,
         "years": 2023
      };
      let date = DateTypeFormatter.timeInstantToDate(timeInstant);
      expect(date.getFullYear()).toBe(2023);
      expect(date.getMonth()).toBe(10);
      expect(date.getDate()).toBe(11);
      expect(date.getHours()).toBe(5);
      expect(date.getMinutes()).toBe(30);
      expect(date.getSeconds()).toBe(30);
   });

   it("formatInstant", () => {
      let timeInstant = {
         "date": 11,
         "hours": 5,
         "milliseconds": 0,
         "minutes": 30,
         "months": 10,
         "seconds": 30,
         "years": 2023
      };

      expect(DateTypeFormatter.formatInstant(timeInstant, "YYYY-MM-DDTHH:mm:ss"))
         .toBe("2023-11-11T05:30:30");
   });

   it("formatStr", () => {
      expect(DateTypeFormatter.formatStr("2023-11-11T05:30:30", "YYYY-MM-DDTHH:mm:ss"))
         .toBe("2023-11-11T05:30:30");
   });
});
