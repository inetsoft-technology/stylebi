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
package com.inetsoft.connectors.script;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class RServeContainer extends GenericContainer<RServeContainer> {
   public static final int RSERVE_PORT = 6311;

   public RServeContainer() {
      super(new ImageFromDockerfile("inetsoft/rserve:1.8-7", false)
               .withFileFromClasspath("Rserv.conf", "com/inetsoft/connectors/script/Rserv.conf")
               .withFileFromClasspath("Rserv.passwd", "com/inetsoft/connectors/script/Rserv.passwd")
               .withFileFromClasspath("Dockerfile", "com/inetsoft/connectors/script/Dockerfile"));
      addExposedPort(RSERVE_PORT);
      setWaitStrategy(Wait.forListeningPort()
                         .withStartupTimeout(Duration.of(90L, ChronoUnit.SECONDS)));
   }

   public Integer getPort() {
      return getMappedPort(RSERVE_PORT);
   }
}
