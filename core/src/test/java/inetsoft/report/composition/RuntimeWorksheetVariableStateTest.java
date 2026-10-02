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
package inetsoft.report.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Time;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The worksheet variable table survives a failover: the runtime worksheet is saved to its
 * state, the state is written to and read from JSON as the runtime sheet cache does, and the
 * worksheet is rebuilt from it (Bug #77528).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RuntimeWorksheetVariableStateTest {
   @Test
   void variableTableSurvivesStateRoundTrip() throws Exception {
      Time time = new Time(1_790_000_000_123L);
      Timestamp timestamp = new Timestamp(1_790_000_000_000L);
      timestamp.setNanos(123_456_789);
      Time[] times = { new Time(1_790_000_000_456L), null };
      Timestamp mixedTimestamp = new Timestamp(1_790_000_001_000L);
      mixedTimestamp.setNanos(987_654_321);
      Timestamp baseTimestamp = new Timestamp(1_790_000_002_000L);
      baseTimestamp.setNanos(5);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws77528", null);
      XPrincipal user = new XPrincipal(new IdentityID("admin", "host-org"));
      RuntimeWorksheet sheet = new RuntimeWorksheet(entry, new Worksheet(), user);
      VariableTable vars = sheet.getAssetQuerySandbox().getVariableTable();
      vars.put("time", time);
      vars.put("timestamp", timestamp);
      vars.put("times", times);
      vars.put("mixed", new Object[] { new Time(1_790_000_000_789L), mixedTimestamp, "s" });
      vars.put("string", "kept");
      VariableTable base = new VariableTable();
      base.put("baseTimestamp", baseTimestamp);
      base.put("baseTimes", new Time[] { new Time(1_790_000_003_123L) });
      vars.setBaseTable(base);

      RuntimeWorksheetState state = sheet.saveState(mapper);
      assertNotNull(state.getVars());

      // a value that can't be converted, e.g. written by another version
      ObjectNode varsJson = (ObjectNode) mapper.readTree(state.getVars());
      ObjectNode entries = (ObjectNode) varsJson.get("vartable");
      entries.putObject("bad").put("type", "java.lang.Integer").put("value", "abc");
      state.setVars(mapper.writeValueAsString(varsJson));

      // RuntimeSheetCache.compressState/decompressState
      byte[] stored = mapper.writeValueAsBytes(state);
      RuntimeWorksheetState loaded = mapper.readValue(stored, RuntimeWorksheetState.class);
      RuntimeWorksheet restored = new RuntimeWorksheet(loaded, mapper);

      VariableTable result = restored.getAssetQuerySandbox().getVariableTable();
      assertNotNull(result);

      assertTime(time, result.get("time"));
      assertTimestamp(timestamp, result.get("timestamp"));

      Object restoredTimes = result.get("times");
      assertInstanceOf(Time[].class, restoredTimes);
      assertEquals(2, ((Time[]) restoredTimes).length);
      assertTime(times[0], ((Time[]) restoredTimes)[0]);
      assertNull(((Time[]) restoredTimes)[1]);

      Object mixed = result.get("mixed");
      assertInstanceOf(Object[].class, mixed);
      Object[] mixedValues = (Object[]) mixed;
      assertEquals(3, mixedValues.length);
      assertTime(new Time(1_790_000_000_789L), mixedValues[0]);
      assertTimestamp(mixedTimestamp, mixedValues[1]);
      assertEquals("s", mixedValues[2]);

      assertEquals("kept", result.get("string"));
      assertEquals("abc", result.get("bad"));

      VariableTable restoredBase = result.getBaseTable();
      assertNotNull(restoredBase);
      assertTimestamp(baseTimestamp, restoredBase.get("baseTimestamp"));
      Object baseTimes = restoredBase.get("baseTimes");
      assertInstanceOf(Time[].class, baseTimes);
      assertTime(new Time(1_790_000_003_123L), ((Time[]) baseTimes)[0]);
   }

   private static void assertTime(Time expected, Object actual) {
      assertInstanceOf(Time.class, actual);
      assertEquals(expected.getTime(), ((Time) actual).getTime());
   }

   private static void assertTimestamp(Timestamp expected, Object actual) {
      assertInstanceOf(Timestamp.class, actual);
      assertEquals(expected.getTime(), ((Timestamp) actual).getTime());
      assertEquals(expected.getNanos(), ((Timestamp) actual).getNanos());
   }

   private final ObjectMapper mapper = RuntimeSheetCache.createObjectMapper();
}
