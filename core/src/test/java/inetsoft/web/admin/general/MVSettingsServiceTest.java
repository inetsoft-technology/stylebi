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
package inetsoft.web.admin.general;

import inetsoft.mv.MVManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.web.admin.general.model.MVSettingsModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The Materialized View page of the general settings ({@link MVSettingsService}): the options are
 * the properties {@code mv.ondemand}, {@code mv.enabled.all}, {@code mv.metadata} and
 * {@code mv.required}, and the default cycle of the data cycle manager and the MV manager.
 * {@code mv.enabled.all} is the default of the on-demand option of a new viewsheet.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVSettingsServiceTest {
   private static final String[] PROPERTIES = {
      "mv.ondemand", "mv.enabled.all", "mv.metadata", "mv.required" };

   @BeforeEach
   void setUp() {
      for(String name : PROPERTIES) {
         saved.put(name, SreeEnv.getProperty(name));
         SreeEnv.remove(name);
      }

      security = mock(SecurityEngine.class);
      mvManager = mock(MVManager.class);
      cycles = mock(DataCycleManager.class);
      service = new MVSettingsService(security, mvManager, cycles);
      admin = new XPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()));
   }

   @AfterEach
   void tearDown() {
      for(Map.Entry<String, String> entry : saved.entrySet()) {
         if(entry.getValue() == null) {
            SreeEnv.remove(entry.getKey());
         }
         else {
            SreeEnv.setProperty(entry.getKey(), entry.getValue());
         }
      }
   }

   @Test
   void everyOptionIsWritten() throws Exception {
      service.setModel(model(true, false, true, false, "Daily"), admin);

      assertEquals("true", SreeEnv.getProperty("mv.ondemand"));
      assertEquals("false", SreeEnv.getProperty("mv.enabled.all"));
      assertEquals("true", SreeEnv.getProperty("mv.metadata"));
      assertEquals("false", SreeEnv.getProperty("mv.required"));
      verify(cycles).setDefaultCycle("Daily");
      verify(cycles).save();
      verify(mvManager).setDefaultCycle("Daily");

      service.setModel(model(false, true, false, true, ""), admin);

      assertEquals("false", SreeEnv.getProperty("mv.ondemand"));
      assertEquals("true", SreeEnv.getProperty("mv.enabled.all"));
      assertEquals("false", SreeEnv.getProperty("mv.metadata"));
      assertEquals("true", SreeEnv.getProperty("mv.required"));
      verify(mvManager).setDefaultCycle("");
   }

   @Test
   void everyOptionIsReadBack() throws Exception {
      SreeEnv.setProperty("mv.ondemand", "true");
      SreeEnv.setProperty("mv.enabled.all", "false");
      SreeEnv.setProperty("mv.metadata", "true");
      SreeEnv.setProperty("mv.required", "false");
      when(mvManager.getDefaultCycle()).thenReturn("Daily");
      noCycles();

      MVSettingsModel model = service.getModel(admin);

      assertTrue(model.onDemand());
      assertFalse(model.onDemandDefault());
      assertTrue(model.metadata());
      assertFalse(model.required());
      assertEquals("Daily", model.defaultCycle());

      SreeEnv.setProperty("mv.ondemand", "false");
      SreeEnv.setProperty("mv.enabled.all", "true");
      SreeEnv.setProperty("mv.metadata", "false");
      SreeEnv.setProperty("mv.required", "true");
      model = service.getModel(admin);

      assertFalse(model.onDemand());
      assertTrue(model.onDemandDefault());
      assertFalse(model.metadata());
      assertTrue(model.required());
   }

   @Test
   void optionsThatAreNotSetAreOff() throws Exception {
      noCycles();

      MVSettingsModel model = service.getModel(admin);

      assertFalse(model.onDemand());
      assertFalse(model.onDemandDefault());
      assertFalse(model.metadata());
      assertFalse(model.required());
      // no default cycle is shown as none
      assertEquals("", model.defaultCycle());
   }

   @Test
   void onlyTheCyclesTheUserMayUseAreListed() throws Exception {
      when(cycles.getDataCycles(anyString()))
         .thenAnswer(inv -> Collections.enumeration(List.of("Weekly", "Daily", "Hourly")));
      String org = Organization.getDefaultOrganizationID();
      when(security.checkPermission(eq(admin), eq(ResourceType.SCHEDULE_CYCLE), anyString(),
                                    eq(ResourceAction.ACCESS)))
         .thenAnswer(inv -> !new IdentityID("Hourly", org).convertToKey()
            .equals(inv.getArgument(2)));

      MVSettingsModel model = service.getModel(admin);

      // sorted by name
      assertEquals(List.of("Daily", "Weekly"), model.cycles());
   }

   @Test
   void enabledAllIsTheDefaultOfANewViewsheet() {
      SreeEnv.setProperty("mv.enabled.all", "true");
      assertTrue(new ViewsheetInfo().isMVOnDemand());

      SreeEnv.setProperty("mv.enabled.all", "false");
      assertFalse(new ViewsheetInfo().isMVOnDemand());

      SreeEnv.remove("mv.enabled.all");
      assertFalse(new ViewsheetInfo().isMVOnDemand());
   }

   private void noCycles() {
      when(cycles.getDataCycles(anyString())).thenReturn(Collections.emptyEnumeration());
   }

   private static MVSettingsModel model(boolean onDemand, boolean onDemandDefault,
                                        boolean metadata, boolean required, String cycle)
   {
      return MVSettingsModel.builder()
         .onDemand(onDemand)
         .onDemandDefault(onDemandDefault)
         .metadata(metadata)
         .required(required)
         .defaultCycle(cycle)
         .build();
   }

   private final Map<String, String> saved = new HashMap<>();
   private SecurityEngine security;
   private MVManager mvManager;
   private DataCycleManager cycles;
   private MVSettingsService service;
   private Principal admin;
}
