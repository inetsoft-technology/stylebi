/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.*;
import inetsoft.util.FileSystemService;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
public class IgniteClusterTest {
   @TempDir
   static Path clusterDir;

   @BeforeAll
   static void setup() {
      TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);
      ignite1 = IgniteClusterTestUtils.getIgniteCluster("ignite1", ipFinder, clusterDir);
      ignite2 = IgniteClusterTestUtils.getIgniteCluster("ignite2", ipFinder, clusterDir, true);
   }

   @AfterAll
   static void destroy() {
      ignite1.close();
      ignite1 = null;
      ignite2.close();
      ignite2 = null;
   }

   @Test
   void sendMessage() throws InterruptedException {
      CountDownLatch latch = new CountDownLatch(2);
      AtomicInteger local = new AtomicInteger();
      AtomicInteger total = new AtomicInteger();

      ignite1.addMessageListener(event -> {
         if(event.isLocal()) {
            local.incrementAndGet();
         }

         total.incrementAndGet();
         latch.countDown();
      });

      ignite2.addMessageListener(event -> {
         if(event.isLocal()) {
            local.incrementAndGet();
         }

         total.incrementAndGet();
         latch.countDown();
      });

      ignite1.sendMessage(new MessageObject());

      latch.await();

      assertEquals(1, local.get());
      assertEquals(2, total.get());
   }

   @Test
   void exchangeMessages() throws Exception {
      String ignite1Address = ignite1.getLocalMember();
      String ignite2Address = ignite2.getLocalMember();

      ignite2.addMessageListener(event -> {
         if(event.getMessage() instanceof ExchangeObject) {
            ignite2.sendMessage(ignite1Address, new MessageObject("exchange"));
         }
      });

      MessageObject result = ignite1.exchangeMessages(ignite2Address, new ExchangeObject(), MessageObject.class);

      assertNotNull(result);
      assertEquals("exchange", result.text);
   }

   @Test
   void transferFile() throws Exception {
      File file1 = FileSystemService.getInstance().getCacheTempFile("igniteTransfer", ".tmp");
      String link = ignite1.addTransferFile(file1);
      File file2 = ignite2.getTransferFile(link);
      assertNotNull(file2);
      assertTrue(file2.exists());
   }

   @Test
   void submit() throws Exception {
      Future<String> future = ignite1.submit(new TestCallableTask());
      String id = future.get(10, TimeUnit.SECONDS);
      assertEquals(ignite2.getIgniteInstance().cluster().localNode().consistentId().toString(), id);
   }

   @Test
   void submitAll() throws Exception {
      Future<Collection<String>> future = ignite1.submitAll(new TestCallableTask());
      Collection<String> ids = future.get(10, TimeUnit.SECONDS);
      assertTrue(ids.contains(ignite1.getIgniteInstance().cluster().localNode().consistentId().toString()));
      assertTrue(ids.contains(ignite2.getIgniteInstance().cluster().localNode().consistentId().toString()));
   }

   @Test
   void submitServiceTaskCallable() throws Exception {
      Future<String> future = ignite1.submit("submitServiceTaskCallable", () -> "test");
      String result = future.get(10, TimeUnit.SECONDS);
      assertEquals("test", result);
   }

   @Test
   void submitServiceTaskRunnable() throws Exception {
      Future<?> future = ignite1.submit("submitServiceTaskRunnable",
                                        (SingletonRunnableTask) () -> {
                                           // empty
                                        });
      assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
   }

   @Test
   void submitServiceTaskRunnableException() throws Exception {
      Future<?> future = ignite1.submit("submitServiceTaskRunnableException",
                                        (SingletonRunnableTask) () -> {
                                           throw new RuntimeException("test");
                                        });
      Throwable ex = assertThrows(Exception.class, () -> future.get(10, TimeUnit.SECONDS));
      Throwable rootCause = ExceptionUtils.getRootCause(ex);
      assertTrue(rootCause instanceof RuntimeException);
      assertEquals("test", rootCause.getMessage());
   }

   @Test
   void submitServiceTaskCallableException() throws Exception {
      Future<?> future = ignite1.submit("submitServiceTaskCallableException",
                                        (SingletonCallableTask<? extends Serializable>) () -> {
                                           throw new RuntimeException("test");
                                        });
      Exception ex = assertThrows(Exception.class, () -> future.get(10, TimeUnit.SECONDS));
      Throwable rootCause = ExceptionUtils.getRootCause(ex);
      assertTrue(rootCause instanceof RuntimeException);
      assertEquals("test", rootCause.getMessage());
   }

   @Test
   void membershipListener() throws Exception {
      // use a separate cluster here as it may interfere with other tests otherwise
      TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);
      IgniteCluster ignite3 = IgniteClusterTestUtils.getIgniteCluster("ignite3", ipFinder, clusterDir);

      CountDownLatch addedLatch = new CountDownLatch(1);
      CountDownLatch removedLatch = new CountDownLatch(1);

      ignite3.addMembershipListener(new MembershipListener() {
         @Override
         public void memberAdded(MembershipEvent event) {
            addedLatch.countDown();
         }

         @Override
         public void memberRemoved(MembershipEvent event) {
            removedLatch.countDown();
         }
      });

      IgniteCluster ignite4 = IgniteClusterTestUtils.getIgniteCluster("ignite4", ipFinder, clusterDir);
      ignite4.close();

      assertTrue(addedLatch.await(4, TimeUnit.SECONDS));
      assertTrue(removedLatch.await(4, TimeUnit.SECONDS));

      ignite3.close();
   }

   private static IgniteCluster ignite1;
   private static IgniteCluster ignite2;

   private static class MessageObject implements Serializable {
      public MessageObject() {
      }

      public MessageObject(String text) {
         this.text = text;
      }

      String text;
   }

   private static class ExchangeObject implements Serializable {

   }

   private static class TestCallableTask implements Callable<String>, Serializable {
      @Override
      public String call() throws Exception {
         return ignite.cluster().localNode().consistentId().toString();
      }

      @IgniteInstanceResource
      private Ignite ignite;
   }
}
