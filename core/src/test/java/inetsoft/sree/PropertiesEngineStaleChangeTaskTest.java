/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.sree;

import inetsoft.storage.InMemoryKeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.util.FileSystemService;
import inetsoft.util.log.LogManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77201: the reload that a storage change event debounces for 500 ms must only ever run
 * against the engine that scheduled it, and not at all once that engine has shut down.
 *
 * <p>The Spring context's engine plays the part of the next test class's engine, which is what
 * {@link PropertiesEngine#getInstance()} resolves. A second engine, created here with an
 * in-memory storage, is the owner of the change task. A single storage event schedules exactly
 * one task, so its deadline never moves and the task is not dropped by a rejected reschedule.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PropertiesEngineStaleChangeTaskTest {
   @BeforeEach
   void createOwner() throws Exception {
      current = PropertiesEngine.getInstance();
      current.init();
      currentProperties = current.getInternalProperties();
      assertNotNull(currentProperties);

      reloads.set(0);
      // counts the event that the change task publishes as its last step, after the reload
      ApplicationEventPublisher publisher = event -> {
         if(event instanceof ApplicationPropertiesChangedEvent) {
            reloads.incrementAndGet();
         }
      };
      owner = new PropertiesEngine(
         context.getBean(KeyValueStorageManager.class), context.getBean(FileSystemService.class),
         publisher, context.getBeanProvider(LogManager.class));
      storage = new InMemoryKeyValueStorage<>();
      Field field = PropertiesEngine.class.getDeclaredField("kvStorage");
      field.setAccessible(true);
      field.set(owner, storage);
      owner.init();
      ownerProperties = owner.getInternalProperties();
      assertNotNull(ownerProperties);
      assertNotSame(currentProperties, ownerProperties);
   }

   @AfterEach
   void shutdownOwner() throws Exception {
      owner.shutdown();
   }

   @Test
   void pendingReloadIsDroppedWhenItsEngineShutsDown() throws Exception {
      storage.remotePut("test77201.key", "value", true);
      // the Spring context closes the engine before the debounced reload runs
      owner.shutdown();

      // well past the 500 ms debounce delay
      Thread.sleep(1500L);

      assertSame(currentProperties, current.getInternalProperties(),
                 "a reload scheduled by a closed engine reloaded the current engine");
      assertSame(ownerProperties, owner.getInternalProperties(),
                 "a closed engine reloaded its properties");
      assertEquals(0, reloads.get(), "a closed engine published a properties changed event");
   }

   @Test
   void pendingReloadRunsAgainstTheEngineThatScheduledIt() throws Exception {
      storage.remotePut("test77201.key", "value", true);

      long end = System.currentTimeMillis() + 10000L;

      while(reloads.get() == 0) {
         if(System.currentTimeMillis() > end) {
            fail("the engine that received the change event never reloaded its properties");
         }

         Thread.sleep(50L);
      }

      assertNotSame(ownerProperties, owner.getInternalProperties());
      assertEquals("value", owner.getProperty("test77201.key"));
      assertSame(currentProperties, current.getInternalProperties(),
                 "a reload scheduled by another engine reloaded the current engine");
   }

   @Autowired
   private ConfigurableApplicationContext context;
   private final AtomicInteger reloads = new AtomicInteger();
   private PropertiesEngine current;
   private Properties currentProperties;
   private PropertiesEngine owner;
   private Properties ownerProperties;
   private InMemoryKeyValueStorage<String> storage;
}
