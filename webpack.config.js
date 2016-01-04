module.exports = {

    entry: './public/javascripts/main.js',

    output: {
        path: './public/javascripts/webpack',
        filename: '[name].js'
    },

    module: {
        loaders: [
            { test: /\.js$/,   loader: 'babel?stage=0', exclude: /node_modules/ },
            { test: /\.less$/, loader: 'style!css!less' },
            { test: /\.css$/,  loader: 'style!css' }
        ]
    },

    externals: {
        //'react': 'React',
        //'react-dom': 'ReactDom',
        // 'moment': 'moment'
    },

    resolve: {
        extensions: ['', '.js']
    },

    devtool: 'source-map'

};