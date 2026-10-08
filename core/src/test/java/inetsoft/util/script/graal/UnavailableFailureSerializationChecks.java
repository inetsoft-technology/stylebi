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
package inetsoft.util.script.graal;

import inetsoft.report.TableLens;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.lens.*;
import inetsoft.sree.internal.cluster.ignite.IgniteUtils;
import inetsoft.uql.util.TableLoadException;
import inetsoft.util.stall.LockStallException;
import inetsoft.util.swap.*;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.marshaller.Marshaller;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78084, a lock stall, a lost swap file or a load failure that Java code called by a
 * script throws gets a suppressed Truffle stack trace element, which cannot be serialized. The
 * failure that leaves the script engine, and the failure a table kept for its later readers,
 * must still serialize, by Java serialization and by the Ignite marshaller of a cluster call
 * response, whichever reader (script or not) read the table first. Run by
 * {@link UnavailableFailureSerializationTest} (Java serialization, core) and
 * {@link UnavailableFailureIgniteMarshalTest} (with a started Ignite node, slow).
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
abstract class UnavailableFailureSerializationChecks {
   static void startIgnite() throws Exception {
      // one local node: no multicast, a static IP finder on 127.0.0.1
      int discoPort = freePort();
      int commPort = freePort();
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(Collections.singletonList("127.0.0.1:" + discoPort));
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(commPort);
      comm.setLocalPortRange(0);

      workDir = Files.createTempDirectory("ignite-78084");
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("bug78084-" + UUID.randomUUID());
      config.setWorkDirectory(workDir.toString());
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(false);
      // as IgniteCluster configures it
      IgniteUtils.configBinaryTypes(config);
      ignite = Ignition.start(config);
      // the marshaller a cluster message is written with (GridIoManager)
      marshaller = ((IgniteEx) ignite).context().marshaller();

      Class<?> response = Class.forName(
         "inetsoft.sree.internal.cluster.ignite.IgniteCluster$AffinityCallResponse");
      responseConstructor = response.getDeclaredConstructor(
         String.class, String.class, Serializable.class, Throwable.class);
      responseConstructor.setAccessible(true);
      responseError = response.getDeclaredMethod("getError");
      responseError.setAccessible(true);
   }

   static void stopIgnite() throws Exception {
      marshaller = null;

      if(ignite != null) {
         ignite.close();
         ignite = null;
      }

      if(workDir != null) {
         try(var paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
         }

         workDir = null;
      }
   }

   @BeforeEach
   void createEngine() throws Exception {
      engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
   }

   @AfterEach
   void closeEngine() {
      if(engine != null) {
         engine.close();
      }
   }

   /**
    * The failure a Java method called by a script throws, by itself or wrapped, leaves the
    * engine as a failure of the same class and content that serializes.
    */
   @Test
   void hostFailureThroughExecSerializes() throws Exception {
      Host host = new Host();
      engine.put("host", host);
      Object script = engine.compile("host.value()");
      List<Supplier<RuntimeException>> failures = List.of(
         () -> new LockStallException("Test.site", "worker", 1234, "/tmp/dump-1.txt"),
         () -> new SwapFileReadException(new File("lost.tdat"), new IOException("gone")),
         () -> new SwapReadInterruptedException(new File("read.tdat"),
                                                new ClosedByInterruptException()));

      for(Supplier<RuntimeException> failure : failures) {
         for(boolean wrapped : new boolean[] { false, true }) {
            RuntimeException found = failure.get();
            host.failure = wrapped ? new IllegalStateException("wrapped", found) : found;
            String what = found.getClass().getSimpleName() + (wrapped ? " wrapped" : "");

            Throwable thrown = assertThrows(RuntimeException.class,
                                            () -> engine.exec(script, null, null), what);

            assertSameFailure(found, thrown, what);
            assertSerializes(thrown, what);
         }
      }
   }

   /** A script reads a distinct table first, a later plain read gets a failure that serializes. */
   @Test
   void distinctTableReadByScriptFirst() throws Exception {
      for(Kind kind : Kind.values()) {
         readScriptFirst(kind, DistinctTableLens::new);
      }
   }

   /** A plain read, a script read and a plain read of a distinct table all serialize. */
   @Test
   void distinctTableReadByScriptBetweenPlainReads() throws Exception {
      for(Kind kind : Kind.values()) {
         readPlainFirst(kind, DistinctTableLens::new);
      }
   }

   /** The same for a join, whose workers keep the failure of a base. */
   @Test
   void joinReadByScript() throws Exception {
      for(Kind kind : Kind.values()) {
         readScriptFirst(kind, UnavailableFailureSerializationChecks::join);
         readPlainFirst(kind, UnavailableFailureSerializationChecks::join);
      }
   }

   /** The same for a summary filter, which keeps the failure of a pass. */
   @Test
   void summaryFilterReadByScript() throws Exception {
      for(Kind kind : Kind.values()) {
         readScriptFirst(kind, UnavailableFailureSerializationChecks::summary);
         readPlainFirst(kind, UnavailableFailureSerializationChecks::summary);
      }
   }

   /**
    * A script is the first reader, on the thread that holds the script lock, so a table that
    * reads its base synchronously throws the failure of the base to the script. Then a plain
    * read, as of another assembly over the same table.
    */
   private void readScriptFirst(Kind kind, Function<TableLens, TableLens> lens) throws Exception {
      TableLens table = lens.apply(new FailingTable(kind));
      String what = kind + " " + table.getClass().getSimpleName() + " script first";

      assertReadFails(kind, readByScript(table), what + ", script read");
      assertReadFails(kind, readPlain(table), what + ", plain read");
      assertReadFails(kind, readByScript(table), what + ", second script read");
      assertReadFails(kind, readPlain(table), what + ", second plain read");
   }

   private void readPlainFirst(Kind kind, Function<TableLens, TableLens> lens) throws Exception {
      TableLens table = lens.apply(new FailingTable(kind));
      String what = kind + " " + table.getClass().getSimpleName() + " plain first";

      assertReadFails(kind, readPlain(table), what + ", plain read");
      assertReadFails(kind, readByScript(table), what + ", script read");
      assertReadFails(kind, readPlain(table), what + ", second plain read");
   }

   private Throwable readByScript(TableLens table) throws Exception {
      engine.put("lens", table);
      Object script = engine.compile("lens.moreRows(2147483647)");

      try {
         engine.exec(script, null, null);
         return null;
      }
      catch(Throwable ex) {
         return ex;
      }
   }

   private static Throwable readPlain(TableLens table) {
      try {
         table.moreRows(TableLens.EOT);
         table.getRowCount();
         return null;
      }
      catch(Throwable ex) {
         return ex;
      }
   }

   /**
    * The read failed with the failure of the base, which the readers find by its class, and
    * the failure serializes.
    */
   private static void assertReadFails(Kind kind, Throwable thrown, String what)
      throws Exception
   {
      assertNotNull(thrown, what + ": the read did not fail");
      RuntimeException found = kind.find(thrown);
      assertNotNull(found, what + ": no " + kind + " in " + thrown);
      assertEquals(kind.message, found.getMessage(), what);
      assertSerializes(thrown, what);
   }

   /**
    * {@code thrown} is a copy of {@code found}: the same class, message, fields and cause.
    */
   private static void assertSameFailure(RuntimeException found, Throwable thrown, String what) {
      assertEquals(found.getClass(), thrown.getClass(), what);
      assertEquals(found.getMessage(), thrown.getMessage(), what);
      assertSame(found.getCause(), thrown.getCause(), what);
      assertNotNull(DataUnavailable.find(thrown), what);

      if(found instanceof LockStallException stall) {
         LockStallException copy = (LockStallException) thrown;
         assertEquals(stall.getSite(), copy.getSite(), what);
         assertEquals(stall.getThreadName(), copy.getThreadName(), what);
         assertEquals(stall.getStalledMillis(), copy.getStalledMillis(), what);
         assertEquals(stall.getDumpPath(), copy.getDumpPath(), what);
      }
      else {
         assertEquals(((SwapFileReadException) found).getFile(),
                      ((SwapFileReadException) thrown).getFile(), what);
      }
   }

   /**
    * The failure survives Java serialization and the Ignite marshaller of the response of a
    * cluster call, with its class and message.
    */
   private static void assertSerializes(Throwable failure, String what) throws Exception {
      Throwable copy = assertDoesNotThrow(() -> jdkRoundTrip(failure), what + ": Java serialization");
      assertEquals(failure.getClass(), copy.getClass(), what);
      assertEquals(failure.getMessage(), copy.getMessage(), what);

      if(marshaller == null) {
         return;
      }

      Object response = responseConstructor.newInstance("id", "node", null, failure);
      Object read = assertDoesNotThrow(
         () -> marshaller.unmarshal(marshaller.marshal(response), UnavailableFailureSerializationChecks.class.getClassLoader()),
         what + ": Ignite marshaller");
      Throwable error = (Throwable) responseError.invoke(read);
      assertEquals(failure.getClass(), error.getClass(), what);
      assertEquals(failure.getMessage(), error.getMessage(), what);
   }

   private static Throwable jdkRoundTrip(Throwable failure) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(failure);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return (Throwable) in.readObject();
      }
   }

   private static TableLens join(TableLens base) {
      // a hash or a merge join, by the memory state
      return new JoinTableLens(base, new DefaultTableLens(FailingTable.data()), new int[] { 0 },
                               new int[] { 0 }, JoinTableLens.INNER_JOIN, true);
   }

   private static TableLens summary(TableLens base) {
      return new SummaryFilter(base, new int[] { 0 }, new int[] { 1 }, new SumFormula(), null);
   }

   private static int freePort() throws IOException {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   /** The failures of a read that are not script values. */
   private enum Kind {
      STALL("Query stalled: no progress for 1234 ms waiting in Test.site on thread \"worker\"") {
         @Override
         RuntimeException create() {
            return new LockStallException("Test.site", "worker", 1234, null);
         }

         @Override
         RuntimeException find(Throwable failure) {
            return LockStallException.find(failure);
         }
      },
      SWAP("Could not read swap file lost.tdat, the swapped data is not available") {
         @Override
         RuntimeException create() {
            return new SwapFileReadException(new File("lost.tdat"), new IOException("gone"));
         }

         @Override
         RuntimeException find(Throwable failure) {
            return SwapFileReadException.find(failure);
         }
      },
      LOAD("Test load failure") {
         @Override
         RuntimeException create() {
            return new TableLoadException(message, new IOException("db error"));
         }

         @Override
         RuntimeException find(Throwable failure) {
            return TableLoadException.find(failure);
         }
      };

      Kind(String message) {
         this.message = message;
      }

      /** A new failure, as a swap fragment or a failed table throws one on each read. */
      abstract RuntimeException create();

      abstract RuntimeException find(Throwable failure);

      final String message;
   }

   /** A base whose data row 5 fails with a new failure on each read. */
   private static final class FailingTable extends DefaultTableLens {
      FailingTable(Kind kind) {
         super(data());
         this.kind = kind;
      }

      static Object[][] data() {
         Object[][] data = new Object[11][];
         data[0] = new Object[] { "key", "value" };

         for(int r = 1; r < data.length; r++) {
            data[r] = new Object[] { "k" + r, r };
         }

         return data;
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 5) {
            throw kind.create();
         }

         return super.getObject(r, c);
      }

      private final Kind kind;
   }

   public static final class Host {
      public Object value() {
         throw failure;
      }

      volatile RuntimeException failure;
   }

   private static Ignite ignite;
   private static Path workDir;
   private static Marshaller marshaller;
   private static Constructor<?> responseConstructor;
   private static Method responseError;
   private GraalJavaScriptEngine engine;
}
