import React from 'react';
import ReactDOM from 'react-dom';
import NewGroupDialog from './new-group-dialog';

var NewGroupButton = React.createClass({
    componentDidMount: function () {
        $(ReactDOM.findDOMNode(this)).popover({
            react: true,
            content: <NewGroupDialog/>
        }).on("shown.bs.popover", function () {
            $(".new-group-popover .group-name").focus();
        });
    },

    render: function () {
        return (
            <a id="new-group">New group</a>
        );
    }
});

export default NewGroupButton;