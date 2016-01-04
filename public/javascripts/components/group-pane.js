import React from 'react';
import Reflux from 'reflux';
import ChatStore from '../events/chat-store';
import ChatActions from '../events/chat-actions';
import NewGroupButton from './new-group-button';
import SettingsButton from './settings-button'

var GroupPane = React.createClass({
    mixins: [Reflux.connect(ChatStore, 'store')],

    onGroupClick: function (group) {
        ChatActions.selectGroup(group);
    },

    onUserClick: function (user) {
        ChatActions.selectUser(user);
    },

    render: function () {
        var self = this;
        var groupItems = self.state.store.groups.map(function (group) {
            var groupClass = (self.state.store.selectedGroup && self.state.store.selectedGroup.id == group.id) ? "selected" : "";
            return (
                <li data-group={group.id} className={groupClass}
                    onClick={self.onGroupClick.bind(self, group)} key={group.id}>
                    <span className="group-header">#</span>
                    <span>{group.name}</span>
                </li>
            );
        });

        var userItems = self.state.store.users.map(function (user) {
            var userClass = (self.state.store.selectedUser && self.state.store.selectedUser.id == user.id) ? "selected" : "";
            return (
                <li data-user={user.id} className={userClass}
                    onClick={self.onUserClick.bind(self, user)} key={user.id}>
                    <span>{user.name}</span>
                </li>
            );
        });


        var integrationItems = self.state.store.integrations.filter(function (integration) {
            return true
        }).map(function (integration) {
            return (
                <span className="integration-name" key={integration.id}>{integration.name}</span>
            );
        });

        var allGroupsClass = (!self.state.store.selectedGroup && !self.state.store.selectedUser && !self.state.store.displaySettings) ? "selected" : "";

        return (
            <ul id="group-pane">
                <li id="all-groups" className={allGroupsClass}
                    onClick={self.onGroupClick.bind(self, undefined)}>
                    <span>All groups</span></li>
                {groupItems}
                <NewGroupButton/>
                {integrationItems}
                <SettingsButton selected={self.state.store.displaySettings}/>
                {userItems}
            </ul>
        );
    }
});

export default GroupPane;