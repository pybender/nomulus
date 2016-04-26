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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ObjectArrays;
import com.google.common.io.Files;
import com.google.common.reflect.TypeToken;

import com.beust.jcommander.JCommander;

import google.registry.model.poll.PollMessage;
import google.registry.testing.AppEngineRule;
import google.registry.testing.CertificateSamples;
import google.registry.testing.ExceptionRule;
import google.registry.tools.params.ParameterFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Base class for all command tests.
 *
 * @param <C> the command type
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class CommandTestCase<C extends Command> {

  private ByteArrayOutputStream stdout = new ByteArrayOutputStream();

  protected C command;

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public TemporaryFolder tmpDir = new TemporaryFolder();

  @Before
  public final void beforeCommandTestCase() {
    // Ensure the UNITTEST environment has been set before constructing a new command instance.
    RegistryToolEnvironment.UNITTEST.setup();
    command = newCommandInstance();
    System.setOut(new PrintStream(stdout));
  }

  void runCommandInEnvironment(RegistryToolEnvironment env, String... args) throws Exception {
    env.setup();
    try {
      JCommander jcommander = new JCommander(command);
      jcommander.addConverterFactory(new ParameterFactory());
      jcommander.parse(args);
      command.run();
    } finally {
      // Clear the session cache so that subsequent reads for verification purposes hit datastore.
      // This primarily matters for AutoTimestamp fields, which otherwise won't have updated values.
      ofy().clearSessionCache();
      // Reset back to UNITTEST environment.
      RegistryToolEnvironment.UNITTEST.setup();
    }
  }

  void runCommand(String... args) throws Exception {
    runCommandInEnvironment(RegistryToolEnvironment.UNITTEST, args);
  }

  /** Adds "--force" as the first parameter, then runs the command. */
  void runCommandForced(String... args) throws Exception {
    runCommand(ObjectArrays.concat("--force", args));
  }

  /** Writes the data to a named temporary file and then returns a path to the file. */
  String writeToNamedTmpFile(String filename, byte[] data) throws IOException {
    File tmpFile = tmpDir.newFile(filename);
    Files.write(data, tmpFile);
    return tmpFile.getPath();
  }

  /** Writes the data to a named temporary file and then returns a path to the file. */
  String writeToNamedTmpFile(String filename, String...data) throws IOException {
    return writeToNamedTmpFile(filename, Joiner.on('\n').join(data).getBytes(UTF_8));
  }

  /** Writes the data to a temporary file and then returns a path to the file. */
  String writeToNamedTmpFile(String filename, Iterable<String> data) throws IOException {
    return writeToNamedTmpFile(filename, FluentIterable.from(data).toArray(String.class));
  }

  /** Writes the data to a temporary file and then returns a path to the file. */
  String writeToTmpFile(byte[] data) throws IOException {
    return writeToNamedTmpFile("tmp_file", data);
  }

  /** Writes the data to a temporary file and then returns a path to the file. */
  String writeToTmpFile(String...data) throws IOException {
    return writeToNamedTmpFile("tmp_file", data);
  }

  /** Writes the data to a temporary file and then returns a path to the file. */
  String writeToTmpFile(Iterable<String> data) throws IOException {
    return writeToNamedTmpFile("tmp_file", FluentIterable.from(data).toArray(String.class));
  }

  /** Returns a path to a known good certificate file. */
  String getCertFilename() throws IOException {
    return writeToNamedTmpFile("cert.pem", CertificateSamples.SAMPLE_CERT);
  }

  /** Reloads the given resource from Datastore. */
  <T> T reloadResource(T resource) {
    return ofy().load().entity(resource).now();
  }

  /** Returns count of all poll messages in Datastore. */
  int getPollMessageCount() {
    return ofy().load().type(PollMessage.class).count();
  }

  void assertInStdout(String expected) throws Exception {
    assertThat(stdout.toString(UTF_8.toString())).contains(expected);
  }

  void assertInStdout(Pattern expected) throws Exception {
    assertThat(stdout.toString(UTF_8.toString())).containsMatch(expected);
  }

  void assertNotInStdout(String expected) throws Exception {
    assertThat(stdout.toString(UTF_8.toString())).doesNotContain(expected);
  }

  String getStdoutAsString() {
    return new String(stdout.toByteArray(), UTF_8);
  }

  List<String> getStdoutAsLines() {
    return Splitter.on('\n').omitEmptyStrings().trimResults().splitToList(getStdoutAsString());
  }

  @SuppressWarnings("unchecked")
  protected C newCommandInstance() {
    try {
      return (C) new TypeToken<C>(getClass()){}.getRawType().newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
