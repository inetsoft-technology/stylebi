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
import inetsoft.util.Tool;
import inetsoft.web.admin.server.GetHeapDumpTransferCompleteMessage;
import inetsoft.web.admin.server.ServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Scheduler thread-dump (plain synchronous read) and heap-dump (kick-off+poll) primitives for the
 * scheduler-status admin-plugin area (track-status/01-design.md section 3b, and the "Revision
 * (heap dump...)" section which supersedes the original design's bare-kickoff proposal).
 *
 * <p>Both operations resolve their target node the same way
 * {@code ServerMonitoringController.writeSchedulerThreadDump}/{@code writeSchedulerHeapDump}
 * already do: iterate {@code Cluster.getClusterNodes()}, keep the ones tagged
 * {@code scheduler=true}, and match an explicit {@code clusterNode} filter against the node's own
 * IP (the part before the first {@code ':'}). Unlike that existing code, an OMITTED
 * {@code clusterNode} with MORE THAN ONE scheduler-tagged node is refused loud, naming the
 * candidates, rather than silently returning the first match -- the existing raw endpoint's own
 * silent-pick-first behavior is a real, closed-here ambiguity gap (this repo's CLAUDE.md
 * tool-misuse doctrine), not something this area inherits.
 *
 * <p>Heap dump calls {@link ServerService#createHeapDump}/{@link ServerService#isHeapDumpComplete}/
 * {@link ServerService#getHeapDumpInfo}/{@link ServerService#disposeHeapDump} directly -- the same
 * primitives {@code ServerMonitoringController.writeHeapDumpResponse} already uses internally, in
 * the same order, but exposed here as two independent REST calls (kick-off, then poll) instead of
 * one blocking background thread with no HTTP-visible signal. This is genuinely new server-side
 * logic (the materialization step: fetch the transfer file, write it into external storage under
 * {@code "heapdump/"}, dispose the id), not a thin wrapper.
 */
@Component
public class SchedulerDiagnosticsService {
   @Autowired
   public SchedulerDiagnosticsService(Cluster cluster, ServerService serverService,
                                      ExternalStorageService externalStorageService)
   {
      this.cluster = cluster;
      this.serverService = serverService;
      this.externalStorageService = externalStorageService;
   }

   public String getThreadDump(String clusterNode) throws Exception {
      String node = resolveSchedulerNode(clusterNode);
      return serverService.getThreadDump(node);
   }

   public SchedulerHeapDumpToken createHeapDump(String clusterNode) throws Exception {
      String node = resolveSchedulerNode(clusterNode);
      String heapId = serverService.createHeapDump(node);
      return new SchedulerHeapDumpToken(heapId, node);
   }

   /**
    * Polls a heap dump kicked off by {@link #createHeapDump}. Once complete, performs the same
    * materialization {@code ServerMonitoringController.writeHeapDumpResponse} does (fetch the
    * transfer file, write it into external storage, dispose the id) and caches the result so a
    * repeat poll for the same token is answered from the cache rather than re-running that
    * materialization a second time (see {@link SchedulerHeapDumpStatus}'s own javadoc for why).
    */
   public SchedulerHeapDumpStatus getHeapDumpStatus(String token, String clusterNode) {
      SchedulerHeapDumpStatus cached = completed.get(token);

      if(cached != null) {
         return cached;
      }

      boolean complete;

      try {
         complete = serverService.isHeapDumpComplete(token, clusterNode);
      }
      catch(Exception e) {
         return new SchedulerHeapDumpStatus(token, true, true, messageOf(e), null);
      }

      if(!complete) {
         return new SchedulerHeapDumpStatus(token, false, false, null, null);
      }

      SchedulerHeapDumpStatus result;

      try {
         String storagePath = materialize(token, clusterNode);
         result = new SchedulerHeapDumpStatus(token, true, false, null, storagePath);
      }
      catch(Exception e) {
         result = new SchedulerHeapDumpStatus(token, true, true, messageOf(e), null);
      }

      completed.put(token, result);
      return result;
   }

   /** Mirrors {@code ServerMonitoringController.writeHeapDumpResponse}: fetch the transfer file,
    * write it into external storage under {@code "heapdump/"}, then always dispose the heap dump id
    * -- regardless of whether the fetch/write itself succeeds, since {@code createHeapDump} has
    * already reserved server-side resources for this id once it reports complete. */
   private String materialize(String heapId, String clusterNode) throws Exception {
      String fileName = getClusterFileName(clusterNode, "HeapDump", ".hprof.gz");
      File file = null;

      try {
         GetHeapDumpTransferCompleteMessage info = serverService.getHeapDumpInfo(heapId, clusterNode);

         if(info.getLink() == null) {
            throw new IllegalStateException("the heap dump completed but produced no transfer link");
         }

         file = cluster.getTransferFile(info.getLink());

         if(file == null || !file.exists()) {
            throw new IllegalStateException(
               "the heap dump completed but its transfer file is missing");
         }

         String storagePath = externalStorageService.getAvailableFile("heapdump/" + fileName, 1);
         externalStorageService.write(storagePath, file.toPath(), null);
         return storagePath;
      }
      finally {
         if(file != null && file.exists()) {
            Tool.deleteFile(file);
         }

         serverService.disposeHeapDump(heapId, clusterNode);
      }
   }

   /** Same convention as {@code ServerMonitoringController.getClusterFileName} (private there, so
    * reimplemented here rather than shared -- matches this run's established precedent of small,
    * self-contained duplication across independent admin-chat areas over reaching into another
    * controller's private helper, e.g. {@code ClusterStatusLabel}). */
   private static String getClusterFileName(String clusterNode, String prefix, String suffix) {
      if(clusterNode == null) {
         return prefix + suffix;
      }

      String node = clusterNode;
      int index = node.indexOf(':');

      if(index >= 0) {
         node = node.substring(0, index);
      }

      String fileName = String.format("%s-%s%s", prefix, node.replace('.', '_'), suffix);

      if(!Tool.isFilePathValid(fileName)) {
         fileName = "invalid";
      }

      return fileName;
   }

   /**
    * Resolves {@code clusterNode} (an optional IP filter) to the full cluster node address
    * {@link ServerService}'s own methods expect, restricted to nodes tagged {@code scheduler=true}
    * -- mirrors {@code ServerMonitoringController.writeSchedulerThreadDump}/
    * {@code writeSchedulerHeapDump}'s own loop exactly, plus the ambiguity refusal this class's own
    * javadoc describes.
    *
    * @return the resolved node address, or {@code null} (meaning "local") when no node is tagged
    *         {@code scheduler=true} at all -- the same convention {@link ServerService#getThreadDump}/
    *         {@link ServerService#createHeapDump} already use for their own {@code node} parameter.
    */
   private String resolveSchedulerNode(String clusterNode) {
      List<String> candidates = schedulerNodes();

      if(clusterNode != null && !clusterNode.isEmpty()) {
         for(String node : candidates) {
            if(clusterNode.equals(nodeIp(node))) {
               return node;
            }
         }

         throw new IllegalArgumentException(
            "clusterNode: \"" + clusterNode + "\" does not name a scheduler-tagged cluster node" +
            (candidates.isEmpty() ? "" : " -- known scheduler nodes: " + candidateIps(candidates)));
      }

      if(candidates.size() > 1) {
         throw new IllegalArgumentException(
            "clusterNode: required -- more than one scheduler-tagged cluster node exists (" +
            candidateIps(candidates) + "); name one explicitly. The raw Enterprise Manager " +
            "endpoint silently returns only the first match in this situation; this area refuses " +
            "instead so a caller is never left thinking a single dump covers every scheduler node.");
      }

      return candidates.isEmpty() ? null : candidates.get(0);
   }

   private List<String> schedulerNodes() {
      List<String> nodes = new ArrayList<>();

      for(String node : cluster.getClusterNodes()) {
         if(Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler"))) {
            nodes.add(node);
         }
      }

      return nodes;
   }

   private static String candidateIps(List<String> nodes) {
      return nodes.stream().map(SchedulerDiagnosticsService::nodeIp).collect(Collectors.joining(", "));
   }

   private static String nodeIp(String node) {
      return node != null && node.contains(":") ? node.substring(0, node.indexOf(":")) : node;
   }

   private static String messageOf(Exception e) {
      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private final Cluster cluster;
   private final ServerService serverService;
   private final ExternalStorageService externalStorageService;
   /** Completed heap-dump results, keyed by token -- see {@link SchedulerHeapDumpStatus}'s own
    * javadoc for why a repeat poll is answered from here instead of re-running materialization. */
   private final ConcurrentMap<String, SchedulerHeapDumpStatus> completed = new ConcurrentHashMap<>();
}
