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
package org.openrewrite.java.internal

import org.openrewrite.java.JavaTypeGoat
import org.openrewrite.java.JavaTypeMappingTest
import org.openrewrite.java.asParameterized
import org.openrewrite.java.tree.JavaType

class JavaReflectionTypeMappingTest : JavaTypeMappingTest {
    companion object {
        private val typeMapping = JavaReflectionTypeMapping(JavaTypeCache())
        private val goat = typeMapping.type(JavaTypeGoat::class.java).asParameterized()!!
    }

    override fun goatType(): JavaType.Parameterized = goat

    override fun classType(fqn: String): JavaType.FullyQualified {
        return typeMapping.type(Class.forName(fqn)) as JavaType.FullyQualified
    }
}
