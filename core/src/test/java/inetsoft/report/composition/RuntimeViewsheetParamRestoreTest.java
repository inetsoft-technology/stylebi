/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.report.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

/**
 * Regression test for Bug #78233: a viewsheet opened with URL parameters lost them after a
 * {@code RuntimeViewsheet#saveState}/restore round trip -- the same round trip
 * {@link RuntimeSheetCache#toSheet} performs on a cluster failover cache miss.
 *
 * <p>Before the fix, {@code RuntimeViewsheet.saveState()} persisted the dead
 * {@code RuntimeViewsheet.vars} field (never populated by the viewer open/reconnect flow)
 * instead of the live {@code AssetQuerySandbox} variable table that
 * {@code CoreLifecycleService} actually writes URL parameters into, and the restoring
 * constructor built a brand-new, empty sandbox without copying any restored values into it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                       initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = { "EmbeddedVS1.zip" })
@Tag("core")
@Tag("integration")
public class RuntimeViewsheetParamRestoreTest {
   @Test
   void urlParameterSurvivesSaveStateRestoreRoundTrip() throws Exception {
      RuntimeViewsheet rvs1 = vs1Resource.getRuntimeViewsheet();

      // Confirms the parameter actually landed in the live sandbox variable table, the same
      // one CoreLifecycleService.doHandleOpenedSheet() writes URL parameters into.
      Assertions.assertEquals("abc",
         rvs1.getViewsheetSandbox().orElseThrow().getVariableTable().get("pStr"));

      // Simulate the RuntimeSheetCache.toSheet() path a surviving node takes after an owner
      // node dies: saveState() -> compress/decompress (a lossless wrapper around the same
      // ObjectMapper round trip) -> the deserializing constructor.
      ObjectMapper mapper = RuntimeSheetCache.createObjectMapper();
      RuntimeViewsheet rvs2 = new RuntimeViewsheet(rvs1.saveState(mapper), mapper);

      Assertions.assertNotSame(rvs1, rvs2);
      Assertions.assertEquals("abc",
         rvs2.getViewsheetSandbox().orElseThrow().getVariableTable().get("pStr"),
         "Bug #78233: URL parameter should survive a saveState/restore round trip " +
         "(simulated cluster failover)");
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent(String assetId) {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(assetId);
      event.setViewer(true);
      Map<String, String[]> parameters = new HashMap<>();
      parameters.put("pStr", new String[] { "abc" });
      event.setParameters(parameters);
      return event;
   }

   @RegisterExtension
   @Order(1)
   RuntimeViewsheetExtension vs1Resource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(VS1_ASSET_ID));

   private static final String VS1_ASSET_ID = "1^128^__NULL__^EmbeddedVS1^host-org";
}
