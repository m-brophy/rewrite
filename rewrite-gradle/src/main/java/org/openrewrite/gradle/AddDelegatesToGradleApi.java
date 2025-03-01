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
package org.openrewrite.gradle;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.Comparator.comparing;

public class AddDelegatesToGradleApi extends Recipe {
    private static final JavaType.ShallowClass CLOSURE_TYPE = JavaType.ShallowClass.build("groovy.lang.Closure");
    private static final JavaType.ShallowClass ACTION_TYPE = JavaType.ShallowClass.build("org.gradle.api.Action");
    private static final JavaType.ShallowClass DELEGATES_TO_TYPE = JavaType.ShallowClass.build("groovy.lang.DelegatesTo");

    @Override
    public String getDisplayName() {
        return "Add `@DelegatesTo` to the Gradle API";
    }

    @Override
    public String getDescription() {
        return "The Gradle API has methods which accept `groovy.lang.Closure`. " +
                "Typically, there is an overload which accepts an `org.gradle.api.Action`." +
                "This recipe takes the type declared as the receiver of the `Action` overload and adds an appropriate " +
                "`@groovy.lang.DelegatesTo` annotation to the `Closure` overload.";
    }

    @Override
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("groovy.lang.Closure");
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext context) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, context);
                if(!hasClosureParameter(md) || commentSuggestsNoDelegate(md)) {
                    return md;
                }
                md = md.withParameters(ListUtils.map(md.getParameters(), it -> {
                    if (!(it instanceof J.VariableDeclarations)) {
                        return it;
                    }
                    J.VariableDeclarations param = (J.VariableDeclarations) it;
                    if (!(TypeUtils.isOfType(CLOSURE_TYPE, param.getType()) && FindAnnotations.find(param, "@groovy.lang.DelegatesTo").isEmpty())) {
                        return param;
                    }
                    if (method.getMethodType() == null || !(method.getMethodType().getDeclaringType() instanceof JavaType.Class)) {
                        return param;
                    }
                    // Construct a matcher that will identify methods identical to this one except they accept Action instead of Closure
                    MethodMatcher matcher = new MethodMatcher(method.getMethodType().withParameterTypes(
                            ListUtils.map(method.getMethodType().getParameterTypes(), p -> {
                                if (TypeUtils.isOfType(CLOSURE_TYPE, p)) {
                                    return ACTION_TYPE;
                                }
                                return p;
                            })));

                    // Get the type parameter of the Action
                    JavaType.Class declaringClass = (JavaType.Class) method.getMethodType().getDeclaringType();
                    //noinspection OptionalGetWithoutIsPresent
                    Optional<JavaType> maybeDelegateType = declaringClass.getMethods().stream()
                            .filter(matcher::matches)
                            .map(m -> (JavaType.Parameterized) m.getParameterTypes().stream()
                                    .filter(mp -> TypeUtils.isOfType(ACTION_TYPE, mp))
                                    .findFirst().get())
                            .filter(m -> m.getTypeParameters().size() == 1)
                            .map(m -> m.getTypeParameters().get(0))
                            .findAny();
                    if (!maybeDelegateType.isPresent()) {
                        return param;
                    }
                    JavaType.FullyQualified delegateType = unwrapGenericTypeVariable(maybeDelegateType.get());
                    if(delegateType == null) {
                        return param;
                    }
                    String simpleName =  delegateType.getFullyQualifiedName().substring( delegateType.getFullyQualifiedName().lastIndexOf('.') + 1);
                    param = param.withTemplate(
                            JavaTemplate.builder(this::getCursor, "@DelegatesTo(#{}.class)")
                                    .imports(delegateType.getFullyQualifiedName(), DELEGATES_TO_TYPE.getFullyQualifiedName())
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .classpath("groovy")
                                            .build())
                                    .build(),
                            param.getCoordinates().addAnnotation(comparing(a -> 0)),
                            simpleName);
                    maybeAddImport(DELEGATES_TO_TYPE);
                    return param;
                }));
                return md;
            }
        };
    }

    @Nullable
    private static JavaType.FullyQualified unwrapGenericTypeVariable(JavaType type) {
        if (type instanceof JavaType.GenericTypeVariable) {
            JavaType.GenericTypeVariable genericType = (JavaType.GenericTypeVariable)type;
            if(genericType.getBounds().size() == 1) {
                return unwrapGenericTypeVariable(genericType.getBounds().get(0));
            } else {
                return null;
            }
        }
        return TypeUtils.asFullyQualified(type);
    }

    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?<!also)[\\s*]++passed[\\s*]+to[\\s*]+the[\\s*]+closure[^.]+parameter", Pattern.DOTALL);
    private static boolean commentSuggestsNoDelegate(J.MethodDeclaration methodDeclaration) {
        return methodDeclaration.getComments().stream()
                .anyMatch(comment -> COMMENT_PATTERN.matcher(comment.printComment()).find());
    }

    private static boolean hasClosureParameter(J.MethodDeclaration methodDeclaration) {
        return methodDeclaration.getParameters().stream()
                .anyMatch(param -> param instanceof J.VariableDeclarations && TypeUtils.isOfType(CLOSURE_TYPE, ((J.VariableDeclarations)param).getType()));
    }
}
