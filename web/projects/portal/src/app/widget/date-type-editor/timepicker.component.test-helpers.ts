import { TimepickerComponent } from "./timepicker.component";

export interface TimeStructLike {
   hour?: number;
   minute?: number;
   second?: number;
}

export interface TimepickerOverrides {
   disabled?: boolean;
   hourStep?: number;
   meridian?: boolean;
   minuteStep?: number;
   model?: TimeStructLike | null;
   readonlyInputs?: number;
   secondStep?: number;
   seconds?: boolean;
   size?: string;
   spinners?: boolean;
}

export interface TimepickerComponentContext {
   comp: TimepickerComponent;
}

export function createTimepickerComponent(
   overrides: TimepickerOverrides = {}
): TimepickerComponentContext {
   const comp = new TimepickerComponent();

   comp.meridian = overrides.meridian ?? false;
   comp.spinners = overrides.spinners ?? false;
   comp.seconds = overrides.seconds ?? false;
   comp.hourStep = overrides.hourStep ?? 1;
   comp.minuteStep = overrides.minuteStep ?? 1;
   comp.secondStep = overrides.secondStep ?? 1;
   comp.readonlyInputs = overrides.readonlyInputs;
   comp.size = overrides.size;
   comp.disabled = overrides.disabled ?? false;
   // The component setter only reads hour/minute/second and clones into its private NgbTime type.
   const modelValue = overrides.model === undefined ?
      { hour: 9, minute: 5, second: 7 } : overrides.model;
   comp.model = modelValue as unknown as TimepickerComponent["model"];

   return { comp };
}
