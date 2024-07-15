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
package inetsoft.util.script;

import inetsoft.util.Catalog;
import org.mozilla.javascript.*;

/**
 * TimeoutContext object represents a context, and times out on script
 * execution timeout.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
public class TimeoutContext extends Context {

   public TimeoutContext() {
      super(ContextFactory.getGlobal());
   }

   /**
    * Create a context.
    */
   public static Context enter() {
      Context cx = Context.getCurrentContext() != null || timeout == 0 && stackDepth == 0 ?
         Context.enter() : ContextFactory.getGlobal().enterContext(new TimeoutContext());

      // @by billh, fix customer bug bug1306850196268
      // do not change optimization level for time out, otherwise the result
      // of script execution might not be accepted by customer
      if(timeout > 0) {
         // cx.setOptimizationLevel(-1);
         cx.setInstructionObserverThreshold(10000);
      }

      if(stackDepth > 0) {
         // @by billh, fix customer bug bug1306850196268
         // do not change optimization level for time out, otherwise the
         // result of script execution might not be accepted by customer
         // cx.setOptimizationLevel(-1);
         cx.setMaximumInterpreterStackDepth(stackDepth);
      }

      cx.setWrapFactory(factory);

      return cx;
   }

   /**
    * Start clock.
    */
   public static void startClock() {
      if(timeout <= 0) {
         return;
      }

      startTime = System.currentTimeMillis();
   }

   /**
    * Set script execution timeout value in seconds.
    * No limit is placed on the query if the value is zero.
    */
   public static void setTimeout(long timeout0) {
      timeout = timeout0;

      if(timeout > 0) {
         TimeoutContext.enter();
      }
   }

   /**
    * Get script execution timeout value in seconds.
    */
   public static long getTimeout() {
      return timeout;
   }

   /**
    * Set script execution stackDepth value.
    * No limit is placed on the query if the value is zero.
    */
   public static void setStackDepth(int maxStackDepth) {
      stackDepth = maxStackDepth;

      if(stackDepth > 0) {
         TimeoutContext.enter();
      }
   }

   /**
    * Get script execution stackDepth value.
    */
   public static int getStackDepth() {
      return stackDepth;
   }

   /**
    * Check if script execution timeout.
    */
   @Override
   public void observeInstructionCount(int instructionCount) {
      if(timeout > 0 && startTime > 0) {
         long currentTime = System.currentTimeMillis();
         long executionTime = currentTime - startTime;

         if(executionTime > timeout * 1000) {
            String msg = Catalog.getCatalog().getString(
               "designer.script.TimeoutWarning", timeout + "",
               executionTime + "");

            startTime = -1;
            throw new ScriptException(msg);
         }
      }
   }

   @Override
   public boolean hasFeature(int featureIndex) {
      if(featureIndex == Context.FEATURE_DYNAMIC_SCOPE) {
         return true;
      }

      return super.hasFeature(featureIndex);
   }

   private static long startTime = -1;
   private static long timeout = -1;
   private static int stackDepth = -1;

   private static WrapFactory factory = new JSFactory();
}
