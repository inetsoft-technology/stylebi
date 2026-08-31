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
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76239: wiz-services sends fieldConfigs measures keyed "nOrP" (bindingSchema.ts:91), but
 * MeasureFieldInfo.getNOrP()/setNOrP() had no @JsonProperty override, so Jackson's default bean
 * mangling expected "norP" instead — the mismatch was silently swallowed by the class hierarchy's
 * @JsonIgnoreProperties(ignoreUnknown = true), leaving getNOrP() null. Deserializes through
 * AutoBindingRequest.fieldConfigs so the fieldType discriminator (SimpleFieldInfo -> MeasureFieldInfo)
 * is exercised, matching the real /viewsheet/autoBinding request shape.
 */
@Tag("core")
public class MeasureFieldInfoNOrPRoundTripTest {
   @Test
   public void nOrPBinds() throws Exception {
      String json = "{\"fieldConfigs\":[{"
         + "\"field\":\"ORDER_VALUE\",\"fieldType\":\"measure\","
         + "\"aggregateFormula\":\"PthPercentile\",\"nOrP\":90}]}";

      ObjectMapper m = new ObjectMapper();
      AutoBindingRequest req = m.readValue(json, AutoBindingRequest.class);

      MeasureFieldInfo field = (MeasureFieldInfo) req.getFieldConfigs().get(0);
      assertEquals(Integer.valueOf(90), field.getNOrP());
   }

   @Test
   public void norPAliasStillBinds() throws Exception {
      // The Jackson-derived name from before this fix; kept working via @JsonAlias so any
      // caller that relied on the old (broken) wire contract does not regress.
      String json = "{\"fieldConfigs\":[{"
         + "\"field\":\"ORDER_VALUE\",\"fieldType\":\"measure\","
         + "\"aggregateFormula\":\"PthPercentile\",\"norP\":90}]}";

      ObjectMapper m = new ObjectMapper();
      AutoBindingRequest req = m.readValue(json, AutoBindingRequest.class);

      MeasureFieldInfo field = (MeasureFieldInfo) req.getFieldConfigs().get(0);
      assertEquals(Integer.valueOf(90), field.getNOrP());
   }

   @Test
   public void secondaryFieldStillRoundTrips() throws Exception {
      // secondaryField is spelled identically on both sides already; must not regress.
      String json = "{\"fieldConfigs\":[{"
         + "\"field\":\"PAID\",\"fieldType\":\"measure\","
         + "\"aggregateFormula\":\"WeightedAverage\",\"secondaryField\":\"QUANTITY\"}]}";

      ObjectMapper m = new ObjectMapper();
      AutoBindingRequest req = m.readValue(json, AutoBindingRequest.class);

      MeasureFieldInfo field = (MeasureFieldInfo) req.getFieldConfigs().get(0);
      assertEquals("QUANTITY", field.getSecondaryField());
   }
}
