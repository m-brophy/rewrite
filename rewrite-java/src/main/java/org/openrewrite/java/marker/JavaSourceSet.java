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
package org.openrewrite.java.marker;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;
import org.intellij.lang.annotations.Language;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.internal.ClassgraphTypeMapping;
import org.openrewrite.java.internal.JavaTypeCache;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Marker;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyMap;
import static org.openrewrite.Tree.randomId;

@Value
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@With
public class JavaSourceSet implements Marker {
    @EqualsAndHashCode.Include
    UUID id;

    String name;
    List<JavaType.FullyQualified> classpath;

    public static JavaSourceSet build(String sourceSetName, Collection<Path> classpath,
                                      JavaTypeCache typeCache, ExecutionContext ctx) {
        List<JavaType.FullyQualified> fqns;
        if (classpath.iterator().hasNext()) {

            List<String> snippets = new ArrayList<>();
            Map<String, List<String>> packagesToTypes = new HashMap<>();
            try (ScanResult scanResult = new ClassGraph()
                    .overrideClasspath(classpath)
                    .enableMemoryMapping()
                    .enableClassInfo()
                    .ignoreClassVisibility()
                    .scan()) {
                for (ClassInfo classInfo : scanResult.getAllClasses()) {
                    // Skip private classes, allowing package-private
                    if (classInfo.isAnonymousInnerClass() || classInfo.isPrivate()) {
                        continue;
                    }

                    // Need to handle inner classes somehow, exclude for now during development only
                    if(classInfo.isInnerClass()) {
                        continue;
                    }
                    String simpleName = classInfo.getPackageName().length() == 0 ?
                            classInfo.getName() :
                            classInfo.getName().substring(classInfo.getPackageName().length() + 1);
                    if(simpleName.startsWith("-")) {
                        // The Java compiler doesn't allow class names to start with "-", but the JVM will load them
                        // So other compilers, like Kotlin, may emit class files with these names which we are unable to handle
                        continue;
                    }
                    packagesToTypes.compute(classInfo.getPackageName(), (unused, acc) -> {
                        if (acc == null) {
                            acc = new ArrayList<>();
                        }
                        acc.add(simpleName);
                        return acc;
                    });
                }
            }
            for (Map.Entry<String, List<String>> packageToTypes : packagesToTypes.entrySet()) {
                StringBuilder sb = new StringBuilder("package ").append(packageToTypes.getKey()).append(";\n")
                        .append("class RewriteTypeStub {\n");

                List<String> value = packageToTypes.getValue();
                for (int i = 0; i < value.size(); i++) {
                    String type = value.get(i);
                    sb.append("    ").append(type).append(" t").append(i).append(";\n");
                }
                sb.append("}");
                snippets.add(sb.toString());
            }

            JavaParser jp = JavaParser.fromJavaVersion()
                    .typeCache(typeCache)
                    .logCompilationWarningsAndErrors(true)
                    .classpath(classpath)
                    .build();
            List<J.CompilationUnit> cus = new ArrayList<>(snippets.size());
            cus.addAll(jp.parse(snippets.toArray(new String[]{})));
            fqns = new ArrayList<>(snippets.size());
            for (J.CompilationUnit cu : cus) {
                List<Statement> statements = cu.getClasses().get(0).getBody().getStatements();
                for(Statement s : statements) {
                    JavaType.FullyQualified fq = ((J.VariableDeclarations)s).getTypeAsFullyQualified();
                    if (fq == null) {
                        continue;
                    }
                    fqns.add(fq);
                }
            }
        } else {
            fqns = Collections.emptyList();
        }

        return new JavaSourceSet(randomId(), sourceSetName, fqns);
    }
}
