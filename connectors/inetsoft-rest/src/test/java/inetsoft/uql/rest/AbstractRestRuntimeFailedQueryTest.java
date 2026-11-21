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
package inetsoft.uql.rest;

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.util.QueryManager;
import inetsoft.util.CancelledException;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82010: a REST query whose read failed (the runner raises a user message and returns no
 * table) must throw instead of returning null, because null is cached by the callers as an empty
 * result and only the first read would warn. A legitimately empty response must still return null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class AbstractRestRuntimeFailedQueryTest {
   @Test
   void failedRunWithUserMessageThrowsTheMessage() {
      final RestJsonQuery query = new RestJsonQuery();

      final RuntimeException ex = assertThrows(RuntimeException.class, () ->
         runtime(query, () -> {
            Tool.addUserMessage("Error executing Rest query: Connection is closed",
                                ConfirmException.INFO);
            return null;
         }).runQuery(query, new VariableTable()));

      assertTrue(ex.getMessage().contains("Error executing Rest query: Connection is closed"),
                 ex.getMessage());
   }

   @Test
   void failedRunWithoutMessageButCompletedReturnsNull() {
      final RestJsonQuery query = new RestJsonQuery();

      final XTableNode result = runtime(query, () -> {
         query.setRunCompleted(true);
         return null;
      }).runQuery(query, new VariableTable());

      assertNull(result);
   }

   @Test
   void emptyResponseWithoutMessageReturnsNull() {
      final RestJsonQuery query = new RestJsonQuery();

      assertNull(runtime(query, () -> null).runQuery(query, new VariableTable()));
   }

   @Test
   void completedRunKeepsReturningNullEvenWithAMessage() {
      final RestJsonQuery query = new RestJsonQuery();

      final XTableNode result = runtime(query, () -> {
         query.setRunCompleted(true);
         Tool.addUserMessage("a warning", ConfirmException.INFO);
         return null;
      }).runQuery(query, new VariableTable());

      assertNull(result);
   }

   @Test
   void partialRowsAreStillReturned() {
      final RestJsonQuery query = new RestJsonQuery();
      final XTableNode partial = org.mockito.Mockito.mock(XTableNode.class);

      final XTableNode result = runtime(query, () -> {
         Tool.addUserMessage("Error executing Rest query: page 2 failed", ConfirmException.INFO);
         return partial;
      }).runQuery(query, new VariableTable());

      assertSame(partial, result);
   }

   @Test
   void cancelledRunStillThrowsCancelledException() {
      final RestJsonQuery query = new RestJsonQuery();
      final QueryManager manager = new QueryManager();
      query.setProperty("queryManager", manager);

      assertThrows(CancelledException.class, () ->
         runtime(query, () -> {
            manager.cancel();
            Tool.addUserMessage("Error executing Rest query: cancelled", ConfirmException.INFO);
            return null;
         }).runQuery(query, new VariableTable()));
   }

   @Test
   void runnerExceptionIsReportedAsAFailure() {
      final RestJsonQuery query = new RestJsonQuery();

      final RuntimeException ex = assertThrows(RuntimeException.class, () ->
         runtime(query, () -> {
            throw new IllegalStateException("HTTP 401");
         }).runQuery(query, new VariableTable()));

      assertTrue(ex.getMessage().contains("HTTP 401"), ex.getMessage());
   }

   private static AbstractRestRuntime runtime(RestJsonQuery query, Supplier<XTableNode> body) {
      return new AbstractRestRuntime() {
         @Override
         protected QueryRunner getQueryRunner(AbstractRestQuery q) {
            return new FakeRunner(body);
         }
      };
   }

   public static final class FakeRunner implements QueryRunner {
      FakeRunner(Supplier<XTableNode> body) {
         this.body = body;
      }

      @Override
      public XTableNode runStream() {
         return body.get();
      }

      @Override
      public XTableNode run() {
         return runStream();
      }

      public void cancel() {
      }

      @Override
      public boolean isCancelled() {
         return false;
      }

      @Override
      public boolean isLiveMode() {
         return false;
      }

      @Override
      public long getTouchTimestamp() {
         return 0;
      }

      private final Supplier<XTableNode> body;
   }
}
