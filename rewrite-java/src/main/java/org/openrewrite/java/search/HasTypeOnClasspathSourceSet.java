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
package org.openrewrite.java.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

@Value
@EqualsAndHashCode(callSuper = true)
public class HasTypeOnClasspathSourceSet<P> extends JavaIsoVisitor<P> {
    String fullyQualifiedTypeName;

    @Override
    public JavaSourceFile visitJavaSourceFile(JavaSourceFile cu, P p) {
        return cu.getMarkers().findFirst(JavaSourceSet.class)
                .filter(sourceSet -> {
                    for (JavaType.FullyQualified classpath : sourceSet.getClasspath()) {
                        if (TypeUtils.isOfClassType(classpath, fullyQualifiedTypeName)) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(sourceSet -> cu)
                .orElse(cu.withMarkers(cu.getMarkers().searchResult()));
    }
}
