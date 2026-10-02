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

package inetsoft.report.lens;

import inetsoft.report.Presenter;
import inetsoft.report.painter.HTMLPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A reader that formats a cell while another reader builds the cached column presenters gets
 * the presenter of its column, not the empty slot of an array published before it is filled
 * (bug #77397).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class AttributeTableLensColPresenterRaceTest {
   @Test
   public void readDuringColPresenterBuildGetsPresenter() throws Exception {
      List<String> failures = new ArrayList<>();

      for(int run = 0; run < RUNS; run++) {
         GatedTable base = new GatedTable();
         ColumnPresenterLens lens = new ColumnPresenterLens(base);
         // a cell presenter elsewhere turns on the presenter check of every cell
         lens.setPresenter(ROWS - 1, 2, null);
         // only the builder of the cached column presenters reads the header row of the base
         base.armed = true;

         CompletableFuture<Object> builder = read(lens, 1, 0);
         assertTrue(base.parked.await(CAP_SECONDS, TimeUnit.SECONDS), "the builder parks");

         Object second = read(lens, 2, 1).get(CAP_SECONDS, TimeUnit.SECONDS);
         base.gate.countDown();
         builder.get(CAP_SECONDS, TimeUnit.SECONDS);

         if(!(second instanceof PresenterPainter)) {
            failures.add("run " + run + ": " + second);
         }
      }

      assertTrue(failures.isEmpty(), failures.size() + "/" + RUNS +
         " reads during the build got no column presenter: " + failures);
   }

   private static CompletableFuture<Object> read(AttributeTableLens lens, int r, int c) {
      CompletableFuture<Object> result = new CompletableFuture<>();
      Thread thread = new Thread(() -> {
         try {
            result.complete(lens.getObject(r, c));
         }
         catch(Throwable ex) {
            result.complete(ex.toString());
         }
      }, "AttributeTableLensColPresenterRaceTest-reader");
      thread.setDaemon(true);
      thread.start();
      return result;
   }

   /**
    * A lens whose column 1 has a column presenter, found by its header.
    */
   private static final class ColumnPresenterLens extends AttributeTableLens {
      ColumnPresenterLens(DefaultTableLens base) {
         super(base);
      }

      @Override
      public Presenter getPresenter(String header, int col) {
         return col == 1 ? PRESENTER : null;
      }
   }

   /**
    * A base whose first read of header cell (0, 1) once armed parks until the gate opens.
    */
   private static final class GatedTable extends DefaultTableLens {
      GatedTable() {
         super(data());
      }

      @Override
      public Object getObject(int r, int c) {
         if(armed && r == 0 && c == 1) {
            armed = false;
            parked.countDown();

            try {
               gate.await(CAP_SECONDS, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.getObject(r, c);
      }

      volatile boolean armed;
      final CountDownLatch parked = new CountDownLatch(1);
      final CountDownLatch gate = new CountDownLatch(1);
   }

   private static Object[][] data() {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "a", "b", "c" };

      for(int r = 1; r <= ROWS; r++) {
         data[r] = new Object[] { "a" + r, "b" + r, "c" + r };
      }

      return data;
   }

   private static final Presenter PRESENTER = new HTMLPresenter();
   private static final int ROWS = 50;
   private static final int RUNS = 20;
   private static final long CAP_SECONDS = 20;
}
