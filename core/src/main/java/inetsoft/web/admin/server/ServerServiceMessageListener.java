/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.server;

import com.sun.management.HotSpotDiagnosticMXBean;
import inetsoft.sree.internal.cluster.*;
import inetsoft.util.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.management.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * Decouple message listener from spring so it can be used with the scheduler
 */
public class ServerServiceMessageListener implements MessageListener {
   public ServerServiceMessageListener(Cluster cluster) {
      this.cluster = cluster;
   }

   @Override
   public void messageReceived(MessageEvent event) {
      String sender = event.getSender();

      if(event.getMessage() instanceof GetThreadDumpMessage) {
         handleGetThreadDumpMessage(sender);
      }
      else if(event.getMessage() instanceof CreateHeapDumpMessage) {
         handleCreateHeapDumpMessage(sender);
      }
      else if(event.getMessage() instanceof IsHeapDumpCompleteMessage) {
         handleIsHeapDumpCompleteMessage(sender, (IsHeapDumpCompleteMessage) event.getMessage());
      }
      else if(event.getMessage() instanceof GetHeapDumpLengthMessage) {
         handleGetHeapDumpLengthMessage(sender, (GetHeapDumpLengthMessage) event.getMessage());
      }
      else if(event.getMessage() instanceof GetHeapDumpContentMessage) {
         handleGetHeapDumpContentMessage(sender, (GetHeapDumpContentMessage) event.getMessage());
      }
      else if(event.getMessage() instanceof DisposeHeapDumpMessage) {
         handleDisposeHeapDumpMessage(sender, (DisposeHeapDumpMessage) event.getMessage());
      }
   }

   void handleGetThreadDumpMessage(String sender) {
      GetThreadDumpCompleteMessage message = new GetThreadDumpCompleteMessage();
      message.setThreadDump(getThreadDump());

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send the thread dump message", e);
      }
   }

   void handleCreateHeapDumpMessage(String sender) {
      CreateHeapDumpCompleteMessage message = new CreateHeapDumpCompleteMessage();
      message.setId(createHeapDump());

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send the heap dump message", e);
      }
   }

   void handleIsHeapDumpCompleteMessage(String sender,
                                        IsHeapDumpCompleteMessage reqMsg)
   {
      IsHeapDumpCompleteCompleteMessage message = new IsHeapDumpCompleteCompleteMessage();
      message.setId(reqMsg.getId());
      message.setComplete(isHeapDumpComplete(reqMsg.getId()));

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send the heap dump message", e);
      }
   }

   void handleGetHeapDumpLengthMessage(String sender,
                                       GetHeapDumpLengthMessage reqMsg)
   {
      GetHeapDumpLengthCompleteMessage message = new GetHeapDumpLengthCompleteMessage();
      message.setId(reqMsg.getId());
      message.setLength(getHeapDumpLength(reqMsg.getId()));

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send the heap dump message", e);
      }
   }

   void handleGetHeapDumpContentMessage(String sender,
                                        GetHeapDumpContentMessage reqMsg)
   {
      GetHeapDumpContentCompleteMessage message = new GetHeapDumpContentCompleteMessage();
      message.setId(reqMsg.getId());
      message.setContent(getHeapDumpContent(reqMsg.getId(), reqMsg.getOffset(),
                                                          reqMsg.getLength()));

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send the heap dump message", e);
      }
   }

   void handleDisposeHeapDumpMessage(String sender,
                                     DisposeHeapDumpMessage reqMsg)
   {
      DisposeHeapDumpCompleteMessage message = new DisposeHeapDumpCompleteMessage();
      message.setId(reqMsg.getId());

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send the heap dump message", e);
      }
   }

   /**
    * Gets a dump of the current stack traces for all threads.
    *
    * @return the stack dump.
    */
   public String getThreadDump() {
      String result = null;

      try {
         ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
         StringWriter buffer = new StringWriter();
         PrintWriter writer = new PrintWriter(buffer);

         writer.format(
            "%1$tF %1$tT %1$tZ\r\n\r\n", new Date(System.currentTimeMillis()));
         Set<Thread> allThreads = Thread.getAllStackTraces().keySet();

         for(ThreadInfo thread : threadBean.dumpAllThreads(true, true)) {
            writer.format(
               "\"%s\" Id=%d %s", thread.getThreadName(), thread.getThreadId(),
               thread.getThreadState());

            if(thread.getLockName() != null) {
               writer.format(" on %s", thread.getLockName());
            }

            if(thread.getLockOwnerName() != null) {
               writer.format(
                  " owned by \"%s\" Id=%d", thread.getLockOwnerName(),
                  thread.getLockOwnerId());
            }

            if(thread.isSuspended()) {
               writer.print(" (suspended)");
            }

            if(thread.isInNative()) {
               writer.print(" (in native)");
            }

            writer.print("\r\n");
            printStackTrace(thread, writer);

            for(Thread actualThread : allThreads) {
               if(actualThread.getId() == thread.getThreadId()) {
                  if(actualThread instanceof GroupedThread) {
                     GroupedThread groupedThread = (GroupedThread) actualThread;

                     if(groupedThread.getPrincipal() != null || groupedThread.hasRecords()) {
                        writer.print("\r\n\tThread context:\r\n");
                        writer.print("\t===============\r\n");

                        if(groupedThread.getPrincipal() != null) {
                           writer.format(
                              "\tUser: %s\r\n",
                              groupedThread.getPrincipal().getName());
                        }

                        for(Object record : groupedThread.getRecords()) {
                           writer.format("\t%s\r\n", record);
                        }
                     }
                  }

                  break;
               }
            }

            LockInfo[] locks = thread.getLockedSynchronizers();

            if(locks.length > 0) {
               writer.format(
                  "\r\n\tNumber of locked synchronizers = %d\r\n",
                  locks.length);

               for(LockInfo li : locks) {
                  writer.format("\t- %s\r\n", li);
               }
            }

            writer.print("\r\n");
         }

         long[] deadlocks = threadBean.findDeadlockedThreads();

         if(deadlocks != null && deadlocks.length > 0) {
            writer.print("Found Java-level deadlocks:\r\n");
            writer.print("===========================\r\n");

            ThreadInfo[] threads = threadBean.getThreadInfo(deadlocks);

            for(ThreadInfo thread : threads) {
               writer.format(
                  "\"%s\":\r\n  waiting to lock %s,\r\n  which is held by \"%s\"\r\n",
                  thread.getThreadName(), thread.getLockName(),
                  thread.getLockOwnerName());
            }

            writer.print("\r\n");
            writer.print(
               "Java stack information for the threads listed above:\r\n");
            writer.print(
               "====================================================\r\n");

            for(ThreadInfo thread : threads) {
               writer.format("\"%s\":\r\n", thread.getThreadName());
               printStackTrace(thread, writer);
            }
         }

         writer.close();
         result = buffer.toString();
      }
      catch(Exception e) {
         LOG.error("Failed to get thread dump", e);
      }

      return result;
   }

   public String getStackTrace(long id) {
      String stackTrace = null;
      ThreadMXBean tm = ManagementFactory.getThreadMXBean();
      ThreadInfo info = tm.getThreadInfo(id, Integer.MAX_VALUE);

      if(info != null) {
         stackTrace = Arrays.stream(info.getStackTrace())
            .map(Object::toString)
            .collect(Collectors.joining("\n "));
      }

      return stackTrace;
   }

   private void printStackTrace(ThreadInfo thread, PrintWriter writer) {
      StackTraceElement[] stackTrace = thread.getStackTrace();
      int i = 0;

      for(; i < stackTrace.length; i++) {
         StackTraceElement ste = stackTrace[i];
         writer.format("\tat %s\r\n", ste.toString());

         if(i == 0 && thread.getLockInfo() != null) {
            Thread.State ts = thread.getThreadState();

            switch(ts) {
            case BLOCKED:
               writer.format("\t-  blocked on %s\r\n", thread.getLockInfo());
               break;
            case WAITING:
            case TIMED_WAITING:
               writer.format("\t-  waiting on %s\r\n", thread.getLockInfo());
               break;
            default:
            }
         }

         for(MonitorInfo mi : thread.getLockedMonitors()) {
            if(mi.getLockedStackDepth() == i) {
               writer.format("\t-  locked %s\r\n", mi);
            }
         }
      }
   }

   /**
    * Creates a heap dump for the VM in the cache directory.
    *
    * @return the identifier used to access the heap dump.
    */
   public String createHeapDump() {
      if(heapDumpId != null) {
         throw new IllegalStateException("A heap dump is already in progress");
      }

      FileSystemService fileSystemService = FileSystemService.getInstance();
      heapDumpId = UUID.randomUUID().toString();
      final File inFile = fileSystemService.getCacheFile(heapDumpId + ".hprof");
      inFile.deleteOnExit();
      final File outFile = fileSystemService.getFile(
         inFile.getParentFile(), inFile.getName() + ".gz");
      outFile.deleteOnExit();

      new Thread(() -> {
         try {
            HotSpotDiagnosticMXBean bean =
               ManagementFactory.newPlatformMXBeanProxy(
                  ManagementFactory.getPlatformMBeanServer(),
                  "com.sun.management:type=HotSpotDiagnostic",
                  HotSpotDiagnosticMXBean.class);
            bean.dumpHeap(inFile.getAbsolutePath(), true);

            InputStream input = null;
            OutputStream output = null;

            try {
               input = new FileInputStream(inFile);
               output = new GZIPOutputStream(new FileOutputStream(outFile));
               Tool.copyTo(input, output);
            }
            finally {
               IOUtils.closeQuietly(input);
               IOUtils.closeQuietly(output);
            }
         }
         catch(Throwable exc) {
            LOG.error("Failed to create heap dump", exc);

            if(outFile.exists() && !outFile.delete()) {
               LOG.warn(
                  "Failed to delete temporary zipped heap dump file: " +
                     outFile);
            }
         }
         finally {
            heapDumpId = null;

            if(inFile.exists() && !inFile.delete()) {
               LOG.warn(
                  "Failed to delete temporary raw heap dump file: " +
                     inFile);
            }
         }
      }).start();

      return heapDumpId;
   }

   /**
    * Determines if the heap dump is complete.
    *
    * @param id the identifier returned from {@link #createHeapDump()}.
    *
    * @return <tt>true</tt> if complete; <tt>false</tt> otherwise.
    */
   public boolean isHeapDumpComplete(String id) {
      return !id.equals(heapDumpId);
   }

   /**
    * Determines if the heap dump is complete.
    *
    * @param id   the identifier returned from {@link #createHeapDump()}.
    * @param node the address of the cluster node.
    *
    * @return <tt>true</tt> if complete; <tt>false</tt> otherwise.
    */
   public boolean isHeapDumpComplete(String id, String node) throws Exception {
      if(node == null) {
         return isHeapDumpComplete(id);
      }

      IsHeapDumpCompleteMessage req = new IsHeapDumpCompleteMessage();
      req.setId(id);

      return cluster.exchangeMessages(node, req, e -> {
         Boolean result = null;

         if(e.getMessage() instanceof IsHeapDumpCompleteCompleteMessage) {
            IsHeapDumpCompleteCompleteMessage msg = (IsHeapDumpCompleteCompleteMessage) e.getMessage();

            if(msg.getId().equals(id)) {
               result = msg.isComplete();
            }
         }

         return result;
      });
   }

   /**
    * Gets the length of a heap dump.
    *
    * @param id the identifier returned from {@link #createHeapDump()}.
    *
    * @return the file length in bytes.
    */
   public int getHeapDumpLength(String id) {
      File file = FileSystemService.getInstance().getCacheFile( id + ".hprof.gz");
      return !file.isFile() ? 0 : (int) file.length();
   }

   /**
    * Gets the length of a heap dump.
    *
    * @param id   the identifier returned from {@link #createHeapDump()}.
    * @param node the address of the cluster node.
    *
    * @return the file length in bytes.
    */
   public int getHeapDumpLength(String id, String node) throws Exception {
      if(node == null) {
         return getHeapDumpLength(id);
      }

      GetHeapDumpLengthMessage req = new GetHeapDumpLengthMessage();
      req.setId(id);

      return cluster.exchangeMessages(node, req, e -> {
         Integer result = null;

         if(e.getMessage() instanceof GetHeapDumpLengthCompleteMessage) {
            GetHeapDumpLengthCompleteMessage msg = (GetHeapDumpLengthCompleteMessage) e.getMessage();

            if(msg.getId().equals(id)) {
               result = msg.getLength();
            }
         }

         return result;
      });
   }

   /**
    * Gets a block of content from a heap dump.
    *
    * @param id     the identifier returned from {@link #createHeapDump()}.
    * @param offset the byte offset in the heap dump file at which the
    *               content starts.
    * @param length the length of the content to retrieve in bytes.
    *
    * @return the file content.
    */
   public byte[] getHeapDumpContent(String id, int offset, int length) {
      File file = FileSystemService.getInstance().getCacheFile( id + ".hprof.gz");
      byte[] data = new byte[length];
      ByteBuffer buffer = ByteBuffer.wrap(data);
      RandomAccessFile raFile = null;
      FileChannel channel = null;

      try {
         raFile = new RandomAccessFile(file, "r");
         channel = raFile.getChannel();
         channel.read(buffer, (long) offset);
      }
      catch(Throwable exc) {
         LOG.error("Failed to read heap dump", exc);
      }
      finally {
         IOUtils.closeQuietly(channel);
         IOUtils.closeQuietly(raFile);
      }

      return data;
   }

   /**
    * Gets a block of content from a heap dump.
    *
    * @param id     the identifier returned from {@link #createHeapDump()}.
    * @param offset the byte offset in the heap dump file at which the
    *               content starts.
    * @param length the length of the content to retrieve in bytes.
    * @param node   the address of the cluster node.
    *
    * @return the file content.
    */
   public byte[] getHeapDumpContent(String id, int offset, int length, String node) throws Exception {
      if(node == null) {
         return getHeapDumpContent(id, offset, length);
      }

      GetHeapDumpContentMessage req = new GetHeapDumpContentMessage();
      req.setId(id);
      req.setOffset(offset);
      req.setLength(length);

      GetHeapDumpContentCompleteMessage message = cluster.exchangeMessages(node, req, e -> {
         GetHeapDumpContentCompleteMessage result = null;

         if(e.getMessage() instanceof GetHeapDumpContentCompleteMessage) {
            GetHeapDumpContentCompleteMessage msg = (GetHeapDumpContentCompleteMessage) e.getMessage();

            if(msg.getId().equals(id)) {
               result = msg;
            }
         }

         return result;
      });

      return message.getContent();
   }

   /**
    * Disposes of a heap dump.
    *
    * @param id the identifier returned from {@link #createHeapDump()}.
    */
   public void disposeHeapDump(String id) {
      File file = FileSystemService.getInstance().getCacheFile( id + ".hprof.gz");

      if(file.exists() && !file.delete()) {
         LOG.warn("Failed to delete heap dump file: " + file);
      }
   }

   /**
    * Disposes of a heap dump.
    *
    * @param id   the identifier returned from {@link #createHeapDump()}.
    * @param node the address of the cluster node.
    */
   public void disposeHeapDump(String id, String node) throws Exception {
      if(node == null) {
         disposeHeapDump(id);
      }

      DisposeHeapDumpMessage req = new DisposeHeapDumpMessage();
      req.setId(id);

      cluster.exchangeMessages(node, req, e -> {
         Boolean result = null;

         if(e.getMessage() instanceof DisposeHeapDumpCompleteMessage) {
            DisposeHeapDumpCompleteMessage msg = (DisposeHeapDumpCompleteMessage) e.getMessage();

            if(msg.getId().equals(id)) {
               result = true;
            }
         }

         return result;
      });
   }

   private final Cluster cluster;
   private String heapDumpId;
   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}