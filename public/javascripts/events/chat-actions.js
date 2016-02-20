import Reflux from 'reflux';

var ChatActions = Reflux.createActions([
    'selectGroup',
    'selectTopic',
    'selectUserTopic',
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
    'markDirectMessageAsRead',
    'enableIntegration',
    'disableIntegration'
]);

export default ChatActions;