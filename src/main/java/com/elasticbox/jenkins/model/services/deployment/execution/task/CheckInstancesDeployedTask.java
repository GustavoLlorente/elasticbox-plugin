/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.services.deployment.execution.task;

import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.error.RepositoryException;
import com.elasticbox.jenkins.model.services.deployment.execution.context.AbstractBoxDeploymentContext;
import com.elasticbox.jenkins.model.services.task.ScheduledPoolingTask;
import com.elasticbox.jenkins.model.services.task.TaskException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class CheckInstancesDeployedTask extends ScheduledPoolingTask<List<Instance>> {

    private static final Logger logger = Logger.getLogger(CheckInstancesDeployedTask.class.getName());

    private static final long DEFAULT_DELAY = 200;
    private static final long DEFAULT_INITIAL_DELAY = 3;
    private static final long DEFAULT_TIMEOUT = 3600;
    private static final long ALL_INSTANCES_DONE_REQUIRED_TIMES = 2;

    private boolean done = false;
    private int okCounter = 0;
    private List<Instance> instances = new ArrayList<>();
    private AbstractBoxDeploymentContext deploymentContext;

    public CheckInstancesDeployedTask(
            AbstractBoxDeploymentContext deploymentContext,
            List<Instance> instances,
            long delay,
            long initialDelay,
            long timeout) {

        super(delay, initialDelay, timeout);
        this.instances = instances;
        this.deploymentContext = deploymentContext;
    }

    public CheckInstancesDeployedTask(
            AbstractBoxDeploymentContext deploymentContext, long delay, long initialDelay, long timeout) {

        this(deploymentContext, null, delay, initialDelay, timeout);
    }

    public CheckInstancesDeployedTask(AbstractBoxDeploymentContext deploymentContext) {
        this(deploymentContext, null, DEFAULT_DELAY, DEFAULT_INITIAL_DELAY, DEFAULT_TIMEOUT);
    }

    @Override
    protected void performExecute() throws TaskException {

        if (instances.isEmpty()) {

            logger.log(
                    Level.SEVERE,
                    "Error executing task: " + this.getClass().getSimpleName() + ", there are no instances to check");

            return;
        }
        if (!done) {
            try {

                String[] ids = new String[instances.size()];
                int instanceCounter = 0;
                for (Instance instance : getInstances()) {
                    ids[instanceCounter] = instance.getId();
                    instanceCounter++;
                }

                final String owner = deploymentContext.getOrder().getOwner();
                result = deploymentContext.getInstanceRepository().getInstances(owner, ids);

                if (result != null && !getResult().isEmpty()) {
                    final String endpointUrl = deploymentContext.getCloud().getEndpointUrl();
                    boolean allInstancesDone = true;
                    for (Instance instance : getResult()) {
                        final Instance.State currentState = instance.getState();

                        logger.log(Level.INFO, "[" + counter + "] Checking instance: "
                                + instance.getInstancePageUrl(endpointUrl)
                                + ", current state: " + instance.getState());

                        deploymentContext.getLogger().info(
                                "[" + counter + "] Checking instance: "
                                        + instance.getInstancePageUrl(endpointUrl)
                                        + ", current state: " + instance.getState());

                        switch (currentState) {

                            case PROCESSING:
                                allInstancesDone = false;
                                break;

                            case DONE:
                                break;

                            case UNAVAILABLE:

                                allInstancesDone = false;

                                logger.log(
                                        Level.SEVERE,
                                        "[" + counter + "] Error checking instance: "
                                                + instance.getInstancePageUrl(endpointUrl)
                                                + ", state: " + instance.getState());

                                deploymentContext.getLogger().info(
                                        "[" + counter + "] Error checking instance: "
                                                + instance.getInstancePageUrl(endpointUrl)
                                                + ", state: " + instance.getState());

                                throw new TaskException(
                                        "Error checking application box instances, instance: "
                                                + instance.getInstancePageUrl(endpointUrl) + " unavailable");

                            default:
                        }
                    }
                    if (allInstancesDone) {
                        okCounter++;
                    }
                } else {
                    logger.log(
                            Level.INFO, "[" + counter + "] Error checking application box instances, there is no "
                            + "result from the API to check");

                    deploymentContext.getLogger().info("[" + counter + "] Error checking application box instances, "
                            + "there is no result from the API to check");

                    throw new TaskException("Error checking application box instances, there is no result from the "
                            + "API to check");
                }


            } catch (RepositoryException e) {
                logger.log(Level.SEVERE, "Error executing task: CheckInstancesDeployedTask", e);
                throw new TaskException("Error executing task: CheckInstancesDeployedTask", e);
            }
        }
    }

    @Override
    public boolean isDone() {
        final List<Instance> instances = getResult();
        if (instances != null && !instances.isEmpty()) {
            for (Instance instance : instances) {
                if (instance.getState() != Instance.State.DONE) {
                    return false;
                }
            }
            if (okCounter == ALL_INSTANCES_DONE_REQUIRED_TIMES) {
                done = true;
            }
        }
        return done;
    }

    public List<Instance> getInstances() {
        return instances;
    }

    public void setInstances(List<Instance> instances) {
        this.instances = instances;
    }
}
