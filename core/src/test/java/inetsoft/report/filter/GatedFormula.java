/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.filter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * A sum formula whose getResult() parks the first caller for which the gate's condition holds,
 * so a test can hold a filter's computation at the point it reads the results. Clones share
 * the gate.
 */
final class GatedFormula implements Formula {
   GatedFormula(Gate gate) {
      this.gate = gate;
   }

   @Override
   public Object getResult() {
      gate.park();
      return sum.getResult();
   }

   @Override
   public double getDoubleResult() {
      gate.park();
      return sum.getDoubleResult();
   }

   @Override
   public void reset() {
      sum.reset();
   }

   @Override
   public void addValue(Object v) {
      sum.addValue(v);
   }

   @Override
   public void addValue(double v) {
      sum.addValue(v);
   }

   @Override
   public void addValue(double[] vs) {
      sum.addValue(vs);
   }

   @Override
   public void addValue(float v) {
      sum.addValue(v);
   }

   @Override
   public void addValue(long v) {
      sum.addValue(v);
   }

   @Override
   public void addValue(int v) {
      sum.addValue(v);
   }

   @Override
   public void addValue(short v) {
      sum.addValue(v);
   }

   @Override
   public boolean isNull() {
      return sum.isNull();
   }

   @Override
   public Object clone() {
      GatedFormula copy = new GatedFormula(gate);
      copy.sum = (SumFormula) sum.clone();
      return copy;
   }

   @Override
   public String getDisplayName() {
      return sum.getDisplayName();
   }

   @Override
   public String getName() {
      return sum.getName();
   }

   @Override
   public boolean isDefaultResult() {
      return sum.isDefaultResult();
   }

   @Override
   public void setDefaultResult(boolean def) {
      sum.setDefaultResult(def);
   }

   private final Gate gate;
   private SumFormula sum = new SumFormula();

   /**
    * Parks the first thread that calls park() once armed and the condition holds, until
    * open() is called.
    */
   static final class Gate {
      void arm(BooleanSupplier condition) {
         this.condition = condition;
         armed.set(true);
      }

      void park() {
         BooleanSupplier condition = this.condition;

         if(armed.get() && (condition == null || condition.getAsBoolean()) &&
            armed.compareAndSet(true, false))
         {
            parked = Thread.currentThread();
            entered.countDown();

            try {
               opened.await(CAP_SECONDS, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      }

      Thread awaitParked() throws InterruptedException {
         if(!entered.await(CAP_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("no thread reached the gate");
         }

         return parked;
      }

      void open() {
         opened.countDown();
      }

      private final AtomicBoolean armed = new AtomicBoolean();
      private final CountDownLatch entered = new CountDownLatch(1);
      private final CountDownLatch opened = new CountDownLatch(1);
      private volatile BooleanSupplier condition;
      private volatile Thread parked;
   }

   static final long CAP_SECONDS = 20;
}
