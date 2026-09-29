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
package inetsoft.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.annotation.*;

/**
 * Sets up the sree home for a test class and resets the shared state after it. The class's Spring
 * context is closed after the class, rather than cached for a later class with the same
 * configuration, because its beans (the key-value store, PropertiesEngine, SecurityEngine) hold
 * the state the class changed. A {@code @DirtiesContext} on the test class itself takes
 * precedence, e.g. to close the context after each test method.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SreeHomeExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public @interface SreeHome {
   String value() default "";
   String[] importUrls() default {};
   String[] importResources() default {};
   String[] materialize() default {};
   DataSpaceFile[] dataSpace() default {};
   SreeProperty[] properties() default {};
   boolean security() default false;
}
