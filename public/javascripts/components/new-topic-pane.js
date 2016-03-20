import React from 'react';
import Reflux from 'reflux';
import ChatActions from '../events/chat-actions';
import ChatStore from '../events/chat-store';

var NewTopicPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    onClick: function () {
        if (this.state.store.selected.groupId) {
            ChatActions.selectTopic();
        }
    },

    render: function () {
        var newTopicClass = ((this.state.store.selected.groupId ? "enabled" : "") + " " + (!this.state.store.selected.topicId ? "selected" : "")).trim();
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