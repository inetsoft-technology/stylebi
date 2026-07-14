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
package inetsoft.util.css;

import inetsoft.test.*;
import inetsoft.util.DataChangeEvent;
import inetsoft.util.DataSpace;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CSSDictionaryRaceTest {
   @Test
   void concurrentReinitShouldNotReturnNull() throws Exception {
      CSSDictionary.resetDictionaryCache();
      String name = "CSSDictionaryTest.multiple-classes.css";
      DataSpace space = DataSpace.getDataSpace();

      if(!space.exists("css", name)) {
         space.withOutputStream("css", name, out ->
            Tool.fileCopy(CSSDictionaryTest.class.getResourceAsStream(name), out));
      }

      CSSDictionary dict = CSSDictionary.getDictionary("css", name, false);
      CSSParameter param = new CSSParameter("", "", "class-one,class-two", null);

      // sanity: single-threaded read works
      assertEquals(Color.BLUE, dict.getBackground(param));

      AtomicBoolean stop = new AtomicBoolean(false);
      AtomicReference<Throwable> failure = new AtomicReference<>();

      // Background thread simulates the blob-storage change callback thread
      // firing the CSS file's change listener (which calls init()).
      Thread reinit = new Thread(() -> {
         DataChangeEvent event = new DataChangeEvent("css", name, System.currentTimeMillis());
         while(!stop.get()) {
            dict.changeListener.dataChanged(event);
         }
      });
      reinit.start();

      try {
         for(int i = 0; i < 200_000 && failure.get() == null; i++) {
            Color bg = dict.getBackground(param);

            if(bg == null) {
               failure.set(new AssertionError(
                  "getBackground returned null during concurrent re-init at iteration " + i));
               break;
            }
         }
      }
      finally {
         stop.set(true);
         reinit.join();
      }

      if(failure.get() != null) {
         throw new AssertionError(failure.get().getMessage(), failure.get());
      }
   }
}
