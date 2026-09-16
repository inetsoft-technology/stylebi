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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { Subject } from "rxjs";
import { CrossSheetFollowService } from "../../composer/gui/wiz/services/cross-sheet-follow.service";
import { PortalAgentNoticeService } from "../services/portal-agent-notice.service";
import { PortalAgentNoticeComponent } from "./portal-agent-notice.component";

describe("PortalAgentNoticeComponent", () => {
   let fixture: ComponentFixture<PortalAgentNoticeComponent>;
   let component: PortalAgentNoticeComponent;
   let assetOpenedSubject: Subject<any>;
   let mockNoticeService: {
      connect: ReturnType<typeof vi.fn>;
      assetOpened: any;
      portalSessionActive: boolean;
   };
   let mockCrossSheetFollowService: {
      isEnabled: ReturnType<typeof vi.fn>;
      setEnabled: ReturnType<typeof vi.fn>;
      pending: boolean;
   };

   function toggle(): HTMLInputElement | null {
      return fixture.nativeElement.querySelector("#crossSheetFollowToggle");
   }

   beforeEach(waitForAsync(() => {
      assetOpenedSubject = new Subject<any>();
      mockNoticeService = {
         connect: vi.fn(),
         assetOpened: assetOpenedSubject.asObservable(),
         portalSessionActive: false
      };
      mockCrossSheetFollowService = {
         isEnabled: vi.fn(() => false),
         setEnabled: vi.fn(),
         pending: false
      };

      TestBed.configureTestingModule({
         imports: [PortalAgentNoticeComponent],
         providers: [
            { provide: PortalAgentNoticeService, useValue: mockNoticeService },
            { provide: CrossSheetFollowService, useValue: mockCrossSheetFollowService }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });

      TestBed.compileComponents();

      fixture = TestBed.createComponent(PortalAgentNoticeComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   }));

   describe("cross-sheet-follow toggle (charter assertion 16 / PSP-022)", () => {
      it("does not render before a portal session is known active", () => {
         expect(mockNoticeService.portalSessionActive).toBe(false);
         expect(toggle()).toBeNull();
      });

      it("renders once portalSessionActive becomes true", () => {
         mockNoticeService.portalSessionActive = true;
         fixture.detectChanges();

         expect(toggle()).toBeTruthy();
      });

      it("stays rendered even after the transient notice banner has nothing showing", () => {
         // No notice was ever shown on this fixture (component.notice is still null), yet the
         // toggle must render purely off portalSessionActive -- it is a persistent indicator
         // (design section 7.4), not tied to the transient per-notice banner's own lifetime.
         expect(component.notice).toBeNull();

         mockNoticeService.portalSessionActive = true;
         fixture.detectChanges();

         expect(toggle()).toBeTruthy();
      });

      it("checked state reflects CrossSheetFollowService.isEnabled()", () => {
         mockNoticeService.portalSessionActive = true;
         mockCrossSheetFollowService.isEnabled = vi.fn(() => true);
         fixture.detectChanges();

         expect(toggle()!.checked).toBe(true);
      });

      it("its change handler calls CrossSheetFollowService.setEnabled with the checked value", () => {
         mockNoticeService.portalSessionActive = true;
         fixture.detectChanges();

         const checkbox = toggle()!;
         checkbox.checked = true;
         checkbox.dispatchEvent(new Event("change"));

         expect(mockCrossSheetFollowService.setEnabled).toHaveBeenCalledWith(true);

         checkbox.checked = false;
         checkbox.dispatchEvent(new Event("change"));

         expect(mockCrossSheetFollowService.setEnabled).toHaveBeenCalledWith(false);
      });

      it("disables the checkbox while CrossSheetFollowService reports a call pending", () => {
         mockNoticeService.portalSessionActive = true;
         mockCrossSheetFollowService.pending = true;
         fixture.detectChanges();

         expect(toggle()!.disabled).toBe(true);
      });

      it("re-enables the checkbox once no call is pending", () => {
         mockNoticeService.portalSessionActive = true;
         mockCrossSheetFollowService.pending = false;
         fixture.detectChanges();

         expect(toggle()!.disabled).toBe(false);
      });

      it("renders the disclosure copy from design section 7.3 layer 3 / charter assertion 16", () => {
         mockNoticeService.portalSessionActive = true;
         fixture.detectChanges();

         const text: string = fixture.nativeElement.textContent;
         expect(text).toContain("Let Claude automatically follow whatever you have open in Composer");
         expect(text).toContain("Turn this off and Claude will only see what you explicitly open through it.");
      });
   });
});
