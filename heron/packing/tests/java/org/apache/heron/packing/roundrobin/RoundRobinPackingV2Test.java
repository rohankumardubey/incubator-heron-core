/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.heron.packing.roundrobin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import org.apache.heron.common.basics.ByteAmount;
import org.apache.heron.packing.CommonPackingTests;
import org.apache.heron.spi.packing.IPacking;
import org.apache.heron.spi.packing.IRepacking;
import org.apache.heron.spi.packing.PackingException;
import org.apache.heron.spi.packing.PackingPlan;
import org.apache.heron.spi.packing.Resource;

public class RoundRobinPackingV2Test extends CommonPackingTests {
  @Override
  protected IPacking getPackingImpl() {
    return new RoundRobinPackingV2();
  }

  @Override
  protected IRepacking getRepackingImpl() {
    return new RoundRobinPackingV2();
  }

  private Resource getDefaultPadding() {
    return new Resource(RoundRobinPackingV2.DEFAULT_CPU_PADDING_PER_CONTAINER,
        RoundRobinPackingV2.DEFAULT_RAM_PADDING_PER_CONTAINER, ByteAmount.ZERO);
  }

  @Before
  public void setUp() {
    super.setUp();
    numContainers = 2;
    topologyConfig.setNumStmgrs(numContainers);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);
  }

  @Test(expected = PackingException.class)
  public void testCheckInsufficientRamFailure() throws Exception {
    // Explicit set insufficient RAM for container
    ByteAmount containerRam = ByteAmount.ZERO;
    topologyConfig.setContainerRamRequested(containerRam);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology, instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers,
        getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithRam(containerRam));
  }

  @Test(expected = PackingException.class)
  public void testCheckInsufficientCpuFailure() throws Exception {
    // Explicit set insufficient CPU for container
    double containerCpu = 1.0;
    topologyConfig.setContainerCpuRequested(containerCpu);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology, instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers,
        getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithCpu(containerCpu));
  }

  @Test
  public void testDefaultResources() throws Exception {
    doPackingTest(topology,
        instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()));
  }

  /**
   * Test the scenario container level resource config are set
   */
  @Test
  public void testContainerRequestedResources() throws Exception {
    // Explicit set resources for container
    ByteAmount containerRam = ByteAmount.fromGigabytes(10);
    ByteAmount containerDisk = ByteAmount.fromGigabytes(20);
    double containerCpu = 30;
    Resource containerResource = new Resource(containerCpu, containerRam, containerDisk);

    topologyConfig.setContainerRamRequested(containerRam);
    topologyConfig.setContainerDiskRequested(containerDisk);
    topologyConfig.setContainerCpuRequested(containerCpu);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers, containerResource);
  }

  /**
   * Test the scenario container level resource config are set
   */
  @Test
  public void testContainerRequestedResourcesWhenRamPaddingSet() throws Exception {
    // Explicit set resources for container
    ByteAmount containerRam = ByteAmount.fromGigabytes(10);
    ByteAmount containerDisk = ByteAmount.fromGigabytes(20);
    double containerCpu = 30;
    ByteAmount containerRamPadding = ByteAmount.fromMegabytes(512);
    Resource containerResource = new Resource(containerCpu, containerRam, containerDisk);

    topologyConfig.setContainerRamRequested(containerRam);
    topologyConfig.setContainerDiskRequested(containerDisk);
    topologyConfig.setContainerCpuRequested(containerCpu);
    topologyConfig.setContainerRamPadding(containerRamPadding);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers, containerResource);
  }

  /**
   * Test the scenario RAM map config is completely set
   */
  @Test
  public void testCompleteRamMapRequestedWithExactlyEnoughResource() throws Exception {
    // Explicit set resources for container
    // the value should be ignored, since we set the complete component RAM map
    ByteAmount containerRam = ByteAmount.fromGigabytes(8);

    // Explicit set component RAM map
    ByteAmount boltRam = ByteAmount.fromGigabytes(1);
    ByteAmount spoutRam = ByteAmount.fromGigabytes(2);

    topologyConfig.setContainerRamRequested(containerRam);
    topologyConfig.setComponentRam(BOLT_NAME, boltRam);
    topologyConfig.setComponentRam(SPOUT_NAME, spoutRam);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithRam(boltRam), boltParallelism,
        instanceDefaultResources.cloneWithRam(spoutRam), spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithRam(containerRam));
  }

  /**
   * Test the scenario RAM map config is completely set
   */
  @Test(expected = PackingException.class)
  public void testCompleteRamMapRequestedWithLessThanEnoughResource() throws Exception {
    // Explicit set resources for container
    // the value should be ignored, since we set the complete component RAM map
    ByteAmount containerRam = ByteAmount.fromGigabytes(2);

    // Explicit set component RAM map
    ByteAmount boltRam = ByteAmount.fromGigabytes(1);
    ByteAmount spoutRam = ByteAmount.fromGigabytes(2);

    topologyConfig.setContainerRamRequested(containerRam);
    topologyConfig.setComponentRam(BOLT_NAME, boltRam);
    topologyConfig.setComponentRam(SPOUT_NAME, spoutRam);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithRam(boltRam), boltParallelism,
        instanceDefaultResources.cloneWithRam(spoutRam), spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithRam(containerRam));
  }

  /**
   * Test the scenario RAM map config is completely set
   */
  @Test
  public void testCompleteRamMapRequestedWithMoreThanEnoughResource() throws Exception {
    // Explicit set resources for container
    // the value should be ignored, since we set the complete component RAM map
    ByteAmount containerRam = ByteAmount.fromBytes(Long.MAX_VALUE);

    // Explicit set component RAM map
    ByteAmount boltRam = ByteAmount.fromGigabytes(1);
    ByteAmount spoutRam = ByteAmount.fromGigabytes(2);

    topologyConfig.setContainerRamRequested(containerRam);
    topologyConfig.setComponentRam(BOLT_NAME, boltRam);
    topologyConfig.setComponentRam(SPOUT_NAME, spoutRam);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithRam(boltRam), boltParallelism,
        instanceDefaultResources.cloneWithRam(spoutRam), spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithRam(containerRam));
  }

  /**
   * Test the scenario RAM map config is completely set
   */
  @Test
  public void testCompleteRamMapRequestedWithoutPaddingResource() throws Exception {
    // Explicit set resources for container
    // the value should be ignored, since we set the complete component RAM map
    ByteAmount containerRam = ByteAmount.fromGigabytes(6);

    // Explicit set component RAM map
    ByteAmount boltRam = ByteAmount.fromGigabytes(1);
    ByteAmount spoutRam = ByteAmount.fromGigabytes(2);

    topologyConfig.setContainerRamRequested(containerRam);
    topologyConfig.setComponentRam(BOLT_NAME, boltRam);
    topologyConfig.setComponentRam(SPOUT_NAME, spoutRam);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithRam(boltRam), boltParallelism,
        instanceDefaultResources.cloneWithRam(spoutRam), spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithRam(containerRam));
  }

  /**
   * Test the scenario CPU map config is completely set and there are exactly enough resource
   */
  @Test
  public void testCompleteCpuMapRequestedWithExactlyEnoughResource() throws Exception {
    // Explicit set resources for container
    double containerCpu = 17;

    // Explicit set component CPU map
    double boltCpu = 4;
    double spoutCpu = 4;

    topologyConfig.setContainerCpuRequested(containerCpu);
    topologyConfig.setComponentCpu(BOLT_NAME, boltCpu);
    topologyConfig.setComponentCpu(SPOUT_NAME, spoutCpu);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithCpu(boltCpu), boltParallelism,
        instanceDefaultResources.cloneWithCpu(spoutCpu), spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithCpu(containerCpu));
  }

  /**
   * Test the scenario CPU map config is completely set and there are more than enough resource
   */
  @Test
  public void testCompleteCpuMapRequestedWithMoreThanEnoughResource() throws Exception {
    // Explicit set resources for container
    double containerCpu = 30;

    // Explicit set component CPU map
    double boltCpu = 4;
    double spoutCpu = 4;

    topologyConfig.setContainerCpuRequested(containerCpu);
    topologyConfig.setComponentCpu(BOLT_NAME, boltCpu);
    topologyConfig.setComponentCpu(SPOUT_NAME, spoutCpu);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithCpu(boltCpu), boltParallelism,
        instanceDefaultResources.cloneWithCpu(spoutCpu), spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithCpu(containerCpu));
  }

  /**
   * Test the scenario CPU map config is completely set and there are less than enough resource
   */
  @Test(expected = PackingException.class)
  public void testCompleteCpuMapRequestedWithLessThanEnoughResource() throws Exception {
    // Explicit set resources for container
    double containerCpu = 10;

    // Explicit set component CPU map
    double boltCpu = 4;
    double spoutCpu = 4;

    topologyConfig.setContainerCpuRequested(containerCpu);
    topologyConfig.setComponentCpu(BOLT_NAME, boltCpu);
    topologyConfig.setComponentCpu(SPOUT_NAME, spoutCpu);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithCpu(boltCpu), boltParallelism,
        instanceDefaultResources.cloneWithCpu(spoutCpu), spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithCpu(containerCpu));
  }

  /**
   * Test the scenario CPU map config is completely set
   * and there are exactly enough resource for instances, but not enough for padding
   */
  @Test
  public void testCompleteCpuMapRequestedWithoutPaddingResource() throws Exception {
    // Explicit set resources for container
    double containerCpu = 16;

    // Explicit set component CPU map
    double boltCpu = 4;
    double spoutCpu = 4;

    topologyConfig.setContainerCpuRequested(containerCpu);
    topologyConfig.setComponentCpu(BOLT_NAME, boltCpu);
    topologyConfig.setComponentCpu(SPOUT_NAME, spoutCpu);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithCpu(boltCpu), boltParallelism,
        instanceDefaultResources.cloneWithCpu(spoutCpu), spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithCpu(containerCpu));
  }

  /**
   * Test the scenario RAM map config is partially set
   */
  @Test
  public void testPartialRamMap() throws Exception {
    // Explicit set resources for container
    ByteAmount containerRam = ByteAmount.fromGigabytes(10);

    // Explicit set component RAM map
    ByteAmount boltRam = ByteAmount.fromGigabytes(1);

    topologyConfig.setContainerRamRequested(containerRam);
    topologyConfig.setComponentRam(BOLT_NAME, boltRam);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithRam(boltRam), boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithRam(containerRam));
  }

  /**
   * Test the scenario CPU map config is partially set
   */
  @Test
  public void testPartialCpuMap() throws Exception {
    // Explicit set resources for container
    double containerCpu = 17;

    // Explicit set component CPU map
    double boltCpu = 4;

    topologyConfig.setContainerCpuRequested(containerCpu);
    topologyConfig.setComponentCpu(BOLT_NAME, boltCpu);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources.cloneWithCpu(boltCpu), boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithCpu(containerCpu));

  }

  /**
   * test even packing of instances
   */
  @Test
  public void testEvenPacking() throws Exception {
    int componentParallelism = 4;
    boltParallelism = componentParallelism;
    spoutParallelism = componentParallelism;

    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology,
        instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()));
  }


  /**
   * test re-packing with same total instances
   */
  @Test
  public void testRepackingWithSameTotalInstances() throws Exception {
    int componentParallelism = 4;
    boltParallelism = componentParallelism;
    spoutParallelism = componentParallelism;

    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    PackingPlan packingPlan = doPackingTestWithPartialResource(topology,
        Optional.empty(), Optional.empty(), boltParallelism,
        Optional.empty(), Optional.empty(), spoutParallelism,
        numContainers, getDefaultPadding(),
        getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()));

    Map<String, Integer> componentChanges = new HashMap<>();
    componentChanges.put(SPOUT_NAME, -1);
    componentChanges.put(BOLT_NAME,  +1);

    doScalingTest(topology, packingPlan, componentChanges,
        instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers, getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()));
  }

  /**
   * test re-packing with more total instances
   */
  @Test
  public void testRepackingWithMoreTotalInstances() throws Exception {
    int componentParallelism = 4;
    boltParallelism = componentParallelism;
    spoutParallelism = componentParallelism;

    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    PackingPlan packingPlan = doPackingTestWithPartialResource(topology,
        Optional.empty(), Optional.empty(), boltParallelism,
        Optional.empty(), Optional.empty(), spoutParallelism,
        numContainers, getDefaultPadding(),
        getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()));

    Map<String, Integer> componentChanges = new HashMap<>();
    componentChanges.put(SPOUT_NAME, +1);
    componentChanges.put(BOLT_NAME,  +1);

    doScalingTest(topology, packingPlan, componentChanges,
        instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers + 1,
        getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()));
  }

  /**
   * test re-packing with fewer total instances
   */
  @Test
  public void testRepackingWithFewerTotalInstances() throws Exception {
    int componentParallelism = 4;
    boltParallelism = componentParallelism;
    spoutParallelism = componentParallelism;

    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    PackingPlan packingPlan = doPackingTestWithPartialResource(topology,
        Optional.empty(), Optional.empty(), boltParallelism,
        Optional.empty(), Optional.empty(), spoutParallelism,
        numContainers, getDefaultPadding(),
        getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()));

    Map<String, Integer> componentChanges = new HashMap<>();
    componentChanges.put(SPOUT_NAME, -2);
    componentChanges.put(BOLT_NAME,  -2);

    doScalingTest(topology, packingPlan, componentChanges,
        instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        numContainers - 1,
        getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()));
  }

  @Test(expected = RuntimeException.class)
  public void testZeroContainersFailure() throws Exception {
    // Explicit set insufficient RAM for container
    ByteAmount containerRam = ByteAmount.fromGigabytes(10);
    topologyConfig.setContainerRamRequested(containerRam);
    topologyConfig.setNumStmgrs(0);
    topology = getTopology(spoutParallelism, boltParallelism, topologyConfig);

    doPackingTest(topology, instanceDefaultResources, boltParallelism,
        instanceDefaultResources, spoutParallelism,
        0,  // 0 containers
        getDefaultUnspecifiedContainerResource(boltParallelism + spoutParallelism,
            numContainers, getDefaultPadding()).cloneWithRam(containerRam));
  }
}
