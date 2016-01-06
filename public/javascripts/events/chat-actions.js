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
    'selectIntegration'
]);

export default ChatActions;