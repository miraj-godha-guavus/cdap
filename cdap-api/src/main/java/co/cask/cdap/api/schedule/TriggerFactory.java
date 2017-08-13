/*
 * Copyright © 2017 Cask Data, Inc.
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

package co.cask.cdap.api.schedule;

import co.cask.cdap.api.ProgramStatus;
import co.cask.cdap.api.app.ProgramType;
import co.cask.cdap.internal.schedule.ScheduleCreationSpec;

/**
 * A factory for getting a specific type of {@link Trigger}
 */
public interface TriggerFactory {

  Trigger or(Trigger... triggers);

  Trigger and(Trigger... triggers);

  /**
   * Create a schedule which is triggered based upon the given cron expression.
   *
   * @param cronExpression the cron expression to specify the time to trigger the schedule
   * @return a {@link ScheduleCreationSpec}
   */
  Trigger byTime(String cronExpression);

  /**
   * Create a schedule which is triggered whenever at least a certain number of new partitions
   * are added to a certain dataset in the same namespace as the app.
   *
   * @param datasetName the name of the dataset in the same namespace of the app
   * @param numPartitions the minimum number of new partitions added to the dataset to trigger the schedule
   * @return a {@link ScheduleCreationSpec}
   */
  Trigger onPartitions(String datasetName, int numPartitions);

  /**
   * Create a schedule which is triggered whenever at least a certain number of new partitions
   * are added to a certain dataset in the specified namespace.
   *
   * @param datasetNamespace the namespace where the dataset is defined
   * @param datasetName the name of the dataset in the specified namespace of the app
   * @param numPartitions the minimum number of new partitions added to the dataset to trigger the schedule
   * @return a {@link ScheduleCreationSpec}
   */
  Trigger onPartitions(String datasetNamespace, String datasetName, int numPartitions);

  /**
   * Create a schedule which is triggered when the given program in the given namespace, application, and
   * application version transitions to any one of the given program statuses.
   *
   * @param programNamespace the namespace where this program is defined
   * @param application the name of the application where this program is defined
   * @param appVersion the version of the application
   * @param programType the type of the program, as supported by the system
   * @param program the name of the program
   * @param programStatuses the set of statuses to trigger the schedule. The schedule will be triggered if the status of
   *                        the specific program transitioned to one of these statuses.
   * @return a {@link ScheduleCreationSpec}
   */
  Trigger onProgramStatus(String programNamespace, String application, String appVersion,
                          ProgramType programType, String program,
                          ProgramStatus... programStatuses);

  /**
   * Create a schedule which is triggered when the given program in the given namespace
   * and application with default version transitions to any one of the given program statuses.
   *
   * @see #onProgramStatus(String, String, String, ProgramType, String, ProgramStatus...)
   */
  Trigger onProgramStatus(String programNamespace, String application, ProgramType programType,
                          String program, ProgramStatus... programStatuses);

  /**
   * Creates a schedule which is triggered when the given program in the given application in the same namespace
   * transitions to any one of the given program statuses.
   *
   * @see #onProgramStatus(String, String, ProgramType, String, ProgramStatus...)
   */
  Trigger onProgramStatus(String application, ProgramType programType,
                          String program, ProgramStatus... programStatuses);

  /**
   * Creates a schedule which is triggered when the given program in the same namespace, application,
   * and application version transitions to any one of the given program statuses.
   *
   * @see #onProgramStatus(String, String, String, ProgramType, String, ProgramStatus...)
   */
  Trigger onProgramStatus(ProgramType programType, String program, ProgramStatus... programStatuses);
}
