import React from 'react';
import GroupPane from './group-pane';
import {Dropdown, MenuItem} from 'react-bootstrap';

var SideBar = React.createClass({
    onLogout: function(e) {
        window.location.replace("/logout")
    },

    render: function () {
        return (
            <div id="side-bar">
                <div id="header"><span>JetChat</span>
                    <Dropdown id="settings-dropdown">
                        <a href="#" bsRole="toggle">
                            <span id="gear"></span>
                        </a>
                        <Dropdown.Menu bsRole="menu">
                            <MenuItem eventKey="2" onSelect={this.onLogout}>Logout</MenuItem>
                        </Dropdown.Menu>
                    </Dropdown>
                </div>
                <GroupPane/>
            </div>
        );
    }
});

export default SideBar;