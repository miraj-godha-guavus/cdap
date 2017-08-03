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
import React, {PropTypes} from 'react';
import {connect} from 'react-redux';
import isNil from 'lodash/isNil';

const Count = ({count}) => <span>{count}</span>;
Count.propTypes = {
  count: PropTypes.number
};

const mapRuleBookStateToProps = (state) => {
  return {
    count: isNil(state.rulebooks.list) ? 0 : state.rulebooks.list.length
  };
};

const RuleBookCountWrapper = connect(
  mapRuleBookStateToProps
)(Count);


const RulesCount = ({count}) => <span>{count}</span>;
RulesCount.propTypes = {
  count: PropTypes.number
};

const mapRulesStateToProps = (state) => {
  return {
    count: isNil(state.rules.list) ? 0 : state.rules.list.length
  };
};

const RulesCountWrapper = connect(
  mapRulesStateToProps
)(Count);

export {RuleBookCountWrapper, RulesCountWrapper};
