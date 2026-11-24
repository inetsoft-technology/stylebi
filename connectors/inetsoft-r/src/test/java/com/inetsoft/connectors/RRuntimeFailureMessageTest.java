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

import inetsoft.uql.VariableTable;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #82215: every failed R query must record why it failed, instead of leaving the caller
 * with nothing but a generic "check the datasource credentials" message.
 */
class RRuntimeFailureMessageTest {
   @BeforeAll
   static void setSreeHome() {
      File home = new File("target/sreeHome");

      if(!home.isDirectory() && !home.mkdirs()) {
         throw new RuntimeException("Failed to create test sree.home: " + home);
      }

      System.setProperty("sree.home", home.getAbsolutePath());
   }

   @BeforeEach
   @AfterEach
   void clearMessages() {
      Tool.clearUserMessage();
   }

   @Test
   void rErrorTextIsReportedForAFailedEval() throws Exception {
      RConnection connection = mock(RConnection.class);
      when(connection.eval("stop('custom failure')")).thenThrow(
         new RserveException(null, "eval failed, request status: error code: 127"));
      when(connection.eval("geterrmessage()")).thenReturn(
         new REXPString("Error: custom failure \n"));

      String message = run(connection, "stop('custom failure')");

      assertTrue(message.contains("custom failure"), message);
      assertFalse(message.contains("error code"), message);
      assertFalse(message.contains("credentials"), message);
      verify(connection).close();
   }

   // transferTable wraps the R failure of the string-result path in a RuntimeException
   @Test
   void rErrorTextIsReportedThroughTheTransferWrapper() throws Exception {
      RConnection connection = mock(RConnection.class);
      when(connection.eval("geterrmessage()")).thenReturn(
         new REXPString("Error in write.csv(nosuchvar) : object 'nosuchvar' not found"));
      Exception wrapped = new RuntimeException("Failed to transfer table 'nosuchvar' from R server",
         new RserveException(null, "eval failed, request status: error code: 127"));

      String message = RRuntime.describeFailure(wrapped, connection, null);

      assertTrue(message.contains("object 'nosuchvar' not found"), message);
      assertFalse(message.contains("Failed to transfer"), message);
   }

   @Test
   void parseErrorTextFromTheExceptionIsKeptWithoutAskingR() throws Exception {
      RConnection connection = mock(RConnection.class);
      when(connection.eval("x <-")).thenThrow(
         new RserveException(null, "eval failed, request status: R parser: input incomplete"));

      String message = run(connection, "x <-");

      assertTrue(message.contains("input incomplete"), message);
      verify(connection, never()).eval("geterrmessage()");
   }

   @Test
   void fallsBackToTheExceptionWhenGeterrmessageFails() throws Exception {
      RConnection connection = mock(RConnection.class);
      when(connection.eval("quit()")).thenThrow(
         new RserveException(null, "eval failed, request status: error code: 127"));
      when(connection.eval("geterrmessage()")).thenThrow(
         new RserveException(null, "eval failed, connection lost"));

      String message = run(connection, "quit()");

      assertTrue(message.contains("error code: 127"), message);
   }

   @Test
   void fallsBackWhenGeterrmessageReturnsNothingUsable() throws Exception {
      RConnection connection = mock(RConnection.class);
      when(connection.eval("bad()")).thenThrow(
         new RserveException(null, "eval failed, request status: error code: 127"));
      when(connection.eval("geterrmessage()")).thenReturn(null);

      String message = run(connection, "bad()");

      assertTrue(message.contains("error code: 127"), message);
   }

   @Test
   void notADataFrameResultIsExplained() throws Exception {
      RConnection connection = mock(RConnection.class);
      when(connection.eval("1:5")).thenReturn(new REXPInteger(new int[] { 1, 2, 3, 4, 5 }));

      String message = run(connection, "1:5");

      assertTrue(message.matches("(?i).*data[ .]?frame.*"), message);
      assertFalse(message.contains("credentials"), message);
   }

   @Test
   void loginFailureNamesTheServerNotThePassword() {
      RRuntime runtime = new RRuntime() {
         @Override
         public RConnection createConnection(RDataSource dataSource) throws Exception {
            throw new RserveException(null, "login failed, request status: authorization failed");
         }
      };
      RQuery query = new RQuery();
      query.setScript("df");

      assertNull(runtime.runQuery(query, new VariableTable()));
      String message = Tool.getUserMessage().getMessage();

      assertTrue(message.contains("log in"), message);
      assertTrue(message.contains("authorization failed"), message);
      assertTrue(message.contains("user and password"), message);
   }

   @Test
   void connectFailureIsReportedWithoutLoginAdvice() {
      RRuntime runtime = new RRuntime() {
         @Override
         public RConnection createConnection(RDataSource dataSource) throws Exception {
            throw new RserveException(null, "Cannot connect: Connection refused");
         }
      };
      RQuery query = new RQuery();
      query.setScript("df");

      assertNull(runtime.runQuery(query, new VariableTable()));
      String message = Tool.getUserMessage().getMessage();

      assertTrue(message.contains("Could not connect"), message);
      assertTrue(message.contains("Connection refused"), message);
      assertFalse(message.contains("password"), message);
   }

   @Test
   void blankScriptIsRefusedBeforeConnecting() {
      boolean[] connected = { false };
      RRuntime runtime = new RRuntime() {
         @Override
         public RConnection createConnection(RDataSource dataSource) {
            connected[0] = true;
            return mock(RConnection.class);
         }
      };

      for(String script : new String[] { null, "", "  \n " }) {
         Tool.clearUserMessage();
         RQuery query = new RQuery();
         query.setScript(script);

         assertNull(runtime.runQuery(query, new VariableTable()));
         assertTrue(Tool.getUserMessage().getMessage().contains("no script"));
      }

      assertFalse(connected[0]);
   }

   @Test
   void messageIsSingleLineTruncatedAndNeverEchoesTheScript() throws Exception {
      String script = "secret_marker <- 'password=hunter2'; stop('x')";
      RConnection connection = mock(RConnection.class);
      when(connection.eval(script)).thenThrow(
         new RserveException(null, "eval failed, request status: error code: 127"));
      when(connection.eval("geterrmessage()")).thenReturn(
         new REXPString("Error: line1\nline2 " + "y".repeat(1000)));

      String message = run(connection, script);

      assertFalse(message.contains("\n"), message);
      assertTrue(message.contains("line1 line2"), message);
      assertTrue(message.length() <= 310, "length " + message.length());
      assertFalse(message.contains("hunter2"), message);
      assertFalse(message.contains("secret_marker"), message);
   }

   private static String run(RConnection connection, String script) {
      RRuntime runtime = new RRuntime() {
         @Override
         public RConnection createConnection(RDataSource dataSource) {
            return connection;
         }
      };
      RQuery query = new RQuery();
      query.setScript(script);

      assertNull(runtime.runQuery(query, new VariableTable()));
      // reading the message clears it, so read it once
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message, "a user message must be recorded");
      return message.getMessage();
   }
}
