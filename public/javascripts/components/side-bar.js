import React from 'react';
import GroupPane from './group-pane';

var SideBar = React.createClass({
    render: function () {
        return (
            <div id="side-bar">
                <div id="header">JetChat</div>
                <GroupPane/>
            </div>
        );
    }
});

export default SideBar;