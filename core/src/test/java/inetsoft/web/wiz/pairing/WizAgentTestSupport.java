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
package inetsoft.web.wiz.pairing;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.annotation.*;

/**
 * Composed JUnit5 annotation for wiz agent worksheet-domain tests.
 *
 * <p>Applying {@code @WizAgentTestSupport} to a test class is equivalent to applying:</p>
 * <ul>
 *   <li>{@code @ExtendWith(SpringExtension.class)} — provides a minimal Spring context</li>
 *   <li>{@code @ContextConfiguration(BaseTestConfiguration)} — wires PropertiesEngine etc.</li>
 *   <li>{@code @SreeHome} — boots the in-process SreeEnv needed by Worksheet/TableAssembly</li>
 *   <li>{@code @DirtiesContext} — tears the context down after each test class</li>
 *   <li>{@code @Tag("core")} — marks the test for the {@code core} Maven Surefire group</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @WizAgentTestSupport
 * class MyWorksheetTest {
 *     @Test
 *     void example() {
 *         Worksheet ws = new Worksheet();
 *         TableAssembly t = TestWorksheets.tableWithColumns(ws, "Orders", "id", "amount");
 *         ...
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public @interface WizAgentTestSupport {
}
