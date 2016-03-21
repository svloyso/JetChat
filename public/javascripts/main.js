import React from 'react';
import ReactDOM from 'react-dom';
import $ from 'jquery';
import Loader from './components/loader';

var PreLoader = React.createClass({
    componentWillMount: function () {
        $.ajax({
            context: this,
            type: "GET",
            url: "/json/state",
            data: {
                userId: _global.user.id,
                groupId: _global.groupId,
                integrationId: _global.selectedIntegrationId,
                integrationTopicGroupId: _global.selectedIntegrationTopicGroupId,
                integrationTopicId: _global.selectedIntegrationTopicId
            },
            success: function (state) {
                _global.users = state.users;
                _global.groups = state.groups;
                _global.integrations = state.integrations;
                _global.integrationGroups = state.integrationGroups;
                _global.selectedIntegrationTopic = state.integrationTopic;
                _global.topics = state.topics;
                var App = require('./app');
                ReactDOM.render(<App/>, document.getElementById('app'))
            },
            fail: function (e) {
                console.error(e);
            }
        });
    },

    render: function () {
        return (
            <div>
                <Loader id="loader" message="Loading JetChat..."/>
            </div>
        );
    }
});


document.addEventListener('DOMContentLoaded', () => {
    ReactDOM.render(<PreLoader/>, document.getElementById('app'))
});