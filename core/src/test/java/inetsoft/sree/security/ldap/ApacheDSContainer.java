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
package inetsoft.sree.security.ldap;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class ApacheDSContainer extends GenericContainer<ApacheDSContainer> {
   public static final int APACHEDS_PORT = 10389;
   public static final String DEFAULT_IMAGE_AND_TAG = "tremolosecurity/apacheds:latest";

   public ApacheDSContainer() {
      this(DEFAULT_IMAGE_AND_TAG);
   }

   public ApacheDSContainer(String image) {
      super(image);
      addExposedPort(APACHEDS_PORT);
      addEnv("APACHEDS_ROOT_PASSWORD", "secret");
      addEnv("APACHEDS_TLS_KS_PWD", "secret");
      addEnv("DN", "dc=example,dc=com");
      addEnv("LDIF_FILE", "/etc/apacheds/security.ldif");
      withClasspathResourceMapping(
         "inetsoft/sree/security/ldap/apacheds.jks",
         "/etc/apacheds/apacheds.jks", BindMode.READ_ONLY);
      withClasspathResourceMapping(
         "inetsoft/sree/security/ldap/security.ldif",
         "/etc/apacheds/security.ldif", BindMode.READ_ONLY);
      setWaitStrategy(
         Wait.forLogMessage(".*modifying entry \"uid=admin,ou=system\"\\n", 1)
            .withStartupTimeout(Duration.of(90L, ChronoUnit.SECONDS)));
   }

   public Integer getPort() {
      return getMappedPort(APACHEDS_PORT);
   }
}
