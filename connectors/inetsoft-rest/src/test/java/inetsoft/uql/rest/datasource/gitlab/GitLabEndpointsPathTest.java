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
package inetsoft.uql.rest.datasource.gitlab;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81801: the commit discussion endpoints must use the documented
 * /repository/commits path, otherwise GitLab answers 404.
 */
class GitLabEndpointsPathTest {
   @Test
   void commitDiscussionSuffixesIncludeRepository() throws Exception {
      String json;

      try(InputStream in = GitLabEndpointsPathTest.class.getResourceAsStream("endpoints.json")) {
         assertNotNull(in);
         json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }

      assertTrue(json.contains(
         "\"/v4/projects/{Project ID}/repository/commits/{Commit ID}/discussions\""));
      assertTrue(json.contains(
         "\"/v4/projects/{Project ID}/repository/commits/{Commit ID}/discussions/{Discussion ID}\""));
      assertFalse(json.contains("/{Project ID}/commits/{Commit ID}/discussions"));
   }
}
