/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.app.runtime.spark.distributed;

import co.cask.cdap.app.guice.ClusterMode;
import co.cask.cdap.app.guice.DistributedArtifactManagerModule;
import co.cask.cdap.app.guice.UnsupportedPluginFinder;
import co.cask.cdap.app.runtime.ProgramOptions;
import co.cask.cdap.app.runtime.ProgramRunner;
import co.cask.cdap.app.runtime.ProgramRuntimeProvider;
import co.cask.cdap.app.runtime.spark.SparkProgramRuntimeProvider;
import co.cask.cdap.common.conf.CConfiguration;
import co.cask.cdap.internal.app.runtime.ProgramRunners;
import co.cask.cdap.internal.app.runtime.artifact.PluginFinder;
import co.cask.cdap.internal.app.runtime.distributed.AbstractProgramTwillRunnable;
import co.cask.cdap.internal.app.spark.SparkCompatReader;
import co.cask.cdap.proto.ProgramType;
import co.cask.cdap.proto.id.ProgramRunId;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import org.apache.hadoop.conf.Configuration;
import org.apache.twill.api.TwillRunnable;

/**
 * A {@link TwillRunnable} wrapper for {@link ProgramRunner} that runs spark.
 */
public class SparkTwillRunnable extends AbstractProgramTwillRunnable<ProgramRunner> {

  /**
   * Main method for the remote execution mode.
   */
  public static void main(String[] args) throws Exception {
    new SparkTwillRunnable(getRunnableNameFromEnv()).doMain();
  }

  SparkTwillRunnable(String name) {
    super(name);
  }

  @Override
  protected ProgramRunner createProgramRunner(Injector injector) {
    // Inside the TwillRunanble, we use the "Local" SparkRunner, since we need to actually submit the job.
    // The actual execution mode of the job is governed by the framework configuration,
    // which is in the hConf we shipped from DistributedSparkProgramRunner
    CConfiguration cConf = injector.getInstance(CConfiguration.class);
    return new SparkProgramRuntimeProvider(SparkCompatReader.get(cConf)) { }
      .createProgramRunner(ProgramType.SPARK, ProgramRuntimeProvider.Mode.LOCAL, injector);
  }

  @Override
  protected Module createModule(CConfiguration cConf, Configuration hConf,
                                ProgramOptions programOptions, ProgramRunId programRunId) {

    Module module = super.createModule(cConf, hConf, programOptions, programRunId);

    // Only supports dynamic artifacts fetching when running on-prem
    return ProgramRunners.getClusterMode(programOptions) == ClusterMode.ON_PREMISE
      ? Modules.combine(module, new DistributedArtifactManagerModule())
      : Modules.combine(module, new AbstractModule() {
      @Override
      protected void configure() {
        bind(PluginFinder.class).to(UnsupportedPluginFinder.class);
      }
    });
  }
}
