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
package org.openrewrite.maven.utilities;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.CheckedFunction1;
import okhttp3.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.maven.cache.MavenArtifactCache;
import org.openrewrite.maven.internal.MavenDownloadingException;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.MavenRepository;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public class MavenArtifactDownloader {
    private static final RetryConfig retryConfig = RetryConfig.custom()
            .retryExceptions(SocketTimeoutException.class, TimeoutException.class)
            .build();

    private static final RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectionSpecs(Arrays.asList(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .build();

    private static final Retry mavenDownloaderRetry = retryRegistry.retry("MavenDownloader");

    private static final CheckedFunction1<Request, Response> sendRequest = Retry.decorateCheckedFunction(
            mavenDownloaderRetry,
            request -> httpClient.newCall(request).execute());

    private final MavenArtifactCache mavenArtifactCache;
    private final Map<String, MavenSettings.Server> serverIdToServer;
    private final Consumer<Throwable> onError;

    public MavenArtifactDownloader(MavenArtifactCache mavenArtifactCache,
                                   @Nullable MavenSettings settings,
                                   Consumer<Throwable> onError) {
        this.mavenArtifactCache = mavenArtifactCache;
        this.onError = onError;
        this.serverIdToServer = settings == null || settings.getServers() == null ?
                new HashMap<>() :
                settings.getServers().getServers().stream()
                        .collect(toMap(MavenSettings.Server::getId, Function.identity()));
    }

    /**
     * Fetch the jar file indicated by the dependency.
     *
     * @param dependency The dependency to download.
     * @return The path on disk of the downloaded artifact or <code>null</code> if unable to download.
     */
    @Nullable
    public Path downloadArtifact(ResolvedDependency dependency) {
        if (dependency.getRequested().getType() != null && !"jar".equals(dependency.getRequested().getType())) {
            return null;
        }

        return mavenArtifactCache.computeArtifact(dependency, () -> {
            try {
                String uri = dependency.getRepository().getUri() + "/" +
                        dependency.getGroupId().replace('.', '/') + '/' +
                        dependency.getArtifactId() + '/' +
                        dependency.getVersion() + '/' +
                        dependency.getArtifactId() + '-' +
                        (dependency.getDatedSnapshotVersion() == null ? dependency.getVersion() : dependency.getDatedSnapshotVersion()) +
                        ".jar";

                Request.Builder request = applyAuthentication(dependency.getRepository(),
                        new Request.Builder().url(uri).get());

                Response response = sendRequest.apply(request.build());
                ResponseBody body = response.body();

                if (!response.isSuccessful() || body == null) {
                    onError.accept(new MavenDownloadingException("Unable to download dependency %s:%s:%s. Response was %s",
                            dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), Integer.toString(response.code())));
                    response.close();
                    return null;
                }

                InputStream bodyStream = body.byteStream();

                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        return bodyStream.read();
                    }

                    @Override
                    public void close() throws IOException {
                        bodyStream.close();
                        response.close();
                    }
                };
            } catch (Throwable t) {
                onError.accept(t);
            }
            return null;
        }, onError);
    }

    private Request.Builder applyAuthentication(MavenRepository repository, Request.Builder request) {
        MavenSettings.Server authInfo = serverIdToServer.get(repository.getId());
        if (authInfo != null) {
            String credentials = Credentials.basic(authInfo.getUsername(), authInfo.getPassword());
            request.header("Authorization", credentials);
        } else if (repository.getUsername() != null && repository.getPassword() != null) {
            String credentials = Credentials.basic(repository.getUsername(), repository.getPassword());
            request.header("Authorization", credentials);
        }
        return request;
    }
}
