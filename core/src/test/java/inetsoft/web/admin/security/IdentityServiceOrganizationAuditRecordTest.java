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
package inetsoft.web.admin.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.IdentityInfoRecord;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80114: an organization id change is recorded in the Identity Info record (description)
 * and in the action record (new id), instead of as a bare modify.
 */
@Tag("core")
class IdentityServiceOrganizationAuditRecordTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class);
      sUtilStatic.when(() -> SUtil.getIdentityInfoRecord(
         any(IdentityID.class), anyInt(), anyString(), any(), anyString()))
         .thenReturn(mock(IdentityInfoRecord.class));
      actionRecord = mock(ActionRecord.class);
      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
   }

   @AfterEach
   void tearDown() {
      sUtilStatic.close();
   }

   @Test
   void idOnlyChange_isModifyWithOldAndNewIdInDescription() throws Exception {
      invoke(model("Acme", "Acme", "b"), new IdentityID("Acme", "a"));

      assertEquals(IdentityInfoRecord.ACTION_TYPE_MODIFY, actionType());
      assertEquals("Change id a to b", description());
      assertEquals(new IdentityID("Acme", "a"), recordId());
      verify(actionRecord).setActionError("new id:b");
   }

   @Test
   void unchangedOrBlankId_isPlainModify() throws Exception {
      for(String id : new String[] { "a", "", null }) {
         sUtilStatic.clearInvocations();
         invoke(model("Acme", "Acme", id), new IdentityID("Acme", "a"));

         assertEquals(IdentityInfoRecord.ACTION_TYPE_MODIFY, actionType());
         assertNull(description());
      }

      verify(actionRecord, never()).setActionError(anyString());
   }

   @Test
   void nameOnlyChange_isUnchangedRename() throws Exception {
      invoke(model("New", "Old", "a"), new IdentityID("Old", "a"));

      assertEquals(IdentityInfoRecord.ACTION_TYPE_RENAME, actionType());
      assertEquals("Rename Old to New", description());
      verify(actionRecord).setActionError("new name:New");
   }

   @Test
   void nameAndIdChange_isOneRenameRecordMentioningBoth() throws Exception {
      invoke(model("New", "Old", "b"), new IdentityID("Old", "a"));

      assertEquals(IdentityInfoRecord.ACTION_TYPE_RENAME, actionType());
      assertEquals("Rename Old to New, change id a to b", description());
      verify(actionRecord).setActionError("new name:New, new id:b");
      sUtilStatic.verify(() -> SUtil.getIdentityInfoRecord(
         any(IdentityID.class), anyInt(), anyString(), any(), anyString()), times(1));
   }

   private void invoke(EditOrganizationPaneModel model, IdentityID oldId) throws Exception {
      AuthenticationProvider provider = mock(AuthenticationProvider.class);
      when(provider.getOrganizationNames()).thenReturn(new String[0]);
      Method m = IdentityService.class.getDeclaredMethod(
         "getIdentityInfoRecord", inetsoft.web.admin.security.user.EntityModel.class, int.class,
         IdentityID.class, AuthenticationProvider.class, ActionRecord.class);
      m.setAccessible(true);
      m.invoke(service, model, Identity.ORGANIZATION, oldId, provider, actionRecord);
   }

   private EditOrganizationPaneModel model(String name, String oldName, String id) {
      EditOrganizationPaneModel model = mock(EditOrganizationPaneModel.class);
      when(model.name()).thenReturn(name);
      when(model.oldName()).thenReturn(oldName);
      when(model.id()).thenReturn(id);
      return model;
   }

   private Object[] last() {
      Object[][] holder = new Object[1][];
      ArgumentCaptor<IdentityID> id = ArgumentCaptor.forClass(IdentityID.class);
      ArgumentCaptor<Integer> type = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> desc = ArgumentCaptor.forClass(String.class);
      sUtilStatic.verify(() -> SUtil.getIdentityInfoRecord(
         id.capture(), type.capture(), action.capture(), desc.capture(), anyString()),
                         atLeastOnce());
      return new Object[] { id.getValue(), action.getValue(), desc.getValue() };
   }

   private IdentityID recordId() {
      return (IdentityID) last()[0];
   }

   private String actionType() {
      return (String) last()[1];
   }

   private String description() {
      return (String) last()[2];
   }

   private IdentityService service;
   private ActionRecord actionRecord;
   private MockedStatic<SUtil> sUtilStatic;
}
