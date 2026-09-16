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
package inetsoft.web.admin.ai.schedulerstatus;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.ExternalStorageService;
import inetsoft.web.admin.server.GetHeapDumpTransferCompleteMessage;
import inetsoft.web.admin.server.ServerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * track-status/01-design.md section 3b (thread dump: the silent-pick-first ambiguity this area
 * closes) and the "Revision (heap dump...)" section (kick-off+poll, and the repeat-poll-after-
 * disposal idempotency decision this class's own javadoc documents).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class SchedulerDiagnosticsServiceTest {
   @Mock private Cluster cluster;
   @Mock private ServerService serverService;
   @Mock private ExternalStorageService externalStorageService;

   private SchedulerDiagnosticsService service;

   @TempDir private Path tempDir;

   @BeforeEach void setUp() {
      service = new SchedulerDiagnosticsService(cluster, serverService, externalStorageService);
   }

   // -------------------------------------------------------------------------
   // thread dump -- node resolution and the ambiguity refusal
   // -------------------------------------------------------------------------

   @Test void threadDumpUsesTheSoleSchedulerNodeWhenClusterNodeOmitted() throws Exception {
      stubSchedulerNodes("10.0.0.1:8080");
      when(serverService.getThreadDump("10.0.0.1:8080")).thenReturn("dump contents");

      assertEquals("dump contents", service.getThreadDump(null));
      verify(serverService).getThreadDump("10.0.0.1:8080");
   }

   @Test void threadDumpRefusesLoudWhenClusterNodeOmittedAndMultipleSchedulerNodesExist() {
      stubSchedulerNodes("10.0.0.1:8080", "10.0.0.2:8080");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.getThreadDump(null));
      assertTrue(ex.getMessage().contains("10.0.0.1"));
      assertTrue(ex.getMessage().contains("10.0.0.2"));
   }

   @Test void threadDumpResolvesAnExplicitClusterNodeByIp() throws Exception {
      stubSchedulerNodes("10.0.0.1:8080", "10.0.0.2:8080");
      when(serverService.getThreadDump("10.0.0.2:8080")).thenReturn("node 2 dump");

      assertEquals("node 2 dump", service.getThreadDump("10.0.0.2"));
   }

   @Test void threadDumpRefusesAnUnrecognizedExplicitClusterNode() {
      stubSchedulerNodes("10.0.0.1:8080");
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.getThreadDump("10.0.0.9"));
      assertTrue(ex.getMessage().contains("10.0.0.9"));
   }

   @Test void threadDumpFallsBackToLocalWhenNoSchedulerNodeIsTagged() throws Exception {
      when(cluster.getClusterNodes()).thenReturn(Set.of());
      when(serverService.getThreadDump(null)).thenReturn("local dump");

      assertEquals("local dump", service.getThreadDump(null));
   }

   // -------------------------------------------------------------------------
   // heap dump kick-off
   // -------------------------------------------------------------------------

   @Test void createHeapDumpReturnsTheResolvedNodeAlongsideTheToken() throws Exception {
      stubSchedulerNodes("10.0.0.1:8080");
      when(serverService.createHeapDump("10.0.0.1:8080")).thenReturn("heap-123");

      SchedulerHeapDumpToken token = service.createHeapDump(null);
      assertEquals("heap-123", token.token());
      assertEquals("10.0.0.1:8080", token.node());
   }

   // -------------------------------------------------------------------------
   // heap dump poll -- not yet complete, complete+materialize, and failure paths
   // -------------------------------------------------------------------------

   @Test void heapDumpStatusReportsIncompleteWithoutMaterializing() throws Exception {
      when(serverService.isHeapDumpComplete("heap-1", "node-a")).thenReturn(false);

      SchedulerHeapDumpStatus status = service.getHeapDumpStatus("heap-1", "node-a");
      assertFalse(status.complete());
      assertFalse(status.failed());
      assertNull(status.storagePath());
      verify(serverService, never()).getHeapDumpInfo(any(), any());
   }

   @Test void heapDumpStatusMaterializesAndDisposesOnceCompleteThenCachesTheResult() throws Exception {
      File dumpFile = Files.createFile(tempDir.resolve("dump.hprof")).toFile();
      GetHeapDumpTransferCompleteMessage info = new GetHeapDumpTransferCompleteMessage();
      info.setLink("transfer-link-1");

      when(serverService.isHeapDumpComplete("heap-1", "node-a")).thenReturn(true);
      when(serverService.getHeapDumpInfo("heap-1", "node-a")).thenReturn(info);
      when(cluster.getTransferFile("transfer-link-1")).thenReturn(dumpFile);
      when(externalStorageService.getAvailableFile(startsWith("heapdump/"), eq(1)))
         .thenReturn("heapdump/HeapDump-node_a.hprof.gz");

      SchedulerHeapDumpStatus status = service.getHeapDumpStatus("heap-1", "node-a");
      assertTrue(status.complete());
      assertFalse(status.failed());
      assertEquals("heapdump/HeapDump-node_a.hprof.gz", status.storagePath());
      verify(serverService).disposeHeapDump("heap-1", "node-a");
      verify(externalStorageService).write(eq("heapdump/HeapDump-node_a.hprof.gz"), any(), isNull());

      // A repeat poll for the same token must not call isHeapDumpComplete/getHeapDumpInfo/
      // disposeHeapDump a second time -- the idempotent-cache decision this class's own javadoc
      // documents (the repeat-poll-after-disposal open question from the design revision).
      SchedulerHeapDumpStatus again = service.getHeapDumpStatus("heap-1", "node-a");
      assertEquals(status, again);
      verify(serverService, times(1)).isHeapDumpComplete("heap-1", "node-a");
      verify(serverService, times(1)).disposeHeapDump("heap-1", "node-a");
   }

   @Test void heapDumpStatusReportsFailedButStillDisposesWhenTransferLinkIsMissing() throws Exception {
      GetHeapDumpTransferCompleteMessage info = new GetHeapDumpTransferCompleteMessage();
      // link left null -- the completed-but-no-transfer-file case.

      when(serverService.isHeapDumpComplete("heap-1", "node-a")).thenReturn(true);
      when(serverService.getHeapDumpInfo("heap-1", "node-a")).thenReturn(info);

      SchedulerHeapDumpStatus status = service.getHeapDumpStatus("heap-1", "node-a");
      assertTrue(status.complete());
      assertTrue(status.failed());
      assertNotNull(status.error());
      assertNull(status.storagePath());
      verify(serverService).disposeHeapDump("heap-1", "node-a");
   }

   @Test void heapDumpStatusReportsFailedWhenIsCompleteItselfThrows() throws Exception {
      when(serverService.isHeapDumpComplete("heap-1", "node-a"))
         .thenThrow(new IllegalStateException("no such heap dump"));

      SchedulerHeapDumpStatus status = service.getHeapDumpStatus("heap-1", "node-a");
      assertTrue(status.complete());
      assertTrue(status.failed());
      assertEquals("no such heap dump", status.error());
      verify(serverService, never()).disposeHeapDump(any(), any());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubSchedulerNodes(String... nodes) {
      when(cluster.getClusterNodes()).thenReturn(Set.of(nodes));

      for(String node : nodes) {
         lenient().when(cluster.getClusterNodeProperty(node, "scheduler")).thenReturn(Boolean.TRUE);
      }
   }
}
