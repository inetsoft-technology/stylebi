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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { of } from "rxjs";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ConnectToClaudeComponent } from "./connect-to-claude.component";

describe("ConnectToClaudeComponent", () => {
   let fixture: ComponentFixture<ConnectToClaudeComponent>;
   let component: ConnectToClaudeComponent;
   let mockSocketConnection: any;
   let mockStompConnection: { subscribe: ReturnType<typeof vi.fn>; send: ReturnType<typeof vi.fn> };

   beforeEach(waitForAsync(() => {
      mockStompConnection = {
         subscribe: vi.fn(),
         send: vi.fn()
      };

      mockSocketConnection = {
         whenConnected: vi.fn(() => of(mockStompConnection))
      };

      // Default: subscribe returns a Subscription-like object
      mockStompConnection.subscribe.mockReturnValue({ unsubscribe: vi.fn() });

      TestBed.configureTestingModule({
         imports: [ConnectToClaudeComponent],
         providers: [
            { provide: ViewsheetClientService, useValue: mockSocketConnection }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });

      TestBed.compileComponents();

      fixture = TestBed.createComponent(ConnectToClaudeComponent);
      component = fixture.componentInstance;
      component.runtimeId = "rt-1";
      component.sheetType = "WORKSHEET";
      component.socketConnection = mockSocketConnection;
      fixture.detectChanges();
   }));

   it("Connect to Claude button calls requestCode", () => {
      const button: HTMLButtonElement = fixture.nativeElement.querySelector("button");
      expect(button).toBeTruthy();
      expect(button.disabled).toBeFalsy();

      button.click();
      fixture.detectChanges();

      expect(component.loading).toBe(true);
      expect(button.disabled).toBeTruthy();
      expect(mockSocketConnection.whenConnected).toHaveBeenCalled();
   });

   it("requestCode subscribes to /user/commands/wiz/pairing/mint and sends mint request", () => {
      component.requestCode();

      expect(mockStompConnection.subscribe).toHaveBeenCalledWith(
         "/user/commands/wiz/pairing/mint",
         expect.any(Function)
      );
      expect(mockStompConnection.send).toHaveBeenCalledWith(
         "/events/wiz/pairing/mint",
         {},
         JSON.stringify({ runtimeId: "rt-1", sheetType: "WORKSHEET" })
      );
   });

   it("displays code when response arrives", () => {
      let capturedHandler: ((msg: any) => void) | null = null;
      const subSpy = { unsubscribe: vi.fn() };
      mockStompConnection.subscribe.mockImplementation((_dest: string, handler: (msg: any) => void) => {
         capturedHandler = handler;
         return subSpy;
      });

      component.requestCode();
      expect(capturedHandler).not.toBeNull();

      capturedHandler!({ frame: { body: JSON.stringify({ code: "ABC123" }) } });
      fixture.detectChanges();

      expect(component.code).toBe("ABC123");
      expect(component.loading).toBe(false);
      expect(subSpy.unsubscribe).toHaveBeenCalled();

      const codeEl: HTMLElement = fixture.nativeElement.querySelector(".wiz-connect-value");
      expect(codeEl).toBeTruthy();
      expect(codeEl.textContent?.trim()).toBe("ABC123");
   });

   it("shows error when response has error field", () => {
      let capturedHandler: ((msg: any) => void) | null = null;
      mockStompConnection.subscribe.mockImplementation((_dest: string, handler: (msg: any) => void) => {
         capturedHandler = handler;
         return { unsubscribe: vi.fn() };
      });

      component.requestCode();
      capturedHandler!({ frame: { body: JSON.stringify({ error: "Feature disabled" }) } });
      fixture.detectChanges();

      expect(component.error).toBe("Feature disabled");
      expect(component.code).toBeNull();
      expect(component.loading).toBe(false);
   });

   it("ngOnDestroy unsubscribes pending mint", () => {
      const subSpy = { unsubscribe: vi.fn() };
      mockStompConnection.subscribe.mockReturnValue(subSpy);

      component.requestCode();
      fixture.destroy();

      expect(subSpy.unsubscribe).toHaveBeenCalled();
   });
});
