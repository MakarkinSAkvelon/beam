/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.loadtests;

import static java.lang.String.format;
import static org.apache.beam.sdk.loadtests.GroupByKeyLoadTest.Options.readFromArgs;
import static org.apache.beam.sdk.loadtests.SyntheticUtils.createStep;
import static org.apache.beam.sdk.loadtests.SyntheticUtils.fromJsonString;

import java.io.IOException;
import java.util.Optional;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.synthetic.SyntheticBoundedIO;
import org.apache.beam.sdk.io.synthetic.SyntheticBoundedIO.SyntheticSourceOptions;
import org.apache.beam.sdk.io.synthetic.SyntheticStep;
import org.apache.beam.sdk.loadtests.metrics.MetricsMonitor;
import org.apache.beam.sdk.loadtests.metrics.MetricsPublisher;
import org.apache.beam.sdk.options.ApplicationNameOptions;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

/**
 * Load test for {@link GroupByKey} operation.
 *
 * <p>The purpose of this test is to measure {@link GroupByKey}'s behaviour in stressful conditions.
 * it uses {@link SyntheticBoundedIO} and {@link SyntheticStep} which both can be parametrized to
 * generate keys and values of various size, impose delay (sleep or cpu burnout) in various moments
 * during the pipeline execution and provide some other performance challenges (see Source's and
 * Step's documentation for more details).
 *
 * <p>In addition, this test allows to: - fanout: produce one input (using Synthetic Source) and
 * process it with multiple sessions performing the same set of operations - reiterate produced
 * PCollection multiple times
 *
 * <p>To run it manually, use the following command:
 *
 * <pre>
 *    ./gradlew :beam-sdks-java-load-tests:run -PloadTest.args='
 *      --fanout=1
 *      --iterations=1
 *      --sourceOptions={"numRecords":1000,...}
 *      --stepOptions={"outputRecordsPerInputRecord":2...}'
 * </pre>
 */
public class GroupByKeyLoadTest {

  /** Pipeline options for the test. */
  public interface Options extends PipelineOptions, ApplicationNameOptions {

    @Description("Options for synthetic source")
    @Validation.Required
    String getSourceOptions();

    void setSourceOptions(String sourceOptions);

    @Description("Options for synthetic step")
    String getStepOptions();

    void setStepOptions(String stepOptions);

    @Description("The number of GroupByKey operations to perform in parallel (fanout)")
    @Default.Integer(1)
    Integer getFanout();

    void setFanout(Integer fanout);

    @Description("Number of reiterations over per-key-grouped values to perform.")
    @Default.Integer(1)
    Integer getIterations();

    void setIterations(Integer iterations);

    static Options readFromArgs(String[] args) {
      return PipelineOptionsFactory.fromArgs(args).withValidation().as(Options.class);
    }
  }

  public static void main(String[] args) throws IOException {
    Options options = readFromArgs(args);

    SyntheticSourceOptions sourceOptions =
        fromJsonString(options.getSourceOptions(), SyntheticSourceOptions.class);

    Optional<SyntheticStep> syntheticStep = createStep(options.getStepOptions());

    Pipeline pipeline = Pipeline.create(options);

    PCollection<KV<byte[], byte[]>> input =
        pipeline.apply(SyntheticBoundedIO.readFrom(sourceOptions));

    for (int branch = 0; branch < options.getFanout(); branch++) {
      SyntheticUtils.applyStepIfPresent(input, format("Synthetic step (%s)", branch), syntheticStep)
          .apply(ParDo.of(new MetricsMonitor("gbk")))
          .apply(format("Group by key (%s)", branch), GroupByKey.create())
          .apply(
              format("Ungroup and reiterate (%s)", branch),
              ParDo.of(new UngroupAndReiterate(options.getIterations())));
    }

    PipelineResult result = pipeline.run();
    result.waitUntilFinish();

    MetricsPublisher.toConsole(result, "gbk");
  }

  private static class UngroupAndReiterate
      extends DoFn<KV<byte[], Iterable<byte[]>>, KV<byte[], byte[]>> {

    private int iterations;

    UngroupAndReiterate(int iterations) {
      this.iterations = iterations;
    }

    @ProcessElement
    public void processElement(ProcessContext c) {
      byte[] key = c.element().getKey();

      // reiterate "iterations" times, emit output only once
      for (int i = 0; i < iterations; i++) {
        for (byte[] value : c.element().getValue()) {

          if (i == iterations - 1) {
            c.output(KV.of(key, value));
          }
        }
      }
    }
  }
}
