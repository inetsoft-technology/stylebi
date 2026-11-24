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
package com.inetsoft.connectors;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.SlotClaim;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rosuda.REngine.Rserve.RConnection;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test cases for <tt>RRuntime</tt>.
 */
class RRuntimeTest {
   /**
    * Sets the sree.home property and creates the corresponding directory, if necessary.
    */
   @BeforeAll
   static void setSreeHome() {
      File home = new File("target/sreeHome");

      if(!home.isDirectory() && !home.mkdirs()) {
         throw new RuntimeException("Failed to create test sree.home: " + home);
      }

      System.setProperty("sree.home", home.getAbsolutePath());
   }

   /**
    * Tests the <tt>runQuery()</tt> method for proper operation.
    *
    * @throws AssertionError if the test fails.
    * @throws Exception if an unexepcted error occurs.
    */
   @Test
   void testRunQuery() throws Exception {
   }

   /**
    * Tests the <tt>testDataSource()</tt> method for proper operation.
    *
    * @throws AssertionError if the test fails.
    * @throws Exception if an unexepcted error occurs.
    */
   @Test
   void testTestDataSource() throws Exception {
   }

   /**
    * Bug #76960: on a pooled worksheet env, runQuery holds one span from the pre-script
    * through the R round trip to the post-script, so the pre-script's globals are still there
    * while R runs, and the context is cleaned once, when the span closes. Fails if the span
    * is removed: the pre-script's own claim would then be closed and cleaned before R runs.
    */
   @Test
   void preScriptStateLastsThroughTheRRoundTrip() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getScriptEnv()).thenReturn(env);
      RConnection connection = mock(RConnection.class);
      Object[] duringEval = new Object[2];

      when(connection.eval(anyString())).thenAnswer(invocation -> {
         duringEval[0] = SlotClaim.openClaims();
         duringEval[1] = PoolTestSupport.run(env, "typeof globalThis.rstate");
         throw new IllegalStateException("no R server in this test");
      });

      RRuntime runtime = new RRuntime() {
         @Override
         public RConnection createConnection(RDataSource dataSource) {
            return connection;
         }
      };

      RQuery query = new RQuery();
      query.setPreExecute("globalThis.rstate = 1; 1");
      query.setScript("df");
      WSExecution.setAssetQuerySandbox(box);

      try {
         assertNull(runtime.runQuery(query, new VariableTable()));
      }
      finally {
         WSExecution.setAssetQuerySandbox(null);
      }

      assertEquals(1, duringEval[0], "claims open during the R round trip");
      assertEquals("number", duringEval[1], "the pre-script's global during the R round trip");
      assertEquals(0, SlotClaim.openClaims());
      assertEquals(1, env.getMetrics().getCleans());
      verify(connection).close();
   }

   /**
    * Bug #82218: a frame returned by name must match the one returned by value, so the R
    * write.csv call must not emit row names and must write NA as an empty cell, which the CSV
    * reader loads as null (the literal "NA" would turn a numeric column into text). Only the
    * command sent to R is asserted: loading the CSV into a swappable table needs the Spring
    * context, so the read is cut short after the command is captured.
    */
   @Test
   void testTransferTableByNameDropsRowNamesAndNa() throws Exception {
      RConnection connection = mock(RConnection.class);
      String[] command = new String[1];
      doAnswer(inv -> {
         command[0] = inv.getArgument(0);
         return null;
      }).when(connection).voidEval(anyString());
      when(connection.openFile(anyString())).thenThrow(new RuntimeException("stop after write"));

      inetsoft.util.FileSystemService fs = mock(inetsoft.util.FileSystemService.class);
      when(fs.getCacheTempFile(anyString(), anyString())).thenReturn(new File("target/r82218.csv"));

      try(org.mockito.MockedStatic<inetsoft.util.FileSystemService> st =
             mockStatic(inetsoft.util.FileSystemService.class))
      {
         st.when(inetsoft.util.FileSystemService::getInstance).thenReturn(fs);
         assertThrows(RuntimeException.class, () -> RRuntime.transferTable(connection, "budget_wide"));
      }

      assertEquals(
         "write.csv(budget_wide, '" + command[0].split("'")[1] +
         "', row.names = FALSE, na = \"\")", command[0]);
   }
}
