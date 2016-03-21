import React from 'react';
import SearchPane from './search-pane';
import NewTopicPane from './new-topic-pane';
import TopicPane from './topic-pane';
import ChatStore from '../events/chat-store';

var TopicBar = React.createClass({
    render: function () {
        var panes = [<SearchPane/>];
        if (this.props.newTopicEnabled) {
            panes.push(<NewTopicPane selected={this.props.newTopicSelected} />);
        }

        if (this.props.topics) {
            panes.push(<TopicPane
                selectedGroupId={this.props.selectedGroupId}
                selectedTopicId={this.props.selectedTopicId}
                separator={this.props.separator}
                stateIsIntegration={this.props.stateIsIntegration}
                topics={this.props.topics}
            />);
        }

        return (
            <div id="topic-bar">
                {panes}
            </div>
        );
    }
});

export default TopicBar;