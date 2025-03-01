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
package org.openrewrite.java.cleanup

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaRecipeTest

interface RemoveRedundantTypeCastTest : JavaRecipeTest {
    override val recipe: Recipe
        get() = RemoveRedundantTypeCast()

    companion object {
        const val test = """
            class Test {
            }
        """
    }

    @Test
    fun doNotChangeUpCast() = assertUnchanged(
        before = """
            class Test {
                Object o = "";
                String s = (String) o;
                String[] sArray = (String[]) list.toArray(new String[0]);
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1739")
    @Test
    fun doNotChangeGenericTypeCast() = assertUnchanged(
        before = """
            import java.util.Collection;
            
            class Test {
                public <T extends Collection<String>> T test() {
                    //noinspection unchecked
                    return (T) get();
                }

                public static Collection<String> get() {
                    return null;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1647")
    @Test
    fun redundantTypeCast() = assertChanged(
        before = """
            @SuppressWarnings("RedundantCast")
            class Test {
                String s = (String) "";
                String s2 = (String) method();

                String method() {
                    return null;
                }
            }
        """,
        after = """
            @SuppressWarnings("RedundantCast")
            class Test {
                String s = "";
                String s2 = method();

                String method() {
                    return null;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1647")
    @Test
    fun downCast() = assertChanged(
        before = """
            @SuppressWarnings("RedundantCast")
            class Test {
                Object o = (String) "";
                Object o2 = (String) method();

                String method() {
                    return null;
                }
            }
        """,
        after = """
            @SuppressWarnings("RedundantCast")
            class Test {
                Object o = "";
                Object o2 = method();

                String method() {
                    return null;
                }
            }
        """
    )

    @Test
    fun downCastParameterizedTypes() = assertChanged(
        before = """
            import java.util.List;
            
            @SuppressWarnings("RedundantCast")
            class Test {
                Object o = (List<String>) method();
                Object o2 = (List<? extends String>) method();
                Object o3 = (List<? super String>) method();

                List<String> method() {
                    return null;
                }
            }
        """,
        after = """
            import java.util.List;
            
            @SuppressWarnings("RedundantCast")
            class Test {
                Object o = method();
                Object o2 = method();
                Object o3 = method();

                List<String> method() {
                    return null;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite/issues/1647")
    @Test
    fun downCastExtendedObject() = assertChanged(
        dependsOn = arrayOf(test),
        before = """
            @SuppressWarnings("RedundantCast")
            class ExtendTest extends Test {
                Test extendTest = (ExtendTest) new ExtendTest();
                Test[][] extendTestArray = (ExtendTest[][]) new ExtendTest[0][0];
            }
        """,
        after = """
            @SuppressWarnings("RedundantCast")
            class ExtendTest extends Test {
                Test extendTest = new ExtendTest();
                Test[][] extendTestArray = new ExtendTest[0][0];
            }
        """
    )
}
