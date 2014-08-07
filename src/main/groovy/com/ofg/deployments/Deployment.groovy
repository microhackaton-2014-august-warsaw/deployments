package com.ofg.deployments

import groovy.transform.Canonical
import groovy.transform.TypeChecked

@TypeChecked
@Canonical
class Deployment {
    String groupId
    String artifactId
    String version
    String jvmParams

    String uniqueId() {
        return "${groupId.replaceAll('\\.', '-')}-${artifactId}"
    }

    String uniqueIdWithVersion() {
        return uniqueId()+"-${version}"
    }
}
