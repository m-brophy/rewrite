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
package org.openrewrite.maven

import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.test.RewriteTest
import java.nio.file.Paths

class ChangeDependencyGroupIdAndArtifactIdTest : RewriteTest {

    @Test
    fun changeDependencyGroupIdAndArtifactId() = rewriteRun(
        { spec ->
            spec.recipe(
                ChangeDependencyGroupIdAndArtifactId(
                    "javax.activation",
                    "javax.activation-api",
                    "jakarta.activation",
                    "jakarta.activation-api",
                    null
                )
            )
        },
        pomXml("""
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                    <dependency>
                        <groupId>javax.activation</groupId>
                        <artifactId>javax.activation-api</artifactId>
                    </dependency>
                </dependencies>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>javax.activation</groupId>
                            <artifactId>javax.activation-api</artifactId>
                            <version>1.2.0</version>
                        </dependency>
                        <dependency>
                            <groupId>jakarta.activation</groupId>
                            <artifactId>jakarta.activation-api</artifactId>
                            <version>1.2.1</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """,
        """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                    <dependency>
                        <groupId>jakarta.activation</groupId>
                        <artifactId>jakarta.activation-api</artifactId>
                    </dependency>
                </dependencies>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>javax.activation</groupId>
                            <artifactId>javax.activation-api</artifactId>
                            <version>1.2.0</version>
                        </dependency>
                        <dependency>
                            <groupId>jakarta.activation</groupId>
                            <artifactId>jakarta.activation-api</artifactId>
                            <version>1.2.1</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """
    ))

    @Test
    fun overrideManagedDependency() = rewriteRun(
        { spec ->
            spec.recipe(
                ChangeDependencyGroupIdAndArtifactId(
                    "javax.activation",
                    "javax.activation-api",
                    "jakarta.activation",
                    "jakarta.activation-api",
                    "1.2.2",
                    true
                )
            )
        },
        pomXml(
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>javax.activation</groupId>
                            <artifactId>javax.activation-api</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>javax.activation</groupId>
                                <artifactId>javax.activation-api</artifactId>
                                <version>1.2.0</version>
                            </dependency>
                            <dependency>
                                <groupId>jakarta.activation</groupId>
                                <artifactId>jakarta.activation-api</artifactId>
                                <version>1.2.1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
            """,
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>jakarta.activation</groupId>
                            <artifactId>jakarta.activation-api</artifactId>
                            <version>1.2.2</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>javax.activation</groupId>
                                <artifactId>javax.activation-api</artifactId>
                                <version>1.2.0</version>
                            </dependency>
                            <dependency>
                                <groupId>jakarta.activation</groupId>
                                <artifactId>jakarta.activation-api</artifactId>
                                <version>1.2.1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
            """
        )
    )

    @Test
    fun managedToUnmanaged() = rewriteRun(
        { spec ->
            spec.recipe(
                ChangeDependencyGroupIdAndArtifactId(
                    "javax.activation",
                    "javax.activation-api",
                    "jakarta.activation",
                    "jakarta.activation-api",
                    "1.2.2",
                    false
                )
            )
        },
        pomXml(
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>javax.activation</groupId>
                            <artifactId>javax.activation-api</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>javax.activation</groupId>
                                <artifactId>javax.activation-api</artifactId>
                                <version>1.2.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
            """,
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>jakarta.activation</groupId>
                            <artifactId>jakarta.activation-api</artifactId>
                            <version>1.2.2</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>javax.activation</groupId>
                                <artifactId>javax.activation-api</artifactId>
                                <version>1.2.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
            """
        )
    )

    @Test
    fun unmanagedToManaged() = rewriteRun(
        { spec ->
            spec.recipe(
                ChangeDependencyGroupIdAndArtifactId(
                    "javax.activation",
                    "javax.activation-api",
                    "jakarta.activation",
                    "jakarta.activation-api",
                    "1.2.2",
                    false
                )
            )
        },
        pomXml(
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>javax.activation</groupId>
                            <artifactId>javax.activation-api</artifactId>
                            <version>1.2.0</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>jakarta.activation</groupId>
                                <artifactId>jakarta.activation-api</artifactId>
                                <version>1.2.1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
            """,
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>jakarta.activation</groupId>
                            <artifactId>jakarta.activation-api</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>jakarta.activation</groupId>
                                <artifactId>jakarta.activation-api</artifactId>
                                <version>1.2.1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
            """
        )
    )

    @Test
    fun changeOnlyArtifactId() = rewriteRun(
        { spec ->
            spec.recipe(
                ChangeDependencyGroupIdAndArtifactId(
                    "org.openrewrite",
                    "rewrite-java-8",
                    "org.openrewrite",
                    "rewrite-java-11",
                    null
                )
            )
        },
        pomXml(
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.openrewrite</groupId>
                            <artifactId>rewrite-java-8</artifactId>
                            <version>7.20.0</version>
                        </dependency>
                    </dependencies>
                </project>
            """,
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencies>
                    <dependency>
                        <groupId>org.openrewrite</groupId>
                        <artifactId>rewrite-java-11</artifactId>
                        <version>7.20.0</version>
                    </dependency>
                </dependencies>
            </project>
            """
        )
    )

    @Test
    fun doNotChangeUnlessBothGroupIdAndArtifactIdMatch()  = rewriteRun(
        { spec ->
            spec.recipe(
                ChangeDependencyGroupIdAndArtifactId(
                    "org.openrewrite.recipe",
                    "rewrite-testing-frameworks",
                    "org.openrewrite.recipe",
                    "rewrite-migrate-java",
                    null
                )
            )
        },
        pomXml(
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.openrewrite.recipe</groupId>
                            <artifactId>rewrite-spring</artifactId>
                            <version>4.12.0</version>
                        </dependency>
                    </dependencies>
                </project>
            """
        )
    )

    @Test
    fun changeDependencyGroupIdAndArtifactIdAndVersion()  = rewriteRun(
        { spec ->
            spec.recipe(
                ChangeDependencyGroupIdAndArtifactId(
                    "javax.activation",
                    "javax.activation-api",
                    "jakarta.activation",
                    "jakarta.activation-api",
                    "2.1.0"
                )
            )
        },
        pomXml(
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>javax.activation</groupId>
                            <artifactId>javax.activation-api</artifactId>
                            <version>1.2.0</version>
                        </dependency>
                    </dependencies>
                </project>
            """,
            """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>my-app</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>jakarta.activation</groupId>
                            <artifactId>jakarta.activation-api</artifactId>
                            <version>2.1.0</version>
                        </dependency>
                    </dependencies>
                </project>
            """
        )
    )

    @Test
    fun changeDependencyGroupIdAndArtifactIdWithDeepHierarchy() = rewriteRun(
        { spec ->
            spec.recipe(
                ChangeDependencyGroupIdAndArtifactId(
                    "io.quarkus",
                    "quarkus-core",
                    "io.quarkus",
                    "quarkus-arc",
                    null
                )
            )
        },
        pomXml(
            """
                <project>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <modules>
                        <module>child</module>
                    </modules>
                </project>
        """
        ) { p -> p.path(Paths.get("pom.xml")) },
        pomXml(
            """
                <project>
                    <parent>
                        <groupId>com.mycompany.app</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>child</artifactId>
                    <version>1</version>
                    <modules>
                        <module>subchild</module>
                    </modules>
                </project>
            """
        ) { p -> p.path(Paths.get("child/pom.xml")) },
        pomXml(
            """
                <project>
                    <parent>
                        <groupId>com.mycompany.app</groupId>
                        <artifactId>child</artifactId>
                        <version>1</version>
                    </parent>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>subchild</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.quarkus</groupId>
                            <artifactId>quarkus-core</artifactId>
                            <version>2.8.0.Final</version>
                        </dependency>
                    </dependencies>
                </project>
            """,
            """
                <project>
                    <parent>
                        <groupId>com.mycompany.app</groupId>
                        <artifactId>child</artifactId>
                        <version>1</version>
                    </parent>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>subchild</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.quarkus</groupId>
                            <artifactId>quarkus-arc</artifactId>
                            <version>2.8.0.Final</version>
                        </dependency>
                    </dependencies>
                </project>
            """.trimIndent()
        ) { p -> p.path(Paths.get("child/subchild/pom.xml")) },
    )

    @Test
    @Issue("https://github.com/openrewrite/rewrite/issues/1717")
    fun changeDependencyGroupIdAndArtifactIdWithDependencyManagementScopeTest() = rewriteRun(
        { spec ->
            spec.recipe(
                ChangeDependencyGroupIdAndArtifactId(
                    "io.quarkus",
                    "quarkus-core",
                    "io.quarkus",
                    "quarkus-arc",
                    null
                )
            )
        },
        pomXml(
            """
                <project>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <modules>
                        <module>child</module>
                    </modules>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.quarkus</groupId>
                                <artifactId>quarkus-core</artifactId>
                                <version>2.8.0.Final</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.quarkus</groupId>
                                <artifactId>quarkus-arc</artifactId>
                                <version>2.8.0.Final</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
        """
        ) { p -> p.path(Paths.get("pom.xml")) },
        pomXml(
            """
                <project>
                    <parent>
                        <groupId>com.mycompany.app</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>child</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.quarkus</groupId>
                            <artifactId>quarkus-core</artifactId>
                        </dependency>
                    </dependencies>
                </project>
            """,
            """
                <project>
                    <parent>
                        <groupId>com.mycompany.app</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <groupId>com.mycompany.app</groupId>
                    <artifactId>child</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.quarkus</groupId>
                            <artifactId>quarkus-arc</artifactId>
                        </dependency>
                    </dependencies>
                </project>
            """
        ) { p -> p.path(Paths.get("child/pom.xml")) },
    )
}
