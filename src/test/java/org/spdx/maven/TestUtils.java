/*
 * Copyright 2023 Source Auditor Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spdx.maven;

import java.util.Arrays;
import java.util.Collection;

/**
 * Test Utils
 *
 * @author Kevin Conner
 */
public class TestUtils
{
    public static String[] toSortedArray(final Collection<String> collection) {
        if ( collection == null ) {
            return null;
        }
        final String[] array = collection.toArray( new String[collection.size()] );
        Arrays.sort( array );
        return array;
    }
}
