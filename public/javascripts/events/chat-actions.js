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
    'newIntegrationMessage',
    'markTopicAsRead',
    'markMessageAsRead',
    'markDirectMessageAsRead',
    'enableIntegration',
    'disableIntegration',
    'alertQuery',
    'userOnline',
    'userOffline',
    'loadNextPage'
]);

export default ChatActions;