import { createTimepickerComponent } from "./timepicker.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("TimepickerComponent display", () => {
   describe("Group 1 - size classes", () => {
      it("should set compact classes for the small size", () => {
         const { comp } = createTimepickerComponent({ size: "small" });

         comp.setControlSize();

         expect(comp.formControlSize).toBe("form-control-sm");
         expect(comp.buttonSize).toBe("btn-sm");
      });

      it("should set large classes for the large size", () => {
         const { comp } = createTimepickerComponent({ size: "large" });

         comp.setControlSize();

         expect(comp.formControlSize).toBe("form-control-lg");
         expect(comp.buttonSize).toBe("btn-lg");
      });

      it("should clear size classes for the default size", () => {
         const { comp } = createTimepickerComponent({ size: "medium" });

         comp.setControlSize();

         expect(comp.formControlSize).toBeNull();
         expect(comp.buttonSize).toBeNull();
      });
   });

   describe("Group 2 - formatting and helper behavior", () => {
      it("should format meridian hours using a 12-hour clock", () => {
         const { comp } = createTimepickerComponent({
            meridian: true,
            model: { hour: 0, minute: 5, second: 9 }
         });

         expect(comp.formattedHour).toBe("12");
         expect(comp.formattedMinute).toBe("05");
         expect(comp.formattedSecond).toBe("09");
      });

      it("should format 24-hour values and wrap hours inside the valid range", () => {
         const { comp } = createTimepickerComponent({
            meridian: false,
            model: { hour: 25, minute: 3, second: 4 }
         });

         expect(comp.model.hour).toBe(25);
         expect(comp.formattedHour).toBe("01");
         expect(comp.formattedMinute).toBe("03");
         expect(comp.formattedSecond).toBe("04");
      });

      it("should surface blanks for invalid numeric values", () => {
         const { comp } = createTimepickerComponent({
            model: { hour: Number.NaN, minute: Number.NaN, second: Number.NaN },
            seconds: true
         });

         expect(comp.formattedHour).toBe("");
         expect(comp.formattedMinute).toBe("");
         expect(comp.formattedSecond).toBe("");
      });

      it("should roll minutes and seconds across their parent units", () => {
         const { comp } = createTimepickerComponent({
            model: { hour: 23, minute: 59, second: 59 },
            seconds: true
         });
         comp.ngOnInit();

         comp.changeMinute(1);
         expect(comp.model.hour).toBe(0);
         expect(comp.model.minute).toBe(0);

         comp.changeSecond(1);
         expect(comp.model.minute).toBe(1);
         expect(comp.model.second).toBe(0);
      });

      it("should track model changes after changeHour", () => {
         const { comp } = createTimepickerComponent({
            model: { hour: 6, minute: 7, second: 8 },
            seconds: true
         });

         comp.ngOnInit();
         comp.changeHour(1);

         expect(comp.model.hour).toBe(7);
         expect(comp.model.minute).toBe(7);
         expect(comp.model.second).toBe(8);
         // getTimeStruct is private — cast needed to assert the underlying NgbTime value
         expect(comp["getTimeStruct"]()).toEqual({
            hour: 7,
            minute: 7,
            second: 8
         });
      });

      it("should return null from getTimeStruct when the internal model is null", () => {
         const { comp } = createTimepickerComponent({ model: { hour: 1, minute: 0, second: 0 } });
         // _model is private — direct null assignment to exercise the null-guard branch in getTimeStruct
         comp["_model"] = null;

         // getTimeStruct is private — cast needed to call the null-guard branch directly
         expect(comp["getTimeStruct"]()).toBeNull();
      });
   });
});
