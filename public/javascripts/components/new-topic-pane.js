import React from 'react';
import Reflux from 'reflux';
import ChatActions from '../events/chat-actions';
import ChatStore from '../events/chat-store';

var NewTopicPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    onClick: function () {
        if (this.state.store.selectedGroup) {
            ChatActions.selectTopic();
        } else if (this.state.store.selectedIntegrationGroup) {
            ChatActions.selectIntegrationTopic(this.state.store.selectedIntegration, this.state.store.selectedIntegrationGroup);
        }
    },

    render: function () {
        var newTopicClass =
            (((this.state.store.selectedGroup || this.state.store.selectedIntegrationGroup) ? "enabled" : "") + " " +
            (!(this.state.store.selectedTopic || this.state.store.selectedIntegrationTopic) ? "selected" : "")).trim();
        return (
            <div id="new-topic-pane">
                <a id="new-topic" className={newTopicClass}
                   onClick={this.onClick}>
                    <span id="plus"/>
                    <span>New topic</span>
                </a>
            </div>
        );
    }
});

export default NewTopicPane;