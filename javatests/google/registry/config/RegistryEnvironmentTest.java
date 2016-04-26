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

package google.registry.config;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RegistryEnvironment}. */
@RunWith(JUnit4.class)
public class RegistryEnvironmentTest {

  @Test
  public void testGet() throws Exception {
    RegistryEnvironment.get();
  }

  @Test
  public void testOverride() throws Exception {
    RegistryEnvironment.overrideConfigurationForTesting(new TestRegistryConfig() {
      @Override
      public String getSnapshotsBucket() {
        return "black velvet";
      }});
    assertThat(RegistryEnvironment.get().config().getSnapshotsBucket()).isEqualTo("black velvet");
  }
}
