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
package inetsoft.util;

import inetsoft.util.config.SecretsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests that {@link PasswordEncryption#newInstance(SecretsConfig)} tolerates a factory whose
 * constructor resolves another {@link PasswordEncryption} through the same cache, which is what
 * every {@link AbstractSecretsManager} does to obtain its local encryption (Bug #80159).
 * <p>
 * {@link SecretsConfig} has no {@code hashCode}, so the cache key's bin is its identity hash
 * and a nested lookup lands in the outer key's bin by chance. The configs here pin the hash so
 * that the collision is deterministic. The test factories are registered by
 * {@code META-INF/services/inetsoft.util.PasswordEncryptionFactory} in the test resources.
 */
@Tag("core")
class PasswordEncryptionNewInstanceReentryTest {
   @BeforeEach
   void setUp() {
      // start from an empty cache so that the outer key's bin is empty (the failing shape),
      // and put the entries other tests of this JVM created back afterwards
      saved.putAll(PasswordEncryption.INSTANCE_CACHE);
      PasswordEncryption.INSTANCE_CACHE.clear();
      ReentrantFactory.nested = null;
      ReentrantFactory.created = 0;
   }

   @AfterEach
   void tearDown() {
      PasswordEncryption.INSTANCE_CACHE.clear();
      PasswordEncryption.INSTANCE_CACHE.putAll(saved);
      ReentrantFactory.nested = null;
   }

   @Test
   void nestedLookupInTheSameBinIsResolved() {
      FixedHashConfig outer = new FixedHashConfig(ReentrantFactory.TYPE, 7);
      FixedHashConfig leaf = new FixedHashConfig(LeafFactory.TYPE, 7);
      ReentrantFactory.nested = leaf;

      PasswordEncryption encryption = PasswordEncryption.newInstance(outer);

      assertNotNull(encryption);
      assertSame(encryption, PasswordEncryption.INSTANCE_CACHE.get(outer));
      assertNotNull(PasswordEncryption.INSTANCE_CACHE.get(leaf));
      assertEquals(1, ReentrantFactory.created);
   }

   @Test
   void nestedLookupInAnotherBinIsResolved() {
      FixedHashConfig outer = new FixedHashConfig(ReentrantFactory.TYPE, 7);
      FixedHashConfig leaf = new FixedHashConfig(LeafFactory.TYPE, 8);
      ReentrantFactory.nested = leaf;

      assertNotNull(PasswordEncryption.newInstance(outer));
      assertNotNull(PasswordEncryption.INSTANCE_CACHE.get(leaf));
   }

   @Test
   void sameConfigIsCreatedOnce() {
      FixedHashConfig outer = new FixedHashConfig(ReentrantFactory.TYPE, 7);
      ReentrantFactory.nested = new FixedHashConfig(LeafFactory.TYPE, 7);

      PasswordEncryption first = PasswordEncryption.newInstance(outer);
      PasswordEncryption second = PasswordEncryption.newInstance(outer);

      assertSame(first, second);
      assertEquals(1, ReentrantFactory.created);
   }

   @Test
   void unknownTypeStillFails() {
      FixedHashConfig config = new FixedHashConfig("no-such-type-80159", 7);

      assertThrows(RuntimeException.class, () -> PasswordEncryption.newInstance(config));
      assertNull(PasswordEncryption.INSTANCE_CACHE.get(config));
   }

   private final Map<SecretsConfig, PasswordEncryption> saved = new HashMap<>();

   private static final class FixedHashConfig extends SecretsConfig {
      FixedHashConfig(String type, int hash) {
         setType(type);
         this.hash = hash;
      }

      @Override
      public int hashCode() {
         return hash;
      }

      private final int hash;
   }

   /**
    * Behaves like {@link AbstractSecretsManager}: resolves another instance while it is created.
    */
   public static class ReentrantFactory implements PasswordEncryptionFactory {
      static final String TYPE = "test-reentrant-80159";
      static volatile SecretsConfig nested;
      static volatile int created;

      @Override
      public String getType() {
         return TYPE;
      }

      @Override
      public PasswordEncryption createPasswordEncryption(SecretsConfig secretsConfig) {
         PasswordEncryption.newInstance(nested);
         created++;
         return mock(PasswordEncryption.class);
      }
   }

   public static class LeafFactory implements PasswordEncryptionFactory {
      static final String TYPE = "test-leaf-80159";

      @Override
      public String getType() {
         return TYPE;
      }

      @Override
      public PasswordEncryption createPasswordEncryption(SecretsConfig secretsConfig) {
         return mock(PasswordEncryption.class);
      }
   }
}
