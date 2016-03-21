import React from 'react';
import ChatStore from '../events/chat-store';
import ChatActions from '../events/chat-actions';
import classNames from 'classnames';
import NewGroupButton from './new-group-button';

var GroupPane = React.createClass({
    shouldComponentUpdate: function (nextProps) {
        return JSON.stringify(this.props) !== JSON.stringify(nextProps);
    },

    onGroupClick: function (groupId) {
        ChatActions.selectGroup(groupId);
    },

    onUserClick: function (userId) {
        ChatActions.selectUser(userId);
    },

    onIntegrationClick: function (integration) {
        ChatActions.selectIntegration(integration);
    },

    onIntegrationGroupClick: function (group) {
        ChatActions.selectIntegrationGroup(this.props.integrations.find(i => i.id == group.integrationId), group);
    },

    groupItem: function (group) {
        var groupClass = classNames({
                ['selected']: this.props.selectedGroupId === group.id,
                ['unread']: group.unreadCount > 0
            }
        );
        return (
            <li className={groupClass} data-group={group.id} key={group.id} onClick={this.onGroupClick.bind(this, group.id)}>
                <span className="group-header">#</span><span>{group.name}</span>
            </li>
        );
    },

    integrationItem: function (group) {
        var integration = this.props.integrations.find(i => i.id == group.key);
        var integrationGroupItems = group.data.map(group => {
            return (
                <li className={this.props.selectedGroupId === group.integrationGroupId ? 'selected' : ''}
                    data-group={group.id}
                    key={group.integrationGroupId}
                    onClick={this.onIntegrationGroupClick.bind(this, group)}>
                    <span className="group-header">#</span><span>{group.name}</span>
                </li>
            )
        });

        return (
            <ul className="integration-groups" key={group.key}>
                <li className={this.props.selectedIntegration === group.key ? "selected" : ""}
                    data-integration={group.key}
                    onClick={this.onIntegrationClick.bind(this, integration)}>
                    <span className="integration-name">{integration.name}</span></li>
                {integrationGroupItems}
            </ul>
        );
    },

    userItem: function (user) {
        var userClass = classNames({
                ['selected']: this.props.selectedUserId === user.id,
                ['unread']: user.unreadCount > 0
            }
        );
        return (
            <li data-user={user.id} className={userClass}
                onClick={this.onUserClick.bind(this, user.id)} key={user.id}>
                <span>{user.name}</span>
            </li>
        );
    },

    render: function () {
        var self = this;
        var groupItems = this.props.groups.map(g => this.groupItem(g));
        var userItems = this.props.users.map(u => this.userItem(u));
        var integrationItems = this.props.integrationGroups.map(g => this.integrationItem(g));

        return (
            <ul id="group-pane">
                <li id="all-groups" className={this.props.selectedMyChats ? 'selected' : ''}
                    onClick={this.onGroupClick.bind(this, undefined)}>
                    <span>My chats</span></li>
                {groupItems}
                <NewGroupButton/>
                {integrationItems}
                {userItems}
            </ul>
        );
    }
});

export default GroupPane;