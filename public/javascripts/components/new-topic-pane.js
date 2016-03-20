import React from 'react';
import ChatActions from '../events/chat-actions';
import ChatStore from '../events/chat-store';

var NewTopicPane = React.createClass({
    onClick: function () {
        ChatActions.selectTopic();
    },

    render: function () {
        return (
            <div id="new-topic-pane">
                <a id="new-topic" className={"enabled" + (this.props.selected ? " selected" : "")}
                   onClick={this.onClick}>
                    <span id="plus"/>
                    <span>New topic</span>
                </a>
            </div>
        );
    }
});

export default NewTopicPane;