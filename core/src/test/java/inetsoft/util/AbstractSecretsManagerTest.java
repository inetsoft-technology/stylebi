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
package inetsoft.util;

import inetsoft.util.credential.CloudPasswordCredential;
import inetsoft.util.credential.Credential;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests the failure handling of {@link AbstractSecretsManager#getCredential(Credential)}.
 * <p>
 * The manager is mocked with CALLS_REAL_METHODS so that its constructor, which resolves the
 * local encryption instance, is never run. Every credential leaves the dbType null so that
 * {@link Tool#isVaultDatabaseSecretsEngine(String)} short circuits before it looks up the
 * configured password encryption.
 */
@Tag("core")
class AbstractSecretsManagerTest {
   @Test
   void nullCredentialReturnsNull() {
      AbstractSecretsManager manager = createManager();

      assertNull(manager.getCredential(null));
      verify(manager, never()).decryptPassword(anyString());
   }

   @Test
   void credentialWithoutIdIsReturnedUnchanged() {
      AbstractSecretsManager manager = createManager();
      CloudPasswordCredential credential = new CloudPasswordCredential();

      assertSame(credential, manager.getCredential(credential));
      verify(manager, never()).decryptPassword(anyString());
   }

   @ParameterizedTest
   @NullAndEmptySource
   @ValueSource(strings = { "   " })
   void unresolvedSecretThrows(String result) {
      AbstractSecretsManager manager = createManager();
      doReturn(result).when(manager).decryptPassword(anyString());

      SecretsUnavailableException ex = assertThrows(
         SecretsUnavailableException.class, () -> manager.getCredential(createCredential()));

      assertTrue(ex.getMessage().contains(SECRET_ID), ex.getMessage());
      assertTrue(ex.getMessage().contains("CloudPasswordCredential"), ex.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "not json at all",
      "{\"user\":\"admin\",password:\"secret\"}",
      "{\"user\":\"admin\""
   })
   void malformedSecretThrowsWithoutLeakingThePayload(String result) {
      AbstractSecretsManager manager = createManager();
      doReturn(result).when(manager).decryptPassword(anyString());

      SecretsUnavailableException ex = assertThrows(
         SecretsUnavailableException.class, () -> manager.getCredential(createCredential()));

      assertNotNull(ex.getCause());
      assertTrue(ex.getMessage().contains(SECRET_ID), ex.getMessage());
      // the payload is the secret and must never reach the message
      assertFalse(ex.getMessage().contains(result), ex.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = { "{}", "{\"username\":\"admin\",\"secret\":\"s3cr3t\"}" })
   void secretWithoutAnyUsableValueThrows(String result) {
      AbstractSecretsManager manager = createManager();
      doReturn(result).when(manager).decryptPassword(anyString());

      SecretsUnavailableException ex = assertThrows(
         SecretsUnavailableException.class, () -> manager.getCredential(createCredential()));

      assertTrue(ex.getMessage().contains(SECRET_ID), ex.getMessage());
      assertTrue(ex.getMessage().contains("does not contain any value"), ex.getMessage());
   }

   @Test
   void fetchFailureThrows() {
      AbstractSecretsManager manager = createManager();
      doThrow(new IllegalStateException("vault down")).when(manager).decryptPassword(anyString());

      SecretsUnavailableException ex = assertThrows(
         SecretsUnavailableException.class, () -> manager.getCredential(createCredential()));

      assertInstanceOf(IllegalStateException.class, ex.getCause());
      assertTrue(ex.getMessage().contains(SECRET_ID), ex.getMessage());
   }

   @Test
   void validSecretIsConvertedAndKeepsTheId() {
      AbstractSecretsManager manager = createManager();
      doReturn("{\"user\":\"scott\",\"password\":\"tiger\"}")
         .when(manager).decryptPassword(anyString());

      Credential converted = manager.getCredential(createCredential());

      assertInstanceOf(CloudPasswordCredential.class, converted);
      assertEquals("scott", ((CloudPasswordCredential) converted).getUser());
      assertEquals("tiger", ((CloudPasswordCredential) converted).getPassword());
      assertEquals(SECRET_ID, converted.getId());
   }

   private static AbstractSecretsManager createManager() {
      return Mockito.mock(AbstractSecretsManager.class, Mockito.CALLS_REAL_METHODS);
   }

   private static CloudPasswordCredential createCredential() {
      CloudPasswordCredential credential = new CloudPasswordCredential();
      credential.setId(SECRET_ID);

      return credential;
   }

   private static final String SECRET_ID = "6f1d0b8e-test-secret-id";
}
