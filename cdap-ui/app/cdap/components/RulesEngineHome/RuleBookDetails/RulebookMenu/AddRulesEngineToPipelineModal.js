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

/*
  1. Get batch and realtime artifacts.
  2. Check if yare is present
  3. Make a batch and realtime config with yare in its stage.
  4. Show the modal.
*/
import React, {Component, PropTypes} from 'react';
import {MyArtifactApi} from 'api/artifact';
import NamespaceStore from 'services/NamespaceStore';
import {findHighestVersion} from 'services/VersionRange/VersionUtilities';
import {Modal, ModalHeader, ModalBody} from 'reactstrap';
import MyRulesEngine from 'api/rulesengine';
import classnames from 'classnames';

require('./AddRulesEngineToPipelineModal.scss');

export default class AddRulesEngineToPipelineModal extends Component {
  static propTypes = {
    rulebookid: PropTypes.string,
    isOpen: PropTypes.bool,
    onClose: PropTypes.func
  };

  state = {
    rulebookid: this.props.rulebookid,
    isOpen: false,
    batchUrl: '',
    realtimeUrl: '',
    batchConfig: null,
    realtimeConfig: null
  };

  updateBatchAndRealtimeConfigs = () => {
    let {selectedNamespace: namespace} = NamespaceStore.getState();
    MyArtifactApi
      .list({namespace})
      .combineLatest([
        MyRulesEngine
          .getRulebook({
            namespace,
            rulebookid: this.props.rulebookid
          })
      ])
      .subscribe(
        (res) => {
          let artifacts = res[0];
          let rulebook = res[1].values[0];
          let batchArtifacts = artifacts.filter(artifact => artifact.name === 'cdap-data-pipeline');
          let realtimeArtifacts = artifacts.filter(artifact => artifact.name === 'cdap-data-streams');
          let yareArtifact = artifacts.filter(artifact => artifact.name === 'yare-plugins');
          let batchHighestArtifact, realtimeHighestArtifact;
          if (batchArtifacts.length > 1) {
            batchHighestArtifact = findHighestVersion(batchArtifacts.map(artifact => artifact.version), true);
          } else {
            batchHighestArtifact = batchArtifacts[0];
          }
          if (realtimeArtifacts.length > 1) {
            realtimeHighestArtifact = findHighestVersion(realtimeArtifacts.map(artifact => artifact.version), true);
          } else {
            realtimeHighestArtifact = realtimeArtifacts[0];
          }
          let rulesEnginePlugin = {
            name: 'RulesEngine',
            plugin: {
              "name": "RulesEngine",
              "type": "transform",
              "label": "RulesEngine",
              artifact: yareArtifact,
              "properties": {
                "rulebook": rulebook
              }
            }
          };
          let batchConfig = {
            artifact: batchHighestArtifact,
            config: {
              stages: [rulesEnginePlugin],
              connections: []
            }
          };
          let realtimeConfig = {
            artifact: realtimeHighestArtifact,
            config: {
              stages: [rulesEnginePlugin],
              connections: []
            }
          };
          let batchUrl = window.getHydratorUrl({
            stateName: 'hydrator.create',
            stateParams: {
              namespace,
              rulesengineid: this.props.rulebookid,
              artifactType: batchHighestArtifact.name
            }
          });
          let realtimeUrl = window.getHydratorUrl({
            stateName: 'hydrator.create',
            stateParams: {
              namespace,
              rulesengineid: this.props.rulebookid,
              artifactType: realtimeHighestArtifact.name
            }
          });
          this.setState({
            batchConfig,
            batchUrl,
            realtimeUrl,
            realtimeConfig
          });
        }
      );
  };

  componentWillReceiveProps(nextProps) {
    if (this.state.isOpen !== nextProps.isOpen) {
      this.setState({
        isOpen: nextProps.isOpen
      });
    }
  }

  componentDidMount() {
    this.updateBatchAndRealtimeConfigs();
  }

  handleOnRealtimeUrlClick = () => {
    if (!this.state.realtimeUrl) { return; }
    window.localStorage.setItem(this.state.rulebookid, JSON.stringify(this.state.realtimeConfig));
  };

  handleOnBatchUrlClick = () => {
    window.localStorage.setItem(this.state.rulebookid, JSON.stringify(this.state.batchConfig));
  };

  render() {
    return (
      <Modal
        isOpen={this.state.isOpen}
        toggle={this.props.onClose}
        className="rules-engine-to-pipeline-modal"
        size="lg"
      >
        <ModalHeader>
          <span>Add to Pipeline</span>
        </ModalHeader>
        <ModalBody>
          <div className="message">
            Choose the type of pipeline to create
          </div>
          <div className="action-buttons">
            <a
              className="btn btn-secondary"
              href={this.state.batchUrl}
              onClick={this.handleOnBatchUrlClick}
            >
              <i className="fa icon-ETLBatch"/>
              <span>Batch Pipeline</span>
            </a>
            <a
              href={this.state.realtimeUrl}
              className={classnames('btn btn-secondary', {
                'inactive': !this.state.realtimeUrl
              })}
              onClick={this.handleOnRealtimeUrlClick}
            >
              <i className="fa icon-sparkstreaming"/>
              <span>Realtime Pipeline</span>
            </a>
          </div>
        </ModalBody>
      </Modal>
    );
  }
}
