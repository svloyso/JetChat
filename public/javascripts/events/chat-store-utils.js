exports.deepEqual = function (o1, o2, properties) {
    for (var i = 0; i < properties.length; i++) {
        var p = properties[i];
        var pp;
        var ppi = p.indexOf(".");
        if (ppi > -1) {
            pp = p.substring(ppi + 1);
            p = p.substring(0, ppi);
        }
        if (o1.hasOwnProperty(p) && !o2.hasOwnProperty(p)
            || o2.hasOwnProperty(p) && !o1.hasOwnProperty(p)) {
            return false;
        }
        if (!o1[p] && o2[p] || !o2[p] && o1[p]) {
            return false;
        }
        if (Object.prototype.toString.call( o1[p] ) === '[object Array]' && Object.prototype.toString.call( o2[p] ) === '[object Array]') {
            if (o1[p].length != o2[p].length) {
                return false;
            }
            if (pp) {
                for (var j = 0; j < o1[p].length; j++) {
                    if (o1[p][j][pp] != o2[p][j][pp]) {
                        return false;
                    }
                }
            }
        } else {
            if (pp) {
                if (o1[p] && o2[p] && o1[p][pp] != o2[p][pp]) {
                    return false;
                }
            } else {
                if (o1[p] != o2[p]) {
                    return false;
                }
            }
        }
    }
    return true;
};