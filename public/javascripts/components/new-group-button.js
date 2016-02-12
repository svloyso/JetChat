import React from 'react';
import ReactDOM from 'react-dom';
import NewGroupDialog from './new-group-dialog';
var $ = require('jquery');
import {Popover, OverlayTrigger} from 'react-bootstrap';


var NewGroupButton = React.createClass({
    render: function () {
        return (
            <OverlayTrigger ref="trigger" placement="right" trigger="click" overlay={<Popover id="new-group"><NewGroupDialog trigger={this.refs.trigger}/></Popover>}>
                <a id="new-group">New group</a>
            </OverlayTrigger>
        );
    }
});

export default NewGroupButton;