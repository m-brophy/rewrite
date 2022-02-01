/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.maven;

import org.openrewrite.DelegatingExecutionContext;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.cache.InMemoryMavenPomCache;
import org.openrewrite.maven.cache.MavenPomCache;
import org.openrewrite.maven.internal.MavenParsingException;
import org.openrewrite.maven.tree.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

public class MavenExecutionContextView extends DelegatingExecutionContext {
    private static final MavenPomCache DEFAULT_POM_CACHE = new InMemoryMavenPomCache();

    private static final String MAVEN_MIRRORS = "org.openrewrite.maven.mirrors";
    private static final String MAVEN_CREDENTIALS = "org.openrewrite.maven.auth";
    private static final String MAVEN_REPOSITORIES = "org.openrewrite.maven.repos";
    private static final String MAVEN_PINNED_SNAPSHOT_VERSIONS = "org.openrewrite.maven.pinnedSnapshotVersions";
    private static final String MAVEN_POM_CACHE = "org.openrewrite.maven.pomCache";
    private static final String MAVEN_RESOLUTION_LISTENER = "org.openrewrite.maven.resolutionListener";

    public MavenExecutionContextView(ExecutionContext delegate) {
        super(delegate);
    }

    public static MavenExecutionContextView view(ExecutionContext ctx) {
        if(ctx instanceof MavenExecutionContextView) {
            return (MavenExecutionContextView) ctx;
        }
        return new MavenExecutionContextView(ctx);
    }

    public void setResoutionListener(ResolutionEventListener listener) {
        putMessage(MAVEN_RESOLUTION_LISTENER, listener);
    }

    public ResolutionEventListener getResolutionListener() {
        return getMessage(MAVEN_RESOLUTION_LISTENER, ResolutionEventListener.NOOP);
    }

    public void setMirrors(Collection<MavenRepositoryMirror> mirrors) {
        putMessage(MAVEN_MIRRORS, mirrors);
    }

    public Collection<MavenRepositoryMirror> getMirrors() {
        return getMessage(MAVEN_MIRRORS, emptyList());
    }

    public void setCredentials(Collection<MavenRepositoryCredentials> credentials) {
        putMessage(MAVEN_CREDENTIALS, credentials);
    }

    public Collection<MavenRepositoryCredentials> getCredentials() {
        return getMessage(MAVEN_CREDENTIALS, emptyList());
    }

    public void setPomCache(MavenPomCache pomCache) {
        putMessage(MAVEN_POM_CACHE, pomCache);
    }

    public MavenPomCache getPomCache() {
        return getMessage(MAVEN_POM_CACHE, DEFAULT_POM_CACHE);
    }

    public void setRepositories(List<MavenRepository> repositories) {
        putMessage(MAVEN_REPOSITORIES, repositories);
    }

    public List<MavenRepository> getRepositories() {
        return getMessage(MAVEN_REPOSITORIES, emptyList());
    }

    /**
     * Require dependency resolution that encounters a matching group:artifact:version coordinate to resolve to a
     * particular dated snapshot version, effectively making snapshot resolution deterministic.
     *
     * @param pinnedSnapshotVersions A set of group:artifact:version and the dated snapshot version to pin them to.
     */
    public void setPinnedSnapshotVersions(Collection<ResolvedGroupArtifactVersion> pinnedSnapshotVersions) {
        putMessage(MAVEN_PINNED_SNAPSHOT_VERSIONS, pinnedSnapshotVersions);
    }

    public Collection<ResolvedGroupArtifactVersion> getPinnedSnapshotVersions() {
        return getMessage(MAVEN_PINNED_SNAPSHOT_VERSIONS, emptyList());
    }

    public void setMavenSettings(@Nullable MavenSettings settings, String... activeProfiles) {
        if (settings == null) {
            return;
        }

        if (settings.getServers() != null) {
            setCredentials(settings.getServers().getServers().stream()
                    .map(server -> new MavenRepositoryCredentials(server.getId(), server.getUsername(), server.getPassword()))
                    .collect(Collectors.toList()));
        }

        if (settings.getMirrors() != null) {
            setMirrors(settings.getMirrors().getMirrors().stream()
                    .map(mirror -> new MavenRepositoryMirror(mirror.getId(), mirror.getUrl(), mirror.getMirrorOf(), mirror.getReleases(), mirror.getSnapshots()))
                    .collect(Collectors.toList()));
        }

        setRepositories(settings.getActiveRepositories(Arrays.asList(activeProfiles)).stream()
                .map(repo -> {
                    try {
                        return new MavenRepository(
                                repo.getId(),
                                repo.getUrl(),
                                repo.getReleases() == null || repo.getReleases().isEnabled(),
                                repo.getSnapshots() != null && repo.getSnapshots().isEnabled(),
                                null,
                                null
                        );
                    } catch (Exception exception) {
                        this.getOnError().accept(new MavenParsingException(
                                "Unable to parse URL %s for Maven settings repository id %s",
                                exception,
                                repo.getUrl(), repo.getId()));
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }
}
