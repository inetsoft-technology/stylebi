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
package inetsoft.util.credential;

import inetsoft.util.AbstractSecretsManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests that a credential which cannot be resolved from the secrets manager is marked as
 * unavailable instead of silently loading blank values.
 */
@Tag("core")
class CloudCredentialTest {
   @Test
   void failureMarksUnavailableWithoutThrowing() {
      AbstractSecretsManager manager = createManager();
      doReturn(null).when(manager).decryptPassword(anyString());
      TestCloudCredential credential = new TestCloudCredential(manager, SECRET_ID);

      assertDoesNotThrow(credential::fetchCredential);
      assertTrue(credential.isCredentialUnavailable());
      assertNull(credential.getUser());
      assertNull(credential.getPassword());
   }

   @Test
   void successClearsTheFlag() {
      AbstractSecretsManager manager = createManager();
      doReturn(VALID_SECRET).when(manager).decryptPassword(anyString());
      TestCloudCredential credential = new TestCloudCredential(manager, SECRET_ID);
      credential.setCredentialUnavailable(true);

      credential.fetchCredential();

      assertFalse(credential.isCredentialUnavailable());
      assertEquals("scott", credential.getUser());
      assertEquals("tiger", credential.getPassword());
   }

   @Test
   void emptyIdIsNotAFailure() {
      AbstractSecretsManager manager = createManager();
      TestCloudCredential credential = new TestCloudCredential(manager, null);
      credential.setCredentialUnavailable(true);

      credential.fetchCredential();

      assertFalse(credential.isCredentialUnavailable());
      assertTrue(credential.ensureCredentialAvailable());
      verify(manager, never()).decryptPassword(anyString());
   }

   @Test
   void ensureCredentialAvailableRetriesExactlyOnce() {
      AbstractSecretsManager manager = createManager();
      doReturn(null, VALID_SECRET).when(manager).decryptPassword(anyString());
      TestCloudCredential credential = new TestCloudCredential(manager, SECRET_ID);
      credential.retryDue = true;

      credential.fetchCredential();
      assertTrue(credential.isCredentialUnavailable());

      assertTrue(credential.ensureCredentialAvailable());
      assertFalse(credential.isCredentialUnavailable());
      assertEquals("scott", credential.getUser());

      // already resolved, so no further vault call is made
      assertTrue(credential.ensureCredentialAvailable());
      verify(manager, times(2)).decryptPassword(anyString());
   }

   @Test
   void ensureCredentialAvailableIsThrottledAfterAFailure() {
      AbstractSecretsManager manager = createManager();
      doReturn(null).when(manager).decryptPassword(anyString());
      TestCloudCredential credential = new TestCloudCredential(manager, SECRET_ID);
      credential.retryDue = false;

      credential.fetchCredential();
      assertTrue(credential.isCredentialUnavailable());

      // the retry interval has not elapsed, so the secrets manager is not called again
      assertFalse(credential.ensureCredentialAvailable());
      assertFalse(credential.ensureCredentialAvailable());
      verify(manager, times(1)).decryptPassword(anyString());
   }

   @Test
   void retryIsNotDueImmediatelyAfterAFailure() {
      AbstractSecretsManager manager = createManager();
      CloudPasswordCredential credential = new CloudPasswordCredential();
      credential.setId(SECRET_ID);

      assertTrue(credential.isFetchRetryDue());

      credential.setCredentialUnavailable(true);
      assertFalse(credential.isFetchRetryDue());

      credential.setCredentialUnavailable(false);
      assertTrue(credential.isFetchRetryDue());
   }

   @Test
   void changingTheSecretIdClearsTheUnavailableState() {
      AbstractSecretsManager manager = createManager();
      doReturn(null).when(manager).decryptPassword(anyString());
      TestCloudCredential credential = new TestCloudCredential(manager, SECRET_ID);

      credential.fetchCredential();
      assertTrue(credential.isCredentialUnavailable());

      // the state described the previous secret, it must not carry over to a different one
      credential.setId("a-different-secret-id");
      assertFalse(credential.isCredentialUnavailable());
      assertTrue(credential.isFetchRetryDue());

      // setting the same id again leaves the state alone
      credential.fetchCredential();
      assertTrue(credential.isCredentialUnavailable());
      credential.setId("a-different-secret-id");
      assertTrue(credential.isCredentialUnavailable());
   }

   @Test
   void onlyOneThreadFetchesAtATime() {
      AbstractSecretsManager manager = createManager();
      doReturn(null).when(manager).decryptPassword(anyString());
      TestCloudCredential credential = new TestCloudCredential(manager, SECRET_ID);
      credential.fetchCredential();
      reset(manager);
      doReturn(null).when(manager).decryptPassword(anyString());

      // a fetch already in flight makes the other callers return rather than pile up behind it
      assertTrue(credential.beginFetchRetry());
      assertFalse(credential.ensureCredentialAvailable());
      verify(manager, never()).decryptPassword(anyString());

      credential.endFetchRetry();
      assertFalse(credential.ensureCredentialAvailable());
      verify(manager, times(1)).decryptPassword(anyString());
   }

   @Test
   void unavailableStateIsNotPartOfEquals() {
      AbstractSecretsManager manager = createManager();
      TestCloudCredential first = new TestCloudCredential(manager, SECRET_ID);
      TestCloudCredential second = new TestCloudCredential(manager, SECRET_ID);
      first.setCredentialUnavailable(true);

      assertEquals(first, second);
      assertEquals(second, first);
   }

   private static AbstractSecretsManager createManager() {
      return Mockito.mock(AbstractSecretsManager.class, Mockito.CALLS_REAL_METHODS);
   }

   /**
    * A cloud credential bound to a specific secrets manager instance, so that the test does not
    * depend on the configured password encryption.
    */
   private static final class TestCloudCredential extends CloudPasswordCredential {
      TestCloudCredential(AbstractSecretsManager manager, String id) {
         this.manager = manager;
         setId(id);
      }

      @Override
      public AbstractSecretsManager getSecretsManager() {
         return manager;
      }

      @Override
      public boolean isFetchRetryDue() {
         return retryDue;
      }

      private final AbstractSecretsManager manager;
      // controlled by the test so that the retry interval does not make it time dependent
      private boolean retryDue = true;
   }

   private static final String SECRET_ID = "6f1d0b8e-test-secret-id";
   private static final String VALID_SECRET = "{\"user\":\"scott\",\"password\":\"tiger\"}";
}
