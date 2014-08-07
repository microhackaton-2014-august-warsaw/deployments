package com.ofg.deployments

import groovy.transform.Canonical
import groovy.transform.TypeChecked

@TypeChecked
@Canonical
class ProcessWithDeployment {
    Process process
    Deployment deployment
}
