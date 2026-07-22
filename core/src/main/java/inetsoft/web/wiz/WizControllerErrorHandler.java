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
import inetsoft.web.wiz.service.UnsupportedDatasourceException;
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
   // Catching it here benefits WorksheetAgentController.addBoundTable specifically: that method
   // declares throws Exception with no catch of its own, so an UnsupportedDatasourceException
   // genuinely propagates here and gets the friendly 422.
   //
   // NOT every other getJDBCDatasource caller benefits, though — verify the actual call chain
   // before assuming one does. WorksheetGenerateController.generateWs() and
   // WorksheetTableController.createTables() both wrap their whole service call in their own
   // catch(Exception e) (the latter one layer deeper, inside WorksheetTableService's per-table
   // try/catch) and always return 200 with errorMessage set — this exception never reaches
   // either controller, let alone this advice. Making those paths return a real 422 would mean
   // changing that per-item/per-request error-handling design, not just adding a handler.
   @ExceptionHandler(UnsupportedDatasourceException.class)
   public ResponseEntity<Map<String, String>> handleUnsupportedDatasource(
      UnsupportedDatasourceException e)
   {
      LOG.warn("Unsupported datasource: {} ({})", e.getDatasourceName(), e.getDatasourceType());

      Map<String, String> payload = new HashMap<>();
      payload.put("error", e.getMessage());
      payload.put("datasourceType", e.getDatasourceType());
      return new ResponseEntity<>(payload, null, HttpStatus.UNPROCESSABLE_ENTITY);
   }

   private static final Logger LOG = LoggerFactory.getLogger(WizControllerErrorHandler.class);
}
