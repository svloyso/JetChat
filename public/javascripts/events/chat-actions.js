import Reflux from 'reflux';

var ChatActions = Reflux.createActions([
    'selectGroup',
    'selectTopic',
    'selectUser',
    'newGroup',
    'newUser',
    'newTopic',
    'newMessage',
    'showIntegrations',
    'selectIntegrationGroup',
    'selectIntegration',
    'selectIntegrationTopic',
    'markTopicAsRead',
    'markMessageAsRead',
    'markDirectMessageAsRead'
]);

export default ChatActions;