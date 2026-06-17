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
package inetsoft.util.script.graal;

import inetsoft.sree.SreeEnv;
import inetsoft.util.script.ScriptException;
import org.graalvm.polyglot.*;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GraalJS-based script engine. Replaces JavaScriptEngine (Rhino).
 * One shared Engine (process-wide code cache); one Context per instance,
 * guarded by a lock.
 */
public class GraalJavaScriptEngine implements AutoCloseable {
   private static final Engine SHARED_ENGINE = Engine.newBuilder()
      .allowExperimentalOptions(true)
      .option("engine.WarnInterpreterOnly", "false")
      .build();

   protected Context context;
   protected final ReentrantLock lock = new ReentrantLock();
   protected final ScriptTimeoutGuard timeoutGuard = new ScriptTimeoutGuard();
   protected boolean sql;

   private final ThreadLocal<Integer> errorCount = ThreadLocal.withInitial(() -> 0);
   private static final int MAX_ERRORS = 30000;

   private static final ScriptScope EMPTY_SCOPE = new ScriptScope() {
      public Object getMember(String n) { return null; }
      public boolean hasMember(String n) { return false; }
      public void putMember(String n, Object v) { }
      public Object[] getMemberKeys() { return new Object[0]; }
   };

   public void init(Map<String, Object> vars) throws Exception {
      lock.lock();

      try {
         if(context != null) {
            context.close(true);
         }

         context = Context.newBuilder("js")
            .engine(SHARED_ENGINE)
            .allowHostAccess(ScriptHostAccess.hostAccess())
            .allowHostClassLookup(ScriptHostAccess.classFilter())
            .allowIO(false)
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .allowCreateProcess(false)
            .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
            .build();

         initScope(vars);
      }
      finally {
         lock.unlock();
      }
   }

   /** Install engine globals. Overridden/extended by report + viewsheet layers. */
   protected void initScope(Map<String, Object> vars) {
      // engine globals (CALC, StyleConstant, XType, Chart, importExisting vars)
      // are installed here by subclasses / wiring tasks. Base impl publishes
      // the supplied vars.
      if(vars != null) {
         Value bindings = context.getBindings("js");

         for(Map.Entry<String, Object> e : vars.entrySet()) {
            bindings.putMember(e.getKey(), e.getValue());
         }
      }
   }

   public Object compile(String cmd) throws Exception {
      return compile(cmd, false);
   }

   public Object compile(String cmd, boolean fieldOnly) throws Exception {
      // store raw text; the with-wrap is applied per-exec against the live scope
      return Source.newBuilder("js", "with(__scope__){\n" + cmd + "\n}", "<cmd>")
         .buildLiteral();
   }

   public void checkFunction(String name, String cmd) throws Exception {
      lock.lock();

      try {
         context.parse("js", cmd); // parse-only; throws on syntax error
      }
      catch(PolyglotException ex) {
         throw new Exception("Syntax error in " + name + ": " + ex.getMessage(), ex);
      }
      finally {
         lock.unlock();
      }
   }

   public Object exec(Object script, Object scope, Object rscope) throws Exception {
      lock.lock();

      try {
         ScriptScope root = (scope instanceof ScriptScope) ? (ScriptScope) scope : null;
         BindingRootProxy proxy =
            new BindingRootProxy(root != null ? root : EMPTY_SCOPE,
                                 () -> null); // Task 5.1: wire FormulaContext::getExecScriptScope

         context.getBindings("js").putMember("__scope__", proxy);

         Duration timeout = currentTimeout();

         try(ScriptTimeoutGuard.Guard ignored = timeoutGuard.guard(context, timeout)) {
            if(errorCount.get() >= MAX_ERRORS) {
               return null;
            }

            Value result = context.eval((Source) script);
            return ScriptValueConverter.toHost(result);
         }
         catch(PolyglotException ex) {
            errorCount.set(errorCount.get() + 1);
            String loc = "";

            if(ex.getSourceLocation() != null) {
               loc = " (line " + ex.getSourceLocation().getStartLine() + ")";
            }

            throw new ScriptException(ex.getMessage() + loc, ex);
         }
      }
      finally {
         lock.unlock();
      }
   }

   protected Duration currentTimeout() {
      try {
         long secs = Long.parseLong(SreeEnv.getProperty("script.execution.timeout"));
         return Duration.ofSeconds(secs);
      }
      catch(Exception ex) {
         // SreeEnv unavailable in test context, or property not set → no timeout
         return Duration.ZERO;
      }
   }

   public void setSQL(boolean sql) { this.sql = sql; }
   public boolean isSQL() { return sql; }

   /**
    * Put a variable in the engine's global bindings.
    */
   public void put(String name, Object value) {
      lock.lock();

      try {
         if(context != null) {
            context.getBindings("js").putMember(name, value);
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Get a variable from the engine's global bindings.
    */
   public Object get(String name) {
      lock.lock();

      try {
         if(context != null) {
            Value v = context.getBindings("js").getMember(name);
            return v == null ? null : ScriptValueConverter.toHost(v);
         }

         return null;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Remove a variable from the engine's global bindings.
    */
   public void remove(String name) {
      lock.lock();

      try {
         if(context != null) {
            context.getBindings("js").removeMember(name);
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Get all member keys from the global bindings.
    */
   public Object[] getMemberKeys() {
      lock.lock();

      try {
         if(context == null) {
            return new Object[0];
         }

         Value bindings = context.getBindings("js");
         Set<String> keys = bindings.getMemberKeys();
         return keys.toArray();
      }
      finally {
         lock.unlock();
      }
   }

   @Override
   public void close() {
      lock.lock();

      try {
         if(context != null) {
            context.close(true);
            context = null;
         }
      }
      finally {
         lock.unlock();
      }
   }
}
