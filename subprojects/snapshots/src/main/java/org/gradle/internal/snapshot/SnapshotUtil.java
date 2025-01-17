/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.snapshot;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import org.gradle.internal.hash.HashCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class SnapshotUtil {

    public static Map<String, FileSystemLocationSnapshot> index(FileSystemSnapshot snapshot) {
        HashMap<String, FileSystemLocationSnapshot> index = new HashMap<>();
        snapshot.accept(entrySnapshot -> {
            index.put(entrySnapshot.getAbsolutePath(), entrySnapshot);
            return SnapshotVisitResult.CONTINUE;
        });
        return index;
    }

    public static Map<String, FileSystemLocationSnapshot> rootIndex(FileSystemSnapshot snapshot) {
        HashMap<String, FileSystemLocationSnapshot> index = new HashMap<>();
        snapshot.accept(entrySnapshot -> {
            index.put(entrySnapshot.getAbsolutePath(), entrySnapshot);
            return SnapshotVisitResult.SKIP_SUBTREE;
        });
        return index;
    }

    public static <T extends FileSystemNode> Optional<MetadataSnapshot> getMetadataFromChildren(ChildMap<T> children, VfsRelativePath targetPath, CaseSensitivity caseSensitivity, Supplier<Optional<MetadataSnapshot>> noChildFoundResult) {
        return children.withNode(targetPath, caseSensitivity, new ChildMap.NodeHandler<T, Optional<MetadataSnapshot>>() {
            @Override
            public Optional<MetadataSnapshot> handleAsDescendantOfChild(VfsRelativePath pathInChild, T child) {
                return child.getSnapshot(pathInChild, caseSensitivity);
            }

            @Override
            public Optional<MetadataSnapshot> handleAsAncestorOfChild(String childPath, T child) {
                return noChildFoundResult.get();
            }

            @Override
            public Optional<MetadataSnapshot> handleExactMatchWithChild(T child) {
                return child.getSnapshot();
            }

            @Override
            public Optional<MetadataSnapshot> handleUnrelatedToAnyChild() {
                return noChildFoundResult.get();
            }
        });
    }

    public static <T extends FileSystemNode> ReadOnlyFileSystemNode getChild(ChildMap<T> children, VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
        return children.withNode(targetPath, caseSensitivity, new ChildMap.NodeHandler<T, ReadOnlyFileSystemNode>() {
            @Override
            public ReadOnlyFileSystemNode handleAsDescendantOfChild(VfsRelativePath pathInChild, T child) {
                return child.getNode(pathInChild, caseSensitivity);
            }

            @Override
            public ReadOnlyFileSystemNode handleAsAncestorOfChild(String childPath, T child) {
                // TODO: This is not correct, it should be a node with the child at targetPath.pathFromChild(childPath).
                return child;
            }

            @Override
            public ReadOnlyFileSystemNode handleExactMatchWithChild(T child) {
                return child;
            }

            @Override
            public ReadOnlyFileSystemNode handleUnrelatedToAnyChild() {
                return ReadOnlyFileSystemNode.EMPTY;
            }
        });
    }

    public static ImmutableMultimap<String, HashCode> getRootHashes(FileSystemSnapshot roots) {
        if (roots == FileSystemSnapshot.EMPTY) {
            return ImmutableMultimap.of();
        }
        ImmutableMultimap.Builder<String, HashCode> builder = ImmutableListMultimap.builder();
        roots.accept(snapshot -> {
            builder.put(snapshot.getAbsolutePath(), snapshot.getHash());
            return SnapshotVisitResult.SKIP_SUBTREE;
        });
        return builder.build();
    }
}
