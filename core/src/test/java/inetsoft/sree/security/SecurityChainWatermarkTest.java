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
package inetsoft.sree.security;

import com.fasterxml.jackson.databind.JsonNode;
import inetsoft.test.*;
import inetsoft.util.DataChangeListener;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79267 and Bug #79127: {@link SecurityChain} took its reload watermark from a LATER read of the file's
 * modification time than the content it described (loadConfiguration read the mtime again after
 * the content, saveConfiguration read it after the write without checking the blob was its own),
 * so a second edit landing in between was skipped for good and one node kept stale providers.
 * These tests run on a real DataSpace (a mocked getDigest returns null) and put the other node's
 * write at each point between the reads with hooks, no sleeps.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityChainWatermarkTest {
   private static final String AUTHC_FILE = "authc-chain.json";
   private static final String AUTHZ_FILE = "authz-chain.json";

   private final List<AuthenticationChain> authcChains = new ArrayList<>();
   private final List<AuthorizationChain> authzChains = new ArrayList<>();

   @AfterEach
   void tearDown() {
      ProbeProvider.hook = null;
      authcChains.forEach(AuthenticationChain::tearDown);
      authzChains.forEach(AuthorizationChain::tearDown);
   }

   // load: a second edit lands while the providers of the first are being created

   @Test
   void secondEditBetweenContentReadAndWatermarkIsNotSkipped() throws Exception {
      TestChain host = newHost();
      TestChain other = newHost();
      save(other, "probe");

      // the other node's second edit lands after host read "probe" and before host finished
      ProbeProvider.hook = () -> {
         tick();
         other.setProviders(named("A", "R"));
         tick();
      };

      host.loadConfiguration();
      assertNull(ProbeProvider.hook, "the hook did not run");
      assertEquals(List.of("probe"), names(host.getProviders()), "content read before the edit");

      host.loadConfiguration();
      assertEquals(List.of("A", "R"), names(host.getProviders()),
                   "the second edit was skipped, the node is stuck on stale providers");
   }

   // save: this node's own write is not reloaded again

   @Test
   void ownSaveDoesNotReloadItself() throws Exception {
      TestChain host = newHost();
      host.setProviders(named("A", "E"));
      List<AuthenticationProvider> held = new ArrayList<>(host.getProviderList());
      tick();

      host.loadConfiguration();

      assertEquals(held.size(), host.getProviderList().size());

      for(int i = 0; i < held.size(); i++) {
         assertSame(held.get(i), host.getProviderList().get(i), "own save reloaded itself");
      }
   }

   @Test
   void ownSaveDoesNotReloadItselfAuthorizationChain() throws Exception {
      AuthorizationChain host = new AuthorizationChain();
      authzChains.add(host);
      removeListener(AUTHZ_FILE, host);
      FileAuthorizationProvider provider = new FileAuthorizationProvider();
      provider.setProviderName("Z");
      host.setProviders(new ArrayList<>(List.of(provider)));
      tick();

      host.loadConfiguration();

      assertSame(provider, host.getProviderList().get(0), "own save reloaded itself");
   }

   // save: a remote commit around this node's save is not skipped. The premise of the old
   // tests, a watermark made of the mtime and digest read after the write, is gone (Bug #79127:
   // the mtime is not ordered across nodes): a save records the digest of the bytes it wrote
   // without reading the store, so a remote commit at any point after the write has another
   // digest and is reloaded. The intent, that no commit is skipped, is kept.

   @Test
   void remoteCommitAfterSaveIsNotSkipped() throws Exception {
      TestChain host = newHost();
      TestChain other = newHost();

      host.setProviders(named("A", "E"));
      remoteSave(other, "R").run();

      host.loadConfiguration();
      assertEquals(List.of("R"), names(host.getProviders()), "the remote commit was skipped");
   }

   @Test
   void remoteCommitBetweenWriteAndEventIsNotSkippedEvenWithEqualMtime() throws Exception {
      // no tick() between the saves: both writes can carry the same mtime
      TestChain host = newHost();
      TestChain other = newHost();

      host.setProviders(named("A", "E"));
      other.setProviders(named("R"));

      host.loadConfiguration();
      assertEquals(List.of("R"), names(host.getProviders()), "the remote commit was skipped");
   }

   @Test
   void overwrittenOwnSaveIsReloadedEvenWhenTheOverwriteHoldsEarlierContent() throws Exception {
      // A -> B -> A: the content the node held before its save comes back
      TestChain host = newHost();
      TestChain other = newHost();
      save(other, "R");
      host.loadConfiguration();
      assertEquals(List.of("R"), names(host.getProviders()));

      host.setProviders(named("A", "E"));
      save(other, "R");

      host.loadConfiguration();
      assertEquals(List.of("R"), names(host.getProviders()), "the remote commit was skipped");
   }

   @Test
   void unchangedFileIsNotReloaded() throws Exception {
      TestChain host = newHost();
      save(host, "A");
      List<AuthenticationProvider> held = new ArrayList<>(host.getProviderList());

      host.loadConfiguration();
      host.loadConfiguration();

      assertEquals(held.size(), host.getProviderList().size());
      assertSame(held.get(0), host.getProviderList().get(0), "an unchanged file was reloaded");
   }

   @Test
   void unavailableDigestReloadsInsteadOfSkipping() throws Exception {
      TestChain host = newHost();
      TestChain other = newHost();
      host.digestOverride = Optional.empty();

      other.setProviders(named("R"));

      host.loadConfiguration();
      assertEquals(List.of("R"), names(host.getProviders()));
      other.setProviders(named("S"));

      host.loadConfiguration();
      assertEquals(List.of("S"), names(host.getProviders()), "a null digest skipped a change");
   }

   // the order is part of the fix: reading the digest after the content could record a digest
   // newer than the content loaded

   @Test
   void loadReadsDigestBeforeContent() throws Exception {
      TestChain host = newHost();
      TestChain other = newHost();
      save(other, "probe");
      host.calls.clear();
      ProbeProvider.hook = () -> host.calls.add("content");

      host.loadConfiguration();

      assertNull(ProbeProvider.hook, "the hook did not run");
      assertEquals("digest", host.calls.get(0));
      assertTrue(host.calls.indexOf("content") > host.calls.indexOf("digest"));
   }

   // helpers

   private TestChain newHost() {
      TestChain chain = new TestChain();
      authcChains.add(chain);
      removeListener(AUTHC_FILE, chain);
      return chain;
   }

   private static Runnable remoteSave(AuthenticationChain other, String... names) {
      return () -> {
         tick();
         other.setProviders(named(names));
         tick();
      };
   }

   private static void save(AuthenticationChain chain, String... names) {
      tick();
      chain.setProviders(named(names));
      tick();
   }

   /** The listener would reload on a file event; the tests call loadConfiguration() themselves. */
   private static void removeListener(String file, DataChangeListener listener) {
      DataSpace.getDataSpace().removeChangeListener(null, file, listener);
   }

   private static List<AuthenticationProvider> named(String... names) {
      List<AuthenticationProvider> list = new ArrayList<>();

      for(String name : names) {
         FileAuthenticationProvider provider =
            "probe".equals(name) ? new ProbeProvider() : new FileAuthenticationProvider();
         provider.setProviderName(name);
         list.add(provider);
      }

      return list;
   }

   private static List<String> names(List<? extends JsonConfigurableProvider> list) {
      return list.stream().map(JsonConfigurableProvider::getProviderName)
         .collect(Collectors.toList());
   }

   /** Makes the next write's modification time later than the last one. */
   private static void tick() {
      long start = System.currentTimeMillis();

      while(System.currentTimeMillis() - start < 3L) {
         Thread.onSpinWait();
      }
   }

   /** Runs a hook once while the host reads the content of the file. */
   public static class ProbeProvider extends FileAuthenticationProvider {
      static volatile Runnable hook;

      public ProbeProvider() {
      }

      @Override
      public void readConfiguration(JsonNode config) {
         super.readConfiguration(config);
         Runnable run = hook;
         hook = null;

         if(run != null) {
            run.run();
         }
      }
   }

   /** Records the digest reads and can make the digest unavailable. */
   private static final class TestChain extends AuthenticationChain {
      volatile Optional<String> digestOverride;
      final List<String> calls = Collections.synchronizedList(new ArrayList<>());

      @Override
      String readDigest(DataSpace dataSpace, String configFile) {
         // calls is null while the superclass constructor runs
         if(calls != null) {
            calls.add("digest");
         }

         Optional<String> override = digestOverride;
         return override != null ? override.orElse(null) :
            super.readDigest(dataSpace, configFile);
      }
   }
}
