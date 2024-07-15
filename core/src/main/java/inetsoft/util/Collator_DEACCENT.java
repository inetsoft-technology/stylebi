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
package inetsoft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.*;

/*
 * @by stevenkuo feature1413407457765 2014/12/11
 * Collator_DEACCENT creates a custom collator for stripping letter accents and sorting
 * letters according to their English counterparts.
 *
 * @author InetSoft Technology Corp
 *
 */
public class Collator_DEACCENT extends Collator {
    /**
     * Constructor.
     */
    public Collator_DEACCENT() {
        this.base = Collator.getInstance();
    }

    /**
     * Get the collation key.
     */
    @Override
    public CollationKey getCollationKey(String source) {
        return base.getCollationKey(source);
    }

    /**
     * Compare two strings.
     */
    @Override
    public int compare(String source, String target) {
        source = Normalizer.normalize(source, Normalizer.Form.NFD);
        target = Normalizer.normalize(target, Normalizer.Form.NFD);
        return base.compare(source, target);
    }

    /**
     * Get the hash code.
     */
    public int hashCode() {
        return base.hashCode();
    }

   private static final Logger LOG =
      LoggerFactory.getLogger(Collator_DEACCENT.class);
    private Collator base;
}
