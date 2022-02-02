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

import io.micrometer.core.instrument.util.DoubleFormat.decimalOrNan
import org.HdrHistogram.ShortCountsHistogram
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openjdk.jol.info.GraphStats
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaTypeVisitor
import org.openrewrite.java.internal.JavaTypeCache
import org.openrewrite.java.tree.JavaType
import java.util.*
import kotlin.math.ln
import kotlin.math.pow

class JavaSourceSetTest {

    @Test
    fun typesFromClasspath() {
        val ctx = InMemoryExecutionContext { e -> throw e }
        val typeCache = JavaTypeCache()
        val javaSourceSet = JavaSourceSet.build("main", JavaParser.runtimeClasspath(), typeCache, ctx)

        val uniqueTypes: MutableSet<JavaType> = Collections.newSetFromMap(IdentityHashMap())
        val typeCacheAfterMapping = mutableMapOf<String, JavaType>()
        val signatureCollisions = sortedMapOf<String, Int>()

        val methodsByType: MutableMap<String, MutableSet<String>> = mutableMapOf()
        val fieldsByType: MutableMap<String, MutableSet<String>> = mutableMapOf()

        javaSourceSet.classpath.forEach {
            object : JavaTypeVisitor<Int>() {
                override fun visit(javaType: JavaType?, p: Int): JavaType? {
                    if (javaType is JavaType) {
                        if (uniqueTypes.add(javaType)) {
                            val signature = javaType.toString()
                            typeCacheAfterMapping.compute(signature) { _, existing ->
                                if (existing != null && javaType !== existing) {
                                    signatureCollisions.compute(signature) { _, acc -> (acc ?: 0) + 1 }
                                }
                                javaType
                            }
                            return super.visit(javaType, p)
                        }
                    }
                    return javaType
                }

                override fun visitMethod(method: JavaType.Method, p: Int): JavaType {
                    methodsByType.getOrPut(method.declaringType.toString()) { mutableSetOf() }.add(method.toString())
                    return super.visitMethod(method, p)
                }

                override fun visitVariable(variable: JavaType.Variable, p: Int): JavaType {
                    fieldsByType.getOrPut(variable.owner!!.toString()) { mutableSetOf() }.add(variable.toString())
                    return super.visitVariable(variable, p)
                }
            }.visit(it, 0)
        }

        val methodHistogram = ShortCountsHistogram(1)
        methodsByType.values.forEach { methodHistogram.recordValue(it.size.toLong()) }

        println("Methods per class:\n")
        methodHistogram.outputPercentileDistribution(System.out, 1, 1.0)
        println("\n\n")

        val fieldHistogram = ShortCountsHistogram(1)
        fieldsByType.values.forEach { fieldHistogram.recordValue(it.size.toLong()) }

        println("Fields per class:\n")
        fieldHistogram.outputPercentileDistribution(System.out, 1, 1.0)
        println("\n\n")

        println("Heap size: ${humanReadableByteCount(GraphStats.parseInstance(javaSourceSet).totalSize().toDouble())}")
        println("Unique signatures: ${typeCache.size()}")
        println("Shallow type count: ${javaSourceSet.classpath.size}")
        println("Deep type count: ${uniqueTypes.size}")
        println("Signature collisions: ${signatureCollisions.size}")

        assertThat(javaSourceSet.classpath.map { it.fullyQualifiedName })
            .contains("org.junit.jupiter.api.Test")

        assertThat(signatureCollisions.entries.map { "${it.key}${if(it.value > 1) " (x${it.value})" else ""}" })
            .`as`("More than one instance of a type collides on the same signature.")
            .isEmpty()
    }

    @Test
    fun typesByPackage() {
        val ctx = InMemoryExecutionContext { e -> throw e }
        val typeCache = JavaTypeCache()
        val javaSourceSet = JavaSourceSet.build("main", JavaParser.runtimeClasspath(), typeCache, ctx)
        val typesByPackage: MutableMap<String, MutableSet<String>> = TreeMap()

        javaSourceSet.classpath.forEach {
            object : JavaTypeVisitor<Int>() {
                override fun visit(javaType: JavaType?, p: Int): JavaType? {
                    if (javaType is JavaType.Class) {
                        typesByPackage.compute(javaType.packageName) { _, acc ->
                            val types = acc ?: mutableSetOf()
                            types.add(javaType.fullyQualifiedName)
                            types
                        }
                    }
                    return javaType
                }
            }.visit(it, 0)
        }

        typesByPackage.entries.sortedByDescending { it.value.size }.forEach {
            println("${it.key} | ${it.value.size}")
        }
    }

    private fun humanReadableByteCount(bytes: Double): String {
        val unit = 1024
        return if (bytes >= unit.toDouble() && !bytes.isNaN()) {
            val exp = (ln(bytes) / ln(unit.toDouble())).toInt()
            val pre = "${"KMGTPE"[exp - 1]}i"
            "${decimalOrNan(bytes / unit.toDouble().pow(exp.toDouble()))} ${pre}B"
        } else {
            "${decimalOrNan(bytes)} B"
        }
    }
}
