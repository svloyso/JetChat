jetchat
=======

This repository SSH URL: ssh://git-hosting.labs.intellij.net/jetchat.git
This repository HTTPS URL: https://git-hosting.labs.intellij.net/jetchat.git

Develop with pleasure(R)

```bash
sbt clean

sbt docker:publishLocal

docker run -p <public_http_port>:<http_port> <image_id> -Dslick.dbs.default.db.url=<db_url> -Dslick.dbs.default.db.user=<db_user> -Dslick.dbs.default.db.password=<db_password> -Dplay.evolutions.autoApply=true -DETCDCTL_PEERS=<etcd_peers> -Dakka.remote.netty.tcp.hostname=<seed_host> -Dakka.remote.netty.tcp.port=<seed_port> -Dhttp.port=<http_port>
```