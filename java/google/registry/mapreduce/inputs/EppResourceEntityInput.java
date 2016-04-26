// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.mapreduce.inputs;

import static google.registry.util.TypeUtils.checkNoInheritanceRelationships;

import com.google.appengine.tools.mapreduce.Input;
import com.google.appengine.tools.mapreduce.InputReader;
import com.google.common.collect.ImmutableSet;

import com.googlecode.objectify.Key;

import google.registry.model.EppResource;
import google.registry.model.index.EppResourceIndexBucket;

/** A MapReduce {@link Input} that loads all {@link EppResource} objects of a given type. */
class EppResourceEntityInput<R extends EppResource> extends EppResourceBaseInput<R> {

  private static final long serialVersionUID = 8162607479124406226L;

  private final ImmutableSet<Class<? extends R>> resourceClasses;

  public EppResourceEntityInput(ImmutableSet<Class<? extends R>> resourceClasses) {
    this.resourceClasses = resourceClasses;
    checkNoInheritanceRelationships(ImmutableSet.<Class<?>>copyOf(resourceClasses));
  }

  @Override
  protected InputReader<R> bucketToReader(Key<EppResourceIndexBucket> bucketKey) {
    return new EppResourceEntityReader<R>(bucketKey, resourceClasses);
  }
}
