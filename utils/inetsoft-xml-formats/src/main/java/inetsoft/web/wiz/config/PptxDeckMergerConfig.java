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
package inetsoft.web.wiz.config;

import inetsoft.report.io.viewsheet.PoiPptxDeckMerger;
import inetsoft.web.wiz.service.PptxDeckMerger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the real (POI-backed) PptxDeckMerger as a Spring bean. Lives in this package
 * (inetsoft.web.wiz.config), physically inside the inetsoft-xml-formats module, so that
 * WebConfig's existing component scan (basePackages = "inetsoft.web") picks it up at runtime —
 * package name drives Spring's scan, not which Maven module a class was compiled from. This
 * keeps WizViewsheetExportController's PptxDeckMerger collaborator constructor-injected and
 * mockable exactly like its other four Phase 1 collaborators, unlike OfficeExporterFactory's
 * own static-reflection getInstance() pattern (not appropriate here since our consumer is a
 * Spring-managed controller, not a static utility method).
 */
@Configuration
public class PptxDeckMergerConfig {
   @Bean
   public PptxDeckMerger pptxDeckMerger() {
      return new PoiPptxDeckMerger();
   }
}
