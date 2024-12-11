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
package inetsoft.util;

import com.sun.jna.Platform;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.util.affinity.AffinitySupport;
import inetsoft.util.audit.*;
import inetsoft.util.log.LogContext;
import org.slf4j.*;

import java.security.Principal;
import java.util.*;
import java.util.function.*;

/**
 * A utility class for creating a thread that is in the same thread group as
 * its creator.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class GroupedThread extends Thread {
   /**
    * Create a thread that is in the same thread group as its creator.
    */
   public GroupedThread() {
      this(ThreadContext.getContextPrincipal());
   }

   /**
    * Create a thread that is in the same thread group as its creator.
    *
    * @param name the name of the thread.
    */
   public GroupedThread(String name) {
      this(name, ThreadContext.getContextPrincipal());
   }

   /**
    * Create a thread that is in the same thread group as its creator.
    *
    * @param user the principal that is associated with the thread.
    */
   public GroupedThread(Principal user) {
      this("GroupedThread", user);
   }

   /**
    * Create a thread that is in the same thread group as its creator.
    *
    * @param name the name of the thread.
    * @param user the principal that is associated with the thread.
    */
   public GroupedThread(String name, Principal user) {
      super(name);

      // @by jasons, Per the documentation, super.getStackTrace() will return
      // an empty list. The intended behavior is to record the stack trace when
      // the thread is created, so using the current thread's stack trace is
      // correct anyway
      this.stackTrace = Thread.currentThread().getStackTrace();
      this.records = new LinkedHashSet<>();
      this.user = user;

      // if user is null, try getting principal from context
      if(this.user == null) {
         this.user = ThreadContext.getContextPrincipal();
      }

      if(Thread.currentThread() instanceof GroupedThread) {
         GroupedThread pthread = (GroupedThread) Thread.currentThread();
         parentStackTrace = pthread.stackTrace;
      }
   }

   /**
    * Create a thread that is in the same thread group as its creator.
    *
    * @param runnable a Runnable instance
    */
   public GroupedThread(Runnable runnable) {
      this(runnable, ThreadContext.getContextPrincipal());
   }

   /**
    * Create a thread that is in the same thread group as its creator.
    *
    * @param runnable a Runnable instance
    * @param name     the name of the thread.
    */
   public GroupedThread(Runnable runnable, String name) {
      this(runnable, name, ThreadContext.getContextPrincipal());
   }

   /**
    * Create a thread that is in the same thread group as its creator.
    *
    * @param runnable a Runnable instance
    * @param user     the principal that is associated with the thread.
    */
   public GroupedThread(Runnable runnable, Principal user) {
      this(runnable, "GroupedThread", user);
   }

   /**
    * Create a thread that is in the same thread group as its creator.
    *
    * @param runnable a Runnable instance
    * @param name     the name of the thread.
    * @param user     the principal that is associated with the thread.
    */
   public GroupedThread(Runnable runnable, String name, Principal user) {
      super(runnable, name);

      // @by jasons, Per the documentation, super.getStackTrace() will return
      // an empty list. The intended behavior is to record the stack trace when
      // the thread is created, so using the current thread's stack trace is
      // correct anyway
      this.stackTrace = Thread.currentThread().getStackTrace();
      this.records = new LinkedHashSet<>();
      this.user = user;

      // if user is null, try getting principal from context
      if(this.user == null) {
         this.user = ThreadContext.getContextPrincipal();
      }

      if(Thread.currentThread() instanceof GroupedThread) {
         GroupedThread pthread = (GroupedThread) Thread.currentThread();
         parentStackTrace = pthread.stackTrace;
      }
   }

   /**
    * Cannot be overridden because pre-processing must be performed. Instead, override
    * {@link #doRun()}.
    */
   @Override
   public final void run() {
      // update the affinity for every thread we create (POSIX)
      if(!Platform.isWindows() && !Platform.isMac() && LicenseManager.getInstance().isAffinitySet())
      {
         final AffinitySupport affinitySupport = AffinitySupport.FACTORY.getInstance();

         try {
            final int[] affinity = affinitySupport.getAffinity();
            affinitySupport.setThreadAffinity(affinity);
         }
         catch(Exception e) {
            if(!affinityErrorLogged) {
               LOG.error("Failed to set group thread affinity", e);
               affinityErrorLogged = true;
            }
         }
      }

      doRun();
   }

   /**
    * Called from {@link #run()} after pre-processing has completed.
    */
   protected void doRun() {
      super.run();
   }

   /**
    * Gets the stack trace from when this thread was created or when the
    * current runnable was created.
    *
    * @return the stack trace.
    */
   public StackTraceElement[] getCreatedStackTrace() {
      return stackTrace;
   }

   /**
    * Sets the stack trace from when this thread was created or when the
    * current runnable was created.
    *
    * @param stackTrace the stack trace.
    */
   void setCreatedStackTrace(StackTraceElement[] stackTrace) {
      this.stackTrace = stackTrace;
   }

   /**
    * Gets the stack trace of the parent thread from when this thread was
    * created or when the current runnable was created.
    *
    * @return the stack trace.
    */
   public StackTraceElement[] getParentStackTrace() {
      return parentStackTrace;
   }

   /**
    * Sets the stack trace of the parent thread from when this thread was
    * created or when the current runnable was created.
    *
    * @param parentStackTrace the stack trace.
    */
   void setParentStackTrace(StackTraceElement[] parentStackTrace) {
      this.parentStackTrace = parentStackTrace;
   }

   /**
    * Set principal of the grouped thread.
    */
   public void setPrincipal(Principal user) {
      this.user = user;

      if(user == null) {
         MDC.remove(LogContext.USER.name());
         MDC.remove(LogContext.GROUP.name());
         MDC.remove(LogContext.ROLE.name());
      }
      else {
         boolean enterprise = LicenseManager.getInstance().isEnterprise();
         IdentityID userIdentity = IdentityID.getIdentityIDFromKey(user.getName());
         String name = enterprise ? user.getName() : userIdentity != null ? userIdentity.getName() : "";
         MDC.put(LogContext.USER.name(), name);

         if(user instanceof XPrincipal principal) {
            String groups = String.join(",",
               Arrays.stream(principal.getGroups())
                  .map(g -> enterprise ?
                     new IdentityID(g, userIdentity.getOrgID()).convertToKey() : g)
                  .toArray(String[]::new));
            String roles = String.join(",",
               Arrays.stream(principal.getRoles())
                  .map(r -> enterprise ? r.convertToKey() : r.getName())
                  .toArray(String[]::new));

            if(!groups.equals("")) {
               MDC.put(LogContext.GROUP.name(), groups);
            }
            else {
               MDC.remove(LogContext.GROUP.name());
            }

            if(!roles.equals("")) {
               MDC.put(LogContext.ROLE.name(), roles);
            }
            else {
               MDC.remove(LogContext.ROLE.name());
            }
         }
      }
   }

   /**
    * Get principal of the grouped thread.
    */
   public Principal getPrincipal() {
      return user;
   }

   /**
    * Set parent of the grouped thread.
    */
   public void setParent(GroupedThread parent) {
      this.parent = parent;
   }

   /**
    * Get parent of the grouped thread.
    */
   public GroupedThread getParent() {
      return parent;
   }

   /**
    * Get the caller's stack trace.
    */
   @Override
   public StackTraceElement[] getStackTrace() {
      StackTraceElement[] currentStackTrace;

      // @by jasons, this method is supposed to return the current stack trace
      // for the caller. In order to do this, we need to include
      // super.getStackTrace() as well the stack trace recorded in the
      // constructor
      try {
         currentStackTrace = super.getStackTrace();
      }
      catch(Throwable ex) {
         currentStackTrace = (new Exception()).getStackTrace();
      }

      int len = (parentStackTrace == null ? 0 : parentStackTrace.length) +
         (stackTrace == null ? 0 : stackTrace.length) +
         currentStackTrace.length;
      int offset = 0;

      StackTraceElement[] result = new StackTraceElement[len];

      System.arraycopy(
         currentStackTrace, 0, result, 0, currentStackTrace.length);
      offset += currentStackTrace.length;

      if(stackTrace != null) {
         System.arraycopy(stackTrace, 0, result, offset, stackTrace.length);
         offset += stackTrace.length;
      }

      if(parentStackTrace != null) {
         System.arraycopy(
            parentStackTrace, 0, result, offset, parentStackTrace.length);
      }

      return result;
   }

   public Object addRecord(LogContext context, String name) {
      Object record = context.getRecord(name);
      addRecord(record);
      return record;
   }

   /**
    * Add the record.
    */
   public void addRecord(Object record) {
      if(parent != null) {
         parent.addRecord(record);
      }

      records.add(record);
      updateMDC(record, true);
   }

   /**
    * Get the record.
    */
   public AuditRecord getRecord(AuditRecord record) {
      if(parent != null) {
         return parent.getRecord(record);
      }

      for(Object record1 : records) {
         if(!(record1 instanceof AuditRecord)) {
            continue;
         }

         AuditRecord arecord = (AuditRecord) record1;

         if(record.equals(arecord)) {
            return arecord;
         }
      }

      return null;
   }

   public Iterable<Object> getRecords() {
      if(parent != null) {
         return parent.getRecords();
      }

      return new LinkedHashSet<>(records);
   }

   public String getRecord(LogContext context) {
      if(parent != null) {
         return parent.getRecord(context);
      }

      return MDC.get(context.name());
   }

   /**
    * Remove the specified record.
    */
   public void removeRecord(Object record) {
      if(parent != null) {
         parent.removeRecord(record);
      }

      records.remove(record);
      updateMDC(record, false);
   }

   public void removeRecords(LogContext context) {
      for(Iterator<Object> i = records.iterator(); i.hasNext();) {
         Object record = i.next();

         if(LogContext.findMatchingContext(record) == context) {
            if(parent != null) {
               parent.removeRecord(record);
            }

            i.remove();
         }
      }

      MDC.remove(context.name());
   }

   /**
    * Remove all the records.
    */
   public void removeRecords() {
      for(Object record : records) {
         if(parent != null) {
            parent.removeRecord(record);
         }

         updateMDC(record, false);
      }

      records.clear();
   }

   public boolean hasRecords() {
      if(parent != null) {
         return !parent.records.isEmpty();
      }

      return !records.isEmpty();
   }

   public static <T> T runWithRecordContext(Supplier<Collection<?>> recordFn, ContextRunner<T> fn)
      throws Exception
   {
      Set<Object> records = new HashSet<>();
      Map<String, String> context = MDC.getCopyOfContextMap();

      withGroupedThread(thread -> {
         records.addAll(recordFn.get());
         records.forEach(thread::addRecord);
      });

      T result = fn.run();

      // only remove records if an exception is not going to be propagated or else the context
      // information will be lost by the time the exception is logged
      withGroupedThread(thread -> records.forEach(thread::removeRecord));

      if(context != null) {
         // completely restore log context because of key collisions
         MDC.setContextMap(context);
      }

      return result;
   }

   public static <T> T applyGroupedThread(Function<GroupedThread, T> fn) {
      if(Thread.currentThread() instanceof GroupedThread) {
         return fn.apply((GroupedThread) Thread.currentThread());
      }

      return null;
   }

   public static void withGroupedThread(Consumer<GroupedThread> fn) {
      applyGroupedThread(v -> {
         fn.accept(v);
         return null;
      });
   }

   private void updateMDC(Object record, boolean add) {
      String key = null;
      String value = null;

      if(record instanceof String) {
         LogContext context = LogContext.findMatchingContext(record);

         if(context != null) {
            key = context.name();
            value = context.getRecordName(record);
         }
      }
      else if(record instanceof QueryRecord) {
         String type = ((QueryRecord) record).getObjectType();
         value = ((QueryRecord) record).getObjectName();

         if(QueryRecord.OBJECT_TYPE_QUERY.equals(type)) {
            key = LogContext.QUERY.name();
         }
         else if(QueryRecord.OBJECT_TYPE_MODEL.equals(type)) {
            key = LogContext.MODEL.name();
         }
         else if(QueryRecord.OBJECT_TYPE_WORKSHEET.equals(type)) {
            key = LogContext.WORKSHEET.name();
         }
      }
      else if(record instanceof ExecutionRecord) {
         String type = ((ExecutionRecord) record).getObjectType();
         value = ((ExecutionRecord) record).getObjectName();

         if(ExecutionRecord.OBJECT_TYPE_VIEW.equals(type)) {
            key = LogContext.DASHBOARD.name();
         }
      }

      if(key != null) {
         if(add) {
            MDC.put(key, value);
         }
         else {
            MDC.remove(key);
         }
      }
   }

   /**
    * Starts this thread.
    */
   @Override
   public void start() {
      if(!shutdown) {
         synchronized(liveThreads) {
            liveThreads.put(this, null);
         }

         super.start();
      }
   }

   /**
    * Dispose this grouped thread.
    */
   public void dispose() {
      synchronized(liveThreads) {
         liveThreads.remove(this);
      }
   }

   /**
    * Cancels the execution of this thread.
    */
   public void cancel() {
      LOG.debug("Cancelling thread: {} {}", this, getClass());
      cancelled = true;

      if(isAlive()) {
         // this may cause a problem if any threads are swallowing exceptions
         // in a loop
         interrupt();
      }

      try {
         join(1000);
      }
      catch(InterruptedException ignore) {
         // ignored
      }

      LOG.debug("Cancelled thread: {}, {}", this, getClass());
   }

   /**
    * Determines if this thread has been cancelled.
    *
    * @return <tt>true</tt> if cancelled; <tt>false</tt> otherwise.
    */
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Cancels all currently running threads.
    */
   public static void cancelAll() {
      LOG.debug("Shutting down all GroupedThread instances");

      if(!shutdown) {
         shutdown = true;

         synchronized(liveThreads) {
            for(Iterator<GroupedThread> i = liveThreads.keySet().iterator();
                i.hasNext();)
            {
               GroupedThread thread = i.next();

               if(thread != null) {
                  thread.cancel();
                  i.remove();
               }
            }
         }
      }
   }

   private Principal user;
   private GroupedThread parent;
   private StackTraceElement[] parentStackTrace;
   private StackTraceElement[] stackTrace;
   private final Set<Object> records;
   private boolean cancelled;
   private static final Map<GroupedThread, Object> liveThreads = new WeakHashMap<>();
   private static boolean shutdown = false;
   private static boolean affinityErrorLogged = false;

   private static final Logger LOG = LoggerFactory.getLogger(GroupedThread.class);

   @FunctionalInterface
   public interface ContextRunner<T> {
      T run() throws Exception;
   }
}
