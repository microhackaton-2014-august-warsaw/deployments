deployments
===========

simple rest-controller deploying server

/deploy (POST)
-------------=
Send a json like this:

{
 "artifactId": "matcher",
 "groupId": "pl.microhackaton",
 "version": "CD-3",
 "jvmParams": "-Dspring.profiles.active=dev -Dserver.port=8099 -Dservice.resolver.url=zookeeper.microhackathon.pl:2181”
}

This will download jar from configured nexus (nexus.microhackathon.pl / releases) and run it with passed parameters in jvmParams. Clever enough not to start two microservices of the same kind (but different version for example).

/stop (GET)
-----------

Will stop all deployments and switch off the server

/list (GET)
-----------

Will print out all working deployments
