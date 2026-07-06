import {
   createTimepickerComponent,
   TimeStructLike
} from "./timepicker.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

function createKeyboardEvent(key: string) {
   return {
      key,
      preventDefault: vi.fn()
   } as unknown as KeyboardEvent;
}

describe("TimepickerComponent interaction", () => {
   describe("Group 1 - initialization and propagation", () => {
      it("should initialize form controls from the current model on ngOnInit", () => {
         const { comp } = createTimepickerComponent({
            model: { hour: 14, minute: 3, second: 9 },
            seconds: true,
            size: "small"
         });

         comp.ngOnInit();

         expect(comp.formControlSize).toBe("form-control-sm");
         expect(comp.buttonSize).toBe("btn-sm");
         expect(comp.form.controls["hourInput"].value).toBe("14");
         expect(comp.form.controls["minuteInput"].value).toBe("03");
         expect(comp.form.controls["secondInput"].value).toBe("09");
      });

      it("should clone the input model and default seconds to zero when seconds are hidden", () => {
         const source: TimeStructLike = { hour: 8, minute: 15 };
         const { comp } = createTimepickerComponent({
            model: source,
            seconds: false
         });

         expect(comp.model).not.toBe(source);
         expect(comp.model.hour).toBe(8);
         expect(comp.model.minute).toBe(15);
         expect(comp.model.second).toBe(0);
         expect(comp.formattedHour).toBe("08");
         expect(comp.formattedMinute).toBe("15");
         expect(comp.formattedSecond).toBe("00");
      });

      it("should propagate spinner changes through form controls and timeChange", () => {
         const { comp } = createTimepickerComponent({
            model: { hour: 10, minute: 20, second: 30 },
            seconds: true
         });
         const emitSpy = vi.spyOn(comp.timeChange, "emit");
         comp.ngOnInit();

         comp.changeHour(2);
         comp.changeMinute(5);
         comp.changeSecond(-10);

         expect(comp.form.controls["hourInput"].value).toBe("12");
         expect(comp.form.controls["minuteInput"].value).toBe("25");
         expect(comp.form.controls["secondInput"].value).toBe("20");
         expect(emitSpy).toHaveBeenLastCalledWith({
            hour: 12,
            minute: 25,
            second: 20
         });
      });
   });

   describe("Group 2 - keyboard handlers", () => {
      it("should increment and decrement the hour with arrow keys", () => {
         const { comp } = createTimepickerComponent({
            model: { hour: 9, minute: 5, second: 0 }
         });
         comp.ngOnInit();
         const up = createKeyboardEvent("ArrowUp");
         const down = createKeyboardEvent("ArrowDown");

         comp.handleHour(up);
         comp.handleHour(down);

         expect((up.preventDefault as ReturnType<typeof vi.fn>)).toHaveBeenCalled();
         expect((down.preventDefault as ReturnType<typeof vi.fn>)).toHaveBeenCalled();
         expect(comp.model.hour).toBe(9);
      });

      it("should increment the minute and second with arrow key handlers", () => {
         const { comp } = createTimepickerComponent({
            model: { hour: 9, minute: 59, second: 59 },
            seconds: true
         });
         comp.ngOnInit();
         const minuteUp = createKeyboardEvent("ArrowUp");
         const secondUp = createKeyboardEvent("ArrowUp");

         comp.handleMinute(minuteUp);
         comp.handleSecond(secondUp);

         expect(comp.model.hour).toBe(10);
         expect(comp.model.minute).toBe(1);
         expect(comp.model.second).toBe(0);
      });

      it("should ignore non-arrow keyboard input", () => {
         const { comp } = createTimepickerComponent({
            model: { hour: 11, minute: 22, second: 33 },
            seconds: true
         });
         const event = createKeyboardEvent("Enter");

         comp.handleHour(event);
         comp.handleMinute(event);
         comp.handleSecond(event);

         expect((event.preventDefault as ReturnType<typeof vi.fn>)).not.toHaveBeenCalled();
         expect(comp.model.hour).toBe(11);
         expect(comp.model.minute).toBe(22);
         expect(comp.model.second).toBe(33);
      });
   });

   describe("Group 3 - direct input updates", () => {
      it("should preserve PM when updating meridian hours", () => {
         const { comp } = createTimepickerComponent({
            meridian: true,
            model: { hour: 15, minute: 10, second: 0 }
         });
         const emitSpy = vi.spyOn(comp.timeChange, "emit");
         comp.ngOnInit();

         comp.updateHour("4");

         expect(comp.model.hour).toBe(16);
         expect(comp.formattedHour).toBe("04");
         expect(emitSpy).toHaveBeenLastCalledWith({
            hour: 16,
            minute: 10,
            second: 0
         });
      });

      it("should map meridian input 12 back to midnight for AM values", () => {
         const { comp } = createTimepickerComponent({
            meridian: true,
            model: { hour: 0, minute: 45, second: 0 }
         });
         comp.ngOnInit();

         comp.updateHour("12");

         expect(comp.model.hour).toBe(0);
         expect(comp.formattedHour).toBe("12");
      });

      it("should update minute and second inputs directly", () => {
         const { comp } = createTimepickerComponent({
            model: { hour: 7, minute: 5, second: 9 },
            seconds: true
         });
         comp.ngOnInit();

         comp.updateMinute("41");
         comp.updateSecond("58");

         expect(comp.model.minute).toBe(41);
         expect(comp.model.second).toBe(58);
         expect(comp.form.controls["minuteInput"].value).toBe("41");
         expect(comp.form.controls["secondInput"].value).toBe("58");
      });

      it("should add twelve hours when toggling meridian with meridian mode enabled", () => {
         const { comp } = createTimepickerComponent({
            meridian: true,
            model: { hour: 1, minute: 0, second: 0 }
         });
         comp.ngOnInit();

         comp.toggleMeridian();

         expect(comp.model.hour).toBe(13);
      });

      it("should leave the hour unchanged when toggleMeridian is called without meridian mode", () => {
         const { comp } = createTimepickerComponent({
            meridian: false,
            model: { hour: 1, minute: 0, second: 0 }
         });
         comp.ngOnInit();

         comp.toggleMeridian();

         expect(comp.model.hour).toBe(1);
      });
   });
});
