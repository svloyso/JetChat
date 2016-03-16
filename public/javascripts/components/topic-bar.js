import React from 'react';
import Reflux from 'reflux';
import SearchPane from './search-pane';
import NewTopicPane from './new-topic-pane';
import TopicPane from './topic-pane';
import ChatStore from '../events/chat-store';

var TopicBar = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    render: function () {
        var invisible = this.state.store.selectedUser ||
            this.state.store.selected.stateId == this.state.store.SETTINGS;
        return (
            <div id="topic-bar" style={{display: invisible ? "none" : ""}}>
                <SearchPane/>
                <NewTopicPane/>
                <TopicPane/>
            </div>
        );
    }
});

export default TopicBar;