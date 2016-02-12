import React from 'react';
import ReactDOM from 'react-dom';
import ChatActions from '../events/chat-actions';
var $ = require('jquery');

var NewGroupDialog = React.createClass({
    createGroup: function () {
        var groupName = ReactDOM.findDOMNode(this.refs.groupName).value.trim();
        if (groupName && !/\s/.test(groupName)) {
            $.ajax({
                type: "POST",
                url: "/json/group/add",
                data: JSON.stringify(groupName),
                contentType: "application/json",
                success: function (group) {
                    ChatActions.newGroup(group, true);
                },
                fail: function (e) {
                    console.error(e);
                }
            });
            this.props.trigger.setState({
                isOverlayShown: false
            });
            ReactDOM.findDOMNode(this.refs.groupName).value = "";
        }
    },

    onKeyPress: function (event) {
        if (event.which == 13) {
            this.createGroup();
        }
    },

    componentDidMount: function () {
        $(ReactDOM.findDOMNode(this.refs.groupName)).focus();
    },

    render: function () {
        return (
            <div className="new-group-popover">
                <input ref="groupName" type="text" className="group-name" placeholder="Name..."
                       onKeyPress={this.onKeyPress}/>
                <div className="group-label">Must be 21 characters or less. No spaces or periods.</div>
                <a className='btn btn-default btn-sm' onClick={this.createGroup}>Create group</a>
            </div>
        );
    }
});

export default NewGroupDialog;