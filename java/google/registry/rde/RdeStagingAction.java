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

package google.registry.rde;

import static google.registry.util.PipelineUtils.createJobPath;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;

import google.registry.config.ConfigModule.Config;
import google.registry.mapreduce.MapreduceRunner;
import google.registry.mapreduce.inputs.EppResourceInputs;
import google.registry.mapreduce.inputs.NullInput;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactResource;
import google.registry.model.host.HostResource;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.rde.RdeMode;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.RegistryCursor;
import google.registry.model.registry.RegistryCursor.CursorType;
import google.registry.request.Action;
import google.registry.request.Response;
import google.registry.util.Clock;
import google.registry.util.FormattingLogger;

import org.joda.time.Duration;

import javax.inject.Inject;

/**
 * MapReduce that idempotently stages escrow deposit XML files on GCS for RDE/BRDA for all TLDs.
 *
 * <h3>MapReduce Operation</h3>
 *
 * <p>This task starts by asking {@link PendingDepositChecker} which deposits need to be generated.
 * If there's nothing to deposit, we return 204 No Content; otherwise, we fire off a MapReduce job
 * and redirect to its status GUI.
 *
 * <p>The mapreduce job scans every {@link EppResource} in datastore. It maps a point-in-time
 * representation of each entity to the escrow XML files in which it should appear.
 *
 * <p>There is one map worker for each {@code EppResourceIndexBucket} entity group shard. There is
 * one reduce worker for each deposit being generated.
 *
 * <p>{@link ContactResource} and {@link HostResource} are emitted on all TLDs, even when the
 * domains on a TLD don't reference them. BRDA {@link RdeMode#THIN thin} deposits exclude contacts
 * and hosts entirely.
 *
 * <p>{@link Registrar} entities, both active and inactive, are included in all deposits. They are
 * not rewinded point-in-time.
 *
 * <p>The XML deposit files generated by this job are humongous. A tiny XML report file is generated
 * for each deposit, telling us how much of what it contains.
 *
 * <p>Once a deposit is successfully generated, an {@link RdeUploadAction} is enqueued which will
 * upload it via SFTP to the third-party escrow provider.
 *
 * <p>To generate escrow deposits manually and locally, use the {@code registry_tool} command
 * {@code GenerateEscrowDepositCommand}.
 *
 * <h3>Logging</h3>
 *
 * <p>To identify the reduce worker request for a deposit in App Engine's log viewer, you can use
 * search text like {@code tld=soy}, {@code watermark=2015-01-01}, and {@code mode=FULL}.
 *
 * <h3>Error Handling</h3>
 *
 * <p>Valid model objects might not be valid to the RDE XML schema. A single invalid object will
 * cause the whole deposit to fail. You need to check the logs, find out which entities are broken,
 * and perform datastore surgery.
 *
 * <p>If a deposit fails, an error is emitted to the logs for each broken entity. It tells you the
 * key and shows you its representation in lenient XML.
 *
 * <p>Failed deposits will be retried indefinitely. This is because RDE and BRDA each have a
 * {@link RegistryCursor} for each TLD. Even if the cursor lags for days, it'll catch up gradually
 * on its own, once the data becomes valid.
 *
 * <p>The third-party escrow provider will validate each deposit we send them. They do both schema
 * validation and reference checking.
 *
 * <p>This job does not perform reference checking. Administrators can do this locally with the
 * {@code ValidateEscrowDepositCommand} command in {@code registry_tool}.
 *
 * <h3>Cursors</h3>
 *
 * <p>Deposits are generated serially for a given (tld, mode) pair. A deposit is never started
 * beyond the cursor. Once a deposit is completed, its cursor is rolled forward transactionally.
 *
 * <p>The mode determines which cursor is used. {@link CursorType#RDE_STAGING} is used for thick
 * deposits and {@link CursorType#BRDA} is used for thin deposits.
 *
 * <p>Use the {@code ListCursorsCommand} and {@code UpdateCursorsCommand} commands to administrate
 * with these cursors.
 *
 * <h3>Security</h3>
 *
 * <p>The deposit and report are encrypted using {@link Ghostryde}. Administrators can use the
 * {@code GhostrydeCommand} command in {@code registry_tool} to view them.
 *
 * <p>Unencrypted XML fragments are stored temporarily between the map and reduce steps. The
 * ghostryde encryption on the full archived deposits makes life a little more difficult for an
 * attacker. But security ultimately depends on the bucket.
 *
 * <h3>Idempotency</h3>
 *
 * <p>We lock the reduce tasks. This is necessary because: a) App Engine tasks might get double
 * executed; and b) Cloud Storage file handles get committed on close <i>even if our code throws an
 * exception.</i>
 *
 * <p>Deposits are generated serially for a given (watermark, mode) pair. A deposit is never started
 * beyond the cursor. Once a deposit is completed, its cursor is rolled forward transactionally.
 * Duplicate jobs may exist {@code <=cursor}. So a transaction will not bother changing the cursor
 * if it's already been rolled forward.
 *
 * <p>Enqueueing {@code RdeUploadAction} is also part of the cursor transaction. This is necessary
 * because the first thing the upload task does is check the staging cursor to verify it's been
 * completed, so we can't enqueue before we roll. We also can't enqueue after the roll, because then
 * if enqueueing fails, the upload might never be enqueued.
 *
 * <h3>Determinism</h3>
 *
 * <p>The filename of an escrow deposit is determistic for a given (TLD, watermark,
 * {@linkplain RdeMode mode}) triplet. Its generated contents is deterministic in all the ways that
 * we care about. Its view of the database is strongly consistent.
 *
 * <p>This is because:
 * <ol>
 * <li>{@code EppResource} queries are strongly consistent thanks to {@link EppResourceIndex}
 * <li>{@code EppResource} entities are rewinded to the point-in-time of the watermark
 * </ol>
 *
 * <p>Here's what's not deterministic:
 * <ul>
 * <li>Ordering of XML fragments. We don't care about this.
 * <li>Information about registrars. There's no point-in-time for these objects. So in order to
 *   guarantee referential correctness of your deposits, you must never delete a registrar entity.
 * </ul>
 *
 * @see "https://tools.ietf.org/html/draft-arias-noguchi-registry-data-escrow-06"
 * @see "https://tools.ietf.org/html/draft-arias-noguchi-dnrd-objects-mapping-05"
 */
@Action(path = "/_dr/task/rdeStaging")
public final class RdeStagingAction implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  @Inject Clock clock;
  @Inject PendingDepositChecker pendingDepositChecker;
  @Inject RdeStagingReducer reducer;
  @Inject Response response;
  @Inject MapreduceRunner mrRunner;
  @Inject @Config("transactionCooldown") Duration transactionCooldown;
  @Inject RdeStagingAction() {}

  @Override
  public void run() {
    ImmutableSetMultimap<String, PendingDeposit> pendings = ImmutableSetMultimap.copyOf(
        Multimaps.filterValues(
            pendingDepositChecker.getTldsAndWatermarksPendingDepositForRdeAndBrda(),
            new Predicate<PendingDeposit>() {
              @Override
              public boolean apply(PendingDeposit pending) {
                if (clock.nowUtc().isBefore(pending.watermark().plus(transactionCooldown))) {
                  logger.infofmt("Ignoring within %s cooldown: %s", transactionCooldown, pending);
                  return false;
                } else {
                  return true;
                }
              }}));
    if (pendings.isEmpty()) {
      String message = "Nothing needs to be deposited";
      logger.info(message);
      response.setStatus(SC_NO_CONTENT);
      response.setPayload(message);
      return;
    }
    for (PendingDeposit pending : pendings.values()) {
      logger.infofmt("%s", pending);
    }
    response.sendJavaScriptRedirect(createJobPath(mrRunner
        .setJobName("Stage escrow deposits for all TLDs")
        .setModuleName("backend")
        .setDefaultReduceShards(pendings.size())
        .runMapreduce(
            new RdeStagingMapper(pendings),
            reducer,
            ImmutableList.of(
                // Add an extra shard that maps over a null resource. See the mapper code for why.
                new NullInput<EppResource>(),
                EppResourceInputs.createEntityInput(EppResource.class)))));
  }
}
