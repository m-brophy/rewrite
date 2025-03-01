/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.marker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.openrewrite.Issue
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaTypeGoat
import org.openrewrite.java.JavaTypeVisitor
import org.openrewrite.java.internal.JavaReflectionTypeMapping
import org.openrewrite.java.internal.JavaTypeCache
import org.openrewrite.java.tree.JavaType
import java.util.*
import kotlin.streams.toList

interface JavaSourceSetTest {

    @Issue("https://github.com/openrewrite/rewrite/issues/1636")
    @Test
    fun buildJavaSourceSet() {
        val typeCache = JavaTypeCache()
        val jss = JavaSourceSet.build("main", emptyList(), typeCache, true)
        val typesBySignature = jss.classpath.associateBy { it.toString() }
        assertThat(typesBySignature["java.lang.Object"]).isInstanceOf(JavaType.Class::class.java)
        assertThat(typesBySignature["java.util.List"]).isInstanceOf(JavaType.Class::class.java)
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1712")
    @Test
    fun shallowTypes() {
        val typeCache = JavaTypeCache()

        val jss = JavaSourceSet.build("main", emptyList(), typeCache, false)
        val typesBySignature = jss.classpath.associateBy { it.toString() }
        assertThat(typesBySignature["java.lang.Object"]).isInstanceOf(JavaType.ShallowClass::class.java)
        assertThat(typesBySignature["java.util.List"]).isInstanceOf(JavaType.ShallowClass::class.java)
    }

    @Issue("https://github.com/openrewrite/rewrite/issues/1677")
    @Test
    fun shadedJar() {
        val typeCache = JavaTypeCache()

        val shaded = JavaSourceSet.build("test", JavaParser.dependenciesFromClasspath("hbase-shaded-client"), typeCache, false)
            .classpath.stream().filter {o -> o.fullyQualifiedName.startsWith("org.apache.hadoop.hbase.shaded")}.toList()
        assertThat(shaded).isNotEmpty
        assertThat(shaded[0]).isInstanceOf(JavaType.ShallowClass::class.java)
    }

    // This test uses a lot of memory and examines a code path that we don't actually take anywhere right now
    @Disabled
    @Test
    fun doesNotDuplicateTypesInCache() {
        val typeCache = JavaTypeCache()
        val uniqueTypes: MutableSet<JavaType> = Collections.newSetFromMap(IdentityHashMap())
        val reflectiveGoat = JavaReflectionTypeMapping(typeCache).type(JavaTypeGoat::class.java)
        newUniqueTypes(uniqueTypes, reflectiveGoat)

        val classpathGoat = JavaSourceSet.build("main", JavaParser.runtimeClasspath(), typeCache, true)
            .classpath
            .find { "JavaTypeGoat" == it.className }!!

        newUniqueTypes(uniqueTypes, classpathGoat, true)
    }

    private fun newUniqueTypes(uniqueTypes: MutableSet<JavaType>, root: JavaType, report: Boolean = false) {
        var newUnique = false
        object : JavaTypeVisitor<Int>() {
            override fun visit(javaType: JavaType?, p: Int): JavaType? {
                if (javaType is JavaType) {
                    if (uniqueTypes.add(javaType)) {
                        if(report) {
                            newUnique = true
                            println(javaType)
                        }
                        return super.visit(javaType, p)
                    }
                }
                return javaType
            }
        }.visit(root, 0)

        if(report && newUnique) {
            fail("Found new unique types there should have been none.")
        }
    }
}
