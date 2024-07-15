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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@ManagedResource
public class ServerMonitorMBean {
   @Autowired()
   public ServerMonitorMBean(ServerService serverService) {
      this.serverService = serverService;
   }

   /**
    * Get the server start date.
    */
   @ManagedAttribute
   public Date getStartDate() {
      return serverService.getStartDate();
   }

   /**
    * Get server uptime.
    * This is an internal method.
    */
   @ManagedAttribute
   public Long getUpTime() {
      return serverService.getUpTime();
   }

   /**
    * Gets a dump of the current stack traces for all threads.
    *
    * @return the stack dump.
    *
    * @since 12.0
    */
   @ManagedOperation
   public String getThreadDump() {
      return serverService.getThreadDump();
   }

   /**
    * Creates a heap dump for the VM in the cache directory.
    *
    * @return the identifier used to access the heap dump.
    *
    * @since 12.0
    */
   @ManagedOperation
   public String createHeapDump() {
      return serverService.createHeapDump();
   }

   /**
    * Determines if the heap dump is complete.
    *
    * @param id the identifier returned from {@link #createHeapDump()}.
    *
    * @return <tt>true</tt> if complete; <tt>false</tt> otherwise.
    *
    * @since 12.0
    */
   @ManagedOperation
   public boolean isHeapDumpComplete(String id) {
      return serverService.isHeapDumpComplete(id);
   }

   /**
    * Gets the length of a heap dump.
    *
    * @param id the identifier returned from {@link #createHeapDump()}.
    *
    * @return the file length in bytes.
    *
    * @since 12.0
    */
   @ManagedOperation
   public int getHeapDumpLength(String id) {
      return serverService.getHeapDumpLength(id);
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
    *
    * @since 12.0
    */
   @ManagedOperation
   public byte[] getHeapDumpContent(String id, int offset, int length) {
      return serverService.getHeapDumpContent(id, offset, length);
   }

   /**
    * Disposes of a heap dump.
    *
    * @param id the identifier returned from {@link #createHeapDump()}.
    *
    * @since 12.0
    */
   @ManagedOperation
   public void disposeHeapDump(String id) {
      serverService.disposeHeapDump(id);
   }

   /**
    * Rotates the log file. When the log file is rotated, a backup of the
    * current log file is created and a new log file is started.
    *
    * @since 12.0
    */
   @ManagedOperation
   public void rotateLogFile() {
      serverService.rotateLogFile();
   }

   private final ServerService serverService;
}
