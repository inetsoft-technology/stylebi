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
import { CommonModule } from "@angular/common";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatTableModule } from "@angular/material/table";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { of } from "rxjs";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { MatSnackBar } from "@angular/material/snack-bar";
import { MatTooltipModule } from "@angular/material/tooltip";
import { SecurityTreeDialogModule } from "../security-tree-dialog/security-tree-dialog.module";
import { IdentityModel } from "./identity-model";
import { SecurityTableViewComponent } from "./security-table-view.component";
import { IdentityClipboardService, COPY_PASTE_CONTEXT_IDENTITY_MEMBERS, COPY_PASTE_CONTEXT_IDENTITY_ROLES } from "./identity-clipboard.service";

describe("SecurityTableViewComponent", () => {
  let component: SecurityTableViewComponent;
  let fixture: ComponentFixture<SecurityTableViewComponent>;
  let mockClipboardService: any;

  function makeIdentity(name: string): IdentityModel {
    return { identityID: { name, orgID: null }, type: IdentityType.USER };
  }

  beforeEach(waitForAsync(() => {
    mockClipboardService = {
      canPaste: jest.fn(() => false),
      hasContent: jest.fn(() => false),
      copiedCount: jest.fn(() => 0),
      copiedTotal: jest.fn(() => 0),
      copy: jest.fn(),
      paste: jest.fn(() => null)
    };

    TestBed.configureTestingModule({
      imports: [
        NoopAnimationsModule,
        CommonModule,
        MatTableModule,
        MatCheckboxModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatCardModule,
        MatIconModule,
        MatSnackBarModule,
        MatTooltipModule,
        SecurityTreeDialogModule,
        MatPaginatorModule
      ],
      declarations: [ SecurityTableViewComponent ],
      providers: [
        { provide: IdentityClipboardService, useValue: mockClipboardService }
      ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SecurityTableViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it("should create", () => {
    expect(component).toBeTruthy();
  });

  describe("copyIdentities", () => {
    beforeEach(() => {
      component.copyPasteContext = COPY_PASTE_CONTEXT_IDENTITY_MEMBERS;
    });

    it("should copy all rows when nothing is selected", () => {
      const alice = makeIdentity("alice");
      const bob = makeIdentity("bob");
      component.dataSource = [alice, bob];
      component.ngOnChanges({ dataSource: { currentValue: [alice, bob], previousValue: null, firstChange: true, isFirstChange: () => true } });

      component.copyIdentities();

      expect(mockClipboardService.copy).toHaveBeenCalledWith([alice, bob], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
    });

    it("should copy only selected rows when selection is non-empty", () => {
      const alice = makeIdentity("alice");
      const bob = makeIdentity("bob");
      component.dataSource = [alice, bob];
      component.ngOnChanges({ dataSource: { currentValue: [alice, bob], previousValue: null, firstChange: true, isFirstChange: () => true } });
      component.selection.select(alice);

      component.copyIdentities();

      expect(mockClipboardService.copy).toHaveBeenCalledWith([alice], COPY_PASTE_CONTEXT_IDENTITY_MEMBERS);
    });

    it("should show snack bar after copying", () => {
      const snackBar = TestBed.inject(MatSnackBar);
      const snackSpy = jest.spyOn(snackBar, "open");
      component.dataSource = [makeIdentity("alice")];
      component.ngOnChanges({ dataSource: { currentValue: component.dataSource, previousValue: null, firstChange: true, isFirstChange: () => true } });

      component.copyIdentities();

      expect(snackSpy).toHaveBeenCalled();
    });

    it("should do nothing when copyPasteContext is null", () => {
      component.copyPasteContext = null;
      component.dataSource = [makeIdentity("alice")];
      component.ngOnChanges({ dataSource: { currentValue: component.dataSource, previousValue: null, firstChange: true, isFirstChange: () => true } });

      component.copyIdentities();

      expect(mockClipboardService.copy).not.toHaveBeenCalled();
    });
  });

  describe("pasteIdentities", () => {
    it("should not open dialog when clipboard is empty", () => {
      mockClipboardService.paste.mockReturnValue(null);
      const dialogSpy = jest.spyOn(component["dialog"], "open");

      component.pasteIdentities();

      expect(dialogSpy).not.toHaveBeenCalled();
    });

    it("should not open dialog when paste returns empty array", () => {
      mockClipboardService.paste.mockReturnValue([]);
      const dialogSpy = jest.spyOn(component["dialog"], "open");

      component.pasteIdentities();

      expect(dialogSpy).not.toHaveBeenCalled();
    });

    it("should emit pasteReplaceIdentities when dialog is confirmed", () => {
      const identities = [makeIdentity("alice")];
      mockClipboardService.paste.mockReturnValue(identities);
      jest.spyOn(component["dialog"], "open").mockReturnValue({ afterClosed: () => of(true) } as any);

      const emitted: IdentityModel[][] = [];
      component.pasteReplaceIdentities.subscribe(v => emitted.push(v));

      component.pasteIdentities();

      expect(emitted.length).toBe(1);
      expect(emitted[0]).toEqual(identities);
    });

    it("should not emit pasteReplaceIdentities when dialog is cancelled", () => {
      const identities = [makeIdentity("alice")];
      mockClipboardService.paste.mockReturnValue(identities);
      jest.spyOn(component["dialog"], "open").mockReturnValue({ afterClosed: () => of(false) } as any);

      const emitted: IdentityModel[][] = [];
      component.pasteReplaceIdentities.subscribe(v => emitted.push(v));

      component.pasteIdentities();

      expect(emitted.length).toBe(0);
    });

    it("should use pasteContexts instead of copyPasteContext when set", () => {
      component.copyPasteContext = COPY_PASTE_CONTEXT_IDENTITY_MEMBERS;
      component.pasteContexts = [COPY_PASTE_CONTEXT_IDENTITY_ROLES, COPY_PASTE_CONTEXT_IDENTITY_MEMBERS];
      const identities = [makeIdentity("alice")];
      mockClipboardService.paste.mockReturnValue(identities);
      jest.spyOn(component["dialog"], "open").mockReturnValue({ afterClosed: () => of(true) } as any);

      component.pasteIdentities();

      expect(mockClipboardService.paste).toHaveBeenCalledWith(
        [COPY_PASTE_CONTEXT_IDENTITY_ROLES, COPY_PASTE_CONTEXT_IDENTITY_MEMBERS],
        null
      );
    });
  });

  describe("pasteTooltip", () => {
    it("should return empty clipboard message when clipboard has no content", () => {
      mockClipboardService.canPaste.mockReturnValue(false);
      mockClipboardService.hasContent.mockReturnValue(false);
      mockClipboardService.copiedCount.mockReturnValue(0);
      expect(component.pasteTooltip).toBe("_#(js:em.security.clipboard.empty)");
    });

    it("should return cannot paste here message when clipboard has content but context does not match", () => {
      mockClipboardService.canPaste.mockReturnValue(false);
      mockClipboardService.hasContent.mockReturnValue(true);
      mockClipboardService.copiedCount.mockReturnValue(0);
      expect(component.pasteTooltip).toBe("_#(js:em.security.clipboard.cannotPasteHere)");
    });

    it("should return no matching identities message when canPaste is true but pasteCount is 0", () => {
      mockClipboardService.canPaste.mockReturnValue(true);
      mockClipboardService.copiedCount.mockReturnValue(0);
      expect(component.pasteTooltip).toBe("_#(js:em.security.clipboard.noMatchingIdentities)");
    });

    it("should return empty string when paste is available", () => {
      mockClipboardService.canPaste.mockReturnValue(true);
      mockClipboardService.copiedCount.mockReturnValue(3);
      expect(component.pasteTooltip).toBe("");
    });
  });

  describe("paste button visibility", () => {
    it("should show paste button when only pasteContexts is set (no copyPasteContext)", () => {
      component.showCopyPaste = true;
      component.copyPasteContext = null;
      component.pasteContexts = COPY_PASTE_CONTEXT_IDENTITY_ROLES;
      fixture.detectChanges();

      const pasteButton = fixture.debugElement.query(By.css("button[mat-stroked-button]"));
      expect(pasteButton).toBeTruthy();
    });

    it("should not show paste button when both copyPasteContext and pasteContexts are null", () => {
      component.showCopyPaste = true;
      component.copyPasteContext = null;
      component.pasteContexts = null;
      fixture.detectChanges();

      const pasteButton = fixture.debugElement.query(By.css("button[mat-stroked-button]"));
      expect(pasteButton).toBeNull();
    });
  });

  describe("pasteBadgeLabel", () => {
    it("should return empty string when pasteCount is 0", () => {
      mockClipboardService.copiedCount.mockReturnValue(0);
      expect(component.pasteBadgeLabel).toBe("");
    });

    it("should return '(N)' when count equals total", () => {
      mockClipboardService.copiedCount.mockReturnValue(3);
      mockClipboardService.copiedTotal.mockReturnValue(3);
      expect(component.pasteBadgeLabel).toBe(" (3)");
    });

    it("should return '(N of M)' when typeFilter reduces the count below total", () => {
      mockClipboardService.copiedCount.mockReturnValue(2);
      mockClipboardService.copiedTotal.mockReturnValue(5);
      expect(component.pasteBadgeLabel).toBe(" (2 of 5)");
    });
  });
});
