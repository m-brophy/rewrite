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
package org.openrewrite.maven

import org.junit.jupiter.api.Test

class RemoveManagedDependencyTest : MavenRecipeTest {

    @Test
    fun removeManagedDependency() = assertChanged(
        recipe =  RemoveManagedDependency(
            "javax.activation",
            "javax.activation-api",
            null
        ),
        before = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
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
        after = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
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

    @Test
    fun removeManagedDependencyWithScopeNone() = assertChanged(
        recipe =  RemoveManagedDependency(
            "javax.activation",
            "javax.activation-api",
            null
        ),
        before = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>javax.activation</groupId>
                            <artifactId>javax.activation-api</artifactId>
                            <version>1.2.0</version>
                            <scope>test</scope>
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
        after = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
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

    @Test
    fun removeManagedDependencyWithScopeMatching() = assertChanged(
        recipe =  RemoveManagedDependency(
            "javax.activation",
            "javax.activation-api",
            "test"
        ),
        before = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>javax.activation</groupId>
                            <artifactId>javax.activation-api</artifactId>
                            <version>1.2.0</version>
                            <scope>test</scope>
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
        after = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
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

    @Test
    fun removeManagedDependencyWithScopeNonMatching() = assertUnchanged(
        recipe =  RemoveManagedDependency(
            "javax.activation",
            "javax.activation-api",
            "compile"
        ),
        before = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.mycompany.app</groupId>
                <artifactId>my-app</artifactId>
                <version>1</version>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>javax.activation</groupId>
                            <artifactId>javax.activation-api</artifactId>
                            <version>1.2.0</version>
                            <scope>test</scope>
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
}
