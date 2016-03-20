import React from 'react';
import SearchPane from './search-pane';
import NewTopicPane from './new-topic-pane';
import TopicPane from './topic-pane';
import ChatStore from '../events/chat-store';

var TopicBar = React.createClass({
    //mixins: [Reflux.connect(ChatStore, 'store')],

    //shouldComponentUpdate: function (nextProps, nextState) {
    //    console.log("TopicBar: receiving\nprops=" + JSON.stringify(nextProps) + "\nstate=" + JSON.stringify(nextState));
    //    return true;
    //},

    render: function () {
        return (
            <div id="topic-bar">
                <SearchPane/>
                <NewTopicPane/>
                <TopicPane/>
            </div>
        );
    }
});

export default TopicBar;