import { Component, EventEmitter, HostListener, Inject, OnDestroy, Output } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";

@Component({
   selector: "em-session-expiration-dialog",
   templateUrl: "./session-expiration-dialog.component.html",
   styleUrls: ["./session-expiration-dialog.component.scss"]
})
export class SessionExpirationDialog implements OnDestroy {
   @Output() onLogout: EventEmitter<void> = new EventEmitter<void>();
   @Output() onTimerFinished: EventEmitter<void> = new EventEmitter<void>();
   private interval: any;
   private _remainingTime: number;
   duration: string;

   set remainingTime(value: number) {
      this._remainingTime = value;
      this.duration = DateTypeFormatter.formatDuration(this._remainingTime, "mm:ss");

      if(this.interval != null) {
         clearInterval(this.interval);
      }

      this.interval = setInterval(() => {
         this._remainingTime = Math.max(0, this._remainingTime - 1000);
         this.duration = DateTypeFormatter.formatDuration(this._remainingTime, "mm:ss");

         if(this._remainingTime == 0) {
            clearInterval(this.interval);
            this.interval = null;
            this.onTimerFinished.emit();
         }
      }, 1000);
   }

   constructor(public dialogRef: MatDialogRef<SessionExpirationDialog>,
               @Inject(MAT_DIALOG_DATA) public data: any)
   {
      this.remainingTime = data.remainingTime;
   }

   stayLoggedInClicked(): void {
      this.dialogRef.close(true);
   }

   logoutClicked(): void {
      this.onLogout.emit();
      this.dialogRef.close(false);
   }

   ngOnDestroy(): void {
      if(this.interval != null) {
         clearInterval(this.interval);
         this.interval = null;
      }
   }

   // to prevent mousedown event from being propagated to the document when it originates on
   // this dialog and being counted as user activity in session-inactivity.service.ts
   @HostListener("mousedown", ["$event"])
   mousedown(event: MouseEvent): void {
      event.stopPropagation();
      event.stopImmediatePropagation();
      event.preventDefault();
   }

   @HostListener("wheel", ["$event"])
   wheel(event: WheelEvent): void {
      event.stopPropagation();
      event.stopImmediatePropagation();
      event.preventDefault();
   }
}
