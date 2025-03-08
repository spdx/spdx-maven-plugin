/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 Source Auditor Inc.
 * SPDX-FileType: SOURCE
 * SPDX-License-Identifier: Apache-2.0
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.spdx.maven.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestIdGenerator {

    @Test
    public void testGetNextId()
    {
        String reproducibleString1 = "ThisString1";
        String reproducibleString2 = "ThisString2";

        String result1 = IdGenerator.getIdGenerator().generateId( reproducibleString1 );
        assertTrue( result1.startsWith( "SPDXRef-" ) );
        assertTrue( result1.endsWith( "0" ) );
        String result2 = IdGenerator.getIdGenerator().generateId( reproducibleString2 );
        assertTrue( result2.startsWith( "SPDXRef-" ) );
        assertTrue( result2.endsWith( "0" ) );
        assertNotEquals( result1, result2 );
        String result3 = IdGenerator.getIdGenerator().generateId( reproducibleString1 );
        assertTrue( result3.startsWith( "SPDXRef-" ) );
        assertTrue( result3.endsWith( "1" ) );
    }
}
