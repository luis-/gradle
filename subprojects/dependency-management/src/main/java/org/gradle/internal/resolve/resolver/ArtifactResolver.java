/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.resolve.resolver;

import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

public interface ArtifactResolver {
    /**
     * Discovers the set of artifacts belonging to the given component, with the type specified. Does not download the artifacts. Any failures are packaged up in the result.
     */
    void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result);

    /**
     * Resolves the given artifact. Any failures are packaged up in the result.
     */
    void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactResolveResult result);

    /**
     * Should the optional artifact be resolved?
     *
     * This does not mean that the artifact exists, only that the artifact should be attempted to be resolved.
     */
    boolean resolveOptionalArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources);
}
