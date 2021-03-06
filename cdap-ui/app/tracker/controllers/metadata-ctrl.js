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

/**
 * This class is responsible for controlling the Metadata View in Tracker
 * entity detai page.
 **/

 /*
   TODO:
     - Add support for Stream Views
     - What to do with externalDataset type
 */

class TrackerMetadataController {
  constructor($state, myTrackerApi, $scope, myAlertOnValium, $timeout, $q, caskFocusManager) {
    this.$state = $state;
    this.myTrackerApi = myTrackerApi;
    this.$scope = $scope;
    this.myAlertOnValium = myAlertOnValium;
    this.$timeout = $timeout;
    this.$q = $q;
    this.caskFocusManager = caskFocusManager;
    this.duplicateTag = false;

    this.propertyInput = {
      key: '',
      value: ''
    };

    let entitySplit = this.$state.params.entityType.split(':');
    this.entityType = entitySplit;

    let params = {
      scope: this.$scope,
      namespace: this.$state.params.namespace,
      entityType: entitySplit[0],
      showCustom: true,
    };

    let metadataApi;

    if (entitySplit.length > 1) {
      params.entityId = entitySplit[1];
      params.entityType = 'streams';
      params.viewId = this.$state.params.entityId;
      metadataApi = this.myTrackerApi.viewsProperties(params).$promise;
    } else {
      params.entityId = this.$state.params.entityId;
      params.entityType = entitySplit[0];
      metadataApi = this.myTrackerApi.properties(params).$promise;
    }

    this.systemTags = {};
    this.userTags = [];

    this.getUserTags();

    this.schema = [];
    this.properties = {};
    this.activePropertyTab = 0;
    this.tagInputModel = '';

    this.loading = true;

    metadataApi.then( (res) => {
      this.loading = false;
      this.processResponse(res);
    }, (err) => {
      this.loading = false;
      console.log('Error', err);
    });

  }

  processResponse(res) {
    let systemMetadata, userMetadata;

    angular.forEach(res, (response) => {
      if (response.scope === 'USER') {
        userMetadata = response;
      } else if (response.scope === 'SYSTEM'){
        systemMetadata = response;
      }
    });

    this.systemTags = {
      system: systemMetadata.tags
    };

    this.properties = {
      system: systemMetadata.properties,
      user: userMetadata.properties,
      isUserEmpty: false,
      isSystemEmpty: false
    };

    /**
     * Need to show Dataset Spec from Dataset Properties if
     * dataset type is externalDataset. Ideally Backend should
     * return this automatically.
     **/
    if (systemMetadata.properties.type === 'externalDataset') {
      this.fetchExternalDatasetProperties();
    }

    if (Object.keys(userMetadata.properties).length === 0) {
      this.activePropertyTab = 1;
      this.properties.isUserEmpty = true;
    }

    this.properties.isSystemEmpty = Object.keys(systemMetadata.properties).length === 0;

    this.schema = systemMetadata.properties.schema;
  }

  fetchExternalDatasetProperties() {
    let datasetParams = {
      namespace: this.$state.params.namespace,
      entityId: this.$state.params.entityId,
      scope: this.$scope
    };
    this.myTrackerApi.getDatasetDetail(datasetParams)
      .$promise
      .then( (res) => {
        this.externalDatasetProperties = res.spec.properties;

        if (Object.keys(this.externalDatasetProperties).length > 0) {
          this.activePropertyTab = 0;
          this.properties.isUserEmpty = false;
        }
      });
  }

  /* METADATA PROPERTIES CONTROL */
  deleteProperty(key) {
    let deleteParams = {
      namespace: this.$state.params.namespace,
      entityType: this.$state.params.entityType,
      entityId: this.$state.params.entityId,
      key: key,
      scope: this.$scope
    };
    this.myTrackerApi.deleteEntityProperty(deleteParams)
      .$promise
      .then(() => {
        delete this.properties.user[key];
      }, (err) => {
        this.myAlertOnValium.show({
          type: 'danger',
          content: err.data
        });
      });
  }

  addProperty() {
    if (!this.propertyInput.key || !this.propertyInput.value) { return; }

    let addParams = {
      namespace: this.$state.params.namespace,
      entityType: this.$state.params.entityType,
      entityId: this.$state.params.entityId,
      scope: this.$scope
    };

    let obj = {};
    obj[this.propertyInput.key] = this.propertyInput.value;

    this.myTrackerApi.addEntityProperty(addParams, obj)
      .$promise
      .then(() => {
        this.properties.user[this.propertyInput.key] = this.propertyInput.value;

        this.propertyInput.key = '';
        this.propertyInput.value = '';
        this.propertyFocus();
      }, (err) => {
        this.myAlertOnValium.show({
          type: 'danger',
          content: err.data
        });
      });
  }

  propertyKeypress(event) {
    switch (event.keyCode) {
      case 13: // Enter Key
        this.addProperty();
        break;
    }
  }

  propertyFocus() {
    this.$timeout( () => {
      let elem = document.getElementById('property-key-input');
      angular.element(elem)[0].focus();
    });
  }

  /* TAGS CONTROL */

  getUserTags() {
    const params = {
      namespace: this.$state.params.namespace,
      entityId: this.$state.params.entityId,
      entityType: this.$state.params.entityType === 'streams' ? 'stream' : 'dataset',
      scope: this.$scope
    };

    this.myTrackerApi.getUserTags(params)
      .$promise
      .then((res) => {
        this.userTags = res;
      });
  }

  deleteTag(tag) {
    const params = {
      namespace: this.$state.params.namespace,
      entityId: this.$state.params.entityId,
      entityType: this.$state.params.entityType === 'streams' ? 'stream' : 'dataset',
      tag,
      scope: this.$scope
    };

    this.myTrackerApi.deleteTag(params)
      .$promise
      .then(() => {
        this.getUserTags();
      });
  }

  addTag() {
    const input = this.tagInputModel;
    if (!input) { return; }

    this.invalidFormat = false;

    this.duplicateTag = this.userTags
      .filter(tag => input === tag.name).length > 0 ? true : false;

    if (!this.duplicateTag) {
      const params = {
        namespace: this.$state.params.namespace,
        entityId: this.$state.params.entityId,
        entityType: this.$state.params.entityType === 'streams' ? 'stream' : 'dataset',
        scope: this.$scope
      };

      this.myTrackerApi.addTag(params, [input])
        .$promise
        .then(() => {
          this.getUserTags();
          this.tagInputModel = '';
        }, (err) => {
          if (err.statusCode === 400) {
            this.invalidFormat = true;
          }
        });
    }
  }

  goToTag(event, tag) {
    event.stopPropagation();
    this.$state.go('tracker.detail.result', {searchQuery: tag});
  }

  openTagInput(event) {
    event.stopPropagation();
    this.inputOpen = true;
    this.caskFocusManager.focus('tagInput');

    this.eventFunction = () => {
      this.escapeInput();
    };
    document.body.addEventListener('click', this.eventFunction, false);
  }

  escapeInput() {
    this.invalidFormat = false;
    this.duplicateTag = false;
    this.inputOpen = false;
    document.body.removeEventListener('click', this.eventFunction, false);
    this.eventFunction = null;
  }

}

angular.module(PKG.name + '.feature.tracker')
  .controller('TrackerMetadataController', TrackerMetadataController);
