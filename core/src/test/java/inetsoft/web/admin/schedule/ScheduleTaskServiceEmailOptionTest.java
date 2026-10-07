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
package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.ResourceType;
import inetsoft.test.*;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.GeneralActionModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77967: a user without the emailDelivery or notificationEmail schedule option can't
 * change the stored email settings of an action. Every stored field the user may not edit is
 * kept on a save, including the attachment password, the email credential and the link URI,
 * whatever the request sends for them. Each body is the model the editor gets from the real
 * ScheduleService.getActionModel, sent back unchanged or with one field changed, and converted
 * and sanitized the way ScheduleTaskService.applyContent does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskServiceEmailOptionTest {
   @BeforeEach
   void setUp() {
      scheduleService = spy(new ScheduleService(null, null, null, new ScheduleConditionService(), null,
                                                mock(DeployService.class), null, null, null,
                                                null, null, null, null));
      doReturn(true).when(scheduleService)
         .checkPermission(any(), eq(ResourceType.SCHEDULE_OPTION), anyString());
      service = new ScheduleTaskService(null, null, scheduleService, null, null, null, null);
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn("user~;~org1");
   }

   // ── emailDelivery ─────────────────────────────────────────────────────────

   @Test
   void noEmailDelivery_unchangedBody_keepsPasswordAndRefreshesLink() throws Exception {
      deny("emailDelivery");
      ViewsheetAction saved = save(passwordAction(), m -> m);

      assertEquals("a@example.com", saved.getEmails());
      assertEquals(PASSWORD, saved.getPassword());
      assertEquals(LINK, saved.getLinkURI());
   }

   @Test
   void noEmailDelivery_unchangedBody_keepsCredential() throws Exception {
      deny("emailDelivery");
      ViewsheetAction saved = save(credentialAction(), m -> m);

      assertTrue(saved.isUseCredential());
      assertEquals(SECRET_ID, saved.getSecretId());
   }

   @Test
   void noEmailDelivery_deliveryTurnedOff_keepsPasswordAndLink() throws Exception {
      deny("emailDelivery");
      ViewsheetAction saved = save(passwordAction(), m -> m.deliverEmailsEnabled(false));

      assertEquals("a@example.com", saved.getEmails());
      assertTrue(saved.isCompressFile());
      assertTrue(saved.isDeliverLink());
      assertEquals(PASSWORD, saved.getPassword());
      assertEquals(STORED_LINK, saved.getLinkURI());
   }

   @Test
   void noEmailDelivery_deliveryTurnedOff_keepsCredential() throws Exception {
      deny("emailDelivery");
      ViewsheetAction saved = save(credentialAction(), m -> m.deliverEmailsEnabled(false));

      assertTrue(saved.isUseCredential());
      assertEquals(SECRET_ID, saved.getSecretId());
   }

   @Test
   void noEmailDelivery_passwordChanged_keepsPassword() throws Exception {
      deny("emailDelivery");

      assertEquals(PASSWORD, save(passwordAction(), m -> m.password("known")).getPassword());
      assertEquals(PASSWORD, save(passwordAction(), m -> m.password("")).getPassword());
   }

   @Test
   void noEmailDelivery_credentialTurnedOff_keepsCredential() throws Exception {
      deny("emailDelivery");
      ViewsheetAction saved = save(credentialAction(),
                                   m -> m.useCredential(false).secretId(null).password("known"));

      assertTrue(saved.isUseCredential());
      assertEquals(SECRET_ID, saved.getSecretId());
      assertNull(saved.getPassword());
   }

   @Test
   void noEmailDelivery_passwordTurnedIntoCredential_keepsPassword() throws Exception {
      deny("emailDelivery");
      ViewsheetAction saved = save(passwordAction(),
                                   m -> m.useCredential(true).secretId(SECRET_ID));

      assertFalse(saved.isUseCredential());
      assertNull(saved.getSecretId());
      assertEquals(PASSWORD, saved.getPassword());
   }

   @Test
   void noEmailDelivery_noStoredEmails_clearsPassword() throws Exception {
      deny("emailDelivery");
      ViewsheetAction stored = new ViewsheetAction();
      stored.setViewsheet(SHEET);
      ViewsheetAction saved = save(stored, m -> m.deliverEmailsEnabled(true).to("x@example.com")
         .bundledAsZip(true).password("known"));

      assertNull(saved.getEmails());
      assertFalse(saved.isUseCredential());
      assertNull(saved.getSecretId());
      assertNull(saved.getPassword());
   }

   @Test
   void emailDelivery_passwordCanBeChangedAndCleared() throws Exception {
      assertEquals("new", save(passwordAction(), m -> m.password("new")).getPassword());
      assertEquals("", save(passwordAction(), m -> m.password("")).getPassword());
      assertNull(save(passwordAction(), m -> m.deliverEmailsEnabled(false)).getPassword());
   }

   // ── notificationEmail ────────────────────────────────────────────────────

   @Test
   void noNotificationEmail_unchangedBody_refreshesLink() throws Exception {
      deny("notificationEmail");
      ViewsheetAction saved = save(notificationAction(), m -> m);

      assertTrue(saved.isLink());
      assertEquals(LINK, saved.getLinkURI());
   }

   @Test
   void noNotificationEmail_notificationsTurnedOff_keepsLink() throws Exception {
      deny("notificationEmail");
      ViewsheetAction saved = save(notificationAction(), m -> m.notificationEnabled(false));

      assertEquals("n@example.com", saved.getNotifications());
      assertTrue(saved.isLink());
      assertEquals(STORED_LINK, saved.getLinkURI());
   }

   /**
    * Saves the stored action with the editor's model of it, changed by {@code change}.
    */
   private ViewsheetAction save(ViewsheetAction stored,
                                UnaryOperator<GeneralActionModel.Builder> change)
      throws Exception
   {
      GeneralActionModel model = change.apply(GeneralActionModel.builder().from(
         (GeneralActionModel) scheduleService.getActionModel(stored, principal, true))).build();
      List<ScheduleAction> storedActions = List.of(stored);
      ScheduleAction action = scheduleService.getActionFromModel(
         model, stored, storedActions, principal, LINK);
      service.sanitizeAction(action, stored, principal, storedActions);
      return (ViewsheetAction) action;
   }

   private void deny(String option) {
      doReturn(false).when(scheduleService)
         .checkPermission(any(), eq(ResourceType.SCHEDULE_OPTION), eq(option));
   }

   private static ViewsheetAction passwordAction() {
      ViewsheetAction action = emailAction();
      action.setPassword(PASSWORD);
      return action;
   }

   private static ViewsheetAction credentialAction() {
      ViewsheetAction action = emailAction();
      action.setUseCredential(true);
      action.setSecretId(SECRET_ID);
      return action;
   }

   private static ViewsheetAction emailAction() {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(SHEET);
      action.setEmails("a@example.com");
      action.setFileFormat("PDF");
      action.setCompressFile(true);
      action.setDeliverLink(true);
      action.setLinkURI(STORED_LINK);
      return action;
   }

   private static ViewsheetAction notificationAction() {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(SHEET);
      action.setNotifications("n@example.com");
      action.setLink(true);
      action.setLinkURI(STORED_LINK);
      return action;
   }

   private static final String SHEET = "1^128^__NULL__^Examples/Census^org1";
   private static final String PASSWORD = "zip-secret";
   private static final String SECRET_ID = "secret-1";
   private static final String LINK = "http://new-host/";
   private static final String STORED_LINK = "http://old-host/";
   private ScheduleService scheduleService;
   private ScheduleTaskService service;
   private Principal principal;
}
