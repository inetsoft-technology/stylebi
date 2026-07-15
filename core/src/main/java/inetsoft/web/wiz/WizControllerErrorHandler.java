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
package inetsoft.web.wiz;

import inetsoft.util.Catalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps authorization denials from the wiz (AI composer agent) controllers to HTTP 403, mirroring
 * {@code ComposerControllerErrorHandler} for the standard composer. Without this the
 * {@code SecurityException}s thrown by the wiz permission gates would fall through unhandled to
 * {@code GlobalExceptionHandler} — which does not handle {@code SecurityException} — and surface as
 * a generic 500 instead of a 403.
 *
 * <p>Scoped to {@code inetsoft.web.wiz} so it covers every wiz controller. The two controllers in
 * this package that carry their own catch-all {@code @ExceptionHandler(Exception.class)}
 * ({@code WorksheetTableController}, {@code DatasourceMetaApiController}) each declare a more
 * specific local {@code SecurityException} handler, since a local handler takes precedence over a
 * {@code @ControllerAdvice} and their catch-all would otherwise intercept the denial first (as 400).
 */
@ControllerAdvice(basePackages = "inetsoft.web.wiz")
public class WizControllerErrorHandler {
   @ExceptionHandler({ inetsoft.sree.security.SecurityException.class, java.lang.SecurityException.class })
   public ResponseEntity<Map<String, String>> handleSecurityException(Exception e) {
      LOG.warn("Unauthorized wiz access: {}", e.getMessage());

      Map<String, String> payload = new HashMap<>();
      payload.put("error", "Forbidden");
      payload.put("message", Catalog.getCatalog().getString("http.error.unauthorized"));
      return new ResponseEntity<>(payload, null, HttpStatus.FORBIDDEN);
   }

   // getJDBCDatasource() (MetadataApiService) is called from several wiz controllers/services
   // beyond DatasourceMetaApiController (which has its own local override of this same handler,
   // since a local @ExceptionHandler always wins over this ControllerAdvice — see that class).
   // Catching it here too means any OTHER wiz controller that reaches a non-JDBC datasource and
   // has no local catch-all of its own (e.g. WorksheetAgentController, WorksheetGenerateController)
   // gets the same friendly 422 instead of whatever its default fallback would otherwise produce.
   // A controller with its own local catch-all Exception.class handler (e.g.
   // WorksheetTableController) still needs its own local override to get this treatment, exactly
   // like the SecurityException case above.
   @ExceptionHandler(inetsoft.web.wiz.service.UnsupportedDatasourceException.class)
   public ResponseEntity<Map<String, String>> handleUnsupportedDatasource(
      inetsoft.web.wiz.service.UnsupportedDatasourceException e)
   {
      LOG.warn("Unsupported datasource: {} ({})", e.getDatasourceName(), e.getDatasourceType());

      Map<String, String> payload = new HashMap<>();
      payload.put("error", e.getMessage());
      payload.put("datasourceType", e.getDatasourceType());
      return new ResponseEntity<>(payload, null, HttpStatus.UNPROCESSABLE_ENTITY);
   }

   private static final Logger LOG = LoggerFactory.getLogger(WizControllerErrorHandler.class);
}
