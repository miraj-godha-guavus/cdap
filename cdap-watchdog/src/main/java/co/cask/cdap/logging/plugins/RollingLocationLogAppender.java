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

package co.cask.cdap.logging.plugins;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.LogbackException;
import ch.qos.logback.core.rolling.RollingPolicy;
import ch.qos.logback.core.rolling.RolloverFailure;
import ch.qos.logback.core.rolling.TriggeringPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.WarnStatus;
import co.cask.cdap.proto.id.NamespaceId;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Rolling Appender for {@link Location}
 */
public class RollingLocationLogAppender extends FileAppender<ILoggingEvent> implements Flushable {
  private static final Logger LOG = LoggerFactory.getLogger(RollingLocationLogAppender.class);

  // TODO CDAP-8196 LocationFactory should be passed from Log framework
  @Inject
  private LocationFactory locationFactory;

  private TriggeringPolicy<ILoggingEvent> triggeringPolicy;
  // used as cache to avoid type casting of triggeringPolicy on every event
  private LocationTriggeringPolicy locationTriggeringPolicy;
  private RollingPolicy rollingPolicy;
  // used as cache to avoid type casting of triggeringPolicy on every event
  private LocationRollingPolicy locationRollingPolicy;

  // log file path will be created by this appender as: <basePath>/namespaceId/applicationId/<filePath>
  private String basePath;
  private String filePath;
  private String filePermissions;
  private LocationManager locationManager;

  public RollingLocationLogAppender() {
    setName(getClass().getName());
  }

  @Override
  public void start() {
    if (validateAppenderProperties()) {
      return;
    }

    // TODO remove if condition after removing Guice injection
    if (locationFactory != null) {
      try {
        this.locationManager = new LocationManager(locationFactory, basePath, filePermissions);
        started = true;
      } catch (IOException e) {
        // not handling it since we will remove this if condition after log framework is in place
      }
    }
  }

  @Override
  public void doAppend(ILoggingEvent eventObject) throws LogbackException {
    try {
      // TODO Remove 'if' after removing Guice injection
      if (locationFactory != null && this.locationManager == null) {
        this.locationManager = new LocationManager(locationFactory, basePath, filePermissions);
        started = true;
      }

      // logic from AppenderBase
      if (!this.started) {
        addStatus(new WarnStatus("Attempted to append to non started appender [" + name + "].", this));
        return;
      }

      // logic from AppenderBase
      if (getFilterChainDecision(eventObject) == FilterReply.DENY) {
        return;
      }

      String namespaceId = eventObject.getMDCPropertyMap().get(LocationManager.TAG_NAMESPACE_ID);
      if (!namespaceId.equals(NamespaceId.SYSTEM.getEntityName())) {
        LocationIdentifier logLocationIdentifier = locationManager.getLocationIdentifier(eventObject
                                                                                           .getMDCPropertyMap());
        rollover(logLocationIdentifier, eventObject);
        OutputStream locationOutputStream = locationManager.getLocationOutputStream(logLocationIdentifier, filePath);
        setOutputStream(locationOutputStream);
        writeOut(eventObject);
      }
    } catch (IllegalArgumentException iae) {
      // this shouldn't happen
      LOG.error("Unrecognized context ", iae);
    } catch (IOException ioe) {
      throw new LogbackException("Exception while appending event. ", ioe);
    }
  }

  private void rollover(final LocationIdentifier logLocationIdentifier, ILoggingEvent logEvent) {
    if (!locationManager.getActiveLocations().containsKey(logLocationIdentifier)) {
      return;
    }
    final LocationOutputStream locationOutputStream = locationManager.getActiveLocations().get(logLocationIdentifier);

    if (triggeringPolicy instanceof LocationTriggeringPolicy) {
      // no need to type cast on every event
      if (locationTriggeringPolicy == null) {
        locationTriggeringPolicy = ((LocationTriggeringPolicy) triggeringPolicy);
      }

      locationTriggeringPolicy.setLocation(locationOutputStream.getLocation());
      // set number of bytes written to locationOutputStream, we need to do this because HDFS does not provide
      // correct size of the file
      locationTriggeringPolicy.setActiveLocationSize(locationOutputStream.getNumOfBytes());

      if (locationTriggeringPolicy.isTriggeringEvent(logEvent)) {
        try {
          if (rollingPolicy instanceof LocationRollingPolicy) {
            // no need to type cast on every event
            if (locationRollingPolicy == null) {
              locationRollingPolicy = ((LocationRollingPolicy) rollingPolicy);
            }

            locationRollingPolicy.setLocation(locationOutputStream.getLocation(), new Closeable() {
              @Override
              public void close() throws IOException {
                locationOutputStream.close();
                locationManager.getActiveLocations().remove(logLocationIdentifier);
              }
            });

            locationRollingPolicy.rollover();
          }
        } catch (RolloverFailure e) {
          // we do not want to stop processing because roll over failed. so catch it and process the event
          LOG.warn("Attempt to rollover failed for appender {}.", name);
        }
      }
    }
  }

  @Override
  public void flush() throws IOException {
    locationManager.flush();
  }

  @Override
  public void stop() {
    if (encoder != null) {
      try {
        LOG.info("Stopping appender {}", this.name);
        encoder.close();
        locationManager.close();
      } catch (IOException ioe) {
        addStatus(new ErrorStatus("Failed to write footer for appender named [" + name + "].", this, ioe));
      } finally {
        this.started = false;
      }
    }
  }

  // override this method to setOutputStream for every event since this appender is appending to multiple files
  @Override
  public void setOutputStream(OutputStream outputStream) {
    if (encoder == null) {
      addWarn("Encoder has not been set. Cannot invoke its init method.");
      return;
    }

    try {
      encoder.init(outputStream);
    } catch (IOException ioe) {
      this.started = false;
      LOG.error("Failed to initialize encoder for appender named {}", name, ioe);
    }
  }

  // Since this appender does not support prudent mode, we override writeOut method from FileAppender
  @Override
  protected void writeOut(ILoggingEvent event) throws IOException {
    this.encoder.doEncode(event);
  }

  private boolean validateAppenderProperties() {
    if (basePath == null) {
      addWarn("No basePath was set for the RollingFileAppender named " + getName());
      return true;
    }

    if (filePath == null) {
      addWarn("No filePath was set for the RollingFileAppender named " + getName());
      return true;
    }

    if (triggeringPolicy == null) {
      addWarn("No TriggeringPolicy was set for the RollingFileAppender named " + getName());
      return true;
    }

    if (rollingPolicy == null) {
      addError("No RollingPolicy was set for the RollingFileAppender named " + getName());
      return true;
    }

    // logic from OutputStreamAppender
    if (this.encoder == null) {
      addStatus(new ErrorStatus("No encoder set for the appender named \"" + name + "\".", this));
      return true;
    }
    return false;
  }

  @VisibleForTesting
  LocationManager getLocationManager() {
    return locationManager;
  }

  public void setRollingPolicy(RollingPolicy policy) {
    rollingPolicy = policy;
  }

  public void setTriggeringPolicy(TriggeringPolicy<ILoggingEvent> policy) {
    triggeringPolicy = policy;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public String getFilePermissions() {
    return filePermissions;
  }

  public void setFilePermissions(String filePermissions) {
    this.filePermissions = filePermissions;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }
}
