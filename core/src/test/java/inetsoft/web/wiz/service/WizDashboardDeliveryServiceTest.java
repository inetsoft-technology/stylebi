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
package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.web.share.ShareConfig;
import inetsoft.web.share.ShareController;
import inetsoft.web.share.ShareMessage;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.dialog.EmailDialogServiceProxy;
import inetsoft.web.viewsheet.model.dialog.EmailDialogModel;
import inetsoft.web.viewsheet.model.dialog.EmailPaneModel;
import inetsoft.web.viewsheet.model.dialog.FileFormatPaneModel;
import inetsoft.web.viewsheet.model.dialog.MessageDialogModel;
import inetsoft.web.wiz.service.WizDashboardDeliveryService.EmailRequest;
import inetsoft.web.wiz.service.WizDashboardDeliveryService.ShareRequest;
import inetsoft.uql.util.XSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #82413: email and share a saved dashboard by path, without a paired session. Pins the
 * defaults (format, bookmark, layout), the permission gates that run before anything is opened or
 * sent, and that the runtime opened for the call is always closed.
 */
@Tag("core")
class WizDashboardDeliveryServiceTest {
   private MockedStatic<XSessionService> sessionStatic;

   /** A principal registers itself with the session service when it is built. */
   @BeforeEach
   void mockSessionService() {
      sessionStatic = mockStatic(XSessionService.class);
      sessionStatic.when(XSessionService::getService).thenReturn(mock(XSessionService.class));
   }

   @AfterEach
   void closeSessionService() {
      sessionStatic.close();
   }

   private static final String ORG = "org1";
   private static final String[] FORMATS = {
      FileFormatInfo.EXPORT_NAME_EXCEL, FileFormatInfo.EXPORT_NAME_POWERPOINT,
      FileFormatInfo.EXPORT_NAME_PDF, FileFormatInfo.EXPORT_NAME_SNAPSHOT,
      FileFormatInfo.EXPORT_NAME_CSV };

   @Test
   void emailsWithTheDialogDefaultsWhenFormatAndBookmarkAreOmitted() throws Exception {
      Fixture f = new Fixture(true);

      {
         WizDashboardDeliveryService.EmailResult r = f.service.email(
            request(List.of("a@x.com", " b@x.com "), null, null), f.wiz, "http://h/sree/");

         assertTrue(r.sent());
         assertEquals(FileFormatInfo.EXPORT_NAME_EXCEL, r.format());
         assertTrue(r.bookmarks().isEmpty());
         EmailDialogModel sent = f.sentModel();
         assertEquals(FileFormatInfo.EXPORT_TYPE_EXCEL, sent.fileFormatPaneModel().formatType());
         assertEquals(0, sent.fileFormatPaneModel().selectedBookmarks().length);
         assertFalse(Boolean.TRUE.equals(sent.fileFormatPaneModel().sendLink()));
         assertEquals("a@x.com,b@x.com", sent.emailPaneModel().toAddress());
         assertEquals("me@x.com", sent.emailPaneModel().fromAddress());
         verify(f.viewsheets).closeViewsheet("rt1", f.wiz);
      }
   }

   @Test
   void aNamedFormatIsMatchedIgnoringCaseAndSnapshotIsNotOffered() throws Exception {
      Fixture f = new Fixture(true);

      {
         assertEquals(FileFormatInfo.EXPORT_NAME_PDF,
                      f.service.email(request(List.of("a@x.com"), "pdf", null), f.wiz, "u/")
                         .format());
         assertEquals(FileFormatInfo.EXPORT_TYPE_PDF,
                      f.sentModel().fileFormatPaneModel().formatType());

         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> f.service.email(request(List.of("a@x.com"), "Snapshot", null), f.wiz, "u/"));
         assertTrue(e.getMessage().contains("format"));
         assertFalse(e.getMessage().contains("Snapshot,"));
      }
   }

   @Test
   void theDefaultFormatIsTheFirstOfferedWhenExcelIsNotOffered() {
      assertEquals(FileFormatInfo.EXPORT_NAME_PDF,
                   WizDashboardDeliveryService.defaultFormat(List.of("PDF", "CSV")));
      assertEquals(FileFormatInfo.EXPORT_NAME_EXCEL,
                   WizDashboardDeliveryService.defaultFormat(List.of("PDF", "Excel")));
      assertThrows(IllegalStateException.class,
                   () -> WizDashboardDeliveryService.defaultFormat(List.of()));
   }

   @Test
   void aBookmarkMustBeOneTheDashboardOffersTheUser() throws Exception {
      Fixture f = new Fixture(true);

      {
         assertEquals(List.of("Q1"), f.service.email(
            request(List.of("a@x.com"), null, List.of("Q1")), f.wiz, "u/").bookmarks());
         assertArrayEquals(new String[] { "Q1" },
                           f.sentModel().fileFormatPaneModel().selectedBookmarks());

         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> f.service.email(request(List.of("a@x.com"), null, List.of("Nope")), f.wiz,
                                  "u/"));
         assertTrue(e.getMessage().contains("bookmarks"));
         assertTrue(e.getMessage().contains("Q1"));
      }
   }

   @Test
   void theDialogLayoutDefaultIsKeptOnlyForAUserWhoMayExpandComponents() throws Exception {
      Fixture allowed = new Fixture(true);
      Fixture denied = new Fixture(true);
      // the real user lacks ExportExpandComponents; the wiz principal would be approved for it
      when(denied.engine.checkPermission(any(), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION),
                                         eq("ExportExpandComponents"), any(ResourceAction.class)))
         .thenAnswer(inv -> SRPrincipal.isWizPrincipal(inv.getArgument(0)));

      {
         allowed.service.email(request(List.of("a@x.com"), null, null), allowed.wiz, "u/");
         assertFalse(allowed.sentModel().fileFormatPaneModel().matchLayout());
         denied.service.email(request(List.of("a@x.com"), null, null), denied.wiz, "u/");
         assertTrue(denied.sentModel().fileFormatPaneModel().matchLayout());
         assertFalse(denied.sentModel().fileFormatPaneModel().expandSelections());
         assertFalse(denied.sentModel().fileFormatPaneModel().onlyDataComponents());
      }
   }

   @Test
   void aUserWithoutTheEmailActionIsRefusedBeforeAnythingIsOpened() throws Exception {
      Fixture f = new Fixture(true);
      when(f.engine.checkPermission(any(), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION), eq("Email"),
                                    any(ResourceAction.class)))
         .thenAnswer(inv -> SRPrincipal.isWizPrincipal(inv.getArgument(0)));

      {
         assertThrows(SecurityException.class,
                      () -> f.service.email(request(List.of("a@x.com"), null, null), f.wiz, "u/"));
         assertThrows(SecurityException.class,
                      () -> f.service.getEmailOptions("Sales/Overview", null, f.wiz));
      }

      verify(f.viewsheets, never()).openViewsheet(any(AssetEntry.class), any(), anyBoolean());
      verifyNoInteractions(f.email);
   }

   @Test
   void anUnreadableDashboardIsNeverOpenedOrMailed() throws Exception {
      Fixture f = new Fixture(false);

      {
         assertThrows(IllegalArgumentException.class,
                      () -> f.service.email(request(List.of("a@x.com"), null, null), f.wiz, "u/"));
      }

      verify(f.viewsheets, never()).openViewsheet(any(AssetEntry.class), any(), anyBoolean());
      verifyNoInteractions(f.email);
   }

   @Test
   void theRuntimeIsClosedWhenTheEmailServiceFails() throws Exception {
      Fixture f = new Fixture(true);
      when(f.email.emailViewsheet(any(), any(), any(), any())).thenThrow(new Exception("smtp"));

      {
         assertThrows(Exception.class,
                      () -> f.service.email(request(List.of("a@x.com"), null, null), f.wiz, "u/"));
      }

      verify(f.viewsheets).closeViewsheet("rt1", f.wiz);
   }

   @Test
   void aServerRefusalIsReportedAsNotSent() throws Exception {
      Fixture f = new Fixture(true);
      when(f.email.emailViewsheet(any(), any(), any(), any())).thenReturn(
         MessageDialogModel.builder().type(MessageCommand.Type.ERROR).success(false)
            .message("bad domain").build());

      {
         WizDashboardDeliveryService.EmailResult r = f.service.email(
            request(List.of("a@x.com"), null, null), f.wiz, "u/");
         assertFalse(r.sent());
         assertEquals("bad domain", r.message());
      }
   }

   @Test
   void recipientsAreRequiredAndTrimmed() {
      assertThrows(IllegalArgumentException.class,
                   () -> WizDashboardDeliveryService.joinAddresses(null, "to", true));
      assertThrows(IllegalArgumentException.class,
                   () -> WizDashboardDeliveryService.joinAddresses(List.of(" ", ""), "to", true));
      assertEquals("", WizDashboardDeliveryService.joinAddresses(null, "cc", false));
      assertEquals("a@x.com,b@x.com", WizDashboardDeliveryService.joinAddresses(
         java.util.Arrays.asList(" a@x.com", null, "b@x.com "), "to", true));
   }

   @Test
   void shareToEmailSendsTheViewerLinkAsTheRealUser() throws Exception {
      Fixture f = new Fixture(true);

      f.service.share(share("EMAIL", "hello", List.of("a@x.com")), f.wiz, "http://h/sree/");

      ArgumentCaptor<ShareMessage> message = ArgumentCaptor.forClass(ShareMessage.class);
      ArgumentCaptor<Principal> principal = ArgumentCaptor.forClass(Principal.class);
      verify(f.shares).sendEmailMessage(message.capture(), principal.capture());
      assertEquals("http://h/sree/app/viewer/view/global/Sales/Overview", message.getValue().link());
      assertEquals(List.of("a@x.com"), message.getValue().recipients());
      assertFalse(SRPrincipal.isWizPrincipal(principal.getValue()));
   }

   @Test
   void onlyTheServerSideTargetsAreShared() throws Exception {
      Fixture f = new Fixture(true);

      for(String target : new String[] { "link", "facebook", "linkedin", "twitter", "" }) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> f.service.share(share(target, "hi", List.of("a@x.com")), f.wiz, "u/"));
         assertTrue(e.getMessage().contains("target"));
      }

      f.service.share(share("slack", "hi", null), f.wiz, "u/");
      f.service.share(share("Google-Chat", "hi", null), f.wiz, "u/");
      verify(f.shares).sendSlackMessage(any(), any());
      verify(f.shares).sendGoogleChatMessage(any(), any());
   }

   @Test
   void aShareTargetTheUserMayNotUseIsRefusedBeforeSending() throws Exception {
      Fixture f = new Fixture(true);
      when(f.engine.checkPermission(any(), eq(ResourceType.SHARE), anyString(),
                                    any(ResourceAction.class)))
         .thenAnswer(inv -> SRPrincipal.isWizPrincipal(inv.getArgument(0)));

      assertThrows(SecurityException.class,
                   () -> f.service.share(share("slack", "hi", null), f.wiz, "u/"));
      verifyNoInteractions(f.shares);
   }

   @Test
   void shareNeedsAMessageAndEmailNeedsRecipients() throws Exception {
      Fixture f = new Fixture(true);

      assertThrows(IllegalArgumentException.class,
                   () -> f.service.share(share("slack", " ", null), f.wiz, "u/"));
      assertThrows(IllegalArgumentException.class,
                   () -> f.service.share(share("email", "hi", List.of()), f.wiz, "u/"));
      verifyNoInteractions(f.shares);
   }

   @Test
   void shareOptionsReportTheEnabledServerTargetsAndTheLink() throws Exception {
      Fixture f = new Fixture(true);
      when(f.shares.getConfig(any(), any())).thenReturn(ShareConfig.builder()
         .emailEnabled(true).slackEnabled(false).googleChatEnabled(true).facebookEnabled(true)
         .linkedinEnabled(true).twitterEnabled(true).linkEnabled(true).build());

      WizDashboardDeliveryService.ShareOptions o =
         f.service.getShareOptions("Sales/Overview", null, f.wiz, "http://h/sree/");

      assertEquals(java.util.Map.of("email", true, "slack", false, "google-chat", true),
                   o.targets());
      assertEquals("http://h/sree/app/viewer/view/global/Sales/Overview", o.link());
      assertNull(f.service.getShareOptions(null, null, f.wiz, "u/").link());
   }

   @Test
   void theViewerLinkNamesTheUserTreeForAPrivateDashboard() {
      AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "Mine/My VS", new IdentityID("bob", ORG), ORG);

      assertEquals("http://h/sree/app/viewer/view/user/bob/Mine/My%20VS",
                   WizDashboardDeliveryService.viewerLink(entry, "http://h/sree/"));
   }

   private static EmailRequest request(List<String> to, String format, List<String> bookmarks) {
      return new EmailRequest("Sales/Overview", null, to, null, null, "subj", "body", format,
                              bookmarks);
   }

   private static ShareRequest share(String target, String message, List<String> recipients) {
      return new ShareRequest("Sales/Overview", null, target, message, "s", recipients, null,
                              null);
   }

   private static final class Fixture {
      final SecurityEngine engine = mock(SecurityEngine.class);
      final AssetRepository repository = mock(AssetRepository.class);
      final ViewsheetService viewsheets = mock(ViewsheetService.class);
      final EmailDialogServiceProxy email = mock(EmailDialogServiceProxy.class);
      final ShareController shares = mock(ShareController.class);
      final WizDashboardAccess access = new WizDashboardAccess(repository, engine);
      final WizDashboardDeliveryService service =
         new WizDashboardDeliveryService(access, viewsheets, email, shares, () -> FORMATS);
      final SRPrincipal wiz = new SRPrincipal(new IdentityID("bob", ORG), new IdentityID[0],
                                              new String[0], ORG, 42L);

      Fixture(boolean readable) {
         wiz.setProperty("wiz", "true");

         try {
            // every check is approved for the wiz principal and for the real user, until a test
            // narrows one
            when(engine.checkPermission(any(), any(ResourceType.class), anyString(),
                                        any(ResourceAction.class))).thenReturn(true);
            when(repository.containsEntry(any(AssetEntry.class))).thenReturn(readable);
            when(viewsheets.openViewsheet(any(AssetEntry.class), any(), anyBoolean()))
               .thenReturn("rt1");
            FileFormatPaneModel file = FileFormatPaneModel.builder()
               .expandEnabled(true).hasPrintLayout(false).matchLayout(false)
               .allBookmarks(new String[] { "Q1", "(Home)" })
               .allBookmarkLabels(new String[] { "Q1", "(Home)" }).build();
            EmailPaneModel pane = EmailPaneModel.builder()
               .fromAddressEnabled(false).fromAddress("me@x.com").fonts(List.of()).build();
            when(email.getEmailDialogModel(anyString(), any())).thenReturn(
               EmailDialogModel.builder().fileFormatPaneModel(file).emailPaneModel(pane).build());
            when(email.emailViewsheet(anyString(), any(), any(), any())).thenReturn(
               MessageDialogModel.builder().type(MessageCommand.Type.INFO).success(true)
                  .message("ok").build());
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      EmailDialogModel sentModel() throws Exception {
         ArgumentCaptor<EmailDialogModel> model = ArgumentCaptor.forClass(EmailDialogModel.class);
         verify(email, atLeastOnce()).emailViewsheet(anyString(), model.capture(), any(), any());
         return model.getValue();
      }
   }
}
