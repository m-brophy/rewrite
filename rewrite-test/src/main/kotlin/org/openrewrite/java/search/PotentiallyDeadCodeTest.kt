/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.search

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaRecipeTest

interface PotentiallyDeadCodeTest : JavaRecipeTest {

    @Test
    fun potentiallyDeadCode() {
        val cu = parser.parse(
            """
                package org.openrewrite;
                class Test {
                    public void test() {
                    }
                    
                    public void test2() {
                    }
                    
                    public void test3() {
                    }
                }
            """.trimIndent(),
            //language=java
            """
                package org.openrewrite;
                class Test2 {{
                    Test t = new Test();
                    t.test2();
                }}
            """.trimIndent()
        )

        val results = PotentiallyDeadCode().run(cu)

        //language=csv
        assertThat(results[0].after!!.printAll()).isEqualTo("""
            declaring type,method
            org.openrewrite.Test,test()
            org.openrewrite.Test,test3()
        """.trimIndent() + "\n")

        //language=csv
        assertThat(results[1].after!!.printAll()).isEqualTo("""
            declaring type,method
            org.openrewrite.Test,<constructor>()
            org.openrewrite.Test,test2()
        """.trimIndent() + "\n")
    }
}
